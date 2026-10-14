package de.flauschiversum.publisher

import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.preview.PreviewInput
import de.flauschiversum.publisher.preview.PreviewRenderer
import de.flauschiversum.publisher.testsupport.Fixtures
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewRendererTest {
    private fun body(md: String, files: Set<String> = emptySet()) = PreviewRenderer.bodyHtml(md, "post", files)

    @Test fun eingebettetesHtmlTabelleUndDurchgestrichen() {
        val h = body("Nadja Bender<br/>\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\n~~alt~~\n").html
        assertTrue(h.contains("Nadja Bender<br/>")); assertTrue(h.contains("<table>")); assertTrue(h.contains("<del>alt</del>"))
    }
    @Test fun einzelbildMitUnterschriftOhneKarussell() {
        val r = body("![Alraune aus Topf gezogen](DSCF0529.jpg)\n", setOf("DSCF0529.jpg"))
        assertTrue(r.html.contains("<figcaption>Alraune aus Topf gezogen</figcaption>"))
        assertFalse(r.html.contains("carousel")); assertEquals(1, r.imageCount); assertEquals(0, r.groupCount); assertEquals(1, r.captionCount)
    }
    @Test fun vierBilderOhneLeerzeileSindEinKarussell() {
        val md = (1..4).joinToString("\n") { "![Bild $it]($it.jpg)" } + "\n"
        val r = body(md, (1..4).map { "$it.jpg" }.toSet())
        assertTrue(r.html.contains("<div class=\"carousel\" data-count=\"4\">"))
        assertEquals(4, Regex("<li class=\"carousel-slide\"").findAll(r.html).count()); assertEquals(1, r.groupCount)
        assertTrue(r.html.contains("1 / 4")); assertTrue(r.html.contains("4 / 4"))
    }
    @Test fun bilderDurchLeerzeileGetrenntSindEinzelbilder() {
        val r = body("![a](1.jpg)\n\n![b](2.jpg)\n", setOf("1.jpg", "2.jpg"))
        assertEquals(0, r.groupCount); assertEquals(2, r.imageCount); assertFalse(r.html.contains("carousel"))
    }
    @Test fun bildnameMitLeerzeichen() {
        val r = body("![Moment](Schwacher Moment.jpg)\n", setOf("Schwacher Moment.jpg"))
        assertTrue(r.html, r.html.contains("https://appassets.androidplatform.net/post/post/Schwacher%20Moment.jpg"))
        assertTrue(r.missing.isEmpty())
    }
    @Test fun fehlendesBildWirdMarkiert() {
        val r = body("![Foto](DSCF0572.jpg)\n", emptySet())
        assertTrue(r.html.contains("Bild fehlt: DSCF0572.jpg")); assertEquals(listOf("DSCF0572.jpg"), r.missing)
    }
    @Test fun skripteWerdenEntfernt() {
        val h = body("Text\n\n<script>alert(1)</script>\n\n<p onclick=\"x()\">a</p> <a href=\"javascript:alert(1)\">l</a>\n").html
        assertFalse(h.contains("<script")); assertFalse(h.contains("alert(1)</")); assertFalse(h.contains("onclick")); assertFalse(h.contains("javascript:"))
    }
    @Test fun kopfUndFehlendesTitelbild() {
        val page = PreviewRenderer.render(PreviewInput("post", "Alraune", PostDate.parse("2014-01-02"), "Ermeline", listOf("Wolle", "Hüte"), "weg.jpg", "Text\n"), setOf("a.jpg"), dark = false)
        assertTrue(page.contains("<h1>Alraune</h1>")); assertTrue(page.contains("02.01.2014 · Ermeline")); assertTrue(page.contains("<span>Hüte</span>"))
        assertTrue(page.contains("Titelbild fehlt: weg.jpg")); assertFalse(page.contains("data-theme=\"dark\""))
        assertTrue(PreviewRenderer.render(PreviewInput("p", "T", null, null, emptyList(), null, ""), emptySet(), dark = true).contains("data-theme=\"dark\""))
        assertTrue(page.contains("Content-Security-Policy")); assertFalse(page.contains("<script"))
    }

    // ---- Abgleich mit dem Blog: Zahl der Bilder, Gruppen, Unterschriften in einer Stichprobe
    @Test fun bilderGruppenUndUnterschriftenStimmenMitDemBlogUeberein() {
        val root = File("/home/user/flauschiversum/content/posts"); org.junit.Assume.assumeTrue(root.isDirectory)
        // Erwartung unabhängig aus dem Markdown ermittelt: aufeinanderfolgende reine Bildzeilen bilden Gruppen
        for (name in listOf("winter-is-coming", "alraune", "flauschangriff", "baumaerkte", "puppenspiel")) {
            val dir = File(root, name); if (!File(dir, "index.md").isFile) continue
            val md = PostDocument.parse(File(dir, "index.md").readText()).body
            val files = dir.list()!!.toSet()
            val r = PreviewRenderer.bodyHtml(md, name, files)
            val lines = md.lines().map { it.trim() }
            val imgLine = Regex("""^!\[[^\]]*]\([^)]*\)$""")
            var expectedGroups = 0; var expectedImages = 0; var run = 0
            for (l in lines + "") { if (imgLine.matches(l)) { run++; expectedImages++ } else { if (run > 1) expectedGroups++; run = 0 } }
            assertEquals("$name Bilder", expectedImages, r.imageCount)
            assertEquals("$name Gruppen", expectedGroups, r.groupCount)
        }
    }

    // ---- Farbschema: Kontraste der Vorschau-Stile (WCAG) in Hell und Dunkel
    private fun vars(css: String, selector: String): Map<String, String> {
        val block = Regex(Regex.escape(selector) + "\\s*\\{([^}]*)}").find(css)!!.groupValues[1]
        return Regex("--([a-z-]+):\\s*(#[0-9a-fA-F]{6})").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
    }
    private fun lum(hex: String): Double {
        fun ch(i: Int): Double { val v = hex.substring(1 + 2 * i, 3 + 2 * i).toInt(16) / 255.0; return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(0) + 0.7152 * ch(1) + 0.0722 * ch(2)
    }
    private fun contrast(a: String, b: String): Double { val (x, y) = lum(a) to lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test fun kontrasteInHellUndDunkel() {
        val css = File("src/main/assets/preview/preview.css").readText()
        for (sel in listOf(":root", ":root[data-theme=\"dark\"]")) {
            val v = vars(css, sel)
            assertTrue("$sel Text auf Hintergrund ${contrast(v["acolor"]!!, v["bgcolor"]!!)}", contrast(v["acolor"]!!, v["bgcolor"]!!) >= 7.0)
            assertTrue("$sel Text auf Karte", contrast(v["acolor"]!!, v["card-bg"]!!) >= 7.0)
            assertTrue("$sel grauer Text", contrast(v["muted"]!!, v["bgcolor"]!!) >= 4.5)
            assertTrue("$sel Link", contrast(v["link"]!!, v["bgcolor"]!!) >= 4.5)
            assertTrue("$sel Schrift auf Akzent", contrast(v["accent-ink"]!!, v["accent"]!!) >= 4.5)
            assertTrue("$sel Hinweis fehlendes Bild", contrast(v["missing-text"]!!, v["missing-bg"]!!) >= 4.5)
        }
    }
}
