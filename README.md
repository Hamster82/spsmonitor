# SPS Monitor – Android-App

Native Android-App zum Lesen und Schreiben von DB-Werten einer Siemens
S7-315 über Ethernet. Kein Webserver, kein Termux, keine Brücke auf
einem PC: Die App spricht das S7-Protokoll selbst.

    App (Kotlin / Compose)  <--S7 über TCP:102-->  S7-315

Das S7-Protokoll (TPKT / COTP / S7-PDU) ist direkt in Kotlin umgesetzt,
deshalb wird keine native Bibliothek wie snap7 gebraucht.

## APK bauen

Auf dem eigenen Rechner ist dafür nichts zu installieren:

1. Dieses Projekt in ein GitHub-Repository hochladen
   (`settings.gradle.kts` muss oben liegen, nicht in einem Unterordner).
2. Reiter **Actions** öffnen, links „APK bauen“ wählen,
   rechts **Run workflow** drücken.
3. Nach etwa 3–6 Minuten liegt die fertige Datei
   - unter **Artifacts** als `SpsMonitor-APK`, und
   - zusätzlich im Branch **apk** als `SpsMonitor.apk` zum direkten Antippen.
4. Auf dem Handy öffnen und installieren. Android fragt einmal nach der
   Erlaubnis für Apps aus dieser Quelle — eine normale Handy-Einstellung,
   dafür braucht es keine Adminrechte am PC.

Die App ist mit einem festen Schlüssel signiert (`app/spsmonitor.jks`).
Dadurch lassen sich spätere Versionen über die alte installieren, ohne
vorher zu deinstallieren — die angelegten Seiten bleiben erhalten.

Alternativ geht auch Android Studio: Ordner öffnen, `Build` → `Build APK(s)`.

## Bedienung

**Verbindungsleiste oben:** IP-Adresse, Rack und Slot eintragen, dann
„Verbinden“. Der Schalter daneben liest alle zwei Sekunden selbsttätig nach.

**Reiter Tags:** Die Werte im Datenbaustein anlegen — Name, DB-Nummer,
Offset, bei `Bool` zusätzlich das Bit, und der Datentyp (Bool, Byte, Int,
DInt, Real). Diese Angaben stammen aus dem Step7-/TIA-Projekt:
`DBD4` → Offset 4, `DBX8.0` → Offset 8 / Bit 0.

**Reiter Übersicht:** Beliebig viele Seiten mit eigenen Anlagenbildern.
Im Bearbeiten-Modus lassen sich Elemente mit dem Finger verschieben und
antippen zum Einstellen; ausgeschaltet ist es die Live-Ansicht.

Elementarten:

- **Symbol** — 22 Industriesymbole (siehe unten). Schaltsymbole werden
  grün, wenn der verknüpfte Tag `True` bzw. 1 liefert. Anzeigende Symbole
  brauchen zusätzlich einen Messbereich (Min/Max), daraus ergibt sich
  Füllstand bzw. Zeigerausschlag.
- **Textfeld** — freie Beschriftung.
- **Messwertfeld** — zeigt den Live-Wert mit Einheit und Nachkommastellen;
  wahlweise per Antippen beschreibbar.
- **Schalter** — schreibt beim Antippen einen festen Wert in einen Tag.
- **Seitenwechsel** — springt zu einer anderen Übersichtsseite, so lassen
  sich die Seiten wie bei einem Bedienpanel verketten.

Verfügbare Symbole:

| Gruppe | Symbole |
|---|---|
| Antriebe & Aggregate | Motor, Pumpe, Gebläse, Rührwerk, Förderschnecke, BHKW |
| Armaturen & Leitungen | Ventil, Regelventil, Rohr waagerecht, Rohr senkrecht, Filter, Wärmetauscher |
| Behälter | Tank, Gasspeicher, Fermenter |
| Messen & Regeln | Temperaturanzeige, Temperaturregelung, Manometer, Durchflussmesser, Balkenanzeige |
| Melden | Lampe, Störmelder |

Die **Temperaturregelung** zeigt Istwert, Sollwertmarke und das
Hysterese-Band, darunter „Heizen EIN“ / „im Band“ / „Heizen AUS“. Das ist
bewusst nur eine Anzeige der Situation — geregelt wird weiterhin in der
SPS, die App greift nicht selbst ein.

**Reiter Auswertung:** Zeichnet die gelesenen Werte als Verlaufskurven
mit, Zeitraum wählbar, dazu Minimum, Maximum und Mittelwert je Kurve.
Über „Als CSV teilen“ geht der Verlauf an jede andere App (Mail, Drive,
Dateien) — Semikolon und Dezimalkomma, Excel öffnet die Datei direkt.

Die Aufzeichnung liegt im Arbeitsspeicher und endet mit der App. Für
dauerhafte Daten also vorher exportieren.

## Voraussetzungen an der Steuerung

- An der CPU muss **„Zugriff mit PUT/GET-Kommunikation vom Fernpartner
  zulassen“** aktiviert sein (Eigenschaften → Schutz). Ohne das blockt
  die 315 jeden Zugriff.
- Bei Datenbausteinen aus dem TIA Portal: **„optimierter Baustein-Zugriff“
  ausschalten**, sonst gibt es keine festen Byte-Offsets zum Ansprechen.
- Rack und Slot stehen in der Hardwarekonfiguration, bei der 315 meist
  Rack 0 / Slot 2.
- Das Handy muss im selben Netz hängen wie die Steuerung, also im WLAN
  der Anlage. Über Mobilfunk ist sie nicht erreichbar.

## Sicherheitshinweis

Die App kann in die laufende Anlage schreiben und hat keine Anmeldung.
Ein Handy geht schneller verloren oder wird entsperrt liegen gelassen als
ein fest installierter Rechner. Es lohnt sich, nur die wirklich nötigen
Tags beschreibbar zu halten, und der Einsatz im Anlagennetz sollte mit
der IT abgestimmt sein.

## Aufbau

```
app/src/main/java/de/spsmonitor/
  MainActivity.kt          Verbindungsleiste und Reiter
  MainViewModel.kt         Zustand, zyklisches Lesen, Schreiben, Aufzeichnung
  data/S7Client.kt         S7-Protokoll (TPKT, COTP, S7-PDU)
  data/Models.kt           Tags, Seiten, Elemente und ihre JSON-Form
  data/Repo.kt             Speichern der Konfiguration
  ui/Symbole.kt            die 22 Industriesymbole
  ui/TagsScreen.kt         Tag-Liste, Bearbeiten, Schreiben
  ui/UebersichtScreen.kt   Seiten, Verschieben, Live-Ansicht
  ui/ElementDialog.kt      Einstellungen eines Elements mit Vorschau
  ui/AuswertungScreen.kt   Kurven, Kennzahlen, CSV-Export
.github/workflows/apk-bauen.yml   baut die APK
```

Das Format der Konfiguration ist dasselbe wie bei der Windows-Fassung,
Tag-Listen lassen sich also zwischen beiden austauschen.

## Stand

Das Protokoll ist gegen Referenztelegramme geprüft (Verbindungsaufbau
byteweise identisch, Zahlenformate verifiziert). An einer echten S7-315
ist die App noch nicht erprobt — beim ersten Einsatz also zuerst nur
lesen und erst danach schreiben, am besten an einem unkritischen
Datenbaustein.
