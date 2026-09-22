#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
adb_bin="${ANDROID_HOME:-/home/revdev/Android/Sdk}/platform-tools/adb"
catalog_file="$repo_root/app/src/main/java/dev/icode/mobile/navigation/DemoScreen.kt"
apk_file="$repo_root/app/build/outputs/apk/debug/app-debug.apk"
output_dir="${1:-$repo_root/artifacts/screens}"
review_dir="${2:-$repo_root/artifacts/review}"
package_name="dev.icode.mobile.demo.debug"
activity_name="dev.icode.mobile.MainActivity"

if [[ ! -x "$adb_bin" ]]; then
    echo "ADB was not found at $adb_bin" >&2
    exit 1
fi

if ! "$adb_bin" get-state >/dev/null 2>&1; then
    echo "No authorized Android device is connected." >&2
    exit 1
fi

mkdir -p "$output_dir"
"$adb_bin" install -r "$apk_file" >/dev/null

mapfile -t screens < <(
    sed -nE 's/^    ([A-Za-z0-9]+)\("[^"]+", "([^"]+)",.*$/\1|\2/p' "$catalog_file"
)

if [[ "${#screens[@]}" -ne 40 ]]; then
    echo "Expected 40 registered screens, found ${#screens[@]}." >&2
    exit 1
fi

index=1
for entry in "${screens[@]}"; do
    enum_name="${entry%%|*}"
    title="${entry#*|}"
    slug="$(printf '%s' "$title" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9' '-' | sed 's/^-//; s/-$//')"
    filename="$(printf '%02d-%s.png' "$index" "$slug")"
    device_file="/sdcard/icode-$index.png"

    "$adb_bin" shell am force-stop "$package_name"
    "$adb_bin" shell am start -W \
        -n "$package_name/$activity_name" \
        --es demo_screen "$enum_name" \
        --ez demo_static true >/dev/null
    # XOS animates the status bar after a cold activity launch. Waiting here
    # keeps system insets and status icons consistent across the full set.
    sleep 2
    "$adb_bin" shell screencap -p "$device_file"
    "$adb_bin" pull "$device_file" "$output_dir/$filename" >/dev/null
    "$adb_bin" shell rm "$device_file"
    printf '[%02d/40] %s\n' "$index" "$filename"
    index=$((index + 1))
done

echo "Captured 40 screens in $output_dir"
python3 "$repo_root/tools/build-screenshot-contact-sheets.py" "$output_dir" "$review_dir"
