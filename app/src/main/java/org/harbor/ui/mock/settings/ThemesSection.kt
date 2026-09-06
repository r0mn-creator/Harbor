package org.harbor.ui.mock.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.harbor.HarborApp
import org.harbor.theme.ColorTheme
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale

object ThemesSection : SettingsSection {
    override val id = "themes"
    override val label = "Themes"
    override val icon: ImageVector = Icons.Filled.Palette

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    override fun Content(
        scale: Scale,
        navState: HarborNavState,
        onThemeChanged: () -> Unit,
        onImportTheme: () -> Unit,
        modifier: Modifier,
    ) {
        val app = HarborApp.instance
        val context = LocalContext.current
        val loaded = remember { app.colors.load() }
        // Not remembered: this must re-read after every onThemeChanged() so the checkmark moves
        // immediately. This panel stays in composition across that recomposition (only the
        // active theme changes, not the selected section), so a remembered value here would
        // go stale.
        val activeName = app.config.colorTheme ?: HarborApp.DEFAULT_THEME_NAME

        Column(modifier) {
            PanelHeading("Color Theme", "Same theme files as LightHouse - drop a .theme into themes/colors to add more.", scale)
            Column(Modifier.padding(start = scale.dp(32), bottom = scale.dp(16))) {
                ActionRow(
                    icon = Icons.Filled.FileOpen,
                    label = "Add a theme from a file",
                    detail = "Pick a .theme file you wrote - Downloads, a USB stick, anywhere",
                    scale = scale,
                    onClick = onImportTheme,
                )
                Spacer(Modifier.height(scale.dp(8)))
                ActionRow(
                    icon = Icons.Filled.OpenInNew,
                    label = "Open Cove to make a theme",
                    detail = "A live color-wheel editor with a preview of this screen",
                    scale = scale,
                    onClick = { openOtherApp(context, "org.cove") },
                )
                Spacer(Modifier.height(scale.dp(8)))
                ActionRow(
                    icon = Icons.Filled.OpenInNew,
                    label = "Open LightHouse",
                    detail = null,
                    scale = scale,
                    onClick = { openOtherApp(context, "org.lighthouse") },
                )
            }
            LazyColumn(contentPadding = PaddingValues(start = scale.dp(32), end = scale.dp(32), top = scale.dp(4), bottom = scale.dp(96))) {
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
}

/** Launches another one of these companion apps by package - tries the release id, then the
 * ".debug" variant, since these are sideloaded and either could be the one installed. */
private fun openOtherApp(context: Context, releasePackage: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(releasePackage)
        ?: context.packageManager.getLaunchIntentForPackage("$releasePackage.debug")
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Not installed on this device", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
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
