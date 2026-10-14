# Tasks

## 1. Beitragsindex

- [x] 1.1 Index-Modell und Einlesen aller `content/posts/*/index.md` (Titel, Datum, Schlagworte, Autor, `image`, Kurzbeschreibung, Text); Unit-Test mit Stichprobe aus dem Blog: Anzahl, Datum mit und ohne Uhrzeit, fehlende Felder — `PostIndexTest`
- [x] 1.2 Beiträge mit ungültigem oder fehlendem Frontmatter trotzdem aufnehmen (Verzeichnisname als Titel, Warnhinweis); Unit-Test — `PostIndexTest.kaputtesFrontmatter…`
- [x] 1.3 Index auf Platte zwischenspeichern, mit Commit-Hash versehen und nach Fetch nur geänderte Beiträge neu lesen; Test mit lokalem Testrepository: zweiter Start liest nichts neu, geänderter Beitrag wird aktualisiert — `PostIndexTest.zweiterStart…` (Zwischenspeicher je Beitragsverzeichnis, TSV-Datei)
- [x] 1.4 Titelbild-Ermittlung (`image` → erstes Bild → Platzhalter); Unit-Tests für die drei Fälle der Spec — `PostIndexTest.titelbildErmittlung`
- [x] 1.5 Entwürfe über JGit aus dem Unterschied zwischen `draft` und Zielbranch ableiten („Neu“/„Geändert“) und aus `draft` lesen, ohne Arbeitsbaum zu wechseln; Test: Entwurf erscheint im Index, Arbeitsbaum unverändert — `PostIndexTest.entwurfErscheintImIndex…` (bare Klon, kein Arbeitsbaum)

## 2. Suche, Filter, Sortierung

- [x] 2.1 Suchfunktion über Titel, Kurzbeschreibung, Schlagworte und Text (Groß-/Kleinschreibung ignoriert, mehrere Wörter = UND); Unit-Tests je Szenario der Spec — `PostSearchTest`
- [x] 2.2 Filter nach Schlagworten (alle gewählten) und Jahr, kombinierbar mit Suche; Unit-Test inkl. Entfernen eines einzelnen Filters — `PostSearchTest`
- [x] 2.3 Sortierung (neueste, älteste, Titel A–Z mit deutschem Collator); Unit-Test: „Ärmel“ vor „Berta“ — `PostSearchTest.sortierung`
- [x] 2.4 Messung: Suche über 200+ Beiträge liefert Ergebnisse in unter einer Sekunde (Test mit synthetischem Bestand von 1000 Beiträgen) — `PostSearchTest.sucheUeberTausendBeitraege…` (JVM)

## 3. Oberfläche der Übersicht

- [x] 3.1 Übersichtsbildschirm als Startbildschirm: Liste (Lazy), Eintrag mit Vorschau, Titel, Datum, Schlagworten; Hinweis bei fehlendem Klon; Compose-UI-Test für Eintrag und Hinweis — `OverviewUiTest`
- [x] 3.2 Vorschaubilder mit Downsampling und Cache (≈200 px); Test: Dekodieren eines großen Beispielbildes bleibt unter einer Speichergrenze, zweiter Aufruf kommt aus dem Cache — `ThumbnailsTest` (Robolectric, native Grafik)
- [x] 3.3 Abschnitt „Entwürfe“ mit Kennzeichnung und Upload-Status; kein doppelter Eintrag für Beiträge mit Entwurf; UI-Tests der drei Szenarien — `OverviewUiTest`, `HomeEditorUiTest`
- [x] 3.4 Suchfeld, Filter-Chips (Schlagwort, Jahr), Sortiermenü, aktive Filter sichtbar und einzeln entfernbar, „Keine Beiträge gefunden“ mit Zurücksetzen; UI-Tests; Zustand übersteht Drehung und Prozessende — `OverviewUiTest`; Zustandswiederherstellung per `StateRestorationTester` (kein echtes Prozessende)
- [x] 3.5 Übersicht baut sich nach jeder Hintergrundaktualisierung selbst neu auf (ohne Sprung der Scrollposition), zusätzlich Aktualisieren per Wischen, „zuletzt aktualisiert“, Meldung „Kein Netz“; Tests mit Testrepository und simuliertem Offline — Neuaufbau nach Aktualisierung und „Kein Netz“ getestet (`OverviewUiTest`, `HomeEditorUiTest`); Aktualisieren per Wischen ist eingebaut (`PullToRefreshBox`), die Geste selbst ist nicht getestet; Scrollposition nicht auf einem Gerät geprüft

## 4. Aktionen und Auswahlmodus

- [x] 4.1 Tipp öffnet Editor; Menü am Eintrag mit Bearbeiten, Eigenschaften, Foto hinzufügen, bei Entwürfen Verwerfen (Veröffentlichen gemeinsam im Abschnitt „Entwürfe“); UI-Test je Aktion ruft den richtigen Ablauf auf — `OverviewUiTest.menueAktionen…`
- [ ] 4.2 „Foto hinzufügen“ über die Fotoauswahl des Systems, Übergabe an den Ablauf von `foto-teilen`; Instrumented-Test mit simulierter Auswahl — „Foto hinzufügen“ startet die Fotoauswahl des Systems und übergibt an die Zuordnung (`IncomingPhotos.receiveUris`, Ziel vorgewählt); der geforderte Instrumented-Test fehlt, da kein Gerät/Emulator zur Verfügung steht
- [x] 4.3 Auswahlmodus für geteilte Fotos (Foto-Vorschau, Ziel antippen, „Neuer Beitrag“, Abbrechen); UI-Tests: Ziel wählen, Suche im Modus, Abbrechen ändert nichts — `ShareUiTest` (Ziel wählen, Suche, Abbrechen; Foto-Vorschau eingebaut)
- [x] 4.4 Schaltfläche „Neuer Beitrag“ öffnet leeren Editor mit Vorgaben; UI-Test — `OverviewUiTest.neuerBeitragSchaltflaeche`

## 5. Integration und Dokumentation

- [ ] 5.1 End-to-End im Emulator mit einer Kopie des Blog-Repositorys: Klonen, Index aufbauen, suchen, filtern, Beitrag bearbeiten, Foto hinzufügen, Entwurf veröffentlichen; Ablauf nachvollziehbar protokolliert; Scrollen flüssig bei 194 Beiträgen — Emulator fehlt; Scrollen flüssig bei 194 Beiträgen nicht gemessen (nur Lazy-Liste und 200-px-Vorschauen)
- [x] 5.2 `README.md` um Übersicht, Suche, Filter und Entwürfe ergänzen; beschriebene Schritte funktionieren wie dokumentiert — `README.md` ergänzt

## Workflow follow-up

- Abhängigkeit beachten: Umsetzung nach den Kern-Aufgaben aus `publisher-grundfunktionen` (Klon, Entwurfs-Branches) und `markdown-editor` (Editor, Eigenschaften).
- Change nach Review archivieren (`/opsx:archive`).
