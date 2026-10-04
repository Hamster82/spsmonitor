@echo off
REM ===========================================================================
REM  Ein Doppelklick - und alles laeuft.
REM
REM  1. Beim allerersten Mal wird die Bruecke gebaut (dauert ein paar Minuten)
REM  2. Danach wird sie gestartet, falls sie noch nicht laeuft
REM  3. Zum Schluss oeffnet sich die Oberflaeche im Browser
REM ===========================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

set PORT=8080
set STOPP=0
if /i "%~1"=="stopp" (set STOPP=1) else (if not "%~1"=="" set PORT=%~1)
if /i "%~2"=="stopp" set STOPP=1
set EXE=bin\Release\net8.0\win-x64\publish\SpsBruecke.exe

title SPS Monitor

REM ---------- Nur beenden?  "SPS Monitor starten.bat" stopp ----------
if %STOPP%==1 (
    echo Bruecke auf Port %PORT% wird beendet ...
    powershell -NoProfile -Command "try{Invoke-RestMethod -Method Post -Uri ('http://localhost:%PORT%/api/beenden') -TimeoutSec 5 ^| Out-Null; 'Beendet.'}catch{'Dort laeuft keine Bruecke.'}"
    timeout /t 3 /nobreak >nul
    exit /b 0
)

echo ===========================================
echo   SPS Monitor wird gestartet
echo ===========================================
echo.

REM ---------- 1. Beim ersten Mal bauen ----------
if not exist "%EXE%" (
    echo Die Bruecke ist noch nicht gebaut.
    echo Das passiert jetzt einmalig und dauert einige Minuten.
    echo Dafuer wird eine Internetverbindung gebraucht.
    echo.
    call Kompilieren.bat auto
    if not exist "%EXE%" (
        echo.
        echo Das Bauen hat nicht geklappt - siehe Meldungen oben.
        pause
        exit /b 1
    )
    echo.
    echo Bauen fertig.
    echo.
)

REM ---------- 2. Laeuft die Bruecke schon? ----------
call :PortOffen %PORT%
if !ERRORLEVEL! EQU 0 (
    echo Die Bruecke laeuft bereits auf Port %PORT%.
) else (
    echo Bruecke wird gestartet ...
    start "SPS-Bruecke" /min "%EXE%" %PORT%

    REM Bis zu 30 Sekunden warten, bis sie bereit ist
    set BEREIT=0
    for /L %%i in (1,1,30) do (
        if !BEREIT! EQU 0 (
            call :PortOffen %PORT%
            if !ERRORLEVEL! EQU 0 (
                set BEREIT=1
            ) else (
                timeout /t 1 /nobreak >nul
            )
        )
    )
    if !BEREIT! EQU 0 (
        echo.
        echo Die Bruecke meldet sich nicht auf Port %PORT%.
        echo Moeglicherweise ist der Port belegt. Mit einem anderen versuchen:
        echo     "SPS Monitor starten.bat" 8081
        pause
        exit /b 1
    )
    echo Bruecke ist bereit.
)

REM ---------- 3. Oberflaeche oeffnen ----------
echo.
echo Oberflaeche wird im Browser geoeffnet: http://localhost:%PORT%
start "" "http://localhost:%PORT%"

echo.
echo Fertig. Das Fenster der Bruecke bitte offen lassen -
echo es ist die Verbindung zur Steuerung.
echo.
timeout /t 4 /nobreak >nul
exit /b 0

REM ---------- Hilfsroutine: ist der Port offen? ----------
:PortOffen
powershell -NoProfile -Command "$c=New-Object Net.Sockets.TcpClient; try{$c.Connect('127.0.0.1',%1); $c.Close(); exit 0}catch{exit 1}" >nul 2>nul
exit /b %ERRORLEVEL%
