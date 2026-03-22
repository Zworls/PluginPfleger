# 🌿 PluginPfleger

> A lightweight plugin update checker and downloader for Paper Minecraft servers.

![Version](https://img.shields.io/badge/version-1.0.0-green)
![Paper](https://img.shields.io/badge/Paper-1.21%2B-blue)
![License](https://img.shields.io/badge/license-MIT-brightgreen)
![Modrinth](https://img.shields.io/modrinth/dt/pluginpfleger?label=Modrinth%20Downloads)

---

## 📖 About

**PluginPfleger** (German for "Plugin Caretaker") automatically scans all installed plugins against **Modrinth** and **Hangar**, notifies you about available updates, and can download them directly to your server — all without leaving the game.

Major version jumps are highlighted in red and skipped by default, so you always have the chance to read the changelog before updating.

---

## ✨ Features

- 🔍 **Dual-source scanning** – checks Modrinth and Hangar automatically
- ⚠️ **Major version protection** – major jumps are flagged and skipped by default
- 📥 **Safe downloads** – updates go to `plugins/updates/` for manual replacement
- 🔄 **Auto-replace mode** – replaces JARs directly with automatic backups in `plugins/backups/`
- ⏱️ **Scheduled scans** – configurable automatic scan interval
- 🚫 **Ignore list** – exclude specific plugins persistently
- 🔐 **LuckPerms support** – permission integration (optional, falls back to OP)
- 🏷️ **Dual command alias** – use `/update` or `/pp`

---

## 📋 Commands

| Command | Description |
|---|---|
| `/update check` | Scan all plugins for updates |
| `/update all` | Download all available updates |
| `/update all -f` | Force-download including major updates |
| `/update status` | Show results of the last scan |
| `/update ignore <plugin>` | Exclude a plugin from scans |
| `/update unignore <plugin>` | Re-include a plugin |
| `/update ignored` | Show the ignore list |
| `/update <plugin>` | Check a single plugin |

---

## 🔐 Permissions

| Permission | Default | Description |
|---|---|---|
| `pluginpfleger.use` | OP | Allows using all `/update` commands |

---

## ⚙️ Configuration

```yaml
# Automatically scan for updates on server start
scan-beim-start: true

# Scan every X hours automatically (0 = disabled)
scan-intervall-stunden: 6

# Download mode:
#   manuell       - Save JARs to plugins/updates/ for manual replacement
#   auto-ersetzen - Replace directly (backup is always created first)
download-modus: manuell

# Broadcast a restart reminder after /update all (minutes, 0 = disabled)
neustart-erinnerung-minuten: 30

# Plugins to skip during scans
ignorierte-plugins: []
```

---

## 🔧 Compatibility

| Platform | Support |
|---|---|
| Paper 1.21+ | ✅ Full support |
| Purpur | ✅ Full support |
| Pufferfish | ✅ Full support |
| Spigot | ⚠️ Should work |
| Folia | ⚠️ Partial |
| Fabric / Forge | ❌ Not supported |

**No external dependencies** – Gson is bundled with Paper. LuckPerms is optional.

---

## 📦 Installation

1. Download the latest JAR from [Releases](../../releases) or [Modrinth](https://modrinth.com/plugin/pluginpfleger)
2. Drop it into your `plugins/` folder
3. Restart the server
4. Use `/update check` to run your first scan

---

## 🔨 Building from Source

Requirements: Java 21, Maven

```bash
git clone https://github.com/DerMaschinist/PluginPfleger.git
cd PluginPfleger
mvn package
```

The built JAR will be in `target/pluginpfleger-1.0.0.jar`.

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).

---

## 🤝 Contributing

Pull requests are welcome! If you find a bug or have a feature request, feel free to open an [issue](../../issues).

---

<p align="center">Made with ❤️ by <a href="https://github.com/Zworls">Zworls</a></p>
