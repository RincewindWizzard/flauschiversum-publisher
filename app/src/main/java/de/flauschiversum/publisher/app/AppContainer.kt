package de.flauschiversum.publisher.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.flauschiversum.publisher.data.AppSettings
import de.flauschiversum.publisher.data.SettingsRepository
import de.flauschiversum.publisher.git.RepoSession
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.git.SyncService
import de.flauschiversum.publisher.setup.SetupStateRepository
import de.flauschiversum.publisher.ssh.AccessChecker
import de.flauschiversum.publisher.ssh.HostKeyStore
import de.flauschiversum.publisher.ssh.SshKeyStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

/** Verbindet die Teile der App (einfache Abhängigkeitsverwaltung von Hand). */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val filesDir: File = context.filesDir

    val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = appScope) { File(filesDir, "datastore/settings.preferences_pb") })
    val setupState = SetupStateRepository(PreferenceDataStoreFactory.create(scope = appScope) { File(filesDir, "datastore/setup.preferences_pb") })

    @Volatile var settingsSnapshot: AppSettings = runBlocking { settings.current() }
        private set

    val keyStore = SshKeyStore(File(filesDir, "ssh"))
    val hostKeys = HostKeyStore(File(filesDir, "ssh/known_hosts"))
    val network = AndroidNetworkState(context)
    val session = RepoSession(File(filesDir, "repo.git"), { settingsSnapshot }, keyStore, hostKeys, network)
    val accessChecker = AccessChecker(keyStore, hostKeys, network)

    val sync = SyncService(
        scope = appScope,
        classify = { session.classify(it) },
        action = {
            val workflow = session.workflow() ?: return@SyncService
            workflow.fetch() // Netzwerkphase ohne Sperre
            session.lock.withLock { workflow.reconcile() }
        },
        onSuccess = { millis -> settings.update { it.copy(lastSyncMillis = millis) } },
    )

    init {
        appScope.launch { settings.settings.collect { settingsSnapshot = it } }
        sync.setLastSuccess(settingsSnapshot.lastSyncMillis)
    }

    fun requestSync(reason: SyncReason) { if (session.isInstalled()) sync.request(reason) }

}
