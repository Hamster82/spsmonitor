#!/usr/bin/env python3
"""
Setzt symbole.js in die Vorlage ein und erzeugt die fertige oberflaeche.html.

So gibt es für die Symbole nur eine Quelle, und die ausgelieferte Datei
bleibt trotzdem eine einzelne, überall lauffähige HTML-Datei.
"""
import os
import sys

ORDNER = os.path.dirname(os.path.abspath(__file__))
VORLAGE = os.path.join(ORDNER, "oberflaeche-vorlage.html")
SYMBOLE = os.path.join(ORDNER, "symbole.js")
ZIEL = os.path.join(ORDNER, "oberflaeche.html")

MARKE = "/*SYMBOLE_HIER*/"


def main():
    vorlage = open(VORLAGE, encoding="utf-8").read()
    symbole = open(SYMBOLE, encoding="utf-8").read()

    if MARKE not in vorlage:
        print("FEHLER: Einsetzmarke %s fehlt in der Vorlage." % MARKE)
        return 1

    fertig = vorlage.replace(MARKE, symbole, 1)
    open(ZIEL, "w", encoding="utf-8").write(fertig)

    print("oberflaeche.html erzeugt (%d Zeichen)" % len(fertig))
    return 0


if __name__ == "__main__":
    sys.exit(main())
