package studio.koeda.norrklang.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.koeda.norrklang.data.settings.TrackSubtitle
import studio.koeda.norrklang.ui.R
import studio.koeda.norrklang.ui.components.BackButton
import studio.koeda.norrklang.ui.theme.LocalFormDimens

/**
 * Now-playing layout sub-page: the line under a song's title (see
 * [TrackSubtitle]) and which line carries a radio stream's song. Both apply
 * at once, the playing item included.
 *
 * The radio choice is a boolean underneath but is shown as two radio options
 * so the two sections read the same way.
 */
@Composable
internal fun NowPlayingPage(
    trackSubtitle: TrackSubtitle,
    radioStationFirst: Boolean,
    onTrackSubtitleSelected: (TrackSubtitle) -> Unit,
    onRadioStationFirstSelected: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalFormDimens.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPadding, vertical = dimens.itemSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(
                text = stringResource(R.string.settings_now_playing),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_now_playing_page_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = dimens.itemSpacing / 2),
        )
        Spacer(Modifier.height(dimens.itemSpacing))
        HorizontalDivider()

        Section(title = stringResource(R.string.settings_now_playing_songs)) {
            TrackSubtitle.entries.forEach { subtitle ->
                OptionRow(
                    label = stringResource(subtitle.labelRes()),
                    hint = stringResource(
                        if (subtitle == TrackSubtitle.ARTIST) {
                            R.string.settings_track_subtitle_artist_hint
                        } else {
                            R.string.settings_track_subtitle_album_hint
                        },
                    ),
                    selected = subtitle == trackSubtitle,
                    onSelected = { onTrackSubtitleSelected(subtitle) },
                )
            }
        }
        HorizontalDivider()
        Section(title = stringResource(R.string.settings_now_playing_radio)) {
            OptionRow(
                label = stringResource(R.string.settings_radio_song_first),
                hint = stringResource(R.string.settings_radio_song_first_hint),
                selected = !radioStationFirst,
                onSelected = { onRadioStationFirstSelected(false) },
            )
            OptionRow(
                label = stringResource(R.string.settings_radio_station_first),
                hint = stringResource(R.string.settings_radio_station_first_hint),
                selected = radioStationFirst,
                onSelected = { onRadioStationFirstSelected(true) },
            )
        }
    }
}

@Composable
private fun Section(title: String, options: @Composable () -> Unit) {
    val dimens = LocalFormDimens.current
    Column(modifier = Modifier.selectableGroup()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = dimens.itemSpacing),
        )
        options()
        Spacer(Modifier.height(dimens.itemSpacing / 2))
    }
}

@Composable
private fun OptionRow(
    label: String,
    hint: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val dimens = LocalFormDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Whole row selects, as in StreamQualityPage.
            .selectable(selected = selected, onClick = onSelected, role = Role.RadioButton)
            .padding(vertical = dimens.itemSpacing / 2),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = dimens.itemSpacing)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun TrackSubtitle.labelRes(): Int = when (this) {
    TrackSubtitle.ARTIST -> R.string.settings_track_subtitle_artist
    TrackSubtitle.ARTIST_ALBUM -> R.string.settings_track_subtitle_artist_album
    TrackSubtitle.ALBUM_ARTIST -> R.string.settings_track_subtitle_album_artist
}
