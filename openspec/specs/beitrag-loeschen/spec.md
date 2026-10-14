# beitrag-loeschen Specification

## Purpose
Beschreibt das Löschen eines Beitrags aus dem Blog über die App, einschließlich Wiederherstellbarkeit bis zur Veröffentlichung und der Weiterleitung der alten Adresse.

## Requirements

### Requirement: Beitrag löschen
Die App SHALL die Aktion „Löschen“ am Beitrag anbieten (Menü in der Übersicht, Menü im Editor). Nach einer Bestätigung, die Titel und Adresse des Beitrags nennt, SHALL die App das gesamte Verzeichnis `content/posts/<name>/` (Text und Medien) als Commit auf `draft` entfernen und pushen. Der Zielbranch ändert sich erst durch „Veröffentlichen“.

#### Scenario: Veröffentlichten Beitrag löschen
- **WHEN** der Nutzer „Alraune“ löscht und bestätigt
- **THEN** fehlt `content/posts/alraune/` auf `draft`, `main` ist unverändert und „Alraune“ steht als Entwurf „Gelöscht“ in der Entwurfsliste

#### Scenario: Abbrechen
- **WHEN** der Nutzer die Bestätigung abbricht
- **THEN** bleibt alles unverändert

#### Scenario: Nur als Entwurf vorhanden
- **WHEN** der Beitrag „Mütze“ nur auf `draft` existiert
- **THEN** entspricht „Löschen“ dem „Verwerfen“, ohne Hinweis auf eine Weiterleitung

### Requirement: Als Entwurf „Gelöscht“ führen
Gelöschte Beiträge SHALL in der Entwurfsliste und in der Sicherheitsabfrage von „Veröffentlichen“ als „Gelöscht“ aufgeführt werden. „Verwerfen“ des Eintrags SHALL das Verzeichnis (und eine zugehörige Weiterleitung) wiederherstellen. Der Beitrag bleibt auf dem Server und im Blog, bis veröffentlicht wird.

#### Scenario: Wiederherstellen
- **WHEN** der Nutzer bei „Alraune“ (Gelöscht) „Verwerfen“ wählt
- **THEN** ist das Verzeichnis `content/posts/alraune/` auf `draft` wieder wie auf `main`, und ein zugehöriger Weiterleitungseintrag ist entfernt

#### Scenario: Veröffentlichen
- **WHEN** der Nutzer „Veröffentlichen“ bestätigt und „Alraune“ als „Gelöscht“ aufgeführt ist
- **THEN** fehlt der Beitrag nach dem Zusammenführen auf `main`

### Requirement: Weiterleitung der alten Adresse
Beim Löschen eines veröffentlichten Beitrags SHALL die App fragen, wohin die alte Adresse weiterleiten soll: Beitragsübersicht `/beitraege/` (Vorgabe), Startseite `/`, ein anderer Beitrag oder keine Weiterleitung. Bei einer Weiterleitung hängt die App `<alter Pfad>: <neuer Pfad>` an `content/redirects.yaml` an. Alter Pfad ist der `permalink`, sonst die vom Blog aus Datum und Verzeichnisname abgeleitete Adresse.

#### Scenario: Standard
- **WHEN** der Beitrag den `permalink: /2014/01/alraune/` hat und der Nutzer die Vorgabe wählt
- **THEN** endet `content/redirects.yaml` mit der Zeile `/2014/01/alraune/: /beitraege/`, vorhandene Zeilen und Kommentare bleiben unverändert

#### Scenario: Auf anderen Beitrag
- **WHEN** der Nutzer als Ziel den Beitrag „Ohrenschützer“ mit `permalink: /2014/02/ohrenschuetzer/` wählt
- **THEN** lautet die Zeile `/2014/01/alraune/: /2014/02/ohrenschuetzer/`

#### Scenario: Ohne Permalink
- **WHEN** der Beitrag im Verzeichnis `grüne-mütze` vom 2026-11-01 keinen `permalink` hat
- **THEN** ist der alte Pfad `/2026/11/gruene-muetze/`

#### Scenario: Keine Weiterleitung
- **WHEN** der Nutzer „Keine Weiterleitung“ wählt
- **THEN** ändert die App `content/redirects.yaml` nicht

### Requirement: Warnung bei Verweisen
Verweist ein anderer Beitrag per Link auf die Adresse des zu löschenden Beitrags, SHALL die Bestätigung darauf hinweisen und die Titel der verweisenden Beiträge (höchstens zehn) nennen.

#### Scenario: Interner Link
- **WHEN** der Beitrag „Ohrenschützer“ einen Link `/2014/01/alraune/` enthält und „Alraune“ gelöscht werden soll
- **THEN** nennt die Bestätigung „Ohrenschützer“ als Beitrag mit einem Link auf diese Adresse
