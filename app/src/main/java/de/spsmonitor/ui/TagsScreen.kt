package de.spsmonitor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.spsmonitor.MainViewModel
import de.spsmonitor.data.PlcTag
import de.spsmonitor.data.S7Client

@Composable
fun TagsScreen(vm: MainViewModel) {
    var bearbeite by remember { mutableStateOf<Int?>(null) }
    var schreibeIndex by remember { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxWidth()) {

        // Dateiauswahl von Android für den Projektaustausch
        val speichernStarter = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { ziel -> ziel?.let { vm.projektInDatei(it) } }

        val ladenStarter = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { quelle -> quelle?.let { vm.projektAusDatei(it) } }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { vm.tagHinzufuegen() }) { Text("Tag hinzufügen") }
            OutlinedButton(onClick = { vm.alleLesen() }, enabled = vm.verbunden) { Text("Jetzt lesen") }
            OutlinedButton(onClick = { speichernStarter.launch("projekt.json") }) {
                Text("Projekt speichern")
            }
            OutlinedButton(onClick = { ladenStarter.launch(arrayOf("application/json", "*/*")) }) {
                Text("Projekt laden")
            }
        }

        Text(
            "DB-Nummer und Offset stammen aus dem Step7-/TIA-Projekt (z. B. DBD4 → Offset 4, " +
                "DBX8.0 → Offset 8 / Bit 0). Ein gespeichertes Projekt lässt sich unverändert in " +
                "der HTML-Oberfläche öffnen.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vm.tags.size) { index ->
                val tag = vm.tags[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tag.name, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "DB${tag.db} · Offset ${tag.offset}" +
                                        (if (tag.typ == "Bool") ".${tag.bit}" else "") +
                                        " · ${tag.typ}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                vm.werte[tag.name] ?: "–",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { bearbeite = index }) { Text("Bearbeiten") }
                            TextButton(
                                onClick = { schreibeIndex = index },
                                enabled = vm.verbunden
                            ) { Text("Schreiben") }
                            TextButton(onClick = { vm.tagEntfernen(index) }) { Text("Löschen") }
                        }
                    }
                }
            }
        }
    }

    // ---------- Tag bearbeiten ----------
    bearbeite?.let { index ->
        val tag = vm.tags.getOrNull(index)
        if (tag == null) { bearbeite = null; return@let }
        TagDialog(
            tag = tag,
            onAbbrechen = { bearbeite = null },
            onSpeichern = { neu -> vm.tagAendern(index, neu); bearbeite = null }
        )
    }

    // ---------- Wert schreiben ----------
    schreibeIndex?.let { index ->
        val tag = vm.tags.getOrNull(index)
        if (tag == null) { schreibeIndex = null; return@let }
        SchreibDialog(
            tag = tag,
            vorschlag = vm.werte[tag.name] ?: "",
            onAbbrechen = { schreibeIndex = null },
            onSchreiben = { wert -> vm.schreiben(tag, wert); schreibeIndex = null }
        )
    }
}

@Composable
private fun TagDialog(tag: PlcTag, onAbbrechen: () -> Unit, onSpeichern: (PlcTag) -> Unit) {
    var name by remember { mutableStateOf(tag.name) }
    var db by remember { mutableStateOf(tag.db.toString()) }
    var offset by remember { mutableStateOf(tag.offset.toString()) }
    var bit by remember { mutableStateOf(tag.bit.toString()) }
    var typ by remember { mutableStateOf(tag.typ) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Tag bearbeiten") },
        text = {
            // Scrollbar, damit bei offener Tastatur nichts abgeschnitten wird.
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = db, onValueChange = { db = it.filter { z -> z.isDigit() } },
                        label = { Text("DB") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = offset, onValueChange = { offset = it.filter { z -> z.isDigit() } },
                        label = { Text("Offset") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    if (typ == "Bool") {
                        OutlinedTextField(
                            value = bit, onValueChange = { bit = it.filter { z -> z.isDigit() } },
                            label = { Text("Bit") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp)
                        )
                    }
                }
                AuswahlFeld(
                    beschriftung = "Datentyp",
                    werte = S7Client.TYPEN,
                    gewaehlt = typ,
                    onGewaehlt = { typ = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSpeichern(
                    tag.copy(
                        name = name.trim().ifBlank { "Tag" },
                        db = db.toIntOrNull() ?: 1,
                        offset = offset.toIntOrNull() ?: 0,
                        bit = (bit.toIntOrNull() ?: 0).coerceIn(0, 7),
                        typ = typ
                    )
                )
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } }
    )
}

@Composable
private fun SchreibDialog(
    tag: PlcTag,
    vorschlag: String,
    onAbbrechen: () -> Unit,
    onSchreiben: (String) -> Unit
) {
    var wert by remember { mutableStateOf(if (tag.typ == "Bool") "True" else vorschlag) }

    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("Wert schreiben") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${tag.name} — DB${tag.db}, Offset ${tag.offset}" +
                        (if (tag.typ == "Bool") ".${tag.bit}" else "") + " (${tag.typ})"
                )
                if (tag.typ == "Bool") {
                    AuswahlFeld("Neuer Zustand", listOf("True", "False"), wert) { wert = it }
                } else {
                    OutlinedTextField(
                        value = wert, onValueChange = { wert = it },
                        label = { Text("Neuer Wert") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Der Wert wird sofort in die laufende Anlage geschrieben.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSchreiben(wert) }) { Text("Schreiben") } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } }
    )
}

/** Einfaches Auswahlfeld ohne experimentelle Bausteine. */
@Composable
fun AuswahlFeld(
    beschriftung: String,
    werte: List<String>,
    gewaehlt: String,
    onGewaehlt: (String) -> Unit
) {
    var offen by remember { mutableStateOf(false) }
    Column {
        Text(beschriftung, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { offen = true }, modifier = Modifier.fillMaxWidth()) {
            Text(gewaehlt.ifBlank { "(bitte wählen)" })
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            werte.forEach { wert ->
                DropdownMenuItem(
                    text = { Text(wert) },
                    onClick = { onGewaehlt(wert); offen = false }
                )
            }
        }
    }
}
