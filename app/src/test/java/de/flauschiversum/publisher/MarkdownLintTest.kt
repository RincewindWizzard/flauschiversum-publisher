package de.flauschiversum.publisher

import de.flauschiversum.publisher.editor.Finding
import de.flauschiversum.publisher.editor.LineIndex
import de.flauschiversum.publisher.editor.MarkdownLint
import de.flauschiversum.publisher.editor.Severity
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.testsupport.Fixtures
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MarkdownLintTest {
    private fun errors(t: String) = MarkdownLint.check(t).filter { it.severity == Severity.ERROR }
    private fun warnings(t: String) = MarkdownLint.check(t).filter { it.severity == Severity.WARNING }

    // ---- gültig nach dem Dialekt des Blogs
    @Test fun eingebettetesHtmlIstKeinBefund() { assertEquals(emptyList<Finding>(), MarkdownLint.check("Nadja Bender<br/>\n")) }
    @Test fun bildzielMitLeerzeichenIstGueltig() {
        assertEquals(emptyList<Finding>(), MarkdownLint.check("![Moment](Schwacher Moment.jpg)\n"))
        assertEquals(emptyList<Finding>(), MarkdownLint.check("![x](DSCF4858 (Kopie).JPG)\n"))
    }
    @Test fun gueltigeTabelleIstKeinFehler() {
        assertEquals(emptyList<Finding>(), errors("| a | b | c |\n|---|---|---|\n| 1 | 2 | 3 |\n| 4 | 5 | 6 |\n"))
    }
    @Test fun maskierteSternchenSindGewollt() { assertEquals(emptyList<Finding>(), errors("Preis: 5 \\*\\* netto, a * b, snake_case_name, 2*3*4\n")) }
    @Test fun durchgestrichenUndFussnote() { assertEquals(emptyList<Finding>(), errors("~~weg~~ und eine Notiz[^1]\n\n[^1]: Text\n")) }

    // ---- Fehler
    @Test fun nichtGeschlosseneHervorhebung() {
        val t = "Das ist **sehr schön\n"
        val e = errors(t).single()
        assertEquals(8, e.start); assertEquals(10, e.end)
        assertEquals("**", t.substring(e.start, e.end))
    }
    @Test fun nichtGeschlosseneKursiveAmWortanfang() { assertEquals(1, errors("das ist *schön und weiter\n").size) }
    @Test fun nichtGeschlossenerCodeblock() {
        val t = "Text\n\n```\ncode\nweiter\n"
        val e = errors(t).single()
        assertEquals(2, LineIndex(t).let { it.lineOf(e.start) - 1 }); assertEquals("```", t.substring(e.start, e.end))
    }
    @Test fun geschlossenerCodeblockMitSternchenIstKeinFehler() { assertEquals(emptyList<Finding>(), errors("```\n**kein fett\n```\n")) }
    @Test fun nichtGeschlossenerCodeSchnipsel() { assertEquals(1, errors("ein `code ohne ende\n").size) }
    @Test fun linkOhneSchliessendeKlammer() {
        assertEquals(1, errors("Siehe [Blog](https://x.de und mehr\n").size)
        assertEquals(1, errors("Bild ![Alt](a.jpg\n").size)
    }
    @Test fun tabellenzeileMitFalscherSpaltenzahl() {
        val t = "| a | b | c |\n|---|---|---|\n| 1 | 2 |\n| 4 | 5 | 6 |\n"
        val e = errors(t).single()
        assertEquals(3, LineIndex(t).lineOf(e.start)); assertTrue(e.message.contains("2"))
    }
    @Test fun maskiertePipeZaehltNichtAlsZelle() { assertEquals(emptyList<Finding>(), errors("| a | b |\n|---|---|\n| x\\|y | 2 |\n")) }

    // ---- Warnungen
    @Test fun uebersprungeneUeberschrift() {
        val t = "## Eins\n\nText\n\n#### Vier\n"
        val w = warnings(t).single()
        assertEquals(5, LineIndex(t).lineOf(w.start))
    }
    @Test fun leererLinkTextOderZiel() {
        assertEquals(1, warnings("[](https://x.de)\n").size)
        assertEquals(1, warnings("[Text]()\n").size)
    }
    @Test fun mehrereLeerzeilen() { assertEquals(1, warnings("a\n\n\n\nb\n").size); assertEquals(0, warnings("a\n\nb\n").size) }
    @Test fun leerzeichenAmZeilenendeVorUeberschrift() { assertEquals(1, warnings("Text  \n## Titel\n").size) }
    @Test fun fehlenderAnhaengeAbschnittWarnt() {
        val old = "Text\n\n## Anhänge\n\n![a](b.jpg)\n"
        assertEquals(1, MarkdownLint.check("Text\n", old).count { it.severity == Severity.WARNING && it.message.contains("Anhänge") })
        assertEquals(0, MarkdownLint.check(old, old).size)
        assertEquals(0, MarkdownLint.check("Text\n", "Text\n").size)
    }

    // ---- Abgleich mit Beiträgen des Blogs: nichts, was der Blog akzeptiert, ist ein Fehler
    @Test fun beitraegeDesBlogsHabenKeineFehler() {
        val bad = Fixtures.posts().mapNotNull { dir ->
            val body = PostDocument.parse(Fixtures.post(dir)).body
            errors(body).takeIf { it.isNotEmpty() }?.let { dir to it.map { f -> f.message + " @" + body.substring(f.start, minOf(f.end + 20, body.length)).replace("\n", "⏎") } }
        }
        assertTrue("Fehler in Beiträgen des Blogs: $bad", bad.isEmpty())
    }

    @Test fun alleBeitraegeDesBlogsWennVorhanden() {
        val root = File("/home/user/flauschiversum/content/posts"); assumeTrue(root.isDirectory)
        val bad = root.listFiles()!!.filter { File(it, "index.md").isFile }.mapNotNull { d ->
            val body = PostDocument.parse(File(d, "index.md").readText()).body
            errors(body).takeIf { it.isNotEmpty() }?.let { d.name to it.map { f -> f.message + " @" + body.substring(f.start, minOf(f.end + 20, body.length)).replace("\n", "⏎") } }
        }
        // Echter Tippfehler im Blog: „![Seite 3(DSCF4749.JPG]“ (Klammer fehlt); der Blog zeigt den Text wörtlich an
        val unexpected = bad.filter { it.first != "bunte-dokumentenmappe" }
        assertTrue("Fehler in Beiträgen des Blogs (${unexpected.size}): ${unexpected.take(8)}", unexpected.isEmpty())
        assertEquals(listOf("bunte-dokumentenmappe"), bad.map { it.first })
    }

    @Test fun langerTextBleibtSchnell() {
        val para = "Ein **fetter** Absatz mit _Kursivem_, einem [Link](https://x.de/a) und Umlauten wie Größe.\n\n"
        val text = para.repeat(300)
        assertTrue(text.length > 20_000)
        val t0 = System.nanoTime(); MarkdownLint.check(text); val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("Prüfung dauerte $ms ms", ms < 1500)
    }
}
