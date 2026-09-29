We have now finalized the direction. Do not continue with the previous storage plan as-is, because the architecture needs to be broader than “move node_modules to private storage.”

Our target is:

**The user experiences one project/workspace, while iCode internally uses multiple storage backends.**

The user's actual source/project files should remain in shared storage whenever technically possible, because they must remain directly accessible outside iCode through normal Android paths and other file managers/editors.

Example:

`/storage/emulated/0/Projects/RevPlayer`

That shared project location is the canonical source of truth for user-owned files. We do NOT want a full private mirror and we do NOT want two-way synchronization.

At the same time, files/directories that require real Linux filesystem semantics or generate very heavy filesystem I/O should live in iCode's app-private Linux-compatible storage.

Examples include dependencies, caches, temporary runtime data, generated build state, toolchains, and similar runtime-managed data.

The user must not have to understand or configure this split.

## Core architecture

Design iCode around a **stack-independent multi-backend WorkspaceManager / workspace abstraction**.

Conceptually:

```text
                         Logical Workspace
                    /workspace/RevPlayer
                              |
                    WorkspaceManager
                              |
             +----------------+----------------+
             |                                 |
        SharedBackend                     LinuxBackend
             |                                 |
      user-owned source                 runtime-managed data
      config/assets/etc.                deps/cache/build/tmp
             |                                 |
             +----------------+----------------+
                              |
                     Workspace Projection
                              |
              +---------------+---------------+
              |               |               |
          iCode UI        PRoot runtime   DocumentsProvider
```

The physical storage may differ, but the logical workspace presented by iCode should remain consistent.

Example logical tree:

```text
/workspace/RevPlayer
├── package.json
├── src/
├── public/
├── node_modules/
├── .next/
└── README.md
```

Possible physical backing:

```text
shared storage:
package.json
src/
public/
README.md

private Linux storage:
node_modules/
.next/
runtime caches/
temporary files/
toolchain state/
```

Inside iCode and inside the Linux runtime, this should feel like a single project tree.

## Important: do NOT design this around Node/npm

The foundation must not contain growing logic such as:

```text
if Node -> special handling
if Python -> special handling
if Gradle -> special handling
if Rust -> special handling
...
```

Instead define generic storage/runtime concepts such as:

```text
USER
LINUX
CACHE
TEMP
```

or an equivalent clean abstraction.

Later, stack/toolchain profiles may declaratively map paths into those storage classes.

Example only:

```text
Node:
node_modules -> LINUX
npm cache -> CACHE
.next/cache -> CACHE

Python:
.venv -> LINUX
__pycache__ -> CACHE

Gradle:
.gradle -> CACHE
build -> LINUX

Rust:
target -> LINUX
```

Profiles must sit on top of the workspace architecture, not define it.

## PRoot side

iCode already uses PRoot.

Research/prototype the simplest reliable way to expose the logical workspace to PRoot.

Start with existing PRoot bind/path projection capabilities.

The desired runtime experience is:

```bash
cd /workspace/RevPlayer
npm install
npm run dev
python ...
gradle ...
git ...
```

The tool should not need to know that some paths underneath `/workspace/RevPlayer` are physically in shared storage and some are in private storage.

Do NOT jump immediately to maintaining a custom PRoot fork.

First prove whether existing PRoot bind/projection capabilities are enough.

If normal bindings are fundamentally insufficient, document the exact limitation and only then investigate an iCode-specific PRoot path-routing extension/fork.

## iCode file explorer

The editor/file explorer should consume the same logical WorkspaceManager abstraction.

Do not independently implement one filesystem view for the editor and another for the runtime.

The project shown in iCode should be the merged logical workspace.

Runtime-backed/generated directories may optionally have subtle internal metadata, but normal users should simply see one project tree.

## Android Files / SAF

Investigate and prototype an Android `DocumentsProvider` backed by the same WorkspaceManager.

Goal:

```text
Android Files
└── iCode
    └── RevPlayer
        ├── src/
        ├── package.json
        ├── node_modules/
        └── ...
```

The provider may expose logical files/directories whose physical backing is either shared or private storage.

This is not intended to replace the user's shared path.

The user's source should still physically remain in shared storage whenever possible.

DocumentsProvider is an additional unified view for SAF-compatible applications and Android Files.

Do not assume direct-path apps like MT Manager can see private runtime directories through `/sdcard`. That is acceptable.

The key requirement is that user-owned source remains visible by normal shared-storage path.

## Do not use synchronization

Do NOT build:

```text
shared project <-> private complete copy
```

No two-way mirror.

We want one canonical copy of user source.

Avoid:

- synchronization conflicts
- stale copies
- duplicate storage
- delete/rename races
- watcher races
- conflict resolution between external editor changes and iCode changes

Generated/runtime data should simply have another backend.

## Linux compatibility fallback

We must acknowledge that Android shared storage cannot faithfully represent every possible Linux repository.

Examples:

- case-sensitive filename collisions such as `Foo.ts` and `foo.ts`
- real symlinks committed into a repository
- executable permission requirements
- other POSIX-specific filesystem semantics

Do not attempt to fake this silently.

Design a compatibility detector and preserve the ability to support a **Linux-native workspace fallback** where the entire repository lives in private Linux-compatible storage.

That should be an exceptional compatibility mode, not the normal user flow.

If such a project is used, it can still be exposed to Android through iCode's DocumentsProvider.

Do not ask normal users to choose between "shared", "hybrid", "POSIX", etc. during ordinary project creation.

iCode should automatically use the normal shared-source architecture and only surface compatibility issues when they actually exist.

## Background freeze is still priority #1

Before implementing the storage architecture, finish diagnosing and fixing the problem where iCode's active development command gets frozen when the application leaves the foreground.

We already observed Android putting the process into `__refrigerator`, with npm appearing stalled for very long periods.

Treat this as a separate foundational problem.

The runtime must not depend on the Activity/editor lifecycle.

Design the development runtime as a persistent, user-initiated session/process/service that owns:

- terminal processes
- npm/pnpm/yarn commands
- pip/python commands
- Gradle
- development servers
- future toolchains
- future agents

The UI should connect/reconnect to that runtime rather than own it.

Test scenarios must include at least:

- iCode active in foreground
- launcher opened
- another app foregrounded
- screen locked/off
- UI Activity recreated
- iCode UI process/activity restarted where applicable
- long-running install/build
- long-running localhost server

Do not simply assume battery-optimization exemption fixes this.

Measure the actual runtime behavior.

If Android/OEM restrictions make absolute continuation impossible in some scenarios, make that explicit and design the session state so the UI can accurately report:

- running
- exited
- interrupted
- killed

Never leave a dead process appearing as an indefinitely running/frozen command.

## Order of work

Follow this order:

1. Finish the background-freeze investigation and implement the correct runtime/session architecture.
2. Verify long-running commands continue correctly when iCode leaves the foreground.
3. Rewrite `docs/workspace-storage-plan.md` around this finalized multi-backend logical-workspace architecture.
4. Design the generic WorkspaceManager/backends and APIs.
5. Build a minimal proof-of-concept:
   - source file from shared storage
   - one private Linux-backed directory
   - both visible as one logical workspace
   - accessible from the PRoot runtime at one project path
6. Make iCode's file explorer consume the same logical workspace.
7. Prototype DocumentsProvider exposing the same logical tree.
8. Add filesystem compatibility detection/fallback design.
9. Only after the foundation is proven, design stack/toolchain profiles.

## Important engineering constraint

Do not make major implementation changes just because they sound theoretically correct.

For each foundational assumption, first create a minimal reproducible test/prototype on the actual device.

Especially verify:

- whether PRoot can reliably project a private directory over/into a project whose source is on shared storage
- file creation/rename/delete behavior across the logical boundary
- file watchers
- Git behavior
- cwd/path resolution
- build tools
- process lifetime/background behavior
- DocumentsProvider read/write/rename/delete behavior
- how iCode's explorer observes external changes to shared files

Document measured results.

## Desired end state

The normal user experience should remain:

```text
1. New Project / Clone / Open Folder
2. User selects:
   /storage/emulated/0/Projects/RevPlayer
3. User codes normally.
4. User runs npm/pip/gradle/etc.
5. iCode silently places runtime-sensitive/generated data where it belongs.
6. User can open their source from MT Manager/Files/other editors at the original shared path.
```

The user should never need to manually manage iCode's private runtime storage.

The guiding rule is:

**Shared storage is the user's project space. Private Linux storage is iCode's invisible execution infrastructure. Physical storage may differ, but iCode presents one logical workspace.**

First inspect the current implementation and the existing `docs/workspace-storage-plan.md`, then update the plan to match this architecture and proceed with the background-freeze fix before beginning the storage implementation.

Do not narrow the solution to RevPlayer, npm, Node, or the current test project. This is an iCode platform-foundation decision and must remain valid for future languages, toolchains, agents, build systems, and project types.
