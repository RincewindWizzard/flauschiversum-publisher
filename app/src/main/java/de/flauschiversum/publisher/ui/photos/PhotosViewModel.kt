package de.flauschiversum.publisher.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.posts.PhotoInfo
import de.flauschiversum.publisher.posts.PhotoManagement
import de.flauschiversum.publisher.posts.PhotoMarkdown
import de.flauschiversum.publisher.posts.PostReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hinweise vor dem Entfernen eines Fotos. */
data class RemoveRequest(val photo: PhotoInfo, val inlineMentions: Int)

data class PhotosUi(
    val loading: Boolean = true,
    val title: String = "",
    val photos: List<PhotoInfo> = emptyList(),
    /** Alt-Text-Eingaben je Datei (noch nicht gespeichert). */
    val altEdits: Map<String, String> = emptyMap(),
    val remove: RemoveRequest? = null,
    val message: String? = null,
    val rev: String = "",
)

/** Fotobildschirm eines Beitrags (Spec `foto-verwaltung`). */
class PhotosViewModel(val c: AppContainer, private val dir: String) : ViewModel() {
    val dirName: String get() = dir
    private val _ui = MutableStateFlow(PhotosUi())
    val ui: StateFlow<PhotosUi> = _ui.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val wf = c.session.workflow() ?: return@launch
            val (photos, title, rev) = c.session.locked {
                Triple(PhotoManagement.photos(wf, dir), PostReader(wf.store).summary(wf.baseRev(), dir)?.title ?: dir, wf.baseRev())
            }
            _ui.update { it.copy(loading = false, photos = photos, title = title, rev = rev, altEdits = emptyMap()) }
        }
    }

    fun editAlt(file: String, text: String) = _ui.update { it.copy(altEdits = it.altEdits + (file to text)) }
    fun altFor(p: PhotoInfo): String = _ui.value.altEdits[p.file] ?: p.alt
    fun messageShown() = _ui.update { it.copy(message = null) }

    private fun change(f: (wf: de.flauschiversum.publisher.git.DraftWorkflow) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wf = c.session.workflow() ?: error("Das Repository ist nicht eingerichtet.")
                c.session.locked { f(wf) }
                c.uploadDraftInBackground()
                reload()
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun saveAlt(file: String) { val alt = _ui.value.altEdits[file] ?: return; change { PhotoManagement.setAlt(it, dir, file, alt) } }
    fun include(file: String) { val alt = _ui.value.altEdits[file].orEmpty(); change { PhotoManagement.include(it, dir, file, alt) } }
    fun move(file: String, delta: Int) {
        val order = _ui.value.photos.filter { it.inAttachments }.map { it.file }
        val from = order.indexOf(file); if (from < 0) return
        val to = (from + delta).coerceIn(0, order.size - 1)
        if (to != from) change { PhotoManagement.move(it, dir, file, to) }
    }
    fun setCover(file: String?) = change { PhotoManagement.setCover(it, dir, file) }

    fun askRemove(photo: PhotoInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val wf = c.session.workflow() ?: return@launch
            val inline = c.session.locked { PostReader(wf.store).document(wf.baseRev(), dir)?.let { PhotoMarkdown.inlineMentions(it.body, photo.file) } ?: 0 }
            _ui.update { it.copy(remove = RemoveRequest(photo, inline)) }
        }
    }
    fun cancelRemove() = _ui.update { it.copy(remove = null) }

    /** Entfernt das Foto; [newCover] ist bei einem Titelbild nötig (null = kein Titelbild). */
    fun confirmRemove(newCover: String?, coverChosen: Boolean) {
        val r = _ui.value.remove ?: return
        _ui.update { it.copy(remove = null) }
        change { wf ->
            if (r.photo.isCover) {
                require(coverChosen) { "Bitte zuerst ein anderes Titelbild oder „kein Titelbild“ wählen." }
                PhotoManagement.setCover(wf, dir, newCover)
            }
            PhotoManagement.remove(wf, dir, r.photo.file)
        }
    }
}
