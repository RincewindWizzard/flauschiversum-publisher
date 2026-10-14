package de.flauschiversum.publisher.upload

import de.flauschiversum.publisher.data.AppSettings
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.NetworkInfo
import de.flauschiversum.publisher.git.PushOutcome
import de.flauschiversum.publisher.git.RepoSession
import de.flauschiversum.publisher.photos.PhotoPlacement
import de.flauschiversum.publisher.photos.PhotoWithAlt
import de.flauschiversum.publisher.photos.SharedPhoto

/** Ergebnis eines Schrittes für den Planer. */
sealed interface StepResult {
    data object Done : StepResult
    /** Später erneut versuchen (Netz fehlt o. ä.); der Planer wartet auf die Netzbedingung mit Backoff. */
    data class Retry(val reason: String) : StepResult
    data class WaitingForWifi(val reason: String) : StepResult
    data class Failed(val reason: String) : StepResult
}

/**
 * Führt die Schritte eines Auftrags aus: erst der lokale Commit auf `draft` (ohne Netz), dann der Push (mit Netz). Beides
 * nutzt die gemeinsame Git-Warteschlange; der Push hält die Sperre nicht, damit Speichern im Editor nicht auf ihn wartet.
 */
class UploadRunner(
    private val store: JobStore,
    private val session: RepoSession,
    private val placement: () -> PhotoPlacement,
    private val settings: () -> AppSettings,
    private val network: NetworkInfo,
    private val now: () -> Long = System::currentTimeMillis,
    /** Wird nach jeder Änderung an `draft` aufgerufen (Listen neu laden). */
    private val onDraftChanged: () -> Unit = {},
    private val maxUnknownAttempts: Int = 5,
) {
    /** Schritt 1: Verkleinern, Rechteangaben, Einbinden, Commit. Ohne Netz möglich. */
    suspend fun commit(id: String): StepResult {
        val job = store.load(id) ?: return StepResult.Failed("Auftrag nicht gefunden")
        if (job.status == JobStatus.DONE) return StepResult.Done
        if (job.committed) return StepResult.Done
        store.update(id) { it.copy(status = JobStatus.RUNNING, message = null) }
        return try {
            val result = session.locked {
                val wf = session.workflow() ?: error("Das Repository ist nicht eingerichtet.")
                val photos = job.photos.map { PhotoWithAlt(SharedPhoto(store.photoFile(id, it.storedName), it.displayName, it.mimeType), it.alt) }
                placement().place(wf, job.target, photos)
            }
            store.deletePhotos(id)
            store.update(id) { it.copy(committed = true, resultDir = result.dir, message = result.warnings.firstOrNull(), status = JobStatus.WAITING_NETWORK) }
            onDraftChanged()
            StepResult.Done
        } catch (e: Exception) {
            val msg = "Ablegen fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
            store.update(id) { it.copy(status = JobStatus.FAILED, message = msg, finishedAt = now()) }
            StepResult.Failed(msg)
        }
    }

    /** Schritt 2: `draft` hochladen. [force] ignoriert „Nur über WLAN“ (Aktion „Jetzt senden“). */
    suspend fun push(id: String, force: Boolean = false): StepResult {
        val job = store.load(id) ?: return StepResult.Failed("Auftrag nicht gefunden")
        if (job.status == JobStatus.DONE) return StepResult.Done
        if (job.status == JobStatus.FAILED && !job.committed) return StepResult.Failed(job.message.orEmpty())
        if (!job.committed) return StepResult.Retry("Commit steht noch aus")
        if (!force && settings().uploadWifiOnly && network.isOnline() && network.isMetered()) {
            store.update(id) { it.copy(status = JobStatus.WAITING_WIFI, message = null) }
            return StepResult.WaitingForWifi("Nur über WLAN hochladen ist an")
        }
        store.update(id) { it.copy(status = JobStatus.RUNNING, attempts = it.attempts + 1) }
        val wf = session.workflow()
        if (wf == null) return fail(id, "Das Repository ist nicht eingerichtet.")
        return try {
            when (val o = wf.pushDraft()) {
                PushOutcome.Ok -> { store.update(id) { it.copy(status = JobStatus.DONE, message = null, finishedAt = now()) }; onDraftChanged(); StepResult.Done }
                is PushOutcome.Rejected -> fail(id, "Der Server hat den Upload abgelehnt: ${o.reason}")
            }
        } catch (e: Exception) {
            when (val f = session.classify(e)) {
                GitFailure.NoNetwork, is GitFailure.Unreachable -> waiting(id, f.message)
                is GitFailure.Other -> if (job.attempts + 1 >= maxUnknownAttempts) fail(id, f.message) else waiting(id, f.message)
                else -> fail(id, "Zugriff: ${f.message}")
            }
        }
    }

    private fun waiting(id: String, reason: String): StepResult {
        store.update(id) { it.copy(status = JobStatus.WAITING_NETWORK, message = reason) }
        onDraftChanged()
        return StepResult.Retry(reason)
    }

    private fun fail(id: String, reason: String): StepResult {
        store.update(id) { it.copy(status = JobStatus.FAILED, message = reason, finishedAt = now()) }
        onDraftChanged()
        return StepResult.Failed(reason)
    }
}
