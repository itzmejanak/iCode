# Runtime bootstrap

Versioned assets and packaging rules for the Ubuntu ARM64 / PRoot runtime.

Everything here is verified on a device before it is depended on. Unverified
claims do not belong in this file.

## 1. Why executables live in the native library directory

Android forbids an app from executing files in its own data directory. The
SELinux policy for `untrusted_app` does not grant `execute` on `app_data_file`,
so a binary copied into `filesDir` cannot be run no matter what mode bits it
has. This is not configurable and it is not a `targetSdk` opt-out.

The only writable-at-install, executable-at-runtime location an app controls is
the **native library directory** (`ApplicationInfo.nativeLibraryDir`), populated
by the package installer from `lib/<abi>/` inside the APK. This is why Termux
ships every runtime binary as a `lib*.so`.

Two consequences shape the whole runtime:

1. **`useLegacyPackaging = true` is mandatory** for `jniLibs`. Modern AGP
   defaults it to `false`, which leaves native libraries compressed inside the
   APK and loaded by the linker directly — in that mode nothing is written to
   `nativeLibraryDir` and there is nothing to execute. The flag is set in
   `app/build.gradle.kts` with a comment pointing back here.
2. **Every executable must be named `lib*.so`**, including ones that are not
   libraries at all. The installer only extracts files matching that pattern.

### Verified on device

Measured from the app's own process (`ExecProbeActivity`, uid 10300), not from a
shell:

| Fact | Result |
|---|---|
| Device | Infinix X6881, Android 14 (SDK 34), `arm64-v8a`, SELinux enforcing |
| App `targetSdk` as installed | 36 |
| NDK used | r27 (27.0.12077973), target API 30, dynamically linked PIE |
| Extracted to `nativeLibraryDir` | yes, mode `-rwxr-xr-x`, label `apk_data_file` |
| Execute from `nativeLibraryDir` | **EXECUTED** — `exit=0`, arguments passed through |
| Execute a copy in `filesDir` | **REFUSED** — `IOException: error=13, Permission denied` |

So both halves of the assumption hold on real hardware: the native library
directory is executable, and the data directory is not.

### Do not verify this with `run-as`

`adb shell run-as <pkg> ./copy.bin` **succeeds** on the same device, and that
result is worthless. `run-as` runs in the `runas_app` SELinux domain, which is
deliberately allowed to execute `app_data_file` because it exists for
debugging. The app itself runs in `untrusted_app`, which is not. Measuring with
`run-as` produces the exact opposite conclusion to the truth.

Only two things give a truthful answer: the debug probe activity, or an
instrumented test.

### How to re-verify

```sh
./gradlew :app:assembleDebug
adb install -r -t --user 0 app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start --user 0 \
  -n dev.icode.mobile.demo.debug/dev.icode.mobile.runtime.ExecProbeActivity
adb logcat -d -s IcodeExecProbe
```

`--user 0` matters: this device's `adb shell` defaults to a secondary user, so
without it both the install and the launch silently target the wrong user.

`app/src/androidTest/.../NativeExecSpikeTest.kt` asserts the same three facts for
CI and emulators. It cannot run on this particular device — the ROM stalls the
instrumentation runner before any test is discovered ("Starting 0 tests") — which
is why the debug probe exists as the on-device path.

The probe and its `libexectest.so` live in the **debug source set**, so neither
can reach a release build.

**Removed 2026-09-26** (code-separation plan, H0): the spike's question is answered and the real
runtime executes from `nativeLibraryDir` on every launch, so `ExecProbeActivity`,
`RuntimeProbeActivity`, `libexectest.so` (the only 4 KB-aligned library left in the APK) and
`NativeExecSpikeTest` were deleted. The measurement above stays as the record; the commands no
longer apply.

## 2. Component sourcing

| Component | Source | Notes |
|---|---|---|
| `proot` | Termux `proot` 5.1.107.92 (aarch64), SHA256 `1f1c983509701f6826f568482c70673ee453a9ba38c9f5fa445a472d6b7524e9` | Depends on `libtalloc` and `libandroid-shmem` |
| `libtalloc` | Termux package | Ships as `libtalloc.so.2`; see the SONAME note below |
| `libandroid-shmem` | Termux package | PRoot's SysV IPC shim on Android |
| Ubuntu rootfs | `ubuntu-base` ARM64 release tarball | Too large to bundle: downloaded on first run and checksum-verified, per `architecture.md` §14.1 |

### SONAME note

Termux binaries link against versioned sonames such as `libtalloc.so.2`. That
filename is not extracted by the Android installer, so the shipped copy must be
`libtalloc.so` and `proot`'s `DT_NEEDED` entry has to match. Either patch the
ELF at packaging time, or build PRoot from source against unversioned sonames.
Building from source is preferred and is the task that follows this spike.

### PRoot loader

PRoot normally writes its loader ELF to a temporary directory and executes it —
which is exactly what Android's W^X rule forbids. The loader therefore has to be
shipped as its own `lib*.so` and pointed at explicitly through `PROOT_LOADER`
(and `PROOT_LOADER_32` where a 32-bit loader applies), never extracted at
runtime. **Unverified so far** — this is the next thing the spike must prove.

## 5. Invocation shape

```sh
libproot.so --link2symlink -0 -r <rootfs> \
    -b /dev -b /proc -b /sys \
    -b /system -b /apex -b /vendor -b /product \
    -b <REAL USER DIRECTORY>:/workspace \
    -w /workspace \
    /bin/sh -l
```

`/system`, `/apex`, `/vendor` and `/product` binds let the bionic linker resolve
host binaries from inside the guest. Each one must exist as a directory in the
rootfs before it can be bound.

**The one place iCode must diverge from Mobile-Harness:** that project binds an
app-internal directory to `/workspace`. iCode binds the user's *real* chosen
path — the direct-path invariant in `architecture.md` §26.1/26.2. That makes
the workspace bind cross the MANAGE_EXTERNAL_STORAGE / FUSE boundary, which has
to be verified on device as its own step.

## 6. What Android storage cannot do (measured)

The workspace is a bound Android directory, and that filesystem is not a normal
Linux one. Measured in the guest against `/storage/emulated/0`:

| Operation | `--link2symlink` on | off |
|---|---|---|
| hard link | fails | fails (`EPERM`) |
| symlink | fails | fails (`EPERM`) |
| **rename** | **fails** | **works** |

Two consequences:

1. **`--link2symlink` must be off for work against a bound Android directory.**
   It rewrites hard links as symlinks, which that filesystem also refuses — and
   with it enabled even `rename` stops working. That is what made `git clone`
   fail at the end with:

   ```
   error: unable to write file …/.git/objects/pack/pack-….pack: No such file or directory
   fatal: unable to rename temporary '*.pack' file
   ```

   Git falls back to `rename` when `link()` fails, so with the flag off the
   clone completes. It stays on for work inside the root filesystem, where
   tools do rely on hard links. See `RuntimeLayout.prootCommand`.

2. **Git's ownership check must be waived.** The directory is owned by a media
   uid while the guest runs as fake root, so ownership can never match and Git
   refuses every command with "detected dubious ownership". The exception is
   recorded in the guest's own global config, so the user's terminal can run
   `git` in the workspace too — not just the commands this app issues.

## 7. Provisioning tools

The base image has `apt` but no `git`, `ripgrep`, `curl` or `openssh-client`.
`apt-get update` reaches the Ubuntu archive from inside the guest, so tools are
installed on demand. Verified: `git version 2.43.0` and `ssh-keygen` both run.
