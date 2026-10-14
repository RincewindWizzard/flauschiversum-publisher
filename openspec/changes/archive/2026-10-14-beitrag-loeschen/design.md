# Design

## Context

Der Blog arbeitet nach „Cool URIs don't change“; nicht mehr vorhandene Adressen werden über `content/redirects.yaml` (`/alt/: /neu/`) weitergeleitet. Beiträge sind Verzeichnisse unter `content/posts/`; Entwürfe ergeben sich aus dem Unterschied zwischen `draft` und Zielbranch (`entwurf-veroeffentlichen`). Es gibt im Blog keine Markierung „unveröffentlicht“ außer einem zukünftigen Datum. Motivation: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Löschen ist umkehrbar bis zur Veröffentlichung und hinterlässt keine unbeabsichtigten toten Adressen.

**Non-Goals:**
- „Zurückziehen“ ohne Löschen (z. B. Datum in die Zukunft setzen oder Entwurfsfeld): der Blog kennt dafür kein Feld; ein Datums-Trick würde Beitrag und Sortierung verfälschen.
- Löschen von Bildern einzelner Beiträge (siehe `foto-verwaltung`), von Seiten oder Schlagworten.
- Papierkorb nach der Veröffentlichung (Wiederherstellen dann über Git am Rechner).

## Decisions

**Löschen = Verzeichnis auf `draft` entfernen; Entwurfsart „Gelöscht“.** Die Ableitung der Entwürfe (Unterschied der Beitragsverzeichnisse) erkennt fehlende Verzeichnisse auf `draft` als „Gelöscht“. Das ergänzt die Spec `entwurf-veroeffentlichen` (dort „Neu“, „Geändert“).

**Weiterleitung als eigene Zeile am Dateiende von `redirects.yaml`.** Zeilenbasiert (wie `tags.yaml`), Kommentare bleiben. Vorgabe ist die Beitragsübersicht, weil der Blog keine passende Einzelseite hat; die Wahl „kein Ziel“ ist bewusst möglich.

**Alte Adresse nach den Regeln des Blogs bestimmen:** `permalink`, sonst `/<JJJJ>/<MM>/<slug>/` mit Slug aus dem Verzeichnisnamen (`ä`→`ae`, `ö`→`oe`, `ü`→`ue`, `ß`→`ss`, sonst `-`). Dieselbe Funktion wird für die Permalink-Vorbelegung im Eigenschaften-Menü genutzt.

**Interne Links suchen** im Index der Übersicht (Text aller Beiträge), nur als Hinweis, nicht als Sperre.

## Risks / Trade-offs

- [Versehentliches Löschen] → Bestätigung mit Titel/Adresse; Wiederherstellen per „Verwerfen“, solange nicht veröffentlicht.
- [Weiterleitungsziel unpassend] → Standard `/beitraege/`; Ziel kann später am Rechner geändert werden.
- [Tote Bild-URLs gelöschter Beiträge (`/…/700x/…`) bleiben ohne Weiterleitung] → Hinweis in der Bestätigung; Bild-URLs sind nicht Teil der Weiterleitung.
- [Konflikt mit Änderungen auf dem Server am gelöschten Beitrag] → Merge-Konflikt beim Veröffentlichen bricht ab (bestehendes Verhalten).
