# Flauschiversum Publisher

Android-App, mit der Beiträge für den statischen Blog [Flauschiversum](https://github.com/RincewindWizzard/flauschiversum)
vom Handy aus geschrieben und per Git über SSH veröffentlicht werden. Die App klont das Repository des Blogs
(flach, ohne Arbeitsverzeichnis), sammelt Änderungen auf dem Branch `draft` und führt sie beim „Veröffentlichen“
in den Zielbranch (`main`) zusammen. Die Anforderungen stehen in [`openspec/`](openspec/).

## Einrichtung

Beim ersten Start führt die App in fünf Schritten durch die Einrichtung:

1. **Repository**: SSH-Adresse des Blog-Repositorys, z. B. `git@git.magierdinge.de:2222:nadja/flauschiversum` (Vorgabe; `host:port:pfad` ist die Kurzform mit eigenem Port) oder `git@github.com:eigentümer/repo.git`
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

- **Übersicht**: oben die offenen Entwürfe (*Neu*, *Geändert*, *Gelöscht*), darunter alle Beiträge mit dem Beitragsbild als
  Vorschau neben dem Titel (Feld `image`, sonst erstes Bild; ohne Bild ein Bildsymbol), Datum und Schlagworten. Die Suche durchsucht Titel, Kurzbeschreibung, Schlagworte und
  Text (mehrere Wörter = alle müssen vorkommen); Filter nach Schlagwort (alle gewählten) und Jahr lassen sich einzeln
  entfernen; sortiert wird nach neuesten, ältesten oder Titel A–Z. Das Menü „⋮“ am Beitrag bietet Bearbeiten,
  Eigenschaften, Foto hinzufügen, Verwerfen und Löschen. Zum Aktualisieren die Liste am oberen Rand nach unten ziehen (Ladekreis; für TalkBack gibt es die Aktion „Aktualisieren“ an der Liste).
  Im Hintergrund holt die App regelmäßig den Stand des Servers, damit Konflikte selten werden.
- **Beitrag schreiben/bearbeiten**: Der Editor zeigt nur den Text. Titel, Datum, Schlagworte, Autor, Titelbild und
  Kurzbeschreibung stehen im Menü *Eigenschaften*. Eingaben gehen bei Unterbrechung nicht verloren.
- **Editor**: Werkzeugleiste für Fett, Kursiv, Überschrift, Aufzählung, Link und Zitat (mit oder ohne Markierung),
  Rückgängig/Wiederholen und Bild einfügen. Markdown-Elemente werden farblich hervorgehoben. Beim Verlassen mit
  ungespeicherten Änderungen fragt die App nach.
- **Prüfungen** (offline, nach kurzer Pause): Markdown-Fehler (z. B. `**fett` nie geschlossen, Codeblock ohne Ende,
  Tabellenzeile mit falscher Spaltenzahl) und Warnungen (übersprungene Überschriftenebene, leere Links, mehrere
  Leerzeilen) sind im Text markiert; „Prüfung: …“ unter dem Editor öffnet die Liste, ein Tipp springt zur Stelle.
  Vor dem Speichern und Veröffentlichen warnt die App bei Fehlern; Weitermachen ist möglich.
- **Rechtschreibung** (Deutsch, eingebautes Wörterbuch): Falsche Wörter sind unterstrichen. Steht der Cursor im Wort,
  erscheinen bis zu fünf Vorschläge sowie „Ins Wörterbuch“ und „Ignorieren“. Das persönliche Wörterbuch lässt sich in
  den Einstellungen bearbeiten. Schlagworte aus `tags.yaml` gelten als richtig.
- **Eigenschaften** (Menü im Editor): Titel, Datum mit Kalender und optionaler Uhrzeit, Permalink (Vorschlag aus Datum
  und Titel), Schlagworte mit Suche, Autor (bekannte Autoren zum Antippen), Titelbild als Vorschau-Raster und
  Kurzbeschreibung mit Zähler und „Aus Text übernehmen“.
- **Hintergrund-Upload und Ausgang**: Beim Ablegen geteilter Fotos sichert die App die Dateien sofort und kehrt zurück.
  Verkleinern, Commit auf `draft` und Upload laufen als Hintergrundauftrag (WorkManager), der App-Schließen und Neustart
  übersteht. Der Commit entsteht auch ohne Netz; der Upload wartet auf Netz und wiederholt sich mit wachsendem Abstand
  (ab 30 s). Bei dauerhaften Fehlern (Zugriff verweigert, Server lehnt ab) wird nicht wiederholt. Der „Ausgang“ in
  der Übersicht zeigt alle Aufträge (Wartet auf Netz/WLAN, Läuft, Fehlgeschlagen, Erledigt), erlaubt Wiederholen,
  Abbrechen vor dem Commit und „Jetzt senden“ über Mobilfunk; erledigte Einträge verschwinden nach sieben Tagen.
  Einstellung „Nur über WLAN hochladen“ unter Einstellungen → Hochladen. Benachrichtigungen sind optional.
- **Vorschau**: „Vorschau“ im Editor (zeigt den aktuellen, auch ungespeicherten Text samt Eigenschaften) oder im Menü am
  Beitrag. Dargestellt werden Kopf, Text mit Tabellen, Durchgestrichenem und eingebettetem HTML, Bilder mit
  Unterschrift, Bildgruppen als wischbares Karussell, fehlende Bilder als Platzhalter. Sie ist eine Annäherung ohne
  Wasserzeichen, Suche und verwandte Beiträge (Hinweis beim ersten Öffnen), läuft offline ohne Skripte, und Links
  öffnen erst nach Bestätigung im Browser.
- **Fotos eines Beitrags** („Fotos“ im Editor oder im Menü am Beitrag): Raster mit Vorschau, Kennzeichnung „Titelbild“
  und „Nicht im Text“. Alt-Text ändern, nicht eingebundene Bilder unter `## Anhänge` einbinden, Reihenfolge mit ↑/↓
  ändern, Titelbild setzen und Fotos samt Bildzeile entfernen (bei einem Titelbild erst ein anderes wählen). Alles
  entsteht als Commit auf `draft`.
- **Beitrag löschen**: „Löschen“ in der Übersicht oder im Editor. Die Abfrage nennt Titel und Adresse, warnt vor
  Beiträgen mit Links auf die Adresse und fragt nach der Weiterleitung (Beitragsübersicht, Startseite, anderer
  Beitrag oder keine; sie wird an `content/redirects.yaml` angehängt). Der Beitrag steht bis zum Veröffentlichen als
  „Gelöscht“ in den Entwürfen und lässt sich mit „Verwerfen“ samt Weiterleitung wiederherstellen.
- **Neues Schlagwort**: In den Eigenschaften unter „Schlagworte“ → „Neues Schlagwort …“. Der Name wird sortiert in
  `content/tags.yaml` eingetragen (eigener Commit auf `draft`, in der Entwurfsliste als „Neues Schlagwort: …“) und sofort
  für den Beitrag gewählt. Gibt es das Schlagwort schon (auch in anderer Schreibweise), wählt die App das vorhandene.
- **Beitrag bearbeiten**: Antippen in der Übersicht. Die App zeigt den Entwurf, sonst den Stand des Zielbranches, und
  übernimmt neuere Fassungen vom Server, solange noch nichts getippt wurde; sonst erscheint ein Hinweis.
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

## Sicherheit, Datenschutz und Diagnose

- **App-Sperre** (Einstellungen → Sicherheit, Vorgabe aus): verlangt beim Öffnen die Geräte-Authentifizierung (Fingerabdruck,
  Gesicht, PIN oder Muster; ohne eingerichtete Bildschirmsperre lässt sie sich nicht einschalten). Sperrzeit: sofort, nach 1
  oder 5 Minuten. Bei aktiver Sperre zeigt die Übersicht der zuletzt benutzten Apps keinen Inhalt (`FLAG_SECURE`). Teilen
  fragt zuerst nach der Authentifizierung; gespeicherte Hintergrundaufträge laufen auch bei gesperrter App weiter. Es gibt
  keine App-eigene Passwortumgehung.
- **Datenschutz**: Die App verbindet sich ausschließlich per SSH mit dem eingestellten Git-Server und enthält keine
  Analyse-, Werbe- oder Absturzbibliotheken. Berechtigungen: Internet, Netzwerkstatus, Benachrichtigungen (erst beim
  ersten Auftrag) sowie unkritische Berechtigungen für Uploads im Vordergrund und die Geräte-Authentifizierung; kein Zugriff
  auf Speicher, Standort, Kontakte oder Mikrofon (Fotos kommen über „Teilen“ bzw. die Fotoauswahl des Systems). Schlüssel,
  Klon, Aufträge und Protokoll liegen im privaten App-Speicher und sind von Android-Backups ausgenommen. Die Vorschau lädt
  nichts aus dem Netz. Diese Punkte prüfen Tests (`PrivacyTest`).
- **Diagnose** (Einstellungen → Diagnose): ein lokales Protokoll (höchstens 1 MB, älteste Einträge fallen weg) mit Zeit,
  Vorgang, Ergebnis, Fehlerart und Server:Port – nie Schlüssel, Passwörter, Beitragstexte oder Bilder. Anzeigen, als Textdatei
  teilen (mit App- und Android-Version, nur auf Wunsch) und löschen. Nach einem Absturz erscheint beim nächsten Start ein
  Hinweis; nichts wird automatisch versendet.

## Installation, Signatur und Updates

- **Bauen und Testen**: `./gradlew lintDebug testDebugUnitTest assembleDebug` (so läuft auch `.github/workflows/ci.yml` bei
  jedem Push und Pull Request; die Debug-APK hängt als Artefakt am Lauf).
- **Release**: Ein Tag `vMAJOR.MINOR.PATCH` (z. B. `v1.2.0`) startet `.github/workflows/release.yml`. Es baut die signierte
  Release-APK und hängt sie an ein Release. Dafür müssen vier Repository-Geheimnisse gesetzt sein: `SIGNING_KEYSTORE_BASE64`
  (Keystore, mit `base64 -w0 release.jks` kodiert), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
  Fehlt eines, bricht der Lauf mit einer Meldung ab und veröffentlicht nichts. `versionName` stammt aus dem Tag,
  `versionCode = MAJOR·10000 + MINOR·100 + PATCH`.
- **Sideloading**: APK herunterladen, auf dem Handy öffnen und „Installation aus dieser Quelle“ erlauben. Mindestversion ist
  Android 8.0 (API 26).
- **Update**: Eine neuere APK mit derselben Signatur lässt sich über die vorhandene App installieren; Schlüssel, Klon,
  Einstellungen und Aufträge bleiben erhalten. Wichtig: den Keystore sicher aufbewahren – mit einem anderen Schlüssel
  signierte APKs lassen sich nicht darüber installieren.

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
