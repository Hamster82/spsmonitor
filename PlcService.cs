using Sharp7;
using System.Globalization;

namespace SpsBruecke
{
    /// <summary>Beschreibung eines Werts im DB, kommt so von der Weboberfläche.</summary>
    public class TagDef
    {
        public string Name { get; set; } = "";
        public int Db { get; set; }
        public int Offset { get; set; }
        public int Bit { get; set; }
        public string Typ { get; set; } = "Real";
    }

    /// <summary>
    /// Kapselt den Sharp7-Client. Alle Zugriffe laufen über ein Lock, weil der
    /// Webserver mehrere Anfragen gleichzeitig bearbeiten kann, der S7-Client
    /// aber nur eine Verbindung hat und nicht threadsicher ist.
    /// </summary>
    public class PlcService
    {
        private readonly S7Client _client = new();
        private readonly object _sperre = new();

        public bool IstVerbunden
        {
            get { lock (_sperre) return _client.Connected; }
        }

        public string? Verbinden(string ip, int rack, int slot)
        {
            lock (_sperre)
            {
                if (_client.Connected)
                    _client.Disconnect();

                int result = _client.ConnectTo(ip, rack, slot);
                return result == 0 ? null : _client.ErrorText(result);
            }
        }

        public void Trennen()
        {
            lock (_sperre)
            {
                if (_client.Connected)
                    _client.Disconnect();
            }
        }

        /// <summary>Liest einen Wert. Gibt (Wert, null) oder (null, Fehlertext) zurück.</summary>
        public (string? Wert, string? Fehler) Lesen(TagDef tag)
        {
            lock (_sperre)
            {
                if (!_client.Connected)
                    return (null, "Nicht verbunden");

                int size = GroesseFuerTyp(tag.Typ);
                byte[] buffer = new byte[size];
                int result = _client.DBRead(tag.Db, tag.Offset, size, buffer);
                if (result != 0)
                    return (null, _client.ErrorText(result));

                string wert = tag.Typ switch
                {
                    "Bool" => S7.GetBitAt(buffer, 0, tag.Bit).ToString(),
                    "Byte" => buffer[0].ToString(CultureInfo.InvariantCulture),
                    "Int" => S7.GetIntAt(buffer, 0).ToString(CultureInfo.InvariantCulture),
                    "DInt" => S7.GetDIntAt(buffer, 0).ToString(CultureInfo.InvariantCulture),
                    "Real" => S7.GetRealAt(buffer, 0).ToString("F3", CultureInfo.InvariantCulture),
                    _ => "?"
                };
                return (wert, null);
            }
        }

        /// <summary>Schreibt einen Wert. Gibt bei Erfolg null zurück, sonst eine Fehlermeldung.</summary>
        public string? Schreiben(TagDef tag, string neuerWert)
        {
            lock (_sperre)
            {
                if (!_client.Connected)
                    return "Nicht verbunden";

                try
                {
                    // Bool ist ein Sonderfall: nur 1 Bit im Byte ändern, deshalb erst
                    // das Byte lesen, damit die anderen 7 Bits erhalten bleiben.
                    if (tag.Typ == "Bool")
                    {
                        bool boolWert = neuerWert.Trim().Equals("true", StringComparison.OrdinalIgnoreCase)
                                        || neuerWert.Trim() == "1";
                        byte[] original = new byte[1];
                        int readResult = _client.DBRead(tag.Db, tag.Offset, 1, original);
                        if (readResult != 0)
                            return _client.ErrorText(readResult);

                        S7.SetBitAt(original, 0, tag.Bit, boolWert);
                        int bitWriteResult = _client.DBWrite(tag.Db, tag.Offset, 1, original);
                        return bitWriteResult == 0 ? null : _client.ErrorText(bitWriteResult);
                    }

                    int size = GroesseFuerTyp(tag.Typ);
                    byte[] buffer = new byte[size];

                    switch (tag.Typ)
                    {
                        case "Byte":
                            buffer[0] = byte.Parse(neuerWert, CultureInfo.InvariantCulture);
                            break;
                        case "Int":
                            S7.SetIntAt(buffer, 0, short.Parse(neuerWert, CultureInfo.InvariantCulture));
                            break;
                        case "DInt":
                            S7.SetDIntAt(buffer, 0, int.Parse(neuerWert, CultureInfo.InvariantCulture));
                            break;
                        case "Real":
                            S7.SetRealAt(buffer, 0, float.Parse(neuerWert, CultureInfo.InvariantCulture));
                            break;
                        default:
                            return $"Unbekannter Typ: {tag.Typ}";
                    }

                    int result = _client.DBWrite(tag.Db, tag.Offset, size, buffer);
                    return result == 0 ? null : _client.ErrorText(result);
                }
                catch (FormatException)
                {
                    return $"'{neuerWert}' passt nicht zum Typ {tag.Typ}";
                }
                catch (OverflowException)
                {
                    return $"'{neuerWert}' liegt außerhalb des Wertebereichs von {tag.Typ}";
                }
            }
        }

        private static int GroesseFuerTyp(string typ) => typ switch
        {
            "Bool" => 1,
            "Byte" => 1,
            "Int" => 2,
            "DInt" => 4,
            "Real" => 4,
            _ => 1
        };
    }
}
