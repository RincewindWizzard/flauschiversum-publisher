package de.flauschiversum.publisher.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import de.flauschiversum.publisher.editor.EditState
import de.flauschiversum.publisher.editor.Finding
import de.flauschiversum.publisher.editor.FormatActions
import de.flauschiversum.publisher.editor.Highlighting
import de.flauschiversum.publisher.editor.LineIndex
import de.flauschiversum.publisher.editor.Misspelling
import de.flauschiversum.publisher.editor.Severity
import de.flauschiversum.publisher.editor.SpanKind
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
fun EditorScreen(vm: EditorViewModel, fontScale: Float, onDone: () -> Unit, onPhotos: (String) -> Unit = {}) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showDelete by remember { mutableStateOf(false) }
    LaunchedEffect(ui.saved) { if (ui.saved) onDone() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    BackHandler(enabled = true) { if (ui.showProperties) vm.showProperties(false) else vm.requestLeave(onDone) }
    Scaffold(
        topBar = { TopAppBar(
            title = { Text(if (ui.showProperties) "Eigenschaften" else ui.title.ifBlank { if (ui.isNew) "Neuer Beitrag" else ui.dir.orEmpty() }, maxLines = 1) },
            navigationIcon = { TextButton(onClick = { if (ui.showProperties) vm.showProperties(false) else vm.requestLeave(onDone) }, modifier = Modifier.testTag("editor-back")) { Text("Zurück") } },
            actions = {
                if (!ui.showProperties && !ui.isNew) TextButton(onClick = { ui.dir?.let(onPhotos) }, modifier = Modifier.testTag("editor-photos")) { Text("Fotos") }
                if (!ui.showProperties && !ui.isNew) TextButton(onClick = { showDelete = true }, modifier = Modifier.testTag("editor-delete")) { Text("Löschen") }
                if (!ui.showProperties) TextButton(onClick = { vm.showProperties(true) }, modifier = Modifier.testTag("open-properties")) { Text("Eigenschaften") }
                TextButton(onClick = vm::save, enabled = ui.loaded && ui.loadProblem == null && !ui.saving, modifier = Modifier.testTag("save")) { Text("Speichern") }
            },
        ) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (showDelete && ui.dir != null) de.flauschiversum.publisher.ui.delete.DeletePostDialog(vm.container, ui.dir!!, onDismiss = { showDelete = false },
            onDeleted = { showDelete = false; vm.markGone() }, onError = { showDelete = false; vm.report(it) })
        if (ui.confirmLeave) AlertDialog(
            onDismissRequest = vm::cancelLeave, title = { Text("Änderungen verwerfen?") },
            text = { Text("Der Beitrag enthält Änderungen, die noch nicht gespeichert sind.") },
            confirmButton = { TextButton(onClick = { vm.leaveDiscarding(onDone) }, modifier = Modifier.testTag("leave-discard")) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = vm::cancelLeave, modifier = Modifier.testTag("leave-stay")) { Text("Weiter bearbeiten") } },
        )
        ui.confirmSaveErrors?.let { n -> AlertDialog(
            onDismissRequest = vm::cancelSaveConfirm, title = { Text("Markdown-Fehler") },
            text = { Text(if (n == 1) "Der Text enthält noch 1 Fehler. Trotzdem speichern?" else "Der Text enthält noch $n Fehler. Trotzdem speichern?") },
            confirmButton = { TextButton(onClick = { vm.save(force = true) }, modifier = Modifier.testTag("save-anyway")) { Text("Trotzdem speichern") } },
            dismissButton = { TextButton(onClick = vm::cancelSaveConfirm, modifier = Modifier.testTag("save-back")) { Text("Zurück") } },
        ) }
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (ui.saving || !ui.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.loadProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp).testTag("load-problem")) }
            if (ui.loaded && ui.loadProblem == null) {
                if (ui.serverChanged) Text("Der Beitrag wurde auf dem Server geändert. Deine Änderungen bleiben erhalten; beim Speichern gewinnt deine Fassung.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).testTag("server-changed"))
                if (ui.updatedFromServer) Text("Der Beitrag wurde mit der neuesten Fassung aktualisiert.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).testTag("updated-from-server"))
                if (ui.restored) Text("Nicht gespeicherte Eingaben wurden wiederhergestellt.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).testTag("restored"))
                if (ui.showProperties) PropertiesContent(ui, vm) else BodyContent(ui, vm, fontScale)
            }
        }
    }
}

@Composable
private fun BodyContent(ui: EditorUi, vm: EditorViewModel, fontScale: Float) {
    var value by remember(ui.loaded, ui.updatedFromServer) { mutableStateOf(TextFieldValue(ui.body, TextRange(ui.body.length))) }
    var showImageDialog by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val transformation = remember(ui.body, ui.findings, ui.misspellings, scheme) { MarkdownTransformation(ui.body, ui.findings, ui.misspellings, scheme) }

    fun apply(st: EditState, record: Boolean = true) {
        value = TextFieldValue(st.text, TextRange(st.selStart, st.selEnd))
        if (record) vm.setBody(st.text, st.selStart, st.selEnd)
    }
    fun state() = EditState(value.text, value.selection.min, value.selection.max)

    // Wort unter dem Cursor, falls falsch geschrieben
    val cursor = value.selection.min
    val under = if (value.selection.collapsed) ui.misspellings.firstOrNull { cursor in it.start..it.end && it.end <= value.text.length && value.text.substring(it.start, it.end) == it.word } else null
    LaunchedEffect(under) { if (under != null) vm.requestSuggestions(under) else vm.clearSuggestions() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it; if (it.text != ui.body) vm.setBody(it.text, it.selection.min, it.selection.max) },
            textStyle = editorTextStyle(fontScale), visualTransformation = transformation,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("body-input"), label = { Text("Text (Markdown)") },
        )
        if (under != null && ui.suggestionsFor == under) SuggestionBar(under, ui.suggestions, vm) { replacement ->
            apply(EditState(value.text.replaceRange(under.start, under.end, replacement), under.start + replacement.length))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton("F", "Fett", "tool-bold") { apply(FormatActions.bold(state())) }
            ToolButton("K", "Kursiv", "tool-italic") { apply(FormatActions.italic(state())) }
            ToolButton("H", "Überschrift", "tool-heading") { apply(FormatActions.heading(state())) }
            ToolButton("•", "Aufzählung", "tool-bullet") { apply(FormatActions.bullet(state())) }
            ToolButton("Link", "Link", "tool-link") { apply(FormatActions.link(state())) }
            ToolButton("„“", "Zitat", "tool-quote") { apply(FormatActions.quote(state())) }
            ToolButton("↶", "Rückgängig", "tool-undo", enabled = ui.canUndo) { vm.undo()?.let { apply(it, record = false) } }
            ToolButton("↷", "Wiederholen", "tool-redo", enabled = ui.canRedo) { vm.redo()?.let { apply(it, record = false) } }
            OutlinedButton(onClick = { showImageDialog = true }, enabled = ui.imageFiles.isNotEmpty(), modifier = Modifier.testTag("insert-image")) { Text("Bild einfügen") }
        }
        TextButton(onClick = { vm.showFindings(true) }, modifier = Modifier.testTag("open-findings")) {
            Text("Prüfung: ${ui.errorCount} Fehler, ${ui.warningCount} Warnungen, ${ui.misspellings.size} Rechtschreibung")
        }
    }
    if (showImageDialog) ImageInsertDialog(ui.imageFiles, onDismiss = { showImageDialog = false }) { ref ->
        val r = MarkdownEdit.insertImage(value.text, value.selection.start, ref)
        apply(EditState(r.text, r.cursor)); showImageDialog = false
    }
    if (ui.showFindings) FindingsDialog(ui, onDismiss = { vm.showFindings(false) }) { f ->
        value = value.copy(selection = TextRange(f.start.coerceAtMost(value.text.length), f.end.coerceAtMost(value.text.length)))
        vm.showFindings(false)
    }
}

@Composable
private fun ToolButton(label: String, description: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = description }) { Text(label) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionBar(m: Misspelling, suggestions: List<String>, vm: EditorViewModel, onReplace: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("suggestion-bar")) {
        Text("„${m.word}“ ist unbekannt", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (suggestions.isEmpty()) Text("Keine Vorschläge", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("no-suggestions"))
            suggestions.forEach { s -> AssistChip(onClick = { onReplace(s) }, label = { Text(s) }, modifier = Modifier.testTag("suggestion-$s")) }
            AssistChip(onClick = { vm.addToDictionary(m.word) }, label = { Text("Ins Wörterbuch") }, modifier = Modifier.testTag("add-word"))
            AssistChip(onClick = { vm.ignoreWord(m.word) }, label = { Text("Ignorieren") }, modifier = Modifier.testTag("ignore-word"))
        }
    }
}

@Composable
private fun FindingsDialog(ui: EditorUi, onDismiss: () -> Unit, onJump: (Finding) -> Unit) {
    val lines = remember(ui.body) { LineIndex(ui.body) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Prüfung") },
        text = {
            if (ui.findings.isEmpty()) Text("Keine Befunde.", modifier = Modifier.testTag("no-findings"))
            else LazyColumn(Modifier.heightIn(max = 360.dp).testTag("findings-list")) {
                items(ui.findings) { f ->
                    val line = lines.lineOf(f.start.coerceAtMost(ui.body.length))
                    Text(
                        "Zeile $line · ${if (f.severity == Severity.ERROR) "Fehler" else "Warnung"}: ${f.message}",
                        color = if (f.severity == Severity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onJump(f) }.padding(vertical = 8.dp).testTag("finding-$line"),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

/** Färbt den Text nach Markdown-Elementen und markiert Befunde und Rechtschreibung; ändert den Text nicht. */
class MarkdownTransformation(
    private val text: String, private val findings: List<Finding>, private val misspellings: List<Misspelling>, private val colors: ColorScheme,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val n = text.length
        val b = AnnotatedString.Builder(text.text)
        fun add(style: SpanStyle, s: Int, e: Int) { val a = s.coerceIn(0, n); val z = e.coerceIn(0, n); if (z > a) b.addStyle(style, a, z) }
        for (sp in Highlighting.spans(text.text)) add(when (sp.kind) {
            SpanKind.HEADING -> SpanStyle(fontWeight = FontWeight.Bold, color = colors.primary)
            SpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            SpanKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            SpanKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            SpanKind.LINK -> SpanStyle(color = colors.tertiary, textDecoration = TextDecoration.Underline)
            SpanKind.IMAGE -> SpanStyle(color = colors.secondary)
            SpanKind.LIST -> SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)
            SpanKind.QUOTE -> SpanStyle(color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic)
            SpanKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceVariant)
        }, sp.start, sp.end)
        for (m in misspellings) add(SpanStyle(textDecoration = TextDecoration.Underline, color = colors.error), m.start, m.end)
        for (f in findings) add(SpanStyle(background = if (f.severity == Severity.ERROR) colors.errorContainer else colors.tertiaryContainer), f.start, f.end)
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
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
    var showTime by remember { mutableStateOf(false) }
    var showNewTag by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(ui.title, vm::setTitle, label = { Text("Titel") }, singleLine = true, isError = ui.error(Fields.TITLE) != null,
            supportingText = { ui.error(Fields.TITLE)?.let { Text(it) } }, modifier = Modifier.fillMaxWidth().testTag("prop-title"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).testTag("prop-date")) { Text("Datum: ${ui.date?.date?.toString() ?: "wählen"}") }
            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f).testTag("prop-time")) { Text(ui.date?.time?.let { "Uhrzeit: %02d:%02d".format(it.hour, it.minute) } ?: "Uhrzeit hinzufügen") }
            if (ui.date?.time != null) TextButton(onClick = { vm.setTime(null) }, modifier = Modifier.testTag("prop-time-clear")) { Text("Entfernen") }
        }
        ui.error(Fields.DATE)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(ui.permalink, vm::setPermalink, label = { Text("Permalink") }, singleLine = true, isError = ui.error(Fields.PERMALINK) != null,
            supportingText = { Text(ui.error(Fields.PERMALINK) ?: if (ui.permalinkAuto && ui.isNew) "Automatisch aus Datum und Titel" else "") }, modifier = Modifier.fillMaxWidth().testTag("prop-permalink"))
        if (ui.isNew && !ui.permalinkAuto) TextButton(onClick = vm::useAutoPermalink) { Text("Automatisch vorschlagen") }

        Text("Schlagworte", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(ui.tagQuery, vm::setTagQuery, label = { Text("Schlagwort suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("tag-search"))
        val unknown = ui.tags.filter { t -> ui.allowedTags.none { it.equals(t, true) } }
        val q = ui.tagQuery.trim()
        val shown = ui.allowedTags.filter { q.isEmpty() || it.contains(q, ignoreCase = true) }.sortedByDescending { t -> ui.tags.any { it.equals(t, true) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            unknown.forEach { t -> FilterChip(selected = true, onClick = { vm.toggleTag(t) }, label = { Text("$t ⚠") }, modifier = Modifier.testTag("tag-unknown-$t")) }
            shown.forEach { t -> FilterChip(selected = ui.tags.any { it.equals(t, true) }, onClick = { vm.toggleTag(ui.tags.firstOrNull { it.equals(t, true) } ?: t) }, label = { Text(t) }, modifier = Modifier.testTag("tag-$t")) }
            if (shown.isEmpty() && q.isNotEmpty()) Text("Kein Schlagwort gefunden.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("tag-none"))
        }
        ui.error(Fields.TAGS)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { showNewTag = true }, modifier = Modifier.testTag("new-tag")) { Text("Neues Schlagwort …") }
        ui.newTagMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("new-tag-message")) }

        OutlinedTextField(ui.author, vm::setAuthor, label = { Text("Autor") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("prop-author"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.knownAuthors.forEach { a -> AssistChip(onClick = { vm.setAuthor(a) }, label = { Text(a) }, modifier = Modifier.testTag("author-$a")) }
        }

        Text("Titelbild", style = MaterialTheme.typography.titleSmall)
        if (ui.isNew || ui.imageFiles.isEmpty()) Text(if (ui.isNew) "Bilder lassen sich nach dem ersten Speichern über „Fotos teilen“ hinzufügen." else "Im Beitragsverzeichnis liegen keine Bilder.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.image == null, onClick = { vm.setImage(null) }, label = { Text("Kein Titelbild") }, modifier = Modifier.testTag("cover-none"))
            ui.imageFiles.forEach { f -> CoverTile(f, selected = ui.image == f, vm = vm) { vm.setImage(f) } }
            if (ui.image != null && ui.image !in ui.imageFiles) FilterChip(selected = true, onClick = { vm.setImage(null) }, label = { Text("${ui.image} (fehlt)") }, modifier = Modifier.testTag("cover-missing"))
        }
        ui.error(Fields.IMAGE)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("cover-error")) }

        val excerptTransformation = remember(ui.excerpt, ui.excerptMisspellings, scheme) { UnderlineTransformation(ui.excerptMisspellings, scheme.error) }
        OutlinedTextField(ui.excerpt, vm::setExcerpt, label = { Text("Kurzbeschreibung") }, minLines = 2, visualTransformation = excerptTransformation,
            supportingText = { Text("${ui.excerpt.length} Zeichen" + if (ui.excerptMisspellings.isNotEmpty()) " · Rechtschreibung: " + ui.excerptMisspellings.joinToString { it.word } else "", modifier = Modifier.testTag("excerpt-counter")) },
            modifier = Modifier.fillMaxWidth().testTag("prop-excerpt"))
        TextButton(onClick = vm::excerptFromText, modifier = Modifier.testTag("excerpt-from-text")) { Text("Aus Text übernehmen") }
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
    if (showNewTag) NewTagDialog(ui.allowedTags, onDismiss = { showNewTag = false }) { name -> vm.createTag(name); showNewTag = false }
    if (showTime) {
        val t = ui.date?.time
        val state = rememberTimePickerState(initialHour = t?.hour ?: 12, initialMinute = t?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false }, title = { Text("Uhrzeit") },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { vm.setTime(java.time.LocalTime.of(state.hour, state.minute, t?.second ?: 0)); showTime = false }, modifier = Modifier.testTag("time-ok")) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun CoverTile(file: String, selected: Boolean, vm: EditorViewModel, onClick: () -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) { value = vm.thumbnail(file) }
    Column(
        Modifier.width(96.dp).clickable(onClick = onClick).testTag("cover-$file")
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bmp = bitmap
        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = file, modifier = Modifier.size(88.dp), contentScale = ContentScale.Crop)
        else Box(Modifier.size(88.dp))
        Text(file, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Unterstreicht falsch geschriebene Wörter (für kurze Felder ohne Markdown). */
class UnderlineTransformation(private val words: List<Misspelling>, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text.text)
        for (m in words) if (m.end <= text.length && m.start < m.end) b.addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = color), m.start, m.end)
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
private fun NewTagDialog(existing: List<String>, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val error = if (name.isEmpty()) null else de.flauschiversum.publisher.posts.TagsFile.validate(name, existing)
    val duplicate = de.flauschiversum.publisher.posts.TagsFile.match(name.trim(), existing)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Neues Schlagwort") },
        text = { Column {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, isError = error != null,
                supportingText = { Text(error ?: "${name.trim().length}/${de.flauschiversum.publisher.posts.TagsFile.MAX_LENGTH}") }, modifier = Modifier.testTag("new-tag-name"))
            if (duplicate != null) TextButton(onClick = { onCreate(duplicate) }, modifier = Modifier.testTag("new-tag-use-existing")) { Text("„$duplicate“ auswählen") }
        } },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank() && de.flauschiversum.publisher.posts.TagsFile.validate(name, existing) == null, modifier = Modifier.testTag("new-tag-create")) { Text("Anlegen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
