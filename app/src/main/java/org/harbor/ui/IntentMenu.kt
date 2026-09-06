// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import org.harbor.data.ExtraSpec
import org.harbor.data.LaunchSpec

/**
 * The launch-intent editor expressed as a menu.
 *
 * It began as a bespoke form, which meant it was the one screen a gamepad could
 * not drive. Rebuilding it out of the same MenuItem vocabulary as everything
 * else gives it cursor navigation, hints that name the action, and touch, for
 * free - and there is now exactly one navigation model in the app.
 *
 * Text entry is the one thing a pad cannot do directly, so those rows open a
 * prompt rather than being inline fields.
 */
object IntentMenu {

    interface Actions {
        fun pickPackage()
        fun promptText(field: TextField, current: String)
        fun cycleActivity()
        fun cycleAction()
        fun cycleRomMode()
        fun toggleFlag(flag: String)
        fun addExtra()
        fun editExtra(index: Int, part: ExtraPart)
        fun cycleExtraType(index: Int)
        fun removeExtra(index: Int)
        fun test()
        fun save()
    }

    enum class TextField { ACTION, MIME, ROM_EXTRA, EXTRA_KEY, EXTRA_VALUE }
    enum class ExtraPart { KEY, VALUE }

    val FLAGS = listOf(
        "GRANT_READ_URI_PERMISSION",
        "NEW_TASK",
        "CLEAR_TOP",
        "CLEAR_TASK",
        "SINGLE_TOP",
        "NO_ANIMATION",
    )

    val ACTIONS = listOf(
        "android.intent.action.VIEW",
        "android.intent.action.MAIN",
        "android.intent.action.SEND",
    )

    val ROM_MODES = listOf(LaunchSpec.ROM_DATA_URI, LaunchSpec.ROM_EXTRA, LaunchSpec.ROM_NONE)

    fun romModeLabel(mode: String): String = when (mode) {
        LaunchSpec.ROM_DATA_URI -> "Data URI"
        LaunchSpec.ROM_EXTRA -> "Named extra"
        LaunchSpec.ROM_NONE -> "None (id only)"
        else -> mode
    }

    private fun romModeDetail(mode: String): String = when (mode) {
        LaunchSpec.ROM_DATA_URI -> "The game's content:// URI goes in the data slot"
        LaunchSpec.ROM_EXTRA -> "The URI goes in a named extra instead"
        LaunchSpec.ROM_NONE -> "Nothing file-based; identified by extras (e.g. a Steam id)"
        else -> ""
    }

    fun node(
        platformName: String,
        spec: LaunchSpec,
        verified: Boolean,
        testGame: String?,
        preview: String,
        a: Actions,
    ): MenuNode {
        val pkg = spec.component?.substringBefore('/').orEmpty()
        val cls = spec.component?.substringAfter('/', "").orEmpty()

        return MenuNode(
            id = "intent",
            title = "Launch intent",
            subtitle = if (verified) "$platformName — verified on this device"
                       else "$platformName — not yet proven, test it",
            items = buildList {
                add(MenuItem.Action("Emulator app",
                    pkg.ifBlank { "not set" }) { a.pickPackage() })
                add(MenuItem.Choice("Activity",
                    "Blank lets Android pick whichever one handles the game",
                    value = cls.ifBlank { "auto" }) { a.cycleActivity() })

                add(MenuItem.Note("Intent"))
                add(MenuItem.Choice("Action", null, value = shortAction(spec.action)) {
                    a.cycleAction()
                })
                add(MenuItem.Action("Action (type it)", spec.action) {
                    a.promptText(TextField.ACTION, spec.action)
                })
                add(MenuItem.Action("MIME type", spec.type ?: "none") {
                    a.promptText(TextField.MIME, spec.type.orEmpty())
                })

                add(MenuItem.Note("How the game is passed"))
                add(MenuItem.Choice(romModeLabel(spec.romMode), romModeDetail(spec.romMode),
                    value = "change") { a.cycleRomMode() })
                if (spec.romMode == LaunchSpec.ROM_EXTRA) {
                    add(MenuItem.Action("Extra that receives it", spec.romExtra ?: "not set") {
                        a.promptText(TextField.ROM_EXTRA, spec.romExtra.orEmpty())
                    })
                }

                add(MenuItem.Note("Flags",
                    "GRANT_READ_URI_PERMISSION is on by default because without it an " +
                        "emulator can start, look alive, and never load the game"))
                FLAGS.forEach { f ->
                    add(MenuItem.Toggle(f, null, true, f in spec.flags) { a.toggleFlag(f) })
                }

                add(MenuItem.Note("Extras", "{file}, {id} and {title} are filled in at launch"))
                spec.extras.forEachIndexed { i, e ->
                    add(MenuItem.Action("${e.key} = ${e.value}", "type: ${e.type}") {
                        a.editExtra(i, ExtraPart.VALUE)
                    })
                    add(MenuItem.Action("   ↳ rename key", e.key) {
                        a.editExtra(i, ExtraPart.KEY)
                    })
                    add(MenuItem.Choice("   ↳ type", null, value = e.type) {
                        a.cycleExtraType(i)
                    })
                    add(MenuItem.Action("   ↳ remove", null, true, danger = true) {
                        a.removeExtra(i)
                    })
                }
                add(MenuItem.Action("Add an extra", null) { a.addExtra() })

                add(MenuItem.Note("Test"))
                if (testGame != null) {
                    add(MenuItem.Action("Test launch", "Runs \"$testGame\" with these settings") {
                        a.test()
                    })
                } else {
                    add(MenuItem.Note("No game to test with — set this console's folder first."))
                }
                add(MenuItem.Action("Save", "Saving clears the verified mark until retested") {
                    a.save()
                })

                add(MenuItem.Note("Resulting intent", preview))
            },
        )
    }

    private fun shortAction(a: String) = a.removePrefix("android.intent.action.")

    fun cycled(list: List<String>, current: String): String {
        val i = list.indexOf(current)
        return list[(i + 1) % list.size]
    }

    /** Apply a cycled activity, keeping the package intact. */
    fun withActivity(spec: LaunchSpec, activities: List<String>): LaunchSpec {
        val pkg = spec.component?.substringBefore('/').orEmpty()
        if (pkg.isBlank()) return spec
        val cls = spec.component?.substringAfter('/', "").orEmpty()
        // "" means let Android choose, and it is deliberately part of the cycle:
        // for most emulators it is the RIGHT answer.
        val options = listOf("") + activities
        val next = options[(options.indexOf(cls).coerceAtLeast(0) + 1) % options.size]
        return spec.copy(component = if (next.isBlank()) pkg else "$pkg/$next")
    }

    fun withExtraAt(spec: LaunchSpec, i: Int, f: (ExtraSpec) -> ExtraSpec): LaunchSpec {
        if (i !in spec.extras.indices) return spec
        return spec.copy(extras = spec.extras.toMutableList().also { it[i] = f(it[i]) })
    }
}
