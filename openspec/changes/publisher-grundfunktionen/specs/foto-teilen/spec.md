# Spec Delta

## Purpose

Beschreibt, wie Fotos aus anderen Apps (Galerie, Kamera) per Android-Teilen-Funktion an die App gehen, auf die Blog-Größe verkleinert, einem Beitrag zugeordnet und im Repository abgelegt werden.

## ADDED Requirements

### Requirement: Teilen-Ziel für Bilder
Die App SHALL in der Android-Teilen-Auswahl als Ziel für einzelne und mehrere Bilder (`image/*`) erscheinen. Teilen von Inhalten anderer Art (Text, Video, PDF) SHALL die App nicht anbieten.

#### Scenario: Ein Foto teilen
- **WHEN** der Nutzer in der Galerie ein Foto teilt und „Flauschiversum Publisher“ wählt
- **THEN** öffnet sich die App mit der Auswahl des Zielbeitrags und einer Vorschau des Fotos

#### Scenario: Mehrere Fotos teilen
- **WHEN** der Nutzer fünf Fotos auf einmal teilt
- **THEN** zeigt die App alle fünf in der Vorschau und ordnet sie gemeinsam einem Beitrag zu

### Requirement: Auf Blog-Größe verkleinern
Die App SHALL jedes geteilte Bild vor dem Ablegen so verkleinern, dass seine längere Kante höchstens 1000 Pixel misst, dabei das Seitenverhältnis erhalten, die EXIF-Drehung anwenden und kleinere Bilder nicht vergrößern. Das Dateiformat bleibt erhalten (JPEG bleibt JPEG, PNG bleibt PNG, WebP bleibt WebP). Das Ergebnis SHALL höchstens 1 MB groß sein, verlustbehaftete Formate werden dafür entsprechend komprimiert.

#### Scenario: Großes Handyfoto
- **WHEN** ein JPEG mit 4000×3000 Pixeln und 6 MB geteilt wird
- **THEN** wird ein JPEG mit 1000×750 Pixeln und höchstens 1 MB abgelegt

#### Scenario: Hochformatfoto
- **WHEN** ein JPEG mit 3000×4000 Pixeln geteilt wird
- **THEN** wird ein JPEG mit 750×1000 Pixeln abgelegt

#### Scenario: Gedrehtes Foto
- **WHEN** ein Foto quer gespeichert ist und EXIF-Orientation 6 trägt
- **THEN** ist das abgelegte Bild aufrecht im Hochformat und trägt keine Drehung mehr

#### Scenario: Kleines Bild
- **WHEN** ein Bild nur 562×750 Pixel groß ist
- **THEN** bleibt die Größe 562×750 erhalten

### Requirement: Keine Standortdaten
Die App SHALL aus den abgelegten Bildern den GPS-Standort und andere ortsbezogene Metadaten entfernen.

#### Scenario: Foto mit Standort
- **WHEN** ein Foto mit GPS-Koordinaten geteilt wird
- **THEN** enthält das abgelegte Bild keine GPS-Metadaten

### Requirement: Rechteangaben
Die App SHALL in jedes abgelegte Bild Rechteangaben schreiben: als Urheber und Rechteinhaber den Rechtetext aus den Einstellungen (Vorgabe „Flauschiversum / Ermeline Wollknoll“) und als Lizenzhinweis `https://flauschiversum.de/impressum/`, in den Metadatenfeldern, die das Dateiformat des Bildes dafür vorsieht (bei JPEG EXIF Artist/Copyright und XMP).

#### Scenario: Rechte im JPEG
- **WHEN** ein Foto geteilt und abgelegt wurde
- **THEN** zeigt ein Metadaten-Betrachter als Urheber „Flauschiversum / Ermeline Wollknoll“ und den Lizenzhinweis an

#### Scenario: Geänderter Rechtetext
- **WHEN** der Nutzer in den Einstellungen einen anderen Rechtetext eingestellt hat
- **THEN** trägt das nächste abgelegte Bild diesen Text

### Requirement: Zielbeitrag wählen
Die App SHALL den Nutzer vor dem Ablegen einen vorhandenen Beitrag aus der Beitragsliste wählen oder einen neuen Beitrag anlegen lassen (mit mindestens Titel). Ohne Auswahl SHALL nichts abgelegt werden.

#### Scenario: Vorhandenen Beitrag wählen
- **WHEN** der Nutzer beim Teilen den Beitrag „Alraune“ aus der Liste wählt
- **THEN** werden die Fotos in `content/posts/alraune/` abgelegt

#### Scenario: Neuen Beitrag anlegen
- **WHEN** der Nutzer „Neuer Beitrag“ wählt und den Titel „Mütze“ eingibt
- **THEN** legt die App den Beitrag wie in `beitrag-erstellen` an und legt die Fotos in dessen Verzeichnis ab

#### Scenario: Abbrechen
- **WHEN** der Nutzer die Zuordnung abbricht
- **THEN** wird nichts im Repository geändert

### Requirement: Ablegen, Committen, Pushen
Die App SHALL die verkleinerten Bilder im Verzeichnis des Zielbeitrags ablegen, zusammen mit der geänderten `index.md` auf dem Entwurfs-Branch des Beitrags committen und pushen (Spec `entwurf-veroeffentlichen`). Gehört der Beitrag schon zum Zielbranch, legt die App dafür einen Entwurfs-Branch an. Ein vorhandenes Bild mit gleichem Dateinamen SHALL nie stillschweigend überschrieben werden.

#### Scenario: Foto zu veröffentlichtem Beitrag
- **WHEN** ein Foto zum bereits veröffentlichten Beitrag „Alraune“ hinzugefügt wird
- **THEN** entsteht der Branch `draft/alraune` mit dem Bild als Commit, gepusht, und `main` bleibt unverändert

#### Scenario: Gleicher Dateiname
- **WHEN** im Beitragsverzeichnis schon `IMG_1.jpg` liegt und ein anderes Foto ebenfalls `IMG_1.jpg` heißt
- **THEN** speichert die App das neue unter einem anderen Namen (z. B. `IMG_1-2.jpg`)

#### Scenario: Dateiname mit Umlauten
- **WHEN** ein geteiltes Bild `Schwacher Moment.jpg` heißt
- **THEN** bleibt der Name erhalten, da der Blog Umlaute und Leerzeichen unterstützt

### Requirement: Alt-Text je Bild
Die App SHALL beim Teilen für jedes Bild ein eigenes Feld für den Alt-Text (die Bildbeschreibung) anbieten. Der Alt-Text SHALL als Beschreibung in der Markdown-Zeile des Bildes unter „Anhänge“ stehen. Er ist optional; ohne Eingabe bleibt die Beschreibung leer.

#### Scenario: Alt-Text je Bild
- **WHEN** der Nutzer zwei Fotos teilt und für das erste „Fertige Mütze“ eingibt, für das zweite nichts
- **THEN** erhält das erste `![Fertige Mütze](…)` und das zweite `![](…)`

### Requirement: Bild im Abschnitt „Anhänge“ einbinden
Beim Hinzufügen eines Bildes SHALL die App es sofort in der `index.md` des Beitrags unter der Überschrift `## Anhänge` mit `![Alt-Text](Dateiname)` einbinden. Fehlt die Überschrift, legt die App sie am Ende der Datei an. Neue Bildzeilen werden am Ende des Abschnitts angefügt, ohne Leerzeile zwischen den Bildzeilen (eine Bildgruppe); zwischen Überschrift und erster Bildzeile steht eine Leerzeile. Text und andere Abschnitte der Datei bleiben unverändert. Datei und Bild kommen in denselben Commit.

#### Scenario: Erstes Bild in einem Beitrag
- **WHEN** zu einem Beitrag ohne Abschnitt „Anhänge“ das Foto `IMG_1.jpg` mit dem Alt-Text „Fertige Mütze“ hinzugefügt wird
- **THEN** endet die `index.md` mit einer Leerzeile, `## Anhänge`, einer Leerzeile und der Zeile `![Fertige Mütze](IMG_1.jpg)`

#### Scenario: Weiteres Bild
- **WHEN** der Abschnitt „Anhänge“ schon `![Fertige Mütze](IMG_1.jpg)` enthält und `IMG_2.jpg` ohne Alt-Text hinzukommt
- **THEN** steht darunter direkt `![](IMG_2.jpg)`, ohne Leerzeile dazwischen

#### Scenario: Mehrere Bilder auf einmal
- **WHEN** drei Fotos gleichzeitig geteilt werden
- **THEN** stehen drei aufeinanderfolgende Bildzeilen unter „Anhänge“

#### Scenario: Abschnitt steht nicht am Ende
- **WHEN** der Abschnitt „Anhänge“ von weiteren Überschriften gefolgt wird
- **THEN** wird die neue Zeile am Ende des Abschnitts „Anhänge“ eingefügt und die folgenden Abschnitte bleiben unverändert
