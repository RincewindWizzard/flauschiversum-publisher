# Proposal

## Why

In der Beitragsliste steht neben dem Titel ein Rahmen mit dem Anfangsbuchstaben des Titels. Das Beitragsbild hilft beim Wiederfinden deutlich mehr: Beiträge des Blogs erkennt man an ihrem Foto schneller als am Titel. Der Code lädt ein Titelbild zwar schon (Feld `image`, sonst erstes Bild), im Alltag sieht der Nutzer aber trotzdem nur Buchstaben; dieses Verhalten ist nirgends als Anforderung festgehalten und nicht gegen echte Bilder abgesichert.

## What Changes

- Neben dem Titel jedes Listeneintrags (Beiträge, Entwürfe, Suchtreffer) erscheint das Beitragsbild als größere, quadratisch zugeschnittene Vorschau statt des Rahmens mit dem Buchstaben.
- Das Bild ist das Feld `image`, wenn die Datei existiert, sonst das erste Bild des Beitrags; nur ohne jedes Bild erscheint ein neutrales Bildsymbol (kein Buchstabe mehr).
- Ein Bild, das nicht geladen werden kann, fällt auf das Bildsymbol zurück und wird beim nächsten Aufbau der Liste erneut versucht, statt dauerhaft als „kein Bild“ zwischengespeichert zu werden.
- Die Ursache, warum im Alltag nur Buchstaben erscheinen, wird mit echten Blog-Bildern (Binärdaten im Testrepository) nachgestellt und behoben; ein Test sichert den ganzen Weg vom Repository bis zum Bild ab.
- Die Vorschau lädt weiterhin klein (kein Originalbild im Speicher) und blockiert das Scrollen nicht.

## Capabilities

### New Capabilities
- `beitragsliste-vorschaubild`: Beitragsbild als Vorschau neben dem Titel in der Beitragsliste, Auswahlregel, Ersatzdarstellung und Verhalten bei Ladefehlern.

### Modified Capabilities
<!-- keine: `beitrag-uebersicht` ist noch nicht archiviert; die Regel zum Titelbild dort bleibt unverändert und wird hier präzisiert -->

## Impact

- Oberfläche: Listeneintrag in `ui/home/HomeScreen.kt` (Vorschau-Element, Größe, Ersatzsymbol).
- Bildladen: `overview/Thumbnails.kt` (Zielgröße, Behandlung von Fehlschlägen), `overview/PostIndex.kt` (Titelbild-Ermittlung).
- Tests: neuer Test mit Binärbildern im Testrepository; vorhandene Übersichts-Tests bleiben gültig (Platzhalter-Tests werden auf das Symbol angepasst).
- Keine neuen Abhängigkeiten, keine Berechtigungen.
