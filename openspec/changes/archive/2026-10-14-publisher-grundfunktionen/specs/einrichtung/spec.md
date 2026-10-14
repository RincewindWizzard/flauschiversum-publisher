# Spec Delta

## Purpose

Beschreibt den geführten Ersteinrichtungsablauf von der Repository-Adresse über SSH-Schlüssel und Eintrag auf dem Server bis zum geklonten, geprüften Blog-Repository, einschließlich Wiederaufnahme und späterer Zugangsprobleme.

## ADDED Requirements

### Requirement: Geführter Ablauf in festen Schritten
Solange die Einrichtung nicht abgeschlossen ist, SHALL die App einen geführten Ablauf mit den Schritten 1 Repository, 2 Schlüssel, 3 Schlüssel auf dem Server eintragen, 4 Verbindung prüfen und 5 Repository holen zeigen („Schritt x von 5“). Ein Schritt SHALL erst weiterführen, wenn er erfüllt ist; erfüllte Schritte lassen sich wiederholen. Nach Schritt 5 öffnet die App die Beitragsübersicht.

#### Scenario: Start ohne Einrichtung
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
- **THEN** startet die App mit Schritt 3, nicht mit Schritt 1

#### Scenario: Klon unterbrochen
- **WHEN** die App während des Klonens beendet wurde
- **THEN** meldet sie beim nächsten Start den Abbruch, entfernt den halben Klon und bietet „Repository holen“ erneut an

### Requirement: Repository-Adresse
Schritt 1 SHALL die Adresse des Repositorys als SSH-Adresse (`benutzer@host:pfad` oder `ssh://benutzer@host[:port]/pfad`, Port Standard 22) für einen beliebigen Host erfassen, vorbelegt mit `git@github.com:RincewindWizzard/flauschiversum.git`. HTTP(S)-Adressen und ungültige Eingaben SHALL die App mit verständlicher Meldung ablehnen.

#### Scenario: Eigener Server
- **WHEN** der Nutzer `ssh://git@vps.beispiel.de:2222/srv/git/flauschiversum.git` eingibt
- **THEN** übernimmt die App Host `vps.beispiel.de`, Port 2222 und den Pfad

#### Scenario: Kurzform
- **WHEN** der Nutzer `git@vps.beispiel.de:flauschiversum.git` eingibt
- **THEN** übernimmt die App Host `vps.beispiel.de`, Port 22 und den Pfad `flauschiversum.git`

#### Scenario: HTTPS eingegeben
- **WHEN** der Nutzer `https://github.com/RincewindWizzard/flauschiversum` eingibt
- **THEN** erklärt die App, dass der Zugang per SSH erfolgt, schlägt `git@github.com:RincewindWizzard/flauschiversum.git` vor und übernimmt es nach Bestätigung

#### Scenario: Ungültige Adresse
- **WHEN** der Nutzer `flauschiversum` ohne Host eingibt
- **THEN** lehnt die App ab und zeigt Beispiele für gültige SSH-Adressen

### Requirement: Anleitung zum Eintragen des Schlüssels
Schritt 3 SHALL den öffentlichen Schlüssel anzeigen und kopierbar machen und je nach Server erklären, wie er eingetragen wird: bei `github.com` über die Deploy-Key-Seite des Repositorys (mit Schreibrecht), bei anderen Servern über einen kopierbaren Befehl für `~/.ssh/authorized_keys` samt Hinweis auf Deploy Keys bei Git-Hostern.

#### Scenario: GitHub
- **WHEN** die Repository-Adresse `github.com` enthält und der Nutzer „GitHub-Seite öffnen“ wählt
- **THEN** öffnet der Browser `https://github.com/<owner>/<repo>/settings/keys/new`, und die App nennt die Handgriffe: Titel (Vorschlag „Flauschiversum Publisher“ plus Gerätename), Schlüssel einfügen, „Allow write access“ anhaken, speichern

#### Scenario: Eigener Server
- **WHEN** die Repository-Adresse auf `vps.beispiel.de` zeigt
- **THEN** zeigt die App einen kopierbaren Befehl, der die Schlüsselzeile an `~/.ssh/authorized_keys` des Benutzers anhängt, und keinen GitHub-Hinweis

#### Scenario: Schlüssel kopieren und zurückkehren
- **WHEN** der Nutzer den Schlüssel kopiert, auf dem Server einträgt und zur App zurückkehrt
- **THEN** zeigt die App weiterhin Schritt 3 mit „Weiter zur Prüfung“

### Requirement: Verbindung und Schreibrecht prüfen
Schritt 4 SHALL prüfen, ob die Anmeldung mit dem Schlüssel gelingt und, soweit der Server es erkennen lässt, ob der Schlüssel Schreibrecht hat, ohne das Repository zu verändern. Die Meldung SHALL die Ursache nennen (Schlüssel nicht akzeptiert, Schreibrecht fehlt, Repository nicht gefunden, kein Netz, Server nicht erreichbar). Nur bei Erfolg geht der Ablauf weiter.

#### Scenario: Nur Leserecht bei GitHub
- **WHEN** der Deploy Key bei GitHub ohne „Allow write access“ eingetragen wurde
- **THEN** meldet die App „Der Schlüssel hat nur Leserecht“ und verweist auf den Eintrag bei GitHub

#### Scenario: Schreibrecht nicht prüfbar
- **WHEN** der Server die Anmeldung akzeptiert, das Schreibrecht aber nicht ohne Push erkennen lässt
- **THEN** meldet die App „Zugang erfolgreich, Schreibrecht wird beim ersten Veröffentlichen bestätigt“ und der Ablauf geht weiter

#### Scenario: Host-Schlüssel bestätigen
- **WHEN** der Server zum ersten Mal kontaktiert wird
- **THEN** zeigt die App vor dem Test Typ und Fingerabdruck seines Host-Schlüssels (Spec `ssh-zugang`) und testet erst nach „Vertrauen“

#### Scenario: Server nicht erreichbar
- **WHEN** der Server auf `vps.beispiel.de:22` nicht antwortet
- **THEN** meldet die App, dass Host und Port nicht erreichbar sind, und nennt mögliche Ursachen (Netz, Firewall, falscher Port)

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
Schlägt der Zugang später dauerhaft fehl (z. B. weil der Schlüssel auf dem Server oder beim Git-Hoster entfernt wurde), SHALL die App einmalig und ohne Dialog einen Hinweis „Zugriff verweigert“ mit Weg zur Prüfung und zum erneuten Eintragen zeigen. Einzelne Netzfehler lösen den Hinweis nicht aus. In den Einstellungen SHALL der Nutzer das Repository neu holen (Klon löschen, Schlüssel behalten), den Schlüssel ersetzen und die Repository-Adresse ändern können.

#### Scenario: Schlüssel entfernt
- **WHEN** die Hintergrundaktualisierung zweimal hintereinander mit „Zugriff verweigert“ scheitert
- **THEN** zeigt die App einen Hinweis mit Sprung zu Schritt 3 und 4, ohne die Bedienung zu sperren

#### Scenario: Kurzer Netzfehler
- **WHEN** eine Aktualisierung wegen fehlendem Netz scheitert
- **THEN** erscheint kein Hinweis zum Zugang

#### Scenario: Repository neu holen
- **WHEN** der Nutzer in den Einstellungen „Repository neu holen“ bestätigt
- **THEN** löscht die App den Klon, behält den Schlüssel und startet bei Schritt 5

#### Scenario: Umzug auf einen anderen Server
- **WHEN** der Nutzer die Repository-Adresse auf einen anderen Server ändert
- **THEN** führt die App ihn erneut durch die Schritte 3 bis 5 für den neuen Server, den vorhandenen Schlüssel kann er weiterverwenden
