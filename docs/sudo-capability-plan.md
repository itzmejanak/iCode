# iCode `sudo` — a managed capability boundary

Status: **plan** (2026-09-23). Follows [backend-split-rules.md](./backend-split-rules.md) §8.
Companion to [integration-plan.md](./integration-plan.md) §9 (runtime lifecycle).

## 1. The problem, as measured

PRoot takes `--link2symlink` once per session, and the two filesystems a
terminal touches want opposite settings. Measured on device, inside a terminal
with a workspace bound:

```
TOUCH-OK
ln: failed to create hard link '/var/lib/dpkg/zz-link' => '/var/lib/dpkg/status': Permission denied
HARDLINK-FAIL
SYMLINK-OK
```

| | rootfs (`/data`, ext4) | workspace (`/sdcard`, FUSE) |
|---|---|---|
| hard links | denied by Android → needs `--link2symlink` | not applicable |
| symlinks, rename | work | symlinks unsupported; the flag breaks `rename` |

So with the flag **off** the user's real files behave and `git` works, but
`apt`/`dpkg` cannot back up `/var/lib/dpkg/status` and fail. With it **on**,
`apt` works and `git clone` cannot finalise a pack on shared storage. Both
failures were reproduced on device.

Mobile-Harness meets the same wall — *"DSH uses POSIX hard links … Android
blocks that syscall inside PRoot"* — but sidesteps it by keeping workspaces in
app-private storage (`File(filesDir, "workspaces/$projectId")`). iCode cannot:
opening the user's real files in place is the product promise stated on the
permission screen, and the direct-path invariant in architecture.md §26.

## 2. The design

One terminal. No Project/System shell split, no pretence that the device is
rooted. `sudo` becomes an **iCode-controlled capability word**: commands run in
the project context by default, and `sudo <command>` runs that command through
the runtime context where package management works, returning its real output
and exit code to the same terminal.

- Familiar to anyone who has used Linux.
- Honest: it is not root, and the failure text says so.
- A boundary that can widen deliberately, one supported operation at a time,
  rather than a general "run anything on the host" hole.

## 3. Mechanism

A bridge directory, app-private, bound into the guest — the pattern
Mobile-Harness already proves (`-b <bridge>:/pocket-bridge`, polled request and
response files).

| Side | Responsibility |
|---|---|
| `/usr/local/bin/sudo` in the rootfs | Writes `<id>.request` with argv and cwd, streams `<id>.out` to its stdout as it grows, exits with `<id>.exit` |
| `SudoBridge` on the host | Watches the bridge, decides whether the request is supported, runs supported ones in a PRoot session with **no workspace bound** (so `--link2symlink` is on), streams output back, records the exit code |

The guest never chooses the flag; the host owns the boundary. An unsupported
request is refused with an explanation, not silently run.

## 4. Ownership

| Fact | Owner |
|---|---|
| Which operations `sudo` may run | `domain` — a pure, tested policy |
| Parsing a request and rendering a refusal | `data/runtime` — pure, tested |
| Running the command in the system context | `data/runtime`, through the existing PRoot command builder |
| Showing the hint in the terminal | `ui` — presentation only |

No new module, no DI framework. The policy is pure so it can be tested without
Android, which §4 of the rulebook asks for before any adapter work.

## 5. Slices

| Slice | Deliverable | Acceptance |
|---|---|---|
| S1 | Pure request parsing and the supported-operation policy | Unit tests, including refusals and argv edge cases |
| S2 | `SudoBridge` host watcher plus the guest `sudo` script; real streamed output and exit code | On device: `sudo apt install git` installs git and prints apt's own output |
| S3 | The hint: a failure that needed privileges explains itself and names the `sudo` line to rerun | On device: bare `apt install git` fails with the hint |
| S4 | Onboarding provisioning (P5) uses the same host path; docs and cleanup | Fresh install has git without visiting Source Control |

## 6. Lifecycle (rulebook §5)

- **Enter**: any terminal, any workspace. No new route.
- **Load**: the bridge directory is created with the session and cleared on start.
- **Act**: one request, one response; the request names argv and cwd.
- **React**: output streams to the same terminal that asked.
- **Interrupt**: terminal closed or app killed mid-request — the host abandons
  the run and the guest script exits non-zero rather than hanging; a stale
  request left by a dead session is ignored and removed on the next start.
- **Recover**: a refused or failed request prints why, and the terminal is
  usable immediately afterwards.
- **Demonstrate**: the demo gateway needs no runtime and is unaffected.

## 7. Security

`sudo` is not root and must never become "run any host command". The policy is
an allowlist of operations iCode supports; everything else is refused with the
reason. The bridge lives in app-private storage, so only this app and its guest
can use it — the same trust domain as the user's own shell. No credential,
token or path from the credential store is readable through it.

## 8. Known debt

A user's own `git` inside a `sudo` session would still see the flag on. `sudo`
is for package management, and the refusal text says so rather than pretending
otherwise.

## 9. Verified on device (S2)

`sudo` works end to end. Observed in the terminal:

```
superpowers $ sudo whoami
sudo in iCode runs package management, not arbitrary commands as root — this
device is not rooted. Supported: apt, apt-cache, apt-get, apt-mark, dpkg.
```

The round trip is real: the guest wrote the request, the host decided, and the
refusal came back to the same terminal with the prompt returning after it.
`sudo apt install -y git` reached the network and ran dpkg, which is the part
that could never work in a workspace-bound session.

## 10. Found while verifying: an interrupted package operation poisons the runtime

The first `apt upgrade` was interrupted by the hard-link failure this plan
exists to fix. That left dpkg mid-transaction, and the runtime could not
recover on its own:

```
Unpacking perl-base (5.38.2-3.2ubuntu0.6) over (5.38.2-3.2ubuntu0.4) ...
dpkg: error ... unable to stat './usr/bin/perl' (which was about to be
installed): Operation not permitted
E: Sub-process /usr/bin/dpkg returned an error code (1)
```

`/usr/bin/perl` was gone from the root filesystem entirely, so every later
`apt install` — `git`, and even `tree` — failed at the same point, because apt
completes pending work before anything new. `sudo dpkg --configure -a` could
not repair it either.

This is damage, not a design limit: apt installed git successfully in a healthy
root filesystem earlier in the same session. But it shows a real gap in the
lifecycle — **a runtime can reach a state the user cannot get out of**, and
today the only cure is deleting the root filesystem by hand.

**Follow-up slice (S5): a repair path.** Settings needs "Reinstall runtime",
and the failed state of the install surface should offer it, so a poisoned
runtime is one deliberate tap to fix rather than a dead end. The install screen
already reports failure honestly; it cannot yet act on this one.
