#!/usr/bin/env python3
"""Import the editor's TextMate grammars into iCode's language catalog (docs/syntax-highlighting-plan.md §4.1).

Usage:
    tools/import-grammars.py

Every source is pinned to one commit (below), so a rerun writes the same files. Set
ICODE_GRAMMAR_CACHE=<folder> to keep the downloads between runs. This writes, replacing what was there:

    app/src/main/assets/textmate/<folder>/...      each grammar and language configuration, as released
    app/src/main/assets/textmate/catalog.json      one line per language (domain/syntax/Languages.kt)
    docs/editor-grammar-provenance.md              the tables under "Shipped grammars"
    app/src/test/resources/syntax-samples/shiki/   Shiki's sample of each language with no sample of iCode's
                                                   own in syntax-samples/icode/ (SyntaxSamplesTest)

What ships, in this order (the first source to declare a scope wins):

1. Sora Editor 0.24.6's grammars the editor started with (Java, Kotlin, Python, XML, HTML, JavaScript,
   Markdown), and YAML from VS Code 337cacaa (docs/editor-grammar-provenance.md says why).
2. Every grammar of VS Code 307b60e7's built-in extensions.
3. Every grammar of Shiki's tm-grammars (textmate-grammars-themes 8f1828eb) whose scope VS Code lacks,
   once its licence is known (Shiki's index, else LICENCES below, checked in the upstream repository).
4. A language whose Shiki grammar has no known licence, from the maintained upstream GitHub Linguist
   vendors (UPSTREAM below), and the grammars Linguist vendors for the formats the icon theme names that
   neither VS Code nor Shiki covers, and for scopes shipped grammars include. Sora's engine reads JSON and
   property lists, so a grammar written in CSON or YAML is converted to JSON (cson_loads, yaml_loads).

A grammar no file type opens is shipped only when a shipped grammar includes it (a helper such as
`source.cpp.embedded.macro`). Injection grammars are not shipped: Sora reads no injections.

Which files a language opens ("files" in the catalog), each name or extension given to one language only,
the first to claim it: VS Code's `contributes.languages` (extensions, filenames, filenamePatterns,
firstLine); for a language VS Code lacks, its upstream VS Code extension's `contributes.languages`, then its
grammar's own `fileTypes`; then GitHub Linguist's languages.yml (LINGUIST, the languages of the same
`tm_scope` or name: extensions, filenames, interpreters). Where only languages VS Code lacks claim a name or
an extension, the one the icon theme draws it as wins. A pattern is kept when it is a name (`compose.yaml`),
a name's beginning (`Dockerfile.*`) or an extension (`*.env`); patterns on a path (`**/.git/config`) are
not, as the Explorer never lists those folders. The claims that lost are listed in the provenance doc.

Nothing is changed by hand except the files in KEPT, which are left as they are (iCode's own Dockerfile and
.env grammars among them), and the edits in EDITS;
grammars and configurations Sora's engine cannot read whole are changed as rules_only and
readable_configuration say, and listed in the provenance doc.
"""

import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "app", "src", "main", "assets")
TEXTMATE = os.path.join(ASSETS, "textmate")
CATALOG = os.path.join(TEXTMATE, "catalog.json")
PROVENANCE = os.path.join(REPO, "docs", "editor-grammar-provenance.md")
# One sample file per language for the tests (SyntaxSamplesTest): iCode's own in icode/, else Shiki's in shiki/.
SAMPLES = os.path.join(REPO, "app", "src", "test", "resources", "syntax-samples")

SORA = "0.24.6"
VSCODE = "307b60e7aba9fe6c2dec8cae5970561b126f807b"
VSCODE_YAML = "337cacaafa0248ef6db264f52971d283b40aa94d"
SHIKI = "8f1828ebccc57930cf0adde9b35ae929584fe191"
LINGUIST = "d456527fe93355baa28c3c29d542c97282b61bc7"

RAW = "https://raw.githubusercontent.com"
SORA_RAW = f"{RAW}/Rosemoe/sora-editor/{SORA}/app/src/main/assets/textmate"
SHIKI_RAW = f"{RAW}/shikijs/textmate-grammars-themes/{SHIKI}/packages/tm-grammars"

SOURCES = {
    "sora": {"name": f"Sora Editor {SORA}", "url": f"https://github.com/Rosemoe/sora-editor/tree/{SORA}/app/src/main/assets/textmate", "licence": "LGPL-2.1-or-later"},
    "vscode-337cacaa": {"name": "VS Code 337cacaa", "url": f"https://github.com/microsoft/vscode/tree/{VSCODE_YAML}", "licence": "MIT"},
    "vscode": {"name": "VS Code 307b60e7", "url": f"https://github.com/microsoft/vscode/tree/{VSCODE}", "licence": "MIT"},
    "shiki": {"name": "Shiki tm-grammars 8f1828eb", "url": f"https://github.com/shikijs/textmate-grammars-themes/tree/{SHIKI}/packages/tm-grammars", "licence": "MIT"},
}

# Sora's grammars: id, scope, folder, grammar, configuration, what it embeds, licence (its upstream's, or Sora's).
SORA_LANGUAGES = [
    ("java", "source.java", "java", "syntaxes/java.tmLanguage.json", "language-configuration.json", None, "MIT"),
    ("kotlin", "source.kotlin", "kotlin", "syntaxes/Kotlin.tmLanguage", "language-configuration.json", None, "LGPL-2.1-or-later"),
    ("python", "source.python", "python", "syntaxes/python.tmLanguage.json", "language-configuration.json", None, "MIT"),
    ("xml", "text.xml", "xml", "syntaxes/xml.tmLanguage.json", "language-configuration.json", None, "MIT"),
    ("html", "text.html.basic", "html", "syntaxes/html.tmLanguage.json", "language-configuration.json", {"source.js": "javascript"}, "TextMate Bundle License"),
    ("javascript", "source.js", "javascript", "syntaxes/JavaScript.tmLanguage.json", "language-configuration.json", None, "MIT"),
    ("markdown", "text.html.markdown", "markdown", "syntaxes/markdown.tmLanguage.json", "language-configuration.json", None, "MIT"),
]

# Grammars VS Code ships but iCode does not, and why.
VSCODE_SKIPPED = {
    "source.c.platform": "568 KB of platform constants that no grammar includes",
    "source.yaml": "VS Code 337cacaa's YAML grammar ships instead (see YAML below)",
}

# Ids for grammars VS Code gives no language of their own (or one another grammar already has).
GRAMMAR_IDS = {
    "source.php": "php-source", "source.cpp.embedded.macro": "cpp-embedded-macro", "source.sassdoc": "sassdoc",
    "text.html.derivative": "html-derivative", "source.js.regexp": "js-regexp", "source.regexp.python": "regexp-python",
    "markdown.math.block": "markdown-math-block", "markdown.math.inline": "markdown-math-inline",
    "markdown.math.codeblock": "markdown-math-codeblock", "source.yaml.1.0": "yaml-1.0", "source.yaml.1.1": "yaml-1.1",
    "source.yaml.1.2": "yaml-1.2", "source.yaml.1.3": "yaml-1.3", "source.yaml.embedded": "yaml-embedded",
    "text.html.markdown.prompt": "prompt",
}

# A language whose configuration comes from somewhere else than its own contributes.languages entry.
CONFIGURATIONS = {
    # Sora's JavaScript configuration, as the editor had it before VS Code's JSX grammar was added.
    "javascriptreact": "textmate/javascript/language-configuration.json",
    # VS Code's YAML configuration at 307b60e7, beside 337cacaa's grammar.
    "yaml": "textmate/yaml/language-configuration.json",
    # A grammar only other grammars include; HTML's configuration is Sora's, in the same folder.
    "html-derivative": None,
}

# Licences of Shiki grammars whose index gives none ('?', NOASSERTION, or a family such as 'GNU'), as each
# upstream repository states it at the pinned commit (checked 2026-11-11): the file or field that says so.
LICENCES = {
    "abap": ("TextMate Bundle License", "README.md"),
    "ahk2": ("LGPL-3.0", "LICENSE"),
    "apache": ("BSD-2-Clause", "LICENSE"),
    "apex": ("BSD-3-Clause", "LICENSE"),
    "apl": ("ISC OR MIT", "package.json licenses"),
    "applescript": ("TextMate Bundle License", "README.mdown"),
    "asciidoc": ("MIT", "LICENSE"),
    "cue": ("MIT", "LICENSE"),
    "elixir": ("Apache-2.0", "LICENSE"),
    "erb": ("TextMate Bundle License", "README.mdown"),
    "hurl": ("MIT", "package.json license"),
    "kusto": ("Apache-2.0", "kusto-syntax-highlighting/package.json license"),
    "llvm": ("Apache-2.0 WITH LLVM-exception", "llvm/LICENSE.TXT"),
    "logo": ("TextMate Bundle License", "README.mdown"),
    "luau": ("MIT", "LICENSE.md"),
    "matlab": ("BSD-2-Clause", "license.txt"),
    "mipsasm": ("TextMate Bundle License", "README.mdown"),
    "nim": ("MIT", "LICENSE"),
    "nsis": ("MIT", "LICENSE"),
    "po": ("TextMate Bundle License", "README.mdown"),
    "purescript": ("MIT", "LICENSE.md"),
    "rel": ("MIT", "README.md"),
    "sass": ("MIT", "LICENSE"),
    "sparql": ("Apache-2.0", "stardog-rdf-grammars/package.json license"),
    "ssh-config": ("TextMate Bundle License", "README.mdown"),
    "toml": ("TextMate Bundle License", "README.mdown"),
    "turtle": ("Apache-2.0", "stardog-rdf-grammars/package.json license"),
    "wolfram": ("MIT", "LICENSE"),
    # Made by Shiki itself, under Shiki's licence.
    "ts-tags": ("MIT", "Shiki's LICENSE"),
}

# Grammars from the maintained upstream repositories GitHub Linguist vendors (linguist d456527f,
# vendor/grammars, grammars.yml; licences from vendor/licenses/git_submodule): for a Shiki grammar whose
# upstream states no licence (glsl, tcl, dream-maker), and for the text formats the icon theme names that
# neither VS Code nor Shiki has a grammar for. Sora's engine reads JSON and property lists only, so a grammar
# in CSON or YAML is converted to JSON. id: scope, repository, commit (Linguist's submodule), grammar path,
# licence.
UPSTREAM = {
    "glsl": ("source.glsl", "euler0/sublime-glsl", "59a5f8a3120358657cefdc4b830b4a883ebfbf77", "GLSL.tmLanguage", "Unlicense"),
    "tcl": ("source.tcl", "textmate/tcl.tmbundle", "f06f80144c01ff1e3836cef21cbac62f8e1c5df6", "Syntaxes/Tcl.plist", "TextMate Bundle License"),
    "dream-maker": ("source.dm", "spacestation13/dm-syntax", "a63b4388d0df050d40f2c6c6afd85637174abbb5", "syntaxes/dm.tmLanguage.json", "MIT"),
    "abap-cds": ("source.abapcds", "FreHu/abap-cds-grammar", "dd803e6ad2eea202b537a87c0da0536b92f60d3e", "syntaxes/cds.tmLanguage.json", "Unlicense"),  # ABAP CDS
    "antlr": ("source.antlr", "textmate/antlr.tmbundle", "7f07385abbdbd716ebb5b15ad8190347554192cf", "Syntaxes/ANTLR.plist", "TextMate Bundle License"),  # ANTLR
    "apiblueprint": ("text.html.markdown.source.gfm.apib", "apiaryio/api-blueprint-sublime-plugin", "0a90eff10e71a8f18974410307b3b86c71afb3ed", "APIBlueprint.tmLanguage", "MIT"),  # API Blueprint
    "autoit": ("source.autoit", "AutoIt/SublimeAutoItScript", "2b2e950c7edb47da76d335fb205593bc83987a19", "AutoIt.tmLanguage", "MIT"),  # AutoIt
    "browserslist": ("text.browserslist", "browserslist/browserslist-vscode", "e40e7efcb19ae08622510959cfb8a9bfc8378c91", "syntaxes/browserslist.tmLanguage.json", "MIT"),  # Browserslist
    "bru": ("source.bru", "usebruno/bruno-ide-extensions", "0f92df1617cf90fe50430a0e76fac55853a398b9", "packages/vscode/syntaxes/bruno.tmLanguage.json", "MIT"),  # Bru
    "caddyfile": ("source.Caddyfile", "caddyserver/vscode-caddyfile", "9ba30fcec02e257776188ca66ed47fc210e86b26", "syntaxes/caddyfile.tmLanguage.json", "MIT"),  # Caddyfile
    "capnp": ("source.capnp", "textmate/capnproto.tmbundle", "9fab42eb8a28b6138b8c11ce841efe10962b852e", "Syntaxes/Cap’n Proto.tmLanguage", "TextMate Bundle License"),  # Cap'n Proto
    "cds": ("source.cds", "SAP/cds-textmate-grammar", "d5389523709ba010cf1b9de78c6c0478bab31bde", "syntaxes/cds.tmLanguage.json", "Apache-2.0"),  # CAP CDS
    "cfml": ("text.html.cfm", "SublimeText/ColdFusion", "dbe2f76038fe77fe90b4cc4a0651aa817f14f1a3", "HTML+CFML.tmLanguage", "MIT"),  # ColdFusion
    "cfscript": ("source.cfscript", "SublimeText/ColdFusion", "dbe2f76038fe77fe90b4cc4a0651aa817f14f1a3", "Cfscript.tmLanguage", "MIT"),  # ColdFusion CFC
    "dot": ("source.dot", "textmate/graphviz.tmbundle", "d1d489f893a0e6ef5d9021bf7dc1ffd08b3f9b90", "Syntaxes/DOT.plist", "TextMate Bundle License"),  # Graphviz (DOT)
    "dune": ("source.dune", "ocamllabs/vscode-ocaml-platform", "71aa5bc671bd5685a3f7d0faeb9dafe9d3666de5", "syntaxes/dune.json", "ISC"),  # Dune
    "eex": ("text.html.elixir", "elixir-lang/elixir-tmbundle", "eb63f1a5da138634cff0caf3d777ba147b47670d", "Syntaxes/HTML (EEx).tmLanguage", "Apache-2.0"),  # HTML+EEX
    "ejs": ("text.html.js", "gregory-m/ejs-tmbundle", "5e48fff7258a94a8f57b142fa38571a1f9f68c44", "Syntaxes/JavaScript Template.tmLanguage", "MIT"),  # EJS
    "forth": ("source.forth", "textmate/forth.tmbundle", "0cf0ec91f65717903b41ea6cd62f6cd17788f0d0", "Syntaxes/Forth.tmLanguage", "TextMate Bundle License"),  # Forth
    "gemini": ("source.gemini", "printfn/gemini-vscode", "72ef757a0cb5d1e7137edd97579a92588f7e54cb", "syntaxes/gemini.tmLanguage.json", "MIT"),  # Gemini
    "go-mod": ("go.mod", "golang/vscode-go", "28e27ffd3a0eca1de30b1a9656a75cbd47e94e4e", "extension/syntaxes/go.mod.tmGrammar.json", "MIT"),  # Go Workspace
    "go-sum": ("go.sum", "golang/vscode-go", "28e27ffd3a0eca1de30b1a9656a75cbd47e94e4e", "extension/syntaxes/go.sum.tmGrammar.json", "MIT"),  # Go Checksums
    "icalendar": ("source.iCalendar", "kimsey0/iCalendar-sublime", "ee1aaa8a96cc72c45be5dbbb02ec8f994a414e23", "iCalendar.tmLanguage", "MIT"),  # iCalendar
    "idris": ("source.idris", "idris-hackers/idris-sublime", "c3a9f006d8f717c3092fdc89f559710fbe4fa6f7", "Idris.tmLanguage", "MIT"),  # Idris
    "kcl": ("source.kcl", "kcl-lang/vscode-kcl", "4eb7b1457f52deab3fe7d903525ae8a41748bc1b", "syntaxes/KCL.tmLanguage.json", "Apache-2.0"),  # KCL
    "kivy": ("source.python.kivy", "p0lygun/kivy-language-grammer", "d6ab305f30432a4603c547e92ab517504aaf4baf", "grammers/kivy.tmLanguage", "MIT"),  # kvlang
    "livescript": ("source.livescript", "sharktide/livescript-vscode", "04b9ae5dc9f50caff43bcb1f09b66e093c8e8ba5", "syntaxes/livescript.tmLanguage.json", "Apache-2.0"),  # LiveScript
    "lolcode": ("source.lolcode", "KrazIvan/LOLCODE-grammar-vscode", "2091c60ebe0b20e169853e5a0385b4bfb54c86f0", "syntaxes/lolcode.tmLanguage.json", "MIT"),  # LOLCODE
    "mcfunction": ("source.mcfunction", "MinecraftCommands/syntax-mcfunction", "cb179b5cad5d5add8557f32bc77f2a714f409879", "mcfunction.tmLanguage.json", "MIT"),  # mcfunction
    "mint": ("source.mint", "mint-lang/mint-vscode", "9b0c8b54f80c9e32bb479ae8737b2c68ec275dd4", "syntaxes/mint.tmLanguage.json", "MIT"),  # Mint
    "moonscript": ("source.moonscript", "leafo/moonscript-tmbundle", "cf69300f2a38d4c585bef4c5ad8543f5e258de8d", "Syntaxes/MoonScript.tmLanguage", "MIT"),  # MoonScript
    "pawn": ("source.pawn", "Southclaw/pawn-sublime-language", "b83dab341b1da7af10937a61d49349009c30f1ee", "Pawn.tmLanguage", "MIT"),  # Pawn
    "peggy": ("source.peggy", "peggyjs/code-peggy-language", "41bc8b61364fc7c9f532852b872b35ae2508b834", "syntaxes/peggy.json", "MIT"),  # PEG.js
    "pip-requirements": ("source.pip-requirements", "microsoft/vscode-python", "e0f1d3c80da21e0246b007990f6513fed9017080", "syntaxes/pip-requirements.tmLanguage.json", "MIT"),  # Pip Requirements
    "powerbuilder": ("source.powerbuilder", "informaticon/PowerBuilder.tmbundle", "054c6f747f135465da5d889b283c55547837639a", "Syntaxes/PowerBuilder.tmLanguage.json", "MIT"),  # PowerBuilder
    "processing": ("source.processing", "textmate/processing.tmbundle", "214b3420f12d65b125e82dc039d580326257f71b", "Syntaxes/Processing.plist", "TextMate Bundle License"),  # Processing
    "procfile": ("source.procfile", "benspaulding/vscode-procfile", "4e149fd5e757352aa5867b59934acc16e19182a1", "syntaxes/procfile.tmLanguage.json", "BSD-3-Clause"),  # Procfile
    "reason": ("source.reason", "ocamllabs/vscode-ocaml-platform", "71aa5bc671bd5685a3f7d0faeb9dafe9d3666de5", "syntaxes/reason.json", "ISC"),  # Reason
    "rebol": ("source.rebol", "Oldes/Sublime-REBOL", "eb06bfce0c612be9be0db24c472ada725204d380", "Rebol.tmLanguage", "MIT"),  # Rebol
    "red": ("source.red", "Oldes/Sublime-Red", "fb0c69926e2f966dab413f1c34a05f376317a319", "Red.tmLanguage", "MIT"),  # Red
    "rego": ("source.rego", "open-policy-agent/vscode-opa", "f5efd999e33d8f94629520d4cccb35e768f785a2", "syntaxes/Rego.tmLanguage", "Apache-2.0"),  # Open Policy Agent
    "rescript": ("source.rescript", "rescript-lang/rescript-vscode", "f29d6a09b69d73c7759ca18adddcfe26b7c1f6d0", "grammars/rescript.tmLanguage.json", "MIT"),  # ReScript
    "riot": ("text.html.riot", "riot/syntax-highlight", "6ffd29ce303d73f2a838621beecc56525965c3b3", "riot.tmlanguage", "MIT"),  # Riot
    "robotframework": ("text.robot", "shellderp/sublime-robot-plugin", "07069ebf20c82663eee24e4c7d82cccca020e8e9", "robot.tmLanguage", "Apache-2.0"),  # RobotFramework
    "slang": ("source.slang", "shader-slang/slang-vscode-extension", "b03da15f7475f53fd24389601dc774b32a4b89d0", "syntaxes/slang.tmLanguage.json", "Apache-2.0"),  # Slang
    "slim": ("text.slim", "borama/vscode-ruby-slim", "098d948ef94014231fbcfc821633b4cb76760860", "syntaxes/slim.tmLanguage.json", "MIT"),  # Slim
    "smali": ("source.smali", "ShaneWilton/sublime-smali", "36add49df8c7d8dde1d5cf0d68c4098183b2714f", "smali.tmLanguage", "MIT"),  # Smali
    "smarty": ("text.html.smarty", "textmate/php-smarty.tmbundle", "3888046662241e02f1a7a39eb14bc4844acc082e", "Syntaxes/Smarty.plist", "TextMate Bundle License"),  # Smarty
    "sml": ("source.ml", "textmate/standard-ml.tmbundle", "b52a5ebf528f89f19bead3f92b10f772ccda74aa", "Syntaxes/Standard ML.plist", "TextMate Bundle License"),  # Standard ML
    "smt": ("source.smt", "SRI-CSL/SMT.tmbundle", "426d5f7e0a5303fedfff2465d30165dcb4fae675", "Syntaxes/smt.tmLanguage", "Unlicense"),  # SMT
    "stan": ("source.stan", "stan-dev/atom-language-stan", "366d1f6408005b84527965069957fa0d7baef35b", "grammars/language-stan.json", "MIT"),  # Stan
    "sugarss": ("source.css.postcss.sugarss", "hudochenkov/Syntax-highlighting-for-PostCSS", "575b918985ce82545a12615a244243e41a91cd52", "Syntaxes/SugarSS.tmLanguage", "MIT"),  # SugarSS
    "tape": ("source.vhs", "griimick/vscode-vhs", "8e1da07699e0e9c716a0b608a1b62297b86f28a6", "syntaxes/vhs.tmLanguage.json", "MIT"),  # Tape
    "teal": ("source.teal", "teal-language/vscode-teal", "13802a7f6b16248c82762fdd10ed9de3f737f3bf", "syntaxes/teal.tmLanguage.json", "MIT"),  # Teal
    "uiua": ("source.uiua", "uiua-lang/uiua-vscode", "333e5b12b27a9981f3e9028e7ebd5165b178e60a", "syntaxes/uiua.tmLanguage.json", "MIT"),  # Uiua
    "vcl": ("source.vcl", "fastly/vscode-fastly-vcl", "01f09f5392d82d3544fe33e086d5fd718083d4c6", "syntaxes/vcl.tmLanguage.json", "MIT"),  # VCL
    "verse": ("source.verse", "simnJS/verse-grammar", "3abf18ec6e8d9fcbbb15be8183cb5b2cb6a621b8", "grammars/verse.tmLanguage.json", "MIT"),  # Verse
    # Grammars Linguist vendors only in CSON or YAML, read by cson_loads and yaml_loads below.
    "agc": ("source.agc", "Alhadis/language-agc", "6fb398ee1110d65f4c92c7644e8e6d896dc43300", "grammars/agc.cson", "ISC"),  # Apollo Guidance Computer
    "cabal": ("source.cabal", "atom-haskell/language-haskell", "a273201bd1ffd518a7700fb883d68b8fb60bcbf3", "grammars/cabal.cson", "MIT"),  # Cabal Config
    "literate-haskell": ("text.tex.latex.haskell", "atom-haskell/language-haskell", "a273201bd1ffd518a7700fb883d68b8fb60bcbf3", "grammars/literate haskell.cson", "MIT"),  # Literate Haskell
    "liquid-haskell": ("annotation.liquidhaskell.haskell", "atom-haskell/language-haskell", "a273201bd1ffd518a7700fb883d68b8fb60bcbf3", "grammars/liquid haskell.cson", "MIT"),  # Liquid Haskell, in Literate Haskell
    "etc": ("etc", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/etc.cson", "ISC"),  # patterns language-etc's grammars share
    "checksums": ("text.checksums", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/checksums.cson", "ISC"),  # Checksums
    "gitconfig": ("source.gitconfig", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/gitconfig.cson", "ISC"),  # Git Config
    "hosts": ("source.hosts", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/hosts.cson", "ISC"),  # Hosts File
    "m3u": ("source.m3u", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/m3u.cson", "ISC"),  # M3U
    "mailmap": ("source.mailmap", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/mailmap.cson", "ISC"),  # Git .mailmap
    "opts": ("source.opts", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/opts.cson", "ISC"),  # Option List
    "shellcheckrc": ("source.shellcheckrc", "Alhadis/language-etc", "6aa793bd649151119bd946673f8f027f0a9c99c8", "grammars/shellcheckrc.cson", "ISC"),  # ShellCheck Config
    "lex": ("source.lex", "Alhadis/language-grammars", "daa8548cc820359b078192dc85b6c8845e9db54c", "grammars/lex.cson", "ISC"),  # Lex
    "lex-regexp": ("source.lex.regexp", "Alhadis/language-grammars", "daa8548cc820359b078192dc85b6c8845e9db54c", "grammars/lex-regexp.cson", "ISC"),  # Lex's patterns
    "postscript": ("source.postscript", "Alhadis/Atom-PostScript", "8f5309cc78f76b1ce5f3e817d3749d5ee64d5212", "grammars/postscript.cson", "ISC"),  # PostScript
    "srt": ("text.srt", "Alhadis/language-subtitles", "9acc0aa2c232a7b06aa0e8c1058ee5be81a98837", "grammars/srt.cson", "ISC"),  # SubRip Text
    "vtt": ("text.vtt", "Alhadis/language-subtitles", "9acc0aa2c232a7b06aa0e8c1058ee5be81a98837", "grammars/vtt.cson", "ISC"),  # WebVTT
    "firestore": ("source.firestore", "jaysquared/atom-firestore-grammar", "750ec2abef42f7061c6bcc29ed5db3b0a1f3f773", "grammars/firestore.cson", "MIT"),  # Cloud Firestore Security Rules
    "denizenscript": ("source.denizenscript", "DenizenScript/denizenscript-grammar", "a9a95232026192c0d5eb23557882f9b69a40d66e", "grammars/denizenscript.cson", "MIT"),  # DenizenScript
    "eml": ("text.eml.basic", "mariozaizar/language-eml", "e92b829c1e68710f1e2d5c039642e3a8ff06db6d", "grammars/language-eml.cson", "MIT"),  # E-mail
    "ecr": ("text.html.ecr", "atom-crystal/language-crystal", "48b87f541ad787ffab5d4a4a1d366f5db16bca1a", "grammars/html (crystal - ecr).cson", "MIT"),  # HTML+ECR
    "lilypond": ("source.lilypond", "nwhetsell/linter-lilypond", "eca5b0f91b959d598b601c3dcdf2194a6b1e8e79", "grammars/lilypond.cson", "MIT"),  # LilyPond
    "macaulay2": ("source.m2", "Macaulay2/language-macaulay2", "e3d0db2a52545b4ce04dd531fd73360bd1407adb", "grammars/macaulay2.cson", "MIT"),  # Macaulay2
    "meson": ("source.meson", "TingPing/language-meson", "90269533120e84a9f5882346a8d646619e606b39", "grammars/meson.cson", "Apache-2.0"),  # Meson
    "nunjucks": ("text.html.nunjucks", "alohaas/language-nunjucks", "d865b2a667c53122e749b7d9698f1b2ac0258d98", "grammars/html (nunjucks templates).cson", "MIT"),  # Nunjucks
    "nunjucks-source": ("source.nunjucks", "alohaas/language-nunjucks", "d865b2a667c53122e749b7d9698f1b2ac0258d98", "grammars/nunjucks templates.cson", "MIT"),  # Nunjucks' tags
    "velocity": ("source.velocity", "animecyc/AtomLanguageVelocity", "6eb92de5a24991192192d768241be12337015884", "grammars/velocity.cson", "MIT"),  # Velocity Template Language
    "xquery": ("source.xq", "wcandillon/language-jsoniq", "89a3515410d82ef32995a1d713ece09bd15eb840", "grammars/xquery.cson", "Apache-2.0"),  # XQuery
    "jsoniq": ("source.jsoniq", "wcandillon/language-jsoniq", "89a3515410d82ef32995a1d713ece09bd15eb840", "grammars/jsoniq.cson", "Apache-2.0"),  # JSONiq
    "yang": ("source.yang", "DzonyKalafut/language-yang", "c2d4a701d15e0ea7a1cef4f2fe4396b336504d7c", "grammars/yang.cson", "MIT"),  # YANG
    "harbour": ("source.harbour", "hernad/atom-language-harbour", "ae860b3b02d08bd65827c0a5f0029e65e4df30b4", "grammars/harbour.cson", "MIT"),  # xBase (Harbour)
    "jisonlex": ("source.jisonlex", "cdibbs/language-jison", "ce8e2c78279b242a95b87e830a60dee55357aedc", "grammars/jisonlex.cson", "MIT"),  # Jison Lex
    "plantuml": ("source.wsd", "qjebbs/vscode-plantuml", "7bc1758ed73dc269f5721d78c6c6c01f461d7cb0", "syntaxes/plantuml.yaml-tmLanguage", "MIT"),  # PlantUML
    "slint": ("source.slint", "slint-ui/slint-tmLanguage", "45bd6116b344fb2eebdf506fbda32a5cc57590a3", "Slint.YAML-tmLanguage", "MIT"),  # Slint
    "sln": ("source.solution", "Nixinova/NovaGrammars", "c6dfb955f070c5aaf3ca7f6ee8d0b1802c6aed95", "grammars/solution.yaml-tmLanguage", "ISC"),  # Microsoft Visual Studio Solution
    "robots-txt": ("text.robots-txt", "Nixinova/NovaGrammars", "c6dfb955f070c5aaf3ca7f6ee8d0b1802c6aed95", "grammars/robots-txt.yaml-tmLanguage", "ISC"),  # robots.txt
    # Grammars shipped grammars include, which Linguist vendors.
    "jflex": ("source.jflex", "jflex-de/jflex.tmbundle", "8b5c0cefc7672ec4d0a5f6d9095256252120fa44", "Syntaxes/JFlex.tmLanguage", "BSD-2-Clause"),  # JFlex
    "agda": ("source.agda", "agda/agda-github-syntax-highlighting", "ba5005841ff9bdc5186af68befd58ed456fc7a8c", "Agda.tmLanguage", "MIT"),  # Agda
    "lean4-markdown": ("source.lean4.markdown", "leanprover/vscode-lean4", "5a25e6abb2e973b4c89a053acc74c479c0bb2e9f", "vscode-lean4/syntaxes/lean4-markdown.json", "Apache-2.0"),  # Lean 4 doc comments
    "regexp": ("source.regexp", "Alhadis/language-regexp", "2e240580b7e7046718b2c495d418f3ded972d618", "grammars/regexp.cson", "ISC"),  # Regular expressions
    "regexp-posix": ("source.regexp.posix", "Alhadis/language-regexp", "2e240580b7e7046718b2c495d418f3ded972d618", "grammars/regexp-posix.cson", "ISC"),  # POSIX regular expressions
    "regexp-extended": ("source.regexp.extended", "Alhadis/language-regexp", "2e240580b7e7046718b2c495d418f3ded972d618", "grammars/regexp-extended.cson", "ISC"),  # Extended regular expressions
    "syon": ("source.sy", "Alhadis/language-regexp", "2e240580b7e7046718b2c495d418f3ded972d618", "grammars/syon.cson", "ISC"),  # SyON
    "x86": ("source.x86", "calculuswhiz/Assembly-Syntax-Definition", "519b7b3c5f8428832dde72a90041d0707f831e5c", "Assembly x86 AT&T.tmLanguage", "MIT"),  # x86 assembly, AT&T syntax
    "cfml-basic": ("text.cfml.basic", "SublimeText/ColdFusion", "dbe2f76038fe77fe90b4cc4a0651aa817f14f1a3", "Coldfusion.tmLanguage", "MIT"),  # CFML tags
    "eex-text": ("text.elixir", "elixir-lang/elixir-tmbundle", "eb63f1a5da138634cff0caf3d777ba147b47670d", "Syntaxes/EEx.tmLanguage", "Apache-2.0"),  # EEx
    "javadoc": ("text.html.javadoc", "textmate/javadoc.tmbundle", "5276d7a93f0cf53b7d425c39c6968b09ea9f2d40", "Syntaxes/JavaDoc.tmLanguage", "TextMate Bundle License"),  # Javadoc
    "mson": ("text.html.markdown.source.gfm.mson", "apiaryio/api-blueprint-sublime-plugin", "0a90eff10e71a8f18974410307b3b86c71afb3ed", "MSON.tmLanguage", "MIT"),  # MSON
    "python-console": ("text.python.console", "MagicStack/MagicPython", "7d0f2b22a5ad8fccbd7341bc7b7a715169283044", "grammars/python-console.cson", "MIT"),  # Python sessions
    "openesql": ("source.openesql", "spgennard/vscode_cobol", "8be1f9a91a4a25c847ca2fe9dcc4793ead8bc347", "syntaxes/openesql.tmLanguage.json", "MIT"),  # SQL in COBOL
}

# Shiki grammars not shipped, and why.
SHIKI_SKIPPED = {
    "dax": "no licence: huyza/dax-language states none at 7fd39aa8, and no other DAX grammar is known",
}

# Languages iCode opens with another grammar than VS Code's: HTML with Sora's `text.html.basic`, which VS
# Code's `text.html.derivative` (shipped for the grammars that include it) wraps.
SCOPES = {"html": "text.html.basic"}

# File types whose id is not their language's: VS Code's `shellscript` stays iCode's `shell`.
TYPE_IDS = {"shellscript": "shell"}

# A file type's family where Linguist's kind (programming, markup, data, prose) does not say iCode's.
FAMILIES = {
    "xml": "code", "xsl": "code", "sql": "code", "diff": "document", "git-commit": "document", "git-rebase": "document",
    "csv": "document", "tsv": "document", "ignore": "data", "dotenv": "data", "properties": "data", "jsonc": "data",
    "jsonl": "data", "json5": "data", "log": "document", "markdown": "document", "dockercompose": "data",
    "mailmap": "data",
}
KINDS = {"programming": "code", "markup": "code", "data": "data", "prose": "document"}

# First lines whose VS Code expression is too wide: JavaScript's `\bnode` also matches `ts-node`, TypeScript's.
FIRST_LINES = {"javascript": r"^#!.*\b(?<!-)node"}

# iCode's own claims, after every source's, for formats the icon theme names that no source gives a
# language: type id, extensions, names, why. Taken only where nothing claimed them first.
ICODE_FILES = [
    ("xml", "tmlanguage plist manifest project jrxml dmn xml.dist.sample bcf vbox vbox-prev drawio dio", "",
     "XML formats: TextMate grammars and property lists, Windows manifests, Eclipse projects, JasperReports, DMN, BibLaTeX control files, VirtualBox machines, draw.io diagrams"),
    ("yaml", "yml.dist yaml.dist", "", "YAML kept as a template to copy"),
    ("json", "jsbeautifyrc esformatter whitesource tldr excalidraw", "", "JSON formats: js-beautify and esformatter settings, WhiteSource settings, tldraw and Excalidraw drawings"),
    ("ini", "hgrc coafile", "hgrc", "INI formats: Mercurial's settings, coala's settings"),
    ("dotenv", "vars", ".dev.vars", "Cloudflare Workers' variables, in .env's syntax"),
    ("latex", "latex tikz clo bbl", "", "LaTeX source: documents, TikZ pictures, class options, BibTeX's output"),
    ("handlebars", "mustache", "", "Mustache templates, which Handlebars extends"),
    ("jinja", "jinja-html", "", "Jinja HTML templates"),
    ("markdown", "rmd qmd", "", "R Markdown and Quarto documents: Markdown with code chunks"),
    ("csharp", "csharp", "", "C# source"),
    ("nginx", "nginxconfig", "", "nginx settings"),
    ("ruby", "vagrantfile", "", "a Vagrantfile, which is Ruby"),
    ("groovy", "jenkins", "", "a Jenkins pipeline, which is Groovy"),
    ("python", "snakemake", "", "a Snakemake workflow, which is Python"),
    ("gnuplot", "gpi", "", "gnuplot scripts"),
    ("sql", "plpgsql psql", "", "PostgreSQL's SQL"),
    ("xml", "cpn pnml dfxp ttml vcxitems ruleset sln.dotsettings.user", "",
     "XML formats: Petri nets, timed text subtitles, Visual Studio item and rule sets, ReSharper settings"),
    ("json", "slnf mjs.map cjs.map", ".vsconfig .swift-format .graphqlconfig .firebaserc .mjmlconfig .ecrc .luaurc .parcelrc .stackblitzrc",
     "JSON formats: Visual Studio solution filters and components, source maps, and tools' settings written in JSON only"),
    ("ini", "wrap coarc", ".mrconfig .hgflow .sentryclirc", "INI formats: Meson wraps, coala, myrepos, hg-flow and Sentry settings"),
    ("gdresource", "godot gdextension", "", "Godot's project and extension files, in the syntax of its resources"),
    ("tcl", "exp", "", "Expect scripts, which are Tcl"),
    ("viml", "gvimrc exrc", "", "Vim settings, which are Vim script"),
    ("javascript", "js.snap cjs.snap mjs.snap jsx.snap ts.snap mts.snap cts.snap tsx.snap", ".sequelizerc",
     "Jest snapshots and Sequelize's settings, which are JavaScript modules"),
    ("python", "bazel", "scsub", "Bazel and SCons build files, which are Starlark and Python"),
    ("jinja", "djt", "", "Django templates, whose tags are Jinja's"),
    ("toml", "pdm.lock", ".replit", "TOML formats: PDM's lock file, Replit's settings"),
    ("terraform", "tfbackend", "", "Terraform backend settings, which are HCL"),
    ("cfml", "lucee", "", "Lucee, which is CFML"),
    ("angular-html", "ng-template", "", "Angular templates"),
    ("apiblueprint", "apiblueprint", "", "API Blueprint"),
    ("abap-cds", "acds", "", "ABAP CDS"),
    ("yaml", "", ".snyk", "Snyk's policy, which is YAML"),
    ("procfile", "", "procfile.windows", "a Procfile for Windows"),
    ("pkl", "", "pklproject", "a Pkl project, which is Pkl"),
    ("cabal", "", "cabal.project.freeze cabal.project.local", "Cabal's project files, which are in Cabal's syntax"),
    ("meson", "", "meson.options", "Meson's options, the newer name of meson_options.txt"),
    ("checksums", "shasum", "", "shasum's output, a list of checksums"),
    ("shellcheckrc", "", "shellcheckrc", "ShellCheck's settings without the dot, as it also reads them"),
    ("slint", "60", "", "Slint's former extension, from when it was SixtyFPS"),
    ("properties", "", ".gitattributes-global .gitattributes_global", "global Git attributes, as VS Code reads .gitattributes"),
    ("ignore", "", ".gitignore-global", "a global .gitignore"),
]
# Also iCode's: a dot file the icon theme names whose name ends in `ignore` (`.dockerignore`, `.helmignore`) is a
# list of paths in .gitignore's syntax, as VS Code's own `.npmignore` and `.gitignore` are.
IGNORE_SUFFIX = "ignore"

# Names and extensions no language is given: FileTypes reads them as formats of other bytes.
NOT_TEXT = {
    ("extension", "wasm"): "a WebAssembly module is bytes (FileTypes.Executable); its text form is `.wat`",
    ("extension", "txt"): "plain text, though Linguist also gives it to Robot Framework",
    ("extension", "pdf"): "a PDF is bytes (FileTypes.Pdf), though the PostScript grammar claims it",
    ("extension", "ai"): "an Illustrator drawing is bytes, though the PostScript grammar claims it",
    ("extension", "aia"): "an Illustrator action is bytes, though the PostScript grammar claims it",
    ("extension", "pfb"): "a binary Type 1 font, though the PostScript grammar claims it",
    ("extension", "eps"): "an EPS picture is shown as a picture, though it is PostScript",
    ("extension", "epsf"): "an EPS picture is shown as a picture, though it is PostScript",
    ("extension", "epsi"): "an EPS picture is shown as a picture, though it is PostScript",
    ("extension", "sum"): "no one format: `go.sum` is Go's (go-sum), and the checksum grammar's other names stay its own",
    ("name", "authors"): "a list of a project's authors is prose, though the Git mailmap grammar claims it",
}

# Copied files left as they are: edited for the editor's engine (docs/editor-grammar-provenance.md).
KEPT = {
    "textmate/markdown/syntaxes/markdown.tmLanguage.json": "bold and italic split into literal-end rules (Sora back-reference bug)",
    "textmate/docker/syntaxes/docker.tmLanguage.json": (
        "iCode's grammar, written from moby's (which colours only instruction names and quoted strings): every part of an "
        "instruction is scoped (the image, its tag and digest, the stage name, `--flag=value` options, ARG, ENV and LABEL "
        "keys and values, EXPOSE ports and protocols, `$VAR` and `${VAR:-default}`, parser directives, heredocs, the exec "
        "form's JSON array), and the shell form of RUN, CMD, ENTRYPOINT and HEALTHCHECK CMD, and RUN heredocs, embed "
        "`source.shell`"),
    "textmate/dotenv/syntaxes/dotenv.tmLanguage.json": (
        "iCode's grammar, written from dotenv-org's (which reads each line with one pattern): a value may be unquoted, "
        "single-, double- or backtick-quoted, and quoted values may span lines; an unquoted number is a number and "
        "`true` or `false` a constant; `$VAR`, `${VAR}` and `${VAR:-default}` are variables, in unquoted and "
        "double-quoted values; a ` #` after an unquoted value starts a comment"),
}


def make_line_comment(text):
    """tm4e's parser reads `lineComment` only as a string; VS Code's form is an object with `noIndent`."""
    return re.sub(r'"lineComment":\s*\{\s*"comment":\s*"#",\s*"noIndent":\s*true\s*\}', '"lineComment": "#"', text)


def numbers(scope):
    """An edit that gives a grammar with no rule for numbers one, last among its top-level patterns: a number
    not inside a name (`x1`, `$a2`, `v1.2`), with its decimal parts and an exponent."""
    rule = {"match": r"(?<![\w$])\d+(?:\.\d+)*(?:[eE][-+]?\d+)?\b", "name": f"constant.numeric.{scope}"}

    def edit(text):
        if text.lstrip().startswith("<"):  # a property list: the rule goes first, as its array's end is not marked
            entry = f"<dict><key>match</key><string>{rule['match'].replace('<', '&lt;')}</string><key>name</key><string>{rule['name']}</string></dict>"
            return re.sub(r"(<key>patterns</key>\s*<array>)", lambda m: m.group(1) + entry, text, count=1)
        grammar = json.loads(text)
        grammar["patterns"].append(rule)
        return json.dumps(grammar, ensure_ascii=False, indent=2) + "\n"
    edit.__doc__ = f"a rule for numbers (`{rule['name']}`), which the grammar lacks, added to its top-level patterns"
    return edit


EDITS = {
    "textmate/make/language-configuration.json": make_line_comment,
    # Code whose grammar colours no number at all (SyntaxSamplesTest).
    "textmate/rel/rel.json": numbers("rel"),
    "textmate/cmake/cmake.json": numbers("cmake"),
    "textmate/tcl/Tcl.plist": numbers("tcl"),
}


def rules_only(path, data):
    """A grammar whose captures and repository hold only rules. VS Code's engine reads only a rule's fields,
    so a capture that is a bare scope name, a list or null colours nothing there, and a repository entry that
    is a list is an empty rule; Sora's engine reads each as a rule and fails the whole grammar. So a capture
    that is not a rule is dropped, and a repository entry that is not one becomes an empty rule."""
    grammar, dropped = json.loads(data), []

    def walk(value, where):
        if isinstance(value, dict):
            for key, child in value.items():
                if key.endswith("aptures") and isinstance(child, dict):
                    for capture in [c for c, rule in child.items() if not isinstance(rule, dict)]:
                        dropped.append(f"{where}/{key}/{capture}")
                        del child[capture]
                if key == "repository" and isinstance(child, dict):
                    for name in [n for n, rule in child.items() if not isinstance(rule, dict)]:
                        dropped.append(f"{where}/repository/{name}")
                        child[name] = {}
                walk(child, f"{where}/{key}")
        elif isinstance(value, list):
            for index, child in enumerate(value):
                walk(child, f"{where}[{index}]")

    walk(grammar, "")
    if not dropped:
        return data
    CHANGED.append(f"{path}: {len(dropped)} capture or repository entries that are not rules, dropped or emptied")
    return (json.dumps(grammar, ensure_ascii=False, indent=2) + "\n").encode()


def readable_configuration(path, data):
    """A language configuration tm4e's parser reads whole: one line comment as a string (not an object or a
    list) and one block comment pair. Otherwise the parser drops the field with only a log line."""
    configuration = json.loads(strip_jsonc(data.decode("utf-8-sig")))
    comments = configuration.get("comments") or {}
    line, block = comments.get("lineComment"), comments.get("blockComment")
    if not line and not block and not configuration.get("brackets"):
        CHANGED.append(f"{path}: not shipped, as it declares no comment and no bracket")
        return None
    changed = []
    if isinstance(line, dict):
        comments["lineComment"] = line["comment"]
        changed.append("lineComment object to its string")
    elif isinstance(line, list):
        comments["lineComment"] = line[0]
        changed.append(f"lineComment {line} to its first")
    if isinstance(block, list) and len(block) > 2:
        comments["blockComment"] = block[:2]
        changed.append(f"blockComment {block} to its first pair")
    if not changed:
        return data
    CHANGED.append(f"{path}: {'; '.join(changed)}")
    return (json.dumps(configuration, ensure_ascii=False, indent=2) + "\n").encode()


CHANGED = []  # what rules_only and readable_configuration changed, printed and recorded in the provenance doc


# --- Fetching -------------------------------------------------------------------------------------------

CACHE = os.environ.get("ICODE_GRAMMAR_CACHE")


def fetch(url, missing_ok=False):
    """The bytes at [url]; None for a 404 when [missing_ok]."""
    path = os.path.join(CACHE, hashlib.sha1(url.encode()).hexdigest()) if CACHE else None
    if path and os.path.exists(path):
        data = open(path, "rb").read()
        return None if data == b"\0missing" else data
    try:
        data = urllib.request.urlopen(urllib.parse.quote(url, safe=":/?=&@"), timeout=60).read()
    except urllib.error.HTTPError as error:
        if error.code != 404 or not missing_ok:
            sys.exit(f"{url}: {error}")
        data = None
    if path:
        os.makedirs(CACHE, exist_ok=True)
        open(path, "wb").write(b"\0missing" if data is None else data)
    return data


def fetch_json(url, missing_ok=False):
    data = fetch(url, missing_ok)
    return None if data is None else json.loads(strip_jsonc(data.decode("utf-8-sig")))


def strip_jsonc(text):
    """JSON with comments and trailing commas, as VS Code writes package.json files and configurations."""
    out, i, string = [], 0, False
    while i < len(text):
        c = text[i]
        if string:
            out.append(c)
            if c == "\\":
                out.append(text[i + 1])
                i += 1
            elif c == '"':
                string = False
        elif c == '"':
            string = True
            out.append(c)
        elif text.startswith("//", i):
            while i < len(text) and text[i] != "\n":
                i += 1
            continue
        elif text.startswith("/*", i):
            i = text.index("*/", i) + 2
            continue
        else:
            out.append(c)
        i += 1
    return re.sub(r",(\s*[}\]])", r"\1", "".join(out))


# --- Reading CSON and YAML grammars ------------------------------------------------------------------------
# Small readers for the forms grammars are written in, so the script needs nothing beyond Python. Checked to
# give what CoffeeScript's own reader (cson-parser 4.0.9) and PyYAML 6.0.2 give for every grammar shipped
# and for all 70 of language-etc's (2026-11-11).

def cson_loads(text):
    """A CSON grammar (CoffeeScript object notation, as Atom's grammars are written) as JSON values: implicit
    and braced objects, arrays, quoted and block strings with JavaScript's escapes, yes/no/on/off, comments."""
    tokens, i, line, start = [], 0, 1, 0
    while i < len(text):
        c = text[i]
        if c == "\n":
            tokens.append(("nl", None, line, 0))
            i, line, start = i + 1, line + 1, i + 1
        elif c in " \t\r":
            i += 1
        elif text.startswith("###", i) and not text.startswith("####", i):
            end = text.index("###", i + 3) + 3
            line += text.count("\n", i, end)
            i = end
        elif c == "#":
            while i < len(text) and text[i] != "\n":
                i += 1
        elif c in "[]{},:":
            tokens.append((c, None, line, i - start))
            i += 1
        elif c in "'\"":
            quote = c * 3 if text.startswith(c * 3, i) else c
            j = i + len(quote)
            while not text.startswith(quote, j):
                j += 2 if text[j] == "\\" else 1
            raw = text[i + len(quote):j]
            value = js_unescape(heredoc(raw) if len(quote) == 3 else re.sub(r"(?<!\\)\n[ \t]*", " ", raw))
            tokens.append(("value", value, line, i - start))
            if "\n" in raw:
                line, start = line + raw.count("\n"), text.rindex("\n", i, j) + 1
            i = j + len(quote)
        else:
            match = re.compile(r"[\w$.+-]+").match(text, i)
            word = match.group(0)
            words = {"true": True, "yes": True, "on": True, "false": False, "no": False, "off": False, "null": None}
            if word in words:
                value = words[word]
            elif re.fullmatch(r"[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?", word):
                value = float(word) if re.search(r"[.eE]", word) else int(word)
            else:
                value = word
            tokens.append(("value" if word in words or not isinstance(value, str) else "word", value, line, i - start))
            i = match.end()
    tokens.append(("end", None, line + 1, 0))
    # Each token's line's indentation, which a value on the lines below must exceed.
    first = {}
    for token in tokens:
        first.setdefault(token[2], token[3])
    position = [0]

    def peek(offset=0):
        return tokens[position[0] + offset]

    def take(kind=None):
        token = tokens[position[0]]
        if kind and token[0] != kind:
            raise ValueError(f"line {token[2]}: {kind} expected, {token[0]} found")
        position[0] += 1
        return token

    def skip(*kinds):
        while peek()[0] in kinds:
            take()

    def is_key():
        return peek()[0] in ("word", "value") and peek(1)[0] == ":"

    def key():
        token = take()
        take(":")
        value = token[1]
        return value if isinstance(value, str) else json.dumps(value)

    def implicit(column):
        """Members `key: value` at [column], on lines of their own or after commas."""
        found = {}
        while True:
            line = peek()[2]
            name = key()
            found[name] = value(first[line])
            mark = position[0]
            if peek()[0] == "," and peek(1)[0] in ("word", "value") and peek(2)[0] == ":":
                take(",")
                continue
            skip("nl")
            if is_key() and peek()[3] == column:
                continue
            position[0] = mark
            return found

    def value(indent):
        if peek()[0] == "nl":
            skip("nl")
            if peek()[3] <= indent:
                raise ValueError(f"line {peek()[2]}: a value expected")
        if is_key():
            return implicit(peek()[3])
        token = take()
        if token[0] == "[":
            items = []
            while True:
                skip("nl", ",")
                if peek()[0] == "]":
                    take()
                    return items
                items.append(implicit(peek()[3]) if is_key() else value(-1))
        if token[0] == "{":
            found = {}
            while True:
                skip("nl", ",")
                if peek()[0] == "}":
                    take()
                    return found
                name = key()
                found[name] = value(-1)
        if token[0] in ("value", "word"):
            return token[1]
        raise ValueError(f"line {token[2]}: unexpected {token[0]}")

    skip("nl")
    result = implicit(peek()[3]) if is_key() else value(-1)
    skip("nl")
    if peek()[0] != "end":
        raise ValueError(f"line {peek()[2]}: unexpected {peek()[0]}")
    return result


def heredoc(raw):
    """A CoffeeScript block string's text: the least indentation of its lines removed, and its first and
    last line breaks."""
    indents = [m.group(1) for m in re.finditer(r"\n+([ \t]*)(?=\S)", raw)]
    indent = min(indents, key=len) if indents else ""
    if indent:
        raw = raw.replace("\n" + indent, "\n")
    return re.sub(r"\n[ \t]*$", "", re.sub(r"^\n", "", raw))


def js_unescape(raw):
    """A JavaScript string literal's escapes: `\\n`, `\\x41`, `\\u0041`; any other escaped character is itself."""
    simple = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "f": "\f", "v": "\v", "0": "\0", "\n": ""}

    def one(match):
        text = match.group(1)
        if text[0] in "xu" and len(text) > 1:
            return chr(int(text[2:-1] if text.startswith("u{") else text[1:], 16))
        return simple.get(text, text)
    return re.sub(r"\\(x[0-9A-Fa-f]{2}|u\{[0-9A-Fa-f]+\}|u[0-9A-Fa-f]{4}|[\s\S])", one, raw)


def yaml_loads(text):
    """A YAML grammar (`.yaml-tmLanguage`) as JSON values: block and flow mappings and sequences, plain,
    single- and double-quoted scalars and comments. Scalars stay strings, as the grammar's fields are."""
    if text.lstrip().startswith("{"):
        return json.loads(text)
    lines = []
    for number, raw in enumerate(text.split("\n"), 1):
        body = strip_comment(raw)
        if body.strip() and body.strip() != "---":
            lines.append((len(body) - len(body.lstrip(" ")), body.strip(), number))
    position = [0]

    def block(indent):
        column, body, number = lines[position[0]]
        if body.startswith("- ") or body == "-":
            items = []
            while position[0] < len(lines) and lines[position[0]][0] == column and lines[position[0]][1][:2] in ("- ", "-"):
                _, body, number = lines[position[0]]
                rest = body[1:].lstrip(" ")
                if not rest:
                    position[0] += 1
                    items.append(block(column))
                    continue
                # `- key: value`: a mapping whose first key is on the dash's line.
                lines[position[0]] = (column + len(body) - len(rest), rest, number)
                items.append(block(column) if is_mapping(rest) else (position.__setitem__(0, position[0] + 1) or scalar(rest, number)))
            return items
        found = {}
        while position[0] < len(lines) and lines[position[0]][0] == column:
            _, body, number = lines[position[0]]
            name, rest = split_key(body, number)
            position[0] += 1
            if rest:
                found[name] = scalar(rest, number)
            elif position[0] < len(lines) and (lines[position[0]][0] > column or lines[position[0]][1].startswith("- ") and lines[position[0]][0] == column):
                found[name] = block(column)
            else:
                found[name] = None
        return found

    return block(-1)


def strip_comment(line):
    """[line] without a `#` comment outside quotes."""
    quote = None
    for i, c in enumerate(line):
        if quote:
            if c == quote:
                quote = None
        elif c in "'\"" and (i == 0 or line[i - 1] in " \t[{,:-"):
            quote = c
        elif c == "#" and (i == 0 or line[i - 1] in " \t"):
            return line[:i].rstrip()
    return line.rstrip()


def is_mapping(body):
    try:
        split_key(body, 0)
        return True
    except ValueError:
        return False


def split_key(body, number):
    if body[0] in "'\"":
        value, end = quoted(body, 0)
    else:
        match = re.match(r"([^:{}\[\],]+?)\s*(?=:(\s|$))", body)
        if not match:
            raise ValueError(f"line {number}: a key expected")
        value, end = match.group(1), match.end()
    if not body[end:].startswith(":"):
        raise ValueError(f"line {number}: `:` expected")
    return value, body[end + 1:].strip()


def quoted(text, i):
    quote, j, out = text[i], i + 1, []
    while True:
        c = text[j]
        if quote == "'" and text.startswith("''", j):
            out.append("'")
            j += 2
        elif c == quote:
            break
        elif quote == '"' and c == "\\":
            out.append(json.loads(f'"\\{text[j + 1]}"') if text[j + 1] in 'nrtbf"\\/' else text[j + 1])
            j += 2
        else:
            out.append(c)
            j += 1
    return "".join(out), j + 1


def scalar(text, number):
    value, end = flow(text, 0, number)
    if text[end:].strip():
        raise ValueError(f"line {number}: unexpected {text[end:]!r}")
    return value


def flow(text, i, number, inside=False):
    while text[i] == " ":
        i += 1
    c = text[i]
    if c in "[{":
        close, items, found = "]" if c == "[" else "}", [], {}
        i += 1
        while True:
            while text[i] in " ,":
                i += 1
            if text[i] == close:
                return (items if c == "[" else found), i + 1
            if c == "[":
                item, i = flow(text, i, number, True)
                items.append(item)
            else:
                name, i = flow(text, i, number, True)
                while text[i] == " ":
                    i += 1
                if text[i] != ":":
                    raise ValueError(f"line {number}: `:` expected in {text!r}")
                found[name], i = flow(text, i + 1, number, True)
    if c in "'\"":
        return quoted(text, i)
    match = re.compile(r"[^,\[\]{}]*?(?=\s*(?:[,\]}]|:\s|$))" if inside else r".*").match(text, i)
    return match.group(0).strip(), match.end()


def vscode(path, commit=VSCODE, missing_ok=False):
    return fetch(f"{RAW}/microsoft/vscode/{commit}/extensions/{path}", missing_ok)


def shiki_index():
    """Shiki's grammar index (packages/tm-grammars/index.js): its languages, then the helper grammars they
    embed or inject (`injections`), each marked `helper`. Both are JavaScript array literals, read as JSON."""
    text = fetch(f"{SHIKI_RAW}/index.js").decode()
    helpers = [dict(entry, helper=True) for entry in js_array(text, "injections")]
    return js_array(text, "grammars") + helpers


def js_array(text, name):
    body = text[text.index(f"export const {name} = ") + len(f"export const {name} = "):]
    body = body[:body.index("\nexport const")] if "\nexport const" in body else body
    out, i = [], 0
    while i < len(body):
        c = body[i]
        if c == "'":
            j, value = i + 1, []
            while body[j] != "'":
                if body[j] == "\\":
                    j += 1
                value.append(body[j])
                j += 1
            out.append(json.dumps("".join(value)))
            i = j + 1
            continue
        match = re.match(r"([A-Za-z_]\w*)\s*:", body[i:])
        if match and (not out or out[-1].rstrip()[-1:] in "{,"):
            out.append(json.dumps(match.group(1)) + ":")
            i += match.end()
            continue
        out.append(c)
        i += 1
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", "".join(out).strip().rstrip(";")))


def linguist_languages():
    """GitHub Linguist's languages.yml: the few fields read here, from its plain YAML layout."""
    text = fetch(f"{RAW}/github-linguist/linguist/{LINGUIST}/lib/linguist/languages.yml").decode()
    languages, language, key = {}, None, None
    for line in text.splitlines():
        if not line or line.startswith(("#", "---")):
            continue
        if not line.startswith(" "):
            language = languages.setdefault(unquote(line.rstrip(":")), {})
        elif line.startswith("  - "):
            language.setdefault(key, []).append(unquote(line[4:]))
        elif line.startswith("  ") and not line.startswith("   "):
            key, _, value = line.strip().partition(":")
            if value.strip():
                language[key] = unquote(value.strip())
    return languages


def unquote(value):
    value = value.strip()
    return json.loads(value) if value.startswith('"') else value.strip("'")


# --- Reading grammars -----------------------------------------------------------------------------------


def grammar_scope(path, data):
    if path.endswith(".json"):
        return json.loads(data)["scopeName"]
    return re.search(r"<key>scopeName</key>\s*<string>([^<]*)</string>", data.decode()).group(1)


def grammar_version(path, data):
    return json.loads(data).get("version") if path.endswith(".json") else None


def grammar_includes(path, data, scope):
    """The other grammars [data] pulls in by scope (`source.css#rule`), as ShippedGrammars reads them."""
    found = set()
    if path.endswith(".json"):
        def walk(value):
            if isinstance(value, dict):
                for key, child in value.items():
                    if key == "include" and isinstance(child, str):
                        found.add(child.split("#")[0])
                    elif key != "name" or not isinstance(child, str):
                        walk(child)
            elif isinstance(value, list):
                for child in value:
                    walk(child)
        walk({k: v for k, v in json.loads(data).items() if k != "name"})
    else:
        found = {m.split("#")[0] for m in re.findall(r"<key>include</key>\s*<string>([^<]*)</string>", data.decode())}
    return sorted(s for s in found if s and not s.startswith("$") and s != scope)


def grammar_file_types(path, data):
    if path.endswith(".json"):
        return json.loads(data).get("fileTypes", [])
    match = re.search(r"<key>fileTypes</key>\s*<array>(.*?)</array>", data.decode(), re.S)
    return re.findall(r"<string>([^<]*)</string>", match.group(1)) if match else []


# --- Building the catalog -------------------------------------------------------------------------------


class Language:
    def __init__(self, id, scope, folder, file, data, source, path, licence, configuration=None, embedded=None, upstream=None):
        self.id, self.scope, self.source, self.path, self.licence = id, scope, source, path, licence
        # An asset name in plain ASCII (`Cap’n Proto.tmLanguage` is written `Cap-n Proto.tmLanguage`).
        self.grammar = f"textmate/{folder}/{re.sub(r'[^A-Za-z0-9 ._()+/-]', '-', file)}"
        self.data, self.configuration, self.embedded = data, configuration, embedded
        self.upstream = upstream if upstream is not None else grammar_version(file, data)
        self.includes = grammar_includes(file, data, scope)
        if grammar_scope(file, data) != scope:
            sys.exit(f"{self.grammar} declares {grammar_scope(file, data)}, not {scope}")
        self.types = []  # [{type, family, extensions, names, prefixes, firstLine}]
        self.claims = []  # (stage, type id, a contributes.languages-like entry)


def main():
    files = {}  # asset path -> bytes
    languages = []
    by_scope = {}

    def add(language, configuration_data=None):
        if language.scope in by_scope:
            sys.exit(f"{language.scope} twice")
        languages.append(language)
        by_scope[language.scope] = language
        put(language.grammar, language.data)
        if configuration_data is not None:
            put(language.configuration, configuration_data)

    def put(path, data):
        if files.get(path, data) != data:
            sys.exit(f"{path}: two different files")
        files[path] = data

    # 1. Sora, and YAML from VS Code 337cacaa.
    for id, scope, folder, grammar, configuration, embedded, licence in SORA_LANGUAGES:
        data = fetch(f"{SORA_RAW}/{folder}/{grammar}")
        config = f"textmate/{folder}/{configuration}"
        add(Language(id, scope, folder, grammar, data, "sora", folder, licence, config, embedded), fetch(f"{SORA_RAW}/{folder}/{configuration}"))
    yaml = Language("yaml", "source.yaml", "yaml", "syntaxes/yaml.tmLanguage.json", vscode("yaml/syntaxes/yaml.tmLanguage.json", VSCODE_YAML),
                    "vscode-337cacaa", "extensions/yaml", "TextMate Bundle License", CONFIGURATIONS["yaml"])
    add(yaml, vscode("yaml/language-configuration.json"))

    # 2. VS Code's built-in extensions.
    listing = json.loads(fetch(f"https://api.github.com/repos/microsoft/vscode/contents/extensions?ref={VSCODE}"))
    contributes = {}  # language id -> merged contributes.languages entry
    grammar_of = {}  # language id -> scope
    for extension in sorted(e["name"] for e in listing if e["type"] == "dir"):
        package = fetch_json(f"{RAW}/microsoft/vscode/{VSCODE}/extensions/{extension}/package.json", missing_ok=True)
        if not package:
            continue
        for entry in package.get("contributes", {}).get("languages", []):
            merged = contributes.setdefault(entry["id"], {"extension": extension})
            for key in ("extensions", "filenames", "filenamePatterns"):
                merged.setdefault(key, []).extend(entry.get(key, []))
            for key in ("firstLine", "configuration"):
                if key in entry and key not in merged:
                    merged[key] = entry[key]
                    if key == "configuration":
                        merged["configurationExtension"] = extension
        grammars = package.get("contributes", {}).get("grammars", [])
        if not grammars:
            continue
        manifest = fetch_json(f"{RAW}/microsoft/vscode/{VSCODE}/extensions/{extension}/cgmanifest.json", missing_ok=True) or {"registrations": []}
        folder = extension.removesuffix("-basics")
        for grammar in grammars:
            scope = grammar["scopeName"]
            if grammar.get("language"):
                grammar_of[grammar["language"]] = scope  # the last a language names is its own, as in VS Code
            if grammar.get("injectTo") or scope in VSCODE_SKIPPED or scope in by_scope:
                continue
            file = grammar["path"].removeprefix("./")
            data = vscode(f"{extension}/{file}")
            language_id = grammar.get("language")
            id = GRAMMAR_IDS.get(scope) or language_id
            if not id or any(l.id == id for l in languages):
                sys.exit(f"{extension} {scope}: no id of its own; add it to GRAMMAR_IDS")
            version = grammar_version(file, data)
            licence = "MIT"
            origin = re.match(r"https://github.com/([^/]+/[^/]+)/commit/", version or "")
            if origin:
                repository = origin.group(1).lower()
                for registration in manifest["registrations"]:
                    url = registration["component"].get("git", {}).get("repositoryUrl", "").lower().removesuffix(".git")
                    if url.endswith("/" + repository):
                        licence = registration.get("license", licence).replace("MIT License", "MIT")
            embedded = grammar.get("embeddedLanguages")
            add(Language(id, scope, folder, file, data, "vscode", f"extensions/{extension}", licence, None, embedded))
            by_scope[scope].vscode_language = language_id

    # Each VS Code grammar's configuration: its language's, beside the grammar.
    for language in languages:
        entry = contributes.get(getattr(language, "vscode_language", None), {})
        if language.id in CONFIGURATIONS:
            language.configuration = CONFIGURATIONS[language.id]
        elif language.source == "vscode" and "configuration" in entry:
            relative = entry["configuration"].removeprefix("./")
            language.configuration = f"textmate/{language.grammar.split('/')[1]}/{relative}"
            put(language.configuration, vscode(f"{entry['configurationExtension']}/{relative}"))

    # 3. Shiki, for the scopes VS Code lacks.
    shiki = shiki_index()
    skipped = dict(SHIKI_SKIPPED)
    for entry in shiki:
        name, scope = entry["name"], entry["scopeName"]
        if scope in by_scope or name in SHIKI_SKIPPED:
            continue
        if any(l.id == name for l in languages):
            skipped[name] = f"VS Code's grammar for {name} ({next(l.scope for l in languages if l.id == name)}) ships instead of {scope}"
            continue
        if name in UPSTREAM:
            continue
        upstream, licence = entry.get("source"), entry.get("license")
        if licence in (None, "?", "NOASSERTION") or name in LICENCES:
            if name not in LICENCES:
                skipped[name] = f"Shiki's index gives the licence as {licence}; not checked"
                continue
            licence = LICENCES[name][0]
        data = fetch(f"{SHIKI_RAW}/grammars/{name}.json")
        language = Language(name, scope, name, f"{name}.json", data, "shiki", f"grammars/{name}.json", licence, upstream=upstream)
        language.helper = entry.get("helper", False)
        language.injection = bool(entry.get("injectTo"))
        if upstream:
            match = re.match(r"https://github.com/([^/]+/[^/]+)/blob/([0-9a-f]{40})/(.*)", upstream)
            language.origin = match.groups() if match else None
        if any(l.id == language.id for l in languages):
            sys.exit(f"{name}: id taken")
        add(language)

    # 4. The upstream grammars Linguist vendors.
    for id, (scope, repository, commit, path, licence) in UPSTREAM.items():
        key = repository.split("/")[1].lower()
        if SOURCES.get(key, {}).get("url", f"https://github.com/{repository}/tree/{commit}") != f"https://github.com/{repository}/tree/{commit}":
            sys.exit(f"{key}: two repositories")
        SOURCES[key] = {"name": f"{repository} {commit[:8]}", "url": f"https://github.com/{repository}/tree/{commit}", "licence": licence}
        data, file = fetch(f"{RAW}/{repository}/{commit}/{path}"), path.split("/")[-1]
        if any(l.id == id for l in languages) or scope in by_scope:
            sys.exit(f"{id} ({scope}): taken")
        form = re.search(r"\.(cson|ya?ml-tmlanguage)$", file, re.I)
        if form:
            # Sora's engine reads JSON and property lists only.
            reader = cson_loads if form.group(1).lower() == "cson" else yaml_loads
            data = (json.dumps(reader(data.decode("utf-8")), ensure_ascii=False, indent=2) + "\n").encode()
            file = file[:form.start()] + ".tmLanguage.json"
        language = Language(id, scope, id, file, data, key, path, licence)
        if form:
            CHANGED.append(f"{language.grammar}: converted from {'CSON' if reader is cson_loads else 'YAML'} to JSON")
        language.origin = (repository, commit, path)
        add(language)

    # A Shiki or upstream grammar's configuration and files: from its upstream VS Code extension, if it is one.
    for language in languages:
        origin = getattr(language, "origin", None)
        if not origin or getattr(language, "helper", False):
            continue
        repository, commit, path = origin
        parts = path.split("/")
        for depth in range(len(parts) - 1, -1, -1):
            base = "/".join(parts[:depth])
            package = fetch_json(f"{RAW}/{repository}/{commit}/{base + '/' if base else ''}package.json", missing_ok=True)
            if not package or "contributes" not in package:
                continue
            ids = [g.get("language") for g in package["contributes"].get("grammars", []) if g.get("scopeName") == language.scope]
            for entry in package["contributes"].get("languages", []):
                if entry.get("id") in ids:
                    language.claims.append((2, language.id, entry))
                    relative = entry.get("configuration", "").removeprefix("./")
                    if relative and not language.configuration:
                        data = fetch(f"{RAW}/{repository}/{commit}/{base + '/' if base else ''}{relative}", missing_ok=True)
                        if data is not None:
                            language.configuration = f"textmate/{language.grammar.split('/')[1]}/language-configuration.json"
                            put(language.configuration, data)
            break

    # A grammar kept as it is: what it includes is the kept file's, not the copied one's.
    for language in languages:
        if language.grammar in KEPT:
            language.data = open(os.path.join(ASSETS, language.grammar), "rb").read()
            language.includes = grammar_includes(language.grammar, language.data, language.scope)

    # Who opens which files.
    linguist = linguist_languages()
    theme = json.load(open(os.path.join(ASSETS, "file-icons", "theme.json")))
    for language in languages:
        if language.source in ("sora", "vscode-337cacaa", "vscode"):
            for language_id, scope in grammar_of.items():
                if SCOPES.get(language_id, scope) == language.scope and language_id in contributes:
                    language.claims.append((1, TYPE_IDS.get(language_id, language_id), contributes[language_id]))
            if language.id == "kotlin":  # Sora's grammar; VS Code has no Kotlin, so its names come from Linguist
                language.claims.append((1, "kotlin", {}))
        elif not getattr(language, "helper", False):
            if not any(c[0] == 2 for c in language.claims):
                language.claims.append((2, language.id, {}))
            # TextMate's fileTypes: extensions, or whole names (`Rakefile`).
            types = grammar_file_types(language.grammar, language.data)
            language.claims.append((3, language.id, {
                "extensions": ["." + t.lstrip(".") for t in types if not t[:1].isupper()],
                "filenames": [t for t in types if t[:1].isupper()],
            }))
        primary = next((c[1] for c in language.claims if c[1] == TYPE_IDS.get(language.id, language.id)), None)
        if primary is None and language.claims:
            primary = language.claims[0][1]
        for name, entry in linguist.items():
            same = primary in (name.lower(), *[a.lower() for a in entry.get("aliases", [])])
            if primary and (entry.get("tm_scope") == language.scope or same):
                # Linguist matches names in their case (`BUILD`); iCode in any, so a bare word is taken only
                # where the icon theme knows the name too.
                names = [n for n in entry.get("filenames", []) if "." in n or n.lower() in theme["fileNames"]]
                language.claims.append((4, primary, {
                    "extensions": entry.get("extensions", []), "filenames": names,
                    "interpreters": entry.get("interpreters", []), "kind": entry.get("type"),
                    "same": same,
                }))

    owner_of = {t: l for l in languages for _, t, _ in l.claims}
    for type_id, extensions, names, _ in ICODE_FILES:
        owner_of[type_id].claims.append((5, type_id, {"extensions": ["." + e for e in extensions.split()], "filenames": names.split()}))
    owner_of["ignore"].claims.append((5, "ignore", {
        "filenames": sorted(k for k in theme["fileNames"] if k.startswith(".") and "/" not in k and k.endswith(IGNORE_SUFFIX)),
        "extensions": sorted("." + k for k in theme["fileExtensions"] if k.endswith(IGNORE_SUFFIX)),
    }))
    claimed = {}  # ("extension"|"name"|"prefix", key) -> type id
    # A name or extension that only languages VS Code lacks claim goes to the one the icon theme draws it as.
    icons = {("extension", k): v for k, v in theme["fileExtensions"].items()} | {("name", k): v for k, v in theme["fileNames"].items()}
    first = {key for language in languages for stage, _, entry in language.claims if stage == 1 for key in keys(entry)}
    for language in languages:
        for stage, type_id, entry in language.claims:
            for key in keys(entry):
                if stage > 1 and key not in first and key not in NOT_TEXT and icons.get(key) in (type_id, theme["languageIds"].get(type_id)):
                    claimed.setdefault(key, type_id)
    clashes = []
    types = {}  # type id -> (language, dict)
    for stage in (1, 2, 3, 4, 5):
        for language in languages:
            for claim_stage, type_id, entry in language.claims:
                if claim_stage != stage:
                    continue
                if type_id in types and types[type_id][0] is not language:
                    sys.exit(f"type {type_id} in two languages")
                record = types.setdefault(type_id, (language, {"type": type_id, "extensions": [], "names": [], "prefixes": [], "firstLine": None, "kind": None}))[1]
                # Its kind is the Linguist language of its own name's (Kotlin's, not Gradle Kotlin DSL's), else the first.
                if entry.get("kind") and (not record["kind"] or entry.get("same") and not record.get("same")):
                    record["kind"], record["same"] = entry["kind"], entry.get("same")
                for kind, key in keys(entry):
                    if (kind, key) in NOT_TEXT:
                        continue
                    # A later name must not take a file an earlier extension already opens (`.eslintrc`).
                    shadowed = next((claimed[("extension", e)] for e in extensions_of(key) if ("extension", e) in claimed), None)
                    if stage > 1 and kind == "name" and (kind, key) not in claimed and shadowed not in (None, type_id):
                        clashes.append(f"{kind} `{key}`: `{shadowed}` by its extension, not `{type_id}` ({STAGES[stage]})")
                        continue
                    owner = claimed.setdefault((kind, key), type_id)
                    if owner != type_id:
                        clashes.append(f"{kind} `{key}`: `{owner}`, not `{type_id}` ({STAGES[stage]})")
                    elif key not in record[PLURAL[kind]]:
                        record[PLURAL[kind]].append(key)
                line = FIRST_LINES.get(type_id) if stage == 1 else None
                line = line or entry.get("firstLine")
                if not line and entry.get("interpreters") and not record["firstLine"]:
                    line = r"^#!.*\b(" + "|".join(re.escape(i) for i in entry["interpreters"]) + r")\b"
                if line and not record["firstLine"]:
                    record["firstLine"] = line
                if type_id not in [t["type"] for t in language.types]:
                    language.types.append(record)

    # A grammar no file type opens ships only when a shipped grammar pulls it in.
    opened = {l.scope for l in languages if l.types}
    shipped, todo = set(opened), list(opened)
    while todo:
        for scope in by_scope[todo.pop()].includes:
            if scope in by_scope and scope not in shipped:
                shipped.add(scope)
                todo.append(scope)
    for language in languages:
        if language.scope not in shipped:
            skipped[language.id] = ("an injection grammar, which Sora does not read" if getattr(language, "injection", False)
                                    else "no file type opens it and no shipped grammar includes it")
    languages = [l for l in languages if l.scope in shipped]
    files = {path: data for path, data in files.items() if any(path in (l.grammar, l.configuration) for l in languages)}
    ids = {l.id for l in languages}
    for language in languages:
        if language.embedded and not set(language.embedded.values()) <= ids:
            language.embedded = None

    write(languages, files, clashes, skipped)
    write_samples(languages, shiki)


def extensions_of(name):
    """[name]'s extensions, the longest first, as FileTypes.extensionsOf reads them."""
    return [name[i + 1:] for i, c in enumerate(name) if c == "." and name[i + 1:]]


STAGES = {1: "VS Code", 2: "its upstream", 3: "its grammar's fileTypes", 4: "Linguist", 5: "iCode"}
PLURAL = {"extension": "extensions", "name": "names", "prefix": "prefixes"}


def keys(entry):
    """What a contributes.languages-like entry claims: ("extension", "ts"), ("name", "makefile"), ("prefix", "dockerfile.")."""
    for extension in entry.get("extensions", []):
        extension = extension.lower()
        yield ("extension", extension[1:]) if extension.startswith(".") else ("name", extension)
    for name in entry.get("filenames", []):
        yield "name", name.lower()
    for pattern in entry.get("filenamePatterns", []):
        pattern = pattern.lower()
        if "/" in pattern:
            continue
        if "*" not in pattern:
            yield "name", pattern
        elif pattern.startswith("*.") and "*" not in pattern[2:]:
            yield "extension", pattern[2:]
        elif pattern.endswith("*") and "*" not in pattern[:-1]:
            yield "prefix", pattern[:-1]


def write(languages, files, clashes, skipped):
    for path, edit in EDITS.items():
        files[path] = edit(files[path].decode()).encode()
    for language in languages:
        if language.grammar.endswith(".json"):
            files[language.grammar] = rules_only(language.grammar, files[language.grammar])
        if language.configuration and language.configuration not in KEPT:
            readable = readable_configuration(language.configuration, files[language.configuration])
            if readable is None:
                del files[language.configuration]
                language.configuration = None
            else:
                files[language.configuration] = readable
    for path in KEPT:
        if not os.path.exists(os.path.join(ASSETS, path)):
            sys.exit(f"{path} is kept as it is, but missing")
        files.pop(path, None)
    keep = set(KEPT) | {"textmate/catalog.json"}
    for root, _, names in os.walk(TEXTMATE):
        for name in names:
            path = os.path.relpath(os.path.join(root, name), ASSETS).replace(os.sep, "/")
            if path not in files and path not in keep:
                os.remove(os.path.join(root, name))
    for root, folders, _ in os.walk(TEXTMATE, topdown=False):
        for folder in folders:
            if not os.listdir(os.path.join(root, folder)):
                os.rmdir(os.path.join(root, folder))
    for path, data in files.items():
        target = os.path.join(ASSETS, path)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        open(target, "wb").write(data)

    used = [key for key in SOURCES if any(l.source == key for l in languages)]
    lines = ['{', '  "sources": {', ",\n".join(f"    {json.dumps(k)}: {json.dumps(SOURCES[k])}" for k in used), '  },', '  "languages": [']
    rows = []
    for language in languages:
        row = {"id": language.id, "scope": language.scope, "grammar": language.grammar}
        if language.configuration:
            row["configuration"] = language.configuration
        if language.embedded:
            row["embedded"] = language.embedded
        if language.includes:
            row["includes"] = language.includes
        row.update({"source": language.source, "path": language.path, "upstream": language.upstream, "licence": language.licence})
        types = []
        for record in language.types:
            item = {}
            if record["type"] != language.id:
                item["type"] = record["type"]
            item["family"] = FAMILIES.get(record["type"]) or KINDS.get(record["kind"], "code")
            for key in ("extensions", "names", "prefixes"):
                if record[key]:
                    item[key] = record[key]
            if record["firstLine"]:
                item["firstLine"] = record["firstLine"]
            types.append(item)
        if types:
            row["files"] = types
        rows.append("    " + json.dumps(row, ensure_ascii=False))
    lines += [",\n".join(rows), "  ]", "}", ""]
    open(CATALOG, "w").write("\n".join(lines))

    write_provenance(languages, used, clashes, skipped)
    for line in CHANGED:
        print("changed:", line)
    print(f"{len(languages)} languages, {sum(len(l.types) for l in languages)} file types")


def write_samples(languages, shiki):
    """Shiki's sample (its repository's samples/<name>.sample) of each language that opens files and has no
    sample of iCode's own: the Shiki grammar's of the same name, else of an alias, else of the same scope."""
    own = {name.rsplit(".", 1)[0] for name in os.listdir(os.path.join(SAMPLES, "icode"))}
    folder = os.path.join(SAMPLES, "shiki")
    os.makedirs(folder, exist_ok=True)
    written = set()
    for language in languages:
        opens = any(t[key] for t in language.types for key in ("extensions", "names", "prefixes", "firstLine"))
        if not opens or language.id in own:
            continue
        names = [e["name"] for e in shiki if e["name"] == language.id]
        names += [e["name"] for e in shiki if language.id in e.get("aliases", [])]
        names += [e["name"] for e in shiki if e["scopeName"] == language.scope]
        for name in names:
            data = fetch(f"{RAW}/shikijs/textmate-grammars-themes/{SHIKI}/samples/{name}.sample", missing_ok=True)
            if data is not None:
                open(os.path.join(folder, f"{language.id}.sample"), "wb").write(data)
                written.add(f"{language.id}.sample")
                break
    for name in os.listdir(folder):
        if name not in written:
            os.remove(os.path.join(folder, name))
    print(f"{len(written)} samples from Shiki, {len(own)} of iCode's own")


def write_provenance(languages, used, clashes, skipped):
    """The tables under "## Shipped grammars", as LanguageCatalogTest reads them back, and the sections this
    script alone writes: what it changed, what it did not ship, and the names two languages claim."""
    def upstream(language):
        if not language.upstream:
            return f"None: {SOURCES[language.source]['name']} is the origin"
        match = re.match(r"https://github.com/([^/]+/[^/]+)/(?:commit|blob)/([0-9a-f]{8})", language.upstream)
        return f"[{match.group(1)}@{match.group(2)}]({language.upstream})" if match else language.upstream

    tables = ["| Source | Where | Licence |", "|---|---|---|"]
    tables += [f"| {SOURCES[k]['name']} | {SOURCES[k]['url']} | {SOURCES[k]['licence']} |" for k in used]
    tables += ["", "| Language | Scope | Copied from | Upstream | Licence |", "|---|---|---|---|---|"]
    tables += [f"| `{l.id}` | `{l.scope}` | {SOURCES[l.source]['name']} `{l.path}` | {upstream(l)} | {l.licence} |" for l in languages]
    doc = open(PROVENANCE).read().split("\n")
    start = doc.index("## Shipped grammars") + 1
    end = next(i for i in range(start, len(doc)) if doc[i].startswith("## "))
    first = next(i for i in range(start, end) if doc[i].startswith("|"))
    last = max(i for i in range(start, end) if doc[i].startswith("|"))
    doc[first:last + 1] = tables
    notes = {
        "## Changed on import": ["tools/import-grammars.py makes these changes, each time it runs:", ""] +
            [f"- `{path}`: {reason}" for path, reason in KEPT.items()] +
            [f"- `{path}`: {edit.__doc__.strip()}" for path, edit in EDITS.items()] + [f"- `{line.replace(': ', '`: ', 1)}" for line in CHANGED],
        "## Not shipped": ["Grammars the sources hold that iCode does not ship, and why:", ""] +
            [f"- `{id}`: {reason}" for id, reason in sorted(skipped.items())] +
            [f"- `{scope}` (VS Code): {reason}" for scope, reason in VSCODE_SKIPPED.items()],
        "## Names and extensions claimed twice": [
            "Each name and extension opens one language: the first to claim it (VS Code, then the language's",
            "upstream, then its grammar's `fileTypes`, then Linguist, then iCode's own below), or the one the",
            "icon theme draws it as. iCode's own claims, for formats the icon theme names that no source gives a",
            "language:", ""] + [f"- `{t}`: {' '.join('`.' + e + '`' for e in x.split())}{' ' if x and n else ''}{' '.join('`' + m + '`' for m in n.split())}: {why}"
                                 for t, x, n, why in ICODE_FILES] +
            ["- `ignore`: every dot file the icon theme names whose name ends in `ignore`, a list of paths in .gitignore's syntax",
             "", "The claims that lost:", ""] + [f"- {clash}" for clash in sorted(set(clashes))] +
            [f"- {kind} `{key}`: none, {reason}" for (kind, key), reason in NOT_TEXT.items()],
    }
    for heading, body in reversed(notes.items()):  # each new one goes first, so they end in this order
        if heading in doc:
            start = doc.index(heading)
            end = next((i for i in range(start + 1, len(doc)) if doc[i].startswith("## ")), len(doc))
        else:
            start = end = next(i for i in range(doc.index("## Shipped grammars") + 1, len(doc)) if doc[i].startswith("## "))
        doc[start:end] = [heading, ""] + body + [""]
    open(PROVENANCE, "w").write("\n".join(doc))


if __name__ == "__main__":
    main()
