package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.icode.mobile.designsystem.IcodeBrandMark
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import androidx.compose.runtime.remember

/** The developer's page; the credit names them by handle and opens this (owner's request, 2026-11-09). */
private const val DEVELOPER_URL = "https://github.com/itzmejanak"
private const val DEVELOPER_HANDLE = "itzmejanak"

/**
 * The app's identity, worded as the splash's: its own mark — each build's, per environment — its
 * name, what it is, and the person who builds it, below the mark and tappable.
 */
@Composable
internal fun AppIdentityHeader(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val accent = IcodeColors.Accent
    val credit = remember(accent) {
        buildAnnotatedString {
            append("built by ")
            withStyle(SpanStyle(color = accent)) { append(DEVELOPER_HANDLE) }
        }
    }
    // Side by side — the mark, then its three lines — and the pair centred on the page, as the splash has it.
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IcodeBrandMark(Modifier.size(IcodeSizes.MarkLg))
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
                Text("iCode", style = IcodeType.Heading)
                Text("MOBILE IDE", style = IcodeType.MonoSmall.copy(color = IcodeColors.Accent))
                Text(
                    credit,
                    modifier = Modifier
                        .padding(top = IcodeSpacing.Space1)
                        .clickable { uri.openUri(DEVELOPER_URL) }
                        .semantics { contentDescription = "Built by $DEVELOPER_HANDLE; opens their GitHub" },
                    style = IcodeType.Caption.copy(color = IcodeColors.TextMuted),
                )
            }
        }
    }
}
