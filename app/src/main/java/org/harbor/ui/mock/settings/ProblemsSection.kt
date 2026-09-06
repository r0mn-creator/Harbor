package org.harbor.ui.mock.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.harbor.HarborApp
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale

object ProblemsSection : SettingsSection {
    override val id = "problems"
    override val label = "Problems"
    override val icon: ImageVector = Icons.Filled.Warning

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    override fun Content(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit, modifier: Modifier) {
        val theme = LocalTheme.current
        val app = HarborApp.instance
        val problems = remember { app.profiles.load().problems }

        Column(modifier) {
            PanelHeading(label, "Profiles that could not be loaded or validated", scale)
            if (problems.isEmpty()) {
                Text(
                    "Nothing to report.",
                    color = theme.textSecondary,
                    fontSize = scale.sp(13),
                    modifier = Modifier.padding(start = scale.dp(32)),
                )
            } else {
                Column(Modifier.padding(start = scale.dp(32))) {
                    problems.forEach { (which, why) ->
                        Text(which, color = theme.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = scale.sp(13))
                        Text(why, color = theme.textSecondary, fontSize = scale.sp(12), modifier = Modifier.padding(bottom = scale.dp(12)))
                    }
                }
            }
        }
    }
}
