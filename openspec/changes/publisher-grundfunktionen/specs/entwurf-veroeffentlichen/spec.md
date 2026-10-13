# Spec Delta

## Purpose

Beschreibt den zweistufigen Weg zum Veröffentlichen: Änderungen landen zuerst auf einem Entwurfs-Branch `draft/*` und gehen erst durch einen bewussten „Veröffentlichen“-Schritt in der App in den Zielbranch.

## ADDED Requirements

### Requirement: Entwurfs-Branch je Beitrag
Beim Speichern eines neuen Beitrags SHALL die App einen Branch `draft/<name>` vom aktuellen Stand des Zielbranchs anlegen (`<name>` = Verzeichnisname des Beitrags). Jede weitere Änderung an diesem Beitrag aus der App SHALL auf diesen Branch committet werden. Der Zielbranch SHALL durch die App nicht direkt verändert werden, außer durch „Veröffentlichen“.

#### Scenario: Neuer Beitrag
- **WHEN** der Nutzer den Beitrag „Mütze“ speichert
- **THEN** existiert lokal der Branch `draft/muetze`, der genau einen Commit mit dem neuen Beitragsverzeichnis über dem Stand von `main` enthält

#### Scenario: Weiteres Foto zum Entwurf
- **WHEN** zu einem Beitrag mit bestehendem Branch `draft/muetze` ein Foto hinzukommt
- **THEN** wird ein weiterer Commit auf `draft/muetze` angelegt und `main` bleibt unverändert

### Requirement: Entwurf pushen
Nach jedem Commit auf einem Entwurfs-Branch SHALL die App den Branch per SSH nach `origin` pushen. Schlägt das Pushen fehl, bleibt der Commit lokal erhalten, die App zeigt „Noch nicht hochgeladen“ und bietet erneutes Pushen an.

#### Scenario: Push erfolgreich
- **WHEN** der Commit auf `draft/muetze` angelegt wurde und Netz besteht
- **THEN** liegt `draft/muetze` danach auch auf dem Server und die App zeigt den Entwurf als hochgeladen

#### Scenario: Push scheitert
- **WHEN** beim Pushen kein Netz besteht
- **THEN** bleibt der Commit lokal, der Entwurf trägt die Kennzeichnung „Noch nicht hochgeladen“, und die App versucht es auf Wunsch erneut

### Requirement: Entwürfe verwalten
Die App SHALL alle Branches `draft/*` (lokal und auf dem Server) als Liste offener Entwürfe mit Titel, Datum der letzten Änderung und Upload-Status anzeigen. Der Nutzer SHALL einen Entwurf öffnen, weiter bearbeiten oder nach Bestätigung verwerfen können (Branch lokal und auf dem Server löschen).

#### Scenario: Entwurf verwerfen
- **WHEN** der Nutzer bei `draft/muetze` „Verwerfen“ wählt und bestätigt
- **THEN** ist der Branch lokal und auf dem Server gelöscht und der Zielbranch unverändert

### Requirement: Veröffentlichen per Merge
Die App SHALL zu jedem Entwurf einen Button „Veröffentlichen“ anbieten. Nach einer Sicherheitsabfrage SHALL die App den Entwurfs-Branch in den Zielbranch zusammenführen, den Zielbranch pushen und den Entwurfs-Branch danach löschen. Zuvor SHALL die App den Zielbranch aktualisieren (mit sichtbarem Fortschritt) und, wenn der Entwurf nicht vollständig hochgeladen ist, ihn zuerst pushen.

#### Scenario: Erfolgreich veröffentlichen
- **WHEN** der Nutzer bei `draft/muetze` „Veröffentlichen“ bestätigt
- **THEN** enthält `main` auf dem Server den Beitrag, `draft/muetze` ist gelöscht und die App meldet „Veröffentlicht“

#### Scenario: Zielbranch ist weitergelaufen
- **WHEN** `main` auf dem Server seit dem Anlegen des Entwurfs neue Commits hat, die den Entwurf nicht berühren
- **THEN** führt die App den Entwurf trotzdem zusammen und veröffentlicht ihn

#### Scenario: Konflikt
- **WHEN** das Zusammenführen Dateien betrifft, die auch im Zielbranch geändert wurden, sodass ein Konflikt entsteht
- **THEN** bricht die App das Zusammenführen ab, lässt den Zielbranch unverändert und meldet, welche Dateien betroffen sind

#### Scenario: Push des Zielbranchs wird abgelehnt
- **WHEN** der Server den Push auf den Zielbranch ablehnt (z. B. Branch-Schutz)
- **THEN** zeigt die App die Ablehnung, löscht den Entwurf nicht und der Nutzer kann es später erneut versuchen

### Requirement: Commit-Angaben
Commits der App SHALL eine verständliche deutschsprachige Nachricht tragen (z. B. „Beitrag: Mütze“ bzw. „Foto zu Mütze hinzugefügt“) und als Autor den in den Einstellungen hinterlegten Namen und die E-Mail-Adresse.

#### Scenario: Commit eines neuen Beitrags
- **WHEN** der Beitrag „Mütze“ committet wird
- **THEN** lautet die Commit-Nachricht „Beitrag: Mütze“ und der Autor entspricht den Einstellungen
