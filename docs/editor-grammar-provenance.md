# Editor grammar provenance

The TextMate grammars are bundled in the APK so syntax coloring works without
network access or a language server. Every shipped grammar is one entry in the
language catalog, `app/src/main/assets/textmate/catalog.json`
(`domain/syntax/Languages.kt`, docs/syntax-highlighting-plan.md D3): its id,
scope, grammar and configuration files, what it embeds and includes, the source
it was copied from, the upstream commit the grammar file names as its own origin
(its `version` field), and its licence. `ui/syntax/SyntaxEngine` loads a language's
grammar from the catalog, with the grammars it includes, the first time a file or
code block needs it.

Unknown text extensions fall back to a readable uncolored editor rather than
guessing a grammar. Binary/malformed files remain rejected by `DocumentCodec`.

## Shipped grammars

`tools/import-grammars.py` writes the grammars, the catalog and the two tables below
from pinned sources: Sora Editor 0.24.6, VS Code 307b60e7 (and 337cacaa for YAML),
Shiki's tm-grammars 8f1828eb for the languages VS Code lacks, and, for the rest, the
upstream repositories GitHub Linguist d456527f vendors. Its own top comment gives the
order and the rules; rerunning it reproduces every file. The tables are the catalog's,
row for row (`LanguageCatalogTest` fails on any difference and prints the tables as
they must read). Files are copied unchanged except for the edits recorded below and
under "Changed on import". Sora Editor is
LGPL-2.1-or-later; a grammar Sora ships keeps its upstream's licence where the
file names an upstream. The Kotlin grammar names none, so it is recorded under
Sora's licence. VS Code's sources and licences per extension are also in each
extension's `cgmanifest.json`.

| Source | Where | Licence |
|---|---|---|
| Sora Editor 0.24.6 | https://github.com/Rosemoe/sora-editor/tree/0.24.6/app/src/main/assets/textmate | LGPL-2.1-or-later |
| VS Code 337cacaa | https://github.com/microsoft/vscode/tree/337cacaafa0248ef6db264f52971d283b40aa94d | MIT |
| VS Code 307b60e7 | https://github.com/microsoft/vscode/tree/307b60e7aba9fe6c2dec8cae5970561b126f807b | MIT |
| Shiki tm-grammars 8f1828eb | https://github.com/shikijs/textmate-grammars-themes/tree/8f1828ebccc57930cf0adde9b35ae929584fe191/packages/tm-grammars | MIT |
| euler0/sublime-glsl 59a5f8a3 | https://github.com/euler0/sublime-glsl/tree/59a5f8a3120358657cefdc4b830b4a883ebfbf77 | Unlicense |
| textmate/tcl.tmbundle f06f8014 | https://github.com/textmate/tcl.tmbundle/tree/f06f80144c01ff1e3836cef21cbac62f8e1c5df6 | TextMate Bundle License |
| spacestation13/dm-syntax a63b4388 | https://github.com/spacestation13/dm-syntax/tree/a63b4388d0df050d40f2c6c6afd85637174abbb5 | MIT |
| FreHu/abap-cds-grammar dd803e6a | https://github.com/FreHu/abap-cds-grammar/tree/dd803e6ad2eea202b537a87c0da0536b92f60d3e | Unlicense |
| textmate/antlr.tmbundle 7f07385a | https://github.com/textmate/antlr.tmbundle/tree/7f07385abbdbd716ebb5b15ad8190347554192cf | TextMate Bundle License |
| apiaryio/api-blueprint-sublime-plugin 0a90eff1 | https://github.com/apiaryio/api-blueprint-sublime-plugin/tree/0a90eff10e71a8f18974410307b3b86c71afb3ed | MIT |
| AutoIt/SublimeAutoItScript 2b2e950c | https://github.com/AutoIt/SublimeAutoItScript/tree/2b2e950c7edb47da76d335fb205593bc83987a19 | MIT |
| browserslist/browserslist-vscode e40e7efc | https://github.com/browserslist/browserslist-vscode/tree/e40e7efcb19ae08622510959cfb8a9bfc8378c91 | MIT |
| usebruno/bruno-ide-extensions 0f92df16 | https://github.com/usebruno/bruno-ide-extensions/tree/0f92df1617cf90fe50430a0e76fac55853a398b9 | MIT |
| caddyserver/vscode-caddyfile 9ba30fce | https://github.com/caddyserver/vscode-caddyfile/tree/9ba30fcec02e257776188ca66ed47fc210e86b26 | MIT |
| textmate/capnproto.tmbundle 9fab42eb | https://github.com/textmate/capnproto.tmbundle/tree/9fab42eb8a28b6138b8c11ce841efe10962b852e | TextMate Bundle License |
| SAP/cds-textmate-grammar d5389523 | https://github.com/SAP/cds-textmate-grammar/tree/d5389523709ba010cf1b9de78c6c0478bab31bde | Apache-2.0 |
| SublimeText/ColdFusion dbe2f760 | https://github.com/SublimeText/ColdFusion/tree/dbe2f76038fe77fe90b4cc4a0651aa817f14f1a3 | MIT |
| textmate/graphviz.tmbundle d1d489f8 | https://github.com/textmate/graphviz.tmbundle/tree/d1d489f893a0e6ef5d9021bf7dc1ffd08b3f9b90 | TextMate Bundle License |
| ocamllabs/vscode-ocaml-platform 71aa5bc6 | https://github.com/ocamllabs/vscode-ocaml-platform/tree/71aa5bc671bd5685a3f7d0faeb9dafe9d3666de5 | ISC |
| elixir-lang/elixir-tmbundle eb63f1a5 | https://github.com/elixir-lang/elixir-tmbundle/tree/eb63f1a5da138634cff0caf3d777ba147b47670d | Apache-2.0 |
| gregory-m/ejs-tmbundle 5e48fff7 | https://github.com/gregory-m/ejs-tmbundle/tree/5e48fff7258a94a8f57b142fa38571a1f9f68c44 | MIT |
| textmate/forth.tmbundle 0cf0ec91 | https://github.com/textmate/forth.tmbundle/tree/0cf0ec91f65717903b41ea6cd62f6cd17788f0d0 | TextMate Bundle License |
| printfn/gemini-vscode 72ef757a | https://github.com/printfn/gemini-vscode/tree/72ef757a0cb5d1e7137edd97579a92588f7e54cb | MIT |
| golang/vscode-go 28e27ffd | https://github.com/golang/vscode-go/tree/28e27ffd3a0eca1de30b1a9656a75cbd47e94e4e | MIT |
| kimsey0/iCalendar-sublime ee1aaa8a | https://github.com/kimsey0/iCalendar-sublime/tree/ee1aaa8a96cc72c45be5dbbb02ec8f994a414e23 | MIT |
| idris-hackers/idris-sublime c3a9f006 | https://github.com/idris-hackers/idris-sublime/tree/c3a9f006d8f717c3092fdc89f559710fbe4fa6f7 | MIT |
| kcl-lang/vscode-kcl 4eb7b145 | https://github.com/kcl-lang/vscode-kcl/tree/4eb7b1457f52deab3fe7d903525ae8a41748bc1b | Apache-2.0 |
| p0lygun/kivy-language-grammer d6ab305f | https://github.com/p0lygun/kivy-language-grammer/tree/d6ab305f30432a4603c547e92ab517504aaf4baf | MIT |
| sharktide/livescript-vscode 04b9ae5d | https://github.com/sharktide/livescript-vscode/tree/04b9ae5dc9f50caff43bcb1f09b66e093c8e8ba5 | Apache-2.0 |
| KrazIvan/LOLCODE-grammar-vscode 2091c60e | https://github.com/KrazIvan/LOLCODE-grammar-vscode/tree/2091c60ebe0b20e169853e5a0385b4bfb54c86f0 | MIT |
| MinecraftCommands/syntax-mcfunction cb179b5c | https://github.com/MinecraftCommands/syntax-mcfunction/tree/cb179b5cad5d5add8557f32bc77f2a714f409879 | MIT |
| mint-lang/mint-vscode 9b0c8b54 | https://github.com/mint-lang/mint-vscode/tree/9b0c8b54f80c9e32bb479ae8737b2c68ec275dd4 | MIT |
| leafo/moonscript-tmbundle cf69300f | https://github.com/leafo/moonscript-tmbundle/tree/cf69300f2a38d4c585bef4c5ad8543f5e258de8d | MIT |
| Southclaw/pawn-sublime-language b83dab34 | https://github.com/Southclaw/pawn-sublime-language/tree/b83dab341b1da7af10937a61d49349009c30f1ee | MIT |
| peggyjs/code-peggy-language 41bc8b61 | https://github.com/peggyjs/code-peggy-language/tree/41bc8b61364fc7c9f532852b872b35ae2508b834 | MIT |
| microsoft/vscode-python e0f1d3c8 | https://github.com/microsoft/vscode-python/tree/e0f1d3c80da21e0246b007990f6513fed9017080 | MIT |
| informaticon/PowerBuilder.tmbundle 054c6f74 | https://github.com/informaticon/PowerBuilder.tmbundle/tree/054c6f747f135465da5d889b283c55547837639a | MIT |
| textmate/processing.tmbundle 214b3420 | https://github.com/textmate/processing.tmbundle/tree/214b3420f12d65b125e82dc039d580326257f71b | TextMate Bundle License |
| benspaulding/vscode-procfile 4e149fd5 | https://github.com/benspaulding/vscode-procfile/tree/4e149fd5e757352aa5867b59934acc16e19182a1 | BSD-3-Clause |
| Oldes/Sublime-REBOL eb06bfce | https://github.com/Oldes/Sublime-REBOL/tree/eb06bfce0c612be9be0db24c472ada725204d380 | MIT |
| Oldes/Sublime-Red fb0c6992 | https://github.com/Oldes/Sublime-Red/tree/fb0c69926e2f966dab413f1c34a05f376317a319 | MIT |
| open-policy-agent/vscode-opa f5efd999 | https://github.com/open-policy-agent/vscode-opa/tree/f5efd999e33d8f94629520d4cccb35e768f785a2 | Apache-2.0 |
| rescript-lang/rescript-vscode f29d6a09 | https://github.com/rescript-lang/rescript-vscode/tree/f29d6a09b69d73c7759ca18adddcfe26b7c1f6d0 | MIT |
| riot/syntax-highlight 6ffd29ce | https://github.com/riot/syntax-highlight/tree/6ffd29ce303d73f2a838621beecc56525965c3b3 | MIT |
| shellderp/sublime-robot-plugin 07069ebf | https://github.com/shellderp/sublime-robot-plugin/tree/07069ebf20c82663eee24e4c7d82cccca020e8e9 | Apache-2.0 |
| shader-slang/slang-vscode-extension b03da15f | https://github.com/shader-slang/slang-vscode-extension/tree/b03da15f7475f53fd24389601dc774b32a4b89d0 | Apache-2.0 |
| borama/vscode-ruby-slim 098d948e | https://github.com/borama/vscode-ruby-slim/tree/098d948ef94014231fbcfc821633b4cb76760860 | MIT |
| ShaneWilton/sublime-smali 36add49d | https://github.com/ShaneWilton/sublime-smali/tree/36add49df8c7d8dde1d5cf0d68c4098183b2714f | MIT |
| textmate/php-smarty.tmbundle 38880466 | https://github.com/textmate/php-smarty.tmbundle/tree/3888046662241e02f1a7a39eb14bc4844acc082e | TextMate Bundle License |
| textmate/standard-ml.tmbundle b52a5ebf | https://github.com/textmate/standard-ml.tmbundle/tree/b52a5ebf528f89f19bead3f92b10f772ccda74aa | TextMate Bundle License |
| SRI-CSL/SMT.tmbundle 426d5f7e | https://github.com/SRI-CSL/SMT.tmbundle/tree/426d5f7e0a5303fedfff2465d30165dcb4fae675 | Unlicense |
| stan-dev/atom-language-stan 366d1f64 | https://github.com/stan-dev/atom-language-stan/tree/366d1f6408005b84527965069957fa0d7baef35b | MIT |
| hudochenkov/Syntax-highlighting-for-PostCSS 575b9189 | https://github.com/hudochenkov/Syntax-highlighting-for-PostCSS/tree/575b918985ce82545a12615a244243e41a91cd52 | MIT |
| griimick/vscode-vhs 8e1da076 | https://github.com/griimick/vscode-vhs/tree/8e1da07699e0e9c716a0b608a1b62297b86f28a6 | MIT |
| teal-language/vscode-teal 13802a7f | https://github.com/teal-language/vscode-teal/tree/13802a7f6b16248c82762fdd10ed9de3f737f3bf | MIT |
| uiua-lang/uiua-vscode 333e5b12 | https://github.com/uiua-lang/uiua-vscode/tree/333e5b12b27a9981f3e9028e7ebd5165b178e60a | MIT |
| fastly/vscode-fastly-vcl 01f09f53 | https://github.com/fastly/vscode-fastly-vcl/tree/01f09f5392d82d3544fe33e086d5fd718083d4c6 | MIT |
| simnJS/verse-grammar 3abf18ec | https://github.com/simnJS/verse-grammar/tree/3abf18ec6e8d9fcbbb15be8183cb5b2cb6a621b8 | MIT |
| Alhadis/language-agc 6fb398ee | https://github.com/Alhadis/language-agc/tree/6fb398ee1110d65f4c92c7644e8e6d896dc43300 | ISC |
| atom-haskell/language-haskell a273201b | https://github.com/atom-haskell/language-haskell/tree/a273201bd1ffd518a7700fb883d68b8fb60bcbf3 | MIT |
| Alhadis/language-etc 6aa793bd | https://github.com/Alhadis/language-etc/tree/6aa793bd649151119bd946673f8f027f0a9c99c8 | ISC |
| Alhadis/language-grammars daa8548c | https://github.com/Alhadis/language-grammars/tree/daa8548cc820359b078192dc85b6c8845e9db54c | ISC |
| Alhadis/Atom-PostScript 8f5309cc | https://github.com/Alhadis/Atom-PostScript/tree/8f5309cc78f76b1ce5f3e817d3749d5ee64d5212 | ISC |
| Alhadis/language-subtitles 9acc0aa2 | https://github.com/Alhadis/language-subtitles/tree/9acc0aa2c232a7b06aa0e8c1058ee5be81a98837 | ISC |
| jaysquared/atom-firestore-grammar 750ec2ab | https://github.com/jaysquared/atom-firestore-grammar/tree/750ec2abef42f7061c6bcc29ed5db3b0a1f3f773 | MIT |
| DenizenScript/denizenscript-grammar a9a95232 | https://github.com/DenizenScript/denizenscript-grammar/tree/a9a95232026192c0d5eb23557882f9b69a40d66e | MIT |
| mariozaizar/language-eml e92b829c | https://github.com/mariozaizar/language-eml/tree/e92b829c1e68710f1e2d5c039642e3a8ff06db6d | MIT |
| atom-crystal/language-crystal 48b87f54 | https://github.com/atom-crystal/language-crystal/tree/48b87f541ad787ffab5d4a4a1d366f5db16bca1a | MIT |
| nwhetsell/linter-lilypond eca5b0f9 | https://github.com/nwhetsell/linter-lilypond/tree/eca5b0f91b959d598b601c3dcdf2194a6b1e8e79 | MIT |
| Macaulay2/language-macaulay2 e3d0db2a | https://github.com/Macaulay2/language-macaulay2/tree/e3d0db2a52545b4ce04dd531fd73360bd1407adb | MIT |
| TingPing/language-meson 90269533 | https://github.com/TingPing/language-meson/tree/90269533120e84a9f5882346a8d646619e606b39 | Apache-2.0 |
| alohaas/language-nunjucks d865b2a6 | https://github.com/alohaas/language-nunjucks/tree/d865b2a667c53122e749b7d9698f1b2ac0258d98 | MIT |
| animecyc/AtomLanguageVelocity 6eb92de5 | https://github.com/animecyc/AtomLanguageVelocity/tree/6eb92de5a24991192192d768241be12337015884 | MIT |
| wcandillon/language-jsoniq 89a35154 | https://github.com/wcandillon/language-jsoniq/tree/89a3515410d82ef32995a1d713ece09bd15eb840 | Apache-2.0 |
| DzonyKalafut/language-yang c2d4a701 | https://github.com/DzonyKalafut/language-yang/tree/c2d4a701d15e0ea7a1cef4f2fe4396b336504d7c | MIT |
| hernad/atom-language-harbour ae860b3b | https://github.com/hernad/atom-language-harbour/tree/ae860b3b02d08bd65827c0a5f0029e65e4df30b4 | MIT |
| cdibbs/language-jison ce8e2c78 | https://github.com/cdibbs/language-jison/tree/ce8e2c78279b242a95b87e830a60dee55357aedc | MIT |
| qjebbs/vscode-plantuml 7bc1758e | https://github.com/qjebbs/vscode-plantuml/tree/7bc1758ed73dc269f5721d78c6c6c01f461d7cb0 | MIT |
| slint-ui/slint-tmLanguage 45bd6116 | https://github.com/slint-ui/slint-tmLanguage/tree/45bd6116b344fb2eebdf506fbda32a5cc57590a3 | MIT |
| Nixinova/NovaGrammars c6dfb955 | https://github.com/Nixinova/NovaGrammars/tree/c6dfb955f070c5aaf3ca7f6ee8d0b1802c6aed95 | ISC |
| jflex-de/jflex.tmbundle 8b5c0cef | https://github.com/jflex-de/jflex.tmbundle/tree/8b5c0cefc7672ec4d0a5f6d9095256252120fa44 | BSD-2-Clause |
| agda/agda-github-syntax-highlighting ba500584 | https://github.com/agda/agda-github-syntax-highlighting/tree/ba5005841ff9bdc5186af68befd58ed456fc7a8c | MIT |
| leanprover/vscode-lean4 5a25e6ab | https://github.com/leanprover/vscode-lean4/tree/5a25e6abb2e973b4c89a053acc74c479c0bb2e9f | Apache-2.0 |
| Alhadis/language-regexp 2e240580 | https://github.com/Alhadis/language-regexp/tree/2e240580b7e7046718b2c495d418f3ded972d618 | ISC |
| calculuswhiz/Assembly-Syntax-Definition 519b7b3c | https://github.com/calculuswhiz/Assembly-Syntax-Definition/tree/519b7b3c5f8428832dde72a90041d0707f831e5c | MIT |
| textmate/javadoc.tmbundle 5276d7a9 | https://github.com/textmate/javadoc.tmbundle/tree/5276d7a93f0cf53b7d425c39c6968b09ea9f2d40 | TextMate Bundle License |
| MagicStack/MagicPython 7d0f2b22 | https://github.com/MagicStack/MagicPython/tree/7d0f2b22a5ad8fccbd7341bc7b7a715169283044 | MIT |
| spgennard/vscode_cobol 8be1f9a9 | https://github.com/spgennard/vscode_cobol/tree/8be1f9a91a4a25c847ca2fe9dcc4793ead8bc347 | MIT |

| Language | Scope | Copied from | Upstream | Licence |
|---|---|---|---|---|
| `java` | `source.java` | Sora Editor 0.24.6 `java` | [atom/language-java@29f977dc](https://github.com/atom/language-java/commit/29f977dc42a7e2568b39bb6fb34c4ef108eb59b3) | MIT |
| `kotlin` | `source.kotlin` | Sora Editor 0.24.6 `kotlin` | None: Sora Editor 0.24.6 is the origin | LGPL-2.1-or-later |
| `python` | `source.python` | Sora Editor 0.24.6 `python` | [MagicStack/MagicPython@b2b4f4ae](https://github.com/MagicStack/MagicPython/commit/b2b4f4ae7b4e6284e80bda8080106b93bd588f9e) | MIT |
| `xml` | `text.xml` | Sora Editor 0.24.6 `xml` | [atom/language-xml@7bc75dfe](https://github.com/atom/language-xml/commit/7bc75dfe779ad5b35d9bf4013d9181864358cb49) | MIT |
| `html` | `text.html.basic` | Sora Editor 0.24.6 `html` | [textmate/html.tmbundle@0c3d5ee5](https://github.com/textmate/html.tmbundle/commit/0c3d5ee54de3a993f747f54186b73a4d2d3c44a2) | TextMate Bundle License |
| `javascript` | `source.js` | Sora Editor 0.24.6 `javascript` | [microsoft/TypeScript-TmLanguage@4d30ff83](https://github.com/microsoft/TypeScript-TmLanguage/commit/4d30ff834ec324f56291addd197aa1e423cedfdd) | MIT |
| `markdown` | `text.html.markdown` | Sora Editor 0.24.6 `markdown` | [microsoft/vscode-markdown-tm-grammar@09c3e715](https://github.com/microsoft/vscode-markdown-tm-grammar/commit/09c3e715102d08bba4c4ea828634474fc58b6f57) | MIT |
| `yaml` | `source.yaml` | VS Code 337cacaa `extensions/yaml` | [textmate/yaml.tmbundle@e54ceae3](https://github.com/textmate/yaml.tmbundle/commit/e54ceae3b719506dba7e481a77cea4a8b576ae46) | TextMate Bundle License |
| `bat` | `source.batchfile` | VS Code 307b60e7 `extensions/bat` | [mmims/language-batchfile@6154ae25](https://github.com/mmims/language-batchfile/commit/6154ae25a24e01ac9329e7bcf958e093cd8733a9) | MIT |
| `clojure` | `source.clojure` | VS Code 307b60e7 `extensions/clojure` | [atom/language-clojure@45bdb881](https://github.com/atom/language-clojure/commit/45bdb881501d0b8f8b707ca1d3fcc8b4b99fca03) | MIT |
| `coffeescript` | `source.coffee` | VS Code 307b60e7 `extensions/coffeescript` | [atom/language-coffee-script@0f6db914](https://github.com/atom/language-coffee-script/commit/0f6db9143663e18b1ad00667820f46747dba495e) | MIT |
| `c` | `source.c` | VS Code 307b60e7 `extensions/cpp` | [jeff-hykin/better-c-syntax@34712a61](https://github.com/jeff-hykin/better-c-syntax/commit/34712a6106a4ffb0a04d2fa836fd28ff6c5849a4) | MIT |
| `cpp-embedded-macro` | `source.cpp.embedded.macro` | VS Code 307b60e7 `extensions/cpp` | [jeff-hykin/better-cpp-syntax@071dd6ec](https://github.com/jeff-hykin/better-cpp-syntax/commit/071dd6ecc9eda347bd84c8aa0e0b557396cb6a40) | MIT |
| `cpp` | `source.cpp` | VS Code 307b60e7 `extensions/cpp` | [jeff-hykin/better-cpp-syntax@071dd6ec](https://github.com/jeff-hykin/better-cpp-syntax/commit/071dd6ecc9eda347bd84c8aa0e0b557396cb6a40) | MIT |
| `cuda-cpp` | `source.cuda-cpp` | VS Code 307b60e7 `extensions/cpp` | [NVIDIA/cuda-cpp-grammar@81e88eae](https://github.com/NVIDIA/cuda-cpp-grammar/commit/81e88eaec5170aa8585736c63627c73e3589998c) | MIT |
| `csharp` | `source.cs` | VS Code 307b60e7 `extensions/csharp` | [dotnet/csharp-tmLanguage@c2719309](https://github.com/dotnet/csharp-tmLanguage/commit/c2719309e91d2e26be21fce0ecaaca86c7b48ce4) | MIT |
| `css` | `source.css` | VS Code 307b60e7 `extensions/css` | [microsoft/vscode-css@de9e6bee](https://github.com/microsoft/vscode-css/commit/de9e6beee756760f31b15efbd782735fc25de3db) | MIT |
| `dart` | `source.dart` | VS Code 307b60e7 `extensions/dart` | [dart-lang/dart-syntax-highlight@b308b4b3](https://github.com/dart-lang/dart-syntax-highlight/commit/b308b4b3502c8639149e63d74844da51031b5f47) | BSD |
| `diff` | `source.diff` | VS Code 307b60e7 `extensions/diff` | [textmate/diff.tmbundle@0593bb77](https://github.com/textmate/diff.tmbundle/commit/0593bb775eab1824af97ef2172fd38822abd97d7) | TextMate Bundle License |
| `dockerfile` | `source.dockerfile` | VS Code 307b60e7 `extensions/docker` | [moby/moby@c2029cb2](https://github.com/moby/moby/commit/c2029cb2574647e4bc28ed58486b8e85883eedb9) | Apache-2.0 |
| `dotenv` | `source.dotenv` | VS Code 307b60e7 `extensions/dotenv` | [dotenv-org/dotenv-vscode@e7e41baa](https://github.com/dotenv-org/dotenv-vscode/commit/e7e41baa5b23e01c1ff0567a4e596c24860e7def) | MIT |
| `fsharp` | `source.fsharp` | VS Code 307b60e7 `extensions/fsharp` | [ionide/ionide-fsgrammar@0cb968a4](https://github.com/ionide/ionide-fsgrammar/commit/0cb968a4b8fdb2e0656b95342cdffbeff04a1248) | MIT |
| `git-commit` | `text.git-commit` | VS Code 307b60e7 `extensions/git-base` | [walles/git-commit-message-plus@35a079de](https://github.com/walles/git-commit-message-plus/commit/35a079dea5a91b087021b40c01a6bb4eb0337a87) | MIT |
| `git-rebase` | `text.git-rebase` | VS Code 307b60e7 `extensions/git-base` | [textmate/git.tmbundle@5870cf3f](https://github.com/textmate/git.tmbundle/commit/5870cf3f8abad3a6637bdf69250b5d2ded427dc4) | MIT |
| `ignore` | `source.ignore` | VS Code 307b60e7 `extensions/git-base` | None: VS Code 307b60e7 is the origin | MIT |
| `go` | `source.go` | VS Code 307b60e7 `extensions/go` | [worlpaker/go-syntax@c74e22eb](https://github.com/worlpaker/go-syntax/commit/c74e22eb9ef32958e3edd130ea750ce78d8b8241) | MIT |
| `groovy` | `source.groovy` | VS Code 307b60e7 `extensions/groovy` | [textmate/groovy.tmbundle@85d8f7c9](https://github.com/textmate/groovy.tmbundle/commit/85d8f7c97ae473ccb9473f6c8d27e4ec957f4be1) | TextMate Bundle License |
| `handlebars` | `text.html.handlebars` | VS Code 307b60e7 `extensions/handlebars` | [daaain/Handlebars@0ffd0a2d](https://github.com/daaain/Handlebars/commit/0ffd0a2d95bfc052c3ff29a3eb4dd3749957b466) | MIT |
| `hlsl` | `source.hlsl` | VS Code 307b60e7 `extensions/hlsl` | [tgjones/shaders-tmLanguage@87c0dca3](https://github.com/tgjones/shaders-tmLanguage/commit/87c0dca3a39170dbd7ee7e277db4f915fb2de14a) | MIT |
| `html-derivative` | `text.html.derivative` | VS Code 307b60e7 `extensions/html` | [textmate/html.tmbundle@390c8870](https://github.com/textmate/html.tmbundle/commit/390c8870273a2ae80244dae6db6ba064a802f407) | TextMate Bundle License |
| `ini` | `source.ini` | VS Code 307b60e7 `extensions/ini` | [textmate/ini.tmbundle@2af0cbb0](https://github.com/textmate/ini.tmbundle/commit/2af0cbb0704940f967152616f2f1ff0aae6287a6) | TextMate Bundle License |
| `javascriptreact` | `source.js.jsx` | VS Code 307b60e7 `extensions/javascript` | [microsoft/TypeScript-TmLanguage@48f60869](https://github.com/microsoft/TypeScript-TmLanguage/commit/48f608692aa6d6ad7bd65b478187906c798234a8) | MIT |
| `js-regexp` | `source.js.regexp` | VS Code 307b60e7 `extensions/javascript` | None: VS Code 307b60e7 is the origin | MIT |
| `json` | `source.json` | VS Code 307b60e7 `extensions/json` | [microsoft/vscode-JSON.tmLanguage@9bd83f1c](https://github.com/microsoft/vscode-JSON.tmLanguage/commit/9bd83f1c252b375e957203f21793316203f61f70) | MIT |
| `jsonc` | `source.json.comments` | VS Code 307b60e7 `extensions/json` | [microsoft/vscode-JSON.tmLanguage@9bd83f1c](https://github.com/microsoft/vscode-JSON.tmLanguage/commit/9bd83f1c252b375e957203f21793316203f61f70) | MIT |
| `jsonl` | `source.json.lines` | VS Code 307b60e7 `extensions/json` | [microsoft/vscode-JSON.tmLanguage@9bd83f1c](https://github.com/microsoft/vscode-JSON.tmLanguage/commit/9bd83f1c252b375e957203f21793316203f61f70) | MIT |
| `snippets` | `source.json.comments.snippets` | VS Code 307b60e7 `extensions/json` | [jeff-hykin/better-snippet-syntax@2b1bb124](https://github.com/jeff-hykin/better-snippet-syntax/commit/2b1bb124cb2b9c75c3c80eae1b8f3a043841d654) | MIT |
| `julia` | `source.julia` | VS Code 307b60e7 `extensions/julia` | [JuliaEditorSupport/atom-language-julia@dbaa9935](https://github.com/JuliaEditorSupport/atom-language-julia/commit/dbaa99358d1dbc2fc6c337f0f6571060dbc4ee1f) | MIT |
| `tex` | `text.tex` | VS Code 307b60e7 `extensions/latex` | [jlelong/vscode-latex-basics@76dc4093](https://github.com/jlelong/vscode-latex-basics/commit/76dc409348227db00f6779772f7763dc90cdf22e) | MIT |
| `latex` | `text.tex.latex` | VS Code 307b60e7 `extensions/latex` | [jlelong/vscode-latex-basics@843cd140](https://github.com/jlelong/vscode-latex-basics/commit/843cd14021feb45069fac66c6e1f98e02a196dea) | MIT |
| `bibtex` | `text.bibtex` | VS Code 307b60e7 `extensions/latex` | [jlelong/vscode-latex-basics@0fcf9283](https://github.com/jlelong/vscode-latex-basics/commit/0fcf9283828cab2aa611072f54feb1e7d501c2b4) | MIT |
| `markdown_latex_combined` | `text.tex.markdown_latex_combined` | VS Code 307b60e7 `extensions/latex` | [jlelong/vscode-latex-basics@8795ab7e](https://github.com/jlelong/vscode-latex-basics/commit/8795ab7e45ae21070a6914cb57d2f040fc49254f) | MIT |
| `cpp_embedded_latex` | `source.cpp.embedded.latex` | VS Code 307b60e7 `extensions/latex` | [jlelong/vscode-latex-basics@dfa69a16](https://github.com/jlelong/vscode-latex-basics/commit/dfa69a16a1154dbc820dc1111d72faa6954dd1e2) | MIT |
| `less` | `source.css.less` | VS Code 307b60e7 `extensions/less` | [radium-v/Better-Less@63c0cba9](https://github.com/radium-v/Better-Less/commit/63c0cba9792e49e255cce0f6dd03250fb30591e6) | MIT |
| `log` | `text.log` | VS Code 307b60e7 `extensions/log` | [emilast/vscode-logfile-highlighter@396c8552](https://github.com/emilast/vscode-logfile-highlighter/commit/396c8552ff99d0e07142fdd940d75dae212dd28c) | MIT |
| `lua` | `source.lua` | VS Code 307b60e7 `extensions/lua` | [sumneko/lua.tmbundle@b295d83b](https://github.com/sumneko/lua.tmbundle/commit/b295d83bf0e91b5d3a69eb097f9ed351623b95be) | TextMate Bundle License |
| `makefile` | `source.makefile` | VS Code 307b60e7 `extensions/make` | [fadeevab/make.tmbundle@1d4c0b54](https://github.com/fadeevab/make.tmbundle/commit/1d4c0b541959995db098df751ffc129da39a294b) | TextMate Bundle License |
| `markdown-math` | `text.html.markdown.math` | VS Code 307b60e7 `extensions/markdown-math` | None: VS Code 307b60e7 is the origin | MIT |
| `objective-c` | `source.objc` | VS Code 307b60e7 `extensions/objective-c` | [jeff-hykin/better-objc-syntax@119b75fb](https://github.com/jeff-hykin/better-objc-syntax/commit/119b75fb1f4d3e8726fa62588e3b935e0b719294) | MIT |
| `objective-cpp` | `source.objcpp` | VS Code 307b60e7 `extensions/objective-c` | [jeff-hykin/better-objcpp-syntax@5a7eb15e](https://github.com/jeff-hykin/better-objcpp-syntax/commit/5a7eb15eee382dd5aa388bc04fdb60a0d2128e14) | MIT |
| `perl` | `source.perl` | VS Code 307b60e7 `extensions/perl` | [textmate/perl.tmbundle@a85927a9](https://github.com/textmate/perl.tmbundle/commit/a85927a902d6e5d7805f56a653f324d34dfad53a) | TextMate Bundle License |
| `raku` | `source.perl.6` | VS Code 307b60e7 `extensions/perl` | [textmate/perl.tmbundle@d9841a08](https://github.com/textmate/perl.tmbundle/commit/d9841a0878239fa43f88c640f8d458590f97e8f5) | TextMate Bundle License |
| `php-source` | `source.php` | VS Code 307b60e7 `extensions/php` | [KapitanOczywisty/language-php@d94fd6b7](https://github.com/KapitanOczywisty/language-php/commit/d94fd6b7dff1613d34bc0d36328f9982e65bd545) | MIT |
| `php` | `text.html.php` | VS Code 307b60e7 `extensions/php` | [KapitanOczywisty/language-php@ff64523c](https://github.com/KapitanOczywisty/language-php/commit/ff64523c94c014d68f5dec189b05557649c5872a) | MIT |
| `powershell` | `source.powershell` | VS Code 307b60e7 `extensions/powershell` | [PowerShell/EditorSyntax@23eed76d](https://github.com/PowerShell/EditorSyntax/commit/23eed76d483c887a1608609e9dd57e09666411cc) | MIT |
| `prompt` | `text.html.markdown.prompt` | VS Code 307b60e7 `extensions/prompt-basics` | 0.1.0 | MIT |
| `jade` | `text.pug` | VS Code 307b60e7 `extensions/pug` | [davidrios/pug-tmbundle@ae1dd60c](https://github.com/davidrios/pug-tmbundle/commit/ae1dd60ca4aa4b45617f236d584216cd8d19eecf) | MIT |
| `regexp-python` | `source.regexp.python` | VS Code 307b60e7 `extensions/python` | [MagicStack/MagicPython@c9b3409d](https://github.com/MagicStack/MagicPython/commit/c9b3409deb69acec31bbf7913830e93a046b30cc) | MIT |
| `r` | `source.r` | VS Code 307b60e7 `extensions/r` | [REditorSupport/vscode-R-syntax@b1999960](https://github.com/REditorSupport/vscode-R-syntax/commit/b199996070723eefbe7a61e0384b2ae4768eb7f0) | MIT |
| `razor` | `text.html.cshtml` | VS Code 307b60e7 `extensions/razor` | [dotnet/roslyn@79e211a2](https://github.com/dotnet/roslyn/commit/79e211a2e4287f9f7508089b81311b2c7fdc169f) | MIT |
| `restructuredtext` | `source.rst` | VS Code 307b60e7 `extensions/restructuredtext` | [trond-snekvik/vscode-rst@7f2d6bb4](https://github.com/trond-snekvik/vscode-rst/commit/7f2d6bb4e20642b60f2979afcb594cfe4b48117a) | MIT |
| `ruby` | `source.ruby` | VS Code 307b60e7 `extensions/ruby` | [Shopify/ruby-lsp@e044408a](https://github.com/Shopify/ruby-lsp/commit/e044408a069f43c892143f19c389628eebffbd01) | MIT |
| `rust` | `source.rust` | VS Code 307b60e7 `extensions/rust` | [dustypomerleau/rust-syntax@ca34cf38](https://github.com/dustypomerleau/rust-syntax/commit/ca34cf382a7b250144f2ccb99ff9af96912c79f2) | MIT |
| `scss` | `source.css.scss` | VS Code 307b60e7 `extensions/scss` | [atom/language-sass@f52ab12f](https://github.com/atom/language-sass/commit/f52ab12f7f9346cc2568129d8c4419bd3d506b47) | MIT |
| `sassdoc` | `source.sassdoc` | VS Code 307b60e7 `extensions/scss` | [atom/language-sass@303bbf0c](https://github.com/atom/language-sass/commit/303bbf0c250fe380b9e57375598cfd916110758b) | MIT |
| `search-result` | `text.searchResult` | VS Code 307b60e7 `extensions/search-result` | None: VS Code 307b60e7 is the origin | MIT |
| `shaderlab` | `source.shaderlab` | VS Code 307b60e7 `extensions/shaderlab` | [tgjones/shaders-tmLanguage@c72c8b39](https://github.com/tgjones/shaders-tmLanguage/commit/c72c8b39380ba5a86c58ceed053b5d965ebf38b3) | MIT |
| `shellscript` | `source.shell` | VS Code 307b60e7 `extensions/shellscript` | [jeff-hykin/better-shell-syntax@35020b0b](https://github.com/jeff-hykin/better-shell-syntax/commit/35020b0bd79a90d3b262b4c13a8bb0b33adc1f45) | MIT |
| `sql` | `source.sql` | VS Code 307b60e7 `extensions/sql` | [microsoft/vscode-mssql@684648ee](https://github.com/microsoft/vscode-mssql/commit/684648ee5414eb2d9df22b0392ef05079f91e44e) | MIT |
| `swift` | `source.swift` | VS Code 307b60e7 `extensions/swift` | [jtbandes/swift-tmlanguage@2be007b6](https://github.com/jtbandes/swift-tmlanguage/commit/2be007b6bc88e54a0edfd3c313ff31276edc923d) | MIT |
| `typescript` | `source.ts` | VS Code 307b60e7 `extensions/typescript-basics` | [microsoft/TypeScript-TmLanguage@48f60869](https://github.com/microsoft/TypeScript-TmLanguage/commit/48f608692aa6d6ad7bd65b478187906c798234a8) | MIT |
| `typescriptreact` | `source.tsx` | VS Code 307b60e7 `extensions/typescript-basics` | [microsoft/TypeScript-TmLanguage@48f60869](https://github.com/microsoft/TypeScript-TmLanguage/commit/48f608692aa6d6ad7bd65b478187906c798234a8) | MIT |
| `vb` | `source.asp.vb.net` | VS Code 307b60e7 `extensions/vb` | [textmate/asp.vb.net.tmbundle@72d44550](https://github.com/textmate/asp.vb.net.tmbundle/commit/72d44550b3286d0382d7be0624140cf97857ff69) | TextMate Bundle License |
| `xsl` | `text.xml.xsl` | VS Code 307b60e7 `extensions/xml` | [atom/language-xml@507de2ee](https://github.com/atom/language-xml/commit/507de2ee7daca60cf02e9e21fbeb92bbae73e280) | MIT |
| `abap` | `source.abap` | Shiki tm-grammars 8f1828eb `grammars/abap.json` | [pvl/abap.tmbundle@92e17e62](https://github.com/pvl/abap.tmbundle/blob/92e17e62a9449d2a6f87bfb30b4f920986c24018/Syntaxes/ABAP.tmLanguage) | TextMate Bundle License |
| `actionscript-3` | `source.actionscript.3` | Shiki tm-grammars 8f1828eb `grammars/actionscript-3.json` | [BowlerHatLLC/vscode-as3mxml@11ccf88d](https://github.com/BowlerHatLLC/vscode-as3mxml/blob/11ccf88dc1ec08a55c262a96676d507b354f19b5/distribution/src/assembly/syntaxes/AS3.tmLanguage) | Apache-2.0 |
| `ada` | `source.ada` | Shiki tm-grammars 8f1828eb `grammars/ada.json` | [AdaCore/ada_language_server@e984d223](https://github.com/AdaCore/ada_language_server/blob/e984d2233630f0a3ea422e86bfa97b36f80fe038/integration/vscode/ada/advanced/ada.tmLanguage.json) | GPL-3.0 |
| `ahk` | `source.ahk` | Shiki tm-grammars 8f1828eb `grammars/ahk.json` | [mark-wiemer/ahkpp@63c1fd4d](https://github.com/mark-wiemer/ahkpp/blob/63c1fd4d675e9ea98f60083786afe94505bdd57b/language/ahk.tmLanguage.yaml) | MIT |
| `ahk2` | `source.ahk2` | Shiki tm-grammars 8f1828eb `grammars/ahk2.json` | [thqby/vscode-autohotkey2-lsp@60b30aa8](https://github.com/thqby/vscode-autohotkey2-lsp/blob/60b30aa89671198bca0d3ba9777008f8c2b2983e/syntaxes/ahk2.tmLanguage.json) | LGPL-3.0 |
| `angular-html` | `text.html.derivative.ng` | Shiki tm-grammars 8f1828eb `grammars/angular-html.json` | [onivim/vscode-exthost@cfc2a221](https://github.com/onivim/vscode-exthost/blob/cfc2a2212de9ea10943af58ebd1817a5ad196463/extensions/html/syntaxes/html-derivative.tmLanguage.json) | MIT |
| `angular-ts` | `source.ts.ng` | Shiki tm-grammars 8f1828eb `grammars/angular-ts.json` | [microsoft/vscode@21054190](https://github.com/microsoft/vscode/blob/210541906e5a96ab39f9c753f921b1bd35f4138b/extensions/typescript-basics/syntaxes/TypeScript.tmLanguage.json) | MIT |
| `apache` | `source.apacheconf` | Shiki tm-grammars 8f1828eb `grammars/apache.json` | [colinta/ApacheConf.tmLanguage@1fdb47b6](https://github.com/colinta/ApacheConf.tmLanguage/blob/1fdb47b6fe2cea20cfedbc522bff3ff1ed656c07/ApacheConf.tmLanguage) | BSD-2-Clause |
| `apex` | `source.apex` | Shiki tm-grammars 8f1828eb `grammars/apex.json` | [forcedotcom/apex-tmLanguage@611becc6](https://github.com/forcedotcom/apex-tmLanguage/blob/611becc66c4553876ac6fd0287f57135581e2e59/grammars/apex.tmLanguage) | BSD-3-Clause |
| `apl` | `source.apl` | Shiki tm-grammars 8f1828eb `grammars/apl.json` | [kimmolinna/vscode-apl-language@13541242](https://github.com/kimmolinna/vscode-apl-language/blob/13541242d9ffae225e89ebba23f9abaa4c60697e/syntaxes/apl.tmLanguage.json) | ISC OR MIT |
| `applescript` | `source.applescript` | Shiki tm-grammars 8f1828eb `grammars/applescript.json` | [textmate/applescript.tmbundle@4947cad7](https://github.com/textmate/applescript.tmbundle/blob/4947cad7494eba084c21cfe04689b4efce96700f/Syntaxes/AppleScript.tmLanguage) | TextMate Bundle License |
| `ara` | `source.ara` | Shiki tm-grammars 8f1828eb `grammars/ara.json` | [ara-lang/highlighting@ed1dbedc](https://github.com/ara-lang/highlighting/blob/ed1dbedcf03d0b67207e41fe2e11218cc925dc0d/syntaxes/ara.json) | Apache-2.0 |
| `asciidoc` | `text.asciidoc` | Shiki tm-grammars 8f1828eb `grammars/asciidoc.json` | [asciidoctor/asciidoctor-vscode@09c406fc](https://github.com/asciidoctor/asciidoctor-vscode/blob/09c406fcd67a300cfc1f49267c70cc97db978986/syntaxes/asciidoc.tmLanguage.json) | MIT |
| `asm` | `source.asm.x86_64` | Shiki tm-grammars 8f1828eb `grammars/asm.json` | [13xforever/x86_64-assembly-vscode@5c1958f4](https://github.com/13xforever/x86_64-assembly-vscode/blob/5c1958f4283d260f5dc88fa4f5f31c620e061a5a/syntaxes/language-x86_64-assembly.tmLanguage) | MIT |
| `astro` | `source.astro` | Shiki tm-grammars 8f1828eb `grammars/astro.json` | [withastro/language-tools@8e55c37c](https://github.com/withastro/language-tools/blob/8e55c37cbcf134dedf0068781245da5a9d46d081/packages/vscode/syntaxes/astro.tmLanguage.src.yaml) | MIT |
| `awk` | `source.awk` | Shiki tm-grammars 8f1828eb `grammars/awk.json` | [luggage66/vscode-awk@eff8c0f1](https://github.com/luggage66/vscode-awk/blob/eff8c0f160ae16592c8d9963fd77046137fd2612/syntaxes/awk.tmLanguage) | MIT |
| `ballerina` | `source.ballerina` | Shiki tm-grammars 8f1828eb `grammars/ballerina.json` | [ballerina-platform/ballerina-grammar@4bf70d32](https://github.com/ballerina-platform/ballerina-grammar/blob/4bf70d328ae9e5a93219e44c031ab3776c76727a/syntaxes/ballerina.tmLanguage) | Apache-2.0 |
| `beancount` | `text.beancount` | Shiki tm-grammars 8f1828eb `grammars/beancount.json` | [Lencerf/vscode-beancount@60dcdbd3](https://github.com/Lencerf/vscode-beancount/blob/60dcdbd3af273aca0ff628220f2828483101ca47/syntaxes/beancount.tmLanguage) | MIT |
| `berry` | `source.berry` | Shiki tm-grammars 8f1828eb `grammars/berry.json` | [berry-lang/berry@74611286](https://github.com/berry-lang/berry/blob/74611286b8d2d2c2ee16ea187029f2a9c4257f5f/tools/plugins/vscode/skiars.berry-1.2.0/syntaxes/berry.json) | MIT |
| `bicep` | `source.bicep` | Shiki tm-grammars 8f1828eb `grammars/bicep.json` | [Azure/bicep@6252abb9](https://github.com/Azure/bicep/blob/6252abb94141fc6e774a53652404be30f1cb9000/src/textmate/bicep.tmlanguage) | MIT |
| `bird2` | `source.bird2` | Shiki tm-grammars 8f1828eb `grammars/bird2.json` | [bird-chinese-community/BIRD-tm-language-grammar@42fec06a](https://github.com/bird-chinese-community/BIRD-tm-language-grammar/blob/42fec06af41a569aa4c7d055ed9751603354b5e4/grammars/bird2.tmLanguage.json) | MPL-2.0 |
| `blade` | `text.html.php.blade` | Shiki tm-grammars 8f1828eb `grammars/blade.json` | [laravel/vs-code-extension@80726f64](https://github.com/laravel/vs-code-extension/blob/80726f64975dc935d6b925d8230cdc1ea075802a/syntaxes/blade.tmLanguage.json) | MIT |
| `bsl` | `source.bsl` | Shiki tm-grammars 8f1828eb `grammars/bsl.json` | [1c-syntax/vsc-language-1c-bsl@c93f87c7](https://github.com/1c-syntax/vsc-language-1c-bsl/blob/c93f87c76d8a536a494039e9dc78a684b14b4e4c/syntaxes/1c.tmLanguage.json) | MIT |
| `c3` | `source.c3` | Shiki tm-grammars 8f1828eb `grammars/c3.json` | [c3lang/vscode-c3@38dcc9ae](https://github.com/c3lang/vscode-c3/blob/38dcc9ae665da32dea3e391f342478668d44e16f/syntaxes/c3.tmLanguage.json) | MIT |
| `cadence` | `source.cadence` | Shiki tm-grammars 8f1828eb `grammars/cadence.json` | [onflow/vscode-cadence@bc7e5d3b](https://github.com/onflow/vscode-cadence/blob/bc7e5d3bb613e01789584283d7911cfd6b616dc4/extension/language/syntaxes/cadence.tmGrammar.json) | Apache-2.0 |
| `cairo` | `source.cairo0` | Shiki tm-grammars 8f1828eb `grammars/cairo.json` | [starkware-libs/cairo-lang@4ea4fe8e](https://github.com/starkware-libs/cairo-lang/blob/4ea4fe8e167845a3402ae2ea0a8b6004aad18dd5/src/starkware/cairo/lang/ide/vscode-cairo/syntaxes/cairo.tmLanguage.json) | Apache-2.0 |
| `chapel` | `source.chapel` | Shiki tm-grammars 8f1828eb `grammars/chapel.json` | [chapel-lang/chapel-vscode@c933a6c3](https://github.com/chapel-lang/chapel-vscode/blob/c933a6c379df844d564644ee6c6df4e6055ceb33/syntaxes/chapel.tmLanguage.json) | Apache-2.0 |
| `clarity` | `source.clar` | Shiki tm-grammars 8f1828eb `grammars/clarity.json` | [hirosystems/clarity.tmbundle@623ab02a](https://github.com/hirosystems/clarity.tmbundle/blob/623ab02ac06f3fc27172503f64726bf0b107e5ee/Syntaxes/clarity.JSON-tmLanguage) | MIT |
| `cmake` | `source.cmake` | Shiki tm-grammars 8f1828eb `grammars/cmake.json` | [twxs/vs.language.cmake@19b1b8fe](https://github.com/twxs/vs.language.cmake/blob/19b1b8fe2d68d72cf294d0c3c22f0a3fc54f8f6f/syntaxes/CMake.tmLanguage) | MIT |
| `cobol` | `source.cobol` | Shiki tm-grammars 8f1828eb `grammars/cobol.json` | [spgennard/vscode_cobol@7020540c](https://github.com/spgennard/vscode_cobol/blob/7020540cc77e04fb2edc8503f906d51fa840bbe5/syntaxes/COBOL.tmLanguage.json) | MIT |
| `codeowners` | `text.codeowners` | Shiki tm-grammars 8f1828eb `grammars/codeowners.json` | [jasonnutter/vscode-codeowners@219dee0a](https://github.com/jasonnutter/vscode-codeowners/blob/219dee0ab25c1bfad0b9de84180117e71221a653/syntaxes/codeowners.tmLanguage.json) | MIT |
| `codeql` | `source.ql` | Shiki tm-grammars 8f1828eb `grammars/codeql.json` | [github/vscode-codeql@a6266bbc](https://github.com/github/vscode-codeql/blob/a6266bbcc88702619ceb580253a65e8f83333c1b/syntaxes/ql.tmLanguage.json) | MIT |
| `common-lisp` | `source.commonlisp` | Shiki tm-grammars 8f1828eb `grammars/common-lisp.json` | [qingpeng9802/vscode-common-lisp@79d04dc9](https://github.com/qingpeng9802/vscode-common-lisp/blob/79d04dc9ca80d98e65c4f28b3d67a24a7829b18f/syntaxes/commonlisp.tmLanguage.json) | MIT |
| `coq` | `source.rocq` | Shiki tm-grammars 8f1828eb `grammars/coq.json` | [rocq-prover/vsrocq@76f6daf4](https://github.com/rocq-prover/vsrocq/blob/76f6daf4589f6617019e7500d5c2acb327578b44/client/syntax/rocq.tmLanguage.json) | MIT |
| `crystal` | `source.crystal` | Shiki tm-grammars 8f1828eb `grammars/crystal.json` | [crystal-lang-tools/vscode-crystal-lang@c920b093](https://github.com/crystal-lang-tools/vscode-crystal-lang/blob/c920b0931ec5f05b78447dbff31b172c64f1d734/syntaxes/crystal.json) | MIT |
| `csv` | `text.csv` | Shiki tm-grammars 8f1828eb `grammars/csv.json` | [mechatroner/vscode_rainbow_csv@3bdafafa](https://github.com/mechatroner/vscode_rainbow_csv/blob/3bdafafa12940a75166089b64bdd80b1d3b071db/syntaxes/csv.tmLanguage.json) | MIT |
| `cue` | `source.cue` | Shiki tm-grammars 8f1828eb `grammars/cue.json` | [cue-lang/vscode-cue@a9ee96a7](https://github.com/cue-lang/vscode-cue/blob/a9ee96a7e61fb8acd39f7403144d0b6f45652987/extension/syntaxes/cue.tmLanguage.json) | MIT |
| `cypher` | `source.cypher` | Shiki tm-grammars 8f1828eb `grammars/cypher.json` | [adam-cowley/neo4j-vscode@ad90d275](https://github.com/adam-cowley/neo4j-vscode/blob/ad90d275ac76c740018e78f78722343f730270e0/cypher/cypher.tmLanguage) | MIT |
| `d` | `source.d` | Shiki tm-grammars 8f1828eb `grammars/d.json` | [Pure-D/code-d@71992a1a](https://github.com/Pure-D/code-d/blob/71992a1a52a3025f3333e4025eee0f255a8cda03/syntaxes/d.json) | MIT |
| `desktop` | `source.desktop` | Shiki tm-grammars 8f1828eb `grammars/desktop.json` | [nico-castell/desktop-file-support@63720f5f](https://github.com/nico-castell/desktop-file-support/blob/63720f5f05313de22b68ecbd0ca3d3ed8ba57e7e/syntaxes/desktop.tmLanguage.json) | MIT |
| `edge` | `text.html.edge` | Shiki tm-grammars 8f1828eb `grammars/edge.json` | [edge-js/edge-vscode@04352c41](https://github.com/edge-js/edge-vscode/blob/04352c41981ca664598674556afa63bd393a10f3/syntaxes/edge.tmLanguage.json) | MIT |
| `elixir` | `source.elixir` | Shiki tm-grammars 8f1828eb `grammars/elixir.json` | [elixir-editors/elixir-tmbundle@43c8cd95](https://github.com/elixir-editors/elixir-tmbundle/blob/43c8cd957d5ac6e1abbd8730fc7a08c81a6e76c9/Syntaxes/Elixir.tmLanguage) | Apache-2.0 |
| `elm` | `source.elm` | Shiki tm-grammars 8f1828eb `grammars/elm.json` | [elm-tooling/elm-language-client-vscode@c7035131](https://github.com/elm-tooling/elm-language-client-vscode/blob/c70351314682c2e0fae8a402ebb301da79821c37/syntaxes/elm-syntax.json) | MIT |
| `emacs-lisp` | `source.emacs.lisp` | Shiki tm-grammars 8f1828eb `grammars/emacs-lisp.json` | [Alhadis/language-emacs-lisp@b38b69f6](https://github.com/Alhadis/language-emacs-lisp/blob/b38b69f684e7750b35f370fe567e7d9431f5fbc8/grammars/emacs-lisp.cson) | ISC |
| `erb` | `text.html.erb` | Shiki tm-grammars 8f1828eb `grammars/erb.json` | [textmate/ruby.tmbundle@ecf0f1df](https://github.com/textmate/ruby.tmbundle/blob/ecf0f1df23984bcff9b51552e35d9696cb0d9539/Syntaxes/HTML%20(Ruby%20-%20ERB).tmLanguage) | TextMate Bundle License |
| `erlang` | `source.erlang` | Shiki tm-grammars 8f1828eb `grammars/erlang.json` | [erlang-ls/grammar@fe898bec](https://github.com/erlang-ls/grammar/blob/fe898becd3004bcb4947ab50d6b6b830fbfb9228/Erlang.plist) | Apache-2.0 |
| `fennel` | `source.fnl` | Shiki tm-grammars 8f1828eb `grammars/fennel.json` | [kongeor/vsc-fennel@66414892](https://github.com/kongeor/vsc-fennel/blob/664148923d7bd72531120d0cba712c6dba6f698d/syntaxes/fennel.tmLanguage.json) | MIT |
| `fish` | `source.fish` | Shiki tm-grammars 8f1828eb `grammars/fish.json` | [bmalehorn/vscode-fish@8288b88c](https://github.com/bmalehorn/vscode-fish/blob/8288b88c46ebe1ef2876fd4275450fc80c5a176a/syntaxes/fish.tmLanguage.json) | MIT |
| `fluent` | `source.ftl` | Shiki tm-grammars 8f1828eb `grammars/fluent.json` | [macabeus/vscode-fluent@8544ea29](https://github.com/macabeus/vscode-fluent/blob/8544ea296a7769a359f43ddff4dc4b6c61f5cf59/syntaxes/fluent.tmLanguage.json) | MIT |
| `fortran-fixed-form` | `source.fortran.fixed` | Shiki tm-grammars 8f1828eb `grammars/fortran-fixed-form.json` | [fortran-lang/vscode-fortran-support@002eaa90](https://github.com/fortran-lang/vscode-fortran-support/blob/002eaa90668fd7d63cd073e8ca21b1388357ed62/syntaxes/fortran_fixed-form.tmLanguage.json) | MIT |
| `fortran-free-form` | `source.fortran.free` | Shiki tm-grammars 8f1828eb `grammars/fortran-free-form.json` | [fortran-lang/vscode-fortran-support@f5fbf935](https://github.com/fortran-lang/vscode-fortran-support/blob/f5fbf935d709e165922b307885a6106e32624018/syntaxes/fortran_free-form.tmLanguage.json) | MIT |
| `gdresource` | `source.gdresource` | Shiki tm-grammars 8f1828eb `grammars/gdresource.json` | [godotengine/godot-vscode-plugin@e7e2d043](https://github.com/godotengine/godot-vscode-plugin/blob/e7e2d0439dea0fb3a568378554c81e0a17348a84/syntaxes/GDResource.tmLanguage.json) | MIT |
| `gdscript` | `source.gdscript` | Shiki tm-grammars 8f1828eb `grammars/gdscript.json` | [godotengine/godot-vscode-plugin@3f2860fd](https://github.com/godotengine/godot-vscode-plugin/blob/3f2860fd76c591a6010f0d35220490933e41d01d/syntaxes/GDScript.tmLanguage.json) | MIT |
| `gdshader` | `source.gdshader` | Shiki tm-grammars 8f1828eb `grammars/gdshader.json` | [godotengine/godot-vscode-plugin@e7e2d043](https://github.com/godotengine/godot-vscode-plugin/blob/e7e2d0439dea0fb3a568378554c81e0a17348a84/syntaxes/GDShader.tmLanguage.json) | MIT |
| `genie` | `source.genie` | Shiki tm-grammars 8f1828eb `grammars/genie.json` | [vala-lang/vala-vscode@8f825e95](https://github.com/vala-lang/vala-vscode/blob/8f825e95648326df2572d0522632251cdff4a43b/syntaxes/genie.YAML-tmLanguage) | MIT |
| `gherkin` | `text.gherkin.feature` | Shiki tm-grammars 8f1828eb `grammars/gherkin.json` | [alexkrechik/VSCucumberAutoComplete@fbb554ad](https://github.com/alexkrechik/VSCucumberAutoComplete/blob/fbb554addf81e8961b58b1577924af10fecf9694/syntaxes/feature.tmLanguage.json) | MIT |
| `gleam` | `source.gleam` | Shiki tm-grammars 8f1828eb `grammars/gleam.json` | [gleam-lang/vscode-gleam@aa487ed8](https://github.com/gleam-lang/vscode-gleam/blob/aa487ed82aeaec8dbac62e796689daf55abf1aa9/syntaxes/gleam.tmLanguage.json) | Apache-2.0 |
| `glimmer-js` | `source.gjs` | Shiki tm-grammars 8f1828eb `grammars/glimmer-js.json` | [lifeart/vsc-ember-syntax@76fce7b3](https://github.com/lifeart/vsc-ember-syntax/blob/76fce7b360ce09a78789e7d5217b8955d1a5d95f/syntaxes/source.gjs.json) | MIT |
| `glimmer-ts` | `source.gts` | Shiki tm-grammars 8f1828eb `grammars/glimmer-ts.json` | [lifeart/vsc-ember-syntax@76fce7b3](https://github.com/lifeart/vsc-ember-syntax/blob/76fce7b360ce09a78789e7d5217b8955d1a5d95f/syntaxes/source.gts.json) | MIT |
| `gn` | `source.gn` | Shiki tm-grammars 8f1828eb `grammars/gn.json` | [google/gn-language-server@a77b9690](https://github.com/google/gn-language-server/blob/a77b96900dd5ad6395294e34c2c5833cc89d5af0/vscode-gn/syntaxes/gn.tmLanguage.json) | Apache-2.0 |
| `gnuplot` | `source.gnuplot` | Shiki tm-grammars 8f1828eb `grammars/gnuplot.json` | [MarioSchwalbe/vscode-gnuplot@c62c1a61](https://github.com/MarioSchwalbe/vscode-gnuplot/blob/c62c1a61c9b6b4658b43d76c397dc2ad43523b6a/syntaxes/gnuplot.tmLanguage) | GPL-3.0 |
| `graphql` | `source.graphql` | Shiki tm-grammars 8f1828eb `grammars/graphql.json` | [prisma-labs/vscode-graphql@4569aef2](https://github.com/prisma-labs/vscode-graphql/blob/4569aef2364b42b6acd8188e4a6ca9b0ba3c6a1d/grammars/graphql.json) | MIT |
| `hack` | `source.hack` | Shiki tm-grammars 8f1828eb `grammars/hack.json` | [slackhq/vscode-hack@f92d6a49](https://github.com/slackhq/vscode-hack/blob/f92d6a49ce03a4ed16d342e6bd9e8be65af0b623/syntaxes/hack.json) | MIT |
| `haml` | `text.haml` | Shiki tm-grammars 8f1828eb `grammars/haml.json` | [karuna/haml-vscode@aa45415a](https://github.com/karuna/haml-vscode/blob/aa45415aefd691af0a501ea4f98e434490623e0d/syntaxes/haml.json) | MIT |
| `haskell` | `source.haskell` | Shiki tm-grammars 8f1828eb `grammars/haskell.json` | [octref/language-haskell@ae87982a](https://github.com/octref/language-haskell/blob/ae87982aac2bf5cf6334c067e49c5ec8fc08b871/syntaxes/haskell.json) | BSD-3-Clause |
| `haxe` | `source.hx` | Shiki tm-grammars 8f1828eb `grammars/haxe.json` | [vshaxe/haxe-TmLanguage@7f2e6fed](https://github.com/vshaxe/haxe-TmLanguage/blob/7f2e6feda8aa74ae5edebb04607f63f762bfc365/haxe.YAML-tmLanguage) | MIT |
| `hcl` | `source.hcl` | Shiki tm-grammars 8f1828eb `grammars/hcl.json` | [hashicorp/syntax@2ec7cd10](https://github.com/hashicorp/syntax/blob/2ec7cd10c9d6134e836bf85eb0f5aa010512bff4/syntaxes/hcl.tmGrammar.json) | MPL-2.0 |
| `hjson` | `source.hjson` | Shiki tm-grammars 8f1828eb `grammars/hjson.json` | [hjson/textmate-hjson@19607516](https://github.com/hjson/textmate-hjson/blob/196075169c9a9f9ab04c8e7423b67ea72c976237/Syntaxes/Hjson.tmLanguage) | MIT |
| `http` | `source.http` | Shiki tm-grammars 8f1828eb `grammars/http.json` | [Huachao/vscode-restclient@6649589e](https://github.com/Huachao/vscode-restclient/blob/6649589e4d3f97d9c0d68a4f7ffe03fa2919c4df/syntaxes/http.tmLanguage.json) | MIT |
| `hurl` | `source.hurl` | Shiki tm-grammars 8f1828eb `grammars/hurl.json` | [nikeee/language-hurl@dec5d90b](https://github.com/nikeee/language-hurl/blob/dec5d90bea8a13d21fd8365d5a0d2d35453653c1/grammars/hurl.tmlanguage.json) | MIT |
| `hxml` | `source.hxml` | Shiki tm-grammars 8f1828eb `grammars/hxml.json` | [vshaxe/haxe-TmLanguage@365040ca](https://github.com/vshaxe/haxe-TmLanguage/blob/365040ca52f480c5a9d7bfe87aaaead06fc08fed/hxml.YAML-tmLanguage) | MIT |
| `hy` | `source.hy` | Shiki tm-grammars 8f1828eb `grammars/hy.json` | [hylang/vscode-hy@8f996307](https://github.com/hylang/vscode-hy/blob/8f99630793b7483cf4cac10a655da6fed08a4f9f/syntaxes/hy.tmLanguage.json) | MIT |
| `imba` | `source.imba` | Shiki tm-grammars 8f1828eb `grammars/imba.json` | [imba/imba@18364351](https://github.com/imba/imba/blob/183643518e567f0e125046b47b6d81f1aa7bdbb1/packages/vscode-imba/syntaxes/imba.tmLanguage.json) | MIT |
| `jinja` | `source.jinja` | Shiki tm-grammars 8f1828eb `grammars/jinja.json` | [samuelcolvin/jinjahtml-vscode@be729c88](https://github.com/samuelcolvin/jinjahtml-vscode/blob/be729c88f723602e7b5df2e9dc5e8c016c31c38f/syntaxes/jinja.tmLanguage.json) | MIT |
| `jison` | `source.jison` | Shiki tm-grammars 8f1828eb `grammars/jison.json` | [cdibbs/language-jison@9d4cb4b0](https://github.com/cdibbs/language-jison/blob/9d4cb4b044832320a51fabb6e533fc299425f552/grammars/jison.cson) | MIT |
| `json5` | `source.json5` | Shiki tm-grammars 8f1828eb `grammars/json5.json` | [mrmlnc/vscode-json5@e884b899](https://github.com/mrmlnc/vscode-json5/blob/e884b89929103ff009322c393080ff45a4544e88/syntaxes/json5.json) | MIT |
| `jsonnet` | `source.jsonnet` | Shiki tm-grammars 8f1828eb `grammars/jsonnet.json` | [heptio/vscode-jsonnet@39088f9c](https://github.com/heptio/vscode-jsonnet/blob/39088f9cde9f483c8e0efeb67a3fd561cd873aad/syntaxes/jsonnet.tmLanguage.json) | Apache-2.0 |
| `jssm` | `source.jssm` | Shiki tm-grammars 8f1828eb `grammars/jssm.json` | [StoneCypher/sublime-jssm@2be02a59](https://github.com/StoneCypher/sublime-jssm/blob/2be02a59d284229bbbfb3dc6c2bdcec9b2c263ef/jssm.tmLanguage) | MIT |
| `just` | `source.just` | Shiki tm-grammars 8f1828eb `grammars/just.json` | [nefrob/vscode-just@22539731](https://github.com/nefrob/vscode-just/blob/2253973135a5cdc34e44df876fecd30aff605150/syntaxes/just.tmLanguage.json) | MIT |
| `kdl` | `source.kdl` | Shiki tm-grammars 8f1828eb `grammars/kdl.json` | [kdl-org/vscode-kdl@b986286d](https://github.com/kdl-org/vscode-kdl/blob/b986286d526d3dba7f5121956a52ce300c3418f0/syntaxes/kdl.tmLanguage.json) | Apache-2.0 |
| `kusto` | `source.kusto` | Shiki tm-grammars 8f1828eb `grammars/kusto.json` | [rosshamish/kuskus@2a5d25aa](https://github.com/rosshamish/kuskus/blob/2a5d25aa9e1680b90e77b2477d710d11af8fcd88/kusto-syntax-highlighting/syntaxes/kusto.tmLanguage.json) | Apache-2.0 |
| `lean` | `source.lean4` | Shiki tm-grammars 8f1828eb `grammars/lean.json` | [leanprover/vscode-lean4@2ca585c4](https://github.com/leanprover/vscode-lean4/blob/2ca585c47d447682452bd72f528feffd03ef4971/vscode-lean4/syntaxes/lean4.json) | Apache-2.0 |
| `liquid` | `text.html.liquid` | Shiki tm-grammars 8f1828eb `grammars/liquid.json` | [Shopify/liquid-tm-grammar@099adcf0](https://github.com/Shopify/liquid-tm-grammar/blob/099adcf0d4beacd078a22432eaeab08d6f1e6da5/grammars/liquid.tmLanguage.json) | MIT |
| `llvm` | `source.llvm` | Shiki tm-grammars 8f1828eb `grammars/llvm.json` | [llvm/llvm-project@c07bc296](https://github.com/llvm/llvm-project/blob/c07bc2962c5f76ebcbd040c4e97fe5d129530087/llvm/utils/vscode/llvm/syntaxes/ll.tmLanguage.yaml) | Apache-2.0 WITH LLVM-exception |
| `logo` | `source.logo` | Shiki tm-grammars 8f1828eb `grammars/logo.json` | [textmate/logo.tmbundle@3205e987](https://github.com/textmate/logo.tmbundle/blob/3205e987ed2a2aa11accdd55a7d094832441f89c/Syntaxes/Logo.tmLanguage) | TextMate Bundle License |
| `luau` | `source.luau` | Shiki tm-grammars 8f1828eb `grammars/luau.json` | [JohnnyMorganz/Luau.tmLanguage@25653377](https://github.com/JohnnyMorganz/Luau.tmLanguage/blob/2565337787d6d4df5cdfdc14bfb25521d55b7c4e/Luau.tmLanguage) | MIT |
| `marko` | `text.marko` | Shiki tm-grammars 8f1828eb `grammars/marko.json` | [marko-js/language-server@9b627df2](https://github.com/marko-js/language-server/blob/9b627df21b63f9aeecd04780b9336ac3b734fa17/packages/vscode/syntaxes/marko.tmLanguage.json) | MIT |
| `matlab` | `source.matlab` | Shiki tm-grammars 8f1828eb `grammars/matlab.json` | [mathworks/MATLAB-Language-grammar@69dbf20c](https://github.com/mathworks/MATLAB-Language-grammar/blob/69dbf20c1eb19efd629c0438a2a493e60617e8de/Matlab.tmbundle/Syntaxes/MATLAB.tmLanguage) | BSD-2-Clause |
| `mdc` | `text.markdown.mdc.standalone` | Shiki tm-grammars 8f1828eb `grammars/mdc.json` | [nuxt-content/vscode-mdc@97c4924c](https://github.com/nuxt-content/vscode-mdc/blob/97c4924c847eea22e50188a5127d893262a66019/syntaxes/mdc.standalone.tmLanguage.json) | MIT |
| `mdx` | `source.mdx` | Shiki tm-grammars 8f1828eb `grammars/mdx.json` | [wooorm/markdown-tm-language@c78b1e5d](https://github.com/wooorm/markdown-tm-language/blob/c78b1e5df644d24fa76716bbe26f4b48a6fc1610/source.mdx.tmLanguage) | MIT |
| `mermaid` | `markdown.mermaid.codeblock` | Shiki tm-grammars 8f1828eb `grammars/mermaid.json` | [bpruitt-goddard/vscode-mermaid-syntax-highlight@8b62f487](https://github.com/bpruitt-goddard/vscode-mermaid-syntax-highlight/blob/8b62f487cb7a89afcd152febfbf47f5d4787657f/syntaxes/mermaid.tmLanguage.yaml) | MIT |
| `mipsasm` | `source.mips` | Shiki tm-grammars 8f1828eb `grammars/mipsasm.json` | [textmate/mips.tmbundle@eb760101](https://github.com/textmate/mips.tmbundle/blob/eb7601016a109bf912a48a1c21988f3c4a59fb3a/Syntaxes/MIPS.tmLanguage) | TextMate Bundle License |
| `mojo` | `source.mojo` | Shiki tm-grammars 8f1828eb `grammars/mojo.json` | [modular/vscode-mojo@7661e4e6](https://github.com/modular/vscode-mojo/blob/7661e4e6157718ab1ecc700441138738f4fcbc9b/syntaxes/mojo.syntax.json) | Apache-2.0 |
| `moonbit` | `source.moonbit` | Shiki tm-grammars 8f1828eb `grammars/moonbit.json` | [moonbitlang/moonbit-tmLanguage@5be3967f](https://github.com/moonbitlang/moonbit-tmLanguage/blob/5be3967f9f5093fd0210afc385bd3495b243d465/grammars/moonbit.tmLanguage.json) | Apache-2.0 |
| `move` | `source.move` | Shiki tm-grammars 8f1828eb `grammars/move.json` | [damirka/move-syntax@01aeb928](https://github.com/damirka/move-syntax/blob/01aeb9284635195a4861cfa7d46759320bb6609c/syntaxes/move.tmLanguage.json) | MIT |
| `narrat` | `source.narrat` | Shiki tm-grammars 8f1828eb `grammars/narrat.json` | [liana-p/narrat-syntax-highlighting-vscode@00d4b410](https://github.com/liana-p/narrat-syntax-highlighting-vscode/blob/00d4b410338fc50ca0ce77a1f7e873c1fb66d376/syntaxes/narrat.tmLanguage.yaml) | MIT |
| `nextflow` | `source.nextflow` | Shiki tm-grammars 8f1828eb `grammars/nextflow.json` | [nextflow-io/vscode-language-nextflow@928fc02d](https://github.com/nextflow-io/vscode-language-nextflow/blob/928fc02dd8d082d489a5cec0c019268894719339/syntaxes/nextflow.tmLanguage.json) | MIT |
| `nextflow-groovy` | `source.nextflow-groovy` | Shiki tm-grammars 8f1828eb `grammars/nextflow-groovy.json` | [nextflow-io/vscode-language-nextflow@928fc02d](https://github.com/nextflow-io/vscode-language-nextflow/blob/928fc02dd8d082d489a5cec0c019268894719339/syntaxes/groovy.tmLanguage.json) | MIT |
| `nginx` | `source.nginx` | Shiki tm-grammars 8f1828eb `grammars/nginx.json` | [hangxingliu/vscode-nginx-conf-hint@49159c02](https://github.com/hangxingliu/vscode-nginx-conf-hint/blob/49159c02fd09dce264ded7802c9499a0f09646f5/src/syntax/nginx.tmLanguage) | GPL-3.0 |
| `nim` | `source.nim` | Shiki tm-grammars 8f1828eb `grammars/nim.json` | [pragmagic/vscode-nim@0272a054](https://github.com/pragmagic/vscode-nim/blob/0272a0544ddf147fde98f8a8f2b624b7dcb19eb3/syntaxes/nim.json) | MIT |
| `nix` | `source.nix` | Shiki tm-grammars 8f1828eb `grammars/nix.json` | [nix-community/vscode-nix-ide@effbf349](https://github.com/nix-community/vscode-nix-ide/blob/effbf3494a43250a537834805b305793994b9ca8/dist/nix.tmLanguage.json) | MIT |
| `nsis` | `source.nsis` | Shiki tm-grammars 8f1828eb `grammars/nsis.json` | [idleberg/vscode-nsis@9adaf15a](https://github.com/idleberg/vscode-nsis/blob/9adaf15a501157c1d3b73730c08a7bcbb057f812/syntaxes/nsis.tmLanguage) | MIT |
| `nushell` | `source.nushell` | Shiki tm-grammars 8f1828eb `grammars/nushell.json` | [nushell/vscode-nushell-lang@dd614517](https://github.com/nushell/vscode-nushell-lang/blob/dd6145171b0e51963f1e3b0f92ade7e9ddfd1628/syntaxes/nushell.tmLanguage.json) | MIT |
| `ocaml` | `source.ocaml` | Shiki tm-grammars 8f1828eb `grammars/ocaml.json` | [reasonml-editor/vscode-reasonml@14af625e](https://github.com/reasonml-editor/vscode-reasonml/blob/14af625ec50ed9968b957b4d6336c5338d81af45/syntaxes/ocaml.json) | Apache-2.0 |
| `odin` | `source.odin` | Shiki tm-grammars 8f1828eb `grammars/odin.json` | [DanielGavin/ols@d26a19b3](https://github.com/DanielGavin/ols/blob/d26a19b38c581e28f67609cbb2d5ac933a9373d1/editors/vscode/syntaxes/odin.tmLanguage.json) | MIT |
| `openscad` | `source.scad` | Shiki tm-grammars 8f1828eb `grammars/openscad.json` | [tbuser/openscad.tmbundle@5962175e](https://github.com/tbuser/openscad.tmbundle/blob/5962175ed355c2d58dc1991801c229809363e999/Syntaxes/OpenSCAD.tmLanguage) | MIT |
| `org` | `source.org` | Shiki tm-grammars 8f1828eb `grammars/org.json` | [vscode-org-mode/vscode-org-mode@98f123a9](https://github.com/vscode-org-mode/vscode-org-mode/blob/98f123a97eca8d0cc1d9e3f313dc2a8be8f25d46/syntaxes/org.tmLanguage.json) | GPL-3.0 |
| `pascal` | `source.pascal` | Shiki tm-grammars 8f1828eb `grammars/pascal.json` | [alefragnani/vscode-language-pascal@f2d6345b](https://github.com/alefragnani/vscode-language-pascal/blob/f2d6345bd9fe83932f4d04fcacf98ce7d74b8034/syntaxes/pascal.tmLanguage) | MIT |
| `pkl` | `source.pkl` | Shiki tm-grammars 8f1828eb `grammars/pkl.json` | [apple/pkl.tmbundle@4ed6ca67](https://github.com/apple/pkl.tmbundle/blob/4ed6ca67ec7579b7a3a86b96ee89874ae825f15f/Syntaxes/pkl.tmLanguage) | Apache-2.0 |
| `plsql` | `source.plsql.oracle` | Shiki tm-grammars 8f1828eb `grammars/plsql.json` | [zabel-xyz/plsql-language@256a6b8f](https://github.com/zabel-xyz/plsql-language/blob/256a6b8f04f2bdc5e60ce921646a5068957078d7/syntaxes/plsql.tmLanguage) | MIT |
| `po` | `source.po` | Shiki tm-grammars 8f1828eb `grammars/po.json` | [textmate/gettext.tmbundle@08bb6955](https://github.com/textmate/gettext.tmbundle/blob/08bb69558d82ada8d4f36b8869f871ce69014749/Syntaxes/Gettext.tmLanguage) | TextMate Bundle License |
| `polar` | `source.polar` | Shiki tm-grammars 8f1828eb `grammars/polar.json` | [osohq/polar-grammar@1a87b3a7](https://github.com/osohq/polar-grammar/blob/1a87b3a7f2b47aea681a0f627b2297f77171d1c3/syntaxes/polar.tmLanguage.json) | Apache-2.0 |
| `postcss` | `source.css.postcss` | Shiki tm-grammars 8f1828eb `grammars/postcss.json` | [vuejs/vetur@8ccae3ba](https://github.com/vuejs/vetur/blob/8ccae3ba50b65837d4cd4acc71a3b84cd3bee27c/syntaxes/vue-postcss.json) | MIT |
| `powerquery` | `source.powerquery` | Shiki tm-grammars 8f1828eb `grammars/powerquery.json` | [microsoft/powerquery-language@eed9810b](https://github.com/microsoft/powerquery-language/blob/eed9810b5f78dfb63319bdbce80bca50419dfa50/PowerQuery.tmLanguage.json) | MIT |
| `prisma` | `source.prisma` | Shiki tm-grammars 8f1828eb `grammars/prisma.json` | [prisma/language-tools@eca96bfd](https://github.com/prisma/language-tools/blob/eca96bfdfa03bde79465e831e502481d386934be/packages/vscode/syntaxes/prisma.tmLanguage.json) | Apache-2.0 |
| `prolog` | `source.prolog` | Shiki tm-grammars 8f1828eb `grammars/prolog.json` | [arthwang/vsc-prolog@5e15f731](https://github.com/arthwang/vsc-prolog/blob/5e15f731d5ef8e0f3974589b84ddefe6a2bf78c7/syntaxes/prolog.swi.tmLanguage.json) | MIT |
| `proto` | `source.proto` | Shiki tm-grammars 8f1828eb `grammars/proto.json` | [zxh0/vscode-proto3@274be763](https://github.com/zxh0/vscode-proto3/blob/274be763ecae7055505354b44f4892a2669e1da8/syntaxes/proto3.tmLanguage.json) | MIT |
| `puppet` | `source.puppet` | Shiki tm-grammars 8f1828eb `grammars/puppet.json` | [octref/puppet-vscode@42e02ef6](https://github.com/octref/puppet-vscode/blob/42e02ef6c02aefb0dcf03f8367fdc106d7213a6c/syntaxes/puppet.tmLanguage) | Apache-2.0 |
| `purescript` | `source.purescript` | Shiki tm-grammars 8f1828eb `grammars/purescript.json` | [nwolverson/vscode-language-purescript@b7641936](https://github.com/nwolverson/vscode-language-purescript/blob/b7641936190d6d8eb346660284dab3a5d1cc636f/syntaxes/purescript.json) | MIT |
| `qml` | `source.qml` | Shiki tm-grammars 8f1828eb `grammars/qml.json` | [seanwu1105/vscode-qt-for-python@51408001](https://github.com/seanwu1105/vscode-qt-for-python/blob/51408001e908800c00ce9f68f66e511545ea29fe/syntaxes/qml.tmLanguage.json) | MIT |
| `qmldir` | `source.qmldir` | Shiki tm-grammars 8f1828eb `grammars/qmldir.json` | [seanwu1105/vscode-qt-for-python@b301e7b1](https://github.com/seanwu1105/vscode-qt-for-python/blob/b301e7b18493ba59c4f52e49c3f73b6cd23d4c4b/syntaxes/qmldir.tmLanguage.json) | MIT |
| `qss` | `source.qss` | Shiki tm-grammars 8f1828eb `grammars/qss.json` | [seanwu1105/vscode-qt-for-python@f0a56a97](https://github.com/seanwu1105/vscode-qt-for-python/blob/f0a56a97d10f58ab7b4b6e2ab6ce34ca4c9d521d/syntaxes/qss.tmLanguage.json) | MIT |
| `racket` | `source.racket` | Shiki tm-grammars 8f1828eb `grammars/racket.json` | [Eugleo/magic-racket@1b850db7](https://github.com/Eugleo/magic-racket/blob/1b850db7f6e7c3ba8a1b47fe2781800a21f669a8/syntaxes/racket.tmLanguage.json) | GPL-3.0 |
| `rbs` | `source.rbs` | Shiki tm-grammars 8f1828eb `grammars/rbs.json` | [soutaro/vscode-rbs-syntax@d945dc76](https://github.com/soutaro/vscode-rbs-syntax/blob/d945dc76ec7eee172c51eb8bea1f206aa2e52925/syntaxes/rbs.tmLanguage.json) | MIT |
| `reg` | `source.reg` | Shiki tm-grammars 8f1828eb `grammars/reg.json` | [mihai-vlc/reg-vscode@896096fc](https://github.com/mihai-vlc/reg-vscode/blob/896096fc6756329d82c71a913663a1c0ac678f74/syntaxes/reg.tmLanguage) | MIT |
| `rel` | `source.rel` | Shiki tm-grammars 8f1828eb `grammars/rel.json` | [relationalai-oss/rel_vscode@63dda4de](https://github.com/relationalai-oss/rel_vscode/blob/63dda4dee48c67b037357f47b562185484555ea9/syntaxes/rel.tmLanguage.json) | MIT |
| `riscv` | `source.riscv` | Shiki tm-grammars 8f1828eb `grammars/riscv.json` | [zhuanhao-wu/vscode-riscv-support@2736b60e](https://github.com/zhuanhao-wu/vscode-riscv-support/blob/2736b60e45ff2ef738d784055e136cf38d6290cd/syntaxes/riscv.tmLanguage) | MIT |
| `ron` | `source.ron` | Shiki tm-grammars 8f1828eb `grammars/ron.json` | [a5huynh/vscode-ron@3acb0d97](https://github.com/a5huynh/vscode-ron/blob/3acb0d97348c972b3b6f04b4e92901a541b22022/syntaxes/ron.tmGrammar.json) | MIT |
| `rosmsg` | `source.rosmsg` | Shiki tm-grammars 8f1828eb `grammars/rosmsg.json` | [jtbandes/ros-tmlanguage@d8ae27bc](https://github.com/jtbandes/ros-tmlanguage/blob/d8ae27bc0d04498c01cb8dc77bfe2884474d9aba/ROS%20Interface.tmLanguage.yaml) | MIT |
| `sas` | `source.sas` | Shiki tm-grammars 8f1828eb `grammars/sas.json` | [rpardee/sas@49f6e733](https://github.com/rpardee/sas/blob/49f6e733c6c0b918a2a825ca2f63dba704f3e8c0/syntaxes/sas.tmLanguage) | MIT |
| `sass` | `source.sass` | Shiki tm-grammars 8f1828eb `grammars/sass.json` | [TheRealSyler/vscode-sass-indented@f0e50cfa](https://github.com/TheRealSyler/vscode-sass-indented/blob/f0e50cfaca4b64a1d49f8d938f188d6fd0bcfb6f/syntaxes/sass.tmLanguage.json) | MIT |
| `scala` | `source.scala` | Shiki tm-grammars 8f1828eb `grammars/scala.json` | [scala/vscode-scala-syntax@672fa16c](https://github.com/scala/vscode-scala-syntax/blob/672fa16c64e12b2cc8b98da011411463968de727/syntaxes/Scala.tmLanguage.json) | MIT |
| `scheme` | `source.scheme` | Shiki tm-grammars 8f1828eb `grammars/scheme.json` | [sjhuangx/vscode-scheme@4e216836](https://github.com/sjhuangx/vscode-scheme/blob/4e21683666e1e2edfa21d040ecd427f628e7b5ff/syntaxes/scheme.tmLanguage) | MIT |
| `sdbl` | `source.sdbl` | Shiki tm-grammars 8f1828eb `grammars/sdbl.json` | [1c-syntax/vsc-language-1c-bsl@c60d4208](https://github.com/1c-syntax/vsc-language-1c-bsl/blob/c60d42089e5139942ff5b1db3a0a3ca8dd094ce6/syntaxes/1c-query.tmLanguage.json) | MIT |
| `shellsession` | `text.shell-session` | Shiki tm-grammars 8f1828eb `grammars/shellsession.json` | [hronro/sublime-linguist-syntax@9c84f1d6](https://github.com/hronro/sublime-linguist-syntax/blob/9c84f1d69dbe89bc23292f129cf2d408a9c7afe1/syntaxes/ShellSession.tmLanguage) | MIT |
| `smalltalk` | `source.smalltalk.gnu` | Shiki tm-grammars 8f1828eb `grammars/smalltalk.json` | [leocamello/vscode-smalltalk@241ae15a](https://github.com/leocamello/vscode-smalltalk/blob/241ae15a15643a7728ddf6c5c1771e340e731041/syntaxes/gnu-smalltalk.YAML-tmLanguage) | MIT |
| `smithy` | `source.smithy` | Shiki tm-grammars 8f1828eb `grammars/smithy.json` | [smithy-lang/smithy-vscode@207935ee](https://github.com/smithy-lang/smithy-vscode/blob/207935eefd01821565e86f115b928fa44992830f/syntaxes/smithy.tmLanguage.json) | Apache-2.0 |
| `solidity` | `source.solidity` | Shiki tm-grammars 8f1828eb `grammars/solidity.json` | [juanfranblanco/vscode-solidity@54ae3025](https://github.com/juanfranblanco/vscode-solidity/blob/54ae302551a095e3f34371bb15f654f574e10fac/syntaxes/solidity.json) | MIT |
| `soy` | `text.html.soy` | Shiki tm-grammars 8f1828eb `grammars/soy.json` | [mthadley/language-closure-templates@7caf3a1f](https://github.com/mthadley/language-closure-templates/blob/7caf3a1fad0245c4196d3766ba0f481762fbc92e/grammars/closure-templates.json) | MIT |
| `sparql` | `source.sparql` | Shiki tm-grammars 8f1828eb `grammars/sparql.json` | [stardog-union/stardog-vsc@a1963c61](https://github.com/stardog-union/stardog-vsc/blob/a1963c610cde0eab23c44fc01ab36652565524de/stardog-rdf-grammars/syntaxes/sparql.tmLanguage.json) | Apache-2.0 |
| `splunk` | `source.splunk_search` | Shiki tm-grammars 8f1828eb `grammars/splunk.json` | [arcsector/vscode-splunk-search-syntax@b1f68ff0](https://github.com/arcsector/vscode-splunk-search-syntax/blob/b1f68ff035950e6a98e0db4af1a338cb1c3c1858/syntaxes/splunk_search.tmLanguage) | MIT |
| `ssh-config` | `source.ssh-config` | Shiki tm-grammars 8f1828eb `grammars/ssh-config.json` | [textmate/ssh-config.tmbundle@212a0000](https://github.com/textmate/ssh-config.tmbundle/blob/212a0000c50e0c877303b2c4fcead539d2af2670/Syntaxes/SSH-Config.tmLanguage) | TextMate Bundle License |
| `stata` | `source.stata` | Shiki tm-grammars 8f1828eb `grammars/stata.json` | [kylebarron/language-stata@dd98de50](https://github.com/kylebarron/language-stata/blob/dd98de502bc1bf2a205f3f023afb98c7aa5b42ee/grammars/stata.json) | MIT |
| `stylus` | `source.stylus` | Shiki tm-grammars 8f1828eb `grammars/stylus.json` | [d4rkr00t/language-stylus@198a851f](https://github.com/d4rkr00t/language-stylus/blob/198a851f385aee857332f3ce5dd981ff67720ead/syntaxes/stylus.json) | MIT |
| `surrealql` | `source.surrealql` | Shiki tm-grammars 8f1828eb `grammars/surrealql.json` | [surrealdb/surrealql-grammar@d7594cbe](https://github.com/surrealdb/surrealql-grammar/blob/d7594cbe432daaa51d30723a63fb63888d5073a0/syntaxes/surrealql.tmLanguage.json) | Apache-2.0 |
| `svelte` | `source.svelte` | Shiki tm-grammars 8f1828eb `grammars/svelte.json` | [sveltejs/language-tools@2cfcc15b](https://github.com/sveltejs/language-tools/blob/2cfcc15b4c44dfc1128432e20ca663fe20bdd12e/packages/svelte-vscode/syntaxes/svelte.tmLanguage.src.yaml) | MIT |
| `system-verilog` | `source.systemverilog` | Shiki tm-grammars 8f1828eb `grammars/system-verilog.json` | [mshr-h/vscode-verilog-hdl-support@e6a5a2b7](https://github.com/mshr-h/vscode-verilog-hdl-support/blob/e6a5a2b76055dd7acbdc7420060e0314cb86e8dd/syntaxes/systemverilog.tmLanguage.json) | MIT |
| `systemd` | `source.systemd` | Shiki tm-grammars 8f1828eb `grammars/systemd.json` | [hangxingliu/vscode-systemd@62ace115](https://github.com/hangxingliu/vscode-systemd/blob/62ace1156b5afa0ec9932e3a55cfc00c0123c457/src/syntax/systemd.tmLanguage) | MIT |
| `talonscript` | `source.talon` | Shiki tm-grammars 8f1828eb `grammars/talonscript.json` | [mrob95/vscode-TalonScript@91f3a6fa](https://github.com/mrob95/vscode-TalonScript/blob/91f3a6faaf926c02582f36ee155f2c4a02b9cc48/syntaxes/talon.tmLanguage.json) | MIT |
| `tasl` | `source.tasl` | Shiki tm-grammars 8f1828eb `grammars/tasl.json` | [underlay/vscode-tasl@dde4b7c0](https://github.com/underlay/vscode-tasl/blob/dde4b7c0b762c2e667584095362dd49ebfacece7/syntaxes/tasl.tmLanguage.json) | MIT |
| `templ` | `source.templ` | Shiki tm-grammars 8f1828eb `grammars/templ.json` | [templ-go/templ-vscode@d0af70c9](https://github.com/templ-go/templ-vscode/blob/d0af70c97ed384e93892c46b93d622ffaba5d93c/syntaxes/templ.tmLanguage.json) | MIT |
| `terraform` | `source.hcl.terraform` | Shiki tm-grammars 8f1828eb `grammars/terraform.json` | [hashicorp/syntax@cc2b4d4f](https://github.com/hashicorp/syntax/blob/cc2b4d4fe389f14b8a13937f4e0d7b2811b57588/syntaxes/terraform.tmGrammar.json) | MPL-2.0 |
| `toml` | `source.toml` | Shiki tm-grammars 8f1828eb `grammars/toml.json` | [textmate/toml.tmbundle@e82b64c1](https://github.com/textmate/toml.tmbundle/blob/e82b64c1e86396220786846201e9aa3f0a2d9ca2/Syntaxes/TOML.tmLanguage) | TextMate Bundle License |
| `ts-tags` | `source.ts.tags` | Shiki tm-grammars 8f1828eb `grammars/ts-tags.json` | None: Shiki tm-grammars 8f1828eb is the origin | MIT |
| `tsv` | `text.tsv` | Shiki tm-grammars 8f1828eb `grammars/tsv.json` | [mechatroner/vscode_rainbow_csv@00d4c947](https://github.com/mechatroner/vscode_rainbow_csv/blob/00d4c947f1bbe6a4a73599bd97fb31bdfbe262f3/syntaxes/tsv.tmLanguage.json) | MIT |
| `turtle` | `source.turtle` | Shiki tm-grammars 8f1828eb `grammars/turtle.json` | [stardog-union/stardog-vsc@3f1364b0](https://github.com/stardog-union/stardog-vsc/blob/3f1364b08bdbf4b4d77f123682db7d2b708b2f6e/stardog-rdf-grammars/syntaxes/turtle.tmLanguage.json) | Apache-2.0 |
| `twig` | `text.html.twig` | Shiki tm-grammars 8f1828eb `grammars/twig.json` | [mblode/vscode-twig-language-2@73f4e272](https://github.com/mblode/vscode-twig-language-2/blob/73f4e2729ce5f490e294dd158c82c22a35cf96de/src/syntaxes/twig.tmLanguage) | MIT |
| `typespec` | `source.tsp` | Shiki tm-grammars 8f1828eb `grammars/typespec.json` | [microsoft/typespec@8e16f8cf](https://github.com/microsoft/typespec/blob/8e16f8cfffd431f2a04647f8f98bbc98e8f6fcd0/grammars/typespec.json) | MIT |
| `typst` | `source.typst` | Shiki tm-grammars 8f1828eb `grammars/typst.json` | [Myriad-Dreamin/tinymist@b4e837d0](https://github.com/Myriad-Dreamin/tinymist/blob/b4e837d01ddb66d676637a1af80b6754ff874395/editors/vscode/package.json) | Apache-2.0 |
| `v` | `source.v` | Shiki tm-grammars 8f1828eb `grammars/v.json` | [vlang/vscode-vlang@cc6dee79](https://github.com/vlang/vscode-vlang/blob/cc6dee79af1fc1332084a263b952489aaed7e884/syntaxes/v.tmLanguage.json) | MIT |
| `vala` | `source.vala` | Shiki tm-grammars 8f1828eb `grammars/vala.json` | [vala-lang/vala-vscode@8f825e95](https://github.com/vala-lang/vala-vscode/blob/8f825e95648326df2572d0522632251cdff4a43b/syntaxes/vala.YAML-tmLanguage) | MIT |
| `verilog` | `source.verilog` | Shiki tm-grammars 8f1828eb `grammars/verilog.json` | [mshr-h/vscode-verilog-hdl-support@67065883](https://github.com/mshr-h/vscode-verilog-hdl-support/blob/67065883604b1b68a97ca33db22b1eb0c05553de/syntaxes/verilog.tmLanguage.json) | MIT |
| `vhdl` | `source.vhdl` | Shiki tm-grammars 8f1828eb `grammars/vhdl.json` | [jonasjj/awesome-vhdl@b787559a](https://github.com/jonasjj/awesome-vhdl/blob/b787559a47d64c7baf2a9ac0f4cac8e2e721cddc/syntaxes/vhdl.tmLanguage) | MIT |
| `viml` | `source.viml` | Shiki tm-grammars 8f1828eb `grammars/viml.json` | [dunstontc/viml@8762570f](https://github.com/dunstontc/viml/blob/8762570fc7d6f1009a3c93efd2a337330d88b88e/syntaxes/viml.tmLanguage.json) | MIT |
| `vue` | `text.html.vue` | Shiki tm-grammars 8f1828eb `grammars/vue.json` | [vuejs/language-tools@cd7a6e53](https://github.com/vuejs/language-tools/blob/cd7a6e53d58111640f116d1e19f1c1cf06c7157d/extensions/vscode/syntaxes/vue.tmLanguage.json) | MIT |
| `vue-html` | `text.html.vue-html` | Shiki tm-grammars 8f1828eb `grammars/vue-html.json` | [vuejs/vetur@53ee5274](https://github.com/vuejs/vetur/blob/53ee52746b1f26f44391c3764bc7bd06d4ddaa46/syntaxes/vue-html.tmLanguage.json) | MIT |
| `vue-vine` | `source.vue-vine` | Shiki tm-grammars 8f1828eb `grammars/vue-vine.json` | [vue-vine/assets@2921493f](https://github.com/vue-vine/assets/blob/2921493f703bfcf64aed5644f275e7e65f2e0a48/grammars/vue-vine.tmLanguage.json) | MIT |
| `vyper` | `source.vyper` | Shiki tm-grammars 8f1828eb `grammars/vyper.json` | [tintinweb/vscode-vyper@3df08ea4](https://github.com/tintinweb/vscode-vyper/blob/3df08ea46fc7df1a5e7f35365e615624849d1c1b/syntaxes/vyper.tmLanguage.json) | MIT |
| `wasm` | `source.wat` | Shiki tm-grammars 8f1828eb `grammars/wasm.json` | [wasmerio/vscode-wasm@9d0ace1f](https://github.com/wasmerio/vscode-wasm/blob/9d0ace1ff113f4a3e95797675039cd94ec257078/syntaxes/wat.json) | MIT |
| `wenyan` | `source.wenyan` | Shiki tm-grammars 8f1828eb `grammars/wenyan.json` | [wenyan-lang/highlight@5100e8a8](https://github.com/wenyan-lang/highlight/blob/5100e8a803978b4f5b009adac035d1f27f591760/wenyan.tmLanguage.json) | MIT |
| `wgsl` | `source.wgsl` | Shiki tm-grammars 8f1828eb `grammars/wgsl.json` | [PolyMeilex/vscode-wgsl@04222c48](https://github.com/PolyMeilex/vscode-wgsl/blob/04222c48533ca541d6dc97043ea096471517e079/syntaxes/wgsl.tmLanguage.json) | MIT |
| `wikitext` | `source.wikitext` | Shiki tm-grammars 8f1828eb `grammars/wikitext.json` | [Frederisk/Wikitext-VSCode-Extension@40e726bf](https://github.com/Frederisk/Wikitext-VSCode-Extension/blob/40e726bfe904a1e3b9a3141b4920f5061ce60e52/syntaxes/wikitext.tmLanguage.yaml) | MIT |
| `wit` | `source.wit` | Shiki tm-grammars 8f1828eb `grammars/wit.json` | [wasmerio/vscode-wasm@5a67ac3f](https://github.com/wasmerio/vscode-wasm/blob/5a67ac3f5ade246b44308768b034204612f3a0c9/syntaxes/wit.json) | MIT |
| `wolfram` | `source.wolfram` | Shiki tm-grammars 8f1828eb `grammars/wolfram.json` | [WolframResearch/vscode-wolfram@8b55fb17](https://github.com/WolframResearch/vscode-wolfram/blob/8b55fb1724d971a303120b3f5b981d654c308c59/syntaxes/wolfram.tmLanguage.json) | MIT |
| `zenscript` | `source.zenscript` | Shiki tm-grammars 8f1828eb `grammars/zenscript.json` | [CraftTweaker/ZenScript-tmLanguage@5a7bcb77](https://github.com/CraftTweaker/ZenScript-tmLanguage/blob/5a7bcb775a6fb37470e56f904e70d8b8b9e6b047/zenscript.tmLanguage.json) | MIT |
| `zig` | `source.zig` | Shiki tm-grammars 8f1828eb `grammars/zig.json` | [ziglang/vscode-zig@9c7bb763](https://github.com/ziglang/vscode-zig/blob/9c7bb7631f3af99331475b633aaf5b70345c6713/syntaxes/zig.tmLanguage.json) | MIT |
| `glsl` | `source.glsl` | euler0/sublime-glsl 59a5f8a3 `GLSL.tmLanguage` | None: euler0/sublime-glsl 59a5f8a3 is the origin | Unlicense |
| `tcl` | `source.tcl` | textmate/tcl.tmbundle f06f8014 `Syntaxes/Tcl.plist` | None: textmate/tcl.tmbundle f06f8014 is the origin | TextMate Bundle License |
| `dream-maker` | `source.dm` | spacestation13/dm-syntax a63b4388 `syntaxes/dm.tmLanguage.json` | None: spacestation13/dm-syntax a63b4388 is the origin | MIT |
| `abap-cds` | `source.abapcds` | FreHu/abap-cds-grammar dd803e6a `syntaxes/cds.tmLanguage.json` | None: FreHu/abap-cds-grammar dd803e6a is the origin | Unlicense |
| `antlr` | `source.antlr` | textmate/antlr.tmbundle 7f07385a `Syntaxes/ANTLR.plist` | None: textmate/antlr.tmbundle 7f07385a is the origin | TextMate Bundle License |
| `apiblueprint` | `text.html.markdown.source.gfm.apib` | apiaryio/api-blueprint-sublime-plugin 0a90eff1 `APIBlueprint.tmLanguage` | None: apiaryio/api-blueprint-sublime-plugin 0a90eff1 is the origin | MIT |
| `autoit` | `source.autoit` | AutoIt/SublimeAutoItScript 2b2e950c `AutoIt.tmLanguage` | None: AutoIt/SublimeAutoItScript 2b2e950c is the origin | MIT |
| `browserslist` | `text.browserslist` | browserslist/browserslist-vscode e40e7efc `syntaxes/browserslist.tmLanguage.json` | None: browserslist/browserslist-vscode e40e7efc is the origin | MIT |
| `bru` | `source.bru` | usebruno/bruno-ide-extensions 0f92df16 `packages/vscode/syntaxes/bruno.tmLanguage.json` | None: usebruno/bruno-ide-extensions 0f92df16 is the origin | MIT |
| `caddyfile` | `source.Caddyfile` | caddyserver/vscode-caddyfile 9ba30fce `syntaxes/caddyfile.tmLanguage.json` | None: caddyserver/vscode-caddyfile 9ba30fce is the origin | MIT |
| `capnp` | `source.capnp` | textmate/capnproto.tmbundle 9fab42eb `Syntaxes/Cap’n Proto.tmLanguage` | None: textmate/capnproto.tmbundle 9fab42eb is the origin | TextMate Bundle License |
| `cds` | `source.cds` | SAP/cds-textmate-grammar d5389523 `syntaxes/cds.tmLanguage.json` | None: SAP/cds-textmate-grammar d5389523 is the origin | Apache-2.0 |
| `cfml` | `text.html.cfm` | SublimeText/ColdFusion dbe2f760 `HTML+CFML.tmLanguage` | None: SublimeText/ColdFusion dbe2f760 is the origin | MIT |
| `cfscript` | `source.cfscript` | SublimeText/ColdFusion dbe2f760 `Cfscript.tmLanguage` | None: SublimeText/ColdFusion dbe2f760 is the origin | MIT |
| `dot` | `source.dot` | textmate/graphviz.tmbundle d1d489f8 `Syntaxes/DOT.plist` | None: textmate/graphviz.tmbundle d1d489f8 is the origin | TextMate Bundle License |
| `dune` | `source.dune` | ocamllabs/vscode-ocaml-platform 71aa5bc6 `syntaxes/dune.json` | None: ocamllabs/vscode-ocaml-platform 71aa5bc6 is the origin | ISC |
| `eex` | `text.html.elixir` | elixir-lang/elixir-tmbundle eb63f1a5 `Syntaxes/HTML (EEx).tmLanguage` | None: elixir-lang/elixir-tmbundle eb63f1a5 is the origin | Apache-2.0 |
| `ejs` | `text.html.js` | gregory-m/ejs-tmbundle 5e48fff7 `Syntaxes/JavaScript Template.tmLanguage` | None: gregory-m/ejs-tmbundle 5e48fff7 is the origin | MIT |
| `forth` | `source.forth` | textmate/forth.tmbundle 0cf0ec91 `Syntaxes/Forth.tmLanguage` | None: textmate/forth.tmbundle 0cf0ec91 is the origin | TextMate Bundle License |
| `gemini` | `source.gemini` | printfn/gemini-vscode 72ef757a `syntaxes/gemini.tmLanguage.json` | None: printfn/gemini-vscode 72ef757a is the origin | MIT |
| `go-mod` | `go.mod` | golang/vscode-go 28e27ffd `extension/syntaxes/go.mod.tmGrammar.json` | None: golang/vscode-go 28e27ffd is the origin | MIT |
| `go-sum` | `go.sum` | golang/vscode-go 28e27ffd `extension/syntaxes/go.sum.tmGrammar.json` | None: golang/vscode-go 28e27ffd is the origin | MIT |
| `icalendar` | `source.iCalendar` | kimsey0/iCalendar-sublime ee1aaa8a `iCalendar.tmLanguage` | None: kimsey0/iCalendar-sublime ee1aaa8a is the origin | MIT |
| `idris` | `source.idris` | idris-hackers/idris-sublime c3a9f006 `Idris.tmLanguage` | None: idris-hackers/idris-sublime c3a9f006 is the origin | MIT |
| `kcl` | `source.kcl` | kcl-lang/vscode-kcl 4eb7b145 `syntaxes/KCL.tmLanguage.json` | None: kcl-lang/vscode-kcl 4eb7b145 is the origin | Apache-2.0 |
| `kivy` | `source.python.kivy` | p0lygun/kivy-language-grammer d6ab305f `grammers/kivy.tmLanguage` | None: p0lygun/kivy-language-grammer d6ab305f is the origin | MIT |
| `livescript` | `source.livescript` | sharktide/livescript-vscode 04b9ae5d `syntaxes/livescript.tmLanguage.json` | None: sharktide/livescript-vscode 04b9ae5d is the origin | Apache-2.0 |
| `lolcode` | `source.lolcode` | KrazIvan/LOLCODE-grammar-vscode 2091c60e `syntaxes/lolcode.tmLanguage.json` | None: KrazIvan/LOLCODE-grammar-vscode 2091c60e is the origin | MIT |
| `mcfunction` | `source.mcfunction` | MinecraftCommands/syntax-mcfunction cb179b5c `mcfunction.tmLanguage.json` | None: MinecraftCommands/syntax-mcfunction cb179b5c is the origin | MIT |
| `mint` | `source.mint` | mint-lang/mint-vscode 9b0c8b54 `syntaxes/mint.tmLanguage.json` | None: mint-lang/mint-vscode 9b0c8b54 is the origin | MIT |
| `moonscript` | `source.moonscript` | leafo/moonscript-tmbundle cf69300f `Syntaxes/MoonScript.tmLanguage` | None: leafo/moonscript-tmbundle cf69300f is the origin | MIT |
| `pawn` | `source.pawn` | Southclaw/pawn-sublime-language b83dab34 `Pawn.tmLanguage` | None: Southclaw/pawn-sublime-language b83dab34 is the origin | MIT |
| `peggy` | `source.peggy` | peggyjs/code-peggy-language 41bc8b61 `syntaxes/peggy.json` | None: peggyjs/code-peggy-language 41bc8b61 is the origin | MIT |
| `pip-requirements` | `source.pip-requirements` | microsoft/vscode-python e0f1d3c8 `syntaxes/pip-requirements.tmLanguage.json` | None: microsoft/vscode-python e0f1d3c8 is the origin | MIT |
| `powerbuilder` | `source.powerbuilder` | informaticon/PowerBuilder.tmbundle 054c6f74 `Syntaxes/PowerBuilder.tmLanguage.json` | None: informaticon/PowerBuilder.tmbundle 054c6f74 is the origin | MIT |
| `processing` | `source.processing` | textmate/processing.tmbundle 214b3420 `Syntaxes/Processing.plist` | None: textmate/processing.tmbundle 214b3420 is the origin | TextMate Bundle License |
| `procfile` | `source.procfile` | benspaulding/vscode-procfile 4e149fd5 `syntaxes/procfile.tmLanguage.json` | None: benspaulding/vscode-procfile 4e149fd5 is the origin | BSD-3-Clause |
| `reason` | `source.reason` | ocamllabs/vscode-ocaml-platform 71aa5bc6 `syntaxes/reason.json` | None: ocamllabs/vscode-ocaml-platform 71aa5bc6 is the origin | ISC |
| `rebol` | `source.rebol` | Oldes/Sublime-REBOL eb06bfce `Rebol.tmLanguage` | None: Oldes/Sublime-REBOL eb06bfce is the origin | MIT |
| `red` | `source.red` | Oldes/Sublime-Red fb0c6992 `Red.tmLanguage` | None: Oldes/Sublime-Red fb0c6992 is the origin | MIT |
| `rego` | `source.rego` | open-policy-agent/vscode-opa f5efd999 `syntaxes/Rego.tmLanguage` | None: open-policy-agent/vscode-opa f5efd999 is the origin | Apache-2.0 |
| `rescript` | `source.rescript` | rescript-lang/rescript-vscode f29d6a09 `grammars/rescript.tmLanguage.json` | None: rescript-lang/rescript-vscode f29d6a09 is the origin | MIT |
| `riot` | `text.html.riot` | riot/syntax-highlight 6ffd29ce `riot.tmlanguage` | None: riot/syntax-highlight 6ffd29ce is the origin | MIT |
| `robotframework` | `text.robot` | shellderp/sublime-robot-plugin 07069ebf `robot.tmLanguage` | None: shellderp/sublime-robot-plugin 07069ebf is the origin | Apache-2.0 |
| `slang` | `source.slang` | shader-slang/slang-vscode-extension b03da15f `syntaxes/slang.tmLanguage.json` | None: shader-slang/slang-vscode-extension b03da15f is the origin | Apache-2.0 |
| `slim` | `text.slim` | borama/vscode-ruby-slim 098d948e `syntaxes/slim.tmLanguage.json` | None: borama/vscode-ruby-slim 098d948e is the origin | MIT |
| `smali` | `source.smali` | ShaneWilton/sublime-smali 36add49d `smali.tmLanguage` | None: ShaneWilton/sublime-smali 36add49d is the origin | MIT |
| `smarty` | `text.html.smarty` | textmate/php-smarty.tmbundle 38880466 `Syntaxes/Smarty.plist` | None: textmate/php-smarty.tmbundle 38880466 is the origin | TextMate Bundle License |
| `sml` | `source.ml` | textmate/standard-ml.tmbundle b52a5ebf `Syntaxes/Standard ML.plist` | None: textmate/standard-ml.tmbundle b52a5ebf is the origin | TextMate Bundle License |
| `smt` | `source.smt` | SRI-CSL/SMT.tmbundle 426d5f7e `Syntaxes/smt.tmLanguage` | None: SRI-CSL/SMT.tmbundle 426d5f7e is the origin | Unlicense |
| `stan` | `source.stan` | stan-dev/atom-language-stan 366d1f64 `grammars/language-stan.json` | None: stan-dev/atom-language-stan 366d1f64 is the origin | MIT |
| `sugarss` | `source.css.postcss.sugarss` | hudochenkov/Syntax-highlighting-for-PostCSS 575b9189 `Syntaxes/SugarSS.tmLanguage` | None: hudochenkov/Syntax-highlighting-for-PostCSS 575b9189 is the origin | MIT |
| `tape` | `source.vhs` | griimick/vscode-vhs 8e1da076 `syntaxes/vhs.tmLanguage.json` | None: griimick/vscode-vhs 8e1da076 is the origin | MIT |
| `teal` | `source.teal` | teal-language/vscode-teal 13802a7f `syntaxes/teal.tmLanguage.json` | None: teal-language/vscode-teal 13802a7f is the origin | MIT |
| `uiua` | `source.uiua` | uiua-lang/uiua-vscode 333e5b12 `syntaxes/uiua.tmLanguage.json` | None: uiua-lang/uiua-vscode 333e5b12 is the origin | MIT |
| `vcl` | `source.vcl` | fastly/vscode-fastly-vcl 01f09f53 `syntaxes/vcl.tmLanguage.json` | None: fastly/vscode-fastly-vcl 01f09f53 is the origin | MIT |
| `verse` | `source.verse` | simnJS/verse-grammar 3abf18ec `grammars/verse.tmLanguage.json` | None: simnJS/verse-grammar 3abf18ec is the origin | MIT |
| `agc` | `source.agc` | Alhadis/language-agc 6fb398ee `grammars/agc.cson` | None: Alhadis/language-agc 6fb398ee is the origin | ISC |
| `cabal` | `source.cabal` | atom-haskell/language-haskell a273201b `grammars/cabal.cson` | None: atom-haskell/language-haskell a273201b is the origin | MIT |
| `literate-haskell` | `text.tex.latex.haskell` | atom-haskell/language-haskell a273201b `grammars/literate haskell.cson` | None: atom-haskell/language-haskell a273201b is the origin | MIT |
| `liquid-haskell` | `annotation.liquidhaskell.haskell` | atom-haskell/language-haskell a273201b `grammars/liquid haskell.cson` | None: atom-haskell/language-haskell a273201b is the origin | MIT |
| `etc` | `etc` | Alhadis/language-etc 6aa793bd `grammars/etc.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `checksums` | `text.checksums` | Alhadis/language-etc 6aa793bd `grammars/checksums.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `gitconfig` | `source.gitconfig` | Alhadis/language-etc 6aa793bd `grammars/gitconfig.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `hosts` | `source.hosts` | Alhadis/language-etc 6aa793bd `grammars/hosts.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `m3u` | `source.m3u` | Alhadis/language-etc 6aa793bd `grammars/m3u.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `mailmap` | `source.mailmap` | Alhadis/language-etc 6aa793bd `grammars/mailmap.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `opts` | `source.opts` | Alhadis/language-etc 6aa793bd `grammars/opts.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `shellcheckrc` | `source.shellcheckrc` | Alhadis/language-etc 6aa793bd `grammars/shellcheckrc.cson` | None: Alhadis/language-etc 6aa793bd is the origin | ISC |
| `lex` | `source.lex` | Alhadis/language-grammars daa8548c `grammars/lex.cson` | None: Alhadis/language-grammars daa8548c is the origin | ISC |
| `lex-regexp` | `source.lex.regexp` | Alhadis/language-grammars daa8548c `grammars/lex-regexp.cson` | None: Alhadis/language-grammars daa8548c is the origin | ISC |
| `postscript` | `source.postscript` | Alhadis/Atom-PostScript 8f5309cc `grammars/postscript.cson` | None: Alhadis/Atom-PostScript 8f5309cc is the origin | ISC |
| `srt` | `text.srt` | Alhadis/language-subtitles 9acc0aa2 `grammars/srt.cson` | None: Alhadis/language-subtitles 9acc0aa2 is the origin | ISC |
| `vtt` | `text.vtt` | Alhadis/language-subtitles 9acc0aa2 `grammars/vtt.cson` | None: Alhadis/language-subtitles 9acc0aa2 is the origin | ISC |
| `firestore` | `source.firestore` | jaysquared/atom-firestore-grammar 750ec2ab `grammars/firestore.cson` | None: jaysquared/atom-firestore-grammar 750ec2ab is the origin | MIT |
| `denizenscript` | `source.denizenscript` | DenizenScript/denizenscript-grammar a9a95232 `grammars/denizenscript.cson` | None: DenizenScript/denizenscript-grammar a9a95232 is the origin | MIT |
| `eml` | `text.eml.basic` | mariozaizar/language-eml e92b829c `grammars/language-eml.cson` | None: mariozaizar/language-eml e92b829c is the origin | MIT |
| `ecr` | `text.html.ecr` | atom-crystal/language-crystal 48b87f54 `grammars/html (crystal - ecr).cson` | None: atom-crystal/language-crystal 48b87f54 is the origin | MIT |
| `lilypond` | `source.lilypond` | nwhetsell/linter-lilypond eca5b0f9 `grammars/lilypond.cson` | None: nwhetsell/linter-lilypond eca5b0f9 is the origin | MIT |
| `macaulay2` | `source.m2` | Macaulay2/language-macaulay2 e3d0db2a `grammars/macaulay2.cson` | None: Macaulay2/language-macaulay2 e3d0db2a is the origin | MIT |
| `meson` | `source.meson` | TingPing/language-meson 90269533 `grammars/meson.cson` | None: TingPing/language-meson 90269533 is the origin | Apache-2.0 |
| `nunjucks` | `text.html.nunjucks` | alohaas/language-nunjucks d865b2a6 `grammars/html (nunjucks templates).cson` | None: alohaas/language-nunjucks d865b2a6 is the origin | MIT |
| `nunjucks-source` | `source.nunjucks` | alohaas/language-nunjucks d865b2a6 `grammars/nunjucks templates.cson` | None: alohaas/language-nunjucks d865b2a6 is the origin | MIT |
| `velocity` | `source.velocity` | animecyc/AtomLanguageVelocity 6eb92de5 `grammars/velocity.cson` | None: animecyc/AtomLanguageVelocity 6eb92de5 is the origin | MIT |
| `xquery` | `source.xq` | wcandillon/language-jsoniq 89a35154 `grammars/xquery.cson` | None: wcandillon/language-jsoniq 89a35154 is the origin | Apache-2.0 |
| `jsoniq` | `source.jsoniq` | wcandillon/language-jsoniq 89a35154 `grammars/jsoniq.cson` | None: wcandillon/language-jsoniq 89a35154 is the origin | Apache-2.0 |
| `yang` | `source.yang` | DzonyKalafut/language-yang c2d4a701 `grammars/yang.cson` | None: DzonyKalafut/language-yang c2d4a701 is the origin | MIT |
| `harbour` | `source.harbour` | hernad/atom-language-harbour ae860b3b `grammars/harbour.cson` | None: hernad/atom-language-harbour ae860b3b is the origin | MIT |
| `jisonlex` | `source.jisonlex` | cdibbs/language-jison ce8e2c78 `grammars/jisonlex.cson` | None: cdibbs/language-jison ce8e2c78 is the origin | MIT |
| `plantuml` | `source.wsd` | qjebbs/vscode-plantuml 7bc1758e `syntaxes/plantuml.yaml-tmLanguage` | None: qjebbs/vscode-plantuml 7bc1758e is the origin | MIT |
| `slint` | `source.slint` | slint-ui/slint-tmLanguage 45bd6116 `Slint.YAML-tmLanguage` | None: slint-ui/slint-tmLanguage 45bd6116 is the origin | MIT |
| `sln` | `source.solution` | Nixinova/NovaGrammars c6dfb955 `grammars/solution.yaml-tmLanguage` | None: Nixinova/NovaGrammars c6dfb955 is the origin | ISC |
| `robots-txt` | `text.robots-txt` | Nixinova/NovaGrammars c6dfb955 `grammars/robots-txt.yaml-tmLanguage` | None: Nixinova/NovaGrammars c6dfb955 is the origin | ISC |
| `jflex` | `source.jflex` | jflex-de/jflex.tmbundle 8b5c0cef `Syntaxes/JFlex.tmLanguage` | None: jflex-de/jflex.tmbundle 8b5c0cef is the origin | BSD-2-Clause |
| `agda` | `source.agda` | agda/agda-github-syntax-highlighting ba500584 `Agda.tmLanguage` | None: agda/agda-github-syntax-highlighting ba500584 is the origin | MIT |
| `lean4-markdown` | `source.lean4.markdown` | leanprover/vscode-lean4 5a25e6ab `vscode-lean4/syntaxes/lean4-markdown.json` | [microsoft/vscode@df3ae4ad](https://github.com/microsoft/vscode/blob/df3ae4adefbb780f5f686e58ac6a8d305a8c86dc/extensions/markdown-basics/syntaxes/markdown.tmLanguage.json) | Apache-2.0 |
| `regexp` | `source.regexp` | Alhadis/language-regexp 2e240580 `grammars/regexp.cson` | None: Alhadis/language-regexp 2e240580 is the origin | ISC |
| `regexp-posix` | `source.regexp.posix` | Alhadis/language-regexp 2e240580 `grammars/regexp-posix.cson` | None: Alhadis/language-regexp 2e240580 is the origin | ISC |
| `regexp-extended` | `source.regexp.extended` | Alhadis/language-regexp 2e240580 `grammars/regexp-extended.cson` | None: Alhadis/language-regexp 2e240580 is the origin | ISC |
| `syon` | `source.sy` | Alhadis/language-regexp 2e240580 `grammars/syon.cson` | None: Alhadis/language-regexp 2e240580 is the origin | ISC |
| `x86` | `source.x86` | calculuswhiz/Assembly-Syntax-Definition 519b7b3c `Assembly x86 AT&T.tmLanguage` | None: calculuswhiz/Assembly-Syntax-Definition 519b7b3c is the origin | MIT |
| `cfml-basic` | `text.cfml.basic` | SublimeText/ColdFusion dbe2f760 `Coldfusion.tmLanguage` | None: SublimeText/ColdFusion dbe2f760 is the origin | MIT |
| `eex-text` | `text.elixir` | elixir-lang/elixir-tmbundle eb63f1a5 `Syntaxes/EEx.tmLanguage` | None: elixir-lang/elixir-tmbundle eb63f1a5 is the origin | Apache-2.0 |
| `javadoc` | `text.html.javadoc` | textmate/javadoc.tmbundle 5276d7a9 `Syntaxes/JavaDoc.tmLanguage` | None: textmate/javadoc.tmbundle 5276d7a9 is the origin | TextMate Bundle License |
| `mson` | `text.html.markdown.source.gfm.mson` | apiaryio/api-blueprint-sublime-plugin 0a90eff1 `MSON.tmLanguage` | None: apiaryio/api-blueprint-sublime-plugin 0a90eff1 is the origin | MIT |
| `python-console` | `text.python.console` | MagicStack/MagicPython 7d0f2b22 `grammars/python-console.cson` | None: MagicStack/MagicPython 7d0f2b22 is the origin | MIT |
| `openesql` | `source.openesql` | spgennard/vscode_cobol 8be1f9a9 `syntaxes/openesql.tmLanguage.json` | [microsoft/vscode-mssql@61ae0eb2](https://github.com/microsoft/vscode-mssql/commit/61ae0eb21ac53883a23e09913a5ae77a59126ff9) | MIT |

The JSX and TSX grammars are registered separately from JavaScript and
TypeScript so the capture fixture `App.tsx` and real React files receive their
proper scopes. A language's configuration file is from the same source as its
grammar, with two exceptions: JSX uses JavaScript's configuration (Sora), and
YAML's configuration is from VS Code 307b60e7 while its grammar is from 337cacaa
(see below).

## Changed on import

tools/import-grammars.py makes these changes, each time it runs:

- `textmate/markdown/syntaxes/markdown.tmLanguage.json`: bold and italic split into literal-end rules (Sora back-reference bug)
- `textmate/docker/syntaxes/docker.tmLanguage.json`: iCode's grammar, written from moby's (which colours only instruction names and quoted strings): every part of an instruction is scoped (the image, its tag and digest, the stage name, `--flag=value` options, ARG, ENV and LABEL keys and values, EXPOSE ports and protocols, `$VAR` and `${VAR:-default}`, parser directives, heredocs, the exec form's JSON array), and the shell form of RUN, CMD, ENTRYPOINT and HEALTHCHECK CMD, and RUN heredocs, embed `source.shell`. Known gap: a line continues only after a backslash, so in a file whose `# escape=` directive picks the backtick each line continued with a backtick is read as a new instruction. Taking a backtick everywhere would break the shell's own backticks at a line's end; the fix is a rule that begins at the directive and never ends, holding a second set of every instruction rule with the backtick in place of the backslash, which this script could derive from the backslash rules rather than have it written by hand)
- `textmate/dotenv/syntaxes/dotenv.tmLanguage.json`: iCode's grammar, written from dotenv-org's (which reads each line with one pattern): a value may be unquoted, single-, double- or backtick-quoted, and quoted values may span lines; an unquoted number is a number and `true` or `false` a constant; `$VAR`, `${VAR}` and `${VAR:-default}` are variables, in unquoted and double-quoted values; a ` #` after an unquoted value starts a comment
- `textmate/make/language-configuration.json`: tm4e's parser reads `lineComment` only as a string; VS Code's form is an object with `noIndent`.
- `textmate/rel/rel.json`: a rule for numbers (`constant.numeric.rel`), which the grammar lacks, added to its top-level patterns
- `textmate/cmake/cmake.json`: a rule for numbers (`constant.numeric.cmake`), which the grammar lacks, added to its top-level patterns
- `textmate/tcl/Tcl.plist`: a rule for numbers (`constant.numeric.tcl`), which the grammar lacks, added to its top-level patterns
- `textmate/agc/agc.tmLanguage.json`: converted from CSON to JSON
- `textmate/cabal/cabal.tmLanguage.json`: converted from CSON to JSON
- `textmate/literate-haskell/literate haskell.tmLanguage.json`: converted from CSON to JSON
- `textmate/liquid-haskell/liquid haskell.tmLanguage.json`: converted from CSON to JSON
- `textmate/etc/etc.tmLanguage.json`: converted from CSON to JSON
- `textmate/checksums/checksums.tmLanguage.json`: converted from CSON to JSON
- `textmate/gitconfig/gitconfig.tmLanguage.json`: converted from CSON to JSON
- `textmate/hosts/hosts.tmLanguage.json`: converted from CSON to JSON
- `textmate/m3u/m3u.tmLanguage.json`: converted from CSON to JSON
- `textmate/mailmap/mailmap.tmLanguage.json`: converted from CSON to JSON
- `textmate/opts/opts.tmLanguage.json`: converted from CSON to JSON
- `textmate/shellcheckrc/shellcheckrc.tmLanguage.json`: converted from CSON to JSON
- `textmate/lex/lex.tmLanguage.json`: converted from CSON to JSON
- `textmate/lex-regexp/lex-regexp.tmLanguage.json`: converted from CSON to JSON
- `textmate/postscript/postscript.tmLanguage.json`: converted from CSON to JSON
- `textmate/srt/srt.tmLanguage.json`: converted from CSON to JSON
- `textmate/vtt/vtt.tmLanguage.json`: converted from CSON to JSON
- `textmate/firestore/firestore.tmLanguage.json`: converted from CSON to JSON
- `textmate/denizenscript/denizenscript.tmLanguage.json`: converted from CSON to JSON
- `textmate/eml/language-eml.tmLanguage.json`: converted from CSON to JSON
- `textmate/ecr/html (crystal - ecr).tmLanguage.json`: converted from CSON to JSON
- `textmate/lilypond/lilypond.tmLanguage.json`: converted from CSON to JSON
- `textmate/macaulay2/macaulay2.tmLanguage.json`: converted from CSON to JSON
- `textmate/meson/meson.tmLanguage.json`: converted from CSON to JSON
- `textmate/nunjucks/html (nunjucks templates).tmLanguage.json`: converted from CSON to JSON
- `textmate/nunjucks-source/nunjucks templates.tmLanguage.json`: converted from CSON to JSON
- `textmate/velocity/velocity.tmLanguage.json`: converted from CSON to JSON
- `textmate/xquery/xquery.tmLanguage.json`: converted from CSON to JSON
- `textmate/jsoniq/jsoniq.tmLanguage.json`: converted from CSON to JSON
- `textmate/yang/yang.tmLanguage.json`: converted from CSON to JSON
- `textmate/harbour/harbour.tmLanguage.json`: converted from CSON to JSON
- `textmate/jisonlex/jisonlex.tmLanguage.json`: converted from CSON to JSON
- `textmate/plantuml/plantuml.tmLanguage.json`: converted from YAML to JSON
- `textmate/slint/Slint.tmLanguage.json`: converted from YAML to JSON
- `textmate/sln/solution.tmLanguage.json`: converted from YAML to JSON
- `textmate/robots-txt/robots-txt.tmLanguage.json`: converted from YAML to JSON
- `textmate/regexp/regexp.tmLanguage.json`: converted from CSON to JSON
- `textmate/regexp-posix/regexp-posix.tmLanguage.json`: converted from CSON to JSON
- `textmate/regexp-extended/regexp-extended.tmLanguage.json`: converted from CSON to JSON
- `textmate/syon/syon.tmLanguage.json`: converted from CSON to JSON
- `textmate/python-console/python-console.tmLanguage.json`: converted from CSON to JSON
- `textmate/bsl/language-configuration.json`: not shipped, as it declares no comment and no bracket
- `textmate/glimmer-js/language-configuration.json`: not shipped, as it declares no comment and no bracket
- `textmate/glimmer-ts/language-configuration.json`: not shipped, as it declares no comment and no bracket
- `textmate/hurl/language-configuration.json`: lineComment object to its string
- `textmate/qmldir/language-configuration.json`: not shipped, as it declares no comment and no bracket
- `textmate/racket/racket.json`: 21 capture or repository entries that are not rules, dropped or emptied
- `textmate/rel/language-configuration.json`: blockComment ['doc"""', '"""', '/*', '*/'] to its first pair
- `textmate/stata/stata.json`: 1 capture or repository entries that are not rules, dropped or emptied
- `textmate/stata/language-configuration.json`: lineComment ['//', '*'] to its first
- `textmate/wikitext/wikitext.json`: 2 capture or repository entries that are not rules, dropped or emptied
- `textmate/gemini/language-configuration.json`: not shipped, as it declares no comment and no bracket

## Not shipped

Grammars the sources hold that iCode does not ship, and why:

- `angular-expression`: no file type opens it and no shipped grammar includes it
- `angular-inline-style`: an injection grammar, which Sora does not read
- `angular-inline-template`: an injection grammar, which Sora does not read
- `angular-let-declaration`: an injection grammar, which Sora does not read
- `angular-template`: an injection grammar, which Sora does not read
- `angular-template-blocks`: an injection grammar, which Sora does not read
- `dax`: no licence: huyza/dax-language states none at 7fd39aa8, and no other DAX grammar is known
- `es-tag-css`: an injection grammar, which Sora does not read
- `es-tag-glsl`: an injection grammar, which Sora does not read
- `es-tag-html`: an injection grammar, which Sora does not read
- `es-tag-sql`: an injection grammar, which Sora does not read
- `es-tag-xml`: an injection grammar, which Sora does not read
- `jinja-html`: no file type opens it and no shipped grammar includes it
- `markdown-nix`: an injection grammar, which Sora does not read
- `markdown-vue`: an injection grammar, which Sora does not read
- `razor`: VS Code's grammar for razor (text.html.cshtml) ships instead of text.aspnetcorerazor
- `vue-directives`: an injection grammar, which Sora does not read
- `vue-interpolations`: an injection grammar, which Sora does not read
- `vue-sfc-style-variable-injection`: an injection grammar, which Sora does not read
- `yaml-1.0`: no file type opens it and no shipped grammar includes it
- `yaml-1.1`: no file type opens it and no shipped grammar includes it
- `yaml-1.2`: no file type opens it and no shipped grammar includes it
- `yaml-1.3`: no file type opens it and no shipped grammar includes it
- `yaml-embedded`: no file type opens it and no shipped grammar includes it
- `source.c.platform` (VS Code): 568 KB of platform constants that no grammar includes
- `source.yaml` (VS Code): VS Code 337cacaa's YAML grammar ships instead (see YAML below)

## Names and extensions claimed twice

Each name and extension opens one language: the first to claim it (VS Code, then the language's
upstream, then its grammar's `fileTypes`, then Linguist, then iCode's own below), or the one the
icon theme draws it as. iCode's own claims, for formats the icon theme names that no source gives a
language:

- `xml`: `.tmlanguage` `.plist` `.manifest` `.project` `.jrxml` `.dmn` `.xml.dist.sample` `.bcf` `.vbox` `.vbox-prev` `.drawio` `.dio`: XML formats: TextMate grammars and property lists, Windows manifests, Eclipse projects, JasperReports, DMN, BibLaTeX control files, VirtualBox machines, draw.io diagrams
- `yaml`: `.yml.dist` `.yaml.dist`: YAML kept as a template to copy
- `json`: `.jsbeautifyrc` `.esformatter` `.whitesource` `.tldr` `.excalidraw`: JSON formats: js-beautify and esformatter settings, WhiteSource settings, tldraw and Excalidraw drawings
- `ini`: `.hgrc` `.coafile` `hgrc`: INI formats: Mercurial's settings, coala's settings
- `dotenv`: `.vars` `.dev.vars`: Cloudflare Workers' variables, in .env's syntax
- `latex`: `.latex` `.tikz` `.clo` `.bbl`: LaTeX source: documents, TikZ pictures, class options, BibTeX's output
- `handlebars`: `.mustache`: Mustache templates, which Handlebars extends
- `jinja`: `.jinja-html`: Jinja HTML templates
- `markdown`: `.rmd` `.qmd`: R Markdown and Quarto documents: Markdown with code chunks
- `csharp`: `.csharp`: C# source
- `nginx`: `.nginxconfig`: nginx settings
- `ruby`: `.vagrantfile`: a Vagrantfile, which is Ruby
- `groovy`: `.jenkins`: a Jenkins pipeline, which is Groovy
- `python`: `.snakemake`: a Snakemake workflow, which is Python
- `gnuplot`: `.gpi`: gnuplot scripts
- `sql`: `.plpgsql` `.psql`: PostgreSQL's SQL
- `xml`: `.cpn` `.pnml` `.dfxp` `.ttml` `.vcxitems` `.ruleset` `.sln.dotsettings.user`: XML formats: Petri nets, timed text subtitles, Visual Studio item and rule sets, ReSharper settings
- `json`: `.slnf` `.mjs.map` `.cjs.map` `.vsconfig` `.swift-format` `.graphqlconfig` `.firebaserc` `.mjmlconfig` `.ecrc` `.luaurc` `.parcelrc` `.stackblitzrc`: JSON formats: Visual Studio solution filters and components, source maps, and tools' settings written in JSON only
- `ini`: `.wrap` `.coarc` `.mrconfig` `.hgflow` `.sentryclirc`: INI formats: Meson wraps, coala, myrepos, hg-flow and Sentry settings
- `gdresource`: `.godot` `.gdextension`: Godot's project and extension files, in the syntax of its resources
- `tcl`: `.exp`: Expect scripts, which are Tcl
- `viml`: `.gvimrc` `.exrc`: Vim settings, which are Vim script
- `javascript`: `.js.snap` `.cjs.snap` `.mjs.snap` `.jsx.snap` `.ts.snap` `.mts.snap` `.cts.snap` `.tsx.snap` `.sequelizerc`: Jest snapshots and Sequelize's settings, which are JavaScript modules
- `python`: `.bazel` `scsub`: Bazel and SCons build files, which are Starlark and Python
- `jinja`: `.djt`: Django templates, whose tags are Jinja's
- `toml`: `.pdm.lock` `.replit`: TOML formats: PDM's lock file, Replit's settings
- `terraform`: `.tfbackend`: Terraform backend settings, which are HCL
- `cfml`: `.lucee`: Lucee, which is CFML
- `angular-html`: `.ng-template`: Angular templates
- `apiblueprint`: `.apiblueprint`: API Blueprint
- `abap-cds`: `.acds`: ABAP CDS
- `yaml`: `.snyk`: Snyk's policy, which is YAML
- `procfile`: `procfile.windows`: a Procfile for Windows
- `pkl`: `pklproject`: a Pkl project, which is Pkl
- `cabal`: `cabal.project.freeze` `cabal.project.local`: Cabal's project files, which are in Cabal's syntax
- `meson`: `meson.options`: Meson's options, the newer name of meson_options.txt
- `checksums`: `.shasum`: shasum's output, a list of checksums
- `shellcheckrc`: `shellcheckrc`: ShellCheck's settings without the dot, as it also reads them
- `slint`: `.60`: Slint's former extension, from when it was SixtyFPS
- `properties`: `.gitattributes-global` `.gitattributes_global`: global Git attributes, as VS Code reads .gitattributes
- `ignore`: `.gitignore-global`: a global .gitignore
- `ignore`: every dot file the icon theme names whose name ends in `ignore`, a list of paths in .gitignore's syntax

The claims that lost:

- extension `4dform`: `json`, not `jsonl` (Linguist)
- extension `4dproject`: `json`, not `jsonl` (Linguist)
- extension `ah2`: `ahk`, not `ahk2` (its upstream)
- extension `ahk`: `ahk`, not `ahk2` (its upstream)
- extension `asm`: `asm`, not `mipsasm` (its grammar's fileTypes)
- extension `asm`: `asm`, not `riscv` (its grammar's fileTypes)
- extension `asm`: `asm`, not `riscv` (its upstream)
- extension `aux`: `tex`, not `latex` (Linguist)
- extension `avsc`: `json`, not `jsonl` (Linguist)
- extension `bbx`: `tex`, not `latex` (Linguist)
- extension `bib`: `bibtex`, not `cobol` (its grammar's fileTypes)
- extension `bib`: `bibtex`, not `cobol` (its upstream)
- extension `cake`: `csharp`, not `coffeescript` (Linguist)
- extension `cbx`: `tex`, not `latex` (Linguist)
- extension `cfg`: `properties`, not `ini` (Linguist)
- extension `cgi`: `shell`, not `perl` (Linguist)
- extension `cgi`: `shell`, not `python` (Linguist)
- extension `cl`: `common-lisp`, not `c` (Linguist)
- extension `cls`: `tex`, not `apex` (Linguist)
- extension `cls`: `tex`, not `apex` (its grammar's fileTypes)
- extension `cls`: `tex`, not `latex` (Linguist)
- extension `code-snippets`: `snippets`, not `jsonc` (Linguist)
- extension `conf`: `properties`, not `apache` (its grammar's fileTypes)
- extension `conf`: `properties`, not `bird2` (its grammar's fileTypes)
- extension `conf`: `properties`, not `nginx` (its grammar's fileTypes)
- extension `conf`: `properties`, not `nginx` (its upstream)
- extension `container`: `systemd`, not `ini` (Linguist)
- extension `cp`: `cpp`, not `pascal` (Linguist)
- extension `cpy`: `python`, not `cobol` (Linguist)
- extension `cpy`: `python`, not `cobol` (its grammar's fileTypes)
- extension `cpy`: `python`, not `cobol` (its upstream)
- extension `cs`: `csharp`, not `smalltalk` (Linguist)
- extension `csl`: `xml`, not `kusto` (Linguist)
- extension `csl`: `xml`, not `kusto` (its grammar's fileTypes)
- extension `csl`: `xml`, not `kusto` (its upstream)
- extension `cy`: `cypher`, not `javascript` (Linguist)
- extension `d`: `d`, not `c` (Linguist)
- extension `d`: `d`, not `makefile` (Linguist)
- extension `ddl`: `plsql`, not `sql` (Linguist)
- extension `dtx`: `tex`, not `latex` (Linguist)
- extension `ejs`: `html`, not `ejs` (Linguist)
- extension `ejs`: `html`, not `ejs` (its grammar's fileTypes)
- extension `erb`: `ruby`, not `erb` (Linguist)
- extension `erb`: `ruby`, not `erb` (its grammar's fileTypes)
- extension `es`: `erlang`, not `javascript` (Linguist)
- extension `f`: `fortran-fixed-form`, not `forth` (Linguist)
- extension `fcgi`: `shell`, not `lua` (Linguist)
- extension `fcgi`: `shell`, not `perl` (Linguist)
- extension `fcgi`: `shell`, not `php` (Linguist)
- extension `fcgi`: `shell`, not `python` (Linguist)
- extension `fcgi`: `shell`, not `ruby` (Linguist)
- extension `fish`: `shell`, not `fish` (Linguist)
- extension `fish`: `shell`, not `fish` (its upstream)
- extension `flex`: `lex`, not `jflex` (Linguist)
- extension `flex`: `lex`, not `jflex` (its grammar's fileTypes)
- extension `for`: `fortran-fixed-form`, not `forth` (Linguist)
- extension `fr`: `forth`, not `haskell` (Linguist)
- extension `frag`: `glsl`, not `javascript` (Linguist)
- extension `fs`: `fsharp`, not `forth` (Linguist)
- extension `fs`: `fsharp`, not `forth` (its grammar's fileTypes)
- extension `fs`: `fsharp`, not `glsl` (Linguist)
- extension `fs`: `fsharp`, not `glsl` (its grammar's fileTypes)
- extension `geojson`: `json`, not `jsonl` (Linguist)
- extension `gitconfig`: `properties`, not `gitconfig` (Linguist)
- extension `gitconfig`: `properties`, not `gitconfig` (its grammar's fileTypes)
- extension `gitmodules`: `properties`, not `gitconfig` (its grammar's fileTypes)
- extension `gltf`: `json`, not `jsonl` (Linguist)
- extension `gs`: `genie`, not `glsl` (Linguist)
- extension `gs`: `genie`, not `glsl` (its grammar's fileTypes)
- extension `gs`: `genie`, not `javascript` (Linguist)
- extension `gs`: `genie`, not `vala` (its grammar's fileTypes)
- extension `gst`: `smalltalk`, not `xml` (Linguist)
- extension `h.in`: `cpp`, not `c` (Linguist)
- extension `h`: `cpp`, not `c` (Linguist)
- extension `h`: `cpp`, not `objective-c` (Linguist)
- extension `har`: `json`, not `jsonl` (Linguist)
- extension `hcl`: `hcl`, not `terraform` (Linguist)
- extension `hh`: `cpp`, not `hack` (Linguist)
- extension `hh`: `cpp`, not `hack` (its grammar's fileTypes)
- extension `hh`: `cpp`, not `hack` (its upstream)
- extension `hlsl`: `hlsl`, not `slang` (its upstream)
- extension `i`: `c`, not `asm` (Linguist)
- extension `ice`: `json`, not `jsonl` (Linguist)
- extension `inc`: `asm`, not `cpp` (Linguist)
- extension `inc`: `asm`, not `html` (Linguist)
- extension `inc`: `asm`, not `pascal` (Linguist)
- extension `inc`: `asm`, not `pawn` (Linguist)
- extension `inc`: `asm`, not `pawn` (its grammar's fileTypes)
- extension `inc`: `asm`, not `php` (Linguist)
- extension `inc`: `asm`, not `sql` (Linguist)
- extension `ins`: `tex`, not `latex` (Linguist)
- extension `json-tmlanguage`: `json`, not `jsonl` (Linguist)
- extension `json.example`: `json`, not `jsonl` (Linguist)
- extension `json5`: `json5`, not `javascript` (Linguist)
- extension `json`: `json`, not `jsonl` (Linguist)
- extension `jsonl`: `jsonl`, not `json` (Linguist)
- extension `jsonld`: `json`, not `javascript` (Linguist)
- extension `jsx`: `javascriptreact`, not `javascript` (Linguist)
- extension `l`: `common-lisp`, not `lex` (Linguist)
- extension `l`: `common-lisp`, not `lex` (its grammar's fileTypes)
- extension `lbx`: `tex`, not `latex` (Linguist)
- extension `ltx`: `latex`, not `tex` (Linguist)
- extension `m`: `objective-c`, not `matlab` (Linguist)
- extension `m`: `objective-c`, not `matlab` (its grammar's fileTypes)
- extension `m`: `objective-c`, not `wolfram` (Linguist)
- extension `m`: `objective-c`, not `wolfram` (its grammar's fileTypes)
- extension `m`: `objective-c`, not `wolfram` (its upstream)
- extension `mcmeta`: `json`, not `jsonl` (Linguist)
- extension `md`: `markdown`, not `mdc` (its upstream)
- extension `mkii`: `tex`, not `latex` (Linguist)
- extension `mkiv`: `tex`, not `latex` (Linguist)
- extension `mkvi`: `tex`, not `latex` (Linguist)
- extension `ml`: `ocaml`, not `sml` (Linguist)
- extension `mm`: `objective-cpp`, not `xml` (Linguist)
- extension `mojo`: `mojo`, not `xml` (Linguist)
- extension `mount`: `systemd`, not `ini` (Linguist)
- extension `msg`: `rosmsg`, not `eml` (its grammar's fileTypes)
- extension `mustache`: `handlebars`, not `smarty` (Linguist)
- extension `network`: `systemd`, not `ini` (Linguist)
- extension `nf`: `groovy`, not `nextflow` (Linguist)
- extension `nf`: `groovy`, not `nextflow` (its upstream)
- extension `nomad`: `hcl`, not `terraform` (Linguist)
- extension `p`: `pascal`, not `gnuplot` (Linguist)
- extension `p`: `pascal`, not `pawn` (its grammar's fileTypes)
- extension `php`: `php`, not `hack` (Linguist)
- extension `php`: `php`, not `hack` (its grammar's fileTypes)
- extension `php`: `php`, not `hack` (its upstream)
- extension `pl`: `perl`, not `prolog` (Linguist)
- extension `pl`: `perl`, not `prolog` (its grammar's fileTypes)
- extension `pl`: `perl`, not `prolog` (its upstream)
- extension `pl`: `perl`, not `raku` (Linguist)
- extension `plt`: `gnuplot`, not `prolog` (Linguist)
- extension `plt`: `gnuplot`, not `prolog` (its upstream)
- extension `pluginspec`: `xml`, not `ruby` (Linguist)
- extension `pm`: `perl`, not `c` (Linguist)
- extension `pm`: `perl`, not `raku` (Linguist)
- extension `pod`: `perl`, not `systemd` (its upstream)
- extension `pp`: `puppet`, not `pascal` (Linguist)
- extension `pp`: `puppet`, not `pascal` (its grammar's fileTypes)
- extension `prc`: `plsql`, not `sql` (Linguist)
- extension `prg`: `plsql`, not `harbour` (Linguist)
- extension `prg`: `plsql`, not `harbour` (its grammar's fileTypes)
- extension `pro`: `prolog`, not `ini` (Linguist)
- extension `properties`: `properties`, not `ini` (Linguist)
- extension `r`: `r`, not `rebol` (Linguist)
- extension `r`: `r`, not `rebol` (its grammar's fileTypes)
- extension `re`: `reason`, not `cpp` (Linguist)
- extension `res`: `rescript`, not `xml` (Linguist)
- extension `rest`: `http`, not `restructuredtext` (Linguist)
- extension `rhtml`: `html`, not `erb` (Linguist)
- extension `rhtml`: `html`, not `erb` (its grammar's fileTypes)
- extension `rkt`: `racket`, not `scheme` (its grammar's fileTypes)
- extension `rkt`: `racket`, not `scheme` (its upstream)
- extension `ron`: `markdown`, not `ron` (Linguist)
- extension `ron`: `markdown`, not `ron` (its upstream)
- extension `rs`: `rust`, not `xml` (Linguist)
- extension `s`: `asm`, not `mipsasm` (its grammar's fileTypes)
- extension `s`: `asm`, not `riscv` (its grammar's fileTypes)
- extension `s`: `asm`, not `riscv` (its upstream)
- extension `s`: `asm`, not `x86` (Linguist)
- extension `s`: `asm`, not `x86` (its grammar's fileTypes)
- extension `sarif`: `json`, not `jsonl` (Linguist)
- extension `sch`: `plsql`, not `scheme` (Linguist)
- extension `sch`: `plsql`, not `scheme` (its grammar's fileTypes)
- extension `sch`: `plsql`, not `scheme` (its upstream)
- extension `sch`: `plsql`, not `xml` (Linguist)
- extension `service`: `systemd`, not `ini` (Linguist)
- extension `shader`: `shaderlab`, not `glsl` (Linguist)
- extension `slnlaunch`: `json`, not `jsonl` (Linguist)
- extension `socket`: `systemd`, not `ini` (Linguist)
- extension `spec`: `python`, not `ruby` (Linguist)
- extension `sql`: `sql`, not `plsql` (Linguist)
- extension `sql`: `sql`, not `plsql` (its grammar's fileTypes)
- extension `sql`: `sql`, not `plsql` (its upstream)
- extension `ss`: `scheme`, not `cobol` (its grammar's fileTypes)
- extension `ss`: `scheme`, not `cobol` (its upstream)
- extension `sty`: `tex`, not `latex` (Linguist)
- extension `t`: `perl`, not `raku` (Linguist)
- extension `tab`: `plsql`, not `sql` (Linguist)
- extension `tab`: `plsql`, not `tsv` (its grammar's fileTypes)
- extension `tab`: `plsql`, not `tsv` (its upstream)
- extension `tact`: `json`, not `jsonl` (Linguist)
- extension `target`: `systemd`, not `ini` (Linguist)
- extension `tex`: `latex`, not `tex` (Linguist)
- extension `tf`: `terraform`, not `hcl` (Linguist)
- extension `tfstate.backup`: `json`, not `jsonl` (Linguist)
- extension `tfstate`: `json`, not `jsonl` (Linguist)
- extension `tfvars`: `terraform`, not `hcl` (Linguist)
- extension `timer`: `systemd`, not `ini` (Linguist)
- extension `toc`: `tex`, not `latex` (Linguist)
- extension `tofu`: `hcl`, not `terraform` (Linguist)
- extension `topojson`: `json`, not `jsonl` (Linguist)
- extension `trigger`: `shell`, not `apex` (Linguist)
- extension `trigger`: `shell`, not `apex` (its grammar's fileTypes)
- extension `ts`: `typescript`, not `xml` (Linguist)
- extension `tsx`: `typescriptreact`, not `xml` (Linguist)
- extension `typ`: `typst`, not `plsql` (its upstream)
- extension `typ`: `typst`, not `xml` (Linguist)
- extension `v`: `v`, not `coq` (Linguist)
- extension `v`: `v`, not `coq` (its grammar's fileTypes)
- extension `v`: `v`, not `coq` (its upstream)
- extension `v`: `v`, not `system-verilog` (its grammar's fileTypes)
- extension `v`: `v`, not `verilog` (Linguist)
- extension `v`: `v`, not `verilog` (its grammar's fileTypes)
- extension `v`: `v`, not `verilog` (its upstream)
- extension `vba`: `vb`, not `viml` (Linguist)
- extension `vh`: `system-verilog`, not `v` (its grammar's fileTypes)
- extension `vh`: `system-verilog`, not `verilog` (its grammar's fileTypes)
- extension `vh`: `system-verilog`, not `verilog` (its upstream)
- extension `vhost`: `apache`, not `nginx` (Linguist)
- extension `volt`: `html`, not `d` (Linguist)
- extension `vsh`: `hlsl`, not `glsl` (Linguist)
- extension `vsh`: `hlsl`, not `glsl` (its grammar's fileTypes)
- extension `vsh`: `hlsl`, not `v` (its grammar's fileTypes)
- extension `vsh`: `hlsl`, not `v` (its upstream)
- extension `webapp`: `json`, not `jsonl` (Linguist)
- extension `webmanifest`: `json`, not `jsonl` (Linguist)
- extension `workflow`: `xml`, not `hcl` (Linguist)
- extension `workflow`: `xml`, not `terraform` (Linguist)
- extension `yy`: `json`, not `jsonl` (Linguist)
- extension `yyp`: `json`, not `jsonl` (Linguist)
- name `.all-contributorsrc`: `json`, not `jsonl` (Linguist)
- name `.arcconfig`: `json`, not `jsonl` (Linguist)
- name `.auto-changelog`: `json`, not `jsonl` (Linguist)
- name `.autorc`: `json`, not `jsonl` (Linguist)
- name `.c8rc`: `json`, not `jsonl` (Linguist)
- name `.eslintrc`: `jsonc` by its extension, not `yaml` (Linguist)
- name `.flaskenv`: `dotenv`, not `shell` (Linguist)
- name `.gitconfig`: `properties` by its extension, not `gitconfig` (Linguist)
- name `.gitmodules`: `properties` by its extension, not `gitconfig` (Linguist)
- name `.htmlhintrc`: `json`, not `jsonl` (Linguist)
- name `.imgbotconfig`: `json`, not `jsonl` (Linguist)
- name `.jslintrc`: `json` by its extension, not `jsonc` (Linguist)
- name `.nycrc`: `json`, not `jsonl` (Linguist)
- name `.oxlintrc.json`: `json` by its extension, not `jsonc` (Linguist)
- name `.releaserc`: `yaml`, not `json` (Linguist)
- name `.releaserc`: `yaml`, not `jsonl` (Linguist)
- name `.secrets.baseline`: `json`, not `jsonl` (Linguist)
- name `.tern-config`: `json`, not `jsonl` (Linguist)
- name `.tern-project`: `json`, not `jsonl` (Linguist)
- name `.watchmanconfig`: `json`, not `jsonl` (Linguist)
- name `api-extractor.json`: `json` by its extension, not `jsonc` (Linguist)
- name `buildozer.spec`: `python` by its extension, not `ini` (Linguist)
- name `bun.lock`: `jsonc`, not `json` (Linguist)
- name `bun.lock`: `jsonc`, not `jsonl` (Linguist)
- name `composer.lock`: `json`, not `jsonl` (Linguist)
- name `deno.lock`: `json`, not `jsonl` (Linguist)
- name `flake.lock`: `json`, not `jsonl` (Linguist)
- name `hosts`: `hosts`, not `ini` (Linguist)
- name `makefile.boot`: `clojure` by its extension, not `makefile` (Linguist)
- name `makefile.frag`: `glsl` by its extension, not `makefile` (Linguist)
- name `makefile.inc`: `asm` by its extension, not `makefile` (Linguist)
- name `makefile.pc`: `c` by its extension, not `makefile` (Linguist)
- name `makefile.wat`: `wasm` by its extension, not `makefile` (Linguist)
- name `mcmod.info`: `json`, not `jsonl` (Linguist)
- name `module.bazel.lock`: `json`, not `jsonl` (Linguist)
- name `nim.cfg`: `properties` by its extension, not `nim` (Linguist)
- name `nim.cfg`: `properties` by its extension, not `nim` (its upstream)
- name `package.resolved`: `json`, not `jsonl` (Linguist)
- name `pipfile.lock`: `json`, not `jsonl` (Linguist)
- name `s`: `riscv`, not `x86` (its grammar's fileTypes)
- name `tslint.json`: `json` by its extension, not `jsonc` (Linguist)
- extension `wasm`: none, a WebAssembly module is bytes (FileTypes.Executable); its text form is `.wat`
- extension `txt`: none, plain text, though Linguist also gives it to Robot Framework
- extension `pdf`: none, a PDF is bytes (FileTypes.Pdf), though the PostScript grammar claims it
- extension `ai`: none, an Illustrator drawing is bytes, though the PostScript grammar claims it
- extension `aia`: none, an Illustrator action is bytes, though the PostScript grammar claims it
- extension `pfb`: none, a binary Type 1 font, though the PostScript grammar claims it
- extension `eps`: none, an EPS picture is shown as a picture, though it is PostScript
- extension `epsf`: none, an EPS picture is shown as a picture, though it is PostScript
- extension `epsi`: none, an EPS picture is shown as a picture, though it is PostScript
- extension `sum`: none, no one format: `go.sum` is Go's (go-sum), and the checksum grammar's other names stay its own
- name `authors`: none, a list of a project's authors is prose, though the Git mailmap grammar claims it

## Every language (2026-11-11, S6 part 1)

The catalog grew from 42 grammars to 307, so that every text type the icon theme names
opens coloured (docs/syntax-highlighting-plan.md §4.1, A9; `FileTypeCoverageTest`). Each
language's file names, extensions, name patterns and first lines are now the catalog's
`files`, not Kotlin rules: the build turns them into the table `domain/FileTypes.kt` reads,
so a language is declared in one place. Licences: Shiki's index gives none, `?` or
`NOASSERTION` for 33 grammars; each was checked in its upstream repository at the pinned
commit (the file or field is named in the script's `LICENCES`). DAX states none and is not
shipped; GLSL, Tcl and DreamMaker come from the maintained grammars Linguist vendors instead,
whose licences Linguist records. Every licence is compatible with iCode's GPL-3.0. VS Code's
`properties` language shares `source.ini` with `ini`, and Sora takes one configuration per
grammar, so `ini/properties.language-configuration.json` is no longer shipped: `.properties`
files use INI's configuration (`;` as the line comment), as they did.

## No gaps inside a file (2026-11-11, S6 part 2)

The catalog holds 357 grammars. The 50 added come from the repositories GitHub Linguist
vendors, at the commits Linguist pins: 41 are written in CSON (Atom's form) or YAML, which
Sora's engine does not read, so the script converts them to JSON as it copies them
(`cson_loads` and `yaml_loads`, small readers checked to give what CoffeeScript's own
`cson-parser` 4.0.9 and PyYAML 6.0.2 give for every one of these files and for all 70 of
language-etc's grammars). They open subtitles, checksums, Cabal, Meson, Nunjucks, XQuery,
YANG, PlantUML, Slint, Visual Studio solutions, robots.txt, hosts and mailmap files and the
other formats listed in the tables, and they ship the scopes other grammars include that no
source had before: Agda, Git config, Jison's lexer, PostScript, regular expressions, x86
assembly, Javadoc, CFML tags, EEx, MSON and Python sessions. Licences are Linguist's records
(`vendor/licenses/git_submodule`): MIT, ISC, Apache-2.0, BSD-2-Clause and the TextMate Bundle
License.

Two grammars are iCode's own, kept by the script (KEPT) and based on the ones VS Code ships:
Dockerfile (from moby's, Apache-2.0) and `.env` (from dotenv-org's, MIT). "Changed on import"
lists what each changes. Three grammars that colour no number get one rule for numbers by an
edit the script makes (`numbers` in EDITS): Rel, CMake and Tcl. A sample per language
(`app/src/test/resources/syntax-samples`, Shiki's where it has one, else iCode's) is
tokenized by `SyntaxSamplesTest`; the words it allows to stay plain are listed there with
their reasons.

## Colours (2026-09-29)

No theme file is shipped. The editor builds its TextMate theme for each palette at run time
(`ui/syntax/SyntaxTheme.kt`) from one scope table (`ui/syntax/SyntaxScopes.kt`) and the
palette's syntax colours (`designsystem/SyntaxColors.kt`, values in `IcodeColors.kt`). The
table's selectors are VS Code's default Dark+ rules — `extensions/theme-defaults/themes/`
`dark_vs.json` and `dark_plus.json` at the same pinned commit 307b60e7 (MIT) — each assigned to
an iCode role, followed by iCode's own additions for scopes those rules leave uncoloured. The
Sora sample theme `darcula.json` and the two preview themes are removed.

## Grammars added 2026-09-29 (editor-colors-plan E4)

Copied unchanged from VS Code at the same pinned commit 307b60e7 (the extension folder named),
each with its `language-configuration.json`. Their file names, extensions, name patterns and `#!`
programs in `domain/FileTypes.kt` are the extension's own `contributes.languages`. Their
sources, upstreams and licences are in the tables under "Shipped grammars".

Not shipped: `cpp/syntaxes/platform.tmLanguage.json` (568 KB, included by no grammar) and CUDA.
Languages the shipped grammars can embed but iCode does not ship (assembly and GLSL in C++, HAML,
Slim and GraphQL in Ruby, Javadoc in Groovy, Blade in PHP, and a Markdown fence's many languages)
are listed, exactly, in `GrammarAssetsTest`; their text keeps its enclosing colour. Path-only
patterns (`**/.git/config`, `**/rebase-merge/done`) are not matched: they are inside `.git` or a
home configuration folder, which the Explorer never lists.

YAML: VS Code's current grammar (RedCMD's YAML 1.2, adopted by VS Code in 2024) is not shipped. On the
editor's engine it scopes `key: value` as one plain scalar and every `:` as
`invalid.illegal.multiline-key` (EditorColoursTest). `yaml/syntaxes/yaml.tmLanguage.json` is instead
VS Code's long-standing grammar from textmate/yaml.tmbundle (TextMate Bundle License), as VS Code
itself shipped it until 2024, taken from VS Code commit 337cacaa (its October 2024 revert to it).

One edit to a copied file: `make/language-configuration.json` writes `lineComment` as the string
`"#"`. VS Code's form, an object with `noIndent`, is newer than the parser in Sora's tm4e, which
rejects it (the configuration would be lost and an error logged). `GrammarAssetsTest` loads every
configuration through that parser.

## The first sixteen languages, aligned (2026-09-29, E4c)

Their names, extensions, name patterns and first-line expressions in `domain/FileTypes.kt` are
now also their VS Code extensions' own (`java`, `python`, `javascript`, `typescript-basics`,
`json`, `html`, `xml`, `css`, `markdown-basics`, `shellscript`, `rust`, `go`, `yaml` at
307b60e7); Kotlin keeps `kt`, `kts` (Sora's grammar; VS Code has none). JSON with comments
(`.jsonc`, `.eslintrc`, `tsconfig.json`) uses the shipped `source.json`, whose grammar already
colours comments. Added from the same commit: the language configurations JSON, TypeScript, CSS,
shell, Rust, Go and YAML lacked; XSL (`xml/syntaxes/xsl.tmLanguage.json`) and JSON Lines
(`json/syntaxes/JSONL.tmLanguage.json`), each with its configuration.

Second edit, withdrawn with those files: three notes inside YAML 1.x `beginCaptures`; the grammar
now shipped has none. `GrammarAssetsTest` still fails on any capture that is not a rule.

## The regex engine and one engine bug (2026-09-29)

- **Native Oniguruma** (`io.github.rosemoe:oniguruma-native`, Sora 0.24.6; Oniguruma is BSD-2): the
  engine the grammars are written for. Without it tm4e uses joni, which rejects 9 of the 9,649
  regular expressions in the shipped grammars (variable-length lookbehinds) and drops their rules:
  CSS selectors, TypeScript's `export default`, C#'s `await`, Ruby block parameters. The JVM tests
  still run joni; `EditorColoursDeviceTest` runs the same check on the phone.
- **Sora bug, back-references:** tm4e quotes a captured text for `Matcher.appendReplacement`, but
  Sora's `MatcherUtils.replaceAll` appends it literally, so an `end` that refers to a capture
  containing a regular-expression character becomes a different pattern: `(\1)` for `**` ends at
  once. Third edit to a copied file: `markdown/syntaxes/markdown.tmLanguage.json` splits `bold` and
  `italic` into an asterisk rule and an underscore rule (`bold_underscore`, `italic_underscore`)
  with literal ends, same behaviour as VS Code. Still affected until Sora is fixed: Rust `r#"…"#`
  and Swift `#"…"#` raw strings, Ruby `%w[…]`-style literals with a bracket or `|` delimiter,
  heredoc names containing `-` or `.`.

