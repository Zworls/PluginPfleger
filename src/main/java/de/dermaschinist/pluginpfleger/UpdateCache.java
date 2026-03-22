package de.dermaschinist.pluginpfleger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UpdateCache {

    private final PluginPfleger plugin;
    private List<UpdateResult> letzteErgebnisse = new ArrayList<>();
    private LocalDateTime      letzterScan      = null;
    private boolean            scanLaeuft       = false;

    public UpdateCache(PluginPfleger plugin) {
        this.plugin = plugin;
    }

    // ── Scan-Ergebnisse ───────────────────────────────────────────────────────

    public void setErgebnisse(List<UpdateResult> ergebnisse) {
        this.letzteErgebnisse = new ArrayList<>(ergebnisse);
        this.letzterScan      = LocalDateTime.now();
    }

    public List<UpdateResult> getLetzteErgebnisse() {
        return Collections.unmodifiableList(letzteErgebnisse);
    }

    public boolean hatErgebnisse()       { return !letzteErgebnisse.isEmpty(); }
    public boolean isScanLaeuft()        { return scanLaeuft; }
    public void setScanLaeuft(boolean b) { scanLaeuft = b; }

    public String getLetzterScanFormatiert() {
        if (letzterScan == null) return "noch kein Scan";
        return letzterScan.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }

    public long getAnzahlUpdates() {
        return letzteErgebnisse.stream().filter(UpdateResult::hasUpdate).count();
    }

    public long getAnzahlNichtGefunden() {
        return letzteErgebnisse.stream()
                .filter(r -> r.getStatus() == UpdateResult.Status.NOT_FOUND).count();
    }

    public long getAnzahlMajorUpdates() {
        return letzteErgebnisse.stream()
                .filter(UpdateResult::isMajorJump).count();
    }

    // ── Ignore-Liste ──────────────────────────────────────────────────────────

    public List<String> getIgnorierteListe() {
        return plugin.getConfig().getStringList("ignorierte-plugins");
    }

    public boolean istIgnoriert(String pluginName) {
        return getIgnorierteListe().stream()
                .anyMatch(s -> s.equalsIgnoreCase(pluginName));
    }

    public boolean hinzufuegenIgnoriert(String pluginName) {
        List<String> liste = new ArrayList<>(getIgnorierteListe());
        if (liste.stream().anyMatch(s -> s.equalsIgnoreCase(pluginName))) return false;
        liste.add(pluginName);
        plugin.getConfig().set("ignorierte-plugins", liste);
        plugin.saveConfig();
        return true;
    }

    public boolean entfernenIgnoriert(String pluginName) {
        List<String> liste = new ArrayList<>(getIgnorierteListe());
        boolean entfernt = liste.removeIf(s -> s.equalsIgnoreCase(pluginName));
        if (entfernt) {
            plugin.getConfig().set("ignorierte-plugins", liste);
            plugin.saveConfig();
        }
        return entfernt;
    }
}
