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
    cancelLabel: String = "Cancel",
    /**
     * Paints the confirm option as destructive. False for a plain either/or
     * question, where neither answer is a loss and red would just be alarming.
     */
    danger: Boolean = true,
    /** true when B must not dismiss — the question has to be answered. */
    mustAnswer: Boolean = false,
    /** 0 = confirm, 1 = cancel. Driven by the d-pad; the pad is the primary input. */
    cursor: Int,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            // Tapping the scrim is a touch-only shortcut for "no". Disabled when the
            // question must be answered, so a stray tap cannot skip it.
            .then(if (mustAnswer) Modifier else Modifier.clickable(onClick = onCancel)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.62f)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surface)
                // Swallow taps on the card so they do not reach the scrim and cancel.
                .clickable(enabled = false) {}
                .padding(32.dp)
        ) {
            // Sized for a sofa, not a desk: this is read from across a room.
            Text(title, color = theme.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = theme.textSecondary, fontSize = 16.sp)
            Spacer(Modifier.height(26.dp))
            Row {
                ChoicePill(confirmLabel, focused = cursor == 0, danger = danger,
                    onClick = { onSelect(0); onConfirm() })
                Spacer(Modifier.width(14.dp))
                ChoicePill(cancelLabel, focused = cursor == 1, danger = false,
                    onClick = { onSelect(1); onCancel() })
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (mustAnswer) "D-pad to choose  ·  A to answer"
                else "D-pad to choose  ·  A to confirm  ·  B to cancel",
                color = theme.textSecondary, fontSize = 13.sp,
            )
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

/**
 * Focus has to be obvious from the sofa, so the focused option is a filled
 * block rather than a slightly brighter outline — at three metres a 1dp border
 * change is invisible. Unfocused options stay quiet so there is only ever one
 * thing that looks live.
 */
@Composable
private fun ChoicePill(label: String, focused: Boolean, danger: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    val tint = if (danger) DangerRed else theme.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (focused) tint else tint.copy(alpha = 0.10f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) tint else tint.copy(alpha = 0.45f),
                shape = RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            // On the filled state the label sits on the tint, so it takes the
            // dialog's own background colour to stay legible on any theme.
            color = if (focused) theme.surface else tint,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
