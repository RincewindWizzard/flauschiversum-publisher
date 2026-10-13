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

### Requirement: Einstellungen
Die App SHALL in den Einstellungen Repository-Adresse, Zielbranch, Name und E-Mail für Commits, den Autor für neue Beiträge und den Rechtetext für Bilder ändern lassen und dauerhaft speichern. Vorgaben: Repository `RincewindWizzard/flauschiversum`, Zielbranch `main`, Rechtetext „Flauschiversum / Ermeline Wollknoll“.

#### Scenario: Vorgaben
- **WHEN** die App zum ersten Mal gestartet wird
- **THEN** zeigen die Einstellungen die Vorgaben für Repository, Zielbranch und Rechtetext

#### Scenario: Änderung bleibt erhalten
- **WHEN** der Nutzer den Commit-Namen ändert und die App neu startet
- **THEN** verwendet die App weiterhin den geänderten Namen

### Requirement: Aktualisieren im Hintergrund
Die App SHALL den Klon möglichst immer aktuell halten, damit Änderungen auf einem aktuellen Stand beginnen und Merge-Konflikte seltener werden: beim Start der App, wenn sie in den Vordergrund kommt, und bevor der Nutzer einen Beitrag öffnet, anlegt oder ein Foto hinzufügt. Das Aktualisieren (Fetch und Nachziehen des Zielbranchs) SHALL im Hintergrund laufen und die Bedienung nie blockieren. Entwürfe bleiben dabei unverändert erhalten.

#### Scenario: Beitrag öffnen während der Aktualisierung
- **WHEN** der Nutzer einen Beitrag öffnet und der Fetch noch läuft
- **THEN** öffnet sich der Editor sofort mit dem letzten bekannten Stand, und die Aktualisierung läuft im Hintergrund weiter

#### Scenario: Anzeige
- **WHEN** im Hintergrund aktualisiert wird
- **THEN** zeigt die App einen dezenten Fortschrittsindikator und keine Sperre oder Dialoge

#### Scenario: Zielbranch hat sich geändert
- **WHEN** der Fetch neue Commits im Zielbranch bringt
- **THEN** zieht die App den lokalen Zielbranch nach, und die Beitragsliste enthält danach auch die neuen Beiträge

#### Scenario: Offline oder Fehler
- **WHEN** keine Verbindung besteht oder der Fetch scheitert
- **THEN** arbeitet die App mit dem letzten Stand weiter, zeigt „zuletzt aktualisiert“ an und unterbricht den Nutzer nicht mit einer Fehlermeldung

#### Scenario: Mehrfach auslösen
- **WHEN** der Nutzer mehrmals kurz hintereinander Beiträge öffnet
- **THEN** läuft höchstens ein Fetch gleichzeitig, und ein soeben beendeter wird nicht sofort wiederholt

### Requirement: Manuelles Aktualisieren
Der Nutzer SHALL die Aktualisierung jederzeit von Hand anstoßen können. Dabei zeigt die App das Ergebnis (aktuell, Kein Netz, Fehler mit Grund) an.

#### Scenario: Von Hand aktualisieren
- **WHEN** der Nutzer die Aktualisierung anstößt und der Server nicht erreichbar ist
- **THEN** meldet die App „Kein Netz“ bzw. den Grund und lässt den Stand unverändert

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
