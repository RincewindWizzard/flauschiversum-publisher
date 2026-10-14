package de.flauschiversum.publisher.upload

import androidx.work.CoroutineWorker
import de.flauschiversum.publisher.data.AppSettings
import de.flauschiversum.publisher.git.NetworkInfo
import de.flauschiversum.publisher.photos.PhotoTarget
import de.flauschiversum.publisher.photos.SharedPhoto
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Aufträge für Fotos: sichern, einplanen, ausführen, anzeigen (Ausgangsbox). */
class UploadService(
    val store: JobStore,
    private val runner: UploadRunner,
    var scheduler: UploadScheduler,
    private val settings: () -> AppSettings,
    private val network: NetworkInfo,
    private val notifications: UploadNotifications?,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _jobs = MutableStateFlow(store.all())
    /** Alle Aufträge, neueste zuerst. */
    val jobs: StateFlow<List<UploadJob>> = _jobs.asStateFlow()

    fun refresh() { _jobs.value = store.all() }

    /** Sichert [photos] (kopiert sie in den Auftrag) und plant Commit und Upload ein. Kehrt sofort zurück. */
    fun submit(photos: List<SharedPhoto>, alts: List<String>, target: PhotoTarget): String {
        val id = UUID.randomUUID().toString()
        val dir = File(store.dir(id), "photos").also { it.mkdirs() }
        val stored = photos.mapIndexed { i, p ->
            val name = "$i"
            // Dateien liegen schon im App-Speicher (Teilen-Eingang): verschieben statt kopieren
            if (!p.file.renameTo(File(dir, name))) { p.file.copyTo(File(dir, name), overwrite = true); p.file.delete() }
            JobPhoto(name, p.displayName, p.mimeType, alts.getOrNull(i).orEmpty())
        }
        val job = UploadJob(
            id, (target as? PhotoTarget.Existing)?.dir, (target as? PhotoTarget.New)?.title, (target as? PhotoTarget.New)?.author,
            stored, JobStatus.QUEUED, committed = false, message = null, createdAt = now(), finishedAt = null, attempts = 0,
        )
        store.save(job); refresh()
        scheduler.scheduleCommit(id)
        return id
    }

    suspend fun commitStep(id: String): StepResult {
        val r = runner.commit(id)
        refresh()
        when (r) {
            StepResult.Done -> scheduler.schedulePush(id, unmetered = settings().uploadWifiOnly)
            else -> store.load(id)?.let { notifications?.result(it) }
        }
        return r
    }

    suspend fun pushStep(id: String, force: Boolean, worker: CoroutineWorker? = null): StepResult {
        // Dauert der Upload länger als zwei Sekunden, läuft er als Vordergrunddienst mit Benachrichtigung
        var fg: Job? = null
        if (worker != null && notifications != null) fg = scope.launch { delay(2000); runCatching { worker.setForeground(notifications.foreground(store.load(id)?.label ?: "Upload")) } }
        val r = try { runner.push(id, force) } finally { fg?.cancel() }
        refresh()
        if (r is StepResult.Done || r is StepResult.Failed) store.load(id)?.let { notifications?.result(it) }
        return r
    }

    /** Fehlgeschlagenen Auftrag erneut starten: ohne Commit erst Commit, sonst Upload. */
    fun retry(id: String) {
        val job = store.load(id) ?: return
        if (job.status != JobStatus.FAILED) return
        store.update(id) { it.copy(status = JobStatus.QUEUED, message = null, finishedAt = null, attempts = 0) }
        refresh()
        if (job.committed) scheduler.schedulePush(id, unmetered = settings().uploadWifiOnly) else scheduler.scheduleCommit(id)
    }

    /** „Jetzt senden“: Upload über jede Verbindung, auch über Mobilfunk. */
    fun sendNow(id: String) {
        val job = store.load(id) ?: return
        if (!job.committed) return
        scheduler.schedulePush(id, unmetered = false, force = true)
    }

    /** Ob [job] sich noch abbrechen lässt (Commit noch nicht gelaufen). */
    fun canCancel(job: UploadJob) = !job.committed && job.status != JobStatus.RUNNING && job.status != JobStatus.DONE

    /** Bricht einen noch nicht gestarteten Auftrag ab: gesicherte Fotos werden gelöscht, der Auftrag verschwindet. */
    fun cancel(id: String): Boolean {
        val job = store.load(id) ?: return false
        if (!canCancel(job)) return false
        scheduler.cancel(id)
        store.remove(id); refresh()
        return true
    }

    /** Erledigte Einträge verschwinden nach sieben Tagen. */
    fun cleanup() {
        val limit = now() - TimeUnit.DAYS.toMillis(7)
        store.all().filter { it.status == JobStatus.DONE && (it.finishedAt ?: it.createdAt) < limit }.forEach { store.remove(it.id) }
        refresh()
    }

    /** Setzt nach einem Neustart der App liegen gebliebene Aufträge fort (z. B. wenn WorkManager-Einträge fehlen). */
    fun resumeAll() {
        for (job in store.all()) when {
            job.status == JobStatus.DONE || job.status == JobStatus.FAILED -> Unit
            !job.committed -> scheduler.scheduleCommit(job.id)
            else -> scheduler.schedulePush(job.id, unmetered = settings().uploadWifiOnly)
        }
    }
}
