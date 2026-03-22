package de.dermaschinist.pluginpfleger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Herunterlader {

    private static final String USER_AGENT = "PluginPfleger/1.0 (DerMaschinist)";

    private final PluginPfleger plugin;

    public Herunterlader(PluginPfleger plugin) {
        this.plugin = plugin;
    }

    public enum Modus { MANUELL, AUTO_ERSETZEN }

    public static class Ergebnis {
        public final boolean erfolg;
        public final String  nachricht;
        public final File    datei;

        public Ergebnis(boolean erfolg, String nachricht, File datei) {
            this.erfolg    = erfolg;
            this.nachricht = nachricht;
            this.datei     = datei;
        }
    }

    public Ergebnis herunterladen(UpdateResult result, Modus modus) {
        if (result.getDownloadUrl() == null) {
            return new Ergebnis(false, "§cKein Download-Link verfügbar.", null);
        }

        File   pluginsOrdner = plugin.getDataFolder().getParentFile();
        String dateiname     = result.getPluginName() + "-" + result.getLatestVersion() + ".jar";

        if (modus == Modus.MANUELL) {
            File updatesOrdner = new File(pluginsOrdner, "updates");
            updatesOrdner.mkdirs();
            File ziel = new File(updatesOrdner, dateiname);
            try {
                ladenZuDatei(result.getDownloadUrl(), ziel);
                return new Ergebnis(true,
                        "§aGespeichert: §fplugins/updates/" + dateiname, ziel);
            } catch (IOException e) {
                return new Ergebnis(false, "§cDownload fehlgeschlagen: " + e.getMessage(), null);
            }
        }

        // AUTO_ERSETZEN: erst Backup, dann ersetzen
        File alteJar = findeAktuelleJar(result.getPluginName(), pluginsOrdner);
        if (alteJar != null) {
            File   backupOrdner = new File(pluginsOrdner, "backups");
            backupOrdner.mkdirs();
            String zeitstempel  = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            File   backup       = new File(backupOrdner, alteJar.getName() + "." + zeitstempel + ".bak");
            try {
                Files.copy(alteJar.toPath(), backup.toPath());
            } catch (IOException e) {
                return new Ergebnis(false, "§cBackup fehlgeschlagen: " + e.getMessage(), null);
            }
        }

        File ziel = new File(pluginsOrdner, dateiname);
        try {
            ladenZuDatei(result.getDownloadUrl(), ziel);
            if (alteJar != null && !alteJar.equals(ziel)) alteJar.delete();
            return new Ergebnis(true,
                    "§aErsetzt! Backup in §fplugins/backups/§a. §eNeustart nötig!", ziel);
        } catch (IOException e) {
            // Backup wiederherstellen
            if (alteJar != null) {
                File backupOrdner = new File(pluginsOrdner, "backups");
                File[] backups = backupOrdner.listFiles((d, n) -> n.startsWith(alteJar.getName()));
                if (backups != null && backups.length > 0) {
                    try { Files.copy(backups[backups.length - 1].toPath(), alteJar.toPath()); }
                    catch (IOException ignored) {}
                }
            }
            return new Ergebnis(false, "§cDownload fehlgeschlagen: " + e.getMessage(), null);
        }
    }

    private void ladenZuDatei(String urlStr, File ziel) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(ziel)) {
            byte[] puffer = new byte[8192];
            int n;
            while ((n = in.read(puffer)) != -1) out.write(puffer, 0, n);
        }
    }

    private File findeAktuelleJar(String pluginName, File pluginsOrdner) {
        File[] jars = pluginsOrdner.listFiles((d, n) ->
                n.toLowerCase().startsWith(pluginName.toLowerCase()) && n.endsWith(".jar"));
        if (jars == null || jars.length == 0) return null;
        return jars[0];
    }
}
