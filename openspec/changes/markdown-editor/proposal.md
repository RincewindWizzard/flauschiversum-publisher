# Proposal

## Why

Beiträge bestehen aus Markdown. Mit dem normalen Textfeld des Handys sind Tippfehler und kaputte Auszeichnungen (nicht geschlossene Hervorhebung, falscher Link) schwer zu sehen, und fertige Beiträge lassen sich in der App gar nicht nachbessern. Ein eingebauter Editor mit Syntaxprüfung und deutscher Rechtschreibprüfung hilft, Fehler vor dem Veröffentlichen zu finden – und macht das Bearbeiten bestehender Beiträge vom Handy aus möglich.

## What Changes

- Die App erhält einen eingebauten Markdown-Editor mit Syntaxhervorhebung und einer Werkzeugleiste für häufige Auszeichnungen. Er ersetzt das einfache Textfeld für den Beitragstext im Editor aus `publisher-grundfunktionen`.
- Ein Syntax-Checker markiert Fehler und Warnungen im Markdown, passend zum Markdown-Dialekt des Blogs (CommonMark mit Tabellen, Durchstreichen und Fußnoten, eingebettetes HTML erlaubt).
- Eine Rechtschreibprüfung für Deutsch (eingebautes Hunspell-Wörterbuch, offline) unterstreicht unbekannte Wörter, schlägt Korrekturen vor und erlaubt, Wörter in ein persönliches Wörterbuch aufzunehmen.
- Bestehende Beiträge lassen sich aus der Beitragsliste öffnen und bearbeiten; Änderungen laufen über den Entwurfs-Branch `draft/<name>` und „Veröffentlichen“.

## Capabilities

### New Capabilities
- `markdown-editor`: Bearbeitungsoberfläche für den Beitragstext mit Hervorhebung, Werkzeugleiste und Rückgängig/Wiederholen.
- `markdown-pruefung`: Syntaxprüfung des Markdowns mit Markierung und Liste der Befunde.
- `rechtschreibpruefung`: Deutsche Rechtschreibprüfung mit Vorschlägen und persönlichem Wörterbuch.
- `beitrag-bearbeiten`: Öffnen, Ändern und Speichern des Textes eines bestehenden Beitrags über einen Entwurfs-Branch.

### Modified Capabilities

(keine in `openspec/specs/`; der Change ersetzt aber das Textfeld aus `beitrag-erstellen` des noch nicht archivierten Changes `publisher-grundfunktionen`, siehe Design)

## Impact

- Neuer Code in `app/` (Compose-Editor, Prüfungen); Abhängigkeit für Hunspell-Wörterbücher (de_DE, Lizenz prüfen) und einen Markdown-Parser auf der JVM.
- App-Größe wächst durch das Wörterbuch.
- Abhängig von `publisher-grundfunktionen` (Repository-Klon, Entwurfs-Branches, Beitragsliste, Anhänge-Abschnitt).
- Das Repository `flauschiversum` bleibt unverändert.
