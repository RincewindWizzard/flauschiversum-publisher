package de.flauschiversum.publisher

import de.flauschiversum.publisher.posts.EditorSnapshotStore
import de.flauschiversum.publisher.posts.ImageRef
import de.flauschiversum.publisher.posts.MarkdownEdit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarkdownEditTest {
    @get:Rule val tmp = TemporaryFolder()
    private val a = ImageRef("Fertige Mütze", "IMG_1.jpg")
    private val b = ImageRef("", "IMG_2.jpg")

    @Test fun bildzeileInLeeremText() {
        assertEquals("![Fertige Mütze](IMG_1.jpg)\n", MarkdownEdit.insertImage("", 0, a).text)
    }

    @Test fun bildzeileMitteImAbsatzWirdAbgesetzt() {
        val r = MarkdownEdit.insertImage("Vorher Nachher", 7, a)
        assertEquals("Vorher \n\n![Fertige Mütze](IMG_1.jpg)\n\nNachher", r.text)
    }

    @Test fun aufeinanderfolgendeBilderBildenEineGruppeOhneLeerzeile() {
        val first = MarkdownEdit.insertImage("Text\n\n", 6, a)
        assertEquals("Text\n\n![Fertige Mütze](IMG_1.jpg)\n", first.text)
        val second = MarkdownEdit.insertImage(first.text, first.text.length, b)
        assertEquals("Text\n\n![Fertige Mütze](IMG_1.jpg)\n![](IMG_2.jpg)\n", second.text)
    }

    @Test fun cursorStehtHinterDerBildzeile() {
        val r = MarkdownEdit.insertImage("A\n\nB", 3, a)
        assertEquals("A\n\n![Fertige Mütze](IMG_1.jpg)\n\nB", r.text)
        assertEquals("A\n\n![Fertige Mütze](IMG_1.jpg)".length, r.cursor)
    }

    // ---- 4.5 Sicherung
    @Test fun sicherungSpeichertUndLaedtAllesInklusiveSonderzeichen() {
        val store = EditorSnapshotStore(tmp.newFolder())
        val values = mapOf("title" to "Ärger: \"Mütze\" #1", "body" to "Zeile 1\nZeile 2\n\n![x](a b.jpg)\n=weg\\", "tags" to "Hüte\nWolle")
        store.save("flauschangriff", values)
        assertEquals(values, EditorSnapshotStore(File(tmp.root, tmp.root.list()!!.first { File(tmp.root, it).isDirectory })).load("flauschangriff"))
    }

    @Test fun sicherungLoeschenUndFehlendeDatei() {
        val dir = tmp.newFolder("s")
        val store = EditorSnapshotStore(dir)
        assertNull(store.load("x"))
        store.save("x", mapOf("a" to "b")); store.delete("x")
        assertNull(store.load("x"))
    }

    @Test fun sicherungenVerschiedenerBeitraegeSindGetrennt() {
        val store = EditorSnapshotStore(tmp.newFolder("t"))
        store.save("a", mapOf("title" to "A")); store.save("_neu", mapOf("title" to "Neu"))
        assertEquals("A", store.load("a")!!["title"]); assertEquals("Neu", store.load("_neu")!!["title"])
    }
}
