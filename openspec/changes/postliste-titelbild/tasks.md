# Tasks

## 1. Ursache finden

- [x] 1.1 Test mit Binärbildern: Testrepository mit echten JPEG-Dateien (aus Beispielen des Blogs, nur gelesen) bauen und den Weg `PostIndex` → `Thumbnails` prüfen; festhalten, ob und wo das Bild verloren geht (Bildname, Lesen aus dem flachen Klon, Dekodierung großer Fotos, zwischengespeicherte Fehlschläge) — `ThumbnailPipelineTest` (flacher Klon, echte JPEG-Dateien inkl. Originalfoto aus dem Blog): Bildname, Lesen aus dem Klon und Dekodierung funktionieren; auf dem Datenweg wurde kein Fehler gefunden. Gefunden und behoben: Fehlschläge wurden dauerhaft zwischengespeichert
- [x] 1.2 Robolectric-Screenshot der Liste mit diesen Bildern; Ergebnis (Bild oder Buchstabe) im Test festhalten — Screenshot `uebersichtMitBildern` (ScreenshotTest): Bilder erscheinen, nur der Beitrag ohne Bild zeigt den Ersatz; `OverviewUiTest.beitragsbildStattPlatzhalter…`

## 2. Vorschau im Eintrag

- [x] 2.1 Eintrag zeigt das Beitragsbild als quadratische Vorschau mit 72 dp (Beiträge, Entwürfe, Suchtreffer); Ersatzsymbol statt Buchstabe, solange das Bild lädt oder fehlt; Compose-UI-Test für Bild, Entwurf und Beitrag ohne Bild — `OverviewUiTest.beitragsbildStattPlatzhalterNurOhneBildDasSymbol` (Beitrag, Suchtreffer; 72 dp, Bildsymbol statt Buchstabe)
- [x] 2.2 Dekodierziel auf 256 px; nur erfolgreich dekodierte Bilder zwischenspeichern, Fehlschläge nicht; Unit-Tests: großes Foto bleibt klein, Fehlschlag wird beim nächsten Aufruf erneut versucht, zweiter Aufruf kommt aus dem Zwischenspeicher — `ThumbnailsTest` (Größe, Zwischenspeicher, Fehlschlag wird erneut versucht)
- [x] 2.3 Titelbild-Auswahl absichern (Endung in beliebiger Schreibweise, `image` verweist auf fehlende Datei, kein Bild); Unit-Tests je Szenario der Spec — `PostIndexTest.titelbildErmittlung`, `ThumbnailPipelineTest` (Endung `.JPG`/`.PNG`, `image` auf fehlende Datei, kein Bild)
- [x] 2.4 Gefundene Ursache aus 1.1 beheben und mit einem Regressionstest festhalten — Es gab keinen Fehler im Datenweg; die einzige gefundene Schwäche (dauerhaft gemerkter Fehlschlag) ist behoben und mit `ThumbnailsTest.voruebergehendNichtLesbaresBild…` abgesichert

## 3. Abschluss

- [x] 3.1 Bestehende Tests anpassen (Platzhalter-Erwartung Buchstabe → Symbol); Zugänglichkeitstest (Beschriftung, 48 dp) bleibt grün — Platzhalter-Erwartungen angepasst; `AccessibilityUiTest` grün
- [ ] 3.2 Bildschirmfotos der Liste mit echten Bildern; auf einem Gerät prüfen, dass Bilder erscheinen und das Scrollen flüssig bleibt (kein Gerät verfügbar: als offen vermerken) — Bildschirmfotos mit echten Bildern sind erstellt (JVM); die Prüfung auf einem Gerät (Bilder erscheinen, Scrollen flüssig) steht aus
- [x] 3.3 `README.md` Abschnitt „Übersicht“ um die Vorschau ergänzen — `README.md` ergänzt

## Workflow follow-up

- Abhängigkeit beachten: baut auf `beitrag-uebersicht` auf (Index, Vorschaubild-Zwischenspeicher).
- Change nach Review archivieren (`/opsx:archive`).
