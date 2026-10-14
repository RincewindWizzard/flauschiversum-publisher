package de.flauschiversum.publisher

import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val container: AppContainer get() = (application as PublisherApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecure(container.settingsSnapshot.lockEnabled)
        handleShare(intent)
        if (intent?.getBooleanExtra(de.flauschiversum.publisher.upload.UploadNotifications.EXTRA_OPEN_OUTBOX, false) == true) container.openOutbox.value = true
        container.appScope.launch(Dispatchers.IO) { container.uploads.cleanup(); container.uploads.resumeAll() }
        setContent {
            val settings by container.settings.settings.collectAsState(initial = container.settingsSnapshot)
            var start by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) { start = withContext(Dispatchers.IO) { startRoute() } }
            // FLAG_SECURE: bei aktiver Sperre zeigt die Übersicht der zuletzt benutzten Apps keinen Inhalt
            LaunchedEffect(settings.lockEnabled) { applySecure(settings.lockEnabled) }
            FlauschiversumPublisherTheme(settings.themeMode) {
                de.flauschiversum.publisher.ui.lock.LockGate(container) {
                    start?.let { AppNav(container, rememberNavController(), it) }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
        if (intent.getBooleanExtra(de.flauschiversum.publisher.upload.UploadNotifications.EXTRA_OPEN_OUTBOX, false)) container.openOutbox.value = true
    }

    /** Geteilte Bilder in den App-Speicher kopieren; die Navigation zeigt dann die Zuordnung. */
    private fun handleShare(intent: android.content.Intent?) {
        if (!container.incoming.isShare(intent) || intent?.getBooleanExtra(HANDLED, false) == true) return
        intent?.putExtra(HANDLED, true)
        val shared = intent!!
        kotlinx.coroutines.runBlocking(Dispatchers.IO) { container.incoming.receive(shared) }
    }

    private fun applySecure(enabled: Boolean) {
        if (enabled) window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.lock.onLeave() // Drehen sperrt nicht
    }

    override fun onStart() {
        container.lock.onReturn()
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

    private companion object { const val HANDLED = "de.flauschiversum.publisher.HANDLED" }
}
