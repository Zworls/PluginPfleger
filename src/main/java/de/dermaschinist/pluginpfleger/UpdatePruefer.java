package de.dermaschinist.pluginpfleger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class UpdatePruefer {

    private static final String MODRINTH_BASE = "https://api.modrinth.com/v2";
    private static final String HANGAR_BASE   = "https://hangar.papermc.io/api/v1";
    private static final String USER_AGENT    = "PluginPfleger/1.0 (DerMaschinist)";
    private static final int    TIMEOUT_MS    = 8000;

    private final PluginPfleger plugin;
    private final UpdateCache   cache;
    private final Logger        log;

    public UpdatePruefer(PluginPfleger plugin, UpdateCache cache) {
        this.plugin = plugin;
        this.cache  = cache;
        this.log    = plugin.getLogger();
    }

    // ── Alle Plugins prüfen ───────────────────────────────────────────────────

    public void pruefeAlle(CommandSender sender) {
        if (cache.isScanLaeuft()) {
            if (sender != null) sender.sendMessage(Msg.PREFIX + "§eScan läuft bereits, bitte warten...");
            return;
        }
        cache.setScanLaeuft(true);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Plugin> zuPruefen = new ArrayList<>();
            for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
                if (!cache.istIgnoriert(p.getName())) zuPruefen.add(p);
            }

            int gesamt = zuPruefen.size();
            List<UpdateResult> ergebnisse = new ArrayList<>();

            for (int i = 0; i < gesamt; i++) {
                Plugin p       = zuPruefen.get(i);
                int    aktuell = i + 1;

                if (sender != null) {
                    final String fortschritt = Msg.PREFIX + "§7[§f" + aktuell + "§7/§f" + gesamt
                            + "§7] Prüfe §b" + p.getName() + "§7...";
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> sender.sendMessage(fortschritt));
                }

                ergebnisse.add(pruefePlugin(p));
                try { Thread.sleep(350); } catch (InterruptedException ignored) {}
            }

            cache.setErgebnisse(ergebnisse);
            cache.setScanLaeuft(false);

            if (sender != null) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> zeigeZusammenfassung(sender, ergebnisse));
            } else {
                long updates = ergebnisse.stream().filter(UpdateResult::hasUpdate).count();
                if (updates > 0) {
                    log.info(updates + " Plugin-Update(s) verfügbar. /update status für Details.");
                } else {
                    log.info("Alle Plugins sind aktuell.");
                }
            }
        });
    }

    // ── Einzelnes Plugin prüfen ───────────────────────────────────────────────

    public void pruefeSingle(Plugin p, java.util.function.Consumer<UpdateResult> callback) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateResult ergebnis = pruefePlugin(p);
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(ergebnis));
        });
    }

    // ── Kern-Logik ────────────────────────────────────────────────────────────

    public UpdateResult pruefePlugin(Plugin p) {
        String name    = p.getName();
        String version = p.getDescription().getVersion();
        String slug    = zuSlug(name);

        try {
            // 1. Modrinth direkt
            UpdateResult r = pruefeModrinth(slug, name, version);
            if (r.getStatus() != UpdateResult.Status.NOT_FOUND) return r;

            // 2. Modrinth Suche
            String mSlug = sucheModrinth(name);
            if (mSlug != null) {
                r = pruefeModrinth(mSlug, name, version);
                if (r.getStatus() != UpdateResult.Status.NOT_FOUND) return r;
            }

            // 3. Hangar direkt
            r = pruefeHangar(slug, name, version);
            if (r.getStatus() != UpdateResult.Status.NOT_FOUND) return r;

            // 4. Hangar Suche
            String hSlug = sucheHangar(name);
            if (hSlug != null) {
                r = pruefeHangar(hSlug, name, version);
                if (r.getStatus() != UpdateResult.Status.NOT_FOUND) return r;
            }

            return UpdateResult.notFound(name, version);

        } catch (Exception e) {
            log.warning("Fehler bei " + name + ": " + e.getMessage());
            return UpdateResult.error(name, version, e.getMessage());
        }
    }

    // ── Zusammenfassung ───────────────────────────────────────────────────────

    private void zeigeZusammenfassung(CommandSender sender, List<UpdateResult> ergebnisse) {
        List<UpdateResult> normal  = ergebnisse.stream()
                .filter(r -> r.getStatus() == UpdateResult.Status.UPDATE_AVAILABLE).toList();
        List<UpdateResult> major   = ergebnisse.stream()
                .filter(UpdateResult::isMajorJump).toList();
        List<UpdateResult> nichtGefunden = ergebnisse.stream()
                .filter(r -> r.getStatus() == UpdateResult.Status.NOT_FOUND).toList();
        List<UpdateResult> fehler  = ergebnisse.stream()
                .filter(r -> r.getStatus() == UpdateResult.Status.ERROR).toList();

        sender.sendMessage(Msg.LINE);

        if (normal.isEmpty() && major.isEmpty()) {
            sender.sendMessage(Msg.PREFIX + "§aAlle Plugins sind aktuell!");
        } else {
            if (!normal.isEmpty()) {
                sender.sendMessage(Msg.PREFIX + "§e" + normal.size() + " Update(s) verfügbar:");
                sender.sendMessage("");
                for (UpdateResult r : normal) {
                    sender.sendMessage("  §a▶ §b" + r.getPluginName()
                            + " §8│ §7[" + r.getSource() + "] §c" + r.getCurrentVersion()
                            + " §8→ §a" + r.getLatestVersion());
                }
            }
            if (!major.isEmpty()) {
                sender.sendMessage("");
                sender.sendMessage(Msg.PREFIX + "§c" + major.size() + " Major-Update(s) §7(nicht automatisch)§c:");
                sender.sendMessage("");
                for (UpdateResult r : major) {
                    sender.sendMessage("  §c⚠ §b" + r.getPluginName()
                            + " §8│ §7[" + r.getSource() + "] §c" + r.getCurrentVersion()
                            + " §8→ §4" + r.getLatestVersion()
                            + " §c(Major-Sprung!)");
                }
                sender.sendMessage("");
                sender.sendMessage(Msg.PREFIX + "§7Major-Updates: §f/update all -f §7zum Erzwingen"
                        + " oder §f/update <plugin> §7für einzelne.");
            }
            sender.sendMessage("");
            sender.sendMessage(Msg.PREFIX + "§7Tipp: §f/update all §7lädt normale Updates nach §fplugins/updates/");
        }

        if (!nichtGefunden.isEmpty()) {
            sender.sendMessage("");
            sender.sendMessage(Msg.PREFIX + "§7Nicht gefunden §8(" + nichtGefunden.size() + ")§7: "
                    + nichtGefunden.stream().map(UpdateResult::getPluginName)
                    .reduce((a, b) -> a + ", " + b).orElse(""));
        }
        if (!fehler.isEmpty()) {
            sender.sendMessage(Msg.PREFIX + "§cFehler bei §f" + fehler.size() + " §cPlugins – siehe Konsole.");
        }
        sender.sendMessage(Msg.LINE);
    }

    // ── Modrinth ──────────────────────────────────────────────────────────────

    private UpdateResult pruefeModrinth(String slug, String name, String version) throws IOException {
        String mcVersion = getMcVersion();
        String url = MODRINTH_BASE + "/project/" + URLEncoder.encode(slug, StandardCharsets.UTF_8)
                + "/version?game_versions=[%22" + mcVersion + "%22]"
                + "&loaders=[%22paper%22,%22spigot%22,%22bukkit%22,%22purpur%22,%22folia%22]";

        String antwort = get(url);
        if (antwort == null || antwort.equals("[]") || antwort.startsWith("{\"error")) {
            return UpdateResult.notFound(name, version);
        }

        JsonArray versionen = JsonParser.parseString(antwort).getAsJsonArray();
        if (versionen.isEmpty()) return UpdateResult.notFound(name, version);

        JsonObject neueste       = versionen.get(0).getAsJsonObject();
        String     neuesteVersion = neueste.get("version_number").getAsString();
        String     downloadUrl   = extrahiereModrinthDownload(neueste);

        if (VersionUtils.isNewer(neuesteVersion, version)) {
            boolean major = VersionUtils.isMajorJump(neuesteVersion, version);
            return UpdateResult.updateAvailable(name, version, neuesteVersion, downloadUrl, slug, "Modrinth", major);
        }
        return UpdateResult.upToDate(name, version, slug, "Modrinth");
    }

    private String extrahiereModrinthDownload(JsonObject version) {
        JsonArray dateien = version.getAsJsonArray("files");
        if (dateien == null || dateien.isEmpty()) return null;
        JsonObject primaer = null;
        for (JsonElement fe : dateien) {
            JsonObject f = fe.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) { primaer = f; break; }
        }
        if (primaer == null) primaer = dateien.get(0).getAsJsonObject();
        return primaer.has("url") ? primaer.get("url").getAsString() : null;
    }

    private String sucheModrinth(String pluginName) throws IOException {
        String antwort = get(MODRINTH_BASE + "/search?query="
                + URLEncoder.encode(pluginName, StandardCharsets.UTF_8)
                + "&facets=[[%22project_type:plugin%22]]&limit=3");
        if (antwort == null) return null;

        JsonArray treffer = JsonParser.parseString(antwort).getAsJsonObject().getAsJsonArray("hits");
        if (treffer == null || treffer.isEmpty()) return null;

        String klein = pluginName.toLowerCase();
        for (JsonElement hit : treffer) {
            JsonObject h    = hit.getAsJsonObject();
            String titel    = h.get("title").getAsString().toLowerCase();
            String hitSlug  = h.get("slug").getAsString();
            if (titel.equals(klein) || hitSlug.equals(klein)
                    || titel.replace("-", "").equals(klein.replace("-", ""))) return hitSlug;
        }
        return null;
    }

    // ── Hangar ────────────────────────────────────────────────────────────────

    private UpdateResult pruefeHangar(String slug, String name, String version) throws IOException {
        String neuste = get(HANGAR_BASE + "/projects/"
                + URLEncoder.encode(slug, StandardCharsets.UTF_8) + "/latestrelease");
        if (neuste == null || neuste.isBlank() || neuste.startsWith("{")) {
            return UpdateResult.notFound(name, version);
        }
        neuste = neuste.replace("\"", "").trim();

        String downloadUrl = HANGAR_BASE + "/projects/" + slug + "/versions/"
                + URLEncoder.encode(neuste, StandardCharsets.UTF_8) + "/PAPER/download";

        if (VersionUtils.isNewer(neuste, version)) {
            boolean major = VersionUtils.isMajorJump(neuste, version);
            return UpdateResult.updateAvailable(name, version, neuste, downloadUrl, slug, "Hangar", major);
        }
        return UpdateResult.upToDate(name, version, slug, "Hangar");
    }

    private String sucheHangar(String pluginName) throws IOException {
        String antwort = get(HANGAR_BASE + "/projects?query="
                + URLEncoder.encode(pluginName, StandardCharsets.UTF_8) + "&limit=3&platform=PAPER");
        if (antwort == null) return null;

        JsonObject obj;
        try { obj = JsonParser.parseString(antwort).getAsJsonObject(); }
        catch (Exception e) { return null; }

        JsonArray ergebnis = obj.has("result") ? obj.getAsJsonArray("result") : null;
        if (ergebnis == null || ergebnis.isEmpty()) return null;

        String klein = pluginName.toLowerCase();
        for (JsonElement el : ergebnis) {
            JsonObject ns = el.getAsJsonObject().has("namespace")
                    ? el.getAsJsonObject().getAsJsonObject("namespace") : null;
            if (ns == null || !ns.has("slug")) continue;
            String slug = ns.get("slug").getAsString().toLowerCase();
            if (slug.equals(klein) || slug.replace("-", "").equals(klein.replace("-", ""))) {
                return ns.get("slug").getAsString();
            }
        }
        return null;
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String get(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        int code = conn.getResponseCode();
        if (code == 404 || code == 400 || code == 422) return null;

        try (InputStream is = conn.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        }
    }

    private String getMcVersion() {
        return plugin.getServer().getBukkitVersion().split("-")[0];
    }

    private String zuSlug(String name) {
        return name.toLowerCase()
                .replace(" ", "-")
                .replace("_", "-")
                .replaceAll("[^a-z0-9\\-]", "");
    }
}
