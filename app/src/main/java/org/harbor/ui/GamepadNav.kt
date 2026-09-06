// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Gamepad navigation, console-launcher style.
 *
 * A handheld launcher is driven with a pad, not a touchscreen, so navigation is
 * an explicit focus index rather than Compose's focus system: it gives exact
 * control over wrapping, row jumps and keeping the selection centred, which is
 * what makes this feel like a console UI instead of a scrollable list.
 */
enum class Nav { LEFT, RIGHT, UP, DOWN, PREV_SYSTEM, NEXT_SYSTEM, LAUNCH, BACK, MENU, SEARCH }

object GamepadNav {

    fun fromKey(code: Int): Nav? = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> Nav.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Nav.RIGHT
        KeyEvent.KEYCODE_DPAD_UP -> Nav.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Nav.DOWN
        // Bumpers page through systems - the single most-used control here.
        KeyEvent.KEYCODE_BUTTON_L1 -> Nav.PREV_SYSTEM
        KeyEvent.KEYCODE_BUTTON_R1 -> Nav.NEXT_SYSTEM
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> Nav.LAUNCH
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> Nav.BACK
        KeyEvent.KEYCODE_BUTTON_Y -> Nav.MENU
        KeyEvent.KEYCODE_BUTTON_X -> Nav.SEARCH
        else -> null
    }

    private const val DEADZONE = 0.5f
    /** Stops one flick of the stick scrolling half the library. */
    private const val REPEAT_MS = 160L

    private var lastStickMs = 0L
    private var stickCentred = true

    /**
     * Left stick -> the same four directions.
     *
     * An analogue axis fires continuously, so without both a re-centre check and
     * a repeat interval a single push races through the whole grid.
     */
    fun fromMotion(event: MotionEvent): Nav? {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return null
        if (event.action != MotionEvent.ACTION_MOVE) return null

        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val mag = maxOf(kotlin.math.abs(x), kotlin.math.abs(y))

        if (mag < DEADZONE) {
            stickCentred = true
            return null
        }
        val now = System.currentTimeMillis()
        if (!stickCentred && now - lastStickMs < REPEAT_MS) return null
        stickCentred = false
        lastStickMs = now

        return if (kotlin.math.abs(x) > kotlin.math.abs(y)) {
            if (x < 0) Nav.LEFT else Nav.RIGHT
        } else {
            if (y < 0) Nav.UP else Nav.DOWN
        }
    }
}

/**
 * Where the selection is. Kept as plain indices so movement rules (wrap at row
 * ends, clamp at the grid edges) are explicit and testable.
 */
data class GridCursor(val index: Int = 0, val columns: Int = 5) {

    fun move(nav: Nav, count: Int): GridCursor {
        if (count == 0) return copy(index = 0)
        val i = index.coerceIn(0, count - 1)
        val next = when (nav) {
            Nav.LEFT -> if (i % columns == 0) i else i - 1
            Nav.RIGHT -> if (i % columns == columns - 1 || i == count - 1) i else i + 1
            Nav.UP -> if (i < columns) i else i - columns
            Nav.DOWN -> if (i + columns >= count) i else i + columns
            else -> i
        }
        return copy(index = next.coerceIn(0, count - 1))
    }

    val row: Int get() = index / columns
}
