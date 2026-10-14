# Spec Delta

## Purpose

Legt fest, welche Daten die App sammelt oder versendet und welche Berechtigungen sie braucht.

## ADDED Requirements

### Requirement: Keine Telemetrie
Die App SHALL keine Nutzungs-, Absturz- oder Standortdaten an Dritte senden und keine Werbe- oder Analysebibliotheken enthalten.

#### Scenario: Netzwerkverkehr
- **WHEN** der Netzwerkverkehr der App beobachtet wird
- **THEN** gehen Verbindungen nur an den in der Repository-Adresse eingestellten Git-Server

### Requirement: Netzwerk nur zum eingestellten Server
Die App SHALL Netzwerkverbindungen ausschließlich per SSH zum in den Einstellungen festgelegten Server aufbauen. Verbindungen aus der Vorschau und aus eingebettetem Beitragsinhalt SHALL blockiert sein.

#### Scenario: Bild aus dem Netz in der Vorschau
- **WHEN** ein Beitrag ein Bild mit einer `https://`-Adresse enthält und die Vorschau geöffnet wird
- **THEN** lädt die App es nicht und zeigt einen Platzhalter

### Requirement: Minimale Berechtigungen
Die App SHALL nur die Berechtigungen anfordern, die sie braucht: Internetzugriff, Netzwerkstatus (für Wiederholung und „Nur über WLAN“), Benachrichtigungen (Android 13+, erst beim ersten Auftrag) sowie unkritische Berechtigungen ohne Abfrage für längere Uploads und die Geräte-Authentifizierung. Zugriff auf Fotos erfolgt ausschließlich über die Auswahl bzw. Teilen-Funktion des Systems, nicht über Speicherberechtigungen.

#### Scenario: Installation
- **WHEN** die Berechtigungen der App aufgelistet werden
- **THEN** enthalten sie keine Speicher-, Standort-, Kontakt- oder Mikrofonberechtigung

### Requirement: Daten bleiben auf dem Gerät
Schlüssel, Einstellungen, Klon, Aufträge und Protokoll SHALL im privaten App-Speicher liegen und nicht in Android-Backups enthalten sein (außer Einstellungen ohne Geheimnisse, falls Backup gewünscht ist).

#### Scenario: Backup
- **WHEN** Android die Daten der App sichert
- **THEN** sind Schlüssel, Klon und Protokoll nicht darin enthalten
