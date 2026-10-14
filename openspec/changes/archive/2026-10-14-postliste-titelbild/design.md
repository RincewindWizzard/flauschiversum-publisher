# Design

## Context

Die Liste (`HomeScreen`) zeigt pro Eintrag schon ein `Thumb`-Element: Es lädt über `Thumbnails` (Zwischenspeicher im Arbeitsspeicher, Dekodierung mit `inSampleSize`) das Bild, dessen Dateiname der Index (`PostIndex.cover`: `image`, sonst erstes Bild) liefert. Ohne Bild oder solange es lädt, zeigt es den Anfangsbuchstaben. Das Element ist 56 dp groß; die Zielgröße der Dekodierung ist 200 px. Beobachtet (Bildschirmfotos aus Robolectric): Es erscheinen nur Buchstaben, weil die Testbeiträge keine echten Bilddateien haben; ob echte Blog-Bilder im Alltag erscheinen, ist weder per Test noch auf einem Gerät belegt. Die Tests prüfen bisher nur Teile (Titelbild-Regel mit Dateinamen, Dekodierung mit erzeugten Bitmaps), nicht den Weg Repository → Index → Vorschau mit Binärdaten.

## Goals / Non-Goals

**Goals:**
- Das Beitragsbild ist in jedem Listeneintrag sichtbar und groß genug zum Wiedererkennen.
- Ein Test mit echten Binärbildern im Testrepository sichert den ganzen Weg ab und deckt auf, falls auf dem Weg etwas schiefgeht.
- Ladefehler bleiben nicht dauerhaft hängen.

**Non-Goals:**
- Kein Vorschau-Cache auf Platte (Arbeitsspeicher reicht; die Bilder liegen lokal im Klon).
- Keine Änderung der Auswahlregel für das Titelbild und kein Hochladen oder Erzeugen von Vorschaubildern im Repository.
- Keine Bildgalerie oder Vollbildansicht in der Liste.

## Decisions

**Größe 72 dp, quadratisch mit Zuschnitt (`ContentScale.Crop`), Ecken wie bisher gerundet.** 56 dp erschwert das Erkennen; 72 dp bleibt neben drei Textzeilen und dem Menü gut lesbar. Alternative: Seitenverhältnis erhalten – verworfen, weil unterschiedlich hohe Zeilen die Liste unruhig machen.

**Dekodierziel 256 px statt 200 px.** 72 dp entsprechen auf üblichen Handys etwa 200 bis 290 px; 256 px bleibt scharf und kostet pro Bild rund 130 KB (RGB_565). Der Zwischenspeicher hält bis zu 96 Bilder.

**Fehlschläge nicht dauerhaft merken.** `Thumbnails` speichert bisher auch `null`-Ergebnisse; ein einmal gescheitertes Bild bliebe bis zum Prozessende ein Symbol. Künftig werden nur erfolgreich dekodierte Bilder zwischengespeichert; ein Fehlschlag wird bei jedem neuen Aufbau der Zeile erneut versucht, aber höchstens einmal pro Aufbau (kein Dauerlauf). Alternative: Fehlschlag mit Ablaufzeit merken – unnötig komplex.

**Ersatz ohne Bild: Bildsymbol statt Buchstabe.** Der Buchstabe wirkt wie ein Bild, ist aber keins; ein neutrales Symbol sagt „kein Bild“ und lässt echte Bilder hervorstechen. Angenommen (nicht ausdrücklich bestellt): Symbol `Image` aus den vorhandenen Icons.

**Ursachenforschung vor dem Umbau.** Zuerst wird der Weg mit Binärbildern nachgestellt (Testrepository mit echten JPEG-Dateien, `PostIndex` → `Thumbnails` → Liste) und geprüft, an welcher Stelle das Bild verloren geht: Auswahl des Bildnamens (Groß-/Kleinschreibung der Endung, Dateien im Verzeichnis), Lesen des Blobs aus dem flachen Klon, Dekodierung großer Fotos, Zwischenspeichern von Fehlschlägen. Das Ergebnis steht in den Tasks; die Reparatur gehört in diesen Change.

## Risks / Trade-offs

- [Mehr Arbeitsspeicher durch größere Vorschauen] → 256 px in RGB_565 und ein Limit von 96 Einträgen im Zwischenspeicher halten es klein; Test mit einem großen Foto prüft die Obergrenze.
- [Große Originale (mehrere MB) werden für jede Vorschau komplett aus dem Repository gelesen] → nur sichtbare Zeilen laden (Lazy-Liste), Dekodierung mit `inSampleSize`; bei Bedarf Lesen der Blob-Größe vorab begrenzen.
- [Ohne Gerät nicht vollständig prüfbar] → Test mit Binärbildern und Robolectric-Screenshot belegen den Ablauf auf der JVM; Verhalten und Flüssigkeit auf einem echten Handy bleiben offen und werden als solche gemeldet.

## Open Questions

- Keine, die den Umfang ändern. Das Ersatzsymbol (Annahme oben) lässt sich ohne Spec-Änderung austauschen.
