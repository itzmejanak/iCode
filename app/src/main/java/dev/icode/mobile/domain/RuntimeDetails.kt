package dev.icode.mobile.domain

/**
 * What can actually be measured about the installed runtime.
 *
 * Every field is read from the device or the build. Anything that cannot be
 * measured is absent rather than estimated — there is no "uptime" here, for
 * instance, because nothing runs continuously to have one.
 */
data class RuntimeDetails(
    /** The base system recorded at install time, or null before one is installed. */
    val release: String? = null,
    /** The device's primary ABI, not the one the build hopes for. */
    val architecture: String = "",
    val rootfsPath: String = "",
    /** Counted from the guest package database; null while unknown. */
    val installedPackages: Int? = null,
    /** Measured size of the root filesystem; null while unknown. */
    val rootfsBytes: Long? = null,
    val availableBytes: Long = 0,
    val prootVersion: String? = null,
) {
    val isInstalled: Boolean get() = release != null
}
