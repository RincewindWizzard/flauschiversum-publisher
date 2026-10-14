# Proposal

## Why

Das Aktualisieren der Beitragsliste liegt heute auf einem Symbol in der Kopfleiste. In Firefox und fast allen anderen Apps zieht man dafür die Liste nach unten. Die Wischgeste ist vertraut, braucht keinen Platz in der Kopfleiste und ist mit dem Daumen gut erreichbar.

## What Changes

- Die Übersicht aktualisiert, wenn der Nutzer die Liste am oberen Rand nach unten zieht (Pull-to-Refresh) und loslässt; währenddessen zeigt ein Ladekreis, dass die App holt.
- Das Aktualisieren-Symbol in der Kopfleiste entfällt (die Geste ersetzt es).
- Für TalkBack und Tastatur bleibt die Aktion „Aktualisieren“ als Aktion der Liste erreichbar, weil eine Wischgeste allein nicht barrierefrei ist.
- Der Ladekreis bleibt mindestens kurz sichtbar, auch wenn das Holen sehr schnell geht, damit die Geste eine spürbare Rückmeldung hat; Fehler (z. B. kein Netz) erscheinen wie bisher als Hinweis in der Liste.
- Die Geste wird mit einem UI-Test abgesichert (bisher ist sie eingebaut, aber nie getestet worden).

## Capabilities

### New Capabilities
- `uebersicht-aktualisieren`: Aktualisieren der Beitragsliste per Wischgeste mit Rückmeldung und barrierefreier Alternative.

### Modified Capabilities
<!-- keine: `beitrag-uebersicht` ist noch nicht archiviert; dort erwähnt die Anforderung „Aktualisieren und Offline“ bereits das Aktualisieren per Wischen und bleibt unverändert -->

## Impact

- `ui/home/HomeScreen.kt`: Geste und Kopfleiste (Symbol entfernen), Aktion für Bedienungshilfen.
- `ui/home/HomeViewModel.kt`: Zustand „wird aktualisiert“ mit Mindestanzeigedauer.
- Tests: `OverviewUiTest`, `HomeEditorUiTest`, `AccessibilityUiTest` (Tag `refresh` entfällt, Wischgeste statt Klick).
- Keine neuen Abhängigkeiten.
