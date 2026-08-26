# OSRS Companion portable launcher.
# First run: shows setup UI (shortcut + settings import).
# Subsequent runs: checks for updates, shows launcher UI, launches RuneLite.
# Template - build-package.ps1 stamps __GITHUB_REPO__ and writes this to dist\launch.ps1.

$githubRepo   = "__GITHUB_REPO__"
$scriptDir    = $PSScriptRoot
$jarPath      = "$scriptDir\extra-plugins.jar"
$versionFile  = "$scriptDir\version.txt"
$setupFlag    = "$scriptDir\.setup-done"
$configPath   = "$env:USERPROFILE\.runelite\profiles2\default-0.properties"
$settingsFile = "$scriptDir\launcher-settings.txt"
$rlExe        = "$env:LOCALAPPDATA\RuneLite\RuneLite.exe"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$colBg      = [System.Drawing.Color]::FromArgb(18, 14, 8)
$colSurface = [System.Drawing.Color]::FromArgb(30, 24, 14)
$colBorder  = [System.Drawing.Color]::FromArgb(60, 48, 24)
$colGold    = [System.Drawing.Color]::FromArgb(212, 160, 48)
$colGoldHi  = [System.Drawing.Color]::FromArgb(236, 185, 72)
$colText    = [System.Drawing.Color]::FromArgb(237, 228, 204)
$colMuted   = [System.Drawing.Color]::FromArgb(140, 120, 80)
$fontTitle  = New-Object System.Drawing.Font("Segoe UI Semibold", 12)
$fontSub    = New-Object System.Drawing.Font("Segoe UI", 9)
$fontSmall  = New-Object System.Drawing.Font("Segoe UI", 8)
$fontBtn    = New-Object System.Drawing.Font("Segoe UI Semibold", 10)

function Set-RlIcon { param($f) if (Test-Path $rlExe) { try { $f.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon($rlExe) } catch {} } }

# -----------------------------------------------------------------------
# FIRST-RUN SETUP (shown once, then .setup-done flag is written)
# -----------------------------------------------------------------------
if (-not (Test-Path $setupFlag)) {
    $bundled = "$scriptDir\settings\default-0.properties"

    $dlg = New-Object System.Windows.Forms.Form
    $dlg.Text            = "RuneLite (Extra Plugins) - Setup"
    $dlg.ClientSize      = New-Object System.Drawing.Size(400, 300)
    $dlg.StartPosition   = "CenterScreen"
    $dlg.FormBorderStyle = "FixedDialog"
    $dlg.MaximizeBox     = $false
    $dlg.MinimizeBox     = $false
    $dlg.BackColor       = $colBg
    Set-RlIcon $dlg

    $lblTitle = New-Object System.Windows.Forms.Label
    $lblTitle.Text = "RuneLite (Extra Plugins)"; $lblTitle.Font = $fontTitle
    $lblTitle.ForeColor = $colGold; $lblTitle.BackColor = $colBg
    $lblTitle.Location = New-Object System.Drawing.Point(20, 18); $lblTitle.AutoSize = $true
    $dlg.Controls.Add($lblTitle)

    $lblSub = New-Object System.Windows.Forms.Label
    $lblSub.Text = "One-time setup"; $lblSub.Font = $fontSmall
    $lblSub.ForeColor = $colMuted; $lblSub.BackColor = $colBg
    $lblSub.Location = New-Object System.Drawing.Point(22, 44); $lblSub.AutoSize = $true
    $dlg.Controls.Add($lblSub)

    $div1 = New-Object System.Windows.Forms.Panel
    $div1.BackColor = $colBorder; $div1.Location = New-Object System.Drawing.Point(20, 68)
    $div1.Size = New-Object System.Drawing.Size(360, 1); $dlg.Controls.Add($div1)

    $lblSec1 = New-Object System.Windows.Forms.Label
    $lblSec1.Text = "SHORTCUT"; $lblSec1.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 7)
    $lblSec1.ForeColor = $colMuted; $lblSec1.BackColor = $colBg
    $lblSec1.Location = New-Object System.Drawing.Point(20, 82); $lblSec1.AutoSize = $true
    $dlg.Controls.Add($lblSec1)

    $chkDesktop = New-Object System.Windows.Forms.CheckBox
    $chkDesktop.Text = "Add shortcut to Desktop (with RuneLite icon)"; $chkDesktop.Font = $fontSub
    $chkDesktop.ForeColor = $colText; $chkDesktop.BackColor = $colBg
    $chkDesktop.Location = New-Object System.Drawing.Point(20, 100); $chkDesktop.AutoSize = $true
    $chkDesktop.Checked = $true; $dlg.Controls.Add($chkDesktop)

    $lblPin = New-Object System.Windows.Forms.Label
    $lblPin.Text = "Tip: right-click the Desktop shortcut and select `"Pin to taskbar`" for quick access."
    $lblPin.Font = $fontSmall; $lblPin.ForeColor = $colMuted; $lblPin.BackColor = $colBg
    $lblPin.Location = New-Object System.Drawing.Point(38, 124); $lblPin.Size = New-Object System.Drawing.Size(342, 28)
    $dlg.Controls.Add($lblPin)

    $div2 = New-Object System.Windows.Forms.Panel
    $div2.BackColor = $colBorder; $div2.Location = New-Object System.Drawing.Point(20, 162)
    $div2.Size = New-Object System.Drawing.Size(360, 1); $dlg.Controls.Add($div2)

    $lblSec2 = New-Object System.Windows.Forms.Label
    $lblSec2.Text = "SETTINGS"; $lblSec2.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 7)
    $lblSec2.ForeColor = $colMuted; $lblSec2.BackColor = $colBg
    $lblSec2.Location = New-Object System.Drawing.Point(20, 176); $lblSec2.AutoSize = $true
    $dlg.Controls.Add($lblSec2)

    $chkSettings = New-Object System.Windows.Forms.CheckBox
    $chkSettings.Font = $fontSub; $chkSettings.BackColor = $colBg
    $chkSettings.Location = New-Object System.Drawing.Point(20, 194); $chkSettings.AutoSize = $true
    if (Test-Path $bundled) {
        $chkSettings.Text = "Import bundled RuneLite settings"
        $chkSettings.ForeColor = $colText; $chkSettings.Checked = (-not (Test-Path $configPath))
    } else {
        $chkSettings.Text = "No bundled settings included"
        $chkSettings.ForeColor = $colMuted; $chkSettings.Enabled = $false
    }
    $dlg.Controls.Add($chkSettings)

    $lblSettingsNote = New-Object System.Windows.Forms.Label
    $lblSettingsNote.Text = "Imports hotkeys, bank tags, and UI layout. Skip to keep your existing settings."
    $lblSettingsNote.Font = $fontSmall; $lblSettingsNote.ForeColor = $colMuted; $lblSettingsNote.BackColor = $colBg
    $lblSettingsNote.Location = New-Object System.Drawing.Point(38, 218); $lblSettingsNote.Size = New-Object System.Drawing.Size(342, 28)
    $dlg.Controls.Add($lblSettingsNote)

    $btnSetup = New-Object System.Windows.Forms.Button
    $btnSetup.Text = "Finish Setup & Launch"; $btnSetup.Font = $fontBtn
    $btnSetup.ForeColor = $colBg; $btnSetup.BackColor = $colGold
    $btnSetup.FlatStyle = "Flat"; $btnSetup.FlatAppearance.BorderSize = 0
    $btnSetup.Location = New-Object System.Drawing.Point(20, 252); $btnSetup.Size = New-Object System.Drawing.Size(360, 38)
    $btnSetup.Cursor = [System.Windows.Forms.Cursors]::Hand
    $btnSetup.Add_MouseEnter({ $btnSetup.BackColor = $colGoldHi })
    $btnSetup.Add_MouseLeave({ $btnSetup.BackColor = $colGold })
    $dlg.Controls.Add($btnSetup)
    $dlg.AcceptButton = $btnSetup

    $btnSetup.Add_Click({
        if ($chkDesktop.Checked) {
            try {
                $shell = New-Object -ComObject WScript.Shell
                $lnk = $shell.CreateShortcut("$env:USERPROFILE\Desktop\RuneLite (Extra Plugins).lnk")
                $lnk.TargetPath = "powershell.exe"
                $lnk.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$scriptDir\launch.ps1`""
                $lnk.WorkingDirectory = $scriptDir
                $lnk.Description = "RuneLite with extra plugins"
                if (Test-Path $rlExe) { $lnk.IconLocation = "$rlExe,0" }
                $lnk.Save()
            } catch { Write-Warning "Could not create Desktop shortcut: $($_.Exception.Message)" }
        }
        if ($chkSettings.Checked -and (Test-Path $bundled)) {
            try {
                New-Item -ItemType Directory -Force -Path (Split-Path $configPath) | Out-Null
                Copy-Item $bundled $configPath -Force
                $pj = "$scriptDir\settings\profiles.json"
                if (Test-Path $pj) { Copy-Item $pj (Split-Path $configPath) -Force }
            } catch { Write-Warning "Could not import settings: $($_.Exception.Message)" }
        }
        $null | Set-Content $setupFlag
        $dlg.Close()
    })

    $dlg.ShowDialog() | Out-Null
    if (-not (Test-Path $setupFlag)) { exit 0 }   # user closed without finishing
}

# -----------------------------------------------------------------------
# LOAD SAVED SETTINGS
# -----------------------------------------------------------------------
$uiScale        = "2.0"
$skipLauncherUI = $false
if (Test-Path $settingsFile) {
    foreach ($line in (Get-Content $settingsFile)) {
        if ($line -match '^uiScale=(.+)$')        { $uiScale        = $Matches[1] }
        if ($line -match '^skipLauncherUI=true$')  { $skipLauncherUI = $true }
    }
}

# -----------------------------------------------------------------------
# DOUBLE-LAUNCH GUARD
# -----------------------------------------------------------------------
function Test-ConfigFree {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $true }
    try { $fs = [System.IO.File]::Open($Path, 'Open', 'ReadWrite', 'None'); $fs.Close(); return $true }
    catch { return $false }
}

$waited = 0
while (-not (Test-ConfigFree $configPath) -and $waited -lt 20) {
    if ($waited -eq 0) { Write-Host "RuneLite is still running - waiting for it to close..." }
    Start-Sleep -Seconds 1; $waited++
}
if (-not (Test-ConfigFree $configPath)) {
    Write-Error "RuneLite is already running. Close it fully and try again."
    Read-Host "Press Enter to exit"; exit 1
}

# -----------------------------------------------------------------------
# AUTO-UPDATE
# -----------------------------------------------------------------------
$pendingChangelog = $null
if ($githubRepo -notlike "*FILL_IN*" -and $githubRepo -notlike "*GITHUB_REPO*") {
    try {
        Write-Host "Checking for plugin updates..."
        $release = Invoke-RestMethod "https://api.github.com/repos/$githubRepo/releases/latest" -TimeoutSec 8 -ErrorAction Stop
        $latest  = $release.tag_name.Trim()
        $current = if (Test-Path $versionFile) { (Get-Content $versionFile -Raw).Trim() } else { "" }
        if ($latest -and $latest -ne $current) {
            $asset = $release.assets | Where-Object { $_.name -eq "osrs-companion-setup.zip" } | Select-Object -First 1
            if ($asset) {
                Write-Host "  Downloading update $latest..."
                $tmpZip = "$scriptDir\update.zip.tmp"
                $tmpDir = "$scriptDir\update-extract"
                Invoke-WebRequest $asset.browser_download_url -OutFile $tmpZip -TimeoutSec 120 -ErrorAction Stop
                if (Test-Path $tmpDir) { Remove-Item $tmpDir -Recurse -Force }
                Expand-Archive $tmpZip -DestinationPath $tmpDir -Force
                $newJar = Join-Path $tmpDir "extra-plugins.jar"
                if (Test-Path $newJar) { Move-Item $newJar $jarPath -Force }
                Remove-Item $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
                Remove-Item $tmpZip  -Force -ErrorAction SilentlyContinue
                $latest | Set-Content $versionFile -Encoding ASCII
                Write-Host "  Updated to $latest."
                $notes = if ($release.body) { $release.body.Trim() } else { "No release notes provided." }
                $pendingChangelog = [PSCustomObject]@{ Version = $latest; Notes = $notes }
            }
        } else { Write-Host "  Plugins are up to date ($current)." }
    } catch { Write-Warning "Update check skipped: $($_.Exception.Message)" }
}

# -----------------------------------------------------------------------
# CHANGELOG POPUP (shown after an update)
# -----------------------------------------------------------------------
if ($pendingChangelog) {
    $cl = New-Object System.Windows.Forms.Form
    $cl.Text = "Updated to $($pendingChangelog.Version)"
    $cl.ClientSize = New-Object System.Drawing.Size(420, 300)
    $cl.StartPosition = "CenterScreen"; $cl.FormBorderStyle = "FixedDialog"
    $cl.MaximizeBox = $false; $cl.MinimizeBox = $false; $cl.BackColor = $colBg
    Set-RlIcon $cl

    $clLbl = New-Object System.Windows.Forms.Label
    $clLbl.Text = "What's new in $($pendingChangelog.Version)"; $clLbl.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 10)
    $clLbl.ForeColor = $colGold; $clLbl.BackColor = $colBg
    $clLbl.Location = New-Object System.Drawing.Point(16, 14); $clLbl.AutoSize = $true
    $cl.Controls.Add($clLbl)

    $clTxt = New-Object System.Windows.Forms.RichTextBox
    $clTxt.Text = $pendingChangelog.Notes; $clTxt.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $clTxt.ForeColor = $colText; $clTxt.BackColor = $colSurface
    $clTxt.BorderStyle = "None"; $clTxt.ReadOnly = $true; $clTxt.ScrollBars = "Vertical"
    $clTxt.Location = New-Object System.Drawing.Point(16, 42); $clTxt.Size = New-Object System.Drawing.Size(388, 200)
    $cl.Controls.Add($clTxt)

    $clBtn = New-Object System.Windows.Forms.Button
    $clBtn.Text = "OK"; $clBtn.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 9)
    $clBtn.ForeColor = $colBg; $clBtn.BackColor = $colGold
    $clBtn.FlatStyle = "Flat"; $clBtn.FlatAppearance.BorderSize = 0
    $clBtn.Location = New-Object System.Drawing.Point(316, 256); $clBtn.Size = New-Object System.Drawing.Size(88, 30)
    $clBtn.Add_Click({ $cl.Close() }); $cl.AcceptButton = $clBtn
    $cl.Controls.Add($clBtn)

    $cl.ShowDialog() | Out-Null
}

# -----------------------------------------------------------------------
# FIND RUNELITE JRE
# -----------------------------------------------------------------------
$java = "$env:LOCALAPPDATA\RuneLite\jre\bin\java.exe"
if (-not (Test-Path $java)) {
    $found = Get-Command java -ErrorAction SilentlyContinue
    if ($found) { $java = $found.Source }
    else {
        Write-Error "Java not found. Install RuneLite from https://runelite.net, or install Java 11+."
        Read-Host "Press Enter to exit"; exit 1
    }
}

# -----------------------------------------------------------------------
# LAUNCHER UI (skipped if user chose "don't ask again")
# -----------------------------------------------------------------------
if (-not $skipLauncherUI) {
    $form = New-Object System.Windows.Forms.Form
    $form.Text = "RuneLite (Extra Plugins)"; $form.ClientSize = New-Object System.Drawing.Size(320, 222)
    $form.StartPosition = "CenterScreen"; $form.FormBorderStyle = "FixedDialog"
    $form.MaximizeBox = $false; $form.MinimizeBox = $false; $form.BackColor = $colBg
    Set-RlIcon $form

    $lblTitle = New-Object System.Windows.Forms.Label
    $lblTitle.Text = "RuneLite (Extra Plugins)"; $lblTitle.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 11)
    $lblTitle.ForeColor = $colGold; $lblTitle.BackColor = $colBg
    $lblTitle.Location = New-Object System.Drawing.Point(20, 18); $lblTitle.AutoSize = $true
    $form.Controls.Add($lblTitle)

    $verText = if (Test-Path $versionFile) { (Get-Content $versionFile -Raw).Trim() } else { "" }
    $lblVer = New-Object System.Windows.Forms.Label
    $lblVer.Text = $verText; $lblVer.Font = $fontSmall
    $lblVer.ForeColor = $colMuted; $lblVer.BackColor = $colBg
    $lblVer.Location = New-Object System.Drawing.Point(22, 42); $lblVer.AutoSize = $true
    $form.Controls.Add($lblVer)

    $divider = New-Object System.Windows.Forms.Panel
    $divider.BackColor = $colBorder; $divider.Location = New-Object System.Drawing.Point(20, 64)
    $divider.Size = New-Object System.Drawing.Size(280, 1); $form.Controls.Add($divider)

    $lblScale = New-Object System.Windows.Forms.Label
    $lblScale.Text = "Display Scale"; $lblScale.Font = $fontSmall
    $lblScale.ForeColor = $colMuted; $lblScale.BackColor = $colBg
    $lblScale.Location = New-Object System.Drawing.Point(20, 80); $lblScale.AutoSize = $true
    $form.Controls.Add($lblScale)

    $tbScale = New-Object System.Windows.Forms.TextBox
    $tbScale.Font = New-Object System.Drawing.Font("Segoe UI", 9)
    $tbScale.ForeColor = $colText; $tbScale.BackColor = $colSurface; $tbScale.BorderStyle = "FixedSingle"
    $tbScale.Location = New-Object System.Drawing.Point(238, 76); $tbScale.Size = New-Object System.Drawing.Size(62, 22)
    $tbScale.Text = $uiScale; $tbScale.TextAlign = "Center"
    $form.Controls.Add($tbScale)

    $initialTick = [int]([double]$uiScale * 10)
    $slider = New-Object System.Windows.Forms.TrackBar
    $slider.Minimum = 10; $slider.Maximum = 30; $slider.TickFrequency = 5
    $slider.SmallChange = 1; $slider.LargeChange = 5
    $slider.Value = [Math]::Max(10, [Math]::Min(30, $initialTick))
    $slider.BackColor = $colBg; $slider.Location = New-Object System.Drawing.Point(14, 96)
    $slider.Size = New-Object System.Drawing.Size(292, 36)
    $form.Controls.Add($slider)

    $slider.Add_ValueChanged({ $tbScale.Text = ($slider.Value / 10.0).ToString("0.0") })
    $tbScale.Add_Leave({
        $parsed = 0.0
        if ([double]::TryParse($tbScale.Text, [ref]$parsed)) {
            $clamped = [Math]::Max(1.0, [Math]::Min(3.0, $parsed))
            $slider.Value = [int]($clamped * 10); $tbScale.Text = $clamped.ToString("0.0")
        } else { $tbScale.Text = ($slider.Value / 10.0).ToString("0.0") }
    })

    $chkSkip = New-Object System.Windows.Forms.CheckBox
    $chkSkip.Text = "Don't ask again - always launch with this scale"
    $chkSkip.Font = $fontSmall; $chkSkip.ForeColor = $colMuted; $chkSkip.BackColor = $colBg
    $chkSkip.Location = New-Object System.Drawing.Point(20, 140); $chkSkip.AutoSize = $true
    $form.Controls.Add($chkSkip)

    $btnLaunch = New-Object System.Windows.Forms.Button
    $btnLaunch.Text = "Launch RuneLite"; $btnLaunch.Font = $fontBtn
    $btnLaunch.ForeColor = $colBg; $btnLaunch.BackColor = $colGold
    $btnLaunch.FlatStyle = "Flat"; $btnLaunch.FlatAppearance.BorderSize = 0
    $btnLaunch.Location = New-Object System.Drawing.Point(20, 168); $btnLaunch.Size = New-Object System.Drawing.Size(280, 38)
    $btnLaunch.Cursor = [System.Windows.Forms.Cursors]::Hand
    $btnLaunch.Add_MouseEnter({ $btnLaunch.BackColor = $colGoldHi })
    $btnLaunch.Add_MouseLeave({ $btnLaunch.BackColor = $colGold })
    $form.Controls.Add($btnLaunch)
    $form.AcceptButton = $btnLaunch

    $launched = $false
    $btnLaunch.Add_Click({
        $script:uiScale = ($slider.Value / 10.0).ToString("0.0")
        $skip = if ($chkSkip.Checked) { "true" } else { "false" }
        @("uiScale=$($script:uiScale)", "skipLauncherUI=$skip") | Set-Content $settingsFile -Encoding ASCII
        $script:launched = $true; $form.Close()
    })

    $form.ShowDialog() | Out-Null
    if (-not $launched) { exit 0 }
}

# -----------------------------------------------------------------------
# ENABLE PLUGINS IN RUNELITE CONFIG
# -----------------------------------------------------------------------
if (Test-Path $configPath) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $content = [System.IO.File]::ReadAllText($configPath, $utf8NoBom)
    if (-not [string]::IsNullOrEmpty($content)) {
        foreach ($key in @('osrsmcpbridgeplugin','cerberushelperplugin','araxxorhelperplugin',
                           'drophighlighterplugin','kalphiteflinchplugin','farmrunplugin')) {
            if ($content -match "runelite\.$key=") {
                $content = $content -replace "runelite\.$key=\w+", "runelite.$key=true"
            } else { $content += "`nrunelite.$key=true`n" }
        }
        [System.IO.File]::WriteAllText($configPath, $content, $utf8NoBom)
    }
}

# -----------------------------------------------------------------------
# CONFIG BACKUP + LAUNCH
# -----------------------------------------------------------------------
$backupRoot = "$env:USERPROFILE\Documents\osrs-companion-backups"

function Backup-Config {
    param([string]$Reason)
    $w = 0
    while (-not (Test-ConfigFree $configPath) -and $w -lt 15) { Start-Sleep 1; $w++ }
    if (-not (Test-Path $configPath)) { return }
    try {
        New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
        $existing = @(Get-ChildItem $backupRoot -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending)
        $newHash = (Get-FileHash $configPath -Algorithm SHA256).Hash
        if ($existing.Count -gt 0) {
            $prev = Join-Path $existing[0].FullName 'default-0.properties'
            if ((Test-Path $prev) -and (Get-FileHash $prev -Algorithm SHA256).Hash -eq $newHash) { return }
        }
        $target = Join-Path $backupRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
        New-Item -ItemType Directory -Force -Path $target | Out-Null
        Copy-Item $configPath (Join-Path $target 'default-0.properties') -Force
        $good = @(Get-ChildItem $backupRoot -Directory | Sort-Object Name -Descending)
        if ($good.Count -gt 10) { $good | Select-Object -Skip 10 | ForEach-Object { Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue } }
        Write-Host "Config backed up ($Reason)"
    } catch { Write-Warning "Config backup failed: $($_.Exception.Message)" }
}

Backup-Config -Reason 'pre-launch'
Write-Host "Launching RuneLite (Extra Plugins) - scale $uiScale..."
try { & $java -ea "-Dsun.java2d.uiScale=$uiScale" -jar $jarPath --developer-mode }
finally { Backup-Config -Reason 'on-close' }
