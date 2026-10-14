# schlagworte-verwalten Specification

## Purpose
Beschreibt das Anlegen neuer Schlagworte in der App, damit die Liste in `content/tags.yaml` ohne Rechner gepflegt werden kann.

## Requirements

### Requirement: Neues Schlagwort anlegen
Die App SHALL bei der Auswahl der Schlagworte die Aktion „Neues Schlagwort“ anbieten. Nach Eingabe eines gültigen Namens SHALL die App das Schlagwort in `content/tags.yaml` eintragen und es für den Beitrag auswählen. Ein Name ist gültig, wenn er nach dem Entfernen von Leerzeichen am Rand nicht leer ist, höchstens 40 Zeichen hat, keine Zeilenumbrüche enthält und sich ohne Beachtung der Groß-/Kleinschreibung von allen vorhandenen Schlagworten unterscheidet.

#### Scenario: Gültiges Schlagwort
- **WHEN** der Nutzer „Makramee“ eingibt und bestätigt
- **THEN** steht `- name: Makramee` in `content/tags.yaml`, und der Beitrag trägt das Schlagwort

#### Scenario: Bereits vorhanden
- **WHEN** der Nutzer „hüte“ eingibt und „Hüte“ existiert
- **THEN** meldet die App, dass das Schlagwort schon besteht, und bietet „Hüte“ zur Auswahl an

#### Scenario: Ungültiger Name
- **WHEN** der Nutzer nur Leerzeichen eingibt
- **THEN** bleibt „Anlegen“ gesperrt und das Feld zeigt den Grund

### Requirement: Datei sicher ändern
Die App SHALL `content/tags.yaml` so ändern, dass nur eine Zeile hinzukommt: das neue Schlagwort wird nach deutscher Sortierung (Umlaute unter ihrem Grundbuchstaben) zwischen die vorhandenen Einträge einsortiert, Kommentare und übrige Zeilen bleiben unverändert. Namen mit Zeichen, die in YAML besonderes bedeuten (z. B. `:` oder `#`), SHALL korrekt in Anführungszeichen geschrieben werden.

#### Scenario: Einsortieren
- **WHEN** „Makramee“ zwischen „Challenge“ und „Nähen“ gehört
- **THEN** steht die neue Zeile an dieser Stelle und der Rest der Datei ist unverändert

#### Scenario: Sonderzeichen
- **WHEN** der Nutzer „Tipps: Wolle“ eingibt
- **THEN** steht `- name: "Tipps: Wolle"` in der Datei und sie bleibt gültiges YAML

#### Scenario: Kommentare
- **WHEN** die Datei Kommentarzeilen am Anfang hat
- **THEN** sind sie nach dem Eintragen unverändert

### Requirement: Als Entwurf führen
Das neue Schlagwort SHALL als Commit auf `draft` entstehen und in der Entwurfsliste sowie in der Sicherheitsabfrage von „Veröffentlichen“ als „Neues Schlagwort: <Name>“ erscheinen. Wird der Beitrag mit dem Schlagwort verworfen, bleibt das Schlagwort als Entwurf bestehen und lässt sich nur mit „Alle Entwürfe verwerfen“ zurücknehmen.

#### Scenario: Sicherheitsabfrage
- **WHEN** der Nutzer „Veröffentlichen“ wählt und „Makramee“ neu ist
- **THEN** nennt die Abfrage „Neues Schlagwort: Makramee“ neben den betroffenen Beiträgen

#### Scenario: Beitrag verworfen
- **WHEN** der Nutzer den Beitrag mit dem neuen Schlagwort verwirft
- **THEN** bleibt „Makramee“ in `tags.yaml` auf `draft`, bis alle Entwürfe verworfen oder veröffentlicht werden

### Requirement: Konflikte mit dem Server vermeiden
Vor dem Eintragen SHALL die App den Stand von `content/tags.yaml` des Zielbranchs prüfen (Hintergrundaktualisierung). Hat jemand das Schlagwort dort inzwischen selbst ergänzt, SHALL die App es nicht doppelt eintragen, sondern das vorhandene verwenden.

#### Scenario: Gleiches Schlagwort inzwischen auf dem Server
- **WHEN** der Fetch ein Schlagwort „Makramee“ im Zielbranch bringt, das der Nutzer gerade anlegen will
- **THEN** wählt die App das vorhandene aus und ändert `tags.yaml` nicht
