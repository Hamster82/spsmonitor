# SPS Monitor – Windows

Liest und schreibt DB-Werte einer Siemens S7-315 über Ethernet.
Die Bedienung läuft im Browser; dahinter arbeitet ein kleines Programm,
das die eigentliche Verbindung zur Steuerung hält.

    Browser (oberflaeche.html)  <--HTTP-->  SpsBruecke.exe  <--S7--> S7-315

Warum dieser Umweg: Ein Browser darf aus Sicherheitsgründen keine rohen
TCP-Verbindungen aufbauen. Die S7-315 spricht aber genau das – ihr eigenes
Protokoll auf Port 102. Die Brücke übersetzt dazwischen.

Dieselben Projekte laufen unverändert in der Android-App.

---

## 1. Starten

**Beim allerersten Mal `SPS Monitor starten.bat` doppelklicken.** Das Skript
macht alles:

1. Es baut die Brücke (dauert einige Minuten, braucht Internet)
2. Danach startet es sie, falls sie nicht schon läuft
3. Zum Schluss öffnet sich die Oberfläche im Browser

Ist Port 8080 belegt, lässt sich ein anderer mitgeben – dafür eine
Eingabeaufforderung im Ordner öffnen und eintippen:

    "SPS Monitor starten.bat" 8081

**Ab dem zweiten Mal genügt die Oberfläche selbst** – siehe Abschnitt 2.

**Alternativ: `SPS Monitor.hta`** – dasselbe als kleines Fenster mit Knöpfen,
mit Anzeige, ob die Brücke gebaut ist und läuft, und mit einem Knopf zum
Beenden. Das ist eine HTML-Anwendung; Windows führt sie mit dem eigenen
Programm `mshta.exe` aus, wodurch sie – anders als eine Seite im Browser –
Programme starten und beenden darf.

> Zwei Hinweise dazu: Manche Virenscanner schauen bei `.hta`-Dateien genauer
> hin, weil das Format auch von Schadsoftware benutzt wird. Und in streng
> verwalteten Firmennetzen ist `mshta.exe` mitunter gesperrt. In beiden Fällen
> einfach die `.bat`-Datei nehmen – die kann dasselbe.

Soll das Symbol auf dem Desktop liegen: Rechtsklick auf
`SPS Monitor starten.bat` → *Senden an* → *Desktop (Verknüpfung erstellen)*.

---

## 2. Die Brücke aus der Oberfläche steuern

Oben rechts in der Kopfzeile sitzt ein kleines Schild: **Brücke läuft**
(grün) oder **Brücke aus** (gelb). Ein Klick darauf klappt die Brückenleiste
auf. Läuft die Brücke nicht, geht die Leiste von selbst auf.

In der Leiste steht, was gerade los ist – Adresse, Fassung, Laufzeit und ob
die Steuerung verbunden ist –, und daneben liegen die Knöpfe:

| Knopf | Wirkung |
|---|---|
| **Brücke starten** | startet sie auf diesem Rechner |
| **Brücke stoppen** | beendet sie sofort (mit Rückfrage) |
| **Erneut suchen** | prüft die eingetragene Adresse noch einmal |

### Wie das Starten funktioniert

Eine Seite im Browser darf von sich aus kein Programm starten – das verbietet
jeder Browser, und das ist auch richtig so. Deshalb meldet sich die Brücke bei
ihrem ersten Lauf unter dem eigenen Benutzerkonto bei Windows an, und zwar für
Adressen der Form `spsmonitor://start?port=8080`. Ein Klick auf **Brücke
starten** ruft genau so eine Adresse auf; Windows startet daraufhin die
Brücke. Der Browser fragt dabei einmal kurz nach („Diese Seite möchte
spsmonitor öffnen“) – mit *Öffnen* bestätigen, und bei Chrome lässt sich das
mit dem Haken *Immer zulassen* dauerhaft merken.

Eingetragen wird das nur unter `HKEY_CURRENT_USER`, also im eigenen
Benutzerkonto – **ohne Administratorrechte**. Deshalb der eine Lauf über die
`.bat` am Anfang: vorher kennt Windows die Brücke nicht.

### Beenden beim Schließen

In der Brückenleiste steht ein Haken **„beim Schließen mit beenden“**. Er ist
von selbst gesetzt, wenn die Brücke auf demselben Rechner läuft. Dann gilt:

- Fenster oder Reiter zu → die Brücke geht ein paar Sekunden später von selbst
  aus. Es bleibt also nichts im Hintergrund stehen.
- **Neu laden** nimmt sie nicht mit. Die Oberfläche meldet sich alle paar
  Sekunden bei der Brücke; bleibt sie weg, wartet die Brücke noch einige
  Sekunden ab – lange genug für ein Neuladen, kurz genug, dass nichts
  vergessen weiterläuft.
- Auch ein Absturz des Browsers reicht: ohne Lebenszeichen beendet sich die
  Brücke ebenso.

Wer den Haken wegnimmt, lässt die Brücke bewusst weiterlaufen – etwa wenn
nebenher ein Handy über sie zugreift.

Zeigt die Adresse auf **einen anderen Rechner**, sind Starten und der Haken
gesperrt: ein fremder Rechner lässt sich von hier aus nicht hochfahren, und
versehentlich abgeschaltet werden soll er auch nicht. Beenden geht weiterhin,
dafür gibt es den Knopf.

Dasselbe gilt im Startfenster `SPS Monitor.hta`: Wird es geschlossen, nimmt es
die Brücke mit – es sei denn, in der Oberfläche arbeitet gerade jemand. Dann
bleibt sie stehen und geht später zusammen mit der Oberfläche aus.

Von der Eingabeaufforderung aus geht es auch:

    "SPS Monitor starten.bat" stopp
    "SPS Monitor starten.bat" 8081 stopp

---

## 3. Bedienung

Oben IP-Adresse, Rack und Slot eintragen, dann **Verbinden**. Der Schalter
daneben liest alle zwei Sekunden selbsttätig nach.

### Reiter „Tags“

Die Werte im Datenbaustein anlegen: Name, DB-Nummer, Offset, bei `Bool`
zusätzlich das Bit, und den Datentyp (Bool, Byte, Int, DInt, Real). Diese
Angaben stammen aus dem Step7-/TIA-Projekt – `DBD4` wird zu Offset 4,
`DBX8.0` zu Offset 8 mit Bit 0.

Hier liegen auch **Projekt speichern** und **Projekt laden**.

### Reiter „Übersicht“

Beliebig viele Seiten mit eigenen Anlagenbildern. Der Schalter *Bearbeiten*
wechselt zwischen Gestalten und Live-Ansicht.

Elementarten:

- **Symbol** – 22 Industriesymbole (siehe unten)
- **Textfeld** – freie Beschriftung
- **Messwertfeld** – zeigt den Live-Wert mit Einheit und Nachkommastellen;
  wahlweise per Klick beschreibbar
- **Schalter** – schreibt beim Klick einen festen Wert in einen Tag
- **Seitenwechsel** – springt zu einer anderen Seite, so lassen sich die
  Seiten wie bei einem Bedienpanel verketten

Verfügbare Symbole:

| Gruppe | Symbole |
|---|---|
| Antriebe & Aggregate | Motor, Pumpe, Gebläse, Rührwerk, Förderschnecke, BHKW |
| Armaturen & Leitungen | Ventil, Regelventil, Rohr waagerecht, Rohr senkrecht, Filter, Wärmetauscher |
| Behälter | Tank, Gasspeicher, Fermenter |
| Messen & Regeln | Temperaturanzeige, Temperaturregelung, Manometer, Durchflussmesser, Balkenanzeige |
| Melden | Lampe, Störmelder |

Schaltsymbole (Motor, Pumpe, Ventil, Lampe …) werden grün, wenn der
verknüpfte Tag `True` bzw. 1 liefert, sonst grau. Anzeigende Symbole brauchen
zusätzlich einen **Messbereich (Min/Max)** – daraus ergibt sich Füllstand
oder Zeigerausschlag –, dazu optional Einheit und Nachkommastellen.

Die **Temperaturregelung** zeigt Istwert, Sollwertmarke und das
Hysterese-Band, darunter „Heizen EIN“ / „im Band“ / „Heizen AUS“. Das ist
bewusst nur eine Anzeige der Situation – geregelt wird in der SPS.

### Reiter „Auswertung“

Zeichnet die gelesenen Werte als Verlaufskurven mit, Zeitraum wählbar, dazu
Minimum, Maximum und Mittelwert je Kurve. **CSV speichern** schreibt den
Verlauf mit Semikolon und Dezimalkomma – Excel öffnet die Datei direkt.
**CSV laden** holt einen früheren Verlauf zum Vergleichen zurück.

Die Aufzeichnung liegt nur im Browser und ist nach dem Neuladen weg – für
dauerhafte Daten also vorher speichern.

---

## 4. Ein Projekt, zwei Fassungen

Windows-Oberfläche und Android-App lesen und schreiben dieselbe Datei
**`projekt.json`**: Verbindungsdaten, Tag-Liste und alle Übersichtsseiten
stecken darin. Ein am Rechner gebautes Projekt lässt sich unverändert auf dem
Handy öffnen und umgekehrt – per USB, E-Mail oder Cloud-Ordner.

Die Seiten sind 1000 × 700 Einheiten groß, unabhängig vom Gerät. Jede Fassung
rechnet das auf die vorhandene Fläche um, sodass eine Seite überall
vollständig und gleich aussieht, nur größer oder kleiner.

Läuft die Brücke, liegt die Datei neben `SpsBruecke.exe`. Dateien aus
früheren Fassungen (`konfiguration.json`, `uebersicht.json`) werden beim
Laden automatisch erkannt und übernommen.

Die genaue Beschreibung steht in `PROJEKTFORMAT.md`.

---

## 5. Ohne Brücke

Die Datei `oberflaeche.html` lässt sich auch allein per Doppelklick öffnen.
Ein Kasten weist dann darauf hin, dass die Brücke fehlt. Möglich ist trotzdem:

- Übersichtsseiten aufbauen und gestalten, Tags eintragen
- Projekte laden und speichern
- frühere CSV-Dateien einlesen und als Verlaufskurven auswerten

Der Aufbau wird dabei im Browser selbst gespeichert und ist beim nächsten
Öffnen wieder da. Nicht möglich ohne Brücke: Live-Werte lesen und in die SPS
schreiben.

Läuft die Brücke auf einem anderen Port oder Rechner, lässt sich ihre Adresse
in diesem Kasten eintragen. Und ist sie auf diesem Rechner schon einmal
gelaufen, holt **Brücke starten** sie von hier aus zurück.

---

## 6. Vom Handy aus

Zwei Wege:

- **Die Android-App** (`SpsMonitor.apk`) – spricht selbst mit der Steuerung,
  es wird kein Rechner gebraucht. Das ist der bequemere Weg.
- **Über die Brücke** – läuft der Rechner ohnehin, zeigt das Konsolenfenster
  beim Start auch die Netzwerkadresse, z. B. `http://192.168.0.50:8080`. Die
  im Handy-Browser öffnen; über *Zum Startbildschirm hinzufügen* entsteht ein
  Symbol, das wie eine App startet. Dafür muss das Handy im selben WLAN sein
  und die Windows-Firewall den Port zulassen (beim ersten Start fragt sie
  danach; „Zulassen" für private Netzwerke genügt und braucht keine
  Adminrechte).

Oben rechts lässt sich die Ansicht zwischen *Automatisch*, *Handy* und
*Desktop* umschalten; die Einstellung merkt sich jedes Gerät.

---

## 7. Voraussetzungen an der Steuerung

- An der CPU muss **„Zugriff mit PUT/GET-Kommunikation vom Fernpartner
  zulassen"** aktiviert sein (Eigenschaften → Schutz). Ohne das blockt die
  315 jeden Zugriff.
- Bei Datenbausteinen aus dem TIA Portal: **„optimierter Baustein-Zugriff"
  ausschalten**, sonst gibt es keine festen Byte-Offsets zum Ansprechen.
- Rack und Slot stehen in der Hardwarekonfiguration, bei der 315 meist
  Rack 0 / Slot 2.

---

## 8. Sicherheitshinweis

Sobald die Oberfläche im Netz erreichbar ist, kann **jeder im selben Netz**
die Anlage bedienen – es gibt keine Anmeldung. Das sollte mit der IT
abgestimmt sein. Wer das nicht möchte, benutzt ausschließlich
`http://localhost:8080` auf dem Rechner selbst und lehnt die
Firewall-Freigabe beim ersten Start ab.

Das gilt auch fürs Beenden: Wer die Oberfläche erreicht, kann die Brücke auch
abschalten. Das automatische Mitbeenden dagegen greift ausschließlich vom
eigenen Rechner aus – ein Handy, das die Seite schließt, nimmt die Brücke
nicht mit.

---

## Dateien

```
SPS Monitor starten.bat   ← beim ersten Mal hiermit starten
                            ("… stopp" beendet die Brücke wieder)
SPS Monitor.hta           ← dasselbe als kleines Fenster mit Knöpfen
oberflaeche.html          die fertige Oberfläche (startet und stoppt
                            die Brücke ab dem zweiten Mal selbst)
PROJEKTFORMAT.md          Beschreibung von projekt.json

Program.cs                Mini-Webserver und die Schnittstelle
PlcService.cs             die eigentliche Sharp7-Kommunikation
SpsBruecke.csproj         Projektdatei
Kompilieren.bat           baut die EXE (ruft das Startskript selbst auf)
Installieren.ps1          holt bei Bedarf ein portables .NET SDK

oberflaeche-vorlage.html  Vorlage ohne Symbole
symbole.js                die 22 Industriesymbole (gemeinsame Quelle)
bauen.py                  setzt symbole.js in die Vorlage ein

projekt.json              entsteht beim ersten Speichern
```

Zum Ändern der Oberfläche `oberflaeche-vorlage.html` oder `symbole.js`
bearbeiten und danach `python bauen.py` aufrufen – das erzeugt die fertige
`oberflaeche.html`. Für Kleinigkeiten geht auch direkt `oberflaeche.html`.
