# beitragsliste-vorschaubild Specification

## Purpose
Beschreibt, wie die Beitragsliste das Beitragsbild als Vorschau neben dem Titel zeigt, damit sich Beiträge am Foto wiederfinden lassen.

## Requirements

### Requirement: Beitragsbild neben dem Titel
Jeder Eintrag der Beitragsliste (Beiträge, Entwürfe und Suchtreffer) SHALL neben dem Titel eine quadratisch zugeschnittene Vorschau des Beitragsbildes zeigen, mindestens 64 × 64 dp groß. Ist ein Bild vorhanden, SHALL kein Buchstabe als Ersatz erscheinen.

#### Scenario: Beitrag mit Bild
- **WHEN** ein Beitrag im Feld `image` die Datei `DSCF0529.jpg` nennt und sie im Beitragsverzeichnis liegt
- **THEN** zeigt der Listeneintrag neben dem Titel eine Vorschau dieses Bildes

#### Scenario: Entwurf und Suchtreffer
- **WHEN** ein Beitrag als Entwurf geführt wird oder in den Suchtreffern steht
- **THEN** zeigt sein Eintrag ebenfalls die Vorschau des Beitragsbildes

### Requirement: Auswahl des Bildes
Die Vorschau SHALL das Bild aus dem Feld `image` zeigen, wenn die Datei im Beitragsverzeichnis existiert, sonst das erste Bild des Beitrags (nach Dateinamen sortiert, ohne Beachtung der Groß-/Kleinschreibung der Endung). Gibt es kein Bild, SHALL ein neutrales Bildsymbol statt eines Buchstabens erscheinen.

#### Scenario: Ohne Feld image
- **WHEN** der Beitrag kein Feld `image` hat und die Dateien `b.jpg` und `a.png` enthält
- **THEN** zeigt die Vorschau `a.png`

#### Scenario: Feld image verweist auf fehlende Datei
- **WHEN** das Feld `image` eine nicht vorhandene Datei nennt
- **THEN** zeigt die Vorschau das erste vorhandene Bild des Beitrags

#### Scenario: Beitrag ohne Bilder
- **WHEN** das Beitragsverzeichnis kein Bild enthält
- **THEN** zeigt der Eintrag ein neutrales Bildsymbol und keinen Buchstaben

### Requirement: Schnell und sparsam laden
Die Vorschau SHALL verkleinert geladen werden (nicht das Original in voller Auflösung) und das Scrollen der Liste nicht blockieren. Bis das Bild da ist, SHALL das Bildsymbol erscheinen.

#### Scenario: Großes Foto
- **WHEN** das Beitragsbild ein Foto mit mehreren Megapixeln ist
- **THEN** wird es verkleinert dekodiert, und die Liste bleibt beim Scrollen flüssig

### Requirement: Ladefehler fallen zurück und werden wiederholt
Lässt sich ein Bild nicht laden oder dekodieren, SHALL der Eintrag das Bildsymbol zeigen. Der Fehlschlag SHALL nicht dauerhaft gemerkt werden: Beim nächsten Aufbau der Liste versucht die App das Bild erneut.

#### Scenario: Vorübergehend nicht lesbar
- **WHEN** das Bild beim ersten Aufbau nicht gelesen werden kann und beim nächsten Aufbau lesbar ist
- **THEN** erscheint die Vorschau dann mit dem Bild
