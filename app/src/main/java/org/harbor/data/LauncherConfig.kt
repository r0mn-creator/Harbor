// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * The launcher's own config file.
 *
 * Plain `key = value`, same spirit as the colour themes: something a person can
 * open and edit. Settings that describe *what the launcher is set to* live here
 * rather than in SharedPreferences, so the whole configuration is visible,
 * portable and backup-able as ordinary files.
 *
 *     # Harbor
 *     color_theme = Cyber-CRT
 */
class LauncherConfig(private val context: Context) {

    private val file: File get() = File(context.getExternalFilesDir(null), "harbor.conf")

    @Volatile private var cache: MutableMap<String, String>? = null

    private fun all(): MutableMap<String, String> {
        cache?.let { return it }
        val m = linkedMapOf<String, String>()
        runCatching {
            if (file.isFile) {
                for (raw in file.readLines()) {
                    val line = raw.substringBefore('#').trim()
                    if (line.isEmpty() || !line.contains('=')) continue
                    m[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
                }
            }
        }.onFailure { Log.e(TAG, "could not read config", it) }
        cache = m
        return m
    }

    fun get(key: String): String? = all()[key]?.takeIf { it.isNotBlank() }

    /** @return null on success, or a reason. Never throws - see ProfileStore. */
    fun set(key: String, value: String?): String? {
        val m = all()
        if (value == null) m.remove(key) else m[key] = value
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeText(buildString {
                appendLine("# Harbor configuration")
                appendLine("# Edit freely; one key = value per line.")
                m.forEach { (k, v) -> appendLine("$k = $v") }
            })
            null
        }.getOrElse {
            Log.e(TAG, "could not write config", it)
            "harbor.conf is not writable: ${it.message ?: it::class.simpleName}"
        }
    }

    var colorTheme: String?
        get() = get(KEY_COLOR_THEME)
        set(v) { set(KEY_COLOR_THEME, v) }

    /** False until first-run setup has been completed or skipped. */
    var setupComplete: Boolean
        get() = get(KEY_SETUP) == "true"
        set(v) { set(KEY_SETUP, if (v) "true" else null) }

    /**
     * The user's own SteamGridDB key, or null if they have not added one.
     *
     * Optional and used only as a fallback once libretro has already been
     * tried: libretro needs no account at all, but it has almost no coverage
     * past the sixth console generation. Storing the key here rather than
     * shipping one in the APK keeps the redistribution decision - and the
     * rate limit - with the person who owns the account, not with the app.
     */
    var steamGridDbKey: String?
        get() = get(KEY_SGDB_KEY)
        set(v) { set(KEY_SGDB_KEY, v?.trim()?.takeIf { it.isNotEmpty() }) }

    /**
     * When on, "Get missing box art" stops filling gaps and instead replaces
     * EVERY cover with a SteamGridDB one, for a consistent art style across the
     * whole shelf instead of a mix of scan styles. Only takes effect with a key
     * set, and only on that explicit action - the quiet background fetch never
     * touches art that already exists.
     */
    var steamGridDbReplaceAll: Boolean
        get() = get(KEY_SGDB_REPLACE_ALL) == "true"
        set(v) { set(KEY_SGDB_REPLACE_ALL, if (v) "true" else null) }

    companion object {
        const val KEY_SETUP = "setup_complete"
        const val KEY_COLOR_THEME = "color_theme"
        const val KEY_SGDB_KEY = "steamgriddb_key"
        const val KEY_SGDB_REPLACE_ALL = "steamgriddb_replace_all"
        private const val TAG = "LH.Config"
    }
}
