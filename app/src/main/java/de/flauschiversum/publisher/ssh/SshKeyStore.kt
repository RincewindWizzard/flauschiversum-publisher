package de.flauschiversum.publisher.ssh

import java.io.File

/**
 * Verwahrt das SSH-Schlüsselpaar im privaten App-Speicher. Das Verzeichnis ist über
 * `backup_rules.xml` und `data_extraction_rules.xml` von Android-Backups ausgenommen.
 */
class SshKeyStore(private val directory: File) {
    private val privateFile get() = File(directory, "id_ed25519")
    private val publicFile get() = File(directory, "id_ed25519.pub")

    fun hasKey(): Boolean = privateFile.isFile && publicFile.isFile

    fun load(): SshKeyPair? {
        if (!hasKey()) return null
        return SshKeyPair(privateFile.readText(), publicFile.readText().trim())
    }

    /** Erzeugt ein neues Schlüsselpaar und ersetzt ein vorhandenes. */
    fun generate(comment: String): SshKeyPair {
        val key = OpenSshKeys.generate(comment)
        save(key)
        return key
    }

    fun save(key: SshKeyPair) {
        directory.mkdirs()
        // Erst in temporäre Dateien schreiben, damit bei Abbruch kein halbes Schlüsselpaar bleibt.
        val tmpPrivate = File(directory, "id_ed25519.tmp")
        val tmpPublic = File(directory, "id_ed25519.pub.tmp")
        tmpPrivate.writeText(key.privatePem)
        tmpPrivate.setReadable(false, false)
        tmpPrivate.setReadable(true, true)
        tmpPrivate.setWritable(false, false)
        tmpPrivate.setWritable(true, true)
        tmpPublic.writeText(key.publicLine + "\n")
        privateFile.delete()
        publicFile.delete()
        check(tmpPrivate.renameTo(privateFile)) { "Privater Schlüssel konnte nicht gespeichert werden" }
        check(tmpPublic.renameTo(publicFile)) { "Öffentlicher Schlüssel konnte nicht gespeichert werden" }
    }

    fun delete() {
        privateFile.delete()
        publicFile.delete()
    }
}
