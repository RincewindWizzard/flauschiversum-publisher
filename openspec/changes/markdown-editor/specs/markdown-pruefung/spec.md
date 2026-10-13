# Spec Delta

## Purpose

Beschreibt die Prüfung des Markdowns auf Syntaxfehler und Hinweise, passend zum Markdown-Dialekt des Blogs, damit fehlerhafte Auszeichnungen vor dem Veröffentlichen auffallen.

## ADDED Requirements

### Requirement: Prüfung nach dem Dialekt des Blogs
Die App SHALL den Text nach den Regeln prüfen, nach denen der Blog ihn verarbeitet: CommonMark mit Tabellen, Durchstreichen und Fußnoten; eingebettetes HTML ist erlaubt. Der Blog gilt als maßgeblich: Was er akzeptiert, SHALL die App nicht als Fehler melden.

#### Scenario: Eingebettetes HTML
- **WHEN** der Text `Nadja Bender<br/>` enthält
- **THEN** meldet die Prüfung dazu nichts

#### Scenario: Leerzeichen im Bildnamen
- **WHEN** der Text `![Moment](Schwacher Moment.jpg)` enthält
- **THEN** meldet die Prüfung keinen Fehler, weil der Blog solche Bildziele akzeptiert

#### Scenario: Tabelle
- **WHEN** der Text eine gültige Tabelle mit Kopfzeile und Trennzeile enthält
- **THEN** meldet die Prüfung keinen Fehler

### Requirement: Fehler und Warnungen
Die Prüfung SHALL Befunde mit Schweregrad Fehler oder Warnung liefern. Fehler sind Konstrukte, die erkennbar nicht wie gemeint dargestellt werden: nicht geschlossene Hervorhebung (`**fett`), nicht geschlossener Codeblock, Link oder Bild mit nicht geschlossener Klammer, Tabellenzeile mit falscher Spaltenzahl. Warnungen sind Auffälligkeiten: übersprungene Überschriftenebene, leerer Link-Text oder leeres Linkziel, mehrere Leerzeilen hintereinander, Leerzeichen am Zeilenende vor einer Überschrift.

#### Scenario: Nicht geschlossene Hervorhebung
- **WHEN** der Text `Das ist **sehr schön` enthält
- **THEN** meldet die Prüfung einen Fehler an dieser Stelle

#### Scenario: Nicht geschlossener Codeblock
- **WHEN** ein Codeblock mit ``` beginnt und nie endet
- **THEN** meldet die Prüfung einen Fehler an der öffnenden Zeile

#### Scenario: Übersprungene Überschrift
- **WHEN** auf eine Überschrift der Ebene 2 direkt eine Überschrift der Ebene 4 folgt
- **THEN** meldet die Prüfung eine Warnung bei der Überschrift der Ebene 4

#### Scenario: Tabellenzeile mit falscher Spaltenzahl
- **WHEN** eine Tabelle drei Spalten hat und eine Zeile nur zwei Zellen
- **THEN** meldet die Prüfung einen Fehler in dieser Zeile

### Requirement: Markierung und Befundliste
Die App SHALL Befunde im Editor an der betroffenen Stelle markieren (Fehler und Warnungen unterscheidbar) und in einer Liste mit Zeilennummer und Beschreibung anzeigen. Ein Tipp auf einen Eintrag der Liste SHALL den Cursor an die Stelle setzen. Die Prüfung SHALL während der Eingabe nach kurzer Pause laufen und die Eingabe nicht blockieren.

#### Scenario: Sprung zum Fehler
- **WHEN** der Nutzer in der Liste einen Fehler in Zeile 12 antippt
- **THEN** springt der Editor zu Zeile 12 und setzt den Cursor an die markierte Stelle

#### Scenario: Behebung
- **WHEN** der Nutzer das fehlende `**` ergänzt
- **THEN** verschwindet der Befund nach kurzer Pause aus Markierung und Liste

### Requirement: Hinweis beim Speichern und Veröffentlichen
Die App SHALL vor dem Speichern in den Entwurf und vor „Veröffentlichen“ auf offene Fehler hinweisen. Der Nutzer SHALL trotzdem fortfahren können; Warnungen erzeugen keine Abfrage.

#### Scenario: Veröffentlichen mit Fehler
- **WHEN** der Entwurf noch einen Syntaxfehler enthält und der Nutzer „Veröffentlichen“ wählt
- **THEN** nennt die App die Zahl der Fehler und lässt den Nutzer zurückgehen oder dennoch veröffentlichen
