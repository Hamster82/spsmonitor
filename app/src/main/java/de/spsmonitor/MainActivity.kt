package de.spsmonitor

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.spsmonitor.ui.AuswertungScreen
import de.spsmonitor.ui.SpsMonitorTheme
import de.spsmonitor.ui.TagsScreen
import de.spsmonitor.ui.UebersichtScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ohne das reißt beim Ausschalten des Bildschirms die Verbindung zur SPS ab.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SpsMonitorTheme {
                Hauptansicht(vm)
            }
        }
    }
}

@Composable
private fun Hauptansicht(vm: MainViewModel) {
    var reiter by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Tags", "Übersicht", "Auswertung").forEachIndexed { index, name ->
                    NavigationBarItem(
                        selected = reiter == index,
                        onClick = { reiter = index },
                        icon = {},
                        label = { Text(name) }
                    )
                }
            }
        }
    ) { abstand ->
        Column(Modifier.fillMaxSize().padding(abstand)) {

            Verbindungsleiste(vm)

            if (vm.meldung.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        vm.meldung,
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            when (reiter) {
                0 -> TagsScreen(vm)
                1 -> UebersichtScreen(vm)
                else -> AuswertungScreen(vm)
            }
        }
    }
}

@Composable
private fun Verbindungsleiste(vm: MainViewModel) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = vm.ip,
                    onValueChange = { vm.ip = it },
                    label = { Text("IP-Adresse") },
                    singleLine = true,
                    enabled = !vm.verbunden,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = vm.rack,
                    onValueChange = { vm.rack = it.filter { z -> z.isDigit() } },
                    label = { Text("Rack") },
                    singleLine = true,
                    enabled = !vm.verbunden,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp)
                )
                OutlinedTextField(
                    value = vm.slot,
                    onValueChange = { vm.slot = it.filter { z -> z.isDigit() } },
                    label = { Text("Slot") },
                    singleLine = true,
                    enabled = !vm.verbunden,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp)
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { vm.verbindenOderTrennen() }, enabled = !vm.beschaeftigt) {
                    Text(if (vm.verbunden) "Trennen" else "Verbinden")
                }
                Text(
                    if (vm.verbunden) "Verbunden" else "Nicht verbunden",
                    fontWeight = FontWeight.Bold,
                    color = if (vm.verbunden) Color(0xFF2E9E4B) else Color(0xFFC0392B)
                )
                Switch(
                    checked = vm.autoRefresh,
                    onCheckedChange = { vm.autoRefreshSetzen(it) },
                    enabled = vm.verbunden
                )
                Text("alle 2 s", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                // Damit immer erkennbar ist, welcher Stand installiert ist
                Text(
                    "v" + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}
