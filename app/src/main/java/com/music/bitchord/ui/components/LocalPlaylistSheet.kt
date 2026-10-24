package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.ui.icons.BitChordIcons
import java.util.Locale

/**
 * Where device tracks go: one of the playlists kept on this phone, or a new
 * one. These never touch a YouTube account, so they work signed out and offline.
 *
 * [songs] is one track from a row's menu or the whole multi-selection of
 * Local Music. [onDone] reports how many actually went in (tracks already in
 * the playlist are skipped) and which playlist took them.
 */
@Composable
fun LocalPlaylistSheet(
    songs: List<Song>,
    playlists: List<LocalPlaylist>,
    onDone: (added: Int, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        var name by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val submit = {
            val title = name.trim()
            if (title.isNotEmpty()) {
                val created = LocalPlaylistStore.createPlaylist(title)
                onDone(LocalPlaylistStore.addSongs(created.id, songs), created.title)
            }
        }
        Column(
            modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.new_local_playlist),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel)) }
                Button(onClick = submit, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.create_playlist))
                }
            }
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        if (songs.size == 1) {
            SheetTrackHeader(songs.first())
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
        }
        SheetHeading(stringResource(R.string.add_to_local_playlist).uppercase(Locale.getDefault()))
        ActionRow(
            icon = BitChordIcons.Plus,
            label = stringResource(R.string.new_local_playlist),
            onClick = { creating = true },
        )
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                ActionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = playlist.title,
                    value = playlist.songs.size.toString(),
                    onClick = { onDone(LocalPlaylistStore.addSongs(playlist.id, songs), playlist.title) },
                )
            }
        }
    }
}
