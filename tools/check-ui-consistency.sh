#!/usr/bin/env bash
# UI consistency guard.
#
# Enforces the zero-duplication / zero-inconsistency rules of
# docs/engineering-rules.md with a ratchet: tools/ui-debt-allowlist.txt records the
# maximum allowed violation count per (rule, file). Counts may only shrink.
#
# Usage:
#   tools/check-ui-consistency.sh            # verify the current tree
#   tools/check-ui-consistency.sh --update   # rewrite the allowlist baseline
#
# Rules:
#   raw-color    Color(0x…) literals outside designsystem/IcodeColors.kt and designsystem/palettes/ (docs/design-and-navigation.md)
#   fq-androidx  fully-qualified androidx.* references inside ui/
#   dp-literal   hard-coded dp values inside ui/screens
#   sp-literal   hard-coded sp values inside ui/screens
#   ui-lines     ui/ files larger than the 300-line budget (screens, the app root,
#                editor and terminal views alike)
#   capture-fork captureMode branches inside ui/screens (capture data belongs
#                in the debug-only CaptureFixtures, not in product screens)
#   orphan-route Route values with no navigate(...) entry point
#   toast        android.widget.Toast anywhere: what iCode says is a notice, on the
#                bell, the Notifications page and Android's shade (docs/engineering-rules.md)
#   dialog       a Compose or Android dialog anywhere: iCode asks and confirms in sheets
#                (IcodeConfirmSheet, IcodeModalSheet; owner's request, 2026-11-04)

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src_root="$repo_root/app/src/main/java"
ui_root="$src_root/dev/icode/mobile/ui"
allow_file="$repo_root/tools/ui-debt-allowlist.txt"
report_file="$(mktemp)"
trap 'rm -f "$report_file"' EXIT

emit() { printf '%s|%s|%s\n' "$1" "$2" "$3" >> "$report_file"; }

count_matches() { # pattern, root
    local pattern="$1" root="$2"
    { grep -rEn "$pattern" "$root" --include='*.kt' 2>/dev/null || true; } \
        | grep -v -e '/IcodeColors\.kt:' -e '/designsystem/palettes/' \
        | awk -F: '{print $1}' \
        | sort | uniq -c \
        | awk '{print $2 " " $1}'
}

# 1. Raw color literals outside the token file.
while read -r file count; do
    [[ -z "$file" ]] && continue
    emit "raw-color" "${file#"$repo_root/"}" "$count"
done < <(count_matches 'Color\(0x' "$src_root")

# 2. Fully-qualified androidx references inside ui/ (import lines excluded).
while read -r file count; do
    [[ -z "$file" ]] && continue
    emit "fq-androidx" "${file#"$repo_root/"}" "$count"
done < <(
    { grep -rEn 'androidx\.' "$ui_root" --include='*.kt' 2>/dev/null || true; } \
        | grep -v ':import ' \
        | awk -F: '{print $1}' \
        | sort | uniq -c \
        | awk '{print $2 " " $1}'
)

# 3. + 4. Hard-coded dp / sp values inside screens.
while read -r file count; do
    [[ -z "$file" ]] && continue
    emit "dp-literal" "${file#"$repo_root/"}" "$count"
done < <(count_matches '[0-9]+\.dp' "$ui_root/screens")

while read -r file count; do
    [[ -z "$file" ]] && continue
    emit "sp-literal" "${file#"$repo_root/"}" "$count"
done < <(count_matches '[0-9]+\.sp' "$ui_root/screens")

# 4a. Toasts anywhere in the app: none is allowed.
while read -r file count; do
    [[ -z "$file" ]] && continue
    emit "toast" "${file#"$repo_root/"}" "$count"
done < <(count_matches 'android\.widget\.Toast|Toast\.makeText' "$src_root")

# 4b. Dialogs anywhere in the app: none is allowed.
while read -r file count; do
    [[ -z "$file" ]] && continue
    emit "dialog" "${file#"$repo_root/"}" "$count"
done < <(count_matches 'androidx\.compose\.ui\.window\.Dialog|androidx\.compose\.material3\.(Basic)?AlertDialog|android\.app\.AlertDialog|androidx\.appcompat\.app\.AlertDialog|DialogFragment' "$src_root")

# 5a. Capture forks inside product screens.
#
# Capture fixtures live in app/src/debug (CaptureFixtures), never in a screen.
# The allowlisted uses are capture-timing gates that only stop a screen from
# moving on by itself, never choose content.
while read -r file count; do
    [[ -z "$file" ]] && continue
    emit "capture-fork" "${file#"$repo_root/"}" "$count"
done < <(count_matches 'actions\.captureMode' "$ui_root/screens")

# 5b. Routes with no navigation entry point.
#
# A route reachable only by an ADB extra is a frame the user can never open.
# Every Route value must be the target of a navigate(...) call somewhere
# outside the route declaration, or be allowlisted with a reason.
route_file="$src_root/dev/icode/mobile/navigation/Route.kt"
while read -r route; do
    [[ -z "$route" ]] && continue
    # An edge is any reference outside the route declaration and the host
    # dispatch tables: a navigate(...) call, a catalog entry, or an effect.
    edges="$(
        { grep -rEl "Route\\.$route\\b" "$src_root" --include='*.kt' 2>/dev/null || true; } \
            | grep -v '/Route\.kt$' \
            | grep -vc 'ScreenHost\.kt$' || true
    )"
    if (( edges == 0 )); then
        emit "orphan-route" "$route" "1"
    fi
done < <(sed -n '/^enum class Route(/,/^}/p' "$route_file" | grep -oE '^    [A-Z][A-Za-z]*\(' | tr -d ' (')

# 5. ui/ files above the 300-line budget.
while read -r file; do
    [[ -z "$file" ]] && continue
    lines="$(wc -l < "$file")"
    if (( lines > 300 )); then
        emit "ui-lines" "${file#"$repo_root/"}" "$lines"
    fi
done < <(find "$ui_root" -type f -name '*.kt' 2>/dev/null | sort)

if [[ "${1:-}" == "--update" ]]; then
    {
        echo "# UI debt allowlist for tools/check-ui-consistency.sh."
        echo "# Ratchet: cycles may only shrink these counts, never grow them."
        echo "# Format: rule|path|max"
        sort "$report_file"
    } > "$allow_file"
    echo "Updated $allow_file ($(grep -c '|' "$allow_file") entries)."
    exit 0
fi

if [[ ! -f "$allow_file" ]]; then
    echo "Missing $allow_file. Run tools/check-ui-consistency.sh --update first." >&2
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
    echo "UI consistency check failed. Fix the regression; only use --update to record a smaller baseline." >&2
    exit 1
fi

echo "UI consistency check passed."
