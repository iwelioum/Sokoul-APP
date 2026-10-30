# Capture de la fenêtre Sokoul (PC) sans la passer au premier plan : PrintWindow avec PW_RENDERFULLCONTENT,
# qui lit le rendu de la fenêtre même cachée derrière d'autres. Une capture noire = vidéo protégée (DRM) à l'écran
# ou fenêtre réduite : ne pas insister.
#   tools\desktop-shot.ps1 -Out C:\...\home.png [-Process Sokoul] [-Title Sokoul]
param([Parameter(Mandatory = $true)][string]$Out, [string]$Process = 'Sokoul', [string]$Title = 'Sokoul')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class SokoulShot {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
}
"@
[SokoulShot]::SetProcessDPIAware() | Out-Null
$p = Get-Process -Name $Process, java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -eq $Title } | Select-Object -First 1
if (-not $p) { throw "Fenêtre « $Title » introuvable" }
$r = New-Object SokoulShot+RECT
[SokoulShot]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
$w = $r.R - $r.L; $h = $r.B - $r.T
$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$hdc = $g.GetHdc()
[SokoulShot]::PrintWindow($p.MainWindowHandle, $hdc, 2) | Out-Null
$g.ReleaseHdc($hdc); $g.Dispose()
$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
"$Out ${w}x${h}"
