package de.flauschiversum.publisher.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.lock.AuthResult
import kotlinx.coroutines.launch

/**
 * Zeigt [content] nur, solange die App nicht gesperrt ist. Bei Sperre erscheint ein leerer Bildschirm mit der Aufforderung zur
 * Geräte-Authentifizierung; nach Fehlversuchen bleibt es dabei, es gibt keinen anderen Weg hinein.
 */
@Composable
fun LockGate(c: AppContainer, content: @Composable () -> Unit) {
    val locked by c.lock.locked.collectAsState()
    if (!locked) { content(); return }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failures by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    fun ask() {
        if (running) return
        running = true
        scope.launch {
            when (c.authenticator.authenticate(context, "Flauschiversum entsperren")) {
                AuthResult.SUCCESS -> { failures = 0; c.lock.unlock() }
                AuthResult.FAILED, AuthResult.CANCELLED -> failures++
            }
            running = false
        }
    }
    LaunchedEffect(Unit) { ask() } // beim Öffnen sofort fragen
    Column(Modifier.fillMaxSize().padding(24.dp).testTag("lock-screen"), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Flauschiversum", style = MaterialTheme.typography.headlineMedium)
        Text("Die App ist gesperrt.", modifier = Modifier.padding(top = 8.dp))
        if (failures > 0) Text("Das Entsperren hat nicht geklappt. Bitte erneut mit der Gerätesperre versuchen.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp).testTag("lock-failed"))
        Button(onClick = ::ask, enabled = !running, modifier = Modifier.padding(top = 16.dp).testTag("unlock")) { Text("Entsperren") }
    }
}
