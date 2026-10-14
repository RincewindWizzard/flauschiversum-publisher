# Tasks

## 1. Zustand und Geste

- [x] 1.1 `HomeViewModel`: Zustand „wird aktualisiert“ ab Anforderung bis Ende des Syncs, mindestens 500 ms sichtbar; läuft schon ein Sync, kein zweiter Start, Zustand bis zu dessen Ende; Unit-Test mit gesteuerter Uhr — `RefreshGestureTest.ladekreisBleibt…`, `laeuftSchonEinLaufStartetKeinZweiter`
- [x] 1.2 `HomeScreen`: `PullToRefreshBox` an den neuen Zustand binden; Aktualisieren-Symbol aus der Kopfleiste entfernen — Symbol aus der Kopfleiste entfernt, `PullToRefreshBox` am neuen Zustand
- [x] 1.3 Aktion „Aktualisieren“ als TalkBack-Aktion der Liste; Test: Aktion vorhanden und löst die Aktualisierung aus — `RefreshGestureTest.bedienungshilfeBietetDieAktionAktualisierenAn`

## 2. Tests der Geste

- [x] 2.1 UI-Test mit `swipeDown()` am oberen Rand: Aktualisierung startet, neuer Beitrag vom Server erscheint — `RefreshGestureTest.ziehenAmOberenRand…` (echte `swipeDown()`-Geste, neuer Beitrag erscheint)
- [x] 2.2 UI-Tests: weiter unten in der Liste kein Auslösen; kurze Liste und „Keine Treffer“ lassen sich herunterziehen; Ladekreis bleibt bei schnellem Holen kurz sichtbar; kein Netz zeigt den Hinweis und lässt die Liste unverändert — `RefreshGestureTest` (weiter unten, keine Treffer, Mindestanzeige, Kein Netz)
- [x] 2.3 Bestehende Tests umstellen (Klick auf Tag `refresh` → Geste bzw. Aktion), Zugänglichkeitstest bleibt grün — `HomeEditorUiTest` auf die Geste umgestellt, `AccessibilityUiTest` grün

## 3. Abschluss

- [x] 3.1 Falls die Geste unter Robolectric nicht auslöst: Ursache im Code beheben (z. B. Nested-Scroll der Liste) und mit dem Test festhalten — Die Geste löste unter Robolectric ohne Codeänderung aus; keine Ursache zu beheben
- [x] 3.2 `README.md` (Übersicht): Aktualisieren per Nach-unten-Ziehen statt Knopf — `README.md` ergänzt
- [x] 3.3 Prüfung auf einem Gerät: Gefühl und Auslöseschwelle der Geste — vom Nutzer auf dem Gerät bestätigt ("Das hat geklappt")

## Workflow follow-up

- Abhängigkeit beachten: baut auf `beitrag-uebersicht` auf (Übersicht, `SyncService`).
- Change nach Review archivieren (`/opsx:archive`).
