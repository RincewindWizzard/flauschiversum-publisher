# Tasks

## 1. Zustand und Geste

- [ ] 1.1 `HomeViewModel`: Zustand „wird aktualisiert“ ab Anforderung bis Ende des Syncs, mindestens 500 ms sichtbar; läuft schon ein Sync, kein zweiter Start, Zustand bis zu dessen Ende; Unit-Test mit gesteuerter Uhr
- [ ] 1.2 `HomeScreen`: `PullToRefreshBox` an den neuen Zustand binden; Aktualisieren-Symbol aus der Kopfleiste entfernen
- [ ] 1.3 Aktion „Aktualisieren“ als TalkBack-Aktion der Liste; Test: Aktion vorhanden und löst die Aktualisierung aus

## 2. Tests der Geste

- [ ] 2.1 UI-Test mit `swipeDown()` am oberen Rand: Aktualisierung startet, neuer Beitrag vom Server erscheint
- [ ] 2.2 UI-Tests: weiter unten in der Liste kein Auslösen; kurze Liste und „Keine Treffer“ lassen sich herunterziehen; Ladekreis bleibt bei schnellem Holen kurz sichtbar; kein Netz zeigt den Hinweis und lässt die Liste unverändert
- [ ] 2.3 Bestehende Tests umstellen (Klick auf Tag `refresh` → Geste bzw. Aktion), Zugänglichkeitstest bleibt grün

## 3. Abschluss

- [ ] 3.1 Falls die Geste unter Robolectric nicht auslöst: Ursache im Code beheben (z. B. Nested-Scroll der Liste) und mit dem Test festhalten
- [ ] 3.2 `README.md` (Übersicht): Aktualisieren per Nach-unten-Ziehen statt Knopf
- [ ] 3.3 Prüfung auf einem Gerät: Gefühl und Auslöseschwelle der Geste (ohne Gerät als offen vermerken)

## Workflow follow-up

- Abhängigkeit beachten: baut auf `beitrag-uebersicht` auf (Übersicht, `SyncService`).
- Change nach Review archivieren (`/opsx:archive`).
