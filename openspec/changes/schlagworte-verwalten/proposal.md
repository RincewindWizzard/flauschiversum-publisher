# Proposal

## Why

Die erlaubten Schlagworte stehen in `content/tags.yaml`; der Blog lässt keine unbekannten durch. In der App lassen sich bisher nur vorhandene Schlagworte wählen. Ein neues Thema müsste am Rechner ergänzt werden – genau das, was die App ersparen soll.

## What Changes

- Im Menü „Eigenschaften“ kann der Nutzer beim Wählen von Schlagworten ein neues Schlagwort anlegen.
- Die App ergänzt es in `content/tags.yaml` (alphabetisch einsortiert, Rest der Datei unverändert), committet auf `draft` und wählt es gleich für den Beitrag aus.
- Neue Schlagworte erscheinen als Teil der Entwürfe und in der Sicherheitsabfrage von „Veröffentlichen“.

## Capabilities

### New Capabilities
- `schlagworte-verwalten`: Neue Schlagworte anlegen und sicher in `tags.yaml` eintragen; Prüfung auf Duplikate und gültige Namen.

### Modified Capabilities

(keine in `openspec/specs/`; ersetzt die Einschränkung „nur vorhandene Schlagworte“ aus `beitrag-eigenschaften` und den Hinweis im Szenario „Neues Schlagwort gewünscht“ aus `repository-klon`)

## Impact

- Neuer Code in `app/` (Eingabedialog, Schicht `posts` für `tags.yaml`).
- Commits auf `draft` ändern auch `content/tags.yaml`.
- Das Repository `flauschiversum` bleibt unverändert.
