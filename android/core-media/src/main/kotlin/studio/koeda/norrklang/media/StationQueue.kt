package studio.koeda.norrklang.media

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import studio.koeda.norrklang.data.model.RadioStation
import studio.koeda.norrklang.data.repo.MusicException
import studio.koeda.norrklang.data.repo.MusicRepository

/**
 * The queue a radio station plays in: every station in Radio-tab order, so
 * skip next/previous change station (see [StationPlaybackMode]).
 */
@OptIn(UnstableApi::class)
internal class StationQueue(
    private val repository: MusicRepository,
    private val buildStation: (RadioStation) -> MediaItem,
) {

    /** @throws MusicException.NotFound when the server no longer has [stationId]. */
    suspend fun startingAt(stationId: String): MediaItemsWithStartPosition {
        val stations = repository.radioStations()
        val index = stations.indexOfFirst { it.id == stationId }
        if (index < 0) throw MusicException.NotFound("Radio station $stationId not found")
        // A live stream has no position to return to.
        return MediaItemsWithStartPosition(stations.map(buildStation), index, C.TIME_UNSET)
    }
}
