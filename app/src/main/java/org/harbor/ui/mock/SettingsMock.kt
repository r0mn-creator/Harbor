package org.harbor.ui.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.harbor.HarborApp
import org.harbor.theme.ColorTheme
import org.harbor.theme.LocalTheme

/**
 * Settings hub: a category rail on the left, the chosen category's panel on the right - not a
 * flat list with "Color Theme" bolted straight onto the tab. Themes is one category among
 * several, same shape as a real settings app (General/Network/Themes/...).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SettingsContent(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit) {
    val theme = LocalTheme.current
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(scale.dp(180))
                .background(theme.surfaceVariant.copy(alpha = 0.4f))
                .padding(vertical = scale.dp(16), horizontal = scale.dp(10)),
        ) {
            SettingsCategory.entries.forEach { category ->
                CategoryRow(
                    category = category,
                    selected = navState.settingsCategory == category,
                    scale = scale,
                    onClick = { navState.selectSettingsCategory(category) },
                )
                Spacer(Modifier.height(scale.dp(4)))
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (navState.settingsCategory) {
                SettingsCategory.GENERAL -> GeneralPanel(scale)
                SettingsCategory.NETWORK -> NetworkPanel(scale)
                SettingsCategory.THEMES -> ThemesPanel(scale, onThemeChanged)
            }
        }
    }
}

private fun SettingsCategory.icon(): ImageVector = when (this) {
    SettingsCategory.GENERAL -> Icons.Filled.Tune
    SettingsCategory.NETWORK -> Icons.Filled.Wifi
    SettingsCategory.THEMES -> Icons.Filled.Palette
}

@Composable
private fun CategoryRow(category: SettingsCategory, selected: Boolean, scale: Scale, onClick: () -> Unit) {
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
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = theme.accent.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = scale.dp(14), vertical = scale.dp(10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                category.icon(),
                contentDescription = null,
                tint = if (selected) theme.accent else theme.textSecondary,
                modifier = Modifier.size(scale.dp(18)),
            )
            Spacer(Modifier.width(scale.dp(10)))
            Text(
                category.label,
                color = if (selected) theme.accent else theme.textPrimary,
                fontSize = scale.sp(14),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun GeneralPanel(scale: Scale) {
    val theme = LocalTheme.current
    Column(Modifier.fillMaxSize().padding(start = scale.dp(32), top = scale.dp(28))) {
        Text(
            "General",
            color = theme.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(18),
            modifier = Modifier.padding(bottom = scale.dp(4)),
        )
        Text(
            "Harbor 0.1.0",
            color = theme.textSecondary,
            fontSize = scale.sp(13),
        )
    }
}

@Composable
private fun NetworkPanel(scale: Scale) {
    val theme = LocalTheme.current
    Column(Modifier.fillMaxSize().padding(start = scale.dp(32), top = scale.dp(28))) {
        Text(
            "Network",
            color = theme.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(18),
            modifier = Modifier.padding(bottom = scale.dp(4)),
        )
        Text(
            "Nothing here yet.",
            color = theme.textSecondary,
            fontSize = scale.sp(13),
        )
    }
}

/**
 * The real theme picker: reads the same ColorThemeStore LightHouse's classic UI uses (the
 * themes/colors folder, one .theme file per entry, "--main/--secondary/--accent/--highlight"
 * format), so any .theme file that works there works here unchanged. Picking one writes
 * harbor.conf and calls [onThemeChanged] so HarborScaffold re-resolves LocalTheme immediately.
 */
@Composable
private fun ThemesPanel(scale: Scale, onThemeChanged: () -> Unit) {
    val theme = LocalTheme.current
    val app = HarborApp.instance
    val loaded = remember { app.colors.load() }
    // Not remembered: this must re-read after every onThemeChanged() so the checkmark moves
    // immediately. This panel stays in composition across that recomposition (only the active
    // theme changes, not the selected category), so a remembered value here would go stale.
    val activeName = app.config.colorTheme ?: HarborApp.DEFAULT_THEME_NAME

    Column(Modifier.fillMaxSize()) {
        Text(
            "Color Theme",
            color = theme.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(18),
            modifier = Modifier.padding(start = scale.dp(32), top = scale.dp(28), bottom = scale.dp(4)),
        )
        Text(
            "Same theme files as LightHouse - drop a .theme into themes/colors to add more.",
            color = theme.textSecondary,
            fontSize = scale.sp(12),
            modifier = Modifier.padding(start = scale.dp(32), bottom = scale.dp(16)),
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = scale.dp(32), vertical = scale.dp(4))) {
            items(loaded.themes) { colorTheme ->
                ThemeRow(
                    colorTheme = colorTheme,
                    active = colorTheme.name == activeName,
                    scale = scale,
                    onClick = {
                        app.config.colorTheme = colorTheme.name.takeIf { it != HarborApp.DEFAULT_THEME_NAME }
                        onThemeChanged()
                    },
                )
                Spacer(Modifier.height(scale.dp(8)))
            }
        }
    }
}

@Composable
private fun ThemeRow(colorTheme: ColorTheme, active: Boolean, scale: Scale, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val rowShape = RoundedCornerShape(scale.dp(14))
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = rowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) theme.accent.copy(alpha = 0.14f) else theme.surfaceVariant,
            focusedContainerColor = theme.accent.copy(alpha = 0.28f),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = scale.dp(16), vertical = scale.dp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Swatch(colorTheme.main, scale)
                Swatch(colorTheme.secondary, scale)
                Swatch(colorTheme.accent, scale)
                Swatch(colorTheme.highlight, scale)
            }
            Spacer(Modifier.width(scale.dp(14)))
            Text(
                colorTheme.name,
                color = theme.textPrimary,
                fontSize = scale.sp(14),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (active) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Active",
                    tint = theme.accent,
                    modifier = Modifier.size(scale.dp(18)),
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: androidx.compose.ui.graphics.Color, scale: Scale) {
    Box(
        Modifier
            .padding(end = scale.dp(4))
            .size(scale.dp(20))
            .clip(CircleShape)
            .background(color),
    )
}
