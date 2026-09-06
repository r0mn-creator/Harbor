// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Imports an exported Beacon library.
 *
 * Beacon keeps everything in a private Room DB that a normal app cannot read,
 * so the import works from an *export folder* the user points us at:
 *
 *     <folder>/beacon_library.json
 *     <folder>/platform_<pid>/game_<gid>/cover.jpg
 *     <folder>/platform_<pid>/game_<gid>/screenshot.jpg
 *
 * The export is produced on a PC with adb (see docs/BEACON_MIGRATION.md).
 *
 * ⚠️ Beacon's document URIs are carried over, but a SAF grant belongs to the app
 * that asked for it. Those URIs will NOT be readable by Harbor until the
 * user re-picks each folder. That is why records are matched to scanned files by
 * NAME as well as by URI, and why the importer reports it rather than leaving
 * the user with a library of unopenable games.
 */
object BeaconImport {

    // ---- the export's shape (only the fields we consume) --------------------

    @Serializable
    private data class Export(val platforms: List<BPlatform> = emptyList())

    @Serializable
    private data class BPlatform(
        val id: Int,
        val name: String,
        @SerialName("short_name") val shortName: String = "",
        @SerialName("player_package") val playerPackage: String = "",
        val order: Int = 100,
        @SerialName("aspect_ratio_width") val arW: Int = 3,
        @SerialName("aspect_ratio_height") val arH: Int = 4,
        val paths: List<String> = emptyList(),
        val games: List<BGame> = emptyList(),
    )

    @Serializable
    private data class BGame(
        val id: Int,
        val name: String,
        @SerialName("cover_image_path") val cover: String? = null,
        @SerialName("screenshot_image_path") val screenshot: String? = null,
        @SerialName("release_date") val releaseDate: Long? = null,
        val publisher: String? = null,
        val developer: String? = null,
        val genres: String? = null,
        @SerialName("is_favourite") val favourite: Int = 0,
        @SerialName("last_played") val lastPlayed: Long? = null,
        val files: List<BFile> = emptyList(),
    )

    @Serializable
    private data class BFile(val file: String, @SerialName("is_primary") val primary: Int = 0)

    // ---- result -------------------------------------------------------------

    data class Report(
        val platforms: Int = 0,
        val games: Int = 0,
        val covers: Int = 0,
        val screenshots: Int = 0,
        val skipped: Int = 0,
        val notes: List<String> = emptyList(),
        val error: String? = null,
    ) {
        /** Deliberately spells out what happened; a silent merge of 557 rows is not ok. */
        fun summary(): String = error ?: buildString {
            append("Imported $games games across $platforms platforms")
            append(" · $covers covers, $screenshots screenshots")
            if (skipped > 0) append(" · $skipped skipped")
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param root folder containing beacon_library.json and the platform_* dirs
     * @param replaceExisting overwrite art/metadata Harbor already has
     */
    fun run(
        context: Context,
        root: File,
        profiles: ProfileStore,
        library: LibraryStore,
        replaceExisting: Boolean = false,
    ): Report {
        val manifest = File(root, "beacon_library.json")
        if (!manifest.isFile) {
            return Report(error = "No beacon_library.json in ${root.name}")
        }
        val export = runCatching { json.decodeFromString<Export>(manifest.readText()) }
            .getOrElse { return Report(error = "Could not read the export: ${it.message}") }

        val notes = mutableListOf<String>()
        val records = mutableListOf<GameRecord>()
        var covers = 0
        var screenshots = 0
        var skipped = 0
        var platformCount = 0

        val existing = library.all().associateBy { it.key }
        val mediaRoot = library.mediaDir.apply { mkdirs() }

        // Existing shelves, looked up three ways. An id alone is not identity:
        // this export calls the Dreamcast "DC" while the catalogue calls it
        // "dreamcast", so an id-only check created a SECOND Dreamcast shelf and
        // sent the imported games to it, leaving two tabs both labelled DC.
        val known = profiles.declared().toMutableList()
        fun shelfFor(bp: BPlatform): PlatformProfile? {
            val slugId = slug(bp.shortName.ifBlank { bp.name })
            return known.firstOrNull { it.id == slugId }
                ?: known.firstOrNull { normaliseTitle(it.name) == normaliseTitle(bp.name) }
                ?: bp.shortName.takeIf { it.isNotBlank() }?.let { short ->
                    known.firstOrNull {
                        it.shortName.isNotBlank() &&
                            normaliseTitle(it.shortName) == normaliseTitle(short)
                    }
                }
        }

        for (bp in export.platforms) {
            if (bp.games.isEmpty()) { skipped++; continue }
            val match = shelfFor(bp)
            // Reuse the existing shelf's id so the games land on the tab the
            // user already has, rather than beside it.
            val pid = match?.id ?: slug(bp.shortName.ifBlank { bp.name })

            // Create the platform profile if we do not already have one. The
            // player package becomes an EDITABLE launch profile, never a lookup.
            if (match == null) {
                profiles.save(
                    PlatformProfile(
                        id = pid,
                        name = bp.name,
                        shortName = bp.shortName.ifBlank { pid.uppercase() },
                        order = bp.order,
                        aspectRatio = "${bp.arW}:${bp.arH}",
                        source = SourceSpec(
                            provider = SourceSpec.FOLDER,
                            // Beacon's tree URIs are carried across, but the SAF
                            // grant is not - see the class comment.
                            roots = bp.paths,
                            extensions = extensionsFrom(bp.games),
                        ),
                        launch = LaunchSpec(
                            component = bp.playerPackage.takeIf {
                                it.isNotBlank() && it != "Android"
                            },
                            action = "android.intent.action.VIEW",
                            romMode = LaunchSpec.ROM_DATA_URI,
                            flags = listOf(LaunchSpec.FLAG_GRANT_READ),
                        ),
                        // Beacon launched these, but Harbor has not, and the
                        // component is a package with no activity yet.
                        verified = false,
                    ).also { known += it }
                )
                platformCount++
            }

            if (bp.paths.isNotEmpty()) {
                notes += "${bp.name}: re-pick its folder in Harbor to restore access."
            }

            for (g in bp.games) {
                val primary = (g.files.firstOrNull { it.primary == 1 } ?: g.files.firstOrNull())?.file
                val key = primary ?: "$pid:${g.id}"
                val prev = existing[key]
                if (prev != null && !replaceExisting) { skipped++; continue }

                val dest = File(mediaRoot, "$pid/${g.id}").apply { mkdirs() }
                val cover = copyArt(root, g.cover, File(dest, "cover.jpg"))?.also { covers++ }
                val shot = copyArt(root, g.screenshot, File(dest, "screenshot.jpg"))?.also { screenshots++ }

                records += GameRecord(
                    key = key,
                    platformId = pid,
                    title = g.name,
                    uri = primary,
                    coverPath = cover ?: prev?.coverPath,
                    screenshotPath = shot ?: prev?.screenshotPath,
                    publisher = g.publisher,
                    developer = g.developer,
                    genres = g.genres,
                    releaseDate = g.releaseDate,
                    favourite = g.favourite == 1,
                    lastPlayed = g.lastPlayed,
                )
            }
        }

        library.put(records)
        return Report(platformCount, records.size, covers, screenshots, skipped, notes)
    }

    /**
     * Beacon stores absolute paths into its own private storage
     * (/data/user/0/com.radikal.gamelauncher/files/platform_1/game_32/cover.jpg).
     * Only the part after "files/" is meaningful inside the export folder.
     */
    private fun copyArt(root: File, beaconPath: String?, dest: File): String? {
        if (beaconPath.isNullOrBlank()) return null
        val rel = beaconPath.substringAfter("/files/", missingDelimiterValue = "")
        if (rel.isBlank()) return null
        val src = File(root, rel)
        if (!src.isFile) return null
        return runCatching {
            src.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            dest.absolutePath
        }.getOrElse {
            Log.e(TAG, "copy $rel failed", it)
            null
        }
    }

    /** Derive the scan extensions from what the platform actually contains. */
    private fun extensionsFrom(games: List<BGame>): List<String> =
        games.asSequence()
            .flatMap { it.files.asSequence() }
            .mapNotNull { it.file.substringAfterLast('.', "").lowercase().takeIf { e -> e.length in 2..5 } }
            .distinct()
            .sorted()
            .toList()
            .ifEmpty { listOf("iso", "zip", "7z") }

    private fun slug(s: String): String =
        s.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-').ifBlank { "platform" }

    private const val TAG = "LH.BeaconImport"
}
