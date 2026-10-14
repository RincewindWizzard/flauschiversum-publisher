# beitrag-eigenschaften Specification

## Purpose
Beschreibt das Menü „Eigenschaften“, in dem alle Frontmatter-Felder eines Beitrags mit passenden Eingabemethoden gepflegt werden, sodass der Texteditor das Frontmatter nie zeigen muss.

## Requirements

### Requirement: Eigenschaften-Menü statt Frontmatter im Text
Der Beitragseditor SHALL den Frontmatter-Block nie im Textfeld anzeigen. Stattdessen SHALL er einen Menüpunkt „Eigenschaften“ anbieten, der alle Felder des Beitrags in einem eigenen Bildschirm zeigt und bearbeiten lässt. Das gilt für neue und für vorhandene Beiträge.

#### Scenario: Text ohne Frontmatter
- **WHEN** der Nutzer einen Beitrag mit Frontmatter im Editor öffnet
- **THEN** zeigt das Textfeld nur den Text nach der zweiten Trennzeile `---`

#### Scenario: Menü öffnen
- **WHEN** der Nutzer „Eigenschaften“ wählt
- **THEN** zeigt die App Titel, Datum, Permalink, Schlagworte, Autor, Titelbild und Kurzbeschreibung des Beitrags

### Requirement: Titel und Datum
Der Titel SHALL als einzeiliges Textfeld erfasst werden (Pflichtfeld). Das Datum SHALL über einen Datumswähler (Kalender) gesetzt werden (Pflichtfeld) und optional mit einer Uhrzeit über einen Zeitwähler; ohne Uhrzeit wird `YYYY-MM-DD`, mit Uhrzeit `YYYY-MM-DD HH:MM:SS` in Ortszeit Europe/Berlin gespeichert. Neue Beiträge beginnen mit dem heutigen Tag ohne Uhrzeit.

#### Scenario: Datum wählen
- **WHEN** der Nutzer im Kalender den 18. Juni 2017 wählt und keine Uhrzeit aktiviert
- **THEN** steht im Frontmatter `date: 2017-06-18`

#### Scenario: Datum mit Uhrzeit
- **WHEN** der Nutzer „Uhrzeit angeben“ aktiviert und 19:10 wählt
- **THEN** steht im Frontmatter `date: 2017-06-18 19:10:00`

#### Scenario: Bestehende Uhrzeit
- **WHEN** ein Beitrag `date: 2014-01-04 19:10:28` hat
- **THEN** zeigt das Menü Datum und Uhrzeit, und das Speichern ohne Änderung behält die Sekunden bei

#### Scenario: Leerer Titel
- **WHEN** der Nutzer den Titel leert
- **THEN** zeigt das Feld einen Fehler und die App speichert die Eigenschaften nicht

### Requirement: Permalink
Der Permalink SHALL für neue Beiträge automatisch aus Datum und Verzeichnisname vorgeschlagen werden (`/<Jahr>/<Monat>/<name>/`). Der Nutzer SHALL ihn überschreiben können; die App prüft das Format (beginnt und endet mit `/`, keine Leerzeichen) und meldet, wenn ein anderer Beitrag denselben Permalink hat. Bei vorhandenen Beiträgen wird ein bestehender Permalink nie automatisch geändert.

#### Scenario: Vorschlag
- **WHEN** ein neuer Beitrag im Verzeichnis `muetze` am 2026-10-13 angelegt wird
- **THEN** schlägt das Menü `/2026/10/muetze/` vor

#### Scenario: Doppelter Permalink
- **WHEN** der Nutzer einen Permalink eingibt, den ein anderer Beitrag bereits nutzt
- **THEN** zeigt das Feld einen Fehler mit dem Titel des anderen Beitrags

#### Scenario: Datumsänderung bei vorhandenem Beitrag
- **WHEN** der Nutzer bei einem Beitrag mit Permalink `/2014/01/alraune/` das Datum ändert
- **THEN** bleibt der Permalink unverändert (Cool URIs don't change)

### Requirement: Schlagworte auswählen
Schlagworte SHALL als auswählbare Chips aus der Liste von `content/tags.yaml` angeboten werden, mit Suchfeld zum Filtern und Mehrfachauswahl. Es können nur Schlagworte aus dieser Liste gewählt werden; neue entstehen über die Aktion „Neues Schlagwort“ (Change `schlagworte-verwalten`).

#### Scenario: Schlagwort wählen
- **WHEN** der Nutzer „Hü“ ins Suchfeld tippt und den Chip „Hüte“ antippt
- **THEN** ist „Hüte“ ausgewählt und im Frontmatter `tags: [Hüte]` eingetragen

#### Scenario: Schlagwort abwählen
- **WHEN** der Nutzer einen ausgewählten Chip erneut antippt
- **THEN** wird das Schlagwort entfernt, und sind alle entfernt, lässt die App das Feld `tags` weg

#### Scenario: Unbekanntes Schlagwort im Beitrag
- **WHEN** ein vorhandener Beitrag ein Schlagwort nennt, das nicht in `content/tags.yaml` steht
- **THEN** zeigt das Menü es als ungültig markiert und lässt es entfernen

### Requirement: Autor
Der Autor SHALL über eine Auswahl der bisher in Beiträgen verwendeten Autorennamen mit der Möglichkeit zur freien Eingabe gesetzt werden. Neue Beiträge sind mit dem Autor aus den Einstellungen vorbelegt. Das Feld ist optional.

#### Scenario: Autor wählen
- **WHEN** in den vorhandenen Beiträgen „Ermeline“ vorkommt und der Nutzer das Auswahlfeld öffnet
- **THEN** bietet die App „Ermeline“ zur Auswahl an

#### Scenario: Freie Eingabe
- **WHEN** der Nutzer einen neuen Namen eintippt
- **THEN** übernimmt die App ihn als Autor

### Requirement: Titelbild
Das Titelbild (`image`) SHALL aus den Bildern im Beitragsverzeichnis in einer Vorschau-Auswahl gewählt werden, nicht als Dateiname getippt. Es kann auch „kein Titelbild“ gewählt werden. Gewählt werden können nur Bilddateien, die im Beitragsverzeichnis existieren.

#### Scenario: Bild wählen
- **WHEN** der Beitrag drei Bilder enthält und der Nutzer das zweite in der Vorschau antippt
- **THEN** steht dessen Dateiname im Feld `image`

#### Scenario: Fehlendes Titelbild
- **WHEN** `image` auf eine Datei zeigt, die im Verzeichnis nicht existiert
- **THEN** markiert das Menü das Feld als ungültig, bis ein vorhandenes Bild oder „kein Titelbild“ gewählt ist

### Requirement: Kurzbeschreibung
Die Kurzbeschreibung (`excerpt`) SHALL in einem mehrzeiligen Textfeld mit Zeichenzähler erfasst werden, optional mit der Aktion „Aus Text übernehmen“, die den ersten Satz des Beitragstextes einsetzt. Das Feld ist optional und wird mit der Rechtschreibprüfung geprüft.

#### Scenario: Aus Text übernehmen
- **WHEN** der Text mit „Wer kann schon sagen er hätte einen Ring selbstgemacht? Alle, die …“ beginnt und der Nutzer „Aus Text übernehmen“ wählt
- **THEN** enthält das Feld „Wer kann schon sagen er hätte einen Ring selbstgemacht?“

#### Scenario: Rechtschreibung
- **WHEN** in der Kurzbeschreibung „wundershön“ steht
- **THEN** wird das Wort unterstrichen

### Requirement: Unbekannte Felder und Formatierung erhalten
Felder im Frontmatter, die das Menü nicht kennt, SHALL beim Speichern erhalten bleiben. Felder, die der Nutzer nicht ändert, SHALL Zeichen für Zeichen (Reihenfolge, Anführungszeichen, Listenschreibweise) unverändert bleiben.

#### Scenario: Unbekanntes Feld
- **WHEN** das Frontmatter ein Feld `kommentare: false` enthält und der Nutzer den Titel ändert
- **THEN** steht `kommentare: false` weiterhin an derselben Stelle

#### Scenario: Nur geändertes Feld
- **WHEN** der Nutzer nur den Titel ändert
- **THEN** unterscheidet sich die Datei vom Original nur in der Zeile `title:`

### Requirement: Eigenschaften speichern
Die App SHALL Änderungen an den Eigenschaften zusammen mit dem Text im selben Commit auf dem Entwurfs-Branch speichern. Pflichtfelder (Titel, Datum) und ungültige Werte SHALL das Speichern verhindern; die App zeigt, welches Feld betroffen ist.

#### Scenario: Ungültiges Feld
- **WHEN** der Permalink ein Leerzeichen enthält und der Nutzer speichern will
- **THEN** speichert die App nicht und verweist auf das Feld Permalink

#### Scenario: Änderung der Schlagworte
- **WHEN** der Nutzer bei einem veröffentlichten Beitrag ein Schlagwort hinzufügt und speichert
- **THEN** entsteht ein Commit auf `draft`, der nur die Zeile `tags:` ändert
