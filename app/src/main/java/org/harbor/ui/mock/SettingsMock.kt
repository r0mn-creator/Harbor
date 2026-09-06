package org.harbor.ui.mock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/** Placeholder - not part of this pass, just proves the tab bar stays put when the tab changes. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SettingsContent(scale: Scale) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Settings", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold, fontSize = scale.sp(20))
    }
}
