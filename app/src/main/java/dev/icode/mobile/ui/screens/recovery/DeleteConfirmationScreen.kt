package dev.icode.mobile.ui.screens.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.DangerButton
import dev.icode.mobile.designsystem.DialogScaffold
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IconBadge
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.navigation.DemoActions
import dev.icode.mobile.navigation.DemoScreen

/** Destructive delete confirmation dialog. */
@Composable
fun DeleteConfirmationScreen(actions: DemoActions) {
    DialogScaffold(title = "auth.ts", subtitle = "Conflict workspace", onBack = actions.back) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4),
        ) {
            IconBadge(
                icon = Icons.Outlined.WarningAmber,
                tint = IcodeColors.Error,
                size = IcodeSizes.MarkMd,
                background = Color.Transparent,
                iconSize = IcodeSizes.MarkIconLg,
                shape = IcodeShapes.Pill,
            )
            Text("Delete Folder", style = IcodeType.PageTitle.copy(color = IcodeColors.Error))
            Text(
                "Are you sure you want to delete the directory below?",
                style = IcodeType.Body,
                textAlign = TextAlign.Center,
            )
            CodeSurface(
                modifier = Modifier.fillMaxWidth().clip(IcodeShapes.Small),
                padding = PaddingValues(IcodeSpacing.Space3),
            ) {
                MonoLine("src/components/auth", color = IcodeColors.Accent)
            }
            Text(
                "This folder contains 8 files. This action is destructive and cannot be undone.",
                style = IcodeType.Body,
                color = IcodeColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            DangerButton(
                "DELETE DESTRUCTIVELY",
                { actions.navigate(DemoScreen.WorkspaceExplorer) },
                Modifier.fillMaxWidth(),
                filled = true,
            )
            SecondaryButton("Cancel", actions.back, Modifier.fillMaxWidth())
        }
    }
}
