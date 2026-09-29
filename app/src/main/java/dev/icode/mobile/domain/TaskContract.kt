package dev.icode.mobile.domain

// Long operations (docs/long-operations-plan.md): one model for every operation a user starts
// and may send to the background — its dialog, its notification and its result.

/** A long operation the user started, while it runs, or after it failed until the user has seen why. */
data class WorkTask(
    val id: Long,
    /** "Deleting…" — what the dialog's title says it is doing. */
    val title: String,
    /** What it works on, by the name the user chose it by: "node_modules". */
    val subject: String,
    /** The item in hand, as a path from the folder [subject] is in; null while it counts. */
    val currentItem: String? = null,
    val done: Long = 0,
    /** Null while it counts. */
    val total: Long? = null,
    val cancellable: Boolean = false,
    /** Sent to the background: no dialog; the notification carries it. */
    val hidden: Boolean = false,
    /** Why it stopped short; null while it runs. */
    val failure: String? = null,
) {
    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
}
