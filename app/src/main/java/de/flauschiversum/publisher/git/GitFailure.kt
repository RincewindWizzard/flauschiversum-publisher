package de.flauschiversum.publisher.git

import com.jcraft.jsch.JSchException
import de.flauschiversum.publisher.ssh.HostKeyCheck
import de.flauschiversum.publisher.ssh.HostKeyInfo
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.errors.TransportException

/** Ob das Gerät gerade eine Netzverbindung hat (auf Android über den ConnectivityManager). */
fun interface NetworkState {
    fun isOnline(): Boolean
}

/** Verständlich eingeordnete Fehler bei Git über SSH. */
sealed interface GitFailure {
    val message: String

    data class HostKeyUnknown(val key: HostKeyInfo) : GitFailure {
        override val message = "Der Server ${key.hostId} ist noch nicht bestätigt (Fingerabdruck ${key.fingerprint})."
    }
    data class HostKeyChanged(val key: HostKeyInfo, val stored: List<String>) : GitFailure {
        override val message = "Der Host-Schlüssel von ${key.hostId} hat sich geändert. " +
            "Neu: ${key.fingerprint}, bekannt: ${stored.joinToString()}. Möglicherweise ein Angriff oder eine Neuinstallation."
    }
    data object KeyRejected : GitFailure {
        override val message = "Der Server akzeptiert den Schlüssel nicht. Ist der öffentliche Schlüssel dort eingetragen?"
    }
    data object ReadOnly : GitFailure {
        override val message = "Der Schlüssel hat nur Leserecht. Bitte mit Schreibrecht eintragen (bei GitHub: „Allow write access“)."
    }
    data object RepositoryNotFound : GitFailure {
        override val message = "Das Repository wurde nicht gefunden oder der Schlüssel hat keinen Zugriff darauf."
    }
    data object NoNetwork : GitFailure {
        override val message = "Kein Netz. Bitte Internetverbindung prüfen."
    }
    data class Unreachable(val host: String, val port: Int) : GitFailure {
        override val message = "Der Server $host auf Port $port ist nicht erreichbar (Netz, Firewall oder falscher Port?)."
    }
    data class Other(override val message: String, val cause: Throwable? = null) : GitFailure
}

/** Eine Ausnahme mit eingeordnetem Grund; wird bei Git-Aktionen der App geworfen. */
class GitFailureException(val failure: GitFailure, cause: Throwable? = null) : Exception(failure.message, cause)

object GitFailures {
    /**
     * Ordnet eine Ausnahme von JGit/JSch ein. [lastCheck] und [presented] stammen von der verwendeten
     * SSH-Fabrik: Sie zeigen, ob die Verbindung wegen des Host-Schlüssels abgebrochen wurde.
     */
    fun classify(
        error: Throwable,
        address: RepositoryAddress,
        network: NetworkState,
        presented: HostKeyInfo?,
        lastCheck: HostKeyCheck?,
    ): GitFailure {
        if (error is GitFailureException) return error.failure
        if (presented != null) {
            when (lastCheck) {
                HostKeyCheck.Unknown -> if (isHostKeyRejection(error)) return GitFailure.HostKeyUnknown(presented)
                is HostKeyCheck.Changed -> if (isHostKeyRejection(error)) return GitFailure.HostKeyChanged(presented, lastCheck.storedFingerprints)
                else -> Unit
            }
        }
        if (!network.isOnline()) return GitFailure.NoNetwork
        val chain = generateSequence(error) { it.cause }.toList()
        val text = chain.joinToString(" | ") { it.message.orEmpty() }.lowercase()
        if (chain.any { it is JSchException && ("auth fail" in it.message.orEmpty().lowercase() || "auth cancel" in it.message.orEmpty().lowercase()) }) {
            return GitFailure.KeyRejected
        }
        if ("read-only" in text || "read only" in text || "push not permitted" in text || ("write access" in text && "denied" in text)) return GitFailure.ReadOnly
        if (chain.any { it is ConnectException || it is NoRouteToHostException || it is UnknownHostException || it is SocketTimeoutException } ||
            "connection refused" in text || "timeout" in text || "timed out" in text || "unknownhost" in text) {
            return GitFailure.Unreachable(address.host, address.port)
        }
        if (chain.any { it is NoRemoteRepositoryException } || "not found" in text || "does not appear to be a git repository" in text ||
            "not a git repository" in text || "repository not found" in text) {
            return GitFailure.RepositoryNotFound
        }
        val first = chain.firstOrNull { it is TransportException } ?: error
        return GitFailure.Other(first.message ?: error.toString(), error)
    }

    private fun isHostKeyRejection(error: Throwable): Boolean {
        val text = generateSequence(error) { it.cause }.joinToString(" | ") { it.message.orEmpty() }.lowercase()
        return "hostkey" in text || "host key" in text || "reject" in text
    }
}
