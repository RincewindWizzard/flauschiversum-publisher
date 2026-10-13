# Spec Delta

## Purpose

Beschreibt, wie Teilen-Aufträge und Pushes zuverlässig im Hintergrund ausgeführt werden, auch bei schlechtem Netz oder wenn die App geschlossen wird.

## ADDED Requirements

### Requirement: Sofort sichern und zurückkehren
Beim Teilen von Fotos SHALL die App die geteilten Dateien sofort in ihren privaten Speicher kopieren, den Auftrag speichern und danach zur vorherigen App zurückkehren. Die Berechtigung auf die geteilten Dateien darf nicht erst im Hintergrund benötigt werden.

#### Scenario: Teilen aus der Galerie
- **WHEN** der Nutzer drei Fotos teilt und den Zielbeitrag gewählt hat
- **THEN** sind die Fotos innerhalb weniger Sekunden gesichert, die Galerie ist wieder im Vordergrund und der Auftrag läuft weiter

#### Scenario: Galerie-App beendet sich
- **WHEN** die Galerie nach dem Teilen beendet wird
- **THEN** geht der Auftrag nicht verloren, weil die Fotos bereits in der App liegen

### Requirement: Auftrag im Hintergrund ausführen
Die App SHALL Verkleinern, Rechteangaben, Einbinden unter „Anhänge“, Commit auf `draft` und Push als einen Hintergrundauftrag ausführen, der dauerhaft gespeichert wird und Prozessende, App-Schließen und Neustart des Geräts übersteht.

#### Scenario: App wird geschlossen
- **WHEN** der Nutzer die App nach dem Teilen aus der Übersicht entfernt
- **THEN** läuft der Auftrag weiter und endet mit Commit und Push

#### Scenario: Neustart des Handys
- **WHEN** das Gerät während eines wartenden Auftrags neu gestartet wird
- **THEN** setzt die App den Auftrag danach fort

### Requirement: Wiederholung bei Netzproblemen
Scheitert der Push an fehlendem oder schlechtem Netz, SHALL die App den Auftrag automatisch wiederholen, sobald eine Verbindung besteht, mit wachsendem Abstand (beginnend bei etwa 30 Sekunden bis höchstens einige Stunden). Bei dauerhaften Fehlern (Zugriff verweigert, Host-Schlüssel geändert) SHALL die App nicht wiederholen, sondern den Auftrag als fehlgeschlagen kennzeichnen und die Ursache nennen. Der lokale Commit bleibt in allen Fällen erhalten.

#### Scenario: Funkloch
- **WHEN** der Push wegen fehlendem Netz scheitert
- **THEN** zeigt die Ausgangsbox „Wartet auf Netz“, und der Push läuft von selbst, sobald Netz besteht

#### Scenario: Zugriff verweigert
- **WHEN** der Server den Schlüssel ablehnt
- **THEN** wird nicht wiederholt, der Auftrag erscheint als „Fehlgeschlagen: Zugriff verweigert“ mit dem Weg zur Einrichtung

#### Scenario: Commit bleibt
- **WHEN** ein Auftrag fehlschlägt
- **THEN** liegen Fotos und Commit weiterhin lokal, und der Entwurf trägt „Noch nicht hochgeladen“

### Requirement: Benachrichtigungen
Dauert ein Auftrag länger als zwei Sekunden, SHALL die App eine laufende Benachrichtigung mit Fortschritt zeigen und das Ergebnis (hochgeladen oder fehlgeschlagen) melden. Ein Tipp auf die Benachrichtigung öffnet die Ausgangsbox. Fehlt die Berechtigung für Benachrichtigungen, SHALL die App sie beim ersten Auftrag erbitten und weiterhin ohne sie funktionieren; Status zeigt dann nur die Ausgangsbox.

#### Scenario: Lange Übertragung
- **WHEN** ein Auftrag mit zehn Fotos mehrere Sekunden dauert
- **THEN** zeigt die Benachrichtigung „Fotos werden hochgeladen“ mit Fortschritt und danach „Hochgeladen“

#### Scenario: Berechtigung abgelehnt
- **WHEN** der Nutzer Benachrichtigungen ablehnt
- **THEN** laufen Aufträge trotzdem, und die Ausgangsbox zeigt den Stand

### Requirement: Ausgangsbox
Die App SHALL eine Ausgangsbox anbieten, die alle Aufträge mit Beitrag, Anzahl der Fotos, Zeitpunkt und Status (Wartet auf Netz, Läuft, Fehlgeschlagen, Erledigt) zeigt. Fehlgeschlagene Aufträge SHALL sich wiederholen lassen. Noch nicht gestartete Aufträge SHALL sich abbrechen lassen (die gesicherten Fotos werden dann gelöscht, nach Bestätigung). Erledigte Einträge verschwinden nach sieben Tagen.

#### Scenario: Erneut versuchen
- **WHEN** der Nutzer bei einem fehlgeschlagenen Auftrag „Wiederholen“ wählt
- **THEN** läuft der Auftrag erneut an

#### Scenario: Abbrechen vor dem Start
- **WHEN** ein Auftrag noch nicht begonnen hat und der Nutzer abbricht und bestätigt
- **THEN** werden die gesicherten Fotos gelöscht und der Auftrag verschwindet

### Requirement: Nur über WLAN
Die App SHALL die Einstellung „Nur über WLAN hochladen“ anbieten (Standard aus). Ist sie an, warten Pushes bei Mobilfunk auf WLAN; der Nutzer kann einen Auftrag in der Ausgangsbox trotzdem sofort über Mobilfunk starten.

#### Scenario: Mobilfunk mit Einstellung
- **WHEN** die Einstellung an ist und nur Mobilfunk besteht
- **THEN** zeigt die Ausgangsbox „Wartet auf WLAN“ mit der Aktion „Jetzt senden“

### Requirement: Reihenfolge und Konsistenz
Aufträge zu demselben Repository SHALL nacheinander ausgeführt werden, damit sich Commits nicht überschneiden. Die Ausführung SHALL dieselbe Git-Warteschlange nutzen wie manuelle Aktionen, und Eingaben des Nutzers (Speichern, Veröffentlichen) dürfen auf einen laufenden Auftrag nur so lange warten, wie dessen lokaler Commit dauert, nicht auf den Push.

#### Scenario: Zwei Aufträge
- **WHEN** der Nutzer kurz hintereinander zweimal Fotos teilt
- **THEN** laufen die Aufträge nacheinander, jeder mit eigenem Commit

#### Scenario: Speichern während eines Pushs
- **WHEN** ein Push läuft und der Nutzer im Editor speichert
- **THEN** wird der Commit sofort angelegt, und sein Push folgt nach dem laufenden
