// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A platform is DATA, never code.
 *
 * Adding, editing or removing an emulator must never need a recompile. The
 * failure this avoids is a hard-coded package registry: an unrecognised emulator
 * gets a bare launcher intent with no ROM attached, so it opens to its own menu
 * and
 * the game never loads. Everything that differs between emulators lives in this
 * file's types and is loaded from JSON at runtime.
 *
 * See docs/ARCHITECTURE.md and docs/LAUNCH_CONTRACTS.md.
 */
@Serializable
data class PlatformProfile(
    val id: String,
    val name: String,
    @SerialName("short_name") val shortName: String = id.uppercase(),
    val order: Int = 100,
    @SerialName("aspect_ratio") val aspectRatio: String = "3:4",
    val source: SourceSpec,
    val launch: LaunchSpec,
    val scraper: ScraperSpec? = null,
    /**
     * Set only by the Test-launch flow, which asks the user whether the game
     * actually loaded. Never inferred from the app starting - X1-BOX spawns its
     * emulator process and sits on "Please insert an Xbox disc" when the intent
     * is wrong, which looks identical to success from the outside.
     */
    val verified: Boolean = false,
    val enabled: Boolean = true,
)

/** How games are DISCOVERED. A fixed set of mechanisms, not one per emulator. */
@Serializable
data class SourceSpec(
    val provider: String,
    /** `folder`: SAF tree URIs to scan. */
    val roots: List<String> = emptyList(),
    /** `folder`: extensions to accept, without the dot. Case-insensitive. */
    val extensions: List<String> = emptyList(),
    /** `folder`: names/globs to skip (e.g. "*.txt", "media"). */
    val ignore: List<String> = emptyList(),
    /** `library` / `shortcuts`: which app to read from. */
    val app: String? = null,
    /**
     * `installed_apps`: the CURATED list of packages to show.
     *
     * Android has no ROM folder, so this section is a hand-picked shelf rather
     * than a scan. Listing every launchable app would bury the handful of games
     * the user actually wants among a hundred utilities.
     */
    val packages: List<String> = emptyList(),
) {
    companion object {
        const val FOLDER = "folder"
        const val INSTALLED_APPS = "installed_apps"
        const val SHORTCUTS = "shortcuts"
        const val LIBRARY = "library"
        val KNOWN = setOf(FOLDER, INSTALLED_APPS, SHORTCUTS, LIBRARY)
    }
}

/**
 * How a game is handed to the emulator. This is the whole point of Harbor.
 */
@Serializable
data class LaunchSpec(
    /** "pkg/activity", or "pkg/.Activity". Blank for an implicit intent. */
    val component: String? = null,
    val action: String = "android.intent.action.VIEW",
    /** MIME type, e.g. "application/octet-stream". */
    val type: String? = null,
    /**
     * Where the game goes:
     *   data_uri - intent.data = the game's content:// URI  (most emulators)
     *   extra    - the URI/path goes in a named extra instead
     *   none     - nothing file-based; the game is identified by extras only
     *              (GameNative: app_id is a Steam number, not a file)
     */
    @SerialName("rom_mode") val romMode: String = ROM_DATA_URI,
    /** For rom_mode = "extra": which extra receives the ROM. */
    @SerialName("rom_extra") val romExtra: String? = null,
    val flags: List<String> = listOf(FLAG_GRANT_READ),
    val extras: List<ExtraSpec> = emptyList(),
) {
    companion object {
        const val ROM_DATA_URI = "data_uri"
        const val ROM_EXTRA = "extra"
        const val ROM_NONE = "none"
        val KNOWN_ROM_MODES = setOf(ROM_DATA_URI, ROM_EXTRA, ROM_NONE)

        /**
         * Defaults ON, and it is load-bearing. Verified on the Odin 2: X1-BOX
         * with ACTION_VIEW + content:// and NO grant flag launches, spawns its
         * :xemu process, and sits forever on "Please insert an Xbox disc". The
         * same intent with the flag boots Halo 2 at 59.9 FPS. That app holds no
         * storage permissions at all, so there is no fallback path.
         */
        const val FLAG_GRANT_READ = "GRANT_READ_URI_PERMISSION"
    }
}

@Serializable
data class ExtraSpec(
    val key: String,
    /** string | int | long | bool */
    val type: String = "string",
    /** May contain {file}, {id}, {title} placeholders. */
    val value: String,
)

@Serializable
data class ScraperSpec(val platform: String? = null)

/** Result of validating a profile. A broken profile is disabled WITH A REASON. */
sealed interface ProfileCheck {
    data object Ok : ProfileCheck
    data class Invalid(val reason: String) : ProfileCheck
}

/**
 * Validate on load. An unknown provider or rom_mode is REPORTED, never
 * defaulted: silently degrading to "launch the app with no ROM" is precisely
 * the failure this project exists to avoid.
 */
fun PlatformProfile.validate(): ProfileCheck {
    if (id.isBlank()) return ProfileCheck.Invalid("missing id")
    if (name.isBlank()) return ProfileCheck.Invalid("missing name")

    if (source.provider !in SourceSpec.KNOWN) {
        return ProfileCheck.Invalid(
            "unknown source provider '${source.provider}' " +
                "(expected one of ${SourceSpec.KNOWN.sorted().joinToString(", ")})"
        )
    }
    if (source.provider == SourceSpec.FOLDER) {
        if (source.roots.isEmpty()) return ProfileCheck.Invalid("folder source has no roots")
        if (source.extensions.isEmpty()) {
            return ProfileCheck.Invalid("folder source has no extensions; it would match nothing")
        }
    }
    if (source.provider == SourceSpec.LIBRARY && source.app.isNullOrBlank()) {
        return ProfileCheck.Invalid("library source needs an 'app' package")
    }

    if (launch.romMode !in LaunchSpec.KNOWN_ROM_MODES) {
        return ProfileCheck.Invalid(
            "unknown rom_mode '${launch.romMode}' " +
                "(expected ${LaunchSpec.KNOWN_ROM_MODES.sorted().joinToString(", ")})"
        )
    }
    if (launch.romMode == LaunchSpec.ROM_EXTRA && launch.romExtra.isNullOrBlank()) {
        return ProfileCheck.Invalid("rom_mode 'extra' needs 'rom_extra' naming the extra")
    }
    if (launch.action.isBlank()) return ProfileCheck.Invalid("launch action is blank")
    // A bare package (no '/') is legitimate and often preferable: with
    // ACTION_VIEW + a content URI, Android resolves to whichever activity in
    // that app actually declares a matching filter. An import may carry only a
    // package, so requiring package/activity would reject every imported
    // platform - which it did, on the first real import.
    launch.component?.let {
        if (it.isNotBlank() && it.count { c -> c == '/' } > 1) {
            return ProfileCheck.Invalid("component '$it' has too many '/'")
        }
        if (it.endsWith("/")) {
            return ProfileCheck.Invalid("component '$it' has no activity after '/'")
        }
    }
    for (e in launch.extras) {
        if (e.key.isBlank()) return ProfileCheck.Invalid("an extra has a blank key")
        if (e.type !in setOf("string", "int", "long", "bool")) {
            return ProfileCheck.Invalid("extra '${e.key}' has unknown type '${e.type}'")
        }
    }
    return ProfileCheck.Ok
}
