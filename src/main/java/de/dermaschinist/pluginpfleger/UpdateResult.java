package de.dermaschinist.pluginpfleger;

public class UpdateResult {

    public enum Status { UPDATE_AVAILABLE, MAJOR_UPDATE, UP_TO_DATE, NOT_FOUND, ERROR }

    private final String pluginName;
    private final String currentVersion;
    private final String latestVersion;
    private final String downloadUrl;
    private final String slug;
    private final String source;
    private final Status status;
    private final String errorMessage;

    public UpdateResult(String pluginName, String currentVersion, String latestVersion,
                        String downloadUrl, String slug, String source,
                        Status status, String errorMessage) {
        this.pluginName     = pluginName;
        this.currentVersion = currentVersion;
        this.latestVersion  = latestVersion;
        this.downloadUrl    = downloadUrl;
        this.slug           = slug;
        this.source         = source;
        this.status         = status;
        this.errorMessage   = errorMessage;
    }

    // ── Factory-Methoden ──────────────────────────────────────────────────────

    public static UpdateResult updateAvailable(String pluginName, String currentVersion,
                                               String latestVersion, String downloadUrl,
                                               String slug, String source, boolean majorJump) {
        Status status = majorJump ? Status.MAJOR_UPDATE : Status.UPDATE_AVAILABLE;
        return new UpdateResult(pluginName, currentVersion, latestVersion,
                downloadUrl, slug, source, status, null);
    }

    public static UpdateResult upToDate(String pluginName, String currentVersion,
                                        String slug, String source) {
        return new UpdateResult(pluginName, currentVersion, currentVersion,
                null, slug, source, Status.UP_TO_DATE, null);
    }

    public static UpdateResult notFound(String pluginName, String currentVersion) {
        return new UpdateResult(pluginName, currentVersion, null,
                null, null, null, Status.NOT_FOUND, null);
    }

    public static UpdateResult error(String pluginName, String currentVersion, String message) {
        return new UpdateResult(pluginName, currentVersion, null,
                null, null, null, Status.ERROR, message);
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    public String getPluginName()     { return pluginName; }
    public String getCurrentVersion() { return currentVersion; }
    public String getLatestVersion()  { return latestVersion; }
    public String getDownloadUrl()    { return downloadUrl; }
    public String getSlug()           { return slug; }
    public String getSource()         { return source != null ? source : "?"; }
    public Status getStatus()         { return status; }
    public String getErrorMessage()   { return errorMessage; }

    public boolean hasUpdate()    { return status == Status.UPDATE_AVAILABLE || status == Status.MAJOR_UPDATE; }
    public boolean isMajorJump()  { return status == Status.MAJOR_UPDATE; }
}
