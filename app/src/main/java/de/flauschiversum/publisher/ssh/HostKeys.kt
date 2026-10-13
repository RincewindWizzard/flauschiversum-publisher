package de.flauschiversum.publisher.ssh

import java.io.File
import java.util.Base64

/** Schlüsseltypen, die die App für Host-Schlüssel akzeptiert. */
enum class HostKeyType(val sshName: String) {
    ED25519("ssh-ed25519"),
    ECDSA("ecdsa-sha2-nistp256"),
    ECDSA384("ecdsa-sha2-nistp384"),
    ECDSA521("ecdsa-sha2-nistp521"),
    RSA("ssh-rsa");

    companion object {
        fun fromSshName(name: String): HostKeyType? = entries.firstOrNull { it.sshName == name }
    }
}

/** Ein Host-Schlüssel, wie ihn der Server beim Verbindungsaufbau vorweist. */
data class HostKeyInfo(
    val host: String,
    val port: Int,
    val type: HostKeyType,
    val blob: ByteArray,
) {
    val fingerprint: String get() = OpenSshKeys.fingerprint(blob)
    val base64: String get() = Base64.getEncoder().encodeToString(blob)
    val hostId: String get() = HostKeyStore.hostId(host, port)
    override fun equals(other: Any?) = other is HostKeyInfo && host == other.host && port == other.port &&
        type == other.type && blob.contentEquals(other.blob)
    override fun hashCode() = fingerprint.hashCode() + 31 * hostId.hashCode()
}

/** Ergebnis der Prüfung eines vorgewiesenen Host-Schlüssels. */
sealed interface HostKeyCheck {
    /** Bekannt und unverändert (oder von GitHub veröffentlicht). */
    data object Trusted : HostKeyCheck
    /** Der Server ist noch nie kontaktiert worden: Der Nutzer muss den Fingerabdruck bestätigen. */
    data object Unknown : HostKeyCheck
    /** Für diesen Server (Host und Port) ist ein anderer Schlüssel gespeichert. */
    data class Changed(val storedFingerprints: List<String>) : HostKeyCheck
}

/**
 * Bestätigte Host-Schlüssel je Host und Port ("beim ersten Mal bestätigen"). Für `github.com` sind die
 * veröffentlichten Fingerabdrücke fest hinterlegt (https://docs.github.com/…/githubs-ssh-key-fingerprints).
 */
class HostKeyStore(
    private val file: File,
    private val pinned: Map<String, Set<String>> = PINNED_FINGERPRINTS,
) {
    private data class Entry(val hostId: String, val type: String, val base64: String)

    @Synchronized
    private fun readAll(): List<Entry> {
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            if (parts.size == 3) Entry(parts[0], parts[1], parts[2]) else null
        }
    }

    @Synchronized
    private fun writeAll(entries: List<Entry>) {
        file.parentFile?.mkdirs()
        file.writeText(entries.joinToString("") { "${it.hostId} ${it.type} ${it.base64}\n" })
    }

    @Synchronized
    fun check(key: HostKeyInfo): HostKeyCheck {
        val stored = readAll().filter { it.hostId == key.hostId }
        val storedMatch = stored.any { it.type == key.type.sshName && it.base64 == key.base64 }
        if (storedMatch) return HostKeyCheck.Trusted
        val pinnedForHost = pinned[key.host.lowercase()].takeIf { key.port == 22 }
        if (pinnedForHost != null && key.fingerprint in pinnedForHost) return HostKeyCheck.Trusted
        // Ein anderer Schlüssel desselben Typs als der gespeicherte ist eine Abweichung (mögliche Fälschung);
        // ein bisher unbekannter Typ gilt als neuer Kontakt und wird zur Bestätigung vorgelegt.
        val sameType = stored.filter { it.type == key.type.sshName }
        if (sameType.isNotEmpty()) {
            return HostKeyCheck.Changed(sameType.map { OpenSshKeys.fingerprint(Base64.getDecoder().decode(it.base64)) })
        }
        return HostKeyCheck.Unknown
    }

    @Synchronized
    fun trust(key: HostKeyInfo) {
        val rest = readAll().filterNot { it.hostId == key.hostId && it.type == key.type.sshName }
        writeAll(rest + Entry(key.hostId, key.type.sshName, key.base64))
    }

    /** Alle bestätigten Server (ohne die fest hinterlegten von GitHub). */
    @Synchronized
    fun list(): List<StoredHostKey> = readAll().mapNotNull { e ->
        val type = HostKeyType.fromSshName(e.type) ?: return@mapNotNull null
        StoredHostKey(e.hostId, type, OpenSshKeys.fingerprint(Base64.getDecoder().decode(e.base64)))
    }

    /** Löscht alle gespeicherten Schlüssel eines Servers, damit er neu bestätigt werden kann. */
    @Synchronized
    fun remove(host: String, port: Int) {
        val id = hostId(host, port)
        writeAll(readAll().filterNot { it.hostId == id })
    }

    companion object {
        fun hostId(host: String, port: Int): String =
            if (port == 22) host.lowercase() else "[${host.lowercase()}]:$port"

        /** Von GitHub veröffentlichte Fingerabdrücke (RSA, ECDSA, Ed25519); Stand der Dokumentation. */
        val PINNED_FINGERPRINTS: Map<String, Set<String>> = mapOf(
            "github.com" to setOf(
                "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s",
                "SHA256:p2QAMXNIC1TJYWeIOttrVc98/R1BUFWu3/LiyKgUfQM",
                "SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU",
            ),
        )
    }
}

data class StoredHostKey(val hostId: String, val type: HostKeyType, val fingerprint: String)
