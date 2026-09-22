package dev.icode.mobile.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.LabeledTextField
import dev.icode.mobile.designsystem.PrimaryButton
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Clone form; authentication opens the Git credentials frame. */
@Composable
fun CloneRepositoryScreen(actions: DemoActions) {
    var url by rememberSaveable { mutableStateOf("https://github.com/icode-dev/compiler-core.git") }
    var path by rememberSaveable { mutableStateOf("~/projects/compiler-core") }
    var branch by rememberSaveable { mutableStateOf("main") }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Clone Repository", onBack = actions.back)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(IcodeSpacing.Space5),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space5),
        ) {
            LabeledTextField(label = "Repository URL", value = url, onValueChange = { url = it })
            LabeledTextField(label = "Destination Directory", value = path, onValueChange = { path = it })
            LabeledTextField(label = "Checkout Branch (Optional)", value = branch, onValueChange = { branch = it })
            IcodeListRow(
                card = true,
                title = "AUTHENTICATION",
                titleStyle = IcodeType.Label,
                titleColor = IcodeColors.TextPrimary,
                onClick = { actions.navigate(DemoScreen.GitAuth) },
                leading = {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = IcodeColors.TextSecondary,
                        modifier = Modifier.size(IcodeSizes.IconMd),
                    )
                },
                trailing = {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                },
            )
        }
        PrimaryButton("Clone Repository", { actions.navigate(DemoScreen.CloneProgress) }, Modifier.fillMaxWidth().padding(IcodeSpacing.Space5))
    }
}
