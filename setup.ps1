# setup.ps1 - First-time setup for OSRS Companion.
# Creates shortcuts and optionally imports RuneLite settings.
# Run this once after extracting the zip. Use launch.ps1 / launch.bat to start normally.

$scriptDir  = $PSScriptRoot
$configPath = "$env:USERPROFILE\.runelite\profiles2\default-0.properties"
$bundled    = "$scriptDir\settings\default-0.properties"
$launchPs1  = "$scriptDir\launch.ps1"
$rlExe      = "$env:LOCALAPPDATA\RuneLite\RuneLite.exe"

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$colBg      = [System.Drawing.Color]::FromArgb(18, 14, 8)
$colSurface = [System.Drawing.Color]::FromArgb(30, 24, 14)
$colBorder  = [System.Drawing.Color]::FromArgb(60, 48, 24)
$colGold    = [System.Drawing.Color]::FromArgb(212, 160, 48)
$colGoldHi  = [System.Drawing.Color]::FromArgb(236, 185, 72)
$colText    = [System.Drawing.Color]::FromArgb(237, 228, 204)
$colMuted   = [System.Drawing.Color]::FromArgb(140, 120, 80)
$colGreen   = [System.Drawing.Color]::FromArgb(80, 180, 80)
$fontTitle  = New-Object System.Drawing.Font("Segoe UI Semibold", 12)
$fontSub    = New-Object System.Drawing.Font("Segoe UI", 9)
$fontLabel  = New-Object System.Drawing.Font("Segoe UI", 8)
$fontBtn    = New-Object System.Drawing.Font("Segoe UI Semibold", 10)

$form = New-Object System.Windows.Forms.Form
$form.Text            = "RuneLite (Extra Plugins) Setup"
$form.ClientSize      = New-Object System.Drawing.Size(400, 320)
$form.StartPosition   = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox     = $false
$form.MinimizeBox     = $false
$form.BackColor       = $colBg
if (Test-Path $rlExe) {
    try { $form.Icon = [System.Drawing.Icon]::ExtractAssociatedIcon($rlExe) } catch {}
}

# Title
$lblTitle = New-Object System.Windows.Forms.Label
$lblTitle.Text      = "RuneLite (Extra Plugins)"
$lblTitle.Font      = $fontTitle
$lblTitle.ForeColor = $colGold
$lblTitle.BackColor = $colBg
$lblTitle.Location  = New-Object System.Drawing.Point(20, 18)
$lblTitle.AutoSize  = $true
$form.Controls.Add($lblTitle)

$lblSub = New-Object System.Windows.Forms.Label
$lblSub.Text      = "One-time setup"
$lblSub.Font      = $fontLabel
$lblSub.ForeColor = $colMuted
$lblSub.BackColor = $colBg
$lblSub.Location  = New-Object System.Drawing.Point(22, 44)
$lblSub.AutoSize  = $true
$form.Controls.Add($lblSub)

$div = New-Object System.Windows.Forms.Panel
$div.BackColor = $colBorder
$div.Location  = New-Object System.Drawing.Point(20, 68)
$div.Size      = New-Object System.Drawing.Size(360, 1)
$form.Controls.Add($div)

# --- Shortcut section ---
$lblShortcuts = New-Object System.Windows.Forms.Label
$lblShortcuts.Text      = "SHORTCUTS"
$lblShortcuts.Font      = New-Object System.Drawing.Font("Segoe UI Semibold", 7)
$lblShortcuts.ForeColor = $colMuted
$lblShortcuts.BackColor = $colBg
$lblShortcuts.Location  = New-Object System.Drawing.Point(20, 82)
$lblShortcuts.AutoSize  = $true
$form.Controls.Add($lblShortcuts)

$chkDesktop = New-Object System.Windows.Forms.CheckBox
$chkDesktop.Text      = "Add shortcut to Desktop"
$chkDesktop.Font      = $fontSub
$chkDesktop.ForeColor = $colText
$chkDesktop.BackColor = $colBg
$chkDesktop.Location  = New-Object System.Drawing.Point(20, 102)
$chkDesktop.AutoSize  = $true
$chkDesktop.Checked   = $true
$form.Controls.Add($chkDesktop)

$lblTaskbar = New-Object System.Windows.Forms.Label
$lblTaskbar.Text      = "To pin to taskbar: right-click the Desktop shortcut after setup and select `"Pin to taskbar`""
$lblTaskbar.Font      = $fontLabel
$lblTaskbar.ForeColor = $colMuted
$lblTaskbar.BackColor = $colBg
$lblTaskbar.Location  = New-Object System.Drawing.Point(38, 126)
$lblTaskbar.Size      = New-Object System.Drawing.Size(342, 30)
$form.Controls.Add($lblTaskbar)

$div2 = New-Object System.Windows.Forms.Panel
$div2.BackColor = $colBorder
$div2.Location  = New-Object System.Drawing.Point(20, 166)
$div2.Size      = New-Object System.Drawing.Size(360, 1)
$form.Controls.Add($div2)

# --- Settings section ---
$lblSettings = New-Object System.Windows.Forms.Label
$lblSettings.Text      = "SETTINGS"
$lblSettings.Font      = New-Object System.Drawing.Font("Segoe UI Semibold", 7)
$lblSettings.ForeColor = $colMuted
$lblSettings.BackColor = $colBg
$lblSettings.Location  = New-Object System.Drawing.Point(20, 180)
$lblSettings.AutoSize  = $true
$form.Controls.Add($lblSettings)

$chkSettings = New-Object System.Windows.Forms.CheckBox
$chkSettings.Font      = $fontSub
$chkSettings.ForeColor = $colText
$chkSettings.BackColor = $colBg
$chkSettings.Location  = New-Object System.Drawing.Point(20, 198)
$chkSettings.AutoSize  = $true

if (Test-Path $bundled) {
    $chkSettings.Text    = "Import bundled RuneLite settings"
    $chkSettings.Checked = (-not (Test-Path $configPath))
    $chkSettings.Enabled = $true
} else {
    $chkSettings.Text    = "No bundled settings included"
    $chkSettings.Checked = $false
    $chkSettings.Enabled = $false
    $chkSettings.ForeColor = $colMuted
}
$form.Controls.Add($chkSettings)

$lblSettingsNote = New-Object System.Windows.Forms.Label
$lblSettingsNote.Text      = "Imports hotkeys, bank tags, and UI layout. Skip to keep your existing settings."
$lblSettingsNote.Font      = $fontLabel
$lblSettingsNote.ForeColor = $colMuted
$lblSettingsNote.BackColor = $colBg
$lblSettingsNote.Location  = New-Object System.Drawing.Point(38, 222)
$lblSettingsNote.Size      = New-Object System.Drawing.Size(342, 28)
$form.Controls.Add($lblSettingsNote)

# --- Install button ---
$btnInstall = New-Object System.Windows.Forms.Button
$btnInstall.Text      = "Install"
$btnInstall.Font      = $fontBtn
$btnInstall.ForeColor = $colBg
$btnInstall.BackColor = $colGold
$btnInstall.FlatStyle = "Flat"
$btnInstall.FlatAppearance.BorderSize = 0
$btnInstall.Location  = New-Object System.Drawing.Point(20, 268)
$btnInstall.Size      = New-Object System.Drawing.Size(360, 38)
$btnInstall.Cursor    = [System.Windows.Forms.Cursors]::Hand
$btnInstall.Add_MouseEnter({ $btnInstall.BackColor = $colGoldHi })
$btnInstall.Add_MouseLeave({ $btnInstall.BackColor = $colGold })
$form.Controls.Add($btnInstall)

$form.AcceptButton = $btnInstall

$btnInstall.Add_Click({
    $errors = @()

    # Desktop shortcut
    if ($chkDesktop.Checked) {
        try {
            $shell = New-Object -ComObject WScript.Shell
            $lnk   = $shell.CreateShortcut("$env:USERPROFILE\Desktop\RuneLite (Extra Plugins).lnk")
            $lnk.TargetPath       = "powershell.exe"
            $lnk.Arguments        = "-NoProfile -ExecutionPolicy Bypass -File `"$launchPs1`""
            $lnk.WorkingDirectory = $scriptDir
            $lnk.Description      = "RuneLite with extra plugins"
            if (Test-Path $rlExe) { $lnk.IconLocation = "$rlExe,0" }
            $lnk.Save()
        } catch {
            $errors += "Could not create Desktop shortcut: $($_.Exception.Message)"
        }
    }

    # Settings import
    if ($chkSettings.Checked -and (Test-Path $bundled)) {
        try {
            New-Item -ItemType Directory -Force -Path (Split-Path $configPath) | Out-Null
            Copy-Item $bundled $configPath -Force
            $profilesJson = "$scriptDir\settings\profiles.json"
            if (Test-Path $profilesJson) { Copy-Item $profilesJson (Split-Path $configPath) -Force }
        } catch {
            $errors += "Could not import settings: $($_.Exception.Message)"
        }
    }

    if ($errors.Count -gt 0) {
        [System.Windows.Forms.MessageBox]::Show(
            ($errors -join "`n"), "Setup warnings",
            [System.Windows.Forms.MessageBoxButtons]::OK,
            [System.Windows.Forms.MessageBoxIcon]::Warning) | Out-Null
    }

    $form.Close()

    # Offer to launch immediately
    $launch = [System.Windows.Forms.MessageBox]::Show(
        "Setup complete!`n`nLaunch RuneLite (Extra Plugins) now?",
        "Done",
        [System.Windows.Forms.MessageBoxButtons]::YesNo,
        [System.Windows.Forms.MessageBoxIcon]::None)

    if ($launch -eq [System.Windows.Forms.DialogResult]::Yes) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launchPs1
    }
})

$form.ShowDialog() | Out-Null
