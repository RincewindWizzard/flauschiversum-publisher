# Tasks

## 1. Bildzeilen im Markdown

- [x] 1.1 Funktionen auf dem Markdown-Text: Bildzeilen finden (inkl. Leerzeichen/Umlaute im Namen), Alt-Text ändern, Zeile entfernen, in „Anhänge“ anfügen; Unit-Tests mit Beispielbeiträgen des Blogs und Sonderzeichen — `PhotoMarkdownTest`; erkannt werden Bildzeilen, die allein in einer Zeile stehen (Verweise mitten im Absatz bleiben unberührt, die App warnt davor)
- [x] 1.2 Reihenfolge in „Anhänge“ ändern und Aufräumen leerer Überschrift; Unit-Tests für Verschieben, einziges Bild und Zeilen außerhalb von „Anhänge“ — `PhotoMarkdownTest`

## 2. Fotobildschirm

- [x] 2.1 Raster der Bilder eines Beitrags mit Kennzeichnung „Titelbild“/„Nicht im Text“ (Vorschauen aus dem Cache der Übersicht); Compose-UI-Test — `PhotosUiTest`, `PhotoManagementTest`; Vorschauen aus dem Zwischenspeicher der Übersicht (`Thumbnails`)
- [x] 2.2 Alt-Text bearbeiten und nicht eingebundene Bilder einbinden; UI-Test, Commit auf `draft` mit nur `index.md` — `PhotosUiTest`, `PhotoManagementTest.altTextUndEinbinden`
- [x] 2.3 Reihenfolge per Ziehen; UI-Test mit drei Bildern — **Abweichung:** Reihenfolge per Pfeiltasten ↑/↓ statt Ziehen; Logik `PhotoMarkdown.move` ist getestet, Ziehen per Geste ist nicht umgesetzt
- [x] 2.4 Foto entfernen mit Bestätigung, Titelbild-Abfrage und Hinweis bei Verweisen im Fließtext; Test: Zeile und Datei weg, ein Commit, Wiederherstellung über „Verwerfen“ — `PhotosUiTest`, `PhotoManagementTest` (Wiederherstellung über „Verwerfen“)

## 3. Integration und Dokumentation

- [x] 3.1 Einstieg „Fotos“ aus Editor und Beitragsübersicht; End-to-End im Emulator: Foto hinzufügen, beschreiben, ordnen, entfernen, veröffentlichen — Einstieg „Fotos“ im Editor und im Menü der Übersicht; Hinzufügen, Beschreiben, Ordnen, Entfernen und Veröffentlichen vom Nutzer auf dem Gerät bestätigt (kein Emulatorlauf)
- [x] 3.2 Beitrag nach Änderungen mit dem Generator des Blogs prüfen (`cargo run --release -- check` in einer Kopie); keine Fehler und keine Warnungen zu fehlenden Medien — `GeneratorCheckTest.fotoverwaltungErzeugtKeineWarnungen…`
- [x] 3.3 `README.md` um Fotoverwaltung ergänzen; Schritte funktionieren wie beschrieben — `README.md` ergänzt

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (foto-teilen) und `markdown-editor` (Eigenschaften, Positionsprüfung).
- Change nach Review archivieren (`/opsx:archive`).
