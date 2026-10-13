package de.flauschiversum.publisher.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.posts.Fields
import de.flauschiversum.publisher.posts.ImageRef
import de.flauschiversum.publisher.posts.MarkdownEdit
import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.ui.theme.editorTextStyle
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, fontScale: Float, onDone: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.saved) { if (ui.saved) onDone() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    BackHandler(enabled = ui.showProperties) { vm.showProperties(false) }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(if (ui.showProperties) "Eigenschaften" else ui.title.ifBlank { if (ui.isNew) "Neuer Beitrag" else ui.dir.orEmpty() }, maxLines = 1) },
            navigationIcon = { TextButton(onClick = { if (ui.showProperties) vm.showProperties(false) else onDone() }, modifier = Modifier.testTag("editor-back")) { Text("Zurück") } },
            actions = {
                if (!ui.showProperties) TextButton(onClick = { vm.showProperties(true) }, modifier = Modifier.testTag("open-properties")) { Text("Eigenschaften") }
                TextButton(onClick = vm::save, enabled = ui.loaded && ui.loadProblem == null && !ui.saving, modifier = Modifier.testTag("save")) { Text("Speichern") }
            },
        ) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.saving || !ui.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.loadProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp).testTag("load-problem")) }
            if (ui.loaded && ui.loadProblem == null) {
                if (ui.restored) Text("Nicht gespeicherte Eingaben wurden wiederhergestellt.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).testTag("restored"))
                if (ui.showProperties) PropertiesContent(ui, vm) else BodyContent(ui, vm, fontScale)
            }
        }
    }
}

@Composable
private fun BodyContent(ui: EditorUi, vm: EditorViewModel, fontScale: Float) {
    var value by remember(ui.loaded) { mutableStateOf(TextFieldValue(ui.body, TextRange(ui.body.length))) }
    var showImageDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = value, onValueChange = { value = it; vm.setBody(it.text) }, textStyle = editorTextStyle(fontScale),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("body-input"), label = { Text("Text (Markdown)") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { showImageDialog = true }, enabled = ui.imageFiles.isNotEmpty(), modifier = Modifier.testTag("insert-image")) { Text("Bild einfügen") }
        }
    }
    if (showImageDialog) ImageInsertDialog(ui.imageFiles, onDismiss = { showImageDialog = false }) { ref ->
        val r = MarkdownEdit.insertImage(value.text, value.selection.start, ref)
        value = TextFieldValue(r.text, TextRange(r.cursor)); vm.setBody(r.text); showImageDialog = false
    }
}

@Composable
private fun ImageInsertDialog(files: List<String>, onDismiss: () -> Unit, onInsert: (ImageRef) -> Unit) {
    var selected by remember { mutableStateOf(files.firstOrNull()) }
    var alt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Bild einfügen") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            files.forEach { f -> Row { FilterChip(selected = selected == f, onClick = { selected = f }, label = { Text(f) }) } }
            OutlinedTextField(value = alt, onValueChange = { alt = it }, label = { Text("Beschreibung (Alt-Text)") }, modifier = Modifier.testTag("alt-input"))
        } },
        confirmButton = { TextButton(onClick = { selected?.let { onInsert(ImageRef(alt, it)) } }, enabled = selected != null, modifier = Modifier.testTag("insert-confirm")) { Text("Einfügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PropertiesContent(ui: EditorUi, vm: EditorViewModel) {
    var showDate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(ui.title, vm::setTitle, label = { Text("Titel") }, singleLine = true, isError = ui.error(Fields.TITLE) != null,
            supportingText = { ui.error(Fields.TITLE)?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("prop-title"))
        OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth().testTag("prop-date")) { Text("Datum: ${ui.date?.render() ?: "wählen"}") }
        ui.error(Fields.DATE)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(ui.permalink, vm::setPermalink, label = { Text("Permalink") }, singleLine = true, isError = ui.error(Fields.PERMALINK) != null,
            supportingText = { Text(ui.error(Fields.PERMALINK) ?: if (ui.permalinkAuto && ui.isNew) "Automatisch aus Datum und Titel" else "") }, modifier = Modifier.fillMaxWidth().testTag("prop-permalink"))
        if (ui.isNew && !ui.permalinkAuto) TextButton(onClick = vm::useAutoPermalink) { Text("Automatisch vorschlagen") }
        Text("Schlagworte", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.allowedTags.forEach { t -> FilterChip(selected = ui.tags.any { it.equals(t, true) }, onClick = { vm.toggleTag(ui.tags.firstOrNull { it.equals(t, true) } ?: t) }, label = { Text(t) }, modifier = Modifier.testTag("tag-$t")) }
        }
        ui.error(Fields.TAGS)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(ui.author, vm::setAuthor, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("prop-author"))
        OutlinedTextField(ui.excerpt, vm::setExcerpt, label = { Text("Kurzbeschreibung") }, minLines = 2, modifier = Modifier.fillMaxWidth().testTag("prop-excerpt"))
        Button(onClick = { vm.showProperties(false) }) { Text("Fertig") }
    }
    if (showDate) {
        val initial = ui.date?.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { ms -> vm.setDate(PostDate(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate(), ui.date?.time)) }
                showDate = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = state) }
    }
}
