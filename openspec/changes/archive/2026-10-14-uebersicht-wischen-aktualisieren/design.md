# Design

## Context

`HomeScreen` umschließt die `LazyColumn` schon mit `PullToRefreshBox` (Material 3; `isRefreshing = ui.syncing`, `onRefresh = vm::refreshNow`), und die Kopfleiste hat zusätzlich ein Symbol „Aktualisieren“ (Tag `refresh`). `refreshNow` fordert `SyncReason.MANUAL` an; der `SyncService` führt höchstens einen Lauf gleichzeitig aus und ignoriert weitere Anforderungen. Die Geste wurde bisher nie getestet (Tests klicken das Symbol). Unbelegt ist, ob sie auf Geräten zuverlässig auslöst; mögliche Stolperstellen: `syncing` wird bei schnellem Holen kaum sichtbar, und bei kurzer Liste hängt das Auslösen vom Nested-Scroll der Liste ab.

## Goals / Non-Goals

**Goals:**
- Wischgeste als einziger sichtbarer Weg zum Aktualisieren, mit klarer Rückmeldung.
- Barrierefreier Ersatz über Aktionen der Liste.
- Test, der die Geste wirklich ausführt.

**Non-Goals:**
- Kein Pull-to-Refresh in anderen Bildschirmen (Ausgang, Fotos); dort gibt es nichts vom Server zu holen.
- Keine Änderung am `SyncService` (Einzellauf, Mindestabstand für automatische Läufe bleiben).
- Keine eigene Geste statt der Material-Komponente.

## Decisions

**Material-3-`PullToRefreshBox` beibehalten, Kopfleistensymbol entfernen.** Die Komponente liefert Verhalten, Animation und Anzeige wie in anderen Apps. Alternative: eigene Geste – unnötig. Angenommen: Das Symbol in der Kopfleiste entfällt vollständig (Wunsch: „soll mit einer Wischgeste passieren“); der Platz hilft außerdem dem Titel.

**Zustand „wird aktualisiert“ im ViewModel mit Mindestdauer.** `HomeUi.refreshing` ist wahr ab dem Ziehen bis zum Ende des Syncs, mindestens 500 ms (Verzögerung per `delay` nach dem Ende, kein Dauerlauf). `isRefreshing` der Komponente liest diesen Wert statt `syncing`, damit der Ladekreis nicht sofort verschwindet. Hintergrund-Syncs (Start, Vordergrund) zeigen weiter nur den schmalen Fortschrittsbalken, nicht den Ladekreis.

**Läuft schon ein Sync: nicht neu starten, Ladekreis an die laufende hängen.** `request(MANUAL)` gibt `false` zurück, wenn schon einer läuft; dann bleibt `refreshing` bis zum Ende dieses Laufs wahr.

**Barrierefreie Aktion über `semantics { customActions }` an der Liste.** Eine benannte Aktion „Aktualisieren“ ruft dieselbe Funktion auf wie die Geste. Alternative: verstecktes Symbol – widerspricht dem Wunsch, die Kopfleiste zu entlasten.

**Test mit echter Wischgeste.** `performTouchInput { swipeDown() }` am Listen-Tag; geprüft werden Auslösen am oberen Rand, kein Auslösen weiter unten, kurze Liste, Mindestanzeige (mit gesteuerter Uhr) und die Aktion für Bedienungshilfen. Zeigt der Test, dass die Geste unter Robolectric nicht auslöst, wird die Ursache am Code behoben (z. B. Nested-Scroll), nicht der Test aufgeweicht.

## Risks / Trade-offs

- [Geste ist ohne Gerät nur in Robolectric prüfbar] → Test auf der JVM belegt Auslösen und Rückmeldung; Gefühl und Schwellwert auf einem echten Handy bleiben als offen vermerkt.
- [Nutzer, die das Symbol gewohnt waren, finden keinen Knopf mehr] → Aktion für Bedienungshilfen bleibt; Hinweis im `README.md`.
- [Mindestdauer verlängert die Anzeige künstlich um höchstens 0,5 s] → bewusst, damit die Rückmeldung sichtbar ist; blockiert nichts.

## Open Questions

- Keine, die den Umfang ändern. Entfernen des Kopfleistensymbols ist als Annahme vermerkt und lässt sich per Tasks umkehren.
