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
$uiScale   = "2.0"
$skipScale = $false
if (Test-Path $settingsFile) {
    foreach ($line in (Get-Content $settingsFile)) {
        if ($line -match '^uiScale=(.+)$')  { $uiScale  = $Matches[1] }
        if ($line -match '^skipScale=true$') { $skipScale = $true }
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
if ($githubRepo -notlike "*FILL_IN*" -and $githubRepo -notlike "*GITHUB_REPO*") {
    $logFile = "$scriptDir\update.log"
    function Write-Log {
        param([string]$Msg)
        $line = "$(Get-Date -Format 'HH:mm:ss') $Msg"
        Write-Host $line
        Add-Content $logFile $line -Encoding UTF8
    }
    try {
        Write-Log "Checking for plugin updates..."
        $apiUrl  = "https://api.github.com/repos/$githubRepo/releases/latest"
        $release = Invoke-RestMethod $apiUrl -TimeoutSec 8 -ErrorAction Stop
        $latest  = $release.tag_name.Trim()
        $current = if (Test-Path $versionFile) { (Get-Content $versionFile -Raw).Trim() } else { "" }

        Write-Log "  Latest: $latest  Local: $(if ($current) { $current } else { '(none)' })"
        if ($latest -and $latest -ne $current) {
            $assetNames = ($release.assets | ForEach-Object { $_.name }) -join ", "
            Write-Log "  Release assets: $assetNames"
            $asset = $release.assets | Where-Object { $_.name -eq "osrs-companion-setup.zip" } | Select-Object -First 1
            if ($asset) {
                Write-Log "  Downloading update $latest..."
                $tmpZip = "$scriptDir\update-tmp.zip"
                $tmpDir = "$scriptDir\update-extract"
                Invoke-WebRequest $asset.browser_download_url -OutFile $tmpZip -TimeoutSec 120 -ErrorAction Stop
                Write-Log "  Downloaded ($([math]::Round((Get-Item $tmpZip).Length / 1MB, 1)) MB). Extracting..."
                if (Test-Path $tmpDir) { Remove-Item $tmpDir -Recurse -Force }
                $prev = $ProgressPreference
                $ProgressPreference = 'SilentlyContinue'
                Expand-Archive $tmpZip -DestinationPath $tmpDir -Force
                $ProgressPreference = $prev
                $newJar = Join-Path $tmpDir "extra-plugins.jar"
                if (Test-Path $newJar) {
                    Copy-Item $newJar $jarPath -Force -ErrorAction Stop
                    Remove-Item $newJar -Force -ErrorAction SilentlyContinue
                    $latest | Set-Content $versionFile -Encoding ASCII
                    Write-Log "  Updated to $latest."
                } else {
                    $extracted = (Get-ChildItem $tmpDir -Recurse | ForEach-Object { $_.Name }) -join ", "
                    Write-Log "  WARNING: extra-plugins.jar not found in zip. Contents: $extracted"
                }
                Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
                Remove-Item $tmpZip -Force -ErrorAction SilentlyContinue
            } else {
                Write-Log "  WARNING: osrs-companion-setup.zip not found in release assets."
            }
        } else {
            Write-Log "  Plugins are up to date ($current)."
        }
    }
    catch {
        $msg = "Update check failed: $($_.Exception.Message)"
        Write-Log "  ERROR: $msg"
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

# --- WinForms setup ---
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

$shortcutFlag   = "$scriptDir\.setup-done"
$bundled        = "$scriptDir\settings\default-0.properties"
$isFirstRun     = -not (Test-Path $shortcutFlag)
$importSettings = $false

function Get-RuneLiteIcon {
    $exe = "$env:LOCALAPPDATA\RuneLite\RuneLite.exe"
    if (Test-Path $exe) {
        try { return [System.Drawing.Icon]::ExtractAssociatedIcon($exe) } catch {}
    }
    return $null
}

# --- First-run setup dialog ---
if ($isFirstRun) {
    $frmSetup = New-Object System.Windows.Forms.Form
    $frmSetup.Text            = "OSRS Companion"
    $frmSetup.StartPosition   = "CenterScreen"
    $frmSetup.FormBorderStyle = "FixedDialog"
    $frmSetup.MaximizeBox     = $false
    $frmSetup.MinimizeBox     = $false
    $frmSetup.BackColor       = $colBg
    $rlIcon = Get-RuneLiteIcon
    if ($rlIcon) { $frmSetup.Icon = $rlIcon }

    $lbl1 = New-Object System.Windows.Forms.Label
    $lbl1.Text = "OSRS Companion"; $lbl1.Font = $fontTitle; $lbl1.ForeColor = $colGold
    $lbl1.BackColor = $colBg; $lbl1.Location = New-Object System.Drawing.Point(20, 18)
    $lbl1.AutoSize = $true; $frmSetup.Controls.Add($lbl1)

    $lbl2 = New-Object System.Windows.Forms.Label
    $lbl2.Text = "First-time setup"; $lbl2.Font = $fontLabel; $lbl2.ForeColor = $colMuted
    $lbl2.BackColor = $colBg; $lbl2.Location = New-Object System.Drawing.Point(22, 42)
    $lbl2.AutoSize = $true; $frmSetup.Controls.Add($lbl2)

    $div1 = New-Object System.Windows.Forms.Panel
    $div1.BackColor = $colBorder; $div1.Location = New-Object System.Drawing.Point(20, 64)
    $div1.Size = New-Object System.Drawing.Size(280, 1); $frmSetup.Controls.Add($div1)

    $chkShortcut = New-Object System.Windows.Forms.CheckBox
    $chkShortcut.Text = "Create Desktop shortcut"
    $chkShortcut.Font = $fontUI; $chkShortcut.ForeColor = $colText; $chkShortcut.BackColor = $colBg
    $chkShortcut.Location = New-Object System.Drawing.Point(20, 82); $chkShortcut.AutoSize = $true
    $chkShortcut.Checked = $true; $frmSetup.Controls.Add($chkShortcut)

    $chkSettings = $null
    $settingsOffset = 0
    if (Test-Path $bundled) {
        $chkSettings = New-Object System.Windows.Forms.CheckBox
        $chkSettings.Text = "Import bundled RuneLite settings"
        $chkSettings.Font = $fontUI; $chkSettings.ForeColor = $colText; $chkSettings.BackColor = $colBg
        $chkSettings.Location = New-Object System.Drawing.Point(20, 114); $chkSettings.AutoSize = $true
        $chkSettings.Checked = (-not (Test-Path $configPath))
        $frmSetup.Controls.Add($chkSettings)
        $settingsOffset = 32
    }

    $btnContinue = New-Object System.Windows.Forms.Button
    $btnContinue.Text = "Continue"
    $btnContinue.Font = $fontBtn; $btnContinue.ForeColor = $colBg; $btnContinue.BackColor = $colGold
    $btnContinue.FlatStyle = "Flat"; $btnContinue.FlatAppearance.BorderSize = 0
    $btnContinue.Location = New-Object System.Drawing.Point(20, (114 + $settingsOffset))
    $btnContinue.Size = New-Object System.Drawing.Size(280, 38)
    $btnContinue.Cursor = [System.Windows.Forms.Cursors]::Hand
    $frmSetup.Controls.Add($btnContinue)
    $frmSetup.AcceptButton = $btnContinue

    $frmSetup.ClientSize = New-Object System.Drawing.Size(320, (168 + $settingsOffset))

    $btnContinue.Add_MouseEnter({ $btnContinue.BackColor = $colGoldHi })
    $btnContinue.Add_MouseLeave({ $btnContinue.BackColor = $colGold })

    $setupCancelled = $true
    $btnContinue.Add_Click({ $script:setupCancelled = $false; $frmSetup.Close() })

    $frmSetup.ShowDialog() | Out-Null

    if ($setupCancelled) { exit 0 }

    # Create shortcut if chosen
    if ($chkShortcut.Checked) {
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
            Write-Host "Created 'OSRS Companion' shortcut on your Desktop."
        }
        catch { Write-Warning "Could not create Desktop shortcut: $($_.Exception.Message)" }
    }

    $null | Set-Content $shortcutFlag
    $importSettings = ($chkSettings -ne $null -and $chkSettings.Checked)
}

# --- Scale dialog (shown on every subsequent run unless "don't ask again") ---
$launched = $false
if (-not $skipScale) {
    $form = New-Object System.Windows.Forms.Form
    $form.Text            = "OSRS Companion"
    $form.StartPosition   = "CenterScreen"
    $form.FormBorderStyle = "FixedDialog"
    $form.MaximizeBox     = $false
    $form.MinimizeBox     = $false
    $form.BackColor       = $colBg
    $rlIcon2 = Get-RuneLiteIcon
    if ($rlIcon2) { $form.Icon = $rlIcon2 }

    $lblTitle = New-Object System.Windows.Forms.Label
    $lblTitle.Text = "OSRS Companion"; $lblTitle.Font = $fontTitle; $lblTitle.ForeColor = $colGold
    $lblTitle.BackColor = $colBg; $lblTitle.Location = New-Object System.Drawing.Point(20, 18)
    $lblTitle.AutoSize = $true; $form.Controls.Add($lblTitle)

    $verText = if (Test-Path $versionFile) { (Get-Content $versionFile -Raw).Trim() } else { "" }
    $lblVer = New-Object System.Windows.Forms.Label
    $lblVer.Text = $verText; $lblVer.Font = $fontLabel; $lblVer.ForeColor = $colMuted
    $lblVer.BackColor = $colBg; $lblVer.Location = New-Object System.Drawing.Point(22, 42)
    $lblVer.AutoSize = $true; $form.Controls.Add($lblVer)

    $divider = New-Object System.Windows.Forms.Panel
    $divider.BackColor = $colBorder; $divider.Location = New-Object System.Drawing.Point(20, 64)
    $divider.Size = New-Object System.Drawing.Size(280, 1); $form.Controls.Add($divider)

    $lblScale = New-Object System.Windows.Forms.Label
    $lblScale.Text = "Display Scale"; $lblScale.Font = $fontLabel; $lblScale.ForeColor = $colMuted
    $lblScale.BackColor = $colBg; $lblScale.Location = New-Object System.Drawing.Point(20, 80)
    $lblScale.AutoSize = $true; $form.Controls.Add($lblScale)

    $tbScale = New-Object System.Windows.Forms.TextBox
    $tbScale.Font = $fontUI; $tbScale.ForeColor = $colText; $tbScale.BackColor = $colSurface
    $tbScale.BorderStyle = "FixedSingle"
    $tbScale.Location = New-Object System.Drawing.Point(238, 76); $tbScale.Size = New-Object System.Drawing.Size(62, 22)
    $tbScale.Text = $uiScale; $tbScale.TextAlign = "Center"; $form.Controls.Add($tbScale)

    $initialTick = [int]([double]$uiScale * 10)
    $slider = New-Object System.Windows.Forms.TrackBar
    $slider.Minimum = 10; $slider.Maximum = 30; $slider.TickFrequency = 5
    $slider.SmallChange = 1; $slider.LargeChange = 5
    $slider.Value = [Math]::Max(10, [Math]::Min(30, $initialTick))
    $slider.BackColor = $colBg
    $slider.Location = New-Object System.Drawing.Point(14, 96); $slider.Size = New-Object System.Drawing.Size(292, 36)
    $form.Controls.Add($slider)

    $slider.Add_ValueChanged({ $tbScale.Text = ($slider.Value / 10.0).ToString("0.0") })

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

    $chkSkip = New-Object System.Windows.Forms.CheckBox
    $chkSkip.Text = "Don't ask again"
    $chkSkip.Font = $fontLabel; $chkSkip.ForeColor = $colMuted; $chkSkip.BackColor = $colBg
    $chkSkip.Location = New-Object System.Drawing.Point(20, 142); $chkSkip.AutoSize = $true
    $form.Controls.Add($chkSkip)

    $btnLaunch = New-Object System.Windows.Forms.Button
    $btnLaunch.Text = "Launch"; $btnLaunch.Font = $fontBtn
    $btnLaunch.ForeColor = $colBg; $btnLaunch.BackColor = $colGold
    $btnLaunch.FlatStyle = "Flat"; $btnLaunch.FlatAppearance.BorderSize = 0
    $btnLaunch.Location = New-Object System.Drawing.Point(20, 166)
    $btnLaunch.Size = New-Object System.Drawing.Size(280, 38)
    $btnLaunch.Cursor = [System.Windows.Forms.Cursors]::Hand; $form.Controls.Add($btnLaunch)
    $form.AcceptButton = $btnLaunch

    $form.ClientSize = New-Object System.Drawing.Size(320, 220)

    $btnLaunch.Add_MouseEnter({ $btnLaunch.BackColor = $colGoldHi })
    $btnLaunch.Add_MouseLeave({ $btnLaunch.BackColor = $colGold })

    $btnLaunch.Add_Click({
        $script:uiScale = ($slider.Value / 10.0).ToString("0.0")
        if ($chkSkip.Checked) {
            "uiScale=$($script:uiScale)`nskipScale=true" | Set-Content $settingsFile -Encoding ASCII
        } else {
            "uiScale=$($script:uiScale)" | Set-Content $settingsFile -Encoding ASCII
        }
        $script:launched = $true
        $form.Close()
    })

    $form.ShowDialog() | Out-Null
} else {
    $launched = $true
}

if (-not $launched) { exit 0 }   # user closed the window

# --- First-run settings import ---
if ($importSettings) {
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
                           'drophighlighterplugin', 'kalphiteflinchplugin', 'farmrunplugin',
                           'sailingsteeringplugin')) {
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
