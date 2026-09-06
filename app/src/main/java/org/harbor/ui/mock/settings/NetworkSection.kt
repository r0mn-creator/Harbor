package org.harbor.ui.mock.settings

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.HarborNavState
import org.harbor.ui.mock.Scale

object NetworkSection : SettingsSection {
    override val id = "network"
    override val label = "Network"
    override val icon: ImageVector = Icons.Filled.Wifi

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    override fun Content(scale: Scale, navState: HarborNavState, onThemeChanged: () -> Unit, modifier: Modifier) {
        val theme = LocalTheme.current
        val context = LocalContext.current
        val status = remember { networkStatus(context) }

        Column(modifier) {
            PanelHeading(label, null, scale)
            Text(
                status,
                color = theme.textSecondary,
                fontSize = scale.sp(13),
                modifier = Modifier.padding(start = scale.dp(32), bottom = scale.dp(16)),
            )
            Box(Modifier.padding(start = scale.dp(32))) {
                ActionRow(
                    icon = Icons.Filled.Wifi,
                    label = "Wi-Fi networks",
                    detail = "Opens the network list directly, without leaving Harbor",
                    scale = scale,
                    onClick = { openWifiPicker(context) },
                )
            }
        }
    }

    private fun networkStatus(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return "Not connected"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected · Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected · Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Connected · Ethernet"
            else -> "Connected"
        }
    }

    /** The floating quick-settings panel when available (no full screen change), the real Wi-Fi settings screen otherwise. */
    private fun openWifiPicker(context: Context) {
        val panel = Intent(Settings.Panel.ACTION_WIFI)
        val resolved = runCatching { context.startActivity(panel) }
        if (resolved.isFailure) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }
}
