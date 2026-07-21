package com.gta.launcher.distribution;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.gta.game.R;

import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class DistributionManager {
    private static final String LOG_TAG = "DistributionManager";
    private static final String PREFS = "distribution_state";
    private static final String KEY_CACHE_VERSION = "installed_cache_version";
    private static final String DEFAULT_CACHE_DIR = "/storage/emulated/0/GTA/";

    public interface ManifestCallback {
        void onSuccess(DistributionManifest manifest);
        void onError(Exception error);
    }

    public interface ProgressCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
    }

    public interface FileCallback {
        void onSuccess(File file);
        void onError(Exception error);
    }

    private final Context appContext;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private final String manifestUrl;
    private final File cacheRoot;

    public DistributionManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
        this.manifestUrl = appContext.getString(R.string.remote_distribution_manifest_url).trim();
        this.cacheRoot = new File(DEFAULT_CACHE_DIR);
    }

    public boolean hasManifestUrl() {
        return !manifestUrl.isEmpty();
    }

    public File getCacheRoot() {
        return cacheRoot;
    }

    public void fetchManifest(final ManifestCallback callback) {
        if (!hasManifestUrl()) {
            postError(callback, new IllegalStateException("Remote manifest URL is not configured"));
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = openConnection(manifestUrl);
                    String body = readString(connection.getInputStream());
                    final DistributionManifest manifest = DistributionManifest.fromJson(body);
                    postSuccess(callback, manifest);
                } catch (Exception e) {
                    postError(callback, e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    public boolean isCacheInstalled(DistributionManifest manifest) {
        long installed = getInstalledCacheVersion();
        boolean directoryReady = cacheRoot.exists() && cacheRoot.isDirectory();
        return directoryReady && installed >= manifest.getCacheVersion();
    }

    public boolean isClientUpdateAvailable(DistributionManifest manifest) {
        long currentVersion = getCurrentVersionCode();
        return manifest.getClientVersionCode() > 0L && manifest.getClientVersionCode() > currentVersion && manifest.hasClientUrl();
    }

    public long getInstalledCacheVersion() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_CACHE_VERSION, 0L);
    }

    private long getCurrentVersionCode() {
        try {
            PackageInfo info = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            //noinspection deprecation
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "Unable to read current package version", e);
            return 0L;
        }
    }

    public void setInstalledCacheVersion(long version) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_CACHE_VERSION, version)
                .apply();
    }

    public void downloadAndInstallCache(final DistributionManifest manifest,
                                        final ProgressCallback progressCallback,
                                        final FileCallback completionCallback) {
        if (!manifest.hasCacheUrl()) {
            postError(completionCallback, new IllegalStateException("Cache URL is not configured"));
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                File tempZip = null;
                try {
                    connection = openConnection(manifest.getCacheUrl());
                    long totalBytes = connection.getContentLengthLong();
                    tempZip = File.createTempFile("gta_cache_", ".zip", appContext.getCacheDir());
                    downloadToFile(connection, tempZip, totalBytes, progressCallback);
                    if (manifest.getCacheChecksum() != null && !manifest.getCacheChecksum().isEmpty()) {
                        String actualHash = sha256(tempZip);
                        if (!manifest.getCacheChecksum().equalsIgnoreCase(actualHash)) {
                            throw new IOException("Cache checksum mismatch");
                        }
                    }
                    extractZip(tempZip, cacheRoot);
                    setInstalledCacheVersion(manifest.getCacheVersion());
                    postSuccess(completionCallback, cacheRoot);
                } catch (Exception e) {
                    postError(completionCallback, e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                    if (tempZip != null && tempZip.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        tempZip.delete();
                    }
                }
            }
        });
    }

    public void downloadAndPromptInstallClient(final DistributionManifest manifest,
                                               final Activity activity,
                                               final ProgressCallback progressCallback,
                                               final FileCallback completionCallback) {
        if (!manifest.hasClientUrl()) {
            postError(completionCallback, new IllegalStateException("Client APK URL is not configured"));
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                File tempApk = null;
                try {
                    connection = openConnection(manifest.getClientUrl());
                    long totalBytes = connection.getContentLengthLong();
                    tempApk = File.createTempFile("gta_client_", ".apk", appContext.getCacheDir());
                    downloadToFile(connection, tempApk, totalBytes, progressCallback);
                    final File finalApk = tempApk;
                    postSuccess(completionCallback, finalApk);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            installApk(activity, finalApk);
                        }
                    });
                } catch (Exception e) {
                    postError(completionCallback, e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    public void installApk(Activity activity, File apkFile) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".provider",
                apkFile
        );
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(intent);
    }

    public String describeCacheState(DistributionManifest manifest) {
        return String.format(Locale.US,
                "Cache v%d installed=%s | Remote v%d",
                getInstalledCacheVersion(),
                Boolean.toString(isCacheInstalled(manifest)),
                manifest.getCacheVersion());
    }

    private void downloadToFile(HttpURLConnection connection,
                                File destination,
                                long totalBytes,
                                ProgressCallback progressCallback) throws IOException {
        byte[] buffer = new byte[8192];
        long downloaded = 0L;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             OutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (progressCallback != null && totalBytes > 0) {
                    final int percent = (int) ((downloaded * 100L) / totalBytes);
                    postProgress(progressCallback, percent, downloaded, totalBytes);
                }
            }
            output.flush();
        }
    }

    private void extractZip(File archive, File destinationDir) throws IOException {
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("Unable to create cache directory: " + destinationDir);
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destinationDir, entry.getName());
                String canonicalDest = destinationDir.getCanonicalPath() + File.separator;
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalDest)) {
                    throw new IOException("Blocked unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Unable to create directory: " + outFile);
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Unable to create directory: " + parent);
                    }
                    try (OutputStream output = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        int read;
                        while ((read = zis.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                        output.flush();
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private HttpURLConnection openConnection(String rawUrl) throws IOException {
        URL url = new URL(rawUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "GTA-Origins-Mobile-Client/1.0");
        int responseCode = connection.getResponseCode();
        if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
            throw new IOException("HTTP " + responseCode + " for " + rawUrl);
        }
        return connection;
    }

    private String readString(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        StringBuilder builder = new StringBuilder();
        try (InputStream input = new BufferedInputStream(inputStream)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read));
            }
        }
        return builder.toString();
    }

    private String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (InputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", b));
        }
        return hex.toString();
    }

    private void postProgress(final ProgressCallback callback, final int percent, final long downloadedBytes, final long totalBytes) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onProgress(percent, downloadedBytes, totalBytes);
            }
        });
    }

    private void postSuccess(final ManifestCallback callback, final DistributionManifest manifest) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(manifest);
            }
        });
    }

    private void postSuccess(final FileCallback callback, final File file) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(file);
            }
        });
    }

    private void postError(final ManifestCallback callback, final Exception error) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private void postError(final FileCallback callback, final Exception error) {
        if (callback == null) {
            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }
}
