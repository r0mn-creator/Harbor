// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.harbor.HarborApp
import org.harbor.theme.ResolvedTheme

/**
 * The guide button, console-style: press it inside a game and get a way back out.
 *
 * This cannot live in Harbor's activity. While a game is foreground the game
 * receives its own input, so the only supported way to see the button is an
 * accessibility service that filters key events globally.
 *
 * Deliberately small for now. It asks one question and does one thing.
 */
class GuideService : AccessibilityService() {

    private var overlay: View? = null
    /** 0 = Yes, 1 = No. Opens on No so a second guide press cannot exit by accident. */
    private var cursor = 1

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            // Never consumed. Plenty of games bind this button themselves, so a tap
            // has to reach them untouched — only a deliberate hold is ours. The cost
            // is that the game also sees the hold, which is the same press it would
            // have seen anyway.
            when (event.action) {
                KeyEvent.ACTION_DOWN ->
                    if (event.repeatCount == 0 && overlay == null) armHold()
                KeyEvent.ACTION_UP -> cancelHold()
            }
            return false
        }

        // While the dialog is up the overlay itself has focus and handles its own
        // keys, so nothing else needs intercepting here.
        return false
    }

    /**
     * Drives the dialog. Used for both real keys and the d-pad, which most pads
     * report as a hat *axis* rather than DPAD keycodes — an accessibility service
     * only ever sees KeyEvents, which is why the overlay takes focus instead of
     * being driven from onKeyEvent.
     */
    private fun onNav(nav: Nav): Boolean {
        when (nav) {
            Nav.LEFT, Nav.UP -> { cursor = 0; render() }
            Nav.RIGHT, Nav.DOWN -> { cursor = 1; render() }
            Nav.LAUNCH -> {
                val goHome = cursor == 0
                hideOverlay()
                if (goHome) exitToHome()
            }
            Nav.BACK -> hideOverlay()
            else -> return false
        }
        return true
    }

    /** Long enough that it cannot be mistaken for a game's own use of the button. */
    private val holdHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val showOnHold = Runnable { if (overlay == null) showOverlay() }

    private fun armHold() {
        holdHandler.removeCallbacks(showOnHold)
        holdHandler.postDelayed(showOnHold, HOLD_MS)
    }

    private fun cancelHold() = holdHandler.removeCallbacks(showOnHold)

    private fun exitToHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // ---- overlay ------------------------------------------------------------

    private fun showOverlay() {
        cursor = 1
        val theme = HarborApp.instance.activeColors()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = buildView(theme)
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // Focusable on purpose. It has to receive the d-pad, which most pads
            // send as a hat axis (a MotionEvent) that onKeyEvent never sees. Taking
            // focus for the life of the dialog is also the correct behaviour: the
            // game must not act on the presses steering this.
            0,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, _, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            GamepadNav.fromKey(e.keyCode)?.let { onNav(it) } ?: false
        }
        view.setOnGenericMotionListener { _, e ->
            GamepadNav.fromMotion(e)?.let { onNav(it) } ?: false
        }
        runCatching { wm.addView(view, lp) }
            .onSuccess { overlay = view; view.requestFocus() }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        overlay = null
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        runCatching { wm.removeView(v) }
    }

    private fun render() {
        val v = overlay ?: return
        val theme = HarborApp.instance.activeColors()
        (v.findViewWithTag<TextView>(TAG_YES))?.let { style(it, theme, focused = cursor == 0) }
        (v.findViewWithTag<TextView>(TAG_NO))?.let { style(it, theme, focused = cursor == 1) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildView(theme: ResolvedTheme): View {
        val scrim = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(AndroidColor.argb(180, 0, 0, 0))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(32), dp(32), dp(32))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(theme.surface.toArgb())
            }
        }
        card.addView(TextView(this).apply {
            text = "Exit to the home screen?"
            // Sized for a sofa, same as the launcher's own dialogs.
            textSize = 24f
            setTextColor(theme.textPrimary.toArgb())
        })
        card.addView(TextView(this).apply {
            text = "The game keeps running in the background."
            textSize = 15f
            setTextColor(theme.textSecondary.toArgb())
            setPadding(0, dp(10), 0, dp(24))
        })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(pill("Yes", TAG_YES, theme, focused = false))
        row.addView(View(this), LinearLayout.LayoutParams(dp(14), 1))
        row.addView(pill("No", TAG_NO, theme, focused = true))
        card.addView(row)
        card.addView(TextView(this).apply {
            text = "D-pad to choose  ·  A to confirm  ·  B to close"
            textSize = 13f
            setTextColor(theme.textSecondary.toArgb())
            setPadding(0, dp(20), 0, 0)
        })
        scrim.addView(card)
        return scrim
    }

    private fun pill(label: String, tag: String, theme: ResolvedTheme, focused: Boolean) =
        TextView(this).apply {
            text = label
            this.tag = tag
            textSize = 17f
            setPadding(dp(26), dp(12), dp(26), dp(12))
            style(this, theme, focused)
        }

    /** Focused option is a filled block — an outline change is invisible across a room. */
    private fun style(t: TextView, theme: ResolvedTheme, focused: Boolean) {
        val tint = theme.primary
        t.background = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(if (focused) tint.toArgb() else tint.copy(alpha = 0.10f).toArgb())
            setStroke(dp(if (focused) 2 else 1), if (focused) tint.toArgb()
            else tint.copy(alpha = 0.45f).toArgb())
        }
        t.setTextColor(if (focused) theme.surface.toArgb() else tint.toArgb())
    }

    override fun onDestroy() {
        cancelHold()
        hideOverlay()
        super.onDestroy()
    }

    private companion object {
        const val TAG_YES = "guide_yes"
        const val TAG_NO = "guide_no"
        const val HOLD_MS = 3000L
    }
}

private fun Color.copy(alpha: Float) = Color(red, green, blue, alpha)
