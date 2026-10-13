# Proposal

## Why

Neben den Funktionen braucht die App einen Rahmen, der sie im Alltag tragbar macht: ein Aussehen, das zum Blog passt und für alle lesbar ist, ein reproduzierbarer Weg, signierte Versionen zu bauen und zu installieren, Hilfe bei SSH-/Git-Problemen unterwegs, Schutz des SSH-Schlüssels auf dem Gerät und klare Regeln zum Datenschutz.

## What Changes

- Erscheinungsbild im Farbschema „Flauschi-Nebel“ (Hell/Dunkel), Schriften Lobster und Lora, Maskottchen als App-Symbol; Barrierefreiheit (Touchflächen, Schriftgröße, TalkBack).
- Automatischer Build und Test bei jedem Push, signierter Release-Build (APK) bei Versions-Tags; Anzeige der Version in der App.
- Diagnoseprotokoll für Git-/SSH-Vorgänge ohne Geheimnisse, einsehbar und teilbar.
- Optionale App-Sperre per Fingerabdruck/Gesicht/Geräte-PIN.
- Datenschutz: keine Telemetrie, Netzwerkzugriff nur zum eingestellten Git-Server, minimale Berechtigungen.

## Capabilities

### New Capabilities
- `app-erscheinungsbild`: Farben, Schriften, Symbol, Dunkelmodus und Barrierefreiheit der App.
- `app-verteilung`: Build, Test und signierte Releases mit Versionierung und Installation per APK.
- `app-diagnose`: Lokales Diagnoseprotokoll und Absturzbericht, teilbar auf Wunsch.
- `app-sperre`: Optionale Sperre der App mit Geräte-Authentifizierung.
- `app-datenschutz`: Grundsätze zu Telemetrie, Netzwerk und Berechtigungen.

### Modified Capabilities

(keine in `openspec/specs/`)

## Impact

- Neuer Code und Ressourcen in `app/` (Theme, Schriften, Symbole, Protokoll, Sperre), neue Datei `.github/workflows/` im Repository `flauschiversum-publisher`.
- Abhängigkeiten: Biometrie-Bibliothek (AndroidX Biometric); Schriften Lobster/Lora (SIL OFL).
- Geheimnisse für das Signieren liegen nur in den Repository-Secrets, nie im Code.
- Das Repository `flauschiversum` bleibt unverändert (Farben und Maskottchen werden nur gelesen).
