# Récupère libVLC (VLC 3.0.21 win64, archive officielle VideoLAN, empreinte SHA-256 vérifiée) pour l'app PC Sokoul.
# Résultat : app-android/desktop/resources/windows/vlc/ (libvlc.dll, libvlccore.dll, plugins/), ignoré par Git.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$version = '3.0.21'
$url = "https://download.videolan.org/pub/videolan/vlc/$version/win64/vlc-$version-win64.zip"
$tmp = Join-Path $env:TEMP "sokoul-vlc-$version"
New-Item -ItemType Directory -Force $tmp | Out-Null
Invoke-WebRequest -Uri $url -OutFile "$tmp\vlc.zip" -UseBasicParsing
Invoke-WebRequest -Uri "$url.sha256" -OutFile "$tmp\vlc.sha256" -UseBasicParsing
$expected = ((Get-Content "$tmp\vlc.sha256" -Raw).Trim() -split '\s+')[0].ToLower()
$actual = (Get-FileHash "$tmp\vlc.zip" -Algorithm SHA256).Hash.ToLower()
if ($expected -ne $actual) { throw "Empreinte SHA-256 différente : archive VLC refusée" }
Expand-Archive -Path "$tmp\vlc.zip" -DestinationPath "$tmp\x" -Force
$src = Get-ChildItem "$tmp\x" -Directory | Select-Object -First 1
$dst = Join-Path $PSScriptRoot '..\app-android\desktop\resources\windows\vlc'
New-Item -ItemType Directory -Force $dst | Out-Null
Copy-Item "$($src.FullName)\libvlc.dll", "$($src.FullName)\libvlccore.dll" $dst
Copy-Item "$($src.FullName)\plugins" $dst -Recurse -Force
Remove-Item $tmp -Recurse -Force
"libVLC $version prêt dans $dst"
