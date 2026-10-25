package com.example.videoeditor

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoeditor.export.Exporter
import com.example.videoeditor.model.Clip
import com.example.videoeditor.model.EditorState
import com.example.videoeditor.model.ExportState
import com.example.videoeditor.model.moved
import com.example.videoeditor.model.withTrim
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val exporter = Exporter(application)
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var nextId = 1L
    private var exportJob: Job? = null

    fun addClips(uris: List<Uri>) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> readDurationMs(uri)?.let { uri to it } }
            }.map { (uri, duration) -> Clip(id = nextId++, uri = uri.toString(), durationMs = duration) }
            if (loaded.isEmpty()) return@launch
            _state.update { s ->
                s.copy(clips = s.clips + loaded, selectedId = s.selectedId ?: loaded.first().id)
            }
        }
    }

    fun select(id: Long) = _state.update { it.copy(selectedId = id) }

    fun setTrim(id: Long, startMs: Long, endMs: Long) = updateClip(id) { it.withTrim(startMs, endMs) }

    fun setCaption(id: Long, caption: String) = updateClip(id) { it.copy(caption = caption) }

    fun move(id: Long, delta: Int) = _state.update { it.copy(clips = it.clips.moved(id, delta)) }

    fun remove(id: Long) = _state.update { s ->
        val clips = s.clips.filterNot { it.id == id }
        s.copy(clips = clips, selectedId = if (s.selectedId == id) clips.firstOrNull()?.id else s.selectedId)
    }

    fun export() {
        val clips = _state.value.clips
        if (clips.isEmpty() || exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            _state.update { it.copy(export = ExportState.Running(0f)) }
            val result = try {
                exporter.export(clips) { p -> _state.update { it.copy(export = ExportState.Running(p)) } }
                ExportState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportState.Failed(e.message ?: e.javaClass.simpleName)
            }
            _state.update { it.copy(export = result) }
        }
    }

    fun dismissExportResult() = _state.update {
        if (it.export is ExportState.Running) it else it.copy(export = ExportState.Idle)
    }

    private fun updateClip(id: Long, transform: (Clip) -> Clip) = _state.update { s ->
        s.copy(clips = s.clips.map { if (it.id == id) transform(it) else it })
    }

    private fun readDurationMs(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(getApplication(), uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
