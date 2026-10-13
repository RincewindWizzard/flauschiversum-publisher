# Design

## Context

Der Blog rendert Markdown mit `pulldown-cmark` (Tabellen, Durchstreichen, Fußnoten) und stellt Bildgruppen als CSS-Karussell (scroll-snap) und Vollbild (PhotoSwipe) dar; Farben stehen im Schema „Flauschi-Nebel“. Die App enthält für `markdown-editor` bereits einen JVM-Markdown-Parser. Der Rust-Generator läuft nicht auf dem Handy. Motivation: siehe `proposal.md`, Anforderungen: siehe `specs/`.

## Goals / Non-Goals

**Goals:**
- Die Vorschau zeigt, was sich beim Schreiben ändert (Bildgruppen, Unterschriften, Tabellen), schnell und offline.
- Keine Ausführung fremden Codes aus Beitragstext.

**Non-Goals:**
- Pixelgenaue Wiedergabe der Seite, Suche, verwandte Beiträge, Navigation, Wasserzeichen, Seitenkopf mit Topbar.
- Ausführen des Generators auf dem Gerät oder auf einem Server.
- Vorschau für Seiten außerhalb von Beiträgen.

## Decisions

**Markdown → HTML mit dem JVM-Parser, Anzeige in einer WebView ohne JavaScript.** Rohes HTML im Beitrag (`<br/>`) ist in Compose schwer wiederzugeben; eine WebView mit abgeschaltetem JavaScript stellt es korrekt und sicher dar. Das Karussell wird wie im Blog mit CSS (scroll-snap) umgesetzt, der Vollbildmodus mit einem kleinen, in der App mitgelieferten Overlay ohne Netzzugriff (Alternative PhotoSwipe-Bibliothek – verworfen, braucht JavaScript). Alternative: Rendern mit Compose-Komponenten – verworfen für den Anfang (Aufwand, HTML-Reste).

**Lokaler Asset-Loader für Bilder und Stile.** Bilder werden über `WebViewAssetLoader` aus dem Klon geliefert, nicht über `file://`; Netzwerkzugriffe werden blockiert (Netzwerk-Sperre in der WebView, `shouldInterceptRequest`). Bilder werden vorher als `700`-Pixel-Varianten (aus dem Cache der Übersicht) ausgeliefert, damit große Originale die Vorschau nicht verlangsamen.

**CSS aus den Farbwerten des Blogs, mitgeliefert.** Die Variablen (Hell/Dunkel) stammen aus `content/style/theme.css` des Blogs; sie werden als Teil der App übernommen (kleine Kopie, Hinweis auf die Quelle). Abweichungen vom Blog werden akzeptiert und in der Spec als Annäherung ausgewiesen. Alternative: CSS zur Laufzeit aus dem Klon lesen – verworfen (der Klon enthält Stile für die ganze Seite, Pfade und Schriften, die nicht passen).

**Rendern entprellt und im Hintergrund**, bei jedem Wechsel in die Vorschau neu; kein Live-Aktualisieren während des Tippens.

## Risks / Trade-offs

- [Vorschau weicht von der echten Seite ab] → Deutlicher Hinweis (Spec); Test gegen Beispielbeiträge, bei denen Bildzahl, Gruppen und Unterschriften mit dem Generator übereinstimmen.
- [Große Beiträge mit vielen Bildern laden langsam] → 700-Pixel-Varianten aus dem Cache, Lazy Loading in der WebView.
- [Fremdes HTML im Beitrag] → JavaScript aus, Netzwerk blockiert, Links nur nach Bestätigung.
- [Schriften (Lobster, Lora) vergrößern die App] → Nur die benötigten Schnitte mitliefern; Lizenz (SIL OFL) prüfen.
