package de.flauschiversum.publisher.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.flauschiversum.publisher.git.RepositoryAddresses
import de.flauschiversum.publisher.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Einstellungen der App mit ihren Vorgaben. */
data class AppSettings(
    val repositoryAddress: String = RepositoryAddresses.DEFAULT,
    val targetBranch: String = DEFAULT_TARGET_BRANCH,
    val commitName: String = DEFAULT_COMMIT_NAME,
    val commitEmail: String = DEFAULT_COMMIT_EMAIL,
    val defaultAuthor: String = DEFAULT_AUTHOR,
    val rightsText: String = DEFAULT_RIGHTS_TEXT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val editorFontScale: Float = 1.0f,
    val uploadWifiOnly: Boolean = false,
    /** Der Hinweis zur Vorschau als Annäherung wurde schon gezeigt. */
    val previewHintShown: Boolean = false,
    /** Optionale App-Sperre mit Geräte-Authentifizierung. */
    val lockEnabled: Boolean = false,
    val lockDelay: String = "ONE_MINUTE",
    /** Zeitpunkt der letzten erfolgreichen Aktualisierung (Millisekunden seit 1970), 0 = nie. */
    val lastSyncMillis: Long = 0L,
) {
    companion object {
        const val DEFAULT_TARGET_BRANCH = "main"
        const val DEFAULT_COMMIT_NAME = "Flauschiversum Publisher"
        const val DEFAULT_COMMIT_EMAIL = "publisher@flauschiversum.de"
        const val DEFAULT_AUTHOR = "Ermeline"
        const val DEFAULT_RIGHTS_TEXT = "Flauschiversum / Ermeline Wollknoll"
        const val RIGHTS_LICENSE_URL = "https://flauschiversum.de/impressum/"
    }
}

/** Speichert [AppSettings] dauerhaft (DataStore). */
class SettingsRepository(private val store: DataStore<Preferences>) {
    private object Keys {
        val repositoryAddress = stringPreferencesKey("repository_address")
        val targetBranch = stringPreferencesKey("target_branch")
        val commitName = stringPreferencesKey("commit_name")
        val commitEmail = stringPreferencesKey("commit_email")
        val defaultAuthor = stringPreferencesKey("default_author")
        val rightsText = stringPreferencesKey("rights_text")
        val themeMode = stringPreferencesKey("theme_mode")
        val editorFontScale = floatPreferencesKey("editor_font_scale")
        val uploadWifiOnly = booleanPreferencesKey("upload_wifi_only")
        val previewHintShown = booleanPreferencesKey("preview_hint_shown")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockDelay = stringPreferencesKey("lock_delay")
        val lastSyncMillis = longPreferencesKey("last_sync_millis")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        val d = AppSettings()
        AppSettings(
            repositoryAddress = p[Keys.repositoryAddress] ?: d.repositoryAddress,
            targetBranch = p[Keys.targetBranch] ?: d.targetBranch,
            commitName = p[Keys.commitName] ?: d.commitName,
            commitEmail = p[Keys.commitEmail] ?: d.commitEmail,
            defaultAuthor = p[Keys.defaultAuthor] ?: d.defaultAuthor,
            rightsText = p[Keys.rightsText] ?: d.rightsText,
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            editorFontScale = p[Keys.editorFontScale] ?: d.editorFontScale,
            uploadWifiOnly = p[Keys.uploadWifiOnly] ?: d.uploadWifiOnly,
            previewHintShown = p[Keys.previewHintShown] ?: d.previewHintShown,
            lockEnabled = p[Keys.lockEnabled] ?: d.lockEnabled,
            lockDelay = p[Keys.lockDelay] ?: d.lockDelay,
            lastSyncMillis = p[Keys.lastSyncMillis] ?: d.lastSyncMillis,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        store.edit { p ->
            p[Keys.repositoryAddress] = next.repositoryAddress
            p[Keys.targetBranch] = next.targetBranch
            p[Keys.commitName] = next.commitName
            p[Keys.commitEmail] = next.commitEmail
            p[Keys.defaultAuthor] = next.defaultAuthor
            p[Keys.rightsText] = next.rightsText
            p[Keys.themeMode] = next.themeMode.name
            p[Keys.editorFontScale] = next.editorFontScale
            p[Keys.uploadWifiOnly] = next.uploadWifiOnly
            p[Keys.previewHintShown] = next.previewHintShown
            p[Keys.lockEnabled] = next.lockEnabled
            p[Keys.lockDelay] = next.lockDelay
            p[Keys.lastSyncMillis] = next.lastSyncMillis
        }
    }
}
