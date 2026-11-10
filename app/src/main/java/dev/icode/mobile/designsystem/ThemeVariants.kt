package dev.icode.mobile.designsystem

import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.ThemeVariantRules

/**
 * One way to draw a theme (docs/theme-variants-plan.md): iCode's own, or a ported palette. Its
 * palette is made the first time it is drawn, so a sheet of variants costs only those it shows.
 */
class ThemeVariant internal constructor(
    val id: String,
    val name: String,
    val theme: AppTheme,
    palette: () -> IcodePalette,
) {
    /** The variant with its card outlines; [IcodePalette.of] derives it without them. */
    val palette: IcodePalette by lazy(palette)
}

/** Every theme's variants, in the order the variant sheet lists them: iCode's own first. */
object ThemeVariants {
    private val dark = listOf(icode(AppTheme.Dark) { IcodePalette.Dark })
    private val light = listOf(icode(AppTheme.Light) { IcodePalette.Light })
    private val amoled = listOf(icode(AppTheme.Amoled) { IcodePalette.Amoled })

    fun of(theme: AppTheme): List<ThemeVariant> = when (theme) {
        AppTheme.Dark -> dark
        AppTheme.Light -> light
        AppTheme.Amoled -> amoled
    }

    /** [theme]'s variant [id]: iCode's own for none, and for an id no variant has, such as one a later version removed. */
    fun find(theme: AppTheme, id: String?): ThemeVariant = of(theme).let { variants -> variants.firstOrNull { it.id == id } ?: variants.first() }

    private fun icode(theme: AppTheme, palette: () -> IcodePalette) = ThemeVariant(ThemeVariantRules.ICODE, "iCode", theme, palette)
}
