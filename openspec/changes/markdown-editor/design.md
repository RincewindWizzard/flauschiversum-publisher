# Design

## Context

Der Editor baut auf `publisher-grundfunktionen` auf (noch nicht umgesetzt): Repository-Klon, Beitragsliste, Entwurfs-Branches `draft/<name>`, Abschnitt `## Anhänge` am Dateiende, Schicht `posts` für Frontmatter. Dieser Change ersetzt das einfache Textfeld des Beitragseditors (`beitrag-erstellen`) durch den neuen Editor und ergänzt das Bearbeiten bestehender Beiträge, das dort als Non-Goal stand. Der Blog rendert mit `pulldown-cmark` (Tabellen, Durchstreichen, Fußnoten); Bildziele mit Leerzeichen (`![x](a b.jpg)`) werden dort vor dem Parsen umgeschrieben und sind deshalb gültig. Motivation und Umfang: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Text schreiben und nachbessern mit sichtbaren Markdown- und Rechtschreibfehlern, komplett offline.
- Prüfung verhält sich wie der Blog: Was er akzeptiert, wird nicht beanstandet.
- Eingaben gehen nie verloren.

**Non-Goals:**
- Bearbeiten des Frontmatters (Titel, Datum, Schlagworte) bestehender Beiträge – eigener Change.
- Grammatik- und Stilprüfung (LanguageTool), Prüfung in anderen Sprachen, automatische Korrektur.
- Live-Vorschau des gerenderten Beitrags, Löschen oder Umbenennen von Beiträgen.
- Prüfung von Frontmatter, Bildverweisen auf vorhandene Dateien und Permalinks (vom Nutzer nicht gewünscht; später ergänzbar).

## Decisions

**Editor: eigenes Compose-Textfeld (`BasicTextField`) mit Hervorhebung über `AnnotatedString`.** Volle Kontrolle über Hervorhebung, Unterstreichungen und Werkzeugleiste. Alternative: WebView mit CodeMirror/Monaco – verworfen (Gewicht, Tastatur- und Auswahlprobleme auf Android, schwer testbar). Rückgängig/Wiederholen und Zustandssicherung liegen im ViewModel (`rememberSaveable` allein reicht für große Texte nicht), der Text wird zusätzlich in den App-Speicher gesichert.

**Markdown-Prüfung: Parser auf der JVM (z. B. commonmark-java mit Erweiterungen für Tabellen, Durchstreichen, Fußnoten) plus eigene Regeln.** CommonMark kennt keine Syntaxfehler: Eine nicht geschlossene Hervorhebung bleibt als wörtliches `**` im Text stehen. Die Regeln suchen daher im geparsten Baum nach solchen wörtlichen Resten (`**`, `_`, Backticks, `[`/`](`) und prüfen Strukturen (Überschriftenebenen, Tabellenspalten, Codeblock-Ende). Mit Quellpositionen am Knoten lassen sich Befunde im Editor markieren. Abweichungen zum Rust-Parser des Blogs sind möglich; Abgleich über Testfälle aus Beiträgen des Blogs (gelesen, nicht verändert), bei denen keine Fehler gemeldet werden dürfen.

**Rechtschreibung: Hunspell-Wörterbuch `de_DE` eingebaut, Prüfung auf der JVM.** Offline, unabhängig vom Handy, und Texte verlassen das Gerät nicht. Kandidat ist die Hunspell-Implementierung von Lucene (rein Java); Alternative eine JNI-Anbindung der nativen Hunspell-Bibliothek. Die Wahl fällt im Spike (Aufgabe 1.2) nach Startzeit, Speicherbedarf, Qualität bei Komposita und Lizenz. Alternative Android-Systemprüfung (`SpellCheckerSession`) wurde vom Nutzer abgelehnt: hängt vom installierten Wörterbuch des Handys ab.

**Nur Fließtext prüfen:** Die Rechtschreibung bekommt vom Markdown-Baum nur Textknoten und Alt-Texte; Links, Code, HTML und Dateinamen werden nicht übergeben. Dadurch ist keine eigene Zeichenfilterung nötig.

**Persönliches Wörterbuch in einer Datei im App-Speicher**, beim ersten Start vorbelegt aus `content/tags.yaml`. Wörter werden normalisiert (Groß-/Kleinschreibung beachtet) gespeichert.

**Prüfungen laufen entprellt im Hintergrund** (z. B. 400 ms nach der letzten Eingabe, `Dispatchers.Default`); Ergebnisse werden gegen Textänderungen während der Berechnung verworfen. Befunde sind Zeichenbereiche; bei Änderungen verschieben sich Bereiche bis zum nächsten Lauf nicht mit.

**Bearbeiten: Frontmatter und Text trennen.** Die Schicht `posts` teilt die Datei in Frontmatter (inklusive Trennzeilen, unverändert) und Text. Gespeichert wird `Frontmatter + bearbeiteter Text`, damit der Diff nur den Text betrifft. Zeilenenden werden wie in der Originaldatei beibehalten.

**Speichern über den vorhandenen Entwurfsweg:** Commit auf `draft/<verzeichnisname>` und Push, „Veröffentlichen“ wie in `entwurf-veroeffentlichen`. Kein neuer Git-Code.

## Risks / Trade-offs

- [Hunspell auf Android zu langsam oder zu speicherhungrig] → Wörterbuch beim App-Start im Hintergrund laden, Ergebnisse zwischenspeichern; im Spike messen. Fallback JNI.
- [Wörterbuchlizenz passt nicht zum Projekt] → Lizenz von `de_DE` vor dem Einbinden prüfen; sonst anderes freies Wörterbuch.
- [Falsche Treffer bei Fachwörtern und Eigennamen] → Persönliches Wörterbuch, Vorbelegung aus den Schlagworten, „Ignorieren“ je Sitzung.
- [Abweichung vom Rust-Parser] → Testfälle aus echten Beiträgen; im Zweifel Warnung statt Fehler.
- [Lange Texte machen das Textfeld träge] → Hervorhebung nur im sichtbaren Bereich neu berechnen; Prüfung im Hintergrund; Test mit einem Beitrag von über 20 000 Zeichen.
- [App wird größer] → Wörterbuch nur de_DE; Größe in der Spike-Messung festhalten.

## Open Questions

- **Lizenz des Wörterbuchs:** Welches `de_DE`-Wörterbuch (z. B. Hunspell-DE von j3e.de oder aus LibreOffice) passt lizenzrechtlich? Klärung vor der Aufnahme in die App (Aufgabe 1.2), ändert nichts an Specs oder Aufgaben.
