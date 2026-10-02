package de.spsmonitor.data

import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class S7Fehler(meldung: String) : Exception(meldung)

/**
 * Verbindung zu einer Siemens S7-CPU über ISO-TCP (Port 102).
 * Aufbau der Telegramme:  TCP -> TPKT -> COTP -> S7-PDU
 *
 * Bewusst ohne Fremdbibliothek, damit die App keine native Komponente braucht.
 * Alle öffentlichen Methoden sind synchronisiert: die Oberfläche fragt im
 * Hintergrund zyklisch ab, währenddessen kann ein Schreibbefehl dazwischenkommen.
 */
class S7Client {

    private var socket: Socket? = null
    private var eingang: DataInputStream? = null
    private var ausgang: OutputStream? = null
    private var pduReferenz = 0

    val verbunden: Boolean
        @Synchronized get() {
            val s = socket
            return s != null && s.isConnected && !s.isClosed
        }

    // ---------- Verbindung ----------

    /** Gibt bei Erfolg null zurück, sonst einen Fehlertext für die Oberfläche. */
    @Synchronized
    fun verbinden(ip: String, rack: Int, slot: Int, timeoutMs: Int = 5000): String? {
        trennenIntern()
        return try {
            val neu = Socket()
            neu.connect(InetSocketAddress(ip, 102), timeoutMs)
            neu.soTimeout = timeoutMs
            neu.tcpNoDelay = true
            socket = neu
            eingang = DataInputStream(neu.getInputStream())
            ausgang = neu.getOutputStream()

            cotpVerbinden(rack, slot)
            setupKommunikation()
            null
        } catch (fehler: Exception) {
            trennenIntern()
            lesbarerFehler(fehler, ip)
        }
    }

    @Synchronized
    fun trennen() = trennenIntern()

    private fun trennenIntern() {
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        eingang = null
        ausgang = null
    }

    private fun lesbarerFehler(fehler: Exception, ip: String): String = when (fehler) {
        is java.net.SocketTimeoutException ->
            "Keine Antwort von $ip. Ist das Gerät im selben Netz und die IP richtig?"
        is java.net.ConnectException ->
            "Verbindung zu $ip abgelehnt. Port 102 erreichbar? CPU eingeschaltet?"
        is java.net.UnknownHostException ->
            "Adresse $ip nicht gefunden."
        else -> fehler.message ?: fehler.toString()
    }

    // ---------- Übertragung ----------

    private fun senden(nutzdaten: ByteArray) {
        val strom = ausgang ?: throw S7Fehler("Nicht verbunden")
        val gesamt = nutzdaten.size + 4
        val rahmen = ByteArray(gesamt)
        rahmen[0] = 0x03                                   // TPKT-Kennung
        rahmen[1] = 0x00
        rahmen[2] = ((gesamt shr 8) and 0xFF).toByte()
        rahmen[3] = (gesamt and 0xFF).toByte()
        nutzdaten.copyInto(rahmen, 4)
        strom.write(rahmen)
        strom.flush()
    }

    private fun empfangen(): ByteArray {
        val strom = eingang ?: throw S7Fehler("Nicht verbunden")
        val kopf = ByteArray(4)
        strom.readFully(kopf)
        if (kopf[0] != 0x03.toByte()) throw S7Fehler("Ungültige Antwort (kein TPKT-Rahmen)")

        val laenge = ((kopf[2].toInt() and 0xFF) shl 8) or (kopf[3].toInt() and 0xFF)
        if (laenge < 4 || laenge > 8192) throw S7Fehler("Unplausible Antwortlänge: $laenge")

        val rest = ByteArray(laenge - 4)
        strom.readFully(rest)
        return rest
    }

    // ---------- Verbindungsaufbau ----------

    private fun cotpVerbinden(rack: Int, slot: Int) {
        // Ziel-TSAP: 0x03 (PG-Verbindung) und Rack*32 + Slot
        val zielTsap = (rack * 32) + slot
        val anfrage = byteArrayOf(
            0x11,                                   // Länge der COTP-Parameter
            0xE0.toByte(),                          // Verbindungsanfrage (CR)
            0x00, 0x00,                             // Ziel-Referenz
            0x00, 0x01,                             // Quell-Referenz
            0x00,                                   // Klasse 0
            0xC0.toByte(), 0x01, 0x0A,              // größte TPDU: 1024 Byte
            0xC1.toByte(), 0x02, 0x01, 0x00,        // Quell-TSAP
            0xC2.toByte(), 0x02, 0x03, zielTsap.toByte()
        )
        senden(anfrage)
        val antwort = empfangen()
        if (antwort.size < 2 || antwort[1] != 0xD0.toByte()) {
            throw S7Fehler(
                "Die CPU hat die Verbindung abgelehnt. Rack und Slot prüfen " +
                "(bei der S7-315 meist Rack 0 / Slot 2)."
            )
        }
    }

    private fun naechsteReferenz(): Int {
        pduReferenz = (pduReferenz + 1) and 0xFFFF
        return pduReferenz
    }

    private fun setupKommunikation() {
        val pdu = byteArrayOf(0x02, 0xF0.toByte(), 0x80.toByte()) +   // COTP-Datenblock
                byteArrayOf(0x32, 0x01, 0x00, 0x00) +                 // S7-Kopf: Anfrage
                zweiBytes(naechsteReferenz()) +
                byteArrayOf(0x00, 0x08, 0x00, 0x00) +                 // Parameterlänge 8, keine Daten
                byteArrayOf(0xF0.toByte(), 0x00, 0x00, 0x03, 0x00, 0x03, 0x03, 0xC0.toByte())
        senden(pdu)
        val antwort = empfangen()
        if (antwort.size < 10 || antwort[3] != 0x32.toByte() || antwort[4] != 0x03.toByte()) {
            throw S7Fehler("Die CPU hat den Verbindungsaufbau nicht bestätigt")
        }
    }

    // ---------- Lesen und Schreiben ----------

    @Synchronized
    fun dbLesen(db: Int, offset: Int, anzahlBytes: Int): ByteArray {
        if (!verbunden) throw S7Fehler("Nicht verbunden")

        val parameter = byteArrayOf(0x04, 0x01) +               // lesen, ein Element
                byteArrayOf(0x12, 0x0A, 0x10, 0x02) +           // Elementkopf, Transportart BYTE
                zweiBytes(anzahlBytes) +
                zweiBytes(db) +
                byteArrayOf(0x84.toByte()) +                    // Bereich: Datenbaustein
                dreiBytes(offset * 8)                           // Startadresse in Bit

        val pdu = byteArrayOf(0x02, 0xF0.toByte(), 0x80.toByte()) +
                byteArrayOf(0x32, 0x01, 0x00, 0x00) +
                zweiBytes(naechsteReferenz()) +
                zweiBytes(parameter.size) +
                byteArrayOf(0x00, 0x00) +
                parameter

        senden(pdu)
        val antwort = empfangen()
        if (antwort.size < 17) throw S7Fehler("Antwort zu kurz")
        pruefeFehlercode(antwort)

        val start = 3 + 12 + parameterLaenge(antwort)
        val rueckgabe = antwort[start].toInt() and 0xFF
        if (rueckgabe != 0xFF) throw S7Fehler(rueckgabeText(rueckgabe))

        if (antwort.size < start + 4 + anzahlBytes) {
            throw S7Fehler("CPU hat weniger Daten geliefert als angefordert")
        }
        return antwort.copyOfRange(start + 4, start + 4 + anzahlBytes)
    }

    @Synchronized
    fun dbSchreiben(db: Int, offset: Int, daten: ByteArray) {
        if (!verbunden) throw S7Fehler("Nicht verbunden")

        val parameter = byteArrayOf(0x05, 0x01) +               // schreiben, ein Element
                byteArrayOf(0x12, 0x0A, 0x10, 0x02) +
                zweiBytes(daten.size) +
                zweiBytes(db) +
                byteArrayOf(0x84.toByte()) +
                dreiBytes(offset * 8)

        var datenteil = byteArrayOf(0x00, 0x04) +               // Platzhalter, Transportart BYTE
                zweiBytes(daten.size * 8) +                     // Länge in Bit
                daten
        if (datenteil.size % 2 != 0) datenteil += byteArrayOf(0x00)   // auf gerade Länge auffüllen

        val pdu = byteArrayOf(0x02, 0xF0.toByte(), 0x80.toByte()) +
                byteArrayOf(0x32, 0x01, 0x00, 0x00) +
                zweiBytes(naechsteReferenz()) +
                zweiBytes(parameter.size) +
                zweiBytes(datenteil.size) +
                parameter +
                datenteil

        senden(pdu)
        val antwort = empfangen()
        if (antwort.size < 16) throw S7Fehler("Antwort zu kurz")
        pruefeFehlercode(antwort)

        val rueckgabe = antwort[3 + 12 + parameterLaenge(antwort)].toInt() and 0xFF
        if (rueckgabe != 0xFF) throw S7Fehler(rueckgabeText(rueckgabe))
    }

    /**
     * Setzt ein einzelnes Bit, ohne die anderen sieben im Byte zu verändern:
     * Byte lesen, Bit ändern, Byte zurückschreiben.
     */
    @Synchronized
    fun bitSchreiben(db: Int, offset: Int, bit: Int, ein: Boolean) {
        val roh = dbLesen(db, offset, 1)
        val alt = roh[0].toInt() and 0xFF
        val neu = if (ein) alt or (1 shl bit) else alt and (1 shl bit).inv()
        dbSchreiben(db, offset, byteArrayOf(neu.toByte()))
    }

    private fun parameterLaenge(antwort: ByteArray): Int =
        ((antwort[3 + 6].toInt() and 0xFF) shl 8) or (antwort[3 + 7].toInt() and 0xFF)

    private fun pruefeFehlercode(antwort: ByteArray) {
        val fehlerklasse = antwort[3 + 10].toInt() and 0xFF
        val fehlercode = antwort[3 + 11].toInt() and 0xFF
        if (fehlerklasse == 0) return

        val nummer = (fehlerklasse shl 8) or fehlercode
        throw S7Fehler(
            when (nummer) {
                0x0005 -> "Adresse ungültig"
                0x000A -> "Baustein existiert nicht (DB-Nummer prüfen)"
                0x8104 -> "Funktion nicht unterstützt – ist PUT/GET an der CPU freigegeben?"
                0x8500 -> "Falsche PDU-Größe"
                else -> String.format(Locale.US, "CPU meldet Fehler 0x%04X", nummer)
            }
        )
    }

    private fun rueckgabeText(code: Int): String = when (code) {
        0x03 -> "Zugriff verweigert – ist PUT/GET an der CPU freigegeben?"
        0x05 -> "Adresse außerhalb des gültigen Bereichs (Offset und Länge prüfen)"
        0x0A -> "Baustein existiert nicht (DB-Nummer prüfen)"
        else -> String.format(Locale.US, "CPU meldet Code 0x%02X", code)
    }

    private fun zweiBytes(wert: Int) =
        byteArrayOf(((wert shr 8) and 0xFF).toByte(), (wert and 0xFF).toByte())

    private fun dreiBytes(wert: Int) = byteArrayOf(
        ((wert shr 16) and 0xFF).toByte(),
        ((wert shr 8) and 0xFF).toByte(),
        (wert and 0xFF).toByte()
    )

    // ---------- Werte umrechnen ----------

    companion object {

        val TYPEN = listOf("Bool", "Byte", "Int", "DInt", "Real")

        fun groesseFuerTyp(typ: String): Int = when (typ) {
            "Bool", "Byte" -> 1
            "Int" -> 2
            "DInt", "Real" -> 4
            else -> 1
        }

        fun bytesZuWert(rohdaten: ByteArray, typ: String, bit: Int = 0): String {
            val puffer = ByteBuffer.wrap(rohdaten).order(ByteOrder.BIG_ENDIAN)
            return when (typ) {
                "Bool" -> if (((rohdaten[0].toInt() shr bit) and 1) == 1) "True" else "False"
                "Byte" -> (rohdaten[0].toInt() and 0xFF).toString()
                "Int" -> puffer.short.toString()
                "DInt" -> puffer.int.toString()
                "Real" -> String.format(Locale.US, "%.3f", puffer.float)
                else -> "?"
            }
        }

        /** Wirft NumberFormatException, wenn der eingegebene Text nicht zum Typ passt. */
        fun wertZuBytes(text: String, typ: String): ByteArray {
            val sauber = text.trim().replace(",", ".")
            return when (typ) {
                "Byte" -> {
                    val zahl = sauber.toInt()
                    if (zahl !in 0..255) throw NumberFormatException("Byte muss zwischen 0 und 255 liegen")
                    byteArrayOf(zahl.toByte())
                }
                "Int" -> ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
                    .putShort(sauber.toShort()).array()
                "DInt" -> ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                    .putInt(sauber.toInt()).array()
                "Real" -> ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                    .putFloat(sauber.toFloat()).array()
                else -> throw NumberFormatException("Typ $typ kann so nicht geschrieben werden")
            }
        }

        fun boolAusText(text: String): Boolean =
            text.trim().lowercase() in setOf("true", "1", "ein", "an", "ja")

        /** Wandelt einen angezeigten Wert in eine Zahl; Bool wird zu 1 bzw. 0. */
        fun alsZahl(wert: String?): Double? {
            if (wert.isNullOrBlank()) return null
            val w = wert.trim()
            if (w.equals("true", true)) return 1.0
            if (w.equals("false", true)) return 0.0
            return w.toDoubleOrNull()
        }
    }
}
