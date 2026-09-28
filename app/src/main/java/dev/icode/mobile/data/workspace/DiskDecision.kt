package dev.icode.mobile.data.workspace

/** What a refresh does with one open file, given what is on disk now (US-FILE-005, US-EDIT-009). */
internal enum class DiskDecision {
    /** Nothing to do. */
    Keep,

    /** No unsaved edits: take the disk version in place. */
    Reload,

    /** Unsaved edits over a changed file: ask which wins. */
    Ask,

    /** Deleted on disk: say so; the text stays open. */
    Gone,
}

/**
 * [readFingerprint] is the version the editor was loaded from; [diskFingerprint]
 * is the file now, null when it is gone.
 */
internal fun decideOnDisk(
    readFingerprint: String,
    diskFingerprint: String?,
    dirty: Boolean,
    recoveryPending: Boolean,
    alreadyAsked: Boolean,
): DiskDecision = when {
    diskFingerprint == null -> DiskDecision.Gone
    diskFingerprint == readFingerprint -> DiskDecision.Keep
    // Its draft-or-disk question comes first, and its answer reads the disk again.
    recoveryPending -> DiskDecision.Keep
    !dirty -> DiskDecision.Reload
    alreadyAsked -> DiskDecision.Keep
    else -> DiskDecision.Ask
}
