package de.flauschiversum.publisher.git

import de.flauschiversum.publisher.data.AppSettings
import de.flauschiversum.publisher.ssh.AppSshSessionFactory
import de.flauschiversum.publisher.ssh.HostKeyStore
import de.flauschiversum.publisher.ssh.SshKeyStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.lib.PersonIdent

/**
 * Das Repository der App: ein geöffneter [GitStore], daraus je nach aktuellen Einstellungen ein [DraftWorkflow],
 * und die gemeinsame Warteschlange ([lock]) für alle Aktionen, die Refs ändern. Die Netzwerkphasen von Holen und
 * Pushen halten die Sperre nicht (siehe [SyncService]).
 */
class RepoSession(
    val dir: File,
    private val settings: () -> AppSettings,
    private val keyStore: SshKeyStore,
    private val hostKeys: HostKeyStore,
    private val network: NetworkState,
) {
    val lock = Mutex()
    private var store: GitStore? = null

    fun isInstalled(): Boolean = File(dir, "HEAD").isFile && File(dir, "objects").isDirectory

    @Synchronized
    fun store(): GitStore? {
        if (!isInstalled()) return null
        return store ?: GitStore(dir).also { store = it }
    }

    /** Schließt den Klon, bevor er gelöscht oder neu angelegt wird. */
    @Synchronized
    fun close() { store?.close(); store = null }

    fun refs(): Refs = Refs(settings().targetBranch)

    fun address(): RepositoryAddress? =
        (RepositoryAddresses.parse(settings().repositoryAddress) as? AddressParse.Valid)?.address

    /** Ein frischer Zugang zur Gegenstelle (null, wenn kein Schlüssel oder keine gültige Adresse da ist). */
    fun remote(store: GitStore): Pair<RemoteOps, AppSshSessionFactory>? {
        val key = keyStore.load() ?: return null
        val address = address() ?: return null
        val factory = AppSshSessionFactory(key, hostKeys)
        return RemoteOps(store, SshRemote(address, factory)) to factory
    }

    fun workflow(): DraftWorkflow? {
        val s = store() ?: return null
        var lastFactory: AppSshSessionFactory? = null
        return DraftWorkflow(
            store = s,
            refs = refs(),
            ops = { remote(s)?.also { lastFactory = it.second }?.first },
            identity = { PersonIdent(settings().commitName, settings().commitEmail) },
            classify = { e -> classify(e, lastFactory) },
        )
    }

    fun classify(e: Throwable, factory: AppSshSessionFactory? = null): GitFailure {
        val address = address() ?: return GitFailure.Other(e.message ?: e.toString(), e)
        return GitFailures.classify(e, address, network, factory?.lastPresented, factory?.lastCheck)
    }

    suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }
}
