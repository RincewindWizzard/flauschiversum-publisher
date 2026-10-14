package de.flauschiversum.publisher

import de.flauschiversum.publisher.preview.PreviewRenderer
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Prüfungen zum Datenschutz (Spec `app-datenschutz`): Berechtigungen, keine Telemetrie, blockierte Netzzugriffe, Backup. */
class PrivacyTest {
    private val root = File("..").canonicalFile
    private fun xml(f: File) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder().parse(f).documentElement

    private fun permissions(manifest: Element): Set<String> =
        (0 until manifest.getElementsByTagName("uses-permission").length).map { (manifest.getElementsByTagName("uses-permission").item(it) as Element).getAttribute("android:name") }.toSet()

    // ---- 5.1 Berechtigungen
    @Test fun manifestEnthaeltNurDieErlaubtenBerechtigungen() {
        val declared = permissions(xml(File(root, "app/src/main/AndroidManifest.xml")))
        val allowed = setOf(
            "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        )
        assertEquals(allowed, declared)
    }

    @Test fun zusammengefuehrtesManifestHatKeineKritischenBerechtigungen() {
        val merged = root.resolve("app/build/intermediates/merged_manifests").walkTopDown().filter { it.name == "AndroidManifest.xml" && it.path.contains("debug") }.firstOrNull()
        org.junit.Assume.assumeTrue("zusammengeführtes Manifest noch nicht gebaut", merged != null)
        val declared = permissions(xml(merged!!))
        val forbidden = listOf("STORAGE", "LOCATION", "CONTACTS", "RECORD_AUDIO", "CAMERA", "PHONE", "SMS", "CALENDAR", "MEDIA")
        val bad = declared.filter { p -> forbidden.any { p.contains(it) } }
        assertTrue("Unerwünschte Berechtigungen: $bad", bad.isEmpty())
        // WorkManager ergänzt nur unkritische Berechtigungen
        assertTrue("Berechtigungen: $declared", declared.all { it in setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.POST_NOTIFICATIONS", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_DATA_SYNC", "android.permission.WAKE_LOCK", "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT") || it.endsWith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") })
    }

    @Test fun teilenFilterNimmtNurBilderAn() {
        val m = xml(File(root, "app/src/main/AndroidManifest.xml"))
        val data = m.getElementsByTagName("data"); val types = (0 until data.length).map { (data.item(it) as Element).getAttribute("android:mimeType") }
        assertEquals(listOf("image/*", "image/*"), types)
    }

    // ---- 5.2 Keine Analyse-SDKs
    @Test fun keineAnalyseWerbungOderAbsturzberichteInDenAbhaengigkeiten() {
        val toml = File(root, "gradle/libs.versions.toml").readText().lowercase()
        val gradle = File(root, "app/build.gradle.kts").readText().lowercase() + File(root, "build.gradle.kts").readText().lowercase()
        val forbidden = listOf("firebase", "crashlytics", "google.android.gms", "play-services", "analytics", "admob", "ads", "sentry", "appcenter", "bugsnag", "acra", "mixpanel", "amplitude", "segment", "datadog", "facebook", "flurry", "adjust", "appsflyer")
        val hits = forbidden.filter { f -> Regex("""(?<![a-z])${Regex.escape(f)}(?![a-z])""").containsMatchIn(toml) || Regex("""(?<![a-z])${Regex.escape(f)}(?![a-z])""").containsMatchIn(gradle) }
        assertTrue("Verbotene Bibliotheken: $hits", hits.isEmpty())
    }

    @Test fun vorschauLaedtKeineBilderAusDemNetz() {
        val md = "![Foto](https://beispiel.de/bild.png)\n\nText mit <img src=\"https://tracker.example/pixel.gif\"> mittendrin und <img src='//cdn.example/x.png'>.\n"
        val page = PreviewRenderer.render(PreviewRenderer.bodyHtml(md, "p", emptySet()).let { de.flauschiversum.publisher.preview.PreviewInput("p", "T", null, null, emptyList(), null, md) }, emptySet(), dark = false)
        assertFalse(Regex("""(src|href)\s*=\s*["']?(https?:)?//(?!appassets)""").containsMatchIn(page)) // kein Verweis ins Netz im Markup
        assertFalse(page.contains("tracker.example")); assertFalse(page.contains("cdn.example"))
        assertTrue(page, page.contains("Bild fehlt: https://beispiel.de/bild.png")) // Platzhalter statt Bild
        assertTrue(page, page.contains("Externes Bild blockiert"))
        // Auch ein <link>/<style>-Import aus dem Netz würde die CSP blockieren
        assertTrue(page.contains("default-src 'none'") && page.contains("img-src https://appassets.androidplatform.net"))
    }

    // ---- 5.2 Backup
    @Test fun backupRegelnSchliessenSchluesselKlonUndProtokollAus() {
        val domains = listOf("file", "database", "sharedpref", "external", "root")
        fun excluded(e: Element) = (0 until e.getElementsByTagName("exclude").length).map { (e.getElementsByTagName("exclude").item(it) as Element).getAttribute("domain") }.toSet()
        val old = xml(File(root, "app/src/main/res/xml/backup_rules.xml"))
        assertTrue(excluded(old).containsAll(domains))
        val new = xml(File(root, "app/src/main/res/xml/data_extraction_rules.xml"))
        for (section in listOf("cloud-backup", "device-transfer")) assertTrue(section, excluded(new.getElementsByTagName(section).item(0) as Element).containsAll(domains))
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\"")); assertTrue(manifest.contains("dataExtractionRules")); assertTrue(manifest.contains("fullBackupContent"))
    }

    // ---- Daten im privaten Speicher
    @Test fun keinSchreibzugriffAufDenExternenSpeicherImCode() {
        val src = File(root, "app/src/main/java").walkTopDown().filter { it.extension == "kt" }
        val bad = src.filter { f -> val t = f.readText(); t.contains("getExternalStorage") || t.contains("Environment.getExternal") || t.contains("externalCacheDir") || t.contains("getExternalFilesDir") }.map { it.name }.toList()
        assertTrue("Externer Speicher genutzt in: $bad", bad.isEmpty())
    }
}
