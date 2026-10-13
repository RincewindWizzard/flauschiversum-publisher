# Spec Delta

## Purpose

Beschreibt die Übersicht über alle Beiträge und Entwürfe, in der der Nutzer Beiträge findet, auswählt und weiterverarbeitet (bearbeiten, Foto hinzufügen, Entwurf veröffentlichen).

## ADDED Requirements

### Requirement: Übersicht als Startbildschirm
Die App SHALL nach dem Start (mit eingerichtetem Klon) die Übersicht „Beiträge“ zeigen. Jeder Eintrag SHALL Titelbild-Vorschau, Titel, Datum und Schlagworte anzeigen; die Reihenfolge ist standardmäßig nach Datum, neueste zuerst. Das Titelbild ist das Bild aus dem Feld `image`; fehlt es, das erste Bild des Beitrags, sonst ein Platzhalter.

#### Scenario: Eintrag mit Titelbild
- **WHEN** der Beitrag „ACAB-Silberring“ `image: "acab-silberring_thumb.jpg"` im Frontmatter hat und die Datei existiert
- **THEN** zeigt der Eintrag diese Vorschau, den Titel, „18.06.2017“ und das Schlagwort „Schmuck“

#### Scenario: Beitrag ohne Titelbild
- **WHEN** ein Beitrag kein Feld `image` hat, aber Bilder im Verzeichnis
- **THEN** zeigt der Eintrag das erste Bild des Verzeichnisses als Vorschau

#### Scenario: Beitrag ohne Bilder
- **WHEN** ein Beitrag weder `image` noch Bilder hat
- **THEN** zeigt der Eintrag einen Platzhalter

#### Scenario: Noch kein Klon
- **WHEN** das Repository noch nicht geklont ist
- **THEN** zeigt die Übersicht statt der Liste einen Hinweis mit dem Weg zur Einrichtung (Schlüssel, Repository holen)

### Requirement: Entwürfe zuerst
Die Übersicht SHALL offene Entwürfe (Beiträge, die auf dem Branch `draft` gegenüber dem Zielbranch neu oder geändert sind) in einem eigenen Abschnitt „Entwürfe“ oberhalb der übrigen Beiträge zeigen, mit Kennzeichnung „Entwurf“ und Upload-Status („Hochgeladen“ oder „Noch nicht hochgeladen“). Gibt es keine Entwürfe, entfällt der Abschnitt. Ein Beitrag mit Entwurf erscheint dort mit seinem Entwurfsstand und nicht zusätzlich in der übrigen Liste.

#### Scenario: Entwurf zu veröffentlichtem Beitrag
- **WHEN** `draft` Änderungen an einem veröffentlichten Beitrag „Alraune“ enthält
- **THEN** steht „Alraune“ im Abschnitt „Entwürfe“ mit dem Titel aus dem Entwurf und erscheint nicht ein zweites Mal in der Liste darunter

#### Scenario: Neuer Beitrag nur als Entwurf
- **WHEN** der Beitrag „Mütze“ nur auf `draft` existiert und noch nicht gepusht ist
- **THEN** steht er im Abschnitt „Entwürfe“ mit „Noch nicht hochgeladen“

#### Scenario: Keine Entwürfe
- **WHEN** `draft` und der Zielbranch denselben Inhalt haben oder `draft` nicht existiert
- **THEN** zeigt die Übersicht keinen Abschnitt „Entwürfe“

### Requirement: Suche
Die Übersicht SHALL ein Suchfeld bieten, das beim Tippen die Liste auf Beiträge einschränkt, deren Titel, Kurzbeschreibung, Schlagworte oder Text das Suchwort enthalten (ohne Beachtung der Groß-/Kleinschreibung; mehrere Wörter verlangen alle Treffer). Trefferstellen im Titel SHALL hervorgehoben werden.

#### Scenario: Suche im Titel
- **WHEN** der Nutzer „silber“ eingibt
- **THEN** zeigt die Liste unter anderem „ACAB-Silberring“

#### Scenario: Suche im Text
- **WHEN** der Nutzer „Goldschmiedekurs“ eingibt und dieses Wort nur im Text von „ACAB-Silberring“ steht
- **THEN** zeigt die Liste genau diesen Beitrag

#### Scenario: Keine Treffer
- **WHEN** kein Beitrag das Suchwort enthält
- **THEN** zeigt die Liste „Keine Beiträge gefunden“ und eine Aktion zum Zurücksetzen der Suche

### Requirement: Filter und Sortierung
Die Übersicht SHALL die Liste nach einem oder mehreren Schlagworten (Beitrag hat alle gewählten) und nach Jahr filtern lassen, in Kombination mit der Suche. Die Sortierung SHALL zwischen „Neueste zuerst“, „Älteste zuerst“ und „Titel A–Z“ wählbar sein. Aktive Filter sind sichtbar und einzeln entfernbar.

#### Scenario: Schlagwort und Jahr
- **WHEN** der Nutzer das Schlagwort „Schmuck“ und das Jahr 2017 wählt
- **THEN** zeigt die Liste nur Beiträge von 2017 mit dem Schlagwort „Schmuck“

#### Scenario: Filter entfernen
- **WHEN** der Nutzer den Filter „2017“ einzeln entfernt
- **THEN** gilt weiterhin der Filter „Schmuck“, aber nicht mehr nur für 2017

#### Scenario: Sortierung
- **WHEN** der Nutzer „Titel A–Z“ wählt
- **THEN** sind die Beiträge alphabetisch nach Titel sortiert, wobei Umlaute wie „Ä“ unter „A“ einsortiert werden

### Requirement: Beitrag auswählen und Aktionen
Ein Tipp auf einen Beitrag SHALL ihn im Markdown-Editor zum Bearbeiten öffnen. Ein Menü am Eintrag SHALL „Bearbeiten“, „Eigenschaften“ und „Foto hinzufügen“ anbieten, bei Entwürfen „Verwerfen“, und Raum für Aktionen späterer Changes lassen („Fotos“, „Vorschau“, „Löschen“). „Veröffentlichen“ steht gemeinsam für alle Entwürfe in der Kopfzeile des Abschnitts „Entwürfe“. Die Abläufe stammen aus den Specs `beitrag-bearbeiten`, `beitrag-eigenschaften`, `foto-teilen` und `entwurf-veroeffentlichen`.

#### Scenario: Beitrag bearbeiten
- **WHEN** der Nutzer „Alraune“ antippt
- **THEN** öffnet der Editor den Text von „Alraune“

#### Scenario: Foto hinzufügen
- **WHEN** der Nutzer im Menü von „Alraune“ „Foto hinzufügen“ wählt
- **THEN** öffnet die App die Fotoauswahl des Geräts, und gewählte Fotos werden wie beim Teilen dem Beitrag „Alraune“ zugeordnet

#### Scenario: Entwurf veröffentlichen
- **WHEN** der Nutzer im Abschnitt „Entwürfe“ „Veröffentlichen“ wählt
- **THEN** startet die Sicherheitsabfrage aus `entwurf-veroeffentlichen` mit der Liste aller Entwürfe

### Requirement: Auswahlmodus für geteilte Fotos
Kommt die App über „Teilen“ mit Fotos, SHALL die Übersicht im Auswahlmodus erscheinen: Oben zeigt sie die Vorschau der Fotos, ein Tipp auf einen Beitrag wählt ihn als Ziel, und ein Knopf „Neuer Beitrag“ legt einen neuen an. Such- und Filterfunktionen stehen zur Verfügung. Abbrechen verlässt den Modus, ohne etwas zu ändern.

#### Scenario: Ziel wählen
- **WHEN** der Nutzer drei Fotos teilt und in der Übersicht „Alraune“ antippt
- **THEN** werden die Fotos „Alraune“ zugeordnet (Ablauf aus `foto-teilen`)

#### Scenario: Suche im Auswahlmodus
- **WHEN** der Nutzer im Auswahlmodus „Alraune“ ins Suchfeld tippt
- **THEN** zeigt die Liste die passenden Beiträge, und der Modus bleibt aktiv

#### Scenario: Abbrechen
- **WHEN** der Nutzer im Auswahlmodus abbricht
- **THEN** wird nichts im Repository geändert

### Requirement: Neuer Beitrag
Die Übersicht SHALL eine Schaltfläche „Neuer Beitrag“ bieten, die den Editor mit leerem Beitrag öffnet.

#### Scenario: Neu anlegen
- **WHEN** der Nutzer „Neuer Beitrag“ antippt
- **THEN** öffnet sich der Editor mit leerem Text und dem Menü „Eigenschaften“ mit Vorgaben (heutiges Datum, Autor aus den Einstellungen)

### Requirement: Aktualisieren und Offline
Die Übersicht SHALL sich nach jeder Hintergrundaktualisierung (Spec `repository-klon`) selbst neu aufbauen, ohne die Bedienung zu unterbrechen. Wischen nach unten SHALL zusätzlich von Hand aktualisieren, währenddessen läuft ein Fortschrittsanzeiger. Die Übersicht zeigt, wann zuletzt aktualisiert wurde. Ohne Netz bleibt die Übersicht mit dem letzten Stand benutzbar.

#### Scenario: Neue Beiträge vom Server
- **WHEN** auf dem Server ein neuer Beitrag liegt und der Nutzer nach unten wischt
- **THEN** erscheint der Beitrag danach in der Liste

#### Scenario: Automatische Aktualisierung
- **WHEN** die App in den Vordergrund kommt und der Server einen neuen Beitrag hat
- **THEN** erscheint er in der Liste, ohne dass der Nutzer etwas tun muss und ohne dass die Liste springt, an der er gerade ist

#### Scenario: Offline
- **WHEN** das Handy keine Verbindung hat und der Nutzer von Hand aktualisieren will
- **THEN** meldet die App „Kein Netz“, die Liste bleibt unverändert

### Requirement: Flüssige Bedienung bei vielen Beiträgen
Die Übersicht SHALL auch bei mehr als 200 Beiträgen flüssig scrollen. Suchergebnisse SHALL nach spätestens einer Sekunde nach dem Tippen erscheinen, und Vorschaubilder SHALL klein geladen werden, nicht in voller Auflösung der Originale.

#### Scenario: Scrollen
- **WHEN** der Nutzer durch eine Liste mit 194 Beiträgen scrollt
- **THEN** bleibt das Scrollen flüssig, und Vorschaubilder erscheinen ohne die Liste zu blockieren

#### Scenario: Erster Start nach dem Klonen
- **WHEN** der Klon gerade fertig ist
- **THEN** zeigt die Übersicht die Liste, sobald der Index aufgebaut ist, und meldet den Fortschritt, falls das länger dauert
