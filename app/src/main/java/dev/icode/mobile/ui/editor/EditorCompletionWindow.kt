package dev.icode.mobile.ui.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.syntaxColor
import dev.icode.mobile.ui.syntax.PaletteColorScheme
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.widget.component.CompletionLayout
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorCompletionAdapter
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.max
import kotlin.math.min

/**
 * The completion list (docs/editor-and-language-services.md): Sora's own window — its request, its
 * threads, its keys and its choosing kept — drawn as iCode draws a menu, each row a kind's badge, the
 * label with its matched letters, its signature and its type; as tall as its rows, under the caret's
 * line or above it where there is more room there; the first row shown as chosen, as soft-keyboard
 * Enter takes it.
 */
internal class EditorCompletionWindow(private val view: IcodeCodeEditor) : EditorAutoCompletion(view) {
    private val rows = CompletionRows(this)

    init {
        setLayout(CompletionList(this))
        setAdapter(rows)
        // Its letters are this window's to colour, from the matcher's own positions.
        setHighlightMatchedLabel(false)
    }

    /** The row drawn as chosen: the one moved to, or the first. */
    val chosen: Int get() = if (currentSelection == -1) 0 else currentSelection

    /** The key row's Tab: the chosen row taken; false when no list shows, so Tab is a tab. */
    fun acceptChosen(): Boolean = isShowing && rows.count > 0 && select(chosen)

    /** The key row's ↑ and ↓: the choice moved; false when no list shows, so the caret moves. */
    fun moveChoice(up: Boolean): Boolean {
        if (!isShowing || rows.count == 0) return false
        if (currentSelection == -1) currentSelection = 0
        if (up) moveUp() else moveDown()
        rows.notifyDataSetChanged()
        return true
    }

    /** No rows, no window: Sora would show an empty one. */
    override fun show() {
        if (rows.count == 0) hide() else super.show()
    }

    /** Under the caret's line, or above it with more room there; as tall as its rows, at most [MAX_ROWS]; where the word being completed starts. */
    override fun updateCompletionWindowPosition(scrollEditor: Boolean) {
        val dp = view.dpUnit
        val cursor = view.cursor
        val rowHeight = view.rowHeight
        val bottom = view.layout.getCharLayoutOffset(cursor.rightLine, cursor.rightColumn)[0] - view.offsetY
        val top = bottom - rowHeight
        val gap = GAP_DP * dp
        val below = view.height - bottom - gap - MARGIN_DP * dp
        val above = top - gap - MARGIN_DP * dp
        val wanted = rows.itemHeight * min(rows.count, MAX_ROWS)
        val under = below >= wanted || below >= above
        val room = (if (under) below else above).toInt()
        val height = max(rows.itemHeight, min(wanted, room / rows.itemHeight * rows.itemHeight))
        val width = min(view.width - (2 * MARGIN_DP * dp).toInt(), (MAX_WIDTH_DP * dp).toInt())
        val line = view.text.getLine(cursor.rightLine)
        val typed = view.textPaint.measureText(EditorCompletionRules.prefixOf(line, cursor.rightColumn))
        // The labels start under the word being completed: its start, less the badge before them.
        val x = (view.updateCursorAnchor() - typed - (ROW_PADDING_DP + BADGE_DP + BADGE_GAP_DP) * dp)
            .coerceIn(MARGIN_DP * dp, max(MARGIN_DP * dp, view.width - width - MARGIN_DP * dp))
        val y = if (under) bottom + gap else top - gap - height
        setMaxHeight(height)
        setLocation(x.toInt() + view.offsetX, y.toInt() + view.offsetY)
        setSize(width, height)
    }

    /** The palette the editor is drawn in: the badges' colours. */
    val palette: IcodePalette? get() = (view.colorScheme as? PaletteColorScheme)?.palette

    val typeface: Typeface get() = view.typefaceText

    companion object {
        const val MAX_ROWS = 6
        const val ROW_DP = 36f
        const val ROW_PADDING_DP = 8f
        const val BADGE_DP = 20f
        const val BADGE_GAP_DP = 10f
        const val CORNER_DP = 8f
        private const val GAP_DP = 2f
        private const val MARGIN_DP = 8f
        private const val MAX_WIDTH_DP = 420f
    }
}

/** The window's box: the raised surface, its 1 dp edge and its corner, as iCode's menus; its rows full width. */
private class CompletionList(private val window: EditorCompletionWindow) : CompletionLayout {
    private lateinit var root: FrameLayout
    private lateinit var list: ListView

    override fun setEditorCompletion(completion: EditorAutoCompletion) = Unit

    override fun inflate(context: Context): View {
        list = ListView(context).apply {
            divider = null
            dividerHeight = 0
            selector = Color.TRANSPARENT.toDrawable()
            isVerticalScrollBarEnabled = true
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ -> window.select(position) }
        }
        root = FrameLayout(context).apply {
            clipToOutline = true
            addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return root
    }

    override fun onApplyColorScheme(colorScheme: EditorColorScheme) {
        val dp = root.resources.displayMetrics.density
        root.background = GradientDrawable().apply {
            cornerRadius = EditorCompletionWindow.CORNER_DP * dp
            setColor(colorScheme.getColor(EditorColorScheme.COMPLETION_WND_BACKGROUND))
            setStroke(dp.toInt().coerceAtLeast(1), colorScheme.getColor(EditorColorScheme.COMPLETION_WND_CORNER))
        }
    }

    override fun getCompletionList(): AdapterView<*> = list

    /** The list keeps no bar while the servers answer: the rows it has stay until the new ones come. */
    override fun setLoading(state: Boolean) = Unit

    override fun ensureListPositionVisible(position: Int, incrementPixels: Int) {
        val first = list.firstVisiblePosition
        val last = list.lastVisiblePosition
        when {
            position < first -> list.setSelection(position)
            position > last -> list.setSelectionFromTop(position, list.height - incrementPixels)
        }
    }
}

/** The rows: the kind's badge, the label — its matched letters in the accent, its signature after it — and the type at the end. */
private class CompletionRows(private val window: EditorCompletionWindow) : EditorCompletionAdapter() {
    override fun getItemHeight(): Int = (EditorCompletionWindow.ROW_DP * window.context.resources.displayMetrics.density).toInt()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup, isCurrentCursorPosition: Boolean): View {
        val row = convertView as? CompletionRow ?: CompletionRow(window.context).apply {
            layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight)
        }
        val item = getItem(position)
        val completion = item as? LanguageCompletion
        val colors = colorScheme
        val primary = colors.getColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY)
        val secondary = colors.getColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY)
        val palette = window.palette
        val kind = item.kind ?: CompletionItemKind.Text
        val tint = palette?.let { kindColor(kind, it) } ?: secondary
        row.badge.text = kind.name.first().lowercase()
        row.badge.setTextColor(tint)
        (row.badge.background as GradientDrawable).setColor(tint and 0x00FFFFFF or BADGE_ALPHA)
        row.label.typeface = window.typeface
        row.label.text = SpannableStringBuilder(item.label).apply {
            setSpan(ForegroundColorSpan(primary), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val matched = colors.getColor(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED)
            completion?.matched?.filter { it < length }?.forEach { setSpan(ForegroundColorSpan(matched), it, it + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            if (item.deprecated) setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            completion?.item?.labelDetail?.takeIf { it.isNotBlank() }?.let { signature ->
                val from = length
                append(signature)
                setSpan(ForegroundColorSpan(palette?.textMuted?.toArgb() ?: secondary), from, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val detail = item.desc?.toString()?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        row.detail.text = detail
        row.detail.visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
        row.detail.setTextColor(secondary)
        row.detail.typeface = window.typeface
        row.chosenColor = colors.getColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT)
        row.chosen = isCurrentCursorPosition || window.currentPosition == -1 && position == 0
        row.contentDescription = listOf(item.label, kind.name, detail).filter { it.isNotEmpty() }.joinToString(", ")
        return row
    }

    /** A kind in the colour the editor gives what it is: a call's, a type's, a member's, a constant's, a keyword's; a snippet in the accent. */
    private fun kindColor(kind: CompletionItemKind, palette: IcodePalette): Int = when (kind) {
        CompletionItemKind.Method, CompletionItemKind.Function, CompletionItemKind.Constructor -> palette.syntaxColor(SyntaxRole.Function)
        CompletionItemKind.Class, CompletionItemKind.Interface, CompletionItemKind.Struct, CompletionItemKind.Enum,
        CompletionItemKind.TypeParameter, CompletionItemKind.Module -> palette.syntaxColor(SyntaxRole.Type)
        CompletionItemKind.Field, CompletionItemKind.Property, CompletionItemKind.Event -> palette.syntaxColor(SyntaxRole.Attribute)
        CompletionItemKind.Constant, CompletionItemKind.EnumMember, CompletionItemKind.Value -> palette.syntaxColor(SyntaxRole.Constant)
        CompletionItemKind.Keyword, CompletionItemKind.Operator -> palette.syntaxColor(SyntaxRole.Keyword)
        CompletionItemKind.Snippet -> palette.accent
        else -> palette.textSecondary
    }.toArgb()

    private companion object {
        /** The badge's ground: its colour at a fifth. */
        const val BADGE_ALPHA = 0x33000000
    }
}

/** One row's views, made once and drawn again for each item the list scrolls to. */
private class CompletionRow(context: Context) : LinearLayout(context) {
    private val dp = context.resources.displayMetrics.density
    val badge = TextView(context)
    val label = TextView(context)
    val detail = TextView(context)
    var chosenColor = 0
    var chosen = false
        set(value) {
            field = value
            // Inset from the box's edges and from the rows beside it, as a menu's chosen row.
            val side = (CHOSEN_INSET_DP * dp).toInt()
            val gap = (CHOSEN_GAP_DP * dp).toInt()
            background = if (value) InsetDrawable(GradientDrawable().apply {
                cornerRadius = CHOSEN_CORNER_DP * dp
                setColor(chosenColor)
            }, side, gap, side, gap) else null
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padding = (EditorCompletionWindow.ROW_PADDING_DP * dp).toInt()
        setPadding(padding, 0, padding, 0)
        val side = (EditorCompletionWindow.BADGE_DP * dp).toInt()
        badge.apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BADGE_SP)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            includeFontPadding = false
            background = GradientDrawable().apply { cornerRadius = BADGE_CORNER_DP * dp }
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(badge, LayoutParams(side, side).apply { marginEnd = (EditorCompletionWindow.BADGE_GAP_DP * dp).toInt() })
        label.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_SP)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            paintFlags = paintFlags or Paint.SUBPIXEL_TEXT_FLAG
        }
        addView(label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        detail.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, DETAIL_SP)
            isSingleLine = true
            // A long type shows its end, where its name is.
            ellipsize = TextUtils.TruncateAt.START
            maxWidth = (DETAIL_MAX_DP * dp).toInt()
        }
        addView(detail, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = padding })
    }

    private companion object {
        const val LABEL_SP = 14f
        const val DETAIL_SP = 12f
        const val BADGE_SP = 11f
        const val BADGE_CORNER_DP = 5f
        const val CHOSEN_CORNER_DP = 6f
        const val CHOSEN_INSET_DP = 4f
        const val CHOSEN_GAP_DP = 2f
        const val DETAIL_MAX_DP = 150f
    }
}
