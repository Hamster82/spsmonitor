#!/usr/bin/env python3
"""
Setzt symbole.js in die Vorlage ein und erzeugt die fertige oberflaeche.html.

Zusätzlich wird der SHA-256-Hash des fertigen <script>-Blocks berechnet und in
die Inhaltssicherheits-Richtlinie (CSP) eingesetzt. Dadurch lässt der Browser
genau dieses eine Skript laufen und nichts sonst - eingeschleustes Skript wird
blockiert.

So gibt es für die Symbole nur eine Quelle, und die ausgelieferte Datei bleibt
trotzdem eine einzelne, überall lauffähige HTML-Datei.

Hinweis zu Zeilenenden: Die Datei wird mit \\n geschrieben und der Hash über
genau diese Bytes gebildet. Wird die Datei später auf CRLF umgestellt, passt
der Hash nicht mehr und der Browser blockiert das Skript. Die .gitattributes
hält die Datei deshalb auf LF; nach einer Handänderung bitte erneut bauen.
"""
import base64
import hashlib
import os
import re
import sys

ORDNER = os.path.dirname(os.path.abspath(__file__))
VORLAGE = os.path.join(ORDNER, "oberflaeche-vorlage.html")
SYMBOLE = os.path.join(ORDNER, "symbole.js")
ZIEL = os.path.join(ORDNER, "oberflaeche.html")

MARKE = "/*SYMBOLE_HIER*/"
CSP_MARKE = "__CSP_SCRIPT_HASH__"


def script_hash(html):
    """SHA-256 über den Inhalt des (einzigen) <script>-Blocks, Base64, wie es
    die CSP erwartet: sha256-<base64>."""
    treffer = re.search(r"<script>(.*)</script>", html, re.S)
    if not treffer:
        raise SystemExit("FEHLER: Kein <script>-Block gefunden.")
    inhalt = treffer.group(1).encode("utf-8")
    roh = hashlib.sha256(inhalt).digest()
    return "sha256-" + base64.b64encode(roh).decode("ascii")


def main():
    with open(VORLAGE, encoding="utf-8", newline="\n") as f:
        vorlage = f.read()
    with open(SYMBOLE, encoding="utf-8", newline="\n") as f:
        symbole = f.read()

    if MARKE not in vorlage:
        print("FEHLER: Einsetzmarke %s fehlt in der Vorlage." % MARKE)
        return 1
    if CSP_MARKE not in vorlage:
        print("FEHLER: CSP-Platzhalter %s fehlt in der Vorlage." % CSP_MARKE)
        return 1

    # 1. Symbole einsetzen - erst danach steht der endgültige Skripttext fest.
    fertig = vorlage.replace(MARKE, symbole, 1)

    # 2. Hash über den fertigen Skriptblock bilden (der CSP-Platzhalter steht
    #    im <head>, außerhalb des Skripts, und verändert den Hash nicht).
    h = script_hash(fertig)
    fertig = fertig.replace(CSP_MARKE, h, 1)

    # 3. Mit festen \n schreiben, damit der Hash zu den Bytes passt.
    with open(ZIEL, "w", encoding="utf-8", newline="\n") as f:
        f.write(fertig)

    print("oberflaeche.html erzeugt (%d Zeichen)" % len(fertig))
    print("CSP-Skript-Hash: %s" % h)
    return 0


if __name__ == "__main__":
    sys.exit(main())
