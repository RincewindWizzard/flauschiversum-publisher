# Spec Delta

## Purpose
Beschreibt das Aktualisieren der Beitragsliste per Wischgeste (nach unten ziehen) einschließlich Rückmeldung und barrierefreier Alternative.

## ADDED Requirements

### Requirement: Aktualisieren durch Nach-unten-Ziehen
Die Beitragsübersicht SHALL aktualisiert werden, wenn der Nutzer die Liste am oberen Rand nach unten zieht und loslässt, wie in anderen Apps üblich. Die Geste SHALL auch bei kurzer oder leerer Liste funktionieren. Steht die Liste nicht am oberen Rand, SHALL das Ziehen die Liste normal scrollen und nicht aktualisieren.

#### Scenario: Ziehen am oberen Rand
- **WHEN** die Liste ganz oben steht und der Nutzer sie nach unten zieht und loslässt
- **THEN** startet die App die Aktualisierung, und neue Beiträge vom Server erscheinen nach dem Holen in der Liste

#### Scenario: Weiter unten in der Liste
- **WHEN** der Nutzer die Liste weiter unten nach unten wischt
- **THEN** scrollt die Liste zurück nach oben, ohne zu aktualisieren

#### Scenario: Kurze Liste
- **WHEN** nur wenige Beiträge in der Liste stehen oder nach einer Suche kein Treffer
- **THEN** lässt sich die Liste trotzdem herunterziehen und aktualisieren

### Requirement: Rückmeldung während des Aktualisierens
Während des Aktualisierens SHALL ein Ladekreis am oberen Rand der Liste erscheinen und mindestens eine halbe Sekunde sichtbar bleiben, auch wenn das Holen schneller endet. Danach SHALL die Zeit „zuletzt aktualisiert“ den neuen Stand zeigen. Läuft schon eine Aktualisierung, SHALL das Ziehen keine zweite starten, der Ladekreis zeigt die laufende.

#### Scenario: Schnelles Holen
- **WHEN** die Aktualisierung nach 100 Millisekunden fertig ist
- **THEN** bleibt der Ladekreis dennoch kurz sichtbar und verschwindet danach

#### Scenario: Läuft schon
- **WHEN** die App gerade im Hintergrund aktualisiert und der Nutzer zieht
- **THEN** startet keine zweite Aktualisierung, und der Ladekreis erscheint bis zum Ende der laufenden

### Requirement: Fehler beim Aktualisieren
Scheitert das Aktualisieren, SHALL die Liste den zuletzt geholten Stand behalten und den Grund wie bisher als Hinweis zeigen („Kein Netz“, „Zugriff verweigert“), ohne die Bedienung zu blockieren.

#### Scenario: Kein Netz
- **WHEN** der Nutzer zieht und das Gerät hat kein Netz
- **THEN** bleibt die Liste unverändert, und ein Hinweis „Kein Netz“ erscheint

### Requirement: Barrierefreie Alternative
Weil eine Wischgeste nicht für alle nutzbar ist, SHALL die Liste die Aktion „Aktualisieren“ auch für Bedienungshilfen (TalkBack-Aktionen, Tastatur) anbieten. Ein eigenes Symbol in der Kopfleiste ist dafür nicht nötig.

#### Scenario: TalkBack
- **WHEN** TalkBack aktiv ist und der Nutzer die Aktionen der Liste öffnet
- **THEN** steht dort „Aktualisieren“, und die Auswahl startet dieselbe Aktualisierung
