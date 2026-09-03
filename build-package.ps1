# build-package.ps1
# Run this on your dev machine to build and publish a new version of the OSRS Companion.
# Creates/updates osrs-companion\dist\ and optionally publishes a GitHub release.
#
# Requirements on YOUR machine:
#   - WSL (Ubuntu) with Gradle working (same as your existing setup)
#   - gh CLI installed and authenticated: https://cli.github.com
#     Run once: gh auth login
#
# Usage:
#   .\build-package.ps1                        <- build dist\ only
#   .\build-package.ps1 -Publish              <- build + publish GitHub release

param(
    [switch]$Publish,
    [string]$Repo = "tjennings5/osrs-companion"
)

$ErrorActionPreference = "Stop"

# Derive paths relative to this script so it works on any machine, not just Tyler's.
$winPluginDir  = "$PSScriptRoot\plugin"
$driveLetter   = $winPluginDir.Substring(0, 1).ToLower()
$wslProjectDir = '/mnt/' + $driveLetter + '/' + $winPluginDir.Substring(3).Replace('\', '/')
$distDir       = "$PSScriptRoot\dist"
$configPath    = "$env:USERPROFILE\.runelite\profiles2\default-0.properties"
$profileDir    = Split-Path $configPath -Parent
$version       = "v" + (Get-Date -Format 'yyyyMMdd-HHmm')

# --- Build fat JAR via existing shadowJar task ---
Write-Host "Building fat JAR (version $version)..."
wsl.exe -d Ubuntu -e bash -lc "cd '$wslProjectDir' && ./gradlew -q shadowJar"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle shadowJar failed - see output above."
    exit 1
}

$jar = Get-ChildItem "$winPluginDir\build\libs\*-all.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    Write-Error "No *-all.jar found in plugin\build\libs\ - Gradle shadowJar may have failed."
    exit 1
}
$sizeMB = [math]::Round($jar.Length / 1048576, 1)
Write-Host "  Built: $($jar.Name) ($sizeMB MB)"

# --- Assemble dist\ ---
Write-Host "Assembling dist\..."
New-Item -ItemType Directory -Force -Path "$distDir\settings" | Out-Null

Copy-Item $jar.FullName -Destination "$distDir\extra-plugins.jar" -Force

$version | Set-Content "$distDir\version.txt" -Encoding ASCII

if (Test-Path $configPath) {
    Copy-Item $configPath -Destination "$distDir\settings\default-0.properties" -Force
    Write-Host "  Bundled RuneLite settings snapshot."
} else {
    Write-Warning "  RuneLite config not found at $configPath - no settings bundled."
}
foreach ($f in @('profiles.json')) {
    $src = Join-Path $profileDir $f
    if (Test-Path $src) { Copy-Item $src "$distDir\settings\" -Force }
}

# Portable launcher - stamp in the GitHub repo name
$launcherSrc = "$PSScriptRoot\companion-launch.ps1"
if (-not (Test-Path $launcherSrc)) {
    Write-Error "companion-launch.ps1 not found next to build-package.ps1"
    exit 1
}
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$launcherContent = [System.IO.File]::ReadAllText($launcherSrc, $utf8NoBom)
$launcherContent = $launcherContent -replace '__GITHUB_REPO__', $Repo
[System.IO.File]::WriteAllText("$distDir\launch.ps1", $launcherContent, $utf8NoBom)

# Single entry point - RuneLite (Extra Plugins).bat calls launch.ps1
@'
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch.ps1"
'@ | Set-Content "$distDir\RuneLite (Extra Plugins).bat" -Encoding ASCII

# README.txt inside the dist folder
$readme = @"
OSRS Companion $version
=======================

GETTING STARTED:
1. Install RuneLite from https://runelite.net and launch it at least once.
2. Put this folder anywhere you like - it stays here permanently.
3. Double-click "RuneLite (Extra Plugins).bat".
   First run shows a setup screen to create a Desktop shortcut and import settings.
   After that it goes straight to the launcher every time.
4. To pin to taskbar: right-click the Desktop shortcut, select Pin to taskbar.

PLUGINS INCLUDED:
  Farm Run Guide, Drop Highlighter, Cerberus Helper,
  Araxxor Helper, Kalphite Flinch Timer, OSRS MCP Bridge,
  Sailing Steering Arrow

AUTO-UPDATES:
The launcher checks for plugin updates every time you start it.
If a new version is available it downloads silently before launching.
You never need a new zip - just keep using the same shortcut.

If login fails after a game update: launch RuneLite normally once
(it will update itself), then use your shortcut as usual.

DISPLAY SCALE:
Default scale is 2.0 (HiDPI). Adjust with the slider in the launcher.
"@
[System.IO.File]::WriteAllText("$distDir\README.txt", $readme, $utf8NoBom)

Write-Host ""
Write-Host "dist\ assembled at: $distDir"

# --- Publish GitHub release ---
if (-not $Publish) {
    Write-Host ""
    Write-Host "To publish so other computers auto-update, run:"
    Write-Host "  .\build-package.ps1 -Publish"
    return
}

Write-Host ""
Write-Host "Publishing GitHub release $version to $Repo..."

# Zip the full dist\ - this is the one file users download
$zipPath = "$PSScriptRoot\osrs-companion-setup.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path "$distDir\*" -DestinationPath $zipPath
Write-Host "  Zipped dist\ -> osrs-companion-setup.zip"

gh release create $version `
    "$zipPath#osrs-companion-setup.zip" `
    --repo $Repo `
    --title "OSRS Companion $version" `
    --notes "Download **osrs-companion-setup.zip**, extract it anywhere, and double-click **launch.bat**.

Already set up? Your launcher will auto-download this update on next launch. Nothing to do." `
    --latest

if ($LASTEXITCODE -eq 0) {
    Write-Host "Published. Users download osrs-companion-setup.zip; existing launchers auto-update from the zip."
} else {
    Write-Warning "GitHub release failed. You can still distribute osrs-companion-setup.zip manually."
}
