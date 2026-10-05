package dev.icode.mobile.capture

import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Draws [CaptureFixtures]; nothing a screen asks for changes them, so every capture is the same. */
class CaptureGateway : ICodeGateway {
    override val snapshot: StateFlow<ICodeSnapshot> = MutableStateFlow(CaptureFixtures.snapshot())

    override fun dispatch(intent: ICodeIntent) = Unit
}
