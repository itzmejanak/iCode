package dev.icode.mobile.domain

/**
 * A byte count as people read it, in binary units: the largest unit that keeps
 * the number at one or more, with one decimal while it is below ten ("4.2 KB",
 * "12 KB") and always for gigabytes, where a tenth is still hundreds of
 * megabytes ("14.2 GB free of 16.0 GB").
 */
object ByteSize {
    fun format(bytes: Long): String = when {
        bytes >= GIB -> "%.1f GB".format(bytes.toDouble() / GIB)
        bytes >= MIB -> scaled(bytes, MIB, "MB")
        bytes >= KIB -> scaled(bytes, KIB, "KB")
        else -> "$bytes B"
    }

    private fun scaled(bytes: Long, unit: Long, name: String): String {
        val value = bytes.toDouble() / unit
        // 9.96 would round up to "10.0"; from there on it is a whole number.
        return if (value < 9.95) "%.1f %s".format(value, name) else "%.0f %s".format(value, name)
    }

    private const val KIB = 1024L
    private const val MIB = 1024L * KIB
    private const val GIB = 1024L * MIB
}
