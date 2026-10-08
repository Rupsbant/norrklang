package studio.koeda.norrklang.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals

class StationPlaybackModeTest {

    private class ModePlayer : FakePlayer() {
        var repeat = Player.REPEAT_MODE_OFF
        override fun getRepeatMode() = repeat
        override fun setRepeatMode(repeatMode: Int) {
            repeat = repeatMode
        }
    }

    private val player = ModePlayer()
    private val preload = mutableListOf<Boolean>()
    private val mode = StationPlaybackMode(player) { preload += it }

    private fun transitionTo(mediaId: MediaId?) = mode.onMediaItemTransition(
        mediaId?.let { MediaItem.Builder().setMediaId(it.encode()).build() },
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
    )

    @Test
    fun `a station turns preloading off and repeat-one on, once`() {
        transitionTo(MediaId.RadioStation("rs-1"))
        transitionTo(MediaId.RadioStation("rs-2"))

        assertEquals(listOf(false), preload)
        assertEquals(Player.REPEAT_MODE_ONE, player.repeat)
    }

    @Test
    fun `leaving radio restores preloading and repeat-off`() {
        transitionTo(MediaId.RadioStation("rs-1"))
        transitionTo(MediaId.Track("tr-1"))

        assertEquals(listOf(false, true), preload)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeat)

        transitionTo(MediaId.RadioStation("rs-1"))
        transitionTo(null)

        assertEquals(listOf(false, true, false, true), preload)
        assertEquals(Player.REPEAT_MODE_OFF, player.repeat)
    }

    @Test
    fun `a repeat mode set between tracks is left alone`() {
        transitionTo(MediaId.Track("tr-1"))
        player.repeat = Player.REPEAT_MODE_ALL
        transitionTo(MediaId.Track("tr-2"))

        assertEquals(emptyList(), preload)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeat)
    }
}
