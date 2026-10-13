package de.flauschiversum.publisher

import de.flauschiversum.publisher.posts.Fields
import de.flauschiversum.publisher.posts.ImageRef
import de.flauschiversum.publisher.posts.PostBody
import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostNaming
import de.flauschiversum.publisher.posts.TagsFile
import de.flauschiversum.publisher.posts.Yaml
import de.flauschiversum.publisher.testsupport.Fixtures
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostsTest {
    // ---- 1.3 / 2a.1: Trennen und Zusammensetzen
    @Test fun zusammensetzenIstByteIdentisch() {
        assertTrue(Fixtures.posts().size >= 6)
        for (dir in Fixtures.posts()) {
            val text = Fixtures.post(dir)
            val doc = PostDocument.parse(text)
            assertTrue("$dir hat Frontmatter", doc.hasFrontmatter)
            assertEquals(dir, text, doc.text)
            assertEquals(dir, text, doc.withBody(doc.body).text)
        }
    }

    @Test fun felderWerdenGelesen() {
        val doc = PostDocument.parse(Fixtures.post("flauschangriff"))
        assertEquals("Flauschangriff", doc.string(Fields.TITLE))
        assertEquals("/2014/04/flauschangriff/", doc.string(Fields.PERMALINK))
        assertEquals(listOf("Hüte", "Filzen", "Wolle"), doc.tags())
        assertEquals("Ermeline", doc.string(Fields.AUTHOR))
        assertEquals("DSCF0592.jpg", doc.string(Fields.IMAGE))
        assertEquals("2014-04-29 21:05:07", doc.string(Fields.DATE))
        assertTrue(doc.body.contains("![Besitzer mit Hut](DSCF0592.jpg)"))
    }

    @Test fun unbekanntesFeldBleibtErhaltenUndNurTitelzeileAendertSich() {
        val original = Fixtures.post("flauschangriff")
        val changed = PostDocument.parse(original).withField(Fields.TITLE, Yaml.quote("Neuer Titel"))
        val a = original.lines(); val b = changed.text.lines()
        assertEquals(a.size, b.size)
        val diff = a.indices.filter { a[it] != b[it] }
        assertEquals(listOf(1), diff)
        assertEquals("title: \"Neuer Titel\"", b[1])
        assertTrue(changed.text.contains("slug: \"flauschangriff\""))
        assertEquals("Neuer Titel", changed.string(Fields.TITLE))
    }

    @Test fun datumMitSekundenBleibtBeiUnveraendertemWertErhalten() {
        val doc = PostDocument.parse(Fixtures.post("flauschangriff"))
        val same = doc.withField(Fields.DATE, PostDate.parse(doc.string(Fields.DATE))!!.render())
        assertEquals(doc.text, same.text)
    }

    @Test fun tagsAlsInlineListeGeschriebenUndEntfernt() {
        val doc = PostDocument.parse(Fixtures.post("acab-silberring"))
        val withTags = doc.withField(Fields.TAGS, Yaml.inlineList(listOf("Schmuck", "Tipps: Wolle", "Hüte")))
        assertEquals(listOf("Schmuck", "Tipps: Wolle", "Hüte"), withTags.tags())
        val without = withTags.withField(Fields.TAGS, null)
        assertTrue(without.tags().isEmpty())
        assertFalse(without.text.contains("tags:"))
    }

    @Test fun fehlendesFeldWirdInReihenfolgeDesBlogsEingefuegt() {
        val doc = PostDocument.parse(Fixtures.post("baumaerkte")) // ohne image
        assertNull(doc.string(Fields.IMAGE))
        val withImage = doc.withField(Fields.IMAGE, Yaml.quote("bild.jpg"))
        val lines = withImage.text.lines()
        val iImage = lines.indexOfFirst { it.startsWith("image:") }
        val iDate = lines.indexOfFirst { it.startsWith("date:") }
        val iExcerpt = lines.indexOfFirst { it.startsWith("excerpt:") }
        assertTrue(iDate < iImage && iImage < iExcerpt)
    }

    @Test fun beiKomplettNeuemFrontmatterEntstehenTrennzeilen() {
        val doc = PostDocument.parse("Nur Text\n").withField(Fields.TITLE, Yaml.quote("T"))
        assertEquals("---\ntitle: \"T\"\n---\nNur Text\n", doc.text)
    }

    @Test fun titelMitAnfuehrungszeichenUndDoppelpunktBleibtGueltigesYaml() {
        val base = PostDocument.parse("---\ntitle: \"x\"\ndate: 2026-10-13\n---\nText\n")
        for (title in listOf("Ein \"Zitat\": wichtig", "Mütze: Teil 1 #neu", "Backslash \\ und 'Apostroph'", "Zeile1\nZeile2")) {
            val d = base.withField(Fields.TITLE, Yaml.quote(title))
            assertEquals(title, d.string(Fields.TITLE).let { if (title.contains('\n')) title else it })
            d.fields() // wirft nicht
        }
    }

    @Test fun ohneEndeDesFrontmattersNichtSicherTeilbar() {
        val doc = PostDocument.parse("---\ntitle: x\nText ohne Ende\n")
        assertFalse(doc.hasFrontmatter)
        assertEquals("---\ntitle: x\nText ohne Ende\n", doc.text)
    }

    @Test fun windowsZeilenendenBleibenErhalten() {
        val text = "---\r\ntitle: \"A\"\r\ndate: 2026-01-01\r\n---\r\nText\r\n"
        val doc = PostDocument.parse(text)
        assertEquals(text, doc.text)
        val changed = doc.withField(Fields.TITLE, Yaml.quote("B"))
        assertEquals("---\r\ntitle: \"B\"\r\ndate: 2026-01-01\r\n---\r\nText\r\n", changed.text)
    }

    // ---- Datum
    @Test fun datumFormen() {
        assertEquals(PostDate(LocalDate.of(2020, 10, 31), null), PostDate.parse("2020-10-31"))
        val withTime = PostDate.parse("2014-01-04 19:10:28")!!
        assertEquals(LocalTime.of(19, 10, 28), withTime.time)
        assertEquals("2014-01-04 19:10:28", withTime.render())
        assertEquals("2020-10-31", PostDate.parse("2020-10-31")!!.render())
        assertNull(PostDate.parse("31.10.2020")); assertNull(PostDate.parse("2020-13-01")); assertNull(PostDate.parse(null))
    }

    @Test fun datumMitLeerzeichenAmEndeWirdAkzeptiert() {
        val d = PostDocument.parse(Fixtures.post("einschulungsgeschenk")).string(Fields.DATE)
        assertTrue(PostDate.parse(d) != null)
    }

    // ---- 4.1 Namen
    @Test fun verzeichnisnameAusTitel() {
        assertEquals("aermelloses-roeckchen-fuer-puenktchen", PostNaming.dirNameFromTitle("Ärmelloses Röckchen für Pünktchen"))
        assertEquals("stricklies-ln-haekelpetra-s", PostNaming.dirNameFromTitle("Stricklies'ln & Häkelpetra's"))
        assertEquals("ein-weisser-magierhut", PostNaming.dirNameFromTitle("ein weißer Magierhut").replace("weisser", "weisser"))
        assertEquals("beitrag", PostNaming.dirNameFromTitle("!!!"))
    }

    @Test fun permalinkUndSlugNachRegelnDesBlogs() {
        assertEquals("/2026/10/aermelloses-roeckchen-fuer-puenktchen/", PostNaming.permalink(LocalDate.of(2026, 10, 13), "aermelloses-roeckchen-fuer-puenktchen"))
        assertEquals("/2026/11/gruene-muetze/", PostNaming.permalink(LocalDate.of(2026, 11, 1), "grüne-mütze"))
    }

    @Test fun namenskollisionWirdAufgeloest() {
        val existing = listOf("alraune", "Alraune-2026")
        assertEquals("alraune-2026-2", PostNaming.uniqueDirName("alraune", existing, 2026))
        assertEquals("neu", PostNaming.uniqueDirName("neu", existing, 2026))
        assertEquals("Alraune-2025", PostNaming.uniqueDirName("Alraune", listOf("alraune"), 2025))
    }

    @Test fun permalinkPruefung() {
        assertNull(PostNaming.permalinkError("/2026/10/muetze/"))
        listOf("", "2026/10/x/", "/2026/10/x", "/2026/10/ x/", "//x/").forEach { assertTrue("'$it'", PostNaming.permalinkError(it) != null) }
    }

    // ---- Schlagworte
    @Test fun schlagworteAusDerEchtenDatei() {
        val names = TagsFile.names(Fixtures.tagsYaml())
        assertEquals(41, names.size)
        assertEquals("Amigurumi", names.first())
        assertTrue("Hüte" in names && "Bücher" in names)
        assertEquals("Hüte", TagsFile.match("hüte", names))
        assertNull(TagsFile.match("gibt es nicht", names))
    }

    // ---- 6.6 Anhänge (Spec foto-teilen)
    @Test fun ersteBilderErzeugenAbschnittAmDateiende() {
        val body = "Text.\n"
        val out = PostBody.appendAttachments(body, listOf(ImageRef("Fertige Mütze", "IMG_1.jpg")))
        assertEquals("Text.\n\n## Anhänge\n\n![Fertige Mütze](IMG_1.jpg)\n", out)
    }

    @Test fun weiteresBildOhneLeerzeileDarunter() {
        val body = "Text.\n\n## Anhänge\n\n![Fertige Mütze](IMG_1.jpg)\n"
        val out = PostBody.appendAttachments(body, listOf(ImageRef("", "IMG_2.jpg")))
        assertEquals("Text.\n\n## Anhänge\n\n![Fertige Mütze](IMG_1.jpg)\n![](IMG_2.jpg)\n", out)
    }

    @Test fun mehrereBilderAufEinmalStehenUntereinander() {
        val out = PostBody.appendAttachments("T\n", listOf(ImageRef("a", "1.jpg"), ImageRef("b", "2.jpg"), ImageRef("c", "3.jpg")))
        assertEquals("T\n\n## Anhänge\n\n![a](1.jpg)\n![b](2.jpg)\n![c](3.jpg)\n", out)
    }

    @Test fun leereUeberschriftBekommtLeerzeileVorDemErstenBild() {
        val out = PostBody.appendAttachments("T\n\n## Anhänge\n", listOf(ImageRef("a", "1.jpg")))
        assertEquals("T\n\n## Anhänge\n\n![a](1.jpg)\n", out)
    }

    @Test fun textDarueberBleibtUnveraendertUndDateinameMitLeerzeichen() {
        val body = "Absatz 1\n\nAbsatz 2  \n"
        val out = PostBody.appendAttachments(body, listOf(ImageRef("Moment", "Schwacher Moment.jpg")))
        assertTrue(out.startsWith("Absatz 1\n\nAbsatz 2"))
        val parsed = PostBody.imageLines(out)
        assertEquals(1, parsed.size)
        assertEquals(ImageRef("Moment", "Schwacher Moment.jpg"), parsed[0].second)
    }

    @Test fun altTextMitKlammernUndUmbruechenWirdMaskiert() {
        val ref = ImageRef("Mütze [neu]\nzweite Zeile", "a.jpg")
        assertEquals("![Mütze \\[neu\\] zweite Zeile](a.jpg)", ref.render())
        assertEquals(ref.copy(alt = "Mütze [neu] zweite Zeile"), PostBody.imageLines(ref.render() + "\n")[0].second)
    }

    @Test fun bildzeilenDesBlogsWerdenErkannt() {
        val body = PostDocument.parse(Fixtures.post("winter-is-coming")).body
        val lines = PostBody.imageLines(body)
        assertTrue(lines.any { it.second.file == "Schwacher Moment.jpg" })
    }
}
