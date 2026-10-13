# Tasks

## 1. Rendern

- [ ] 1.1 Markdown → HTML mit Tabellen, Durchstreichen, Fußnoten und durchgereichtem HTML; Unit-Tests mit Beispielbeiträgen des Blogs (Bildgruppen, Tabellen, `<br/>`)
- [ ] 1.2 Bildgruppen (Absatz mit mehreren Bildzeilen) und Einzelbilder im HTML: Karussell-Markup, Bildunterschrift aus dem Alt-Text; Unit-Tests je Szenario der Spec, Bildname mit Leerzeichen
- [ ] 1.3 Markierung fehlender Bilder und fehlendem Titelbild; Unit-Test

## 2. Anzeige

- [ ] 2.1 WebView ohne JavaScript, Asset-Loader für Klon-Bilder (700-Pixel-Varianten aus dem Cache), Netzwerkzugriffe blockiert; Instrumented-Test: Skript wird nicht ausgeführt, externe Anfrage wird blockiert
- [ ] 2.2 CSS mit Farben aus dem Schema „Flauschi-Nebel“ (Hell/Dunkel), Schriften Lobster/Lora; Kontrastprüfung als Unit-Test, Screenshot-Test in beiden Modi
- [ ] 2.3 Karussell (scroll-snap) mit Seitenanzeige und Vollbild-Overlay ohne JavaScript-Bibliothek; Instrumented-Test: Wischen, Tippen auf das Bild, Schließen
- [ ] 2.4 Link-Bestätigung vor dem Öffnen im Browser; UI-Test

## 3. Einbindung

- [ ] 3.1 Umschalter „Bearbeiten/Vorschau“ im Editor mit aktuellem Text und aktuellen Eigenschaften; Vorschau aus der Übersicht; UI-Tests
- [ ] 3.2 Einmaliger Hinweis zur Annäherung; Test: erscheint nur beim ersten Öffnen
- [ ] 3.3 Abgleich mit dem Generator: Zahl der Bilder, Gruppen und Unterschriften bei einer Stichprobe von Beiträgen stimmt mit der Ausgabe des Generators überein (Kopie des Blogs, nur gelesen)
- [ ] 3.4 `README.md` um Vorschau ergänzen; Schritte funktionieren wie beschrieben

## Workflow follow-up

- Abhängigkeit beachten: nach `markdown-editor` (Parser, Eigenschaften) und `beitrag-uebersicht` (Vorschaubild-Cache).
- Change nach Review archivieren (`/opsx:archive`).
