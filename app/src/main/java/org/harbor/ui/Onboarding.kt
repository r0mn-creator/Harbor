// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import org.harbor.data.CatalogueSystem

/**
 * First-run setup.
 *
 * A fresh install otherwise lands on a shelf reading "folder source has no
 * roots" with a Choose folder button - technically honest, and a terrible first
 * thing to see.
 *
 * Deliberately asks for ONE emulator and ONE folder. Setting up twelve systems
 * before you have seen a single game is a chore, and the launcher is useful the
 * moment one system works; everything else is a Settings screen away. Every
 * step therefore carries both **Next** and **Done**, and Done finishes setup
 * immediately from wherever you are.
 *
 * Each step is a [MenuNode], so the wizard renders through the same row
 * vocabulary as Settings and inherits pad navigation, touch, and the hints that
 * name the action - rather than being a second UI to keep in step.
 */
enum class Step { WELCOME, EMULATOR, FOLDER, DONE }

data class OnboardingState(
    /** A Beacon export found in shared storage, if any. */
    val beaconExport: String?,
    /** Catalogue systems whose emulator is installed and which are not added yet. */
    val detected: List<Pair<CatalogueSystem, String>>,
    /** The system chosen in this run, once one has been. */
    val pickedId: String?,
    val pickedName: String?,
    val pickedEmulator: String?,
    val pickedHasFolder: Boolean,
    val pickedGames: Int,
    val systemsAdded: Int,
    val gamesFound: Int,
    val busy: Boolean = false,
)

interface OnboardingActions {
    fun next()
    fun finish()
    fun importBeacon()
    fun pickSystem(system: CatalogueSystem)
    fun chooseFolderForPicked()
    fun openCatalogue()
}

object Onboarding {

    fun node(step: Step, s: OnboardingState, a: OnboardingActions): MenuNode = when (step) {

        Step.WELCOME -> MenuNode(
            id = "ob-welcome",
            title = "LH Game Launcher",
            subtitle = "Set up one emulator and point LH at its games. That is " +
                "the whole of it - you can add every other system later in " +
                "Settings, and you can press Done at any point.",
            items = buildList {
                add(MenuItem.Action("Next",
                    "Choose the first emulator") { a.next() })
                if (s.beaconExport != null) {
                    add(MenuItem.Action("Import from Beacon first",
                        "An export was found in ${s.beaconExport} - brings your " +
                            "games, artwork and favourites across",
                        enabled = !s.busy) { a.importBeacon() })
                }
                add(MenuItem.Action("Done",
                    "Skip setup and go to the launcher") { a.finish() })
            },
        )

        Step.EMULATOR -> MenuNode(
            id = "ob-emulator",
            title = "Choose your first emulator",
            subtitle = when {
                s.pickedName != null ->
                    "${s.pickedName} is set up via ${s.pickedEmulator}. " +
                        "Next asks where its games are."
                s.detected.isNotEmpty() ->
                    "These are installed on this device, so LH can set each one " +
                        "up with a launch method that is already known to work."
                else ->
                    "No emulators detected. You can still pick any system from " +
                        "the full list and tell LH how to launch it."
            },
            items = buildList {
                // Capped so that Next and Done stay on screen without
                // scrolling: "Done at any time" is worthless if Done is six
                // rows below the fold. The full list is one row further down.
                for ((sys, emu) in s.detected.take(5)) {
                    val chosen = sys.id == s.pickedId
                    add(MenuItem.Action(
                        sys.name,
                        if (chosen) "Selected - via $emu" else "via $emu",
                        enabled = !s.busy,
                    ) { a.pickSystem(sys) })
                }
                add(MenuItem.Action("Show every system",
                    if (s.detected.size > 5)
                        "${s.detected.size - 5} more detected, plus the full catalogue"
                    else "The full catalogue, including consoles with no emulator yet") {
                    a.openCatalogue()
                })
                add(MenuItem.Action("Next",
                    if (s.pickedName != null) "Point LH at ${s.pickedName} games"
                    else "Choose an emulator first",
                    enabled = s.pickedId != null) { a.next() })
                add(MenuItem.Action("Done", "Finish setup here") { a.finish() })
            },
        )

        Step.FOLDER -> MenuNode(
            id = "ob-folder",
            title = if (s.pickedName != null) "Where are your ${s.pickedName} games?"
                    else "Where are your games?",
            subtitle = "Android makes you grant each folder explicitly, and the " +
                "grant cannot be inherited from another app - so this is one " +
                "picker, once. Choose the folder that holds the ROMs.",
            items = buildList {
                add(MenuItem.Action(
                    if (s.pickedHasFolder) "Choose a different folder" else "Choose folder",
                    when {
                        s.pickedGames > 0 -> "${s.pickedGames} game(s) found"
                        s.pickedHasFolder -> "Folder set, but nothing matched in it"
                        else -> "Opens the system folder picker"
                    },
                    enabled = !s.busy,
                ) { a.chooseFolderForPicked() })
                add(MenuItem.Action("Next", "Finish up") { a.next() })
                add(MenuItem.Action("Done", "Finish setup here") { a.finish() })
            },
        )

        Step.DONE -> MenuNode(
            id = "ob-done",
            title = "Ready",
            subtitle = summary(s),
            items = listOf(
                MenuItem.Action("Done", "Open my library") { a.finish() },
                MenuItem.Note("Add more systems in Settings > Add a system, and " +
                    "fetch covers with Settings > Library > Get missing box art."),
                MenuItem.Note("L1 and R1 change system. B opens the app drawer. " +
                    "Y opens Settings."),
            ),
        )
    }

    private fun summary(s: OnboardingState): String = when {
        s.gamesFound > 0 ->
            "${s.gamesFound} game(s) across ${s.systemsAdded} system(s)."
        s.systemsAdded > 0 ->
            "${s.systemsAdded} system(s) added, no games found yet. Check the " +
                "folder in Settings > Consoles."
        else ->
            "Nothing set up yet - that is fine. Everything is in Settings when " +
                "you want it."
    }
}
