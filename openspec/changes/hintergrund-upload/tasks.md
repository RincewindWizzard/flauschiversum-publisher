# Tasks

## 1. Sichern und Auftrag

- [ ] 1.1 Auftragsmodell und dauerhafte Ablage (Beitrag, Dateien, Alt-Texte, Status, Zeitpunkte); Unit-Tests für Speichern, Laden und Statusübergänge
- [ ] 1.2 Teilen-Eingang: Dateien sofort in den App-Speicher kopieren, Auftrag speichern, zur vorigen App zurückkehren; Instrumented-Test mit simuliertem Teilen: Fotos liegen im App-Speicher, Activity endet ohne Wartezeit

## 2. Ausführung

- [ ] 2.1 WorkManager-Auftrag: Verkleinern, Rechteangaben, Einbinden unter „Anhänge“, lokaler Commit auf `draft` (nutzt Funktionen aus `foto-teilen`); Test mit Testrepository: Commit entsteht ohne Netz
- [ ] 2.2 Push als eigener Schritt mit Netzbedingung und Backoff; Unterscheidung dauerhafter und vorübergehender Fehler; Tests: Funkloch wird wiederholt, Zugriff verweigert nicht
- [ ] 2.3 Gemeinsame Git-Warteschlange: Aufträge nacheinander, Editor-Commit überholt laufenden Push; Test mit zwei parallelen Aufträgen und einem Speichern
- [ ] 2.4 Aufräumen: gesicherte Originale nach Commit löschen; Test: Speicher wird frei

## 3. Anzeige und Einstellungen

- [ ] 3.1 Benachrichtigungen (laufend mit Fortschritt ab zwei Sekunden, Ergebnis) und lazy erbetene Berechtigung; Instrumented-Test, App läuft auch ohne Berechtigung
- [ ] 3.2 Ausgangsbox mit Status, Wiederholen, Abbrechen (mit Bestätigung), Aufräumen nach sieben Tagen; UI-Tests
- [ ] 3.3 Einstellung „Nur über WLAN hochladen“ mit „Jetzt senden“; Test mit simuliertem Mobilfunk
- [ ] 3.4 Hinweis zur Akku-Optimierung in den Einstellungen; manuell auf Gerät geprüft

## 4. Integration und Dokumentation

- [ ] 4.1 End-to-End im Emulator: Teilen, App schließen, Flugmodus an/aus, Foto erscheint auf `draft` am Server; Ablauf protokolliert
- [ ] 4.2 `README.md` um Hintergrund-Upload und Ausgangsbox ergänzen; Schritte funktionieren wie beschrieben

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (Teilen, Git-Warteschlange, Entwurfsweg).
- Change nach Review archivieren (`/opsx:archive`).
