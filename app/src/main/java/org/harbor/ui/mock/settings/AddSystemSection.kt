package org.harbor.ui.mock.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.harbor.HarborApp
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale

object AddSystemSection : SettingsSection {
    override val id = "add_system"
    override val label = "Add a System"
    override val icon: ImageVector = Icons.Filled.Add

    @Composable
    override fun Content(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit, onImportTheme: () -> Unit, modifier: Modifier) {
        val app = HarborApp.instance
        val addedIds = remember { app.profiles.declared().map { it.id }.toSet() }
        val systems = remember { app.catalogue.load().systems }
        var justAdded by remember { mutableStateOf(setOf<String>()) }

        Column(modifier) {
            PanelHeading(label, "Consoles with no emulator yet are listed too", scale)
            LazyColumn(contentPadding = PaddingValues(start = scale.dp(32), end = scale.dp(32), bottom = scale.dp(96))) {
                items(systems) { system ->
                    val already = system.id in addedIds || system.id in justAdded
                    val installed = remember(system.id) { app.catalogue.installedFor(system) }
                    ActionRow(
                        icon = Icons.Filled.SportsEsports,
                        label = "${system.name}  ·  ${system.year}",
                        detail = when {
                            already -> "Already added"
                            installed.isNotEmpty() -> "${installed.first().name} is installed"
                            system.emulators.isEmpty() -> "No Android emulator exists yet"
                            else -> "No supported emulator installed"
                        },
                        enabled = !already,
                        scale = scale,
                        onClick = {
                            val profile = app.catalogue.profileFor(system, installed.firstOrNull(), order = 100)
                            app.profiles.save(profile)
                            justAdded = justAdded + system.id
                        },
                    )
                    Spacer(Modifier.height(scale.dp(4)))
                }
            }
        }
    }
}
