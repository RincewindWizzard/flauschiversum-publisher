# Design

## Context

Die App ist ein leeres Android-Projekt (Kotlin, Jetpack Compose, Material 3, minSdk 24, Paket
`de.flauschiversum.publisher`). Der Blog liegt im Repository `RincewindWizzard/flauschiversum`
(Rust-Generator); Beiträge sind Verzeichnisse `content/posts/<name>/` mit `index.md` und Medien,
Schlagworte stehen in `content/tags.yaml`. Der Generator erzeugt `200x`/`700x`/`1000x`-Varianten
selbst (Spec `image-processing` im Blog) und vergrößert nie; ein bereits auf 1000 Pixel
verkleinertes Original ist daher für ihn gültig. Die Staging-Pipeline des Blogs läuft bei Push auf
`dev` und `claude/**`, nicht auf `draft/*`. Motivation und Umfang: siehe `proposal.md`,
Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**

- Beitrag schreiben und Fotos teilen, ohne Rechner und ohne zusätzliche Server.
- Das Repository bleibt die einzige Wahrheit; die App erzeugt nur Dateien und Git-Commits.
- Nichts geht live, ohne dass der Nutzer in der App „Veröffentlichen“ bestätigt.

**Non-Goals:**

- Änderungen am Blog-Repository (Generator, Workflows, Specs).
- Bearbeiten bestehender Beiträge im Text, Löschen von Beiträgen, Verwalten von Seiten oder
  `tags.yaml` (spätere Changes).
- Vorschau des gerenderten Beitrags, Login mit GitHub-Konto, andere Git-Hoster, Videos.

## Decisions

**Git-Bibliothek: JGit.** Reines Java, kein natives Git nötig, unterstützt
Clone/Fetch/Branch/Merge/Push. Alternative: Git-Binary mitliefern – verworfen (Größe, Wartung je
ABI). Risiko Ed25519/SSH-Transport auf Android: siehe Risiken; früh per Spike prüfen.

**SSH-Schlüssel: Ed25519, privat im App-internen Speicher, von Backups ausgenommen.** Ein Deploy Key
ist auf ein Repository und Schreibrecht begrenzt – kleinerer Schaden bei Verlust als ein
persönlicher Schlüssel. Alternativen: Schlüssel importieren (zusätzlicher Aufwand, riskanter),
Zugriff über GitHub-Token/HTTPS (vom Nutzer nicht gewünscht). Der Android Keystore unterstützt
Ed25519 für SSH nicht direkt; Verschlüsselung der Schlüsseldatei mit einem Keystore-Schlüssel ist
eine mögliche Härtung.

**Host-Schlüssel von github.com fest hinterlegt** (statt „beim ersten Mal vertrauen“), mit
Aktualisierung über eine App-Version. Verhindert Man-in-the-Middle im öffentlichen WLAN.

**Ein Klon, ein Branch je Entwurf.** Entwurfs-Branches `draft/<verzeichnisname>` werden vom
Zielbranch aus angelegt, committet und gepusht. „Veröffentlichen“ führt in den Zielbranch zusammen
(Merge ohne Konfliktlösung in der App; bei Konflikt Abbruch) und löscht den Entwurfs-Branch.
Alternative: Pull Requests über die GitHub-API – verworfen, weil dafür ein Token nötig wäre, das der
Nutzer nicht einrichten will.

**Bildverarbeitung auf dem Gerät mit Android-Bordmitteln** (Bitmap-Decoder mit Zielgröße,
EXIF-Drehung anwenden, neu codieren, Qualität schrittweise senken bis ≤ 1 MB). Beim Neu-Codieren
fallen Metadaten ohnehin weg; Urheber-/Rechte-Angaben bleiben dadurch nicht erhalten (siehe Offene
Fragen). Die Verarbeitung läuft im Hintergrund, nicht auf dem Hauptthread.

**Aufbau:** Compose-Oberfläche mit ViewModels; Schichten `git` (JGit-Kapsel), `posts` (Frontmatter,
Verzeichnisnamen, Schlagworte lesen), `images` (Skalieren) und `ui`. Die Schichten ohne
Android-Abhängigkeit (`posts`, Teile von `git`) sind mit reinen JVM-Tests prüfbar.

**Dateiformat-Treue:** Die App erzeugt Frontmatter nach `content-format` des Blogs und prüft in
Tests gegen Beispiel-Beiträge aus `content/posts/` des Blogs (gelesen, nicht verändert).

## Risks / Trade-offs

- [Ed25519 über JGit-SSH auf Android ist nicht sicher lauffähig] → Früh einen Spike bauen (Clone und
  Push gegen GitHub aus der App). Fallback: andere SSH-Bibliothek hinter derselben Git-Kapsel oder
  RSA-4096 als Schlüsseltyp (Spec `ssh-zugang` müsste dann angepasst werden).
- [Repository wächst durch Bilder, Klon wird groß] → Speicherplatzprüfung vor dem Klonen; flacher
  Klon (`depth`) als spätere Option, solange Merge und Push funktionieren.
- [Merge-Konflikt am Zielbranch] → Abbruch ohne Änderung, klare Meldung; Auflösung am Rechner.
- [Push auf Zielbranch durch Branch-Schutz abgelehnt] → Meldung; Entwurf bleibt erhalten.
- [Deploy Key hat Schreibrecht auf das ganze Repository] → Schlüssel nur im geschützten Speicher;
  bei Geräteverlust Key bei GitHub löschen.
- [Handy-Akku/Netz bricht während Push ab] → Commit bleibt lokal, erneuter Push möglich, Status
  sichtbar.

## Open Questions

- **Merge-Ziel:** Standard `main` (konfigurierbar). Soll „Veröffentlichen“ künftig auf `dev`
  (Staging) und erst danach auf `main` führen? -> main
- **Vorschau/Staging:** Die Staging-Pipeline des Blogs läuft nicht auf `draft/*`, Entwürfe sind also
  nicht online einsehbar. Soll der Workflow im Blog-Repository um `draft/**` erweitert werden
  (eigener Change dort, nicht in diesem Repo)? -> nein
- **Branch-Namen:** `draft/<verzeichnisname>` – ist ein Datum im Namen (`draft/2026-10-13-muetze`)
  gewünscht? -> nein, so wie der Ordnername im Verzeichnis
- **Bildgröße:** „1000x“ ist als Breite verstanden; Hochformatfotos werden also auf 1000 Pixel
  Breite begrenzt (Höhe bis 1333). Soll die längere Kante gelten? -> ja die höhere Kante
- **Alt-Text beim Teilen:** Pflicht, optional oder nachträglich im Editor? -> Alt Text gehört an das Bild
- **Konfliktbehandlung:** Reicht Abbruch mit Meldung, oder soll die App einen Konfliktlösungsweg
  anbieten? -> erstmal Abbruch das ist sonst viel zu kompliziert
- **Rechte-Metadaten:** Der Blog führt Rechte-Metadaten an Bildern (siehe `image-processing`). Soll
  die App Urheber-/Copyright-Angaben in die verkleinerten Bilder schreiben? -> ja
- **Autor/Commit-Identität:** Name und E-Mail aus den Einstellungen – sind feste Vorgaben (z. B.
  „Ermeline“) gewünscht? -> ja. Bzw das sollte man in den EInstellungen konfiguriren können
