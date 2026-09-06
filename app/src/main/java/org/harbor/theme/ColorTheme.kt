// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.theme

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * A colour theme: one file, four roles.
 *
 * The format is deliberately CSS-like rather than JSON - no braces, no quotes,
 * no comma to forget. Someone can write one in a text editor on the device:
 *
 *     // Cyber-CRT
 *     --main:      #0C0F0A;
 *     --secondary: #1F2826;
 *     --accent:    #32E0C4;
 *     --highlight: #EE4540;
 *
 * The FILE NAME is the theme name, so adding a theme is dropping a file in and
 * renaming it. Everything the UI needs is derived from the four roles, so a
 * complete theme is four lines; any derived colour can still be overridden by
 * naming it explicitly (--text, --outline, --error, --surface-variant).
 */
data class ColorTheme(
    val name: String,
    val main: Color,
    val secondary: Color,
    val accent: Color,
    val highlight: Color,
    val overrides: Map<String, Color> = emptyMap(),
    /** null for the built-in default, which has no file. */
    val file: File? = null,
) {
    val isDefault: Boolean get() = file == null
}

object ColorThemes {

    const val DEFAULT_NAME = "Default"

    /** The built-in scheme, expressed in the same four roles. */
    val DEFAULT = ColorTheme(
        name = DEFAULT_NAME,
        main = Color(0xFF0E1116),
        secondary = Color(0xFF161B22),
        accent = Color(0xFF4C9AFF),
        highlight = Color(0xFF22C55E),
    )

    // ---- parsing ------------------------------------------------------------

    /**
     * Tolerant on purpose: blank lines, `//` and `/* */` comments, `;` optional,
     * and either `--main:` or plain `main:`. A hand-edited file should not fail
     * over punctuation.
     */
    fun parse(name: String, text: String, file: File?): Result<ColorTheme> {
        val values = mutableMapOf<String, Color>()
        val stripped = text.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        for (raw in stripped.lineSequence()) {
            val line = raw.substringBefore("//").trim().removeSuffix(";").trim()
            if (line.isEmpty() || !line.contains(':')) continue
            val key = line.substringBefore(':').trim().removePrefix("--").lowercase()
            val v = line.substringAfter(':').trim()
            val c = parseHex(v) ?: continue
            values[key] = c
        }
        val main = values["main"] ?: values["background"]
        val secondary = values["secondary"] ?: values["surface"]
        val accent = values["accent"]
        val highlight = values["highlight"]
        if (main == null || secondary == null || accent == null || highlight == null) {
            val missing = listOfNotNull(
                if (main == null) "main" else null,
                if (secondary == null) "secondary" else null,
                if (accent == null) "accent" else null,
                if (highlight == null) "highlight" else null,
            )
            return Result.failure(
                IllegalArgumentException("missing ${missing.joinToString(", ")}")
            )
        }
        return Result.success(
            ColorTheme(name, main, secondary, accent, highlight,
                overrides = values.filterKeys {
                    it in setOf("text", "text-secondary", "outline", "error", "surface-variant")
                },
                file = file)
        )
    }

    private fun parseHex(s: String): Color? {
        val h = s.trim().removePrefix("#")
        if (h.length != 6 && h.length != 8) return null
        val v = h.toLongOrNull(16) ?: return null
        return if (h.length == 6) Color(0xFF000000L or v) else Color(v)
    }

    fun serialize(t: ColorTheme): String = buildString {
        appendLine("// ${t.name}")
        appendLine("--main:      ${hex(t.main)};")
        appendLine("--secondary: ${hex(t.secondary)};")
        appendLine("--accent:    ${hex(t.accent)};")
        appendLine("--highlight: ${hex(t.highlight)};")
    }

    fun hex(c: Color): String {
        val r = (c.red * 255).toInt(); val g = (c.green * 255).toInt()
        val b = (c.blue * 255).toInt()
        return "#%02X%02X%02X".format(r, g, b)
    }

    // ---- deriving the full palette from four roles --------------------------

    private fun luminance(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    /** WCAG relative luminance, which needs the gamma curve undone first. */
    private fun relLuminance(c: Color): Float {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92f
                           else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * ch(c.red) + 0.7152f * ch(c.green) + 0.0722f * ch(c.blue)
    }

    /** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    fun contrast(a: Color, b: Color): Float {
        val la = relLuminance(a); val lb = relLuminance(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /**
     * Text is the one role an author can set and make the whole UI unreadable,
     * which is the reason it stays OPTIONAL and derived by default. When it IS
     * set, a poor choice is reported rather than silently rendered - the same
     * rule the rest of the app follows.
     *
     * 4.5:1 is the WCAG AA threshold for body text.
     */
    fun warnings(t: ColorTheme): List<String> = buildList {
        t.overrides["text"]?.let { text ->
            val c = contrast(text, t.main)
            if (c < 4.5f) {
                add("--text on --main is %.1f:1, below the 4.5:1 readable threshold"
                    .format(c))
            }
        }
        t.overrides["text-secondary"]?.let { text ->
            val c = contrast(text, t.main)
            if (c < 3.0f) {
                add("--text-secondary on --main is %.1f:1 and will be hard to read"
                    .format(c))
            }
        }
        if (contrast(t.accent, t.main) < 2.0f) {
            add("--accent barely separates from --main; the selection will be hard to see")
        }
    }

    private fun mix(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )

    /**
     * Text is chosen against the MAIN colour's luminance, so a light theme is
     * readable without the author having to think about it. A theme that only
     * specifies four dark colours must not produce white-on-white.
     */
    fun resolve(t: ColorTheme): ResolvedTheme {
        val dark = luminance(t.main) < 0.5f
        val ink = if (dark) Color(0xFFF2F5F8) else Color(0xFF0D1117)
        val text = t.overrides["text"] ?: ink
        val textSecondary = t.overrides["text-secondary"] ?: mix(text, t.main, 0.45f)
        val surfaceVariant = t.overrides["surface-variant"]
            ?: mix(t.secondary, ink, 0.08f)

        return ResolvedTheme(
            file = ThemeFile(id = t.name, name = t.name),
            background = t.main,
            surface = t.secondary,
            surfaceVariant = surfaceVariant,
            primary = t.accent,
            secondary = t.highlight,
            accent = t.highlight,
            textPrimary = text,
            textSecondary = textSecondary,
            textDisabled = mix(text, t.main, 0.7f),
            outline = t.overrides["outline"] ?: mix(text, t.main, 0.82f),
            error = t.overrides["error"] ?: t.highlight,
        )
    }
}

/**
 * Colour themes on disk: one file each, in a folder the user can open.
 */
class ColorThemeStore(private val context: Context) {

    val dir: File get() = File(context.getExternalFilesDir(null), "themes/colors")

    fun installBundled() {
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "could not create $dir"); return
        }
        val names = runCatching { context.assets.list("colors") }.getOrNull().orEmpty()
        for (n in names) {
            val out = File(dir, n)
            if (out.exists()) continue          // never clobber an edited theme
            runCatching {
                context.assets.open("colors/$n").use { i ->
                    out.outputStream().use { o -> i.copyTo(o) }
                }
            }.onFailure { Log.e(TAG, "extract $n failed", it) }
        }
    }

    /**
     * Copy a theme file the user picked into the themes folder.
     *
     * This exists because on Android 11+ the app's own files directory is not
     * reachable from a file manager or over USB, so "drop a .theme file in"
     * is advice a person cannot follow. The picker needs no storage
     * permission and works on every Android version.
     *
     * @return the theme name on success, or null with a reason.
     */
    fun importFrom(name: String, text: String): Result<String> {
        // Notepad appends .txt unless you pick "All Files", and Windows hides
        // extensions, so "Sunset.theme" routinely arrives as "Sunset.theme.txt".
        // Tolerate it rather than naming the theme after the mistake.
        val clean = name.substringAfterLast('/')
            .removeSuffix(".txt").removeSuffix(EXT).removeSuffix(".txt")
            .replace(Regex("""[^A-Za-z0-9 ._-]"""), "_").trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("that file has no usable name"))
        // Parse BEFORE writing: a file that cannot be read as a theme should be
        // rejected at the moment the user picks it, not silently listed as a
        // problem afterwards.
        ColorThemes.parse(clean, text, null).onFailure { return Result.failure(it) }
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.failure(IllegalStateException("could not create $dir"))
        }
        return runCatching { File(dir, clean + EXT).writeText(text); clean }
    }

    data class Loaded(
        val themes: List<ColorTheme>,
        /** file name -> why it was rejected, or a readability warning. */
        val problems: Map<String, String>,
    )

    /** Default is always first; the rest sort by name. */
    fun load(): Loaded {
        val out = mutableListOf(ColorThemes.DEFAULT)
        val problems = linkedMapOf<String, String>()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }
            ?.sortedBy { it.name.lowercase() }.orEmpty()
        for (f in files) {
            val name = f.name.removeSuffix(EXT)
            val parsed = runCatching { ColorThemes.parse(name, f.readText(), f) }
                .getOrElse { Result.failure(it) }
            parsed
                .onSuccess {
                    out += it
                    // Loaded, but say so if it will be hard to read.
                    ColorThemes.warnings(it).firstOrNull()?.let { w -> problems[f.name] = w }
                }
                // Reported, not skipped: a theme that silently vanishes looks
                // identical to one that was never added.
                .onFailure { problems[f.name] = it.message ?: "could not be read" }
        }
        return Loaded(out, problems)
    }

    private companion object {
        const val TAG = "LH.Colors"
        const val EXT = ".theme"
    }
}
