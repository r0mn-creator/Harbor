// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.harbor.core.DisplayGame
import org.harbor.data.PlatformProfile
import org.harbor.theme.LocalTheme
import java.io.File

/** Launcher icon for an Android-section entry, or null for everything else. */
@Composable
private fun DisplayGame.androidIcon(): androidx.compose.ui.graphics.ImageBitmap? {
    val pkg = entry?.id ?: return null
    if (entry?.uri != null) return null          // file-backed, not an app
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(pkg) {
        runCatching {
            val d = ctx.packageManager.getApplicationIcon(pkg)
            val bmp = android.graphics.Bitmap.createBitmap(
                d.intrinsicWidth.coerceAtLeast(1),
                d.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            val c = android.graphics.Canvas(bmp)
            d.setBounds(0, 0, c.width, c.height)
            d.draw(c)
            bmp.asImageBitmap()
        }.getOrNull()
    }
}

/** One system page: its profile, its games, and anything the user must know. */
data class SystemPage(
    val profile: PlatformProfile,
    val games: List<DisplayGame>,
    val notes: List<String> = emptyList(),
    /** Set when the profile itself is broken; the page shows the reason. */
    val problem: String? = null,
) {
    /** Curated from installed apps rather than scanned from a folder. */
    val isAppShelf: Boolean
        get() = profile.source.provider == org.harbor.data.SourceSpec.INSTALLED_APPS
}

private const val COLUMNS = 5

/**
 * "3:4" -> 0.75 (width / height). Systems genuinely differ: 3:4 discs, 1:1
 * squares, 8:7 DS/3DS, 3:5 tall PSP/Switch, 2:3 Android posters. Rendering
 * every one at the same ratio crops or stretches most of them.
 */
private fun aspectOf(spec: String): Float {
    val parts = spec.split(":")
    val w = parts.getOrNull(0)?.toFloatOrNull() ?: 3f
    val h = parts.getOrNull(1)?.toFloatOrNull() ?: 4f
    return if (h <= 0f) 0.75f else (w / h).coerceIn(0.4f, 2.0f)
}

@Composable
fun HomeScreen(
    pages: List<SystemPage>,
    systemIndex: Int,
    cursor: GridCursor,
    onChooseFolder: (String) -> Unit,
    onImport: () -> Unit,
    onLaunch: (SystemPage, DisplayGame) -> Unit,
    // Every on-screen control is also a touch target. The hints are not a
    // legend for the pad - they are buttons that happen to show which pad
    // button does the same thing.
    onSelect: (Int) -> Unit,
    onPrevSystem: () -> Unit,
    onNextSystem: () -> Unit,
    /** Held down on a tile, or X on a pad - opens that game's own menu (rename, art, remove). */
    onLongPress: (SystemPage, DisplayGame) -> Unit = { _, _ -> },
    /** Background work, shown quietly rather than blocking the screen. */
    artStatus: String? = null,
    onPickSystem: (Int) -> Unit,
    onSettings: () -> Unit,
    onApps: () -> Unit,
) {
    val theme = LocalTheme.current
    val page = pages.getOrNull(systemIndex)
    val selected = page?.games?.getOrNull(cursor.index)

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.background)
    ) {
        // Ambient wash behind the grid, derived from the selected cover so the
        // page feels like it belongs to the game rather than to the launcher.
        AmbientGlow(selected)

        Column(Modifier.fillMaxSize()) {
            SystemTabs(pages, systemIndex, onPrevSystem, onNextSystem, onPickSystem)

            // Horizontal swipe pages systems, same as the bumpers. The grid
            // scrolls vertically, so a horizontal drag cannot be meant for it.
            val swipePx = with(LocalDensity.current) { 72.dp.toPx() }
            Box(
                Modifier
                    .weight(1f)
                    .pointerInput(pages.size, systemIndex) {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onDragEnd = {
                                // Swipe left = go forward, matching every
                                // paged UI on the platform.
                                if (dx <= -swipePx) onNextSystem()
                                else if (dx >= swipePx) onPrevSystem()
                            },
                        ) { change, amount ->
                            dx += amount
                            change.consume()
                        }
                    }
            ) {
                when {
                    page == null -> CentreMessage("No systems yet. Press Y to add one.")
                    page.problem != null -> ProblemPane(page, onChooseFolder)
                    page.games.isEmpty() -> ProblemPane(page, onChooseFolder)
                    else -> {
                        val anyPlayable = page.games.any { it.playable }
                        CoverGrid(
                            page, cursor, perTileBadges = anyPlayable,
                            onSelect = onSelect,
                            onLaunch = { g -> onLaunch(page, g) },
                            onLongPress = { g -> onLongPress(page, g) },
                        )
                    }
                }
            }

            page?.let { p ->
                if (p.games.isNotEmpty() && p.games.none { it.playable }) {
                    // Only a folder-backed shelf gets a prompt here. Which apps
                    // appear on an app shelf is decided in Settings, where the
                    // system was added - the home screen is for playing.
                    if (!p.isAppShelf) {
                        SystemNotice(
                            p.games.size.toString() +
                                " games imported — choose this system's folder to play them"
                        ) { onChooseFolder(p.profile.id) }
                    }
                }
            }
            BottomBar(
                selected = selected,
                isAppShelf = page?.isAppShelf == true,
                onImport = onImport,
                onApps = onApps,
                onSettings = onSettings,
                onPlay = { if (page != null && selected != null) onLaunch(page, selected) },
                onMenu = { if (page != null && selected != null) onLongPress(page, selected) },
                artStatus = artStatus,
            )
        }
    }
}

@Composable
private fun AmbientGlow(selected: DisplayGame?) {
    val theme = LocalTheme.current
    // Cheap and stable: tint from the theme accent rather than decoding the
    // bitmap on every cursor move, which would stutter the grid.
    val tint = theme.primary
    val alpha by animateFloatAsState(
        targetValue = if (selected != null) 0.07f else 0.03f,
        animationSpec = tween(400),
        label = "glow",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = alpha), Color.Transparent),
                    center = Offset(260f, 620f),
                    radius = 1100f,
                )
            )
    )
}

@Composable
private fun SystemTabs(
    pages: List<SystemPage>,
    systemIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val theme = LocalTheme.current
    // Everything that marks SELECTION on this screen keys off theme.primary,
    // never theme.textPrimary. Text resolves to near-white for every dark
    // theme, so a highlight drawn from it looks identical in all of them - the
    // home screen appeared not to be themed at all, while Settings clearly was.
    val tabState = androidx.compose.foundation.lazy.rememberLazyListState()
    // With 15 systems the row overflows; without this the selected tab can sit
    // off-screen or behind the R1 chip.
    LaunchedEffect(systemIndex) {
        if (pages.isNotEmpty()) {
            tabState.animateScrollToItem(systemIndex.coerceAtMost(pages.size - 1))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BumperChip("L1", onPrev)
        Spacer(Modifier.width(12.dp))

        LazyRow(
            modifier = Modifier.weight(1f),
            state = tabState,
            contentPadding = PaddingValues(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(pages) { p ->
                val i = pages.indexOf(p)
                val on = i == systemIndex
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) theme.primary.copy(alpha = 0.28f) else Color.Transparent)
                        .clickable { onPick(i) }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        p.profile.shortName.uppercase(),
                        color = if (on) theme.textPrimary else theme.textSecondary,
                        fontSize = 17.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        BumperChip("R1", onNext)
    }
}

@Composable
private fun BumperChip(label: String, onClick: () -> Unit) {
    val theme = LocalTheme.current
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(theme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = theme.background, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CoverGrid(
    page: SystemPage,
    cursor: GridCursor,
    perTileBadges: Boolean,
    onSelect: (Int) -> Unit,
    onLaunch: (DisplayGame) -> Unit,
    onLongPress: (DisplayGame) -> Unit = {},
) {
    val state = rememberLazyGridState()

    // Keep the selected row on screen as the cursor moves.
    LaunchedEffect(cursor.index) {
        val row = cursor.row
        val first = state.firstVisibleItemIndex / COLUMNS
        val visibleRows = 2
        if (row < first) state.animateScrollToItem(row * COLUMNS)
        else if (row >= first + visibleRows) {
            state.animateScrollToItem(((row - visibleRows + 1) * COLUMNS).coerceAtLeast(0))
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        state = state,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val aspect = aspectOf(page.profile.aspectRatio)
        itemsIndexed(page.games) { i, g ->
            CoverTile(
                g, focused = i == cursor.index, showBadge = perTileBadges,
                aspect = aspect,
                onLongPress = { onLongPress(g) },
            ) {
                // First tap selects, a second tap on the selected tile plays.
                // A single tap launching makes a mis-tap boot a game, and it
                // leaves touch users with no way to build the selection that
                // the "A Play" button acts on.
                if (i == cursor.index) onLaunch(g) else onSelect(i)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CoverTile(
    game: DisplayGame,
    focused: Boolean,
    showBadge: Boolean,
    aspect: Float,
    onLongPress: () -> Unit = {},
    onLaunch: () -> Unit,
) {
    val theme = LocalTheme.current
    val scale by animateFloatAsState(
        targetValue = if (focused) 1f else 0.94f,
        animationSpec = tween(140),
        label = "tile",
    )
    Column(
        Modifier
            .scale(scale)
            // combinedClickable rather than plain clickable: this is the one
            // spot that needs a hold gesture, for a game's own menu (rename,
            // pick different art, remove) - the same touch pattern most
            // console-style launchers use, without giving up the tap ripple.
            .combinedClickable(onClick = onLaunch, onLongClick = onLongPress)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(RoundedCornerShape(10.dp))
                .background(theme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, theme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
        ) {
            val cover = game.coverPath?.let { File(it) }?.takeIf { it.isFile }
            val appIcon = if (cover == null) game.androidIcon() else null
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = game.title,
                    // Fit, not Crop. A console's tile ratio is one number but its
                    // covers are not all exactly that, and cropping takes the bite
                    // out of the edges — which is where box art puts the title.
                    // A thin letterbox on the surfaceVariant behind it is the
                    // cheaper loss.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (appIcon != null) {
                // No poster scraped yet: the launcher icon is a better
                // placeholder than two letters, and makes the shelf usable now.
                Image(
                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(appIcon),
                    contentDescription = game.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(22.dp),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        game.title.take(2).uppercase(),
                        color = theme.textSecondary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Title only on the focused tile, exactly like a console launcher -
            // a caption under every cover turns the grid into a spreadsheet.
            if (focused) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        game.title,
                        color = if (game.playable) theme.primary else theme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Box art is the whole point of the grid, so an unplayable game is
            // marked with a badge rather than by dimming the cover. Washing out
            // every tile made a working library look broken.
            if (!game.playable && showBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("no access", color = theme.error, fontSize = 9.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            if (game.favourite) {
                Text(
                    "★",
                    color = Color(0xFFFFC53D),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                )
            }
        }
    }
}

@Composable
private fun ProblemPane(page: SystemPage, onChooseFolder: (String) -> Unit) {
    val theme = LocalTheme.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(page.profile.name, color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        page.problem?.let {
            Text(it, color = theme.error, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
        }
        page.notes.forEach {
            Text(it, color = theme.textSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
        }
        val needsFolder = !page.isAppShelf && (page.problem?.contains("no roots") == true ||
            page.notes.any { it.contains("folder", ignoreCase = true) })
        if (needsFolder) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.primary.copy(alpha = 0.18f))
                    .border(1.dp, theme.primary, RoundedCornerShape(8.dp))
                    .clickable { onChooseFolder(page.profile.id) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("Choose folder…", color = theme.primary, fontSize = 15.sp,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CentreMessage(msg: String) {
    val theme = LocalTheme.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(msg, color = theme.textSecondary, fontSize = 16.sp)
    }
}

@Composable
private fun SystemNotice(text: String, action: String = "Choose folder", onFix: () -> Unit) {
    val theme = LocalTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(theme.error.copy(alpha = 0.13f))
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = theme.textPrimary, fontSize = 13.sp)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(theme.primary.copy(alpha = 0.22f))
                .clickable(onClick = onFix)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(action, color = theme.primary, fontSize = 13.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BottomBar(
    selected: DisplayGame?,
    isAppShelf: Boolean,
    onImport: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    artStatus: String? = null,
) {
    val theme = LocalTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Hint("B", "Apps", onClick = onApps)
        Spacer(Modifier.width(22.dp))
        Hint("Y", "Settings", onClick = onSettings)
        Spacer(Modifier.width(22.dp))
        Box(Modifier.clickable(onClick = onImport)) {
            Text("Import", color = theme.textSecondary, fontSize = 15.sp)
        }

        Spacer(Modifier.weight(1f))

        // Box art arrives on its own; this is the only trace it leaves. It must
        // never block or steal the cursor - the user is here to play a game,
        // not to watch a download.
        artStatus?.let {
            Text(it, color = theme.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.width(22.dp))
        }

        // Rename, art, remove - the same menu a held-down tile opens, reachable
        // here for a pad with no touchscreen to hold.
        Hint("X", "Menu", dim = selected == null, onClick = onMenu)
        Spacer(Modifier.width(22.dp))

        // Says what A will actually do, including when it cannot do it.
        val label = when {
            selected == null -> "—"
            !selected.playable && isAppShelf -> "Not on this device"
            !selected.playable -> "Needs folder access"
            else -> "Play"
        }
        Hint("A", label, dim = selected?.playable != true, onClick = onPlay)
    }
}

@Composable
private fun Hint(
    button: String,
    label: String,
    dim: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val theme = LocalTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
        else Modifier,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.primary.copy(alpha = if (dim) 0.38f else 1f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(button, color = theme.background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (dim) theme.textSecondary else theme.textPrimary,
            fontSize = 15.sp,
        )
    }
}
