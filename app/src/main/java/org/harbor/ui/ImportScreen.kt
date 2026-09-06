// Copyright 2026 r0mn-creator
// SPDX-License-Identifier: Apache-2.0

package org.harbor.ui

import android.content.Context
import android.os.Environment
import org.harbor.HarborApp
import org.harbor.data.BeaconImport
import java.io.File

/**
 * Finding and running a Beacon import.
 *
 * The export is produced on a PC (see docs/BEACON_MIGRATION.md) and placed in
 * shared storage. Beacon's own data lives in private app storage that no normal
 * app can read, so importing from a folder is not a shortcut - it is the only
 * route that does not require root.
 */
object ImportSource {

    /** Places an export is likely to be, checked in order. */
    fun candidates(): List<File> {
        val ext = Environment.getExternalStorageDirectory()
        return listOf(
            File(ext, "HarborImport/beacon"),
            File(ext, "HarborImport"),
            File(ext, "Download/HarborImport/beacon"),
            File(ext, "Download/beacon_export"),
        )
    }

    fun find(): File? = candidates().firstOrNull { File(it, "beacon_library.json").isFile }

    fun run(context: Context, replaceExisting: Boolean = false): BeaconImport.Report {
        val root = find() ?: return BeaconImport.Report(
            error = "No export found. Put beacon_library.json and the platform_* " +
                "folders in ${candidates().first().absolutePath}"
        )
        val app = HarborApp.instance
        return BeaconImport.run(context, root, app.profiles, app.library, replaceExisting)
    }
}
