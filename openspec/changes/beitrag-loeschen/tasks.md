# Tasks

## 1. Löschen und Adresse

- [x] 1.1 Alte Adresse eines Beitrags bestimmen (`permalink` oder abgeleitet; Umlaute, ß, Sonderzeichen); Unit-Tests mit den Beispielen der URL-Spec des Blogs — `PostDeletionTest.alteAdresse…`
- [x] 1.2 Verzeichnis eines Beitrags auf `draft` entfernen (Commit „Beitrag gelöscht: <Titel>“) und pushen; Test mit Testrepository: Verzeichnis fehlt, `main` unverändert — `PostDeletionTest.veroeffentlichtenBeitragLoeschen…`
- [x] 1.3 Entwurfsart „Gelöscht“ in der Ableitung der Entwürfe, Liste und Sicherheitsabfrage; Tests für Anzeige und Veröffentlichen (Beitrag fehlt auf `main` danach) — `PostDeletionTest`, `DeleteUiTest` (Anzeige „Gelöscht“; Sicherheitsabfrage listet Art über `draftLabel`)
- [x] 1.4 Wiederherstellen über „Verwerfen“ inklusive Weiterleitungseintrag; Test: Verzeichnis und `redirects.yaml` entsprechen wieder `main` — `PostDeletionTest.wiederherstellen…`, `DeleteUiTest.geloeschterBeitragLaesstSichVerwerfen…`

## 2. Weiterleitung

- [x] 2.1 Zeile an `content/redirects.yaml` anhängen (zeilenbasiert, Kommentare unverändert, Pfade mit `/`); Unit-Tests mit der echten Datei des Blogs — `PostDeletionTest.weiterleitung…` mit der echten Datei
- [x] 2.2 Auswahl des Ziels (Beitragsübersicht, Startseite, anderer Beitrag, keine) im Löschdialog; UI-Tests je Auswahl — `DeleteUiTest` (Vorgabe, keine, anderer Beitrag, Startseite nicht eigens)
- [x] 2.3 Hinweis auf verweisende Beiträge (Suche im Index nach Link auf die alte Adresse); Test mit Beispielbeiträgen — `PostDeletionTest.verweisendeBeitraegeWerdenGenannt` (Logik); der Dialogtext ist nicht per UI-Test geprüft

## 3. Oberfläche und Integration

- [x] 3.1 „Löschen“ im Menü in Übersicht und Editor mit Bestätigung; Sonderfall „nur als Entwurf vorhanden“ = Verwerfen; UI-Tests — `DeleteUiTest` (Übersicht, Editor, nur Entwurf)
- [x] 3.2 End-to-End gegen den Generator des Blogs (Kopie): gelöschter Beitrag verschwindet, Weiterleitungsseite entsteht, `check` ohne Fehler — `GeneratorCheckTest.geloeschterBeitragVerschwindetUndLeitetWeiter`
- [x] 3.3 `README.md` um Löschen und Weiterleitung ergänzen; Schritte funktionieren wie beschrieben — `README.md` ergänzt

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (Entwurfsweg), `markdown-editor` (Menü) und `beitrag-uebersicht` (Index, Auswahl).
- Change nach Review archivieren (`/opsx:archive`).
