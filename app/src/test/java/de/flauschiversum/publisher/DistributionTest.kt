package de.flauschiversum.publisher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistributionTest {
    private val root = File("..").canonicalFile

    // ---- 2.3 Versionierung
    @Test fun versionscodeSteigtMitJederVersion() {
        assertTrue(AppVersion.codeOf("1.2.1") > AppVersion.codeOf("1.2.0"))
        assertTrue(AppVersion.codeOf("1.3.0") > AppVersion.codeOf("1.2.99"))
        assertTrue(AppVersion.codeOf("2.0.0") > AppVersion.codeOf("1.99.99"))
        assertEquals(10200, AppVersion.codeOf("1.2.0")); assertEquals(10200, AppVersion.codeOf("v1.2.0"))
        assertEquals(1, AppVersion.codeOf("dev"))
    }
    @Test fun gradleBerechnungStimmtMitDerAppUeberein() {
        // BuildConfig stammt aus build.gradle.kts; bei einer Version nach Schema muss der Code zur App-Formel passen
        if (Regex("""^\d+\.\d+\.\d+""").containsMatchIn(BuildConfig.VERSION_NAME)) assertEquals(AppVersion.codeOf(BuildConfig.VERSION_NAME), BuildConfig.VERSION_CODE)
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("a.toInt() * 10000 + b.toInt() * 100 + c.toInt()"))
    }

    // ---- 2.1 / 2.2 Workflows
    @Test fun ciWorkflowFuehrtLintTestsUndBuildAus() {
        val ci = File(root, ".github/workflows/ci.yml").readText()
        for (step in listOf("lintDebug", "testDebugUnitTest", "assembleDebug")) assertTrue(step, ci.contains("./gradlew $step"))
        assertTrue(ci.contains("push:") && ci.contains("pull_request:")); assertTrue(ci.contains("upload-artifact"))
    }
    @Test fun releaseWorkflowBrauchtTagUndGeheimnisseUndBrichtOhneSieAb() {
        val rel = File(root, ".github/workflows/release.yml").readText()
        assertTrue(rel.contains("v[0-9]+.[0-9]+.[0-9]+")); assertTrue(rel.contains("secrets.SIGNING_KEYSTORE_BASE64"))
        assertTrue(rel.indexOf("exit 1") in 0 until rel.indexOf("assembleRelease")) // Abbruch vor dem Bau
        assertTrue(rel.contains("-PversionName=")); assertTrue(rel.contains("gh release create"))
        // Signaturmaterial steht nie im Repository
        val tracked = root.walkTopDown().filter { it.isFile && !it.path.contains("/build/") && !it.path.contains("/.gradle/") && !it.path.contains("/.git/") }
        assertTrue(tracked.none { it.extension in setOf("jks", "keystore", "p12") })
    }

    // ---- 2.3 Lizenzen decken alle Bibliotheken ab
    @Test fun lizenzlisteDecktAlleVerwendetenBibliothekenAb() {
        val toml = File(root, "gradle/libs.versions.toml").readText()
        val gradle = File(root, "app/build.gradle.kts").readText()
        val used = Regex("""^\s*implementation\(libs\.([a-zA-Z0-9.]+)\)""", RegexOption.MULTILINE).findAll(gradle).map { it.groupValues[1] }.toSet() +
            Regex("""implementation\(libs\.([a-zA-Z0-9.]+)\)\s*\{""").findAll(gradle).map { it.groupValues[1] }.toSet()
        val groups = used.mapNotNull { alias ->
            val key = alias.replace('.', '-')
            Regex("""^$key\s*=\s*\{[^}]*group\s*=\s*"([^"]+)"""", RegexOption.MULTILINE).find(toml)?.groupValues?.get(1)
        }.toSet()
        assertTrue(groups.size > 10)
        val covered = Licenses.entries.flatMap { it.groups }
        val missing = groups.filter { g -> covered.none { g == it || g.startsWith("$it.") } }
        assertTrue("Ohne Lizenzeintrag: $missing", missing.isEmpty())
    }
}
