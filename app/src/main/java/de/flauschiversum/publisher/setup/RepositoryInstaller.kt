package de.flauschiversum.publisher.setup

import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.GitFailures
import de.flauschiversum.publisher.git.GitStore
import de.flauschiversum.publisher.git.NetworkState
import de.flauschiversum.publisher.git.Refs
import de.flauschiversum.publisher.git.RemoteOps
import de.flauschiversum.publisher.git.RepositoryAddress
import de.flauschiversum.publisher.posts.PostReader
import java.io.File
import org.eclipse.jgit.lib.ProgressMonitor

/** Fortschritt und Abbruch eines Klons. */
class CloneProgress(private val onUpdate: (String, Int, Int) -> Unit = { _, _, _ -> }) : ProgressMonitor {
    @Volatile var cancelled = false
    private var title = ""
    private var total = 0
    private var done = 0
    override fun start(totalTasks: Int) = Unit
    override fun beginTask(title: String, totalWork: Int) { this.title = title; total = totalWork; done = 0; onUpdate(title, 0, totalWork) }
    override fun update(completed: Int) { done += completed; onUpdate(title, done, total) }
    override fun endTask() = Unit
    override fun isCancelled(): Boolean = cancelled
    override fun showDuration(enabled: Boolean) = Unit
}

/** Ergebnis des Prüfens eines geklonten Repositorys. */
sealed interface CloneCheck {
    data object Ok : CloneCheck
    data class BranchMissing(val branch: String, val available: List<String>) : CloneCheck {
        val message: String get() = "Branch $branch nicht gefunden." + if (available.isEmpty()) "" else " Vorhanden: ${available.joinToString()}."
    }
    data object NoPosts : CloneCheck
    data object NoTags : CloneCheck
}

object CloneVerifier {
    fun verify(store: GitStore, refs: Refs): CloneCheck {
        if (store.refId(refs.target) == null) return CloneCheck.BranchMissing(refs.targetBranch, emptyList())
        val reader = PostReader(store)
        val hasPost = reader.dirNames(refs.target).any { store.exists(refs.target, "${PostReader.POSTS_DIR}/$it/index.md") }
        if (!hasPost) return CloneCheck.NoPosts
        if (store.readText(refs.target, "content/tags.yaml") == null) return CloneCheck.NoTags
        return CloneCheck.Ok
    }
}

sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data class NotEnoughSpace(val neededBytes: Long, val freeBytes: Long) : InstallOutcome
    data object Cancelled : InstallOutcome
    data class BranchMissing(val branch: String, val available: List<String>) : InstallOutcome
    /** Das Repository ist keins des Blogs: [reason] erklärt, was fehlt. */
    data class WrongRepository(val reason: String) : InstallOutcome
    data class Failed(val failure: GitFailure) : InstallOutcome
}

/** Holt das Repository (Schritt 5): Speicherprüfung, flacher Klon, Prüfung, Aufräumen bei Abbruch oder Fehler. */
class RepositoryInstaller(
    private val dir: File,
    private val address: RepositoryAddress,
    private val refs: Refs,
    private val opsFactory: (GitStore) -> RemoteOps,
    private val network: NetworkState,
    private val freeSpace: () -> Long = { dir.parentFile?.usableSpace ?: 0L },
    private val presented: () -> Pair<de.flauschiversum.publisher.ssh.HostKeyInfo?, de.flauschiversum.publisher.ssh.HostKeyCheck?> = { null to null },
) {
    fun install(progress: CloneProgress = CloneProgress()): InstallOutcome {
        val free = freeSpace()
        if (free < MIN_FREE_BYTES) return InstallOutcome.NotEnoughSpace(MIN_FREE_BYTES, free)
        dir.deleteRecursively()
        val store = GitStore(dir)
        try {
            val ops = opsFactory(store)
            val heads = ops.listRemoteHeads().map { it.name.removePrefix("refs/heads/") }
            if (refs.targetBranch !in heads) {
                dir.deleteRecursively()
                return InstallOutcome.BranchMissing(refs.targetBranch, heads.filter { it != Refs.DRAFT })
            }
            val branches = listOf(refs.targetBranch) + if (Refs.DRAFT in heads) listOf(Refs.DRAFT) else emptyList()
            ops.cloneShallow(branches, progress)
            if (progress.cancelled) { store.close(); dir.deleteRecursively(); return InstallOutcome.Cancelled }
            val check = CloneVerifier.verify(store, refs)
            if (check != CloneCheck.Ok) {
                store.close(); dir.deleteRecursively()
                return when (check) {
                    is CloneCheck.BranchMissing -> InstallOutcome.BranchMissing(check.branch, heads)
                    CloneCheck.NoPosts -> InstallOutcome.WrongRepository("Es gibt kein „content/posts/“ mit Beiträgen. Das scheint kein Flauschiversum-Blog zu sein.")
                    CloneCheck.NoTags -> InstallOutcome.WrongRepository("Die Datei „content/tags.yaml“ fehlt. Das scheint kein Flauschiversum-Blog zu sein.")
                    CloneCheck.Ok -> InstallOutcome.Success
                }
            }
            // Lokalen draft vom Server übernehmen, falls dort einer liegt
            store.refId(refs.draftRemote)?.let { store.updateRef(refs.draft, it, force = true) }
            store.close()
            return InstallOutcome.Success
        } catch (e: Exception) {
            store.close(); dir.deleteRecursively()
            if (progress.cancelled) return InstallOutcome.Cancelled
            val (key, check) = presented()
            return InstallOutcome.Failed(GitFailures.classify(e, address, network, key, check))
        }
    }

    companion object {
        /** Mindestens frei sein müssen 1,5 GB (der Blog ist mehrere hundert MB groß). */
        const val MIN_FREE_BYTES = 1_500_000_000L
    }
}
