package studio.koeda.norrklang.media

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.extractor.metadata.icy.IcyInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class RadioNowPlayingListenerTest {

    private class QueuePlayer(vararg items: MediaItem) : FakePlayer() {
        val items = items.toMutableList()
        var index = 0

        override fun getCurrentMediaItem(): MediaItem? = items.getOrNull(index)
        override fun getCurrentMediaItemIndex() = index
        override fun getMediaItemCount() = items.size
        override fun getMediaItemAt(index: Int): MediaItem = items[index]
        override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
            items[index] = mediaItem
        }

        val titles get() = items.map { it.mediaMetadata.title.toString() }
    }

    private fun station(id: String, name: String) = MediaItem.Builder()
        .setMediaId(MediaId.RadioStation(id).encode())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(name).setStation(name).build())
        .build()

    private fun track(id: String, title: String) = MediaItem.Builder()
        .setMediaId(MediaId.Track(id).encode())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

    private fun icy(title: String) = Metadata(IcyInfo(ByteArray(0), title, null))

    private fun position(player: QueuePlayer, index: Int, item: MediaItem? = player.items[index]) =
        Player.PositionInfo(null, index, item, null, index, 0L, 0L, -1, -1)

    /** Moves [player] to [to] and reports the discontinuity the real player would. */
    private fun RadioNowPlayingListener.skip(player: QueuePlayer, to: Int) {
        val old = position(player, player.index)
        player.index = to
        onPositionDiscontinuity(old, position(player, to), Player.DISCONTINUITY_REASON_SEEK)
    }

    @Test
    fun `the stream's song replaces the current station's title`() {
        val player = QueuePlayer(station("rs-1", "KEXP"), station("rs-2", "FIP")).apply { index = 1 }
        RadioNowPlayingListener(player).onMetadata(icy(" Air - La femme d'argent "))

        assertEquals(listOf("KEXP", "Air - La femme d'argent"), player.titles)
    }

    @Test
    fun `a track's title is never replaced`() {
        val player = QueuePlayer(track("tr-1", "Song"))
        RadioNowPlayingListener(player).onMetadata(icy("Not for this item"))

        assertEquals(listOf("Song"), player.titles)
    }

    @Test
    fun `a station left behind gets its own name back`() {
        val player = QueuePlayer(station("rs-1", "KEXP"), station("rs-2", "FIP"))
        val listener = RadioNowPlayingListener(player)
        listener.onMetadata(icy("Song on KEXP"))
        listener.skip(player, to = 1)

        assertEquals(listOf("KEXP", "FIP"), player.titles)
    }

    @Test
    fun `a replaced queue is not renamed by the old queue's position`() {
        val player = QueuePlayer(station("rs-1", "KEXP"), station("rs-2", "FIP"))
        val listener = RadioNowPlayingListener(player)
        listener.onMetadata(icy("Song on KEXP"))
        val old = position(player, 0)

        // Shorter queue: the old index is gone.
        player.items.clear()
        listener.onPositionDiscontinuity(old, position(player, 0, item = null), Player.DISCONTINUITY_REASON_REMOVE)

        // Same index, unrelated item.
        player.items += listOf(track("tr-1", "Song"), track("tr-2", "Other"))
        player.index = 1
        listener.onPositionDiscontinuity(old, position(player, 1), Player.DISCONTINUITY_REASON_REMOVE)

        assertEquals(listOf("Song", "Other"), player.titles)
    }
}
