# Design

## Context

Die Bildzeilen stehen als `![Alt](Datei)` im Markdown (bei `foto-teilen` unter `## Anhänge` am Dateiende). Der Blog erlaubt Dateinamen mit Umlauten und Leerzeichen (`content-format`) und erzeugt aus den Bildern die Varianten `200x`/`700x`/`1000x`. Dieser Change ergänzt nur das Verwalten; Hinzufügen bleibt in `foto-teilen`. Motivation: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Jede Bildänderung ist sicher: nur die betroffenen Zeilen und Dateien ändern sich.
- Die Operationen sind unabhängig vom Editor (reine Funktionen auf dem Markdown-Text), damit sie testbar sind.

**Non-Goals:**
- Bilder drehen, zuschneiden oder ersetzen; Bildbearbeitung.
- Verschieben von Bildern im Fließtext (dafür der Editor).
- Verwalten von Videos und GIFs.

## Decisions

**Bildzeilen über den Markdown-Parser finden, nicht über Regex.** Die Schicht `posts` nutzt dieselben Quellpositionen wie die Markdown-Prüfung (`markdown-editor`), erkennt Bildziele mit Leerzeichen (`![x](a b.jpg)`, vom Blog tolerierte Schreibweise) und verändert nur die Zeichenbereiche der betroffenen Zeilen. Alternative: Regex – verworfen (fragil bei Klammern im Alt-Text und Dateinamen).

**Reihenfolge nur im Abschnitt „Anhänge“.** Der Abschnitt ist eine zusammenhängende Bildgruppe; Umordnen heißt Zeilen vertauschen. Positionen im Fließtext sind redaktionelle Entscheidungen und bleiben dem Editor vorbehalten.

**Entfernen = Textänderung + Dateilöschung in einem Commit.** Das Verzeichnis bleibt konsistent (kein verwaister Verweis, keine verwaiste Datei). Der Blog meldet Verweise auf fehlende Dateien beim Build; die App verhindert sie.

**Titelbild bleibt im Menü „Eigenschaften“.** Fotos-Bildschirm zeigt den Status, ändert das Feld `image` aber nur über die Auswahl vor dem Entfernen (Aufruf der Eigenschaften-Logik).

## Risks / Trade-offs

- [Zeile wird falsch erkannt, Text geht verloren] → Operationen als reine Funktionen mit Tests auf echten Beiträgen des Blogs; vor dem Speichern Vergleich der Differenz (nur erwartete Zeilen betroffen).
- [Gleicher Bildverweis mehrfach im Text] → Alt-Text ändert alle Stellen; Entfernen löscht alle Zeilen mit Hinweis.
- [Löschen einer Datei aus dem Klon ohne Rückweg] → Bestätigung; Wiederherstellung über „Verwerfen“ (Datei kommt aus `main` zurück), solange nicht veröffentlicht.
