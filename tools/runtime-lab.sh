#!/usr/bin/env bash
# The PRoot facts iCode's runtime contract stands on (docs/runtime-and-terminal.md), checked
# against PRoot built from the pinned third_party/proot commit with iCode's patches, on this Linux
# host: a test root filesystem borrows the host's /usr and /etc, and each check starts PRoot the way
# RuntimeLayout does.
#
# Usage: tools/runtime-lab.sh [work-dir]      (default: build/runtime-lab; needs git, gcc, make, bash)
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mkdir -p "${1:-$repo/build/runtime-lab}" && cd "${1:-$repo/build/runtime-lab}" && pwd)
pinned=$(git -C "$repo" ls-tree HEAD third_party/proot | awk '{print $3}')
proot="$work/proot-$pinned"

# PRoot, once per pinned commit: termux's source, iCode's patches, the vendored talloc.
if [ ! -x "$proot" ]; then
    src="$work/src-$pinned"
    if [ ! -d "$src/.git" ]; then
        git init -q "$src"
        git -C "$src" fetch -q --depth 1 https://github.com/termux/proot.git "$pinned"
        git -C "$src" checkout -q FETCH_HEAD
        for patch in "$repo"/third_party/patches/proot/*.patch; do git -C "$src" apply "$patch"; done
    fi
    talloc="$repo/third_party/talloc"
    gcc -c -O1 -fPIC -include limits.h -I"$talloc" -D_GNU_SOURCE -D__STDC_WANT_LIB_EXT1__=1 \
        -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=3 \
        "$talloc/talloc.c" -o "$work/talloc.o"
    ar rcs "$work/libtalloc.a" "$work/talloc.o"
    # Android's limits.h defines ARG_MAX, glibc's does not; the host needs no 32-bit loader.
    make -s -C "$src/src" HAS_LOADER_32BIT= CFLAGS="-I$talloc -include limits.h -DARG_MAX=131072 -O2 -w" \
        LDFLAGS="-L$work -ltalloc -Wl,-z,noexecstack" proot >/dev/null
    cp "$src/src/proot" "$proot"
fi

# A fresh test runtime: the root filesystem and the workspaces, as iCode's files/ holds them.
lab="$work/lab"
rm -rf "$lab"
mkdir -p "$lab/rootfs/root" "$lab/rootfs/tmp" "$lab/rootfs/workspace" "$lab/rootfs/proc" "$lab/rootfs/dev" \
    "$lab/workspaces/app" "$lab/workspaces/other" "$lab/tmp"
for link in bin lib lib64 sbin; do ln -s "usr/$link" "$lab/rootfs/$link"; done
echo "hello" > "$lab/workspaces/other/README.md"

# PRoot's own variables, as RuntimeLayout.hostEnvironment gives them, and a guest command, as
# RuntimeLayout.prootCommand builds one: `env -i` with the context's variables.
guest() {
    env -i LD_LIBRARY_PATH=/data/app/dev.icode.mobile/lib/arm64 PROOT_TMP_DIR="$lab/tmp" PROOT_VERBOSE=-1 \
        "$proot" --link2symlink -0 -r "$lab/rootfs" -b /usr -b /etc -b /proc -b /dev "$@"
}
whole() { guest -b "$lab/workspaces:/workspace" "$@"; }

failures=0
check() {
    local name=$1 expected=$2 actual=$3
    if [ "$expected" = "$actual" ]; then
        echo "ok    $name"
    else
        echo "FAIL  $name"
        echo "      expected: $expected"
        echo "      actual:   $actual"
        failures=$((failures + 1))
    fi
}

# Guest environment isolation: the guest has exactly the variables it is given; PRoot's never reach it.
check "the guest's environment is exactly env -i's" \
    "HOME=/root|ICODE_CONTEXT=command|PATH=/usr/bin:/bin" \
    "$(whole /usr/bin/env -i HOME=/root ICODE_CONTEXT=command PATH=/usr/bin:/bin /usr/bin/env | sort | paste -sd'|')"
check "env -i finds a bare command on the new PATH (language servers start so)" \
    "found" \
    "$(whole /usr/bin/env -i PATH=/usr/bin:/bin bash -c 'echo found')"

# Login environment snapshot, as GuestEnvironment.script reads it.
snapshot="exec '/bin/bash' -l -i -c '/usr/bin/printf '\''\\000icode-environment\\000'\''; /usr/bin/env -0' </dev/null"
check "the snapshot's marker comes before the variables" \
    "icode-environment" \
    "$(whole /usr/bin/env -i HOME=/root PATH=/usr/bin:/bin HISTFILE=/dev/null /bin/sh -c "{ $snapshot
}" 2>/dev/null | tr '\0' '\n' | sed -n 2p)"

# Shared workspace tree: one tree for every launch, so every launch sees writes under /workspace.
whole /usr/bin/env -i PATH=/usr/bin:/bin /bin/sh -c 'echo probe > /workspace/other/probe.txt'
check "a file one launch writes in a workspace is on the phone where the Explorer reads it" \
    "probe" "$(cat "$lab/workspaces/other/probe.txt")"
check "and the next launch reads the same file" \
    "probe" "$(whole /usr/bin/env -i PATH=/usr/bin:/bin /bin/cat /workspace/other/probe.txt)"

# Guest path link behavior, as GuestPaths follows links.
whole /usr/bin/env -i PATH=/usr/bin:/bin /bin/sh -c 'ln -s /workspace/other/README.md /root/readme; echo data > /root/a; ln /root/a /root/b'
check "a link made in the runtime keeps the runtime path it names" \
    "/workspace/other/README.md" "$(readlink "$lab/rootfs/root/readme")"
stand_in=$(readlink "$lab/rootfs/root/b")
check "PRoot's stand-in for a hard link names the phone's path of its file" \
    "under $lab/rootfs/root/" "$([[ $stand_in == "$lab/rootfs/root/.l2s."* ]] && echo "under $lab/rootfs/root/" || echo "$stand_in")"

echo
if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
fi
echo "every check holds"
