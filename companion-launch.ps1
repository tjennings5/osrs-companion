# OSRS Companion portable launcher.
# Checks GitHub for plugin updates, shows a settings UI, then launches RuneLite.
# This file is a template - build-package.ps1 stamps in the GitHub repo and copies
# it to dist\launch.ps1. Do not run this file directly; run dist\launch.bat instead.

$githubRepo   = "__GITHUB_REPO__"   # stamped by build-package.ps1
$scriptDir    = $PSScriptRoot
$jarPath      = "$scriptDir\extra-plugins.jar"
$versionFile  = "$scriptDir\version.txt"
$configPath   = "$env:USERPROFILE\.runelite\profiles2\default-0.properties"
$settingsFile = "$scriptDir\launcher-settings.txt"

# --- Load saved launcher settings ---
$uiScale = "2.0"
if (Test-Path $settingsFile) {
    foreach ($line in (Get-Content $settingsFile)) {
        if ($line -match '^uiScale=(.+)$') { $uiScale = $Matches[1] }
    }
}

# --- Double-launch guard ---
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

# --- Auto-update check ---
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

# --- Find RuneLite's JRE ---
$java = "$env:LOCALAPPDATA\RuneLite\jre\bin\java.exe"
if (-not (Test-Path $java)) {
    $found = Get-Command java -ErrorAction SilentlyContinue
    if ($found) {
        $java = $found.Source
    } else {
        Write-Error "Java not found.`nInstall RuneLite from https://runelite.net, or install Java 11+."
        Read-Host "Press Enter to exit"
        exit 1
    }
}

# --- Desktop shortcut (first run only) ---
$shortcutFlag = "$scriptDir\.shortcut-created"
if (-not (Test-Path $shortcutFlag)) {
    try {
        $runeliteExe = "$env:LOCALAPPDATA\RuneLite\RuneLite.exe"
        $shell = New-Object -ComObject WScript.Shell
        $lnk   = $shell.CreateShortcut("$env:USERPROFILE\Desktop\OSRS Companion.lnk")
        $lnk.TargetPath       = "powershell.exe"
        $lnk.Arguments        = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptDir\launch.ps1`""
        $lnk.WorkingDirectory = $scriptDir
        $lnk.Description      = "OSRS Companion - RuneLite with custom plugins"
        if (Test-Path $runeliteExe) { $lnk.IconLocation = "$runeliteExe,0" }
        $lnk.Save()
        $null | Set-Content $shortcutFlag
        Write-Host "Created 'OSRS Companion' shortcut on your Desktop."
    }
    catch { Write-Warning "Could not create Desktop shortcut: $($_.Exception.Message)" }
}

# --- Launcher UI ---
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$colBg      = [System.Drawing.Color]::FromArgb(18, 14, 8)
$colSurface = [System.Drawing.Color]::FromArgb(30, 24, 14)
$colBorder  = [System.Drawing.Color]::FromArgb(60, 48, 24)
$colGold    = [System.Drawing.Color]::FromArgb(212, 160, 48)
$colGoldHi  = [System.Drawing.Color]::FromArgb(236, 185, 72)
$colText    = [System.Drawing.Color]::FromArgb(237, 228, 204)
$colMuted   = [System.Drawing.Color]::FromArgb(140, 120, 80)
$fontUI     = New-Object System.Drawing.Font("Segoe UI", 9)
$fontLabel  = New-Object System.Drawing.Font("Segoe UI", 8)
$fontTitle  = New-Object System.Drawing.Font("Segoe UI Semibold", 11)
$fontBtn    = New-Object System.Drawing.Font("Segoe UI Semibold", 10)

$form = New-Object System.Windows.Forms.Form
$form.Text            = "OSRS Companion"
$form.ClientSize      = New-Object System.Drawing.Size(320, 230)
$form.StartPosition   = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox     = $false
$form.MinimizeBox     = $false
$form.BackColor       = $colBg

# Set RuneLite icon if available
$runeliteExe = "$env:LOCALAPPDATA\RuneLite\RuneLite.exe"
if (Test-Path $runeliteExe) {
    try { $form.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon($runeliteExe) } catch {}
}

# Title
$lblTitle = New-Object System.Windows.Forms.Label
$lblTitle.Text      = "OSRS Companion"
$lblTitle.Font      = $fontTitle
$lblTitle.ForeColor = $colGold
$lblTitle.BackColor = $colBg
$lblTitle.Location  = New-Object System.Drawing.Point(20, 18)
$lblTitle.AutoSize  = $true
$form.Controls.Add($lblTitle)

# Version
$verText = if (Test-Path $versionFile) { (Get-Content $versionFile -Raw).Trim() } else { "" }
$lblVer = New-Object System.Windows.Forms.Label
$lblVer.Text      = $verText
$lblVer.Font      = $fontLabel
$lblVer.ForeColor = $colMuted
$lblVer.BackColor = $colBg
$lblVer.Location  = New-Object System.Drawing.Point(22, 42)
$lblVer.AutoSize  = $true
$form.Controls.Add($lblVer)

# Divider panel
$divider = New-Object System.Windows.Forms.Panel
$divider.BackColor = $colBorder
$divider.Location  = New-Object System.Drawing.Point(20, 64)
$divider.Size      = New-Object System.Drawing.Size(280, 1)
$form.Controls.Add($divider)

# UI Scale label
$lblScale = New-Object System.Windows.Forms.Label
$lblScale.Text      = "Display Scale"
$lblScale.Font      = $fontLabel
$lblScale.ForeColor = $colMuted
$lblScale.BackColor = $colBg
$lblScale.Location  = New-Object System.Drawing.Point(20, 80)
$lblScale.AutoSize  = $true
$form.Controls.Add($lblScale)

# Scale textbox (shows current value, allows typing)
$tbScale = New-Object System.Windows.Forms.TextBox
$tbScale.Font      = $fontUI
$tbScale.ForeColor = $colText
$tbScale.BackColor = $colSurface
$tbScale.BorderStyle = "FixedSingle"
$tbScale.Location  = New-Object System.Drawing.Point(238, 76)
$tbScale.Size      = New-Object System.Drawing.Size(62, 22)
$tbScale.Text      = $uiScale
$tbScale.TextAlign = "Center"
$form.Controls.Add($tbScale)

# Scale slider (1.0 to 3.0 in 0.1 steps, stored as int 10-30)
$initialTick = [int]([double]$uiScale * 10)
$slider = New-Object System.Windows.Forms.TrackBar
$slider.Minimum       = 10
$slider.Maximum       = 30
$slider.TickFrequency = 5
$slider.SmallChange   = 1
$slider.LargeChange   = 5
$slider.Value         = [Math]::Max(10, [Math]::Min(30, $initialTick))
$slider.BackColor     = $colBg
$slider.Location      = New-Object System.Drawing.Point(14, 96)
$slider.Size          = New-Object System.Drawing.Size(292, 36)
$form.Controls.Add($slider)

# Sync slider -> textbox
$slider.Add_ValueChanged({
    $tbScale.Text = ($slider.Value / 10.0).ToString("0.0")
})

# Sync textbox -> slider on leave
$tbScale.Add_Leave({
    $parsed = 0.0
    if ([double]::TryParse($tbScale.Text, [ref]$parsed)) {
        $clamped = [Math]::Max(1.0, [Math]::Min(3.0, $parsed))
        $slider.Value = [int]($clamped * 10)
        $tbScale.Text = $clamped.ToString("0.0")
    } else {
        $tbScale.Text = ($slider.Value / 10.0).ToString("0.0")
    }
})

# Import settings checkbox (only shown if bundled settings exist and config is present)
$bundled = "$scriptDir\settings\default-0.properties"
$chkImport = $null
$importOffset = 0
if (Test-Path $bundled) {
    $chkImport = New-Object System.Windows.Forms.CheckBox
    $chkImport.Text      = "Import bundled RuneLite settings"
    $chkImport.Font      = $fontLabel
    $chkImport.ForeColor = $colMuted
    $chkImport.BackColor = $colBg
    $chkImport.Location  = New-Object System.Drawing.Point(20, 142)
    $chkImport.AutoSize  = $true
    $chkImport.Checked   = (-not (Test-Path $configPath))  # default on if no config exists
    $form.Controls.Add($chkImport)
    $importOffset = 28
}

# Launch button
$btnY    = 142 + $importOffset
$formH   = 196 + $importOffset
$btnLaunch = New-Object System.Windows.Forms.Button
$btnLaunch.Text      = "Launch"
$btnLaunch.Font      = $fontBtn
$btnLaunch.ForeColor = $colBg
$btnLaunch.BackColor = $colGold
$btnLaunch.FlatStyle = "Flat"
$btnLaunch.FlatAppearance.BorderSize = 0
$btnLaunch.Location  = New-Object System.Drawing.Point(20, $btnY)
$btnLaunch.Size      = New-Object System.Drawing.Size(280, 38)
$btnLaunch.Cursor    = [System.Windows.Forms.Cursors]::Hand
$form.Controls.Add($btnLaunch)

# Resize form to fit
$form.ClientSize = New-Object System.Drawing.Size(320, $formH)

# Hover effect on launch button
$btnLaunch.Add_MouseEnter({ $btnLaunch.BackColor = $colGoldHi })
$btnLaunch.Add_MouseLeave({ $btnLaunch.BackColor = $colGold })

$launched = $false
$btnLaunch.Add_Click({
    $script:uiScale = ($slider.Value / 10.0).ToString("0.0")
    "uiScale=$($script:uiScale)" | Set-Content $settingsFile -Encoding ASCII
    $script:launched = $true
    $form.Close()
})

# Allow Enter key to launch
$form.AcceptButton = $btnLaunch
$form.ShowDialog() | Out-Null

if (-not $launched) { exit 0 }   # user closed the window

# --- First-run settings import ---
if ($chkImport -and $chkImport.Checked) {
    Write-Host "Importing bundled RuneLite settings..."
    New-Item -ItemType Directory -Force -Path (Split-Path $configPath) | Out-Null
    Copy-Item $bundled $configPath -Force
    $profilesJson = "$scriptDir\settings\profiles.json"
    if (Test-Path $profilesJson) { Copy-Item $profilesJson (Split-Path $configPath) -Force }
    Write-Host "  Settings imported."
}

# --- Enable plugins in the RuneLite config ---
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

# --- Config backup ---
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

# --- Launch ---
Write-Host "Launching OSRS Companion (scale $uiScale)..."

try {
    & $java -ea "-Dsun.java2d.uiScale=$uiScale" -jar $jarPath --developer-mode
}
finally {
    Backup-Config -Reason 'on-close'
}
