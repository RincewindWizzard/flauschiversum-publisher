package de.flauschiversum.publisher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.data.AppSettings
import de.flauschiversum.publisher.ssh.StoredHostKey
import de.flauschiversum.publisher.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUi(
    val settings: AppSettings = AppSettings(),
    val keyFingerprint: String? = null,
    val publicKey: String? = null,
    val hostKeys: List<StoredHostKey> = emptyList(),
    val pinned: Map<String, Set<String>> = emptyMap(),
    val dictionary: List<String> = emptyList(),
)

class SettingsViewModel(val c: AppContainer) : ViewModel() {
    private val extra = MutableStateFlow(load())
    private val _ui = MutableStateFlow(SettingsUi(c.settingsSnapshot).merge(extra.value))
    val ui: StateFlow<SettingsUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { c.settings.settings.collect { s -> _ui.update { it.copy(settings = s) } } }
    }

    private fun load(): SettingsUi {
        val key = c.keyStore.load()
        return SettingsUi(keyFingerprint = key?.fingerprint, publicKey = key?.publicLine, dictionary = c.spell.personal.all(), hostKeys = c.hostKeys.list(), pinned = de.flauschiversum.publisher.ssh.HostKeyStore.PINNED_FINGERPRINTS)
    }

    private fun SettingsUi.merge(o: SettingsUi) = copy(keyFingerprint = o.keyFingerprint, publicKey = o.publicKey, hostKeys = o.hostKeys, pinned = o.pinned)

    private fun reloadExtra() { _ui.update { it.merge(load()) } }

    fun update(transform: (AppSettings) -> AppSettings) { viewModelScope.launch { c.settings.update(transform) } }

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    val container: AppContainer get() = c
    fun addDictionaryWord(word: String) { c.spell.personal.add(word.trim()); _ui.update { it.copy(dictionary = c.spell.personal.all()) } }
    fun removeDictionaryWord(word: String) { c.spell.personal.remove(word); _ui.update { it.copy(dictionary = c.spell.personal.all()) } }

    fun removeHostKey(hostId: String) {
        val (host, port) = if (hostId.startsWith("[")) hostId.substring(1, hostId.indexOf(']')) to hostId.substringAfterLast(':').toInt() else hostId to 22
        c.hostKeys.remove(host, port)
        reloadExtra()
    }

    /** Löscht den Klon und die Bestätigung, damit die Einrichtung ab Schritt 5 neu beginnt. */
    fun reinstallRepository(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            c.session.locked { c.session.close(); c.session.dir.deleteRecursively() }
            c.setupState.update { it.copy(cloneVerifiedFor = null) }
            onDone()
        }
    }

    /** Beginnt bei „Eintragen“ von vorn: der bisherige Schlüssel bleibt, gilt aber als nicht eingetragen. */
    fun forgetRegistration(onDone: () -> Unit) {
        viewModelScope.launch { c.setupState.update { it.copy(registeredFor = null, accessVerifiedFor = null) }; onDone() }
    }

    fun changeAddress(onDone: () -> Unit) {
        viewModelScope.launch { c.setupState.update { it.copy(confirmedAddress = null) }; onDone() }
    }
}
