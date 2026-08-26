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
#   .\build-package.ps1                         <- build dist\ only
#   .\build-package.ps1 -Publish               <- build + publish GitHub release
#   .\build-package.ps1 -Publish -Repo owner/repo  <- override GitHub repo
#
# The recipient NEVER needs WSL, Gradle, or this folder.
# They just need the dist\ folder (or a zip of it) and RuneLite installed.

param(
    [switch]$Publish,
    # ↓ Set this to your GitHub username/repo once, or pass -Repo each time.
    [string]$Repo = "tjennings5/osrs-companion"
)

$ErrorActionPreference = "Stop"

# Derive paths relative to this script so it works on any machine, not just Tyler's.
$winPluginDir  = "$PSScriptRoot\plugin"
$wslProjectDir = ($winPluginDir -replace '^([A-Za-z]):\\', { '/mnt/' + $_.Groups[1].Value.ToLower() + '/' }) -replace '\\', '/'
$distDir       = "$PSScriptRoot\dist"
$configPath    = "$env:USERPROFILE\.runelite\profiles2\default-0.properties"
$profileDir    = Split-Path $configPath -Parent
$version       = "v" + (Get-Date -Format 'yyyyMMdd-HHmm')

# ── Build fat JAR via existing shadowJar task ────────────────────────────────
Write-Host "Building fat JAR (version $version)..."
Write-Host "  (using your existing runelite-plugin\build.gradle - nothing changed there)"
$output = wsl.exe -d Ubuntu -e bash -lc "cd '$wslProjectDir' && ./gradlew -q shadowJar" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle shadowJar failed:`n$($output -join "`n")"
    exit 1
}

$jar = Get-ChildItem "$winPluginDir\build\libs\*-all.jar" |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    Write-Error "No *-all.jar found in plugin\build\libs\  — Gradle shadowJar may have failed. Check the output above."
    exit 1
}
Write-Host "  Built: $($jar.Name) ($([math]::Round($jar.Length / 1MB, 1)) MB)"

# ── Assemble dist\ ───────────────────────────────────────────────────────────
Write-Host "Assembling dist\..."
New-Item -ItemType Directory -Force -Path "$distDir\settings" | Out-Null

# Fat JAR
Copy-Item $jar.FullName -Destination "$distDir\osrs-mcp-bridge-all.jar" -Force

# Version tag (launcher compares this to GitHub to decide whether to update)
$version | Set-Content "$distDir\version.txt" -Encoding ASCII

# Settings snapshot (recipient can choose to import on first run)
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

# Portable launcher (copy from this folder, stamp in the GitHub repo)
$launcherSrc = "$PSScriptRoot\companion-launch.ps1"
if (-not (Test-Path $launcherSrc)) {
    Write-Error "companion-launch.ps1 not found next to build-package.ps1 — they must be in the same folder."
    exit 1
}
$launcherContent = [System.IO.File]::ReadAllText($launcherSrc, [System.Text.UTF8Encoding]::new($false))
$launcherContent = $launcherContent -replace '__GITHUB_REPO__', $Repo
[System.IO.File]::WriteAllText("$distDir\launch.ps1", $launcherContent, [System.Text.UTF8Encoding]::new($false))

# launch.bat (double-click entry point)
@'
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch.ps1"
pause
'@ | Set-Content "$distDir\launch.bat" -Encoding ASCII

# README
@"
OSRS Companion $version
=======================

SETUP (one time):
1. Install RuneLite from https://runelite.net and launch it at least once.
2. Put this folder anywhere you like.
3. Double-click launch.bat.
4. First run: choose whether to import the bundled settings
   (hotkeys, bank tags, UI layout, etc.). Say No to keep your own RuneLite settings.

PLUGINS INCLUDED:
  Farm Run Guide, Drop Highlighter, Cerberus Helper,
  Araxxor Helper, Kalphite Flinch Timer, OSRS MCP Bridge

AUTO-UPDATES:
The launcher checks for plugin updates every time you start it.
If a new version is available it downloads silently before launching.
You never need a new zip — just keep using the same launch.bat.

If login fails after a game update: launch RuneLite normally once
(it will update itself), then use launch.bat as usual.

DISPLAY SCALE:
Edit launch.ps1 and change uiScale=1.0 to match your monitor
(1.5 for 150% Windows scaling, 2.0 for 4K/200% scaling).
"@ | Set-Content "$distDir\README.txt" -Encoding UTF8

Write-Host ""
Write-Host "dist\ assembled at: $distDir"
Write-Host "Your existing runelite-plugin\ setup is untouched."

# ── Publish GitHub release (optional) ───────────────────────────────────────
if (-not $Publish) {
    Write-Host ""
    Write-Host "To publish so other computers auto-update, run:"
    Write-Host "  .\build-package.ps1 -Publish -Repo YOUR_GITHUB_USERNAME/osrs-companion"
    Write-Host ""
    Write-Host "First time? Create the repo at https://github.com/new (can be private),"
    Write-Host "then run: gh auth login"
    return
}

if ($Repo -like "FILL_IN*") {
    Write-Error "Set your GitHub repo with -Repo owner/repo before publishing."
    exit 1
}

Write-Host ""
Write-Host "Publishing GitHub release $version to $Repo..."
gh release create $version "$distDir\osrs-mcp-bridge-all.jar" `
    --repo $Repo `
    --title "OSRS Companion $version" `
    --notes "Plugin update $version" `
    --latest

if ($LASTEXITCODE -eq 0) {
    Write-Host "Published. All other computers will auto-download this update on next launch."
} else {
    Write-Warning "GitHub release failed. You can still distribute dist\ manually as a zip."
}
