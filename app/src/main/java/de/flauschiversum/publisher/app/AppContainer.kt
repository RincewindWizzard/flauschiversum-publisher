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

    /** Austauschbar für Tests. */
    var lock = de.flauschiversum.publisher.lock.AppLock({ settingsSnapshot })
    var authenticator: de.flauschiversum.publisher.lock.Authenticator = de.flauschiversum.publisher.lock.BiometricAuthenticator()

    /** Diagnoseprotokoll (höchstens 1 MB) und Absturzhinweis. */
    val diag = de.flauschiversum.publisher.diag.DiagnosticLog(File(filesDir, "diagnose/protokoll.log"))
    val crash = de.flauschiversum.publisher.diag.CrashHandler(diag, File(filesDir, "diagnose/absturz.marke"), BuildConfigInfo.version)

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
        onFailure = { f -> diagFailure(de.flauschiversum.publisher.diag.DiagOp.SYNC, f) },
    )

    val spell = de.flauschiversum.publisher.editor.SpellService(context, appScope, File(filesDir, "dictionary/personal.txt"))
    private var indexFor: de.flauschiversum.publisher.git.GitStore? = null
    private var index: de.flauschiversum.publisher.overview.PostIndex? = null
    /** Index der Beiträge (mit Zwischenspeicher auf Platte); neu, wenn das Repository neu angelegt wurde. */
    @Synchronized fun postIndex(): de.flauschiversum.publisher.overview.PostIndex? {
        val store = session.store() ?: return null
        if (indexFor !== store) { indexFor = store; index = de.flauschiversum.publisher.overview.PostIndex(store, File(filesDir, "index/posts.tsv")) }
        return index
    }
    /** Vorschaubilder der Beiträge aus dem lokalen Klon. */
    val thumbnails = de.flauschiversum.publisher.overview.Thumbnails { rev, path -> runCatching { session.store()?.readBytes(rev, path) }.getOrNull() }

    /** Benachrichtigungen der App (Uploads und Meldungen des Servers). */
    val notifier = de.flauschiversum.publisher.upload.UploadNotifications(context)

    val uploads: de.flauschiversum.publisher.upload.UploadService = run {
        val store = de.flauschiversum.publisher.upload.JobStore(File(filesDir, "outbox"))
        val runner = de.flauschiversum.publisher.upload.UploadRunner(store, session, { photoPlacement }, { settingsSnapshot }, network, onDraftChanged = { draftChanges.value++ }, diag = diag)
        de.flauschiversum.publisher.upload.UploadService(
            store, runner, de.flauschiversum.publisher.upload.WorkManagerScheduler(context), { settingsSnapshot }, network,
            notifier, appScope,
        )
    }

    /** Soll die Ausgangsbox geöffnet werden (Tipp auf eine Benachrichtigung)? */
    val openOutbox = kotlinx.coroutines.flow.MutableStateFlow(false)

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

    /** Vermerkt einen Fehler im Diagnoseprotokoll (Fehlerart, Server, Port — nie die Meldung). */
    fun diagFailure(op: de.flauschiversum.publisher.diag.DiagOp, f: de.flauschiversum.publisher.git.GitFailure, retry: Boolean = false) {
        val a = session.address()
        diag.recordFailure(op, f, a?.host, a?.port, retry)
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
