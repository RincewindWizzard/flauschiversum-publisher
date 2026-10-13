# Spec Delta

## Purpose

Beschreibt die Vorschau eines Beitrags in der App: möglichst nah an der Darstellung des Blogs, ohne Netzzugriff und ohne den Generator des Blogs auszuführen.

## ADDED Requirements

### Requirement: Vorschau öffnen
Die App SHALL aus dem Editor (Umschalter „Bearbeiten/Vorschau“) und aus der Beitragsübersicht (Menü am Beitrag) eine Vorschau des Beitrags öffnen. Die Vorschau im Editor SHALL den aktuellen, auch ungespeicherten Text und die aktuellen Eigenschaften verwenden.

#### Scenario: Aus dem Editor
- **WHEN** der Nutzer im Editor einen Satz ändert und „Vorschau“ wählt
- **THEN** zeigt die Vorschau den geänderten Satz

#### Scenario: Aus der Übersicht
- **WHEN** der Nutzer im Menü von „Alraune“ „Vorschau“ wählt
- **THEN** zeigt die App „Alraune“ in der Vorschau

### Requirement: Darstellung wie die Beitragsseite
Die Vorschau SHALL den Beitrag als Kopf (Titel, Datum, Autor, Schlagworte) und Text zeigen. Der Text SHALL nach dem Markdown-Dialekt des Blogs dargestellt werden (CommonMark mit Tabellen, Durchstreichen, Fußnoten; eingebettetes HTML wie `<br/>` wird übernommen). Bilder erscheinen in der Breite des Bildschirms mit ihrer Beschreibung als Bildunterschrift.

#### Scenario: Eingebettetes HTML
- **WHEN** der Text `Nadja Bender<br/>` enthält
- **THEN** zeigt die Vorschau einen Zeilenumbruch an dieser Stelle

#### Scenario: Tabelle und Durchstreichen
- **WHEN** der Text eine Tabelle und `~~alt~~` enthält
- **THEN** zeigt die Vorschau die Tabelle und durchgestrichenen Text

#### Scenario: Bildunterschrift
- **WHEN** der Text `![Alraune aus Topf gezogen](DSCF0529.jpg)` enthält
- **THEN** zeigt die Vorschau das Bild mit der Unterschrift „Alraune aus Topf gezogen“

### Requirement: Bildgruppen als Karussell
Aufeinanderfolgende Bildzeilen ohne Leerzeile SHALL als Gruppe in der Vorschau als wischbares Karussell erscheinen (zusammen mit Seitenanzeige), eine Gruppe mit genau einem Bild als Einzelbild ohne Bedienelemente. Ein Tipp auf ein Bild SHALL es im Vollbild öffnen.

#### Scenario: Vier Bilder
- **WHEN** vier Bildzeilen ohne Leerzeile aufeinander folgen
- **THEN** zeigt die Vorschau ein Karussell mit vier Bildern, durch Wischen erreichbar

#### Scenario: Einzelbild
- **WHEN** ein Bild allein in einem Absatz steht
- **THEN** zeigt die Vorschau es ohne Karussell-Bedienelemente

#### Scenario: Vollbild
- **WHEN** der Nutzer ein Bild antippt
- **THEN** öffnet es sich im Vollbild mit Unterschrift und lässt sich schließen

### Requirement: Fehlende Bilder und Probleme markieren
Verweist eine Bildzeile auf eine Datei, die im Beitragsverzeichnis nicht existiert, SHALL die Vorschau an dieser Stelle einen auffälligen Platzhalter mit dem Dateinamen zeigen. Das Titelbild, das nicht existiert, SHALL ebenfalls markiert werden.

#### Scenario: Tippfehler im Dateinamen
- **WHEN** der Text `![Foto](DSCF0572.jpg)` enthält und die Datei nicht existiert
- **THEN** zeigt die Vorschau „Bild fehlt: DSCF0572.jpg“

### Requirement: Farbschema, Sicherheit und Offline
Die Vorschau SHALL das Farbschema des Blogs in Hell und Dunkel gemäß Systemeinstellung (bzw. App-Einstellung) verwenden und vollständig offline laufen. Skripte aus eingebettetem HTML SHALL nicht ausgeführt werden, Netzwerkzugriffe aus der Vorschau SHALL blockiert sein. Links im Text SHALL erst nach Bestätigung im externen Browser geöffnet werden.

#### Scenario: Skript im HTML
- **WHEN** der Text `<script>…</script>` enthält
- **THEN** wird das Skript nicht ausgeführt

#### Scenario: Link antippen
- **WHEN** der Nutzer einen Link `https://beispiel.de/` antippt
- **THEN** fragt die App, ob die Adresse im Browser geöffnet werden soll

#### Scenario: Dunkelmodus
- **WHEN** das Gerät im Dunkelmodus ist
- **THEN** zeigt die Vorschau helle Schrift auf dunklem Grund mit ausreichendem Kontrast

### Requirement: Hinweis auf Abweichungen
Die Vorschau SHALL als Annäherung gekennzeichnet sein: Funktionen wie Suche, verwandte Beiträge, Navigation zwischen Beiträgen und Wasserzeichen auf den Bildern erscheinen nicht. Die App SHALL das einmal pro Installation beim ersten Öffnen erläutern.

#### Scenario: Erste Vorschau
- **WHEN** der Nutzer zum ersten Mal die Vorschau öffnet
- **THEN** erklärt ein kurzer Hinweis, dass es eine Annäherung ohne Wasserzeichen, Suche und verwandte Beiträge ist
