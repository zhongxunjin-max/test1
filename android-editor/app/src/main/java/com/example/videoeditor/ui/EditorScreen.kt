package com.example.videoeditor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoeditor.EditorViewModel
import com.example.videoeditor.R
import com.example.videoeditor.export.toEffects
import com.example.videoeditor.export.toMediaItem
import com.example.videoeditor.model.Clip
import com.example.videoeditor.model.ExportState
import com.example.videoeditor.model.Filter
import com.example.videoeditor.model.MusicTrack
import com.example.videoeditor.model.Transition
import com.example.videoeditor.model.formatMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        viewModel.addClips(it)
    }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::setMusic)
    }
    val exporting = state.export is ExportState.Running

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(
                        onClick = viewModel::export,
                        enabled = state.clips.isNotEmpty() && !exporting,
                    ) { Text(stringResource(R.string.export)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (state.selected == null) {
                    Text(stringResource(R.string.empty_hint))
                } else {
                    PreviewPlayer(state.selected, Modifier.fillMaxSize())
                }
            }

            ExportStatus(state.export, viewModel::dismissExportResult)

            Timeline(
                clips = state.clips,
                selectedId = state.selectedId,
                onSelect = viewModel::select,
                onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            )

            Column(
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.selected?.let { clip ->
                    ClipControls(
                        clip = clip,
                        isFirst = state.clips.firstOrNull()?.id == clip.id,
                        onFilter = { viewModel.setFilter(clip.id, it) },
                        onTransition = { viewModel.setTransition(clip.id, it) },
                        onTrim = { s, e -> viewModel.setTrim(clip.id, s, e) },
                        onCaption = { viewModel.setCaption(clip.id, it) },
                        onMove = { viewModel.move(clip.id, it) },
                        onRemove = { viewModel.remove(clip.id) },
                    )
                }
                if (state.clips.isNotEmpty()) {
                    AudioControls(
                        music = state.music,
                        originalVolume = state.originalVolume,
                        onPickMusic = { musicPicker.launch(arrayOf("audio/*")) },
                        onRemoveMusic = viewModel::removeMusic,
                        onMusicVolume = viewModel::setMusicVolume,
                        onOriginalVolume = viewModel::setOriginalVolume,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPlayer(clip: Clip?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(clip?.filter) {
        player.setVideoEffects(clip?.filter?.toEffects().orEmpty())
    }
    LaunchedEffect(clip?.id, clip?.startMs, clip?.endMs) {
        if (clip == null) {
            player.clearMediaItems()
        } else {
            player.setMediaItem(clip.toMediaItem())
            player.prepare()
        }
    }
    AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = modifier)
}

@Composable
private fun ExportStatus(export: ExportState, onDismiss: () -> Unit) {
    when (export) {
        ExportState.Idle -> Unit
        is ExportState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.exporting, (export.progress * 100).toInt()))
            LinearProgressIndicator(progress = { export.progress }, modifier = Modifier.fillMaxWidth())
        }
        ExportState.Done -> Text(
            stringResource(R.string.export_done),
            Modifier.clickable(onClick = onDismiss),
        )
        is ExportState.Failed -> Text(
            stringResource(R.string.export_failed, export.message),
            Modifier.clickable(onClick = onDismiss),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Timeline(
    clips: List<Clip>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(clips, key = { it.id }) { clip ->
            val index = clips.indexOf(clip) + 1
            Card(
                onClick = { onSelect(clip.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (clip.id == selectedId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Text(
                    stringResource(R.string.clip_label, index, formatMs(clip.trimmedMs)),
                    Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                )
            }
        }
        item { Button(onClick = onAdd) { Text(stringResource(R.string.add_clips)) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipControls(
    clip: Clip,
    isFirst: Boolean,
    onFilter: (Filter) -> Unit,
    onTransition: (Transition) -> Unit,
    onTrim: (Long, Long) -> Unit,
    onCaption: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    // Slider position is local while dragging; the preview and model update on release.
    var range by remember(clip.id, clip.startMs, clip.endMs) {
        mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                R.string.trim_range,
                formatMs(range.start.toLong()),
                formatMs(range.endInclusive.toLong()),
            )
        )
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..clip.durationMs.toFloat(),
            onValueChangeFinished = { onTrim(range.start.toLong(), range.endInclusive.toLong()) },
        )
        Text(stringResource(R.string.filter_title))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Filter.entries) { filter ->
                FilterChip(
                    selected = clip.filter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(stringResource(filter.labelRes())) },
                )
            }
        }
        Text(stringResource(if (isFirst) R.string.transition_first_clip else R.string.transition_title))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Transition.entries) { transition ->
                FilterChip(
                    selected = clip.transition == transition && !isFirst,
                    onClick = { onTransition(transition) },
                    enabled = !isFirst,
                    label = { Text(stringResource(transition.labelRes())) },
                )
            }
        }
        OutlinedTextField(
            value = clip.caption,
            onValueChange = onCaption,
            label = { Text(stringResource(R.string.caption)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMove(-1) }) { Text(stringResource(R.string.move_left)) }
            OutlinedButton(onClick = { onMove(1) }) { Text(stringResource(R.string.move_right)) }
            OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
        }
    }
}

@StringRes
private fun Filter.labelRes(): Int = when (this) {
    Filter.ORIGINAL -> R.string.filter_original
    Filter.MONO -> R.string.filter_mono
    Filter.SEPIA -> R.string.filter_sepia
    Filter.WARM -> R.string.filter_warm
    Filter.COOL -> R.string.filter_cool
    Filter.VIVID -> R.string.filter_vivid
}

@StringRes
private fun Transition.labelRes(): Int = when (this) {
    Transition.NONE -> R.string.transition_none
    Transition.FADE -> R.string.transition_fade
    Transition.SLIDE -> R.string.transition_slide
    Transition.ZOOM -> R.string.transition_zoom
}

@Composable
private fun AudioControls(
    music: MusicTrack?,
    originalVolume: Float,
    onPickMusic: () -> Unit,
    onRemoveMusic: () -> Unit,
    onMusicVolume: (Float) -> Unit,
    onOriginalVolume: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.music_title))
        if (music == null) {
            OutlinedButton(onClick = onPickMusic) { Text(stringResource(R.string.add_music)) }
        } else {
            Text(stringResource(R.string.music_label, music.name, formatMs(music.durationMs)))
            Text(stringResource(R.string.music_volume, (music.volume * 100).toInt()))
            Slider(value = music.volume, onValueChange = onMusicVolume)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickMusic) { Text(stringResource(R.string.change_music)) }
                OutlinedButton(onClick = onRemoveMusic) { Text(stringResource(R.string.remove_music)) }
            }
            Text(stringResource(R.string.music_hint), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.original_volume, (originalVolume * 100).toInt()))
        Slider(value = originalVolume, onValueChange = onOriginalVolume)
    }
}
