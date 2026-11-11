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
#   store-owner        a SharedPreferences file or DataStore opened in more than one file,
#                      or under a name this guard cannot read (G1)
#   sha256-owner       SHA-256 computed outside data/io/Sha256.kt (G1)
#   byte-format        a byte count formatted outside domain/ByteSize.kt (G1)
#   notice-text        a notice put together where it is told, not by its area's builder in
#                      domain/notices/ (docs/notices-redesign-plan.md D4)
#   tm4e-owner         tm4e (org.eclipse.tm4e.*) or Sora's TextMate registries and theme models
#                      (io.github.rosemoe.sora.langs.textmate.registry.*) used outside ui/syntax/,
#                      the one owner of the TextMate engine (docs/syntax-highlighting-plan.md D2, A6).
#                      Sora's editor-facing TextMate classes in io.github.rosemoe.sora.langs.textmate
#                      itself (TextMateLanguage, TextMateColorScheme) are not covered: the editor
#                      holds the language ui/syntax/ gives it. Comment lines are skipped. Only
#                      app/src/main is checked, so tests may use tm4e.

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

# 6. One owner per store: two files opening the same preferences would be two writers of one
#    file. The name is the literal, or the file's own `const val NAME = "..."`.
declare -A store_files=()
while read -r file; do
    [[ -z "$file" ]] && continue
    while read -r arg; do
        if [[ "$arg" == \"* ]]; then
            name="${arg//\"/}"
        else
            name="$({ grep -oE "const val $arg = \"[^\"]+\"" "$file" || true; } | sed -E 's/.*= "(.*)"/\1/')"
        fi
        if [[ -z "$name" ]]; then
            emit "store-owner" "${file#"$repo_root/"}" 1
        else
            store_files[$name]+="${file#"$repo_root/"} "
        fi
    done < <(grep -oE '(getSharedPreferences|preferencesDataStore)\((name = )?[^,)]+' "$file" | sed -E 's/.*\((name = )?//')
done < <(grep -rlE 'getSharedPreferences\(|preferencesDataStore\(' "$src_root" --include='*.kt' | sort)
for name in "${!store_files[@]}"; do
    read -ra owners <<< "${store_files[$name]}"
    if (( ${#owners[@]} > 1 )); then
        for owner in "${owners[@]}"; do emit "store-owner" "$owner" "${#owners[@]}"; done
    fi
done

# 7. One digest: every stored fingerprint and key is Sha256's lowercase hex.
count_matches '"SHA-?256"|MessageDigest' "$src_root" | { grep -v '/data/io/Sha256\.kt ' || true; } | report "sha256-owner"

# 8. One byte vocabulary: sizes people read go through ByteSize ("MB" and "MiB" for one unit,
#    integer division, a limit typed beside its constant — all seen before G1).
#    Comments may quote an example.
{ grep -rEn '/ *\(?1024\b|"[^"]*([0-9]|\}) ?(KB|MB|GB|KiB|MiB|GiB)\b' "$src_root" --include='*.kt' 2>/dev/null || true; } \
    | { grep -vE '^[^:]+:[0-9]+:[[:space:]]*(\*|//|/\*)' || true; } \
    | { grep -v '/domain/ByteSize\.kt:' || true; } \
    | awk -F: '{print $1}' | sort | uniq -c | awk '{print $2 " " $1}' | report "byte-format"

# 9. A notice's words are written only in its area's builder (docs/notices-redesign-plan.md D4): a
#    Notice is made in domain/notices/, or read back by its store — never put together where it is
#    told. What shares the name is not a notice: an arrival's or a link's class, a notification's id.
{ grep -rEn '(^|[^A-Za-z.])Notice\(' "$src_root" --include='*.kt' 2>/dev/null || true; } \
    | { grep -vE '^[^:]+:[0-9]+:.*(class Notice\(|Notice\([0-9])' || true; } \
    | { grep -vE '/domain/notices/|/data/notices/NoticeStore\.kt:' || true; } \
    | awk -F: '{print $1}' | sort | uniq -c | awk '{print $2 " " $1}' | report "notice-text"

# 10. One owner of the TextMate engine: tm4e and Sora's TextMate registries only in ui/syntax/.
{ grep -rEn 'org\.eclipse\.tm4e\.|io\.github\.rosemoe\.sora\.langs\.textmate\.registry\.' "$src_root" --include='*.kt' 2>/dev/null || true; } \
    | { grep -vE '^[^:]+:[0-9]+:[[:space:]]*(\*|//|/\*)' || true; } \
    | { grep -v "^$src_root/ui/syntax/" || true; } \
    | awk -F: '{print $1}' | sort | uniq -c | awk '{print $2 " " $1}' | report "tm4e-owner"

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
