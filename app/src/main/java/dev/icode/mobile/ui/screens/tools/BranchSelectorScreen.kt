package dev.icode.mobile.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
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
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTextField
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Branch picker sheet over the source-control surface. */
@Composable
fun BranchSelectorScreen(actions: DemoActions) {
    var query by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(IcodeColors.BackgroundDeep)) {
        Column(Modifier.padding(IcodeSpacing.Space4)) {
            Text("Source Control", style = IcodeType.Title.copy(color = IcodeColors.TextMuted))
            MonoLine(DemoContent.activeBranch, modifier = Modifier.padding(top = IcodeSpacing.Space3), color = IcodeColors.TextMuted)
        }
        IcodeBottomSheet(
            title = "Switch Branch",
            onClose = actions.back,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            IcodeTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search branches...",
                leadingIcon = Icons.Outlined.Search,
            )
            DemoContent.branches.forEach { branch ->
                IcodeListRow(
                    title = branch,
                    selected = branch == DemoContent.activeBranch,
                    onClick = actions.back,
                    titleStyle = IcodeType.Mono,
                    titleColor = if (branch == DemoContent.activeBranch) IcodeColors.Accent else IcodeColors.TextPrimary,
                    trailing = {
                        if (branch == DemoContent.activeBranch) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
                        }
                    },
                )
            }
            Text(
                "⊕  Create New Branch",
                modifier = Modifier.padding(vertical = IcodeSpacing.Space3),
                style = IcodeType.Body.copy(color = IcodeColors.Accent, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
