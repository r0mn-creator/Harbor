package org.harbor.ui.mock.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.harbor.theme.LocalTheme
import org.harbor.ui.mock.Scale

/** Shared building blocks every settings section's panel is made of, so a new section looks like the rest for free. */

@Composable
internal fun PanelHeading(title: String, subtitle: String?, scale: Scale) {
    val theme = LocalTheme.current
    Text(
        title,
        color = theme.textPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = scale.sp(18),
        modifier = Modifier.padding(start = scale.dp(32), top = scale.dp(28), bottom = scale.dp(4)),
    )
    if (subtitle != null) {
        Text(
            subtitle,
            color = theme.textSecondary,
            fontSize = scale.sp(12),
            modifier = Modifier.padding(start = scale.dp(32), bottom = scale.dp(16)),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    detail: String?,
    enabled: Boolean = true,
    scale: Scale,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    val rowShape = RoundedCornerShape(scale.dp(12))
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it },
        shape = ClickableSurfaceDefaults.shape(shape = rowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = theme.surfaceVariant,
            focusedContainerColor = theme.accent.copy(alpha = 0.24f),
            disabledContainerColor = theme.surfaceVariant.copy(alpha = 0.5f),
        ),
        enabled = enabled,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = scale.dp(14), vertical = scale.dp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) theme.textPrimary else theme.textDisabled,
                modifier = Modifier.size(scale.dp(18)),
            )
            Spacer(Modifier.width(scale.dp(12)))
            Column {
                Text(
                    label,
                    color = if (enabled) theme.textPrimary else theme.textDisabled,
                    fontSize = scale.sp(14),
                    fontWeight = FontWeight.Medium,
                )
                if (detail != null) {
                    Text(detail, color = theme.textSecondary, fontSize = scale.sp(11))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ToggleRow(
    label: String,
    detail: String?,
    checked: Boolean,
    enabled: Boolean,
    scale: Scale,
    onToggle: (Boolean) -> Unit,
) {
    val theme = LocalTheme.current
    val rowShape = RoundedCornerShape(scale.dp(12))
    Surface(
        onClick = { if (enabled) onToggle(!checked) },
        modifier = Modifier.fillMaxWidth().let { if (enabled) it.clickable { onToggle(!checked) } else it },
        shape = ClickableSurfaceDefaults.shape(shape = rowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = theme.surfaceVariant,
            focusedContainerColor = theme.accent.copy(alpha = 0.24f),
        ),
        enabled = enabled,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = scale.dp(14), vertical = scale.dp(12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    color = if (enabled) theme.textPrimary else theme.textDisabled,
                    fontSize = scale.sp(13),
                    fontWeight = FontWeight.Medium,
                )
                if (detail != null) {
                    Text(detail, color = theme.textSecondary, fontSize = scale.sp(11))
                }
            }
            Box(
                Modifier
                    .size(width = scale.dp(38), height = scale.dp(22))
                    .clip(RoundedCornerShape(50))
                    .background(if (checked) theme.accent.copy(alpha = 0.8f) else theme.textDisabled.copy(alpha = 0.4f)),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(scale.dp(3))
                        .size(scale.dp(16))
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SmallButton(label: String, scale: Scale, danger: Boolean = false, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Surface(
        onClick = onClick,
        modifier = Modifier.clickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(scale.dp(10))),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (danger) theme.error.copy(alpha = 0.18f) else theme.accent.copy(alpha = 0.18f),
            focusedContainerColor = if (danger) theme.error.copy(alpha = 0.32f) else theme.accent.copy(alpha = 0.32f),
        ),
    ) {
        Text(
            label,
            color = if (danger) theme.error else theme.accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(13),
            modifier = Modifier.padding(horizontal = scale.dp(16), vertical = scale.dp(10)),
        )
    }
}

@Composable
internal fun Swatch(color: Color, scale: Scale) {
    Box(
        Modifier
            .padding(end = scale.dp(4))
            .size(scale.dp(20))
            .clip(CircleShape)
            .background(color),
    )
}
