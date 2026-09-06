package org.harbor.ui.mock.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.harbor.HarborApp
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale

object LibrarySection : SettingsSection {
    override val id = "library"
    override val label = "Library"
    override val icon: ImageVector = Icons.Filled.Folder

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    override fun Content(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit, onImportTheme: () -> Unit, modifier: Modifier) {
        val theme = LocalTheme.current
        val app = HarborApp.instance
        var key by rememberSaveable { mutableStateOf(app.config.steamGridDbKey ?: "") }
        var saved by rememberSaveable { mutableStateOf(app.config.steamGridDbKey != null) }
        var replaceAll by rememberSaveable { mutableStateOf(app.config.steamGridDbReplaceAll) }

        Column(modifier.padding(end = scale.dp(32))) {
            PanelHeading(label, null, scale)
            Column(Modifier.padding(start = scale.dp(32))) {
                Text("SteamGridDB key", color = theme.textPrimary, fontSize = scale.sp(14), fontWeight = FontWeight.SemiBold)
                Text(
                    if (saved) "Set · used for systems libretro has little art for, like Xbox 360"
                    else "Not set · needed for newer systems - Xbox 360, PS3, Switch",
                    color = theme.textSecondary,
                    fontSize = scale.sp(12),
                    modifier = Modifier.padding(bottom = scale.dp(8)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.textPrimary, fontSize = scale.sp(13)),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(scale.dp(10)))
                            .background(theme.surfaceVariant)
                            .padding(horizontal = scale.dp(12), vertical = scale.dp(10)),
                    )
                    Spacer(Modifier.width(scale.dp(10)))
                    SmallButton("Save", scale) {
                        app.config.steamGridDbKey = key
                        saved = app.config.steamGridDbKey != null
                    }
                }
                Text(
                    "Free at steamgriddb.com - sign in, then Preferences > API > Generate API key.",
                    color = theme.textSecondary,
                    fontSize = scale.sp(11),
                    modifier = Modifier.padding(top = scale.dp(8), bottom = scale.dp(20)),
                )
                ToggleRow(
                    label = "Replace all art with SteamGridDB",
                    detail = "For one consistent look, instead of only filling in what libretro misses",
                    checked = replaceAll,
                    enabled = saved,
                    scale = scale,
                ) {
                    replaceAll = it
                    app.config.steamGridDbReplaceAll = it
                }
                Text(
                    "Import, rescan and cleanup aren't wired into this new Settings yet - use the classic menu for those in the meantime.",
                    color = theme.textSecondary,
                    fontSize = scale.sp(11),
                    modifier = Modifier.padding(top = scale.dp(20)),
                )
            }
        }
    }
}
