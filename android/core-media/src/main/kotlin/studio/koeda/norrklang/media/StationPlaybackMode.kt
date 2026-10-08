package studio.koeda.norrklang.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * While a station is current: no preloading (the next item is another live
 * stream, stale by the time it plays) and repeat-one (a stream that ends
 * reconnects instead of sliding into the next station; skips ignore it).
 * Acts only on entering or leaving radio, so a repeat mode set for tracks
 * survives.
 */
internal class StationPlaybackMode(
    private val player: Player,
    private val preloadNextItem: (Boolean) -> Unit,
) : Player.Listener {

    private var onStation = false

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val station = mediaItem?.mediaId?.let(MediaId::parse) is MediaId.RadioStation
        if (station == onStation) return
        onStation = station
        preloadNextItem(!station)
        player.repeatMode = if (station) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
}
