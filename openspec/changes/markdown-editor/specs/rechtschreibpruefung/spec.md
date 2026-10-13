# Spec Delta

## Purpose

Beschreibt die deutsche Rechtschreibprüfung im Editor: Erkennung unbekannter Wörter, Korrekturvorschläge und ein eigenes Wörterbuch, ohne dass Texte das Handy verlassen.

## ADDED Requirements

### Requirement: Offline-Prüfung auf Deutsch
Die App SHALL den Beitragstext mit einem eingebauten deutschen Wörterbuch prüfen, ohne Netzzugriff und ohne den Text an Dritte zu senden. Unbekannte Wörter SHALL im Editor unterstrichen werden.

#### Scenario: Tippfehler
- **WHEN** der Nutzer „Wolle ist wundershön“ schreibt
- **THEN** wird „wundershön“ unterstrichen und „Wolle“ nicht

#### Scenario: Ohne Netz
- **WHEN** das Handy im Flugmodus ist
- **THEN** funktioniert die Prüfung unverändert

#### Scenario: Zusammengesetzte Wörter
- **WHEN** der Nutzer „Strickanleitung“ oder „Häkelmütze“ schreibt
- **THEN** werden sie als richtig erkannt, wenn die Zusammensetzung nach den Regeln des Wörterbuchs gültig ist

### Requirement: Nur Fließtext prüfen
Die Prüfung SHALL nur lesbaren Text prüfen. Nicht geprüft werden Links (Ziel und URL), Bilddateinamen, Code, Codeblöcke, eingebettetes HTML und Markdown-Syntaxzeichen.

#### Scenario: Dateiname
- **WHEN** der Text `![Mütze](IMG_20170617_122458.jpg)` enthält
- **THEN** wird der Dateiname nicht unterstrichen, der Alt-Text „Mütze“ aber geprüft

#### Scenario: URL
- **WHEN** der Text `[Shop](https://beispiel.de/wolle-kaufen)` enthält
- **THEN** wird die URL nicht geprüft

### Requirement: Korrekturvorschläge
Beim Antippen eines unterstrichenen Wortes SHALL die App bis zu fünf Korrekturvorschläge anbieten. Ein Tipp auf einen Vorschlag ersetzt das Wort im Text. Die Groß-/Kleinschreibung des Originals (Satzanfang, Substantiv) SHALL berücksichtigt werden.

#### Scenario: Vorschlag übernehmen
- **WHEN** der Nutzer bei „wundershön“ den Vorschlag „wunderschön“ antippt
- **THEN** steht im Text „wunderschön“

#### Scenario: Kein Vorschlag
- **WHEN** zu einem Wort keine Vorschläge existieren
- **THEN** zeigt die App „Keine Vorschläge“ und weiterhin die anderen Aktionen

### Requirement: Persönliches Wörterbuch
Die App SHALL Wörter zum persönlichen Wörterbuch hinzufügen („Zum Wörterbuch“) und wieder entfernen lassen. Aufgenommene Wörter werden nicht mehr unterstrichen, bleiben nach App-Neustart erhalten und gelten in allen Beiträgen. „Ignorieren“ gilt nur für die aktuelle Sitzung.

#### Scenario: Wort aufnehmen
- **WHEN** der Nutzer bei „Amigurumi“ „Zum Wörterbuch“ wählt
- **THEN** wird „Amigurumi“ nicht mehr unterstrichen, auch nicht nach einem App-Neustart

#### Scenario: Wort entfernen
- **WHEN** der Nutzer „Amigurumi“ in den Einstellungen aus dem Wörterbuch löscht
- **THEN** wird es wieder unterstrichen

### Requirement: Vorbelegung mit Fachwörtern
Das persönliche Wörterbuch SHALL beim ersten Start mit den Schlagworten aus `content/tags.yaml` des Klons vorbelegt werden, damit Begriffe wie „Amigurumi“ nicht als Fehler gelten.

#### Scenario: Schlagwort als bekannt
- **WHEN** `content/tags.yaml` das Schlagwort „Amigurumi“ enthält
- **THEN** wird „Amigurumi“ im Beitragstext nicht unterstrichen
