package de.flauschiversum.publisher.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import de.flauschiversum.publisher.PublisherApp
import java.util.concurrent.TimeUnit

/** Plant die Schritte eines Auftrags ein. */
interface UploadScheduler {
    fun scheduleCommit(id: String)
    /** [unmetered]: nur über WLAN; [force]: „Jetzt senden“ über jede Verbindung. */
    fun schedulePush(id: String, unmetered: Boolean, force: Boolean = false)
    fun cancel(id: String)
}

/** Produktiv: WorkManager (überlebt Prozessende und Neustart), zwei Ketten: Commits ohne Netz, Pushs mit Netz. */
class WorkManagerScheduler(private val context: Context) : UploadScheduler {
    private fun wm() = WorkManager.getInstance(context)

    override fun scheduleCommit(id: String) {
        val req = OneTimeWorkRequestBuilder<CommitWorker>().setInputData(Data.Builder().putString(KEY_ID, id).build()).addTag(tag(id)).build()
        wm().enqueueUniqueWork(CHAIN_COMMIT, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    override fun schedulePush(id: String, unmetered: Boolean, force: Boolean) {
        val net = if (unmetered && !force) NetworkType.UNMETERED else NetworkType.CONNECTED
        val req = OneTimeWorkRequestBuilder<PushWorker>()
            .setInputData(Data.Builder().putString(KEY_ID, id).putBoolean(KEY_FORCE, force).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(net).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag(id)).build()
        wm().enqueueUniqueWork(CHAIN_PUSH, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    override fun cancel(id: String) { wm().cancelAllWorkByTag(tag(id)) }

    companion object {
        const val KEY_ID = "job"; const val KEY_FORCE = "force"
        const val CHAIN_COMMIT = "outbox-commit"; const val CHAIN_PUSH = "outbox-push"
        fun tag(id: String) = "job-$id"
    }
}

class CommitWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as PublisherApp).container
        val id = inputData.getString(WorkManagerScheduler.KEY_ID) ?: return Result.failure()
        return when (c.uploads.commitStep(id)) {
            is StepResult.Done -> Result.success()
            else -> Result.failure()
        }
    }
}

class PushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as PublisherApp).container
        val id = inputData.getString(WorkManagerScheduler.KEY_ID) ?: return Result.failure()
        return when (c.uploads.pushStep(id, inputData.getBoolean(WorkManagerScheduler.KEY_FORCE, false), this)) {
            is StepResult.Done -> Result.success()
            is StepResult.Retry -> Result.retry()
            is StepResult.WaitingForWifi -> { c.uploads.scheduler.schedulePush(id, unmetered = true); Result.success() } // mit WLAN-Bedingung neu einreihen
            is StepResult.Failed -> Result.failure()
        }
    }
}
