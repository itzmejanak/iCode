package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkbenchGateway
import dev.icode.mobile.ui.LocalWorkbenchSnapshot

// Destinations these rows will open once they exist: an update source, a
// diagnostics export, the licence notices, the project's source and pages.
// Until then each says so instead of answering a tap with nothing.
private val diagnosticActions = listOf("Check for Updates", "Export Diagnostics", "View Licenses", "View Source")

private val resources = listOf("Documentation", "Release Notes", "Report Issue")

private const val NOT_AVAILABLE = "Not available yet"

/** About iCode: what this build and this phone's runtime actually are. */
@Composable
fun AboutScreen(actions: NavigationActions) {
    val state = LocalWorkbenchSnapshot.current
    val gateway = LocalWorkbenchGateway.current
    LaunchedEffect(Unit) { gateway.dispatch(WorkbenchIntent.RefreshRuntimeDetails) }
    val runtime = state.runtimeDetails
    val buildSpecs = listOf(
        "App Version" to BuildConfig.VERSION_NAME,
        "Runtime Version" to (
            runtime.release?.let { release ->
                listOfNotNull(release, runtime.architecture.takeIf { it.isNotEmpty() }).joinToString(" · ")
            } ?: "Not installed"
        ),
        "Editor Engine" to "Sora Editor ${BuildConfig.EDITOR_ENGINE_VERSION}",
        "Min Android API" to "API ${BuildConfig.MIN_SDK}",
    )
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("About iCode", subtitle = "Application specs & diagnostic details", onBack = actions.back)
        LazyColumn(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space4 + IcodeSpacing.Micro),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IcodeBrandMark(Modifier.size(IcodeSizes.Tile))
                    Text(
                        "iCode Mobile IDE",
                        modifier = Modifier.padding(top = IcodeSpacing.Space2 + IcodeSpacing.Micro),
                        style = IcodeType.Heading,
                    )
                    Text("NATIVE ANDROID WORKBENCH", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
                }
            }
            item { SectionLabel("Build Specifications") }
            item {
                IcodeCard(Modifier.fillMaxWidth()) {
                    buildSpecs.forEach { (label, value) -> DetailRow(label, value) }
                }
            }
            item { SectionLabel("Diagnostics", Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro)) }
            items(diagnosticActions) { action ->
                IcodeListRow(
                    card = true,
                    title = action,
                    subtitle = NOT_AVAILABLE,
                    titleColor = IcodeColors.TextSecondary,
                    subtitleStyle = IcodeType.Caption,
                )
            }
            item { SectionLabel("Resources", Modifier.padding(top = IcodeSpacing.Space1 + IcodeSpacing.Micro)) }
            items(resources) { title ->
                IcodeListRow(
                    card = true,
                    title = title,
                    subtitle = NOT_AVAILABLE,
                    titleStyle = IcodeType.Body,
                    titleColor = IcodeColors.TextSecondary,
                    subtitleStyle = IcodeType.Caption,
                )
            }
        }
    }
}
