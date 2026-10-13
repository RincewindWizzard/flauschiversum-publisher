# Tasks

## 1. Grundlagen und Spike

- [ ] 1.1 Markdown-Parser mit Tabellen-, Durchgestrichen- und Fußnoten-Erweiterung einbinden; Unit-Test parst einen Beispielbeitrag des Blogs und liefert Knoten mit Quellpositionen
- [ ] 1.2 Spike Rechtschreibung: Hunspell `de_DE` (Lucene-Hunspell oder JNI) auf einem Gerät/Emulator laden; Startzeit, Speicherbedarf, Komposita-Erkennung und Lizenz in `design.md` festhalten und Wörterbuch auswählen
- [ ] 1.3 Schicht `posts`: Datei in Frontmatter und Text trennen und wieder zusammensetzen (Zeilenenden erhalten); Test: Zusammensetzen ohne Änderung ist byte-identisch für alle Beiträge einer Stichprobe aus dem Blog

## 2. Editor (`markdown-editor`)

- [ ] 2.1 Compose-Editor mit `BasicTextField`, UTF-8-treuer Übernahme und Speichern; Test: Text mit Umlauten und `<br/>` bleibt unverändert, unveränderter Text ergibt unveränderte Datei
- [ ] 2.2 Syntaxhervorhebung (Überschriften, fett/kursiv, Links, Bildzeilen, Listen, Zitate, Code) über `AnnotatedString`; Unit-Test der Spannen, Messung mit 20 000 Zeichen ohne spürbare Verzögerung
- [ ] 2.3 Werkzeugleiste (fett, kursiv, Überschrift, Aufzählung, Link, Zitat) mit und ohne Markierung; Unit-Tests der Textumformung inkl. Cursorposition
- [ ] 2.4 Rückgängig/Wiederholen im ViewModel; Test: Löschen und Rückgängig stellt den Text her
- [ ] 2.5 Textsicherung bei Unterbrechung, Drehung und Verlassen mit Rückfrage; Test: Zustand nach Prozess-Neustart und Drehung identisch (Instrumented-Test)

## 3. Markdown-Prüfung (`markdown-pruefung`)

- [ ] 3.1 Regeln für Fehler (nicht geschlossene Hervorhebung, Codeblock, Link/Bild-Klammer, Tabellenspalten); Unit-Tests je Szenario der Spec
- [ ] 3.2 Regeln für Warnungen (Überschriftenebenen, leerer Link, mehrere Leerzeilen) und Warnung bei entferntem Abschnitt `## Anhänge`; Unit-Tests je Szenario
- [ ] 3.3 Abgleich mit dem Blog: Test über eine Stichprobe von Beiträgen aus `content/posts/` des Blogs (nur gelesen) – keine Fehler für HTML, Bildziele mit Leerzeichen und Tabellen
- [ ] 3.4 Markierung im Editor, Befundliste mit Sprung zur Stelle, entprellte Hintergrundprüfung; UI-Test: Behebung entfernt den Befund
- [ ] 3.5 Hinweis vor Speichern/Veröffentlichen bei offenen Fehlern (Fortfahren möglich, Warnungen ohne Abfrage); UI-Test für beide Wege

## 4. Rechtschreibprüfung (`rechtschreibpruefung`)

- [ ] 4.1 Wörterbuch aus Aufgabe 1.2 einbinden, Laden im Hintergrund; Test: „wundershön“ falsch, „Wolle“, „Strickanleitung“, „Häkelmütze“ richtig
- [ ] 4.2 Nur Fließtext und Alt-Texte aus dem Markdown-Baum prüfen; Tests: Dateiname, URL, Code, HTML werden nicht gemeldet, Alt-Text schon
- [ ] 4.3 Unterstreichung im Editor, Vorschlagsmenü (bis zu fünf, Groß-/Kleinschreibung beachtet), Ersetzen; UI-Test: Vorschlag übernehmen, „Keine Vorschläge“
- [ ] 4.4 Persönliches Wörterbuch (hinzufügen, entfernen, „Ignorieren“ je Sitzung) in Datei und Einstellungen; Tests inkl. Persistenz nach Neustart
- [ ] 4.5 Vorbelegung aus `content/tags.yaml`; Test: „Amigurumi“ wird nicht gemeldet

## 5. Beiträge bearbeiten (`beitrag-bearbeiten`)

- [ ] 5.1 „Bearbeiten“ in der Beitragsliste; öffnet nur den Text, Frontmatter unverändert, Meldung bei nicht trennbarer Datei; Tests mit Beispielbeiträgen und Datei ohne Frontmatter-Ende
- [ ] 5.2 Aktualisieren vor dem Öffnen, Text aus vorhandenem `draft/<name>` bevorzugen, Hinweis ohne Netz; Test mit lokalem Testrepository
- [ ] 5.3 Speichern als Commit auf `draft/<name>` und Push über den vorhandenen Entwurfsweg; keine Änderung bedeutet kein Commit; Tests: nur `index.md` im Diff, `main` unverändert

## 6. Integration und Dokumentation

- [ ] 6.1 Editor im Beitragseditor aus `publisher-grundfunktionen` statt des einfachen Textfelds einsetzen; End-to-End im Emulator: Beitrag bearbeiten, Fehler beheben, speichern, veröffentlichen
- [ ] 6.2 Beitrag nach der Bearbeitung mit dem Generator des Blogs prüfen (`cargo run --release -- check` in einer Kopie des Blogs); Ergebnis ohne Fehler
- [ ] 6.3 `README.md` um Editor, Prüfungen und persönliches Wörterbuch ergänzen; beschriebene Schritte funktionieren wie dokumentiert

## Workflow follow-up

- Abhängigkeit beachten: Umsetzung nach den Kern-Aufgaben aus `publisher-grundfunktionen` (Klon, Beitragsliste, Entwurfs-Branches).
- Change nach Review archivieren (`/opsx:archive`).
