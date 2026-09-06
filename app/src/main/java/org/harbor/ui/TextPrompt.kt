// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.theme.LocalTheme

/**
 * Typing on a handheld, from a controller.
 *
 * The system IME is a touch keyboard - nothing in it answers to a d-pad - so
 * relying on it for these few fields would have been the one place the "works
 * from the sofa, docked to a TV" promise broke. The keyboard here is drawn by
 * us and driven by the same explicit cursor every other screen uses, docked to
 * the bottom of the panel with the field it is filling anchored directly above
 * it, rather than a text box floating somewhere else on screen with the
 * keyboard appearing separately below.
 */
@Composable
fun TextPromptOverlay(
    title: String,
    hint: String?,
    text: String,
    textCursor: Int,
    symbols: Boolean,
    shift: Boolean,
    cursorRow: Int,
    cursorCol: Int,
    onKeyTap: (row: Int, col: Int) -> Unit,
    onCancel: () -> Unit,
) {
    val theme = LocalTheme.current
    val noRipple = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // A plain background() is invisible to touch - without a consumer
            // here, a tap anywhere on the scrim (including over the text
            // preview, which has none of its own) falls straight through to
            // whatever is underneath, e.g. the settings row that opened this.
            .clickable(interactionSource = noRipple, indication = null) {},
    ) {
        // Anchored to the bottom, not centred: this is meant to read as a
        // keyboard panel sliding up from the edge of the screen, the same
        // shape a console's own on-screen keyboard takes, with the field it
        // fills pushed up to sit right above the keys as they appear.
        //
        // The panel itself is only slightly opaque - the game grid or
        // settings list stays faintly visible through the gaps between keys,
        // so typing doesn't feel like it walls off the whole screen. Keys,
        // the text field and every badge paint their own solid background on
        // top of this, so none of THEM go see-through - only the panel does.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(theme.surface.copy(alpha = 0.90f))
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
                Text(title, color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                hint?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = theme.textSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badges sit right on the field they move, rather than in
                    // a caption at the bottom of the whole panel - the same
                    // reason the home screen puts its L1/R1 chips at the edges
                    // of the row they page, not in a legend somewhere else.
                    CursorBumper("L1")
                    Spacer(Modifier.width(8.dp))
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        val at = textCursor.coerceIn(0, text.length)
                        Text(
                            text.substring(0, at),
                            color = theme.textPrimary, fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        // A static caret rather than a blinking one: this is not
                        // a real text field with IME focus, and pretending it is
                        // would invite tapping it expecting the system keyboard.
                        // It sits at the actual cursor, not always at the end.
                        Text("│", color = theme.primary, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text.substring(at),
                            color = theme.textPrimary, fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    CursorBumper("R1")
                }
            }
            KeyboardPanel(
                symbols = symbols,
                shift = shift,
                cursorRow = cursorRow,
                cursorCol = cursorCol,
                onTap = onKeyTap,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                PadHint("B", "Cancel", onCancel)
                Spacer(Modifier.weight(1f))
                Text(
                    "D-pad to move · A to press a key",
                    color = theme.textSecondary, fontSize = 12.sp,
                )
            }
        }
    }
}

/** Decorative, not a touch target - L1/R1 are shoulder buttons, nothing to
 *  tap on screen. Same solid-circle shape as the home screen's system-paging
 *  chips, so the two "these bumpers move something sideways" ideas read the
 *  same way everywhere in the app. */
@Composable
private fun CursorBumper(label: String) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(theme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = theme.background, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * The Android-shelf app picker, pad-navigable.
 *
 * Same explicit-cursor model as every other list, so it is driven with the
 * d-pad and A rather than being the one screen that needs a finger.
 */
@Composable
fun AppPickerScreen(
    apps: List<AppChoice>,
    cursor: Int,
    onSelect: (Int) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val theme = LocalTheme.current
    val state = rememberLazyListState()
    LaunchedEffect(cursor) { state.animateScrollToItem((cursor - 3).coerceAtLeast(0)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 30.dp, end = 30.dp, top = 22.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Choose apps", color = theme.textPrimary, fontSize = 26.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text("${apps.count { it.chosen }} on the shelf",
                color = theme.textSecondary, fontSize = 13.sp)
        }
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 30.dp, end = 30.dp, bottom = 20.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(apps) { i, a ->
                val focused = i == cursor
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (focused) theme.primary.copy(alpha = 0.16f) else Color.Transparent
                        )
                        .then(
                            if (focused) Modifier.border(1.dp, theme.primary.copy(alpha = 0.75f),
                                RoundedCornerShape(10.dp)) else Modifier
                        )
                        .clickable { onSelect(i); onToggle(a.pkg, !a.chosen) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(a.label, color = theme.textPrimary, fontSize = 15.sp)
                        Text(a.pkg, color = theme.textSecondary, fontSize = 11.sp)
                    }
                    if (a.chosen) {
                        Text("on the shelf", color = theme.accent, fontSize = 12.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 30.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadHint("B", "Done", onClose)
            Spacer(Modifier.weight(1f))
            val a = apps.getOrNull(cursor)
            PadHint(
                "A",
                if (a == null) "—" else if (a.chosen) "Remove from shelf" else "Add to shelf",
                { a?.let { onToggle(it.pkg, !it.chosen) } },
                dim = a == null,
            )
        }
    }
}
