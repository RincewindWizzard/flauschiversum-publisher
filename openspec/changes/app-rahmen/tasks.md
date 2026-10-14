# Tasks

## 1. Erscheinungsbild (`app-erscheinungsbild`)

- [x] 1.1 Farbschema aus `theme.css` als Material-3-`ColorScheme` (Hell/Dunkel), Wahl „System/Hell/Dunkel“ in den Einstellungen; Unit-Test der Farbwerte und der Moduswahl — `SettingsAndThemeTest`
- [x] 1.2 Kontrastprüfung aller verwendeten Farbpaare (WCAG AA) als Unit-Test — `SettingsAndThemeTest.kontrastWcagInBeidenModi`, `PreviewRendererTest.kontrasteInHellUndDunkel`
- [ ] 1.3 Schriften Lobster und Lora einbinden (nur benötigte Schnitte), Titel und Editor/Vorschau; Screenshot-Test; einstellbare Editorgröße mit UI-Test — Lobster/Lora sind eingebunden (Titel in Lobster, Editor in Lora), Editorgröße einstellbar (`SettingsAndThemeTest.editorSchriftgroesse…`); der Screenshot-Test fehlt
- [ ] 1.4 Adaptives Symbol mit Maskottchen und monochrome Ebene; Prüfung im Launcher/Emulator — Adaptives Symbol mit Maskottchen und monochromer Ebene ist in den Ressourcen; Prüfung im Launcher/Emulator steht aus
- [x] 1.5 Barrierefreiheit: Mindestgröße 48 dp, Beschriftungen, Schrift bis 200 %, Fehler nicht nur über Farbe, „Animationen entfernen“; Compose-Accessibility-Tests auf den Hauptbildschirmen — `AccessibilityUiTest`: 48 dp, Beschriftungen, Schrift 200 % (Hauptbildschirme); Fehler mit „⚠“ und Text. Nicht geprüft: TalkBack auf einem Gerät und „Animationen entfernen“ (die App hat nur Material-Animationen, die der Systemeinstellung folgen)

## 2. Verteilung (`app-verteilung`)

- [ ] 2.1 Workflow `ci.yml` (Lint, Unit-Tests, `assembleDebug`) und Artefakt; geprüft durch einen grünen und einen absichtlich roten Lauf — `.github/workflows/ci.yml` ist geschrieben und im Ablauf lokal nachvollzogen (`lintDebug`, `testDebugUnitTest`, `assembleDebug` laufen grün); der Lauf auf GitHub (grün und absichtlich rot) fehlt
- [ ] 2.2 Workflow `release.yml` für Tags `v*.*.*` mit Signatur aus Repository-Secrets und Anhängen der APK an ein Release; Test mit Test-Tag, Fehlen des Geheimnisses bricht ab — `.github/workflows/release.yml` ist geschrieben (Tag, Geheimnisprüfung vor dem Bau, `gh release create`), `assembleRelease` läuft lokal unsigniert durch; nicht auf GitHub erprobt
- [x] 2.3 Versionierung aus dem Tag (`versionName`, `versionCode`) und Anzeige „Über“ mit Version, Build-Kennung und Lizenzen; Unit-Test der Berechnung — `DistributionTest`, About mit Version, Build und Lizenzen
- [ ] 2.4 Dokumentation zu Signatur, Sideloading und Update (`README.md`); Update von Version A auf B im Emulator erhält Schlüssel und Klon — Dokumentation im `README.md` steht; das Update von Version A auf B im Emulator fehlt

## 3. Diagnose (`app-diagnose`)

- [x] 3.1 Protokollklasse (rotierend, 1 MB) mit Allowlist-Feldern; Unit-Tests: Größe, Rotation, keine Schlüssel/Texte im Fehlerfall — `DiagnosticLogTest`
- [x] 3.2 Einbindung in Git-/SSH-Schicht und Aufträge; Test: fehlgeschlagener Push erzeugt erwarteten Eintrag — `DiagnosticsUiTest.fehlgeschlagenerPushErzeugtEintrag…` (Sync, Auftrag, Veröffentlichen, Klonen, Zugangsprüfung angebunden)
- [x] 3.3 Anzeige, Teilen (mit App- und Android-Version) und Löschen in den Einstellungen; UI-Tests — `DiagnosticsUiTest.anzeigenTeilenUndLoeschen…`
- [ ] 3.4 Absturz-Handler mit Hinweis beim nächsten Start; Instrumented-Test mit provoziertem Absturz — Absturz-Handler und Hinweis beim nächsten Start sind eingebaut; `DiagnosticLogTest.absturzWirdVermerkt…` prüft die Logik, der Instrumented-Test mit echtem Absturz fehlt

## 4. App-Sperre (`app-sperre`)

- [ ] 4.1 Schalter mit Gerätesperre-Prüfung und Bestätigung über `BiometricPrompt`; Instrumented-Test mit simulierter Authentifizierung — Schalter, Gerätesperre-Prüfung und Bestätigung per `BiometricPrompt` (WEAK oder Geräte-Anmeldung) sind eingebaut und mit einem Ersatz-Authentifizierer getestet (`AppLockUiTest`); der echte `BiometricPrompt` ist auf keinem Gerät erprobt
- [x] 4.2 Sperrzeiten (sofort, 1, 5 Minuten), `FLAG_SECURE` bei aktiver Sperre, Sperre bei Rückkehr; Tests für Schnell zurück/nach Ablauf — `AppLockLogicTest`, `AppLockUiTest` (FLAG_SECURE per Robolectric-Activity)
- [x] 4.3 Teilen bei Sperre: Authentifizierung, dann Fortsetzung; Hintergrundaufträge laufen weiter; Tests — `AppLockUiTest` (Teilen bei Sperre, Hintergrundauftrag)

## 5. Datenschutz (`app-datenschutz`)

- [x] 5.1 Manifest nur mit `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`; Unit-/Lint-Test, der das Manifest prüft — `PrivacyTest`
- [x] 5.2 Keine Analyse-SDKs (Abhängigkeitsprüfung) und gesperrte Netzwerkzugriffe der Vorschau (Test mit externem Bild); Backup-Regeln schließen Schlüssel, Klon und Protokoll aus; Tests — `PrivacyTest` (Abhängigkeiten, Vorschau-Blockade, Backup-Regeln)
- [x] 5.3 Abschnitt „Datenschutz“ im `README.md` und in „Über“; Inhalt stimmt mit der Umsetzung überein — `README.md` und „Über“

## Workflow follow-up

- Abhängigkeit beachten: Verteilung (2) kann sofort beginnen; Erscheinungsbild (1) parallel zu den ersten Bildschirmen; Sperre (4) und Datenschutz (5) nach den Funktionen.
- Change nach Review archivieren (`/opsx:archive`).
