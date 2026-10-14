package de.flauschiversum.publisher.diag

import de.flauschiversum.publisher.git.GitFailure
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Vorgänge, die protokolliert werden (feste Liste: es gibt keinen Freitext). */
enum class DiagOp(val label: String) { CLONE("Klonen"), FETCH("Holen"), PUSH("Push"), SYNC("Aktualisieren"), PUBLISH("Veröffentlichen"), ACCESS("Zugang prüfen"), JOB_COMMIT("Auftrag: Ablegen"), JOB_PUSH("Auftrag: Hochladen"), CRASH("Absturz") }

enum class DiagResult(val label: String) { OK("ok"), FAILED("fehlgeschlagen"), RETRY("wird wiederholt") }

/** Fehlerarten als feste Texte, abgeleitet aus [GitFailure] — nie die Meldung selbst (sie könnte Pfade o. ä. enthalten). */
object DiagKind {
    fun of(f: GitFailure): String = when (f) {
        is GitFailure.HostKeyUnknown -> "Host-Schlüssel unbekannt"
        is GitFailure.HostKeyChanged -> "Host-Schlüssel geändert"
        GitFailure.KeyRejected -> "Zugriff verweigert"
        GitFailure.ReadOnly -> "Nur Leserecht"
        GitFailure.RepositoryNotFound -> "Repository nicht gefunden"
        GitFailure.NoNetwork -> "Kein Netz"
        is GitFailure.Unreachable -> "Server nicht erreichbar"
        is GitFailure.Other -> "Sonstiger Fehler"
    }
}

/**
 * Lokales Diagnoseprotokoll: eine Zeile je Vorgang (Zeit, Vorgang, Ergebnis, Fehlerart, Server:Port). Die Schnittstelle nimmt
 * nur feste Felder an, damit nie Schlüssel, Passwörter, Beitragstexte oder Bildinhalte hineingelangen. Höchstens [maxBytes];
 * ist die Datei zu groß, fallen die ältesten Zeilen weg.
 */
class DiagnosticLog(private val file: File, private val maxBytes: Long = 1_000_000L, private val clock: () -> Long = System::currentTimeMillis) {
    private fun time(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).apply { timeZone = TimeZone.getDefault() }.format(Date(clock()))

    @Synchronized
    fun record(op: DiagOp, result: DiagResult, kind: String? = null, host: String? = null, port: Int? = null, where: String? = null) {
        val parts = buildList {
            add(time()); add(op.label); add(result.label)
            kind?.let { add(it.clean()) }
            if (host != null) add("$host:${port ?: 22}".clean())
            where?.let { add("bei " + it.clean()) }
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(parts.joinToString(" · ") + "\n", Charsets.UTF_8)
            rotate()
        }
    }

    fun recordFailure(op: DiagOp, failure: GitFailure, host: String?, port: Int?, retry: Boolean = false) =
        record(op, if (retry) DiagResult.RETRY else DiagResult.FAILED, DiagKind.of(failure), host, port)

    private fun String.clean() = replace('\n', ' ').replace('\r', ' ').take(200)

    private fun rotate() {
        if (file.length() <= maxBytes) return
        val lines = file.readLines(Charsets.UTF_8)
        // Hälfte der Grenze behalten, ältere Einträge fallen weg
        var size = 0L; var from = lines.size
        while (from > 0 && size + lines[from - 1].toByteArray(Charsets.UTF_8).size + 1 <= maxBytes / 2) { from--; size += lines[from].toByteArray(Charsets.UTF_8).size + 1 }
        file.writeText(lines.drop(from).joinToString("") { it + "\n" }, Charsets.UTF_8)
    }

    @Synchronized fun text(): String = if (file.isFile) file.readText(Charsets.UTF_8) else ""
    @Synchronized fun clear() { file.delete() }
    fun sizeBytes(): Long = if (file.isFile) file.length() else 0L
}
