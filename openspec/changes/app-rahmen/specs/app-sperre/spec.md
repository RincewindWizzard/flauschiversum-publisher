# Spec Delta

## Purpose

Beschreibt die optionale Sperre der App, damit auf einem entsperrten oder fremden Handy nicht unbeabsichtigt veröffentlicht werden kann.

## ADDED Requirements

### Requirement: Optionale App-Sperre
Die App SHALL in den Einstellungen eine Sperre anbieten (Vorgabe aus). Ist sie an, SHALL die App beim Öffnen die Geräte-Authentifizierung verlangen (Fingerabdruck, Gesicht oder Geräte-PIN/Muster) und bis dahin keine Inhalte zeigen. Ist auf dem Gerät keine Bildschirmsperre eingerichtet, lässt sich die Sperre nicht einschalten und die App erklärt warum.

#### Scenario: Sperre einschalten
- **WHEN** der Nutzer die Sperre einschaltet
- **THEN** verlangt die App einmal die Authentifizierung zur Bestätigung

#### Scenario: Kein Bildschirmsperre
- **WHEN** das Gerät keine Bildschirmsperre hat
- **THEN** bleibt der Schalter aus und die App erklärt, dass eine Gerätesperre nötig ist

### Requirement: Sperrzeit
Die App SHALL sich nach dem Verlassen nach einer wählbaren Zeit (sofort, 1 Minute, 5 Minuten; Vorgabe 1 Minute) wieder sperren. Die Inhalte SHALL in der Übersicht der zuletzt benutzten Apps nicht sichtbar sein, solange die Sperre an ist.

#### Scenario: Nach einer Minute
- **WHEN** der Nutzer die App verlässt und nach zwei Minuten zurückkehrt
- **THEN** verlangt die App die Authentifizierung

#### Scenario: Schnell zurück
- **WHEN** der Nutzer nach 20 Sekunden zurückkehrt
- **THEN** zeigt die App sich ohne erneute Abfrage

#### Scenario: Zuletzt benutzte Apps
- **WHEN** die Sperre an ist und der Nutzer die Übersicht der zuletzt benutzten Apps öffnet
- **THEN** zeigt die Vorschau der App keinen Inhalt

### Requirement: Teilen und Hintergrundaufträge
Kommt die App über „Teilen“, SHALL sie zuerst authentifizieren und danach den Vorgang fortsetzen. Bereits gespeicherte Hintergrundaufträge laufen auch bei gesperrter App weiter.

#### Scenario: Teilen bei Sperre
- **WHEN** der Nutzer bei aktiver Sperre ein Foto teilt
- **THEN** verlangt die App die Authentifizierung und zeigt danach die Zielbeitrag-Auswahl

#### Scenario: Auftrag im Hintergrund
- **WHEN** die App gesperrt ist und ein gespeicherter Auftrag fällig wird
- **THEN** läuft der Auftrag ohne Abfrage weiter

### Requirement: Abbruch und Ausweichen
Schlägt die Authentifizierung wiederholt fehl, SHALL die App geschlossen bleiben und nur die Geräte-Authentifizierung als Weg anbieten. Es gibt keine App-eigene Passwortumgehung.

#### Scenario: Mehrere Fehlversuche
- **WHEN** die Authentifizierung mehrfach scheitert
- **THEN** zeigt die App keine Inhalte und bietet nur erneute Geräte-Authentifizierung an
