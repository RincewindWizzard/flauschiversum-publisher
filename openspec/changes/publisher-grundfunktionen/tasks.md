# Tasks

## 1. Grundgerüst und Spike

- [x] 1.1 Abhängigkeiten für Git über SSH (JGit mit SSH-Transport) und Ed25519 ergänzen; `./gradlew assembleDebug` läuft durch (JGit 6.10, JSch-Fork mit BouncyCastle, minSdk 26 mit Core-Library-Desugaring)
- [x] 1.2 Spike auf der JVM gegen einen eingebetteten SSH-Server (siehe `GitSpikeTest`, `SshAccessTest`): Ed25519-Schlüssel, flacher Klon, Holen, Nachladen (Deepen), Merge im Speicher, Push, Schreibrechtprüfung über `git-receive-pack`; Ergebnisse in `design.md` festgehalten
- [ ] 1.4 Spike-Nachweis auf einem Android-Gerät und gegen echtes GitHub (Schlüssel erzeugen, flach klonen, `draft` pushen, Deploy Key ohne Schreibrecht); wegen fehlendem Emulator/Zugangsdaten in dieser Umgebung nicht geprüft
- [x] 1.3 Paketstruktur `git`, `posts`, `images`, `ui` mit Navigation (Compose) anlegen und die App baut und startet in der Robolectric-Umgebung (Emulator in dieser Umgebung nicht verfügbar)

## 2. SSH-Zugang (`ssh-zugang`)

- [x] 2.1 Schlüsselerzeugung und -speicherung im privaten App-Speicher, von Backups ausgenommen (`backup_rules.xml`, `data_extraction_rules.xml`); Unit-Test: Schlüssel wird gespeichert, wieder geladen und ist im OpenSSH-Format ausgebbar
- [x] 2.2 Bildschirm „Schlüssel“: erzeugen/ersetzen mit Warnung, öffentlichen Schlüssel kopieren und teilen; per Robolectric-UI-Test geprüft (`SetupUiTest`)
- [x] 2.3 Host-Schlüssel-Verwaltung: Bestätigung beim ersten Kontakt (Typ, SHA256-Fingerabdruck), Speicherung je Host und Port, mitgelieferte GitHub-Schlüssel, Abbruch bei Abweichung, Liste und Löschen in den Einstellungen; Tests: Erstkontakt fragt, bekannter Server nicht, geänderter Schlüssel bricht ab, GitHub ohne Rückfrage, Löschen erzwingt neue Bestätigung
- [x] 2.4 „Verbindung testen“ ohne Repository-Änderung mit unterscheidbaren Meldungen (ok, Schlüssel nicht akzeptiert, Schreibrecht fehlt, Schreibrecht nicht prüfbar, Repository nicht gefunden, kein Netz, Server auf Host und Port nicht erreichbar); Unit-Tests für die Fehlerzuordnung, Test: Repository nach dem Test unverändert

## 3. Repository-Klon (`repository-klon`)

- [x] 3.1 Einstellungen für Repository-Adresse, Zielbranch (Standard `main`), Commit-Name und -E-Mail, Autor für neue Beiträge und Rechtetext (Vorgabe „Flauschiversum / Ermeline Wollknoll“) mit Speicherung; Tests der Vorgaben und der Persistenz
- [x] 3.2 Flacher Klon (Tiefe 1, nur Zielbranch) mit Fortschritt, Aufräumen bei Abbruch und Speicherprüfung (≥ 1,5 GB); Test mit lokalem Testrepository, Abbruch hinterlässt keinen Klon
- [x] 3.2a Historie bei Bedarf nachladen (Deepen) für die Merge-Basis; Test: `main` ist weit vorausgelaufen, Merge gelingt ohne Nutzerentscheidung
- [x] 3.3 Hintergrund-Synchronisation (Fetch und Nachziehen des Zielbranchs) bei Start, Vordergrund und Öffnen eines Beitrags: höchstens ein Lauf, Mindestabstand, blockiert weder Hauptthread noch Commits des Nutzers, Anzeige „zuletzt aktualisiert“ und dezenter Indikator; Tests mit Testrepository: neue Commits erscheinen, `draft` mit Änderungen bleibt unverändert, `draft` ohne Änderungen wird auf den Zielbranch vorgesetzt, Fehler/Offline bleibt still, parallele Auslöser starten nur einen Fetch, Commit während Fetch wartet nicht auf das Netz
- [x] 3.3a Manuelles Aktualisieren mit Ergebnisanzeige (aktuell, Kein Netz, Fehler mit Grund); UI-Test
- [x] 3.4 Beitragsliste aus `content/posts/*/index.md` (Titel, Datum, Verzeichnis, Kennzeichnung Entwurf); Test gegen Beispielbeiträge aus dem Blog (inkl. Datum mit Uhrzeit)
- [x] 3.5 Erlaubte Schlagworte aus `content/tags.yaml` einlesen; Test mit dem echten Dateiformat des Blogs

## 3b. Einrichtungsablauf (`einrichtung`)

- [x] 3b.1 Zustandsautomat mit gespeichertem Fortschritt (Schritt 1–5) und Wiederaufnahme beim ersten offenen Schritt; Unit-Tests: Neustart nach Schritt 3 startet bei Schritt 3, Sperre vorwärts ohne erfüllten Schritt, unterbrochener Klon wird entfernt
- [x] 3b.2 Schritt 1: Repository-Adresse prüfen (`benutzer@host:pfad`, `ssh://benutzer@host[:port]/pfad`, HTTP(S) erkennen und SSH-Adresse vorschlagen, ungültige Eingaben ablehnen, Vorgabe); Unit-Tests je Szenario der Spec inkl. Port und Pfad
- [x] 3b.3 Schritt 3: Anleitung zum Eintragen des Schlüssels (Anzeige/Kopieren; bei `github.com` „GitHub-Seite öffnen“ mit Titelvorschlag und Hinweis „Allow write access“; sonst kopierbarer `authorized_keys`-Befehl und Deploy-Key-Hinweis); Tests der URL-/Befehlsbildung, UI-Test beider Varianten
- [x] 3b.4 Schritt 4: Prüfung mit den Ursachenmeldungen aus Aufgabe 2.4 und Bestätigung des Host-Schlüssels im Ablauf, Weiterführung nur bei Erfolg (auch bei „Schreibrecht nicht prüfbar“ mit Hinweis); UI-Tests für Erfolg, nur Leserecht, Server nicht erreichbar, Erstkontakt
- [x] 3b.5 Schritt 5: Hinweise zu Datenmenge, Mobilfunkabfrage und Speicherprüfung, danach Klon (Aufgabe 3.2); UI-Tests für Mobilfunk, zu wenig Speicher und Abbruch
- [x] 3b.6 Prüfung des Klons (Zielbranch, `content/posts/`, `tags.yaml`) mit Auswahl eines anderen Zielbranchs bzw. neuem Klon bei Fehler; Tests mit Testrepositories (ohne `main`, ohne `content/posts/`)
- [x] 3b.7 Zugangsprobleme später: Hinweis bei dauerhaft verweigertem Zugriff (zweimal in Folge) ohne Dialog, Sprung zu Schritt 3/4; „Repository neu holen“, „Schlüssel ersetzen“ und Ändern der Repository-Adresse (erneut Schritte 3–5) in den Einstellungen; Tests: Netzfehler löst nichts aus, entfernter Schlüssel löst Hinweis aus

## 4. Beitrag erstellen (`beitrag-erstellen`)

- [x] 4.1 Verzeichnisnamen und Permalink aus Titel und Datum ableiten (Umlaute, Sonderzeichen, Kollisionen); Unit-Tests mit den Beispielen der Spec
- [x] 4.2 Frontmatter und `index.md` erzeugen (gültiges YAML auch bei Anführungszeichen/Doppelpunkt); Tests, die erzeugte Dateien wieder parsen und mit Beiträgen des Blogs vergleichen
- [x] 4.3 Editor-Bildschirm mit Text und Zugang zum Menü „Eigenschaften“ (Felder Titel, Datum (Europe/Berlin), Schlagworte, Autor, Kurzbeschreibung; einfache Eingaben, komfortable Eingabemethoden folgen im Change `markdown-editor`) und Pflichtfeldprüfung; Compose-UI-Test für fehlenden Titel
- [x] 4.4 Bild-Einfügen im Editor (`![Beschreibung](Datei)`, Bildgruppen); Unit-Test für die erzeugte Markdown-Zeile
- [x] 4.5 Eingaben bei Unterbrechung sichern und wiederherstellen; Test: Zustand nach Prozess-Neustart ist identisch

## 5. Entwurf und Veröffentlichung (`entwurf-veroeffentlichen`)

- [x] 5.1 Branch `draft` vom Zielbranch anlegen (falls er fehlt), Beitrag darauf committen (Commit-Nachricht und Autor nach Spec); Tests mit lokalem Testrepository: `main` unverändert, zweiter Beitrag wird weiterer Commit auf `draft`
- [x] 5.2 Push des Entwurfs mit Status „hochgeladen/noch nicht hochgeladen“ und erneutem Versuch; Test: Push-Fehler lässt Commit lokal bestehen
- [x] 5.3 Entwürfe je Beitrag aus dem Unterschied `draft`/Zielbranch ableiten („Neu“/„Geändert“), Verwerfen einzelner Entwürfe (Zurücksetzen-Commit bzw. Entfernen) und „Alle verwerfen“ (Reset von `draft`, Force-Push); Tests: nur der gewählte Beitrag ändert sich, neuer Beitrag verschwindet, `main` unberührt
- [x] 5.4 „Veröffentlichen“: Zielbranch aktualisieren, `draft` pushen, Merge in Zielbranch, Push, danach `draft` auf den Zielbranch setzen; Tests für Erfolg (mehrere Beiträge), weitergelaufenen Zielbranch, Konflikt (Abbruch, `main` und `draft` unverändert) und abgelehnten Push (`draft` bleibt)
- [x] 5.5 Entwurfsliste und Sicherheitsabfrage „Veröffentlichen“ mit Aufzählung aller betroffenen Beiträge; per Robolectric-UI-Test geprüft (`HomeEditorUiTest`)
- [x] 5.6 Entwurf nachführen: `draft` ohne eigene Änderungen per Fast-Forward auf den Zielbranch setzen (im Hintergrund, mit Push), `draft` mit Änderungen unverändert lassen; Tests für beide Fälle

## 6. Fotos teilen (`foto-teilen`)

- [x] 6.1 Intent-Filter für `ACTION_SEND`/`ACTION_SEND_MULTIPLE` mit `image/*` im Manifest; Robolectric-Test (`ShareUiTest`): Bilder-Intent wird von `MainActivity` aufgelöst, Text-Teilen nicht (echtes Teilen aus einer Galerie-App nicht geprüft, kein Gerät)
- [x] 6.2 Verkleinern, sodass die längere Kante ≤ 1000 Pixel misst, mit EXIF-Drehung, Formattreue, ≤ 1 MB und ohne Vergrößern; Tests mit Beispielbildern (4000×3000, 3000×4000, EXIF 6, 562×750)
- [x] 6.3 Entfernen der GPS-/Standortmetadaten und Schreiben der Rechteangaben (EXIF Artist/Copyright, XMP, Text aus den Einstellungen); Test: Ergebnisbild enthält keine GPS-Tags, aber die Rechteangaben
- [x] 6.4 Zuordnungsbildschirm: vorhandenen Beitrag wählen, neuen anlegen oder abbrechen; UI-Test für Abbruch ändert nichts
- [x] 6.5 Ablegen im Beitragsverzeichnis mit Namenskollision (`IMG_1-2.jpg`), Commit auf `draft` und Push; Test inkl. Foto zu veröffentlichtem Beitrag
- [x] 6.6 Alt-Text-Feld je Bild; Einbinden unter `## Anhänge` (Überschrift am Dateiende anlegen, Zeilen am Dateiende anfügen, Leerzeile nach der Überschrift, Rest unverändert); Unit-Tests für die drei Szenarien der Spec, Datei im selben Commit wie die Bilder

## 7. Integration und Dokumentation

- [ ] 7.1 End-to-End im Emulator gegen ein Testrepository: Schlüssel, Klon, Beitrag mit geteilten Fotos, Veröffentlichen; Ablauf in `README.md` dokumentiert. Offen: Emulator/Gerät fehlt hier; die einzelnen Schritte sind per Robolectric gegen einen eingebetteten SSH-Server geprüft (Einrichtung bis Klon, Beitrag, Fotos, Veröffentlichen), aber nicht als durchgehender Lauf auf Android
- [x] 7.2 Erzeugten Beitrag mit dem Generator des Blogs prüfen (`flauschiversum check` in einer Kopie des Blogs, `GeneratorCheckTest`); Ergebnis ohne Fehler und ohne neue Warnungen
- [x] 7.3 `README.md` mit Einrichtung (Schlüssel bei GitHub als Deploy Key oder auf einem eigenen Server in `authorized_keys` eintragen) und Bedienung; Schritte gegen die Oberfläche der App abgeglichen (nicht auf einem Gerät durchgespielt)

## Workflow follow-up

- Change nach Review archivieren (`/opsx:archive`).
