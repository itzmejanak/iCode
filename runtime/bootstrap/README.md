# Runtime bootstrap

This file is the maintainer reference for the native bootstrap that starts iCode's Ubuntu guest. It explains packaging and launch contracts; it does not duplicate mutable pins, product architecture, acceptance status, or release procedure.

## 1. Sources of truth

Use the narrowest current authority:

- `app/src/main/cpp/CMakeLists.txt` defines the native sources, patches, loader, executable carriers, ABI restriction, and linker alignment.
- `app/build.gradle.kts` defines APK native-library extraction, packaged ABIs, and the merged-library alignment check.
- `RuntimeLayout.kt`, `GuestPaths.kt`, `GuestContext.kt`, and `GuestEnvironment.kt` define host paths, PRoot arguments, binds, and guest environments.
- `RuntimeInstaller.kt` defines installation and readiness. `app/src/main/assets/runtime/manifest.json` defines the selected runtime and download pins.
- `third_party/`, `third_party/patches/`, and `THIRD_PARTY_NOTICES.md` define native provenance and local changes.

For wider context, use [Architecture](../../docs/architecture.md), [Runtime and terminal](../../docs/runtime-and-terminal.md), [Build and release](../../docs/build-and-release.md), and [Third-party notices](../../THIRD_PARTY_NOTICES.md). Current implementation and tests are authoritative for shipped behavior. Outstanding device acceptance belongs in the roadmap referenced by the runtime guide.

## 2. Native build contract

The app, guest, and native build support only `arm64-v8a`. Gradle packages only that ABI, CMake rejects every other ABI, and the runtime manifest requires it. There is no 32-bit PRoot or loader path.

CMake builds these components from repository source:

- the `icodespawn` process bridge;
- Termux terminal JNI;
- talloc 2.4.3 from `third_party/talloc`;
- libandroid-shmem from its pinned submodule;
- PRoot and its loader from the pinned Termux PRoot submodule; and
- the carrier targets that package the PRoot executables.

PRoot and libandroid-shmem are copied into the CMake build tree. Sorted patches from `third_party/patches/<component>/*.patch` are then applied with the host `patch` utility. Patch failure stops configuration, and the submodule worktrees remain unchanged.

The current patches are part of the runtime contract:

- PRoot hard-link emulation leaves the original file intact and returns the real error when a filesystem, including Android shared storage, refuses its symlinks.
- PRoot reports the initial guest process's actual exit or signal, propagates graceful termination, imposes a final kill deadline, and prevents tracees from surviving PRoot.
- Guest processes start with fresh login signal dispositions and an unblocked signal mask instead of inheriting Android process state.
- libandroid-shmem derives its key directory from `PROOT_TMP_DIR` and returns non-race symlink failures instead of retrying forever. The affected SysV IPC extension is not enabled by iCode.

All CMake native targets are linked for a 16 KiB maximum page size. Each Gradle packaging task also runs `check<Variant>PageAlignment` against the merged 64-bit native libraries and fails if any load segment is aligned below 16 KiB. The merged-output check is required because it validates what the APK will contain, including executable carriers and native libraries selected from duplicate inputs.

## 3. Executable carriers and loader

Android does not permit this app to execute arbitrary binaries copied into writable app-private data. PRoot and its loader therefore run from `ApplicationInfo.nativeLibraryDir`, while the writable Ubuntu root filesystem remains under `filesDir`.

Android's package machinery admits and extracts native-library entries, not general executable payloads. CMake handles that boundary as follows:

1. Build raw executables named `proot-bin` and `prootloader-bin`.
2. Build shared-library carrier targets so Gradle packages native entries named `libproot.so` and `libprootloader.so`.
3. After linking each carrier, copy the corresponding executable over the carrier output. The final packaged bytes are executables despite their `.so` names.

`useLegacyPackaging = true` is required for `jniLibs`. Without legacy packaging, Android may load native libraries directly from the APK, so the executable files would not exist at the paths under `nativeLibraryDir` that `RuntimeLayout` launches.

The PRoot loader is shipped and used. CMake builds it as a static, freestanding executable and compiles PRoot with `PROOT_UNBUNDLE_LOADER`. At launch, the host environment sets:

```text
LD_LIBRARY_PATH=<nativeLibraryDir>
PROOT_LOADER=<nativeLibraryDir>/libprootloader.so
PROOT_TMP_DIR=<cacheDir>/proot-tmp
PROOT_VERBOSE=-1
```

This prevents PRoot from writing a loader into a non-executable temporary directory. `PROOT_NO_SECCOMP` is deliberately not set.

## 4. Filesystem and invocation

The stable layout is:

```text
<filesDir>/runtime/rootfs     Ubuntu root, writable and app-private
<filesDir>/workspaces         all managed workspaces, writable and app-private
<nativeLibraryDir>/libproot.so
<nativeLibraryDir>/libprootloader.so
<cacheDir>/proot-tmp          PRoot temporary data
```

`RuntimeLayout.prootCommand()` is the only command builder. Its general argument shape is:

```text
<nativeLibraryDir>/libproot.so
  --link2symlink
  [--kill-on-exit]
  -0
  -r <filesDir>/runtime/rootfs
  [-b /dev] [-b /proc] [-b /sys]
  [-b /system] [-b /apex] [-b /vendor] [-b /product]
  [-b /storage/emulated/0]
  -b <filesDir>/workspaces:/workspace
  -w <guest-working-directory>
  /usr/bin/env -i <context-variables...> <guest-command...>
```

A host bind is added only when its source exists, and its destination is created in the rootfs first. The Android system binds allow guest tools to reach required kernel and Android trees. They do not add Android privileges.

Every launch binds the complete app-private workspaces root at `/workspace`. A workspace is `/workspace/<name>`, and every guest process can see all managed workspaces. `/workspace` is never a user-selected shared-storage path.

When `/storage/emulated/0` exists, it is optionally visible at the same path and `/sdcard` is created as an alias if that guest path is unused. The bind does not bypass Android storage policy. Android's All Files Access grant controls whether the app UID, and therefore guest processes, can use shared storage; granting or revoking it also affects already-running guests. Shared storage remains separate from managed workspaces and does not acquire Linux link, executable-bit, case, or package-manager semantics.

PRoot's host-only variables do not enter the guest. `/usr/bin/env -i` starts the requested command with only the variables assembled for its `GuestContext`. Terminal contexts let their login shell read the profile; non-terminal user contexts receive the bounded, cached login-profile environment plus noninteractive overrides; install and repair do not read the user profile.

## 5. Manifest, download, and readiness boundary

`app/src/main/assets/runtime/manifest.json` is the bundled pin authority. A schema- and architecture-compatible website manifest may become the selected authority according to `ManifestSource`; a refresh changes future installs or tool downloads and does not mutate an existing runtime.

The selected manifest supplies:

- the required ABI;
- rootfs identity, URL, SHA-256, minimum free space, and progress metadata;
- PRoot display/source metadata; and
- named archive and package-size metadata used by Runtime Tool installers.

PRoot and its loader are built into the APK; their manifest metadata does not make them runtime downloads. The Ubuntu rootfs and optional Runtime Tool archives are downloaded separately from their declared pins. Do not copy URLs, versions, sizes, or digests into this file.

For the rootfs, `RuntimeInstaller` validates the ABI and packaged executables, reserves the declared space, and accepts only a cached or newly downloaded archive whose SHA-256 matches the selected descriptor. Unverified or mismatched files are deleted. Android extracts the verified archive directly into the app-private rootfs, requires `bin/sh` and `usr/bin/apt`, configures managed files, and runs a guest health check. It writes `installed.json` and reports Ready only after that command succeeds.

## 6. Platform limits

PRoot translates paths and system calls. It is not a VM, container security boundary, or source of Android root. Fake uid 0 supports guest package tools but cannot bypass the app UID, SELinux, runtime permissions, other apps' storage, protected system data, mounting restrictions, kernel-module restrictions, or raw-device restrictions. A user-installed guest program receives the filesystem and network access of the iCode app UID.

Maintain these known constraints:

- Android apps cannot bind ports below 1024 and cannot read `/proc/net`.
- `--link2symlink` represents hard links with symlinks and hidden `.l2s.` files. Renaming a containing directory can break their absolute stand-ins. The local failure patch prevents data loss when shared storage refuses links; it does not make shared storage support links.
- Guest glibc restartable sequences remain disabled with `GLIBC_TUNABLES=glibc.pthread.rseq=0` because they are incompatible with PRoot tracing.
- Android can kill child processes under process pressure. Foreground-service and optional wake-lock handling reduce background risk but cannot preserve PTYs, PRoot processes, language servers, or managed work across app-process death.

## 7. Maintainer verification

For native or bootstrap changes:

1. Start from a recursive checkout with the pinned PRoot and libandroid-shmem submodules, tracked talloc source, JDK 17, NDK 27.0.12077973, CMake 3.22.1, and the host `patch` utility.
2. Confirm every repository patch applies cleanly to a build-tree copy of its pinned upstream revision. Do not patch submodule worktrees in place.
3. Assemble every affected variant. Native compilation and `check<Variant>PageAlignment` must both pass; linker flags alone are not sufficient evidence.
4. Run applicable tests and the current runtime acceptance matrix on an ARM64 API-30-or-newer device or emulator as described by the canonical build and runtime guides. Use only current procedures and evidence.
5. For a release, review the exact native revisions, patch set, manifest pins, downloaded artifacts, merged native outputs, licence texts, notices, source-offer obligations, and accompanying attribution. This review is not fully automated.
