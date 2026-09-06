package org.harbor.ui.mock.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.harbor.HarborApp
import org.harbor.data.PlatformProfile
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale
import org.harbor.ui.mock.frostedSelection

/**
 * The one section that actually uses Settings' third panel: the list is panel two, and tapping a
 * console opens its detail as panel three. Every other section only ever fills the [modifier] it
 * gets; this is the only place in the codebase a settings section splits it further.
 */
object ConsolesSection : SettingsSection {
    override val id = "consoles"
    override val label = "Consoles"
    override val icon: ImageVector = Icons.Filled.SportsEsports

    @Composable
    override fun Content(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit, modifier: Modifier) {
        Row(modifier) {
            ConsolesList(scale, navState, Modifier.weight(1f).fillMaxHeight())
            if (navState.settingsConsoleDetailId != null) {
                ConsoleDetail(scale, navState, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConsolesList(scale: Scale, navState: HarborNavState, modifier: Modifier) {
    val theme = LocalTheme.current
    val app = HarborApp.instance
    val profiles = remember { app.profiles.load().profiles }

    Column(modifier) {
        PanelHeading("Consoles", "Open one to see its status", scale)
        if (profiles.isEmpty()) {
            Text(
                "No consoles configured yet.",
                color = theme.textSecondary,
                fontSize = scale.sp(13),
                modifier = Modifier.padding(start = scale.dp(32)),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(start = scale.dp(32), end = scale.dp(32), bottom = scale.dp(96))) {
                items(profiles) { p ->
                    ConsoleRow(
                        profile = p,
                        selected = navState.settingsConsoleDetailId == p.id,
                        scale = scale,
                        onClick = { navState.selectSettingsConsoleDetail(p.id) },
                    )
                    Spacer(Modifier.height(scale.dp(8)))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConsoleRow(profile: PlatformProfile, selected: Boolean, scale: Scale, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val rowShape = RoundedCornerShape(scale.dp(12))
    val needsFolder = profile.source.provider == "folder" && profile.source.roots.isEmpty()
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .let { if (selected) it.frostedSelection(rowShape) else it },
        shape = ClickableSurfaceDefaults.shape(shape = rowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.Transparent else theme.surfaceVariant,
            focusedContainerColor = theme.accent.copy(alpha = 0.28f),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = scale.dp(16), vertical = scale.dp(12))) {
            Text(profile.name, color = theme.textPrimary, fontSize = scale.sp(14), fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    needsFolder -> "Needs folder access"
                    profile.verified -> "Launch verified"
                    else -> "Launch not yet verified"
                },
                color = theme.textSecondary,
                fontSize = scale.sp(12),
            )
        }
    }
}

@Composable
private fun ConsoleDetail(scale: Scale, navState: HarborNavState, modifier: Modifier) {
    val theme = LocalTheme.current
    val app = HarborApp.instance
    val id = navState.settingsConsoleDetailId
    var profile by remember(id) { mutableStateOf(app.profiles.load().profiles.firstOrNull { it.id == id }) }
    val p = profile

    Column(modifier.padding(end = scale.dp(32))) {
        if (p == null) {
            PanelHeading("Console", null, scale)
            Text("Not found.", color = theme.textSecondary, fontSize = scale.sp(13), modifier = Modifier.padding(start = scale.dp(32)))
            return@Column
        }
        PanelHeading(p.name, if (p.verified) "Launch verified on this device" else "Launch not yet proven", scale)
        Column(Modifier.padding(start = scale.dp(32))) {
            ToggleRow(
                label = "Show on home",
                detail = null,
                checked = p.enabled,
                enabled = true,
                scale = scale,
            ) { checked ->
                val updated = p.copy(enabled = checked)
                app.profiles.save(updated)
                profile = updated
            }
            Spacer(Modifier.height(scale.dp(16)))
            Text(
                "Folder and emulator editing aren't in this new Settings yet - use the classic menu for those in the meantime.",
                color = theme.textSecondary,
                fontSize = scale.sp(11),
                modifier = Modifier.padding(bottom = scale.dp(20)),
            )
            SmallButton("Remove console", scale, danger = true) {
                app.profiles.delete(p.id)
                navState.selectSettingsConsoleDetail(null)
            }
        }
    }
}
