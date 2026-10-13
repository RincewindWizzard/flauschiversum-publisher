# Spec Delta

## Purpose

Beschreibt, wie die App gebaut, getestet und als signierte Version ausgeliefert wird, und wie der Nutzer sie installiert und aktualisiert.

## ADDED Requirements

### Requirement: Automatischer Build und Test
Das Repository SHALL bei jedem Push und jedem Pull Request automatisch Lint, Unit-Tests und den Debug-Build ausführen und bei Fehlern fehlschlagen.

#### Scenario: Push mit Testfehler
- **WHEN** ein Push einen fehlschlagenden Unit-Test enthält
- **THEN** schlägt der Lauf fehl und nennt den Test

#### Scenario: Erfolgreicher Push
- **WHEN** Lint, Tests und Build bestehen
- **THEN** ist der Lauf grün und stellt die Debug-APK als Artefakt bereit

### Requirement: Signierter Release
Bei einem Versions-Tag (`vMAJOR.MINOR.PATCH`) SHALL der Build eine signierte, optimierte Release-APK erzeugen und an eine Veröffentlichung (Release) im Repository anhängen. Das Signaturschlüsselmaterial SHALL nur in den Geheimnissen der Build-Umgebung liegen, nie im Repository.

#### Scenario: Neuer Tag
- **WHEN** der Tag `v1.2.0` gepusht wird
- **THEN** entsteht eine signierte APK mit Version 1.2.0 und hängt am Release `v1.2.0`

#### Scenario: Fehlendes Geheimnis
- **WHEN** das Signaturmaterial in der Build-Umgebung fehlt
- **THEN** schlägt der Release-Lauf mit einer klaren Meldung fehl und veröffentlicht nichts

### Requirement: Versionierung
`versionName` SHALL aus dem Tag stammen, `versionCode` SHALL mit jeder neuen Version steigen (abgeleitet aus MAJOR, MINOR und PATCH). Die App SHALL Version und Build-Kennung in den Einstellungen unter „Über“ anzeigen.

#### Scenario: Versionscode
- **WHEN** die Version 1.2.0 auf 1.2.1 steigt
- **THEN** ist der `versionCode` der neuen Version größer

#### Scenario: Anzeige
- **WHEN** der Nutzer „Über“ öffnet
- **THEN** zeigt die App Version, Build-Kennung und die Lizenzen der verwendeten Bibliotheken

### Requirement: Installieren und Aktualisieren
Die App SHALL sich über die APK installieren und mit einer neueren APK derselben Signatur ohne Datenverlust aktualisieren lassen (SSH-Schlüssel, Einstellungen, Klon und Aufträge bleiben erhalten). Mindestversion ist Android 7.0 (API 24).

#### Scenario: Aktualisierung
- **WHEN** der Nutzer eine neuere signierte APK über die vorhandene installiert
- **THEN** startet die App mit unverändertem Schlüssel, Klon und Einstellungen

#### Scenario: Zu altes Android
- **WHEN** das Gerät Android 6 oder älter hat
- **THEN** lässt sich die APK nicht installieren
