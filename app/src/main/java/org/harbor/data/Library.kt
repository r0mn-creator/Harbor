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
 * Persisted metadata for a game.
 *
 * Discovery finds *files*; it cannot know a publisher or a cover. So the
 * library is a separate, durable store that discovery results are merged
 * against. A record can also exist with no matching file yet (imported before
 * the folder was granted), which is why nothing here assumes the file is
 * currently reachable.
 */
@Serializable
data class GameRecord(
    /** Stable identity: the game's URI for file-backed, or "platform:id". */
    val key: String,
    val platformId: String,
    val title: String,
    /** Original file/document URI, when file-backed. */
    val uri: String? = null,
    /** Library id for id-backed platforms. */
    val libraryId: String? = null,
    @SerialName("cover") val coverPath: String? = null,
    @SerialName("screenshot") val screenshotPath: String? = null,
    val publisher: String? = null,
    val developer: String? = null,
    val genres: String? = null,
    @SerialName("release_date") val releaseDate: Long? = null,
    val favourite: Boolean = false,
    @SerialName("last_played") val lastPlayed: Long? = null,
    /**
     * Removed from the shelf by the user, via a scanned game's own context
     * menu. Never deletes the ROM: the file stays exactly where it is, so
     * un-hiding is always possible by editing this one field back.
     */
    val hidden: Boolean = false,
) {
    /**
     * Filename without extension or bracketed region tags, lowercased.
     * Used to re-link an imported record to a scanned file when the URI does
     * not match - the normal case, because a document URI is granted to the app
     * that asked for it, not to us.
     */
    val matchName: String get() = normaliseTitle(title)
}

/**
 * Titles compared with punctuation AND spacing removed.
 *
 * An imported record may read "Killer7" where the ROM is
 * "Killer 7 (USA) (Disc 1).iso"; with spaces significant those are different
 * games, the record looks orphaned, and a library cleanup would throw away its
 * artwork. Region and disc tags are
 * dropped first because they are not part of the title.
 */
fun normaliseTitle(s: String): String =
    s.lowercase()
        .replace(Regex("""\s*[\(\[][^)\]]*[\)\]]"""), "")
        .replace(Regex("""[^a-z0-9]+"""), "")
        .trim()

/**
 * The library on disk. One JSON file, loaded once and kept in memory - 557
 * games is tiny, and a database would buy nothing here.
 */
class LibraryStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    private val file: File get() = File(context.getExternalFilesDir(null), "library.json")
    val mediaDir: File get() = File(context.getExternalFilesDir(null), "media")

    @Volatile
    private var cache: MutableList<GameRecord>? = null

    fun all(): List<GameRecord> {
        cache?.let { return it }
        val loaded = runCatching {
            if (!file.exists()) mutableListOf()
            else json.decodeFromString<List<GameRecord>>(file.readText()).toMutableList()
        }.getOrElse {
            Log.e(TAG, "library unreadable, starting empty", it)
            mutableListOf()
        }
        cache = loaded
        return loaded
    }

    fun forPlatform(platformId: String): List<GameRecord> =
        all().filter { it.platformId == platformId }

    fun replaceAll(records: List<GameRecord>) {
        cache = records.toMutableList()
        persist()
    }

    /** Add or update by key. */
    fun put(records: List<GameRecord>) {
        val list = all().toMutableList()
        for (r in records) {
            val i = list.indexOfFirst { it.key == r.key }
            if (i >= 0) list[i] = r else list += r
        }
        cache = list
        persist()
    }

    /**
     * Forget records whose game is no longer on disk.
     *
     * Imported libraries accumulate these: a source that lists each disc of a
     * multi-disc set, and each .cue beside a .gdi, as separate games leaves
     * records behind once Harbor collapses them to one entry - they sit
     * greyed out forever. Deliberately an explicit action, never automatic - a
     * game can also be "missing" because an SD card is unmounted, and silently
     * deleting its metadata and art would be unrecoverable.
     *
     * @return how many were removed.
     */
    fun forgetMissing(keepKeys: Set<String>): Int {
        val list = all()
        val kept = list.filter { it.key in keepKeys }
        val removed = list.size - kept.size
        if (removed > 0) { cache = kept.toMutableList(); persist() }
        return removed
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GameRecord.serializer()), all()))
        }.onFailure { Log.e(TAG, "could not save library", it) }
    }

    private companion object { const val TAG = "LH.Library" }
}
