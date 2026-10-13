package de.flauschiversum.publisher.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.editor.EditState
import de.flauschiversum.publisher.editor.Finding
import de.flauschiversum.publisher.editor.MarkdownLint
import de.flauschiversum.publisher.editor.Misspelling
import de.flauschiversum.publisher.editor.Severity
import de.flauschiversum.publisher.editor.UndoStack
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
    /** Befunde der Markdown-Prüfung und Rechtschreibung für den Text [analyzedText]. */
    val findings: List<Finding> = emptyList(),
    val misspellings: List<Misspelling> = emptyList(),
    val analyzedText: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showFindings: Boolean = false,
    /** Gesetzt (Zahl der Fehler), wenn vor dem Speichern nachgefragt wird. */
    val confirmSaveErrors: Int? = null,
    val confirmLeave: Boolean = false,
    val suggestionsFor: Misspelling? = null,
    val suggestions: List<String> = emptyList(),
    val knownAuthors: List<String> = emptyList(),
    val excerptMisspellings: List<Misspelling> = emptyList(),
    val tagQuery: String = "",
    /** Der Beitrag hat sich auf dem Server geändert, während der Nutzer schon eigene Änderungen hat. */
    val serverChanged: Boolean = false,
    /** Der Beitrag wurde durch eine neuere Fassung vom Server ersetzt (der Nutzer hatte noch nichts getippt). */
    val updatedFromServer: Boolean = false,
) {
    val errorCount: Int get() = findings.count { it.severity == Severity.ERROR }
    val warningCount: Int get() = findings.count { it.severity == Severity.WARNING }
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
    private var analysisJob: Job? = null
    private var undoStack = UndoStack(EditState("", 0))
    private var originalBody: String = ""
    private var contentRev: String? = null
    private var readOriginal: PostProps? = null
    private var readBase: PostDocument? = null
    private var authors: List<String> = emptyList()

    init {
        c.spell.warmUp(); load()
        if (dir != null) viewModelScope.launch {
            var last = -1L
            c.sync.state.collect { st -> if (st.generation != last) { val first = last < 0; last = st.generation; if (!first) refreshFromServer() } }
        }
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            c.requestSync(SyncReason.OPEN_POST)
            val wf = c.session.workflow()
            if (wf == null) { _ui.update { it.copy(loaded = true, loadProblem = "Das Repository ist noch nicht eingerichtet.") }; return@launch }
            val loaded = c.session.locked { readState(wf) }
            original = readOriginal; baseDoc = readBase
            val snapshot = snapshots.load(key)
            val start = (snapshot?.let { s -> restore(loaded, s) } ?: loaded).let { u -> u.copy(knownAuthors = (listOf(c.settingsSnapshot.defaultAuthor) + authors).distinct()) }
            originalBody = baseDoc?.body.orEmpty()
            undoStack = UndoStack(EditState(start.body, start.body.length))
            _ui.update { start }
            analyze(start.body)
            scheduleExcerptCheck(start.excerpt)
        }
    }

    private fun readState(wf: DraftWorkflow): EditorUi {
        val reader = PostReader(wf.store)
        val overview = wf.overview()
        val rev = dir?.let { wf.revFor(it, overview) } ?: wf.baseRev()
        val allowed = wf.store.readText(wf.baseRev(), "content/tags.yaml")?.let { TagsFile.names(it) }.orEmpty()
        c.spell.setTags(allowed)
        contentRev = rev
        authors = (reader.summaries(wf.refs.target) + (if (wf.hasDraft()) reader.summaries(wf.refs.draft) else emptyList())).mapNotNull { it.author?.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        existingDirs = (reader.dirNames(wf.refs.target) + (if (wf.hasDraft()) reader.dirNames(wf.refs.draft) else emptyList())).toSet()
        otherPermalinks = (reader.summaries(wf.refs.target) + (if (wf.hasDraft()) reader.summaries(wf.refs.draft) else emptyList()))
            .filter { it.dir != dir && it.permalink != null }.associate { it.permalink!! to it.title }
        if (dir == null) {
            val today = LocalDate.now(ZoneId.of("Europe/Berlin"))
            val date = PostDate(today, null)
            readOriginal = null; readBase = null
            return EditorUi(loaded = true, isNew = true, date = date, author = c.settingsSnapshot.defaultAuthor, allowedTags = allowed, permalink = "", permalinkAuto = true)
        }
        val doc = reader.document(rev, dir) ?: return EditorUi(loaded = true, isNew = false, dir = dir, loadProblem = "Der Beitrag „$dir“ wurde nicht gefunden.")
        if (!doc.hasFrontmatter) return EditorUi(loaded = true, isNew = false, dir = dir, loadProblem = "Der Beitrag „$dir“ hat kein lesbares Frontmatter und lässt sich hier nicht bearbeiten.")
        val props = runCatching { PostEditing.read(doc) }.getOrElse {
            return EditorUi(loaded = true, isNew = false, dir = dir, loadProblem = "Das Frontmatter von „$dir“ ist ungültig: ${it.message?.lineSequence()?.firstOrNull()}")
        }
        readOriginal = props; readBase = doc
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

    /** Neuer Text aus dem Editor; [sel] ist der Cursor bzw. die Auswahl danach (für Rückgängig). */
    fun setBody(v: String, selStart: Int = v.length, selEnd: Int = selStart) {
        undoStack.record(EditState(v, selStart, selEnd))
        edit { it.copy(body = v, canUndo = undoStack.canUndo, canRedo = undoStack.canRedo, suggestionsFor = null, suggestions = emptyList()) }
        scheduleAnalysis(v)
    }

    fun undo(): EditState? = undoStack.undo()?.also { applyHistory(it) }
    fun redo(): EditState? = undoStack.redo()?.also { applyHistory(it) }
    private fun applyHistory(st: EditState) {
        edit { it.copy(body = st.text, canUndo = undoStack.canUndo, canRedo = undoStack.canRedo) }
        scheduleAnalysis(st.text)
    }

    // ---- Prüfung im Hintergrund (entprellt)
    private fun scheduleAnalysis(text: String) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch { delay(400); analyze(text) }
    }

    private suspend fun analyze(text: String) {
        val (findings, spelling) = kotlinx.coroutines.withContext(Dispatchers.Default) {
            val lint = MarkdownLint.check(text, originalBody.takeIf { it.isNotEmpty() })
            val sp = c.spell.checker()?.check(text).orEmpty()
            lint to sp
        }
        // Ergebnis zu einem inzwischen geänderten Text wird verworfen
        _ui.update { if (it.body == text) it.copy(findings = findings, misspellings = spelling, analyzedText = text) else it }
    }

    /** Nach einer Aktualisierung: neuere Fassung ohne Eingabe übernehmen, sonst nur auf die Änderung hinweisen. */
    private suspend fun refreshFromServer() {
        val wf = c.session.workflow() ?: return
        val cur = _ui.value
        if (!cur.loaded || cur.loadProblem != null || cur.saved || cur.saving) return
        val fresh = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { c.session.locked { readState(wf) } }.getOrNull() } ?: return
        if (fresh.loadProblem != null) return
        val changed = fresh.body != originalBody || fresh.props != original
        if (!changed) { _ui.update { it.copy(serverChanged = false) }; return }
        if (!isDirty()) {
            original = readOriginal; baseDoc = readBase; originalBody = baseDoc?.body.orEmpty()
            undoStack = UndoStack(EditState(fresh.body, fresh.body.length))
            _ui.update { fresh.copy(knownAuthors = it.knownAuthors, updatedFromServer = true, canUndo = false, canRedo = false) }
            analyze(fresh.body); scheduleExcerptCheck(fresh.excerpt)
        } else _ui.update { it.copy(serverChanged = true) }
    }

    fun showFindings(show: Boolean) { _ui.update { it.copy(showFindings = show) } }

    // ---- Rechtschreibung
    fun requestSuggestions(m: Misspelling) {
        if (_ui.value.suggestionsFor == m) return
        _ui.update { it.copy(suggestionsFor = m, suggestions = emptyList()) }
        viewModelScope.launch {
            val list = kotlinx.coroutines.withContext(Dispatchers.Default) { c.spell.checker()?.suggestions(m.word).orEmpty() }
            _ui.update { if (it.suggestionsFor == m) it.copy(suggestions = list) else it }
        }
    }
    fun clearSuggestions() { _ui.update { it.copy(suggestionsFor = null, suggestions = emptyList()) } }
    fun ignoreWord(word: String) { c.spell.ignore(word); clearSuggestions(); scheduleAnalysis(_ui.value.body) }
    fun addToDictionary(word: String) { c.spell.personal.add(word); clearSuggestions(); scheduleAnalysis(_ui.value.body) }

    /** Wurde etwas gegenüber dem geöffneten Stand geändert? */
    fun isDirty(): Boolean {
        val u = _ui.value
        if (!u.loaded || u.saved || u.loadProblem != null) return false
        return if (u.isNew) u.title.isNotBlank() || u.body.isNotBlank() || u.tags.isNotEmpty() || u.excerpt.isNotBlank()
        else u.body != originalBody || u.props != original
    }

    fun requestLeave(onLeave: () -> Unit) { if (isDirty()) _ui.update { it.copy(confirmLeave = true) } else onLeave() }
    fun cancelLeave() { _ui.update { it.copy(confirmLeave = false) } }
    fun leaveDiscarding(onLeave: () -> Unit) { snapshots.delete(key); _ui.update { it.copy(confirmLeave = false) }; onLeave() }
    fun cancelSaveConfirm() { _ui.update { it.copy(confirmSaveErrors = null) } }
    fun setTitle(v: String) = edit { it.copy(title = v).withAutoPermalink() }
    fun setDate(v: PostDate) = edit { it.copy(date = v).withAutoPermalink() }
    fun setPermalink(v: String) = edit { it.copy(permalink = v, permalinkAuto = false) }
    fun useAutoPermalink() = edit { it.copy(permalinkAuto = true).withAutoPermalink() }
    fun setAuthor(v: String) = edit { it.copy(author = v) }
    fun setExcerpt(v: String) { edit { it.copy(excerpt = v) }; scheduleExcerptCheck(v) }
    fun setTagQuery(v: String) { _ui.update { it.copy(tagQuery = v) } }
    fun setTime(t: java.time.LocalTime?) = edit { u -> u.copy(date = u.date?.copy(time = t)) }
    fun excerptFromText() { setExcerpt(de.flauschiversum.publisher.editor.Excerpt.firstSentence(_ui.value.body)) }

    private var excerptJob: Job? = null
    private fun scheduleExcerptCheck(text: String) {
        excerptJob?.cancel()
        excerptJob = viewModelScope.launch {
            delay(400)
            val sp = kotlinx.coroutines.withContext(Dispatchers.Default) { c.spell.checker()?.check(text).orEmpty() }
            _ui.update { if (it.excerpt == text) it.copy(excerptMisspellings = sp) else it }
        }
    }

    /** Verkleinerte Vorschau eines Bildes im Beitragsverzeichnis (null, wenn es nicht lesbar ist). */
    suspend fun thumbnail(file: String, size: Int = 160): android.graphics.Bitmap? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val d = dir ?: return@withContext null
        val rev = contentRev ?: return@withContext null
        runCatching {
            val bytes = c.session.locked { c.session.store()?.readBytes(rev, "${PostReader.POSTS_DIR}/$d/$file") } ?: return@runCatching null
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
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

    fun save(force: Boolean = false) {
        val u = _ui.value
        if (u.saving) return
        if (!force) {
            val errors = MarkdownLint.check(u.body, originalBody.takeIf { it.isNotEmpty() }).count { it.severity == Severity.ERROR }
            if (errors > 0) { _ui.update { it.copy(confirmSaveErrors = errors) }; return }
        }
        _ui.update { it.copy(confirmSaveErrors = null) }
        val allowed = u.allowedTags
        val tags = PostEditing.canonicalTags(u.tags, allowed)
        val props = u.props.copy(tags = tags)
        val errors = PostEditing.validate(props, allowed, otherPermalinks, if (u.isNew) null else u.imageFiles)
        if (errors.isNotEmpty()) { _ui.update { it.copy(errors = errors, showProperties = true, message = errors.first().message) }; return }
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
