package dev.icode.mobile.designsystem.palettes

import androidx.compose.ui.graphics.Color

/**
 * The Dark variants: antigem's dark themes (Santoshkurmi/antigem 2a04d61), the classics under
 * their projects' names. Each is also AMOLED's, on true black (ThemeSeed.onBlack).
 * Seeds as listed in docs/design-and-navigation.md; sources in THIRD_PARTY_NOTICES.md.
 */
internal val DarkSeeds: List<ThemeSeed> = listOf(
    ThemeSeed(
        "amber_night", "Amber Night", classic = false,
        page = Color(0xFF181816), card = Color(0xFF232320), raised = Color(0xFF32312D), editor = Color(0xFF1E1E1C),
        text = Color(0xFFF5F5F4), secondary = Color(0xFFA8A29E), accent = Color(0xFFD97706),
        green = Color(0xFF16A34A), amber = Color(0xFFD97706), error = Color(0xFFDC2626), blue = Color(0xFF3B82F6),
        syntax = syntax(0xFFC678DD, 0xFFE5C07B, 0xFF98C379, 0xFFD19A66, 0xFF7F848E, 0xFF61AFEF),
    ),
    ThemeSeed(
        "aurora", "Aurora", classic = false,
        page = Color(0xFF0B1118), card = Color(0xFF121A23), raised = Color(0xFF1A2530), editor = Color(0xFF0A1016),
        text = Color(0xFFE3ECF2), secondary = Color(0xFF8DA2B3), accent = Color(0xFF2DD4BF),
        green = Color(0xFF4ADE80), amber = Color(0xFFFBBF24), error = Color(0xFFFB7185), blue = Color(0xFF67E8F9),
        syntax = syntax(0xFFA78BFA, 0xFFFDE68A, 0xFF86EFAC, 0xFFFDBA74, 0xFF5B6F80, 0xFF67E8F9),
    ),
    ThemeSeed(
        "catppuccin_mocha", "Catppuccin Mocha", classic = true,
        page = Color(0xFF1E1E2E), card = Color(0xFF252536), raised = Color(0xFF313244), editor = Color(0xFF181825),
        text = Color(0xFFCDD6F4), secondary = Color(0xFFA6ADC8), accent = Color(0xFFCBA6F7),
        green = Color(0xFFA6E3A1), amber = Color(0xFFF9E2AF), error = Color(0xFFF38BA8), blue = Color(0xFF89DCEB),
        syntax = syntax(0xFFCBA6F7, 0xFFF9E2AF, 0xFFA6E3A1, 0xFFFAB387, 0xFF7F849C, 0xFF89B4FA),
        ansi = ansi(
            0x45475A, 0xF38BA8, 0xA6E3A1, 0xF9E2AF, 0x89B4FA, 0xF5C2E7, 0x94E2D5, 0xA6ADC8,
            0x585B70, 0xF37799, 0x89D88B, 0xEBD391, 0x74A8FC, 0xF2AEDE, 0x6BD7CA, 0xBAC2DE,
        ),
    ),
    ThemeSeed(
        "deep_ocean", "Deep Ocean", classic = false,
        page = Color(0xFF0B1426), card = Color(0xFF111D33), raised = Color(0xFF1A2942), editor = Color(0xFF070E1C),
        text = Color(0xFFE2E8F0), secondary = Color(0xFF94A3B8), accent = Color(0xFF38BDF8),
        green = Color(0xFF34D399), amber = Color(0xFFFBBF24), error = Color(0xFFFB7185), blue = Color(0xFF38BDF8),
        syntax = syntax(0xFFC084FC, 0xFFFCD34D, 0xFF86EFAC, 0xFFFDBA74, 0xFF64748B, 0xFF7DD3FC),
    ),
    ThemeSeed(
        "dracula", "Dracula", classic = true,
        page = Color(0xFF282A36), card = Color(0xFF303241), raised = Color(0xFF44475A), editor = Color(0xFF21222C),
        text = Color(0xFFF8F8F2), secondary = Color(0xFFA9ADCB), accent = Color(0xFFBD93F9),
        green = Color(0xFF50FA7B), amber = Color(0xFFFFB86C), error = Color(0xFFFF5555), blue = Color(0xFF8BE9FD),
        syntax = syntax(0xFFFF79C6, 0xFF8BE9FD, 0xFFF1FA8C, 0xFFBD93F9, 0xFF7282B5, 0xFF50FA7B),
        ansi = ansi(
            0x21222C, 0xFF5555, 0x50FA7B, 0xF1FA8C, 0xBD93F9, 0xFF79C6, 0x8BE9FD, 0xF8F8F2,
            0x6272A4, 0xFF6E6E, 0x69FF94, 0xFFFFA5, 0xD6ACFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF,
        ),
    ),
    ThemeSeed(
        "forest", "Forest", classic = false,
        page = Color(0xFF0F1A14), card = Color(0xFF16241C), raised = Color(0xFF1E3127), editor = Color(0xFF0A120E),
        text = Color(0xFFE6F0E9), secondary = Color(0xFF9DB5A6), accent = Color(0xFF4ADE80),
        green = Color(0xFF4ADE80), amber = Color(0xFFFBBF24), error = Color(0xFFF87171), blue = Color(0xFF60A5FA),
        syntax = syntax(0xFFC4B5FD, 0xFFFDE68A, 0xFFA7F3D0, 0xFFFDBA74, 0xFF6A8777, 0xFF7DD3FC),
    ),
    ThemeSeed(
        "graphite", "Graphite", classic = false,
        page = Color(0xFF121212), card = Color(0xFF1B1B1C), raised = Color(0xFF262628), editor = Color(0xFF161616),
        text = Color(0xFFEDEBE8), secondary = Color(0xFFA29E98), accent = Color(0xFFF0A04B),
        green = Color(0xFF5BD18A), amber = Color(0xFFF4C152), error = Color(0xFFEF6B6B), blue = Color(0xFF6CB4F2),
        syntax = syntax(0xFFC678DD, 0xFFE5C07B, 0xFF98C379, 0xFFD19A66, 0xFF6B717D, 0xFF61AFEF),
    ),
    ThemeSeed(
        "gruvbox_dark", "Gruvbox Dark", classic = true,
        page = Color(0xFF1D2021), card = Color(0xFF282828), raised = Color(0xFF32302F), editor = Color(0xFF1A1C1D),
        text = Color(0xFFEBDBB2), secondary = Color(0xFFA89984), accent = Color(0xFFFE8019),
        green = Color(0xFFB8BB26), amber = Color(0xFFFABD2F), error = Color(0xFFFB4934), blue = Color(0xFF83A598),
        syntax = syntax(0xFFFB4934, 0xFFFABD2F, 0xFFB8BB26, 0xFFD3869B, 0xFF928374, 0xFF8EC07C),
        ansi = ansi(
            0x282828, 0xCC241D, 0x98971A, 0xD79921, 0x458588, 0xB16286, 0x689D6A, 0xA89984,
            0x928374, 0xFB4934, 0xB8BB26, 0xFABD2F, 0x83A598, 0xD3869B, 0x8EC07C, 0xEBDBB2,
        ),
    ),
    ThemeSeed(
        "midnight_aurora", "Midnight Aurora", classic = false,
        page = Color(0xFF0A0B14), card = Color(0xFF151826), raised = Color(0xFF1C2033), editor = Color(0xFF0C0E19),
        text = Color(0xFFE8EAF6), secondary = Color(0xFF9AA1BF), accent = Color(0xFFA78BFA),
        green = Color(0xFF4ADE80), amber = Color(0xFFFBBF24), error = Color(0xFFFB7185), blue = Color(0xFF60A5FA),
        syntax = syntax(0xFFC4A7FF, 0xFF5EEAD4, 0xFF9AE6B4, 0xFFFFB86B, 0xFF5B6285, 0xFF7DB5FF),
        ansi = ansi(
            0x2A2D3A, 0xFB7185, 0x4ADE80, 0xFBBF24, 0x60A5FA, 0xC084FC, 0x2DD4BF, 0xB3B8D0,
            0x5D6173, 0xFF97A3, 0x85E7A1, 0xFDD072, 0x89BCFD, 0xCFA4FF, 0x79DFCF, 0xD6DBF5,
        ),
    ),
    ThemeSeed(
        "nord", "Nord", classic = true,
        page = Color(0xFF2E3440), card = Color(0xFF3B4252), raised = Color(0xFF434C5E), editor = Color(0xFF272C36),
        text = Color(0xFFECEFF4), secondary = Color(0xFFA3ADC2), accent = Color(0xFF88C0D0),
        green = Color(0xFFA3BE8C), amber = Color(0xFFEBCB8B), error = Color(0xFFBF616A), blue = Color(0xFF5E81AC),
        syntax = syntax(0xFF81A1C1, 0xFF8FBCBB, 0xFFA3BE8C, 0xFFB48EAD, 0xFF6E7A94, 0xFF88C0D0),
        ansi = ansi(
            0x3B4252, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x88C0D0, 0xE5E9F0,
            0x4C566A, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x8FBCBB, 0xECEFF4,
        ),
    ),
    ThemeSeed(
        "obsidian", "Obsidian", classic = false,
        page = Color(0xFF0D0E12), card = Color(0xFF16181D), raised = Color(0xFF1F2229), editor = Color(0xFF111318),
        text = Color(0xFFECEDF0), secondary = Color(0xFF9297A3), accent = Color(0xFF8B9CFF),
        green = Color(0xFF4ADE80), amber = Color(0xFFF5B454), error = Color(0xFFF87171), blue = Color(0xFF60A5FA),
        syntax = syntax(0xFFC4A1FF, 0xFF7DD3FC, 0xFF9FE2B0, 0xFFF9B98A, 0xFF5E6573, 0xFF8BB4FF),
    ),
    ThemeSeed(
        "rose_pine", "Rosé Pine", classic = true,
        page = Color(0xFF191724), card = Color(0xFF1F1D2E), raised = Color(0xFF26233A), editor = Color(0xFF16141F),
        text = Color(0xFFE0DEF4), secondary = Color(0xFF9A96B5), accent = Color(0xFFEBBCBA),
        green = Color(0xFF95D5A7), amber = Color(0xFFF6C177), error = Color(0xFFEB6F92), blue = Color(0xFF9CCFD8),
        syntax = syntax(0xFFC4A7E7, 0xFF9CCFD8, 0xFFF6C177, 0xFFEBBCBA, 0xFF6E6A86, 0xFFEA9A97),
        ansi = ansi(
            0x26233A, 0xEB6F92, 0x31748F, 0xF6C177, 0x9CCFD8, 0xC4A7E7, 0xEBBCBA, 0xE0DEF4,
            0x6E6A86, 0xEB6F92, 0x31748F, 0xF6C177, 0x9CCFD8, 0xC4A7E7, 0xEBBCBA, 0xE0DEF4,
        ),
    ),
    ThemeSeed(
        "solarized_dark", "Solarized Dark", classic = true,
        page = Color(0xFF002B36), card = Color(0xFF073642), raised = Color(0xFF0B4452), editor = Color(0xFF00212B),
        text = Color(0xFFEEE8D5), secondary = Color(0xFF93A1A1), accent = Color(0xFF268BD2),
        green = Color(0xFF859900), amber = Color(0xFFB58900), error = Color(0xFFDC322F), blue = Color(0xFF2AA198),
        syntax = syntax(0xFF859900, 0xFFB58900, 0xFF2AA198, 0xFFD33682, 0xFF6C8288, 0xFF268BD2),
        ansi = ansi(
            0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
            0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3,
        ),
    ),
    ThemeSeed(
        "sunset", "Sunset", classic = false,
        page = Color(0xFF1C1020), card = Color(0xFF26162B), raised = Color(0xFF342036), editor = Color(0xFF140A17),
        text = Color(0xFFF6E7EF), secondary = Color(0xFFC2A3B4), accent = Color(0xFFFB923C),
        green = Color(0xFF4ADE80), amber = Color(0xFFFBBF24), error = Color(0xFFF43F5E), blue = Color(0xFF818CF8),
        syntax = syntax(0xFFF472B6, 0xFFFDE047, 0xFFBEF264, 0xFFFB923C, 0xFF8A7083, 0xFF93C5FD),
    ),
    ThemeSeed(
        "tokyo_night", "Tokyo Night", classic = true,
        page = Color(0xFF1A1B26), card = Color(0xFF1F2335), raised = Color(0xFF292E42), editor = Color(0xFF16161E),
        text = Color(0xFFC0CAF5), secondary = Color(0xFF8B93B8), accent = Color(0xFF7AA2F7),
        green = Color(0xFF9ECE6A), amber = Color(0xFFE0AF68), error = Color(0xFFF7768E), blue = Color(0xFF7DCFFF),
        syntax = syntax(0xFFBB9AF7, 0xFF2AC3DE, 0xFF9ECE6A, 0xFFFF9E64, 0xFF6A72A0, 0xFF7AA2F7),
        ansi = ansi(
            0x15161E, 0xF7768E, 0x9ECE6A, 0xE0AF68, 0x7AA2F7, 0xBB9AF7, 0x7DCFFF, 0xA9B1D6,
            0x414868, 0xF7768E, 0x9ECE6A, 0xE0AF68, 0x7AA2F7, 0x9D7CD8, 0x7DCFFF, 0xC0CAF5,
        ),
    ),
)
