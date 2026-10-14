package de.flauschiversum.publisher.diag

import java.io.File

/**
 * Merkt sich einen Absturz für den Hinweis beim nächsten Start und vermerkt ihn im Protokoll (Art, Ort, Version). Es wird nichts
 * automatisch versendet.
 */
class CrashHandler(private val log: DiagnosticLog, private val marker: File, private val version: String) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e -> handle(e); previous?.uncaughtException(t, e) }
    }

    /** Protokolliert [e] und legt die Marke für den nächsten Start an. */
    fun handle(e: Throwable) {
        val frame = e.stackTrace.firstOrNull { it.className.startsWith("de.flauschiversum.publisher") } ?: e.stackTrace.firstOrNull()
        val where = frame?.let { "${it.className.substringAfterLast('.')}.${it.methodName}(${it.fileName}:${it.lineNumber})" }
        log.record(DiagOp.CRASH, DiagResult.FAILED, kind = "${e.javaClass.name} (Version $version)", where = where)
        runCatching { marker.parentFile?.mkdirs(); marker.writeText("1") }
    }

    fun pending(): Boolean = marker.isFile
    fun acknowledge() { marker.delete() }
}
