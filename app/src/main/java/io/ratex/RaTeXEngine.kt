/*
 * Adapted from RaTeX-CMP 0.1.15, library/src/androidMain/kotlin/io/ratex/RaTeXEngine.android.kt
 * (https://github.com/darriousliu/RaTeX-CMP). MIT License, Copyright (c) 2026 RaTeX-CMP Contributors;
 * the full notice is in app/src/main/resources/licenses/RaTeX.txt.
 */
package io.ratex

/**
 * RaTeX's native library (third_party/ratex): LaTeX laid out as KaTeX lays it out. The library binds
 * by JNI name to this class and its two native functions, so their package, names and signatures are
 * RaTeX-CMP's, unchanged. Blocking, for about a millisecond a formula (docs/markdown-rendering-plan.md §2.4).
 */
object RaTeXEngine {
    init {
        System.loadLibrary("ratex_ffi")
    }

    /** [latex]'s display list as JSON, in display or inline style, in opaque black; throws with RaTeX's message when it cannot. */
    fun layout(latex: String, display: Boolean): String =
        nativeParseAndLayout(latex, display, INK) ?: throw RaTeXException(nativeGetLastError() ?: "RaTeX gave no reason")

    @JvmStatic
    private external fun nativeParseAndLayout(latex: String, displayMode: Boolean, color: FloatArray): String?

    @JvmStatic
    private external fun nativeGetLastError(): String?

    /** Opaque black, `MathLayout.INK`: what is drawn in it takes the text's colour. */
    private val INK = floatArrayOf(0f, 0f, 0f, 1f)
}

class RaTeXException(message: String) : Exception(message)
