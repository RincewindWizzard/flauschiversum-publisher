package de.flauschiversum.publisher.ui.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import de.flauschiversum.publisher.ui.components.AppButton
import de.flauschiversum.publisher.ui.components.AppOutlinedButton

/** Wohin mit den geteilten Fotos? Beitrag wählen oder neu anlegen, Beschreibung je Bild, Speichern oder Abbrechen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScreen(vm: ShareViewModel, onClose: () -> Unit, beforeSave: ((proceed: () -> Unit) -> Unit)? = null) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.done) { if (ui.done) onClose() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (ui.photos.size == 1) "1 Foto ablegen" else "${ui.photos.size} Fotos ablegen") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                AppOutlinedButton(onClick = vm::cancel, modifier = Modifier.padding(end = 8.dp).testTag("share-cancel"), label = "Abbrechen", icon = Icons.Default.Close)
                AppButton(onClick = { if (beforeSave != null) beforeSave { vm.save() } else vm.save() }, enabled = ui.canSave, modifier = Modifier.testTag("share-save"), label = if (ui.saving) "Speichert …" else "Ablegen")
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp).testTag("share-list")) {
            item { Text("Beschreibung der Bilder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(ui.photos) { i, p ->
                PhotoPreview(p.file, Modifier.padding(top = 4.dp).testTag("preview-$i"))
                OutlinedTextField(
                    value = ui.alts.getOrNull(i).orEmpty(), onValueChange = { vm.setAlt(i, it) },
                    label = { Text("Beschreibung: ${p.displayName ?: "Foto ${i + 1}"}") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("alt-$i"),
                )
            }
            item { Text("Wohin damit?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                Row(Modifier.fillMaxWidth().clickable { vm.chooseNew() }.testTag("choose-new"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ui.creatingNew, onClick = vm::chooseNew)
                    Text("Neuen Beitrag anlegen")
                }
            }
            if (ui.creatingNew) item {
                OutlinedTextField(value = ui.newTitle, onValueChange = vm::setNewTitle, label = { Text("Titel des neuen Beitrags") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new-title"))
            }
            item {
                OutlinedTextField(value = ui.query, onValueChange = vm::setQuery, label = { Text("Beitrag suchen") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("post-search"))
            }
            items(ui.filtered, key = { it.dir }) { post ->
                Row(Modifier.fillMaxWidth().clickable { vm.select(post.dir) }.testTag("post-${post.dir}"), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = ui.selectedDir == post.dir, onClick = { vm.select(post.dir) })
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(post.title)
                        post.date?.let { Text(it.render(), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(file: java.io.File, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, o)
                var sample = 1
                while (o.outWidth / (sample * 2) >= 240 && o.outHeight / (sample * 2) >= 240) sample *= 2
                android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Vorschau", modifier = modifier.height(120.dp), contentScale = ContentScale.Fit) }
}
