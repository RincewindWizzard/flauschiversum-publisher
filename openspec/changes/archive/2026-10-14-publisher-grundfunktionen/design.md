# Design

## Context

Die App ist ein leeres Android-Projekt (Kotlin, Jetpack Compose, Material 3, minSdk 26, Paket
`de.flauschiversum.publisher`). Der Blog liegt im Repository `RincewindWizzard/flauschiversum`
(Rust-Generator); Beiträge sind Verzeichnisse `content/posts/<name>/` mit `index.md` und Medien,
Schlagworte stehen in `content/tags.yaml`. Der Generator erzeugt `200x`/`700x`/`1000x`-Varianten
selbst (Spec `image-processing` im Blog) und vergrößert nie; ein bereits auf 1000 Pixel
verkleinertes Original ist daher für ihn gültig. Die Staging-Pipeline des Blogs läuft bei Push auf
`dev` und `claude/**`, nicht auf `draft`. Motivation und Umfang: siehe `proposal.md`,
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
- Vorschau des gerenderten Beitrags, Login mit GitHub-Konto, Videos.

## Decisions

**Git-Bibliothek: JGit.** Reines Java, kein natives Git nötig, unterstützt
Clone/Fetch/Branch/Merge/Push. Alternative: Git-Binary mitliefern – verworfen (Größe, Wartung je
ABI). Risiko Ed25519/SSH-Transport auf Android: siehe Risiken; früh per Spike prüfen.

**SSH-Schlüssel: Ed25519, privat im App-internen Speicher, von Backups ausgenommen.** Ein Deploy Key
ist auf ein Repository und Schreibrecht begrenzt – kleinerer Schaden bei Verlust als ein
persönlicher Schlüssel. Alternativen: Schlüssel importieren (zusätzlicher Aufwand, riskanter),
Zugriff über Token/HTTPS (vom Nutzer nicht gewünscht). Der Android Keystore unterstützt
Ed25519 für SSH nicht direkt; Verschlüsselung der Schlüsseldatei mit einem Keystore-Schlüssel ist
eine mögliche Härtung.

**Geführte Einrichtung als Zustandsautomat.** Die Schritte 1–5 sind ein persistenter Zustand (`DataStore`); die Oberfläche leitet den Schritt aus dem Zustand ab, nicht umgekehrt, damit Abbrüche und Neustarts immer am richtigen Schritt landen. Die Prüfung des Zugangs verbindet sich mit dem Push-Dienst von GitHub (Aushandlung von `git-receive-pack`) ohne Referenzen zu übertragen: GitHub weist Deploy Keys ohne Schreibrecht dort ab; das wird im Spike (Aufgabe 1.2) bestätigt. Alternative: einen Test-Push auf einen Wegwerf-Branch – verworfen (verändert das Repository).

**Flacher Klon (Tiefe 1), Historie bei Bedarf nachladen.** Spart Daten und Platz gegenüber dem vollen Klon; Beitragsdateien (rund 365 MB, überwiegend Originalfotos) bleiben aber vollständig im Arbeitsbaum, ein flacher Klon braucht daher weiterhin mehrere hundert MB. Die App verlangt 1,5 GB Freiraum und fragt bei Mobilfunk nach (mit dem bare-Repository ohne Arbeitsbaum belegt der Klon grob die Hälfte). Merge-Basen fehlen im flachen Klon; beim Veröffentlichen vertieft die App die Historie (Deepen/Unshallow), bis sie gefunden ist. Alternative: Partial Clone ohne Blobs – verworfen, da JGit das nicht zuverlässig unterstützt und Vorschaubilder die Dateien brauchen.

**Beliebiger SSH-Server, Port aus der Adresse.** Die Repository-Adresse ist eine SSH-Adresse (`benutzer@host:pfad` oder `ssh://benutzer@host[:port]/pfad`), Standardport 22. Es gibt keinen automatischen Port-Fallback (z. B. `ssh.github.com:443`); unerreichbare Server oder gesperrte Ports werden erkannt und erklärt. Die App setzt nur voraus, dass der Server `git-upload-pack` und `git-receive-pack` über SSH anbietet (reines `git`-über-SSH, z. B. ein Bare-Repository auf einem VPS, GitHub, GitLab, Gitea/Forgejo). HTTPS wird erkannt und eine SSH-Adresse vorgeschlagen.

**Host-Schlüssel: „beim ersten Mal bestätigen“, GitHub vorbelegt.** Da der Server beliebig ist, kann der Host-Schlüssel nicht fest eingebaut sein. Beim ersten Kontakt zeigt die App Typ und SHA256-Fingerabdruck und speichert den bestätigten Schlüssel (eigene known-hosts-Datei im App-Speicher, mit Host und Port als Schlüssel). Weicht der Schlüssel später ab, bricht die App ab; Zurücksetzen nur ausdrücklich in den Einstellungen. Für `github.com` sind die veröffentlichten Schlüssel mitgeliefert (Aktualisierung über eine App-Version), damit die Vorgabe ohne Rückfrage läuft. Alternative: Host-Schlüssel nicht prüfen – verworfen (Man-in-the-Middle im öffentlichen WLAN).

**Eintragen des Schlüssels je nach Server.** Für `github.com` führt die App zur Deploy-Key-Seite des Repositorys; für andere Server zeigt sie einen kopierbaren `authorized_keys`-Befehl und den allgemeinen Hinweis auf Deploy Keys bei Git-Hostern. Die Schreibrechtprüfung ist serverabhängig: GitHub weist einen Deploy Key ohne Schreibrecht schon beim Aufbau der `git-receive-pack`-Sitzung ab; andere Server entscheiden oft erst beim Push. Die App meldet dort ehrlich „Schreibrecht wird beim ersten Veröffentlichen bestätigt“.

**Ein Klon, ein gemeinsamer Branch `draft`.** Alle Änderungen der App werden auf `draft` committet und gepusht. „Veröffentlichen“ führt `draft` in den Zielbranch zusammen (Merge ohne Konfliktlösung in der App; bei Konflikt Abbruch) und setzt `draft` danach auf den Stand des Zielbranchs. Offene Entwürfe sind keine eigenen Branches, sondern ergeben sich aus dem Unterschied der Beitragsverzeichnisse zwischen `draft` und Zielbranch. Weniger Branches, einfacheres Git, keine Namenskonflikte. Folge: Es werden immer alle Entwürfe gemeinsam veröffentlicht; Einzelverwerfen geschieht durch einen Zurücksetzen-Commit des Beitragsverzeichnisses, „Alle verwerfen“ setzt `draft` auf den Zielbranch zurück (dafür ist ein Force-Push nötig; `draft` gehört allein der App). Alternative: ein Branch je Beitrag (`draft/<name>`) – verworfen als zu kompliziert. Alternative: Pull Requests über die GitHub-API – verworfen, weil dafür ein Token nötig wäre, das der Nutzer nicht einrichten will.

**Bildverarbeitung auf dem Gerät mit Android-Bordmitteln** (Bitmap-Decoder mit Zielgröße,
EXIF-Drehung anwenden, neu codieren, Qualität schrittweise senken bis ≤ 1 MB). Beim Neu-Codieren
fallen Metadaten weg; die App schreibt danach die Rechteangaben (Urheber, Copyright, Lizenzhinweis
wie in `image-processing` des Blogs, Text in den Einstellungen änderbar) wieder hinein und lässt
Standortdaten weg. Skaliert wird so, dass die **längere** Kante höchstens 1000 Pixel misst (nicht
nur die Breite). Die Verarbeitung läuft im Hintergrund, nicht auf dem Hauptthread.

**Zielbranch `main`, kein Staging für Entwürfe.** „Veröffentlichen“ führt in `main` zusammen
(einstellbar). Die Staging-Pipeline des Blogs wird nicht um `draft` erweitert; Entwürfe haben
keine Online-Vorschau.

**Branch-Name `draft`.** Ein fester Name ohne Datum und Verzeichnisnamen.

**Konflikte: Abbruch.** Gibt es beim Zusammenführen einen Konflikt, bricht die App mit Meldung ab;
eine Konfliktlösung in der App gibt es nicht (zu aufwendig, am Rechner lösbar).

**Alt-Text gehört an das Bild.** Beim Teilen wird er pro Bild eingegeben und als Beschreibung in
`![Alt-Text](Datei)` übernommen. Die Zeile wird sofort in der `index.md` unter `## Anhänge` am
Dateiende angefügt – „Anhänge“ ist immer der letzte Abschnitt (ohne Leerzeilen zwischen den
Bildzeilen); den Text darüber fasst die App nicht an.

**Identität aus den Einstellungen.** Autorname, E-Mail (Commit-Autor), Autor für neue Beiträge und
Rechtetext sind in den Einstellungen änderbar, mit sinnvollen Vorgaben.

**Hintergrund-Synchronisation gegen Konflikte.** Ein Synchronisationsdienst führt Fetch und Nachziehen des Zielbranchs (Fast-Forward; die App committet nie direkt auf den Zielbranch, außer beim Veröffentlichen) bei App-Start, Rückkehr in den Vordergrund und beim Öffnen eines Beitrags aus. Er läuft in einer Coroutine außerhalb des Hauptthreads, höchstens ein Lauf gleichzeitig, mit Mindestabstand (z. B. 60 s) zwischen automatischen Läufen. Die Netzwerkphase des Fetch hält keine Sperre auf dem Repository; nur das kurze Nachziehen der Referenzen läuft in der gemeinsamen Git-Warteschlange, damit Commits des Nutzers nicht blockieren und sich nicht mit dem Nachziehen überschneiden. Ein `draft` ohne eigene Änderungen wird im Hintergrund auf den Zielbranch vorgesetzt (Fast-Forward); ein `draft` mit Änderungen bleibt unverändert (kein stilles Zusammenführen); Konflikte zeigen sich erst beim Veröffentlichen und führen zum Abbruch. Alternative: Zielbranch in `draft` mit Änderungen einmischen – verworfen (unerwartete Commits, zusätzliche Konfliktfälle).

**Hintergrundausführung:** Verkleinern, Commit und Push der Teilen-Aufträge laufen in der Fassung dieses Changes zunächst in der App; der Change `hintergrund-upload` verlegt sie in zuverlässige Hintergrundaufträge mit Warteschlange und Wiederholung.

**Bares Repository ohne Arbeitsbaum.** Der Klon ist ein *bares*, flaches Repository (`repo.git`). Lesen (Beiträge, Schlagworte, Bilder) und Schreiben (Commits auf `draft`) laufen direkt über die Objektdatenbank von JGit (Baum lesen, Baum im Speicher ändern, Commit anlegen, Branch setzen), Merges im Speicher (`ResolveMerger`). Vorteile: kein zweites Exemplar aller Bilder (der Klon braucht grob die Hälfte des Platzes), Branches lassen sich ohne Auschecken lesen und ändern, keine halbfertigen Arbeitsbäume bei Abbruch. Alternative: Arbeitsbaum auf `draft` – verworfen (doppelter Platzbedarf, Konflikte mit dem Branchwechsel).

**Spike-Ergebnisse (Aufgabe 1.2, gegen einen eingebetteten SSH-Server auf der JVM).** Ed25519-Schlüssel (BouncyCastle) mit JSch (`com.github.mwiede:jsch`, Signatur und X25519 über `com.jcraft.jsch.bc.*`) und JGit funktionieren; flacher Klon (Tiefe 1), Holen ohne zu vertiefen, Commit und Push von `draft`, Nachladen der Historie bis zum gemeinsamen Vorfahren und Merge im Speicher funktionieren. Die Schreibrechtprüfung über die Sitzung zu `git-receive-pack` erkennt einen Server, der Schreiben beim Verbindungsaufbau abweist (nachgebildet wie bei GitHub Deploy Keys). **Nicht** nachgeprüft: echtes GitHub und der Lauf auf Android-Geräten (ART); dafür gibt es die Aufgabe 1.4. Folgen: `minSdk` ist 26 (JGit nutzt `java.nio.file`), Core-Library-Desugaring ist aktiv, der vollständige BouncyCastle-Provider ersetzt den abgespeckten von Android beim Start.

**Aufbau:** Compose-Oberfläche mit ViewModels; Schichten `git` (JGit-Kapsel), `posts` (Frontmatter,
Verzeichnisnamen, Schlagworte lesen), `images` (Skalieren) und `ui`. Die Schichten ohne
Android-Abhängigkeit (`posts`, Teile von `git`) sind mit reinen JVM-Tests prüfbar.

**Dateiformat-Treue:** Die App erzeugt Frontmatter nach `content-format` des Blogs und prüft in
Tests gegen Beispiel-Beiträge aus `content/posts/` des Blogs (gelesen, nicht verändert).

## Risks / Trade-offs

- [Ed25519 über JGit-SSH auf Android ist nicht sicher lauffähig] → Früh einen Spike bauen (Clone und
  Push gegen GitHub und einen eigenen SSH-Server aus der App). Fallback: andere SSH-Bibliothek hinter derselben Git-Kapsel oder
  RSA-4096 als Schlüsseltyp (Spec `ssh-zugang` müsste dann angepasst werden).
- [Repository wächst durch Bilder, Klon wird groß] → Flacher Klon, Speicherplatzprüfung (1,5 GB) vor dem Klonen, Hinweis bei Mobilfunk.
- [JGit kann flache Klone nicht zuverlässig vertiefen oder damit mergen] → Im Spike (Aufgabe 1.2) prüfen; Fallback: voller Klon (Spec `repository-klon` anpassen).
- [Port des Servers im aktuellen Netz gesperrt] → Erkennen und erklären; kein Fallback vorgesehen.
- [Schreibrecht auf Nicht-GitHub-Servern vorab nicht prüfbar] → Ehrliche Meldung im Ablauf; klare Fehlermeldung beim ersten abgelehnten Push.
- [Eigener Server mit abweichender Git-Konfiguration (z. B. ohne Unterstützung für flache Klone)] → Im Spike gegen einen Bare-Repository-Server prüfen; Fallback voller Klon.
- [Merge-Konflikt am Zielbranch] → Abbruch ohne Änderung, klare Meldung; Auflösung am Rechner.
- [Push auf Zielbranch durch Branch-Schutz abgelehnt] → Meldung; Entwurf bleibt erhalten.
- [Der Schlüssel hat Schreibrecht auf das ganze Repository] → Schlüssel nur im geschützten Speicher;
  bei Geräteverlust den Schlüssel auf dem Server bzw. bei GitHub entfernen.
- [Handy-Akku/Netz bricht während Push ab] → Commit bleibt lokal, erneuter Push möglich, Status
  sichtbar.
