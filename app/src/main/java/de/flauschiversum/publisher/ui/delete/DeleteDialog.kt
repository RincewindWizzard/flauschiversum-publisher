package de.flauschiversum.publisher.ui.delete

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.posts.DeletionInfo
import de.flauschiversum.publisher.posts.PostDeletion
import de.flauschiversum.publisher.posts.PostReader
import de.flauschiversum.publisher.posts.PostSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Auswahl des Weiterleitungsziels. */
sealed interface RedirectChoice {
    data object Overview : RedirectChoice
    data object Home : RedirectChoice
    data class Post(val dir: String) : RedirectChoice
    data object None : RedirectChoice
}

class DeleteData(val info: DeletionInfo, val others: List<PostSummary>)

object DeleteActions {
    suspend fun load(c: AppContainer, dir: String): DeleteData? = withContext(Dispatchers.IO) {
        c.session.locked {
            val wf = c.session.workflow() ?: return@locked null
            val info = PostDeletion.info(wf, dir) ?: return@locked null
            DeleteData(info, PostReader(wf.store).summaries(wf.baseRev()).filter { it.dir != dir })
        }
    }

    /** Führt das Löschen aus; gibt eine Fehlermeldung zurück oder null bei Erfolg. */
    suspend fun delete(c: AppContainer, dir: String, choice: RedirectChoice): String? = withContext(Dispatchers.IO) {
        try {
            c.session.locked {
                val wf = c.session.workflow() ?: error("Das Repository ist nicht eingerichtet.")
                val target = when (choice) {
                    RedirectChoice.Overview -> PostDeletion.DEFAULT_TARGET
                    RedirectChoice.Home -> "/"
                    is RedirectChoice.Post -> PostDeletion.pathOf(wf, choice.dir)
                    RedirectChoice.None -> null
                }
                PostDeletion.delete(wf, dir, target)
            }
            c.uploadDraftInBackground()
            null
        } catch (e: Exception) {
            c.session.classify(e).let { if (it is GitFailure.Other) "Löschen fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}" else it.message }
        }
    }
}

/** Sicherheitsabfrage zum Löschen mit Wahl des Weiterleitungsziels und Hinweis auf verweisende Beiträge. */
@Composable
fun DeletePostDialog(c: AppContainer, dir: String, onDismiss: () -> Unit, onDeleted: () -> Unit, onError: (String) -> Unit) {
    val data by produceState<DeleteData?>(null, dir) { value = DeleteActions.load(c, dir) }
    var choice by remember { mutableStateOf<RedirectChoice>(RedirectChoice.Overview) }
    var query by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (start) {
            val err = DeleteActions.delete(c, dir, choice)
            working = false; start = false
            if (err == null) onDeleted() else onError(err)
        }
    }
    val d = data
    if (d == null) { AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, text = { Text("Lädt …") }); return }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (d.info.published) "Beitrag löschen?" else "Entwurf verwerfen?", modifier = Modifier.testTag("delete-title")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("delete-dialog")) {
                Text("„${d.info.title}“", style = MaterialTheme.typography.titleMedium)
                Text(d.info.oldPath, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("delete-path"))
                if (!d.info.published) {
                    Text("Der Beitrag gibt es nur als Entwurf; er wird verworfen.", modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text("Der Beitrag bleibt im Blog, bis du veröffentlichst. Bis dahin lässt er sich über „Verwerfen“ wiederherstellen.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    if (d.info.referrers.isNotEmpty()) Text(
                        "Links auf diese Adresse haben: " + d.info.referrers.joinToString { "„$it“" },
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("delete-referrers"),
                    )
                    Text("Alte Adresse weiterleiten auf:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    ChoiceRow("Beitragsübersicht (/beitraege/)", choice == RedirectChoice.Overview, "redirect-overview") { choice = RedirectChoice.Overview }
                    ChoiceRow("Startseite (/)", choice == RedirectChoice.Home, "redirect-home") { choice = RedirectChoice.Home }
                    ChoiceRow("Keine Weiterleitung", choice == RedirectChoice.None, "redirect-none") { choice = RedirectChoice.None }
                    ChoiceRow("Anderer Beitrag", choice is RedirectChoice.Post, "redirect-post") { if (choice !is RedirectChoice.Post) choice = d.others.firstOrNull()?.let { RedirectChoice.Post(it.dir) } ?: RedirectChoice.Overview }
                    if (choice is RedirectChoice.Post) {
                        OutlinedTextField(query, { query = it }, label = { Text("Beitrag suchen") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("redirect-search"))
                        val shown = d.others.filter { query.isBlank() || it.title.contains(query.trim(), true) }.take(30)
                        Column {
                            shown.forEach { p ->
                                Row(Modifier.fillMaxWidth().clickable { choice = RedirectChoice.Post(p.dir) }.testTag("redirect-to-${p.dir}"), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton((choice as RedirectChoice.Post).dir == p.dir, { choice = RedirectChoice.Post(p.dir) })
                                    Text(p.title)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { working = true; start = true }, enabled = !working, modifier = Modifier.testTag("delete-confirm")) { Text(if (d.info.published) "Löschen" else "Verwerfen") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working, modifier = Modifier.testTag("delete-cancel")) { Text("Abbrechen") } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label)
    }
}
