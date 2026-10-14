# Proposal

## Why

Die App kann Fotos zu einem Beitrag hinzufügen (`foto-teilen`), aber nicht mehr korrigieren: Ein falsch geteiltes Foto bleibt im Beitrag, die Reihenfolge der Bilder lässt sich nicht ändern, und ein vergessener oder falscher Alt-Text ist nicht nachträglich korrigierbar. Bei einem Blog, der von seinen Fotos lebt, braucht es eine Fotoverwaltung pro Beitrag.

## What Changes

- Neuer Bildschirm „Fotos“ eines Beitrags: alle Bilder im Beitragsverzeichnis als Raster, mit Kennzeichnung „Titelbild“ und „Nicht im Text eingebunden“.
- Alt-Text eines Bildes nachträglich ändern; die Änderung gilt für alle Stellen im Text, an denen das Bild eingebunden ist.
- Reihenfolge der Bilder im Abschnitt `## Anhänge` per Ziehen ändern.
- Foto entfernen: Bildzeilen aus dem Text und Datei aus dem Verzeichnis, mit Warnung bei Verwendung im Text und als Titelbild.
- Nicht eingebundene Bilder mit einem Tipp in `## Anhänge` einbinden.

## Capabilities

### New Capabilities
- `foto-verwaltung`: Bilder eines Beitrags ansehen, beschreiben, ordnen, einbinden und entfernen.

### Modified Capabilities

(keine in `openspec/specs/`; baut auf `foto-teilen` und `beitrag-bearbeiten` der Changes `publisher-grundfunktionen` und `markdown-editor` auf)

## Impact

- Neuer Code in `app/` (Fotobildschirm, Markdown-Zeilenoperationen auf Bildzeilen in der Schicht `posts`).
- Löschen von Bilddateien im Klon; Commits auf `draft`.
- Das Repository `flauschiversum` bleibt unverändert.
