# Tasks

## 1. Datei `tags.yaml`

- [x] 1.1 Schicht `posts`: `tags.yaml` zeilenweise lesen, Einfügeposition nach deutscher Sortierung bestimmen, Zeile mit korrektem Quoting einfügen; Unit-Tests mit der echten Datei des Blogs (nur gelesen): Einsortieren, Umlaute, Sonderzeichen, Kommentare unverändert, Datei weiter gültig — `TagsFileTest` mit der echten Datei des Blogs
- [x] 1.2 Namensprüfung (leer, Länge, Umbruch, Duplikat ohne Beachtung der Groß-/Kleinschreibung gegen Zielbranch und `draft`); Unit-Tests je Szenario — `TagsFileTest.namenspruefung`

## 2. Oberfläche und Git

- [x] 2.1 Aktion „Neues Schlagwort“ in der Schlagwortauswahl der Eigenschaften mit Eingabedialog und Sofortauswahl; Compose-UI-Test — `TagsUiTest`
- [x] 2.2 Commit „Schlagwort: <Name>“ auf `draft` und Push über den vorhandenen Entwurfsweg; Test mit Testrepository: nur `tags.yaml` im Diff, `main` unverändert — `TagCreationTest.commitEnthaeltNurTagsYaml…`
- [x] 2.3 Entwurfsliste und Sicherheitsabfrage zeigen „Neues Schlagwort: <Name>“; Verwerfen eines Beitrags lässt das Schlagwort bestehen, „Alle verwerfen“ nimmt es zurück; Tests — `TagsUiTest.entwurfslisteUndAbfrage…`, `TagCreationTest`
- [x] 2.4 Abgleich mit dem Server: vorhandenes Schlagwort statt Doppelung; Test mit zwei Klonen — `TagCreationTest.serverhatDasSchlagwort…` (Server-Stand per Fetch statt zwei Klonen)

## 3. Integration und Dokumentation

- [x] 3.1 End-to-End gegen den Generator des Blogs (Kopie): Beitrag mit neuem Schlagwort `check` und Build ohne Fehler, Seite `/tag/<name>/` entsteht — `GeneratorCheckTest.beitragMitNeuemSchlagwort…`: `check` und Build ohne Fehler, `/tag/makramee/` entsteht
- [x] 3.2 `README.md` um „Neues Schlagwort anlegen“ ergänzen; Schritte funktionieren wie beschrieben — `README.md` ergänzt, Schritte gegen die Oberfläche abgeglichen (nicht auf einem Gerät)

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (Entwurfsweg) und `markdown-editor` (Eigenschaften).
- Change nach Review archivieren (`/opsx:archive`).
