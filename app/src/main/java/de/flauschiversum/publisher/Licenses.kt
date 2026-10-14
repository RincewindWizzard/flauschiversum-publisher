package de.flauschiversum.publisher

/** Eine verwendete Bibliothek oder Datei mit Lizenz; [groups] sind die Maven-Gruppen, die sie abdeckt. */
data class LicenseEntry(val name: String, val license: String, val groups: List<String> = emptyList(), val note: String? = null)

object Licenses {
    val entries: List<LicenseEntry> = listOf(
        LicenseEntry("JGit", "Eclipse Distribution License 1.0 (BSD-3)", listOf("org.eclipse.jgit")),
        LicenseEntry("JSch (Fork von mwiede)", "BSD-3", listOf("com.github.mwiede")),
        LicenseEntry("Bouncy Castle", "MIT", listOf("org.bouncycastle")),
        LicenseEntry("SLF4J", "MIT", listOf("org.slf4j")),
        LicenseEntry("Apache Lucene (Hunspell)", "Apache License 2.0", listOf("org.apache.lucene")),
        LicenseEntry("commonmark-java", "BSD-2", listOf("org.commonmark")),
        LicenseEntry("SnakeYAML Engine", "Apache License 2.0", listOf("org.snakeyaml")),
        LicenseEntry("Kotlin und kotlinx.coroutines", "Apache License 2.0", listOf("org.jetbrains.kotlinx", "org.jetbrains.kotlin")),
        LicenseEntry("AndroidX, Jetpack Compose, WorkManager, DataStore, WebKit", "Apache License 2.0", listOf("androidx.core", "androidx.activity", "androidx.lifecycle", "androidx.navigation", "androidx.datastore", "androidx.work", "androidx.biometric", "androidx.exifinterface", "androidx.compose", "androidx.compose.material3", "androidx.compose.ui", "androidx.webkit", "androidx.fragment")),
        LicenseEntry("Desugar JDK Libs", "GPLv2 mit Classpath-Ausnahme", listOf("com.android.tools")),
        LicenseEntry("Wörterbuch de_DE (igerman98, Björn Jacke)", "GPL v2 oder v3", note = "Die Rechtschreibprüfung nutzt dieses Wörterbuch; Lizenztext liegt unter assets/dict/LICENSE-de_DE.txt"),
        LicenseEntry("Schrift Lobster", "SIL Open Font License 1.1"),
        LicenseEntry("Schrift Lora", "SIL Open Font License 1.1"),
        LicenseEntry("Maskottchen und Farbschema „Flauschi-Nebel“", "Flauschiversum / Ermeline Wollknoll", note = "Aus dem Blog übernommen"),
    )
}
