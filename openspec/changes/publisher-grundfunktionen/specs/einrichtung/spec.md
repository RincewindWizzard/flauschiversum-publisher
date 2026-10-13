# Spec Delta

## Purpose

Beschreibt den geführten Ersteinrichtungsablauf von der Repository-Adresse über SSH-Schlüssel und Eintrag bei GitHub bis zum geklonten, geprüften Blog-Repository, einschließlich Wiederaufnahme und späterer Zugangsprobleme.

## ADDED Requirements

### Requirement: Geführter Ablauf in festen Schritten
Solange die Einrichtung nicht abgeschlossen ist, SHALL die App einen geführten Ablauf mit den Schritten 1 Repository, 2 Schlüssel, 3 Eintrag bei GitHub, 4 Verbindung prüfen und 5 Repository holen zeigen („Schritt x von 5“). Ein Schritt SHALL erst weiterführen, wenn er erfüllt ist; erfüllte Schritte lassen sich wiederholen. Nach Schritt 5 öffnet die App die Beitragsübersicht.

#### Scenario: Neustart ohne Einrichtung
- **WHEN** die App zum ersten Mal gestartet wird
- **THEN** zeigt sie Schritt 1 von 5 mit der vorbelegten Repository-Adresse

#### Scenario: Schritt nicht übersprungen
- **WHEN** noch kein Schlüssel erzeugt wurde
- **THEN** ist der Weg zu Schritt 3 gesperrt

#### Scenario: Abschluss
- **WHEN** der Klon geprüft und fertig ist
- **THEN** öffnet die App die Beitragsübersicht und zeigt den Ablauf nicht mehr

### Requirement: Wiederaufnahme
Die App SHALL den Fortschritt der Einrichtung dauerhaft speichern. Nach einem Abbruch oder App-Neustart SHALL sie beim ersten noch nicht erfüllten Schritt weitermachen. Ein unvollständiger Klon wird dabei gelöscht und Schritt 5 neu angeboten.

#### Scenario: App beendet nach Schritt 3
- **WHEN** der Nutzer die App nach dem Anzeigen des Schlüssels beendet und später wieder startet
- **THEN** startet die App mit Schritt 3 (Eintrag bei GitHub), nicht mit Schritt 1

#### Scenario: Klon unterbrochen
- **WHEN** die App während des Klonens beendet wurde
- **THEN** meldet sie beim nächsten Start den Abbruch, entfernt den halben Klon und bietet „Repository holen“ erneut an

### Requirement: Repository-Adresse
Schritt 1 SHALL die Adresse des Repositorys als SSH-Adresse (`git@github.com:<owner>/<repo>.git` oder `ssh://git@github.com/<owner>/<repo>.git`) mit der Vorgabe `git@github.com:RincewindWizzard/flauschiversum.git` erfassen. Eine HTTPS-Adresse SHALL die App erkennen, erklären, dass der Zugang per SSH erfolgt, und die passende SSH-Adresse vorschlagen. Andere Hosts als `github.com` und ungültige Adressen werden mit einer verständlichen Meldung abgelehnt.

#### Scenario: HTTPS eingegeben
- **WHEN** der Nutzer `https://github.com/RincewindWizzard/flauschiversum` eingibt
- **THEN** schlägt die App `git@github.com:RincewindWizzard/flauschiversum.git` vor und übernimmt es nach Bestätigung

#### Scenario: Anderer Host
- **WHEN** der Nutzer `git@gitlab.com:x/y.git` eingibt
- **THEN** lehnt die App ab und nennt, dass nur `github.com` unterstützt wird

#### Scenario: Gültige Adresse
- **WHEN** der Nutzer die Vorgabe unverändert bestätigt
- **THEN** merkt sich die App Eigentümer `RincewindWizzard` und Repository `flauschiversum`

### Requirement: Anleitung zum Eintrag bei GitHub
Schritt 3 SHALL den öffentlichen Schlüssel anzeigen und kopierbar machen, eine Schaltfläche „GitHub-Seite öffnen“ bieten, die die Seite zum Hinzufügen eines Deploy Keys des Repositorys öffnet (`https://github.com/<owner>/<repo>/settings/keys/new`), und die nötigen Handgriffe nennen: Titel (die App schlägt „Flauschiversum Publisher“ plus Gerätename vor), Schlüssel einfügen, „Allow write access“ anhaken, speichern. Die App SHALL ein Zurückkehren zur App mit „Weiter zur Prüfung“ anbieten.

#### Scenario: Seite öffnen
- **WHEN** der Nutzer „GitHub-Seite öffnen“ wählt
- **THEN** öffnet der Browser die Seite zum Hinzufügen eines Deploy Keys des gewählten Repositorys

#### Scenario: Schlüssel kopieren und zurückkehren
- **WHEN** der Nutzer den Schlüssel kopiert, bei GitHub einträgt und zur App zurückkehrt
- **THEN** zeigt die App weiterhin Schritt 3 mit „Weiter zur Prüfung“

### Requirement: Verbindung und Schreibrecht prüfen
Schritt 4 SHALL prüfen, ob die Anmeldung mit dem Schlüssel gelingt und ob der Schlüssel Schreibrecht hat, ohne dabei etwas im Repository zu verändern. Die Meldung SHALL die Ursache nennen: Schlüssel nicht eingetragen, nur Leserecht („Allow write access“ fehlt), Repository nicht gefunden, kein Netz, Port 22 nicht erreichbar. Nur bei Erfolg geht der Ablauf weiter.

#### Scenario: Nur Leserecht
- **WHEN** der Deploy Key ohne „Allow write access“ eingetragen wurde
- **THEN** meldet die App „Der Schlüssel hat nur Leserecht“ und verweist auf den Eintrag bei GitHub

#### Scenario: Port 22 blockiert
- **WHEN** das Netz Port 22 blockiert und die Verbindung ins Leere läuft
- **THEN** meldet die App, dass SSH über Port 22 nicht erreichbar ist, und rät zu einem anderen Netz (z. B. Mobilfunk)

#### Scenario: Erfolg
- **WHEN** Anmeldung und Schreibrecht funktionieren
- **THEN** zeigt die App „Zugang eingerichtet“ und führt zu Schritt 5, ohne das Repository zu ändern

### Requirement: Repository holen
Schritt 5 SHALL vor dem Klonen auf die Datenmenge (mehrere hundert MB) und den Speicherbedarf hinweisen, bei Mobilfunkverbindung ausdrücklich nachfragen und mindestens 1,5 GB freien Speicher verlangen. Danach klont die App den Zielbranch flach (nur der aktuelle Stand, Tiefe 1) mit Fortschrittsanzeige und Abbruchmöglichkeit.

#### Scenario: Mobilfunk
- **WHEN** das Handy per Mobilfunk verbunden ist und der Nutzer „Repository holen“ wählt
- **THEN** fragt die App, ob die Daten wirklich über Mobilfunk geladen werden sollen

#### Scenario: Zu wenig Speicher
- **WHEN** weniger als 1,5 GB frei sind
- **THEN** beginnt die App nicht zu klonen und nennt den fehlenden Platz

#### Scenario: Abbruch
- **WHEN** der Nutzer während des Klonens abbricht
- **THEN** löscht die App den unvollständigen Klon und bietet einen neuen Versuch an

### Requirement: Prüfung des Klons
Nach dem Klonen SHALL die App prüfen, ob der Zielbranch existiert, `content/posts/` mindestens einen Beitrag mit `index.md` und `content/tags.yaml` lesbar enthält. Schlägt eine Prüfung fehl, SHALL die App den Grund nennen und Adresse oder Zielbranch ändern sowie neu klonen lassen.

#### Scenario: Zielbranch fehlt
- **WHEN** das Repository keinen Branch `main` hat
- **THEN** meldet die App „Branch main nicht gefunden“ und lässt einen anderen Zielbranch wählen

#### Scenario: Falsches Repository
- **WHEN** das Repository kein `content/posts/` enthält
- **THEN** meldet die App, dass es kein Flauschiversum-Blog zu sein scheint, und behält die Einrichtung offen

### Requirement: Zugangsprobleme nach der Einrichtung
Schlägt der Zugang später dauerhaft fehl (z. B. weil der Deploy Key bei GitHub gelöscht wurde), SHALL die App einmalig und ohne Dialog einen Hinweis „Zugriff verweigert“ mit Weg zur Prüfung und zum erneuten Eintragen zeigen. Einzelne Netzfehler lösen den Hinweis nicht aus. In den Einstellungen SHALL der Nutzer das Repository neu holen (Klon löschen, Schlüssel behalten) und den Schlüssel ersetzen können.

#### Scenario: Schlüssel bei GitHub gelöscht
- **WHEN** die Hintergrundaktualisierung zweimal hintereinander mit „Zugriff verweigert“ scheitert
- **THEN** zeigt die App einen Hinweis mit Sprung zu Schritt 3 und 4, ohne die Bedienung zu sperren

#### Scenario: Kurzer Netzfehler
- **WHEN** eine Aktualisierung wegen fehlendem Netz scheitert
- **THEN** erscheint kein Hinweis zum Zugang

#### Scenario: Repository neu holen
- **WHEN** der Nutzer in den Einstellungen „Repository neu holen“ bestätigt
- **THEN** löscht die App den Klon, behält den Schlüssel und startet bei Schritt 5
