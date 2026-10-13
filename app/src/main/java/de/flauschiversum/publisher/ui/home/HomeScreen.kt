package de.flauschiversum.publisher.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.git.DraftKind
import de.flauschiversum.publisher.git.UploadState
import java.text.DateFormat
import java.util.Date

fun draftLabel(kind: DraftKind) = when (kind) { DraftKind.NEW -> "Neu"; DraftKind.CHANGED -> "Geändert"; DraftKind.DELETED -> "Gelöscht" }

fun formatSync(millis: Long): String =
    if (millis == 0L) "noch nie aktualisiert" else "zuletzt aktualisiert " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onOpenPost: (String?) -> Unit, onSettings: () -> Unit, onFixAccess: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Flauschiversum", style = MaterialTheme.typography.headlineSmall) }, actions = {
            TextButton(onClick = vm::refreshNow) { Text("Aktualisieren") }
            TextButton(onClick = onSettings, modifier = Modifier.testTag("open-settings")) { Text("Einstellungen") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { onOpenPost(null) }, modifier = Modifier.testTag("new-post")) { Text("Neuer Beitrag") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.syncing || ui.publishing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("sync-indicator"))
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                // Das erste Element bleibt immer an seinem Platz: So rutschen neue Beiträge und Entwürfe darunter ins Bild.
                item(key = "kopf") {
                    Column {
                Text(formatSync(ui.lastSyncMillis), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("last-sync"))
                if (ui.accessProblem) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(16.dp).fillMaxWidth().testTag("access-problem")) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Zugriff verweigert. Ist der Schlüssel noch auf dem Server eingetragen?")
                        TextButton(onClick = { vm.fixAccess(onFixAccess) }, modifier = Modifier.testTag("fix-access")) { Text("Prüfen und erneut eintragen") }
                    }
                }
                    }
                }
                if (ui.drafts.isNotEmpty() || (ui.overview?.otherChanges?.isNotEmpty() == true)) {
                    item { DraftsHeader(ui, vm) }
                    items(ui.drafts, key = { "d-" + it.summary.dir }) { row -> PostItem(row, onClick = { onOpenPost(row.summary.dir) }, onDiscard = { vm.discard(row.summary.dir) }) }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                if (ui.loading) item { Text("Lade Beiträge …", Modifier.padding(16.dp)) }
                items(ui.posts, key = { it.summary.dir }) { row -> PostItem(row, onClick = { onOpenPost(row.summary.dir) }, onDiscard = null) }
            }
        }
    }
    if (ui.confirmPublish) PublishDialog(ui, vm)
}

@Composable
private fun DraftsHeader(ui: HomeUi, vm: HomeViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Entwürfe", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("drafts-header"))
        val upload = ui.overview?.upload
        if (upload == UploadState.PENDING) Text("Noch nicht hochgeladen", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("upload-pending"))
        if (upload == UploadState.DIVERGED) Text("Entwurf weicht vom Server ab", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::askPublish, enabled = !ui.publishing, modifier = Modifier.testTag("publish")) { Text("Veröffentlichen") }
            if (upload == UploadState.PENDING) OutlinedButton(onClick = vm::retryUpload) { Text("Hochladen") }
            OutlinedButton(onClick = vm::discardAll) { Text("Alle verwerfen") }
        }
    }
}

@Composable
private fun PostItem(row: PostRow, onClick: () -> Unit, onDiscard: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("post-${row.summary.dir}")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(row.summary.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            row.draft?.let { Text(draftLabel(it), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge) }
        }
        val meta = listOfNotNull(row.summary.date?.date?.toString(), row.summary.tags.takeIf { it.isNotEmpty() }?.joinToString(", "))
        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        row.summary.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (onDiscard != null) TextButton(onClick = onDiscard) { Text("Verwerfen") }
    }
}

@Composable
private fun PublishDialog(ui: HomeUi, vm: HomeViewModel) {
    val posts = ui.overview?.posts.orEmpty()
    AlertDialog(
        onDismissRequest = vm::cancelPublish,
        title = { Text("Veröffentlichen?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Diese Änderungen gehen live:")
                posts.forEach { Text("• ${it.dir} (${draftLabel(it.kind)})", modifier = Modifier.testTag("publish-item")) }
                ui.overview?.otherChanges?.forEach { Text("• ${it.path}") }
                if (ui.publishErrors.isNotEmpty()) {
                    val n = ui.publishErrors.values.sum()
                    Text(
                        (if (n == 1) "Achtung: 1 Markdown-Fehler" else "Achtung: $n Markdown-Fehler") + " in " + ui.publishErrors.keys.joinToString { "„$it“" } + ". Die Beiträge werden trotzdem veröffentlicht, wenn du fortfährst.",
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("publish-errors"),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::publish, modifier = Modifier.testTag("publish-confirm")) { Text("Veröffentlichen") } },
        dismissButton = { TextButton(onClick = vm::cancelPublish) { Text("Abbrechen") } },
    )
}
