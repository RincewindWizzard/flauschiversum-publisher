# Proposal

## Why

Der Blog hat bereits 194 Beiträge (Stand heute) und wächst. Um einen davon zu bearbeiten oder ein Foto dazuzulegen, muss man ihn schnell finden – am Handy ohne Tastatur-Suche im Dateisystem. Die einfache Beitragsliste aus `publisher-grundfunktionen` zeigt nur Titel und Datum; für den Alltag braucht es eine echte Übersicht mit Vorschau, Suche und Filter, in der auch offene Entwürfe sofort auffallen.

## What Changes

- Neuer Startbildschirm „Beiträge“: scrollbare Übersicht aller Beiträge mit Titelbild-Vorschau, Titel, Datum und Schlagworten, neueste zuerst.
- Offene Entwürfe (Beiträge, die auf `draft` neu oder geändert sind) stehen als eigener Abschnitt oben, mit Upload-Status.
- Suche über Titel, Kurzbeschreibung, Schlagworte und Text; Filter nach Schlagwort und Jahr; wählbare Sortierung.
- Tippen auf einen Beitrag öffnet ihn zum Bearbeiten; ein Menü am Eintrag bietet „Foto hinzufügen“, „Eigenschaften“ und bei Entwürfen „Veröffentlichen“ und „Verwerfen“.
- Dieselbe Übersicht dient als Auswahl, wenn Fotos über „Teilen“ ankommen: Antippen wählt den Zielbeitrag, „Neuer Beitrag“ legt einen an.
- Aktualisieren per Wischen nach unten (Fetch); die Übersicht funktioniert auch offline.

## Capabilities

### New Capabilities
- `beitrag-uebersicht`: Übersicht der Beiträge und Entwürfe mit Vorschau, Suche, Filter, Sortierung, Aktionen je Beitrag und Auswahlmodus für geteilte Fotos.

### Modified Capabilities

(keine in `openspec/specs/`; die Übersicht ist die Darstellung der „Beitragsliste“ aus `repository-klon` und die Auswahl für `foto-teilen` und `beitrag-bearbeiten` der noch nicht archivierten Changes, siehe Design)

## Impact

- Neuer Code in `app/` (Compose-Oberfläche, Index der Beiträge, Vorschaubilder); Abhängigkeit für das Laden von Bildern (z. B. Coil) und ggf. lokaler Cache.
- Hängt ab von `publisher-grundfunktionen` (Klon, Entwurfs-Branches) und `markdown-editor` (Bearbeiten, Eigenschaften).
- Das Repository `flauschiversum` bleibt unverändert.
