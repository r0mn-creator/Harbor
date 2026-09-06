package org.harbor.ui.mock

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.harbor.ui.GamepadNav
import org.harbor.ui.Nav

/** Debug-only host for [HarborScaffold], launched directly via adb for design review. */
class MockHomeActivity : ComponentActivity() {
    private val navState = HarborNavState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        setContent { HarborScaffold(navState) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    private fun immersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---- input: same dispatch-level interception MainActivity uses, so bumpers/triggers/
    // d-pad reach GamepadNav before Compose's own focus system can swallow them. ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val nav = GamepadNav.fromKey(event.keyCode)
            if (nav != null) { handle(nav); return true }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (GamepadNav.fromKey(event.keyCode) != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        GamepadNav.fromTrigger(event)?.let { handle(it); return true }
        GamepadNav.fromMotion(event)?.let { handle(it); return true }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        GamepadNav.fromTrigger(event)?.let { handle(it); return true }
        GamepadNav.fromMotion(event)?.let { handle(it); return true }
        return super.onGenericMotionEvent(event)
    }

    private fun handle(nav: Nav) {
        when (nav) {
            Nav.PREV_TAB -> navState.prevTab()
            Nav.NEXT_TAB -> navState.nextTab()
            Nav.PREV_SYSTEM -> navState.prevConsole()
            Nav.NEXT_SYSTEM -> navState.nextConsole()
            else -> {}
        }
    }
}
