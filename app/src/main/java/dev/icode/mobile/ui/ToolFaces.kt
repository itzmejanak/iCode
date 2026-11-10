package dev.icode.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.domain.ToolBadge
import dev.icode.mobile.domain.WorkspaceTool

/** Each tool's icon, wherever the tool is reached: the dock and the Explorer's rail. */
internal val WorkspaceTool.icon: ImageVector
    get() = when (this) {
        WorkspaceTool.Explorer -> Icons.Outlined.FolderOpen
        WorkspaceTool.Editor -> Icons.Outlined.Description
        WorkspaceTool.Search -> Icons.Outlined.Search
        WorkspaceTool.SourceControl -> Icons.Outlined.AccountTree
        WorkspaceTool.Assistant -> Icons.Outlined.AutoAwesome
        WorkspaceTool.Terminal -> Icons.Outlined.Terminal
    }

/** The colour its [ToolBadge.tone] gives a tool's icon; null at rest. */
internal val ToolBadge.tint: Color?
    @Composable get() = tone?.tint

/** The dot its [ToolBadge.runs] puts on a tool's icon, as a running process's. */
internal val ToolBadge.dot: Color?
    @Composable get() = if (runs) IcodeColors.Green else null
