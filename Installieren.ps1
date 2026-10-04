$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dotnetDir = Join-Path $scriptDir ".dotnet-portable"
$dotnetExe = Join-Path $dotnetDir "dotnet.exe"

if (Test-Path $dotnetExe) {
    Write-Host "Portables .NET SDK bereits vorhanden."
    exit 0
}

Write-Host "Lade portables .NET SDK herunter (offizielles Microsoft-Skript,"
Write-Host "kein Adminrecht noetig, installiert nur in diesen Ordner) ..."

$installScript = Join-Path $env:TEMP "dotnet-install.ps1"
Invoke-WebRequest -Uri "https://dot.net/v1/dotnet-install.ps1" -OutFile $installScript

# -Channel 8.0 passt zum TargetFramework (net8.0-windows) im Projekt.
# -InstallDir legt alles nur lokal in .dotnet-portable ab, nichts Systemweites.
& $installScript -Channel 8.0 -InstallDir $dotnetDir

if (-not (Test-Path $dotnetExe)) {
    Write-Host "Download/Installation ist fehlgeschlagen."
    exit 1
}

Write-Host "Portables .NET SDK ist bereit."
