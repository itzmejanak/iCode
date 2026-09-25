package dev.icode.mobile.capture

import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Draws [CaptureFixtures]; nothing a screen asks for changes them, so every capture is the same. */
class CaptureGateway : WorkbenchGateway {
    override val snapshot: StateFlow<WorkbenchSnapshot> = MutableStateFlow(CaptureFixtures.snapshot())

    override fun dispatch(intent: WorkbenchIntent) = Unit
}
