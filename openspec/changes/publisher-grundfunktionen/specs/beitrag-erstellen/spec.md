# Spec Delta

## Purpose

Beschreibt, wie in der App ein neuer Beitrag eingegeben und als Datei im Format des Blogs (`content-format`) erzeugt wird, sodass der Site-Generator ihn ohne Nacharbeit akzeptiert.

## ADDED Requirements

### Requirement: Beitrag eingeben
Die App SHALL einen Editor für einen neuen Beitrag anbieten mit den Feldern Titel (Pflicht), Datum (Pflicht, vorbelegt mit dem heutigen Tag in der Zeitzone Europe/Berlin), Text (Markdown), Schlagworte (Mehrfachauswahl), Autor (optional, vorbelegt aus den Einstellungen) und Kurzbeschreibung (optional, `excerpt`).

#### Scenario: Pflichtfeld fehlt
- **WHEN** der Nutzer speichern will, aber keinen Titel eingegeben hat
- **THEN** markiert die App das Feld Titel und speichert nichts

#### Scenario: Vorbelegtes Datum
- **WHEN** der Nutzer um 0:30 Uhr deutscher Zeit einen neuen Beitrag beginnt
- **THEN** ist das Datum der aktuelle Kalendertag in Europe/Berlin

### Requirement: Beitragsverzeichnis und Permalink
Die App SHALL für den Beitrag ein Verzeichnis `content/posts/<name>/` mit genau einer `index.md` anlegen. `<name>` SHALL aus dem Titel abgeleitet werden (Kleinbuchstaben, Umlaute umgeschrieben, Sonderzeichen durch Bindestriche) und nicht mit einem bestehenden Verzeichnis kollidieren. Der `permalink` SHALL dem URL-Schema des Blogs (`/<Jahr>/<Monat>/<name>/`) folgen und vor dem Speichern änderbar sein.

#### Scenario: Name aus Titel
- **WHEN** der Titel „Ärmelloses Röckchen für Pünktchen“ lautet und am 2026-10-13 gespeichert wird
- **THEN** heißt das Verzeichnis `aermelloses-roeckchen-fuer-puenktchen` und der Permalink lautet `/2026/10/aermelloses-roeckchen-fuer-puenktchen/`

#### Scenario: Name ist vergeben
- **WHEN** `content/posts/alraune/` bereits existiert und ein neuer Beitrag „Alraune“ heißt
- **THEN** schlägt die App einen anderen Namen vor (z. B. mit Jahr) und überschreibt den vorhandenen Beitrag nicht

### Requirement: Frontmatter nach Blog-Format
Die App SHALL die `index.md` als UTF-8-Datei mit YAML-Frontmatter erzeugen, das `title`, `date` (`YYYY-MM-DD`), `permalink` und – falls angegeben – `tags` (nur erlaubte Schlagworte), `author`, `excerpt` und `image` enthält. Der Text steht danach als Markdown.

#### Scenario: Beitrag mit Schlagworten
- **WHEN** der Nutzer den Titel „Mütze“, das Datum 2026-10-13 und die Schlagworte Hüte und Wolle wählt
- **THEN** beginnt die `index.md` mit `---`, enthält `title: "Mütze"`, `date: 2026-10-13`, `tags: [Hüte, Wolle]` und endet das Frontmatter mit `---`

#### Scenario: Sonderzeichen im Titel
- **WHEN** der Titel Anführungszeichen oder einen Doppelpunkt enthält
- **THEN** ist das Frontmatter trotzdem gültiges YAML und der Titel bleibt unverändert

### Requirement: Bilder im Text einbinden
Der Editor SHALL Bilder des Beitrags mit `![Beschreibung](Dateiname)` und dem relativen Dateinamen aus dem Beitragsverzeichnis einfügen können. Direkt aufeinanderfolgende Bildzeilen ohne Leerzeile bilden laut Blog eine Bildgruppe.

#### Scenario: Bild einfügen
- **WHEN** der Nutzer das Bild `IMG_1.jpg` an der Cursorposition einfügt und die Beschreibung „Fertige Mütze“ eingibt
- **THEN** steht dort `![Fertige Mütze](IMG_1.jpg)`

### Requirement: Entwurf lokal bewahren
Die App SHALL ungespeicherte Eingaben bei Unterbrechung (App wird beendet, Anruf, Neustart) erhalten und beim nächsten Öffnen des Editors wiederherstellen.

#### Scenario: App wird beendet
- **WHEN** der Nutzer einen halb geschriebenen Beitrag hat und Android die App beendet
- **THEN** zeigt der Editor beim nächsten Öffnen denselben Text, Titel und dieselben Schlagworte
