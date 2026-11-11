package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.designsystem.CHECKING
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeFact
import dev.icode.mobile.designsystem.IcodeFactGrid
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeSpinner
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.behindSheet
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
    AboutLink.Licenses to links?.licenses,
    AboutLink.Source to links?.source,
)

private fun resourceLinks(links: AppLinks?) = listOf(
    AboutLink.Documentation to links?.documentation,
    AboutLink.ReleaseNotes to links?.releaseNotes,
)

private fun updateSubtitle(check: UpdateCheck): String = when (check) {
    UpdateCheck.Idle -> "Tap to check"
    UpdateCheck.Checking -> CHECKING
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
    // Four short facts, each its label over its value (docs/screens-polish-plan.md D4).
    val build = listOf(
        IcodeFact("Version", BuildConfig.VERSION_NAME),
        IcodeFact("Android", "API ${BuildConfig.MIN_SDK} and later"),
        IcodeFact(
            "Runtime",
            when {
                !runtime.inspected -> CHECKING
                runtime.releaseName == null -> "Not installed"
                else -> listOfNotNull(runtime.releaseName, runtime.architecture.takeIf { it.isNotEmpty() }).joinToString(" · ")
            },
        ),
        IcodeFact("Editor", "Sora Editor ${BuildConfig.EDITOR_ENGINE_VERSION}"),
    )
    var reporting by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().behindSheet(reporting)) {
            IcodeTopBar("About iCode", subtitle = settingsSubtitle(Route.About), onBack = actions.back)
            IcodePageColumn {
                AppIdentityHeader(Modifier.padding(vertical = IcodeSpacing.Space4 + IcodeSpacing.Micro))
                IcodeSection("Build") {
                    IcodeCard(Modifier.fillMaxWidth()) { IcodeFactGrid(build) }
                }
                IcodeSection("Diagnostics") {
                    val update = web.update
                    SettingRow(
                        "Check for Updates",
                        Modifier.fillMaxWidth(),
                        subtitle = updateSubtitle(update),
                        leadingIcon = Icons.Outlined.SystemUpdate,
                        onClick = when (update) {
                            is UpdateCheck.Available -> ({ uri.openUri(update.downloadUrl) })
                            UpdateCheck.Checking -> null
                            else -> ({ gateway.dispatch(WebIntent.CheckForUpdates) })
                        },
                        trailing = { if (update == UpdateCheck.Checking) IcodeSpinner() },
                    )
                    diagnosticLinks(web.links).forEach { (link, url) -> LinkRow(link, url) { uri.openUri(it) } }
                }
                IcodeSection("Resources") {
                    SettingRow(
                        "Report a Bug",
                        Modifier.fillMaxWidth(),
                        subtitle = if (web.report.draft.title.isNotBlank()) "Draft saved — tap to finish" else "Screenshots welcome; device info only if you choose",
                        leadingIcon = Icons.Outlined.BugReport,
                        onClick = { reporting = true },
                    )
                    resourceLinks(web.links).forEach { (link, url) -> LinkRow(link, url) { uri.openUri(it) } }
                    if (web.supportText.isNotBlank()) {
                        Text(web.supportText, modifier = Modifier.padding(top = IcodeSpacing.Space2), style = IcodeType.Caption)
                    }
                }
                IcodeSection("Privacy") {
                    SettingRow(
                        "Share Device Info",
                        Modifier.fillMaxWidth(),
                        subtitle = "Anonymous: app and Android version, phone model. Never your files.",
                        leadingIcon = Icons.Outlined.PhoneAndroid,
                        onClick = { gateway.dispatch(WebIntent.SetDeviceInfoConsent(web.consent != DeviceInfoConsent.Shared)) },
                        trailing = {
                            IcodeToggle(checked = web.consent == DeviceInfoConsent.Shared) { shared ->
                                gateway.dispatch(WebIntent.SetDeviceInfoConsent(shared))
                            }
                        },
                    )
                    web.links?.privacy?.let { LinkRow(AboutLink.Privacy, it) { url -> uri.openUri(url) } }
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

/** The links About carries, each by its title and its icon. */
private enum class AboutLink(val title: String, val icon: ImageVector) {
    Licenses("View Licenses", Icons.Outlined.Gavel),
    Source("View Source", Icons.Outlined.Code),
    Documentation("Documentation", Icons.AutoMirrored.Outlined.MenuBook),
    ReleaseNotes("Release Notes", Icons.Outlined.NewReleases),
    Privacy("Privacy Policy", Icons.Outlined.PrivacyTip),
}

/**
 * A row the website's admin can point somewhere, as Settings' rows are; until it does, it says so
 * instead of answering a tap with nothing.
 */
@Composable
private fun LinkRow(link: AboutLink, url: String?, open: (String) -> Unit) {
    SettingRow(
        link.title,
        Modifier.fillMaxWidth(),
        subtitle = if (url == null) NOT_AVAILABLE else url.removePrefix("https://"),
        leadingIcon = link.icon,
        leadingTint = if (url == null) IcodeColors.TextSecondary else IcodeColors.Accent,
        titleColor = if (url == null) IcodeColors.TextSecondary else IcodeColors.TextPrimary,
        onClick = url?.let { { open(it) } },
        trailing = {
            if (url != null) Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        },
    )
}
