package de.flauschiversum.publisher.posts

import java.io.File
import java.util.Properties

/**
 * Sichert ungespeicherte Eingaben im Editor (Titel, Text, Schlagworte …) in Dateien im privaten App-Speicher,
 * damit sie Unterbrechungen (App beendet, Drehung, Neustart) überstehen. Pro Beitrag eine Datei.
 */
class EditorSnapshotStore(private val dir: File) {
    private fun file(key: String) = File(dir, "editor-" + key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".properties")

    fun save(key: String, values: Map<String, String>) {
        dir.mkdirs()
        val p = Properties()
        values.forEach { (k, v) -> p.setProperty(k, v) }
        val tmp = File(dir, "editor-tmp-${System.nanoTime()}")
        tmp.outputStream().use { p.store(it, null) }
        val target = file(key)
        target.delete()
        if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
    }

    fun load(key: String): Map<String, String>? {
        val f = file(key)
        if (!f.isFile) return null
        val p = Properties()
        f.inputStream().use { p.load(it) }
        return p.stringPropertyNames().associateWith { p.getProperty(it) }
    }

    fun delete(key: String) { file(key).delete() }
}
