package de.spsmonitor.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * Speichert das Projekt als projekt.json im App-Ordner.
 *
 * Gleiches Format wie die HTML-Oberfläche und die Brücke – ein Projekt lässt
 * sich dadurch zwischen allen Fassungen austauschen.
 */
class Repo(private val kontext: Context) {

    private val datei: File
        get() = File(kontext.filesDir, "projekt.json")

    /** Datei aus früheren Fassungen; wird einmalig übernommen. */
    private val alteDatei: File
        get() = File(kontext.filesDir, "konfiguration.json")

    fun laden(): Konfiguration {
        return try {
            when {
                datei.exists() -> Konfiguration.fromJson(JSONObject(datei.readText()))
                alteDatei.exists() -> {
                    val alt = Konfiguration.fromJson(JSONObject(alteDatei.readText()))
                    speichern(alt)          // ins neue Format überführen
                    alt
                }
                else -> Konfiguration()
            }
        } catch (fehler: Exception) {
            // Beschädigte Datei: lieber mit Standardwerten starten als abstürzen.
            Konfiguration()
        }
    }

    fun speichern(konfiguration: Konfiguration) {
        try {
            datei.writeText(konfiguration.toJson().toString(2))
        } catch (_: Exception) {
            // Speichern ist nicht kritisch für den laufenden Betrieb.
        }
    }

    // ---------- Austausch über die Dateiauswahl ----------

    /** Schreibt das Projekt in eine vom Nutzer gewählte Datei. Null bei Erfolg. */
    fun inDateiSchreiben(ziel: Uri, konfiguration: Konfiguration): String? {
        return try {
            val strom = kontext.contentResolver.openOutputStream(ziel, "wt")
                ?: return "Die Datei konnte nicht beschrieben werden."
            strom.use { it.write(konfiguration.toJson().toString(2).toByteArray(Charsets.UTF_8)) }
            null
        } catch (fehler: Exception) {
            fehler.message ?: "Speichern fehlgeschlagen"
        }
    }

    /** Liest ein Projekt aus einer gewählten Datei. Null, wenn es nicht klappt. */
    fun ausDateiLesen(quelle: Uri): Konfiguration? = try {
        val text = kontext.contentResolver.openInputStream(quelle)?.use { strom ->
            strom.readBytes().toString(Charsets.UTF_8)
        }
        if (text.isNullOrBlank()) null
        else Konfiguration.fromJson(JSONObject(text))
    } catch (fehler: Exception) {
        null
    }
}
