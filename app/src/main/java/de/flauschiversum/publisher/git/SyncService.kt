package de.flauschiversum.publisher.git

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wie weit die Hintergrundaktualisierung ist. */
data class SyncState(
    val running: Boolean = false,
    /** Zeitpunkt der letzten erfolgreichen Aktualisierung (Millisekunden), 0 = noch nie. */
    val lastSuccessMillis: Long = 0L,
    /** Fehler der letzten Aktualisierung (still: nur zur Anzeige „zuletzt aktualisiert“), null = ok. */
    val lastFailure: GitFailure? = null,
    /** Der Zugriff wurde mehrmals hintereinander verweigert: Hinweis „Zugriff verweigert“. */
    val accessProblem: Boolean = false,
    /** Zähler, der bei jedem Ende einer Aktualisierung steigt (zum Neuladen von Listen). */
    val generation: Long = 0L,
)

enum class SyncReason { START, FOREGROUND, OPEN_POST, MANUAL }

/**
 * Aktualisiert das Repository im Hintergrund, ohne die Bedienung zu blockieren: höchstens ein Lauf gleichzeitig,
 * automatische Läufe mit Mindestabstand, Fehler werden still gemerkt. [action] führt Holen und Abgleichen aus
 * und wirft bei Fehlern.
 */
class SyncService(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minIntervalMillis: Long = 60_000L,
    private val authFailuresForHint: Int = 2,
    private val classify: (Throwable) -> GitFailure,
    private val action: suspend () -> Unit,
    private val onSuccess: suspend (Long) -> Unit = {},
    private val onFailure: (GitFailure) -> Unit = {},
) {
    private val running = AtomicBoolean(false)
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()
    private var lastStart = 0L
    private var authFailures = 0

    fun setLastSuccess(millis: Long) = _state.update { it.copy(lastSuccessMillis = millis) }

    /** Fordert eine Aktualisierung an; kehrt sofort zurück. Gibt zurück, ob ein Lauf gestartet wurde. */
    fun request(reason: SyncReason): Boolean {
        val manual = reason == SyncReason.MANUAL
        val now = clock()
        if (!manual && lastStart != 0L && now - lastStart < minIntervalMillis) return false
        if (!running.compareAndSet(false, true)) return false
        lastStart = now
        _state.update { it.copy(running = true) }
        scope.launch(Dispatchers.IO) {
            try {
                action()
                authFailures = 0
                val done = clock()
                _state.update { it.copy(running = false, lastSuccessMillis = done, lastFailure = null, accessProblem = false, generation = it.generation + 1) }
                onSuccess(done)
            } catch (e: kotlinx.coroutines.CancellationException) {
                running.set(false)
                _state.update { it.copy(running = false) }
                throw e
            } catch (e: Throwable) {
                val failure = classify(e)
                if (failure is GitFailure.KeyRejected) authFailures++
                runCatching { onFailure(failure) }
                _state.update {
                    it.copy(
                        running = false, lastFailure = failure, generation = it.generation + 1,
                        accessProblem = it.accessProblem || authFailures >= authFailuresForHint,
                    )
                }
            } finally {
                running.set(false)
            }
        }
        return true
    }
}
