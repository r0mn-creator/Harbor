// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.theme.LocalTheme

/**
 * A game's own menu, reached by holding a tile down.
 *
 * A modal overlay rather than a screen in the settings tree: it needs the
 * specific game that was held, not a path string, and it should close the
 * instant its one job is done rather than leaving a trail to back out of.
 *
 * State (which row is highlighted, whether the remove step is showing) lives
 * in the caller, the same way every other pad-driven screen in this app
 * works - a menu that only a touch could move through would leave pad users
 * unable to reach "Remove" at all.
 */
@Composable
fun GameContextMenu(
    title: String,
    items: List<Pair<String, Boolean>>,
    confirming: Boolean,
    cursor: Int,
    onSelect: (Int) -> Unit,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalTheme.current
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = noRipple, indication = null) {
                if (!confirming) onDismiss()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(14.dp))
                .background(theme.surface)
                // Swallows the tap so pressing inside the card does not fall
                // through to the scrim behind it and dismiss the whole thing.
                .clickable(interactionSource = noRipple, indication = null) {}
                .padding(20.dp)
        ) {
            if (!confirming) {
                Text(title, color = theme.textPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer14()
                items.forEachIndexed { i, (label, danger) ->
                    MenuRow(
                        label = label,
                        color = if (danger) theme.error else theme.textPrimary,
                        focused = i == cursor,
                        onClick = { onSelect(i); onActivate() },
                    )
                }
            } else {
                Text("Remove \"$title\"?", color = theme.textPrimary, fontSize = 18.sp,
                    fontWeight = FontWeight.Bold)
                Spacer8()
                Text(
                    "The file stays on the card - only the shelf entry and its " +
                        "artwork are removed. There is no undo screen for this yet.",
                    color = theme.textSecondary, fontSize = 13.sp,
                )
                Spacer14()
                // Index 0 = Remove, 1 = Cancel. Cancel is what the cursor opens
                // on, so a stray repeat of the button that opened this step
                // cannot also be the press that deletes something.
                MenuRow("Remove", theme.error, focused = cursor == 0,
                    onClick = { onSelect(0); onActivate() })
                MenuRow("Cancel", theme.textSecondary, focused = cursor == 1,
                    onClick = { onSelect(1); onActivate() })
            }
        }
    }
}

@Composable
private fun Spacer14() = Box(Modifier.height(14.dp))
@Composable
private fun Spacer8() = Box(Modifier.height(8.dp))

@Composable
private fun MenuRow(label: String, color: Color, focused: Boolean, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (focused) theme.primary.copy(alpha = 0.16f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 13.dp),
        ) {
            Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.outline.copy(alpha = 0.3f)))
    }
}
