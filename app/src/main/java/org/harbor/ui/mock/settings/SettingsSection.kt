package org.harbor.ui.mock.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale

/**
 * One entry in the Settings sidebar.
 *
 * Each section is a self-contained module - its icon, label and content all live together in
 * one file - and the sidebar reads from a single ordered list ([settingsSections]). Adding,
 * removing or reordering a section is a one-line change there, never a shared `when` block that
 * every section has to be threaded through.
 *
 * A section that never drills any deeper just fills the [modifier] it's given. One that does
 * (Consoles, into a specific console) lays out its own extra panel internally, keyed off
 * [HarborNavState] - the orchestrator in HarborScaffold.kt never needs to know which sections are
 * one panel and which are two; that's entirely the section's own concern. This is also why
 * Settings can never grow past 3 panels by construction: the sidebar is panel one, a section's
 * [Content] is panel two, and at most one further panel lives inside that same [Content] call.
 */
interface SettingsSection {
    val id: String
    val label: String
    val icon: ImageVector

    @Composable
    fun Content(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit, modifier: Modifier)
}

/** Display order in the sidebar is registration order here - move a line to reorder it. */
val settingsSections: List<SettingsSection> = listOf(
    GeneralSection,
    LibrarySection,
    ConsolesSection,
    AddSystemSection,
    ThemesSection,
    NetworkSection,
    ProblemsSection,
)
