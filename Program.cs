using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;

namespace SpsBruecke
{
    public static class Program
    {
        public const string Version = "1.4";

        private static readonly PlcService Plc = new();
        private static readonly JsonSerializerOptions JsonOptionen = new()
        {
            PropertyNameCaseInsensitive = true,
            WriteIndented = true
        };

        private static string BasisOrdner => AppContext.BaseDirectory;
        private static string HtmlDatei => Path.Combine(BasisOrdner, "oberflaeche.html");
        private static string ProjektDatei => Path.Combine(BasisOrdner, "projekt.json");
        // Datei aus früheren Fassungen - wird beim ersten Start übernommen
        private static string AlteDatei => Path.Combine(BasisOrdner, "konfiguration.json");

        private static int Port = 8080;
        private static readonly DateTime GestartetUm = DateTime.Now;

        // ---- Wachhund -------------------------------------------------------
        // Die Oberfläche meldet sich regelmäßig. Bleiben die Lebenszeichen aus,
        // wurde der Browser geschlossen - dann beendet sich die Brücke selbst.
        // Scharf gemacht wird der Wachhund nur vom eigenen Rechner aus, damit
        // ein Handy im Netz die Brücke nicht versehentlich abschaltet.
        private static readonly object WachhundSperre = new();
        private static bool WachhundScharf;
        private static DateTime WachhundFrist = DateTime.MaxValue;
        private static int WachhundSekunden = 25;

        public static void Main(string[] args)
        {
            bool leise = false;

            foreach (var arg in args)
            {
                if (int.TryParse(arg, out int gewaehlterPort) && gewaehlterPort is > 0 and < 65536)
                    Port = gewaehlterPort;
                else if (arg.StartsWith("spsmonitor:", StringComparison.OrdinalIgnoreCase))
                {
                    // Aufruf aus dem Browser:  spsmonitor://start?port=8080
                    leise = true;
                    Port = PortAusUrlLesen(arg, Port);
                }
                else if (arg.Equals("--leise", StringComparison.OrdinalIgnoreCase))
                    leise = true;
            }

            int port = Port;
            if (leise) FensterVerkleinern();
            ProtokollAnmelden();

            Console.Title = "SPS-Brücke";
            Console.WriteLine("=================================================");
            Console.WriteLine("  SPS-Brücke läuft");
            Console.WriteLine("=================================================");
            Console.WriteLine();
            Console.WriteLine("  Oberfläche im Browser öffnen:");
            Console.WriteLine($"    http://localhost:{port}");
            Console.WriteLine();
            Console.WriteLine("  Von einem anderen Gerät im selben Netz (Tablet/Handy):");
            foreach (var ip in EigeneIpAdressen())
                Console.WriteLine($"    http://{ip}:{port}");
            Console.WriteLine();
            Console.WriteLine("  Dieses Fenster offen lassen - es ist die Verbindung zur SPS.");
            Console.WriteLine("  Beenden: in der Oberfläche auf \"Brücke stoppen\", dieses Fenster");
            Console.WriteLine("  schließen oder Strg+C drücken. Wird die Oberfläche geschlossen,");
            Console.WriteLine("  geht die Brücke nach wenigen Sekunden von selbst aus.");
            Console.WriteLine();

            TcpListener listener;
            try
            {
                listener = new TcpListener(IPAddress.Any, port);
                listener.Start();
            }
            catch (SocketException ex)
            {
                // Aus dem Browser gestartet und der Port ist belegt: dann läuft
                // die Brücke bereits. Das ist kein Fehler - einfach still gehen.
                if (leise) return;

                Console.WriteLine($"  FEHLER: Port {port} konnte nicht geöffnet werden.");
                Console.WriteLine($"  {ex.Message}");
                Console.WriteLine();
                Console.WriteLine("  Vermutlich läuft die Brücke schon, oder ein anderes Programm");
                Console.WriteLine("  belegt den Port. Anderen Port verwenden:  SpsBruecke.exe 8081");
                Console.WriteLine();
                Console.WriteLine("  Zum Schließen eine Taste drücken.");
                Console.ReadKey();
                return;
            }

            WachhundStarten();

            while (true)
            {
                try
                {
                    var client = listener.AcceptTcpClient();
                    // Jede Anfrage in einem eigenen Thread, damit langsame SPS-Zugriffe
                    // die Oberfläche nicht blockieren.
                    ThreadPool.QueueUserWorkItem(_ => AnfrageBearbeiten(client));
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"  Fehler beim Annehmen einer Verbindung: {ex.Message}");
                }
            }
        }

        // ===================== Wachhund =====================

        /// <summary>
        /// Prüft einmal pro Sekunde, ob sich die Oberfläche noch meldet.
        /// Bleibt sie länger weg als vereinbart, beendet sich die Brücke.
        /// </summary>
        private static void WachhundStarten()
        {
            var faden = new Thread(() =>
            {
                while (true)
                {
                    Thread.Sleep(1000);
                    bool abgelaufen;
                    lock (WachhundSperre)
                        abgelaufen = WachhundScharf && DateTime.Now > WachhundFrist;

                    if (abgelaufen)
                    {
                        Console.WriteLine();
                        Console.WriteLine("  Die Oberfläche wurde geschlossen - die Brücke beendet sich.");
                        SauberBeenden();
                    }
                }
            })
            { IsBackground = true, Name = "Wachhund" };
            faden.Start();
        }

        /// <summary>Lebenszeichen der Oberfläche: Frist neu setzen.</summary>
        private static void WachhundFuettern(int sekunden)
        {
            lock (WachhundSperre)
            {
                WachhundSekunden = Math.Clamp(sekunden, 5, 3600);
                WachhundScharf = true;
                WachhundFrist = DateTime.Now.AddSeconds(WachhundSekunden);
            }
        }

        /// <summary>Oberfläche abgemeldet: kurze Gnadenfrist, damit ein
        /// Neuladen der Seite die Brücke nicht mitnimmt.</summary>
        private static void WachhundKurzeFrist(int sekunden)
        {
            lock (WachhundSperre)
            {
                if (!WachhundScharf) return;
                WachhundFrist = DateTime.Now.AddSeconds(Math.Clamp(sekunden, 2, 60));
            }
        }

        private static void WachhundAus()
        {
            lock (WachhundSperre)
            {
                WachhundScharf = false;
                WachhundFrist = DateTime.MaxValue;
            }
        }

        /// <summary>Alles, was die Oberfläche über die Brücke wissen will.</summary>
        private static object StandObjekt(bool vomEigenenRechner) => new
        {
            verbunden = Plc.IstVerbunden,
            bruecke = true,
            version = Version,
            port = Port,
            laeuftSeit = GestartetUm.ToString("yyyy-MM-dd HH:mm:ss"),
            laufzeitSekunden = Math.Round((DateTime.Now - GestartetUm).TotalSeconds),
            eigenerRechner = vomEigenenRechner,
            wachhund = WachhundStand()
        };

        private static object WachhundStand()
        {
            lock (WachhundSperre)
            {
                double rest = WachhundScharf
                    ? Math.Max(0, (WachhundFrist - DateTime.Now).TotalSeconds)
                    : 0;
                return new
                {
                    scharf = WachhundScharf,
                    sekunden = WachhundSekunden,
                    restSekunden = Math.Round(rest)
                };
            }
        }

        /// <summary>Verbindung zur SPS lösen und das Programm verlassen.</summary>
        private static void SauberBeenden()
        {
            try { Plc.Trennen(); } catch { /* beim Beenden egal */ }
            Environment.Exit(0);
        }

        // ===================== Start aus dem Browser =====================

        /// <summary>Holt den Port aus  spsmonitor://start?port=8081</summary>
        private static int PortAusUrlLesen(string url, int standard)
        {
            int frage = url.IndexOf("port=", StringComparison.OrdinalIgnoreCase);
            if (frage < 0) return standard;

            var ziffern = new StringBuilder();
            for (int i = frage + 5; i < url.Length && char.IsDigit(url[i]); i++)
                ziffern.Append(url[i]);

            return int.TryParse(ziffern.ToString(), out int p) && p is > 0 and < 65536 ? p : standard;
        }

        /// <summary>
        /// Trägt  spsmonitor://  für den angemeldeten Benutzer ein. Danach darf
        /// die Oberfläche im Browser die Brücke selbst starten. Das geschieht
        /// nur unter dem eigenen Benutzerkonto (HKEY_CURRENT_USER) und braucht
        /// deshalb keine Administratorrechte.
        /// </summary>
        private static void ProtokollAnmelden()
        {
            if (!OperatingSystem.IsWindows()) return;

            try
            {
                string exe = Environment.ProcessPath ?? "";
                if (string.IsNullOrEmpty(exe)) return;

                string schluessel = @"HKCU\Software\Classes\spsmonitor";
                RegSetzen(schluessel, "", "REG_SZ", "URL:SPS Monitor");
                RegSetzen(schluessel, "URL Protocol", "REG_SZ", "");
                RegSetzen(schluessel + @"\DefaultIcon", "", "REG_SZ", $"\"{exe}\",0");
                RegSetzen(schluessel + @"\shell\open\command", "", "REG_SZ", $"\"{exe}\" \"%1\"");
            }
            catch
            {
                // Nur Komfort: klappt das nicht, bleibt der Start über die
                // Verknüpfung bzw. das Startfenster.
            }
        }

        private static void RegSetzen(string schluessel, string name, string typ, string wert)
        {
            var start = new ProcessStartInfo("reg")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.ArgumentList.Add("add");
            start.ArgumentList.Add(schluessel);
            if (name.Length > 0) { start.ArgumentList.Add("/v"); start.ArgumentList.Add(name); }
            else start.ArgumentList.Add("/ve");
            start.ArgumentList.Add("/t"); start.ArgumentList.Add(typ);
            start.ArgumentList.Add("/d"); start.ArgumentList.Add(wert);
            start.ArgumentList.Add("/f");

            using var vorgang = Process.Start(start);
            vorgang?.WaitForExit(5000);
        }

        [DllImport("kernel32.dll")] private static extern IntPtr GetConsoleWindow();
        [DllImport("user32.dll")] private static extern bool ShowWindow(IntPtr fenster, int befehl);

        /// <summary>Konsolenfenster klein machen - beim Start aus dem Browser
        /// soll kein schwarzes Fenster aufspringen.</summary>
        private static void FensterVerkleinern()
        {
            if (!OperatingSystem.IsWindows()) return;
            try
            {
                var fenster = GetConsoleWindow();
                if (fenster != IntPtr.Zero) ShowWindow(fenster, 6); // 6 = SW_MINIMIZE
            }
            catch { /* ohne Konsole ebenfalls egal */ }
        }

        private static IEnumerable<string> EigeneIpAdressen()
        {
            var ergebnis = new List<string>();
            try
            {
                foreach (var adresse in Dns.GetHostAddresses(Dns.GetHostName()))
                {
                    if (adresse.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(adresse))
                        ergebnis.Add(adresse.ToString());
                }
            }
            catch
            {
                // Netzwerkadressen sind nur ein Hinweis - localhost funktioniert immer.
            }
            if (ergebnis.Count == 0)
                ergebnis.Add("(keine Netzwerkadresse gefunden)");
            return ergebnis;
        }

        // ===================== HTTP =====================

        private static void AnfrageBearbeiten(TcpClient client)
        {
            try
            {
                using (client)
                using (var stream = client.GetStream())
                {
                    client.ReceiveTimeout = 15000;
                    client.SendTimeout = 15000;

                    bool vomEigenenRechner = IstEigenerRechner(client);

                    var (methode, pfad, koerper) = AnfrageLesen(stream);
                    if (methode == null || pfad == null)
                        return;

                    // Preflight des Browsers (kommt, wenn die HTML-Datei direkt per
                    // Doppelklick geöffnet wurde statt über die Brücke).
                    if (methode == "OPTIONS")
                    {
                        AntwortSenden(stream, 204, "text/plain", Array.Empty<byte>());
                        return;
                    }

                    Route(stream, methode, pfad, koerper, vomEigenenRechner);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"  Fehler bei einer Anfrage: {ex.Message}");
            }
        }

        /// <summary>Kommt die Anfrage vom selben Rechner (localhost)?</summary>
        private static bool IstEigenerRechner(TcpClient client)
        {
            try
            {
                if (client.Client.RemoteEndPoint is IPEndPoint ende)
                {
                    var adresse = ende.Address;
                    if (adresse.IsIPv4MappedToIPv6) adresse = adresse.MapToIPv4();
                    return IPAddress.IsLoopback(adresse);
                }
            }
            catch { /* im Zweifel: nein */ }
            return false;
        }

        private static (string? Methode, string? Pfad, string Koerper) AnfrageLesen(NetworkStream stream)
        {
            var kopfBytes = new List<byte>();

            // Kopfzeilen zeichenweise bis zur Leerzeile lesen.
            while (true)
            {
                int b = stream.ReadByte();
                if (b < 0) return (null, null, "");
                kopfBytes.Add((byte)b);

                if (kopfBytes.Count >= 4)
                {
                    int n = kopfBytes.Count;
                    if (kopfBytes[n - 4] == 13 && kopfBytes[n - 3] == 10 &&
                        kopfBytes[n - 2] == 13 && kopfBytes[n - 1] == 10)
                        break;
                }

                if (kopfBytes.Count > 64 * 1024) return (null, null, ""); // Schutz gegen Unsinn
            }

            string kopf = Encoding.UTF8.GetString(kopfBytes.ToArray());
            var zeilen = kopf.Split("\r\n");
            var ersteTeile = zeilen[0].Split(' ');
            if (ersteTeile.Length < 2) return (null, null, "");

            string methode = ersteTeile[0];
            string pfad = ersteTeile[1];

            int laenge = 0;
            foreach (var zeile in zeilen)
            {
                if (zeile.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase))
                    int.TryParse(zeile.Substring("Content-Length:".Length).Trim(), out laenge);
            }

            string koerper = "";
            if (laenge > 0)
            {
                var puffer = new byte[laenge];
                int gelesen = 0;
                while (gelesen < laenge)
                {
                    int n = stream.Read(puffer, gelesen, laenge - gelesen);
                    if (n <= 0) break;
                    gelesen += n;
                }
                koerper = Encoding.UTF8.GetString(puffer, 0, gelesen);
            }

            return (methode, pfad, koerper);
        }

        private static void AntwortSenden(NetworkStream stream, int status, string inhaltsTyp, byte[] koerper)
        {
            string statusText = status switch
            {
                200 => "OK",
                204 => "No Content",
                400 => "Bad Request",
                404 => "Not Found",
                500 => "Internal Server Error",
                _ => "OK"
            };

            var kopf = new StringBuilder();
            kopf.Append($"HTTP/1.1 {status} {statusText}\r\n");
            kopf.Append($"Content-Type: {inhaltsTyp}\r\n");
            kopf.Append($"Content-Length: {koerper.Length}\r\n");
            // Erlaubt auch das direkte Öffnen der HTML-Datei per Doppelklick.
            kopf.Append("Access-Control-Allow-Origin: *\r\n");
            kopf.Append("Access-Control-Allow-Headers: Content-Type\r\n");
            kopf.Append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n");
            kopf.Append("Cache-Control: no-store\r\n");
            kopf.Append("Connection: close\r\n");
            kopf.Append("\r\n");

            var kopfBytes = Encoding.UTF8.GetBytes(kopf.ToString());
            stream.Write(kopfBytes, 0, kopfBytes.Length);
            if (koerper.Length > 0)
                stream.Write(koerper, 0, koerper.Length);
            stream.Flush();
        }

        private static void JsonSenden(NetworkStream stream, object objekt) =>
            AntwortSenden(stream, 200, "application/json; charset=utf-8",
                Encoding.UTF8.GetBytes(JsonSerializer.Serialize(objekt, JsonOptionen)));

        // ===================== Routen =====================

        private static void Route(NetworkStream stream, string methode, string pfad, string koerper,
                                  bool vomEigenenRechner)
        {
            // Query-Teil abschneiden
            int frage = pfad.IndexOf('?');
            string abfrage = frage >= 0 ? pfad.Substring(frage + 1) : "";
            if (frage >= 0) pfad = pfad.Substring(0, frage);

            switch (pfad)
            {
                case "/":
                case "/index.html":
                case "/oberflaeche.html":
                    HtmlSenden(stream);
                    return;

                case "/api/status":
                    JsonSenden(stream, StandObjekt(vomEigenenRechner));
                    return;

                // Die Oberfläche meldet sich - und sagt, wie lange sie
                // höchstens wegbleiben darf, bevor die Brücke aufgibt.
                case "/api/lebenszeichen":
                {
                    if (!vomEigenenRechner)
                    {
                        // Ein Gerät im Netz darf die Brücke nicht abschalten.
                        JsonSenden(stream, StandObjekt(false));
                        return;
                    }

                    int sekunden = 25;
                    bool an = true;
                    if (!string.IsNullOrWhiteSpace(koerper))
                    {
                        var wunsch = JsonSerializer.Deserialize<WachhundAnfrage>(koerper, JsonOptionen);
                        if (wunsch != null) { sekunden = wunsch.Sekunden ?? 25; an = wunsch.An ?? true; }
                    }
                    else if (abfrage.Contains("aus", StringComparison.OrdinalIgnoreCase))
                        an = false;

                    if (an) WachhundFuettern(sekunden); else WachhundAus();
                    JsonSenden(stream, StandObjekt(vomEigenenRechner));
                    return;
                }

                // Die Seite wird geschlossen oder neu geladen. Kurze Gnadenfrist:
                // meldet sich innerhalb weniger Sekunden wieder jemand, bleibt
                // die Brücke; sonst geht sie von selbst aus.
                case "/api/abmelden":
                {
                    if (vomEigenenRechner)
                    {
                        WachhundKurzeFrist(6);
                        Console.WriteLine("  Oberfläche abgemeldet - Brücke endet in 6 Sekunden, falls sie nicht zurückkommt.");
                    }
                    JsonSenden(stream, new { ok = true });
                    return;
                }

                // Ausdrücklich beenden, auf Knopfdruck aus der Oberfläche.
                case "/api/beenden":
                {
                    Console.WriteLine("  Beenden angefordert - die Brücke wird geschlossen.");
                    JsonSenden(stream, new { ok = true });
                    try { stream.Flush(); } catch { }

                    // Antwort noch rausschreiben lassen, dann gehen.
                    var ende = new Thread(() => { Thread.Sleep(300); SauberBeenden(); })
                        { IsBackground = true };
                    ende.Start();
                    return;
                }

                case "/api/verbinden":
                {
                    var anfrage = JsonSerializer.Deserialize<VerbindenAnfrage>(koerper, JsonOptionen);
                    if (anfrage == null || string.IsNullOrWhiteSpace(anfrage.Ip))
                    {
                        JsonSenden(stream, new { ok = false, fehler = "Keine IP-Adresse angegeben." });
                        return;
                    }
                    string? fehler = Plc.Verbinden(anfrage.Ip.Trim(), anfrage.Rack, anfrage.Slot);
                    Console.WriteLine(fehler == null
                        ? $"  Verbunden mit {anfrage.Ip} (Rack {anfrage.Rack}, Slot {anfrage.Slot})"
                        : $"  Verbindung zu {anfrage.Ip} fehlgeschlagen: {fehler}");
                    JsonSenden(stream, new { ok = fehler == null, fehler });
                    return;
                }

                case "/api/trennen":
                    Plc.Trennen();
                    Console.WriteLine("  Verbindung getrennt.");
                    JsonSenden(stream, new { ok = true });
                    return;

                case "/api/lesen":
                {
                    var tags = JsonSerializer.Deserialize<List<TagDef>>(koerper, JsonOptionen) ?? new();
                    var ergebnisse = new List<object>();
                    foreach (var tag in tags)
                    {
                        var (wert, fehler) = Plc.Lesen(tag);
                        ergebnisse.Add(new { name = tag.Name, wert, fehler });
                    }
                    JsonSenden(stream, ergebnisse);
                    return;
                }

                case "/api/schreiben":
                {
                    var anfrage = JsonSerializer.Deserialize<SchreibAnfrage>(koerper, JsonOptionen);
                    if (anfrage?.Tag == null)
                    {
                        JsonSenden(stream, new { ok = false, fehler = "Ungültige Anfrage." });
                        return;
                    }
                    string? fehler = Plc.Schreiben(anfrage.Tag, anfrage.Wert ?? "");
                    Console.WriteLine(fehler == null
                        ? $"  Geschrieben: {anfrage.Tag.Name} = {anfrage.Wert}"
                        : $"  Schreiben von {anfrage.Tag.Name} fehlgeschlagen: {fehler}");
                    JsonSenden(stream, new { ok = fehler == null, fehler });
                    return;
                }

                case "/api/projekt":
                case "/api/konfiguration":   // alter Name, bleibt bedienbar
                    if (methode == "POST")
                    {
                        try
                        {
                            File.WriteAllText(ProjektDatei, koerper, new UTF8Encoding(false));
                            JsonSenden(stream, new { ok = true });
                        }
                        catch (Exception ex)
                        {
                            JsonSenden(stream, new { ok = false, fehler = ex.Message });
                        }
                    }
                    else
                    {
                        // Projektdatei, sonst die alte Konfiguration, sonst leer
                        if (File.Exists(ProjektDatei))
                            AntwortSenden(stream, 200, "application/json; charset=utf-8",
                                File.ReadAllBytes(ProjektDatei));
                        else if (File.Exists(AlteDatei))
                            AntwortSenden(stream, 200, "application/json; charset=utf-8",
                                File.ReadAllBytes(AlteDatei));
                        else
                            JsonSenden(stream, new { tags = Array.Empty<object>(), seiten = Array.Empty<object>() });
                    }
                    return;

                default:
                    AntwortSenden(stream, 404, "text/plain; charset=utf-8",
                        Encoding.UTF8.GetBytes("Nicht gefunden"));
                    return;
            }
        }

        private static void HtmlSenden(NetworkStream stream)
        {
            if (!File.Exists(HtmlDatei))
            {
                AntwortSenden(stream, 404, "text/plain; charset=utf-8", Encoding.UTF8.GetBytes(
                    "oberflaeche.html wurde nicht gefunden. Sie muss im selben Ordner wie SpsBruecke.exe liegen."));
                return;
            }
            AntwortSenden(stream, 200, "text/html; charset=utf-8", File.ReadAllBytes(HtmlDatei));
        }

        private class VerbindenAnfrage
        {
            public string Ip { get; set; } = "";
            public int Rack { get; set; }
            public int Slot { get; set; } = 2;
        }

        private class SchreibAnfrage
        {
            public TagDef? Tag { get; set; }
            public string? Wert { get; set; }
        }

        private class WachhundAnfrage
        {
            public bool? An { get; set; }
            public int? Sekunden { get; set; }
        }
    }
}
