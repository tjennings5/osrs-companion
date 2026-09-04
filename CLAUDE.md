# OSRS Companion — Project Context

## What this is

A self-contained Windows package that launches RuneLite with a set of custom plugins. Users download a zip, extract it anywhere, and double-click a bat file. No WSL, Gradle, or Java install required on the recipient's machine.

The project lives at `C:\Users\Tyler\Documents\projects\osrs-companion\` on the dev machine.

---

## Repository

GitHub: `tjennings5/osrs-companion` (public)  
Releases: `gh release create` via `build-package.ps1 -Publish`

---

## Directory structure

```
osrs-companion/
  companion-launch.ps1    # Launcher template — stamped by build-package.ps1 into dist\launch.ps1
  build-package.ps1       # Dev script: builds JAR, assembles dist\, optionally publishes GitHub release
  setup.ps1               # First-time setup script for new users (not part of the launcher flow)
  plugin/                 # Gradle project — all plugin source code
    src/main/java/
      com/bridge/         # OsrsMcpBridgePlugin — exports character data to JSON for the MCP server
      com/araxxor/        # AraxxorHelperPlugin
      com/cerberus/       # CerberusHelperPlugin
      com/dropHighlighter/ # DropHighlighterPlugin
      com/farmrun/        # FarmRunPlugin
      com/kalphite/       # KalphiteFlinchPlugin
      com/spawntimer/     # SpawnTimerPlugin — ctrl+right-click NPC spawn point timers
      com/combat/         # Shared combat utilities (AttackClock, HealthBar, XpDamage)
    src/test/java/com/bridge/
      OsrsMcpBridgePluginTest.java  # Dev entrypoint: loads all plugins via ExternalPluginManager
  dist/                   # Output folder — this is what gets zipped and published
    RuneLite (Extra Plugins).bat
    launch.ps1            # Stamped from companion-launch.ps1 (GITHUB_REPO filled in)
    extra-plugins.jar     # Fat JAR built by shadowJar (all plugin classes + deps)
    version.txt           # Current version tag (e.g. v20250831-1430)
    settings/
      default-0.properties  # Bundled RuneLite settings snapshot
      profiles.json
```

---

## Build & publish workflow

All plugin changes go in `plugin/` (not in the separate `osrs-mcp` repo).

**Local dev testing (use this while iterating on a plugin):**
```powershell
.\dev-test.ps1
```
Builds the fat JAR and launches RuneLite directly with your real settings — skips the GitHub update check, first-run setup, and zip/release steps that `build-package.ps1` does for distribution. Doesn't touch `dist\` or GitHub, safe to run repeatedly.

**Build only (no publish):**
```powershell
.\build-package.ps1
```

**Build + publish GitHub release:**
```powershell
.\build-package.ps1 -Publish
```

This:
1. Runs `./gradlew shadowJar` via WSL to compile `extra-plugins.jar`
2. Copies the JAR, settings, and stamped launcher into `dist\`
3. Zips `dist\` → `osrs-companion-setup.zip`
4. Creates a GitHub release with the zip as the only asset

Gradle is run through WSL (Ubuntu). The Gradle wrapper is at `plugin/gradle/wrapper/gradle-wrapper.properties` — if it's missing, copy it from the `osrs-mcp` repo.

---

## Auto-update mechanism

`dist\launch.ps1` checks GitHub releases on every launch:
1. Fetches `https://api.github.com/repos/tjennings5/osrs-companion/releases/latest`
2. Compares `tag_name` to `dist\version.txt`
3. If different: downloads `osrs-companion-setup.zip`, extracts `extra-plugins.jar`, replaces local copy
4. Writes progress to `dist\update.log`

Key implementation details:
- Temp zip must be named `.zip` — `Expand-Archive` rejects other extensions
- Use `Copy-Item` (not `Move-Item -Force`) to overwrite the JAR; `Move-Item` fails when the destination already exists on Windows
- `$ProgressPreference = 'SilentlyContinue'` around `Expand-Archive` prevents the progress bar from overwriting console output

---

## Launcher UI flow

Two phases controlled by `$scriptDir\.setup-done` flag file:

**First run** (flag absent): WinForms dialog with:
- "Create Desktop shortcut" checkbox (checked by default)
- "Import bundled RuneLite settings" checkbox (shown only if `settings\default-0.properties` exists; pre-checked if user has no existing config)
- "Continue" button → writes `.setup-done`, creates shortcut, sets `$importSettings`

**Subsequent runs** (flag present): Scale dialog with:
- Slider (1.0–3.0 in 0.1 steps), text box showing current value
- "Don't ask again" checkbox
- "Launch" button → saves `launcher-settings.txt` (with `skipScale=true` if checked), launches

If `skipScale=true` is in `launcher-settings.txt`, the scale dialog is skipped entirely and RuneLite launches immediately.

Settings file (`launcher-settings.txt`) format:
```
uiScale=2.0
skipScale=true   # optional, omit to show slider every time
```

Color scheme: dark OSRS-gold (`#120E08` background, `#D4A030` gold accent, `#EDE4CC` text).

---

## Plugin loading

All plugins are loaded via `ExternalPluginManager.loadBuiltin()` in `OsrsMcpBridgePluginTest.java`. This is the dev-client loading path — plugins aren't submitted to the Plugin Hub.

Launch command (in `companion-launch.ps1`):
```powershell
& $java -ea "-Dsun.java2d.uiScale=$uiScale" -jar $jarPath --developer-mode
```

Where `$jarPath` is `dist\extra-plugins.jar` (the shadowJar fat JAR).

Plugin enabled states are forced to `true` in the RuneLite config before each launch because dev-loaded plugins reset to disabled every session. The keys written are:
- `runelite.osrsmcpbridgeplugin`
- `runelite.cerberushelperplugin`
- `runelite.araxxorhelperplugin`
- `runelite.drophighlighterplugin`
- `runelite.kalphiteflinchplugin`
- `runelite.farmrunplugin`
- `runelite.spawntimerplugin`

---

## OsrsMcpBridgePlugin

Exports character data to `%USERPROFILE%\.runelite\osrs-mcp-bridge\{username}.json` every 2 seconds.

Exports: stats (level + XP per skill), quest states, inventory, equipment, bank (persists across sessions once opened), group storage.

The MCP server (`osrs-mcp` repo, running on the Pi at 192.168.2.170) reads these JSON files to answer Claude's questions about the character. The plugin already supports multiple characters — each account gets its own file named by username.

---

## Double-launch guard

Before launching, the script tries to exclusively open the RuneLite config file. If it's locked (another RuneLite is running), it waits up to 20 seconds then errors out. This prevents RuneLite from wiping settings when two instances start simultaneously.

---

## Config backup

Backups saved to `%USERPROFILE%\Documents\osrs-companion-backups\` (timestamped folders). Keeps the 10 most recent, skips if unchanged (SHA-256 comparison). Runs pre-launch and on RuneLite exit.

---

## Related repo

`osrs-mcp` (separate project, `C:\Users\Tyler\Documents\projects\osrs-mcp\`) contains:
- `server.py` — Python MCP server that Claude Desktop connects to; reads the JSON files the bridge plugin writes
- `runelite-plugin/` — older copy of the bridge plugin (not used for building; `osrs-companion/plugin/` is the active source)

The MCP server runs on the Pi (192.168.2.170) as a plain background process started with `nohup`. To restart it after changing `server.py`:
```bash
scp server.py tyler@192.168.2.170:~/osrs-mcp/server.py
ssh tyler@192.168.2.170 "kill $(pgrep -f server.py); cd ~/osrs-mcp && nohup venv/bin/python3 server.py > server.log 2>&1 &"
```
