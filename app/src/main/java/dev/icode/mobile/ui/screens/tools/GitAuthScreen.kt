package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.demo.DemoContent
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledField
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.StatusDot
import dev.icode.mobile.navigation.DemoActions

/** Git credential method selection with SSH key details. */
@Composable
fun GitAuthScreen(actions: DemoActions) {
    var selected by rememberSaveable { mutableStateOf(DemoContent.authMethods.first()) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Git Credentials", onBack = actions.back)
        Column(
            modifier = Modifier.weight(1f).padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            SectionLabel("Authentication Method", Modifier.padding(bottom = IcodeSpacing.Micro))
            DemoContent.authMethods.forEach { method ->
                IcodeListRow(
                    card = true,
                    selected = selected == method,
                    onClick = { selected = method },
                    leading = {
                        StatusDot(
                            color = if (selected == method) IcodeColors.Accent else IcodeColors.Outline,
                            size = IcodeSizes.DotMd,
                        )
                    },
                    content = {
                        Column(Modifier.weight(1f)) {
                            Text(
                                method,
                                style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                            )
                            if (selected == method && method.startsWith("SSH")) {
                                Column(
                                    Modifier.padding(top = IcodeSpacing.Space3),
                                    verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
                                ) {
                                    Text("Key Path", style = IcodeType.Caption)
                                    MonoLine("~/.ssh/id_rsa_icode")
                                    Text("Key Status", style = IcodeType.Caption)
                                    MonoLine("Loaded, configured for github.com", color = IcodeColors.Green, style = IcodeType.MonoSmall)
                                    SecondaryButton("Regenerate SSH Key", {}, Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro))
                                }
                            }
                        }
                    },
                    trailing = {
                        if (selected == method) {
                            MonoLine("ACTIVE", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                        }
                    },
                )
            }
        }
        PrimaryButton("Test Git Connection", actions.back, Modifier.fillMaxWidth().padding(IcodeSpacing.Space5))
    }
}
