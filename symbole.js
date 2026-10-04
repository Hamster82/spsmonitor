/* ============================================================================
   Industriesymbole – wortgleiche Übertragung aus der Android-App (Symbole.kt).
   Gezeichnet in einem gedachten 100x100-Feld und auf die Elementgröße skaliert,
   damit ein Projekt in beiden Fassungen gleich aussieht.
   ============================================================================ */

const SYMBOL_GRUPPEN = [
  ["Antriebe & Aggregate", ["Motor", "Pumpe", "Gebläse", "Rührwerk", "Förderschnecke", "BHKW"]],
  ["Armaturen & Leitungen", ["Ventil", "Regelventil", "Rohr waagerecht", "Rohr senkrecht", "Filter", "Wärmetauscher"]],
  ["Behälter", ["Tank", "Gasspeicher", "Fermenter"]],
  ["Messen & Regeln", ["Temperaturanzeige", "Temperaturregelung", "Manometer", "Durchflussmesser", "Balkenanzeige"]],
  ["Melden", ["Lampe", "Störmelder"]]
];

const SYMBOLE_ALLE = SYMBOL_GRUPPEN.flatMap(g => g[1]);

/** Symbole, die einen Messwert darstellen und deshalb Min/Max brauchen. */
const SYMBOLE_MIT_MESSWERT = [
  "Tank", "Gasspeicher", "Fermenter", "Regelventil", "Durchflussmesser",
  "Temperaturanzeige", "Temperaturregelung", "Manometer", "Balkenanzeige"
];

// Farbwelt – gleiche Werte wie in der App
const F = {
  RAND: "#1F2D3D",
  METALL: "#8D9AAC",
  METALL_HELL: "#C6CFDA",
  GEHAEUSE: "#3E5871",
  GRUEN: "#2EB85C",
  GRAU: "#BFC7D1",
  ROT: "#D9372B",
  GELB: "#F0B429",
  FLUESSIG: "#3E8ACC",
  GAS: "#9AD0F5",
  WEISS: "#F7F9FC"
};

/** Hex-Farbe mit Deckkraft versehen. */
function mitAlpha(hex, alpha) {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

function symbolIstEin(wert) {
  if (wert === undefined || wert === null) return false;
  const w = String(wert).trim();
  return w.toLowerCase() === "true" || w === "1";
}

function symbolAlsZahl(wert) {
  if (wert === undefined || wert === null || String(wert).trim() === "") return null;
  const w = String(wert).trim();
  if (w.toLowerCase() === "true") return 1;
  if (w.toLowerCase() === "false") return 0;
  const z = parseFloat(w.replace(",", "."));
  return isNaN(z) ? null : z;
}

function symbolAnteil(wert, el) {
  const zahl = symbolAlsZahl(wert);
  if (zahl === null) return 0;
  const spanne = (el.maxWert ?? 100) - (el.minWert ?? 0);
  if (spanne === 0) return 0;
  return Math.max(0, Math.min(1, (zahl - (el.minWert ?? 0)) / spanne));
}

function symbolMesstext(wert, el) {
  const zahl = symbolAlsZahl(wert);
  if (zahl === null) return "–";
  const nk = Math.max(0, Math.min(4, el.nachkommastellen ?? 1));
  const text = zahl.toFixed(nk).replace(".", ",");
  return el.einheit ? text + " " + el.einheit : text;
}

/**
 * Zeichnet ein Symbol mittig und formatfüllend in die angegebene Fläche.
 * ctx: 2D-Zeichenkontext, breite/hoehe: Fläche in Bildpunkten.
 */
function zeichneSymbol(ctx, el, wert, breite, hoehe) {
  const typ = el.symbolTyp || "Motor";
  const skala = Math.min(breite, hoehe) / 100;
  const ox = (breite - 100 * skala) / 2;
  const oy = (hoehe - 100 * skala) / 2;

  const px = x => ox + x * skala;
  const py = y => oy + y * skala;
  const dicke = w => Math.max(0.6, w * skala);

  function pfadRundrechteck(x, y, b, h, ecke) {
    const r = Math.min(ecke * skala, (b * skala) / 2, (h * skala) / 2);
    ctx.beginPath();
    if (ctx.roundRect) {
      ctx.roundRect(px(x), py(y), b * skala, h * skala, r);
    } else {
      const X = px(x), Y = py(y), B = b * skala, H = h * skala;
      ctx.moveTo(X + r, Y);
      ctx.lineTo(X + B - r, Y); ctx.quadraticCurveTo(X + B, Y, X + B, Y + r);
      ctx.lineTo(X + B, Y + H - r); ctx.quadraticCurveTo(X + B, Y + H, X + B - r, Y + H);
      ctx.lineTo(X + r, Y + H); ctx.quadraticCurveTo(X, Y + H, X, Y + H - r);
      ctx.lineTo(X, Y + r); ctx.quadraticCurveTo(X, Y, X + r, Y);
      ctx.closePath();
    }
  }

  function kasten(x, y, b, h, farbe, ecke = 0) {
    ctx.fillStyle = farbe;
    if (ecke > 0) { pfadRundrechteck(x, y, b, h, ecke); ctx.fill(); }
    else ctx.fillRect(px(x), py(y), b * skala, h * skala);
  }

  function kastenRand(x, y, b, h, farbe, w = 2, ecke = 0) {
    ctx.strokeStyle = farbe;
    ctx.lineWidth = dicke(w);
    if (ecke > 0) { pfadRundrechteck(x, y, b, h, ecke); ctx.stroke(); }
    else ctx.strokeRect(px(x), py(y), b * skala, h * skala);
  }

  function linie(x1, y1, x2, y2, farbe, w = 2) {
    ctx.strokeStyle = farbe;
    ctx.lineWidth = dicke(w);
    ctx.beginPath();
    ctx.moveTo(px(x1), py(y1));
    ctx.lineTo(px(x2), py(y2));
    ctx.stroke();
  }

  function kreis(cx, cy, r, farbe) {
    ctx.fillStyle = farbe;
    ctx.beginPath();
    ctx.arc(px(cx), py(cy), r * skala, 0, Math.PI * 2);
    ctx.fill();
  }

  function kreisRand(cx, cy, r, farbe, w = 2) {
    ctx.strokeStyle = farbe;
    ctx.lineWidth = dicke(w);
    ctx.beginPath();
    ctx.arc(px(cx), py(cy), r * skala, 0, Math.PI * 2);
    ctx.stroke();
  }

  function oval(x, y, b, h, farbe) {
    ctx.fillStyle = farbe;
    ctx.beginPath();
    ctx.ellipse(px(x + b / 2), py(y + h / 2), (b * skala) / 2, (h * skala) / 2, 0, 0, Math.PI * 2);
    ctx.fill();
  }

  function ovalRand(x, y, b, h, farbe, w = 2) {
    ctx.strokeStyle = farbe;
    ctx.lineWidth = dicke(w);
    ctx.beginPath();
    ctx.ellipse(px(x + b / 2), py(y + h / 2), (b * skala) / 2, (h * skala) / 2, 0, 0, Math.PI * 2);
    ctx.stroke();
  }

  /** Entspricht drawArc der App: Winkel in Grad, 0 Grad rechts, im Uhrzeigersinn. */
  function bogen(farbe, start, weite, zurMitte, x, y, b, h, randBreite) {
    const cx = px(x + b / 2), cy = py(y + h / 2);
    const rx = (b * skala) / 2, ry = (h * skala) / 2;
    const s = start * Math.PI / 180, e = (start + weite) * Math.PI / 180;
    ctx.beginPath();
    if (zurMitte) ctx.moveTo(cx, cy);
    ctx.ellipse(cx, cy, rx, ry, 0, s, e);
    if (zurMitte) ctx.closePath();
    if (randBreite) {
      ctx.strokeStyle = farbe; ctx.lineWidth = dicke(randBreite); ctx.stroke();
    } else {
      ctx.fillStyle = farbe; ctx.fill();
    }
  }

  function flaeche(punkte, farbe) {
    ctx.fillStyle = farbe;
    ctx.beginPath();
    punkte.forEach(([x, y], i) => i === 0 ? ctx.moveTo(px(x), py(y)) : ctx.lineTo(px(x), py(y)));
    ctx.closePath();
    ctx.fill();
  }

  function umriss(punkte, farbe, w = 2) {
    ctx.strokeStyle = farbe;
    ctx.lineWidth = dicke(w);
    ctx.beginPath();
    punkte.forEach(([x, y], i) => i === 0 ? ctx.moveTo(px(x), py(y)) : ctx.lineTo(px(x), py(y)));
    ctx.closePath();
    ctx.stroke();
  }

  function text(inhalt, x, y, groesse, farbe, fett = false) {
    ctx.fillStyle = farbe;
    ctx.textAlign = "center";
    ctx.textBaseline = "alphabetic";
    ctx.font = (fett ? "bold " : "") + (groesse * skala) + "px 'Segoe UI', Arial, sans-serif";
    ctx.fillText(inhalt, px(x), py(y));
  }

  const ein = symbolIstEin(wert);
  const hatWert = wert !== undefined && wert !== null && String(wert).trim() !== "";
  const zustand = !hatWert ? F.GRAU : (ein ? F.GRUEN : F.GRAU);

  switch (typ) {

    // ---------------- Antriebe ----------------

    case "Motor": {
      linie(76, 50, 94, 50, F.METALL, 6);                      // Welle
      kasten(38, 20, 22, 10, F.GEHAEUSE, 1);                   // Klemmenkasten
      kasten(16, 30, 60, 40, zustand, 3);                      // Gehäuse
      kastenRand(16, 30, 60, 40, F.RAND, 2, 3);
      for (let i = 0; i <= 4; i++)
        linie(22 + i * 11, 32, 22 + i * 11, 68, mitAlpha(F.RAND, .25), 1.5);
      kasten(12, 36, 6, 28, F.METALL, 1);                      // Lagerschild
      kasten(74, 42, 6, 16, F.METALL, 1);
      text("M", 46, 56, 20, F.RAND, true);
      kasten(20, 70, 52, 6, F.METALL, 1);                      // Fuß
      break;
    }

    case "Pumpe": {
      kasten(10, 44, 20, 12, F.METALL);                        // Saugstutzen
      kasten(44, 10, 12, 18, F.METALL);                        // Druckstutzen
      kreis(50, 50, 24, zustand);                              // Spiralgehäuse
      kreisRand(50, 50, 24, F.RAND, 2.5);
      flaeche([[42, 38], [42, 62], [66, 50]], F.WEISS);        // Laufrad
      kreis(50, 50, 5, F.METALL_HELL);
      kreisRand(50, 50, 5, F.RAND, 1.5);
      kasten(28, 74, 44, 8, F.METALL, 2);                      // Grundplatte
      break;
    }

    case "Gebläse": {
      kreis(50, 48, 28, mitAlpha(F.GEHAEUSE, .15));
      kreisRand(50, 48, 28, F.RAND, 2.5);
      for (let i = 0; i <= 2; i++) {                           // drei Flügel
        const w = i * 120 * Math.PI / 180;
        const sx = 50 + 8 * Math.cos(w), sy = 48 + 8 * Math.sin(w);
        const ex = 50 + 26 * Math.cos(w + 0.5), ey = 48 + 26 * Math.sin(w + 0.5);
        const mx = 50 + 20 * Math.cos(w), my = 48 + 20 * Math.sin(w);
        flaeche([[sx, sy], [mx, my], [ex, ey]], zustand);
      }
      kreis(50, 48, 7, F.METALL);
      kreisRand(50, 48, 7, F.RAND, 1.5);
      kasten(30, 80, 40, 8, F.METALL, 2);
      break;
    }

    case "Rührwerk": {
      kasten(36, 8, 28, 16, zustand, 2);                       // Antrieb
      kastenRand(36, 8, 28, 16, F.RAND, 2, 2);
      text("M", 50, 21, 11, F.RAND, true);
      linie(50, 24, 50, 78, F.METALL, 4);                      // Welle
      flaeche([[26, 60], [50, 54], [50, 62]], F.METALL_HELL);  // Blätter
      flaeche([[74, 60], [50, 54], [50, 62]], F.METALL_HELL);
      flaeche([[32, 78], [50, 72], [50, 80]], F.METALL_HELL);
      flaeche([[68, 78], [50, 72], [50, 80]], F.METALL_HELL);
      linie(20, 88, 80, 88, mitAlpha(F.RAND, .35), 2);
      break;
    }

    case "Förderschnecke": {
      kasten(18, 36, 68, 28, F.METALL_HELL, 3);                // Trog
      kastenRand(18, 36, 68, 28, F.RAND, 2, 3);
      ctx.strokeStyle = (hatWert && ein) ? F.GRUEN : F.METALL;
      ctx.lineWidth = dicke(2.5);
      for (let i = 0; i <= 4; i++) {                           // Wendel
        const x = 24 + i * 13;
        ctx.beginPath();
        ctx.moveTo(px(x), py(60));
        ctx.quadraticCurveTo(px(x + 6.5), py(34), px(x + 13), py(60));
        ctx.stroke();
      }
      linie(18, 50, 86, 50, F.METALL, 3);                      // Welle
      kasten(6, 40, 12, 20, zustand, 2);                       // Antrieb
      kastenRand(6, 40, 12, 20, F.RAND, 1.5, 2);
      kasten(30, 24, 20, 12, F.METALL, 1);                     // Einlauf
      break;
    }

    case "BHKW": {
      kasten(10, 26, 80, 48, F.METALL_HELL, 3);                // Modul
      kastenRand(10, 26, 80, 48, F.RAND, 2.5, 3);
      kasten(16, 34, 34, 32, F.GEHAEUSE, 2);                   // Motorblock
      for (let i = 0; i <= 3; i++) kasten(19 + i * 8, 28, 5, 6, F.METALL);
      kreis(68, 50, 15, zustand);                              // Generator
      kreisRand(68, 50, 15, F.RAND, 2.5);
      text("G", 68, 56, 16, F.RAND, true);
      linie(50, 50, 53, 50, F.METALL, 4);
      kasten(20, 74, 60, 6, F.METALL, 1);
      break;
    }

    // ---------------- Armaturen ----------------

    case "Ventil": {
      const farbe = !hatWert ? F.GRAU : (ein ? F.GRUEN : F.ROT);
      linie(4, 54, 18, 54, F.METALL, 7);                       // Leitung
      linie(82, 54, 96, 54, F.METALL, 7);
      flaeche([[18, 36], [18, 72], [50, 54]], farbe);          // Kegel
      flaeche([[82, 36], [82, 72], [50, 54]], farbe);
      umriss([[18, 36], [18, 72], [50, 54]], F.RAND, 2);
      umriss([[82, 36], [82, 72], [50, 54]], F.RAND, 2);
      linie(50, 54, 50, 24, F.METALL, 3);                      // Spindel
      kasten(36, 16, 28, 8, F.METALL, 2);                      // Handrad
      kastenRand(36, 16, 28, 8, F.RAND, 1.5, 2);
      break;
    }

    case "Regelventil": {
      const auf = symbolAnteil(wert, el);
      linie(4, 62, 18, 62, F.METALL, 7);
      linie(82, 62, 96, 62, F.METALL, 7);
      flaeche([[18, 48], [18, 76], [50, 62]], F.FLUESSIG);
      flaeche([[82, 48], [82, 76], [50, 62]], F.FLUESSIG);
      umriss([[18, 48], [18, 76], [50, 62]], F.RAND, 2);
      umriss([[82, 48], [82, 76], [50, 62]], F.RAND, 2);
      linie(50, 62, 50, 30, F.METALL, 3);
      oval(30, 12, 40, 20, F.GEHAEUSE);                        // Membranantrieb
      ovalRand(30, 12, 40, 20, F.RAND, 2);
      kasten(22, 84, 56, 8, F.GRAU, 2);                        // Stellungsanzeige
      kasten(22, 84, 56 * auf, 8, F.GRUEN, 2);
      kastenRand(22, 84, 56, 8, F.RAND, 1.5, 2);
      text(symbolMesstext(wert, el), 50, 44, 12, F.RAND, true);
      break;
    }

    case "Rohr waagerecht": {
      kasten(0, 42, 100, 16, F.METALL);
      kasten(0, 42, 100, 5, F.METALL_HELL);                    // Lichtkante
      linie(0, 42, 100, 42, F.RAND, 1.5);
      linie(0, 58, 100, 58, F.RAND, 1.5);
      kasten(14, 38, 6, 24, F.GEHAEUSE, 1);                    // Flansche
      kasten(80, 38, 6, 24, F.GEHAEUSE, 1);
      break;
    }

    case "Rohr senkrecht": {
      kasten(42, 0, 16, 100, F.METALL);
      kasten(42, 0, 5, 100, F.METALL_HELL);
      linie(42, 0, 42, 100, F.RAND, 1.5);
      linie(58, 0, 58, 100, F.RAND, 1.5);
      kasten(38, 14, 24, 6, F.GEHAEUSE, 1);
      kasten(38, 80, 24, 6, F.GEHAEUSE, 1);
      break;
    }

    case "Filter": {
      linie(4, 50, 22, 50, F.METALL, 7);
      linie(78, 50, 96, 50, F.METALL, 7);
      kasten(22, 22, 56, 56, F.METALL_HELL, 3);
      kastenRand(22, 22, 56, 56, F.RAND, 2.5, 3);
      for (let i = 0; i <= 5; i++)
        linie(26 + i * 9, 26, 26 + i * 9 + 10, 74, mitAlpha(F.GEHAEUSE, .55), 2);
      for (let i = 0; i <= 5; i++)
        linie(26 + i * 9 + 10, 26, 26 + i * 9, 74, mitAlpha(F.GEHAEUSE, .55), 2);
      kastenRand(22, 22, 56, 56, F.RAND, 2.5, 3);
      break;
    }

    case "Wärmetauscher": {
      kasten(14, 26, 72, 48, F.METALL_HELL, 3);
      kastenRand(14, 26, 72, 48, F.RAND, 2.5, 3);
      ctx.strokeStyle = mitAlpha(F.ROT, .8);                   // Mäander
      ctx.lineWidth = dicke(3);
      ctx.beginPath();
      ctx.moveTo(px(20), py(38));
      let x = 20, oben = true;
      while (x < 80) {
        ctx.lineTo(px(x + 10), py(oben ? 62 : 38));
        oben = !oben;
        x += 10;
      }
      ctx.stroke();
      linie(4, 34, 14, 34, F.METALL, 6);
      linie(86, 34, 96, 34, F.METALL, 6);
      linie(4, 66, 14, 66, F.METALL, 6);
      linie(86, 66, 96, 66, F.METALL, 6);
      break;
    }

    // ---------------- Behälter ----------------

    case "Tank": {
      const fuellung = symbolAnteil(wert, el);
      const innen = 54 * fuellung;
      kasten(24, 24, 52, 56, F.WEISS, 2);                      // Mantel
      if (fuellung > 0) kasten(25, 24 + (54 - innen) + 1, 50, innen, F.FLUESSIG);
      oval(24, 72, 52, 16, fuellung > 0 ? F.FLUESSIG : F.WEISS);   // Boden
      oval(24, 16, 52, 16, F.METALL_HELL);                     // Deckel
      ovalRand(24, 16, 52, 16, F.RAND, 2.5);
      kastenRand(24, 24, 52, 56, F.RAND, 2.5, 2);
      ovalRand(24, 72, 52, 16, F.RAND, 2.5);
      for (let i = 1; i <= 3; i++) linie(76, 24 + i * 13.5, 82, 24 + i * 13.5, F.RAND, 1.5);
      text(symbolMesstext(wert, el), 50, 56, 13, F.RAND, true);
      break;
    }

    case "Gasspeicher": {
      const fuellung = symbolAnteil(wert, el);
      kasten(14, 72, 72, 10, F.METALL, 2);                     // Sockel
      bogen(F.WEISS, 180, 180, true, 14, 30, 72, 84);          // Hülle
      if (fuellung > 0) {                                      // Füllstand steigt von unten
        ctx.save();
        ctx.beginPath();
        ctx.moveTo(px(50), py(72));
        ctx.ellipse(px(50), py(72), (72 * skala) / 2, (84 * skala) / 2, 0,
                    Math.PI, Math.PI * 2);
        ctx.closePath();
        ctx.clip();
        const h = 42 * fuellung;
        ctx.fillStyle = F.GAS;
        ctx.fillRect(px(14), py(72 - h), 72 * skala, h * skala);
        ctx.restore();
      }
      bogen(F.RAND, 180, 180, false, 14, 30, 72, 84, 2.5);
      linie(14, 72, 86, 72, F.RAND, 2.5);
      text(symbolMesstext(wert, el), 50, 66, 13, F.RAND, true);
      break;
    }

    case "Fermenter": {
      const fuellung = symbolAnteil(wert, el);
      const innen = 40 * fuellung;
      bogen(F.METALL_HELL, 180, 180, true, 16, 18, 68, 40);    // Kuppel
      kasten(16, 38, 68, 42, F.WEISS);
      if (fuellung > 0) kasten(17, 38 + (40 - innen), 66, innen, mitAlpha(F.FLUESSIG, .85));
      bogen(F.RAND, 180, 180, false, 16, 18, 68, 40, 2.5);
      kastenRand(16, 38, 68, 42, F.RAND, 2.5);
      linie(50, 22, 50, 68, F.METALL, 3);                      // Rührwerk
      flaeche([[36, 66], [50, 61], [50, 69]], F.METALL);
      flaeche([[64, 66], [50, 61], [50, 69]], F.METALL);
      kasten(42, 12, 16, 8, F.GEHAEUSE, 2);
      text(symbolMesstext(wert, el), 50, 90, 13, F.RAND, true);
      break;
    }

    // ---------------- Messen & Regeln ----------------

    case "Temperaturanzeige":
    case "Temperaturregelung": {
      const istRegler = typ === "Temperaturregelung";
      const fuellung = symbolAnteil(wert, el);
      const unten = 72, obenG = 20;
      const saeule = (unten - obenG) * fuellung;
      const minW = el.minWert ?? 0, maxW = el.maxWert ?? 100;
      const soll = el.sollwert ?? 40, hyst = el.hysterese ?? 2;

      if (istRegler && maxW !== minW) {                        // Hysteresefeld
        const spanne = maxW - minW;
        const sollAnteil = Math.max(0, Math.min(1, (soll - minW) / spanne));
        const halbe = Math.max(0, Math.min(.5, hyst / 2 / spanne));
        const obenY = unten - (unten - obenG) * (sollAnteil + halbe);
        const untenY = unten - (unten - obenG) * (sollAnteil - halbe);
        kasten(30, obenY, 18, Math.max(2, untenY - obenY), mitAlpha(F.GRUEN, .22));
        const sollY = unten - (unten - obenG) * sollAnteil;
        linie(28, sollY, 50, sollY, F.GRUEN, 2);
      }

      kasten(34, obenG, 10, unten - obenG, F.WEISS, 5);        // Rohr
      if (fuellung > 0) kasten(35.5, unten - saeule, 7, saeule, F.ROT, 4);
      kastenRand(34, obenG, 10, unten - obenG, F.RAND, 2, 5);
      kreis(39, 76, 11, F.ROT);                                // Kolben
      kreisRand(39, 76, 11, F.RAND, 2);
      for (let i = 0; i <= 4; i++) linie(45, obenG + i * 13, 51, obenG + i * 13, F.RAND, 1.5);

      text(symbolMesstext(wert, el), 72, 44, 14, F.RAND, true);

      if (istRegler) {
        const zahl = symbolAlsZahl(wert);
        const halbeHyst = hyst / 2;
        let lageText, lageFarbe;
        if (zahl === null) { lageText = "kein Wert"; lageFarbe = F.GRAU; }
        else if (zahl < soll - halbeHyst) { lageText = "Heizen EIN"; lageFarbe = F.GRUEN; }
        else if (zahl > soll + halbeHyst) { lageText = "Heizen AUS"; lageFarbe = F.GRAU; }
        else { lageText = "im Band"; lageFarbe = F.GELB; }

        text("Soll " + soll.toFixed(1).replace(".", ","), 72, 60, 10, F.RAND);
        text("± " + halbeHyst.toFixed(1).replace(".", ","), 72, 72, 10, F.RAND);
        kasten(50, 78, 48, 14, mitAlpha(lageFarbe, .25), 3);
        kastenRand(50, 78, 48, 14, lageFarbe, 1.5, 3);
        text(lageText, 74, 88, 8.5, F.RAND, true);
      }
      break;
    }

    case "Manometer": {
      const zeiger = symbolAnteil(wert, el);
      kreis(50, 48, 34, F.WEISS);
      kreisRand(50, 48, 34, F.METALL, 6);
      kreisRand(50, 48, 34, F.RAND, 2);
      for (let i = 0; i <= 10; i++) {                          // Skala 225° bis -45°
        const grad = 225 - i * 27;
        const bog = grad * Math.PI / 180;
        const lang = i % 5 === 0;
        const r1 = lang ? 22 : 25;
        linie(50 + r1 * Math.cos(bog), 48 - r1 * Math.sin(bog),
              50 + 29 * Math.cos(bog), 48 - 29 * Math.sin(bog),
              F.RAND, lang ? 2 : 1.2);
      }
      const grad = 225 - 270 * zeiger;
      const bog = grad * Math.PI / 180;
      linie(50, 48, 50 + 26 * Math.cos(bog), 48 - 26 * Math.sin(bog), F.ROT, 3);
      kreis(50, 48, 4, F.RAND);
      kasten(44, 82, 12, 12, F.METALL, 1);                     // Anschluss
      text(symbolMesstext(wert, el), 50, 72, 12, F.RAND, true);
      break;
    }

    case "Durchflussmesser": {
      kasten(0, 38, 100, 24, F.METALL);                        // Leitung
      linie(0, 38, 100, 38, F.RAND, 1.5);
      linie(0, 62, 100, 62, F.RAND, 1.5);
      kasten(24, 28, 52, 44, F.WEISS, 3);                      // Messgerät
      kastenRand(24, 28, 52, 44, F.RAND, 2.5, 3);
      flaeche([[34, 44], [34, 56], [48, 50]], F.FLUESSIG);     // Flussrichtung
      flaeche([[50, 44], [50, 56], [64, 50]], F.FLUESSIG);
      text(symbolMesstext(wert, el), 50, 84, 13, F.RAND, true);
      break;
    }

    case "Balkenanzeige": {
      const fuellung = symbolAnteil(wert, el);
      kasten(18, 20, 64, 34, F.WEISS, 3);
      kasten(19, 21, 62 * fuellung, 32, F.FLUESSIG, 3);
      kastenRand(18, 20, 64, 34, F.RAND, 2.5, 3);
      for (let i = 1; i <= 4; i++) linie(18 + i * 12.8, 54, 18 + i * 12.8, 60, F.RAND, 1.5);
      text(symbolMesstext(wert, el), 50, 80, 15, F.RAND, true);
      break;
    }

    // ---------------- Melden ----------------

    case "Lampe": {
      if (hatWert && ein) {                                    // Leuchtschein
        kreis(50, 46, 36, mitAlpha(F.GELB, .22));
        for (let i = 0; i <= 7; i++) {
          const bog = i * 45 * Math.PI / 180;
          linie(50 + 30 * Math.cos(bog), 46 - 30 * Math.sin(bog),
                50 + 38 * Math.cos(bog), 46 - 38 * Math.sin(bog), F.GELB, 2.5);
        }
      }
      kreis(50, 46, 26, (hatWert && ein) ? F.GELB : F.GRAU);
      kreisRand(50, 46, 26, F.RAND, 2.5);
      linie(34, 32, 66, 60, mitAlpha(F.RAND, .3), 2);
      linie(66, 32, 34, 60, mitAlpha(F.RAND, .3), 2);
      kasten(40, 72, 20, 12, F.METALL, 2);                     // Sockel
      kastenRand(40, 72, 20, 12, F.RAND, 1.5, 2);
      break;
    }

    case "Störmelder": {
      const aktiv = hatWert && ein;
      const farbe = aktiv ? F.ROT : F.GRAU;
      flaeche([[50, 12], [90, 80], [10, 80]], farbe);
      umriss([[50, 12], [90, 80], [10, 80]], F.RAND, 2.5);
      kasten(46, 36, 8, 24, F.WEISS, 2);
      kreis(50, 68, 4.5, F.WEISS);
      break;
    }

    default: {
      kastenRand(20, 20, 60, 60, F.GRAU, 2, 4);
      text("?", 50, 60, 28, F.GRAU, true);
    }
  }
}
