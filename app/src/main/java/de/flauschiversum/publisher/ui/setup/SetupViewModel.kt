package de.flauschiversum.publisher.ui.setup

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.flauschiversum.publisher.app.AppContainer
import de.flauschiversum.publisher.git.AddressParse
import de.flauschiversum.publisher.git.GitFailure
import de.flauschiversum.publisher.git.RepositoryAddress
import de.flauschiversum.publisher.git.RepositoryAddresses
import de.flauschiversum.publisher.git.Refs
import de.flauschiversum.publisher.git.SyncReason
import de.flauschiversum.publisher.setup.CloneProgress
import de.flauschiversum.publisher.setup.InstallOutcome
import de.flauschiversum.publisher.setup.KeyInstructions
import de.flauschiversum.publisher.setup.RepositoryInstaller
import de.flauschiversum.publisher.setup.SetupFacts
import de.flauschiversum.publisher.setup.SetupFlow
import de.flauschiversum.publisher.setup.SetupStep
import de.flauschiversum.publisher.ssh.AccessResult
import de.flauschiversum.publisher.ssh.HostKeyInfo
import de.flauschiversum.publisher.ssh.SshKeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface CheckUi {
    data object Idle : CheckUi
    data object Running : CheckUi
    /** Erster Kontakt: Der Nutzer soll den Fingerabdruck bestätigen. */
    data class NeedsHostTrust(val key: HostKeyInfo) : CheckUi
    data class Done(val result: AccessResult) : CheckUi
}

sealed interface CloneUi {
    data object Idle : CloneUi
    data object ConfirmMobile : CloneUi
    data class Running(val task: String, val done: Int, val total: Int) : CloneUi
    data class Problem(val message: String, val branches: List<String> = emptyList(), val canPickBranch: Boolean = false) : CloneUi
}

data class SetupUi(
    val facts: SetupFacts,
    val shown: SetupStep,
    val finished: Boolean,
    val addressInput: String,
    val addressMessage: String?,
    val httpsSuggestion: String?,
    val address: RepositoryAddress?,
    val key: SshKeyPair?,
    val plan: KeyInstructions.Plan?,
    val check: CheckUi,
    val clone: CloneUi,
    val targetBranch: String,
)

/** Zustand und Aktionen des Einrichtungsablaufs (Spec `einrichtung`). */
class SetupViewModel(private val c: AppContainer) : ViewModel() {
    private val deviceName = Build.MODEL ?: "Handy"
    private var shownOverride: SetupStep? = null
    private var addressInput = c.settingsSnapshot.repositoryAddress
    private var addressMessage: String? = null
    private var httpsSuggestion: String? = null
    @Volatile private var marks = kotlinx.coroutines.runBlocking { c.setupState.current() }
    private val check = MutableStateFlow<CheckUi>(CheckUi.Idle)
    private val clone = MutableStateFlow<CloneUi>(CloneUi.Idle)
    private var progress: CloneProgress? = null
    private val _ui = MutableStateFlow(compute())
    val ui: StateFlow<SetupUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            cleanupInterruptedClone()
            c.settings.settings.collect { refresh() }
        }
        viewModelScope.launch { c.setupState.marks.collect { marks = it; refresh() } }
        viewModelScope.launch { check.collect { refresh() } }
        viewModelScope.launch { clone.collect { refresh() } }
    }

    private fun facts(): SetupFacts {
        val s = c.settingsSnapshot
        return SetupFacts(
            confirmedAddress = marks.confirmedAddress, currentAddress = s.repositoryAddress,
            keyFingerprint = c.keyStore.load()?.fingerprint, registeredFor = marks.registeredFor,
            accessVerifiedFor = marks.accessVerifiedFor, cloneVerifiedFor = marks.cloneVerifiedFor,
            targetBranch = s.targetBranch, cloneDirExists = c.session.dir.exists(),
        )
    }

    private fun compute(): SetupUi {
        val f = facts()
        val current = SetupFlow.currentStep(f)
        val shown = shownOverride?.takeIf { SetupFlow.canOpen(it, f) } ?: current ?: SetupStep.CLONE
        val key = c.keyStore.load()
        val address = (RepositoryAddresses.parse(c.settingsSnapshot.repositoryAddress) as? AddressParse.Valid)?.address
        val plan = if (key != null && address != null) KeyInstructions.planFor(address, key.publicLine, deviceName) else null
        return SetupUi(f, shown, current == null, addressInput, addressMessage, httpsSuggestion, address, key, plan, check.value, clone.value, c.settingsSnapshot.targetBranch)
    }

    private fun refresh() { _ui.value = compute() }

    private fun cleanupInterruptedClone() {
        val f = facts()
        if (SetupFlow.needsCloneCleanup(f) && clone.value !is CloneUi.Running) {
            c.session.close(); c.session.dir.deleteRecursively()
        }
    }

    fun goTo(step: SetupStep) { shownOverride = step; refresh() }

    // ---- Schritt 1
    fun onAddressChanged(text: String) { addressInput = text; addressMessage = null; httpsSuggestion = null; refresh() }

    fun useSuggestion() { httpsSuggestion?.let { addressInput = it; httpsSuggestion = null; addressMessage = null; refresh() } }

    fun submitAddress() {
        when (val r = RepositoryAddresses.parse(addressInput)) {
            is AddressParse.Invalid -> { addressMessage = r.reason; httpsSuggestion = null }
            is AddressParse.Https -> {
                addressMessage = "Der Zugang erfolgt per SSH. Verwende diese Adresse: ${r.suggestion}"
                httpsSuggestion = r.suggestion
            }
            is AddressParse.Valid -> {
                addressMessage = null; httpsSuggestion = null
                viewModelScope.launch {
                    val changed = r.address.text != c.settingsSnapshot.repositoryAddress
                    c.settings.update { it.copy(repositoryAddress = r.address.text) }
                    c.setupState.update { it.copy(confirmedAddress = r.address.text) }
                    if (changed) { withContext(Dispatchers.IO) { c.session.close(); c.session.dir.deleteRecursively() } }
                    shownOverride = null
                    check.value = CheckUi.Idle
                    refresh()
                }
                return
            }
        }
        refresh()
    }

    // ---- Schritt 2
    fun generateKey() {
        viewModelScope.launch(Dispatchers.IO) {
            c.keyStore.generate("flauschiversum-publisher")
            shownOverride = null
            check.value = CheckUi.Idle
            refresh()
        }
    }

    // ---- Schritt 3
    fun confirmRegistered() {
        val id = facts().keyAndAddressId() ?: return
        viewModelScope.launch { c.setupState.update { it.copy(registeredFor = id) }; shownOverride = null; refresh() }
    }

    // ---- Schritt 4
    fun runCheck() {
        val address = _ui.value.address ?: return
        check.value = CheckUi.Running
        viewModelScope.launch(Dispatchers.IO) {
            var result = c.accessChecker.check(address)
            if (result is AccessResult.Failed && result.failure is GitFailure.HostKeyUnknown) {
                check.value = CheckUi.NeedsHostTrust((result.failure as GitFailure.HostKeyUnknown).key)
                return@launch
            }
            val a = c.session.address()
            if (result is AccessResult.Ok) c.diag.record(de.flauschiversum.publisher.diag.DiagOp.ACCESS, de.flauschiversum.publisher.diag.DiagResult.OK, host = a?.host, port = a?.port)
            else if (result is AccessResult.Failed) c.diagFailure(de.flauschiversum.publisher.diag.DiagOp.ACCESS, result.failure)
            // Das Ergebnis bleibt sichtbar, bis der Nutzer „Weiter“ wählt (der nächste Schritt ist dann schon freigeschaltet).
            shownOverride = SetupStep.CHECK
            check.value = CheckUi.Done(result)
            if (result is AccessResult.Ok) {
                val id = facts().keyAndAddressId()
                if (id != null) c.setupState.update { it.copy(accessVerifiedFor = id) }
            }
        }
    }

    fun trustHostKey(key: HostKeyInfo) {
        c.accessChecker.trust(key)
        runCheck()
    }

    fun rejectHostKey() { check.value = CheckUi.Idle }

    fun continueAfterCheck() { shownOverride = null; refresh() }

    // ---- Schritt 5
    fun startClone(confirmedMobile: Boolean = false) {
        if (clone.value is CloneUi.Running) return
        if (c.network.isMetered() && !confirmedMobile) { clone.value = CloneUi.ConfirmMobile; return }
        val address = _ui.value.address ?: return
        val refs = Refs(c.settingsSnapshot.targetBranch)
        val progress = CloneProgress { task, done, total -> clone.value = CloneUi.Running(task, done, total) }
        this.progress = progress
        clone.value = CloneUi.Running("Verbinde …", 0, 0)
        viewModelScope.launch(Dispatchers.IO) {
          try {
            c.session.close()
            lateinit var factory: de.flauschiversum.publisher.ssh.AppSshSessionFactory
            val installer = RepositoryInstaller(
                dir = c.session.dir, address = address, refs = refs,
                opsFactory = { store ->
                    val key = c.keyStore.load()!!
                    factory = de.flauschiversum.publisher.ssh.AppSshSessionFactory(key, c.hostKeys)
                    de.flauschiversum.publisher.git.RemoteOps(store, de.flauschiversum.publisher.git.SshRemote(address, factory))
                },
                network = c.network,
                freeSpace = c.freeSpace,
                presented = { factory.lastPresented to factory.lastCheck },
            )
            when (val r = installer.install(progress)) {
                InstallOutcome.Success -> {
                    c.session.address().let { a -> c.diag.record(de.flauschiversum.publisher.diag.DiagOp.CLONE, de.flauschiversum.publisher.diag.DiagResult.OK, host = a?.host, port = a?.port) }
                    c.setupState.update { it.copy(cloneVerifiedFor = facts().cloneId()) }
                    clone.value = CloneUi.Idle
                    c.requestSync(SyncReason.START)
                }
                is InstallOutcome.NotEnoughSpace -> clone.value = CloneUi.Problem(
                    "Zu wenig freier Speicher: ${r.freeBytes / 1_000_000} MB frei, ${r.neededBytes / 1_000_000} MB nötig. Bitte Platz schaffen und erneut versuchen.",
                )
                InstallOutcome.Cancelled -> clone.value = CloneUi.Idle
                is InstallOutcome.BranchMissing -> clone.value = CloneUi.Problem(
                    "Branch „${r.branch}“ nicht gefunden." + if (r.available.isEmpty()) "" else " Vorhanden: ${r.available.joinToString()}.",
                    branches = r.available, canPickBranch = true,
                )
                is InstallOutcome.WrongRepository -> clone.value = CloneUi.Problem(r.reason)
                is InstallOutcome.Failed -> { c.diagFailure(de.flauschiversum.publisher.diag.DiagOp.CLONE, r.failure); clone.value = CloneUi.Problem(r.failure.message) }
            }
          } catch (e: Throwable) {
            clone.value = CloneUi.Problem("Unerwarteter Fehler beim Holen des Repositorys: ${e.message ?: e.javaClass.simpleName}")
          }
        }
    }

    fun cancelClone() { progress?.cancelled = true }

    fun dismissCloneProblem() { clone.value = CloneUi.Idle }

    fun pickBranch(name: String) {
        viewModelScope.launch {
            c.settings.update { it.copy(targetBranch = name) }
            clone.value = CloneUi.Idle
            refresh()
        }
    }

    fun cancelMobileConfirm() { clone.value = CloneUi.Idle }
}
