# Tasks

## 1. Rendern

- [x] 1.1 Markdown → HTML mit Tabellen, Durchstreichen, Fußnoten und durchgereichtem HTML; Unit-Tests mit Beispielbeiträgen des Blogs (Bildgruppen, Tabellen, `<br/>`) — `PreviewRendererTest`
- [x] 1.2 Bildgruppen (Absatz mit mehreren Bildzeilen) und Einzelbilder im HTML: Karussell-Markup, Bildunterschrift aus dem Alt-Text; Unit-Tests je Szenario der Spec, Bildname mit Leerzeichen — `PreviewRendererTest` (Einzelbild, Karussell, Leerzeichen im Namen); Bildzeilen direkt unter Text werden wie im Blog herausgelöst
- [x] 1.3 Markierung fehlender Bilder und fehlendem Titelbild; Unit-Test — `PreviewRendererTest.fehlendesBild…`, `kopfUndFehlendesTitelbild`

## 2. Anzeige

- [ ] 2.1 WebView ohne JavaScript, Asset-Loader für Klon-Bilder (700-Pixel-Varianten aus dem Cache), Netzwerkzugriffe blockiert; Instrumented-Test: Skript wird nicht ausgeführt, externe Anfrage wird blockiert — WebView ohne JavaScript, `blockNetworkLoads`, Asset-Loader (Bilder aus dem Klon in Originalgröße, nicht als 700-Pixel-Variante) und CSP-Meta sind eingebaut; Skripte werden zusätzlich im HTML entfernt (`PreviewRendererTest.skripteWerdenEntfernt`). Der Instrumented-Test, dass die WebView kein Skript ausführt und externe Anfragen blockiert, fehlt
- [ ] 2.2 CSS mit Farben aus dem Schema „Flauschi-Nebel“ (Hell/Dunkel), Schriften Lobster/Lora; Kontrastprüfung als Unit-Test, Screenshot-Test in beiden Modi — CSS mit den Farben von „Flauschi-Nebel“ (Hell/Dunkel) und Schriften Lobster/Lora; Kontrastprüfung als Unit-Test (`kontrasteInHellUndDunkel`) ist erledigt, der Screenshot-Test fehlt
- [ ] 2.3 Karussell (scroll-snap) mit Seitenanzeige und Vollbild-Overlay ohne JavaScript-Bibliothek; Instrumented-Test: Wischen, Tippen auf das Bild, Schließen — Karussell (scroll-snap, „n / m“) und Vollbild über CSS `:target` ohne Skript sind eingebaut; Wischen und Tippen sind nicht auf einem Gerät geprüft
- [ ] 2.4 Link-Bestätigung vor dem Öffnen im Browser; UI-Test — Bestätigungsdialog vor dem Öffnen im Browser ist eingebaut (`PreviewPolicy.needsConfirmation` getestet); ein UI-Test des Dialogs fehlt

## 3. Einbindung

- [x] 3.1 Umschalter „Bearbeiten/Vorschau“ im Editor mit aktuellem Text und aktuellen Eigenschaften; Vorschau aus der Übersicht; UI-Tests — `PreviewUiTest` (Editor-Umschalter mit ungespeichertem Text, Menü der Übersicht, Laden aus dem Repository)
- [x] 3.2 Einmaliger Hinweis zur Annäherung; Test: erscheint nur beim ersten Öffnen — `PreviewUiTest.hinweisErscheintNurBeimErstenMal`
- [x] 3.3 Abgleich mit dem Generator: Zahl der Bilder, Gruppen und Unterschriften bei einer Stichprobe von Beiträgen stimmt mit der Ausgabe des Generators überein (Kopie des Blogs, nur gelesen) — `GeneratorCheckTest.vorschauStimmt…`: Zahl der Bilder, Gruppen und Unterschriften stimmt bei einer Stichprobe mit dem Generator überein
- [x] 3.4 `README.md` um Vorschau ergänzen; Schritte funktionieren wie beschrieben — `README.md` ergänzt

## Workflow follow-up

- Abhängigkeit beachten: nach `markdown-editor` (Parser, Eigenschaften) und `beitrag-uebersicht` (Vorschaubild-Cache).
- Change nach Review archivieren (`/opsx:archive`).
