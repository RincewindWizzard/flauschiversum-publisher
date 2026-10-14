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
import de.flauschiversum.publisher.overview.IndexedPost
import de.flauschiversum.publisher.overview.PostQuery
import de.flauschiversum.publisher.overview.PostSearch
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.posts.TagCreation
import de.flauschiversum.publisher.posts.PostSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Eine Zeile der Beitragsübersicht. [draft] ist gesetzt, wenn der Beitrag ein offener Entwurf ist. */
data class PostRow(val summary: PostSummary, val draft: DraftKind?, val cover: String? = null, val rev: String = "")

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
    val query: PostQuery = PostQuery(),
    val years: List<Int> = emptyList(),
    val allTags: List<String> = emptyList(),
    /** Zahl der Beiträge ohne Suche und Filter. */
    val totalPosts: Int = 0,
    /** Das Repository ist noch nicht geholt. */
    val noClone: Boolean = false,
    /** Der Nutzer hat per Wischgeste aktualisiert und die Anzeige läuft (mindestens [HomeViewModel.minRefreshMillis]). */
    val refreshing: Boolean = false,
)

/** Beitragsübersicht mit Entwürfen und „Veröffentlichen“ (Specs `repository-klon`, `entwurf-veroeffentlichen`). */
class HomeViewModel @JvmOverloads constructor(val c: AppContainer, private val minRefreshMillis: Long = 500L) : ViewModel() {
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

    private var base: List<IndexedPost> = emptyList()
    private var draftRows: List<PostRow> = emptyList()
    private var overviewNow: DraftOverview? = null
    private var targetRef: String = ""

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ok = c.session.locked { load() }
                if (ok == null) { local.update { it.copy(loading = false, noClone = true) }; return@launch }
                publishState()
            } catch (e: Throwable) {
                local.update { it.copy(loading = false, message = "Beiträge konnten nicht gelesen werden: ${e.message ?: e.javaClass.simpleName}") }
            }
        }
    }

    private fun publishState() {
        val q = local.value.query
        val rows = PostSearch.apply(base, q).map { PostRow(it.summary, null, it.cover, targetRef) }
        local.update { it.copy(loading = false, noClone = false, drafts = draftRows, posts = rows, overview = overviewNow, newTags = newTagsLoaded,
            years = PostSearch.years(base), allTags = PostSearch.tags(base), totalPosts = base.size) }
    }

    fun setQuery(q: PostQuery) { local.update { it.copy(query = q) }; publishState() }

    private fun load(): Boolean? {
        newTagsLoaded = c.session.workflow()?.let { TagCreation.newTags(it) }.orEmpty()
        val wf = c.session.workflow() ?: return null
        val index = c.postIndex() ?: return null
        targetRef = wf.refs.target
        val overview = wf.overview()
        val draftDirs = overview.posts.associate { it.dir to it.kind }
        val targetIndex = index.build(wf.refs.target)
        val draftIndex = if (overview.posts.any { it.kind != DraftKind.DELETED }) index.build(wf.refs.draft).associateBy { it.dir } else emptyMap()
        draftRows = overview.posts.mapNotNull { d ->
            val p = if (d.kind == DraftKind.DELETED) targetIndex.firstOrNull { it.dir == d.dir } else draftIndex[d.dir]
            p?.let { PostRow(it.summary, d.kind, it.cover, if (d.kind == DraftKind.DELETED) wf.refs.target else wf.refs.draft) }
        }
        base = targetIndex.filter { it.dir !in draftDirs }
        overviewNow = overview
        return true
    }

    fun askDelete(dir: String?) { local.update { it.copy(deleteDir = dir) } }
    fun deleted() { local.update { it.copy(deleteDir = null) }; reload() }
    fun deleteFailed(msg: String) { local.update { it.copy(deleteDir = null, message = msg) } }

    /** Aktualisieren per Wischgeste oder Bedienungshilfe: startet einen Lauf (oder hängt sich an den laufenden) und zeigt den Ladekreis. */
    fun refreshNow() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        val started = System.currentTimeMillis()
        c.sync.request(SyncReason.MANUAL) // läuft schon einer, wird kein zweiter gestartet
        viewModelScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(120_000) { c.sync.state.first { !it.running } }
            val rest = minRefreshMillis - (System.currentTimeMillis() - started)
            if (rest > 0) kotlinx.coroutines.delay(rest)
            local.update { it.copy(refreshing = false) }
        }
    }

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
            when (result) {
                is PublishResult.Published -> {
                    val a = c.session.address(); val sm = result.serverMessages
                    c.diag.record(de.flauschiversum.publisher.diag.DiagOp.PUBLISH, if (sm.hasProblem) de.flauschiversum.publisher.diag.DiagResult.FAILED else de.flauschiversum.publisher.diag.DiagResult.OK, kind = if (sm.hasProblem) "Server-Meldung" else null, host = a?.host, port = a?.port)
                    if (sm.hasProblem) c.notifier.serverProblem("Veröffentlicht, aber der Server meldet einen Fehler", sm.summary, sm.full)
                }
                is PublishResult.Failed -> c.diagFailure(de.flauschiversum.publisher.diag.DiagOp.PUBLISH, result.failure)
                is PublishResult.Conflict -> { val a = c.session.address(); c.diag.record(de.flauschiversum.publisher.diag.DiagOp.PUBLISH, de.flauschiversum.publisher.diag.DiagResult.FAILED, kind = "Konflikt", host = a?.host, port = a?.port) }
                is PublishResult.Rejected -> { val a = c.session.address(); c.diag.record(de.flauschiversum.publisher.diag.DiagOp.PUBLISH, de.flauschiversum.publisher.diag.DiagResult.FAILED, kind = "Server hat abgelehnt", host = a?.host, port = a?.port) }
                else -> Unit
            }
            val text = when (result) {
                is PublishResult.Published -> if (result.serverMessages.hasProblem) "Veröffentlicht, aber der Server meldet einen Fehler:\n${result.serverMessages.summary}" else "Veröffentlicht."
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
