package de.flauschiversum.publisher.ui.outbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.upload.JobStatus
import de.flauschiversum.publisher.upload.UploadJob
import java.text.DateFormat
import java.util.Date
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import de.flauschiversum.publisher.ui.components.AppOutlinedButton
import de.flauschiversum.publisher.ui.components.AppTextButton
import de.flauschiversum.publisher.ui.components.AppIconButton

/** Ausgangsbox: alle Aufträge mit Status; Wiederholen, Abbrechen (mit Bestätigung), „Jetzt senden“. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OutboxScreen(c: AppContainer, onBack: () -> Unit, onFixAccess: () -> Unit = {}) {
    val jobs by c.uploads.jobs.collectAsState()
    var cancelId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { c.uploads.cleanup() }
    Scaffold(topBar = { TopAppBar(title = { Text("Ausgang") }, navigationIcon = { AppIconButton(icon = Icons.Default.ArrowBack, label = "Zurück", onClick = onBack, modifier = Modifier.testTag("outbox-back")) }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).testTag("outbox-list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (jobs.isEmpty()) item { Text("Keine Aufträge.", modifier = Modifier.padding(top = 16.dp).testTag("outbox-empty")) }
            items(jobs, key = { it.id }) { job -> JobCard(c, job, onCancel = { cancelId = job.id }, onFixAccess = onFixAccess) }
        }
    }
    cancelId?.let { id ->
        AlertDialog(
            onDismissRequest = { cancelId = null }, title = { Text("Auftrag abbrechen?") },
            text = { Text("Die gesicherten Fotos werden gelöscht.") },
            confirmButton = { AppTextButton(onClick = { c.uploads.cancel(id); cancelId = null }, modifier = Modifier.testTag("cancel-confirm"), label = "Abbrechen und löschen", icon = Icons.Default.Close) },
            dismissButton = { AppTextButton(onClick = { cancelId = null }, modifier = Modifier.testTag("cancel-keep"), label = "Behalten", icon = Icons.Default.Close) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobCard(c: AppContainer, job: UploadJob, onCancel: () -> Unit, onFixAccess: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("job-${job.id}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.resultDir ?: job.label, style = MaterialTheme.typography.titleMedium)
            Text("${job.photos.size.takeIf { it > 0 }?.let { "$it Foto${if (it == 1) "" else "s"}" } ?: "Fotos"} · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(job.createdAt)), style = MaterialTheme.typography.bodySmall)
            val failed = job.status == JobStatus.FAILED
            Text(
                job.status.label + (job.message?.takeIf { failed || job.status == JobStatus.WAITING_NETWORK }?.let { ": $it" } ?: ""),
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("status-${job.id}"),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (failed) AppOutlinedButton(onClick = { c.uploads.retry(job.id) }, modifier = Modifier.testTag("retry-${job.id}"), label = "Wiederholen", icon = Icons.Default.Refresh)
                if (failed && job.message?.contains("Zugriff") == true) AppOutlinedButton(onClick = onFixAccess, modifier = Modifier.testTag("fix-${job.id}"), label = "Zur Einrichtung", icon = Icons.Default.Settings)
                if (job.status == JobStatus.WAITING_WIFI) AppOutlinedButton(onClick = { c.uploads.sendNow(job.id) }, modifier = Modifier.testTag("sendnow-${job.id}"), label = "Jetzt senden", icon = Icons.Default.Send)
                if (c.uploads.canCancel(job)) AppOutlinedButton(onClick = onCancel, modifier = Modifier.testTag("cancel-${job.id}"), label = "Abbrechen", icon = Icons.Default.Close)
            }
        }
    }
}
