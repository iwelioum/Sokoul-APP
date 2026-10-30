# Outils adb pour essayer Sokoul sur la tablette (ou un autre appareil).
#   .\tools\adb-tablet.ps1 shot [-Out capture.png] [-Cmd "input tap 94 1180"]   capture (après une commande shell facultative)
#   .\tools\adb-tablet.ps1 install                                              installe la version debug compilée
#   .\tools\adb-tablet.ps1 launch                                               réveille l'écran et ouvre l'application
#   .\tools\adb-tablet.ps1 rotate landscape|portrait|restore                    rotation forcée, puis remise à l'état noté
# Appareil : variable SOKOUL_DEVICE, sinon le premier appareil adb connecté (mDNS compris).
param(
    [Parameter(Position = 0)][ValidateSet('shot', 'install', 'launch', 'rotate', 'serial')][string]$Action = 'shot',
    [Parameter(Position = 1)][string]$Arg,
    [string]$Cmd,
    [string]$Out = "$env:TEMP\sokoul-shot.png"
)
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.sokoul.iptv.debug'
$serial = $env:SOKOUL_DEVICE
if (-not $serial) {
    $serial = (& $adb devices | Select-String '\tdevice$' | Select-Object -First 1) -replace '\s+device$', ''
}
if (-not $serial) { throw 'Aucun appareil adb : activez le débogage sans fil puis « adb connect »' }
$d = '-s', $serial
$state = "$env:TEMP\sokoul-rotation.txt"

switch ($Action) {
    'serial' { $serial }
    'shot' {
        if ($Cmd) { & $adb @d shell $Cmd; Start-Sleep -Milliseconds 1500 }
        & $adb @d shell screencap -p /data/local/tmp/sokoul.png
        & $adb @d pull /data/local/tmp/sokoul.png $Out | Out-Null
        $Out
    }
    'install' {
        & $adb @d install -r "$env:LOCALAPPDATA\SokoulBuild\app\outputs\apk\debug\app-debug.apk"
    }
    'launch' {
        & $adb @d shell "input keyevent KEYCODE_WAKEUP; am start -n $pkg/com.sokoul.iptv.MainActivity"
    }
    'rotate' {
        if ($Arg -eq 'restore') {
            if (Test-Path $state) {
                $acc, $user = (Get-Content $state) -split ' '
                & $adb @d shell "settings put system user_rotation $user; settings put system accelerometer_rotation $acc"
                Remove-Item $state
            }
        } else {
            if (-not (Test-Path $state)) {
                $acc = (& $adb @d shell settings get system accelerometer_rotation).Trim()
                $user = (& $adb @d shell settings get system user_rotation).Trim()
                Set-Content $state "$acc $user"
            }
            $r = if ($Arg -eq 'landscape') { 1 } else { 0 }
            & $adb @d shell "settings put system accelerometer_rotation 0; settings put system user_rotation $r"
        }
    }
}
