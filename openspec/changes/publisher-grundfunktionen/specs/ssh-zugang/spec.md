# Spec Delta

## Purpose

Beschreibt, wie sich die App gegenüber einem beliebigen Git-Server per SSH ausweist: Erzeugen und Verwahren des Schlüssels, Weitergabe des öffentlichen Schlüssels und Absicherung gegen falsche Server.

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
Die App SHALL den öffentlichen Schlüssel im OpenSSH-Format (`ssh-ed25519 …`) anzeigen und in die Zwischenablage kopieren oder über die Teilen-Funktion weitergeben können, damit der Nutzer ihn auf dem Server oder bei seinem Git-Hoster mit Schreibrecht für das Repository eintragen kann.

#### Scenario: Schlüssel kopieren
- **WHEN** der Nutzer „Öffentlichen Schlüssel kopieren“ wählt
- **THEN** liegt die einzeilige Schlüsselzeile in der Zwischenablage und die App bestätigt das Kopieren

### Requirement: Host-Schlüssel des Servers prüfen
Die App SHALL die Host-Schlüssel (Ed25519, ECDSA, RSA) der Server, mit denen sie spricht, nach dem Prinzip „beim ersten Mal bestätigen“ verwalten: Beim ersten Kontakt zeigt sie Schlüsseltyp und SHA256-Fingerabdruck und verbindet erst nach Bestätigung des Nutzers; der Schlüssel wird danach gespeichert. Für `github.com` ist der veröffentlichte Schlüssel fest hinterlegt und gilt ohne Rückfrage. Weicht der Schlüssel später ab, SHALL die App die Verbindung abbrechen und warnen.

#### Scenario: Erster Kontakt mit eigenem Server
- **WHEN** die App zum ersten Mal mit `vps.beispiel.de` spricht
- **THEN** zeigt sie Schlüsseltyp und Fingerabdruck zur Bestätigung und verbindet erst nach „Vertrauen“

#### Scenario: Bekannter Server
- **WHEN** der Server einen bereits bestätigten Schlüssel vorweist
- **THEN** verbindet die App ohne Rückfrage

#### Scenario: Geänderter Host-Schlüssel
- **WHEN** ein bekannter Server einen anderen Host-Schlüssel vorweist
- **THEN** bricht die App ab, warnt vor einem möglichen Angriff oder einer Neuinstallation und nennt beide Fingerabdrücke

#### Scenario: GitHub
- **WHEN** die App mit `github.com` spricht und der Server den veröffentlichten Schlüssel vorweist
- **THEN** verbindet die App ohne Rückfrage

### Requirement: Bekannte Server verwalten
Die App SHALL die bestätigten Host-Schlüssel in den Einstellungen mit Server, Typ und Fingerabdruck anzeigen und einzelne Einträge nach ausdrücklicher Warnung löschen lassen, damit ein gewollt neu installierter Server wieder bestätigt werden kann.

#### Scenario: Eintrag zurücksetzen
- **WHEN** der Nutzer den Eintrag von `vps.beispiel.de` löscht und bestätigt
- **THEN** fragt die App beim nächsten Kontakt wieder nach der Bestätigung des Schlüssels

### Requirement: Verbindung testen
Die App SHALL eine Funktion „Verbindung testen“ anbieten, die ohne Änderung am Repository die Anmeldung und, soweit der Server es erkennen lässt, das Schreibrecht des Schlüssels prüft und das Ergebnis verständlich meldet (erfolgreich, Schlüssel nicht akzeptiert, Schreibrecht fehlt, Repository nicht gefunden, kein Netz, Server auf Host und Port nicht erreichbar). Der Ablauf der Einrichtung steht in der Spec `einrichtung`.

#### Scenario: Schlüssel noch nicht eingetragen
- **WHEN** der Nutzer die Verbindung testet und der Server den Schlüssel nicht akzeptiert
- **THEN** meldet die App, dass der Schlüssel nicht eingetragen ist, und verweist auf die Anzeige des öffentlichen Schlüssels

#### Scenario: Kein Netz
- **WHEN** das Handy keine Internetverbindung hat
- **THEN** meldet die App „Kein Netz“ statt eines Authentifizierungsfehlers

#### Scenario: Server nicht erreichbar
- **WHEN** der Server auf dem angegebenen Host und Port keine Verbindung annimmt
- **THEN** meldet die App „Server nicht erreichbar“ mit Host und Port
