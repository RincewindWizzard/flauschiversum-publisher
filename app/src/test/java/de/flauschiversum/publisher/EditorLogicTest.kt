package de.flauschiversum.publisher

import de.flauschiversum.publisher.editor.EditState
import de.flauschiversum.publisher.editor.Excerpt
import de.flauschiversum.publisher.editor.FormatActions
import de.flauschiversum.publisher.editor.Highlighting
import de.flauschiversum.publisher.editor.HunspellEngine
import de.flauschiversum.publisher.editor.PersonalDictionary
import de.flauschiversum.publisher.editor.SpanKind
import de.flauschiversum.publisher.editor.SpellChecker
import de.flauschiversum.publisher.editor.UndoStack
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FormatActionsTest {
    @Test fun markierterTextWirdFett() {
        val r = FormatActions.bold(EditState("Eine Mütze hier", 5, 10))
        assertEquals("Eine **Mütze** hier", r.text); assertEquals(7, r.selStart); assertEquals(12, r.selEnd)
    }
    @Test fun ohneMarkierungStehtDerCursorZwischenDenZeichen() {
        val r = FormatActions.italic(EditState("ab", 1))
        assertEquals("a**b", r.text); assertEquals(2, r.selStart)
    }
    @Test fun nochmaligFettNimmtAuszeichnungWeg() {
        val r = FormatActions.bold(EditState("**Mütze**", 0, 9))
        assertEquals("Mütze", r.text)
    }
    @Test fun linkOhneMarkierung() {
        val r = FormatActions.link(EditState("Siehe ", 6))
        assertEquals("Siehe [](https://)", r.text); assertEquals(7, r.selStart); assertEquals(7, r.selEnd)
    }
    @Test fun linkMitMarkierungSelektiertDieAdresse() {
        val r = FormatActions.link(EditState("Blog", 0, 4))
        assertEquals("[Blog](https://)", r.text); assertEquals("https://", r.text.substring(r.selStart, r.selEnd))
    }
    @Test fun ueberschriftAufmehrerenZeilenUndZurueck() {
        val r = FormatActions.heading(EditState("a\nb\nc", 0, 3))
        assertEquals("## a\n## b\nc", r.text)
        assertEquals("a\nb\nc", FormatActions.heading(EditState(r.text, 0, r.text.length - 2)).text)
    }
    @Test fun aufzaehlungUndZitat() {
        assertEquals("- eins\n- zwei", FormatActions.bullet(EditState("eins\nzwei", 0, 9)).text)
        assertEquals("> Zitat", FormatActions.quote(EditState("Zitat", 2)).text)
        assertEquals("Zitat", FormatActions.quote(EditState("> Zitat", 3)).text)
    }
}

class UndoStackTest {
    @Test fun loeschenUndRueckgaengigStelltDenTextHer() {
        var now = 0L
        val u = UndoStack(EditState("Hallo Welt", 10), clock = { now })
        now = 5000; u.record(EditState("Hallo ", 6))
        assertTrue(u.canUndo)
        assertEquals("Hallo Welt", u.undo()!!.text)
        assertEquals("Hallo ", u.redo()!!.text)
    }
    @Test fun schnellesTippenIstEinSchritt() {
        var now = 0L
        val u = UndoStack(EditState("", 0), clock = { now })
        for (i in 1..5) { now += 100; u.record(EditState("x".repeat(i), i)) }
        assertEquals("", u.undo()!!.text)
        assertFalse(u.canUndo)
    }
    @Test fun neueEingabeVerwirftWiederholen() {
        var now = 0L
        val u = UndoStack(EditState("a", 1), clock = { now })
        now = 5000; u.record(EditState("ab", 2)); u.undo()
        now = 9000; u.record(EditState("ac", 2))
        assertFalse(u.canRedo)
    }
}

class HighlightingTest {
    private fun kinds(t: String) = Highlighting.spans(t).map { it.kind }.toSet()
    @Test fun ueberschrift() { val s = Highlighting.spans("## Anhänge\n").single(); assertEquals(SpanKind.HEADING, s.kind); assertEquals(0, s.start); assertEquals(10, s.end) }
    @Test fun inlineElemente() {
        val t = "Ein **fetter** und *kursiver* Text mit `code` und [Link](https://x.de)\n"
        assertEquals(setOf(SpanKind.BOLD, SpanKind.ITALIC, SpanKind.CODE, SpanKind.LINK), kinds(t))
        val bold = Highlighting.spans(t).first { it.kind == SpanKind.BOLD }
        assertEquals("**fetter**", t.substring(bold.start, bold.end))
    }
    @Test fun bildzeileListeZitatCodeblock() {
        assertEquals(setOf(SpanKind.IMAGE), kinds("![Alt](a b.jpg)\n"))
        assertTrue(SpanKind.LIST in kinds("- Punkt\n")); assertTrue(SpanKind.QUOTE in kinds("> Zitat\n"))
        assertEquals(listOf(SpanKind.CODE, SpanKind.CODE, SpanKind.CODE), Highlighting.spans("```\n**x**\n```").map { it.kind })
    }
    @Test fun langerTextBleibtSchnell() {
        val t = "Ein **fetter** Absatz mit _Kursivem_ und [Link](https://x.de/a).\n\n".repeat(400)
        val t0 = System.nanoTime(); Highlighting.spans(t); val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(t.length > 20_000); assertTrue("dauerte $ms ms", ms < 500)
    }
}

class ExcerptTest {
    @Test fun ersterSatz() {
        assertEquals("Wer kann schon sagen er hätte einen Ring selbstgemacht?", Excerpt.firstSentence("Wer kann schon sagen er hätte einen Ring selbstgemacht? Alle, die …\n\nMehr."))
    }
    @Test fun ueberspringtUeberschriftenBilderUndEntferntAuszeichnung() {
        assertEquals("Das ist fett und ein Link.", Excerpt.firstSentence("## Titel\n\n![a](b.jpg)\n\nDas ist **fett** und ein [Link](https://x.de). Noch mehr."))
    }
    @Test fun ohneSatzzeichenDerGanzeAbsatz() { assertEquals("Nur ein Fragment", Excerpt.firstSentence("Nur ein\nFragment\n\nZweiter Absatz.")) }
}

class SpellingTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir = File("src/main/assets/dict")
    private val engine by lazy { HunspellEngine(dir.resolve("de_DE.aff").inputStream(), dir.resolve("de_DE.dic").inputStream()) }
    private val extra by lazy { dir.resolve("extra-words.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
    private fun checker(known: Set<String> = emptySet(), personal: PersonalDictionary? = null, ignored: Set<String> = emptySet()) =
        SpellChecker(engine) { it in extra || it in known || personal?.contains(it) == true || it in ignored }
    private fun words(t: String, c: SpellChecker = checker()) = c.check(t).map { it.word }

    @Test fun tippfehlerUndKorrekteWoerter() {
        assertEquals(listOf("wundershön"), words("Das ist wundershön und die Wolle ist weich."))
        assertEquals(emptyList<String>(), words("Strickanleitung Häkelmütze Kuschelkissen Wollknäuel"))
    }
    @Test fun markierungSitztAufDemWort() {
        val t = "Das ist wundershön."
        val m = checker().check(t).single()
        assertEquals("wundershön", t.substring(m.start, m.end))
    }
    @Test fun dateinameUrlCodeUndHtmlWerdenNichtGeprueft() {
        val t = "![Ein Foto](Xyzzyq Bild.jpg)\n\nSiehe https://exampel.de/qwxz und `qwxzcode` <br/> <span class=\"qwxzclass\">x</span>\n\n```\nqwxzblock\n```\n"
        assertEquals(emptyList<String>(), words(t))
    }
    @Test fun altTextWirdGeprueft() { assertEquals(listOf("wundershön"), words("![Ein wundershön Hut](a.jpg)\n")) }
    @Test fun linkTextWirdGeprueftZielNicht() { assertEquals(listOf("wundershön"), words("[wundershön](https://qwxz.de)\n")) }
    @Test fun vorschlaegeEnthaltenDieKorrektur() {
        val s = checker().suggestions("wundershön")
        assertTrue(s.toString(), "wunderschön" in s); assertTrue(s.size <= 5)
        assertEquals(emptyList<String>(), checker().suggestions("qxqxqxqxqxjjjj"))
    }
    @Test fun schlagwortGiltAlsBekannt() {
        assertTrue(words("Mein Amigurumi") .isEmpty())
        assertEquals(listOf("Zzyxwolle"), words("Zzyxwolle"))
        assertEquals(emptyList<String>(), words("Zzyxwolle", checker(known = setOf("Zzyxwolle"))))
    }
    @Test fun persoenlichesWoerterbuchBleibtNachNeustart() {
        val f = File(tmp.root, "dict/personal.txt")
        val p = PersonalDictionary(f); p.add("Zzyxwolle")
        assertEquals(emptyList<String>(), words("Zzyxwolle", checker(personal = p)))
        val reloaded = PersonalDictionary(f)
        assertEquals(listOf("Zzyxwolle"), reloaded.all())
        reloaded.remove("Zzyxwolle")
        assertEquals(listOf("Zzyxwolle"), words("Zzyxwolle", checker(personal = PersonalDictionary(f))))
    }
    @Test fun ignorierenGiltFuerDieSitzung() { assertEquals(emptyList<String>(), words("Zzyxwolle", checker(ignored = setOf("Zzyxwolle")))) }
    @Test fun bindestrichwoerterUndEinzelbuchstaben() {
        assertEquals(emptyList<String>(), words("Ein Strick-Muster und 3D-Druck, a b"))
    }
    @Test fun ohneNetzUndOhneAndroid() { assertTrue(engine.isCorrect("Wolle")) } // reine JVM, keine Netzwerkzugriffe
}
