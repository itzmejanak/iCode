package dev.icode.mobile.designsystem.palettes

import androidx.compose.ui.graphics.Color

/**
 * The AMOLED variants designed on pure black: antigem's Midnight and High Contrast
 * (Santoshkurmi/antigem 2a04d61). The rest of AMOLED is each Dark variant on black.
 * Seeds as listed in docs/theme-variants-plan.md §1.2 and D6; sources in THIRD_PARTY_NOTICES.md.
 */
internal val AmoledSeeds: List<ThemeSeed> = listOf(
    ThemeSeed(
        "high_contrast", "High Contrast", classic = false,
        page = Color(0xFF000000), card = Color(0xFF0D0D0D), raised = Color(0xFF1F1F1F), editor = Color(0xFF000000),
        text = Color(0xFFFFFFFF), secondary = Color(0xFFD4D4D4), accent = Color(0xFFFFD60A),
        green = Color(0xFF00E676), amber = Color(0xFFFFC400), error = Color(0xFFFF5252), blue = Color(0xFF40C4FF),
        syntax = syntax(0xFFFF6EFF, 0xFFFFD60A, 0xFF00E676, 0xFFFF9E40, 0xFFBDBDBD, 0xFF40C4FF),
    ),
    ThemeSeed(
        "midnight", "Midnight", classic = false,
        page = Color(0xFF000000), card = Color(0xFF0F1115), raised = Color(0xFF1A1D24), editor = Color(0xFF0A0C10),
        text = Color(0xFFECEFF4), secondary = Color(0xFF9AA3B2), accent = Color(0xFF22D3EE),
        green = Color(0xFF22C55E), amber = Color(0xFFF59E0B), error = Color(0xFFF43F5E), blue = Color(0xFF60A5FA),
        syntax = syntax(0xFFC792EA, 0xFFFFCB6B, 0xFFC3E88D, 0xFFF78C6C, 0xFF5C6370, 0xFF82AAFF),
    ),
)
