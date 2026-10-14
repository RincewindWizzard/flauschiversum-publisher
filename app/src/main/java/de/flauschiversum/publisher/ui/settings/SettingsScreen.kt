package de.flauschiversum.publisher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.BuildConfig
import de.flauschiversum.publisher.ui.Platform
import de.flauschiversum.publisher.ui.theme.ThemeMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import de.flauschiversum.publisher.ui.components.AppOutlinedButton
import de.flauschiversum.publisher.ui.components.AppTextButton
import de.flauschiversum.publisher.ui.components.AppIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onSetup: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val s = ui.settings
    val context = LocalContext.current
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = { TopAppBar(title = { Text("Einstellungen") }, navigationIcon = { AppIconButton(icon = Icons.Default.ArrowBack, label = "Zurück", onClick = onBack, modifier = Modifier.testTag("settings-back")) }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Section("Repository")
            Text("Adresse: ${s.repositoryAddress}", modifier = Modifier.testTag("settings-address"))
            AppOutlinedButton(onClick = { vm.changeAddress(onSetup) }, label = "Adresse ändern …", icon = Icons.Default.Edit)
            TextField("Zielbranch", s.targetBranch, "settings-branch") { v -> vm.update { it.copy(targetBranch = v.trim().ifEmpty { "main" }) } }
            var confirmReinstall by remember { mutableStateOf(false) }
            AppOutlinedButton(onClick = { confirmReinstall = true }, label = "Repository neu holen …", icon = Icons.Default.Refresh)
            if (confirmReinstall) AlertDialog(
                onDismissRequest = { confirmReinstall = false },
                title = { Text("Repository neu holen?") },
                text = { Text("Der lokale Klon wird gelöscht und neu geholt. Nicht hochgeladene Entwürfe gehen dabei verloren. Der Schlüssel bleibt.") },
                confirmButton = { AppTextButton(onClick = { confirmReinstall = false; vm.reinstallRepository(onSetup) }, label = "Neu holen", icon = Icons.Default.Refresh) },
                dismissButton = { AppTextButton(onClick = { confirmReinstall = false }, label = "Abbrechen", icon = Icons.Default.Close) },
            )
            HorizontalDivider()

            Section("Commits und Bilder")
            TextField("Name für Commits", s.commitName, "settings-commit-name") { v -> vm.update { it.copy(commitName = v) } }
            TextField("E-Mail für Commits", s.commitEmail, "settings-commit-email") { v -> vm.update { it.copy(commitEmail = v) } }
            TextField("Autor für neue Beiträge", s.defaultAuthor, "settings-author") { v -> vm.update { it.copy(defaultAuthor = v) } }
            TextField("Rechtetext für Bilder (Urheber)", s.rightsText, "settings-rights") { v -> vm.update { it.copy(rightsText = v) } }
            HorizontalDivider()

            Section("Darstellung")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = s.themeMode == m, onClick = { vm.setTheme(m) }, label = { Text(when (m) { ThemeMode.SYSTEM -> "System"; ThemeMode.LIGHT -> "Hell"; ThemeMode.DARK -> "Dunkel" }) }, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag("theme-${m.name}"))
                }
            }
            Text("Schriftgröße im Editor: ${(s.editorFontScale * 100).toInt()} %")
            Slider(value = s.editorFontScale, onValueChange = { v -> vm.update { it.copy(editorFontScale = (Math.round(v * 20) / 20f)) } }, valueRange = 0.8f..1.6f)
            HorizontalDivider()

            Section("Hochladen")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = s.uploadWifiOnly, onCheckedChange = { v -> vm.update { it.copy(uploadWifiOnly = v) } }, modifier = Modifier.heightIn(min = 48.dp).testTag("wifi-only").semantics { contentDescription = "Nur über WLAN hochladen" })
                Text("Nur über WLAN hochladen")
            }
            Text("Manche Hersteller beenden Hintergrundaufgaben früh. Wenn Uploads nicht von selbst laufen, nimm die App von der Akku-Optimierung aus.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("battery-hint"))
            AppOutlinedButton(onClick = { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.testTag("battery-settings"), label = "Akku-Einstellungen öffnen", icon = Icons.Default.Settings)
            HorizontalDivider()

            Section("SSH-Schlüssel")
            Text(ui.keyFingerprint ?: "Kein Schlüssel vorhanden", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            ui.publicKey?.let { pk -> AppOutlinedButton(onClick = { Platform.copy(context, "Öffentlicher Schlüssel", pk, "Öffentlicher Schlüssel kopiert") }, label = "Öffentlichen Schlüssel kopieren", icon = Icons.Default.ContentCopy) }
            AppOutlinedButton(onClick = { vm.forgetRegistration(onSetup) }, label = "Schlüssel erneut eintragen / prüfen …", icon = Icons.Default.Refresh)
            HorizontalDivider()

            Section("Bekannte Server")
            Text("Fest hinterlegt: " + ui.pinned.keys.joinToString(), style = MaterialTheme.typography.bodySmall)
            if (ui.hostKeys.isEmpty()) Text("Noch keine bestätigten Server.", style = MaterialTheme.typography.bodySmall)
            var toRemove by remember { mutableStateOf<String?>(null) }
            ui.hostKeys.forEach { h ->
                Column(Modifier.fillMaxWidth().testTag("hostkey-${h.hostId}")) {
                    Text("${h.hostId} · ${h.type.sshName}", style = MaterialTheme.typography.bodyMedium)
                    Text(h.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    AppTextButton(onClick = { toRemove = h.hostId }, label = "Zurücksetzen …", icon = Icons.Default.Refresh)
                }
            }
            toRemove?.let { id -> AlertDialog(
                onDismissRequest = { toRemove = null },
                title = { Text("Server zurücksetzen?") },
                text = { Text("Beim nächsten Kontakt mit $id fragt die App erneut nach der Bestätigung des Host-Schlüssels. Mache das nur, wenn der Server bewusst neu installiert wurde.") },
                confirmButton = { AppTextButton(onClick = { vm.removeHostKey(id); toRemove = null }, label = "Zurücksetzen", icon = Icons.Default.Refresh) },
                dismissButton = { AppTextButton(onClick = { toRemove = null }, label = "Abbrechen", icon = Icons.Default.Close) },
            ) }
            HorizontalDivider()

            Section("Persönliches Wörterbuch")
            var newWord by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newWord, { newWord = it }, label = { Text("Neues Wort") }, singleLine = true, modifier = Modifier.weight(1f).testTag("dict-new"))
                AppOutlinedButton(onClick = { vm.addDictionaryWord(newWord); newWord = "" }, enabled = newWord.isNotBlank(), modifier = Modifier.testTag("dict-add"), label = "Hinzufügen", icon = Icons.Default.Add)
            }
            if (ui.dictionary.isEmpty()) Text("Noch keine eigenen Wörter. Unbekannte Wörter lassen sich im Editor aufnehmen.", style = MaterialTheme.typography.bodySmall)
            ui.dictionary.forEach { w ->
                Row(Modifier.fillMaxWidth().testTag("dict-$w"), verticalAlignment = Alignment.CenterVertically) {
                    Text(w, Modifier.weight(1f))
                    AppTextButton(onClick = { vm.removeDictionaryWord(w) }, modifier = Modifier.testTag("dict-remove-$w"), label = "Entfernen", icon = Icons.Default.Delete)
                }
            }
            HorizontalDivider()

            Section("Sicherheit")
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = s.lockEnabled, onCheckedChange = { v -> scope.launch { vm.setLock(context, v) } },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("lock-switch").semantics { contentDescription = "App-Sperre" })
                Text("App-Sperre mit Gerätesperre")
            }
            ui.lockMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("lock-message")) }
            if (s.lockEnabled) {
                Text("Sperren nach dem Verlassen", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.flauschiversum.publisher.lock.LockDelay.entries.forEach { d ->
                        FilterChip(selected = s.lockDelay == d.name, onClick = { vm.update { it.copy(lockDelay = d.name) } }, label = { Text(d.label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("lock-delay-${d.name}"))
                    }
                }
            }
            HorizontalDivider()

            Section("Diagnose")
            var showLog by remember { mutableStateOf(false) }
            var confirmClear by remember { mutableStateOf(false) }
            Text("Das Protokoll bleibt auf dem Gerät. Es enthält nur Zeit, Vorgang, Ergebnis, Fehlerart und Server – nie Schlüssel oder Beitragstexte.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppOutlinedButton(onClick = { showLog = true }, modifier = Modifier.testTag("diag-show"), label = "Anzeigen", icon = Icons.Default.Visibility)
                AppOutlinedButton(onClick = { runCatching { context.startActivity(de.flauschiversum.publisher.diag.DiagnosticShare.intent(context, vm.container.diag)) } }, modifier = Modifier.testTag("diag-share"), label = "Teilen", icon = Icons.Default.Share)
                AppOutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.testTag("diag-clear"), label = "Löschen", icon = Icons.Default.Delete)
            }
            if (showLog) AlertDialog(
                onDismissRequest = { showLog = false }, title = { Text("Protokoll") },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(vm.container.diag.text().ifBlank { "Das Protokoll ist leer." }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("diag-text")) } },
                confirmButton = { AppTextButton(onClick = { showLog = false }, modifier = Modifier.testTag("diag-close"), label = "Schließen", icon = Icons.Default.Close) },
            )
            if (confirmClear) AlertDialog(
                onDismissRequest = { confirmClear = false }, title = { Text("Protokoll löschen?") },
                confirmButton = { AppTextButton(onClick = { vm.container.diag.clear(); confirmClear = false }, modifier = Modifier.testTag("diag-clear-confirm"), label = "Löschen", icon = Icons.Default.Delete) },
                dismissButton = { AppTextButton(onClick = { confirmClear = false }, label = "Abbrechen", icon = Icons.Default.Close) },
            )
            HorizontalDivider()

            Section("Über")
            Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", modifier = Modifier.testTag("about-version"))
            Text("Build ${BuildConfig.BUILD_ID}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("about-build"))
            Text(
                "Datenschutz: Die App verbindet sich nur per SSH mit dem eingestellten Git-Server. Es gibt keine Analyse, Werbung oder Absturzberichte an Dritte. " +
                    "Schlüssel, Klon, Aufträge und Protokoll liegen im privaten App-Speicher und sind von Backups ausgenommen. Die Vorschau lädt nichts aus dem Netz.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("about-privacy"),
            )
            var showLicenses by remember { mutableStateOf(false) }
            AppOutlinedButton(onClick = { showLicenses = true }, modifier = Modifier.testTag("about-licenses"), label = "Lizenzen", icon = Icons.Default.Description)
            if (showLicenses) AlertDialog(
                onDismissRequest = { showLicenses = false }, title = { Text("Lizenzen") },
                text = { Column(Modifier.verticalScroll(rememberScrollState()).testTag("licenses-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.flauschiversum.publisher.Licenses.entries.forEach { e ->
                        Column { Text(e.name, style = MaterialTheme.typography.titleSmall); Text(e.license, style = MaterialTheme.typography.bodySmall); e.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    }
                } },
                confirmButton = { AppTextButton(onClick = { showLicenses = false }, modifier = Modifier.testTag("licenses-close"), label = "Schließen", icon = Icons.Default.Close) },
            )
        }
    }
}

@Composable
private fun Section(title: String) { Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }

@Composable
private fun TextField(label: String, value: String, tag: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag(tag))
}
