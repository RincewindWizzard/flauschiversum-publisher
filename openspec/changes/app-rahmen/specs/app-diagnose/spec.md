# Spec Delta

## Purpose

Beschreibt das lokale Diagnoseprotokoll, mit dem sich Probleme bei SSH, Git, Aufträgen und Abstürzen unterwegs eingrenzen lassen, ohne Geheimnisse preiszugeben.

## ADDED Requirements

### Requirement: Diagnoseprotokoll
Die App SHALL Vorgänge zu SSH, Git, Hintergrundaufträgen und Fehlern lokal protokollieren (Zeitpunkt, Vorgang, Ergebnis, Fehlerart, Server und Port). Das Protokoll SHALL höchstens 1 MB groß sein und die ältesten Einträge zuerst verwerfen. Es SHALL nie private Schlüssel, Passwörter, Beitragstexte oder Bildinhalte enthalten.

#### Scenario: Fehlgeschlagener Push
- **WHEN** ein Push mit „Zugriff verweigert“ scheitert
- **THEN** enthält das Protokoll Zeit, Vorgang „Push“, Server, Port und „Zugriff verweigert“, aber keinen Schlüssel

#### Scenario: Größe
- **WHEN** das Protokoll 1 MB erreicht
- **THEN** verwirft die App die ältesten Einträge

### Requirement: Protokoll einsehen und teilen
Die App SHALL das Protokoll in den Einstellungen anzeigen und auf Wunsch als Textdatei über die Teilen-Funktion weitergeben (mit App-Version und Android-Version). Das Teilen geschieht nur durch eine Aktion des Nutzers.

#### Scenario: Teilen
- **WHEN** der Nutzer „Protokoll teilen“ wählt
- **THEN** öffnet sich die Teilen-Auswahl mit einer Textdatei, die App- und Android-Version nennt

### Requirement: Absturzbericht
Die App SHALL nach einem Absturz beim nächsten Start einen Hinweis zeigen und den Fehler (Art, Ort im Programm, Version) im Protokoll vermerken. Es werden keine Berichte automatisch versendet.

#### Scenario: Nach einem Absturz
- **WHEN** die App abgestürzt ist und neu gestartet wird
- **THEN** zeigt sie einen Hinweis mit der Möglichkeit, das Protokoll anzusehen oder zu teilen

### Requirement: Protokoll löschen
Der Nutzer SHALL das Protokoll jederzeit löschen können.

#### Scenario: Löschen
- **WHEN** der Nutzer „Protokoll löschen“ bestätigt
- **THEN** ist das Protokoll leer
