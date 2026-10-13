# Tasks

## 1. Erscheinungsbild (`app-erscheinungsbild`)

- [ ] 1.1 Farbschema aus `theme.css` als Material-3-`ColorScheme` (Hell/Dunkel), Wahl „System/Hell/Dunkel“ in den Einstellungen; Unit-Test der Farbwerte und der Moduswahl
- [ ] 1.2 Kontrastprüfung aller verwendeten Farbpaare (WCAG AA) als Unit-Test
- [ ] 1.3 Schriften Lobster und Lora einbinden (nur benötigte Schnitte), Titel und Editor/Vorschau; Screenshot-Test; einstellbare Editorgröße mit UI-Test
- [ ] 1.4 Adaptives Symbol mit Maskottchen und monochrome Ebene; Prüfung im Launcher/Emulator
- [ ] 1.5 Barrierefreiheit: Mindestgröße 48 dp, Beschriftungen, Schrift bis 200 %, Fehler nicht nur über Farbe, „Animationen entfernen“; Compose-Accessibility-Tests auf den Hauptbildschirmen

## 2. Verteilung (`app-verteilung`)

- [ ] 2.1 Workflow `ci.yml` (Lint, Unit-Tests, `assembleDebug`) und Artefakt; geprüft durch einen grünen und einen absichtlich roten Lauf
- [ ] 2.2 Workflow `release.yml` für Tags `v*.*.*` mit Signatur aus Repository-Secrets und Anhängen der APK an ein Release; Test mit Test-Tag, Fehlen des Geheimnisses bricht ab
- [ ] 2.3 Versionierung aus dem Tag (`versionName`, `versionCode`) und Anzeige „Über“ mit Version, Build-Kennung und Lizenzen; Unit-Test der Berechnung
- [ ] 2.4 Dokumentation zu Signatur, Sideloading und Update (`README.md`); Update von Version A auf B im Emulator erhält Schlüssel und Klon

## 3. Diagnose (`app-diagnose`)

- [ ] 3.1 Protokollklasse (rotierend, 1 MB) mit Allowlist-Feldern; Unit-Tests: Größe, Rotation, keine Schlüssel/Texte im Fehlerfall
- [ ] 3.2 Einbindung in Git-/SSH-Schicht und Aufträge; Test: fehlgeschlagener Push erzeugt erwarteten Eintrag
- [ ] 3.3 Anzeige, Teilen (mit App- und Android-Version) und Löschen in den Einstellungen; UI-Tests
- [ ] 3.4 Absturz-Handler mit Hinweis beim nächsten Start; Instrumented-Test mit provoziertem Absturz

## 4. App-Sperre (`app-sperre`)

- [ ] 4.1 Schalter mit Gerätesperre-Prüfung und Bestätigung über `BiometricPrompt`; Instrumented-Test mit simulierter Authentifizierung
- [ ] 4.2 Sperrzeiten (sofort, 1, 5 Minuten), `FLAG_SECURE` bei aktiver Sperre, Sperre bei Rückkehr; Tests für Schnell zurück/nach Ablauf
- [ ] 4.3 Teilen bei Sperre: Authentifizierung, dann Fortsetzung; Hintergrundaufträge laufen weiter; Tests

## 5. Datenschutz (`app-datenschutz`)

- [ ] 5.1 Manifest nur mit `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`; Unit-/Lint-Test, der das Manifest prüft
- [ ] 5.2 Keine Analyse-SDKs (Abhängigkeitsprüfung) und gesperrte Netzwerkzugriffe der Vorschau (Test mit externem Bild); Backup-Regeln schließen Schlüssel, Klon und Protokoll aus; Tests
- [ ] 5.3 Abschnitt „Datenschutz“ im `README.md` und in „Über“; Inhalt stimmt mit der Umsetzung überein

## Workflow follow-up

- Abhängigkeit beachten: Verteilung (2) kann sofort beginnen; Erscheinungsbild (1) parallel zu den ersten Bildschirmen; Sperre (4) und Datenschutz (5) nach den Funktionen.
- Change nach Review archivieren (`/opsx:archive`).
