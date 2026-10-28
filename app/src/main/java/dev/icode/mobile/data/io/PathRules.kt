package dev.icode.mobile.data.io

/** This location is [path] itself or lies inside it. */
internal fun String.isAtOrUnder(path: String): Boolean = this == path || startsWith("$path/")

/** Where this location is after [from] became [to]; unchanged when it was not under [from]. */
internal fun String.relocated(from: String, to: String): String =
    if (isAtOrUnder(from)) to + removePrefix(from) else this
