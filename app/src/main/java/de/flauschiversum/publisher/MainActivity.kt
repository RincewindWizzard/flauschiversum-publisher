package de.flauschiversum.publisher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.setup.SetupFacts
import de.flauschiversum.publisher.setup.SetupFlow
import de.flauschiversum.publisher.ui.AppNav
import de.flauschiversum.publisher.ui.Routes
import de.flauschiversum.publisher.ui.theme.FlauschiversumPublisherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PublisherApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsState(initial = container.settingsSnapshot)
            var start by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { start = withContext(Dispatchers.IO) { startRoute() } }
            FlauschiversumPublisherTheme(settings.themeMode) {
                start?.let { AppNav(container, rememberNavController(), it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.requestSync(SyncReason.FOREGROUND)
    }

    private suspend fun startRoute(): String {
        val c = container
        val s = c.settings.current()
        val m = c.setupState.current()
        val facts = SetupFacts(
            m.confirmedAddress, s.repositoryAddress, c.keyStore.load()?.fingerprint, m.registeredFor, m.accessVerifiedFor, m.cloneVerifiedFor,
            s.targetBranch, c.session.dir.exists(),
        )
        return if (SetupFlow.currentStep(facts) == null && c.session.isInstalled()) Routes.HOME else Routes.SETUP
    }
}
