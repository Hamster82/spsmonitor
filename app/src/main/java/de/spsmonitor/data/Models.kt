package de.spsmonitor.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ein Wert im Datenbaustein der Steuerung. */
data class PlcTag(
    val name: String = "Neuer Tag",
    val db: Int = 1,
    val offset: Int = 0,
    val bit: Int = 0,
    val typ: String = "Real"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("db", db).put("offset", offset)
        .put("bit", bit).put("typ", typ)

    companion object {
        fun fromJson(o: JSONObject) = PlcTag(
            name = o.optString("name", ""),
            db = o.optInt("db", 1),
            offset = o.optInt("offset", 0),
            bit = o.optInt("bit", 0),
            typ = o.optString("typ", "Real")
        )
    }
}

/** Art eines Elements auf einer Übersichtsseite. */
object ElementTyp {
    const val SYMBOL = "Symbol"
    const val TEXT = "Text"
    const val VARIABLE = "Variable"
    const val BUTTON = "Button"
    const val SEITENWECHSEL = "Seitenwechsel"

    val ALLE = listOf(SYMBOL, TEXT, VARIABLE, BUTTON, SEITENWECHSEL)
}

/** Ein Element auf der Leinwand: Symbol, Text, Messwertfeld, Schalter oder Seitensprung. */
data class UebersichtElement(
    val id: String = UUID.randomUUID().toString(),
    val typ: String = ElementTyp.SYMBOL,
    val x: Float = 40f,
    val y: Float = 40f,
    val breite: Float = 110f,
    val hoehe: Float = 110f,
    val text: String = "",
    val schriftGroesse: Float = 15f,
    val symbolTyp: String = "Motor",
    val tagName: String = "",
    val einheit: String = "",
    val nachkommastellen: Int = 1,
    val editierbar: Boolean = false,
    val minWert: Float = 0f,
    val maxWert: Float = 100f,
    val sollwert: Float = 40f,
    val hysterese: Float = 2f,
    val schreibWert: String = "True",
    val zielSeite: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("typ", typ)
        .put("x", x.toDouble()).put("y", y.toDouble())
        .put("breite", breite.toDouble()).put("hoehe", hoehe.toDouble())
        .put("text", text).put("schriftGroesse", schriftGroesse.toDouble())
        .put("symbolTyp", symbolTyp).put("tagName", tagName)
        .put("einheit", einheit).put("nachkommastellen", nachkommastellen)
        .put("editierbar", editierbar)
        .put("minWert", minWert.toDouble()).put("maxWert", maxWert.toDouble())
        .put("sollwert", sollwert.toDouble()).put("hysterese", hysterese.toDouble())
        .put("schreibWert", schreibWert).put("zielSeite", zielSeite)

    companion object {
        fun fromJson(o: JSONObject) = UebersichtElement(
            id = o.optString("id", UUID.randomUUID().toString()),
            typ = o.optString("typ", ElementTyp.SYMBOL),
            x = o.optDouble("x", 40.0).toFloat(),
            y = o.optDouble("y", 40.0).toFloat(),
            breite = o.optDouble("breite", 110.0).toFloat(),
            hoehe = o.optDouble("hoehe", 110.0).toFloat(),
            text = o.optString("text", ""),
            schriftGroesse = o.optDouble("schriftGroesse", 15.0).toFloat(),
            symbolTyp = o.optString("symbolTyp", "Motor"),
            tagName = o.optString("tagName", ""),
            einheit = o.optString("einheit", ""),
            nachkommastellen = o.optInt("nachkommastellen", 1),
            editierbar = o.optBoolean("editierbar", false),
            minWert = o.optDouble("minWert", 0.0).toFloat(),
            maxWert = o.optDouble("maxWert", 100.0).toFloat(),
            sollwert = o.optDouble("sollwert", 40.0).toFloat(),
            hysterese = o.optDouble("hysterese", 2.0).toFloat(),
            schreibWert = o.optString("schreibWert", "True"),
            zielSeite = o.optString("zielSeite", "")
        )
    }
}

/** Eine Übersichtsseite mit eigener Leinwand. */
data class UebersichtSeite(
    val name: String = "Übersicht 1",
    val elemente: List<UebersichtElement> = emptyList()
) {
    fun toJson(): JSONObject {
        val liste = JSONArray()
        elemente.forEach { liste.put(it.toJson()) }
        return JSONObject().put("name", name).put("elemente", liste)
    }

    companion object {
        fun fromJson(o: JSONObject): UebersichtSeite {
            val liste = o.optJSONArray("elemente") ?: JSONArray()
            val elemente = (0 until liste.length()).mapNotNull { i ->
                liste.optJSONObject(i)?.let { UebersichtElement.fromJson(it) }
            }
            return UebersichtSeite(o.optString("name", "Übersicht"), elemente)
        }
    }
}

/** Alles, was dauerhaft gespeichert wird. */
data class Konfiguration(
    val ip: String = "192.168.0.1",
    val rack: Int = 0,
    val slot: Int = 2,
    val tags: List<PlcTag> = standardTags(),
    val seiten: List<UebersichtSeite> = listOf(UebersichtSeite())
) {
    fun toJson(): JSONObject {
        val tagListe = JSONArray()
        tags.forEach { tagListe.put(it.toJson()) }
        val seitenListe = JSONArray()
        seiten.forEach { seitenListe.put(it.toJson()) }
        return JSONObject()
            .put("ip", ip).put("rack", rack).put("slot", slot)
            .put("tags", tagListe).put("seiten", seitenListe)
    }

    companion object {
        fun standardTags() = listOf(
            PlcTag("Einspeisung", 1, 0, 0, "Real"),
            PlcTag("Gasverbrauch", 1, 4, 0, "Real"),
            PlcTag("CH4-Gehalt", 1, 8, 0, "Real")
        )

        fun fromJson(o: JSONObject): Konfiguration {
            val tagListe = o.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagListe.length()).mapNotNull { i ->
                tagListe.optJSONObject(i)?.let { PlcTag.fromJson(it) }
            }
            val seitenListe = o.optJSONArray("seiten") ?: JSONArray()
            val seiten = (0 until seitenListe.length()).mapNotNull { i ->
                seitenListe.optJSONObject(i)?.let { UebersichtSeite.fromJson(it) }
            }
            return Konfiguration(
                ip = o.optString("ip", "192.168.0.1"),
                rack = o.optInt("rack", 0),
                slot = o.optInt("slot", 2),
                tags = tags.ifEmpty { standardTags() },
                seiten = seiten.ifEmpty { listOf(UebersichtSeite()) }
            )
        }
    }
}

/** Ein Messzeitpunkt für die Auswertung. */
data class Messpunkt(val zeit: Long, val werte: Map<String, Double>)
