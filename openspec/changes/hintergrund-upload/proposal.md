# Proposal

## Why

Fotos kommen oft aus einer anderen App per „Teilen“, häufig unterwegs mit schwachem Netz. Verarbeiten, Committen und Pushen dürfen nicht davon abhängen, dass der Nutzer die App geöffnet lässt oder dass das Netz gerade gut ist. Ein verlorenes Foto ist ärgerlicher als ein langsamer Upload.

## What Changes

- Beim Teilen kopiert die App die Fotos sofort in ihren Speicher und kehrt zur vorigen App zurück; Verkleinern, Commit und Push laufen als Hintergrundauftrag weiter.
- Aufträge werden dauerhaft gespeichert und bei Netzverlust automatisch wiederholt; sie überstehen Prozessende und Neustart.
- Fortschritt und Ergebnis erscheinen als Benachrichtigung; eine „Ausgangsbox“ zeigt wartende, laufende und fehlgeschlagene Aufträge mit Wiederholen und Abbrechen.
- Einstellung „Nur über WLAN hochladen“.

## Capabilities

### New Capabilities
- `hintergrund-upload`: Zuverlässige Hintergrundverarbeitung und -übertragung von Teilen-Aufträgen und Pushes mit Warteschlange, Wiederholung, Benachrichtigung und Ausgangsbox.

### Modified Capabilities

(keine in `openspec/specs/`; verändert die Ausführung der Abläufe aus `foto-teilen` und „Entwurf pushen“ aus `entwurf-veroeffentlichen`)

## Impact

- Neuer Code in `app/` (Hintergrundaufträge mit WorkManager, Ausgangsbox, Benachrichtigungen); Berechtigung für Benachrichtigungen (Android 13+).
- Aufträge nutzen die gemeinsame Git-Warteschlange aus `publisher-grundfunktionen`.
- Das Repository `flauschiversum` bleibt unverändert.
