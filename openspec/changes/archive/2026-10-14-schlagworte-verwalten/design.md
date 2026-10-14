# Design

## Context

`content/tags.yaml` enthält eine Kopfzeile mit Kommentaren und Einträge `- name: <Name>` (derzeit 41, alphabetisch). Der Blog akzeptiert nur Schlagworte aus dieser Liste (`content-format`). Die App ändert Dateien nur über Commits auf `draft`. Motivation: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Neues Thema vom Handy aus anlegen, ohne `tags.yaml` von Hand zu bearbeiten.
- Kleiner, vorhersehbarer Diff (eine Zeile).

**Non-Goals:**
- Umbenennen oder Löschen von Schlagworten (betrifft viele Beiträge und URLs `/tag/<name>/`).
- Beschreibungen, Sortierung oder Gruppierung von Schlagworten.

## Decisions

**Zeilenbasiertes Einfügen statt YAML neu schreiben.** Die Schicht `posts` liest die Datei als Zeilen, sucht die Einfügeposition nach deutschem Collator (Vergleich ohne Beachtung der Groß-/Kleinschreibung) und fügt genau eine Zeile ein. Alternative: YAML parsen und neu serialisieren – verworfen (Kommentare und Formatierung gingen verloren).

**Quoting nach YAML-Regeln:** Namen, die mit Sonderzeichen beginnen oder `:`, `#`, Anführungszeichen, `-` am Anfang enthalten, werden in doppelte Anführungszeichen gesetzt und maskiert; sonst unquoted wie im vorhandenen Bestand.

**Ein Commit pro Schlagwort**, getrennt vom Beitragscommit, damit „Verwerfen“ des Beitrags das Schlagwort nicht still zurücknimmt (siehe Spec). Commit-Nachricht „Schlagwort: <Name>“.

**Duplikatprüfung gegen den Stand von Zielbranch und `draft`** (Union), da der Blog Schlagworte ohne Beachtung der Groß-/Kleinschreibung zuordnet.

## Risks / Trade-offs

- [Tippfehler im Schlagwort wird veröffentlicht] → Vorschau des Namens im Dialog; Umbenennen ist bewusst ein späterer Change; bis zur Veröffentlichung kann „Alle Entwürfe verwerfen“ das Schlagwort zurücknehmen.
- [Schlagwort mit Sonderzeichen erzeugt ungültige URL `/tag/<name>/`] → Namensregeln der Spec; Generator des Blogs bildet URLs aus dem Namen, Test mit „Tipps: Wolle“ im End-to-End gegen den Generator.
- [Zwei Geräte legen dasselbe Schlagwort an] → Prüfung beim Hintergrundabgleich (siehe Spec), Konflikt beim Veröffentlichen bricht ab.
