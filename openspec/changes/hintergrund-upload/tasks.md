# Tasks

## 1. Sichern und Auftrag

- [x] 1.1 Auftragsmodell und dauerhafte Ablage (Beitrag, Dateien, Alt-Texte, Status, Zeitpunkte); Unit-Tests für Speichern, Laden und Statusübergänge — `UploadTest.auftragWirdDauerhaft…` (Datei je Auftrag, übersteht Neuladen)
- [ ] 1.2 Teilen-Eingang: Dateien sofort in den App-Speicher kopieren, Auftrag speichern, zur vorigen App zurückkehren; Instrumented-Test mit simuliertem Teilen: Fotos liegen im App-Speicher, Activity endet ohne Wartezeit — Fotos werden beim Teilen sofort in den App-Speicher kopiert (`IncomingPhotos`), „Ablegen“ verschiebt sie in den Auftrag und kehrt sofort zurück (`UploadTest.teilenSichertSofort…`, `ShareUiTest`); der geforderte Instrumented-Test mit echtem Teilen aus einer anderen App fehlt

## 2. Ausführung

- [x] 2.1 WorkManager-Auftrag: Verkleinern, Rechteangaben, Einbinden unter „Anhänge“, lokaler Commit auf `draft` (nutzt Funktionen aus `foto-teilen`); Test mit Testrepository: Commit entsteht ohne Netz — `UploadTest.teilenSichertSofortUndCommitEntstehtOhneNetz`; WorkManager-Anbindung (`CommitWorker`/`PushWorker`) mit `TestListenableWorkerBuilder` geprüft (`OutboxUiTest.workerMelden…`)
- [x] 2.2 Push als eigener Schritt mit Netzbedingung und Backoff; Unterscheidung dauerhafter und vorübergehender Fehler; Tests: Funkloch wird wiederholt, Zugriff verweigert nicht — `UploadTest` (Funkloch wird wiederholt, Zugriff verweigert/abgelehnter Push nicht); Backoff 30 s exponentiell über WorkManager — die tatsächliche Wiederholung durch WorkManager auf einem Gerät ist nicht geprüft
- [x] 2.3 Gemeinsame Git-Warteschlange: Aufträge nacheinander, Editor-Commit überholt laufenden Push; Test mit zwei parallelen Aufträgen und einem Speichern — `UploadTest.zweiAuftraege…`, `speichernImEditorWartetNichtAufDenPush`
- [x] 2.4 Aufräumen: gesicherte Originale nach Commit löschen; Test: Speicher wird frei — `UploadTest` (Originale nach Commit gelöscht)

## 3. Anzeige und Einstellungen

- [ ] 3.1 Benachrichtigungen (laufend mit Fortschritt ab zwei Sekunden, Ergebnis) und lazy erbetene Berechtigung; Instrumented-Test, App läuft auch ohne Berechtigung — Benachrichtigungen (laufend ab zwei Sekunden als Vordergrunddienst, Ergebnis) und lazy erbetene Berechtigung (`POST_NOTIFICATIONS` beim ersten Auftrag) sind eingebaut; ohne Berechtigung läuft alles weiter. Auf einem Gerät nicht geprüft; der Vordergrunddienst-Typ `dataSync` ist im Manifest, aber unerprobt
- [x] 3.2 Ausgangsbox mit Status, Wiederholen, Abbrechen (mit Bestätigung), Aufräumen nach sieben Tagen; UI-Tests — `OutboxUiTest`, `UploadTest.erledigteEintraege…`
- [x] 3.3 Einstellung „Nur über WLAN hochladen“ mit „Jetzt senden“; Test mit simuliertem Mobilfunk — `UploadTest.mobilfunkMitEinstellung…`, `OutboxUiTest.wartenAufWlan…`
- [ ] 3.4 Hinweis zur Akku-Optimierung in den Einstellungen; manuell auf Gerät geprüft — Hinweistext und Schaltfläche „Akku-Einstellungen öffnen“ sind in den Einstellungen; manuelle Prüfung auf einem Gerät steht aus

## 4. Integration und Dokumentation

- [ ] 4.1 End-to-End im Emulator: Teilen, App schließen, Flugmodus an/aus, Foto erscheint auf `draft` am Server; Ablauf protokolliert — Emulator/Gerät fehlt
- [x] 4.2 `README.md` um Hintergrund-Upload und Ausgangsbox ergänzen; Schritte funktionieren wie beschrieben — `README.md` ergänzt

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (Teilen, Git-Warteschlange, Entwurfsweg).
- Change nach Review archivieren (`/opsx:archive`).
