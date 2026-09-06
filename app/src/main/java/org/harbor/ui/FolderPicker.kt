// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.harbor.data.PlatformProfile
import org.harbor.data.ProfileStore

/**
 * Adding a ROM folder.
 *
 * SAF grants do NOT transfer between apps and do not survive a reinstall, so
 * Harbor has to take its own persistable permission for every tree. An
 * imported library carries perfectly valid tree URIs that Harbor still
 * cannot read for exactly this reason - the URI is right, the grant is missing.
 * That is why importing still needs the user to re-pick each folder once,
 * and why the UI has to say so instead of showing an empty section.
 */
object FolderPicker {

    /**
     * @param initial a tree URI to open the picker AT.
     *
     * After an import we already know the exact folder for every system - the
     * URI is right, only the grant is missing. Seeding EXTRA_INITIAL_URI
     * turns re-granting into one tap per system instead of navigating to the SD
     * card and hunting for the folder eleven times. Without it the picker opens
     * at internal storage root, which Android refuses to grant at all.
     */
    /**
     * Where to open when nothing has been picked yet - i.e. first run.
     *
     * The default landing place is the root of internal storage, which Android
     * refuses to grant ("Can't use this folder. To protect your privacy, choose
     * another folder"), so a new user's very first action fails. A removable
     * card's root IS grantable and is where ROM libraries usually live, so
     * prefer it and fall back to Downloads.
     */
    fun defaultStart(context: android.content.Context): Uri? {
        val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
        val card = sm?.storageVolumes?.firstOrNull {
            it.isRemovable && !it.isPrimary && it.state == android.os.Environment.MEDIA_MOUNTED
        }
        val id = card?.uuid?.let { "$it:" } ?: "primary:Download"
        return runCatching {
            Uri.parse(
                "content://com.android.externalstorage.documents/document/" +
                    android.net.Uri.encode(id)
            )
        }.getOrNull()
    }

    fun intent(initial: Uri? = null, fallbackDoc: Uri? = null): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            if (initial != null && android.os.Build.VERSION.SDK_INT >= 26) {
                // EXTRA_INITIAL_URI wants a DOCUMENT uri; handing it the tree uri
                // is silently ignored and the picker opens at internal storage
                // root, which Android then refuses to grant at all.
                val doc = runCatching {
                    android.provider.DocumentsContract.buildDocumentUriUsingTree(
                        initial,
                        android.provider.DocumentsContract.getTreeDocumentId(initial),
                    )
                }.getOrNull() ?: initial
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, doc)
            } else if (fallbackDoc != null && android.os.Build.VERSION.SDK_INT >= 26) {
                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, fallbackDoc)
            }
        }

    /**
     * A folder source needs attention when it has no roots at all, or when none
     * of its roots is currently readable. The second case is the normal state
     * after an import: SAF grants belong to the app that asked for them, so
     * the imported URIs are valid but unusable until Harbor takes its own.
     */
    fun needsFolder(context: Context, roots: List<String>): Boolean {
        if (roots.isEmpty()) return true
        return roots.none { r ->
            runCatching {
                androidx.documentfile.provider.DocumentFile
                    .fromTreeUri(context, Uri.parse(r))?.canRead() == true
            }.getOrDefault(false)
        }
    }

    /**
     * Persist the grant and add the tree to a profile.
     * @return the updated profile, or null if the grant could not be taken.
     */
    fun accept(
        context: Context,
        store: ProfileStore,
        profile: PlatformProfile,
        tree: Uri,
    ): PlatformProfile? {
        val ok = runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }.isSuccess
        if (!ok) return null

        // Drop roots that nest with the new one. Picking a game's own folder by
        // mistake used to leave the real system folder listed but ungranted, so
        // the section scanned one game and looked simply empty - "5 of 26
        // playable" with no hint that the folder was wrong.
        val added = tree.toString()
        val roots = (profile.source.roots.filterNot { nests(it, added) } + added).distinct()
        val updated = profile.copy(source = profile.source.copy(roots = roots))
        // A grant we cannot persist is worse than none: the UI would show the
        // folder as set while every launch still failed.
        if (store.save(updated) != null) return null
        return updated
    }

    /** True when either tree contains the other. */
    private fun nests(a: String, b: String): Boolean {
        val x = a.trimEnd('/'); val y = b.trimEnd('/')
        return x == y || x.startsWith("$y%2F") || y.startsWith("$x%2F") ||
            x.startsWith("$y/") || y.startsWith("$x/")
    }

    /** Trees Harbor currently holds a grant for - shown in Settings. */
    fun granted(context: Context): List<Uri> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri }
}
