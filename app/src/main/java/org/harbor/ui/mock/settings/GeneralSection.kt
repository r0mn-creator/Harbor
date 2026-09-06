package org.harbor.ui.mock.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.harbor.HarborApp
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale

object GeneralSection : SettingsSection {
    override val id = "general"
    override val label = "General"
    override val icon: ImageVector = Icons.Filled.Tune

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    override fun Content(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit, onImportTheme: () -> Unit, modifier: Modifier) {
        val theme = LocalTheme.current
        val app = HarborApp.instance
        val profileCount = remember { app.profiles.load().profiles.size }
        Column(modifier) {
            PanelHeading(label, null, scale)
            Text(
                "Harbor 0.1.0",
                color = theme.textSecondary,
                fontSize = scale.sp(13),
                modifier = Modifier.padding(start = scale.dp(32)),
            )
            Text(
                "$profileCount console(s) configured",
                color = theme.textSecondary,
                fontSize = scale.sp(13),
                modifier = Modifier.padding(start = scale.dp(32), top = scale.dp(4)),
            )
        }
    }
}
