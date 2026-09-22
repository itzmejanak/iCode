package dev.icode.mobile.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icode.mobile.designsystem.Divider
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.SecondaryButton
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.navigation.DemoGroup
import dev.icode.mobile.navigation.DemoScreen

/**
 * Demo-only catalog of all Figma frames.
 *
 * Every screen title opens it through `LocalTitleAction`; Phase 1 verification
 * uses it to reach each of the 40 frames directly.
 */
@Composable
fun ScreenCatalog(
    current: DemoScreen,
    onDismiss: () -> Unit,
    onSelect: (DemoScreen) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SecondaryButton(text = "Close", onClick = onDismiss)
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                Text("Figma screen catalog", style = IcodeType.Title)
                Text("${DemoScreen.entries.size} implemented frames · current ${current.figmaNode}", style = IcodeType.Caption)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight(0.72f)
                    .widthIn(max = 520.dp),
            ) {
                DemoGroup.entries.forEach { group ->
                    item {
                        SectionLabel(group.label, Modifier.padding(top = IcodeSpacing.Space3, bottom = IcodeSpacing.Space1))
                    }
                    items(DemoScreen.entries.filter { it.group == group }) { screen ->
                        IcodeListRow(
                            title = screen.title,
                            selected = screen == current,
                            onClick = { onSelect(screen) },
                            modifier = Modifier.padding(horizontal = IcodeSpacing.Space2),
                            trailing = {
                                Text(screen.figmaNode, style = IcodeType.MonoSmall, color = IcodeColors.Accent)
                            },
                        )
                        Divider()
                    }
                }
            }
        },
        containerColor = IcodeColors.Surface,
        tonalElevation = 0.dp,
    )
}
