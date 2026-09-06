package org.harbor.ui.mock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class HarborTab { HOME, LIBRARY, SETTINGS }

enum class SettingsCategory(val label: String) {
    GENERAL("General"), NETWORK("Network"), THEMES("Themes")
}

/**
 * Where the mockup currently is: which persistent tab, which console within Library, and which
 * settings category is showing in the right-hand detail panel.
 */
class HarborNavState {
    var tab by mutableStateOf(HarborTab.HOME)
        private set
    var consoleIndex by mutableIntStateOf(0)
        private set
    var settingsCategory by mutableStateOf(SettingsCategory.GENERAL)
        private set

    fun selectTab(target: HarborTab) {
        tab = target
    }

    fun nextTab() {
        val values = HarborTab.entries
        tab = values[(tab.ordinal + 1) % values.size]
    }

    fun prevTab() {
        val values = HarborTab.entries
        tab = values[(tab.ordinal - 1 + values.size) % values.size]
    }

    fun selectConsole(index: Int) {
        consoleIndex = index
    }

    fun nextConsole() {
        consoleIndex = (consoleIndex + 1) % mockConsoles.size
    }

    fun prevConsole() {
        consoleIndex = (consoleIndex - 1 + mockConsoles.size) % mockConsoles.size
    }

    fun selectSettingsCategory(category: SettingsCategory) {
        settingsCategory = category
    }
}
