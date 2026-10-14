package de.flauschiversum.publisher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import de.flauschiversum.publisher.data.AppSettings
import de.flauschiversum.publisher.data.SettingsRepository
import de.flauschiversum.publisher.ui.theme.DarkColors
import de.flauschiversum.publisher.ui.theme.LightColors
import de.flauschiversum.publisher.ui.theme.Nebel
import de.flauschiversum.publisher.ui.theme.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsAndThemeTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @After fun tearDown() { scope.cancel() }

    private fun repo(file: File) = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

    // ---- 3.1 Einstellungen
    @Test fun vorgabenBeimErstenStart() = runBlocking {
        val s = repo(File(tmp.root, "a.preferences_pb")).current()
        assertEquals("git@github.com:RincewindWizzard/flauschiversum.git", s.repositoryAddress)
        assertEquals("main", s.targetBranch)
        assertEquals("Flauschiversum / Ermeline Wollknoll", s.rightsText)
        assertEquals("Ermeline", s.defaultAuthor)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertFalse(s.uploadWifiOnly)
    }

    @Test fun aenderungenBleibenNachNeustartErhalten() = runBlocking {
        val file = File(tmp.root, "b.preferences_pb")
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        SettingsRepository(PreferenceDataStoreFactory.create(scope = first) { file })
            .update { it.copy(commitName = "Ermeline Wollknoll", themeMode = ThemeMode.DARK, uploadWifiOnly = true, editorFontScale = 1.25f) }
        first.cancel() // „App-Neustart“: der erste DataStore wird beendet
        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val s = SettingsRepository(PreferenceDataStoreFactory.create(scope = second) { file }).current()
        second.cancel()
        assertEquals("Ermeline Wollknoll", s.commitName)
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertTrue(s.uploadWifiOnly)
        assertEquals(1.25f, s.editorFontScale, 0.0001f)
    }

    // ---- Farbschema
    @Test fun moduswahl() {
        assertTrue(ThemeMode.SYSTEM.isDark(true)); assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertFalse(ThemeMode.LIGHT.isDark(true)); assertTrue(ThemeMode.DARK.isDark(false))
    }

    @Test fun farbwerteEntsprechenDemBlog() {
        assertEquals(Color(0xFFF6F3FC), LightColors.background)
        assertEquals(Color(0xFF1D1930), LightColors.onBackground)
        assertEquals(Color(0xFF6D4FE0), LightColors.primary)
        assertEquals(Color(0xFF0E0B1F), DarkColors.background)
        assertEquals(Color(0xFF8F73F5), DarkColors.primary)
    }

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble(); val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test fun kontrastWcagInBeidenModi() {
        for ((name, c) in listOf("hell" to LightColors, "dunkel" to DarkColors)) {
            val text = listOf(
                "Text auf Hintergrund" to (c.onBackground to c.background),
                "Text auf Fläche" to (c.onSurface to c.surface),
                "Grauer Text auf Fläche" to (c.onSurfaceVariant to c.surface),
                "Grauer Text auf Hintergrund" to (c.onSurfaceVariant to c.background),
                "Schrift auf Akzent" to (c.onPrimary to c.primary),
                "Link auf Hintergrund" to (c.secondary to c.background),
                "Text auf Akzentfläche" to (c.onPrimaryContainer to c.primaryContainer),
                "Fehler auf Fläche" to (c.error to c.surface),
                "Schrift auf Fehler" to (c.onError to c.error),
                "Gold-Schrift" to (c.onTertiary to c.tertiary),
            )
            for ((label, pair) in text) {
                val r = contrast(pair.first, pair.second)
                assertTrue("$name: $label hat nur ${"%.2f".format(r)} : 1 (< 4,5)", r >= 4.5)
            }
            val controls = listOf(
                "Akzent auf Hintergrund" to (c.primary to c.background),
                "Akzent auf Fläche" to (c.primary to c.surface),
                "Rand auf Fläche" to (c.outline to c.surface),
                "Rand auf Hintergrund" to (c.outline to c.background),
            )
            for ((label, pair) in controls) {
                val r = contrast(pair.first, pair.second)
                assertTrue("$name: $label hat nur ${"%.2f".format(r)} : 1 (< 3)", r >= 3.0)
            }
        }
        assertEquals(Nebel.Light.bg, LightColors.background)
    }

    @Test fun editorSchriftgroesseSkaliertNurDenEditorText() {
        assertEquals(17f, de.flauschiversum.publisher.ui.theme.editorTextStyle(1f).fontSize.value, 0.01f)
        assertEquals(25.5f, de.flauschiversum.publisher.ui.theme.editorTextStyle(1.5f).fontSize.value, 0.01f)
        assertEquals(26f * 1.5f, de.flauschiversum.publisher.ui.theme.editorTextStyle(1.5f).lineHeight.value, 0.01f)
    }
}
