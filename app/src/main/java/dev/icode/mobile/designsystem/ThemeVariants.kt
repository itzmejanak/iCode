package dev.icode.mobile.designsystem

import dev.icode.mobile.designsystem.palettes.AmoledSeeds
import dev.icode.mobile.designsystem.palettes.DarkSeeds
import dev.icode.mobile.designsystem.palettes.LightSeeds
import dev.icode.mobile.designsystem.palettes.ThemeSeed
import dev.icode.mobile.designsystem.palettes.onBlack
import dev.icode.mobile.designsystem.palettes.palette
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.ThemeVariantRules

/** Where a variant is listed in the sheet (docs/design-and-navigation.md). */
enum class VariantGroup { Icode, Classic, More }

/**
 * One way to draw a theme (docs/design-and-navigation.md): iCode's own, or a ported palette. Its
 * palette is made the first time it is drawn, so a sheet of variants costs only those it shows.
 */
class ThemeVariant internal constructor(
    val id: String,
    val name: String,
    val theme: AppTheme,
    val group: VariantGroup,
    palette: () -> IcodePalette,
) {
    /** The variant with its card outlines; [IcodePalette.of] derives it without them. */
    val palette: IcodePalette by lazy(palette)
}

/**
 * Every theme's variants, in the order the variant sheet lists them: iCode's own first, then the
 * classics and the rest, each A to Z. AMOLED has every Dark variant on true black besides
 * the ones designed on it.
 */
object ThemeVariants {
    private val dark = listOf(icode(AppTheme.Dark) { IcodePalette.Dark }) +
        DarkSeeds.map { seed -> variant(seed, AppTheme.Dark) { seed.palette(PaletteKind.Dark) } }.sorted()
    private val light = listOf(icode(AppTheme.Light) { IcodePalette.Light }) +
        LightSeeds.map { seed -> variant(seed, AppTheme.Light) { seed.palette(PaletteKind.Light) } }.sorted()
    private val amoled = listOf(icode(AppTheme.Amoled) { IcodePalette.Amoled }) + (
        AmoledSeeds.map { seed -> variant(seed, AppTheme.Amoled) { seed.palette(PaletteKind.Amoled) } } +
            DarkSeeds.map { seed -> variant(seed, AppTheme.Amoled) { seed.onBlack() } }
        ).sorted()

    fun of(theme: AppTheme): List<ThemeVariant> = when (theme) {
        AppTheme.Dark -> dark
        AppTheme.Light -> light
        AppTheme.Amoled -> amoled
    }

    /** [theme]'s variant [id]: iCode's own for none, and for an id no variant has, such as one a later version removed. */
    fun find(theme: AppTheme, id: String?): ThemeVariant = of(theme).let { variants -> variants.firstOrNull { it.id == id } ?: variants.first() }

    private fun icode(theme: AppTheme, palette: () -> IcodePalette) =
        ThemeVariant(ThemeVariantRules.ICODE, "iCode", theme, VariantGroup.Icode, palette)

    private fun variant(seed: ThemeSeed, theme: AppTheme, palette: () -> IcodePalette) =
        ThemeVariant(seed.id, seed.name, theme, if (seed.classic) VariantGroup.Classic else VariantGroup.More, palette)

    private fun List<ThemeVariant>.sorted() = sortedWith(compareBy<ThemeVariant>({ it.group }, { it.name }))
}
