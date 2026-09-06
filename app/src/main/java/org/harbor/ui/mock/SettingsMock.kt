package org.harbor.ui.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.settings.SettingsSection
import org.harbor.ui.mock.settings.settingsSections

/**
 * Settings hub: capped at 3 panels by design - a section rail (this file, always present), a
 * section's own content (always present), and at most one further drill-down panel that a
 * section may lay out for itself (see ConsolesSection - the only one that currently does).
 *
 * This file only owns the rail and the top-level dispatch to whichever [SettingsSection] is
 * selected; it has no per-section logic of its own. Adding, removing or reordering a settings
 * section means editing [settingsSections] - this file never changes.
 */
@Composable
internal fun SettingsContent(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit) {
    val selected = settingsSections.firstOrNull { it.id == navState.settingsSectionId } ?: settingsSections.first()
    Row(Modifier.fillMaxSize()) {
        SettingsSidebar(scale, navState)
        selected.Content(scale, navState, onThemeChanged, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SettingsSidebar(scale: Scale, navState: HarborNavState) {
    val theme = LocalTheme.current
    Column(
        Modifier
            .fillMaxHeight()
            .width(scale.dp(180))
            .background(theme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = scale.dp(16), horizontal = scale.dp(10)),
    ) {
        settingsSections.forEach { section ->
            SectionRow(
                section = section,
                selected = navState.settingsSectionId == section.id,
                scale = scale,
                onClick = { navState.selectSettingsSection(section.id) },
            )
            Spacer(Modifier.height(scale.dp(4)))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionRow(section: SettingsSection, selected: Boolean, scale: Scale, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val rowShape = RoundedCornerShape(scale.dp(12))
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .let { if (selected) it.frostedSelection(rowShape) else it },
        shape = ClickableSurfaceDefaults.shape(shape = rowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = theme.accent.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = scale.dp(14), vertical = scale.dp(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                section.icon,
                contentDescription = null,
                tint = if (selected) theme.accent else theme.textSecondary,
                modifier = Modifier.size(scale.dp(18)),
            )
            Spacer(Modifier.width(scale.dp(10)))
            Text(
                section.label,
                color = if (selected) theme.accent else theme.textPrimary,
                fontSize = scale.sp(14),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
