# Design

## Context

`foto-teilen` beschreibt die Schritte vom Teilen bis zum Push, `entwurf-veroeffentlichen` das Pushen der Commits auf `draft`. Beides läuft nach dem bisherigen Plan in der laufenden App. Geteilte Bilder kommen als `content://`-URIs mit zeitlich begrenzter Leseberechtigung (nur solange die empfangende Activity lebt). Die Git-Warteschlange (ein Zugriff gleichzeitig auf den Klon) ist Teil von `publisher-grundfunktionen`. Motivation: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Kein Foto geht verloren, auch nicht bei Funkloch, Prozessende oder Neustart.
- Nutzer wird nicht blockiert; Status ist sichtbar.

**Non-Goals:**
- Synchronisation mehrerer Geräte, Teilen von Videos, Fortsetzen unterbrochener Pushes mittendrin (Git überträgt beim Wiederholen neu).
- Eigener Hintergrunddienst über WorkManager hinaus.

## Decisions

**Zweistufig: sofort sichern (Vordergrund), Rest als WorkManager-Auftrag.** Beim Teilen werden Dateien in den App-Speicher kopiert (kurz, im Vordergrund, solange die Berechtigung der URIs gilt) und ein Auftrag in einer Datenbank/Datei gespeichert. Danach startet ein `CoroutineWorker` (WorkManager) mit Verkleinern, Commit und Push. Alternative: nur Foreground Service – verworfen (Android-Beschränkungen für Start aus dem Hintergrund, Benachrichtigungspflicht dauerhaft). Für längere Übertragungen kann der Worker als beschleunigter Auftrag oder über `setForeground` mit Benachrichtigung laufen.

**Lokaler Commit getrennt vom Push.** Verkleinern und Commit passieren sofort lokal (unabhängig vom Netz); nur der Push hängt am Netz und wird wiederholt. So bleibt `draft` lokal konsistent, und ein Funkloch gefährdet nur die Übertragung.

**Wiederholung mit Backoff und Netzbedingung.** Der Push-Auftrag verlangt „verbundenes Netz“ (optional „ungemessen“ bei „Nur über WLAN“) und nutzt WorkManagers exponentielles Backoff. Dauerhafte Fehler (Authentifizierung, Host-Schlüssel) beenden den Auftrag als fehlgeschlagen.

**Eine gemeinsame Git-Warteschlange.** Alle Git-Aktionen (Aufträge, Speichern im Editor, Hintergrundaktualisierung) laufen über dieselbe Warteschlange; die Netzwerkphase von Push und Fetch hält keine Sperre, nur Referenzänderungen (siehe `publisher-grundfunktionen`). Ein manueller Commit kann deshalb einen laufenden Push überholen.

**Ausgangsbox aus dem Auftragsspeicher abgeleitet**, nicht aus Benachrichtigungen; sie ist die Quelle der Wahrheit, Benachrichtigungen sind nur Hinweise.

## Risks / Trade-offs

- [Speicherbedarf der gesicherten Originale] → Nach erfolgreichem Verkleinern und Commit werden die kopierten Originale gelöscht; nur das verkleinerte Bild bleibt im Klon.
- [Android beendet Hintergrundarbeit aggressiv (Hersteller)] → WorkManager mit Wiederholung; Hinweis auf Akku-Optimierung in den Einstellungen.
- [Zwei gleichzeitige Pushes auf `draft`] → Serialisierung in der Git-Warteschlange; abgewiesener Push wegen neuer Remote-Commits führt zu Fetch und erneutem Versuch (bei Nicht-Fast-Forward Abbruch mit Meldung).
- [Benachrichtigungsberechtigung abgelehnt] → App funktioniert, Status nur in der Ausgangsbox.
