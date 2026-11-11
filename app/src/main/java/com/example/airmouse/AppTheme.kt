package com.example.airmouse

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build

data class ThemePalette(
    val background: Int,
    val surface: Int,
    val surfaceContainer: Int,
    val primary: Int,
    val onPrimary: Int,
    val secondary: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val danger: Int,
    val warning: Int,
    val success: Int
)

/**
 * Material Design 3 / Material You Dynamic Color & Mihon Theme Palettes
 */
enum class AppTheme(val displayName: String) {
    MONET("Material You (Dynamic)"),
    DEFAULT("Mihon Default Blue"),
    CATPPUCCIN("Catppuccin Mocha"),
    GREEN_APPLE("Green Apple"),
    LAVENDER("Lavender"),
    MIDNIGHT_DUSK("Midnight Dusk"),
    NORD("Nord"),
    STRAWBERRY_DAIQUIRI("Strawberry Daiquiri"),
    TAKO("Tako"),
    TEALTURQUOISE("Teal & Turquoise"),
    TIDAL_WAVE("Tidal Wave"),
    YINYANG("Yin & Yang"),
    YOTSUBA("Yotsuba");

    fun resolvePalette(context: Context, amoledBlack: Boolean): ThemePalette {
        val base = when (this) {
            MONET -> resolveMonetPalette(context)
            DEFAULT -> ThemePalette(
                background = Color.parseColor("#111318"),
                surface = Color.parseColor("#1D2026"),
                surfaceContainer = Color.parseColor("#282A30"),
                primary = Color.parseColor("#3B82F6"),
                onPrimary = Color.parseColor("#FFFFFF"),
                secondary = Color.parseColor("#60A5FA"),
                onSurface = Color.parseColor("#E2E2E9"),
                onSurfaceVariant = Color.parseColor("#C4C6D0"),
                outline = Color.parseColor("#44474F"),
                danger = Color.parseColor("#EF4444"),
                warning = Color.parseColor("#F59E0B"),
                success = Color.parseColor("#22C55E")
            )
            CATPPUCCIN -> ThemePalette(
                background = Color.parseColor("#1E1E2E"),
                surface = Color.parseColor("#181825"),
                surfaceContainer = Color.parseColor("#313244"),
                primary = Color.parseColor("#CBA6F7"),
                onPrimary = Color.parseColor("#11111B"),
                secondary = Color.parseColor("#B4BEFE"),
                onSurface = Color.parseColor("#CDD6F4"),
                onSurfaceVariant = Color.parseColor("#BAC2DE"),
                outline = Color.parseColor("#45475A"),
                danger = Color.parseColor("#F38BA8"),
                warning = Color.parseColor("#FAB387"),
                success = Color.parseColor("#A6E3A1")
            )
            GREEN_APPLE -> ThemePalette(
                background = Color.parseColor("#0F1511"),
                surface = Color.parseColor("#1B211C"),
                surfaceContainer = Color.parseColor("#252B26"),
                primary = Color.parseColor("#22C55E"),
                onPrimary = Color.parseColor("#052E16"),
                secondary = Color.parseColor("#7ADB8F"),
                onSurface = Color.parseColor("#DFE4DD"),
                onSurfaceVariant = Color.parseColor("#BFC9C0"),
                outline = Color.parseColor("#404942"),
                danger = Color.parseColor("#EF4444"),
                warning = Color.parseColor("#F59E0B"),
                success = Color.parseColor("#4ADE80")
            )
            LAVENDER -> ThemePalette(
                background = Color.parseColor("#16121A"),
                surface = Color.parseColor("#221E27"),
                surfaceContainer = Color.parseColor("#2D2832"),
                primary = Color.parseColor("#A855F7"),
                onPrimary = Color.parseColor("#FFFFFF"),
                secondary = Color.parseColor("#DCB8FF"),
                onSurface = Color.parseColor("#E9E0EB"),
                onSurfaceVariant = Color.parseColor("#CCC3CE"),
                outline = Color.parseColor("#4A444D"),
                danger = Color.parseColor("#F43F5E"),
                warning = Color.parseColor("#FBBF24"),
                success = Color.parseColor("#34D399")
            )
            MIDNIGHT_DUSK -> ThemePalette(
                background = Color.parseColor("#16101D"),
                surface = Color.parseColor("#231A2C"),
                surfaceContainer = Color.parseColor("#2E2339"),
                primary = Color.parseColor("#F02475"),
                onPrimary = Color.parseColor("#FFFFFF"),
                secondary = Color.parseColor("#FF7AA8"),
                onSurface = Color.parseColor("#EDE0F2"),
                onSurfaceVariant = Color.parseColor("#D0C1D6"),
                outline = Color.parseColor("#52425B"),
                danger = Color.parseColor("#FB7185"),
                warning = Color.parseColor("#FBBF24"),
                success = Color.parseColor("#4ADE80")
            )
            NORD -> ThemePalette(
                background = Color.parseColor("#242933"),
                surface = Color.parseColor("#2E3440"),
                surfaceContainer = Color.parseColor("#3B4252"),
                primary = Color.parseColor("#88C0D0"),
                onPrimary = Color.parseColor("#2E3440"),
                secondary = Color.parseColor("#81A1C1"),
                onSurface = Color.parseColor("#ECEFF4"),
                onSurfaceVariant = Color.parseColor("#D8DEE9"),
                outline = Color.parseColor("#4C566A"),
                danger = Color.parseColor("#BF616A"),
                warning = Color.parseColor("#EBCB8B"),
                success = Color.parseColor("#A3BE8C")
            )
            STRAWBERRY_DAIQUIRI -> ThemePalette(
                background = Color.parseColor("#1A1113"),
                surface = Color.parseColor("#271D1F"),
                surfaceContainer = Color.parseColor("#322729"),
                primary = Color.parseColor("#E11D48"),
                onPrimary = Color.parseColor("#FFFFFF"),
                secondary = Color.parseColor("#FFB2B9"),
                onSurface = Color.parseColor("#F0DEDF"),
                onSurfaceVariant = Color.parseColor("#D7C1C3"),
                outline = Color.parseColor("#524345"),
                danger = Color.parseColor("#F43F5E"),
                warning = Color.parseColor("#F59E0B"),
                success = Color.parseColor("#22C55E")
            )
            TAKO -> ThemePalette(
                background = Color.parseColor("#17151F"),
                surface = Color.parseColor("#211F2B"),
                surfaceContainer = Color.parseColor("#2C2938"),
                primary = Color.parseColor("#F3B375"),
                onPrimary = Color.parseColor("#26190A"),
                secondary = Color.parseColor("#A390E4"),
                onSurface = Color.parseColor("#E7E0EC"),
                onSurfaceVariant = Color.parseColor("#CBC4D0"),
                outline = Color.parseColor("#49454F"),
                danger = Color.parseColor("#EF4444"),
                warning = Color.parseColor("#F59E0B"),
                success = Color.parseColor("#4ADE80")
            )
            TEALTURQUOISE -> ThemePalette(
                background = Color.parseColor("#0D1516"),
                surface = Color.parseColor("#192122"),
                surfaceContainer = Color.parseColor("#232C2D"),
                primary = Color.parseColor("#14B8A6"),
                onPrimary = Color.parseColor("#042F2E"),
                secondary = Color.parseColor("#4FD8EB"),
                onSurface = Color.parseColor("#DEE3E5"),
                onSurfaceVariant = Color.parseColor("#BFC8CA"),
                outline = Color.parseColor("#3F484A"),
                danger = Color.parseColor("#EF4444"),
                warning = Color.parseColor("#F59E0B"),
                success = Color.parseColor("#22C55E")
            )
            TIDAL_WAVE -> ThemePalette(
                background = Color.parseColor("#0B151C"),
                surface = Color.parseColor("#162129"),
                surfaceContainer = Color.parseColor("#202C35"),
                primary = Color.parseColor("#0284C7"),
                onPrimary = Color.parseColor("#FFFFFF"),
                secondary = Color.parseColor("#5FD4FD"),
                onSurface = Color.parseColor("#DFE3E7"),
                onSurfaceVariant = Color.parseColor("#C0C7CD"),
                outline = Color.parseColor("#40484E"),
                danger = Color.parseColor("#EF4444"),
                warning = Color.parseColor("#F59E0B"),
                success = Color.parseColor("#22C55E")
            )
            YINYANG -> ThemePalette(
                background = Color.parseColor("#121212"),
                surface = Color.parseColor("#1E1E1E"),
                surfaceContainer = Color.parseColor("#2C2C2C"),
                primary = Color.parseColor("#E2E8F0"),
                onPrimary = Color.parseColor("#0F172A"),
                secondary = Color.parseColor("#94A3B8"),
                onSurface = Color.parseColor("#F8FAFC"),
                onSurfaceVariant = Color.parseColor("#CBD5E1"),
                outline = Color.parseColor("#525252"),
                danger = Color.parseColor("#EF4444"),
                warning = Color.parseColor("#F59E0B"),
                success = Color.parseColor("#22C55E")
            )
            YOTSUBA -> ThemePalette(
                background = Color.parseColor("#19120E"),
                surface = Color.parseColor("#261E19"),
                surfaceContainer = Color.parseColor("#312823"),
                primary = Color.parseColor("#EA580C"),
                onPrimary = Color.parseColor("#FFFFFF"),
                secondary = Color.parseColor("#FFB59B"),
                onSurface = Color.parseColor("#F0DFD8"),
                onSurfaceVariant = Color.parseColor("#D7C2B9"),
                outline = Color.parseColor("#52443D"),
                danger = Color.parseColor("#EF4444"),
                warning = Color.parseColor("#FBBF24"),
                success = Color.parseColor("#22C55E")
            )
        }

        return if (amoledBlack) {
            base.copy(
                background = Color.BLACK,
                surface = Color.parseColor("#0A0A0A"),
                surfaceContainer = Color.parseColor("#161616")
            )
        } else {
            base
        }
    }

    private fun resolveMonetPalette(context: Context): ThemePalette {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                ThemePalette(
                    background = context.getColor(android.R.color.system_neutral1_900),
                    surface = context.getColor(android.R.color.system_neutral1_800),
                    surfaceContainer = context.getColor(android.R.color.system_neutral2_800),
                    primary = context.getColor(android.R.color.system_accent1_400),
                    onPrimary = context.getColor(android.R.color.system_neutral1_900),
                    secondary = context.getColor(android.R.color.system_accent2_300),
                    onSurface = context.getColor(android.R.color.system_neutral1_50),
                    onSurfaceVariant = context.getColor(android.R.color.system_neutral2_200),
                    outline = context.getColor(android.R.color.system_neutral2_600),
                    danger = Color.parseColor("#EF4444"),
                    warning = Color.parseColor("#F59E0B"),
                    success = Color.parseColor("#22C55E")
                )
            } catch (_: Exception) {
                DEFAULT.resolvePalette(context, false)
            }
        }
        return DEFAULT.resolvePalette(context, false)
    }

    companion object {
        fun materialCard(
            fillColor: Int,
            radiusPx: Int,
            strokeColor: Int? = null,
            strokeWidthPx: Int = 2
        ): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radiusPx.toFloat()
                setColor(fillColor)
                if (strokeColor != null) {
                    setStroke(strokeWidthPx, strokeColor)
                }
            }
        }

        /**
         * High-visibility Material 3 state tint list for CheckBoxes and Switches
         * so checkmarks and tracks are bright and clearly distinguishable.
         */
        fun checkboxTintList(palette: ThemePalette): ColorStateList {
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            val colors = intArrayOf(
                palette.primary,
                palette.onSurfaceVariant
            )
            return ColorStateList(states, colors)
        }
    }
}
