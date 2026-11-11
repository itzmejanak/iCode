package dev.icode.mobile.ui.syntax

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import dev.icode.mobile.domain.syntax.Language
import dev.icode.mobile.domain.syntax.Languages
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.GrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.registry.IGrammarSource

/**
 * The one owner of the TextMate engine (docs/syntax-highlighting-plan.md D2, D4). Nothing is parsed at
 * app start: the catalog is read, and a language's grammar parsed with what it includes, on the one
 * loader thread when a file or block first needs it. Threads cannot share a tm4e grammar (§3.7, S1), so
 * each editor gets an instance of its own ([language]) and the read-only thread has its own, which
 * [tokens] uses. A grammar that fails is its language's alone, logged once ([failure]).
 */
internal object SyntaxEngine {
    private const val TAG = "iCodeSyntax"
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { Thread(it, "syntax-grammars") }

    /** The read-only views' one tokenizing thread (D6): its grammars and its cache are used there only. */
    private val readOnly: CoroutineDispatcher = Executors.newSingleThreadExecutor { Thread(it, "syntax-tokens") }.asCoroutineDispatcher()

    /** Made on the loader thread at the first request; read from any thread only for [failure]. */
    @Volatile private var grammars: GrammarLoader<GrammarRegistry>? = null

    /** The read-only thread's grammar of each language asked for, null for one that failed; [readOnly] only. */
    private val readOnlyGrammars = mutableMapOf<String, IGrammar?>()

    /** The read-only views' spans; [readOnly] only. */
    private val cache = SyntaxCache()

    /** Why language [id]'s grammar failed to load, or null if it loaded or has not been asked for; any thread. */
    fun failure(id: String): Throwable? = grammars?.failure(id)

    /**
     * A new editor language for catalog language [id], handed to [onReady] on the main thread: Sora's
     * TextMate language on a grammar instance of its own, within [SyntaxLimits], drawn with the current
     * theme; or why it could not load. A language [onReady] does not keep must be destroyed.
     */
    @MainThread fun language(context: Context, id: String, onReady: (Result<TextMateLanguage>) -> Unit) {
        val app = context.applicationContext
        loader.execute {
            val loaded = runCatching { grammars(app).let { it.load(id) to it.catalog.byId(id)!!.scope } }
            main.post { onReady(loaded.mapCatching { (registry, scope) -> EditorLanguage.of(registry, scope) }) }
        }
    }

    /**
     * [text]'s spans as catalog language [id] (D5, D6), made on [readOnly] with that thread's own grammar
     * and kept in its cache; null when [id]'s grammar failed, [text] is past [SyntaxLimits.BLOCK_CHARS],
     * or tokenizing threw.
     */
    suspend fun tokens(context: Context, id: String, text: String): List<SyntaxSpan>? {
        val app = context.applicationContext
        return withContext(readOnly) {
            cache.tokens(id, text) {
                runCatching { readOnlyGrammar(app, id)?.let { syntaxSpans(it, text) } }
                    .onFailure { Log.e(TAG, "Tokenizing $id failed; the block stays plain", it) }
                    .getOrNull()
            }
        }
    }

    /** Catalog language [id]'s grammar for the read-only thread, null when it could not load. [readOnly] only: it waits for the loader the first time. */
    private fun readOnlyGrammar(context: Context, id: String): IGrammar? {
        // Not getOrPut: a language that failed is kept as null, and not asked for again.
        if (readOnlyGrammars.containsKey(id)) return readOnlyGrammars[id]
        val grammar: IGrammar? = runCatching {
            loader.submit(Callable { grammars(context).let { it.load(id) to it.catalog.byId(id)!!.scope } }).get()
        }.mapCatching { (registry, scope) ->
            LimitedGrammar(checkNotNull(registry.findGrammar(scope)) { "$scope did not load" })
        }.getOrNull()
        readOnlyGrammars[id] = grammar
        return grammar
    }

    /** The loader, made the first time: on the loader thread only. */
    private fun grammars(context: Context): GrammarLoader<GrammarRegistry> = grammars ?: run {
        val assets = context.assets
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))
        val catalog = Languages.parse(assets.open(Languages.ASSET).bufferedReader().use { it.readText() })
        GrammarLoader(catalog, { GrammarRegistry(null) }, { loadGrammar(definition(it)) }) { language, error ->
            Log.e(TAG, "The ${language.id} grammar failed to load; its files stay plain", error)
        }.also { grammars = it }
    }

    /** [language] as Sora registers a grammar: its file, configuration, name and scope, and what it embeds. */
    private fun definition(language: Language): GrammarDefinition {
        val file = checkNotNull(FileProviderRegistry.getInstance().tryGetInputStream(language.grammar)) { "${language.grammar} cannot be opened" }
        val definition = DefaultGrammarDefinition.withLanguageConfiguration(
            IGrammarSource.fromInputStream(file, language.grammar, Charsets.UTF_8),
            language.configuration,
            language.id,
            language.scope,
        )
        return if (language.embedded.isEmpty()) definition else definition.withEmbeddedLanguages(language.embedded)
    }
}

/**
 * Sora's TextMate language on a grammar of its own [registry], which follows the current theme as Sora's
 * shared registry did, so the colour ids it gives are the theme the editor draws with.
 */
private class EditorLanguage private constructor(private val registry: GrammarRegistry, grammar: IGrammar, private val themes: ThemeRegistry) :
    TextMateLanguage(LimitedGrammar(grammar), registry.findLanguageConfiguration(grammar.scopeName), registry, themes, false) {
    private val follow = ThemeRegistry.ThemeChangeListener { registry.setTheme(it) }

    init {
        themes.addListener(follow)
    }

    override fun destroy() {
        themes.removeListener(follow)
        super.destroy()
    }

    companion object {
        @MainThread fun of(registry: GrammarRegistry, scope: String): EditorLanguage {
            val themes = ThemeRegistry.getInstance()
            registry.setTheme(checkNotNull(themes.currentThemeModel) { "No TextMate theme is current" })
            return EditorLanguage(registry, checkNotNull(registry.findGrammar(scope)) { "$scope did not load" }, themes)
        }
    }
}
