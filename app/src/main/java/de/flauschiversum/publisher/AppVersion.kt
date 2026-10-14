package de.flauschiversum.publisher

/** Versionsnummer aus dem Tag `vMAJOR.MINOR.PATCH`; entspricht der Berechnung in `app/build.gradle.kts`. */
object AppVersion {
    private val SEMVER = Regex("""^v?(\d+)\.(\d+)\.(\d+)""")

    /** `versionCode`: MAJOR · 10000 + MINOR · 100 + PATCH (steigt mit jeder neuen Version); ohne gültige Version 1. */
    fun codeOf(version: String): Int {
        val m = SEMVER.find(version) ?: return 1
        val (a, b, c) = m.destructured
        require(b.toInt() < 100 && c.toInt() < 100) { "MINOR und PATCH müssen unter 100 bleiben" }
        return a.toInt() * 10_000 + b.toInt() * 100 + c.toInt()
    }
}
