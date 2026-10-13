# Spec Delta

## Purpose

Beschreibt den lokalen Klon des Blog-Repositorys auf dem Handy und die daraus gelesenen Daten (Beitragsliste, erlaubte Schlagworte), auf denen alle anderen Funktionen aufbauen.

## ADDED Requirements

### Requirement: Lokaler Klon
Die App SHALL das Repository `RincewindWizzard/flauschiversum` per SSH in den privaten App-Speicher klonen, sobald ein funktionierender Schlüssel vorliegt. Repository-Adresse und Zielbranch SHALL in den Einstellungen änderbar sein; der Zielbranch ist standardmäßig `main`.

#### Scenario: Erster Klon
- **WHEN** der Schlüssel bei GitHub eingetragen ist und der Nutzer „Repository holen“ wählt
- **THEN** zeigt die App den Fortschritt, klont das Repository und meldet danach den Stand des Zielbranchs

#### Scenario: Klon bricht ab
- **WHEN** die Verbindung während des Klonens abreißt
- **THEN** meldet die App den Fehler, löscht den unvollständigen Klon und erlaubt einen neuen Versuch

#### Scenario: Zu wenig Speicherplatz
- **WHEN** der freie Speicher für den Klon nicht reicht
- **THEN** meldet die App den Platzmangel, bevor oder sobald das Klonen scheitert, und lässt keinen halben Klon zurück

### Requirement: Aktualisieren
Die App SHALL den Klon vor dem Anlegen oder Ändern eines Beitrags und auf Wunsch des Nutzers per Fetch aktualisieren. Lokale, noch nicht veröffentlichte Entwürfe SHALL dabei erhalten bleiben.

#### Scenario: Neue Beiträge auf dem Server
- **WHEN** auf dem Server neue Commits im Zielbranch liegen und der Nutzer aktualisiert
- **THEN** enthält die Beitragsliste danach auch die neuen Beiträge

#### Scenario: Offline
- **WHEN** keine Verbindung besteht
- **THEN** arbeitet die App mit dem letzten Stand weiter und zeigt an, wann zuletzt aktualisiert wurde

### Requirement: Beitragsliste
Die App SHALL aus dem Klon alle Beiträge unter `content/posts/` mit Titel, Datum und Verzeichnisname anzeigen, neueste zuerst. Beiträge, die nur auf einem Entwurfs-Branch liegen, SHALL als Entwurf gekennzeichnet werden.

#### Scenario: Liste anzeigen
- **WHEN** der Klon 194 Beiträge enthält
- **THEN** listet die App alle 194 mit Titel und Datum, den neuesten zuerst

#### Scenario: Entwurf in der Liste
- **WHEN** ein Beitrag nur auf `draft/alraune` existiert
- **THEN** erscheint er in der Liste mit der Kennzeichnung „Entwurf“

### Requirement: Erlaubte Schlagworte
Die App SHALL die erlaubten Schlagworte aus `content/tags.yaml` des Klons lesen und zur Auswahl anbieten. Die App darf keine Schlagworte vergeben, die dort nicht stehen.

#### Scenario: Auswahl
- **WHEN** der Nutzer beim Beitrag Schlagworte wählt
- **THEN** bietet die App genau die Namen aus `content/tags.yaml` an

#### Scenario: Neues Schlagwort gewünscht
- **WHEN** der Nutzer ein Thema braucht, das nicht in der Liste steht
- **THEN** weist die App darauf hin, dass es zuerst in `content/tags.yaml` ergänzt werden muss
