# markdown-editor Specification

## Purpose
Beschreibt die Bearbeitungsoberfläche für den Markdown-Text eines Beitrags auf dem Handy: Eingabe, Hervorhebung, Hilfen für Auszeichnungen und Schutz vor Textverlust.

## Requirements

### Requirement: Markdown-Text bearbeiten
Die App SHALL den Beitragstext (alles nach dem Frontmatter) in einem Editor anzeigen und bearbeiten lassen. Der Editor SHALL den Text unverändert als UTF-8 speichern, einschließlich Umlauten, Leerzeilen und eingebettetem HTML (z. B. `<br/>`).

#### Scenario: Umlaute und HTML bleiben erhalten
- **WHEN** der Nutzer einen Text mit „Größe“ und `<br/>` bearbeitet und speichert
- **THEN** steht in der Datei exakt derselbe Text, ohne Umkodierung oder Ersetzung

#### Scenario: Unberührter Text
- **WHEN** der Nutzer einen Beitrag öffnet und ohne Änderung speichert
- **THEN** ist die Datei Byte für Byte unverändert

### Requirement: Syntaxhervorhebung
Der Editor SHALL Markdown-Elemente beim Tippen farblich bzw. durch Schrift hervorheben: Überschriften, Hervorhebungen (fett, kursiv), Links, Bildzeilen, Listen, Zitate und Code.

#### Scenario: Überschrift
- **WHEN** der Nutzer die Zeile `## Anhänge` schreibt
- **THEN** wird sie als Überschrift hervorgehoben

#### Scenario: Lange Texte
- **WHEN** ein Beitrag mehr als 20 000 Zeichen hat
- **THEN** bleibt die Eingabe flüssig und die Hervorhebung verzögert sich höchstens kurz

### Requirement: Werkzeugleiste
Der Editor SHALL eine Werkzeugleiste für fett, kursiv, Überschrift, Aufzählung, Link und Zitat anbieten. Ein Tipp SHALL die Auszeichnung um den markierten Text setzen oder, ohne Markierung, an der Cursorposition einfügen und den Cursor in die Auszeichnung setzen.

#### Scenario: Markierten Text fett machen
- **WHEN** der Nutzer das Wort „Mütze“ markiert und „Fett“ tippt
- **THEN** steht `**Mütze**` im Text

#### Scenario: Link ohne Markierung
- **WHEN** der Nutzer ohne Markierung „Link“ tippt
- **THEN** wird `[](https://)` eingefügt und der Cursor steht zwischen den eckigen Klammern

### Requirement: Rückgängig und Wiederholen
Der Editor SHALL beliebig viele Änderungen der laufenden Sitzung rückgängig machen und wiederholen lassen.

#### Scenario: Rückgängig
- **WHEN** der Nutzer einen Absatz löscht und „Rückgängig“ tippt
- **THEN** erscheint der Absatz wieder

### Requirement: Kein Textverlust
Der Editor SHALL ungespeicherte Änderungen bei Unterbrechung (App beendet, Drehung des Bildschirms, Neustart) sichern und beim nächsten Öffnen wiederherstellen. Beim Verlassen mit ungespeicherten Änderungen SHALL die App nachfragen.

#### Scenario: Bildschirm drehen
- **WHEN** der Nutzer das Handy während der Eingabe dreht
- **THEN** bleiben Text, Cursorposition und Rückgängig-Verlauf erhalten

#### Scenario: Verlassen mit Änderungen
- **WHEN** der Nutzer zurück tippt, obwohl der Text geändert wurde
- **THEN** fragt die App, ob die Änderungen gespeichert oder verworfen werden sollen
