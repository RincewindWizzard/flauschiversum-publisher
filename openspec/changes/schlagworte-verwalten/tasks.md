# Tasks

## 1. Datei `tags.yaml`

- [ ] 1.1 Schicht `posts`: `tags.yaml` zeilenweise lesen, Einfügeposition nach deutscher Sortierung bestimmen, Zeile mit korrektem Quoting einfügen; Unit-Tests mit der echten Datei des Blogs (nur gelesen): Einsortieren, Umlaute, Sonderzeichen, Kommentare unverändert, Datei weiter gültig
- [ ] 1.2 Namensprüfung (leer, Länge, Umbruch, Duplikat ohne Beachtung der Groß-/Kleinschreibung gegen Zielbranch und `draft`); Unit-Tests je Szenario

## 2. Oberfläche und Git

- [ ] 2.1 Aktion „Neues Schlagwort“ in der Schlagwortauswahl der Eigenschaften mit Eingabedialog und Sofortauswahl; Compose-UI-Test
- [ ] 2.2 Commit „Schlagwort: <Name>“ auf `draft` und Push über den vorhandenen Entwurfsweg; Test mit Testrepository: nur `tags.yaml` im Diff, `main` unverändert
- [ ] 2.3 Entwurfsliste und Sicherheitsabfrage zeigen „Neues Schlagwort: <Name>“; Verwerfen eines Beitrags lässt das Schlagwort bestehen, „Alle verwerfen“ nimmt es zurück; Tests
- [ ] 2.4 Abgleich mit dem Server: vorhandenes Schlagwort statt Doppelung; Test mit zwei Klonen

## 3. Integration und Dokumentation

- [ ] 3.1 End-to-End gegen den Generator des Blogs (Kopie): Beitrag mit neuem Schlagwort `check` und Build ohne Fehler, Seite `/tag/<name>/` entsteht
- [ ] 3.2 `README.md` um „Neues Schlagwort anlegen“ ergänzen; Schritte funktionieren wie beschrieben

## Workflow follow-up

- Abhängigkeit beachten: nach `publisher-grundfunktionen` (Entwurfsweg) und `markdown-editor` (Eigenschaften).
- Change nach Review archivieren (`/opsx:archive`).
