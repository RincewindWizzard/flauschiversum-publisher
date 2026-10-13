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
    val network = de.flauschiversum.publisher.git.SwitchableNetwork(AndroidNetworkState(context))
    /** Freier Speicher für die Prüfung vor dem Klonen (austauschbar für Tests). */
    var freeSpace: () -> Long = { filesDir.usableSpace }
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

    val incoming = de.flauschiversum.publisher.photos.IncomingPhotos(context)
    val imageProcessor: de.flauschiversum.publisher.images.ImageProcessor = de.flauschiversum.publisher.images.AndroidImageProcessor(File(context.cacheDir, "exif"))
    /** Legt Fotos in Beiträgen ab; die Rechteangaben kommen aus den Einstellungen. */
    var photoPlacement = de.flauschiversum.publisher.photos.PhotoPlacement(imageProcessor) {
        de.flauschiversum.publisher.images.Rights(settingsSnapshot.rightsText, AppSettings.RIGHTS_LICENSE_URL)
    }

    init {
        appScope.launch { settings.settings.collect { settingsSnapshot = it } }
        sync.setLastSuccess(settingsSnapshot.lastSyncMillis)
    }

    fun requestSync(reason: SyncReason) { if (session.isInstalled()) sync.request(reason) }

    /** Zähler, der steigt, wenn sich Entwürfe lokal geändert haben (zum Neuladen von Listen). */
    val draftChanges = kotlinx.coroutines.flow.MutableStateFlow(0L)

    /** Schiebt `draft` nach einer Änderung im Hintergrund zum Server; Fehler bleiben als „Noch nicht hochgeladen“ sichtbar. */
    fun uploadDraftInBackground() {
        draftChanges.value++
        appScope.launch(Dispatchers.IO) {
            runCatching { session.workflow()?.pushDraft() }
            draftChanges.value++
        }
    }

}
