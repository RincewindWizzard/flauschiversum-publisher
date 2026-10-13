# Spec Delta

## Purpose

Beschreibt das Aussehen der App im Stil des Blogs und ihre Bedienbarkeit für alle: Farben, Schriften, Symbol, Dunkelmodus und Barrierefreiheit.

## ADDED Requirements

### Requirement: Farbschema Flauschi-Nebel
Die App SHALL das Farbschema „Flauschi-Nebel“ des Blogs in Hell und Dunkel verwenden (Violett und Indigo mit Gold und Cyan als Akzenten). Die Farben SHALL an einer Stelle definiert sein und den Werten aus `content/style/theme.css` des Blogs entsprechen. Dynamische Systemfarben (Material You) werden nicht verwendet.

#### Scenario: Helles Gerät
- **WHEN** das Gerät im Hellmodus ist
- **THEN** zeigt die App einen hellen Lavendelgrund (`#f6f3fc`), dunklen Text (`#1d1930`) und den Akzent `#6d4fe0`

#### Scenario: Dunkles Gerät
- **WHEN** das Gerät im Dunkelmodus ist
- **THEN** zeigt die App das dunkle Schema des Blogs mit seinen Farbwerten

### Requirement: Modus wählbar
Die App SHALL in den Einstellungen die Wahl „System“ (Vorgabe), „Hell“ und „Dunkel“ anbieten und sofort anwenden.

#### Scenario: Dunkel erzwingen
- **WHEN** der Nutzer bei hellem Gerät „Dunkel“ wählt
- **THEN** wechselt die App sofort in den Dunkelmodus und merkt sich die Wahl

### Requirement: Kontrast
Text und Bedienelemente SHALL in beiden Modi mindestens die Kontraste nach WCAG AA erreichen: 4,5 : 1 für normalen Text, 3 : 1 für großen Text und Bedienelemente.

#### Scenario: Messung
- **WHEN** die Farbpaare des Schemas geprüft werden
- **THEN** erreicht jedes verwendete Paar aus Text/Hintergrund und Bedienelement/Hintergrund den Mindestwert

### Requirement: Schriften
Die App SHALL Lobster für den Namen und die Titel der Hauptbildschirme und Lora für den Beitragstext im Editor und in der Vorschau verwenden; Bedienelemente und Eingabefelder nutzen die Systemschrift. Die Schriftgröße des Editors SHALL einstellbar sein.

#### Scenario: Titel
- **WHEN** die Beitragsübersicht geöffnet ist
- **THEN** erscheint der Titel „Flauschiversum“ in Lobster

#### Scenario: Editorgröße
- **WHEN** der Nutzer die Editorschrift vergrößert
- **THEN** zeigt der Editor den Text größer, ohne dass andere Bereiche ihre Größe ändern

### Requirement: App-Symbol
Die App SHALL ein adaptives Launcher-Symbol mit dem Maskottchen des Blogs und ein einfarbiges Symbol für Android 13+ („Themed Icons“) haben.

#### Scenario: Launcher
- **WHEN** die App im Launcher angezeigt wird
- **THEN** zeigt sie das Maskottchen auf dem Hintergrund des Schemas

### Requirement: Barrierefreiheit
Alle Bedienelemente SHALL mindestens 48 × 48 dp groß sein, Beschriftungen für TalkBack haben, bei Systemschriftgröße bis 200 % ohne abgeschnittene Inhalte nutzbar sein und Fehler nie allein über Farbe anzeigen. Bewegte Elemente SHALL der Systemeinstellung „Animationen entfernen“ folgen.

#### Scenario: TalkBack
- **WHEN** TalkBack aktiv ist und der Nutzer eine Schaltfläche fokussiert
- **THEN** liest TalkBack eine verständliche Beschriftung vor

#### Scenario: Große Schrift
- **WHEN** die Systemschrift auf 200 % steht
- **THEN** bleiben Beschriftungen und Eingaben auf allen Bildschirmen lesbar und erreichbar

#### Scenario: Fehler im Feld
- **WHEN** ein Pflichtfeld fehlt
- **THEN** zeigt die App neben der Farbe ein Symbol und einen Text
