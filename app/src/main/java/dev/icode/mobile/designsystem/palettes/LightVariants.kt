package dev.icode.mobile.designsystem.palettes

import androidx.compose.ui.graphics.Color

/**
 * The Light variants: antigem's light themes (Santoshkurmi/antigem 2a04d61) and four classics
 * from their projects' own palettes, each role as its Dark sibling takes it.
 * Seeds as listed in docs/theme-variants-plan.md §1.2 and D6; sources in THIRD_PARTY_NOTICES.md.
 */
internal val LightSeeds: List<ThemeSeed> = listOf(
    ThemeSeed(
        "catppuccin_latte", "Catppuccin Latte", classic = true,
        page = Color(0xFFE6E9EF), card = Color(0xFFEFF1F5), raised = Color(0xFFCCD0DA), editor = null,
        text = Color(0xFF4C4F69), secondary = Color(0xFF6C6F85), accent = Color(0xFF8839EF),
        green = Color(0xFF40A02B), amber = Color(0xFFDF8E1D), error = Color(0xFFD20F39), blue = Color(0xFF04A5E5),
        syntax = syntax(0xFF8839EF, 0xFFDF8E1D, 0xFF40A02B, 0xFFFE640B, 0xFF8C8FA1, 0xFF1E66F5),
        ansi = ansi(
            0x5C5F77, 0xD20F39, 0x40A02B, 0xDF8E1D, 0x1E66F5, 0xEA76CB, 0x179299, 0xACB0BE,
            0x6C6F85, 0xDE293E, 0x49AF3D, 0xEEA02D, 0x456EFF, 0xFE85D8, 0x2D9FA8, 0xBCC0CC,
        ),
    ),
    ThemeSeed(
        "gruvbox_light", "Gruvbox Light", classic = true,
        page = Color(0xFFF9F5D7), card = Color(0xFFFBF1C7), raised = Color(0xFFF2E5BC), editor = null,
        text = Color(0xFF3C3836), secondary = Color(0xFF7C6F64), accent = Color(0xFFAF3A03),
        green = Color(0xFF79740E), amber = Color(0xFFB57614), error = Color(0xFF9D0006), blue = Color(0xFF076678),
        syntax = syntax(0xFF9D0006, 0xFFB57614, 0xFF79740E, 0xFF8F3F71, 0xFF928374, 0xFF427B58),
        ansi = ansi(
            0xFDF4C1, 0xCC241D, 0x98971A, 0xD79921, 0x458588, 0xB16286, 0x689D6A, 0x7C6F64,
            0x928374, 0x9D0006, 0x79740E, 0xB57614, 0x076678, 0x8F3F71, 0x427B58, 0x3C3836,
        ),
    ),
    ThemeSeed(
        "lavender", "Lavender", classic = false,
        page = Color(0xFFF8F6FD), card = Color(0xFFFFFFFF), raised = Color(0xFFECE7F8), editor = null,
        text = Color(0xFF241B3A), secondary = Color(0xFF655B7F), accent = Color(0xFF7C3AED),
        green = Color(0xFF15803D), amber = Color(0xFFB45309), error = Color(0xFFDC2626), blue = Color(0xFF2563EB),
        syntax = syntax(0xFFD8B4FE, 0xFFFDE68A, 0xFFA7F3D0, 0xFFFDBA74, 0xFF7E7396, 0xFF93C5FD),
    ),
    ThemeSeed(
        "linen", "Linen", classic = false,
        page = Color(0xFFFBF8F3), card = Color(0xFFFFFFFF), raised = Color(0xFFF0EBE3), editor = null,
        text = Color(0xFF26211C), secondary = Color(0xFF6F6861), accent = Color(0xFFC2410C),
        green = Color(0xFF15803D), amber = Color(0xFFB45309), error = Color(0xFFDC2626), blue = Color(0xFF1D4ED8),
        syntax = syntax(0xFFE4A7F0, 0xFFF2CC8F, 0xFFB7D99A, 0xFFF5A97F, 0xFF8A8078, 0xFF8FC2F0),
    ),
    ThemeSeed(
        "mint", "Mint", classic = false,
        page = Color(0xFFF4FBF8), card = Color(0xFFFFFFFF), raised = Color(0xFFDDF2EA), editor = null,
        text = Color(0xFF10231B), secondary = Color(0xFF4B6858), accent = Color(0xFF059669),
        green = Color(0xFF15803D), amber = Color(0xFFB45309), error = Color(0xFFDC2626), blue = Color(0xFF0369A1),
        syntax = syntax(0xFFC4B5FD, 0xFFFDE68A, 0xFFA7F3D0, 0xFFFDBA74, 0xFF6B8A7A, 0xFF7DD3FC),
    ),
    ThemeSeed(
        "paper", "Paper", classic = false,
        page = Color(0xFFF7F1E3), card = Color(0xFFFFFAEF), raised = Color(0xFFECE3CE), editor = null,
        text = Color(0xFF2B2418), secondary = Color(0xFF6E6250), accent = Color(0xFF9A3412),
        green = Color(0xFF4D7C0F), amber = Color(0xFFB45309), error = Color(0xFFB91C1C), blue = Color(0xFF1D4ED8),
        syntax = syntax(0xFFF0A6CA, 0xFFF2D58A, 0xFFB5D49A, 0xFFF4B183, 0xFF8C7F67, 0xFF9CC3E6),
    ),
    ThemeSeed(
        "porcelain", "Porcelain", classic = false,
        page = Color(0xFFF9F9FB), card = Color(0xFFFFFFFF), raised = Color(0xFFE8EAED), editor = null,
        text = Color(0xFF1C1917), secondary = Color(0xFF78716C), accent = Color(0xFFD97706),
        green = Color(0xFF16A34A), amber = Color(0xFFD97706), error = Color(0xFFDC2626), blue = Color(0xFF3B82F6),
        syntax = syntax(0xFFC678DD, 0xFFE5C07B, 0xFF98C379, 0xFFD19A66, 0xFF7F848E, 0xFF61AFEF),
    ),
    ThemeSeed(
        "rose_pine_dawn", "Rosé Pine Dawn", classic = true,
        page = Color(0xFFFAF4ED), card = Color(0xFFFFFAF3), raised = Color(0xFFF2E9E1), editor = null,
        text = Color(0xFF575279), secondary = Color(0xFF797593), accent = Color(0xFFD7827E),
        green = Color(0xFF95D5A7), amber = Color(0xFFEA9D34), error = Color(0xFFB4637A), blue = Color(0xFF56949F),
        syntax = syntax(0xFF907AA9, 0xFF56949F, 0xFFEA9D34, 0xFFD7827E, 0xFF9893A5, 0xFF286983),
        ansi = ansi(
            0xF2E9E1, 0xB4637A, 0x286983, 0xEA9D34, 0x56949F, 0x907AA9, 0xD7827E, 0x575279,
            0x9893A5, 0xB4637A, 0x286983, 0xEA9D34, 0x56949F, 0x907AA9, 0xD7827E, 0x575279,
        ),
    ),
    ThemeSeed(
        "sakura", "Sakura", classic = false,
        page = Color(0xFFFFF7F9), card = Color(0xFFFFFFFF), raised = Color(0xFFFBE4EA), editor = null,
        text = Color(0xFF3B1F2B), secondary = Color(0xFF7F5A68), accent = Color(0xFFDB2777),
        green = Color(0xFF15803D), amber = Color(0xFFB45309), error = Color(0xFFDC2626), blue = Color(0xFF2563EB),
        syntax = syntax(0xFFF472B6, 0xFFFCD34D, 0xFF86EFAC, 0xFFFDBA74, 0xFF9C7B88, 0xFF93C5FD),
    ),
    ThemeSeed(
        "sky", "Sky", classic = false,
        page = Color(0xFFF5F8FC), card = Color(0xFFFFFFFF), raised = Color(0xFFE3EBF5), editor = null,
        text = Color(0xFF0F1B2D), secondary = Color(0xFF52637A), accent = Color(0xFF2563EB),
        green = Color(0xFF16A34A), amber = Color(0xFFB45309), error = Color(0xFFDC2626), blue = Color(0xFF0284C7),
        syntax = syntax(0xFFC792EA, 0xFFFFCB6B, 0xFFA3E4A1, 0xFFF9A87A, 0xFF64748B, 0xFF82AAFF),
    ),
    ThemeSeed(
        "solarized_light", "Solarized Light", classic = true,
        page = Color(0xFFFDF6E3), card = Color(0xFFFFFBEF), raised = Color(0xFFEEE8D5), editor = null,
        text = Color(0xFF073642), secondary = Color(0xFF586E75), accent = Color(0xFF268BD2),
        green = Color(0xFF859900), amber = Color(0xFFB58900), error = Color(0xFFDC322F), blue = Color(0xFF268BD2),
        syntax = syntax(0xFF859900, 0xFFB58900, 0xFF2AA198, 0xFFD33682, 0xFF657B83, 0xFF268BD2),
        ansi = ansi(
            0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
            0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3,
        ),
    ),
    ThemeSeed(
        "tokyo_night_day", "Tokyo Night Day", classic = true,
        page = Color(0xFFE1E2E7), card = Color(0xFFD0D5E3), raised = Color(0xFFC4C8DA), editor = null,
        text = Color(0xFF3760BF), secondary = Color(0xFF6172B0), accent = Color(0xFF2E7DE9),
        green = Color(0xFF587539), amber = Color(0xFF8C6C3E), error = Color(0xFFF52A65), blue = Color(0xFF007197),
        syntax = syntax(0xFF9854F1, 0xFF188092, 0xFF587539, 0xFFB15C00, 0xFF848CB5, 0xFF2E7DE9),
        ansi = ansi(
            0xB4B5B9, 0xF52A65, 0x587539, 0x8C6C3E, 0x2E7DE9, 0x9854F1, 0x007197, 0x6172B0,
            0xA1A6C5, 0xF52A65, 0x587539, 0x8C6C3E, 0x2E7DE9, 0x7847BD, 0x007197, 0x3760BF,
        ),
    ),
)
