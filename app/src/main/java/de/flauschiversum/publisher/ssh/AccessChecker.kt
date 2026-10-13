package de.flauschiversum.publisher.ssh

import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.GitFailures
import de.flauschiversum.publisher.git.NetworkState
import de.flauschiversum.publisher.git.RepositoryAddress
import de.flauschiversum.publisher.git.SshRemote
import org.eclipse.jgit.api.Git

/** Ergebnis von „Verbindung testen“. */
sealed interface AccessResult {
    /** Anmeldung gelungen; [writeConfirmed] ist nur bei Servern gesichert, die Schreibrecht beim Verbindungsaufbau prüfen. */
    data class Ok(val writeConfirmed: Boolean) : AccessResult
    data class Failed(val failure: GitFailure) : AccessResult
    data object NoKey : AccessResult
}

/** Prüft Zugang und Schreibrecht, ohne am Repository etwas zu verändern. */
class AccessChecker(
    private val keyStore: SshKeyStore,
    private val hostKeys: HostKeyStore,
    private val network: NetworkState,
) {
    fun check(address: RepositoryAddress): AccessResult {
        val key = keyStore.load() ?: return AccessResult.NoKey
        val factory = AppSshSessionFactory(key, hostKeys)
        val remote = SshRemote(address, factory)
        try {
            with(remote) { Git.lsRemoteRepository().setRemote(address.text).setHeads(true).useSsh().call() }
        } catch (e: Exception) {
            return AccessResult.Failed(GitFailures.classify(e, address, network, factory.lastPresented, factory.lastCheck))
        }
        // Schreibrecht prüfen: Verbindung zum Push-Dienst öffnen, ohne etwas zu übertragen.
        return try {
            remote.openTransport().use { t -> t.openPush().close() }
            AccessResult.Ok(writeConfirmed = address.isGitHub)
        } catch (e: Exception) {
            AccessResult.Failed(GitFailures.classify(e, address, network, factory.lastPresented, factory.lastCheck))
        }
    }

    /**
     * Holt den Host-Schlüssel eines Servers, ohne sich anzumelden, damit der Nutzer den Fingerabdruck bestätigen
     * kann. Liefert null, wenn der Server nicht erreichbar ist.
     */
    fun probeHostKey(address: RepositoryAddress): HostKeyInfo? {
        val key = keyStore.load() ?: OpenSshKeys.generate("probe")
        val factory = AppSshSessionFactory(key, hostKeys, HostKeyMode.RECORD_ONLY)
        val remote = SshRemote(address, factory, timeoutSeconds = 15)
        runCatching { with(remote) { Git.lsRemoteRepository().setRemote(address.text).useSsh().call() } }
        return factory.lastPresented
    }

    /** Merkt einen vom Nutzer bestätigten Host-Schlüssel. */
    fun trust(key: HostKeyInfo) = hostKeys.trust(key)
}
