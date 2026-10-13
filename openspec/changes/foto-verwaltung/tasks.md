# Tasks

## 1. Bildzeilen im Markdown

- [ ] 1.1 Funktionen auf dem Markdown-Text: Bildzeilen finden (inkl. Leerzeichen/Umlaute im Namen), Alt-Text ändern, Zeile entfernen, in „Anhänge“ anfügen; Unit-Tests mit Beispielbeiträgen des Blogs und Sonderzeichen
- [ ] 1.2 Reihenfolge in „Anhänge“ ändern und Aufräumen leerer Überschrift; Unit-Tests für Verschieben, einziges Bild und Zeilen außerhalb von „Anhänge“

## 2. Fotobildschirm

- [ ] 2.1 Raster der Bilder eines Beitrags mit Kennzeichnung „Titelbild“/„Nicht im Text“ (Vorschauen aus dem Cache der Übersicht); Compose-UI-Test
- [ ] 2.2 Alt-Text bearbeiten und nicht eingebundene Bilder einbinden; UI-Test, Commit auf `draft` mit nur `index.md`
- [ ] 2.3 Reihenfolge per Ziehen; UI-Test mit drei Bildern
- [ ] 2.4 Foto entfernen mit Bestätigung, Titelbild-Abfrage und Hinweis bei Verweisen im Fließtext; Test: Zeile und Datei weg, ein Commit, Wiederherstellung über „Verwerfen“

## 3. Integration und Dokumentation

- [ ] 3.1 Einstieg „Fotos“ aus Editor und Beitragsübersicht; End-to-End im Emulator: Foto hinzufügen, beschreiben, ordnen, entfernen, veröffentlichen
- [ ] 3.2 Beitrag nach Änderungen mit dem Generator des Blogs prüfen (`cargo run --release -- check` in einer Kopie); keine Fehler und keine Warnungen zu fehlenden Medien
- [ ] 3.3 `README.md` um Fotoverwaltung ergänzen; Schritte funktionieren wie beschrieben

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (foto-teilen) und `markdown-editor` (Eigenschaften, Positionsprüfung).
- Change nach Review archivieren (`/opsx:archive`).
