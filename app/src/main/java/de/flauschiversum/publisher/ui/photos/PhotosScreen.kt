package de.flauschiversum.publisher.ui.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.posts.PhotoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhotosScreen(vm: PhotosViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Fotos: ${ui.title}", maxLines = 1) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("photos-back")) { Text("Zurück") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).testTag("photos-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!ui.loading && ui.photos.isEmpty()) item { Text("Im Beitragsverzeichnis liegen keine Bilder. Fotos lassen sich über „Teilen“ oder „Foto hinzufügen“ ergänzen.", modifier = Modifier.testTag("no-photos")) }
            items(ui.photos, key = { it.file }) { p ->
                val canMoveUp = p.inAttachments && ui.photos.filter { it.inAttachments }.firstOrNull()?.file != p.file
                val canMoveDown = p.inAttachments && ui.photos.filter { it.inAttachments }.lastOrNull()?.file != p.file
                Card(Modifier.fillMaxWidth().testTag("photo-${p.file}")) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Thumb(vm, ui.rev, p)
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.file, style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (p.isCover) AssistChip(onClick = {}, label = { Text("Titelbild") }, modifier = Modifier.testTag("badge-cover-${p.file}"))
                                if (p.notInText) AssistChip(onClick = {}, label = { Text("Nicht im Text") }, modifier = Modifier.testTag("badge-notintext-${p.file}"))
                            }
                            OutlinedTextField(vm.altFor(p), { vm.editAlt(p.file, it) }, label = { Text("Beschreibung (Alt-Text)") }, modifier = Modifier.fillMaxWidth().testTag("alt-${p.file}"))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!p.notInText) OutlinedButton(onClick = { vm.saveAlt(p.file) }, enabled = vm.altFor(p) != p.alt, modifier = Modifier.testTag("save-alt-${p.file}")) { Text("Speichern") }
                                if (p.notInText) OutlinedButton(onClick = { vm.include(p.file) }, modifier = Modifier.testTag("include-${p.file}")) { Text("Einbinden") }
                                if (canMoveUp) OutlinedButton(onClick = { vm.move(p.file, -1) }, modifier = Modifier.testTag("up-${p.file}")) { Text("↑") }
                                if (canMoveDown) OutlinedButton(onClick = { vm.move(p.file, +1) }, modifier = Modifier.testTag("down-${p.file}")) { Text("↓") }
                                if (!p.isCover) OutlinedButton(onClick = { vm.setCover(p.file) }, modifier = Modifier.testTag("cover-${p.file}")) { Text("Als Titelbild") }
                                OutlinedButton(onClick = { vm.askRemove(p) }, modifier = Modifier.testTag("remove-${p.file}")) { Text("Entfernen") }
                            }
                            if (p.outsideRefs > 0) Text("Steht im Fließtext: Position nur im Editor änderbar.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("hint-text-${p.file}"))
                        }
                    }
                }
            }
        }
    }
    ui.remove?.let { r -> RemoveDialog(r, ui.photos, vm) }
}

@Composable
private fun Thumb(vm: PhotosViewModel, rev: String, p: PhotoInfo) {
    val bmp by produceState<android.graphics.Bitmap?>(null, rev, p.file) {
        value = withContext(Dispatchers.IO) { vm.c.thumbnails.get(rev, "content/posts/${vm.dirName}/${p.file}") }
    }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = p.file, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoveDialog(r: RemoveRequest, photos: List<PhotoInfo>, vm: PhotosViewModel) {
    var chosen by remember { mutableStateOf(false) }
    var newCover by remember { mutableStateOf<String?>(null) }
    val needsCover = r.photo.isCover
    AlertDialog(
        onDismissRequest = vm::cancelRemove, title = { Text("Foto entfernen?") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("remove-dialog")) {
                Text("„${r.photo.file}“ und alle Bildzeilen dazu werden entfernt. Über „Verwerfen“ lässt sich das vor dem Veröffentlichen zurücknehmen.")
                if (r.photo.outsideRefs > 0 || r.inlineMentions > 0) Text("Das Foto wird auch im Fließtext verwendet; diese Stellen ändern sich mit.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("remove-text-hint"))
                if (r.inlineMentions > 0) Text("Verweise mitten im Absatz bleiben stehen und müssen im Editor angepasst werden.", color = MaterialTheme.colorScheme.error)
                if (needsCover) {
                    Text("Das ist das Titelbild. Bitte wähle zuerst ein anderes oder „kein Titelbild“:", modifier = Modifier.testTag("cover-required"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { chosen = true; newCover = null }, label = { Text(if (chosen && newCover == null) "✓ Kein Titelbild" else "Kein Titelbild") }, modifier = Modifier.testTag("newcover-none"))
                        photos.filter { it.file != r.photo.file }.forEach { o -> AssistChip(onClick = { chosen = true; newCover = o.file }, label = { Text(if (newCover == o.file) "✓ ${o.file}" else o.file) }, modifier = Modifier.testTag("newcover-${o.file}")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.confirmRemove(newCover, chosen) }, enabled = !needsCover || chosen, modifier = Modifier.testTag("remove-confirm")) { Text("Entfernen") } },
        dismissButton = { TextButton(onClick = vm::cancelRemove, modifier = Modifier.testTag("remove-cancel")) { Text("Abbrechen") } },
    )
}
