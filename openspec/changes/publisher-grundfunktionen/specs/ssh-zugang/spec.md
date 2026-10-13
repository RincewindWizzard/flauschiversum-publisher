# Spec Delta

## Purpose

Beschreibt, wie sich die App gegenüber dem Git-Server per SSH ausweist: Erzeugen und Verwahren des Schlüssels, Weitergabe des öffentlichen Schlüssels und Absicherung gegen falsche Server.

## ADDED Requirements

### Requirement: Schlüsselpaar in der App erzeugen
Die App SHALL auf Wunsch des Nutzers ein SSH-Schlüsselpaar vom Typ Ed25519 erzeugen. Der private Schlüssel SHALL ausschließlich im geschützten Speicher der App liegen und die App nie verlassen, auch nicht in Android-Backups.

#### Scenario: Erster Start ohne Schlüssel
- **WHEN** die App gestartet wird und noch kein Schlüssel existiert
- **THEN** führt der Einrichtungsablauf (Spec `einrichtung`) zum Erzeugen des Schlüsselpaars, und ohne Schlüssel ist keine Git-Aktion möglich

#### Scenario: Schlüssel existiert bereits
- **WHEN** bereits ein Schlüssel vorhanden ist und der Nutzer „Neu erzeugen“ wählt
- **THEN** warnt die App, dass der alte Schlüssel unwiderruflich ersetzt wird, und erzeugt erst nach Bestätigung einen neuen

#### Scenario: Backup
- **WHEN** Android ein Backup der App-Daten erstellt
- **THEN** ist der private Schlüssel darin nicht enthalten

### Requirement: Öffentlichen Schlüssel weitergeben
Die App SHALL den öffentlichen Schlüssel im OpenSSH-Format (`ssh-ed25519 …`) anzeigen und in die Zwischenablage kopieren oder über die Teilen-Funktion weitergeben können, damit der Nutzer ihn bei GitHub als Deploy Key mit Schreibrecht für `RincewindWizzard/flauschiversum` eintragen kann.

#### Scenario: Schlüssel kopieren
- **WHEN** der Nutzer „Öffentlichen Schlüssel kopieren“ wählt
- **THEN** liegt die einzeilige Schlüsselzeile in der Zwischenablage und die App bestätigt das Kopieren

### Requirement: Host-Schlüssel des Servers prüfen
Die App SHALL die veröffentlichten Host-Schlüssel von `github.com` (Ed25519, ECDSA, RSA) fest hinterlegt haben und nur Verbindungen zu Servern aufbauen, deren Host-Schlüssel einem davon entspricht. Die SHA256-Fingerabdrücke SHALL in der App einsehbar sein, damit der Nutzer sie mit der Dokumentation von GitHub vergleichen kann. Bei Abweichung SHALL die Verbindung abgebrochen und eine Fehlermeldung angezeigt werden.

#### Scenario: Fingerabdruck einsehen
- **WHEN** der Nutzer in den Einstellungen „Host-Schlüssel“ öffnet
- **THEN** zeigt die App die hinterlegten Fingerabdrücke (SHA256) von `github.com`

#### Scenario: Unbekannter oder geänderter Host-Schlüssel
- **WHEN** der Server einen anderen Host-Schlüssel als den hinterlegten vorweist
- **THEN** bricht die App die Verbindung ab und meldet, dass dem Server nicht vertraut wird

### Requirement: Verbindung testen
Die App SHALL eine Funktion „Verbindung testen“ anbieten, die ohne Änderung am Repository die Anmeldung und das Schreibrecht des Schlüssels prüft und das Ergebnis verständlich meldet (erfolgreich, Schlüssel nicht eingetragen, nur Leserecht, Repository nicht gefunden, kein Netz, Port 22 nicht erreichbar). Der Ablauf der Einrichtung steht in der Spec `einrichtung`.

#### Scenario: Schlüssel noch nicht bei GitHub eingetragen
- **WHEN** der Nutzer die Verbindung testet und GitHub den Schlüssel nicht akzeptiert
- **THEN** meldet die App, dass der Schlüssel bei GitHub nicht eingetragen ist, und verweist auf die Anzeige des öffentlichen Schlüssels

#### Scenario: Kein Netz
- **WHEN** das Handy keine Internetverbindung hat
- **THEN** meldet die App „Kein Netz“ statt eines Authentifizierungsfehlers
