package de.spsmonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.spsmonitor.MainViewModel
import de.spsmonitor.data.ElementTyp
import de.spsmonitor.data.S7Client
import de.spsmonitor.data.UebersichtElement
import java.util.Locale

/**
 * Gedachte Seitengröße; alle Elementkoordinaten beziehen sich darauf.
 * Gleiche Maße wie in der HTML-Oberfläche, damit eine Seite überall
 * gleich aussieht und immer vollständig ins Bild passt.
 */
private const val LEINWAND_BREITE = 1000f
private const val LEINWAND_HOEHE = 700f

@Composable
fun UebersichtScreen(vm: MainViewModel) {
    var bearbeiten by remember { mutableStateOf(false) }
    var gewaehlt by remember { mutableStateOf<String?>(null) }
    var dialogElement by remember { mutableStateOf<UebersichtElement?>(null) }
    var dialogIstNeu by remember { mutableStateOf(false) }
    var seiteNeuDialog by remember { mutableStateOf(false) }
    var seiteUmbenennenDialog by remember { mutableStateOf(false) }
    var eingabeDialog by remember { mutableStateOf<UebersichtElement?>(null) }

    val seite = vm.seiten.getOrNull(vm.aktiveSeite)

    Column(Modifier.fillMaxSize()) {

        // ---------- Seitenwahl ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            vm.seiten.forEachIndexed { index, s ->
                val aktiv = index == vm.aktiveSeite
                Button(
                    onClick = { vm.aktiveSeite = index; gewaehlt = null },
                    colors = if (aktiv) ButtonDefaults.buttonColors()
                             else ButtonDefaults.outlinedButtonColors()
                ) { Text(s.name) }
            }
            OutlinedButton(onClick = { seiteNeuDialog = true }) { Text("+ Seite") }
        }

        // ---------- Werkzeugleiste ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bearbeiten", style = MaterialTheme.typography.labelLarge)
            Switch(checked = bearbeiten, onCheckedChange = { bearbeiten = it; gewaehlt = null })

            if (bearbeiten) {
                ElementTyp.ALLE.forEach { typ ->
                    OutlinedButton(onClick = {
                        dialogElement = neuesElement(typ, vm)
                        dialogIstNeu = true
                    }) { Text("+ $typ") }
                }
                OutlinedButton(onClick = { seiteUmbenennenDialog = true }) { Text("Seite umbenennen") }
                OutlinedButton(onClick = { vm.seiteLoeschen(vm.aktiveSeite); gewaehlt = null }) {
                    Text("Seite löschen")
                }
            }
        }

        if (bearbeiten && gewaehlt != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(onClick = {
                    dialogElement = seite?.elemente?.firstOrNull { it.id == gewaehlt }
                    dialogIstNeu = false
                }) { Text("Bearbeiten") }
                OutlinedButton(onClick = {
                    gewaehlt?.let { vm.elementLoeschen(it) }
                    gewaehlt = null
                }) { Text("Löschen") }
            }
        }

        Text(
            if (bearbeiten) "Element antippen zum Auswählen, ziehen zum Verschieben."
            else "Live-Ansicht – Schalter und gelb hinterlegte Felder sind bedienbar.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        // ---------- Leinwand ----------
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(6.dp)
                .background(if (bearbeiten) Color(0xFFEFF3F8) else Color.White)
                .border(1.dp, Color(0xFFBFC7D1))
        ) {
            // So skalieren, dass die ganze Seite hineinpasst (wie ein Bedienpanel)
            val skala = minOf(
                this.maxWidth.value / LEINWAND_BREITE,
                this.maxHeight.value / LEINWAND_HOEHE
            )
            val randLinks = ((this.maxWidth.value - LEINWAND_BREITE * skala) / 2f).coerceAtLeast(0f)
            val randOben = ((this.maxHeight.value - LEINWAND_HOEHE * skala) / 2f).coerceAtLeast(0f)
            val dichte = LocalDensity.current

            seite?.elemente?.forEach { el ->
                Box(
                    Modifier
                        .offset((randLinks + el.x * skala).dp, (randOben + el.y * skala).dp)
                        .size((el.breite * skala).dp, (el.hoehe * skala).dp)
                        .then(
                            if (bearbeiten) Modifier
                                .border(
                                    if (gewaehlt == el.id) 2.dp else 1.dp,
                                    if (gewaehlt == el.id) Color(0xFFE8590C) else Color(0xFF1F5F9E)
                                )
                                .pointerInput(el.id) {
                                    detectTapGestures(onTap = { gewaehlt = el.id })
                                }
                                .pointerInput(el.id) {
                                    detectDragGestures(
                                        onDragStart = { gewaehlt = el.id },
                                        onDragEnd = { vm.speichern() }
                                    ) { change, verschiebung ->
                                        change.consume()
                                        // Bildschirm-Pixel in Leinwandeinheiten umrechnen
                                        val dx = with(dichte) { verschiebung.x.toDp().value } / skala
                                        val dy = with(dichte) { verschiebung.y.toDp().value } / skala
                                        val aktuell = vm.seiten.getOrNull(vm.aktiveSeite)
                                            ?.elemente?.firstOrNull { it.id == el.id }
                                            ?: return@detectDragGestures
                                        vm.elementAendern(
                                            aktuell.copy(
                                                x = (aktuell.x + dx).coerceAtLeast(0f),
                                                y = (aktuell.y + dy).coerceAtLeast(0f)
                                            ),
                                            speichernJetzt = false
                                        )
                                    }
                                }
                            else Modifier
                        )
                ) {
                    ElementInhalt(
                        el = el,
                        vm = vm,
                        skala = skala,
                        bearbeiten = bearbeiten,
                        onWertEingabe = { eingabeDialog = el }
                    )
                }
            }
        }
    }

    // ---------- Dialoge ----------

    dialogElement?.let { el ->
        ElementDialog(
            element = el,
            tagNamen = vm.tags.map { it.name },
            seitenNamen = vm.seiten.map { it.name },
            onAbbrechen = { dialogElement = null },
            onSpeichern = { neu ->
                if (dialogIstNeu) vm.elementHinzufuegen(neu) else vm.elementAendern(neu)
                dialogElement = null
            }
        )
    }

    if (seiteNeuDialog) {
        TextDialog(
            titel = "Neue Seite",
            beschriftung = "Name der Seite",
            vorgabe = "Übersicht ${vm.seiten.size + 1}",
            onAbbrechen = { seiteNeuDialog = false },
            onBestaetigen = { vm.seiteHinzufuegen(it); seiteNeuDialog = false }
        )
    }

    if (seiteUmbenennenDialog && seite != null) {
        TextDialog(
            titel = "Seite umbenennen",
            beschriftung = "Neuer Name",
            vorgabe = seite.name,
            onAbbrechen = { seiteUmbenennenDialog = false },
            onBestaetigen = { vm.seiteUmbenennen(vm.aktiveSeite, it); seiteUmbenennenDialog = false }
        )
    }

    eingabeDialog?.let { el ->
        val tag = vm.tagMitNamen(el.tagName)
        if (tag == null) { eingabeDialog = null; return@let }
        TextDialog(
            titel = "Wert schreiben",
            beschriftung = "${el.tagName} (${tag.typ})",
            vorgabe = vm.werte[el.tagName] ?: "",
            warnung = "Der Wert wird sofort in die laufende Anlage geschrieben.",
            onAbbrechen = { eingabeDialog = null },
            onBestaetigen = { vm.schreiben(tag, it); eingabeDialog = null }
        )
    }
}

/** Darstellung eines einzelnen Elements. */
@Composable
private fun ElementInhalt(
    el: UebersichtElement,
    vm: MainViewModel,
    skala: Float,
    bearbeiten: Boolean,
    onWertEingabe: () -> Unit
) {
    val wert = vm.werte[el.tagName]

    when (el.typ) {
        ElementTyp.SYMBOL -> {
            Canvas(Modifier.fillMaxSize()) { zeichneSymbol(el, wert) }
        }

        ElementTyp.TEXT -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    el.text.ifBlank { "Text" },
                    fontSize = (el.schriftGroesse * skala).sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        ElementTyp.VARIABLE -> {
            val anzeige = wert?.let { roh ->
                val zahl = S7Client.alsZahl(roh)
                val text = if (zahl == null) roh
                           else String.format(Locale.GERMANY, "%.${el.nachkommastellen.coerceIn(0, 4)}f", zahl)
                if (el.einheit.isBlank()) text else "$text ${el.einheit}"
            } ?: "–"

            Box(
                Modifier.fillMaxSize()
                    .background(if (el.editierbar) Color(0xFFFDF6D8) else Color(0xFFF2F5F8))
                    .border(1.dp, Color(0xFF9AA4B2))
                    .then(
                        if (!bearbeiten && el.editierbar) Modifier.clickable { onWertEingabe() }
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    anzeige,
                    fontSize = (el.schriftGroesse * skala).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2D3D)
                )
            }
        }

        ElementTyp.BUTTON -> {
            Button(
                onClick = {
                    vm.tagMitNamen(el.tagName)?.let { vm.schreiben(it, el.schreibWert) }
                        ?: run { vm.meldung = "Tag '${el.tagName}' nicht gefunden." }
                },
                enabled = !bearbeiten && vm.verbunden,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(el.text.ifBlank { "Schalter" }, fontSize = (el.schriftGroesse * skala).sp)
            }
        }

        ElementTyp.SEITENWECHSEL -> {
            OutlinedButton(
                onClick = { vm.zuSeiteWechseln(el.zielSeite) },
                enabled = !bearbeiten,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(el.text.ifBlank { "Weiter" }, fontSize = (el.schriftGroesse * skala).sp)
            }
        }
    }
}

private fun neuesElement(typ: String, vm: MainViewModel): UebersichtElement = when (typ) {
    ElementTyp.SYMBOL -> UebersichtElement(typ = typ, breite = 130f, hoehe = 130f, symbolTyp = "Motor")
    ElementTyp.TEXT -> UebersichtElement(typ = typ, breite = 220f, hoehe = 50f, text = "Neuer Text", schriftGroesse = 22f)
    ElementTyp.VARIABLE -> UebersichtElement(typ = typ, breite = 220f, hoehe = 70f, schriftGroesse = 22f)
    ElementTyp.BUTTON -> UebersichtElement(typ = typ, breite = 200f, hoehe = 70f, text = "Start", schriftGroesse = 18f)
    else -> UebersichtElement(
        typ = ElementTyp.SEITENWECHSEL, breite = 200f, hoehe = 70f, text = "Weiter",
        schriftGroesse = 18f, zielSeite = vm.seiten.firstOrNull()?.name ?: ""
    )
}

/** Kleiner Dialog für eine einzelne Texteingabe. */
@Composable
fun TextDialog(
    titel: String,
    beschriftung: String,
    vorgabe: String,
    warnung: String? = null,
    onAbbrechen: () -> Unit,
    onBestaetigen: (String) -> Unit
) {
    var text by remember { mutableStateOf(vorgabe) }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text(titel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(beschriftung) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                warnung?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onBestaetigen(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Abbrechen") } }
    )
}
