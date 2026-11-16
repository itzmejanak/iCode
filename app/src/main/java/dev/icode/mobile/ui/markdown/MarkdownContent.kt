package dev.icode.mobile.ui.markdown

import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LocalCodeTextScale
import dev.icode.mobile.designsystem.PinchToScaleText
import dev.icode.mobile.designsystem.scaledBy
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.markdown.MarkdownDocument
import dev.icode.mobile.domain.markdown.MarkdownParser
import dev.icode.mobile.domain.markdown.MarkdownPlace
import dev.icode.mobile.domain.markdown.MarkdownStream
import dev.icode.mobile.domain.markdown.MarkdownTarget
import dev.icode.mobile.ui.LocalICodeGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the blocks of a document on screen share (docs/editor-and-language-services.md): where its links
 * lead, whether its `https` images load, and where a tap goes. One per document on screen; the same
 * object while it streams, so a block that did not change is not drawn again.
 */
@Stable
internal class MarkdownContext(
    val place: MarkdownPlace?,
    /** A file's preview loads `https` images; an answer does not, as one could be steered to leak through them. */
    val remoteImages: Boolean,
    private val follow: (MarkdownTarget) -> Unit,
    /** Brings the block at an index into view; the footnotes are after the last block. */
    private val reveal: (Int) -> Unit,
) {
    /** The document as last drawn: where its anchors and footnotes are, read when tapped. */
    var document: MarkdownDocument = MarkdownDocument.Empty

    fun open(target: MarkdownTarget) {
        if (target is MarkdownTarget.Anchor) {
            document.anchors[target.slug]?.let(reveal)
        } else {
            follow(target)
        }
    }

    fun openFootnotes() = reveal(document.blocks.size)

    fun revealBlock(index: Int) = reveal(index)
}

/**
 * A Markdown file's preview: the editor's font size, pinched larger or smaller; its own scroll, its
 * blocks composed as they come into view. Read off the main thread, 150 ms after the text last changed.
 * Its links lead from [place] ([MarkdownPlace.ofFile]).
 */
@Composable
fun MarkdownPreview(text: String, place: MarkdownPlace?, fontSizeSp: Int, modifier: Modifier = Modifier) {
    val document by produceState(MarkdownDocument.Empty, text) {
        if (value !== MarkdownDocument.Empty) delay(PREVIEW_DEBOUNCE_MILLIS)
        value = withContext(Dispatchers.Default) { MarkdownParser.file(text) { ensureActive() }.also(::layOutMath) }
    }
    PinchToScaleText(modifier.fillMaxSize().background(IcodeColors.BackgroundDeep)) {
        val type = markdownType(IcodeType.Body.copy(fontSize = fontSizeSp.sp).scaledBy(LocalCodeTextScale.current))
        val list = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val context = rememberContext(place, remoteImages = true) { index -> scope.launch { list.animateScrollToItem(index) } }
        SideEffect { context.document = document }
        SelectionContainer {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = list,
                contentPadding = PaddingValues(horizontal = IcodeSpacing.Gutter, vertical = IcodeSpacing.Space3),
                verticalArrangement = Arrangement.spacedBy(type.gap),
            ) {
                itemsIndexed(document.blocks, contentType = { _, block -> block::class }) { _, block -> MarkdownBlockView(block, context, type) }
                if (document.footnotes.isNotEmpty()) item { MarkdownFootnotes(document.footnotes, context, type) }
            }
        }
    }
}

/**
 * An assistant's answer, at the chat's pinched size, selectable; its links lead from [place]
 * ([MarkdownPlace.ofAnswer]). While it streams ([streaming]) it is read off the main thread, only what
 * is new each time ([MarkdownStream]); once whole it is read once, and kept for when it is scrolled back to.
 */
@Composable
fun MarkdownAnswer(text: String, streaming: Boolean, place: MarkdownPlace?, modifier: Modifier = Modifier) {
    val document = if (streaming) streamedAnswer(text) else remember(text) { wholeAnswer(text) }
    val type = markdownType(IcodeType.Body.scaledBy(LocalCodeTextScale.current))
    val requesters = remember { mutableMapOf<Int, BringIntoViewRequester>() }
    val scope = rememberCoroutineScope()
    val context = rememberContext(place, remoteImages = false) { index -> scope.launch { requesters[index]?.bringIntoView() } }
    SideEffect { context.document = document }
    fun Modifier.revealable(index: Int) = bringIntoViewRequester(requesters.getOrPut(index) { BringIntoViewRequester() })
    SelectionContainer(modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(type.gap)) {
            document.blocks.forEachIndexed { index, block -> MarkdownBlockView(block, context, type, Modifier.revealable(index)) }
            if (document.footnotes.isNotEmpty()) MarkdownFootnotes(document.footnotes, context, type, Modifier.revealable(document.blocks.size))
        }
    }
}

@Composable
private fun rememberContext(place: MarkdownPlace?, remoteImages: Boolean, reveal: (Int) -> Unit): MarkdownContext {
    val gateway = LocalICodeGateway.current
    return remember(place, remoteImages) {
        MarkdownContext(
            place,
            remoteImages,
            follow = { target ->
                when (target) {
                    is MarkdownTarget.Web -> gateway.dispatch(ICodeIntent.OpenExternalUrl(target.url))
                    is MarkdownTarget.File -> gateway.dispatch(ICodeIntent.OpenFile(target.path, target.line))
                    is MarkdownTarget.Anchor -> Unit
                }
            },
            reveal = reveal,
        )
    }
}

/** An answer as it streams: one [MarkdownStream], fed on one thread at a time, the newest text first when several wait. */
@Composable
private fun streamedAnswer(text: String): MarkdownDocument {
    val stream = remember { MarkdownStream() }
    val latest by rememberUpdatedState(text)
    val document by produceState(MarkdownDocument.Empty) {
        snapshotFlow { latest }.conflate().collect { sofar ->
            value = withContext(Dispatchers.Default) { stream.update(sofar, complete = false).also(::layOutMath) }
        }
    }
    return document
}

/** A whole answer, read once: an answer scrolled back to is not read again. */
private fun wholeAnswer(text: String): MarkdownDocument = answers.get(text) ?: MarkdownStream().update(text, complete = true).also { answers.put(text, it) }

/** The answers read whole most recently. */
private val answers = LruCache<String, MarkdownDocument>(64)

/** A preview waits this long after the text last changed before reading it again. */
private const val PREVIEW_DEBOUNCE_MILLIS = 150L
