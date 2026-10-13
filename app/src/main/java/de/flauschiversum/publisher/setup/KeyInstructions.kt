package de.flauschiversum.publisher.setup

import de.flauschiversum.publisher.git.RepositoryAddress

/** Anleitung, wie der öffentliche Schlüssel auf dem Server eingetragen wird (Schritt 3). */
object KeyInstructions {
    sealed interface Plan {
        /** GitHub: Seite „Deploy Key hinzufügen“ des Repositorys. */
        data class GitHub(val url: String, val suggestedTitle: String) : Plan
        /** Eigener Server: Befehl für `~/.ssh/authorized_keys` des Benutzers. */
        data class OwnServer(val user: String, val host: String, val command: String) : Plan
    }

    fun deployKeyTitle(deviceName: String): String = "Flauschiversum Publisher (${deviceName.trim().ifEmpty { "Handy" }})"

    /** Befehl, der die Schlüsselzeile an die `authorized_keys` des angemeldeten Benutzers anhängt. */
    fun authorizedKeysCommand(publicLine: String): String {
        val safe = publicLine.trim().replace("'", "'\\''")
        return "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo '$safe' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
    }

    fun planFor(address: RepositoryAddress, publicLine: String, deviceName: String): Plan {
        val url = address.gitHubDeployKeyUrl()
        return if (url != null) Plan.GitHub(url, deployKeyTitle(deviceName))
        else Plan.OwnServer(address.user, address.host, authorizedKeysCommand(publicLine))
    }
}
