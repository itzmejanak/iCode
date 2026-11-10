#!/usr/bin/env python3
"""Import Material Icon Theme's file and folder icons into iCode (docs/file-kinds-icons-plan.md D5).

Usage:
    tools/import-file-icons.py PKief.material-icon-theme-<version>.vsix

The .vsix is the release VS Code installs, from Open VSX
(https://open-vsx.org/extension/PKief/material-icon-theme). This writes, replacing what was there:

    app/src/main/assets/file-icons/theme.json        the theme's names, extensions, folders and language ids
    app/src/main/assets/file-icons/icons/<id>.svg    every icon the theme uses, as released
    app/src/main/resources/licenses/MIT-Material-Icon-Theme.txt

Nothing is drawn or changed by hand: the theme is data, read as VS Code reads it. Keys are lower case,
as VS Code matches them. Every folder icon has one open icon (checked here), so the theme lists each
folder name once, and each folder icon's open icon once (`open`).
"""

import json
import os
import shutil
import sys
import zipfile

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "app", "src", "main", "assets", "file-icons")
LICENCE = os.path.join(REPO, "app", "src", "main", "resources", "licenses", "MIT-Material-Icon-Theme.txt")
SECTIONS = ("fileExtensions", "fileNames", "folderNames", "languageIds")


def lower_keys(section, mapping):
    """The mapping with lower-case keys; two keys that differ only in case must agree."""
    out = {}
    for key, icon in mapping.items():
        low = key.lower()
        if low in out and out[low] != icon:
            sys.exit(f"{section}: '{key}' and another spelling map to {out[low]} and {icon}")
        out[low] = icon
    return dict(sorted(out.items()))


def main(vsix):
    with zipfile.ZipFile(vsix) as archive:
        theme = json.loads(archive.read("extension/dist/material-icons.json"))
        package = json.loads(archive.read("extension/package.json"))
        definitions = theme["iconDefinitions"]

        light_theme = theme.get("light", {})
        light = {s: lower_keys("light." + s, light_theme.get(s, {})) for s in SECTIONS}
        sections = {s: lower_keys(s, theme[s]) for s in SECTIONS}
        opens = {theme["folder"]: theme["folderExpanded"]}
        for names, expanded in ((sections["folderNames"], theme["folderNamesExpanded"]),
                                (light["folderNames"], light_theme.get("folderNamesExpanded", {}))):
            expanded = lower_keys("folderNamesExpanded", expanded)
            for name, icon in names.items():
                if name not in expanded or opens.setdefault(icon, expanded[name]) != expanded[name]:
                    sys.exit(f"folder '{name}' ({icon}) has no single open icon")
        used = {theme["file"]} | set(opens) | set(opens.values())
        for mappings in (sections, light):
            used |= {icon for s in SECTIONS for icon in mappings[s].values()}
        missing = sorted(icon for icon in used if icon not in definitions)
        if missing:
            sys.exit(f"icons the theme names but does not define: {missing[:10]}")

        icons = os.path.join(ASSETS, "icons")
        shutil.rmtree(icons, ignore_errors=True)
        os.makedirs(icons)
        for icon in sorted(used):
            path = os.path.normpath(os.path.join("extension/dist", definitions[icon]["iconPath"]))
            with open(os.path.join(icons, icon + ".svg"), "wb") as out:
                out.write(archive.read(path.replace(os.sep, "/")))

        shipped = {
            "source": f"Material Icon Theme {package['version']} (MIT): " + package["repository"]["url"].removeprefix("git+").removesuffix(".git"),
            "file": theme["file"],
            "folder": theme["folder"],
            "open": dict(sorted(opens.items())),
            **sections,
            "light": {s: light[s] for s in SECTIONS if light[s]},
        }
        with open(os.path.join(ASSETS, "theme.json"), "w", encoding="utf-8") as out:
            json.dump(shipped, out, separators=(",", ":"), ensure_ascii=False)
            out.write("\n")
        os.makedirs(os.path.dirname(LICENCE), exist_ok=True)
        with open(LICENCE, "wb") as out:
            out.write(archive.read("extension/LICENSE.txt"))
    print(f"Material Icon Theme {package['version']}: {len(used)} icons, "
          + ", ".join(f"{len(sections[s])} {s}" for s in SECTIONS))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
