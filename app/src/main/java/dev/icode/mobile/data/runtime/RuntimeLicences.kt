package dev.icode.mobile.data.runtime

import android.content.Context

/**
 * The licences runtime tools accept (docs/runtime-and-terminal.md), each exactly as its owner
 * publishes it: `assets/runtime/licences/<id>.txt`. The License Agreement shows this text, and what
 * records its acceptance is derived from the same bytes, so the two cannot differ.
 */
internal object RuntimeLicences {
    fun text(context: Context, id: String): String =
        context.assets.open("$FOLDER/$id.txt").bufferedReader().use { it.readText() }

    const val FOLDER = "runtime/licences"
}
