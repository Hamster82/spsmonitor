package de.spsmonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.spsmonitor.data.ElementTyp
import de.spsmonitor.data.UebersichtElement

/**
 * Einstellungen eines Elements. Bewusst bildschirmfüllend statt als kleiner
 * Popup-Kasten: Kopf- und Fußzeile bleiben stehen, der Inhalt dazwischen lässt
 * sich scrollen. So sind die Schaltflächen auch dann erreichbar, wenn viele
 * Felder sichtbar sind oder die Tastatur offen ist.
 */
@Composable
fun ElementDialog(
    element: UebersichtElement,
    tagNamen: List<String>,
    seitenNamen: List<String>,
    onAbbrechen: () -> Unit,
    onSpeichern: (UebersichtElement) -> Unit
) {
    var symbolTyp by remember { mutableStateOf(element.symbolTyp) }
    var tagName by remember { mutableStateOf(element.tagName) }
    var text by remember { mutableStateOf(element.text) }
    var schrift by remember { mutableStateOf(element.schriftGroesse.toInt().toString()) }
    var einheit by remember { mutableStateOf(element.einheit) }
    var nachkomma by remember { mutableStateOf(element.nachkommastellen.toString()) }
    var editierbar by remember { mutableStateOf(element.editierbar) }
    var minWert by remember { mutableStateOf(element.minWert.toString()) }
    var maxWert by remember { mutableStateOf(element.maxWert.toString()) }
    var sollwert by remember { mutableStateOf(element.sollwert.toString()) }
    var hysterese by remember { mutableStateOf(element.hysterese.toString()) }
    var schreibWert by remember { mutableStateOf(element.schreibWert) }
    var zielSeite by remember { mutableStateOf(element.zielSeite.ifBlank { seitenNamen.firstOrNull() ?: "" }) }
    var breite by remember { mutableStateOf(element.breite.toInt().toString()) }
    var hoehe by remember { mutableStateOf(element.hoehe.toInt().toString()) }

    val istSymbol = element.typ == ElementTyp.SYMBOL
    val istMesswert = istSymbol && symbolTyp in Symbole.MIT_MESSWERT
    val istRegler = istSymbol && symbolTyp == "Temperaturregelung"

    fun gebaut(): UebersichtElement = element.copy(
        symbolTyp = symbolTyp,
        tagName = tagName,
        text = text,
        schriftGroesse = schrift.toFloatOrNull() ?: 18f,
        einheit = einheit,
        nachkommastellen = nachkomma.toIntOrNull() ?: 1,
        editierbar = editierbar,
        minWert = minWert.replace(",", ".").toFloatOrNull() ?: 0f,
        maxWert = maxWert.replace(",", ".").toFloatOrNull() ?: 100f,
        sollwert = sollwert.replace(",", ".").toFloatOrNull() ?: 40f,
        hysterese = hysterese.replace(",", ".").toFloatOrNull() ?: 2f,
        schreibWert = schreibWert,
        zielSeite = zielSeite,
        breite = breite.toFloatOrNull()?.coerceAtLeast(20f) ?: 120f,
        hoehe = hoehe.toFloatOrNull()?.coerceAtLeast(20f) ?: 120f
    )

    val titel = when (element.typ) {
        ElementTyp.SYMBOL -> "Symbol einstellen"
        ElementTyp.TEXT -> "Textfeld einstellen"
        ElementTyp.VARIABLE -> "Messwertfeld einstellen"
        ElementTyp.BUTTON -> "Schalter einstellen"
        else -> "Seitenwechsel einstellen"
    }

    Dialog(
        onDismissRequest = onAbbrechen,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {

                // ---------- Kopfzeile: bleibt stehen ----------
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        titel,
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ---------- Inhalt: scrollt ----------
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    if (istSymbol) {
                        // Vorschau mit Beispielwert
                        val beispiel = if (istMesswert) {
                            val min = minWert.replace(",", ".").toFloatOrNull() ?: 0f
                            val max = maxWert.replace(",", ".").toFloatOrNull() ?: 100f
                            ((min + max) / 2f).toString()
                        } else "True"

                        Text("Vorschau", style = MaterialTheme.typography.labelLarge)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .background(Color(0xFFFBFCFD))
                                .border(1.dp, Color(0xFFBFC7D1))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.fillMaxSize()) { zeichneSymbol(gebaut(), beispiel) }
                        }

                        Text("Symbol wählen", style = MaterialTheme.typography.labelLarge)
                        Symbole.GRUPPEN.forEach { (gruppe, namen) ->
                            Text(
                                gruppe,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // Raster aus drei Spalten, von Hand gebaut - ein LazyGrid
                            // in einer scrollenden Spalte würde die Höhe sprengen.
                            namen.chunked(3).forEach { zeile ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    zeile.forEach { name ->
                                        SymbolKachel(
                                            name = name,
                                            gewaehlt = symbolTyp == name,
                                            vorlage = element,
                                            modifier = Modifier.weight(1f)
                                        ) { symbolTyp = name }
                                    }
                                    // Lücken auffüllen, damit die Kacheln gleich breit bleiben
                                    repeat(3 - zeile.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    if (element.typ in listOf(ElementTyp.TEXT, ElementTyp.BUTTON, ElementTyp.SEITENWECHSEL)) {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it },
                            label = { Text("Beschriftung") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (element.typ in listOf(ElementTyp.SYMBOL, ElementTyp.VARIABLE, ElementTyp.BUTTON)) {
                        AuswahlFeld(
                            beschriftung = when (element.typ) {
                                ElementTyp.BUTTON -> "Tag, in den geschrieben wird"
                                ElementTyp.SYMBOL -> "Tag für Zustand/Messwert (optional)"
                                else -> "Verknüpfter Tag"
                            },
                            werte = listOf("(keiner)") + tagNamen,
                            gewaehlt = tagName.ifBlank { "(keiner)" },
                            onGewaehlt = { tagName = if (it == "(keiner)") "" else it }
                        )
                    }

                    if (element.typ == ElementTyp.VARIABLE || istMesswert) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = einheit, onValueChange = { einheit = it },
                                label = { Text("Einheit") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = nachkomma,
                                onValueChange = { nachkomma = it.filter { z -> z.isDigit() } },
                                label = { Text("Nachkommast.") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (istMesswert) {
                        Text("Messbereich für die Darstellung", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = minWert, onValueChange = { minWert = it },
                                label = { Text("Minimum") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxWert, onValueChange = { maxWert = it },
                                label = { Text("Maximum") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (istRegler) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sollwert, onValueChange = { sollwert = it },
                                label = { Text("Sollwert") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = hysterese, onValueChange = { hysterese = it },
                                label = { Text("Hysterese") }, singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Reine Anzeige: unterhalb Sollwert minus halber Hysterese erscheint " +
                                "\"Heizen EIN\", oberhalb \"Heizen AUS\". Geregelt wird in der SPS.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (element.typ == ElementTyp.VARIABLE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = editierbar, onCheckedChange = { editierbar = it })
                            Text("Wert per Antippen veränderbar")
                        }
                    }

                    if (element.typ == ElementTyp.BUTTON) {
                        OutlinedTextField(
                            value = schreibWert, onValueChange = { schreibWert = it },
                            label = { Text("Wert, der geschrieben wird") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (element.typ == ElementTyp.SEITENWECHSEL) {
                        AuswahlFeld("Zielseite", seitenNamen, zielSeite) { zielSeite = it }
                    }

                    if (!istSymbol) {
                        OutlinedTextField(
                            value = schrift,
                            onValueChange = { schrift = it.filter { z -> z.isDigit() } },
                            label = { Text("Schriftgröße") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Größe auf der Seite", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = breite, onValueChange = { breite = it.filter { z -> z.isDigit() } },
                            label = { Text("Breite") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = hoehe, onValueChange = { hoehe = it.filter { z -> z.isDigit() } },
                            label = { Text("Höhe") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Die Seite ist 1000 Einheiten breit – ein Symbol mit 130 füllt also etwa ein Achtel.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // ---------- Fußzeile: bleibt stehen ----------
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onAbbrechen) { Text("Abbrechen") }
                        Button(onClick = { onSpeichern(gebaut()) }) { Text("Übernehmen") }
                    }
                }
            }
        }
    }
}

/** Eine antippbare Kachel mit Symbolvorschau und Namen. */
@Composable
private fun SymbolKachel(
    name: String,
    gewaehlt: Boolean,
    vorlage: UebersichtElement,
    modifier: Modifier = Modifier,
    onKlick: () -> Unit
) {
    val probe = vorlage.copy(symbolTyp = name, einheit = "", nachkommastellen = 0)
    val beispiel = if (name in Symbole.MIT_MESSWERT) "65" else "True"

    Column(
        modifier
            .background(if (gewaehlt) MaterialTheme.colorScheme.primaryContainer else Color(0xFFFBFCFD))
            .border(
                if (gewaehlt) 2.dp else 1.dp,
                if (gewaehlt) MaterialTheme.colorScheme.primary else Color(0xFFD5DCE4)
            )
            .clickable { onKlick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.fillMaxWidth().height(62.dp)) { zeichneSymbol(probe, beispiel) }
        Text(
            name,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
