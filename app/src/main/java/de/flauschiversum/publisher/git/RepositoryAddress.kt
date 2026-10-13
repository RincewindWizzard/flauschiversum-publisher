package de.flauschiversum.publisher.git

/** Die Adresse eines Git-Repositorys, erreichbar per SSH. */
data class RepositoryAddress(
    val user: String,
    val host: String,
    val port: Int,
    /** Pfad so, wie ihn der Server erwartet (relativ bei der Kurzform, sonst mit führendem "/"). */
    val path: String,
    /** Die Adresse in der vom Nutzer gewählten Schreibweise, bereinigt. */
    val text: String,
) {
    /** Eigentümer und Name bei Git-Hostern mit Pfaden der Form `eigentümer/repo[.git]`. */
    val ownerAndName: Pair<String, String>?
        get() {
            val parts = path.trim('/').removeSuffix(".git").split('/')
            return if (parts.size == 2 && parts.all { it.isNotEmpty() }) parts[0] to parts[1] else null
        }

    val isGitHub: Boolean get() = host.equals("github.com", ignoreCase = true)

    /** Seite zum Hinzufügen eines Deploy Keys bei GitHub. */
    fun gitHubDeployKeyUrl(): String? {
        if (!isGitHub) return null
        val (owner, name) = ownerAndName ?: return null
        return "https://github.com/$owner/$name/settings/keys/new"
    }
}

/** Ergebnis der Prüfung einer eingegebenen Adresse. */
sealed interface AddressParse {
    data class Valid(val address: RepositoryAddress) : AddressParse
    /** Eine HTTP(S)-Adresse: Der Zugang erfolgt per SSH, [suggestion] ist die passende SSH-Adresse. */
    data class Https(val suggestion: String) : AddressParse
    data class Invalid(val reason: String) : AddressParse
}

object RepositoryAddresses {
    const val DEFAULT = "git@github.com:RincewindWizzard/flauschiversum.git"
    const val EXAMPLES = "git@github.com:eigentümer/repo.git oder ssh://git@server.example:2222/srv/git/repo.git"

    private val scpForm = Regex("""^([A-Za-z0-9._-]+)@(\[[0-9A-Fa-f:.]+]|[A-Za-z0-9._-]+):(?!//)(.+)$""")
    private val sshUrl = Regex("""^ssh://([A-Za-z0-9._-]+)@(\[[0-9A-Fa-f:.]+]|[A-Za-z0-9._-]+)(?::(\d{1,5}))?/(.+)$""")
    private val httpUrl = Regex("""^https?://(?:[^@/]+@)?([A-Za-z0-9._-]+)(?::\d+)?/(.+)$""", RegexOption.IGNORE_CASE)

    fun parse(input: String): AddressParse {
        val text = input.trim()
        if (text.isEmpty()) return AddressParse.Invalid("Bitte eine Adresse eingeben. Beispiele: $EXAMPLES")
        httpUrl.matchEntire(text)?.let { m ->
            val host = m.groupValues[1].lowercase()
            val path = m.groupValues[2].trimEnd('/').removeSuffix(".git")
            if (path.isEmpty()) return AddressParse.Invalid("Die Adresse enthält keinen Repository-Pfad.")
            return AddressParse.Https("git@$host:$path.git")
        }
        if (text.contains("://") && !text.startsWith("ssh://")) {
            return AddressParse.Invalid("Nur SSH-Adressen werden unterstützt. Beispiele: $EXAMPLES")
        }
        sshUrl.matchEntire(text)?.let { m ->
            val port = m.groupValues[3].ifEmpty { "22" }.toInt()
            if (port !in 1..65535) return AddressParse.Invalid("Der Port $port ist ungültig (1 bis 65535).")
            val path = "/" + m.groupValues[4]
            return AddressParse.Valid(RepositoryAddress(m.groupValues[1], m.groupValues[2].trim('[', ']'), port, path, text))
        }
        scpForm.matchEntire(text)?.let { m ->
            return AddressParse.Valid(RepositoryAddress(m.groupValues[1], m.groupValues[2].trim('[', ']'), 22, m.groupValues[3], text))
        }
        return AddressParse.Invalid("Das ist keine gültige SSH-Adresse. Beispiele: $EXAMPLES")
    }
}
