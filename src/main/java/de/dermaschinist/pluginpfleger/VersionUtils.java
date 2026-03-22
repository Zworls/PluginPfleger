package de.dermaschinist.pluginpfleger;

public class VersionUtils {

    /**
     * Normalisiert einen Versionsstring.
     * v1.2.3 → 1.2.3
     * 1.2.3-SNAPSHOT → 1.2.3
     * 1.8.1.2+1.21.7 → 1.8.1.2
     */
    public static String normalize(String version) {
        if (version == null) return "0";
        version = version.trim();
        version = version.replaceAll("^[vV]", "");
        version = version.split("\\+")[0];
        version = version.split("-")[0];
        return version.trim();
    }

    /**
     * Gibt true zurück wenn latest neuer als current ist.
     */
    public static boolean isNewer(String latest, String current) {
        if (latest == null || current == null) return false;
        latest  = normalize(latest);
        current = normalize(current);
        if (latest.equals(current)) return false;

        String[] lp = latest.split("\\.");
        String[] cp = current.split("\\.");
        int len = Math.max(lp.length, cp.length);

        for (int i = 0; i < len; i++) {
            int l = i < lp.length ? parseIntSafe(lp[i]) : 0;
            int c = i < cp.length ? parseIntSafe(cp[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }
        return false;
    }

    /**
     * Gibt true zurück wenn der Sprung ein Major-Update ist.
     * Beispiel: 4.2.1 → 5.0.0 = true
     *           4.2.1 → 4.3.0 = false
     */
    public static boolean isMajorJump(String latest, String current) {
        if (latest == null || current == null) return false;
        latest  = normalize(latest);
        current = normalize(current);

        int latestMajor  = getMajor(latest);
        int currentMajor = getMajor(current);

        return latestMajor > currentMajor;
    }

    private static int getMajor(String version) {
        String[] parts = version.split("\\.");
        return parts.length > 0 ? parseIntSafe(parts[0]) : 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
