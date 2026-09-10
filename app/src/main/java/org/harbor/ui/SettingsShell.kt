// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harbor.theme.LocalTheme

/**
 * Settings as a two-pane console screen: a category rail on the left, its
 * content on the right.
 *
 * Landscape handheld, so both panes fit at once and most changes are two
 * button presses rather than a walk down a stack. Drilling into one console
 * still slides the right pane, which keeps the "went deeper" cue.
 */

enum class Pane { RAIL, CONTENT }

data class SettingsCategory(val id: String, val label: String)

val SETTINGS_CATEGORIES = listOf(
    SettingsCategory("consoles", "Consoles"),
    SettingsCategory("library", "Library"),
    SettingsCategory("add", "Add a system"),
    SettingsCategory("themes", "Themes"),
    SettingsCategory("problems", "Problems"),
    SettingsCategory("about", "About"),
)

@Composable
fun SettingsShell(
    categories: List<SettingsCategory>,
    categoryIndex: Int,
    node: MenuNode,
    contentCursor: Int,
    pane: Pane,
    /** true when we drilled into a detail, false when we came back. */
    forward: Boolean,
    subtitle: String?,
    onPickCategory: (Int) -> Unit,
    onFocusContent: () -> Unit,
    onSelectContent: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalTheme.current

    Column(
        Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", color = theme.textPrimary, fontSize = 25.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            subtitle?.let { Text(it, color = theme.textSecondary, fontSize = 13.sp) }
        }

        Row(Modifier.weight(1f)) {
            CategoryRail(
                categories, categoryIndex,
                focused = pane == Pane.RAIL,
                onPick = onPickCategory,
                onEnter = onFocusContent,
            )
            Spacer(Modifier.width(18.dp))
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = node.id,
                    transitionSpec = {
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(200)) { w -> dir * w / 4 } + fadeIn(tween(160)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { w -> -dir * w / 4 } +
                                    fadeOut(tween(120))
                            )
                    },
                    label = "settings-content",
                ) { _ ->
                    ContentPane(node, contentCursor, pane == Pane.CONTENT,
                        onSelectContent, onActivate)
                }
            }
        }

        ShellHints(node, contentCursor, pane, onBack) { onActivate(contentCursor) }
    }
}

@Composable
private fun CategoryRail(
    categories: List<SettingsCategory>,
    index: Int,
    focused: Boolean,
    onPick: (Int) -> Unit,
    onEnter: () -> Unit,
) {
    val theme = LocalTheme.current
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .padding(start = 26.dp)
    ) {
        categories.forEachIndexed { i, c ->
            val on = i == index
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            on && focused -> theme.primary.copy(alpha = 0.20f)
                            on -> theme.textPrimary.copy(alpha = 0.08f)
                            else -> Color.Transparent
                        }
                    )
                    .then(
                        if (on && focused) Modifier.border(1.dp, theme.primary.copy(alpha = 0.75f),
                            RoundedCornerShape(12.dp)) else Modifier
                    )
                    .clickable { onPick(i); onEnter() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A selection bar rather than an icon: no emoji, and it reads
                // clearly at a glance on a handheld.
                Box(
                    Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (on) theme.primary else Color.Transparent)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    c.label,
                    color = if (on) theme.textPrimary else theme.textSecondary,
                    fontSize = 16.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ContentPane(
    node: MenuNode,
    cursor: Int,
    focused: Boolean,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
) {
    val theme = LocalTheme.current
    val state = rememberLazyListState()

    LaunchedEffect(cursor, node.id) {
        state.animateScrollToItem((cursor - 2).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize().padding(end = 26.dp)) {
        node.subtitle?.let {
            Text(it, color = theme.textSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(node.items) { i, item ->
                ContentRow(
                    item = item,
                    focused = focused && i == cursor,
                    onClick = { if (item.enabled) { onSelect(i); onActivate(i) } },
                    onLongPress = ((item as? MenuItem.Action)?.onLongPress)?.let { act ->
                        { if (item.enabled) { onSelect(i); act() } }
                    },
                )
            }
        }
    }
}

@Composable
private fun ContentRow(
    item: MenuItem,
    focused: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val theme = LocalTheme.current

    if (item is MenuItem.Note) {
        Text(
            item.detail?.let { "${item.label} — $it" } ?: item.label,
            color = theme.textSecondary, fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )
        return
    }

    val danger = (item as? MenuItem.Action)?.danger == true
    // Cards rather than flat rows - easier to scan at arm's length.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) theme.primary.copy(alpha = 0.16f) else theme.surface)
            .then(
                if (focused) Modifier.border(1.dp, theme.primary.copy(alpha = 0.8f),
                    RoundedCornerShape(12.dp)) else Modifier
            )
            .then(
                if (onLongPress != null)
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongPress)
                else Modifier.clickable(onClick = onClick)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                color = when {
                    danger -> theme.error
                    !item.enabled -> theme.textDisabled
                    else -> theme.textPrimary
                },
                fontSize = 16.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            )
            item.detail?.let {
                Text(it, color = theme.textSecondary, fontSize = 12.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
        }
        when (item) {
            is MenuItem.Submenu -> {
                item.badge?.let {
                    Text(it, color = theme.secondary, fontSize = 12.sp)
                    Spacer(Modifier.width(12.dp))
                }
                Text("›", color = theme.textSecondary, fontSize = 22.sp)
            }
            is MenuItem.Toggle -> ShellSwitch(item.on)
            is MenuItem.Choice -> Text(item.value, color = theme.primary, fontSize = 15.sp,
                fontWeight = FontWeight.Medium)
            else -> Unit
        }
    }
}

@Composable
private fun ShellSwitch(on: Boolean) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) theme.primary.copy(alpha = 0.85f) else theme.surfaceVariant),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier.padding(3.dp).size(18.dp).clip(CircleShape)
                .background(if (on) theme.background else theme.textSecondary)
        )
    }
}

@Composable
private fun ShellHints(
    node: MenuNode,
    cursor: Int,
    pane: Pane,
    onBack: () -> Unit,
    onActivate: () -> Unit,
) {
    val theme = LocalTheme.current
    val item = node.items.getOrNull(cursor)
    val aLabel = when {
        pane == Pane.RAIL -> "Open"
        item is MenuItem.Submenu -> "Open"
        item is MenuItem.Toggle -> if (item.on) "Turn off" else "Turn on"
        item is MenuItem.Choice -> "Change"
        item is MenuItem.Action -> item.label
        else -> "—"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 26.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PadHint("B", if (pane == Pane.CONTENT) "Back" else "Close", onBack)
        Spacer(Modifier.width(20.dp))
        Text(
            if (pane == Pane.RAIL) "D-pad up/down to pick a section"
            else "D-pad to move  ·  left to go back",
            color = theme.textSecondary, fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        PadHint("A", aLabel, onActivate,
            dim = pane == Pane.CONTENT && item?.enabled != true)
    }
}
