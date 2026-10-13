# Design

## Context

Die App ist ein Compose-Projekt (Kotlin, Material 3, minSdk 26, targetSdk 37) mit einem Standard-Theme aus der Projektvorlage. Das Repository `flauschiversum-publisher` liegt auf GitHub; für den Build ist dort bisher nichts eingerichtet. Farben und Maskottchen des Blogs liegen im Repository `flauschiversum` (`content/style/theme.css`, Favicon-/Maskottchen-Dateien) und werden nur gelesen. Motivation: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Die App fühlt sich wie der Blog an und ist für alle bedienbar.
- Jede Version ist reproduzierbar gebaut, getestet und signiert.
- Wenn etwas schiefgeht, lässt es sich ohne Rechner eingrenzen.
- Der SSH-Schlüssel auf dem Gerät ist nicht beliebig zugänglich.

**Non-Goals:**
- Veröffentlichung im Play Store oder F-Droid (nur APK über das Repository).
- Automatische App-Updates aus der App heraus.
- Übersetzungen (nur Deutsch) und Tablet-Layouts über das Notwendige hinaus.
- Fernlöschung oder Geräteverwaltung.

## Decisions

**Theme:** Eigenes Material-3-`ColorScheme` aus den Variablen von `theme.css` (Hell/Dunkel), ohne Dynamic Color. Die Werte stehen in einer Datei (`Color.kt`), mit Quellenhinweis; ein Unit-Test prüft die Kontrastpaare nach WCAG. Schriften Lobster und Lora (SIL OFL) werden mitgeliefert, nur die genutzten Schnitte.

**Symbol:** Adaptives Symbol aus dem Maskottchen des Blogs (Vordergrund) und einem Farbverlauf aus dem Schema (Hintergrund); monochrome Ebene für Themed Icons.

**CI mit GitHub Actions:** Workflow `ci.yml` (Lint, Unit-Tests, `assembleDebug`) bei Push und Pull Request; Workflow `release.yml` bei Tags `v*.*.*` mit `assembleRelease`, Signatur aus Repository-Secrets (Keystore Base64, Passwörter, Alias) und Anhängen der APK an ein Release. Versionscode aus Tag: `MAJOR*10000 + MINOR*100 + PATCH`. Annahme: Repository liegt auf GitHub (Stand heute); bei einem anderen Host müsste der Workflow ersetzt werden.

**Diagnose:** Eigene, kleine Protokollklasse (rotierende Datei, 1 MB) statt Fremdbibliothek; Einträge nur aus der Git-/SSH-Schicht, den Aufträgen und dem Absturz-Handler (`Thread.setDefaultUncaughtExceptionHandler`); Filter, der Schlüsselzeilen und Pfade entfernt.

**App-Sperre mit AndroidX Biometric** (`BiometricPrompt` mit Geräte-Anmeldeinformationen als Rückfall). `FLAG_SECURE` verbirgt die Vorschau in der Übersicht der zuletzt benutzten Apps, solange die Sperre an ist. Der Schlüssel selbst wird nicht neu verschlüsselt (Verschlüsselung mit einem Keystore-Schlüssel, der an die Authentifizierung gebunden ist, bleibt als spätere Härtung offen).

**Datenschutz:** Netzwerk ausschließlich über die SSH-Bibliothek zum eingestellten Server; die Vorschau-WebView blockiert alle Anfragen; keine Analyse-SDKs. Manifest nur mit `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`.

## Risks / Trade-offs

- [Signaturschlüssel geht verloren] → Keystore zusätzlich außerhalb von GitHub sichern; Hinweis in der Doku. Ohne Schlüssel sind keine Updates über die bestehende Installation möglich.
- [Sideloading: Nutzer muss „Unbekannte Quellen“ erlauben] → Anleitung in der Doku.
- [Schriften und Maskottchen vergrößern die App] → Schnitte beschränken, Bilder als Vektor.
- [Diagnoseprotokoll enthält doch Sensibles] → Allowlist-Felder statt Filter allein; Test mit Schlüsseltext im Fehlerfall.
- [FLAG_SECURE verhindert Screenshots] → bewusst nur bei aktiver Sperre.

## Open Questions

- **Verteilung:** Reicht die APK über GitHub Releases, oder soll später F-Droid/Play Store folgen? Annahme: APK über GitHub Releases. Ändert Specs und Aufgaben dieses Changes nicht.
