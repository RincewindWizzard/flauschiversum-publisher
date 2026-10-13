package de.flauschiversum.publisher.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.setup.KeyInstructions
import de.flauschiversum.publisher.setup.SetupFlow
import de.flauschiversum.publisher.setup.SetupStep
import de.flauschiversum.publisher.ssh.AccessResult
import de.flauschiversum.publisher.ssh.HostKeyInfo
import de.flauschiversum.publisher.ui.Platform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(vm: SetupViewModel, onFinished: () -> Unit) {
    val ui by vm.ui.collectAsState()
    if (ui.finished && ui.shown == SetupStep.CLONE && ui.clone is CloneUi.Idle) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onFinished() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Einrichtung") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Schritt ${ui.shown.number} von ${SetupStep.COUNT}: ${ui.shown.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("setup-step"))
            LinearProgressIndicator(progress = { ui.shown.number / SetupStep.COUNT.toFloat() }, modifier = Modifier.fillMaxWidth())
            when (ui.shown) {
                SetupStep.REPOSITORY -> RepositoryStep(ui, vm)
                SetupStep.KEY -> KeyStep(ui, vm)
                SetupStep.REGISTER -> RegisterStep(ui, vm)
                SetupStep.CHECK -> CheckStep(ui, vm)
                SetupStep.CLONE -> CloneStep(ui, vm)
            }
            StepNavigation(ui, vm)
        }
    }
}

@Composable
private fun StepNavigation(ui: SetupUi, vm: SetupViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val prev = SetupStep.entries.firstOrNull { it.number == ui.shown.number - 1 }
        if (prev != null) TextButton(onClick = { vm.goTo(prev) }) { Text("Zurück") }
        val next = SetupStep.entries.firstOrNull { it.number == ui.shown.number + 1 }
        if (next != null && SetupFlow.canOpen(next, ui.facts)) TextButton(onClick = { vm.goTo(next) }) { Text("Weiter") }
    }
}

@Composable
private fun RepositoryStep(ui: SetupUi, vm: SetupViewModel) {
    Text("Wo liegt das Repository des Blogs? Gib die SSH-Adresse ein, z. B. git@github.com:eigentümer/repo.git oder ssh://git@server.example:2222/srv/git/repo.git.")
    OutlinedTextField(
        value = ui.addressInput, onValueChange = vm::onAddressChanged, label = { Text("Repository-Adresse (SSH)") },
        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("address-input"), isError = ui.addressMessage != null && ui.httpsSuggestion == null,
    )
    ui.addressMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("address-message")) }
    ui.httpsSuggestion?.let { Button(onClick = vm::useSuggestion) { Text("SSH-Adresse übernehmen") } }
    Button(onClick = vm::submitAddress, modifier = Modifier.testTag("address-next")) { Text("Weiter") }
}

@Composable
private fun KeyStep(ui: SetupUi, vm: SetupViewModel) {
    val context = LocalContext.current
    if (ui.key == null) {
        Text("Die App erzeugt einen SSH-Schlüssel (Ed25519). Der private Schlüssel bleibt auf diesem Gerät und verlässt die App nie.")
        Button(onClick = vm::generateKey, modifier = Modifier.testTag("generate-key")) { Text("Schlüssel erzeugen") }
    } else {
        Text("Ein Schlüssel ist vorhanden.")
        Fingerprint(ui.key.fingerprint)
        OutlinedButton(onClick = { Platform.copy(context, "Öffentlicher Schlüssel", ui.key.publicLine, "Öffentlicher Schlüssel kopiert") }) { Text("Öffentlichen Schlüssel kopieren") }
        var confirm by remember { mutableStateOf(false) }
        TextButton(onClick = { confirm = true }) { Text("Neu erzeugen …") }
        if (confirm) AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Schlüssel ersetzen?") },
            text = { Text("Der alte Schlüssel wird unwiderruflich ersetzt. Der neue muss danach erneut eingetragen werden.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.generateKey() }) { Text("Ersetzen") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Fingerprint(text: String) {
    SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun RegisterStep(ui: SetupUi, vm: SetupViewModel) {
    val context = LocalContext.current
    val key = ui.key
    if (key == null) { Text("Zuerst muss ein Schlüssel erzeugt werden."); return }
    Text("Trage den öffentlichen Schlüssel mit Schreibrecht auf dem Server ein:")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        SelectionContainer { Text(key.publicLine, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp).testTag("public-key")) }
    }
    OutlinedButton(onClick = { Platform.copy(context, "Öffentlicher Schlüssel", key.publicLine, "Öffentlicher Schlüssel kopiert") }) { Text("Schlüssel kopieren") }
    OutlinedButton(onClick = { Platform.shareText(context, "Öffentlicher Schlüssel", key.publicLine) }) { Text("Teilen …") }
    when (val plan = ui.plan) {
        is KeyInstructions.Plan.GitHub -> {
            Text("Bei GitHub: Öffne die Seite „Deploy key hinzufügen“, gib als Titel „${plan.suggestedTitle}“ ein, füge den Schlüssel ein, hake „Allow write access“ an und speichere.", modifier = Modifier.testTag("plan-github"))
            Button(onClick = { Platform.openUrl(context, plan.url) }) { Text("GitHub-Seite öffnen") }
        }
        is KeyInstructions.Plan.OwnServer -> {
            Text("Auf deinem Server (Benutzer „${plan.user}“ auf ${plan.host}): Führe diesen Befehl aus, um den Schlüssel in die authorized_keys einzutragen. Bei Git-Hostern wie GitLab oder Gitea trägst du den Schlüssel stattdessen als Deploy Key mit Schreibrecht in der Weboberfläche ein.", modifier = Modifier.testTag("plan-server"))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                SelectionContainer { Text(plan.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) }
            }
            OutlinedButton(onClick = { Platform.copy(context, "Befehl", plan.command, "Befehl kopiert") }) { Text("Befehl kopieren") }
        }
        null -> Unit
    }
    Button(onClick = vm::confirmRegistered, modifier = Modifier.testTag("register-next")) { Text("Weiter zur Prüfung") }
}

@Composable
private fun CheckStep(ui: SetupUi, vm: SetupViewModel) {
    Text("Die App prüft jetzt die Verbindung und das Schreibrecht, ohne am Repository etwas zu ändern.")
    when (val c = ui.check) {
        CheckUi.Idle -> Button(onClick = vm::runCheck, modifier = Modifier.testTag("run-check")) { Text("Verbindung prüfen") }
        CheckUi.Running -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Prüfe …") }
        is CheckUi.NeedsHostTrust -> HostKeyDialog(c.key, onTrust = { vm.trustHostKey(c.key) }, onReject = vm::rejectHostKey)
        is CheckUi.Done -> CheckResult(c.result, ui, vm)
    }
}

@Composable
private fun HostKeyDialog(key: HostKeyInfo, onTrust: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Server bestätigen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Das ist der erste Kontakt mit ${key.hostId}. Vergleiche den Fingerabdruck, wenn du ihn kennst (z. B. aus der Dokumentation deines Anbieters).")
                Text("Typ: ${key.type.sshName}")
                Fingerprint(key.fingerprint)
            }
        },
        confirmButton = { TextButton(onClick = onTrust, modifier = Modifier.testTag("trust-host")) { Text("Vertrauen") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Abbrechen") } },
    )
}

@Composable
private fun CheckResult(result: AccessResult, ui: SetupUi, vm: SetupViewModel) {
    when (result) {
        is AccessResult.Ok -> {
            Text("Zugang eingerichtet.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("check-ok"))
            if (!result.writeConfirmed) Text("Zugang erfolgreich, Schreibrecht wird beim ersten Veröffentlichen bestätigt.", modifier = Modifier.testTag("check-write-unverified"))
            Button(onClick = vm::continueAfterCheck, modifier = Modifier.testTag("check-next")) { Text("Weiter") }
        }
        AccessResult.NoKey -> Text("Es gibt noch keinen Schlüssel.")
        is AccessResult.Failed -> {
            Text(result.failure.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("check-error"))
            if (result.failure is GitFailure.HostKeyChanged) Text("Aus Sicherheitsgründen bricht die App ab. Ist der Server neu installiert worden, kannst du den bekannten Schlüssel in den Einstellungen unter „Bekannte Server“ zurücksetzen.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::runCheck) { Text("Erneut prüfen") }
                if (result.failure is GitFailure.KeyRejected || result.failure is GitFailure.ReadOnly) {
                    OutlinedButton(onClick = { vm.goTo(SetupStep.REGISTER) }) { Text("Zum Eintragen") }
                }
            }
        }
    }
}

@Composable
private fun CloneStep(ui: SetupUi, vm: SetupViewModel) {
    when (val c = ui.clone) {
        CloneUi.Idle -> {
            Text("Jetzt holt die App das Repository (nur der aktuelle Stand, mehrere hundert MB) in ihren privaten Speicher. Es werden mindestens 1,5 GB freier Speicher gebraucht; am besten über WLAN.")
            Text("Zielbranch: ${ui.targetBranch}")
            Button(onClick = { vm.startClone() }, modifier = Modifier.testTag("start-clone")) { Text("Repository holen") }
        }
        CloneUi.ConfirmMobile -> AlertDialog(
            onDismissRequest = vm::cancelMobileConfirm,
            title = { Text("Über Mobilfunk laden?") },
            text = { Text("Du bist nicht im WLAN. Das Repository ist mehrere hundert MB groß. Wirklich über Mobilfunk laden?") },
            confirmButton = { TextButton(onClick = { vm.startClone(confirmedMobile = true) }, modifier = Modifier.testTag("confirm-mobile")) { Text("Ja, laden") } },
            dismissButton = { TextButton(onClick = vm::cancelMobileConfirm) { Text("Abbrechen") } },
        )
        is CloneUi.Running -> {
            Text("${c.task}${if (c.total > 0) " (${c.done}/${c.total})" else ""}")
            if (c.total > 0) LinearProgressIndicator(progress = { (c.done.toFloat() / c.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = vm::cancelClone, modifier = Modifier.testTag("cancel-clone")) { Text("Abbrechen") }
        }
        is CloneUi.Problem -> {
            Text(c.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("clone-problem"))
            if (c.canPickBranch) c.branches.forEach { b -> OutlinedButton(onClick = { vm.pickBranch(b) }) { Text("Branch „$b“ verwenden") } }
            Button(onClick = { vm.dismissCloneProblem() }) { Text("Erneut versuchen") }
            OutlinedButton(onClick = { vm.goTo(SetupStep.REPOSITORY) }) { Text("Adresse ändern") }
        }
    }
}
