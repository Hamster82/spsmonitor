# PV-Überschuss-Heizstab – neues LOGO!-Programm

**LOGO! 8.3** (Bestell-Nr. …`1FB08`…, 230 V, Relaisausgänge, Ethernet) · 3 Stufen à 1 kW, einzeln geschaltet ·
Betrieb **nur bei Netz-Einspeisung** · bedien- und beobachtbar über die **SPS-Monitor-App** (S7, VM = DB1).

> Das `.lsc`-Format von LOGO!Soft Comfort ist verschlüsselt und kann weder gelesen noch von außen erzeugt werden.
> Dieses Programm wird deshalb als **nachbaubarer Plan** geliefert. Vollständige Fassung mit Funktionsplan:
> **[PV-Heizstab_LOGO_Programm_2026-10-24.pdf](PV-Heizstab_LOGO_Programm_2026-10-24.pdf)** · Funktionsplan: [funktionsplan.svg](funktionsplan.svg)

## Prinzip

- **Zuschalten:** Überschuss (Einspeisung) > **1200 W** für **60 s** → nächste Stufe ein.
- **Sequenziell von selbst:** jede Stufe senkt den Überschuss um ~1 kW → Stufe 2 erst ab ~2,2 kW, Stufe 3 ab ~3,2 kW echtem Überschuss. Kein Schwingen.
- **Abschalten:** Netzbezug > **300 W** für **30 s** → Stufen fallen ab, bauen sich bei neuem Überschuss wieder auf.
- **Hand-Betrieb** über die App (M2 + M3/M4/M5), unabhängig vom Überschuss.
- Temperaturbegrenzung macht der Heizstab selbst; zusätzlich **mechanischer STB** in Reihe zu den Schützspulen empfohlen.

## Ein-/Ausgänge

| Signal | LOGO! | Hinweis |
|---|---|---|
| Stufe 1/2/3 (je 1 kW) | `Q1` / `Q2` / `Q3` | je ein Schütz → je eine Heizphase |
| Freigabe Automatik | `I1` | Schrankschalter (Automatik auch ohne App) |
| Überschuss (W) | Analogwert | aus **vorhandener Kostal-Schnittstelle**, in Block B1 skaliert → `AM1` |

## Blöcke (Kurzfassung – Details im PDF)

**Aufbereitung/Betriebsart**
- B1 Analogverstärker: Kostal-Wert → `AM1` (Verstärkung 1, bei falschem Vorzeichen **−1**)
- B2 Schwellwertschalter `AM1`: Ein 1200 / Aus 1000 → „genug“
- B3 Schwellwertschalter `AM1`: Ein −100 / Aus −300 → B4 NICHT → „Import“ → B5 Einschaltverz. 30 s → „ImportBest“
- B6 ODER(M1, I1) → „AutoFrei“ · B7 NICHT(M2) · B8 UND → „Auto“ (+ Merker M20) · B9 NICHT(Auto)

**Stufen-Sequenzer** (je Stufe: UND → Einschaltverz. 60 s → RS-Selbsthalterelais)
- Stufe 1: Set = Auto ∧ genug (60 s); Reset = ImportBest ∨ ¬Auto → **S1**
- Stufe 2: Set = Auto ∧ S1 ∧ genug (60 s); Reset = ImportBest ∨ ¬Auto ∨ ¬S1 → **S2**
- Stufe 3: Set = Auto ∧ S2 ∧ genug (60 s); Reset = ImportBest ∨ ¬Auto ∨ ¬S2 → **S3**

**Ausgänge**
- Q1 = (Auto ∧ S1) ∨ (M2 ∧ M3) · Q2 = (Auto ∧ S2) ∨ (M2 ∧ M4) · Q3 = (Auto ∧ S3) ∨ (M2 ∧ M5)

## App-Anbindung (VM = DB1)

LOGO! als **S7-Server** aktivieren (feste IP, Server-Verbindung + „Zugriff zulassen“, TSAP notieren). Merker/Ausgänge per
VM-Mapping auf feste Offsets legen; die App nutzt dieselben DB1-Offsets:

| Richtung | Wert | LOGO! | DB1 (App) | Typ |
|---|---|---|---|---|
| App → LOGO! | Automatik / Hand | M1 / M2 | `DB1.DBX0.0` / `0.1` | Bool |
| App → LOGO! | Hand Stufe 1/2/3 | M3/M4/M5 | `DB1.DBX0.2 / 0.3 / 0.4` | Bool |
| LOGO! → App | Stufe 1/2/3 ein | Q1/Q2/Q3 | `DB1.DBX1.0 / 1.1 / 1.2` | Bool |
| LOGO! → App | Automatik / Hand aktiv | M20 / M2 | `DB1.DBX1.3 / 1.4` | Bool |
| LOGO! → App | Überschuss (W) | AM1 | `DB1.DBW2` | Int |
| App → LOGO! (opt.) | Ein-/Aus-Schwelle | AM3 / AM4 | `DB1.DBW4 / DBW6` | Int |

**Verbindung:** LOGO! 8 nutzt feste **TSAP** (üblich lokal `0x0100` / entfernt `0x0200`), nicht Rack/Slot. Kommt die App mit
Rack/Slot nicht durch, braucht sie einen kleinen **„LOGO!-Modus“ (TSAP)** – auf Zuruf ergänzbar.

## Inbetriebnahme (Kurz)

1. **Überschuss kalibrieren:** in der App `DB1.DBW2` beobachten – bei Einspeisung **positiv, in Watt**. Sonst B1-Verstärkung
   (Vorzeichen/Maßstab) anpassen. *(Das ist die häufigste Ursache, warum solche Programme „nicht funktionieren“.)*
2. **Hand-Test** (M2 + M3/M4/M5), dann **Automatik-Test** (I1/M1, künstlich Last erzeugen → Stufen müssen abschalten).

---

## Anbindung an das BESTEHENDE Programm

Der Export (`Meyer_PV.pdf`) zeigt: Die LOGO! liest die Kostal (192.168.178.25, Modbus TCP,
Port 1502, Unit 71) und rechnet den Überschuss als **PV − Gesamtverbrauch** (Block B001).

> **Korrektur / zurückgezogen:** Eine frühere Fassung dieser README empfahl, die Modbus-Register
> (Länge 2 / gerade Register / REAL) zu ändern. **Das war falsch.** Die LOGO! adressiert
> 1-basiert (Registernummer = Kostal-Doku-Adresse + 1); die Original-Lesung funktioniert und
> wird **nicht** angefasst. Die aktuelle, korrigierte Anleitung ist
> **`LOGO-Anleitung_Steuerung-App_2026-10-24.pdf`** – sie ändert nur die Schaltlogik und bindet
> die App an, die Kostal-Seite bleibt unverändert.

## App-Datei `projekt.json`

Fertiges Bedienbild für die SPS-Monitor-App: sechs Messwerte (PV, Hausverbrauch, Netzleistung,
Batterie-Leistung, SOC, Überschuss), drei Stufen-Melder, Betriebsart (Automatik/Hand),
Hand-Stufen und die einstellbare Ein-Schwelle. **Die Adressen sind in der App frei editierbar** –
trage dort genau die VM-Adressen ein, die deine LOGO! bereitstellt.

| Zweck | Wert | DB1-Adresse (App) | Typ | LOGO!-seitig |
|---|---|---|---|---|
| App → LOGO! | Automatik / Hand | `DBX200.0 / .1` | Bool | **neu anzulegen** (App-Steuerbits) |
| App → LOGO! | Hand Stufe 1/2/3 | `DBX200.2 / .3 / .4` | Bool | **neu** |
| LOGO! → App | Stufe 1/2/3 ein | `DBX201.0 / .1 / .2` | Bool | Q1/Q2/Q3 spiegeln |
| LOGO! → App | Auto / Hand aktiv | `DBX201.3 / .4` | Bool | Status |
| LOGO! → App | PV / Hausverbrauch | `DBW202 / DBW204` | Int (W) | aus B003 / B004 |
| LOGO! → App | Netzleistung (optional) | `DBW206` | Int (W) | nur falls vorhanden |
| LOGO! → App | Batterie-Leistung / SOC | `DBW208 / DBW210` | Int | aus B022 / B005 |
| LOGO! → App | Überschuss | `DBW212` | Int (W) | aus B001 |
| App ↔ LOGO! | Ein-Schwelle | `DBW50` | Int (W) | **schon vorhanden** (B010.On) |

**Wichtig – Steuern erfordert LOGO!-Ergänzung:** Dein jetziges Programm hat keine App-Steuerbits.
Zum Bedienen aus der App drei bis fünf Merker anlegen (Automatik/Hand/Hand-Stufen), per VM-Zuordnung
auf `VB200` legen und in die Stufen-Logik einbinden (Auto: wie bisher; Hand: Stufen folgen den Bits).
Bis dahin **zeigt** die App die Werte nur an.

**Display der LOGO!:** Der vorhandene Meldetext (B002) zeigt bereits ÜS_W, PV_W, HV_W, Batt_%, Batt_Wh,
Batt_W und die Stufen. Nach der Modbus-Korrektur stimmen diese Zahlen; sinnvoll ist, **Überschuss**,
**PV**, **Hausverbrauch**, **SOC** und **Stufe 1/2/3** gut sichtbar zu lassen.
