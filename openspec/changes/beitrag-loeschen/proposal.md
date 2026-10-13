# Proposal

## Why

Ein versehentlich veröffentlichter oder überholter Beitrag lässt sich in der App bisher nicht entfernen. Weil der Blog nach dem Grundsatz „Cool URIs don't change“ arbeitet, darf Löschen aber keine toten Adressen hinterlassen, ohne dass der Nutzer das bewusst entscheidet.

## What Changes

- Aktion „Löschen“ am Beitrag (Übersicht und Editor): entfernt das Beitragsverzeichnis `content/posts/<name>/` mit allen Medien als Commit auf `draft`.
- Gelöschte Beiträge erscheinen als Entwurf „Gelöscht“, lassen sich über „Verwerfen“ wiederherstellen und gehen erst durch „Veröffentlichen“ live.
- Beim Löschen eines veröffentlichten Beitrags schlägt die App eine Weiterleitung der alten Adresse in `content/redirects.yaml` vor (Standard: auf die Beitragsübersicht).
- Löschen eines noch nie veröffentlichten Entwurfs entspricht „Verwerfen“.

## Capabilities

### New Capabilities
- `beitrag-loeschen`: Beitrag entfernen, als Entwurf führen, Weiterleitung der alten Adresse anbieten, Wiederherstellen vor dem Veröffentlichen.

### Modified Capabilities

(keine in `openspec/specs/`; ergänzt die Entwurfsarten „Neu“ und „Geändert“ aus `entwurf-veroeffentlichen` um „Gelöscht“)

## Impact

- Neuer Code in `app/` (Löschen, Weiterleitungseintrag in `redirects.yaml` über die Schicht `posts`).
- Commits auf `draft` löschen Dateien und ändern `content/redirects.yaml`.
- Das Repository `flauschiversum` bleibt unverändert.
