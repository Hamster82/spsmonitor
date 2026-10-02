package de.spsmonitor

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.spsmonitor.data.Konfiguration
import de.spsmonitor.data.Messpunkt
import de.spsmonitor.data.PlcTag
import de.spsmonitor.data.Repo
import de.spsmonitor.data.S7Client
import de.spsmonitor.data.UebersichtElement
import de.spsmonitor.data.UebersichtSeite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repo(app)
    private val client = S7Client()

    // ---------- Verbindung ----------
    var ip by mutableStateOf("192.168.0.1")
    var rack by mutableStateOf("0")
    var slot by mutableStateOf("2")
    var verbunden by mutableStateOf(false)
        private set
    var beschaeftigt by mutableStateOf(false)
        private set
    var meldung by mutableStateOf("")

    // ---------- Daten ----------
    val tags = mutableStateListOf<PlcTag>()
    val werte = mutableStateMapOf<String, String>()
    val seiten = mutableStateListOf<UebersichtSeite>()
    var aktiveSeite by mutableStateOf(0)

    // ---------- Aufzeichnung ----------
    val verlauf = mutableStateListOf<Messpunkt>()
    var aufzeichnung by mutableStateOf(true)
    var autoRefresh by mutableStateOf(false)
        private set

    private var abfrageJob: Job? = null

    companion object {
        private const val MAX_PUNKTE = 20000
        const val TAKT_MS = 2000L
    }

    init {
        val k = repo.laden()
        ip = k.ip
        rack = k.rack.toString()
        slot = k.slot.toString()
        tags.addAll(k.tags)
        seiten.addAll(k.seiten)
    }

    // ---------- Speichern ----------

    fun speichern() {
        repo.speichern(
            Konfiguration(
                ip = ip,
                rack = rack.toIntOrNull() ?: 0,
                slot = slot.toIntOrNull() ?: 2,
                tags = tags.toList(),
                seiten = seiten.toList()
            )
        )
    }

    fun konfigurationAlsText(): String = repo.alsText(
        Konfiguration(ip, rack.toIntOrNull() ?: 0, slot.toIntOrNull() ?: 2, tags.toList(), seiten.toList())
    )

    fun konfigurationAusText(text: String): Boolean {
        val k = repo.ausText(text) ?: return false
        ip = k.ip
        rack = k.rack.toString()
        slot = k.slot.toString()
        tags.clear(); tags.addAll(k.tags)
        seiten.clear(); seiten.addAll(k.seiten)
        aktiveSeite = 0
        speichern()
        return true
    }

    // ---------- Verbindung ----------

    fun verbindenOderTrennen() {
        if (verbunden) {
            autoRefreshSetzen(false)
            viewModelScope.launch {
                withContext(Dispatchers.IO) { client.trennen() }
                verbunden = false
                meldung = ""
            }
            return
        }

        viewModelScope.launch {
            beschaeftigt = true
            val fehler = withContext(Dispatchers.IO) {
                client.verbinden(ip.trim(), rack.toIntOrNull() ?: 0, slot.toIntOrNull() ?: 2)
            }
            beschaeftigt = false
            verbunden = fehler == null
            meldung = fehler ?: ""
            if (verbunden) {
                speichern()
                alleLesen()
            }
        }
    }

    // ---------- Lesen ----------

    fun alleLesen() {
        if (!verbunden) { meldung = "Erst verbinden."; return }
        viewModelScope.launch { leseDurchgang() }
    }

    private suspend fun leseDurchgang() {
        val liste = tags.toList()
        val ergebnis = withContext(Dispatchers.IO) {
            val neueWerte = HashMap<String, String>()
            var fehlertext: String? = null
            for (tag in liste) {
                if (tag.name.isBlank()) continue
                try {
                    val roh = client.dbLesen(tag.db, tag.offset, S7Client.groesseFuerTyp(tag.typ))
                    neueWerte[tag.name] = S7Client.bytesZuWert(roh, tag.typ, tag.bit)
                } catch (fehler: Exception) {
                    if (fehlertext == null) fehlertext = "${tag.name}: ${fehler.message}"
                }
            }
            Pair(neueWerte, fehlertext)
        }

        werte.putAll(ergebnis.first)
        meldung = ergebnis.second ?: ""

        if (aufzeichnung && ergebnis.first.isNotEmpty()) {
            val zahlen = HashMap<String, Double>()
            werte.forEach { (name, wert) -> S7Client.alsZahl(wert)?.let { zahlen[name] = it } }
            if (zahlen.isNotEmpty()) {
                verlauf.add(Messpunkt(System.currentTimeMillis(), zahlen))
                while (verlauf.size > MAX_PUNKTE) verlauf.removeAt(0)
            }
        }
    }

    fun autoRefreshSetzen(ein: Boolean) {
        autoRefresh = ein
        abfrageJob?.cancel()
        abfrageJob = null
        if (!ein) return

        abfrageJob = viewModelScope.launch {
            while (isActive && verbunden) {
                leseDurchgang()
                delay(TAKT_MS)
            }
        }
    }

    // ---------- Schreiben ----------

    /** Schreibt einen Wert; meldet das Ergebnis über [meldung]. */
    fun schreiben(tag: PlcTag, neuerWert: String) {
        if (!verbunden) { meldung = "Erst verbinden."; return }

        viewModelScope.launch {
            beschaeftigt = true
            val fehler = withContext(Dispatchers.IO) {
                try {
                    if (tag.typ == "Bool") {
                        client.bitSchreiben(tag.db, tag.offset, tag.bit, S7Client.boolAusText(neuerWert))
                    } else {
                        client.dbSchreiben(tag.db, tag.offset, S7Client.wertZuBytes(neuerWert, tag.typ))
                    }
                    null
                } catch (fehler: NumberFormatException) {
                    "'$neuerWert' passt nicht zum Typ ${tag.typ}"
                } catch (fehler: Exception) {
                    fehler.message ?: "Schreiben fehlgeschlagen"
                }
            }
            beschaeftigt = false
            meldung = fehler ?: "Geschrieben: ${tag.name} = $neuerWert"
            if (fehler == null) leseDurchgang()
        }
    }

    fun tagMitNamen(name: String): PlcTag? = tags.firstOrNull { it.name == name }

    // ---------- Tags ----------

    fun tagHinzufuegen() {
        tags.add(PlcTag(name = "Tag ${tags.size + 1}"))
        speichern()
    }

    fun tagAendern(index: Int, neu: PlcTag) {
        if (index !in tags.indices) return
        val alt = tags[index]
        if (alt.name != neu.name) werte.remove(alt.name)
        tags[index] = neu
        speichern()
    }

    fun tagEntfernen(index: Int) {
        if (index !in tags.indices) return
        werte.remove(tags[index].name)
        tags.removeAt(index)
        speichern()
    }

    // ---------- Übersichtsseiten ----------

    fun seiteHinzufuegen(name: String) {
        val sauber = name.trim().ifBlank { "Übersicht ${seiten.size + 1}" }
        if (seiten.any { it.name == sauber }) { meldung = "Diesen Seitennamen gibt es schon."; return }
        seiten.add(UebersichtSeite(sauber, emptyList()))
        aktiveSeite = seiten.size - 1
        speichern()
    }

    fun seiteUmbenennen(index: Int, neuerName: String) {
        if (index !in seiten.indices) return
        val sauber = neuerName.trim()
        if (sauber.isBlank()) return
        if (seiten.filterIndexed { i, _ -> i != index }.any { it.name == sauber }) {
            meldung = "Diesen Seitennamen gibt es schon."
            return
        }
        val alt = seiten[index].name
        // Sprungziele mitziehen, damit die Seitenwechsel-Schalter weiter stimmen.
        for (i in seiten.indices) {
            val seite = seiten[i]
            val angepasst = seite.elemente.map {
                if (it.typ == de.spsmonitor.data.ElementTyp.SEITENWECHSEL && it.zielSeite == alt)
                    it.copy(zielSeite = sauber) else it
            }
            seiten[i] = seite.copy(
                name = if (i == index) sauber else seite.name,
                elemente = angepasst
            )
        }
        speichern()
    }

    fun seiteLoeschen(index: Int) {
        if (seiten.size <= 1) { meldung = "Die letzte Seite kann nicht gelöscht werden."; return }
        if (index !in seiten.indices) return
        seiten.removeAt(index)
        aktiveSeite = aktiveSeite.coerceAtMost(seiten.size - 1)
        speichern()
    }

    // ---------- Elemente ----------

    fun elementHinzufuegen(element: UebersichtElement) {
        val seite = seiten.getOrNull(aktiveSeite) ?: return
        seiten[aktiveSeite] = seite.copy(elemente = seite.elemente + element)
        speichern()
    }

    fun elementAendern(element: UebersichtElement, speichernJetzt: Boolean = true) {
        val seite = seiten.getOrNull(aktiveSeite) ?: return
        seiten[aktiveSeite] = seite.copy(
            elemente = seite.elemente.map { if (it.id == element.id) element else it }
        )
        if (speichernJetzt) speichern()
    }

    fun elementLoeschen(id: String) {
        val seite = seiten.getOrNull(aktiveSeite) ?: return
        seiten[aktiveSeite] = seite.copy(elemente = seite.elemente.filter { it.id != id })
        speichern()
    }

    fun zuSeiteWechseln(name: String) {
        val index = seiten.indexOfFirst { it.name == name }
        if (index >= 0) aktiveSeite = index
        else meldung = "Seite '$name' gibt es nicht (mehr)."
    }

    // ---------- Auswertung ----------

    fun verlaufLoeschen() {
        verlauf.clear()
        meldung = "Aufzeichnung gelöscht."
    }

    /**
     * Schreibt den Verlauf als CSV in den Zwischenspeicher der App und gibt die
     * Datei zurück. Semikolon und Dezimalkomma, damit Excel sie direkt öffnet.
     */
    fun csvSchreiben(): File? {
        if (verlauf.isEmpty()) { meldung = "Es wurde noch nichts aufgezeichnet."; return null }
        return try {
            val spalten = verlauf.flatMap { it.werte.keys }.distinct().sorted()
            val zeitFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)

            val text = StringBuilder()
            text.append("Zeitpunkt;").append(spalten.joinToString(";")).append("\n")
            for (punkt in verlauf) {
                text.append(zeitFormat.format(Date(punkt.zeit)))
                for (spalte in spalten) {
                    text.append(";")
                    punkt.werte[spalte]?.let {
                        text.append(String.format(Locale.GERMANY, "%.3f", it))
                    }
                }
                text.append("\n")
            }

            val ordner = File(getApplication<Application>().cacheDir, "export")
            ordner.mkdirs()
            val name = "Auswertung_" +
                SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMANY).format(Date()) + ".csv"
            val datei = File(ordner, name)
            // Mit BOM, sonst zeigt Excel die Umlaute falsch an.
            datei.writeText("﻿$text")
            datei
        } catch (fehler: Exception) {
            meldung = "Export fehlgeschlagen: ${fehler.message}"
            null
        }
    }

    override fun onCleared() {
        abfrageJob?.cancel()
        client.trennen()
        super.onCleared()
    }
}
