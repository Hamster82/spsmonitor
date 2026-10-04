@echo off
setlocal
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"
set DOTNET_EXE=%SCRIPT_DIR%.dotnet-portable\dotnet.exe
REM Mit dem Zusatz "auto" laeuft alles ohne Rueckfragen - so ruft der Starter auf.
set AUTO=%1

if not exist "%DOTNET_EXE%" (
    echo ============================================
    echo  Portables .NET SDK wird einmalig geladen
    echo  ^(kein Adminrecht noetig, braucht Internet^)
    echo ============================================
    powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%Installieren.ps1"
    echo.
)

if not exist "%DOTNET_EXE%" (
    echo Download ist fehlgeschlagen - siehe Meldung oben.
    if not "%AUTO%"=="auto" pause
    exit /b 1
)

echo ============================================
echo  SpsBruecke wird als eigenstaendige EXE gebaut
echo ============================================
"%DOTNET_EXE%" publish "%SCRIPT_DIR%SpsBruecke.csproj" -c Release

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Beim Bauen ist ein Fehler aufgetreten - siehe Meldung oben.
    if not "%AUTO%"=="auto" pause
    exit /b 1
)

echo.
echo Fertig! SpsBruecke.exe und oberflaeche.html liegen in:
echo bin\Release\net8.0\win-x64\publish\
echo.

if "%AUTO%"=="auto" exit /b 0

echo Zum Benutzen einfach "SPS Monitor starten.bat" doppelklicken.
echo.
explorer bin\Release\net8.0\win-x64\publish
pause
