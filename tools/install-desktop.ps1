# Installe (ou met à jour) Sokoul pour Windows à partir de l'image construite par « gradlew :desktop:createDistributable ».
#   - copie dans %LOCALAPPDATA%\Programs\Sokoul (aucun droit administrateur) ;
#   - raccourcis « Sokoul » sur le Bureau et dans le menu Démarrer ;
#   - « Désinstaller Sokoul » dans le menu Démarrer (retire l'app et les raccourcis, garde les données %APPDATA%\Sokoul).
$ErrorActionPreference = 'Stop'
$image = Join-Path $env:LOCALAPPDATA 'SokoulBuild\desktop\compose\binaries\main\app\Sokoul'
if (-not (Test-Path "$image\Sokoul.exe")) { throw "Image introuvable : lancez d'abord gradlew :desktop:createDistributable" }
$target = Join-Path $env:LOCALAPPDATA 'Programs\Sokoul'
Get-Process Sokoul -ErrorAction SilentlyContinue | Stop-Process -Force
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
New-Item -ItemType Directory -Force $target | Out-Null
Copy-Item "$image\*" $target -Recurse -Force
$icon = Join-Path $PSScriptRoot '..\app-android\desktop\icon\sokoul.ico'
Copy-Item $icon "$target\sokoul.ico" -Force

$desktop = [Environment]::GetFolderPath('Desktop')
$menu = Join-Path ([Environment]::GetFolderPath('Programs')) 'Sokoul'
New-Item -ItemType Directory -Force $menu | Out-Null

$uninstall = @"
@echo off
taskkill /im Sokoul.exe /f >nul 2>&1
del "$desktop\Sokoul.lnk" >nul 2>&1
rmdir /s /q "$menu"
rmdir /s /q "$target"
echo Sokoul a ete desinstalle (vos donnees restent dans %APPDATA%\Sokoul).
pause
"@
Set-Content -Path "$target\Desinstaller Sokoul.cmd" -Value $uninstall -Encoding ASCII

$shell = New-Object -ComObject WScript.Shell
foreach ($lnkPath in @("$desktop\Sokoul.lnk", "$menu\Sokoul.lnk")) {
    $s = $shell.CreateShortcut($lnkPath)
    $s.TargetPath = "$target\Sokoul.exe"
    $s.WorkingDirectory = $target
    $s.IconLocation = "$target\sokoul.ico,0"
    $s.Description = 'Sokoul TV pour Windows'
    $s.Save()
}
$u = $shell.CreateShortcut("$menu\Désinstaller Sokoul.lnk")
$u.TargetPath = "$target\Desinstaller Sokoul.cmd"
$u.IconLocation = "$target\sokoul.ico,0"
$u.Save()
"Sokoul installé dans $target (raccourcis : Bureau et menu Démarrer)"
