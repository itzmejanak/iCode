package dev.icode.mobile.domain.phone

/**
 * The screen as the model reads it (docs/phone-agent-plan.md §5.2): one line per element that shows
 * text or can be acted on, numbered in reading order. Hidden and off-screen views and pure layouts are
 * left out; a row that can be tapped reads as one element with what its children say, as TalkBack
 * reads it; a password's text is never read; overlays — iCode's bubble and glow among them — are not
 * on the screen the model sees.
 */
object PhoneReading {
    /** The deepest a tree is read: Mobilerun Portal's guard, for apps that nest without end. */
    const val MAX_DEPTH = 128

    /** Elements listed at most; the rest are counted. */
    const val MAX_ELEMENTS = 300

    /** Characters of a label at most. */
    const val MAX_LABEL = 100

    /** [windows], top-most first as Android lists them, read as screen [id]. */
    fun screen(id: Int, windows: List<PhoneWindow>): PhoneScreen {
        val elements = mutableListOf<PhoneElement>()
        val front = windows.firstOrNull { it.active && it.kind == PhoneWindow.Kind.App }
            ?: windows.firstOrNull { it.kind == PhoneWindow.Kind.App }
            ?: windows.firstOrNull { it.kind == PhoneWindow.Kind.System }
        windows.forEachIndexed { index, window ->
            if (window.kind == PhoneWindow.Kind.App || window.kind == PhoneWindow.Kind.System) {
                window.root?.let { read(it, window.app, listOf(index), 0, elements) }
            }
        }
        val listed = elements.take(MAX_ELEMENTS).mapIndexed { index, element -> element.copy(number = index + 1) }
        return PhoneScreen(
            id = id,
            app = front?.app.orEmpty(),
            title = front?.title,
            elements = listed,
            keyboard = windows.any { it.kind == PhoneWindow.Kind.Keyboard },
            more = (elements.size - MAX_ELEMENTS).coerceAtLeast(0),
        )
    }

    /** [screen] as the model reads it. */
    fun text(screen: PhoneScreen): String = buildString {
        append("Screen ${screen.id} · ")
        append(screen.title?.let { "$it (${screen.app})" } ?: screen.app.ifEmpty { "nothing in front" })
        appendLine()
        if (screen.keyboard) appendLine("The keyboard is open.")
        if (screen.elements.isEmpty()) appendLine("Nothing on it can be read as text.")
        screen.elements.forEach { appendLine(line(it)) }
        if (screen.more > 0) appendLine("(${screen.more} more elements not listed: scroll, or look at the screen)")
    }.trimEnd()

    /** `[3] switch "Wi‑Fi" · on · tap`. */
    fun line(element: PhoneElement): String = buildString {
        append("[${element.number}] ${element.role}")
        if (element.label.isNotEmpty()) append(" \"${element.label}\"")
        element.states.forEach { append(" · $it") }
        if (element.abilities.isNotEmpty()) append(" · " + PhoneAbility.entries.filter { it in element.abilities }.joinToString("/") { it.word })
    }

    private fun read(node: PhoneNode, app: String, path: List<Int>, depth: Int, into: MutableList<PhoneElement>) {
        if (depth > MAX_DEPTH || !node.visible || node.bounds.empty) return
        val abilities = abilities(node)
        if (abilities.isNotEmpty() || node.editable) {
            // An element of its own: what it says, and what its children that do nothing say.
            val words = (listOfNotNull(own(node)) + if (node.editable) emptyList() else said(node.children, depth + 1)).distinct()
            into += element(node, words, abilities, app, path)
            node.children.forEachIndexed { index, child -> readActionable(child, app, path + index, depth + 1, into) }
            return
        }
        val label = own(node)
        if (label != null) into += element(node, listOf(label), abilities, app, path)
        node.children.forEachIndexed { index, child -> read(child, app, path + index, depth + 1, into) }
    }

    /** Under an element: only what can itself be acted on is listed apart; its words are the element's. */
    private fun readActionable(node: PhoneNode, app: String, path: List<Int>, depth: Int, into: MutableList<PhoneElement>) {
        if (depth > MAX_DEPTH || !node.visible || node.bounds.empty) return
        if (abilities(node).isNotEmpty() || node.editable) return read(node, app, path, depth, into)
        node.children.forEachIndexed { index, child -> readActionable(child, app, path + index, depth + 1, into) }
    }

    /** The words of [nodes] that do nothing themselves, in order, stopping at any that act. */
    private fun said(nodes: List<PhoneNode>, depth: Int): List<String> = nodes.flatMap { node ->
        when {
            depth > MAX_DEPTH || !node.visible || node.bounds.empty -> emptyList()
            abilities(node).isNotEmpty() || node.editable -> emptyList()
            else -> listOfNotNull(own(node)) + said(node.children, depth + 1)
        }
    }

    private fun element(node: PhoneNode, words: List<String>, abilities: Set<PhoneAbility>, app: String, path: List<Int>): PhoneElement {
        val label = when {
            node.editable -> listOfNotNull(clean(node.description) ?: clean(node.hint) ?: idName(node.viewId)).joinToString()
            words.isNotEmpty() -> words.joinToString(" · ")
            abilities.isNotEmpty() -> idName(node.viewId).orEmpty()
            else -> ""
        }
        return PhoneElement(
            number = 0,
            role = role(node),
            label = shortened(label),
            states = states(node),
            abilities = abilities,
            bounds = node.bounds,
            app = app,
            path = path,
        )
    }

    /** What a view says itself: its text, else its description; a password's text never. */
    private fun own(node: PhoneNode): String? =
        (if (node.password || node.editable) null else clean(node.text)) ?: clean(node.description)

    private fun abilities(node: PhoneNode): Set<PhoneAbility> {
        if (!node.enabled) return emptySet()
        return buildSet {
            if (node.clickable || node.checkable) add(PhoneAbility.Tap)
            if (node.longClickable) add(PhoneAbility.Hold)
            if (node.editable) add(PhoneAbility.Type)
            if (node.scrollable) add(PhoneAbility.Scroll)
        }
    }

    private fun states(node: PhoneNode): List<String> = buildList {
        if (node.editable) {
            when {
                node.password -> add("password")
                !clean(node.text).isNullOrEmpty() && clean(node.text) != clean(node.hint) -> add("\"${shortened(clean(node.text)!!)}\"")
                else -> add("empty")
            }
        }
        if (node.checkable) add(if (role(node) == "switch") (if (node.checked) "on" else "off") else (if (node.checked) "checked" else "unchecked"))
        if (node.selected) add("selected")
        if (!node.enabled) add("disabled")
        if (node.focused) add("focused")
    }

    /** The element's kind, from its class as Android and Compose name it. */
    fun role(node: PhoneNode): String {
        val name = node.className.substringAfterLast('.')
        return when {
            node.editable || name.contains("EditText") -> "field"
            name.contains("Switch") || name.contains("ToggleButton") -> "switch"
            name.contains("CheckBox") || name.contains("CheckedTextView") -> "checkbox"
            name.contains("RadioButton") -> "radio"
            name.contains("SeekBar") || name.contains("Slider") || name.contains("RatingBar") -> "slider"
            name.contains("ProgressBar") -> "progress"
            name.contains("Button") -> "button"
            name.contains("Image") -> "image"
            name.contains("WebView") -> "web page"
            name.contains("Tab") -> "tab"
            node.scrollable || LISTS.any { name.contains(it) } -> "list"
            name.contains("TextView") || name == "Text" -> "text"
            node.clickable -> "item"
            else -> "text"
        }
    }

    /** `com.whatsapp:id/send` → "send": a label of last resort, for a button that says nothing else. */
    private fun idName(viewId: String?): String? = viewId?.substringAfterLast('/')?.replace('_', ' ')?.takeIf { it.isNotBlank() }

    private fun clean(text: String?): String? = text?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() }

    private fun shortened(text: String): String = if (text.length <= MAX_LABEL) text else text.take(MAX_LABEL - 1).trimEnd() + "…"

    private val WHITESPACE = Regex("\\s+")
    private val LISTS = listOf("RecyclerView", "ListView", "ScrollView", "GridView", "ViewPager", "Pager")
}
