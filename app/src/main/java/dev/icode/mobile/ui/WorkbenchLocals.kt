package dev.icode.mobile.ui

import androidx.compose.runtime.compositionLocalOf
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchSnapshot

val LocalWorkbenchSnapshot = compositionLocalOf<WorkbenchSnapshot> {
    error("WorkbenchSnapshot was not provided")
}

val LocalWorkbenchGateway = compositionLocalOf<WorkbenchGateway> {
    error("WorkbenchGateway was not provided")
}
