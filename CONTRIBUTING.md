# Contributing

## What you need

- **Windows 10/11** with WSL 2 installed ([install guide](https://learn.microsoft.com/en-us/windows/wsl/install))
- **Ubuntu** inside WSL (the default distro when you install WSL)
- **Java 11** inside Ubuntu — run once: `sudo apt install openjdk-11-jdk`
- **Git** — [git-scm.com](https://git-scm.com)
- A RuneLite install ([runelite.net](https://runelite.net)) to test with

## Setup

```bash
git clone https://github.com/tjennings5/osrs-companion.git
cd osrs-companion
```

To launch RuneLite with the plugins loaded for testing, run from the `plugin/` folder in WSL:

```bash
cd plugin
./gradlew run
```

This downloads all dependencies automatically on first run (may take a minute). RuneLite will open with all the plugins active.

## Project layout

```
plugin/                    ← all Java plugin source code
  src/main/java/com/osrsmcp/
    bridge/                ← core data export plugin
    araxxor/               ← Araxxor helper
    cerberus/              ← Cerberus helper
    combat/                ← shared combat utilities
    farmrun/               ← Farm Run Guide
    kalphite/              ← Kalphite flinch timer
  src/main/java/com/dropHighlighter/
                           ← Drop Highlighter plugin
  src/test/java/com/osrsmcp/bridge/
    OsrsMcpBridgePluginTest.java  ← main entry point that loads all plugins

build-package.ps1          ← builds the distributable + publishes a GitHub release
companion-launch.ps1       ← template for the portable launcher (stamped by build-package.ps1)
```

## Making a change

1. Fork this repo and clone your fork
2. Make your changes in `plugin/src/`
3. Test with `./gradlew run` from `plugin/` in WSL
4. Submit a pull request — describe what you changed and why

## Adding a new plugin

1. Create a new package under `plugin/src/main/java/com/osrsmcp/yourplugin/`
2. Write your plugin class annotated with `@PluginDescriptor`
3. Register it in `OsrsMcpBridgePluginTest.java` — find the `ExternalPluginManager.loadBuiltin(...)` call and add your class
4. Add the plugin key to the enabled-list in `companion-launch.ps1` (the `foreach ($key in @(...))` block)
5. Test with `./gradlew run`

## Code style

- Java 11, no lambdas that require newer versions
- Follow the patterns in existing plugins — `@Subscribe` for event handlers, `@Inject` for dependencies
- No external dependencies beyond what RuneLite already provides
- See `plugin/AGENTS.md` for additional conventions used in this codebase
