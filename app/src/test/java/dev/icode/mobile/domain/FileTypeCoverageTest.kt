package dev.icode.mobile.domain

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every text type the icon theme names is coloured (docs/syntax-highlighting-plan.md A9): each language id,
 * extension and file name in assets/file-icons/theme.json opens, through [FileTypes], a type with a grammar
 * in the catalog, or a format of other bytes, or is exempted here with its reason, by the icon it is drawn
 * with (a group) or one by one. An exemption nothing needs any more fails too.
 */
class FileTypeCoverageTest {
    private val theme = JSONObject(File("src/main/assets/file-icons/theme.json").readText())

    /** What the theme names that [FileTypes] opens with no grammar: `extension psd`, `name license`, `language git`, each with its icon. */
    private fun uncovered(): Map<String, String> = buildMap {
        fun coloured(type: FileType) = !type.text || type.language != null
        val extensions = theme.getJSONObject("fileExtensions")
        extensions.keys().forEach { if (!coloured(FileTypes.of("x.$it"))) put("extension $it", extensions.getString(it)) }
        val names = theme.getJSONObject("fileNames")
        names.keys().forEach { if (!coloured(FileTypes.of(it.substringAfterLast('/')))) put("name $it", names.getString(it)) }
        val ids = theme.getJSONObject("languageIds")
        ids.keys().forEach { id ->
            val known = FileTypes.rules.any { (it.type.id == id || LanguageServiceRules.languageIdOf(it.type) == id) && it.type.language != null }
            if (!known) put("language $id", ids.getString(id))
        }
    }

    @Test fun `every text type the icon theme names has a grammar, or a reason it has none`() {
        val missing = uncovered().filter { (key, icon) -> key !in ENTRIES && icon !in GROUPS }
        assertEquals(emptyMap<String, String>(), missing)
    }

    @Test fun `every exemption is still needed`() {
        val uncovered = uncovered()
        assertEquals(emptySet<String>(), ENTRIES.keys - uncovered.keys)
        val drawn = uncovered.filterKeys { it !in ENTRIES }.values.toSet()
        assertEquals(emptySet<String>(), GROUPS.keys - drawn)
    }

    private companion object {
        const val BYTES = "a format of its own: bytes, or numbers in text (a 3D model, a picture), that no grammar colours"
        const val PROSE = "prose, with no syntax to colour"
        const val NONE = "no grammar in VS Code, Shiki or the grammars GitHub Linguist vendors"
        const val CSON = "Linguist vendors a grammar for it only in CSON, which Sora's engine does not read"
        const val YAML = "Linguist vendors a grammar for it only in YAML, which the import and its tests do not read yet"
        const val KEYS = "a key or certificate: PEM text or DER bytes, with no grammar"
        const val VERSION = "one version, name or number, with no syntax"
        const val EMPTY = "an empty file that keeps a folder or marks a package"
        const val OUTPUT = "a tool's output or cache, not edited by hand"
        const val AMBIGUOUS = "JSON, YAML or JavaScript, as the tool allows: the name cannot say which"
        const val FIRST_LINE = "a script in any language, which its first line names (a Git hook, artisan)"
        const val NOT_ICODE = "a language id of an extension iCode does not have: its files are matched by name and extension"

        /** Icon groups whose every entry left is exempt for one reason. */
        val GROUPS = mapOf(
        "3d" to BYTES, "adobe-illustrator" to BYTES, "adobe-photoshop" to BYTES, "adobe-swc" to BYTES,
        "adonis" to FIRST_LINE, "applescript" to BYTES, "architecture" to PROSE, "audio" to BYTES,
        "authors" to PROSE, "babel" to AMBIGUOUS, "ballerina" to BYTES, "bithound" to AMBIGUOUS, "blender" to BYTES,
        "bucklescript" to BYTES, "changelog" to PROSE, "chrome" to BYTES, "cline" to PROSE,
        "commitizen" to AMBIGUOUS, "commitlint" to AMBIGUOUS, "conduct" to PROSE, "console" to FIRST_LINE,
        "contributing" to PROSE, "craco" to AMBIGUOUS, "credits" to PROSE, "database" to BYTES, "disc" to BYTES,
        "dll" to BYTES, "document" to PROSE, "eslint" to AMBIGUOUS, "exe" to BYTES, "figma" to BYTES,
        "flash" to BYTES, "font" to BYTES, "foxpro" to BYTES, "gamemaker" to BYTES, "husky" to AMBIGUOUS,
        "image" to BYTES, "installation" to PROSE, "jest" to AMBIGUOUS, "laravel" to FIRST_LINE,
        "lefthook" to AMBIGUOUS, "license" to PROSE, "lintstaged" to AMBIGUOUS, "lottie" to BYTES,
        "minecraft" to BYTES, "modernizr" to AMBIGUOUS, "mrpack" to BYTES, "nano-staged" to AMBIGUOUS,
        "nuget" to BYTES, "ocaml" to BYTES, "onnx" to BYTES, "packship" to AMBIGUOUS, "palette" to BYTES,
        "postcss" to AMBIGUOUS, "posthtml" to AMBIGUOUS, "powerpoint" to BYTES, "prettier" to AMBIGUOUS,
        "puppeteer" to AMBIGUOUS, "pytorch" to BYTES, "readme" to PROSE, "remark" to AMBIGUOUS,
        "renovate" to AMBIGUOUS, "roadmap" to PROSE, "roblox" to BYTES, "robots" to YAML, "sas" to BYTES,
        "semantic-release" to AMBIGUOUS, "simulink" to BYTES, "sketch" to BYTES, "slint" to YAML,
        "stylelint" to AMBIGUOUS, "svgr" to AMBIGUOUS, "syncpack" to AMBIGUOUS, "taze" to AMBIGUOUS,
        "textlint" to AMBIGUOUS, "todo" to PROSE, "uml" to YAML, "unity" to BYTES, "unlicense" to PROSE,
        "verilog" to BYTES, "video" to BYTES, "virtual" to BYTES, "zip" to BYTES,
        )

        /** Entries exempt one by one. */
        val ENTRIES = mapOf(
        "extension abc" to NONE, "extension aea" to NONE, "extension ags" to NONE, "extension alloy" to NONE,
        "extension argus" to NONE, "extension ass" to NONE, "extension azcli" to NONE, "extension binsource" to NONE,
        "extension blink" to NONE, "extension bubble" to NONE, "extension cleanpeg" to NONE, "extension cm" to NONE,
        "extension coco" to NONE, "extension code-workplace" to NONE, "extension computeshader" to NONE,
        "extension config" to NONE, "extension d.ets" to NONE, "extension dlc" to NONE, "extension duc" to NONE,
        "extension eex" to NONE, "extension emlx" to NONE, "extension esx" to NONE, "extension fast" to NONE,
        "extension features" to NONE, "extension fen" to NONE, "extension fhtml" to NONE,
        "extension fragmentshader" to NONE, "extension geometryshader" to NONE, "extension gr" to NONE,
        "extension gram" to NONE, "extension grm" to NONE, "extension hex" to NONE, "extension hip" to NONE,
        "extension hp" to NONE, "extension html.bubble" to NONE, "extension html_vm" to NONE,
        "extension huff" to NONE, "extension kl" to NONE, "extension lang" to NONE, "extension lock" to NONE,
        "extension lrc" to NONE, "extension markdoc" to NONE, "extension merlin" to NONE, "extension mi" to NONE,
        "extension mii" to NONE, "extension mitigus" to NONE, "extension mlb" to NONE, "extension mlton" to NONE,
        "extension ms" to NONE, "extension opam" to NONE, "extension option" to NONE, "extension otne" to NONE,
        "extension passwd" to NONE, "extension peg" to NONE, "extension pest" to NONE, "extension pgn" to NONE,
        "extension php.bubble" to NONE, "extension pine" to NONE, "extension pipeline" to NONE,
        "extension prop" to NONE, "extension prx" to NONE, "extension psv" to NONE, "extension ptm" to NONE,
        "extension pure" to NONE, "extension rc" to NONE, "extension restql" to NONE, "extension rql" to NONE,
        "extension san" to NONE, "extension sbv" to NONE, "extension secret" to NONE, "extension settings" to NONE,
        "extension spwn" to NONE, "extension ssa" to NONE, "extension sub" to NONE, "extension svx" to NONE,
        "extension sy" to NONE, "extension tag" to NONE, "extension tauri" to NONE, "extension template" to NONE,
        "extension tlpp" to NONE, "extension tobi" to NONE, "extension tool-versions" to NONE,
        "extension toon" to NONE, "extension tosd" to NONE, "extension tree" to NONE, "extension tw" to NONE,
        "extension twee" to NONE, "extension use" to NONE, "extension vbhtml" to NONE, "extension ved" to NONE,
        "extension veda" to NONE, "extension vedic" to NONE, "extension vertexshader" to NONE,
        "extension vfl" to NONE, "extension vm" to NONE, "extension windi" to NONE, "extension wpy" to NONE,
        "extension xcplayground" to NONE, "extension zeabur" to NONE, "extension ц" to NONE, "name ._sc_" to NONE,
        "name .astylerc" to NONE, "name .bazelrc" to NONE, "name .cursor" to NONE, "name .cursorrc" to NONE,
        "name .flowconfig" to NONE, "name .git" to NONE, "name .git-for-windows-updater" to NONE,
        "name .gitinclude" to NONE, "name .hg" to NONE, "name .hgtags" to NONE, "name .hhconfig" to NONE,
        "name .kl" to NONE, "name .mailmap" to NONE, "name .mcattributes" to NONE, "name .mcdefinitions" to NONE,
        "name .mincloudrc" to NONE, "name .nuxtrc" to NONE, "name .pug-lintrc" to NONE,
        "name .qa-mincloudrc" to NONE, "name .scrapy" to NONE, "name .secrets" to NONE, "name .swiftformat" to NONE,
        "name .tobimake" to NONE, "name .vfl" to NONE, "name .yarnclean" to NONE, "name .yarnrc" to NONE,
        "name _sc_" to NONE, "name cname" to NONE, "name compile_flags.txt" to NONE, "name duc.fbs" to NONE,
        "name dune-workspace" to NONE, "name dune-workspace.dev" to NONE, "name excalidraw" to NONE,
        "name git-history" to NONE, "name jvm.config" to NONE, "name manifest.in" to NONE,
        "name maven.config" to NONE, "name owners" to NONE, "name phpstan.dist.neon" to NONE,
        "name phpstan.neon" to NONE, "name phpstan.neon.dist" to NONE, "name plastic.branchexplorer" to NONE,
        "name plastic.selector" to NONE, "name plastic.wktree" to NONE, "name plastic.workspace" to NONE,
        "name plastic.workspaces" to NONE, "name security" to NONE, "name security.txt" to NONE,
        "name tsdown.config" to NONE, "name werf-includes.lock" to NONE, "name xmake" to NONE,
        "extension agc" to CSON, "extension cabal" to CSON, "extension ch" to CSON, "extension dsc" to CSON,
        "extension ecr" to CSON, "extension eml" to CSON, "extension lex" to CSON, "extension lhs" to CSON,
        "extension ly" to CSON, "extension m2" to CSON, "extension m3u" to CSON, "extension m3u8" to CSON,
        "extension mbox" to CSON, "extension njk" to CSON, "extension nunjucks" to CSON, "extension prw" to CSON,
        "extension sha256" to CSON, "extension sha256sum" to CSON, "extension sha256sums" to CSON,
        "extension shasum" to CSON, "extension srt" to CSON, "extension vtl" to CSON, "extension vtt" to CSON,
        "extension xquery" to CSON, "extension yang" to CSON, "name .rspec" to CSON, "name .shellcheckrc" to CSON,
        "name .yardopts" to CSON, "name cabal.project.freeze" to CSON, "name cabal.project.local" to CSON,
        "name firestore.rules" to CSON, "name meson.options" to CSON, "name meson_options.txt" to CSON,
        "name mocha.opts" to CSON, "name sha256sums" to CSON, "name shellcheckrc" to CSON,
        "extension sln" to YAML,
        "extension cer" to KEYS, "extension cert" to KEYS, "extension crt" to KEYS, "extension key" to KEYS,
        "extension pem" to KEYS, "extension pub" to KEYS,
        "extension .wakatime-project" to VERSION, "name .bazelversion" to VERSION, "name .bun-version" to VERSION,
        "name .node-version" to VERSION, "name .nvmrc" to VERSION, "name .pdm-python" to VERSION,
        "name .python-version" to VERSION, "name .ruby-version" to VERSION, "name .swift-version" to VERSION,
        "name .wakatime-project" to VERSION, "name .xcode-version" to VERSION, "name cdp.pid" to VERSION,
        "name celerybeat.pid" to VERSION,
        "name .gitkeep" to EMPTY, "name .gitpreserve" to EMPTY, "name .keep" to EMPTY, "name py.typed" to EMPTY,
        "extension ali" to OUTPUT, "extension blg" to OUTPUT, "extension swiftdeps" to OUTPUT,
        "extension synctex" to OUTPUT, "extension viminfo" to OUTPUT, "name .eslintcache" to OUTPUT,
        "name .phpunit.result.cache" to OUTPUT, "name .stylelintcache" to OUTPUT, "name .yarn-integrity" to OUTPUT,
        "name cmakecache.txt" to OUTPUT, "name tags" to OUTPUT,
        "name .config/graphqlrc" to AMBIGUOUS, "name .esmrc" to AMBIGUOUS, "name .graphqlrc" to AMBIGUOUS,
        "language plaintext" to PROSE, "name .cursorrules" to PROSE,
        "extension amx" to BYTES, "extension bin" to BYTES, "extension dat" to BYTES, "extension dhallb" to BYTES,
        "extension edb" to BYTES, "extension egg" to BYTES, "extension gpg" to BYTES, "extension ibc" to BYTES,
        "extension mo" to BYTES, "extension oft" to BYTES, "extension olm" to BYTES, "extension ost" to BYTES,
        "extension p7s" to BYTES, "extension pst" to BYTES, "extension pyc" to BYTES, "extension rpmsg" to BYTES,
        "extension spv" to BYTES, "extension suo" to BYTES, "extension swiftdoc" to BYTES,
        "extension swiftmodule" to BYTES, "extension swiftsourceinfo" to BYTES, "extension tnef" to BYTES,
        "extension vsix" to BYTES, "extension whl" to BYTES, "language pdf" to BYTES, "name .coverage" to BYTES,
        "name bun.lockb" to BYTES, "name celerybeat-schedule" to BYTES,
        "language ansible" to NOT_ICODE, "language ansible-jinja" to NOT_ICODE,
        "language aspnetcorerazor" to NOT_ICODE, "language bibtex-style" to NOT_ICODE, "language blink" to NOT_ICODE,
        "language cabal" to NOT_ICODE, "language cake" to NOT_ICODE, "language capnb" to NOT_ICODE,
        "language cds-markdown-injection" to NOT_ICODE, "language concourse-pipeline-yaml" to NOT_ICODE,
        "language concourse-task-yaml" to NOT_ICODE, "language context" to NOT_ICODE,
        "language cucumber" to NOT_ICODE, "language dhall" to NOT_ICODE, "language django-html" to NOT_ICODE,
        "language django-txt" to NOT_ICODE, "language dockerbake" to NOT_ICODE, "language doctex" to NOT_ICODE,
        "language doctex-installer" to NOT_ICODE, "language editorconfig" to NOT_ICODE, "language ets" to NOT_ICODE,
        "language git" to NOT_ICODE, "language github-actions-workflow" to NOT_ICODE, "language grain" to NOT_ICODE,
        "language helm" to NOT_ICODE, "language hosts" to NOT_ICODE, "language huff" to NOT_ICODE,
        "language jupyter" to NOT_ICODE, "language kql" to NOT_ICODE, "language lang-cfml" to NOT_ICODE,
        "language latex-class" to NOT_ICODE, "language latex-expl3" to NOT_ICODE,
        "language latex-package" to NOT_ICODE, "language markdoc" to NOT_ICODE, "language mjml" to NOT_ICODE,
        "language ng-template" to NOT_ICODE, "language nimble" to NOT_ICODE, "language nunjucks" to NOT_ICODE,
        "language opentofu" to NOT_ICODE, "language otne" to NOT_ICODE, "language perl6" to NOT_ICODE,
        "language pgn" to NOT_ICODE, "language psv" to NOT_ICODE, "language pug" to NOT_ICODE,
        "language reason_lisp" to NOT_ICODE, "language rsweave" to NOT_ICODE, "language slint" to NOT_ICODE,
        "language spring-boot-properties" to NOT_ICODE, "language spring-boot-properties-yaml" to NOT_ICODE,
        "language ssh_config" to NOT_ICODE, "language stylable" to NOT_ICODE, "language svg" to NOT_ICODE,
        "language systemd-conf" to NOT_ICODE, "language systemd-unit-file" to NOT_ICODE,
        "language testoutput" to NOT_ICODE, "language text-gemini" to NOT_ICODE, "language tree" to NOT_ICODE,
        "language twee3" to NOT_ICODE, "language twee3-chapbook-1" to NOT_ICODE,
        "language twee3-harlowe-3" to NOT_ICODE, "language twee3-sugarcube-2" to NOT_ICODE,
        "language vue-postcss" to NOT_ICODE, "language xquery" to NOT_ICODE, "language yang" to NOT_ICODE,
        )
    }
}
