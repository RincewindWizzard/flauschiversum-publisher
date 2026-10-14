package de.flauschiversum.publisher.git

import de.flauschiversum.publisher.posts.PostReader
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.merge.ResolveMerger
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

/** Benannte Refs des Repositorys. */
class Refs(val targetBranch: String) {
    val target = "refs/remotes/origin/$targetBranch"
    val draft = "refs/heads/draft"
    val draftRemote = "refs/remotes/origin/draft"
    val branches = listOf(targetBranch, DRAFT)

    companion object { const val DRAFT = "draft" }
}

enum class DraftKind { NEW, CHANGED, DELETED }

data class DraftPost(val dir: String, val kind: DraftKind)

enum class UploadState {
    /** Es gibt keinen Entwurf. */
    NONE,
    /** Lokaler und Server-Stand sind gleich. */
    UPLOADED,
    /** Lokale Commits sind noch nicht auf dem Server. */
    PENDING,
    /** Lokaler und Server-Stand sind auseinandergelaufen (z. B. von einem anderen Gerät verändert). */
    DIVERGED,
}

data class DraftOverview(
    val posts: List<DraftPost>,
    /** Geänderte Dateien außerhalb der Beiträge (z. B. `content/tags.yaml`). */
    val otherChanges: List<PathDiff>,
    val upload: UploadState,
) {
    val isEmpty: Boolean get() = posts.isEmpty() && otherChanges.isEmpty()
}

sealed interface PublishResult {
    /** [serverMessages]: Ausgabe des Servers zum Push, z. B. eines Deploy-Hooks (kann einen Fehler melden, obwohl der Push gelang). */
    data class Published(val posts: List<DraftPost>, val otherChanges: List<PathDiff>, val serverMessages: ServerMessages = ServerMessages.NONE) : PublishResult
    data object NothingToPublish : PublishResult
    data class Conflict(val files: List<String>) : PublishResult
    data class Rejected(val reason: String) : PublishResult
    /** Der Server war nicht erreichbar o. ä. */
    data class Failed(val failure: GitFailure) : PublishResult
}

data class SyncResult(val targetChanged: Boolean, val draftAdvanced: Boolean, val upload: UploadState)

/**
 * Der Weg von der Änderung bis zur Veröffentlichung: Commits auf dem gemeinsamen Branch `draft`, Ableiten der
 * Entwürfe, Verwerfen und Zusammenführen in den Zielbranch. Alles passiert in der Objektdatenbank des bare Klons.
 */
class DraftWorkflow(
    val store: GitStore,
    val refs: Refs,
    private val ops: () -> RemoteOps?,
    private val identity: () -> PersonIdent,
    private val classify: (Throwable) -> GitFailure = { GitFailure.Other(it.message ?: it.toString(), it) },
) {
    private val reader = PostReader(store)

    fun targetId(): ObjectId? = store.refId(refs.target)
    fun draftId(): ObjectId? = store.refId(refs.draft)
    fun hasDraft(): Boolean = draftId() != null

    /** Stand, von dem aus Änderungen beginnen: der Entwurf, sonst der Zielbranch. */
    fun baseRev(): String = if (hasDraft()) refs.draft else refs.target

    /** Legt einen Commit auf `draft` an (der Branch entsteht bei Bedarf aus dem Zielbranch). Null: nichts geändert. */
    fun commit(changes: List<TreeChange>, message: String): ObjectId? {
        check(targetId() != null) { "Das Repository ist noch nicht geholt." }
        return store.commit(baseRev(), changes, message, identity(), refs.draft)
    }

    // ---------- Entwürfe

    /** Gemeinsamer Vorfahre von Zielbranch und `draft` (null, wenn die Historie dafür nicht ausreicht). */
    fun baseCommit(): ObjectId? {
        val t = targetId() ?: return null
        val d = draftId() ?: return null
        return RevWalk(store.repo).use { rw ->
            rw.revFilter = org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE
            rw.markStart(rw.parseCommit(t)); rw.markStart(rw.parseCommit(d))
            rw.next()?.id
        }
    }

    /**
     * Die Änderungen, die `draft` selbst mitbringt: Pfade, die sich gegenüber dem gemeinsamen Vorfahren geändert haben
     * und noch vom aktuellen Zielbranch abweichen. Was nur im Zielbranch weitergelaufen ist, zählt nicht dazu.
     */
    fun ownChanges(): List<PathDiff> {
        if (targetId() == null || draftId() == null) return emptyList()
        val fromBase = store.diff(baseCommit()?.name ?: refs.target, refs.draft)
        if (fromBase.isEmpty()) return emptyList()
        val againstTarget = store.diff(refs.target, refs.draft).map { it.path }.toSet()
        return fromBase.filter { it.path in againstTarget }
    }

    /** Ob `draft` eigene Änderungen gegenüber dem Zielbranch hat. */
    fun hasChanges(): Boolean = ownChanges().isNotEmpty()

    /** Übernimmt den Server-Stand von `draft` als lokalen Branch, wenn es lokal noch keinen gibt (nach dem Klonen). */
    fun adoptRemoteDraft() {
        val remote = store.refId(refs.draftRemote) ?: return
        if (draftId() == null) store.updateRef(refs.draft, remote, force = true)
    }

    fun upload(): UploadState {
        val local = draftId() ?: return UploadState.NONE
        val remote = store.refId(refs.draftRemote)
        return when {
            remote == null -> if (hasChanges()) UploadState.PENDING else UploadState.NONE
            remote == local -> UploadState.UPLOADED
            store.isAncestor(remote, local) -> UploadState.PENDING
            else -> UploadState.DIVERGED
        }
    }

    fun overview(): DraftOverview {
        val target = targetId()
        val draft = draftId()
        if (target == null || draft == null) return DraftOverview(emptyList(), emptyList(), UploadState.NONE)
        val diffs = ownChanges()
        val prefix = PostReader.POSTS_DIR + "/"
        val byDir = linkedMapOf<String, MutableList<PathDiff>>()
        val other = mutableListOf<PathDiff>()
        for (d in diffs) {
            if (d.path.startsWith(prefix)) byDir.getOrPut(d.path.removePrefix(prefix).substringBefore('/')) { mutableListOf() } += d
            else other += d
        }
        val posts = byDir.map { (dir, _) ->
            val inTarget = store.exists(refs.target, "$prefix$dir/index.md")
            val inDraft = store.exists(refs.draft, "$prefix$dir/index.md")
            val kind = when {
                !inTarget && inDraft -> DraftKind.NEW
                inTarget && !inDraft -> DraftKind.DELETED
                else -> DraftKind.CHANGED
            }
            DraftPost(dir, kind)
        }.sortedBy { it.dir.lowercase() }
        val upload = if (posts.isEmpty() && other.isEmpty()) UploadState.NONE else upload()
        return DraftOverview(posts, other.sortedBy { it.path }, upload)
    }

    /** Stand, aus dem der Beitrag [dir] gelesen werden soll: `draft`, wenn er dort neu/geändert/gelöscht ist, sonst der Zielbranch. */
    fun revFor(dir: String, overview: DraftOverview = overview()): String =
        if (overview.posts.any { it.dir == dir }) refs.draft else refs.target

    // ---------- Verwerfen

    /** Setzt den Beitrag [dir] auf `draft` auf den Stand des Zielbranchs zurück (neue Beiträge verschwinden). */
    fun discard(dir: String): ObjectId? =
        commit(listOf(TreeChange.ReplaceDir("${PostReader.POSTS_DIR}/$dir", refs.target)), "Entwurf verworfen: $dir")

    /** Setzt den gesamten Entwurf auf den Zielbranch zurück (lokal; der Server-Stand folgt mit [pushDraft] erzwungen). */
    fun discardAllLocal() {
        val target = targetId() ?: return
        store.updateRef(refs.draft, target, force = true)
    }

    // ---------- Übertragen

    /** Schiebt `draft` zum Server. [force] nur nach „Alle verwerfen“. */
    fun pushDraft(force: Boolean = false): PushOutcome {
        val local = draftId() ?: return PushOutcome.Ok
        val o = ops() ?: return PushOutcome.Rejected("draft", "Kein Zugang eingerichtet")
        return o.push(mapOf(Refs.DRAFT to local), force = force).also { lastDraftMessages = o.lastServerMessages }
    }

    /** Server-Meldungen zum letzten Hochladen von `draft`. */
    @Volatile var lastDraftMessages: ServerMessages = ServerMessages.NONE
        private set

    /** Holt Zielbranch und `draft` (reine Netzwerkphase, braucht keine Sperre auf dem Repository). */
    fun fetch() {
        ops()?.fetch(refs.branches)
    }

    /**
     * Gleicht `draft` nach dem Holen ab: Übernimmt einen neueren Server-Stand und setzt einen Entwurf ohne eigene
     * Änderungen auf den Zielbranch vor. Gibt zurück, ob `draft` bewegt wurde.
     */
    fun reconcile(): Boolean {
        var advanced = false
        val local = draftId(); val remote = store.refId(refs.draftRemote)
        if (local == null && remote != null) {
            store.updateRef(refs.draft, remote, force = true); advanced = true
        } else if (remote != null && local != null && local != remote && store.isAncestor(local, remote)) {
            store.updateRef(refs.draft, remote, force = true); advanced = true
        }
        val d = draftId(); val target = targetId()
        if (d != null && target != null && d != target && !hasChanges() && store.isAncestor(d, target)) {
            store.updateRef(refs.draft, target, force = true); advanced = true
            if (store.refId(refs.draftRemote) != target) runCatching { pushDraft() }
        }
        return advanced
    }

    /** Holt Zielbranch und `draft` und führt `draft` nach, wenn er keine eigenen Änderungen hat. */
    fun sync(): SyncResult {
        if (ops() == null) return SyncResult(false, false, upload())
        val targetBefore = targetId()
        fetch()
        val advanced = reconcile()
        return SyncResult(targetBefore != targetId(), advanced, upload())
    }

    // ---------- Veröffentlichen

    fun publish(): PublishResult {
        val o = ops() ?: return PublishResult.Failed(GitFailure.Other("Kein Zugang eingerichtet"))
        try {
            o.fetch(refs.branches)
        } catch (e: Exception) {
            return PublishResult.Failed(classify(e))
        }
        val before = overview()
        if (before.isEmpty) return PublishResult.NothingToPublish
        val draft = draftId()!!
        val target = targetId()!!
        // Erst den Entwurf hochladen, damit nichts verloren geht
        if (upload() == UploadState.PENDING) {
            val pushed = try { pushDraft() } catch (e: Exception) { return PublishResult.Failed(classify(e)) }
            if (pushed is PushOutcome.Rejected) return PublishResult.Rejected(pushed.reason)
        }
        val newTarget: ObjectId = if (store.isAncestor(target, draft)) {
            draft
        } else {
            val hasBase = try {
                o.deepenUntilMergeBase(draft, target, refs.branches)
            } catch (e: Exception) { return PublishResult.Failed(classify(e)) }
            if (!hasBase) return PublishResult.Rejected("Kein gemeinsamer Vorfahre von Entwurf und ${refs.targetBranch} gefunden.")
            val merger = MergeStrategy.RECURSIVE.newMerger(store.repo, true)
            if (!merger.merge(target, draft)) {
                val files = (merger as? ResolveMerger)?.unmergedPaths?.toList().orEmpty()
                return PublishResult.Conflict(files)
            }
            val names = before.posts.joinToString(", ") { it.dir }.ifEmpty { "Änderungen" }
            mergeCommit(merger.resultTreeId, target, draft, "Veröffentlicht: $names")
        }
        val pushed = try { o.push(mapOf(refs.targetBranch to newTarget)) } catch (e: Exception) { return PublishResult.Failed(classify(e)) }
        if (pushed is PushOutcome.Rejected) return PublishResult.Rejected(pushed.reason)
        val serverMessages = o.lastServerMessages
        // Erfolg: draft entspricht jetzt dem Zielbranch
        store.updateRef(refs.draft, newTarget, force = true)
        runCatching { pushDraft() }
        return PublishResult.Published(before.posts, before.otherChanges, serverMessages)
    }

    private fun mergeCommit(tree: ObjectId, parentA: ObjectId, parentB: ObjectId, message: String): ObjectId {
        store.repo.newObjectInserter().use { ins ->
            val cb = org.eclipse.jgit.lib.CommitBuilder()
            cb.setTreeId(tree)
            cb.setParentIds(parentA, parentB)
            val who = identity()
            cb.author = who; cb.committer = who
            cb.message = message
            val id = ins.insert(cb)
            ins.flush()
            return id
        }
    }

    // ---------- Hilfen für Anzeigen

    fun parseCommit(id: ObjectId): RevCommit = RevWalk(store.repo).use { it.parseCommit(id) }

    @Suppress("unused")
    private fun diffEntryType(t: DiffEntry.ChangeType) = t
}
