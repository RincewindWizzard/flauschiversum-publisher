package de.flauschiversum.publisher.ui.preview

import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.posts.PostEditing
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.preview.PreviewInput
import de.flauschiversum.publisher.preview.PreviewRenderer
import de.flauschiversum.publisher.preview.PreviewWebView
import de.flauschiversum.publisher.preview.openExternally
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Was die Vorschau braucht: die Eingabe und die Dateien im Beitragsverzeichnis samt Stand, aus dem Bilder gelesen werden. */
class PreviewData(val input: PreviewInput, val files: List<String>, val rev: String)

object PreviewLoader {
    /** Liest einen Beitrag aus dem Repository (Entwurf, sonst Zielbranch). */
    suspend fun load(c: AppContainer, dir: String): PreviewData? = withContext(Dispatchers.IO) {
        c.session.locked {
            val wf = c.session.workflow() ?: return@locked null
            val rev = wf.revFor(dir)
            val doc = PostReader(wf.store).document(rev, dir) ?: return@locked null
            val p = runCatching { PostEditing.read(doc) }.getOrNull() ?: return@locked null
            PreviewData(PreviewInput(dir, p.title, p.date, p.author, p.tags, p.image, doc.body), PostReader(wf.store).files(rev, dir), rev)
        }
    }
}

/** Die Vorschau selbst (ohne Rahmen), wiederverwendbar im Editor und als eigener Bildschirm. */
@Composable
fun PreviewPane(c: AppContainer, data: PreviewData, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsState(initial = c.settingsSnapshot)
    val dark = settings.themeMode.isDark(isSystemInDarkTheme())
    var link by remember { mutableStateOf<Uri?>(null) }
    var showHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!c.settingsSnapshot.previewHintShown) { showHint = true; c.settings.update { it.copy(previewHintShown = true) } } }
    val html = remember(data, dark) { PreviewRenderer.render(data.input, data.files, dark) }
    Column(modifier) {
        if (showHint) Card(Modifier.fillMaxWidth().padding(8.dp).testTag("preview-hint")) {
            Column(Modifier.padding(12.dp)) {
                Text("Die Vorschau ist eine Annäherung: Suche, verwandte Beiträge, Navigation und Wasserzeichen auf den Bildern erscheinen nicht.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showHint = false }, modifier = Modifier.testTag("preview-hint-ok")) { Text("Verstanden") }
            }
        }
        PreviewWebView(html, imageBytes = { dir, file -> c.session.store()?.readBytes(data.rev, "${PostReader.POSTS_DIR}/$dir/$file") }, onLink = { link = it }, modifier = Modifier.fillMaxSize())
    }
    link?.let { u ->
        AlertDialog(
            onDismissRequest = { link = null }, title = { Text("Im Browser öffnen?") }, text = { Text(u.toString(), modifier = Modifier.testTag("link-url")) },
            confirmButton = { TextButton(onClick = { openExternally(context, u); link = null }, modifier = Modifier.testTag("link-open")) { Text("Öffnen") } },
            dismissButton = { TextButton(onClick = { link = null }, modifier = Modifier.testTag("link-cancel")) { Text("Abbrechen") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(c: AppContainer, dir: String, onBack: () -> Unit) {
    val data by produceState<PreviewData?>(null, dir) { value = PreviewLoader.load(c, dir) }
    Scaffold(topBar = { TopAppBar(title = { Text("Vorschau: ${data?.input?.title ?: dir}", maxLines = 1) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("preview-back")) { Text("Zurück") } }) }) { pad ->
        val d = data
        if (d == null) Text("Lädt …", Modifier.padding(pad).padding(16.dp)) else PreviewPane(c, d, Modifier.padding(pad))
    }
}
