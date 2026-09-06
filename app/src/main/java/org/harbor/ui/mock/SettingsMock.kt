package org.harbor.ui.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.harbor.HarborApp
import org.harbor.theme.ColorTheme
import org.harbor.theme.ColorThemes
import org.harbor.theme.LocalTheme

/**
 * Real theme picker, not a placeholder: reads the same ColorThemeStore LightHouse's classic UI
 * uses (the themes/colors folder, one .theme file per entry, "--main/--secondary/--accent/
 * --highlight" format), so any .theme file that works there works here unchanged. Picking one
 * writes harbor.conf and calls [onThemeChanged] so HarborScaffold re-resolves LocalTheme
 * immediately.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SettingsContent(scale: Scale, onThemeChanged: () -> Unit) {
    val theme = LocalTheme.current
    val app = HarborApp.instance
    val loaded = remember { app.colors.load() }
    // Not remembered: this must re-read after every onThemeChanged() so the checkmark moves
    // immediately. SettingsContent stays in composition across that recomposition (only the
    // active theme changes, not the selected tab), so a remembered value here would go stale.
    val activeName = app.config.colorTheme ?: ColorThemes.DEFAULT_NAME

    Column(Modifier.fillMaxSize()) {
        Text(
            "Color Theme",
            color = theme.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(18),
            modifier = Modifier.padding(start = scale.dp(40), top = scale.dp(28), bottom = scale.dp(4)),
        )
        Text(
            "Same theme files as LightHouse - drop a .theme into themes/colors to add more.",
            color = theme.textSecondary,
            fontSize = scale.sp(12),
            modifier = Modifier.padding(start = scale.dp(40), bottom = scale.dp(16)),
        )
        LazyColumn(contentPadding = PaddingValues(horizontal = scale.dp(40), vertical = scale.dp(4))) {
            items(loaded.themes) { colorTheme ->
                ThemeRow(
                    colorTheme = colorTheme,
                    active = colorTheme.name == activeName,
                    scale = scale,
                    onClick = {
                        app.config.colorTheme = colorTheme.name.takeIf { it != ColorThemes.DEFAULT_NAME }
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
