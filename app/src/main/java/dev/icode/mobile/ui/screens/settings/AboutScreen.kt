package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import dev.icode.mobile.designsystem.DetailRow
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeListRow
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.domain.AppLinks
import dev.icode.mobile.domain.DeviceInfoConsent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.UpdateCheck
import dev.icode.mobile.domain.WebIntent
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalICodeGateway
import dev.icode.mobile.ui.LocalICodeSnapshot

private const val NOT_AVAILABLE = "Not available yet"

/** A row's title and the link the website's admin set for it (icode-web › App config). */
private fun diagnosticLinks(links: AppLinks?) = listOf(
    "View Licenses" to links?.licenses,
    "View Source" to links?.source,
)

private fun resourceLinks(links: AppLinks?) = listOf(
    "Documentation" to links?.documentation,
    "Release Notes" to links?.releaseNotes,
)

private fun updateSubtitle(check: UpdateCheck): String = when (check) {
    UpdateCheck.Idle -> "Tap to check"
    UpdateCheck.Checking -> "Checking…"
    UpdateCheck.UpToDate -> "You're on the latest version"
    is UpdateCheck.Available -> if (check.required) "Update required: ${check.versionName} — tap to download" else "${check.versionName} available — tap to download"
    is UpdateCheck.Failed -> check.reason
}

/** About iCode: what this build and this phone's runtime actually are. */
@Composable
fun AboutScreen(actions: NavigationActions) {
    val state = LocalICodeSnapshot.current
    val gateway = LocalICodeGateway.current
    val uri = LocalUriHandler.current
    val web = state.web
    LaunchedEffect(Unit) {
        gateway.dispatch(ICodeIntent.RefreshRuntimeDetails)
        gateway.dispatch(WebIntent.RefreshAppConfig)
    }
    val runtime = state.runtimeDetails
    val buildSpecs = listOf(
        "App Version" to BuildConfig.VERSION_NAME,
        "Runtime Version" to when {
            !runtime.inspected -> "Checking…"
            runtime.release == null -> "Not installed"
            else -> listOfNotNull(runtime.release, runtime.architecture.takeIf { it.isNotEmpty() }).joinToString(" · ")
        },
        "Editor Engine" to "Sora Editor ${BuildConfig.EDITOR_ENGINE_VERSION}",
        "Min Android API" to "API ${BuildConfig.MIN_SDK}",
    )
    var reporting by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(reporting)) {
            IcodeTopBar("About iCode", subtitle = settingsSubtitle(Route.About), onBack = actions.back)
            IcodePageColumn {
                AppIdentityHeader(Modifier.padding(vertical = IcodeSpacing.Space4 + IcodeSpacing.Micro))
                IcodeSection("Build Specifications") {
                    IcodeCard(Modifier.fillMaxWidth()) {
                        buildSpecs.forEach { (label, value) -> DetailRow(label, value) }
                    }
                }
                IcodeSection("Diagnostics") {
                    val update = web.update
                    IcodeListRow(
                        card = true,
                        title = "Check for Updates",
                        subtitle = updateSubtitle(update),
                        subtitleStyle = IcodeType.Caption,
                        onClick = when (update) {
                            is UpdateCheck.Available -> ({ uri.openUri(update.downloadUrl) })
                            UpdateCheck.Checking -> null
                            else -> ({ gateway.dispatch(WebIntent.CheckForUpdates) })
                        },
                    )
                    IcodeListRow(
                        card = true,
                        title = "Export Diagnostics",
                        subtitle = NOT_AVAILABLE,
                        titleColor = IcodeColors.TextSecondary,
                        subtitleStyle = IcodeType.Caption,
                    )
                    diagnosticLinks(web.links).forEach { (title, url) -> LinkRow(title, url) { uri.openUri(it) } }
                }
                IcodeSection("Resources") {
                    IcodeListRow(
                        card = true,
                        title = "Report a Bug",
                        subtitle = if (web.report.draft.title.isNotBlank()) "Draft saved — tap to finish" else "Screenshots welcome; device info only if you choose",
                        subtitleStyle = IcodeType.Caption,
                        onClick = { reporting = true },
                    )
                    resourceLinks(web.links).forEach { (title, url) -> LinkRow(title, url, titleStyle = IcodeType.Body) { uri.openUri(it) } }
                    if (web.supportText.isNotBlank()) {
                        Text(web.supportText, modifier = Modifier.padding(top = IcodeSpacing.Space2), style = IcodeType.Caption)
                    }
                }
                IcodeSection("Privacy") {
                    IcodeListRow(
                        card = true,
                        title = "Share Device Info",
                        subtitle = "Anonymous: app and Android version, phone model. Never your files.",
                        subtitleStyle = IcodeType.Caption,
                        onClick = { gateway.dispatch(WebIntent.SetDeviceInfoConsent(web.consent != DeviceInfoConsent.Shared)) },
                        trailing = {
                            IcodeToggle(checked = web.consent == DeviceInfoConsent.Shared) { shared ->
                                gateway.dispatch(WebIntent.SetDeviceInfoConsent(shared))
                            }
                        },
                    )
                    web.links?.privacy?.let { LinkRow("Privacy Policy", it) { url -> uri.openUri(url) } }
                }
            }
        }
        if (reporting) {
            IcodeSheetOverlay(onClose = { reporting = false }) {
                ReportSheet(web.report, Modifier.align(Alignment.BottomCenter)) { reporting = false }
            }
        }
    }
}

/** A row the website's admin can point somewhere; until it does, it says so instead of answering a tap with nothing. */
@Composable
private fun LinkRow(title: String, url: String?, titleStyle: TextStyle = IcodeType.Body, open: (String) -> Unit) {
    IcodeListRow(
        card = true,
        title = title,
        subtitle = if (url == null) NOT_AVAILABLE else url.removePrefix("https://"),
        titleStyle = titleStyle,
        titleColor = if (url == null) IcodeColors.TextSecondary else IcodeColors.TextPrimary,
        subtitleStyle = IcodeType.Caption,
        onClick = url?.let { { open(it) } },
    )
}
