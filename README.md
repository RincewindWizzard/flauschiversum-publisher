# Flauschiversum Publisher

Android-App, mit der Beiträge für den statischen Blog [Flauschiversum](https://github.com/RincewindWizzard/flauschiversum)
vom Handy aus geschrieben und per Git über SSH veröffentlicht werden. Die App klont das Repository des Blogs
(flach, ohne Arbeitsverzeichnis), sammelt Änderungen auf dem Branch `draft` und führt sie beim „Veröffentlichen“
in den Zielbranch (`main`) zusammen. Die Anforderungen stehen in [`openspec/`](openspec/).

## Einrichtung

Beim ersten Start führt die App in fünf Schritten durch die Einrichtung:

1. **Repository**: SSH-Adresse des Blog-Repositorys, z. B. `git@github.com:RincewindWizzard/flauschiversum.git`
   oder `ssh://git@mein-server.example/srv/blog.git`. Nur SSH auf Port 22 wird unterstützt; eine HTTPS-Adresse
   erkennt die App und schlägt die passende SSH-Adresse vor.
2. **Schlüssel**: Die App erzeugt ein Ed25519-Schlüsselpaar. Der private Schlüssel verlässt das Gerät nie.
3. **Schlüssel eintragen**: Die App zeigt den öffentlichen Schlüssel an (Kopieren/Teilen):
   - *GitHub*: im Repository unter *Settings → Deploy keys → Add deploy key*, **„Allow write access“ anhaken**.
   - *Eigener Server (VPS)*: Zeile in `~/.ssh/authorized_keys` des Git-Benutzers eintragen.
4. **Verbindung prüfen**: Beim ersten Kontakt zeigt die App den Fingerabdruck des Servers. Für github.com
   vergleicht sie ihn mit den veröffentlichten Fingerabdrücken; bei einem eigenen Server bitte selbst prüfen
   (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`) und dann bestätigen. Bei GitHub prüft die App auch das
   Schreibrecht; bei eigenen Servern zeigt sich das erst beim ersten Veröffentlichen.
5. **Repository holen**: flacher Klon (Verlauf nur der letzte Stand). Über Mobilfunk fragt die App vorher nach;
   es braucht rund 1,5 GB freien Speicher.

Mit *Einstellungen → Einrichtung wiederholen* lässt sich jeder Schritt erneut durchlaufen.

## Bedienung

- **Übersicht**: oben die offenen Entwürfe (*Neu*, *Geändert*, *Gelöscht*), darunter alle Beiträge, neueste zuerst.
  Im Hintergrund holt die App regelmäßig den Stand des Servers, damit Konflikte selten werden.
- **Beitrag schreiben/bearbeiten**: Der Editor zeigt nur den Text. Titel, Datum, Schlagworte, Autor, Titelbild und
  Kurzbeschreibung stehen im Menü *Eigenschaften*. Eingaben gehen bei Unterbrechung nicht verloren.
- **Speichern** legt einen Commit auf `draft` an und lädt ihn im Hintergrund hoch („Noch nicht hochgeladen“ erscheint,
  wenn das nicht klappt).
- **Fotos teilen**: In der Galerie ein oder mehrere Fotos wählen → *Teilen* → *Flauschiversum*. Dann einen
  vorhandenen Beitrag wählen oder einen neuen anlegen und je Bild eine Beschreibung eingeben. Die App verkleinert
  auf höchstens 1000 Pixel Kantenlänge und 1 MB, dreht nach der Kamera-Ausrichtung, entfernt Standortdaten, schreibt
  die Rechteangaben (Einstellungen) ein, legt die Datei im Beitragsverzeichnis ab (bei gleichem Namen `IMG_1-2.jpg`)
  und bindet sie unter `## Anhänge` am Ende des Beitrags ein.
- **Veröffentlichen**: Die Sicherheitsabfrage listet alle betroffenen Beiträge. Danach wird `draft` in den Zielbranch
  übernommen und gepusht; bei einem Konflikt bricht die App ab und ändert nichts.
- **Verwerfen**: einzelne Entwürfe oder alle; der Zielbranch bleibt unberührt.

## Entwicklung

```sh
export ANDROID_HOME=/pfad/zum/android-sdk
./gradlew testDebugUnitTest      # JVM- und Robolectric-Tests (Compose-UI-Tests eingeschlossen)
./gradlew assembleDebug          # Debug-APK
openspec validate --all --strict # Anforderungen prüfen
```

Die Tests laufen ohne Emulator. Git/SSH-Abläufe werden gegen einen eingebetteten Apache-MINA-SSH-Server geprüft.

Optional prüft `GeneratorCheckTest` erzeugte Beiträge mit dem echten Generator des Blogs. Dazu eine Kopie des Blogs
anlegen, dort `cargo build --release` ausführen und `BLOG_GENERATOR_DIR=<Kopie>` setzen; ohne die Variable wird der
Test übersprungen.

### Was nicht automatisch geprüft ist

Es gibt keine Tests auf einem Emulator oder echten Gerät und keine gegen das echte GitHub. Ob JGit, JSch und
BouncyCastle unter Android (ART) wie auf der JVM laufen, ist daher noch zu bestätigen (Aufgabe 1.4 von
`publisher-grundfunktionen`).
