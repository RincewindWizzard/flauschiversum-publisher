# Design

## Context

Die Daten kommen aus dem lokalen Klon (`repository-klon` in `publisher-grundfunktionen`, noch nicht umgesetzt): `content/posts/<verzeichnis>/index.md` mit Frontmatter (`title`, `date`, `permalink`, `tags`, `author`, `image`, `excerpt`), daneben die Bilder. Der Blog hat derzeit 194 Beiträge und etwa 365 MB Beitragsdateien (überwiegend Originalfotos in voller Größe); 176 Beiträge haben ein Feld `image`, 18 nicht. Entwürfe liegen als Branches `draft/<verzeichnisname>` vor (`entwurf-veroeffentlichen`). Die Übersicht ersetzt die einfache „Beitragsliste“ als Oberfläche und ist der Auswahlbildschirm für Bearbeiten (`beitrag-bearbeiten`), Eigenschaften (`beitrag-eigenschaften`) und Fotos (`foto-teilen`). Motivation: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Jeder Beitrag ist in wenigen Sekunden gefunden (Suche, Filter, Vorschau).
- Entwürfe und ihr Upload-Status sind auf einen Blick erkennbar.
- Auch mit Hunderten von Beiträgen und großen Originalfotos bleibt die Liste flüssig.

**Non-Goals:**
- Löschen, Umbenennen oder Verschieben von Beiträgen, Mehrfachauswahl für Sammelaktionen.
- Verwaltung von Seiten (`content/pages/`) und `tags.yaml`.
- Volltextsuche mit Rangfolge, Unschärfe oder Grammatik; Vorschau des gerenderten Beitrags.

## Decisions

**Beitragsindex im Speicher, auf Platte zwischengespeichert.** Beim ersten Start und nach jedem Fetch liest die App nur die Frontmatter-Blöcke und den Text aller `index.md` und baut daraus einen Index (Titel, Datum, Schlagworte, Autor, Titelbild-Dateiname, Kurzbeschreibung, Text in Kleinbuchstaben für die Suche). Der Index wird als Datei im App-Speicher abgelegt und mit dem Commit-Hash des Klons versehen; ändert sich nur ein Teil der Beiträge, werden nur diese neu gelesen (Vergleich der Dateien zwischen den Commits). Alternative: Datenbank (Room/SQLite mit FTS) – verworfen für den Anfang, weil 200–1000 Beiträge und einfache Teilstring-Suche im Speicher ausreichen; bei deutlich mehr Beiträgen kann FTS nachgerüstet werden, ohne die Oberfläche zu ändern.

**Entwürfe aus den Branch-Objekten lesen, ohne Checkout.** Die Frontmatter und der Text eines Entwurfs werden über JGit direkt aus dem Branch-Stand gelesen (`TreeWalk` auf `refs/heads/draft/*` bzw. `refs/remotes/origin/draft/*`). Dadurch muss der Arbeitsbaum nicht gewechselt werden, und Übersicht und Editor behindern sich nicht. Gibt es lokalen und Remote-Stand, gilt der lokale. Alternative: je Entwurf auschecken – verworfen (langsam, Seiteneffekte auf den Arbeitsbaum, Risiko für ungesicherte Änderungen).

**Vorschaubilder klein erzeugen und zwischenspeichern.** Die Originale im Klon sind groß (mehrere Megapixel). Vorschauen werden mit Downsampling dekodiert (Ziel ca. 200 Pixel Breite, wie die `200x`-Variante des Blogs) und als kleine Dateien im App-Cache abgelegt, geordnet nach Dateiname und Änderungszeit. Für das Laden und Zwischenspeichern kommt eine Bildbibliothek für Compose (z. B. Coil) mit eigener Größenvorgabe in Frage. Alternative: Bilder je Aufruf in voller Größe dekodieren – verworfen (Speicher, Ruckeln).

**Titelbild-Regel:** Feld `image`, wenn die Datei existiert; sonst das erste Bild des Verzeichnisses (nach Dateinamen sortiert; Bildendungen wie `content-format`); sonst Platzhalter. Das entspricht dem Verhalten des Blogs ohne eigene Annahmen über Kennzeichnung der Titelbilder.

**Suche und Filter als reine Funktionen über dem Index** (`Query → Liste`), laufen auf `Dispatchers.Default` und werden entprellt (z. B. 150 ms). Suchwörter und Text werden in Kleinbuchstaben verglichen; Sortierung nach Titel nutzt einen deutschen Collator (Umlaute unter A/O/U). Filter und Sortierung werden im ViewModel gehalten und per `SavedStateHandle` über Drehung und Prozessende gerettet.

**Eine Übersicht, zwei Modi.** Der Bildschirm bekommt einen Modus (`Normal` oder `Auswahl(fotos)`). Im Auswahlmodus ändern sich nur die Kopfzeile (Foto-Vorschau, Abbrechen), das Verhalten beim Antippen (Ziel wählen statt Editor öffnen) und das Fehlen der Beitragsmenüs. Liste, Suche und Filter sind identisch.

**Aktionen delegieren:** Die Übersicht ruft nur die Abläufe der anderen Specs auf (Editor öffnen, Eigenschaften öffnen, Fotoauswahl des Systems, Veröffentlichen/Verwerfen) und implementiert sie nicht selbst.

## Risks / Trade-offs

- [Erstes Einlesen von Hunderten `index.md` dauert] → Im Hintergrund mit Fortschritt; Ergebnis zwischenspeichern; danach nur Änderungen lesen.
- [Index veraltet nach Änderungen in der App] → Nach jedem Speichern/Veröffentlichen/Fetch den betroffenen Beitrag im Index neu lesen.
- [Vorschauen für Beiträge mit sehr großen Originalen sind beim ersten Mal langsam] → Dekodieren mit Downsampling, parallel begrenzt, Platzhalter bis zur Fertigstellung, danach aus dem Cache.
- [Speicherbedarf des Volltextes im Speicher] → Der Text von 200 Beiträgen ist klein (wenige MB); bei sehr großen Beständen auf Datei/FTS ausweichen.
- [Entwurf und veröffentlichter Beitrag mit demselben Verzeichnis verwirren] → Entwurf ersetzt den Beitrag in der Liste und ist deutlich gekennzeichnet (siehe Spec).
- [Beiträge mit ungültigem Frontmatter] → Eintrag trotzdem anzeigen (Verzeichnisname als Titel, ohne Datum), mit Warnhinweis, aber nicht ausblenden.

## Open Questions

- **Gruppierung:** Soll die Liste nach Monat/Jahr gruppiert (Kopfzeilen) angezeigt werden oder reicht eine flache Liste? Annahme: flach, Jahr über den Filter. Änderbar ohne Auswirkung auf Daten und Aufgaben.
