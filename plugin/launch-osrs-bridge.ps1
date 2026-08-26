# Launches RuneLite (native Windows Java) with the osrs-mcp-bridge plugin loaded.
# Uses your saved Jagex Launcher credentials automatically (see README for how those got set up).

$configPath = "C:\Users\Tyler\.runelite\profiles2\default-0.properties"

# A running RuneLite holds this file open with no sharing, so being able to
# open it exclusively is a reliable proxy for "no client is running" — more so
# than a process-name check, since the client can be RuneLite.exe or a bare
# java.exe depending on how it was started.
function Test-ConfigFree
{
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $true }
    try {
        $fs = [System.IO.File]::Open($Path, 'Open', 'ReadWrite', 'None')
        $fs.Close()
        return $true
    }
    catch { return $false }
}

$profileDir = Split-Path $configPath -Parent
$backupRoot = "C:\Users\Tyler\Documents\projects\osrs-mcp\backups"
$backupKeep = 15

# Snapshots the RuneLite profile files into backups\<timestamp>\.
#
# The 2026-08-04 near-miss is the reason this exists: a client that can't read
# its config silently falls back to defaults and then writes those defaults
# back out. By the time you notice your settings are gone, the only copy is
# already overwritten. Two things make these snapshots trustworthy:
#
#  - A snapshot much smaller than the newest good one is almost certainly a
#    defaults dump (~48KB vs ~240KB here), so it's tagged SUSPECT and doesn't
#    count against rotation. A run of bad sessions can't push out good copies.
#  - Identical content is skipped, so launching and closing repeatedly doesn't
#    churn through the retention window.
function Backup-RuneLiteConfig
{
    param([string]$Reason)

    # A just-exited client can hold the file for another moment; the snapshot is
    # worthless if we read it mid-write.
    $waited = 0
    while (-not (Test-ConfigFree $configPath) -and $waited -lt 15) {
        Start-Sleep -Seconds 1
        $waited++
    }
    if (-not (Test-ConfigFree $configPath)) {
        Write-Warning "Config still locked - skipping $Reason backup."
        return
    }
    if (-not (Test-Path $configPath)) { return }

    try {
        New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
        $existing = @(Get-ChildItem $backupRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notlike '*SUSPECT*' } | Sort-Object Name -Descending)

        $newHash = (Get-FileHash -LiteralPath $configPath -Algorithm SHA256).Hash
        if ($existing.Count -gt 0) {
            $prev = Join-Path $existing[0].FullName 'default-0.properties'
            if ((Test-Path -LiteralPath $prev) -and
                (Get-FileHash -LiteralPath $prev -Algorithm SHA256).Hash -eq $newHash) {
                Write-Host "Config unchanged since last backup - nothing to snapshot."
                return
            }
        }

        $size = (Get-Item -LiteralPath $configPath).Length
        $suspect = $false
        if ($existing.Count -gt 0) {
            $best = ($existing | ForEach-Object {
                $f = Join-Path $_.FullName 'default-0.properties'
                if (Test-Path -LiteralPath $f) { (Get-Item -LiteralPath $f).Length } else { 0 }
            } | Measure-Object -Maximum).Maximum
            if ($best -gt 0 -and $size -lt ($best * 0.5)) { $suspect = $true }
        }

        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        if ($suspect) { $stamp = "$stamp-SUSPECT" }
        $target = Join-Path $backupRoot $stamp
        New-Item -ItemType Directory -Force -Path $target | Out-Null

        foreach ($f in @('default-0.properties', 'profiles.json', '$rsprofile--1.properties')) {
            $s = Join-Path $profileDir $f
            if (Test-Path -LiteralPath $s) { Copy-Item -LiteralPath $s -Destination $target -Force }
        }

        if ($suspect) {
            Write-Warning "Backed up to $stamp, but it is far smaller than your last good backup - your settings may have been reset this session. Compare against the newest non-SUSPECT folder before relaunching."
        } else {
            Write-Host "Backed up RuneLite config -> $stamp ($Reason)"
        }

        # Rotate good snapshots only; SUSPECT ones are kept for inspection.
        $good = @(Get-ChildItem $backupRoot -Directory | Where-Object { $_.Name -notlike '*SUSPECT*' } |
            Sort-Object Name -Descending)
        if ($good.Count -gt $backupKeep) {
            $good | Select-Object -Skip $backupKeep | ForEach-Object {
                Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
            }
        }
    }
    catch {
        # Never let a backup problem stop you from playing.
        Write-Warning "Config backup failed: $($_.Exception.Message)"
    }
}

# Starting a second client before the first has released its config is what
# wiped every setting on 2026-08-04: the new client couldn't open the file,
# concluded it didn't exist, fell back to defaults for ~1100 keys, and pushed
# that reset up to RuneLite's config sync. A closing client releases the file
# within a few seconds, so wait briefly before giving up rather than failing
# anyone who just clicked X.
$waited = 0
while (-not (Test-ConfigFree $configPath) -and $waited -lt 20) {
    if ($waited -eq 0) { Write-Host "RuneLite still holds its config - waiting for it to close..." }
    Start-Sleep -Seconds 1
    $waited++
}
if (-not (Test-ConfigFree $configPath)) {
    Write-Error "RuneLite is already running (its config file is still locked). Close it fully and re-run this shortcut - launching now would reset all your RuneLite settings."
    exit 1
}

# Snapshot before we touch anything. The close-time backup below covers the
# normal case, but this one also captures sessions you ran through the regular
# RuneLite launcher, which never goes through this script at all.
Backup-RuneLiteConfig -Reason 'pre-launch'

# RuneLite doesn't persist these plugins' "enabled" toggles as real saved
# preferences (they're loaded via a dev-testing mechanism, not a proper Hub
# install), so they reset to false on every shutdown. Force them back to true
# here, before launch, so you never have to do this by hand. The key name is
# "runelite." plus the plugin class' simple name, lowercased.
#
# Read/write explicitly as UTF-8 without a BOM: RuneLite parses this file as
# UTF-8, while Set-Content on Windows PowerShell would write it as ANSI and a
# BOM would corrupt the first key. Today the file is pure ASCII so it round-
# trips either way, but that stops being true the moment a bank tag or setup
# name picks up a non-ASCII character.
if (Test-Path $configPath) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $content = [System.IO.File]::ReadAllText($configPath, $utf8NoBom)
    if ([string]::IsNullOrEmpty($content)) {
        Write-Error "Read $configPath but got nothing back - refusing to write, as that would blank your RuneLite config."
        exit 1
    }
    foreach ($key in @('osrsmcpbridgeplugin', 'cerberushelperplugin', 'araxxorhelperplugin', 'drophighlighterplugin', 'kalphiteflinchplugin', 'farmrunplugin')) {
        if ($content -match "runelite\.$key=") {
            $content = $content -replace "runelite\.$key=\w+", "runelite.$key=true"
        } else {
            $content += "`nrunelite.$key=true`n"
        }
    }
    [System.IO.File]::WriteAllText($configPath, $content, $utf8NoBom)
}

# ---------------------------------------------------------------------------
# Classpath.
#
# We bypass the RuneLite launcher (we invoke the client's main class directly),
# so we also bypass its self-updating. The injected client has to match the
# game's current revision or login dies with error_game_js5connect_outofdate --
# which is what happened on 2026-08-05, with a classpath hardcoded at 1.12.33.
#
# build.gradle asks for 'latest.release' and no longer caches that resolution,
# so just asking Gradle on every launch *is* the whole update mechanism. About
# a second with a warm daemon, four cold. WSL has to be running either way --
# the jars live in its Gradle cache, which is where this classpath points.
# ---------------------------------------------------------------------------
$wslProjectDir = "/mnt/c/Users/Tyler/Documents/projects/osrs-mcp/runelite-plugin"
$wslDistro     = "Ubuntu"
$cpCacheFile   = "C:\Users\Tyler\Documents\projects\osrs-mcp\runelite-plugin\build\launch-classpath.txt"
$utf8NoBomCp   = New-Object System.Text.UTF8Encoding($false)

# Gradle runs under WSL and prints WSL paths, but the JVM that receives this
# classpath is the native Windows one and can't read them.
function Convert-WslPathToWindows
{
    param([string]$Path)
    if ($Path -match '^/mnt/([a-z])/(.*)$') {
        return ($Matches[1].ToUpper() + ':\' + $Matches[2].Replace('/', '\'))
    }
    return ("\\wsl.localhost\$wslDistro" + $Path.Replace('/', '\'))
}

Write-Host "Building against the current RuneLite release..."
# testClasses rather than build: a failing unit test shouldn't stop you playing.
$gradle = "cd '$wslProjectDir' && ./gradlew -q testClasses printTestClasspath"
$output = & wsl.exe -d $wslDistro -e bash -lc $gradle 2>&1

# printTestClasspath emits one colon-separated line; the build can print other
# things, so pick the line that actually looks like a classpath.
$line = $output | Where-Object { $_ -match 'client-[\d.]+\.jar' } | Select-Object -Last 1

$cp = $null
if ($LASTEXITCODE -eq 0 -and $line) {
    # Split only at colons that begin a new absolute path, so a colon inside a
    # path can never be mistaken for the separator.
    $cp = (($line.Trim() -split ':(?=/)') | ForEach-Object { Convert-WslPathToWindows $_ }) -join ';'
    [System.IO.File]::WriteAllText($cpCacheFile, $cp, $utf8NoBomCp)
    if ($cp -match 'client-(\d+(?:\.\d+)+)\.jar') { Write-Host "RuneLite $($Matches[1])." }
}
else {
    Write-Warning "Gradle failed:`n$($output -join "`n")"
    # The last classpath that built, kept only so a broken build doesn't cost
    # you a session. Note that if the build broke *because* RuneLite updated,
    # this is the old version and login will still fail - the only real fix is
    # to make the plugin compile against the new API.
    if (Test-Path $cpCacheFile) {
        $cp = [System.IO.File]::ReadAllText($cpCacheFile, $utf8NoBomCp).Trim()
        Write-Warning "Falling back to the last classpath that built successfully."
    }
    else {
        Write-Error "Gradle failed and there is no previous classpath to fall back on - cannot launch."
        exit 1
    }
}
# The MCP server now runs on the Pi, so it can't read the plugin's export file
# directly any more — it has to be pushed over. Start that sync loop here,
# scoped to this play session: it exits when RuneLite does, so nothing is left
# running in the background when you're not playing, and there's no standing
# scheduled task to maintain.
$syncScript = "C:\Users\Tyler\Documents\projects\osrs-mcp\sync-character-data.ps1"
$sync = $null
if (Test-Path $syncScript) {
    $sync = Start-Process -FilePath "powershell.exe" `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $syncScript, '-Loop', '-IntervalSeconds', '20') `
        -WindowStyle Hidden -PassThru
    Write-Host "Started character-data sync loop (PID $($sync.Id)) -> pi4"
} else {
    Write-Warning "Sync script not found at $syncScript - character data will NOT reach the Pi."
}

# UI scale. The RuneLite *launcher* has a --scale option, but we bypass the
# launcher entirely (we invoke the client's main class directly), so that flag
# isn't available to us. The launcher only ever translated it into this JVM
# property anyway, and RuneLite forces flatlaf.uiScale.enabled=false, so
# sun.java2d.uiScale is the one knob that actually does anything here.
# Setting this explicitly also pins the client against Windows' display scaling:
# with the property absent, Java reads the system setting (measured: 150% ->
# scale 1.5). With it set, the value below wins regardless of what Windows is
# on, so changing the desktop scale or dragging to another monitor won't
# resize the client. Change $uiScale to adjust; 1.0 is unscaled.
$uiScale = "2.0"

try {
    & "C:\Users\Tyler\AppData\Local\RuneLite\jre\bin\java.exe" -ea "-Dsun.java2d.uiScale=$uiScale" -cp $cp com.osrsmcp.bridge.OsrsMcpBridgePluginTest --developer-mode --debug
}
finally {
    # Runs even if RuneLite crashes or this window is closed, so the loop can't
    # be orphaned into a stray background process.
    if ($sync -and -not $sync.HasExited) {
        Stop-Process -Id $sync.Id -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped character-data sync loop."
    }
    # One last pass, so the final post-logout snapshot reaches the Pi instead
    # of being stranded by whatever the loop's timing happened to be.
    if (Test-Path $syncScript) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $syncScript
    }

    # RuneLite writes its config during shutdown, so this has to run after the
    # client process is gone - that's why it lives here and not anywhere earlier.
    Backup-RuneLiteConfig -Reason 'on-close'
}
