package de.spsmonitor.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import de.spsmonitor.data.S7Client
import de.spsmonitor.data.UebersichtElement
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Industriesymbole, gezeichnet in einem gedachten 100x100-Feld und auf die
 * tatsächliche Elementgröße skaliert.
 */
object Symbole {

    val GRUPPEN: List<Pair<String, List<String>>> = listOf(
        "Antriebe & Aggregate" to listOf("Motor", "Pumpe", "Gebläse", "Rührwerk", "Förderschnecke", "BHKW"),
        "Armaturen & Leitungen" to listOf("Ventil", "Regelventil", "Rohr waagerecht", "Rohr senkrecht", "Filter", "Wärmetauscher"),
        "Behälter" to listOf("Tank", "Gasspeicher", "Fermenter"),
        "Messen & Regeln" to listOf("Temperaturanzeige", "Temperaturregelung", "Manometer", "Durchflussmesser", "Balkenanzeige"),
        "Melden" to listOf("Lampe", "Störmelder")
    )

    val ALLE: List<String> = GRUPPEN.flatMap { it.second }

    /** Symbole, die einen Messwert darstellen und deshalb Min/Max brauchen. */
    val MIT_MESSWERT = listOf(
        "Tank", "Gasspeicher", "Fermenter", "Regelventil", "Durchflussmesser",
        "Temperaturanzeige", "Temperaturregelung", "Manometer", "Balkenanzeige"
    )

    // Farbwelt
    val RAND = Color(0xFF1F2D3D)
    val METALL = Color(0xFF8D9AAC)
    val METALL_HELL = Color(0xFFC6CFDA)
    val GEHAEUSE = Color(0xFF3E5871)
    val GRUEN = Color(0xFF2EB85C)
    val GRAU = Color(0xFFBFC7D1)
    val ROT = Color(0xFFD9372B)
    val GELB = Color(0xFFF0B429)
    val FLUESSIG = Color(0xFF3E8ACC)
    val GAS = Color(0xFF9AD0F5)
    val WEISS = Color(0xFFF7F9FC)
}

/** Zustand "ein" aus einem Tag-Wert ableiten. */
private fun istEin(wert: String?): Boolean {
    val w = wert?.trim() ?: return false
    return w.equals("true", true) || w == "1"
}

/** Anteil 0..1 eines Messwerts im eingestellten Bereich. */
private fun anteil(wert: String?, el: UebersichtElement): Float {
    val zahl = S7Client.alsZahl(wert) ?: return 0f
    val spanne = (el.maxWert - el.minWert)
    if (spanne == 0f) return 0f
    return (((zahl - el.minWert) / spanne).toFloat()).coerceIn(0f, 1f)
}

private fun messtext(wert: String?, el: UebersichtElement): String {
    val zahl = S7Client.alsZahl(wert) ?: return "–"
    val text = String.format(Locale.GERMANY, "%.${el.nachkommastellen.coerceIn(0, 4)}f", zahl)
    return if (el.einheit.isBlank()) text else "$text ${el.einheit}"
}

/**
 * Zeichnet ein Symbol mittig und formatfüllend in die aktuelle Fläche.
 */
fun DrawScope.zeichneSymbol(el: UebersichtElement, wert: String?) {
    val skala = minOf(size.width, size.height) / 100f
    val ox = (size.width - 100f * skala) / 2f
    val oy = (size.height - 100f * skala) / 2f

    fun p(x: Float, y: Float) = Offset(ox + x * skala, oy + y * skala)
    fun s(x: Float, y: Float) = Size(x * skala, y * skala)
    fun dicke(w: Float) = w * skala

    fun kasten(x: Float, y: Float, b: Float, h: Float, farbe: Color, ecke: Float = 0f) {
        if (ecke > 0f) drawRoundRect(farbe, p(x, y), s(b, h), CornerRadius(ecke * skala))
        else drawRect(farbe, p(x, y), s(b, h))
    }

    fun kastenRand(x: Float, y: Float, b: Float, h: Float, farbe: Color, w: Float = 2f, ecke: Float = 0f) {
        if (ecke > 0f) drawRoundRect(farbe, p(x, y), s(b, h), CornerRadius(ecke * skala), style = Stroke(dicke(w)))
        else drawRect(farbe, p(x, y), s(b, h), style = Stroke(dicke(w)))
    }

    fun linie(x1: Float, y1: Float, x2: Float, y2: Float, farbe: Color, w: Float = 2f) {
        drawLine(farbe, p(x1, y1), p(x2, y2), dicke(w))
    }

    fun kreis(cx: Float, cy: Float, r: Float, farbe: Color) =
        drawCircle(farbe, r * skala, p(cx, cy))

    fun kreisRand(cx: Float, cy: Float, r: Float, farbe: Color, w: Float = 2f) =
        drawCircle(farbe, r * skala, p(cx, cy), style = Stroke(dicke(w)))

    fun oval(x: Float, y: Float, b: Float, h: Float, farbe: Color) =
        drawOval(farbe, p(x, y), s(b, h))

    fun ovalRand(x: Float, y: Float, b: Float, h: Float, farbe: Color, w: Float = 2f) =
        drawOval(farbe, p(x, y), s(b, h), style = Stroke(dicke(w)))

    fun flaeche(punkte: List<Pair<Float, Float>>, farbe: Color) {
        val pfad = Path()
        punkte.forEachIndexed { i, (x, y) ->
            if (i == 0) pfad.moveTo(p(x, y).x, p(x, y).y) else pfad.lineTo(p(x, y).x, p(x, y).y)
        }
        pfad.close()
        drawPath(pfad, farbe)
    }

    fun umriss(punkte: List<Pair<Float, Float>>, farbe: Color, w: Float = 2f) {
        val pfad = Path()
        punkte.forEachIndexed { i, (x, y) ->
            if (i == 0) pfad.moveTo(p(x, y).x, p(x, y).y) else pfad.lineTo(p(x, y).x, p(x, y).y)
        }
        pfad.close()
        drawPath(pfad, farbe, style = Stroke(dicke(w)))
    }

    fun text(inhalt: String, x: Float, y: Float, groesse: Float, farbe: Color, fett: Boolean = false) {
        val stift = android.graphics.Paint().apply {
            isAntiAlias = true
            color = farbe.toArgb()
            textSize = groesse * skala
            textAlign = android.graphics.Paint.Align.CENTER
            if (fett) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(inhalt, p(x, y).x, p(x, y).y, stift)
    }

    val ein = istEin(wert)
    val hatWert = !wert.isNullOrBlank()
    val zustandsFarbe = if (!hatWert) Symbole.GRAU else if (ein) Symbole.GRUEN else Symbole.GRAU

    when (el.symbolTyp) {

        // ---------------- Antriebe ----------------

        "Motor" -> {
            linie(76f, 50f, 94f, 50f, Symbole.METALL, 6f)                 // Welle
            kasten(38f, 20f, 22f, 10f, Symbole.GEHAEUSE, 1f)              // Klemmenkasten
            kasten(16f, 30f, 60f, 40f, zustandsFarbe, 3f)                 // Gehäuse
            kastenRand(16f, 30f, 60f, 40f, Symbole.RAND, 2f, 3f)
            for (i in 0..4) linie(22f + i * 11f, 32f, 22f + i * 11f, 68f, Symbole.RAND.copy(alpha = .25f), 1.5f)
            kasten(12f, 36f, 6f, 28f, Symbole.METALL, 1f)                 // Lagerschild
            kasten(74f, 42f, 6f, 16f, Symbole.METALL, 1f)
            text("M", 46f, 56f, 20f, Symbole.RAND, true)
            kasten(20f, 70f, 52f, 6f, Symbole.METALL, 1f)                 // Fuß
        }

        "Pumpe" -> {
            kasten(10f, 44f, 20f, 12f, Symbole.METALL)                    // Saugstutzen
            kasten(44f, 10f, 12f, 18f, Symbole.METALL)                    // Druckstutzen
            kreis(50f, 50f, 24f, zustandsFarbe)                           // Spiralgehäuse
            kreisRand(50f, 50f, 24f, Symbole.RAND, 2.5f)
            flaeche(listOf(42f to 38f, 42f to 62f, 66f to 50f), Symbole.WEISS)   // Laufrad
            kreis(50f, 50f, 5f, Symbole.METALL_HELL)
            kreisRand(50f, 50f, 5f, Symbole.RAND, 1.5f)
            kasten(28f, 74f, 44f, 8f, Symbole.METALL, 2f)                 // Grundplatte
        }

        "Gebläse" -> {
            kreis(50f, 48f, 28f, Symbole.GEHAEUSE.copy(alpha = .15f))
            kreisRand(50f, 48f, 28f, Symbole.RAND, 2.5f)
            for (i in 0..2) {                                             // drei Flügel
                val winkel = Math.toRadians((i * 120).toDouble())
                val sx = 50f + 8f * cos(winkel).toFloat()
                val sy = 48f + 8f * sin(winkel).toFloat()
                val ex = 50f + 26f * cos(winkel + 0.5).toFloat()
                val ey = 48f + 26f * sin(winkel + 0.5).toFloat()
                val mx = 50f + 20f * cos(winkel).toFloat()
                val my = 48f + 20f * sin(winkel).toFloat()
                flaeche(listOf(sx to sy, mx to my, ex to ey), zustandsFarbe)
            }
            kreis(50f, 48f, 7f, Symbole.METALL)
            kreisRand(50f, 48f, 7f, Symbole.RAND, 1.5f)
            kasten(30f, 80f, 40f, 8f, Symbole.METALL, 2f)
        }

        "Rührwerk" -> {
            kasten(36f, 8f, 28f, 16f, zustandsFarbe, 2f)                  // Antrieb
            kastenRand(36f, 8f, 28f, 16f, Symbole.RAND, 2f, 2f)
            text("M", 50f, 21f, 11f, Symbole.RAND, true)
            linie(50f, 24f, 50f, 78f, Symbole.METALL, 4f)                 // Welle
            flaeche(listOf(26f to 60f, 50f to 54f, 50f to 62f), Symbole.METALL_HELL)   // Blätter
            flaeche(listOf(74f to 60f, 50f to 54f, 50f to 62f), Symbole.METALL_HELL)
            flaeche(listOf(32f to 78f, 50f to 72f, 50f to 80f), Symbole.METALL_HELL)
            flaeche(listOf(68f to 78f, 50f to 72f, 50f to 80f), Symbole.METALL_HELL)
            linie(20f, 88f, 80f, 88f, Symbole.RAND.copy(alpha = .35f), 2f)
        }

        "Förderschnecke" -> {
            kasten(18f, 36f, 68f, 28f, Symbole.METALL_HELL, 3f)           // Trog
            kastenRand(18f, 36f, 68f, 28f, Symbole.RAND, 2f, 3f)
            for (i in 0..4) {                                             // Wendel
                val x = 24f + i * 13f
                val pfad = Path()
                pfad.moveTo(p(x, 60f).x, p(x, 60f).y)
                pfad.quadraticBezierTo(p(x + 6.5f, 34f).x, p(x + 6.5f, 34f).y, p(x + 13f, 60f).x, p(x + 13f, 60f).y)
                drawPath(pfad, if (hatWert && ein) Symbole.GRUEN else Symbole.METALL, style = Stroke(dicke(2.5f)))
            }
            linie(18f, 50f, 86f, 50f, Symbole.METALL, 3f)                 // Welle
            kasten(6f, 40f, 12f, 20f, zustandsFarbe, 2f)                  // Antrieb
            kastenRand(6f, 40f, 12f, 20f, Symbole.RAND, 1.5f, 2f)
            kasten(30f, 24f, 20f, 12f, Symbole.METALL, 1f)                // Einlauf
        }

        "BHKW" -> {
            kasten(10f, 26f, 80f, 48f, Symbole.METALL_HELL, 3f)           // Modul
            kastenRand(10f, 26f, 80f, 48f, Symbole.RAND, 2.5f, 3f)
            kasten(16f, 34f, 34f, 32f, Symbole.GEHAEUSE, 2f)              // Motorblock
            for (i in 0..3) kasten(19f + i * 8f, 28f, 5f, 6f, Symbole.METALL)   // Zylinder
            kreis(68f, 50f, 15f, zustandsFarbe)                           // Generator
            kreisRand(68f, 50f, 15f, Symbole.RAND, 2.5f)
            text("G", 68f, 56f, 16f, Symbole.RAND, true)
            linie(50f, 50f, 53f, 50f, Symbole.METALL, 4f)
            kasten(20f, 74f, 60f, 6f, Symbole.METALL, 1f)
        }

        // ---------------- Armaturen ----------------

        "Ventil" -> {
            val farbe = if (!hatWert) Symbole.GRAU else if (ein) Symbole.GRUEN else Symbole.ROT
            linie(4f, 54f, 18f, 54f, Symbole.METALL, 7f)                  // Leitung
            linie(82f, 54f, 96f, 54f, Symbole.METALL, 7f)
            flaeche(listOf(18f to 36f, 18f to 72f, 50f to 54f), farbe)    // Kegel links
            flaeche(listOf(82f to 36f, 82f to 72f, 50f to 54f), farbe)    // Kegel rechts
            umriss(listOf(18f to 36f, 18f to 72f, 50f to 54f), Symbole.RAND, 2f)
            umriss(listOf(82f to 36f, 82f to 72f, 50f to 54f), Symbole.RAND, 2f)
            linie(50f, 54f, 50f, 24f, Symbole.METALL, 3f)                 // Spindel
            kasten(36f, 16f, 28f, 8f, Symbole.METALL, 2f)                 // Handrad
            kastenRand(36f, 16f, 28f, 8f, Symbole.RAND, 1.5f, 2f)
        }

        "Regelventil" -> {
            val auf = anteil(wert, el)
            linie(4f, 62f, 18f, 62f, Symbole.METALL, 7f)
            linie(82f, 62f, 96f, 62f, Symbole.METALL, 7f)
            flaeche(listOf(18f to 48f, 18f to 76f, 50f to 62f), Symbole.FLUESSIG)
            flaeche(listOf(82f to 48f, 82f to 76f, 50f to 62f), Symbole.FLUESSIG)
            umriss(listOf(18f to 48f, 18f to 76f, 50f to 62f), Symbole.RAND, 2f)
            umriss(listOf(82f to 48f, 82f to 76f, 50f to 62f), Symbole.RAND, 2f)
            linie(50f, 62f, 50f, 30f, Symbole.METALL, 3f)
            oval(30f, 12f, 40f, 20f, Symbole.GEHAEUSE)                    // Membranantrieb
            ovalRand(30f, 12f, 40f, 20f, Symbole.RAND, 2f)
            kasten(22f, 84f, 56f, 8f, Symbole.GRAU, 2f)                   // Stellungsanzeige
            kasten(22f, 84f, 56f * auf, 8f, Symbole.GRUEN, 2f)
            kastenRand(22f, 84f, 56f, 8f, Symbole.RAND, 1.5f, 2f)
            text(messtext(wert, el), 50f, 44f, 12f, Symbole.RAND, true)
        }

        "Rohr waagerecht" -> {
            kasten(0f, 42f, 100f, 16f, Symbole.METALL)
            kasten(0f, 42f, 100f, 5f, Symbole.METALL_HELL)                // Lichtkante
            linie(0f, 42f, 100f, 42f, Symbole.RAND, 1.5f)
            linie(0f, 58f, 100f, 58f, Symbole.RAND, 1.5f)
            kasten(14f, 38f, 6f, 24f, Symbole.GEHAEUSE, 1f)               // Flansche
            kasten(80f, 38f, 6f, 24f, Symbole.GEHAEUSE, 1f)
        }

        "Rohr senkrecht" -> {
            kasten(42f, 0f, 16f, 100f, Symbole.METALL)
            kasten(42f, 0f, 5f, 100f, Symbole.METALL_HELL)
            linie(42f, 0f, 42f, 100f, Symbole.RAND, 1.5f)
            linie(58f, 0f, 58f, 100f, Symbole.RAND, 1.5f)
            kasten(38f, 14f, 24f, 6f, Symbole.GEHAEUSE, 1f)
            kasten(38f, 80f, 24f, 6f, Symbole.GEHAEUSE, 1f)
        }

        "Filter" -> {
            linie(4f, 50f, 22f, 50f, Symbole.METALL, 7f)
            linie(78f, 50f, 96f, 50f, Symbole.METALL, 7f)
            kasten(22f, 22f, 56f, 56f, Symbole.METALL_HELL, 3f)
            kastenRand(22f, 22f, 56f, 56f, Symbole.RAND, 2.5f, 3f)
            for (i in 0..5) linie(26f + i * 9f, 26f, 26f + i * 9f + 10f, 74f, Symbole.GEHAEUSE.copy(alpha = .55f), 2f)
            for (i in 0..5) linie(26f + i * 9f + 10f, 26f, 26f + i * 9f, 74f, Symbole.GEHAEUSE.copy(alpha = .55f), 2f)
            kastenRand(22f, 22f, 56f, 56f, Symbole.RAND, 2.5f, 3f)
        }

        "Wärmetauscher" -> {
            kasten(14f, 26f, 72f, 48f, Symbole.METALL_HELL, 3f)
            kastenRand(14f, 26f, 72f, 48f, Symbole.RAND, 2.5f, 3f)
            val pfad = Path()                                              // Mäander
            pfad.moveTo(p(20f, 38f).x, p(20f, 38f).y)
            var x = 20f
            var oben = true
            while (x < 80f) {
                pfad.lineTo(p(x + 10f, if (oben) 62f else 38f).x, p(x + 10f, if (oben) 62f else 38f).y)
                oben = !oben
                x += 10f
            }
            drawPath(pfad, Symbole.ROT.copy(alpha = .8f), style = Stroke(dicke(3f)))
            linie(4f, 34f, 14f, 34f, Symbole.METALL, 6f)
            linie(86f, 34f, 96f, 34f, Symbole.METALL, 6f)
            linie(4f, 66f, 14f, 66f, Symbole.METALL, 6f)
            linie(86f, 66f, 96f, 66f, Symbole.METALL, 6f)
        }

        // ---------------- Behälter ----------------

        "Tank" -> {
            val fuellung = anteil(wert, el)
            val hoeheInnen = 54f * fuellung
            kasten(24f, 24f, 52f, 56f, Symbole.WEISS, 2f)                 // Mantel
            if (fuellung > 0f) kasten(25f, 24f + (54f - hoeheInnen) + 1f, 50f, hoeheInnen, Symbole.FLUESSIG)
            oval(24f, 72f, 52f, 16f, if (fuellung > 0f) Symbole.FLUESSIG else Symbole.WEISS)   // Boden
            oval(24f, 16f, 52f, 16f, Symbole.METALL_HELL)                 // Deckel
            ovalRand(24f, 16f, 52f, 16f, Symbole.RAND, 2.5f)
            kastenRand(24f, 24f, 52f, 56f, Symbole.RAND, 2.5f, 2f)
            ovalRand(24f, 72f, 52f, 16f, Symbole.RAND, 2.5f)
            for (i in 1..3) linie(76f, 24f + i * 13.5f, 82f, 24f + i * 13.5f, Symbole.RAND, 1.5f)
            text(messtext(wert, el), 50f, 56f, 13f, Symbole.RAND, true)
        }

        "Gasspeicher" -> {
            val fuellung = anteil(wert, el)
            kasten(14f, 72f, 72f, 10f, Symbole.METALL, 2f)                // Sockel
            drawArc(Symbole.WEISS, 180f, 180f, true, p(14f, 30f), s(72f, 84f))   // Hülle
            if (fuellung > 0f) {                                           // Füllstand steigt von unten
                val kuppel = Path().apply {
                    addArc(
                        androidx.compose.ui.geometry.Rect(p(14f, 30f), s(72f, 84f)),
                        180f, 180f
                    )
                    close()
                }
                clipPath(kuppel) {
                    val h = 42f * fuellung
                    drawRect(Symbole.GAS, p(14f, 72f - h), s(72f, h))
                }
            }
            drawArc(Symbole.RAND, 180f, 180f, false, p(14f, 30f), s(72f, 84f), style = Stroke(dicke(2.5f)))
            linie(14f, 72f, 86f, 72f, Symbole.RAND, 2.5f)
            text(messtext(wert, el), 50f, 66f, 13f, Symbole.RAND, true)
        }

        "Fermenter" -> {
            val fuellung = anteil(wert, el)
            val hoeheInnen = 40f * fuellung
            drawArc(Symbole.METALL_HELL, 180f, 180f, true, p(16f, 18f), s(68f, 40f))   // Kuppel
            kasten(16f, 38f, 68f, 42f, Symbole.WEISS)
            if (fuellung > 0f) kasten(17f, 38f + (40f - hoeheInnen), 66f, hoeheInnen, Symbole.FLUESSIG.copy(alpha = .85f))
            drawArc(Symbole.RAND, 180f, 180f, false, p(16f, 18f), s(68f, 40f), style = Stroke(dicke(2.5f)))
            kastenRand(16f, 38f, 68f, 42f, Symbole.RAND, 2.5f)
            linie(50f, 22f, 50f, 68f, Symbole.METALL, 3f)                 // Rührwerk
            flaeche(listOf(36f to 66f, 50f to 61f, 50f to 69f), Symbole.METALL)
            flaeche(listOf(64f to 66f, 50f to 61f, 50f to 69f), Symbole.METALL)
            kasten(42f, 12f, 16f, 8f, Symbole.GEHAEUSE, 2f)
            text(messtext(wert, el), 50f, 90f, 13f, Symbole.RAND, true)
        }

        // ---------------- Messen & Regeln ----------------

        "Temperaturanzeige", "Temperaturregelung" -> {
            val istRegler = el.symbolTyp == "Temperaturregelung"
            val fuellung = anteil(wert, el)
            val saeuleUnten = 72f
            val saeuleOben = 20f
            val saeuleHoehe = (saeuleUnten - saeuleOben) * fuellung

            // Hysteresefeld hinter der Säule
            if (istRegler && el.maxWert != el.minWert) {
                val spanne = el.maxWert - el.minWert
                val sollAnteil = ((el.sollwert - el.minWert) / spanne).coerceIn(0f, 1f)
                val halbe = (el.hysterese / 2f / spanne).coerceIn(0f, 0.5f)
                val obenY = saeuleUnten - (saeuleUnten - saeuleOben) * (sollAnteil + halbe)
                val untenY = saeuleUnten - (saeuleUnten - saeuleOben) * (sollAnteil - halbe)
                kasten(30f, obenY, 18f, (untenY - obenY).coerceAtLeast(2f), Symbole.GRUEN.copy(alpha = .22f))
                val sollY = saeuleUnten - (saeuleUnten - saeuleOben) * sollAnteil
                linie(28f, sollY, 50f, sollY, Symbole.GRUEN, 2f)
            }

            kasten(34f, saeuleOben, 10f, saeuleUnten - saeuleOben, Symbole.WEISS, 5f)   // Rohr
            if (fuellung > 0f) kasten(35.5f, saeuleUnten - saeuleHoehe, 7f, saeuleHoehe, Symbole.ROT, 4f)
            kastenRand(34f, saeuleOben, 10f, saeuleUnten - saeuleOben, Symbole.RAND, 2f, 5f)
            kreis(39f, 76f, 11f, Symbole.ROT)                              // Kolben
            kreisRand(39f, 76f, 11f, Symbole.RAND, 2f)
            for (i in 0..4) linie(45f, saeuleOben + i * 13f, 51f, saeuleOben + i * 13f, Symbole.RAND, 1.5f)

            text(messtext(wert, el), 72f, 44f, 14f, Symbole.RAND, true)

            if (istRegler) {
                val zahl = S7Client.alsZahl(wert)
                val halbeHyst = el.hysterese / 2f
                val lage = when {
                    zahl == null -> "kein Wert" to Symbole.GRAU
                    zahl < el.sollwert - halbeHyst -> "Heizen EIN" to Symbole.GRUEN
                    zahl > el.sollwert + halbeHyst -> "Heizen AUS" to Symbole.GRAU
                    else -> "im Band" to Symbole.GELB
                }
                text("Soll ${String.format(Locale.GERMANY, "%.1f", el.sollwert)}", 72f, 60f, 10f, Symbole.RAND)
                text("± ${String.format(Locale.GERMANY, "%.1f", halbeHyst)}", 72f, 72f, 10f, Symbole.RAND)
                kasten(50f, 78f, 48f, 14f, lage.second.copy(alpha = .25f), 3f)
                kastenRand(50f, 78f, 48f, 14f, lage.second, 1.5f, 3f)
                text(lage.first, 74f, 88f, 8.5f, Symbole.RAND, true)
            }
        }

        "Manometer" -> {
            val zeiger = anteil(wert, el)
            kreis(50f, 48f, 34f, Symbole.WEISS)
            kreisRand(50f, 48f, 34f, Symbole.METALL, 6f)
            kreisRand(50f, 48f, 34f, Symbole.RAND, 2f)
            for (i in 0..10) {                                             // Skala von 225° bis -45°
                val grad = 225.0 - i * 27.0
                val bog = Math.toRadians(grad)
                val lang = i % 5 == 0
                val r1 = if (lang) 22f else 25f
                linie(
                    50f + r1 * cos(bog).toFloat(), 48f - r1 * sin(bog).toFloat(),
                    50f + 29f * cos(bog).toFloat(), 48f - 29f * sin(bog).toFloat(),
                    Symbole.RAND, if (lang) 2f else 1.2f
                )
            }
            val grad = 225.0 - 270.0 * zeiger
            val bog = Math.toRadians(grad)
            linie(50f, 48f, 50f + 26f * cos(bog).toFloat(), 48f - 26f * sin(bog).toFloat(), Symbole.ROT, 3f)
            kreis(50f, 48f, 4f, Symbole.RAND)
            kasten(44f, 82f, 12f, 12f, Symbole.METALL, 1f)                 // Anschluss
            text(messtext(wert, el), 50f, 72f, 12f, Symbole.RAND, true)
        }

        "Durchflussmesser" -> {
            kasten(0f, 38f, 100f, 24f, Symbole.METALL)                     // Leitung
            linie(0f, 38f, 100f, 38f, Symbole.RAND, 1.5f)
            linie(0f, 62f, 100f, 62f, Symbole.RAND, 1.5f)
            kasten(24f, 28f, 52f, 44f, Symbole.WEISS, 3f)                  // Messgerät
            kastenRand(24f, 28f, 52f, 44f, Symbole.RAND, 2.5f, 3f)
            flaeche(listOf(34f to 44f, 34f to 56f, 48f to 50f), Symbole.FLUESSIG)   // Flussrichtung
            flaeche(listOf(50f to 44f, 50f to 56f, 64f to 50f), Symbole.FLUESSIG)
            text(messtext(wert, el), 50f, 84f, 13f, Symbole.RAND, true)
        }

        "Balkenanzeige" -> {
            val fuellung = anteil(wert, el)
            kasten(18f, 20f, 64f, 34f, Symbole.WEISS, 3f)
            kasten(19f, 21f, 62f * fuellung, 32f, Symbole.FLUESSIG, 3f)
            kastenRand(18f, 20f, 64f, 34f, Symbole.RAND, 2.5f, 3f)
            for (i in 1..4) linie(18f + i * 12.8f, 54f, 18f + i * 12.8f, 60f, Symbole.RAND, 1.5f)
            text(messtext(wert, el), 50f, 80f, 15f, Symbole.RAND, true)
        }

        // ---------------- Melden ----------------

        "Lampe" -> {
            if (hatWert && ein) {                                           // Leuchtschein
                kreis(50f, 46f, 36f, Symbole.GELB.copy(alpha = .22f))
                for (i in 0..7) {
                    val bog = Math.toRadians((i * 45).toDouble())
                    linie(
                        50f + 30f * cos(bog).toFloat(), 46f - 30f * sin(bog).toFloat(),
                        50f + 38f * cos(bog).toFloat(), 46f - 38f * sin(bog).toFloat(),
                        Symbole.GELB, 2.5f
                    )
                }
            }
            kreis(50f, 46f, 26f, if (hatWert && ein) Symbole.GELB else Symbole.GRAU)
            kreisRand(50f, 46f, 26f, Symbole.RAND, 2.5f)
            linie(34f, 32f, 66f, 60f, Symbole.RAND.copy(alpha = .3f), 2f)
            linie(66f, 32f, 34f, 60f, Symbole.RAND.copy(alpha = .3f), 2f)
            kasten(40f, 72f, 20f, 12f, Symbole.METALL, 2f)                  // Sockel
            kastenRand(40f, 72f, 20f, 12f, Symbole.RAND, 1.5f, 2f)
        }

        "Störmelder" -> {
            val aktiv = hatWert && ein
            val farbe = if (aktiv) Symbole.ROT else Symbole.GRAU
            flaeche(listOf(50f to 12f, 90f to 80f, 10f to 80f), farbe)
            umriss(listOf(50f to 12f, 90f to 80f, 10f to 80f), Symbole.RAND, 2.5f)
            kasten(46f, 36f, 8f, 24f, Symbole.WEISS, 2f)
            kreis(50f, 68f, 4.5f, Symbole.WEISS)
        }

        else -> {                                                           // unbekannt
            kastenRand(20f, 20f, 60f, 60f, Symbole.GRAU, 2f, 4f)
            text("?", 50f, 60f, 28f, Symbole.GRAU, true)
        }
    }
}
