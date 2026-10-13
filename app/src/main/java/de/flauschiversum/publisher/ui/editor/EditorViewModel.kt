package de.flauschiversum.publisher.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.git.DraftWorkflow
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.git.TreeChange
import de.flauschiversum.publisher.posts.EditorSnapshotStore
import de.flauschiversum.publisher.posts.FieldError
import de.flauschiversum.publisher.posts.Fields
import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostEditing
import de.flauschiversum.publisher.posts.PostNaming
import de.flauschiversum.publisher.posts.PostProps
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.posts.TagsFile
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUi(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val dir: String? = null,
    val title: String = "",
    val date: PostDate? = null,
    val permalink: String = "",
    val permalinkAuto: Boolean = true,
    val tags: List<String> = emptyList(),
    val author: String = "",
    val excerpt: String = "",
    val image: String? = null,
    val body: String = "",
    val allowedTags: List<String> = emptyList(),
    val imageFiles: List<String> = emptyList(),
    val errors: List<FieldError> = emptyList(),
    val showProperties: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
    val restored: Boolean = false,
    val loadProblem: String? = null,
) {
    fun error(field: String): String? = errors.firstOrNull { it.field == field }?.message
    val props: PostProps get() = PostProps(title, date, permalink.takeIf { it.isNotBlank() }, tags, author.takeIf { it.isNotBlank() }, image, excerpt.takeIf { it.isNotBlank() })
}

/** Beitrag schreiben und bearbeiten: Text im Editor, Felder im Menü „Eigenschaften“ (Specs `beitrag-erstellen`, `beitrag-bearbeiten`). */
class EditorViewModel(private val c: AppContainer, private val handle: SavedStateHandle, private val dir: String?) : ViewModel() {
    private val key = dir ?: "_neu"
    private val snapshots = EditorSnapshotStore(File(c.context.filesDir, "editor"))
    private val _ui = MutableStateFlow(EditorUi(isNew = dir == null, dir = dir))
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()
    private var original: PostProps? = null
    private var baseDoc: PostDocument? = null
    private var otherPermalinks: Map<String, String> = emptyMap()
    private var existingDirs: Set<String> = emptySet()
    private var backupJob: Job? = null

    init { load() }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            c.requestSync(SyncReason.OPEN_POST)
            val wf = c.session.workflow()
            if (wf == null) { _ui.update { it.copy(loaded = true, loadProblem = "Das Repository ist noch nicht eingerichtet.") }; return@launch }
            val loaded = c.session.locked { readState(wf) }
            val snapshot = snapshots.load(key)
            _ui.update { snapshot?.let { s -> restore(loaded, s) } ?: loaded }
        }
    }

    private fun readState(wf: DraftWorkflow): EditorUi {
        val reader = PostReader(wf.store)
        val overview = wf.overview()
        val rev = dir?.let { wf.revFor(it, overview) } ?: wf.baseRev()
        val allowed = wf.store.readText(wf.baseRev(), "content/tags.yaml")?.let { TagsFile.names(it) }.orEmpty()
        existingDirs = (reader.dirNames(wf.refs.target) + (if (wf.hasDraft()) reader.dirNames(wf.refs.draft) else emptyList())).toSet()
        otherPermalinks = (reader.summaries(wf.refs.target) + (if (wf.hasDraft()) reader.summaries(wf.refs.draft) else emptyList()))
            .filter { it.dir != dir && it.permalink != null }.associate { it.permalink!! to it.title }
        if (dir == null) {
            val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
            val date = PostDate(today, null)
            original = null; baseDoc = null
            return EditorUi(loaded = true, isNew = true, date = date, author = c.settingsSnapshot.defaultAuthor, allowedTags = allowed, permalink = "", permalinkAuto = true)
        }
        val doc = reader.document(rev, dir) ?: return EditorUi(loaded = true, isNew = false, dir = dir, loadProblem = "Der Beitrag „$dir“ wurde nicht gefunden.")
        if (!doc.hasFrontmatter) return EditorUi(loaded = true, isNew = false, dir = dir, loadProblem = "Der Beitrag „$dir“ hat kein lesbares Frontmatter und lässt sich hier nicht bearbeiten.")
        val props = runCatching { PostEditing.read(doc) }.getOrElse {
            return EditorUi(loaded = true, isNew = false, dir = dir, loadProblem = "Das Frontmatter von „$dir“ ist ungültig: ${it.message?.lineSequence()?.firstOrNull()}")
        }
        original = props; baseDoc = doc
        val images = reader.files(rev, dir).filter { f -> f.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif") }
        return EditorUi(
            loaded = true, isNew = false, dir = dir, title = props.title, date = props.date, permalink = props.permalink.orEmpty(), permalinkAuto = false,
            tags = props.tags, author = props.author.orEmpty(), excerpt = props.excerpt.orEmpty(), image = props.image, body = doc.body,
            allowedTags = allowed, imageFiles = images,
        )
    }

    private fun restore(base: EditorUi, s: Map<String, String>): EditorUi = base.copy(
        title = s["title"] ?: base.title, body = s["body"] ?: base.body, permalink = s["permalink"] ?: base.permalink,
        permalinkAuto = s["permalinkAuto"]?.toBooleanStrictOrNull() ?: base.permalinkAuto,
        tags = s["tags"]?.split('\n')?.filter { it.isNotEmpty() } ?: base.tags, author = s["author"] ?: base.author,
        excerpt = s["excerpt"] ?: base.excerpt, date = PostDate.parse(s["date"]) ?: base.date, image = s["image"]?.ifEmpty { null } ?: base.image,
        restored = true,
    )

    private fun backup() {
        backupJob?.cancel()
        backupJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            val u = _ui.value
            if (!u.loaded || u.saved) return@launch
            snapshots.save(key, mapOf(
                "title" to u.title, "body" to u.body, "permalink" to u.permalink, "permalinkAuto" to u.permalinkAuto.toString(),
                "tags" to u.tags.joinToString("\n"), "author" to u.author, "excerpt" to u.excerpt, "date" to (u.date?.render() ?: ""), "image" to (u.image ?: ""),
            ))
        }
    }

    private fun edit(transform: (EditorUi) -> EditorUi) { _ui.update { transform(it).copy(errors = emptyList()) }; backup() }

    fun setBody(v: String) = edit { it.copy(body = v) }
    fun setTitle(v: String) = edit { it.copy(title = v).withAutoPermalink() }
    fun setDate(v: PostDate) = edit { it.copy(date = v).withAutoPermalink() }
    fun setPermalink(v: String) = edit { it.copy(permalink = v, permalinkAuto = false) }
    fun useAutoPermalink() = edit { it.copy(permalinkAuto = true).withAutoPermalink() }
    fun setAuthor(v: String) = edit { it.copy(author = v) }
    fun setExcerpt(v: String) = edit { it.copy(excerpt = v) }
    fun setImage(v: String?) = edit { it.copy(image = v) }
    fun toggleTag(t: String) = edit { it.copy(tags = if (t in it.tags) it.tags - t else it.tags + t) }
    fun showProperties(show: Boolean) { _ui.update { it.copy(showProperties = show) } }
    fun messageShown() { _ui.update { it.copy(message = null) } }

    /** Insert einer Bildzeile an der Cursorposition ist Sache der Oberfläche; hier nur Hilfe für Verzeichnisnamen. */
    private fun EditorUi.withAutoPermalink(): EditorUi {
        if (!permalinkAuto || !isNew) return this
        val d = date ?: return this
        val name = PostNaming.dirNameFromTitle(title.ifBlank { "beitrag" })
        return copy(permalink = PostNaming.permalink(d.date, uniqueName(name, d.date.year)))
    }

    private fun uniqueName(base: String, year: Int) = PostNaming.uniqueDirName(base, existingDirs, year)

    fun discardChanges(onDone: () -> Unit) { snapshots.delete(key); onDone() }

    fun save() {
        val u = _ui.value
        if (u.saving) return
        val allowed = u.allowedTags
        val tags = PostEditing.canonicalTags(u.tags, allowed)
        val props = u.props.copy(tags = tags)
        val errors = PostEditing.validate(props, allowed, otherPermalinks, if (u.isNew) null else u.imageFiles)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errors = errors, showProperties = errors.any { e -> e.field != Fields.TITLE } || it.showProperties, message = errors.first().message) }; return }
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val wf = c.session.workflow()
            if (wf == null) { _ui.update { it.copy(saving = false, message = "Das Repository ist nicht eingerichtet.") }; return@launch }
            try {
                c.session.locked {
                    val name = u.dir ?: PostNaming.dirNameFromTitle(props.title).let { PostNaming.uniqueDirName(it, existingDirsNow(wf), props.date!!.date.year) }
                    val perma = if (u.isNew && props.permalink == null) PostNaming.permalink(props.date!!.date, name) else props.permalink
                    val doc = PostEditing.apply(baseDoc, original, props.copy(permalink = perma), u.body)
                    val msg = if (u.isNew) "Beitrag: ${props.title}" else "Beitrag geändert: ${props.title}"
                    wf.commit(listOf(TreeChange.Put("${PostReader.POSTS_DIR}/$name/index.md", doc.text.toByteArray(Charsets.UTF_8))), msg)
                }
                snapshots.delete(key)
                c.uploadDraftInBackground()
                _ui.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(saving = false, message = c.session.classify(e).let { f -> if (f is GitFailure.Other) "Speichern fehlgeschlagen: ${f.message}" else f.message }) }
            }
        }
    }

    private fun existingDirsNow(wf: DraftWorkflow): Collection<String> {
        val reader = PostReader(wf.store)
        return reader.dirNames(wf.refs.target) + (if (wf.hasDraft()) reader.dirNames(wf.refs.draft) else emptyList())
    }
}
