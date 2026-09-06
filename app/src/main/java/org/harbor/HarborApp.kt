// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor

import android.app.Application
import org.harbor.data.CatalogueStore
import org.harbor.data.LauncherConfig
import org.harbor.data.LibraryStore
import org.harbor.data.ProfileStore
import org.harbor.theme.ColorThemeStore
import org.harbor.theme.ColorThemes
import org.harbor.theme.ResolvedTheme
import org.harbor.theme.ThemeStore

class HarborApp : Application() {

    lateinit var profiles: ProfileStore
        private set
    lateinit var themes: ThemeStore
        private set
    lateinit var library: LibraryStore
        private set
    lateinit var catalogue: CatalogueStore
        private set
    lateinit var colors: ColorThemeStore
        private set
    lateinit var config: LauncherConfig
        private set

    override fun onCreate() {
        super.onCreate()
        installStrictMode()
        installCrashGuard()
        profiles = ProfileStore(this)
        themes = ThemeStore(this)
        library = LibraryStore(this)
        catalogue = CatalogueStore(this)
        colors = ColorThemeStore(this)
        config = LauncherConfig(this)
        // Extract bundled presets on first run and after an update. Neither call
        // ever overwrites an existing file, so a user's edited Xbox profile or
        // hand-tuned theme survives every update.
        profiles.installBundled()
        themes.installBundled()
        colors.installBundled()
    }

    /**
     * Debug-only: surface main-thread disk reads and leaks in logcat.
     *
     * A launcher does file IO constantly (profiles, themes, library) and it is
     * very easy to end up reading them during composition or on every keypress,
     * which shows up as input lag long before it shows up as a crash.
     */
    private fun installStrictMode() {
        if (!BuildConfig.DEBUG) return
        android.os.StrictMode.setThreadPolicy(
            android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        android.os.StrictMode.setVmPolicy(
            android.os.StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }

    /**
     * Harbor is the HOME app. If it dies the device has no home screen, so
     * an uncaught exception must not simply take the process down - Android
     * would relaunch it, hit the same state, and crash-loop the device into
     * being unusable.
     *
     * The guard records what happened and lets the default handler proceed, so
     * the failure is never silent, but the next start can read the marker and
     * come up in a reduced state rather than repeating it.
     */
    private fun installCrashGuard() {
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val f = java.io.File(getExternalFilesDir(null), "last_crash.txt")
                f.writeText(buildString {
                    appendLine("thread: " + t.name)
                    appendLine("when: " + java.util.Date())
                    appendLine()
                    appendLine(android.util.Log.getStackTraceString(e))
                })
            }
            prior?.uncaughtException(t, e)
        }
    }

    /** The active palette, from the colour theme named in harbor.conf. */
    fun activeColors(): ResolvedTheme {
        val want = config.colorTheme ?: return ColorThemes.resolve(ColorThemes.DEFAULT)
        val t = colors.load().themes.firstOrNull { it.name == want }
            ?: ColorThemes.DEFAULT      // a deleted or renamed theme falls back
        return ColorThemes.resolve(t)
    }

    companion object {
        lateinit var instance: HarborApp
            private set
    }

    init {
        instance = this
    }
}
