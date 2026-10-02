package de.spsmonitor.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Speichert die Konfiguration als JSON im App-Ordner.
 * Gleiches Format wie die Windows- und Termux-Fassung, damit sich
 * Tag-Listen und Übersichtsseiten zwischen den Geräten austauschen lassen.
 */
class Repo(private val kontext: Context) {

    private val datei: File
        get() = File(kontext.filesDir, "konfiguration.json")

    fun laden(): Konfiguration {
        return try {
            if (!datei.exists()) Konfiguration()
            else Konfiguration.fromJson(JSONObject(datei.readText()))
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

    /** Für den Austausch mit anderen Geräten. */
    fun alsText(konfiguration: Konfiguration): String = konfiguration.toJson().toString(2)

    fun ausText(text: String): Konfiguration? = try {
        Konfiguration.fromJson(JSONObject(text))
    } catch (fehler: Exception) {
        null
    }
}
