// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.theme.LocalTheme

/**
 * "Are you sure?" for something that cannot be undone.
 *
 * Deliberately asymmetric: the destructive choice is drawn in the error colour
 * and named after what it actually does ("Remove"), while cancelling is the
 * plain, easy option. Tapping the scrim behind it also cancels, so the safe
 * outcome is always the one that takes least effort.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(14.dp))
                .background(theme.surface)
                // Swallow taps on the card so they do not reach the scrim and cancel.
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Text(title, color = theme.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = theme.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Row {
                DangerPill(confirmLabel, onConfirm)
                Spacer(Modifier.width(12.dp))
                PlainPill("Cancel", onCancel)
            }
        }
    }
}

/**
 * Deliberately NOT `theme.error`.
 *
 * `ColorThemes.resolve` falls error back to the theme's `--highlight` unless a
 * theme sets an explicit `--error` override, and the built-in Default's highlight
 * is green — which would paint "Remove" in the colour that everywhere else means
 * go. A confirmation for something irreversible is a safety affordance, not a
 * styling opportunity, so it stays red whatever the theme says. Same reasoning as
 * the glass chrome that stays neutral for legibility.
 */
private val DangerRed = Color(0xFFFF5C5C)

@Composable
private fun DangerPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(DangerRed.copy(alpha = 0.15f))
            .border(1.dp, DangerRed.copy(alpha = 0.8f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlainPill(label: String, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, theme.primary.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = theme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
