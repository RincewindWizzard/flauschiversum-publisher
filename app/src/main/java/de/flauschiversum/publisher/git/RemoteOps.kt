package de.flauschiversum.publisher.git

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.TagOpt
import org.eclipse.jgit.transport.URIish

/** Ein Ref, wie ihn der Server meldet. */
data class RemoteRef(val name: String, val id: ObjectId)

/** Ergebnis eines Pushs. */
sealed interface PushOutcome {
    data object Ok : PushOutcome
    /** Der Server hat das Update abgelehnt (z. B. Branch-Schutz oder nicht Fast-Forward). */
    data class Rejected(val ref: String, val reason: String) : PushOutcome
}

/**
 * Netzwerkoperationen auf dem lokalen Repository: flach klonen, Branches holen, Historie nachladen, pushen.
 * Remote-Tracking-Refs liegen unter `refs/remotes/origin/…`, lokale Branches unter `refs/heads/…`.
 */
class RemoteOps(private val store: GitStore, private val remote: SshRemote) {

    /** Welche Branches der Server hat (nur Köpfe). */
    fun listRemoteHeads(): List<RemoteRef> =
        with(remote) { Git.lsRemoteRepository().setRemote(remote.address.gitUrl).setHeads(true).setTags(false).useSsh().call() }
            .map { RemoteRef(it.name, it.objectId) }

    /** Legt das bare Repository an und holt [branches] mit der Tiefe 1. */
    fun cloneShallow(branches: List<String>, progress: ProgressMonitor? = null) {
        check(!store.exists()) { "Das Repository existiert bereits: ${store.dir}" }
        store.dir.mkdirs()
        Git.init().setBare(true).setDirectory(store.dir).setInitialBranch(branches.first()).call().use { git ->
            val config = git.repository.config
            config.setString("remote", "origin", "url", remote.address.gitUrl)
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            config.setBoolean("gc", null, "auto", false)
            config.save()
        }
        fetch(branches, depth = 1, progress = progress)
    }

    /** Holt die Branches ohne die Historie zu vertiefen (ein flacher Klon bleibt flach). */
    fun fetch(branches: List<String>, depth: Int? = null, unshallow: Boolean = false, progress: ProgressMonitor? = null) {
        val existing = listRemoteHeads().map { it.name }.toSet()
        val specs = branches.filter { "refs/heads/$it" in existing }
            .map { RefSpec("+refs/heads/$it:refs/remotes/origin/$it") }
        if (specs.isEmpty()) return
        Git(store.repo).use { git ->
            with(remote) {
                val cmd = git.fetch().setRemote("origin").setRefSpecs(specs).setTagOpt(TagOpt.NO_TAGS)
                    .setRemoveDeletedRefs(true).setCheckFetchedObjects(false)
                if (depth != null) cmd.setDepth(depth)
                if (unshallow) cmd.setUnshallow(true)
                if (progress != null) cmd.setProgressMonitor(progress)
                cmd.useSsh().call()
            }
        }
    }

    /**
     * Lädt Historie nach, bis [a] und [b] einen gemeinsamen Vorfahren haben (oder die ganze Historie da ist).
     * Gibt true zurück, wenn ein gemeinsamer Vorfahre gefunden wurde.
     */
    fun deepenUntilMergeBase(a: ObjectId, b: ObjectId, branches: List<String>, progress: ProgressMonitor? = null): Boolean {
        if (hasMergeBase(a, b)) return true
        for (depth in listOf(10, 50, 200, 1000)) {
            fetch(branches, depth = depth, progress = progress)
            if (hasMergeBase(a, b)) return true
        }
        fetch(branches, unshallow = true, progress = progress)
        return hasMergeBase(a, b)
    }

    fun hasMergeBase(a: ObjectId, b: ObjectId): Boolean = org.eclipse.jgit.revwalk.RevWalk(store.repo).use { rw ->
        rw.revFilter = org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE
        rw.markStart(rw.parseCommit(a))
        rw.markStart(rw.parseCommit(b))
        rw.next() != null
    }

    /**
     * Schiebt lokale Branches. [force] erlaubt das Überschreiben (nur für `draft`). Nach Erfolg wird der
     * Remote-Tracking-Ref nachgezogen.
     */
    fun push(branches: Map<String, ObjectId>, force: Boolean = false, progress: ProgressMonitor? = null): PushOutcome {
        Git(store.repo).use { git ->
            val specs = branches.keys.map { RefSpec((if (force) "+" else "") + "${branches[it]!!.name}:refs/heads/$it") }
            val results = with(remote) {
                val cmd = git.push().setRemote("origin").setRefSpecs(specs).setForce(force)
                if (progress != null) cmd.setProgressMonitor(progress)
                cmd.useSsh().call()
            }
            for (r in results) for (u in r.remoteUpdates) {
                if (u.status != RemoteRefUpdate.Status.OK && u.status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    return PushOutcome.Rejected(u.remoteName, "${u.status}${u.message?.let { ": $it" } ?: ""}")
                }
            }
        }
        for ((name, id) in branches) store.updateRef("refs/remotes/origin/$name", id, force = true)
        return PushOutcome.Ok
    }

    /** Löscht einen Branch auf dem Server. */
    fun deleteRemoteBranch(name: String) {
        Git(store.repo).use { git ->
            with(remote) { git.push().setRemote("origin").setRefSpecs(RefSpec(":refs/heads/$name")).useSsh().call() }
        }
        store.deleteRef("refs/remotes/origin/$name")
    }
}
