# Proposal

## Why

Neue Beiträge für das Flauschiversum entstehen unterwegs: Fotos werden mit dem Handy gemacht, Texte
oft spontan geschrieben. Heute müssen sie am Rechner als Verzeichnis mit `index.md` und Bildern ins
Git-Repository `flauschiversum` gelegt, per Hand verkleinert, committet und gepusht werden. Die App
„Flauschiversum Publisher“ soll diesen Weg komplett auf das Handy verlegen – mit Entwurfsstufe,
damit nichts ungeprüft live geht.

## What Changes

- Die App erzeugt ein SSH-Schlüsselpaar (Ed25519) und zeigt den öffentlichen Schlüssel zum Eintragen
  als Deploy Key mit Schreibrecht bei GitHub an. Der private Schlüssel verlässt das Gerät nicht.
- Die App hält einen lokalen Klon des Repositorys `RincewindWizzard/flauschiversum` im App-Speicher
  und kann ihn per SSH aktualisieren.
- Man kann einen neuen Beitrag schreiben (Titel, Datum, Markdown-Text, Schlagworte aus
  `content/tags.yaml`, Permalink) und als Verzeichnis `content/posts/<name>/index.md` im Format des
  Blogs speichern.
- Jede Veröffentlichung läuft über den Entwurfs-Branch `draft`: Die App committet und pusht
  dorthin. Ein „Veröffentlichen“-Button in der App führt den Entwurf in den Zielbranch zusammen und
  pusht diesen.
- Die App nimmt Fotos über die Android-Teilen-Funktion entgegen (`ACTION_SEND` /
  `ACTION_SEND_MULTIPLE`, `image/*`), skaliert sie so, dass die längere Kante höchstens 1000 Pixel misst, versieht sie mit Alt-Text und Rechteangaben, lässt den Nutzer
  einen vorhandenen oder neuen Beitrag wählen, legt die Bilder im Beitragsverzeichnis ab, committet
  und pusht.

## Capabilities

### New Capabilities

- `ssh-zugang`: Erzeugen und Verwahren des SSH-Schlüssels, Anzeige des öffentlichen Schlüssels,
  Prüfung des Server-Host-Schlüssels, Verbindungstest.
- `repository-klon`: Lokaler Klon von `flauschiversum`, Aktualisieren (fetch), Einlesen der
  Beitragsliste und der erlaubten Schlagworte.
- `beitrag-erstellen`: Eingabe eines neuen Beitrags und Erzeugen von `index.md` mit Frontmatter nach
  der Spec `content-format` des Blogs.
- `entwurf-veroeffentlichen`: Entwurfs-Branch `draft`, Commit und Push sowie Zusammenführen in
  den Zielbranch („Veröffentlichen“).
- `foto-teilen`: Teilen-Ziel für Fotos, Skalieren, Zuordnen zu einem Beitrag und Ablegen im
  Beitragsverzeichnis.

### Modified Capabilities

(keine – die Spezifikationen des Blogs im Repository `flauschiversum` bleiben unverändert und sind
nur Referenz)

## Impact

- Neuer Code in `app/` (Kotlin, Jetpack Compose); neue Abhängigkeiten für Git über SSH (z. B. JGit
  mit SSH-Transport) und Bildverarbeitung.
- Manifest: Internet-Berechtigung, Intent-Filter für `image/*` (SEND, SEND_MULTIPLE).
- Netzwerk: Zugriff auf `github.com` per SSH (Port 22 bzw. `ssh.github.com:443`).
- Das Repository `flauschiversum` wird weder verändert noch gebaut; Pushes auf `draft` lösen dort kein
  Staging aus (der Workflow läuft nur auf `dev` und `claude/**`).
