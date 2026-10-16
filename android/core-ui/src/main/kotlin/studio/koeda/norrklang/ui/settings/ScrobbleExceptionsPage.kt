package studio.koeda.norrklang.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import studio.koeda.norrklang.data.settings.ServerSettingsRepository.ScrobbleSettings
import studio.koeda.norrklang.ui.R
import studio.koeda.norrklang.ui.components.BackButton
import studio.koeda.norrklang.ui.theme.LocalFormDimens

/**
 * Hub for the play-reporting exclusions, one row per kind (see
 * [ScrobbleExclusionScreen]). Kept off the main page: most users never
 * touch these, and three rows there read as three more settings.
 */
@Composable
internal fun ScrobbleExceptionsPage(
    scrobble: ScrobbleSettings,
    onArtists: () -> Unit,
    onPlaylists: () -> Unit,
    onLibraries: () -> Unit,
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
                text = stringResource(R.string.settings_scrobble_exceptions),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_scrobble_exceptions_page_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = dimens.itemSpacing / 2),
        )
        Spacer(Modifier.height(dimens.itemSpacing))
        HorizontalDivider()
        ExclusionRow(
            title = stringResource(R.string.settings_scrobble_artists),
            subtitle = stringResource(R.string.settings_scrobble_artists_hint),
            excludedCount = scrobble.excludedArtistIds.size,
            onClick = onArtists,
        )
        HorizontalDivider()
        ExclusionRow(
            title = stringResource(R.string.settings_scrobble_playlists),
            subtitle = stringResource(R.string.settings_scrobble_playlists_hint),
            excludedCount = scrobble.excludedPlaylistIds.size,
            onClick = onPlaylists,
        )
        HorizontalDivider()
        ExclusionRow(
            title = stringResource(R.string.settings_scrobble_libraries),
            subtitle = stringResource(R.string.settings_scrobble_libraries_hint),
            excludedCount = scrobble.excludedLibraryIds.size,
            onClick = onLibraries,
        )
        HorizontalDivider()
    }
}
