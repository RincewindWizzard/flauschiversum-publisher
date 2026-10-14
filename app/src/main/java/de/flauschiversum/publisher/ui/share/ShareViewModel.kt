package de.flauschiversum.publisher.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.PhotoWithAlt
import de.flauschiversum.publisher.photos.SharedPhoto
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.posts.PostSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUi(
    val photos: List<SharedPhoto> = emptyList(),
    val alts: List<String> = emptyList(),
    val posts: List<PostSummary> = emptyList(),
    val query: String = "",
    val newTitle: String = "",
    /** Gewählter vorhandener Beitrag; null: neuer Beitrag. */
    val selectedDir: String? = null,
    val creatingNew: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    val message: String? = null,
) {
    val canSave: Boolean get() = !saving && photos.isNotEmpty() && (if (creatingNew) newTitle.isNotBlank() else selectedDir != null)
    val filtered: List<PostSummary> get() = posts.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
}

/** Zuordnung geteilter Fotos zu einem Beitrag (Spec `foto-teilen`). */
class ShareViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(ShareUi())
    val ui: StateFlow<ShareUi> = _ui.asStateFlow()

    init {
        c.incoming.targetDir.value?.let { d -> _ui.update { it.copy(selectedDir = d, creatingNew = false) } }
        viewModelScope.launch { c.incoming.pending.collect { p -> _ui.update { it.copy(photos = p, alts = List(p.size) { i -> it.alts.getOrNull(i).orEmpty() }) } } }
        viewModelScope.launch(Dispatchers.IO) {
            val posts = runCatching { c.session.locked {
                val wf = c.session.workflow() ?: return@locked emptyList()
                val reader = PostReader(wf.store)
                val rev = wf.baseRev()
                reader.summaries(rev)
            } }.getOrDefault(emptyList())
            _ui.update { it.copy(posts = posts) }
        }
    }

    fun setAlt(index: Int, v: String) = _ui.update { u -> u.copy(alts = u.alts.toMutableList().also { if (index in it.indices) it[index] = v }) }
    fun setQuery(v: String) = _ui.update { it.copy(query = v) }
    fun select(dir: String) = _ui.update { it.copy(selectedDir = dir, creatingNew = false) }
    fun chooseNew() = _ui.update { it.copy(creatingNew = true, selectedDir = null) }
    fun setNewTitle(v: String) = _ui.update { it.copy(newTitle = v) }
    fun messageShown() = _ui.update { it.copy(message = null) }

    /** Bricht ab: nichts wird geändert, die Kopien werden gelöscht. */
    fun cancel() { c.incoming.clear(); _ui.update { it.copy(done = true) } }

    /** Sichert die Fotos als Hintergrundauftrag und kehrt sofort zurück; Commit und Upload laufen danach im Hintergrund. */
    fun save() {
        val u = _ui.value
        if (!u.canSave) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val target = if (u.creatingNew) PhotoTarget.New(u.newTitle, c.settingsSnapshot.defaultAuthor) else PhotoTarget.Existing(u.selectedDir!!)
                c.uploads.submit(u.photos, u.alts, target)
                c.incoming.release()
                _ui.update { it.copy(saving = false, done = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, message = "Sichern fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }
}
