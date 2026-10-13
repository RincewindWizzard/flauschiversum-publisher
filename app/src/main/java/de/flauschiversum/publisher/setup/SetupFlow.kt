package de.flauschiversum.publisher.setup

/** Die fünf Schritte der Einrichtung (Spec `einrichtung`). */
enum class SetupStep(val number: Int, val title: String) {
    REPOSITORY(1, "Repository"),
    KEY(2, "Schlüssel"),
    REGISTER(3, "Schlüssel eintragen"),
    CHECK(4, "Verbindung prüfen"),
    CLONE(5, "Repository holen");

    companion object { const val COUNT = 5 }
}

/**
 * Was bereits erledigt ist. Die Merkmale hängen an der Adresse und am Schlüssel: Ändert sich eines davon, sind
 * die folgenden Schritte automatisch wieder offen.
 */
data class SetupFacts(
    /** Adresse, für die Schritt 1 bestätigt wurde. */
    val confirmedAddress: String?,
    val currentAddress: String,
    /** Fingerabdruck des vorhandenen Schlüssels (null: keiner). */
    val keyFingerprint: String?,
    /** Vom Nutzer als „eingetragen“ bestätigt für diesen Schlüssel und diese Adresse. */
    val registeredFor: String?,
    /** Verbindung erfolgreich geprüft für diesen Schlüssel und diese Adresse. */
    val accessVerifiedFor: String?,
    /** Klon vollständig und geprüft für diese Adresse und diesen Zielbranch. */
    val cloneVerifiedFor: String?,
    val targetBranch: String,
    /** Es liegt ein (möglicherweise unvollständiger) Klon auf der Platte. */
    val cloneDirExists: Boolean,
) {
    private val keyAndAddress: String? get() = keyFingerprint?.let { "$it@$currentAddress" }
    val repositoryDone: Boolean get() = confirmedAddress != null && confirmedAddress == currentAddress
    val keyDone: Boolean get() = keyFingerprint != null
    val registerDone: Boolean get() = keyAndAddress != null && registeredFor == keyAndAddress
    val checkDone: Boolean get() = keyAndAddress != null && accessVerifiedFor == keyAndAddress
    val cloneDone: Boolean get() = cloneVerifiedFor == "$currentAddress#$targetBranch" && cloneDirExists

    fun keyAndAddressId(): String? = keyAndAddress
    fun cloneId(): String = "$currentAddress#$targetBranch"
}

object SetupFlow {
    /** Der erste noch nicht erfüllte Schritt oder null, wenn die Einrichtung abgeschlossen ist. */
    fun currentStep(f: SetupFacts): SetupStep? = when {
        !f.repositoryDone -> SetupStep.REPOSITORY
        !f.keyDone -> SetupStep.KEY
        !f.registerDone -> SetupStep.REGISTER
        !f.checkDone -> SetupStep.CHECK
        !f.cloneDone -> SetupStep.CLONE
        else -> null
    }

    /** Ein Schritt darf nur geöffnet werden, wenn alle davor erfüllt sind (erledigte lassen sich wiederholen). */
    fun canOpen(step: SetupStep, f: SetupFacts): Boolean {
        val current = currentStep(f) ?: return true
        return step.number <= current.number
    }

    /** Ein unterbrochener Klon (Verzeichnis da, aber nicht geprüft) muss vor einem neuen Versuch entfernt werden. */
    fun needsCloneCleanup(f: SetupFacts): Boolean = f.cloneDirExists && !f.cloneDone
}
