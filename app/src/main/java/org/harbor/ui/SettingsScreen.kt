// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import org.harbor.data.CatalogueSystem

/**
 * State the Settings menu renders from. Gathered by the Activity and rebuilt on
 * every navigation so a row always shows the truth, not a stale snapshot.
 */
data class SettingsState(
    val platforms: List<PlatformRowState>,
    val catalogue: List<CatalogueRowState>,
    val colorThemes: List<String>,
    val activeColorTheme: String,
    val colorProblems: Map<String, String>,
    val gamesTotal: Int,
    val gamesPlayable: Int,
    val problems: Map<String, String>,
    val cleanupPlan: CleanupPlan,
    val missingArt: Int,
    /** The user's own key, or null if they have not added one. */
    val steamGridDbKey: String? = null,
    val steamGridDbReplaceAll: Boolean = false,
)

/**
 * What "Forget missing games" would do, worked out before anything is deleted.
 *
 * This exists because the action once removed 62 real games: a folder had been
 * granted on one game's own subfolder, so 21 present PlayStation titles scanned
 * as missing. No heuristic catches every version of that, so the plan is shown
 * by name and the deletion is a second, separate press.
 */
data class CleanupPlan(
    val removals: List<CleanupGroup>,
    /** platform name -> why its records were not considered. */
    val skipped: List<Pair<String, String>>,
) {
    val total: Int get() = removals.sumOf { it.titles.size }
}

data class CleanupGroup(
    val platform: String,
    val titles: List<String>,
    val keys: List<String>,
)

data class PlatformRowState(
    val id: String,
    val name: String,
    val games: Int,
    val playable: Int,
    val verified: Boolean,
    val needsFolder: Boolean,
    val enabled: Boolean,
    val problem: String? = null,
    /** installed_apps platform: a curated shelf rather than a scanned folder. */
    val isAppShelf: Boolean = false,
    val aspectRatio: String = "3:4",
    /** Apps known to run this system, plus whatever it is set to now. */
    val emulators: List<EmulatorOption> = emptyList(),
    val currentEmulator: String? = null,
)

/**
 * One app that can run a system.
 *
 * Listed whether or not it is installed: knowing that Dolphin is the app for
 * GameCube is useful before you have it, and a list that hides everything you
 * have not installed looks broken on a fresh device.
 */
data class EmulatorOption(
    val pkg: String,
    val name: String,
    val installed: Boolean,
    /** Its launch contract has been confirmed on a real device. */
    val verified: Boolean,
    val inUse: Boolean,
)

data class CatalogueRowState(
    val system: CatalogueSystem,
    val alreadyAdded: Boolean,
    val installedEmulator: String?,
)

/** One installed app in the Android-shelf picker. */
data class AppChoice(val pkg: String, val label: String, val chosen: Boolean)
