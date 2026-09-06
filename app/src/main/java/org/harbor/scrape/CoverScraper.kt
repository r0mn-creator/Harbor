// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.scrape

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Box art, from libretro first and the user's own SteamGridDB account second.
 *
 * libretro is tried for everything because it needs **no API key and no
 * account**: IGDB / TheGamesDB / SteamGridDB all ship a secret in the APK if
 * they are baked in, which is both a licensing problem and something that
 * breaks the day the key is revoked.
 *
 *     https://thumbnails.libretro.com/<Platform>/Named_Boxarts/<Game>.png
 *
 * Our platform names already match libretro's directory names, because the
 * catalogue follows libretro-database naming - so there is no mapping table to
 * maintain, only a normalised lookup for the few that differ ("Sega Dreamcast"
 * vs "Sega - Dreamcast").
 *
 * libretro's coverage falls off a cliff after the sixth console generation -
 * measured at 12 covers for the entire Xbox 360 library, and no directory at
 * all for Switch - because it is a hobbyist archive of case scans, not a
 * database of modern box art. SteamGridDB has that art, but every route to it
 * needs an account, so it is a deliberately opt-in **fallback**: only for the
 * titles libretro found nothing for, and only when the user has pasted in
 * their own key. That keeps the redistribution decision, and the rate limit,
 * with the account holder rather than with this project.
 */
object CoverScraper {

    private const val BASE = "https://thumbnails.libretro.com"
    private const val SGDB_BASE = "https://www.steamgriddb.com/api/v2"
    private const val TAG = "LH.Scraper"

    data class Target(
        val key: String,
        val platformId: String,
        val platformName: String,
        val title: String,
    )

    data class Found(val key: String, val file: File)

    data class Report(
        val found: List<Found>,
        /** title -> why nothing was fetched. */
        val unmatched: List<String>,
        val notes: List<String>,
    ) {
        fun summary(): String = when {
            found.isEmpty() && unmatched.isEmpty() -> "Every game already has art"
            found.isEmpty() -> "No art found for any of the ${unmatched.size}"
            unmatched.isEmpty() -> "Found art for all ${found.size}"
            else -> "Found art for ${found.size}; no match for ${unmatched.size}"
        }
    }

    /**
     * Builds where they exist and nowhere else: a demo, kiosk or prototype dump
     * carries the right *name* and the wrong *box*, and it wins on length in the
     * near-match pass. Dropping them up front is cheaper than out-ranking them.
     */
    private val BUILD_VARIANT =
        Regex("""\((Demo|Kiosk|Beta|Proto|Sample|Program|Test|Debug)[^)]*\)|Trial Version""",
            RegexOption.IGNORE_CASE)

    private val TAG_GROUP = Regex("""\s*[\(\[{][^)\]}]*[)\]}]""")
    private val SET_INDEX = Regex("""^\d{3,4}\s*-\s*""")
    private val EXTENSION = Regex("""\.[A-Za-z0-9]{1,4}$""")
    private val NOISE = Regex("""\b(the|and|gba|gbc|nes|snes|n64)\b""", RegexOption.IGNORE_CASE)

    /**
     * Titles compared with region tags, set indices, articles and punctuation
     * gone. Deliberately lossy: this library has "Zelda - the Minish Cap GBA"
     * where the dump is "Legend of Zelda, The - The Minish Cap (USA)".
     */
    fun norm(s: String): String = s
        .replace(EXTENSION, "")
        .replace(SET_INDEX, "")
        .replace(TAG_GROUP, "")
        .replace("#", " ")
        .replace(NOISE, " ")
        .lowercase()
        .replace(Regex("""[^a-z0-9]+"""), "")

    /** USA first, then World/Europe/Japan; a Virtual Console re-release last. */
    private fun rank(name: String): Int {
        val region = when {
            name.contains("(USA") -> 0
            name.contains("(World") -> 1
            name.contains("(Europe") -> 2
            name.contains("(Japan") -> 3
            else -> 4
        }
        return region * 2 + if (name.contains("Virtual Console")) 1 else 0
    }

    // ---- fetching -----------------------------------------------------------

    /** @return the body, or a Failure carrying the HTTP status once headers arrive. */
    private fun getResult(url: String, headers: Map<String, String> = emptyMap()): Result<String> = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Harbor")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            val code = responseCode
            if (code != 200) {
                val body = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                disconnect()
                throw HttpStatus(code, body)
            }
            inputStream.bufferedReader().use { it.readText() }.also { disconnect() }
        }
    }.onFailure { if (it !is HttpStatus) Log.w(TAG, "fetch failed: $url", it) }

    private fun get(url: String): String? = getResult(url).getOrNull()

    /** Thrown to carry the response code + body past runCatching. */
    private class HttpStatus(val code: Int, val body: String) : Exception("HTTP $code")

    private fun download(
        url: String,
        dest: File,
        headers: Map<String, String> = emptyMap(),
    ): Boolean = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Harbor")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (responseCode != 200) { disconnect(); return false }
            dest.parentFile?.mkdirs()
            // Straight to a temp file, then rename: a half-written cover that
            // Coil later fails to decode looks exactly like a scraper bug.
            val tmp = File(dest.parentFile, dest.name + ".part")
            inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            disconnect()
            tmp.renameTo(dest)
        }
    }.onFailure { Log.w(TAG, "download failed: $url", it) }.getOrDefault(false)

    private val HREF = Regex("href=\"([^\"]+)\"")

    private fun links(html: String): List<String> =
        HREF.findAll(html).map { decode(it.groupValues[1]) }.toList()

    private fun decode(s: String): String = runCatching {
        java.net.URLDecoder.decode(s.replace("&amp;", "&"), "UTF-8")
    }.getOrDefault(s)

    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // ---- the pass -----------------------------------------------------------

    /**
     * @param mediaDir covers are written under `<platformId>` then `scraped`
     * @param steamGridDbKey when present, tried for whatever libretro misses
     * @param preferSteamGridDb skip libretro entirely and go straight to
     *        SteamGridDB for every target, even ones that already have a
     *        cover from a different pass. This is what "replace all art with
     *        SteamGridDB" means: mixing sources defeats the point of asking
     *        for one consistent look, so the request is honoured literally
     *        rather than only filling gaps.
     * @param onProgress called with a human-readable step, for the UI
     */
    fun scrape(
        targets: List<Target>,
        mediaDir: File,
        steamGridDbKey: String? = null,
        preferSteamGridDb: Boolean = false,
        onProgress: (String) -> Unit = {},
    ): Report {
        if (targets.isEmpty()) return Report(emptyList(), emptyList(), emptyList())

        val found = mutableListOf<Found>()
        // Targets libretro could not place, kept whole (not just the title) so
        // the SteamGridDB pass still knows which platform and key each one is.
        val unresolved = mutableListOf<Target>()
        val notes = mutableListOf<String>()

        if (preferSteamGridDb) {
            unresolved += targets
        } else {
        onProgress("Reading the libretro platform list…")
        val root = get("$BASE/")
        if (root == null) {
            notes += "Could not reach thumbnails.libretro.com. Check the network."
            unresolved += targets
        } else {
            val dirs = links(root).filter { it.endsWith("/") }
                .map { it.trimEnd('/') }
                .filter { it.isNotBlank() && !it.startsWith(".") && !it.startsWith("http") }
            val dirByNorm = dirs.associateBy { norm(it) }

            for ((platformId, group) in targets.groupBy { it.platformId }) {
                val platformName = group.first().platformName
                val dir = dirs.firstOrNull { it.equals(platformName, true) }
                    ?: dirByNorm[norm(platformName)]
                if (dir == null) {
                    notes += "$platformName has no thumbnail set on libretro."
                    unresolved += group
                    continue
                }

                onProgress("$platformName: reading the box art index…")
                val index = get("$BASE/${encode(dir)}/Named_Boxarts/")
                if (index == null) {
                    notes += "$platformName: could not read its box art index."
                    unresolved += group
                    continue
                }

                val files = links(index)
                    .filter { it.endsWith(".png", true) && !it.contains('/') }
                    .filterNot { BUILD_VARIANT.containsMatchIn(it) }
                val table = files.groupBy { norm(it) }
                    .mapValues { (_, v) -> v.sortedBy { rank(it) } }
                val keys = table.keys.toList()

                var done = 0
                for (t in group) {
                    done++
                    onProgress("$platformName: ${t.title} ($done of ${group.size})")
                    val q = norm(t.title)
                    val pick = table[q]?.firstOrNull() ?: nearMatch(q, keys, table)
                    if (pick == null) { unresolved += t; continue }

                    val dest = File(mediaDir, "$platformId/scraped/${safe(t.title)}.png")
                    val url = "$BASE/${encode(dir)}/Named_Boxarts/${encode(pick)}"
                    if (dest.isFile && dest.length() > 0) {
                        found += Found(t.key, dest)
                    } else if (download(url, dest)) {
                        found += Found(t.key, dest)
                    } else {
                        unresolved += t
                    }
                }
            }
        }
        }

        if (unresolved.isEmpty() || steamGridDbKey.isNullOrBlank()) {
            return Report(found, unresolved.map { it.title }, notes)
        }

        val stillUnresolved = scrapeFromSteamGridDb(
            unresolved, mediaDir, steamGridDbKey, found, notes, onProgress,
        )
        return Report(found, stillUnresolved.map { it.title }, notes)
    }

    data class ArtCandidate(
        val source: String,
        val label: String,
        val previewUrl: String,
        val fullUrl: String,
    )

    /**
     * Every plausible cover for one game, from both sources, for a person to
     * choose from rather than the automatic matcher picking one.
     *
     * The safety rule that guards the unattended pass - only a query sitting
     * INSIDE a longer name, exact match first - does not apply here on
     * purpose. A row of thumbnails a human looks at before choosing is itself
     * the safety check, so this can afford to show more than the one best
     * guess, including near-misses the automatic pass would rightly reject.
     */
    fun candidates(
        title: String,
        platformName: String,
        steamGridDbKey: String?,
    ): List<ArtCandidate> {
        val out = mutableListOf<ArtCandidate>()
        val q = norm(title)

        val root = get("$BASE/")
        val dir = root?.let { html ->
            val dirs = links(html).filter { it.endsWith("/") }
                .map { it.trimEnd('/') }
                .filter { it.isNotBlank() && !it.startsWith(".") && !it.startsWith("http") }
            dirs.firstOrNull { it.equals(platformName, true) }
                ?: dirs.associateBy { norm(it) }[norm(platformName)]
        }
        if (dir != null) {
            val index = get("$BASE/${encode(dir)}/Named_Boxarts/")
            if (index != null) {
                val matches = links(index)
                    .filter { it.endsWith(".png", true) && !it.contains('/') }
                    .filterNot { BUILD_VARIANT.containsMatchIn(it) }
                    .filter { val n = norm(it); n == q || n.contains(q) || q.contains(n) }
                    .sortedBy { rank(it) }
                    .take(16)
                for (f in matches) {
                    val url = "$BASE/${encode(dir)}/Named_Boxarts/${encode(f)}"
                    out += ArtCandidate("libretro", f.removeSuffix(".png"), url, url)
                }
            }
        }

        if (!steamGridDbKey.isNullOrBlank()) {
            val headers = mapOf("Authorization" to "Bearer $steamGridDbKey")
            val searchUrl = "$SGDB_BASE/search/autocomplete/${encode(searchTitle(title))}"
            val games = runCatching {
                json.decodeFromString<SgdbEnvelope<List<SgdbGame>>>(
                    getResult(searchUrl, headers).getOrThrow()
                )
            }.getOrNull()?.takeIf { it.success }?.data.orEmpty().take(3)

            for (g in games) {
                val gridsUrl = "$SGDB_BASE/grids/game/${g.id}" +
                    "?dimensions=600x900&types=static&nsfw=false&humor=false"
                val images = runCatching {
                    json.decodeFromString<SgdbEnvelope<List<SgdbImage>>>(
                        getResult(gridsUrl, headers).getOrThrow()
                    )
                }.getOrNull()?.takeIf { it.success }?.data.orEmpty()
                    .sortedByDescending { it.score }
                    .take(10)
                for (img in images) out += ArtCandidate("SteamGridDB", g.name, img.thumb, img.url)
            }
        }
        return out
    }

    /** Downloads a chosen candidate's full-size image as this game's cover. */
    fun applyCandidate(
        candidate: ArtCandidate,
        platformId: String,
        title: String,
        mediaDir: File,
    ): File? {
        val ext = candidate.fullUrl.substringAfterLast('.', "png").substringBefore('?').take(4)
        val dest = File(mediaDir, "$platformId/scraped/${safe(title)}.$ext")
        return if (download(candidate.fullUrl, dest)) dest else null
    }

    /**
     * Only ever matches a query that sits INSIDE a longer official name, never
     * the reverse. That direction matters: with it reversed, "Pokemon - Fire
     * Red" matched a file simply called "Pokemon" and the shelf would have shown
     * the wrong game's box with no way to tell. Wrong art is worse than none.
     */
    private fun nearMatch(
        q: String,
        keys: List<String>,
        table: Map<String, List<String>>,
    ): String? {
        if (q.length < 6) return null
        val hits = keys.filter { it.contains(q) }
        if (hits.isEmpty()) return null
        val best = hits.minWithOrNull(
            compareBy({ it.length }, { rank(table.getValue(it).first()) })
        ) ?: return null
        return table[best]?.firstOrNull()
    }

    private fun safe(s: String): String =
        s.replace(Regex("""[^A-Za-z0-9 ._-]"""), "_").take(120).trim()

    // ---- SteamGridDB fallback -------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private class SgdbEnvelope<T>(
        val success: Boolean = false,
        val data: T? = null,
        val errors: List<String> = emptyList(),
    )
    @Serializable private class SgdbGame(val id: Long, val name: String)
    @Serializable private class SgdbImage(
        val url: String,
        val thumb: String = url,
        val score: Int = 0,
        val style: List<String> = emptyList(),
    )

    /** A human-readable query, not the fully-collapsed key used for exact matching. */
    private fun searchTitle(s: String): String = s
        .replace(EXTENSION, "").replace(SET_INDEX, "").replace(TAG_GROUP, "")
        .replace("#", " ").trim()

    /**
     * The titles libretro had nothing for, tried against the user's own
     * SteamGridDB account. Stops at the first sign the key itself is the
     * problem (401/403) or that the account is rate-limited (429) rather than
     * repeating the same failure once per remaining title.
     */
    private fun scrapeFromSteamGridDb(
        targets: List<Target>,
        mediaDir: File,
        key: String,
        found: MutableList<Found>,
        notes: MutableList<String>,
        onProgress: (String) -> Unit,
    ): List<Target> {
        val headers = mapOf("Authorization" to "Bearer $key")
        val unresolved = mutableListOf<Target>()

        for ((i, t) in targets.withIndex()) {
            onProgress("SteamGridDB: ${t.title} (${i + 1} of ${targets.size})")

            val searchUrl = "$SGDB_BASE/search/autocomplete/${encode(searchTitle(t.title))}"
            val searchBody = getResult(searchUrl, headers)
            val stopReason = searchBody.exceptionOrNull()?.let { sgdbStopReason(it) }
            if (stopReason != null) {
                notes += "SteamGridDB: $stopReason"
                unresolved += targets.drop(i)
                break
            }
            val games = runCatching {
                json.decodeFromString<SgdbEnvelope<List<SgdbGame>>>(searchBody.getOrThrow())
            }.getOrNull()?.takeIf { it.success }?.data.orEmpty()
            if (games.isEmpty()) { unresolved += t; continue }

            // Same safety rule as the libretro match: an exact name first, and
            // otherwise only a query that sits INSIDE a longer official name -
            // never the reverse - so a short title cannot latch onto an
            // unrelated game that merely contains those letters.
            val q = norm(t.title)
            val game = games.firstOrNull { norm(it.name) == q }
                ?: games.filter { norm(it.name).contains(q) && q.length >= 6 }
                    .minByOrNull { it.name.length }
            if (game == null) { unresolved += t; continue }

            val gridsUrl = "$SGDB_BASE/grids/game/${game.id}" +
                "?dimensions=600x900&types=static&nsfw=false&humor=false"
            val gridsBody = getResult(gridsUrl, headers)
            val gridsStop = gridsBody.exceptionOrNull()?.let { sgdbStopReason(it) }
            if (gridsStop != null) {
                notes += "SteamGridDB: $gridsStop"
                unresolved += targets.drop(i)
                break
            }
            val image = runCatching {
                json.decodeFromString<SgdbEnvelope<List<SgdbImage>>>(gridsBody.getOrThrow())
            }.getOrNull()?.takeIf { it.success }?.data.orEmpty()
                .maxByOrNull { it.score }
            if (image == null) { unresolved += t; continue }

            // The image itself is served from SGDB's public CDN, not the API -
            // no Authorization header belongs on this request.
            val ext = image.url.substringAfterLast('.', "png").substringBefore('?').take(4)
            val dest = File(mediaDir, "${t.platformId}/scraped/${safe(t.title)}.$ext")
            if (download(image.url, dest)) found += Found(t.key, dest) else unresolved += t
        }
        return unresolved
    }

    /** Null unless the failure means "stop calling SteamGridDB for this run". */
    private fun sgdbStopReason(e: Throwable): String? = when {
        e !is HttpStatus -> null
        e.code == 401 || e.code == 403 -> "the key was rejected - check it in Settings"
        e.code == 429 -> "rate limited - try again later"
        else -> null
    }
}
