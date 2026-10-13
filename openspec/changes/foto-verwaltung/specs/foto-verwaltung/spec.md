# Spec Delta

## Purpose

Beschreibt die Verwaltung der Bilder eines Beitrags in der App: Übersicht, Alt-Texte, Reihenfolge, Einbinden und Entfernen.

## ADDED Requirements

### Requirement: Fotos eines Beitrags anzeigen
Die App SHALL zu jedem Beitrag einen Bildschirm „Fotos“ anbieten, der alle Bilddateien im Beitragsverzeichnis (Endungen nach `content-format` des Blogs) als Raster mit kleinen Vorschauen zeigt. Bilder SHALL gekennzeichnet werden als „Titelbild“ (Feld `image`) und „Nicht im Text“, wenn kein Bild-Verweis im Markdown auf sie zeigt.

#### Scenario: Raster
- **WHEN** der Nutzer bei einem Beitrag mit fünf Bildern „Fotos“ öffnet
- **THEN** zeigt die App fünf Vorschauen, das Titelbild trägt die Kennzeichnung „Titelbild“

#### Scenario: Nicht eingebundenes Bild
- **WHEN** eine Datei im Verzeichnis liegt, im Text aber kein `![…](Datei)` auf sie verweist
- **THEN** trägt ihre Vorschau die Kennzeichnung „Nicht im Text“

### Requirement: Alt-Text ändern
Die App SHALL den Alt-Text eines Bildes nachträglich ändern lassen. Die Änderung SHALL in allen Bildzeilen des Beitrags gelten, die auf diese Datei verweisen, und den restlichen Text unverändert lassen.

#### Scenario: Alt-Text ergänzen
- **WHEN** der Nutzer bei `IMG_1.jpg` den Alt-Text „Fertige Mütze“ eingibt und die Zeile `![](IMG_1.jpg)` im Text steht
- **THEN** lautet die Zeile `![Fertige Mütze](IMG_1.jpg)`

#### Scenario: Bild mit Leerzeichen im Namen
- **WHEN** die Datei `Schwacher Moment.jpg` heißt und der Nutzer ihren Alt-Text ändert
- **THEN** wird die Bildzeile mit diesem Dateinamen gefunden und geändert

### Requirement: Reihenfolge im Abschnitt „Anhänge“
Die App SHALL die Reihenfolge der Bildzeilen im Abschnitt `## Anhänge` per Ziehen ändern lassen. Bildzeilen außerhalb dieses Abschnitts bleiben unverändert. Nach der Änderung stehen die Bildzeilen weiterhin ohne Leerzeile direkt untereinander.

#### Scenario: Bild nach vorn
- **WHEN** unter „Anhänge“ drei Bilder stehen und der Nutzer das dritte an die erste Stelle zieht
- **THEN** steht dessen Zeile zuerst, die beiden anderen folgen in bisheriger Reihenfolge

#### Scenario: Bild im Fließtext
- **WHEN** ein Bild außerhalb von „Anhänge“ im Text eingebunden ist
- **THEN** lässt sich seine Position im Fotobildschirm nicht ändern, und die App weist auf den Editor hin

### Requirement: Nicht eingebundenes Bild einbinden
Die App SHALL ein als „Nicht im Text“ gekennzeichnetes Bild mit einer Aktion „Einbinden“ am Ende von `## Anhänge` mit dem eingegebenen Alt-Text einfügen.

#### Scenario: Einbinden
- **WHEN** der Nutzer bei einem nicht eingebundenen Bild „Einbinden“ wählt und „Detail“ als Alt-Text eingibt
- **THEN** steht am Ende von „Anhänge“ `![Detail](Datei)`

### Requirement: Foto entfernen
Die App SHALL ein Foto nach Bestätigung entfernen: alle Bildzeilen im Text, die auf die Datei verweisen, und die Datei selbst. Ist das Bild das Titelbild, SHALL die App vorher verlangen, ein anderes Titelbild oder „kein Titelbild“ zu wählen. Verweist das Bild an Stellen außerhalb von „Anhänge“ im Text, SHALL die Bestätigung darauf hinweisen. Das Entfernen entsteht als Commit auf `draft`.

#### Scenario: Foto aus Anhängen entfernen
- **WHEN** der Nutzer das Foto `IMG_2.jpg` entfernt und bestätigt
- **THEN** sind die Zeile `![…](IMG_2.jpg)` und die Datei weg, und ein Commit liegt auf `draft`

#### Scenario: Titelbild entfernen
- **WHEN** das Foto das Titelbild ist
- **THEN** verlangt die App zuerst die Wahl eines anderen Titelbilds oder „kein Titelbild“

#### Scenario: Letztes Bild unter „Anhänge“
- **WHEN** die entfernte Zeile die einzige unter `## Anhänge` war
- **THEN** entfernt die App auch die leere Überschrift `## Anhänge`

#### Scenario: Abbrechen
- **WHEN** der Nutzer die Bestätigung abbricht
- **THEN** bleibt alles unverändert

### Requirement: Änderungen als Entwurf
Alle Änderungen der Fotoverwaltung SHALL als Commits auf `draft` landen und über „Veröffentlichen“ live gehen; der Beitrag erscheint danach als Entwurf „Geändert“.

#### Scenario: Reihenfolge ändern
- **WHEN** der Nutzer die Reihenfolge ändert
- **THEN** liegt ein Commit auf `draft`, der nur `index.md` des Beitrags ändert, und `main` ist unverändert
