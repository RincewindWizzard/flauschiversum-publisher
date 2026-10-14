package de.flauschiversum.publisher.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo

/** Benachrichtigungen zu Hintergrundaufträgen. Ohne Berechtigung bleiben sie aus; die Ausgangsbox zeigt den Stand. */
class UploadNotifications(private val context: Context) {
    companion object {
        const val CHANNEL = "uploads"
        const val EXTRA_OPEN_OUTBOX = "de.flauschiversum.publisher.OPEN_OUTBOX"
        private const val ONGOING_ID = 7001
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Uploads", NotificationManager.IMPORTANCE_LOW).apply { description = "Fotos und Beiträge hochladen" })
        }
    }

    private fun openOutbox(): PendingIntent {
        val intent = Intent(context, Class.forName("de.flauschiversum.publisher.MainActivity")).putExtra(EXTRA_OPEN_OUTBOX, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Laufende Benachrichtigung für länger dauernde Aufträge. */
    fun foreground(text: String): ForegroundInfo {
        ensureChannel()
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Fotos werden hochgeladen")
            .setContentText(text).setProgress(0, 0, true).setOngoing(true).setContentIntent(openOutbox()).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ONGOING_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(ONGOING_ID, n)
    }

    /** Ergebnis melden, sofern Benachrichtigungen erlaubt sind. */
    @android.annotation.SuppressLint("MissingPermission") // areNotificationsEnabled() prüft die Berechtigung; SecurityException wird abgefangen
    fun result(job: UploadJob) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel()
        val (title, text) = when (job.status) {
            JobStatus.DONE -> "Hochgeladen" to job.label
            JobStatus.FAILED -> "Hochladen fehlgeschlagen" to (job.message ?: job.label)
            else -> return
        }
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload_done).setContentTitle(title).setContentText(text)
            .setContentIntent(openOutbox()).setAutoCancel(true).build()
        runCatching { nm.notify(job.id.hashCode(), n) }
    }
}
