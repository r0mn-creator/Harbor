// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import org.harbor.data.CatalogueSystem

/**
 * Builds the Settings menu as nested screens.
 *
 * Rebuilt from live state on every navigation, so a row always shows the truth
 * rather than a snapshot taken when the screen opened. Screens are addressed by
 * a path of ids, which keeps the navigation stack a plain list of strings.
 */
class MenuTree(
    private val state: SettingsState,
    private val actions: MenuActions,
) {
    /** Everything the menu can do. Kept in one place so the tree stays declarative. */
    interface MenuActions {
        fun import()
        fun setupFolders()
        fun rescan()
        fun cleanupLibrary()
        fun scrapeCovers()
        fun editSteamGridDbKey()
        fun setSteamGridDbReplaceAll(v: Boolean)
        fun chooseFolder(platformId: String)
        fun chooseApps(platformId: String)
        fun setEmulator(platformId: String, pkg: String)
        fun editIntent(platformId: String)
        fun cycleAspect(platformId: String)
        fun setEnabled(platformId: String, enabled: Boolean)
        fun remove(platformId: String)
        fun addSystem(system: CatalogueSystem)
        fun pickColorTheme(name: String?)
        fun removeColorTheme(name: String)
        fun openAccessibilitySettings()
        fun importColorTheme()
        fun openColorFolder()
        fun openOtherApp(releasePackage: String)
    }

    fun nodeFor(path: List<String>): MenuNode = when {
        path.isEmpty() -> consoles()
        path[0] == "about" -> about()
        path[0] == "library" && path.size == 1 -> library()
        path[0] == "library" -> cleanup()
        path[0] == "consoles" && path.size == 1 -> consoles()
        path[0] == "consoles" && path.size > 2 && path[2] == "emulator" -> emulators(path[1])
        path[0] == "consoles" -> platform(path[1])
        path[0] == "add" -> addSystem()
        path[0] == "themes" && path.size == 1 -> themes()
        path[0] == "themes" -> colors()
        path[0] == "appearance" -> themes()
        path[0] == "problems" -> problems()
        else -> root()
    }

    private fun about() = MenuNode(
        id = "about", title = "About",
        items = listOf(
            MenuItem.Action(
                "Guide button",
                "Hold the guide button for 3s in a game to get an exit prompt. " +
                    "Needs turning on in Android's accessibility settings.",
            ) { actions.openAccessibilitySettings() },
            MenuItem.Note("Harbor 0.1.0"),
            MenuItem.Note("${state.gamesTotal} games", "${state.gamesPlayable} playable"),
            MenuItem.Note("${state.platforms.size} consoles",
                "${state.platforms.count { it.verified }} with a verified launch"),
            MenuItem.Note("Launch profiles and themes are plain JSON files in the app's " +
                "files directory. Edit or share them freely."),
        ),
    )

    private fun root() = MenuNode(
        id = "root",
        title = "Settings",
        subtitle = "${state.gamesPlayable} of ${state.gamesTotal} games playable",
        items = buildList {
            add(MenuItem.Submenu("library", "Library",
                "Import, folders and rescanning"))
            add(MenuItem.Submenu("consoles", "Consoles",
                "${state.platforms.size} added",
                badge = state.platforms.count { !it.verified }
                    .takeIf { it > 0 }?.let { "$it untested" }))
            add(MenuItem.Submenu("add", "Add a system",
                "Every console from 2000 onward"))
            add(MenuItem.Submenu("appearance", "Appearance",
                "Colours and fonts"))
            if (state.problems.isNotEmpty()) {
                add(MenuItem.Submenu("problems", "Problems",
                    "${state.problems.size} profile(s) need attention"))
            }
        },
    )

    private fun library() = MenuNode(
        id = "library", title = "Library",
        items = listOf(
            MenuItem.Action("Import from Beacon",
                "Reads an export from /sdcard/HarborImport/beacon") { actions.import() },
            MenuItem.Action("Set up folders",
                "Walks every system that still needs access") { actions.setupFolders() },
            MenuItem.Action("Rescan now",
                "Re-read every folder") { actions.rescan() },
            MenuItem.Action(
                if (state.steamGridDbReplaceAll) "Replace all box art"
                else "Get missing box art",
                when {
                    state.steamGridDbReplaceAll && state.steamGridDbKey == null ->
                        "Add a SteamGridDB key below first"
                    state.steamGridDbReplaceAll ->
                        "Re-fetches every cover from SteamGridDB, even ones you already have"
                    state.missingArt == 0 -> "Every game already has a cover"
                    state.steamGridDbKey != null ->
                        "${state.missingArt} game(s) have none · tries libretro, then SteamGridDB"
                    else -> "${state.missingArt} game(s) have none · fetches from libretro"
                },
                enabled = state.missingArt > 0 ||
                    (state.steamGridDbReplaceAll && state.steamGridDbKey != null),
            ) { actions.scrapeCovers() },
            MenuItem.Action("SteamGridDB key",
                if (state.steamGridDbKey != null)
                    "Set · used for systems libretro has little art for, like Xbox 360"
                else "Not set · needed for newer systems - Xbox 360, PS3, Switch") {
                actions.editSteamGridDbKey()
            },
            MenuItem.Toggle(
                "Replace all art with SteamGridDB",
                "For one consistent look, instead of only filling in what libretro misses",
                enabled = state.steamGridDbKey != null,
                on = state.steamGridDbReplaceAll,
            ) { actions.setSteamGridDbReplaceAll(it) },
            MenuItem.Note("Free at steamgriddb.com - sign in, then Preferences › API › " +
                "Generate API key. Only used as a fallback, and only for the titles " +
                "libretro has nothing for, unless \"Replace all\" above is on."),
            MenuItem.Submenu("cleanup", "Forget missing games",
                if (state.cleanupPlan.total == 0)
                    "Nothing to forget — every record has a game"
                else "${state.cleanupPlan.total} record(s) have no file · review first"),
            MenuItem.Note("Only run that when every folder is connected — a game " +
                "on an unplugged card looks missing, and its artwork would go too."),
        ),
    )

    /** The removal list, by name, before anything is removed. */
    private fun cleanup(): MenuNode {
        val plan = state.cleanupPlan
        return MenuNode(
            id = "cleanup", title = "Forget missing games",
            subtitle = if (plan.total == 0) "Every record still has a game on disk"
                       else "${plan.total} record(s) have no file. Check the list before removing.",
            items = buildList {
                for (g in plan.removals) {
                    add(MenuItem.Note("${g.platform} — ${g.titles.size}",
                        g.titles.joinToString(", ")))
                }
                for ((name, why) in plan.skipped) {
                    add(MenuItem.Note("$name — left alone", why))
                }
                if (plan.total > 0) {
                    add(MenuItem.Action(
                        "Forget these ${plan.total}",
                        "Their artwork is deleted too. Re-import brings them back.",
                        danger = true,
                    ) { actions.cleanupLibrary() })
                }
            },
        )
    }

    /** Pick the app that runs a system, without touching the intent editor. */
    private fun emulators(id: String): MenuNode {
        val p = state.platforms.firstOrNull { it.id == id }
            ?: return MenuNode(id, "Emulator", items = listOf(MenuItem.Note("Not found.")))
        return MenuNode(
            id = "emulator",
            title = "Emulator for ${p.name}",
            subtitle = "Choosing one fills in a launch method known to work with it.",
            items = buildList {
                for (e in p.emulators) {
                    add(MenuItem.Action(
                        e.name,
                        when {
                            e.inUse && !e.installed -> "In use — but not installed"
                            e.inUse -> "In use"
                            !e.installed -> "Not installed"
                            e.verified -> "Installed · launch confirmed on a device"
                            else -> "Installed · launch untested"
                        },
                        enabled = e.installed && !e.inUse,
                    ) { actions.setEmulator(id, e.pkg) })
                }
                if (p.emulators.none { it.installed }) {
                    add(MenuItem.Note("None of these are installed. Install one, or " +
                        "set the launch up by hand with Launch intent."))
                }
                add(MenuItem.Action("Set it up by hand",
                    "Open the launch intent editor") { actions.editIntent(id) })
            },
        )
    }

    private fun consoles() = MenuNode(
        id = "consoles", title = "Consoles",
        subtitle = "Open one to change its emulator, folder or artwork",
        items = state.platforms.map { p ->
            MenuItem.Submenu(
                id = p.id,
                label = p.name,
                detail = when {
                    p.problem != null -> p.problem
                    p.isAppShelf -> "${p.games} apps"
                    p.needsFolder -> "${p.games} games · needs folder access"
                    else -> "${p.playable} of ${p.games} playable"
                },
                badge = if (!p.verified) "untested" else null,
            )
        },
    )

    private fun platform(id: String): MenuNode {
        val p = state.platforms.firstOrNull { it.id == id }
            ?: return MenuNode(id, "Console", items = listOf(MenuItem.Note("Not found.")))
        return MenuNode(
            id = id,
            title = p.name,
            subtitle = if (p.verified) "Launch verified on this device"
                       else "Launch not yet proven — test it",
            items = buildList {
                if (p.isAppShelf) {
                    add(MenuItem.Action("Choose apps",
                        "${p.games} on the shelf") { actions.chooseApps(p.id) })
                } else {
                    add(MenuItem.Action(
                        if (p.needsFolder) "Choose folder" else "Change folder",
                        if (p.needsFolder) "No readable folder yet" else "${p.games} games found",
                    ) { actions.chooseFolder(id) })
                }
                add(MenuItem.Submenu("emulator", "Emulator",
                    p.currentEmulator ?: "Not set — pick the app that runs this system"))
                add(MenuItem.Action("Launch intent",
                    "How a game is handed to the emulator") { actions.editIntent(id) })
                add(MenuItem.Choice("Box art shape",
                    "Tap to cycle through real case shapes",
                    value = p.aspectRatio) { actions.cycleAspect(id) })
                add(MenuItem.Toggle("Show on home", null, true, p.enabled) {
                    actions.setEnabled(id, it)
                })
                add(MenuItem.Note("Removing keeps imported games and artwork, " +
                    "so adding the system back does not mean importing again."))
                add(MenuItem.Action("Remove console", null, true, danger = true) {
                    actions.remove(id)
                })
            },
        )
    }

    private fun addSystem() = MenuNode(
        id = "add", title = "Add a system",
        subtitle = "Consoles with no emulator yet are listed too",
        items = buildList {
            add(MenuItem.Note("When an emulator appears for one of these, adding it is a " +
                "settings change rather than an app update."))
            state.catalogue.forEach { c ->
                add(MenuItem.Action(
                    label = "${c.system.name}  ·  ${c.system.year}",
                    detail = when {
                        c.alreadyAdded -> "Already added"
                        c.installedEmulator != null -> "${c.installedEmulator} is installed"
                        c.system.emulators.isEmpty() -> "No Android emulator exists yet"
                        else -> "No supported emulator installed"
                    },
                    enabled = !c.alreadyAdded,
                ) { actions.addSystem(c.system) })
            }
        },
    )

    private fun themes() = MenuNode(
        id = "themes", title = "Themes",
        items = listOf(
            MenuItem.Submenu("colors", "Colours",
                state.activeColorTheme,
                badge = state.colorProblems.size.takeIf { it > 0 }?.let { "$it broken" }),
            MenuItem.Note("Layout themes — changing where things sit on screen, not just " +
                "their colour — are planned for a later version."),
        ),
    )

    private fun colors() = MenuNode(
        id = "colors", title = "Colours",
        subtitle = "One file per theme in themes/colors — the file name is the theme name",
        items = buildList {
            state.colorThemes.forEach { name ->
                val isDefault = name == org.harbor.theme.ColorThemes.DEFAULT_NAME
                add(MenuItem.Action(
                    label = name,
                    // isDefault is checked FIRST: the built-in has no file to delete,
                    // so offering "hold to remove" on it would be a lie even when it
                    // is also the active theme.
                    detail = when {
                        isDefault -> if (name == state.activeColorTheme) "Active" else null
                        name == state.activeColorTheme -> "Active  ·  hold A to remove"
                        else -> "Hold A to remove"
                    },
                    // The built-in default has no file behind it, so there is
                    // nothing to delete and no long-press offered.
                    onLongPress = if (isDefault) null else ({ actions.removeColorTheme(name) }),
                ) { actions.pickColorTheme(name) })
            }
            add(MenuItem.Action("Add a theme from a file",
                "Pick a .theme file you wrote - Downloads, a USB stick, anywhere") {
                actions.importColorTheme()
            })
            add(MenuItem.Action("Where these live", "Folder path, for adb or a rooted file manager") {
                actions.openColorFolder()
            })
            add(MenuItem.Action("Open Cove to make a theme",
                "A live color-wheel editor with a preview of this screen") {
                actions.openOtherApp("org.cove")
            })
            add(MenuItem.Action("Open LightHouse", null) {
                actions.openOtherApp("org.lighthouse")
            })
            state.colorProblems.forEach { (f, why) ->
                add(MenuItem.Note(f, why))
            }
            add(MenuItem.Note("A theme is four lines: --main, --secondary, --accent and " +
                "--highlight. Everything else is derived, and text contrast follows --main."))
        },
    )

    private fun problems() = MenuNode(
        id = "problems", title = "Problems",
        subtitle = "Profiles that could not be loaded or validated",
        items = state.problems.map { (which, why) ->
            MenuItem.Note(which, why)
        }.ifEmpty { listOf(MenuItem.Note("Nothing to report.")) },
    )
}
