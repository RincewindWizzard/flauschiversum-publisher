# Tasks

## 1. Grundgerüst und Spike

- [ ] 1.1 Abhängigkeiten für Git über SSH (JGit mit SSH-Transport) und Ed25519 ergänzen; `./gradlew assembleDebug` läuft durch
- [ ] 1.2 Spike: Schlüssel erzeugen und gegen GitHub klonen/pushen (Testrepository, Debug-Bildschirm); Ergebnis (Ed25519 ja/nein, Fallback) in `design.md` unter Risiken festhalten
- [ ] 1.3 Paketstruktur `git`, `posts`, `images`, `ui` mit Navigation (Compose) anlegen und App startet im Emulator mit leerem Hauptbildschirm

## 2. SSH-Zugang (`ssh-zugang`)

- [ ] 2.1 Schlüsselerzeugung und -speicherung im privaten App-Speicher, von Backups ausgenommen (`backup_rules.xml`, `data_extraction_rules.xml`); Unit-Test: Schlüssel wird gespeichert, wieder geladen und ist im OpenSSH-Format ausgebbar
- [ ] 2.2 Bildschirm „Schlüssel“: erzeugen/ersetzen mit Warnung, öffentlichen Schlüssel kopieren und teilen; manuell im Emulator geprüft
- [ ] 2.3 Fester Host-Schlüssel von `github.com`; Test: abweichender Host-Schlüssel führt zum Verbindungsabbruch
- [ ] 2.4 „Verbindung testen“ mit unterscheidbaren Meldungen (ok, Schlüssel fehlt, kein Schreibrecht, kein Netz); Unit-Tests für die Fehlerzuordnung

## 3. Repository-Klon (`repository-klon`)

- [ ] 3.1 Einstellungen für Repository-Adresse, Zielbranch (Standard `main`), Autorname und E-Mail mit Speicherung; Test der Standardwerte
- [ ] 3.2 Klonen mit Fortschritt, Aufräumen bei Abbruch und Speicherplatzprüfung; Test mit lokalem Testrepository, Abbruch hinterlässt keinen Klon
- [ ] 3.3 Aktualisieren (Fetch) ohne Verlust lokaler Entwürfe und Anzeige „zuletzt aktualisiert“; Test: neue Commits erscheinen, `draft/*` bleibt
- [ ] 3.4 Beitragsliste aus `content/posts/*/index.md` (Titel, Datum, Verzeichnis, Kennzeichnung Entwurf); Test gegen Beispielbeiträge aus dem Blog (inkl. Datum mit Uhrzeit)
- [ ] 3.5 Erlaubte Schlagworte aus `content/tags.yaml` einlesen; Test mit dem echten Dateiformat des Blogs

## 4. Beitrag erstellen (`beitrag-erstellen`)

- [ ] 4.1 Verzeichnisnamen und Permalink aus Titel und Datum ableiten (Umlaute, Sonderzeichen, Kollisionen); Unit-Tests mit den Beispielen der Spec
- [ ] 4.2 Frontmatter und `index.md` erzeugen (gültiges YAML auch bei Anführungszeichen/Doppelpunkt); Tests, die erzeugte Dateien wieder parsen und mit Beiträgen des Blogs vergleichen
- [ ] 4.3 Editor-Bildschirm mit Titel, Datum (Europe/Berlin), Text, Schlagwort-Auswahl, Autor, Kurzbeschreibung und Pflichtfeldprüfung; Compose-UI-Test für fehlenden Titel
- [ ] 4.4 Bild-Einfügen im Editor (`![Beschreibung](Datei)`, Bildgruppen); Unit-Test für die erzeugte Markdown-Zeile
- [ ] 4.5 Eingaben bei Unterbrechung sichern und wiederherstellen; Test: Zustand nach Prozess-Neustart ist identisch

## 5. Entwurf und Veröffentlichung (`entwurf-veroeffentlichen`)

- [ ] 5.1 Entwurfs-Branch `draft/<name>` anlegen, Beitrag committen (Commit-Nachricht und Autor nach Spec); Test mit lokalem Testrepository, `main` unverändert
- [ ] 5.2 Push des Entwurfs mit Status „hochgeladen/noch nicht hochgeladen“ und erneutem Versuch; Test: Push-Fehler lässt Commit lokal bestehen
- [ ] 5.3 Entwurfsliste (lokal und Server), Weiterbearbeiten und Verwerfen mit Bestätigung; Test: Verwerfen löscht Branch lokal und remote
- [ ] 5.4 „Veröffentlichen“: Merge in Zielbranch, Push, Entwurf löschen; Tests für Erfolg, weitergelaufenen Zielbranch, Konflikt (Abbruch, Ziel unverändert) und abgelehnten Push (Entwurf bleibt)
- [ ] 5.5 Bildschirme für Entwurfsliste und Sicherheitsabfrage „Veröffentlichen“; manuell im Emulator geprüft

## 6. Fotos teilen (`foto-teilen`)

- [ ] 6.1 Intent-Filter für `ACTION_SEND`/`ACTION_SEND_MULTIPLE` mit `image/*` im Manifest; Instrumented-Test: Teilen-Intent öffnet die Zuordnung, Text-Teilen nicht
- [ ] 6.2 Verkleinern auf 1000 Pixel Breite mit EXIF-Drehung, Formattreue, ≤ 1 MB und ohne Vergrößern; Tests mit Beispielbildern (4000×3000, EXIF 6, 562×750)
- [ ] 6.3 Entfernen der GPS-/Standortmetadaten; Test: Ergebnisbild enthält keine GPS-Tags
- [ ] 6.4 Zuordnungsbildschirm: vorhandenen Beitrag wählen, neuen anlegen oder abbrechen; UI-Test für Abbruch ändert nichts
- [ ] 6.5 Ablegen im Beitragsverzeichnis mit Namenskollision (`IMG_1-2.jpg`), Commit auf `draft/<name>` und Push; Test inkl. Foto zu veröffentlichtem Beitrag
- [ ] 6.6 Option „Im Text einfügen“ bzw. „Nur ablegen“; Test: Bildzeilen ohne Leerzeile am Textende

## 7. Integration und Dokumentation

- [ ] 7.1 End-to-End im Emulator gegen ein Testrepository: Schlüssel, Klon, Beitrag mit geteilten Fotos, Veröffentlichen; Ablauf in `README.md` dokumentiert und nachvollziehbar
- [ ] 7.2 Erzeugten Beitrag mit dem Generator des Blogs prüfen (`cargo run --release -- check` in einer Kopie des Blogs); Ergebnis ohne Fehler
- [ ] 7.3 `README.md` mit Einrichtung (Deploy Key bei GitHub eintragen) und Bedienung; Schritte funktionieren wie beschrieben

## Workflow follow-up

- Offene Fragen aus `design.md` mit dem Nutzer klären, vor allem Merge-Ziel, Vorschau über Staging und Bildgröße (Breite oder längere Kante).
- Change nach Review archivieren (`/opsx:archive`).
