# Spec Delta

## Purpose

Beschreibt, wie ein bereits vorhandener Beitrag aus der Beitragsliste im Editor geöffnet, geändert und über einen Entwurfs-Branch veröffentlicht wird.

## ADDED Requirements

### Requirement: Beitrag zum Bearbeiten öffnen
Die App SHALL aus der Beitragsliste einen vorhandenen Beitrag im Markdown-Editor öffnen können. Der Texteditor SHALL nur den Text nach dem Frontmatter bearbeiten; das Frontmatter ändert sich ausschließlich über das Menü „Eigenschaften“ (Spec `beitrag-eigenschaften`) und bleibt sonst Zeichen für Zeichen unverändert.

#### Scenario: Beitrag öffnen
- **WHEN** der Nutzer in der Liste den Beitrag „Alraune“ antippt und „Bearbeiten“ wählt
- **THEN** zeigt der Editor den Text von `content/posts/alraune/index.md` ohne Frontmatter

#### Scenario: Frontmatter bleibt
- **WHEN** der Nutzer einen Satz im Text ändert und speichert
- **THEN** unterscheidet sich die Datei vom Original nur im geänderten Satz, das Frontmatter ist identisch

#### Scenario: Eigenschaften zusammen mit Text speichern
- **WHEN** der Nutzer den Text und den Titel ändert und speichert
- **THEN** enthält ein Commit auf `draft/alraune` beide Änderungen in `content/posts/alraune/index.md`

#### Scenario: Beitrag ohne Frontmatter-Ende
- **WHEN** eine `index.md` kein abschließendes `---` hat und sich nicht sicher teilen lässt
- **THEN** öffnet die App den Beitrag nicht zum Bearbeiten und meldet den Grund

### Requirement: Änderung über Entwurfs-Branch
Beim Speichern einer Änderung SHALL die App einen Entwurfs-Branch `draft/<verzeichnisname>` vom Zielbranch anlegen (oder den vorhandenen verwenden), die Änderung (Text und Eigenschaften) dort committen und pushen. Der Zielbranch ändert sich erst durch „Veröffentlichen“.

#### Scenario: Erste Änderung
- **WHEN** der Nutzer einen veröffentlichten Beitrag ändert und speichert
- **THEN** existiert `draft/alraune` mit einem Commit, der nur `content/posts/alraune/index.md` ändert, und `main` ist unverändert

#### Scenario: Weitere Änderung
- **WHEN** `draft/alraune` schon existiert und der Nutzer erneut speichert
- **THEN** kommt ein weiterer Commit auf `draft/alraune`

#### Scenario: Keine Änderung
- **WHEN** der Nutzer speichert, ohne den Text geändert zu haben
- **THEN** wird kein Commit angelegt

### Requirement: Aktueller Stand beim Bearbeiten
Beim Öffnen eines Beitrags SHALL die App sofort den letzten bekannten Stand zeigen und parallel im Hintergrund aktualisieren (Spec `repository-klon`), ohne zu warten. Gibt es für den Beitrag einen Entwurfs-Branch, SHALL die App den Text aus diesem Branch öffnen, nicht aus dem Zielbranch. Bringt die Aktualisierung eine neuere Fassung des Beitrags, die Eingabe aber noch unverändert ist, SHALL der Editor die neue Fassung ohne Rückfrage übernehmen.

#### Scenario: Offener Entwurf
- **WHEN** `draft/alraune` ungeveröffentlichte Änderungen enthält und der Nutzer „Alraune“ bearbeitet
- **THEN** zeigt der Editor den Text aus `draft/alraune`

#### Scenario: Neuere Fassung, noch nichts getippt
- **WHEN** der Nutzer „Alraune“ öffnet, noch nichts geändert hat und die Hintergrundaktualisierung eine neuere Fassung der Datei bringt
- **THEN** zeigt der Editor kurz danach die neue Fassung, mit einem unaufdringlichen Hinweis „Aktualisiert“

#### Scenario: Ohne Netz
- **WHEN** kein Netz besteht
- **THEN** öffnet die App den Beitrag aus dem letzten lokalen Stand und weist darauf hin

### Requirement: Serverseitige Änderung während der Eingabe
Hat sich die Datei des Beitrags auf dem Server geändert, während der Nutzer schon Änderungen im Editor hat, SHALL die App den Editortext nicht überschreiben. Sie SHALL stattdessen einen Hinweis „Beitrag wurde auf dem Server geändert“ anzeigen, ohne die Eingabe zu blockieren; die Zusammenführung erfolgt später beim Veröffentlichen (bei Konflikt Abbruch nach `entwurf-veroeffentlichen`).

#### Scenario: Gleichzeitige Änderung
- **WHEN** der Nutzer in „Alraune“ tippt und der Hintergrund-Fetch eine geänderte `index.md` von „Alraune“ bringt
- **THEN** bleibt der eingegebene Text erhalten, und ein Hinweis erscheint, ohne Dialog und ohne Unterbrechung der Eingabe

### Requirement: Anhänge-Abschnitt schützen
Der Editor SHALL den Abschnitt `## Anhänge` am Dateiende wie normalen Text behandeln, aber beim Bearbeiten nicht ohne Warnung dessen Überschrift entfernen oder verschieben, da die App neue Fotos dort anfügt.

#### Scenario: Überschrift gelöscht
- **WHEN** der Nutzer die Zeile `## Anhänge` löscht und es darunter Bildzeilen gibt
- **THEN** warnt die Prüfung, dass neue Fotos ohne diesen Abschnitt neu angelegt werden und vorhandene Bilder dann im übrigen Text stehen
