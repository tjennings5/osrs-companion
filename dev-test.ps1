# dev-test.ps1
# Local-only dev loop: builds the plugin JAR and launches RuneLite directly with
# your real settings, skipping everything the packaged launcher does that only
# matters for distribution (GitHub update check, first-run setup, zip/release).
#
# Does not touch dist\ or GitHub. Safe to run repeatedly while iterating.
#
# Usage:
#   .\dev-test.ps1                 <- build + launch, scale 2.0
#   .\dev-test.ps1 -Scale 1.5

param(
    [string]$Scale = "2.0"
)

$ErrorActionPreference = "Stop"

$winPluginDir  = "$PSScriptRoot\plugin"
$driveLetter   = $winPluginDir.Substring(0, 1).ToLower()
$wslProjectDir = '/mnt/' + $driveLetter + '/' + $winPluginDir.Substring(3).Replace('\', '/')
$configPath    = "$env:USERPROFILE\.runelite\profiles2\default-0.properties"

# --- Build fat JAR ---
Write-Host "Building fat JAR..."
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
Write-Host "  Built: $($jar.Name) ($([math]::Round($jar.Length / 1048576, 1)) MB)"

# --- Double-launch guard ---
# Same reasoning as the packaged launcher: a running RuneLite holds its config
# file exclusively, and launching a second client while one is open can wipe it.
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
    exit 1
}

# --- Enable plugins in the RuneLite config ---
# Dev-loaded plugins don't persist their enabled state between sessions.
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

# --- Find RuneLite's JRE ---
$java = "$env:LOCALAPPDATA\RuneLite\jre\bin\java.exe"
if (-not (Test-Path $java)) {
    $found = Get-Command java -ErrorAction SilentlyContinue
    if ($found) {
        $java = $found.Source
    } else {
        Write-Error "Java not found.`nInstall RuneLite from https://runelite.net, or install Java 11+."
        exit 1
    }
}

# --- Launch ---
Write-Host "Launching (scale $Scale)..."
& $java -ea "-Dsun.java2d.uiScale=$Scale" -jar $jar.FullName --developer-mode
