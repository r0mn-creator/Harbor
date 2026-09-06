@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.harbor.ui.mock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
private class Scale(availableHeight: Dp) {
    val factor: Float = (availableHeight.value / REFERENCE_HEIGHT.value).coerceIn(1f, MAX_FACTOR)

    fun dp(value: Int): Dp = (value * factor).dp
    fun sp(value: Int): TextUnit = (value * factor).sp

    private companion object {
        val REFERENCE_HEIGHT = 468.dp
        const val MAX_FACTOR = 2f
    }
}

/**
 * First visual mockup of Harbor's Home screen: iOS 26 Games-app layout rhythm
 * (hero carousel -> icon shelf -> floating glass tab bar) over LightHouse's
 * true-black ground, using androidx.tv for D-pad focus and Haze for the
 * frosted-glass tab bar. Sample data only - not wired to the real library.
 */
private data class MockGame(val title: String, val subtitle: String, val gradient: List<Color>)

private val heroGames = listOf(
    MockGame("Halo 3", "Xbox 360 · Bungie", listOf(Color(0xFF1B3A57), Color(0xFF07090D))),
    MockGame("Need for Speed: Carbon", "Xbox 360 · EA Black Box", listOf(Color(0xFF4A1F1F), Color(0xFF07090D))),
    MockGame("Ninja Gaiden II", "Xbox 360 · Team Ninja", listOf(Color(0xFF2E1B47), Color(0xFF07090D))),
)

private val continueGames = listOf(
    "Halo 3", "NFS: Carbon", "NFS: Most Wanted", "Ninja Gaiden II", "Gears of War", "Forza 2",
)

private val amber = Color(0xFFFFB74D)

@Composable
fun HarborMockHome() {
    val hazeState = remember { HazeState() }
    val pagerState = rememberPagerState(pageCount = { heroGames.size })

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val scale = remember(maxHeight) { Scale(maxHeight) }

        Column(
            Modifier
                .fillMaxSize()
                .haze(state = hazeState),
        ) {
            HeroCarousel(pagerState, scale)
            Spacer(Modifier.height(scale.dp(16)))
            ContinuePlayingShelf(scale)
            Spacer(Modifier.height(scale.dp(64)))
        }

        GlassTabBar(
            hazeState = hazeState,
            scale = scale,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scale.dp(28)),
        )
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
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            focusedContainerColor = Color.White.copy(alpha = 0.26f),
        ),
    ) {
        Row(
            Modifier
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
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
    Column {
        Text(
            "Continue Playing",
            color = Color.White.copy(alpha = 0.9f),
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
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = amber.copy(alpha = 0.9f),
                        ),
                        modifier = Modifier.size(scale.dp(96)),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.VideogameAsset,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(scale.dp(24)),
                            )
                        }
                    }
                    Spacer(Modifier.height(scale.dp(6)))
                    Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = scale.sp(12))
                }
            }
        }
    }
}

@Composable
private fun GlassTabBar(hazeState: HazeState, scale: Scale, modifier: Modifier = Modifier) {
    Row(
        modifier
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
        TabItem(icon = Icons.Filled.Home, label = "Home", selected = true, scale = scale)
        Spacer(Modifier.width(scale.dp(4)))
        TabItem(icon = Icons.Filled.VideogameAsset, label = "Library", selected = false, scale = scale)
        Spacer(Modifier.width(scale.dp(4)))
        TabItem(icon = Icons.Filled.Settings, label = "Settings", selected = false, scale = scale)
    }
}

@Composable
private fun TabItem(icon: ImageVector, label: String, selected: Boolean, scale: Scale) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) amber.copy(alpha = 0.22f) else Color.Transparent,
            focusedContainerColor = amber.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = scale.dp(16), vertical = scale.dp(8)),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) amber else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(scale.dp(18)),
            )
            Spacer(Modifier.width(scale.dp(6)))
            Text(label, color = if (selected) amber else Color.White.copy(alpha = 0.7f), fontSize = scale.sp(13))
        }
    }
}
