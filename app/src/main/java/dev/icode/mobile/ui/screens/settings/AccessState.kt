package dev.icode.mobile.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeType

/** What Android allows iCode, as a settings row says it: green when allowed, amber when not. */
@Composable
internal fun AccessState(label: String, allowed: Boolean) {
    Text(label, style = IcodeType.Label.copy(color = if (allowed) IcodeColors.Green else IcodeColors.Amber))
}
