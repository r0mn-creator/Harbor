// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Themes are data files, like platforms. Every field is optional and falls back
 * to the built-in default, so a two-line theme that only changes the background
 * is valid. See docs/THEMING.md.
 */
@Serializable
data class ThemeFile(
    val id: String,
    val name: String = id,
    val author: String? = null,
    val colors: ColorsSpec = ColorsSpec(),
    @SerialName("background_image") val backgroundImage: BackgroundSpec = BackgroundSpec(),
    val typography: TypographySpec = TypographySpec(),
    val shape: ShapeSpec = ShapeSpec(),
    val grid: GridSpec = GridSpec(),
)

@Serializable
data class ColorsSpec(
    val background: String = "#0E1116",
    val surface: String = "#161B22",
    @SerialName("surface_variant") val surfaceVariant: String = "#1F262E",
    val primary: String = "#4C9AFF",
    val secondary: String = "#8B62F5",
    val accent: String = "#22C55E",
    @SerialName("text_primary") val textPrimary: String = "#E6EDF3",
    @SerialName("text_secondary") val textSecondary: String = "#8B949E",
    @SerialName("text_disabled") val textDisabled: String = "#484F58",
    val outline: String = "#30363D",
    val error: String = "#F85149",
)

@Serializable
data class BackgroundSpec(
    /** null = use colors.background; else a relative bundle path or a URI. */
    val source: String? = null,
    /** Box art is unreadable over a busy wallpaper; both applied at render time. */
    val dim: Float = 0.55f,
    val blur: Float = 12f,
    val scale: String = "cover",
)

@Serializable
data class TypographySpec(
    val family: String = "Inter",
    @SerialName("family_display") val familyDisplay: String? = null,
    /** Multiplies every size at once - the control that matters on a 7" handheld. */
    val scale: Float = 1.0f,
    val weights: Map<String, Int> = mapOf("title" to 700, "body" to 400, "label" to 500),
)

@Serializable
data class ShapeSpec(
    @SerialName("corner_radius") val cornerRadius: Int = 14,
    @SerialName("card_elevation") val cardElevation: Int = 2,
)

@Serializable
data class GridSpec(
    @SerialName("cover_aspect_default") val coverAspectDefault: String = "3:4",
    val spacing: Int = 12,
)

/** Parsed, render-ready form. */
@Immutable
data class ResolvedTheme(
    val file: ThemeFile,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val outline: Color,
    val error: Color,
    /** Non-null when a named font could not be loaded; shown in Settings. */
    val fontWarning: String? = null,
)

/**
 * A hex colour that will not parse falls back to the default AND is reported.
 * Silently rendering a typo as black looks like a design choice rather than a
 * mistake - the same class of failure as a silently ignored launch intent.
 */
fun parseColor(hex: String, fallback: Color, onError: (String) -> Unit): Color {
    val s = hex.trim().removePrefix("#")
    if (s.length != 6 && s.length != 8) {
        onError("'$hex' is not #RRGGBB or #AARRGGBB")
        return fallback
    }
    val v = s.toLongOrNull(16)
    if (v == null) {
        onError("'$hex' is not valid hex")
        return fallback
    }
    return if (s.length == 6) Color(0xFF000000L or v) else Color(v)
}

fun ThemeFile.resolve(onError: (String) -> Unit = {}): ResolvedTheme {
    val d = ColorsSpec()
    fun c(v: String, dflt: String) =
        parseColor(v, parseColor(dflt, Color.Magenta) {}, onError)
    return ResolvedTheme(
        file = this,
        background = c(colors.background, d.background),
        surface = c(colors.surface, d.surface),
        surfaceVariant = c(colors.surfaceVariant, d.surfaceVariant),
        primary = c(colors.primary, d.primary),
        secondary = c(colors.secondary, d.secondary),
        accent = c(colors.accent, d.accent),
        textPrimary = c(colors.textPrimary, d.textPrimary),
        textSecondary = c(colors.textSecondary, d.textSecondary),
        textDisabled = c(colors.textDisabled, d.textDisabled),
        outline = c(colors.outline, d.outline),
        error = c(colors.error, d.error),
    )
}

val DefaultTheme: ResolvedTheme = ThemeFile(id = "harbor-dark", name = "Harbor Dark").resolve()

/** Live preview: the active theme flows through the tree, never a static object. */
val LocalTheme = compositionLocalOf { DefaultTheme }
