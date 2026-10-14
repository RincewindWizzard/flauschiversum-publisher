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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import de.flauschiversum.publisher.overview.PostQuery
import de.flauschiversum.publisher.overview.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

fun draftLabel(kind: DraftKind) = when (kind) { DraftKind.NEW -> "Neu"; DraftKind.CHANGED -> "Geändert"; DraftKind.DELETED -> "Gelöscht" }

fun formatSync(millis: Long): String =
    if (millis == 0L) "noch nie aktualisiert" else "zuletzt aktualisiert " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel, onOpenPost: (String?) -> Unit, onSettings: () -> Unit, onFixAccess: () -> Unit,
    onOpenProperties: (String) -> Unit = { onOpenPost(it) }, onAddPhoto: (String) -> Unit = {}, onOpenPhotos: (String) -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    // Suche und Filter überstehen Drehung und Prozessende
    var savedQuery by rememberSaveable(stateSaver = QuerySaver) { mutableStateOf(ui.query) }
    LaunchedEffect(Unit) { if (savedQuery != ui.query) vm.setQuery(savedQuery) }
    LaunchedEffect(ui.query) { savedQuery = ui.query }
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
            PullToRefreshBox(isRefreshing = ui.syncing, onRefresh = vm::refreshNow, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize().testTag("list")) {
                // Das erste Element bleibt immer an seinem Platz: So rutschen neue Beiträge und Entwürfe darunter ins Bild.
                item(key = "kopf") {
                    Column {
                        Text(formatSync(ui.lastSyncMillis), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("last-sync"))
                        if (ui.syncFailure == de.flauschiversum.publisher.git.GitFailure.NoNetwork) Text("Kein Netz – es gilt der zuletzt geholte Stand.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).testTag("offline"))
                        if (ui.accessProblem) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(16.dp).fillMaxWidth().testTag("access-problem")) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Zugriff verweigert. Ist der Schlüssel noch auf dem Server eingetragen?")
                                TextButton(onClick = { vm.fixAccess(onFixAccess) }, modifier = Modifier.testTag("fix-access")) { Text("Prüfen und erneut eintragen") }
                            }
                        }
                    }
                }
                if (ui.drafts.isNotEmpty() || ui.newTags.isNotEmpty() || (ui.overview?.otherChanges?.isNotEmpty() == true)) {
                    item { DraftsHeader(ui, vm) }
                    items(ui.drafts, key = { "d-" + it.summary.dir }) { row -> PostItem(row, vm.c, onClick = { onOpenPost(row.summary.dir) }, actions = PostActions(onOpenPost, onOpenProperties, onAddPhoto, onOpenPhotos, vm)) }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                item(key = "suche") { SearchBar(ui, vm) }
                if (ui.noClone) item(key = "keinklon") {
                    Column(Modifier.padding(16.dp).testTag("no-clone")) {
                        Text("Das Repository ist noch nicht geholt. Schließe die Einrichtung ab, um Beiträge zu sehen.")
                        TextButton(onClick = onFixAccess, modifier = Modifier.testTag("to-setup")) { Text("Zur Einrichtung") }
                    }
                }
                if (ui.loading) item { Text("Lade Beiträge …", Modifier.padding(16.dp)) }
                if (!ui.loading && ui.posts.isEmpty() && ui.totalPosts > 0) item(key = "leer") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Keine Beiträge gefunden.", modifier = Modifier.testTag("no-results"))
                        TextButton(onClick = { vm.setQuery(PostQuery(sort = ui.query.sort)) }, modifier = Modifier.testTag("reset-filters")) { Text("Zurücksetzen") }
                    }
                }
                items(ui.posts, key = { it.summary.dir }) { row -> PostItem(row, vm.c, onClick = { onOpenPost(row.summary.dir) }, actions = PostActions(onOpenPost, onOpenProperties, onAddPhoto, onOpenPhotos, vm)) }
            }
            }
        }
    }
    if (ui.confirmPublish) PublishDialog(ui, vm)
    ui.deleteDir?.let { d -> de.flauschiversum.publisher.ui.delete.DeletePostDialog(vm.c, d, onDismiss = { vm.askDelete(null) }, onDeleted = vm::deleted, onError = vm::deleteFailed) }
}

@Composable
private fun DraftsHeader(ui: HomeUi, vm: HomeViewModel) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Entwürfe", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("drafts-header"))
        ui.newTags.forEach { Text("Neues Schlagwort: $it", modifier = Modifier.testTag("new-tag-$it")) }
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

class PostActions(
    val onEdit: (String?) -> Unit, val onProperties: (String) -> Unit, val onPhoto: (String) -> Unit, val onPhotos: (String) -> Unit, val vm: HomeViewModel,
)

@Composable
private fun PostItem(row: PostRow, c: de.flauschiversum.publisher.app.AppContainer, onClick: () -> Unit, actions: PostActions) {
    val dir = row.summary.dir
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag("post-$dir"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Thumb(row, c)
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.summary.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                row.draft?.let { Text(draftLabel(it), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge) }
            }
            val meta = listOfNotNull(row.summary.date?.date?.toString(), row.summary.tags.takeIf { it.isNotEmpty() }?.joinToString(", "))
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.summary.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.testTag("menu-$dir")) { Text("⋮") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (row.draft != DraftKind.DELETED) {
                    DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; actions.onEdit(dir) }, modifier = Modifier.testTag("action-edit-$dir"))
                    DropdownMenuItem(text = { Text("Eigenschaften") }, onClick = { menu = false; actions.onProperties(dir) }, modifier = Modifier.testTag("action-props-$dir"))
                    DropdownMenuItem(text = { Text("Fotos") }, onClick = { menu = false; actions.onPhotos(dir) }, modifier = Modifier.testTag("action-photos-$dir"))
                    DropdownMenuItem(text = { Text("Foto hinzufügen") }, onClick = { menu = false; actions.onPhoto(dir) }, modifier = Modifier.testTag("action-photo-$dir"))
                }
                if (row.draft != null) DropdownMenuItem(text = { Text("Verwerfen") }, onClick = { menu = false; actions.vm.discard(dir) }, modifier = Modifier.testTag("action-discard-$dir"))
                if (row.draft != DraftKind.DELETED) DropdownMenuItem(text = { Text("Löschen") }, onClick = { menu = false; actions.vm.askDelete(dir) }, modifier = Modifier.testTag("action-delete-$dir"))
            }
        }
    }
}

@Composable
private fun Thumb(row: PostRow, c: de.flauschiversum.publisher.app.AppContainer) {
    val cover = row.cover
    val bitmap by produceState<android.graphics.Bitmap?>(null, row.summary.dir, cover, row.rev) {
        value = if (cover == null) null else withContext(Dispatchers.IO) { c.thumbnails.get(row.rev, "content/posts/${row.summary.dir}/$cover") }
    }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).testTag("thumb-${row.summary.dir}"), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(row.summary.title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchBar(ui: HomeUi, vm: HomeViewModel) {
    var showTags by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    val q = ui.query
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(q.text, { vm.setQuery(q.copy(text = it)) }, singleLine = true, label = { Text("Beitrag suchen …") }, modifier = Modifier.fillMaxWidth().testTag("search"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { showTags = true }, label = { Text("Schlagwort") }, modifier = Modifier.testTag("filter-tags"))
            Box {
                AssistChip(onClick = { yearMenu = true }, label = { Text("Jahr") }, modifier = Modifier.testTag("filter-year"))
                DropdownMenu(yearMenu, { yearMenu = false }) { ui.years.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { yearMenu = false; vm.setQuery(q.copy(year = y)) }, modifier = Modifier.testTag("year-$y")) } }
            }
            Box {
                AssistChip(onClick = { sortMenu = true }, label = { Text(q.sort.label) }, modifier = Modifier.testTag("sort"))
                DropdownMenu(sortMenu, { sortMenu = false }) { SortOrder.entries.forEach { o -> DropdownMenuItem(text = { Text(o.label) }, onClick = { sortMenu = false; vm.setQuery(q.copy(sort = o)) }, modifier = Modifier.testTag("sort-${o.name}")) } }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            q.tags.forEach { t -> InputChip(selected = true, onClick = { vm.setQuery(q.copy(tags = q.tags - t)) }, label = { Text("$t ✕") }, modifier = Modifier.testTag("active-tag-$t")) }
            q.year?.let { y -> InputChip(selected = true, onClick = { vm.setQuery(q.copy(year = null)) }, label = { Text("$y ✕") }, modifier = Modifier.testTag("active-year")) }
        }
        if (q.isFiltered) Text("${ui.posts.size} von ${ui.totalPosts} Beiträgen", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("result-count"))
    }
    if (showTags) AlertDialog(
        onDismissRequest = { showTags = false }, title = { Text("Schlagworte") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.allTags.forEach { t -> FilterChip(selected = t in q.tags, onClick = { vm.setQuery(q.copy(tags = if (t in q.tags) q.tags - t else q.tags + t)) }, label = { Text(t) }, modifier = Modifier.testTag("tagfilter-$t")) }
        } } },
        confirmButton = { TextButton(onClick = { showTags = false }, modifier = Modifier.testTag("tagfilter-done")) { Text("Fertig") } },
    )
}

private val QuerySaver = androidx.compose.runtime.saveable.Saver<PostQuery, List<String>>(
    save = { listOf(it.text, it.tags.joinToString("\u001F"), it.year?.toString().orEmpty(), it.sort.name) },
    restore = { PostQuery(it[0], it[1].split('\u001F').filter { t -> t.isNotEmpty() }.toSet(), it[2].toIntOrNull(), SortOrder.valueOf(it[3])) },
)

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
                ui.newTags.forEach { Text("• Neues Schlagwort: $it", modifier = Modifier.testTag("publish-new-tag")) }
                ui.overview?.otherChanges?.filter { !(it.path == "content/tags.yaml" && ui.newTags.isNotEmpty()) }?.forEach { Text("• ${it.path}") }
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
