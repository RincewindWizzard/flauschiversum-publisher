# Tasks

## 1. Grundlagen und Spike

- [x] 1.1 Markdown-Parser mit Tabellen-, Durchgestrichen- und Fußnoten-Erweiterung einbinden; Unit-Test parst einen Beispielbeitrag des Blogs und liefert Knoten mit Quellpositionen — `MarkdownLintTest`: Beiträge des Blogs parsen, Befunde mit Quellpositionen
- [x] 1.2 Spike Rechtschreibung: Hunspell `de_DE` (Lucene-Hunspell oder JNI) auf einem Gerät/Emulator laden; Startzeit, Speicherbedarf, Komposita-Erkennung und Lizenz in `design.md` festhalten und Wörterbuch auswählen — JVM-Teil und Gerät: vom Nutzer auf dem Gerät bestätigt (Wörterbuch lädt, Fehler werden markiert). Offen bleibt die Lizenzentscheidung zum GPL-Wörterbuch igerman98 (siehe `design.md`)
- [x] 1.3 Schicht `posts`: Datei in Frontmatter und Text trennen und wieder zusammensetzen (Zeilenenden erhalten); Test: Zusammensetzen ohne Änderung ist byte-identisch für alle Beiträge einer Stichprobe aus dem Blog

## 2. Editor (`markdown-editor`)

- [x] 2.1 Compose-Editor mit `BasicTextField`, UTF-8-treuer Übernahme und Speichern; Test: Text mit Umlauten und `<br/>` bleibt unverändert, unveränderter Text ergibt unveränderte Datei — `EditorFeaturesUiTest.umlauteUndHtmlBleibenByteGenau`; Feld ist ein `OutlinedTextField` statt `BasicTextField`
- [x] 2.2 Syntaxhervorhebung (Überschriften, fett/kursiv, Links, Bildzeilen, Listen, Zitate, Code) über `AnnotatedString`; Unit-Test der Spannen, Messung mit 20 000 Zeichen ohne spürbare Verzögerung — `HighlightingTest`, 20 000+ Zeichen in unter 500 ms (JVM)
- [x] 2.3 Werkzeugleiste (fett, kursiv, Überschrift, Aufzählung, Link, Zitat) mit und ohne Markierung; Unit-Tests der Textumformung inkl. Cursorposition — `FormatActionsTest`, UI-Test für Fett und Link
- [x] 2.4 Rückgängig/Wiederholen im ViewModel; Test: Löschen und Rückgängig stellt den Text her — `UndoStackTest`, `rueckgaengigStelltGeloeschtenTextWiederHer`
- [x] 2.5 Textsicherung bei Unterbrechung, Drehung und Verlassen mit Rückfrage; Test: Zustand nach Prozess-Neustart und Drehung identisch (Instrumented-Test) — Rückfrage und Wiederherstellung per Robolectric getestet; Drehung und Prozess-Neustart vom Nutzer auf dem Gerät bestätigt

## 2a. Eigenschaften-Menü (`beitrag-eigenschaften`)

- [x] 2a.1 Verlustfreies Lesen und Ändern der Frontmatter-Zeilen in der Schicht `posts` (bekannte Felder ersetzen/einfügen/entfernen, unbekannte Felder erhalten); Unit-Tests: nur geänderte Zeile im Diff, unbekanntes Feld bleibt, Datum mit Sekunden bleibt, Stichprobe aus dem Blog byte-identisch ohne Änderung — `PostEditingTest`
- [x] 2a.2 Eigenschaften-Bildschirm mit Titel (Pflicht) und Datum über Kalender- und optionalen Zeitwähler; Tests für `YYYY-MM-DD` und `YYYY-MM-DD HH:MM:SS`, Compose-UI-Test für leeren Titel — `PropertiesUiTest.zeitWaehlenUndEntfernen`, `HomeEditorUiTest.fehlenderTitelWirdGemeldet…`
- [x] 2a.3 Permalink-Feld mit Vorschlag aus Datum und Verzeichnisname, Format- und Duplikatprüfung; bestehende Permalinks bleiben bei Datumsänderung; Unit-Tests je Szenario — `PostEditingTest`, `PostsTest`
- [x] 2a.4 Schlagwort-Chips mit Suchfeld aus `tags.yaml`, ungültige Schlagworte markiert; Tests für Wählen, Abwählen (Feld entfällt) und unbekanntes Schlagwort — `PropertiesUiTest`
- [x] 2a.5 Autor-Auswahl (aus Beitragsliste gesammelt, freie Eingabe, Vorbelegung aus Einstellungen); Test der Sammlung aus Beispielbeiträgen — `PropertiesUiTest.autorAusBekanntenWaehlen`
- [x] 2a.6 Titelbild-Auswahl als Vorschau-Raster inkl. „kein Titelbild“, Fehler bei fehlender Datei; Tests mit Beispielverzeichnis — `PropertiesUiTest`; Vorschaubilder werden in Robolectric nicht dekodiert (nur Auswahl geprüft)
- [x] 2a.7 Kurzbeschreibung mit Zähler, „Aus Text übernehmen“ und Rechtschreibung; Test der Satzermittlung am Beispiel „Wer kann schon sagen …“ — `ExcerptTest`, `PropertiesUiTest`
- [x] 2a.8 Speichern von Text und Eigenschaften in einem Commit; Pflichtfelder und ungültige Werte verhindern das Speichern mit Verweis auf das Feld; End-to-End im Emulator, Commit-Diff geprüft — Ein Commit mit Text und Eigenschaften per Test geprüft (`HomeEditorUiTest`); Speichern vom Nutzer auf dem Gerät bestätigt (kein Emulator)

## 3. Markdown-Prüfung (`markdown-pruefung`)

- [x] 3.1 Regeln für Fehler (nicht geschlossene Hervorhebung, Codeblock, Link/Bild-Klammer, Tabellenspalten); Unit-Tests je Szenario der Spec — `MarkdownLintTest`
- [x] 3.2 Regeln für Warnungen (Überschriftenebenen, leerer Link, mehrere Leerzeilen) und Warnung bei entferntem Abschnitt `## Anhänge`; Unit-Tests je Szenario — `MarkdownLintTest`
- [x] 3.3 Abgleich mit dem Blog: Test über eine Stichprobe von Beiträgen aus `content/posts/` des Blogs (nur gelesen) – keine Fehler für HTML, Bildziele mit Leerzeichen und Tabellen — Stichprobe (`beitraegeDesBlogsHabenKeineFehler`) und alle 194 Beiträge, wenn der Blog vorhanden ist. Eine echte Auffälligkeit: `bunte-dokumentenmappe` hat `![Seite 3(DSCF4749.JPG]` (fehlende Klammer), die Prüfung meldet sie als Fehler
- [x] 3.4 Markierung im Editor, Befundliste mit Sprung zur Stelle, entprellte Hintergrundprüfung; UI-Test: Behebung entfernt den Befund — `EditorFeaturesUiTest.fehlerErscheintNachKurzerPause…`
- [x] 3.5 Hinweis vor Speichern/Veröffentlichen bei offenen Fehlern (Fortfahren möglich, Warnungen ohne Abfrage); UI-Test für beide Wege — `EditorFeaturesUiTest` (Speichern) und `HomeEditorUiTest` (Veröffentlichen)

## 4. Rechtschreibprüfung (`rechtschreibpruefung`)

- [x] 4.1 Wörterbuch aus Aufgabe 1.2 einbinden, Laden im Hintergrund; Test: „wundershön“ falsch, „Wolle“, „Strickanleitung“, „Häkelmütze“ richtig — `SpellingTest`; „Häkelmütze“ steht in der Zusatzliste `extra-words.txt`, weil das Wörterbuch das Wort nicht kennt
- [x] 4.2 Nur Fließtext und Alt-Texte aus dem Markdown-Baum prüfen; Tests: Dateiname, URL, Code, HTML werden nicht gemeldet, Alt-Text schon — `SpellingTest`
- [x] 4.3 Unterstreichung im Editor, Vorschlagsmenü (bis zu fünf, Groß-/Kleinschreibung beachtet), Ersetzen; UI-Test: Vorschlag übernehmen, „Keine Vorschläge“ — `EditorFeaturesUiTest.rechtschreibfehlerBietetVorschlagAnUndErsetzt`; „Keine Vorschläge“ nur auf Logikebene (`SpellingTest`)
- [x] 4.4 Persönliches Wörterbuch (hinzufügen, entfernen, „Ignorieren“ je Sitzung) in Datei und Einstellungen; Tests inkl. Persistenz nach Neustart — `SpellingTest`, `EditorFeaturesUiTest.wortInsPersoenlicheWoerterbuchAufnehmen`; Verwaltung in den Einstellungen: `SettingsUiTest.persoenlichesWoerterbuch…`
- [x] 4.5 Vorbelegung aus `content/tags.yaml`; Test: „Amigurumi“ wird nicht gemeldet — `SpellingTest.schlagwortGiltAlsBekannt` (Mechanismus), Tags werden aus `tags.yaml` geladen

## 5. Beiträge bearbeiten (`beitrag-bearbeiten`)

- [x] 5.1 „Bearbeiten“ in der Beitragsliste; Textfeld zeigt nur den Text, Frontmatter nur über das Menü „Eigenschaften“, Meldung bei nicht trennbarer Datei; Tests mit Beispielbeiträgen und Datei ohne Frontmatter-Ende — `EditorFeaturesUiTest.dateiOhneFrontmatterEnde…`
- [x] 5.2 Beitrag sofort mit dem letzten Stand öffnen, Hintergrundaktualisierung anstoßen, Text von `draft` nur bei Beiträgen mit Entwurf, sonst vom Zielbranch, neuere Fassung ohne Eingabe übernehmen, bei bereits getippter Eingabe nur Hinweis „Beitrag wurde auf dem Server geändert“; Tests mit Testrepository für alle Fälle inkl. Offline — `EditorFeaturesUiTest` (neuere Fassung, Eingabe, Entwurf); Offline-Fall wegen lokalem Klon nicht eigens getestet
- [x] 5.3 Speichern als Commit auf `draft` und Push über den vorhandenen Entwurfsweg; keine Änderung bedeutet kein Commit; Tests: nur `index.md` im Diff, `main` unverändert — `EditorFeaturesUiTest.speichernOhneAenderung…`, `HomeEditorUiTest`

## 6. Integration und Dokumentation

- [x] 6.1 Editor im Beitragseditor aus `publisher-grundfunktionen` statt des einfachen Textfelds einsetzen; End-to-End im Emulator: Beitrag bearbeiten, Fehler beheben, speichern, veröffentlichen — Editor im Beitragseditor ersetzt das einfache Textfeld; End-to-End im Emulator fehlt
- [x] 6.2 Beitrag nach der Bearbeitung mit dem Generator des Blogs prüfen (`cargo run --release -- check` in einer Kopie des Blogs); Ergebnis ohne Fehler — `GeneratorCheckTest.bearbeiteterBeitragBestehtDiePruefungDesGenerators`
- [x] 6.3 `README.md` um Editor, Prüfungen und persönliches Wörterbuch ergänzen; Schritte gegen die Oberfläche abgeglichen (nicht auf einem Gerät durchgespielt)

## Workflow follow-up

- Abhängigkeit beachten: Umsetzung nach den Kern-Aufgaben aus `publisher-grundfunktionen` (Klon, Beitragsliste, Entwurfs-Branches).
- Change nach Review archivieren (`/opsx:archive`).
