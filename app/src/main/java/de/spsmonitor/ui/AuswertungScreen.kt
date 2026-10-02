package de.spsmonitor.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.spsmonitor.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val KURVEN_FARBEN = listOf(
    Color(0xFF1F5F9E), Color(0xFFE8590C), Color(0xFF2E9E4B), Color(0xFF8E44AD),
    Color(0xFFC9A227), Color(0xFFC0392B), Color(0xFF128277), Color(0xFF8B5A2B)
)

private val ZEITRAEUME = listOf(
    "Letzte 5 Minuten" to 5L,
    "Letzte 30 Minuten" to 30L,
    "Letzte 2 Stunden" to 120L,
    "Gesamter Verlauf" to 0L
)

@Composable
fun AuswertungScreen(vm: MainViewModel) {
    val kontext = LocalContext.current
    // Anfangs sind die ersten drei Tags angehakt. Die Vorbelegung passiert einmalig
    // beim Anlegen – ein Schreiben während des Zeichnens würde Compose sonst in eine
    // Endlosschleife aus Neuzeichnen und erneutem Schreiben schicken.
    val auswahl = remember {
        mutableStateMapOf<String, Boolean>().apply {
            vm.tags.filter { it.name.isNotBlank() }.take(3).forEach { put(it.name, true) }
        }
    }
    var zeitraum by remember { mutableStateOf(ZEITRAEUME[1].first) }

    val minuten = ZEITRAEUME.firstOrNull { it.first == zeitraum }?.second ?: 30L
    val grenze = if (minuten == 0L) 0L else System.currentTimeMillis() - minuten * 60_000L
    val punkte = vm.verlauf.filter { it.zeit >= grenze }
    val gewaehlteNamen = vm.tags.map { it.name }.filter { auswahl[it] == true }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = vm.aufzeichnung, onCheckedChange = { vm.aufzeichnung = it })
            Text("Aufzeichnung läuft", style = MaterialTheme.typography.bodyMedium)
            Text("${vm.verlauf.size} Punkte", style = MaterialTheme.typography.bodySmall)
        }

        AuswahlFeld("Zeitraum", ZEITRAEUME.map { it.first }, zeitraum) { zeitraum = it }

        // ---------- Diagramm ----------
        Box(
            Modifier.fillMaxWidth().height(260.dp)
                .background(Color.White)
                .border(1.dp, Color(0xFFBFC7D1))
        ) {
            Canvas(Modifier.fillMaxWidth().height(260.dp).padding(4.dp)) {
                val links = 64f
                val unten = size.height - 34f
                val oben = 12f
                val rechts = size.width - 10f

                val stift = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = Color(0xFF555555).toArgb()
                    textSize = 22f
                }

                if (punkte.size < 2 || gewaehlteNamen.isEmpty()) {
                    stift.textSize = 24f
                    drawContext.canvas.nativeCanvas.drawText(
                        if (gewaehlteNamen.isEmpty()) "Unten Kurven anhaken."
                        else "Noch zu wenige Messpunkte.",
                        24f, size.height / 2f, stift
                    )
                    return@Canvas
                }

                // Wertebereich über alle gewählten Kurven
                var min = Double.MAX_VALUE
                var max = -Double.MAX_VALUE
                punkte.forEach { punkt ->
                    gewaehlteNamen.forEach { name ->
                        punkt.werte[name]?.let {
                            if (it < min) min = it
                            if (it > max) max = it
                        }
                    }
                }
                if (min == Double.MAX_VALUE) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "Für die gewählten Kurven liegen keine Werte vor.", 24f, size.height / 2f, stift
                    )
                    return@Canvas
                }
                if (kotlin.math.abs(max - min) < 1e-9) { min -= 1.0; max += 1.0 }
                else { val luft = (max - min) * 0.08; min -= luft; max += luft }

                val startZeit = punkte.first().zeit
                val endZeit = punkte.last().zeit
                val spanne = (endZeit - startZeit).coerceAtLeast(1L)

                fun x(zeit: Long) = links + (rechts - links) * ((zeit - startZeit).toFloat() / spanne)
                fun y(wert: Double) = (oben + (unten - oben) * (1.0 - (wert - min) / (max - min))).toFloat()

                // Gitter und Beschriftung
                for (i in 0..4) {
                    val wert = min + (max - min) * i / 4.0
                    val yy = y(wert)
                    drawLine(Color(0xFFE3E7EC), androidx.compose.ui.geometry.Offset(links, yy),
                        androidx.compose.ui.geometry.Offset(rechts, yy), 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.GERMANY, "%.1f", wert), 6f, yy + 7f, stift
                    )
                }

                drawLine(Color(0xFF1F2D3D), androidx.compose.ui.geometry.Offset(links, oben),
                    androidx.compose.ui.geometry.Offset(links, unten), 2f)
                drawLine(Color(0xFF1F2D3D), androidx.compose.ui.geometry.Offset(links, unten),
                    androidx.compose.ui.geometry.Offset(rechts, unten), 2f)

                val uhr = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
                drawContext.canvas.nativeCanvas.drawText(uhr.format(Date(startZeit)), links, unten + 26f, stift)
                drawContext.canvas.nativeCanvas.drawText(
                    uhr.format(Date(endZeit)), rechts - 90f, unten + 26f, stift
                )

                // Kurven
                gewaehlteNamen.forEachIndexed { index, name ->
                    val farbe = KURVEN_FARBEN[index % KURVEN_FARBEN.size]
                    var vorher: androidx.compose.ui.geometry.Offset? = null
                    punkte.forEach { punkt ->
                        val wert = punkt.werte[name]
                        if (wert != null) {
                            val jetzt = androidx.compose.ui.geometry.Offset(x(punkt.zeit), y(wert))
                            vorher?.let { drawLine(farbe, it, jetzt, 3f) }
                            vorher = jetzt
                        }
                    }
                }
            }
        }

        // ---------- Kennzahlen ----------
        gewaehlteNamen.forEachIndexed { index, name ->
            val werte = punkte.mapNotNull { it.werte[name] }
            if (werte.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).background(KURVEN_FARBEN[index % KURVEN_FARBEN.size]))
                    Text(
                        "$name — min ${"%.2f".format(Locale.GERMANY, werte.min())} · " +
                            "max ${"%.2f".format(Locale.GERMANY, werte.max())} · " +
                            "Ø ${"%.2f".format(Locale.GERMANY, werte.average())}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---------- Kurvenauswahl ----------
        Text("Kurven anzeigen", style = MaterialTheme.typography.labelLarge)
        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            vm.tags.forEachIndexed { index, tag ->
                if (tag.name.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = auswahl[tag.name] == true,
                            onCheckedChange = { auswahl[tag.name] = it }
                        )
                        Box(Modifier.size(14.dp).background(KURVEN_FARBEN[index % KURVEN_FARBEN.size]))
                        Text("  ${tag.name}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ---------- Export ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val datei = vm.csvSchreiben()
                if (datei != null) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            kontext, "${kontext.packageName}.dateien", datei
                        )
                        val absicht = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        kontext.startActivity(Intent.createChooser(absicht, "CSV teilen"))
                        vm.meldung = "${vm.verlauf.size} Messpunkte exportiert."
                    } catch (fehler: Exception) {
                        vm.meldung = "Teilen fehlgeschlagen: ${fehler.message}"
                    }
                }
            }) { Text("Als CSV teilen") }

            OutlinedButton(onClick = { vm.verlaufLoeschen() }) { Text("Aufzeichnung löschen") }
        }

        Text(
            "Die Aufzeichnung läuft nur, solange die App geöffnet ist, und liegt im " +
                "Arbeitsspeicher. Für dauerhafte Daten vorher exportieren.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
