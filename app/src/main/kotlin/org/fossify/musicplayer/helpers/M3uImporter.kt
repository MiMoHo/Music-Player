package org.fossify.musicplayer.helpers

import net.bjoernpetersen.m3u.M3uParser
import net.bjoernpetersen.m3u.model.M3uEntry
import org.fossify.commons.extensions.showErrorToast
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.models.Track
import java.io.File

class M3uImporter(
    val activity: SimpleActivity,
    val callback: (result: ImportResult) -> Unit
) {
    var failedEvents = 0
    var exportedEvents = 0

    enum class ImportResult {
        IMPORT_FAIL, IMPORT_OK, IMPORT_PARTIAL
    }

    fun importPlaylist(path: String, playListId: Int) {
        val inputStream = if (path.contains("/")) {
            File(path).inputStream()
        } else {
            activity.assets.open(path)
        }

        try {
            val m3uEntries: List<M3uEntry> = M3uParser.parse(inputStream.reader())

            val existingTracks = activity.audioHelper.getAllTracks()
                .filter { it.playListId == 0 }

            val playlistItems = mutableListOf<Track>()
            for (m3uEntry in m3uEntries) {
                // m3u entries may use paths relative to the playlist file. The parser is
                // invoked without a base path, so relative entries stay unresolved and can
                // never equal an absolute MediaStore path. The playlist's real directory is
                // also unavailable for content:// imports (copied to a cache temp file), so
                // match on the trailing path segment instead of requiring an exact path.
                val entryPath = m3uEntry.location.toString().replace('\\', '/')
                for (track in existingTracks) {
                    val pathMatches = track.path == entryPath || track.path.endsWith("/$entryPath")
                    if (pathMatches || m3uEntry.title == track.title) {
                        val copy = track.copy(id = 0, playListId = playListId)
                        playlistItems.add(copy)
                    }
                }
            }

            activity.audioHelper.insertTracks(playlistItems)
            exportedEvents = playlistItems.size
        } catch (e: Exception) {
            failedEvents++
            activity.showErrorToast(e)
        } finally {
            inputStream.close()
        }

        callback(
            when {
                exportedEvents == 0 -> ImportResult.IMPORT_FAIL
                failedEvents > 0 -> ImportResult.IMPORT_PARTIAL
                else -> ImportResult.IMPORT_OK
            }
        )
    }
}
