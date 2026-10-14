package de.flauschiversum.publisher.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.DraftOverview
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.PublishResult
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.git.UploadState
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.posts.TagCreation
import de.flauschiversum.publisher.posts.PostSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Eine Zeile der Beitragsübersicht. [draft] ist gesetzt, wenn der Beitrag ein offener Entwurf ist. */
data class PostRow(val summary: PostSummary, val draft: DraftKind?)

data class HomeUi(
    val loading: Boolean = true,
    val drafts: List<PostRow> = emptyList(),
    val posts: List<PostRow> = emptyList(),
    val overview: DraftOverview? = null,
    val syncing: Boolean = false,
    val lastSyncMillis: Long = 0L,
    val syncFailure: GitFailure? = null,
    val accessProblem: Boolean = false,
    val publishing: Boolean = false,
    val message: String? = null,
    val confirmPublish: Boolean = false,
    /** Beiträge mit offenen Markdown-Fehlern unter den Entwürfen: Titel → Zahl der Fehler. */
    val publishErrors: Map<String, Int> = emptyMap(),
    /** Schlagworte, die nur im Entwurf stehen. */
    val newTags: List<String> = emptyList(),
    val deleteDir: String? = null,
)

/** Beitragsübersicht mit Entwürfen und „Veröffentlichen“ (Specs `repository-klon`, `entwurf-veroeffentlichen`). */
class HomeViewModel(val c: AppContainer) : ViewModel() {
    private val local = MutableStateFlow(HomeUi())
    private var newTagsLoaded: List<String> = emptyList()
    val ui: StateFlow<HomeUi> = local.asStateFlow()

    init {
        viewModelScope.launch { c.sync.state.collect { s ->
            local.update { it.copy(syncing = s.running, lastSyncMillis = s.lastSuccessMillis, syncFailure = s.lastFailure, accessProblem = s.accessProblem) }
        } }
        viewModelScope.launch { var last = -1L; c.sync.state.collect { s -> if (s.generation != last) { last = s.generation; reload() } } }
        viewModelScope.launch { c.draftChanges.collect { reload() } }
        c.requestSync(SyncReason.START)
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loaded = c.session.locked { load() } ?: return@launch
                local.update { it.copy(loading = false, drafts = loaded.first, posts = loaded.second, overview = loaded.third, newTags = newTagsLoaded) }
            } catch (e: Throwable) {
                local.update { it.copy(loading = false, message = "Beiträge konnten nicht gelesen werden: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    private fun load(): Triple<List<PostRow>, List<PostRow>, DraftOverview>? {
        newTagsLoaded = c.session.workflow()?.let { TagCreation.newTags(it) }.orEmpty()
        val wf = c.session.workflow() ?: return null
        val reader = PostReader(wf.store)
        val overview = wf.overview()
        val draftDirs = overview.posts.associate { it.dir to it.kind }
        val drafts = overview.posts.mapNotNull { d ->
            val rev = if (d.kind == DraftKind.DELETED) wf.refs.target else wf.refs.draft
            reader.summary(rev, d.dir)?.let { PostRow(it, d.kind) }
        }
        val posts = reader.summaries(wf.refs.target).filter { it.dir !in draftDirs }.map { PostRow(it, null) }
        return Triple(drafts, posts, overview)
    }

    fun askDelete(dir: String?) { local.update { it.copy(deleteDir = dir) } }
    fun deleted() { local.update { it.copy(deleteDir = null) }; reload() }
    fun deleteFailed(msg: String) { local.update { it.copy(deleteDir = null, message = msg) } }

    fun refreshNow() { c.sync.request(SyncReason.MANUAL) }

    fun askPublish() {
        local.update { it.copy(confirmPublish = true, publishErrors = emptyMap()) }
        viewModelScope.launch(Dispatchers.IO) {
            val errors = runCatching { c.session.locked { markdownErrors() } }.getOrDefault(emptyMap())
            local.update { it.copy(publishErrors = errors) }
        }
    }

    private fun markdownErrors(): Map<String, Int> {
        val wf = c.session.workflow() ?: return emptyMap()
        val reader = PostReader(wf.store)
        val result = linkedMapOf<String, Int>()
        for (d in wf.overview().posts) {
            if (d.kind == DraftKind.DELETED) continue
            val doc = reader.document(wf.refs.draft, d.dir) ?: continue
            val n = de.flauschiversum.publisher.editor.MarkdownLint.check(doc.body).count { it.severity == de.flauschiversum.publisher.editor.Severity.ERROR }
            if (n > 0) result[reader.summary(wf.refs.draft, d.dir)?.title ?: d.dir] = n
        }
        return result
    }
    fun cancelPublish() { local.update { it.copy(confirmPublish = false) } }

    fun publish() {
        local.update { it.copy(confirmPublish = false, publishing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val wf = c.session.workflow()
            val result = if (wf == null) PublishResult.Failed(GitFailure.Other("Das Repository ist nicht eingerichtet.")) else c.session.locked { wf.publish() }
            val text = when (result) {
                is PublishResult.Published -> "Veröffentlicht."
                PublishResult.NothingToPublish -> "Es gibt nichts zu veröffentlichen."
                is PublishResult.Conflict -> "Konflikt bei: ${result.files.joinToString()}. Bitte am Rechner lösen; nichts wurde verändert."
                is PublishResult.Rejected -> "Der Server hat das Veröffentlichen abgelehnt: ${result.reason}. Der Entwurf bleibt erhalten."
                is PublishResult.Failed -> result.failure.message
            }
            local.update { it.copy(publishing = false, message = text) }
            reload()
        }
    }

    fun discard(dir: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val wf = c.session.workflow() ?: return@launch
            c.session.locked { de.flauschiversum.publisher.posts.PostDeletion.discard(wf, dir) }
            runCatching { wf.pushDraft() }
            reload()
        }
    }

    fun discardAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val wf = c.session.workflow() ?: return@launch
            c.session.locked { wf.discardAllLocal() }
            runCatching { wf.pushDraft(force = true) }
            reload()
        }
    }

    fun retryUpload() {
        viewModelScope.launch(Dispatchers.IO) {
            val wf = c.session.workflow() ?: return@launch
            val r = runCatching { wf.pushDraft() }
            local.update { it.copy(message = if (r.isSuccess) null else c.session.classify(r.exceptionOrNull()!!).message) }
            reload()
        }
    }

    /** Setzt „eingetragen/geprüft“ zurück, damit die Einrichtung bei Schritt 3 fortsetzt (Zugang wurde verweigert). */
    fun fixAccess(onDone: () -> Unit) {
        viewModelScope.launch { c.setupState.update { it.copy(registeredFor = null, accessVerifiedFor = null) }; onDone() }
    }

    fun messageShown() { local.update { it.copy(message = null) } }
    fun uploadState(): UploadState = local.value.overview?.upload ?: UploadState.NONE
}
