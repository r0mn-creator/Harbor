@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.harbor.ui.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.harbor.theme.LocalTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Every size in this screen is expressed in units of [Scale] rather than a fixed dp, so the
 * same layout composes correctly on a handheld's ~460dp-tall landscape viewport and a TV's much
 * taller one - one design, not a handheld version and a separate TV version. Sizes below are
 * tuned against [REFERENCE_HEIGHT] (a handheld landscape height); [factor] grows past 1x only
 * once more vertical space is actually available, and is capped so a very tall display doesn't
 * blow proportions out.
 */
class Scale(availableHeight: Dp) {
    val factor: Float = (availableHeight.value / REFERENCE_HEIGHT.value).coerceIn(1f, MAX_FACTOR)

    fun dp(value: Int): Dp = (value * factor).dp
    fun sp(value: Int): TextUnit = (value * factor).sp

    private companion object {
        val REFERENCE_HEIGHT = 468.dp
        const val MAX_FACTOR = 2f
    }
}

/**
 * A selected pill/tab's background: a light frost tinted with the active theme's accent colour
 * (the same "--highlight" role LightHouse's own .theme files already use), rather than a flat
 * fill.
 *
 * Not a real Haze blur - a hazeChild can't be a descendant of the haze() source it would need
 * to read (the whole screen content, here), and nesting one glass blur inside another bar that's
 * already blurring its own background would be redundant anyway. A soft white-to-accent gradient
 * plus a thin lit edge reads as "frosted" without it.
 */
@Composable
internal fun Modifier.frostedSelection(shape: Shape): Modifier {
    val accent = LocalTheme.current.accent
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.18f), accent.copy(alpha = 0.16f)),
            ),
        )
        .border(1.dp, accent.copy(alpha = 0.4f), shape)
}

/**
 * Shared host for every Harbor screen: the persistent glass tab bar (Home/Library/Settings,
 * paged with LT/RT) lives here, not inside any one screen, so it can never be present on one
 * screen and missing on another. Screens render as [navState.tab] changes; the bar and its
 * LT/RT badges never disappear.
 */
@Composable
fun HarborScaffold(navState: HarborNavState, onThemeChanged: () -> Unit, onImportTheme: () -> Unit) {
    val hazeState = remember { HazeState() }
    val theme = LocalTheme.current

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(theme.background),
    ) {
        val scale = remember(maxHeight) { Scale(maxHeight) }

        Box(
            Modifier
                .fillMaxSize()
                .haze(state = hazeState),
        ) {
            when (navState.tab) {
                HarborTab.HOME -> HomeContent(scale)
                HarborTab.LIBRARY -> LibraryContent(scale, navState)
                HarborTab.SETTINGS -> SettingsContent(scale, navState, onThemeChanged, onImportTheme)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = scale.dp(28), start = scale.dp(24), end = scale.dp(24)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            TriggerBadge("LT", scale, onClick = navState::prevTab)
            Spacer(Modifier.width(scale.dp(12)))
            GlassTabBar(hazeState, scale, navState)
            Spacer(Modifier.width(scale.dp(12)))
            TriggerBadge("RT", scale, onClick = navState::nextTab)
        }
    }
}

/**
 * A small circular shoulder-button/trigger affordance - LT/RT flank the tab bar, LB/RB flank
 * Library's console bar. Tappable too, so touch has full parity with the pad.
 *
 * Styled to actually read as glass rather than a flat translucent chip: an off-centre radial
 * glow (the "light source" sits up and to the left, like the reference render) plus a diagonal
 * rim that's bright where the light grazes the edge and dark where it doesn't - not a real Haze
 * blur, since this composable is also used inside Library's already-hazed content (ConsoleBar's
 * LB/RB), where a hazeChild would hit the same descendant-of-haze crash documented above.
 */
@Composable
internal fun TriggerBadge(label: String, scale: Scale, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.clickable(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = LocalTheme.current.accent.copy(alpha = 0.35f),
        ),
    ) {
        Box(
            Modifier
                .size(scale.dp(40))
                .glassSurface(),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = scale.sp(11), fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Real glass, not a flat translucent fill: an off-centre radial glow (the light sits up and to
 * the left, like Apple's own Liquid Glass reference renders) plus a diagonal rim that's bright
 * where the light grazes the edge and dark where it doesn't. Used for every glass surface in this
 * UI - the circular/pill chrome (badges, the Play pill) and the box-art tiles alike, so the
 * material reads as one consistent thing rather than badges being glass and cards being flat
 * colour.
 *
 * @param cornerRadius Explicit corner radius for a rounded-rect surface (art tiles); omit for a
 * circle or a fully-rounded pill, where half the shorter side is always the correct radius and
 * there's no need to inspect the actual Shape.
 *
 * Not a real Haze blur: several call sites (Library's LB/RB, and its game grid) live inside
 * content already marked `.haze()`, and a hazeChild can't be a descendant of the haze() source it
 * would read from - see the crash documented above. This fakes the same "light passing through
 * glass" read with a static gradient instead.
 */
internal fun Modifier.glassSurface(cornerRadius: Dp? = null): Modifier = this.drawWithCache {
    val corner = glassCorner(cornerRadius)
    val (fill, rim) = glassBrushes()
    onDrawBehind {
        drawRoundRect(brush = fill, cornerRadius = corner)
        drawRoundRect(brush = rim, cornerRadius = corner, style = Stroke(width = 1.dp.toPx()))
    }
}

/**
 * Same glass as [glassSurface], but painted ON TOP of the tile's own content instead of behind
 * it - for box art specifically, so a real cover image reads as sitting under glass (a case still
 * in its shrink wrap) rather than the glass being hidden underneath an opaque image.
 */
internal fun Modifier.glassOverlay(cornerRadius: Dp? = null): Modifier = this.drawWithCache {
    val corner = glassCorner(cornerRadius)
    val (fill, rim) = glassBrushes()
    onDrawWithContent {
        drawContent()
        drawRoundRect(brush = fill, cornerRadius = corner)
        drawRoundRect(brush = rim, cornerRadius = corner, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun androidx.compose.ui.draw.CacheDrawScope.glassCorner(cornerRadius: Dp?): CornerRadius =
    if (cornerRadius != null) CornerRadius(cornerRadius.toPx()) else CornerRadius(size.minDimension / 2f)

private fun androidx.compose.ui.draw.CacheDrawScope.glassBrushes(): Pair<Brush, Brush> {
    val highlight = Offset(size.width * 0.28f, 0f)
    val fill = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.26f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.04f),
        ),
        center = highlight,
        radius = size.maxDimension * 1.1f,
    )
    val rim = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.80f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.30f),
        ),
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height),
    )
    return fill to rim
}

/** Real cover art for a couple of titles so the glass-over-art look can actually be judged; every other title still falls back to the placeholder icon. */
internal val boxArtAssets = mapOf(
    "Halo 3" to "file:///android_asset/mock_boxart/halo3.jpg",
    "Need for Speed: Carbon" to "file:///android_asset/mock_boxart/nfs_carbon.jpg",
    "NFS: Carbon" to "file:///android_asset/mock_boxart/nfs_carbon.jpg",
)

private data class MockGame(val title: String, val subtitle: String, val gradient: List<Color>)

private val heroGames = listOf(
    MockGame("Halo 3", "Xbox 360 · Bungie", listOf(Color(0xFF1B3A57), Color(0xFF07090D))),
    MockGame("Need for Speed: Carbon", "Xbox 360 · EA Black Box", listOf(Color(0xFF4A1F1F), Color(0xFF07090D))),
    MockGame("Ninja Gaiden II", "Xbox 360 · Team Ninja", listOf(Color(0xFF2E1B47), Color(0xFF07090D))),
)

private val continueGames = listOf(
    "Halo 3", "NFS: Carbon", "NFS: Most Wanted", "Ninja Gaiden II", "Gears of War", "Forza 2",
)

@Composable
private fun HomeContent(scale: Scale) {
    val pagerState = rememberPagerState(pageCount = { heroGames.size })
    Column(Modifier.fillMaxSize()) {
        HeroCarousel(pagerState, scale)
        Spacer(Modifier.height(scale.dp(16)))
        ContinuePlayingShelf(scale)
        Spacer(Modifier.height(scale.dp(64)))
    }
}

@Composable
private fun HeroCarousel(pagerState: PagerState, scale: Scale) {
    Box(Modifier.fillMaxWidth().height(scale.dp(224))) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val game = heroGames[page]
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(game.gradient)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                            ),
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = scale.dp(40), bottom = scale.dp(20), end = scale.dp(40)),
                ) {
                    Text(game.title, color = Color.White, fontSize = scale.sp(34), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(scale.dp(4)))
                    Text(game.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = scale.sp(14))
                    Spacer(Modifier.height(scale.dp(14)))
                    GlassPillButton(scale)
                }

                val art = boxArtAssets[game.title]
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = scale.dp(48))
                            .width(scale.dp(110))
                            .height(scale.dp(140))
                            .clip(RoundedCornerShape(10.dp))
                            .glassOverlay(cornerRadius = 10.dp),
                    )
                }
            }
        }

        CarouselDefaults.IndicatorRow(
            itemCount = heroGames.size,
            activeItemIndex = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = scale.dp(40), top = scale.dp(16)),
        )
    }
}

@Composable
private fun GlassPillButton(scale: Scale) {
    val pillShape = RoundedCornerShape(50)
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = pillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.20f),
        ),
    ) {
        Row(
            Modifier
                .glassSurface()
                .padding(horizontal = scale.dp(20), vertical = scale.dp(8)),
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(scale.dp(18)),
            )
            Spacer(Modifier.width(scale.dp(6)))
            Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = scale.sp(15))
        }
    }
}

@Composable
private fun ContinuePlayingShelf(scale: Scale) {
    val theme = LocalTheme.current
    Column {
        Text(
            "Continue Playing",
            color = theme.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = scale.sp(16),
            modifier = Modifier.padding(start = scale.dp(40), bottom = scale.dp(10)),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = scale.dp(40))) {
            items(continueGames) { title ->
                Column(
                    Modifier.padding(end = scale.dp(14)).width(scale.dp(96)),
                ) {
                    Surface(
                        onClick = {},
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = theme.accent.copy(alpha = 0.9f),
                        ),
                        modifier = Modifier
                            .size(scale.dp(96))
                            .background(theme.surfaceVariant, RoundedCornerShape(16.dp))
                            .glassOverlay(cornerRadius = 16.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val art = boxArtAssets[title]
                            if (art != null) {
                                AsyncImage(
                                    model = art,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.VideogameAsset,
                                    contentDescription = null,
                                    tint = theme.textPrimary,
                                    modifier = Modifier.size(scale.dp(24)),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(scale.dp(6)))
                    Text(title, color = theme.textSecondary, fontSize = scale.sp(12))
                }
            }
        }
    }
}

@Composable
private fun GlassTabBar(hazeState: HazeState, scale: Scale, navState: HarborNavState) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .hazeChild(
                state = hazeState,
                shape = RoundedCornerShape(50),
                style = HazeDefaults.style(
                    backgroundColor = Color.Black,
                    tint = Color.White.copy(alpha = 0.08f),
                    blurRadius = scale.dp(28),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = scale.dp(8), vertical = scale.dp(6)),
    ) {
        TabItem(Icons.Filled.Home, "Home", navState.tab == HarborTab.HOME, scale) { navState.selectTab(HarborTab.HOME) }
        Spacer(Modifier.width(scale.dp(4)))
        TabItem(Icons.Filled.VideogameAsset, "Library", navState.tab == HarborTab.LIBRARY, scale) { navState.selectTab(HarborTab.LIBRARY) }
        Spacer(Modifier.width(scale.dp(4)))
        TabItem(Icons.Filled.Settings, "Settings", navState.tab == HarborTab.SETTINGS, scale) { navState.selectTab(HarborTab.SETTINGS) }
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, scale: Scale, onClick: () -> Unit) {
    val pillShape = RoundedCornerShape(50)
    val accent = LocalTheme.current.accent
    Surface(
        onClick = onClick,
        modifier = Modifier
            .clickable(onClick = onClick)
            .let { if (selected) it.frostedSelection(pillShape) else it },
        shape = ClickableSurfaceDefaults.shape(shape = pillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = accent.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = scale.dp(16), vertical = scale.dp(8)),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) accent else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(scale.dp(18)),
            )
            Spacer(Modifier.width(scale.dp(6)))
            Text(label, color = if (selected) accent else Color.White.copy(alpha = 0.7f), fontSize = scale.sp(13))
        }
    }
}
