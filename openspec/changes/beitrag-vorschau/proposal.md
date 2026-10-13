# Proposal

## Why

Der Editor prüft Syntax und Rechtschreibung, zeigt aber nicht, wie der Beitrag aussehen wird: Bildgruppen, Bildunterschriften, Tabellen und HTML-Reste sind im Rohtext schwer zu beurteilen. Eine Vorschau in der App verhindert Überraschungen nach dem Veröffentlichen, ohne den Blog bauen zu müssen.

## What Changes

- Neuer Modus „Vorschau“ im Beitragseditor und aus der Beitragsübersicht: der Beitrag gerendert wie auf der Beitragsseite (Kopf mit Titel, Datum, Schlagworten, Text, Bilder).
- Bildgruppen als wischbares Karussell, Bildunterschriften aus dem Alt-Text, Tabellen, Fußnoten, Durchstreichen.
- Hell und Dunkel passend zum Farbschema des Blogs; vollständig offline, ohne Skripte.
- Fehlende Bilder und Syntaxprobleme sind in der Vorschau markiert.

## Capabilities

### New Capabilities
- `beitrag-vorschau`: Gerenderte Vorschau eines Beitrags in der App, lokal und offline.

### Modified Capabilities

(keine in `openspec/specs/`)

## Impact

- Neuer Code in `app/` (Markdown-zu-HTML, WebView mit lokalem Asset-Loader, mitgeliefertes CSS und Schriften).
- Abhängigkeit vom Markdown-Parser aus `markdown-editor`.
- Das Repository `flauschiversum` bleibt unverändert.
