# OSRS Companion portable launcher.
# Checks GitHub for plugin updates, then launches RuneLite with all custom plugins.
# This file is a template — build-package.ps1 stamps in the GitHub repo and copies
# it to dist\launch.ps1. Do not run this file directly; run dist\launch.bat instead.
#
# To change display scale: find $uiScale below and set it to 1.5 or 2.0.

$githubRepo  = "__GITHUB_REPO__"   # stamped by build-package.ps1
$scriptDir   = $PSScriptRoot
$jarPath     = "$scriptDir\extra-plugins.jar"
$versionFile = "$scriptDir\version.txt"
$configPath  = "$env:USERPROFILE\.runelite\profiles2\default-0.properties"
$uiScale     = "1.0"   # ← change to 1.5 or 2.0 for HiDPI / 4K displays

# ── Double-launch guard ──────────────────────────────────────────────────────
# A running RuneLite holds its config file with no sharing, so trying to open
# it exclusively is a reliable proxy for "no client is running". Launching a
# second client while one is already running can wipe all RuneLite settings.
function Test-ConfigFree {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $true }
    try {
        $fs = [System.IO.File]::Open($Path, 'Open', 'ReadWrite', 'None')
        $fs.Close()
        return $true
    }
    catch { return $false }
}

$waited = 0
while (-not (Test-ConfigFree $configPath) -and $waited -lt 20) {
    if ($waited -eq 0) { Write-Host "RuneLite is still running - waiting for it to close..." }
    Start-Sleep -Seconds 1
    $waited++
}
if (-not (Test-ConfigFree $configPath)) {
    Write-Error "RuneLite is already running (its config file is locked). Close it fully and try again."
    Read-Host "Press Enter to exit"
    exit 1
}

# ── Auto-update check ────────────────────────────────────────────────────────
# Downloads a new plugin JAR from GitHub if one has been published since the
# last time this ran. Skips silently on any network error so offline play works.
if ($githubRepo -notlike "*FILL_IN*" -and $githubRepo -ne "__GITHUB_REPO__") {
    try {
        Write-Host "Checking for plugin updates..."
        $apiUrl  = "https://api.github.com/repos/$githubRepo/releases/latest"
        $release = Invoke-RestMethod $apiUrl -TimeoutSec 8 -ErrorAction Stop
        $latest  = $release.tag_name.Trim()
        $current = if (Test-Path $versionFile) { (Get-Content $versionFile -Raw).Trim() } else { "" }

        if ($latest -and $latest -ne $current) {
            $asset = $release.assets | Where-Object { $_.name -eq "extra-plugins.jar" } | Select-Object -First 1
            if ($asset) {
                Write-Host "  Downloading update $latest..."
                $tmp = "$jarPath.tmp"
                Invoke-WebRequest $asset.browser_download_url -OutFile $tmp -TimeoutSec 120 -ErrorAction Stop
                Move-Item $tmp $jarPath -Force
                $latest | Set-Content $versionFile -Encoding ASCII
                Write-Host "  Updated to $latest."
            }
        } else {
            Write-Host "  Plugins are up to date ($current)."
        }
    }
    catch {
        Write-Warning "Update check skipped: $($_.Exception.Message)"
    }
}

# ── Find RuneLite's JRE ──────────────────────────────────────────────────────
$java = "$env:LOCALAPPDATA\RuneLite\jre\bin\java.exe"
if (-not (Test-Path $java)) {
    # Fall back to system Java if RuneLite's bundled JRE isn't present
    $found = Get-Command java -ErrorAction SilentlyContinue
    if ($found) {
        $java = $found.Source
        Write-Host "  Using system Java: $java"
    } else {
        Write-Error "Java not found.`nInstall RuneLite from https://runelite.net (includes a bundled JRE), or install Java 11+."
        Read-Host "Press Enter to exit"
        exit 1
    }
}

# ── First-run settings import ────────────────────────────────────────────────
# Auto-imports bundled settings only when the user has no RuneLite config at all.
# On subsequent runs this block is skipped entirely — settings are left alone.
$bundled = "$scriptDir\settings\default-0.properties"
if ((Test-Path $bundled) -and (-not (Test-Path $configPath))) {
    Write-Host "First run: importing bundled RuneLite settings..."
    New-Item -ItemType Directory -Force -Path (Split-Path $configPath) | Out-Null
    Copy-Item $bundled $configPath -Force
    $profilesJson = "$scriptDir\settings\profiles.json"
    if (Test-Path $profilesJson) {
        Copy-Item $profilesJson (Split-Path $configPath) -Force
    }
    Write-Host "  Settings imported. You can change anything in RuneLite's settings as usual."
}

# ── Enable plugins in the RuneLite config ────────────────────────────────────
# RuneLite doesn't persist dev-loaded plugins' enabled state, so they reset to
# false every session. Force them to true before launch so they're always on.
if (Test-Path $configPath) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $content   = [System.IO.File]::ReadAllText($configPath, $utf8NoBom)
    if (-not [string]::IsNullOrEmpty($content)) {
        foreach ($key in @('osrsmcpbridgeplugin', 'cerberushelperplugin', 'araxxorhelperplugin',
                           'drophighlighterplugin', 'kalphiteflinchplugin', 'farmrunplugin')) {
            if ($content -match "runelite\.$key=") {
                $content = $content -replace "runelite\.$key=\w+", "runelite.$key=true"
            } else {
                $content += "`nrunelite.$key=true`n"
            }
        }
        [System.IO.File]::WriteAllText($configPath, $content, $utf8NoBom)
    }
}

# ── Config backup ────────────────────────────────────────────────────────────
$backupRoot = "$env:USERPROFILE\Documents\osrs-companion-backups"

function Backup-Config {
    param([string]$Reason)
    $w = 0
    while (-not (Test-ConfigFree $configPath) -and $w -lt 15) { Start-Sleep 1; $w++ }
    if (-not (Test-Path $configPath)) { return }
    try {
        New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
        $existing = @(Get-ChildItem $backupRoot -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending)

        $newHash = (Get-FileHash $configPath -Algorithm SHA256).Hash
        if ($existing.Count -gt 0) {
            $prev = Join-Path $existing[0].FullName 'default-0.properties'
            if ((Test-Path $prev) -and (Get-FileHash $prev -Algorithm SHA256).Hash -eq $newHash) { return }
        }

        $stamp  = Get-Date -Format 'yyyyMMdd-HHmmss'
        $target = Join-Path $backupRoot $stamp
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item $configPath (Join-Path $target 'default-0.properties') -Force

        $good = @(Get-ChildItem $backupRoot -Directory | Sort-Object Name -Descending)
        if ($good.Count -gt 10) {
            $good | Select-Object -Skip 10 | ForEach-Object {
                Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
        Write-Host "Config backed up ($Reason)"
    }
    catch { Write-Warning "Config backup failed: $($_.Exception.Message)" }
}

Backup-Config -Reason 'pre-launch'

# ── Launch ───────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "Launching OSRS Companion..."
Write-Host "  Java: $java"
Write-Host "  JAR:  $jarPath"

try {
    & $java "-Dsun.java2d.uiScale=$uiScale" -jar $jarPath --developer-mode
}
finally {
    # Runs even on crash or force-close, so the backup always happens.
    Backup-Config -Reason 'on-close'
}
