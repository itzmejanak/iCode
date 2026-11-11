package dev.icode.mobile.domain

import dev.icode.mobile.domain.notices.NoticeTone

/** What a notice says, as tests compare it: its id and time differ every time. */
internal data class Said(val tone: NoticeTone, val title: String, val detail: String? = null)

/** What [this] says on the bell; null for any other effect. */
internal fun ICodeEffect.said(): Said? = (this as? ICodeEffect.Told)?.notice?.let { Said(it.tone, it.title, it.detail) }
