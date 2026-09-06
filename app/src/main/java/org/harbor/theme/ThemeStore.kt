// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.theme

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads themes from disk, same shape as ProfileStore: bundled presets extracted
 * once into a user-writable directory, a user file with the same id wins, and
 * extraction never overwrites. See docs/THEMING.md.
 */
class ThemeStore(private val context: Context) {

    data class Loaded(
        val themes: List<ThemeFile>,
        val problems: Map<String, String>,
    )

    private val json = Json { ignoreUnknownKeys = false; isLenient = true; prettyPrint = true }

    val dir: File get() = File(context.getExternalFilesDir(null), "themes")
    val fontsDir: File get() = File(dir, "fonts")
    val imagesDir: File get() = File(dir, "images")

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("harbor", Context.MODE_PRIVATE)

    fun installBundled() {
        listOf(dir, fontsDir, imagesDir).forEach { if (!it.exists()) it.mkdirs() }
        val names = runCatching { context.assets.list("themes") }.getOrNull().orEmpty()
        for (n in names) {
            if (!n.endsWith(".json")) continue
            val out = File(dir, n)
            if (out.exists()) continue          // user's copy always wins
            runCatching {
                context.assets.open("themes/$n").use { i ->
                    out.outputStream().use { o -> i.copyTo(o) }
                }
            }.onFailure { Log.e(TAG, "extract $n failed", it) }
        }
    }

    fun load(): Loaded {
        val themes = mutableListOf<ThemeFile>()
        val problems = linkedMapOf<String, String>()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name }.orEmpty()
        for (f in files) {
            runCatching { json.decodeFromString<ThemeFile>(f.readText()) }
                .onSuccess { themes += it }
                .onFailure {
                    problems[f.name] = "could not be read: ${it.message ?: it::class.simpleName}"
                }
        }
        return Loaded(themes, problems)
    }

    /** Currently selected theme id, or null for the built-in default. */
    var selectedId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(v) = prefs.edit().apply { if (v == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, v) }.apply()

    fun active(): ResolvedTheme {
        val id = selectedId ?: return DefaultTheme
        val t = load().themes.firstOrNull { it.id == id } ?: return DefaultTheme
        return t.resolve()
    }

    /**
     * Reset to defaults: stop using any custom theme and clear overrides.
     *
     * Deliberately does NOT delete the user's theme files - an hour of colour
     * picking should never be one mis-tap from gone. Deleting a theme is a
     * separate, explicit action.
     */
    fun resetToDefaults() {
        prefs.edit().remove(KEY_SELECTED).apply()
    }

    fun save(t: ThemeFile) {
        if (!dir.exists()) dir.mkdirs()
        File(dir, "${t.id}.json").writeText(json.encodeToString(ThemeFile.serializer(), t))
    }

    fun delete(id: String): Boolean = File(dir, "$id.json").delete()

    private companion object {
        const val TAG = "LH.ThemeStore"
        const val KEY_SELECTED = "selected_theme"
    }
}
