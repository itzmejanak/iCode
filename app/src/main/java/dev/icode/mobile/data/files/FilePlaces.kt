package dev.icode.mobile.data.files

import dev.icode.mobile.data.storage.PhoneStorage
import java.io.File

/**
 * iCode's two places (docs/storage-explorer-plan.md §4): Private, its workspaces, and Shared, phone
 * storage — the files of each, and which one a location is in.
 */
internal class FilePlaces(workspaces: File, val storage: PhoneStorage) {
    val privateFiles = PlaceFiles(workspaces, gitOwnsDotGit = true)
    val sharedFiles = PlaceFiles(storage.root, gitOwnsDotGit = false)

    /** The place [location] is in: Shared's when it is on phone storage, else Private's, which refuses what is outside it. */
    fun of(location: String): PlaceFiles = if (storage.contains(location)) sharedFiles else privateFiles

    /** Why [locations] cannot be changed now — on phone storage, without All Files Access — or null. */
    fun accessRefusal(locations: Collection<String>): String? = NO_ACCESS.takeIf { locations.any(storage::contains) && !storage.hasAccess() }

    companion object {
        const val NO_ACCESS = "Allow access to phone storage first"
    }
}
