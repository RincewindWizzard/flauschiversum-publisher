package de.flauschiversum.publisher.testsupport

import de.flauschiversum.publisher.upload.UploadScheduler
import de.flauschiversum.publisher.upload.UploadService
import kotlinx.coroutines.runBlocking

/** Führt Auftragsschritte sofort und nacheinander aus (statt über WorkManager). Zum Anhalten: [paused]. */
class InlineScheduler(private val service: () -> UploadService) : UploadScheduler {
    @Volatile var paused = false
    val pending = mutableListOf<() -> Unit>()
    val log = mutableListOf<String>()

    private fun run(label: String, f: () -> Unit) { log += label; if (paused) pending += f else f() }
    fun resume() { paused = false; while (pending.isNotEmpty()) pending.removeAt(0)() }

    override fun scheduleCommit(id: String) = run("commit:$id") { runBlocking { service().commitStep(id) } }
    override fun schedulePush(id: String, unmetered: Boolean, force: Boolean) = run("push:$id:unmetered=$unmetered:force=$force") { runBlocking { service().pushStep(id, force) } }
    override fun cancel(id: String) { log += "cancel:$id"; pending.clear() }
}
