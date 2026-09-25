package dev.icode.mobile.domain

/**
 * "1 file", "3 files": a count with its noun in the right number, the one way
 * every screen says how many. [many] is for nouns that do not just add an s.
 */
fun countOf(count: Int, one: String, many: String = one + "s"): String =
    "$count ${if (count == 1) one else many}"
