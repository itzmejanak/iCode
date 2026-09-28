#!/usr/bin/env bash
# Backend consistency guard (code-separation plan S5.10; extended by step G).
#
# Same ratchet as tools/check-ui-consistency.sh: tools/backend-debt-allowlist.txt
# records the maximum allowed count per (rule, file). Counts may only shrink.
#
# Usage:
#   tools/check-backend-consistency.sh            # verify the current tree
#   tools/check-backend-consistency.sh --update   # rewrite the allowlist baseline
#
# Rules:
#   backend-lines      data/ and domain/ files above the 300-line review budget
#   apt-owner          apt-get outside RuntimePackageInstaller (one dpkg writer)
#   git-stderr-file    the old shared Git stderr file: every command has its own
#   workspace-git-cli  Git commands issued from data/workspace instead of a Git owner
#   screen-data-import screens or design system reaching into data/ past the gateway

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src_root="$repo_root/app/src/main/java/dev/icode/mobile"
allow_file="$repo_root/tools/backend-debt-allowlist.txt"
report_file="$(mktemp)"
trap 'rm -f "$report_file"' EXIT

emit() { printf '%s|%s|%s\n' "$1" "$2" "$3" >> "$report_file"; }

count_matches() { # pattern, root...  -> "file count" lines
    local pattern="$1"; shift
    { grep -rEn "$pattern" "$@" --include='*.kt' 2>/dev/null || true; } \
        | awk -F: '{print $1}' | sort | uniq -c | awk '{print $2 " " $1}'
}

report() { # rule, then "file count" lines on stdin
    local rule="$1" file count
    while read -r file count; do
        [[ -z "$file" ]] && continue
        emit "$rule" "${file#"$repo_root/"}" "$count"
    done
}

# 1. Backend files above the review budget.
while read -r file; do
    lines="$(wc -l < "$file")"
    if (( lines > 300 )); then
        emit "backend-lines" "${file#"$repo_root/"}" "$lines"
    fi
done < <(find "$src_root/data" "$src_root/domain" -type f -name '*.kt' | sort)

# 2. One package installer: dpkg has one database and one lock.
count_matches 'apt-get' "$src_root" | { grep -v '/RuntimePackageInstaller\.kt ' || true; } | report "apt-owner"

# 3. No shared stderr file: two commands at once overwrote each other's errors.
count_matches 'icode-git-stderr' "$src_root" | report "git-stderr-file"

# 4. Git runs through its owners (data/git), never straight from the workspace layer.
count_matches 'gitCli\.(run|runIn|runScript)\(' "$src_root/data/workspace" | report "workspace-git-cli"

# 5. Screens and the design system see the gateway contract, not data/.
count_matches '^import dev\.icode\.mobile\.data\.' "$src_root/ui/screens" "$src_root/designsystem" | report "screen-data-import"

if [[ "${1:-}" == "--update" ]]; then
    {
        echo "# Backend debt allowlist for tools/check-backend-consistency.sh."
        echo "# Ratchet: cycles may only shrink these counts, never grow them."
        echo "# Format: rule|path|max"
        sort "$report_file"
    } > "$allow_file"
    echo "Updated $allow_file ($(grep -c '|' "$allow_file" || true) entries)."
    exit 0
fi

if [[ ! -f "$allow_file" ]]; then
    echo "Missing $allow_file. Run tools/check-backend-consistency.sh --update first." >&2
    exit 1
fi

fail=0
while IFS='|' read -r rule path count; do
    [[ -z "$rule" ]] && continue
    allowed="$(awk -F'|' -v r="$rule" -v p="$path" '$1 == r && $2 == p { print $3 }' "$allow_file" | head -1)"
    if [[ -z "$allowed" ]]; then
        printf 'new violation:      %s  %s (%s)\n' "$rule" "$path" "$count" >&2
        fail=1
    elif (( count > allowed )); then
        printf 'regression:         %s  %s (%s > %s)\n' "$rule" "$path" "$count" "$allowed" >&2
        fail=1
    fi
done < "$report_file"

if (( fail )); then
    echo "Backend consistency check failed. Fix the regression; only use --update to record a smaller baseline." >&2
    exit 1
fi

echo "Backend consistency check passed."
