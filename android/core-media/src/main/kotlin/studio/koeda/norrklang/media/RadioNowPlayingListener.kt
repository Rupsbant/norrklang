package studio.koeda.norrklang.media

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo

/**
 * Shows the song a station is playing: ExoPlayer decodes the stream's ICY
 * "StreamTitle" into [IcyInfo], but the item's own title always wins in
 * the player's merged metadata — so the current item is re-issued with the
 * ICY title. Same URI, so ExoPlayer updates it in place without a reload.
 *
 * A station left for another queue item gets its own name back, so the
 * queue lists stations rather than the songs they last played.
 */
@OptIn(UnstableApi::class) // Metadata, IcyInfo
internal class RadioNowPlayingListener(private val player: Player) : Player.Listener {

    override fun onMetadata(metadata: Metadata) {
        val item = player.currentMediaItem ?: return
        if (MediaId.parse(item.mediaId) !is MediaId.RadioStation) return
        val title = (0 until metadata.length())
            .asSequence()
            .map(metadata::get)
            .filterIsInstance<IcyInfo>()
            .firstNotNullOfOrNull { it.title?.trim()?.takeIf(String::isNotEmpty) }
            ?: return
        if (item.mediaMetadata.title == title) return
        player.replaceMediaItem(player.currentMediaItemIndex, item.withTitle(title))
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val index = oldPosition.mediaItemIndex
        if (index == newPosition.mediaItemIndex || index >= player.mediaItemCount) return
        val item = player.getMediaItemAt(index)
        // A replaced queue reuses the index for an unrelated item.
        if (item.mediaId != oldPosition.mediaItem?.mediaId) return
        if (MediaId.parse(item.mediaId) !is MediaId.RadioStation) return
        val station = item.mediaMetadata.station ?: return
        if (item.mediaMetadata.title == station) return
        player.replaceMediaItem(index, item.withTitle(station))
    }

    private fun MediaItem.withTitle(title: CharSequence): MediaItem =
        buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setTitle(title).build()).build()
}
