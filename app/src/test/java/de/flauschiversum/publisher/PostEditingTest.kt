package de.flauschiversum.publisher

import de.flauschiversum.publisher.posts.Fields
import de.flauschiversum.publisher.posts.PostDate
import de.flauschiversum.publisher.posts.PostDocument
import de.flauschiversum.publisher.posts.PostEditing
import de.flauschiversum.publisher.posts.PostProps
import de.flauschiversum.publisher.testsupport.Fixtures
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostEditingTest {
    private val allowed = listOf("Hüte", "Wolle", "Filzen", "Schmuck")

    @Test fun neuerBeitragErhaeltFrontmatterNachFormatDesBlogs() {
        val props = PostProps("Mütze", PostDate(LocalDate.of(2026, 10, 13), null), "/2026/10/muetze/", listOf("Hüte", "Wolle"), "Ermeline", null, "Eine warme Mütze.")
        val doc = PostEditing.apply(null, null, props, "Text der Mütze.\n")
        assertEquals(
            "---\ntitle: \"Mütze\"\npermalink: /2026/10/muetze/\ntags: [Hüte, Wolle]\nauthor: Ermeline\ndate: 2026-10-13\nexcerpt: \"Eine warme Mütze.\"\n---\nText der Mütze.\n",
            doc.text,
        )
        assertEquals(props.copy(), PostEditing.read(doc))
    }

    @Test fun erzeugteDateiLaesstSichWiederLesenUndMitBlogvergleichen() {
        // Gleiche Form wie echte Beiträge des Blogs: Zeilen und Reihenfolge
        val real = PostDocument.parse(Fixtures.post("flauschangriff"))
        val props = PostEditing.read(real).copy(image = null)
        val rebuilt = PostEditing.apply(null, null, props, real.body)
        val keys = { d: PostDocument -> d.text.lines().drop(1).takeWhile { it != "---" }.map { it.substringBefore(':') } }
        assertEquals(listOf("title", "permalink", "tags", "author", "date", "excerpt"), keys(rebuilt))
        assertEquals(props, PostEditing.read(rebuilt))
    }

    @Test fun sonderzeichenImTitelBleibenGueltigesYaml() {
        val props = PostProps("Ein \"Zitat\": wichtig #1", PostDate(LocalDate.of(2026, 1, 2), null))
        val doc = PostEditing.apply(null, null, props, "")
        assertEquals("Ein \"Zitat\": wichtig #1", PostEditing.read(doc).title)
    }

    @Test fun nurGeaenderteFelderWerdenGeschrieben() {
        val original = PostDocument.parse(Fixtures.post("flauschangriff"))
        val before = PostEditing.read(original)
        val changed = PostEditing.apply(original, before, before.copy(tags = listOf("Hüte")), original.body)
        val a = original.text.lines(); val b = changed.text.lines()
        assertEquals(a.size, b.size)
        assertEquals(listOf("tags: [Hüte]"), b.filterIndexed { i, l -> l != a[i] })
        // ohne Änderung bleibt die Datei identisch, auch mit Datum inkl. Sekunden
        assertEquals(original.text, PostEditing.apply(original, before, before, original.body).text)
    }

    @Test fun leereOptionaleFelderWerdenEntfernt() {
        val original = PostDocument.parse(Fixtures.post("flauschangriff"))
        val before = PostEditing.read(original)
        val changed = PostEditing.apply(original, before, before.copy(tags = emptyList(), excerpt = "  "), original.body)
        assertFalse(changed.text.contains("tags:")); assertFalse(changed.text.contains("excerpt:"))
        assertTrue(changed.text.contains("slug: \"flauschangriff\""))
    }

    @Test fun datumMitUhrzeitUndAenderung() {
        val original = PostDocument.parse(Fixtures.post("flauschangriff"))
        val before = PostEditing.read(original)
        val changed = PostEditing.apply(original, before, before.copy(date = PostDate(LocalDate.of(2026, 10, 13), java.time.LocalTime.of(19, 10))), original.body)
        assertEquals("2026-10-13 19:10:00", changed.string(Fields.DATE))
    }

    @Test fun pruefungPflichtfelderSchlagworteUndPermalink() {
        val ok = PostProps("T", PostDate(LocalDate.of(2026, 1, 1), null), "/2026/01/t/", listOf("hüte"))
        assertTrue(PostEditing.validate(ok, allowed).isEmpty())
        val bad = PostProps("", null, "/x y/", listOf("Gibtsnicht"), image = "fehlt.jpg")
        val e = PostEditing.validate(bad, allowed, imageFiles = listOf("a.jpg")).map { it.field }
        assertEquals(setOf(Fields.TITLE, Fields.DATE, Fields.PERMALINK, Fields.TAGS, Fields.IMAGE), e.toSet())
        val dup = PostEditing.validate(ok, allowed, otherPermalinks = mapOf("/2026/01/t/" to "Anderer Beitrag"))
        assertEquals(listOf(Fields.PERMALINK), dup.map { it.field }); assertTrue(dup[0].message.contains("Anderer Beitrag"))
    }

    @Test fun schlagworteWerdenInDieSchreibweiseDerListeGebracht() {
        assertEquals(listOf("Hüte", "Wolle"), PostEditing.canonicalTags(listOf("hüte", "WOLLE", "hüte"), allowed))
    }
}
