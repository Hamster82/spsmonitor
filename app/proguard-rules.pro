# Die App nutzt keine Reflexion: JSON wird von Hand aufgebaut, nicht über
# Feldnamen gelesen. Deshalb reichen die Standardregeln; hier nur Absicherungen.

# Zeilennummern für verständliche Absturzmeldungen behalten
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Die Datenklassen bleiben vollständig erhalten, damit das Speicherformat
# der Konfiguration zur Windows-Fassung kompatibel bleibt.
-keep class de.spsmonitor.data.** { *; }

# Kotlin-Coroutines
-dontwarn kotlinx.coroutines.**
