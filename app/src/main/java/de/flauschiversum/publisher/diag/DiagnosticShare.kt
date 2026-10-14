package de.flauschiversum.publisher.diag

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import de.flauschiversum.publisher.app.BuildConfigInfo
import java.io.File

/** Das Protokoll als Textdatei zum Teilen, mit App- und Android-Version. Geteilt wird nur auf Wunsch des Nutzers. */
object DiagnosticShare {
    fun report(log: DiagnosticLog): String = buildString {
        appendLine("Flauschiversum Publisher – Diagnoseprotokoll")
        appendLine("App-Version: ${BuildConfigInfo.version}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), Gerät: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        append(log.text().ifBlank { "(leer)\n" })
    }

    /** Schreibt die Datei in den Cache und liefert den Teilen-Intent (Auswahl durch den Nutzer). */
    fun intent(context: Context, log: DiagnosticLog): Intent {
        val file = File(context.cacheDir, "diag/diagnose.txt").also { it.parentFile?.mkdirs(); it.writeText(report(log), Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, "Diagnoseprotokoll")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Protokoll teilen")
    }
}
