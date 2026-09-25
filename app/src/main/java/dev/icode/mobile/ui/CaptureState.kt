package dev.icode.mobile.ui

import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkspacePreferencesRepository

/**
 * Fixed state drawn instead of the device's, so the capture tool's
 * screenshots are the same on every run. Only a debug build makes one
 * (`app/src/debug/.../capture`); a release build has no fixtures to draw.
 */
class CaptureState(
    val gateway: WorkbenchGateway,
    val preferences: WorkspacePreferencesRepository,
)
