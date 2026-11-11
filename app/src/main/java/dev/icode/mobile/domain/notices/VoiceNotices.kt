package dev.icode.mobile.domain.notices

/** What voice tells (docs/notices-redesign-plan.md D8). */
object VoiceNotices {
    /** The words of the voice that could not speak: why. */
    fun notReadAloud(words: String) = Notice(NoticeTone.Problem, NoticeSource.Voice, "Couldn't read the answer aloud", detail = words)

    /** A speech provider's form refused, in its words. */
    fun providerRefused(words: String?) = Notice(NoticeTone.HeadsUp, NoticeSource.Voice, words ?: "Couldn't save the speech provider")
}
