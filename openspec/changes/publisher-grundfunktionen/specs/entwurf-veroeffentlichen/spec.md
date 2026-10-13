# Spec Delta

## Purpose

Beschreibt den zweistufigen Weg zum Veröffentlichen: Änderungen landen zuerst auf dem gemeinsamen Branch `draft` und gehen erst durch einen bewussten „Veröffentlichen“-Schritt in der App in den Zielbranch.

## ADDED Requirements

### Requirement: Gemeinsamer Entwurfs-Branch
Die App SHALL alle Änderungen (neue und geänderte Beiträge, Fotos) auf dem einen Branch `draft` committen. Existiert er nicht, legt die App ihn vom aktuellen Stand des Zielbranchs an. Der Zielbranch SHALL durch die App nicht direkt verändert werden, außer durch „Veröffentlichen“.

#### Scenario: Erster Entwurf
- **WHEN** der Nutzer den Beitrag „Mütze“ speichert und noch kein Branch `draft` existiert
- **THEN** legt die App `draft` vom Stand von `main` an und committet den Beitrag dort; `main` bleibt unverändert

#### Scenario: Zweiter Beitrag
- **WHEN** auf `draft` bereits „Mütze“ liegt und der Nutzer den Beitrag „Schal“ speichert
- **THEN** kommt ein weiterer Commit auf `draft`, und beide Beiträge sind Teil des Entwurfs

#### Scenario: Weiteres Foto
- **WHEN** zu einem Beitrag ein Foto hinzukommt
- **THEN** wird ein weiterer Commit auf `draft` angelegt und `main` bleibt unverändert

### Requirement: Entwurf pushen
Nach jedem Commit auf `draft` SHALL die App den Branch per SSH nach `origin` pushen. Schlägt das Pushen fehl, bleibt der Commit lokal erhalten, die App zeigt „Noch nicht hochgeladen“ und bietet erneutes Pushen an.

#### Scenario: Push erfolgreich
- **WHEN** der Commit auf `draft` angelegt wurde und Netz besteht
- **THEN** liegt `draft` danach auch auf dem Server und die App zeigt den Entwurf als hochgeladen

#### Scenario: Push scheitert
- **WHEN** beim Pushen kein Netz besteht
- **THEN** bleibt der Commit lokal, der Entwurf trägt die Kennzeichnung „Noch nicht hochgeladen“, und die App versucht es auf Wunsch erneut

### Requirement: Entwürfe je Beitrag erkennen
Die App SHALL die offenen Entwürfe aus den Änderungen ableiten, die `draft` gegenüber dem gemeinsamen Vorfahren mit dem Zielbranch selbst mitbringt und die vom Zielbranch noch abweichen: Jeder Beitrag, dessen Verzeichnis unter `content/posts/` so neu, geändert oder gelöscht ist, gilt als Entwurf („Neu“, „Geändert“ oder „Gelöscht“). Weitere Branches dafür gibt es nicht.

#### Scenario: Neuer Beitrag
- **WHEN** `content/posts/muetze/` auf `draft`, aber nicht auf `main` existiert
- **THEN** führt die App „Mütze“ als Entwurf „Neu“

#### Scenario: Geänderter Beitrag
- **WHEN** nur `content/posts/alraune/index.md` auf `draft` von `main` abweicht
- **THEN** führt die App „Alraune“ als Entwurf „Geändert“ und keinen anderen Beitrag

#### Scenario: Gelöschter Beitrag
- **WHEN** `content/posts/alraune/` auf `draft` fehlt, auf `main` aber existiert
- **THEN** führt die App „Alraune“ als Entwurf „Gelöscht“

#### Scenario: Zielbranch ist weitergelaufen
- **WHEN** auf `main` ein neuer Beitrag „Neu“ hinzugekommen ist, den `draft` noch nicht kennt
- **THEN** zählt „Neu“ nicht als Entwurf, und „Mütze“ bleibt der einzige Entwurf

#### Scenario: Nichts offen
- **WHEN** `draft` und `main` denselben Inhalt haben
- **THEN** gibt es keine Entwürfe

### Requirement: Entwürfe verwerfen
Die App SHALL einzelne Entwürfe nach Bestätigung verwerfen können, indem sie das Beitragsverzeichnis auf `draft` mit einem neuen Commit auf den Stand des Zielbranchs zurücksetzt (bei neuen Beiträgen: entfernt) und pusht. Zusätzlich SHALL „Alle Entwürfe verwerfen“ den Branch `draft` auf den Stand des Zielbranchs zurücksetzen. Der Zielbranch bleibt in beiden Fällen unverändert.

#### Scenario: Einzelnen Entwurf verwerfen
- **WHEN** der Nutzer bei „Alraune“ (Geändert) „Verwerfen“ wählt und bestätigt
- **THEN** entspricht `content/posts/alraune/` auf `draft` wieder `main`, andere Entwürfe sind unberührt

#### Scenario: Neuen Entwurf verwerfen
- **WHEN** der Nutzer bei „Mütze“ (Neu) „Verwerfen“ wählt und bestätigt
- **THEN** ist `content/posts/muetze/` auf `draft` entfernt

#### Scenario: Alle verwerfen
- **WHEN** der Nutzer „Alle Entwürfe verwerfen“ bestätigt
- **THEN** zeigt `draft` denselben Inhalt wie `main` und es gibt keine Entwürfe mehr

### Requirement: Veröffentlichen per Merge
Die App SHALL einen Button „Veröffentlichen“ anbieten. Nach einer Sicherheitsabfrage, die alle betroffenen Beiträge auflistet, SHALL die App `draft` in den Zielbranch zusammenführen, den Zielbranch pushen und danach `draft` auf den Stand des Zielbranchs setzen. Zuvor aktualisiert die App den Zielbranch (mit sichtbarem Fortschritt) und pusht `draft`, falls nötig. Es werden stets alle Entwürfe gemeinsam veröffentlicht.

#### Scenario: Erfolgreich veröffentlichen
- **WHEN** „Mütze“ und „Alraune“ Entwürfe sind und der Nutzer „Veröffentlichen“ bestätigt
- **THEN** enthält `main` auf dem Server beide Beiträge, `draft` entspricht danach `main`, und die App meldet „Veröffentlicht“

#### Scenario: Liste in der Abfrage
- **WHEN** der Nutzer „Veröffentlichen“ wählt
- **THEN** nennt die Sicherheitsabfrage jeden betroffenen Beitrag mit „Neu“, „Geändert“ oder „Gelöscht“

#### Scenario: Zielbranch ist weitergelaufen
- **WHEN** `main` auf dem Server seit dem Anlegen des Entwurfs neue Commits hat, die den Entwurf nicht berühren
- **THEN** führt die App den Entwurf trotzdem zusammen und veröffentlicht ihn

#### Scenario: Flacher Klon
- **WHEN** der Klon flach ist und `main` auf dem Server weitergelaufen ist
- **THEN** lädt die App die dafür nötige Historie nach (Spec `repository-klon`) und veröffentlicht ohne Rückfrage

#### Scenario: Konflikt
- **WHEN** das Zusammenführen Dateien betrifft, die auch im Zielbranch geändert wurden, sodass ein Konflikt entsteht
- **THEN** bricht die App das Zusammenführen ab, lässt den Zielbranch und `draft` unverändert und meldet, welche Dateien betroffen sind

#### Scenario: Push des Zielbranchs wird abgelehnt
- **WHEN** der Server den Push auf den Zielbranch ablehnt (z. B. Branch-Schutz)
- **THEN** zeigt die App die Ablehnung, `draft` bleibt unverändert und der Nutzer kann es später erneut versuchen

### Requirement: Entwurf nachführen
Hat `draft` keine eigenen Änderungen gegenüber dem Zielbranch, SHALL die App ihn bei der Hintergrundaktualisierung auf den neuesten Stand des Zielbranchs vorsetzen (Fast-Forward, kein Merge-Commit). Hat `draft` eigene Änderungen, bleibt er bei der Hintergrundaktualisierung unverändert.

#### Scenario: Kein offener Entwurf
- **WHEN** `draft` dem alten `main` entspricht und der Fetch neue Commits auf `main` bringt
- **THEN** zeigt `draft` danach ebenfalls auf den neuen Stand von `main`

#### Scenario: Offener Entwurf
- **WHEN** `draft` einen Beitrag enthält, der nicht auf `main` liegt, und der Fetch neue Commits auf `main` bringt
- **THEN** bleibt `draft` unverändert, und die Entwurfsänderungen sind weiterhin erhalten

### Requirement: Commit-Angaben
Commits der App SHALL eine verständliche deutschsprachige Nachricht tragen (z. B. „Beitrag: Mütze“ bzw. „Foto zu Mütze hinzugefügt“) und als Autor den in den Einstellungen hinterlegten Namen und die E-Mail-Adresse.

#### Scenario: Commit eines neuen Beitrags
- **WHEN** der Beitrag „Mütze“ committet wird
- **THEN** lautet die Commit-Nachricht „Beitrag: Mütze“ und der Autor entspricht den Einstellungen
