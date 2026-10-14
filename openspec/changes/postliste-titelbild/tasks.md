# Tasks

## 1. Ursache finden

- [ ] 1.1 Test mit Binärbildern: Testrepository mit echten JPEG-Dateien (aus Beispielen des Blogs, nur gelesen) bauen und den Weg `PostIndex` → `Thumbnails` prüfen; festhalten, ob und wo das Bild verloren geht (Bildname, Lesen aus dem flachen Klon, Dekodierung großer Fotos, zwischengespeicherte Fehlschläge)
- [ ] 1.2 Robolectric-Screenshot der Liste mit diesen Bildern; Ergebnis (Bild oder Buchstabe) im Test festhalten

## 2. Vorschau im Eintrag

- [ ] 2.1 Eintrag zeigt das Beitragsbild als quadratische Vorschau mit 72 dp (Beiträge, Entwürfe, Suchtreffer); Ersatzsymbol statt Buchstabe, solange das Bild lädt oder fehlt; Compose-UI-Test für Bild, Entwurf und Beitrag ohne Bild
- [ ] 2.2 Dekodierziel auf 256 px; nur erfolgreich dekodierte Bilder zwischenspeichern, Fehlschläge nicht; Unit-Tests: großes Foto bleibt klein, Fehlschlag wird beim nächsten Aufruf erneut versucht, zweiter Aufruf kommt aus dem Zwischenspeicher
- [ ] 2.3 Titelbild-Auswahl absichern (Endung in beliebiger Schreibweise, `image` verweist auf fehlende Datei, kein Bild); Unit-Tests je Szenario der Spec
- [ ] 2.4 Gefundene Ursache aus 1.1 beheben und mit einem Regressionstest festhalten

## 3. Abschluss

- [ ] 3.1 Bestehende Tests anpassen (Platzhalter-Erwartung Buchstabe → Symbol); Zugänglichkeitstest (Beschriftung, 48 dp) bleibt grün
- [ ] 3.2 Bildschirmfotos der Liste mit echten Bildern; auf einem Gerät prüfen, dass Bilder erscheinen und das Scrollen flüssig bleibt (kein Gerät verfügbar: als offen vermerken)
- [ ] 3.3 `README.md` Abschnitt „Übersicht“ um die Vorschau ergänzen

## Workflow follow-up

- Abhängigkeit beachten: baut auf `beitrag-uebersicht` auf (Index, Vorschaubild-Zwischenspeicher).
- Change nach Review archivieren (`/opsx:archive`).
