package de.flauschiversum.publisher.lock

import de.flauschiversum.publisher.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wann sich die App nach dem Verlassen wieder sperrt. */
enum class LockDelay(val label: String, val millis: Long) {
    IMMEDIATE("Sofort", 0L), ONE_MINUTE("Nach 1 Minute", 60_000L), FIVE_MINUTES("Nach 5 Minuten", 300_000L);
    companion object { fun parse(name: String?): LockDelay = entries.firstOrNull { it.name == name } ?: ONE_MINUTE }
}

/**
 * Zustand der optionalen App-Sperre. Beim Prozessstart ist die App gesperrt, wenn die Sperre an ist; nach dem Verlassen sperrt
 * sie sich, sobald die gewählte Zeit abgelaufen ist. Hintergrundaufträge sind davon unabhängig (sie nutzen die Oberfläche nicht).
 */
class AppLock(private val settings: () -> AppSettings, private val clock: () -> Long = System::currentTimeMillis) {
    private val _locked = MutableStateFlow(settings().lockEnabled)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var leftAt: Long? = null

    /** Die Oberfläche ist nicht mehr sichtbar (Verlassen, nicht Drehen). */
    fun onLeave() { leftAt = clock() }

    /** Die Oberfläche kommt zurück. */
    fun onReturn() {
        val s = settings()
        val left = leftAt; leftAt = null
        if (!s.lockEnabled) { _locked.value = false; return }
        if (left != null && clock() - left >= LockDelay.parse(s.lockDelay).millis) _locked.value = true
    }

    fun unlock() { _locked.value = false }
    /** Nach dem Einschalten in den Einstellungen ist die Sperre bestätigt, die App bleibt offen. */
    fun enabledNow() { _locked.value = false }
    fun disabledNow() { _locked.value = false }
}
