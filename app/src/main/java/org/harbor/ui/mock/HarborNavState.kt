package org.harbor.ui.mock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.harbor.ui.mock.settings.settingsSections

enum class HarborTab { HOME, LIBRARY, SETTINGS }

/**
 * Where the mockup currently is: which persistent tab, which console within Library's mock data,
 * which settings section is selected (by id - see [settingsSections], the registry a section is
 * added to or removed from), and - only for sections that drill one level deeper, currently just
 * Consoles - which real profile id is open in the third settings panel.
 *
 * Settings is capped at 3 panels by design: the section rail (always) -> a section's own content
 * (always) -> one further drill-down (only for a section that actually has one). Most sections
 * never populate the third panel at all.
 */
class HarborNavState {
    var tab by mutableStateOf(HarborTab.HOME)
        private set
    var consoleIndex by mutableIntStateOf(0)
        private set
    var settingsSectionId by mutableStateOf(settingsSections.first().id)
        private set
    var settingsConsoleDetailId by mutableStateOf<String?>(null)
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

    fun selectSettingsSection(id: String) {
        settingsSectionId = id
        settingsConsoleDetailId = null
    }

    fun selectSettingsConsoleDetail(id: String?) {
        settingsConsoleDetailId = id
    }
}
