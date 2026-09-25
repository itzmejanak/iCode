package dev.icode.mobile.domain

/** A byte count as people read it: binary units, the largest that keeps the number at or above one. */
object ByteSize {
    fun format(bytes: Long): String = when {
        bytes >= GIB -> "%.1f GB".format(bytes.toDouble() / GIB)
        bytes >= MIB -> "%.0f MB".format(bytes.toDouble() / MIB)
        bytes >= KIB -> "%.0f KB".format(bytes.toDouble() / KIB)
        else -> "$bytes B"
    }

    private const val KIB = 1024L
    private const val MIB = 1024L * KIB
    private const val GIB = 1024L * MIB
}
