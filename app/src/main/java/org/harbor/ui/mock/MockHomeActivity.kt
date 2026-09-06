package org.harbor.ui.mock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.harbor.HarborApp
import org.harbor.theme.LocalTheme
import org.harbor.ui.GamepadNav
import org.harbor.ui.Nav

/** Debug-only host for [HarborScaffold], launched directly via adb for design review. */
class MockHomeActivity : ComponentActivity() {
    private val navState = HarborNavState()
    private var colorEpoch by mutableIntStateOf(0)

    /** Same picker MainActivity's classic menu already uses - any file, since ".theme" has no registered mime type. */
    private val themePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri: Uri = res.data?.data ?: return@registerForActivityResult
        val name = queryDisplayName(uri) ?: "Imported"
        val text = runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            Toast.makeText(this, "Could not read that file", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        HarborApp.instance.colors.importFrom(name, text)
            .onSuccess {
                Toast.makeText(this, "Added the \"$it\" theme", Toast.LENGTH_SHORT).show()
                colorEpoch++
            }
            .onFailure {
                Toast.makeText(this, "Not a theme file: ${it.message ?: "could not be read"}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    }.getOrNull()

    private fun importColorTheme() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        runCatching { themePicker.launch(i) }
            .onFailure { Toast.makeText(this, "No file picker available on this device", Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        immersive()
        setContent {
            // Same pattern as MainActivity: re-read on every colorEpoch bump so picking a
            // theme in Settings applies immediately, not after a restart.
            val theme = remember(colorEpoch) { HarborApp.instance.activeColors() }
            CompositionLocalProvider(LocalTheme provides theme) {
                HarborScaffold(navState, onThemeChanged = { colorEpoch++ }, onImportTheme = ::importColorTheme)
            }
        }
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
