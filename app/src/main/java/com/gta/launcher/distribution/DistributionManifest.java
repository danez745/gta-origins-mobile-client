package com.gta.launcher.distribution;

import org.json.JSONException;
import org.json.JSONObject;

public final class DistributionManifest {
    private final long clientVersionCode;
    private final String clientVersionName;
    private final long cacheVersion;
    private final String cacheUrl;
    private final String clientUrl;
    private final String cacheChecksum;
    private final String notes;

    public DistributionManifest(long clientVersionCode,
                                String clientVersionName,
                                long cacheVersion,
                                String cacheUrl,
                                String clientUrl,
                                String cacheChecksum,
                                String notes) {
        this.clientVersionCode = clientVersionCode;
        this.clientVersionName = clientVersionName;
        this.cacheVersion = cacheVersion;
        this.cacheUrl = cacheUrl;
        this.clientUrl = clientUrl;
        this.cacheChecksum = cacheChecksum;
        this.notes = notes;
    }

    public static DistributionManifest fromJson(String json) throws JSONException {
        JSONObject object = new JSONObject(json);
        return new DistributionManifest(
                object.optLong("clientVersionCode", 0L),
                object.optString("clientVersionName", ""),
                object.optLong("cacheVersion", 0L),
                object.optString("cacheUrl", ""),
                object.optString("clientUrl", ""),
                object.optString("cacheChecksum", ""),
                object.optString("notes", "")
        );
    }

    public long getClientVersionCode() {
        return clientVersionCode;
    }

    public String getClientVersionName() {
        return clientVersionName;
    }

    public long getCacheVersion() {
        return cacheVersion;
    }

    public String getCacheUrl() {
        return cacheUrl;
    }

    public String getClientUrl() {
        return clientUrl;
    }

    public String getCacheChecksum() {
        return cacheChecksum;
    }

    public String getNotes() {
        return notes;
    }

    public boolean hasCacheUrl() {
        return cacheUrl != null && !cacheUrl.isEmpty();
    }

    public boolean hasClientUrl() {
        return clientUrl != null && !clientUrl.isEmpty();
    }

    @Override
    public String toString() {
        return "DistributionManifest{" +
                "clientVersionCode=" + clientVersionCode +
                ", clientVersionName='" + clientVersionName + '\'' +
                ", cacheVersion=" + cacheVersion +
                ", cacheUrl='" + cacheUrl + '\'' +
                ", clientUrl='" + clientUrl + '\'' +
                ", cacheChecksum='" + cacheChecksum + '\'' +
                ", notes='" + notes + '\'' +
                '}';
    }
}
