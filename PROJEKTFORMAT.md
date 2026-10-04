# Gemeinsames Projektformat

Beide Fassungen des SPS Monitors – Windows und Android – lesen und
schreiben dieselbe Datei:
**`projekt.json`**. Ein Projekt, das in der Android-App gebaut wurde,
lässt sich unverändert in der HTML-Oberfläche öffnen und umgekehrt.

## Aufbau

```json
{
  "format": "spsmonitor-projekt",
  "version": 1,

  "ip": "192.168.0.1",
  "rack": 0,
  "slot": 2,

  "tags": [
    { "name": "Einspeisung", "db": 1, "offset": 0, "bit": 0, "typ": "Real" }
  ],

  "seiten": [
    {
      "name": "Übersicht 1",
      "elemente": [
        {
          "id": "e1",
          "typ": "Symbol",
          "x": 40, "y": 40, "breite": 130, "hoehe": 130,
          "text": "",
          "schriftGroesse": 15,
          "symbolTyp": "Motor",
          "tagName": "Einspeisung",
          "einheit": "", "nachkommastellen": 1,
          "editierbar": false,
          "minWert": 0, "maxWert": 100,
          "sollwert": 40, "hysterese": 2,
          "schreibWert": "True",
          "zielSeite": ""
        }
      ]
    }
  ]
}
```

## Koordinaten

Die Leinwand einer Seite ist **1000 Einheiten breit und 700 hoch** –
unabhängig vom Gerät. Jede Fassung rechnet diese Einheiten auf die
tatsächliche Anzeigefläche um. Dadurch sieht dieselbe Seite auf dem
Handy und am Rechner gleich aus, nur größer oder kleiner.

Ein Symbol mit `breite: 130` füllt also etwa ein Achtel der Seitenbreite.

## Feldbedeutung

| Feld | Gilt für | Bedeutung |
|---|---|---|
| `typ` | alle | `Symbol`, `Text`, `Variable`, `Button`, `Seitenwechsel` |
| `x`, `y` | alle | Position in Leinwandeinheiten (linke obere Ecke) |
| `breite`, `hoehe` | alle | Größe in Leinwandeinheiten |
| `text` | Text, Button, Seitenwechsel | Beschriftung |
| `schriftGroesse` | Text, Variable, Button, Seitenwechsel | in Leinwandeinheiten |
| `symbolTyp` | Symbol | einer der 22 Symbolnamen |
| `tagName` | Symbol, Variable, Button | verknüpfter Tag |
| `einheit`, `nachkommastellen` | Variable, messende Symbole | Anzeigeformat |
| `editierbar` | Variable | per Antippen beschreibbar |
| `minWert`, `maxWert` | messende Symbole | Messbereich für Füllstand/Zeiger |
| `sollwert`, `hysterese` | Temperaturregelung | Sollwert und Bandbreite |
| `schreibWert` | Button | Wert, der beim Klick geschrieben wird |
| `zielSeite` | Seitenwechsel | Name der Zielseite |

## Symbolnamen

```
Antriebe & Aggregate   Motor, Pumpe, Gebläse, Rührwerk, Förderschnecke, BHKW
Armaturen & Leitungen  Ventil, Regelventil, Rohr waagerecht, Rohr senkrecht,
                       Filter, Wärmetauscher
Behälter               Tank, Gasspeicher, Fermenter
Messen & Regeln        Temperaturanzeige, Temperaturregelung, Manometer,
                       Durchflussmesser, Balkenanzeige
Melden                 Lampe, Störmelder
```

## Wo die Datei liegt

| Fassung | Ablage | Austausch |
|---|---|---|
| Windows (Brücke läuft) | `projekt.json` neben `SpsBruecke.exe` | Datei kopieren |
| Windows (ohne Brücke) | im Browser gespeichert | „Projekt speichern" / „Projekt laden" |
| Android-App | im App-Ordner | „Projekt speichern" / „Projekt laden" |

In beiden Fassungen gibt es im Reiter *Tags* die Knöpfe **Projekt
speichern** und **Projekt laden**. Damit wandert ein Projekt zwischen
den Geräten – per Datei, E-Mail, USB-Stick oder Cloud-Ordner.

## Ältere Dateien

Dateien aus früheren Fassungen (`konfiguration.json`, `uebersicht.json`,
`tags.json`) werden beim Laden automatisch erkannt und übernommen.
Beim nächsten Speichern entsteht daraus eine `projekt.json`.
