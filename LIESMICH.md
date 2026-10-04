# SPS Monitor – Windows-Fassung

Liest und schreibt DB-Werte einer Siemens S7-315 über Ethernet. Bedient wird
im Browser; dahinter hält ein kleines Programm („die Brücke“) die eigentliche
Verbindung zur Steuerung.

Dieselben Projekte (`projekt.json`) laufen unverändert in der Android-App im
Zweig `main` dieses Projektarchivs.

## Herunterladen

Oben auf **Code → Download ZIP**, oder direkt:

    https://github.com/Hamster82/spsmonitor/archive/refs/heads/windows.zip

Das Archiv entpacken – alle Dateien bleiben zusammen in einem Ordner.

## Starten

Beim allerersten Mal **`SPS Monitor starten.bat`** doppelklicken. Das Skript
baut die Brücke (dauert einige Minuten, braucht Internet), startet sie und
öffnet die Oberfläche im Browser. Administratorrechte braucht es dafür nicht.

Ab dem zweiten Mal genügt die Oberfläche selbst: oben rechts steht ein Schild
**Brücke läuft** / **Brücke aus**, ein Klick darauf öffnet die Leiste mit
**Brücke starten**, **Brücke stoppen** und dem Haken *beim Schließen mit
beenden*.

Alles Weitere steht in `Anleitung.md`, das Dateiformat in `PROJEKTFORMAT.md`.

## Hinweis

Die Brücke kennt keine Anmeldung. Wer die Oberfläche im Netz erreicht, kann
die Anlage bedienen. Das gehört mit der IT abgestimmt; wer das nicht will,
benutzt nur `http://localhost:8080` auf dem Rechner selbst und lehnt die
Firewall-Freigabe beim ersten Start ab.
