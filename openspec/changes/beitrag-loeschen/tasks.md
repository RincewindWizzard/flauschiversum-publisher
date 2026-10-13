# Tasks

## 1. Löschen und Adresse

- [ ] 1.1 Alte Adresse eines Beitrags bestimmen (`permalink` oder abgeleitet; Umlaute, ß, Sonderzeichen); Unit-Tests mit den Beispielen der URL-Spec des Blogs
- [ ] 1.2 Verzeichnis eines Beitrags auf `draft` entfernen (Commit „Beitrag gelöscht: <Titel>“) und pushen; Test mit Testrepository: Verzeichnis fehlt, `main` unverändert
- [ ] 1.3 Entwurfsart „Gelöscht“ in der Ableitung der Entwürfe, Liste und Sicherheitsabfrage; Tests für Anzeige und Veröffentlichen (Beitrag fehlt auf `main` danach)
- [ ] 1.4 Wiederherstellen über „Verwerfen“ inklusive Weiterleitungseintrag; Test: Verzeichnis und `redirects.yaml` entsprechen wieder `main`

## 2. Weiterleitung

- [ ] 2.1 Zeile an `content/redirects.yaml` anhängen (zeilenbasiert, Kommentare unverändert, Pfade mit `/`); Unit-Tests mit der echten Datei des Blogs
- [ ] 2.2 Auswahl des Ziels (Beitragsübersicht, Startseite, anderer Beitrag, keine) im Löschdialog; UI-Tests je Auswahl
- [ ] 2.3 Hinweis auf verweisende Beiträge (Suche im Index nach Link auf die alte Adresse); Test mit Beispielbeiträgen

## 3. Oberfläche und Integration

- [ ] 3.1 „Löschen“ im Menü in Übersicht und Editor mit Bestätigung; Sonderfall „nur als Entwurf vorhanden“ = Verwerfen; UI-Tests
- [ ] 3.2 End-to-End gegen den Generator des Blogs (Kopie): gelöschter Beitrag verschwindet, Weiterleitungsseite entsteht, `check` ohne Fehler
- [ ] 3.3 `README.md` um Löschen und Weiterleitung ergänzen; Schritte funktionieren wie beschrieben

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (Entwurfsweg), `markdown-editor` (Menü) und `beitrag-uebersicht` (Index, Auswahl).
- Change nach Review archivieren (`/opsx:archive`).
