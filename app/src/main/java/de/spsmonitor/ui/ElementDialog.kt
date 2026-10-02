package de.spsmonitor.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.spsmonitor.data.ElementTyp
import de.spsmonitor.data.UebersichtElement

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

    val istMesswert = element.typ == ElementTyp.SYMBOL && symbolTyp in Symbole.MIT_MESSWERT
    val istRegler = element.typ == ElementTyp.SYMBOL && symbolTyp == "Temperaturregelung"

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

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = {
            Text(
                when (element.typ) {
                    ElementTyp.SYMBOL -> "Symbol"
                    ElementTyp.TEXT -> "Textfeld"
                    ElementTyp.VARIABLE -> "Messwertfeld"
                    ElementTyp.BUTTON -> "Schalter"
                    else -> "Seitenwechsel"
                } + " einstellen"
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                if (element.typ == ElementTyp.SYMBOL) {
                    // Vorschau mit Beispielwert
                    val beispiel = if (istMesswert) {
                        val min = minWert.replace(",", ".").toFloatOrNull() ?: 0f
                        val max = maxWert.replace(",", ".").toFloatOrNull() ?: 100f
                        ((min + max) / 2f).toString()
                    } else "True"

                    Box(
                        Modifier.fillMaxWidth().height(130.dp)
                            .background(Color(0xFFFBFCFD))
                            .border(1.dp, Color(0xFFBFC7D1))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(118.dp)) {
                            zeichneSymbol(gebaut(), beispiel)
                        }
                    }

                    Symbole.GRUPPEN.forEach { (gruppe, namen) ->
                        Text(gruppe, style = MaterialTheme.typography.labelMedium)
                        AuswahlFeld("", namen, if (symbolTyp in namen) symbolTyp else "") {
                            symbolTyp = it
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
                    Text("Messbereich für die Darstellung", style = MaterialTheme.typography.labelMedium)
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

                if (element.typ != ElementTyp.SYMBOL) {
                    OutlinedTextField(
                        value = schrift,
                        onValueChange = { schrift = it.filter { z -> z.isDigit() } },
                        label = { Text("Schriftgröße") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Größe auf der Seite", style = MaterialTheme.typography.labelMedium)
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
        },
        confirmButton = { TextButton(onClick = { onSpeichern(gebaut()) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } }
    )
}
