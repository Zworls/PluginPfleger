package de.dermaschinist.pluginpfleger;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class PluginPfleger extends JavaPlugin {

    private UpdatePruefer   pruefer;
    private UpdateCache     cache;
    private PermissionHelper permissionHelper;
    private BukkitTask      geplanterScan;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        cache            = new UpdateCache(this);
        permissionHelper = new PermissionHelper(this);
        pruefer          = new UpdatePruefer(this, cache);

        PflegerBefehl befehl = new PflegerBefehl(this, pruefer, cache, permissionHelper);
        getCommand("update").setExecutor(befehl);
        getCommand("update").setTabCompleter(befehl);

        // Duplikate beim Start prüfen
        pruefeDuplikate();

        // Scan beim Start
        if (getConfig().getBoolean("scan-beim-start", true)) {
            getServer().getScheduler().runTaskLater(this, () -> {
                getLogger().info("Starte automatischen Scan beim Start...");
                pruefer.pruefeAlle(null);
            }, 100L);
        }

        // Geplanter Scan
        int stunden = getConfig().getInt("scan-intervall-stunden", 6);
        if (stunden > 0) {
            long ticks = stunden * 60L * 60L * 20L;
            geplanterScan = getServer().getScheduler().runTaskTimer(this, () -> {
                getLogger().info("Geplanter Scan läuft...");
                pruefer.pruefeAlle(null);
            }, ticks, ticks);
        }

        getLogger().info("PluginPfleger aktiviert! /update oder /pp zum Starten.");
    }

    @Override
    public void onDisable() {
        if (geplanterScan != null) geplanterScan.cancel();
        getLogger().info("PluginPfleger deaktiviert.");
    }

    private void pruefeDuplikate() {
        Plugin[] plugins = getServer().getPluginManager().getPlugins();
        List<String> namen = new ArrayList<>();
        for (Plugin p : plugins) {
            String klein = p.getName().toLowerCase();
            if (namen.contains(klein)) {
                getLogger().warning("⚠ DUPLIKAT: Plugin '" + p.getName()
                        + "' ist mehrfach geladen! Bitte prüfen!");
            }
            namen.add(klein);
        }
    }

    public UpdatePruefer getPruefer()           { return pruefer; }
    public UpdateCache getCache()               { return cache; }
    public PermissionHelper getPermissionHelper() { return permissionHelper; }
}
