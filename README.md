# OSRS Companion

A curated set of RuneLite plugins packaged as a one-click launcher. Drop the folder on any Windows machine with RuneLite installed and play — no source code, no build tools, no setup beyond a double-click.

## Plugins Included

| Plugin | Description |
|---|---|
| **Farm Run Guide** | Guides herb and tree farm runs patch by patch — bank checklist, directional overlay, and auto-advance when you plant |
| **Drop Highlighter** | Highlights noteworthy drops on the ground so nothing valuable gets left behind in a busy pile |
| **Cerberus Helper** | Audio cues for ghost summons and phase transitions, timed to the actual attack cycle |
| **Araxxor Helper** | Tracks open paths and phase state to remove the guesswork from rotation-dependent mechanics |
| **Kalphite Flinch Timer** | Overlay showing the optimal flinch window for the Kalphite Queen |
| **OSRS MCP Bridge** | Exports live character data — stats, inventory, bank, quests — for use with an AI assistant |

## Setup

**Requirements:** Windows 10/11 · RuneLite installed ([runelite.net](https://runelite.net))

1. **Install RuneLite** and launch it at least once so it downloads its files.
2. **Download the latest release** — go to [Releases](../../releases/latest) and grab `osrs-companion-dist.zip`.
3. **Unzip anywhere** — Desktop, Documents, wherever.
4. **Double-click `launch.bat`.**

On your first launch you'll be asked whether to import bundled RuneLite settings (hotkeys, bank tags, UI layout). Say Yes to start with the same setup as everyone else, or No to keep your own.

## Updates

**Plugin updates** — Every time you launch, the launcher silently checks this repo for a newer release. If one exists, it downloads before RuneLite opens. Nothing to do on your end.

**RuneLite updates** — RuneLite updates itself through its own launcher. If login fails after a game update, launch RuneLite normally once to let it update, then use `launch.bat` as usual.

> **You never need a new zip.** Once you have the folder, keep using the same `launch.bat` — it handles everything automatically.

## Contributing

Plugin source code is in `plugin/src/`. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup instructions, project layout, and how to add a new plugin.

---

## Publishing an Update

After merging changes, build and publish a new release from the `osrs-companion\` folder:

```powershell
.\build-package.ps1 -Publish
```

Requires WSL + Ubuntu and the `gh` CLI (`gh auth login` once). This rebuilds the JAR, packages the dist folder, and publishes a GitHub release. Everyone else auto-downloads the update on next launch.
