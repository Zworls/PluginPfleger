package de.dermaschinist.pluginpfleger;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PflegerBefehl implements CommandExecutor, TabCompleter {

    private final PluginPfleger    plugin;
    private final UpdatePruefer    pruefer;
    private final UpdateCache      cache;
    private final PermissionHelper perms;
    private final Herunterlader    herunterlader;

    private BukkitTask neustartErinnerungTask = null;

    public PflegerBefehl(PluginPfleger plugin, UpdatePruefer pruefer,
                         UpdateCache cache, PermissionHelper perms) {
        this.plugin        = plugin;
        this.pruefer       = pruefer;
        this.cache         = cache;
        this.perms         = perms;
        this.herunterlader = new Herunterlader(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!perms.hatBerechtigung(sender, "pluginpfleger.use")) {
            sender.sendMessage(Msg.PREFIX + "§cKeine Berechtigung.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("hilfe")) {
            zeigeHilfe(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "check"    -> handleCheck(sender);
            case "all"      -> handleAll(sender, args);
            case "status"   -> handleStatus(sender);
            case "ignore"   -> handleIgnore(sender, args, true);
            case "unignore" -> handleIgnore(sender, args, false);
            case "ignored"  -> handleIgnoreListe(sender);
            default         -> handleEinzeln(sender, args[0]);
        }
        return true;
    }

    // ── /update check ─────────────────────────────────────────────────────────

    private void handleCheck(CommandSender sender) {
        if (cache.isScanLaeuft()) {
            sender.sendMessage(Msg.PREFIX + "§eScan läuft bereits...");
            return;
        }
        int gesamt = zaehleZuPruefende();
        sender.sendMessage(Msg.LINE);
        sender.sendMessage(Msg.PREFIX + "§eScanne §f" + gesamt + " §ePlugins...");
        sender.sendMessage(Msg.LINE);
        pruefer.pruefeAlle(sender);
    }

    // ── /update all [-f] ──────────────────────────────────────────────────────

    private void handleAll(CommandSender sender, String[] args) {
        if (cache.isScanLaeuft()) {
            sender.sendMessage(Msg.PREFIX + "§eScan läuft bereits...");
            return;
        }

        boolean force = args.length > 1 && args[1].equalsIgnoreCase("-f");

        String modusStr = plugin.getConfig().getString("download-modus", "manuell");
        Herunterlader.Modus modus = modusStr.equalsIgnoreCase("auto-ersetzen")
                ? Herunterlader.Modus.AUTO_ERSETZEN
                : Herunterlader.Modus.MANUELL;

        int gesamt = zaehleZuPruefende();
        sender.sendMessage(Msg.LINE);
        sender.sendMessage(Msg.PREFIX + "§eScanne §f" + gesamt + " §ePlugins und lade Updates...");
        sender.sendMessage(Msg.PREFIX + "§7Modus: §f"
                + (modus == Herunterlader.Modus.MANUELL ? "Manuell (→ plugins/updates/)" : "Auto-Ersetzen (mit Backup)"));
        if (force) sender.sendMessage(Msg.PREFIX + "§c⚠ Force-Modus: Major-Updates werden ebenfalls geladen!");
        sender.sendMessage(Msg.LINE);

        cache.setScanLaeuft(true);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Plugin> zuPruefen = new ArrayList<>();
            for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
                if (!cache.istIgnoriert(p.getName())) zuPruefen.add(p);
            }

            int                size       = zuPruefen.size();
            List<UpdateResult> ergebnisse = new ArrayList<>();

            for (int i = 0; i < size; i++) {
                Plugin p       = zuPruefen.get(i);
                int    aktuell = i + 1;
                final String progress = Msg.PREFIX + "§7[§f" + aktuell + "§7/§f" + size
                        + "§7] Prüfe §b" + p.getName() + "§7...";
                plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(progress));
                ergebnisse.add(pruefer.pruefePlugin(p));
                try { Thread.sleep(350); } catch (InterruptedException ignored) {}
            }

            cache.setErgebnisse(ergebnisse);
            cache.setScanLaeuft(false);

            List<UpdateResult> normaleUpdates = ergebnisse.stream()
                    .filter(r -> r.getStatus() == UpdateResult.Status.UPDATE_AVAILABLE).toList();
            List<UpdateResult> majorUpdates   = ergebnisse.stream()
                    .filter(UpdateResult::isMajorJump).toList();
            List<UpdateResult> zuLaden        = force
                    ? ergebnisse.stream().filter(UpdateResult::hasUpdate).toList()
                    : normaleUpdates;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Msg.LINE);
                if (!force && !majorUpdates.isEmpty()) {
                    sender.sendMessage(Msg.PREFIX + "§c⚠ " + majorUpdates.size()
                            + " Major-Update(s) übersprungen:");
                    for (UpdateResult r : majorUpdates) {
                        sender.sendMessage("  §c⚠ §b" + r.getPluginName()
                                + " §c" + r.getCurrentVersion() + " §8→ §4" + r.getLatestVersion()
                                + " §7(§f/update all -f §7oder §f/update " + r.getPluginName() + "§7)");
                    }
                    sender.sendMessage("");
                }
                if (zuLaden.isEmpty()) {
                    sender.sendMessage(Msg.PREFIX + "§aKeine normalen Updates verfügbar.");
                    sender.sendMessage(Msg.LINE);
                    return;
                }
                sender.sendMessage(Msg.PREFIX + "§e" + zuLaden.size() + " Update(s) werden heruntergeladen...");
                sender.sendMessage("");
            });

            final Herunterlader.Modus finalModus = modus;
            for (UpdateResult r : zuLaden) {
                Herunterlader.Ergebnis dl = herunterlader.herunterladen(r, finalModus);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (dl.erfolg) {
                        String farbe = r.isMajorJump() ? "§c" : "§a";
                        sender.sendMessage("  " + farbe + "✔ §b" + r.getPluginName()
                                + " §8│ §7[" + r.getSource() + "] §c" + r.getCurrentVersion()
                                + " §8→ " + farbe + r.getLatestVersion());
                        sender.sendMessage("    " + dl.nachricht);
                    } else {
                        sender.sendMessage("  §c✘ §b" + r.getPluginName() + " §8│ " + dl.nachricht);
                    }
                });
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Msg.LINE);
                if (finalModus == Herunterlader.Modus.MANUELL) {
                    sender.sendMessage(Msg.PREFIX
                            + "§7JARs in §fplugins/updates/§7 – manuell austauschen & neu starten.");
                }
                planeNeustartErinnerung(sender);
                sender.sendMessage(Msg.LINE);
            });
        });
    }

    // ── /update status ────────────────────────────────────────────────────────

    private void handleStatus(CommandSender sender) {
        if (!cache.hatErgebnisse()) {
            sender.sendMessage(Msg.PREFIX + "§eNoch kein Scan. Nutze §f/update check§e.");
            return;
        }
        List<UpdateResult> updates = cache.getLetzteErgebnisse().stream()
                .filter(UpdateResult::hasUpdate).toList();

        sender.sendMessage(Msg.LINE);
        sender.sendMessage(Msg.PREFIX + "§eLetzter Scan:     §f" + cache.getLetzterScanFormatiert());
        sender.sendMessage(Msg.PREFIX + "§eGeprüft:          §f" + cache.getLetzteErgebnisse().size());
        sender.sendMessage(Msg.PREFIX + "§eNormale Updates:  §a" + (cache.getAnzahlUpdates() - cache.getAnzahlMajorUpdates()));
        sender.sendMessage(Msg.PREFIX + "§eMajor-Updates:    §c" + cache.getAnzahlMajorUpdates());
        sender.sendMessage(Msg.PREFIX + "§eNicht gefunden:   §7" + cache.getAnzahlNichtGefunden());

        if (!updates.isEmpty()) {
            sender.sendMessage("");
            for (UpdateResult r : updates) {
                String farbe = r.isMajorJump() ? "§c" : "§a";
                String label = r.isMajorJump() ? " §c(Major!)" : "";
                sender.sendMessage("  " + farbe + "▶ §b" + r.getPluginName()
                        + " §8│ §7[" + r.getSource() + "] §c" + r.getCurrentVersion()
                        + " §8→ " + farbe + r.getLatestVersion() + label);
            }
        }
        sender.sendMessage(Msg.LINE);
    }

    // ── /update ignore / unignore ─────────────────────────────────────────────

    private void handleIgnore(CommandSender sender, String[] args, boolean ignorieren) {
        if (args.length < 2) {
            sender.sendMessage(Msg.PREFIX + "§cNutze: /update "
                    + (ignorieren ? "ignore" : "unignore") + " <pluginname>");
            return;
        }
        String name = args[1];
        if (ignorieren) {
            if (cache.hinzufuegenIgnoriert(name)) {
                sender.sendMessage(Msg.PREFIX + "§b" + name + " §ewird ab sofort ignoriert.");
            } else {
                sender.sendMessage(Msg.PREFIX + "§b" + name + " §eist bereits ignoriert.");
            }
        } else {
            if (cache.entfernenIgnoriert(name)) {
                sender.sendMessage(Msg.PREFIX + "§b" + name + " §ewird wieder geprüft.");
            } else {
                sender.sendMessage(Msg.PREFIX + "§b" + name + " §ewar nicht in der Ignore-Liste.");
            }
        }
    }

    private void handleIgnoreListe(CommandSender sender) {
        List<String> liste = cache.getIgnorierteListe();
        if (liste.isEmpty()) {
            sender.sendMessage(Msg.PREFIX + "§7Keine Plugins ignoriert.");
            return;
        }
        sender.sendMessage(Msg.PREFIX + "§eIgnorierte Plugins §8(" + liste.size() + ")§e:");
        liste.forEach(n -> sender.sendMessage("  §7- §b" + n));
    }

    // ── /update <plugin> ──────────────────────────────────────────────────────

    private void handleEinzeln(CommandSender sender, String pluginName) {
        Plugin ziel = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (ziel == null) {
            sender.sendMessage(Msg.PREFIX + "§cPlugin '§b" + pluginName + "§c' nicht gefunden.");
            return;
        }
        sender.sendMessage(Msg.PREFIX + "§ePrüfe §b" + ziel.getName() + "§e...");
        pruefer.pruefeSingle(ziel, ergebnis -> {
            switch (ergebnis.getStatus()) {
                case UPDATE_AVAILABLE -> sender.sendMessage(Msg.PREFIX
                        + "§a[UPDATE] §b" + ergebnis.getPluginName()
                        + " §8│ §7[" + ergebnis.getSource() + "] §c" + ergebnis.getCurrentVersion()
                        + " §8→ §a" + ergebnis.getLatestVersion());
                case MAJOR_UPDATE -> {
                    sender.sendMessage(Msg.PREFIX
                            + "§c[MAJOR] §b" + ergebnis.getPluginName()
                            + " §8│ §7[" + ergebnis.getSource() + "] §c" + ergebnis.getCurrentVersion()
                            + " §8→ §4" + ergebnis.getLatestVersion() + " §c(Major-Sprung!)");
                    sender.sendMessage(Msg.PREFIX + "§7Changelog prüfen, dann mit §f/update all -f §7laden.");
                }
                case UP_TO_DATE -> sender.sendMessage(Msg.PREFIX
                        + "§a[OK] §b" + ergebnis.getPluginName()
                        + " §7ist aktuell §8(§f" + ergebnis.getCurrentVersion()
                        + "§8) §7via " + ergebnis.getSource());
                case NOT_FOUND -> sender.sendMessage(Msg.PREFIX
                        + "§7[?] §b" + ergebnis.getPluginName()
                        + " §7nicht auf Modrinth oder Hangar gefunden.");
                case ERROR -> sender.sendMessage(Msg.PREFIX
                        + "§c[FEHLER] §b" + ergebnis.getPluginName()
                        + "§c: " + ergebnis.getErrorMessage());
            }
        });
    }

    // ── Neustart-Erinnerung ───────────────────────────────────────────────────

    private void planeNeustartErinnerung(CommandSender sender) {
        int minuten = plugin.getConfig().getInt("neustart-erinnerung-minuten", 30);
        if (minuten <= 0) return;
        if (neustartErinnerungTask != null) neustartErinnerungTask.cancel();
        long ticks = minuten * 60L * 20L;
        neustartErinnerungTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () ->
                plugin.getServer().broadcastMessage(
                        Msg.PREFIX + "§e⚠ Plugin-Updates heruntergeladen – bitte Server neu starten!"),
                ticks, ticks);
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private int zaehleZuPruefende() {
        int count = 0;
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            if (!cache.istIgnoriert(p.getName())) count++;
        }
        return count;
    }

    private void zeigeHilfe(CommandSender sender) {
        sender.sendMessage(Msg.LINE);
        sender.sendMessage(Msg.PREFIX + "§bPluginPfleger §8– Befehle §7(Alias: /pp)");
        sender.sendMessage("  §f/update check               §7– Alle Plugins prüfen");
        sender.sendMessage("  §f/update all                 §7– Normale Updates herunterladen");
        sender.sendMessage("  §f/update all -f              §7– §cAuch Major-Updates §7herunterladen");
        sender.sendMessage("  §f/update status              §7– Letzten Scan anzeigen");
        sender.sendMessage("  §f/update ignore §b<plugin>    §7– Plugin ignorieren");
        sender.sendMessage("  §f/update unignore §b<plugin>  §7– Plugin wieder prüfen");
        sender.sendMessage("  §f/update ignored             §7– Ignore-Liste anzeigen");
        sender.sendMessage("  §f/update §b<pluginname>       §7– Einzelnes Plugin prüfen");
        sender.sendMessage(Msg.LINE);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList(
                    "check", "all", "status", "ignore", "unignore", "ignored", "hilfe"));
            Arrays.stream(plugin.getServer().getPluginManager().getPlugins())
                    .map(Plugin::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .forEach(opts::add);
            return opts;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
            return List.of("-f");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("ignore")
                || args[0].equalsIgnoreCase("unignore"))) {
            return Arrays.stream(plugin.getServer().getPluginManager().getPlugins())
                    .map(Plugin::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
