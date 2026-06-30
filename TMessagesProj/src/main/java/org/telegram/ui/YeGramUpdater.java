package org.telegram.ui;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks for new yeGram releases on GitHub, downloads the APK and starts the installation.
 *
 * Releases are read from the GitHub REST API for the repository
 * https://github.com/Team-YeMax/yeGram (the "releases/latest" endpoint).
 */
public class YeGramUpdater {

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/Team-YeMax/yeGram/releases/latest";

    private YeGramUpdater() {
    }

    /**
     * Checks GitHub for a newer release. Shows a progress dialog while checking and, if a new
     * version is found, offers to download and install it. Must be called from the UI thread.
     */
    public static void checkForUpdates(final Activity activity, final Theme.ResourcesProvider resourcesProvider) {
        if (activity == null) {
            return;
        }

        final AlertDialog progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
        progressDialog.setCanCancel(true);
        progressDialog.setTitle(LocaleController.getString(R.string.YeGramChecking));
        progressDialog.show();

        new Thread(() -> {
            try {
                final String json = readUrl(LATEST_RELEASE_API);
                final JSONObject release = new JSONObject(json);
                final String latestTag = release.optString("tag_name", release.optString("name", ""));
                final String latestVersion = normalizeVersion(latestTag);
                final String currentVersion = normalizeVersion(BuildVars.BUILD_VERSION_STRING);
                final String apkUrl = findApkUrl(release);

                AndroidUtilities.runOnUIThread(() -> {
                    dismiss(progressDialog);
                    if (activity.isFinishing()) {
                        return;
                    }
                    if (!latestVersion.isEmpty() && isNewer(latestVersion, currentVersion) && apkUrl != null) {
                        showUpdateDialog(activity, resourcesProvider, latestTag, apkUrl);
                    } else {
                        showInfo(activity, resourcesProvider, LocaleController.getString(R.string.YeGramNoUpdates));
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    dismiss(progressDialog);
                    if (!activity.isFinishing()) {
                        showInfo(activity, resourcesProvider, LocaleController.getString(R.string.YeGramUpdateError));
                    }
                });
            }
        }).start();
    }

    private static void showUpdateDialog(final Activity activity, final Theme.ResourcesProvider resourcesProvider, final String version, final String apkUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.YeGramUpdateAvailableTitle));
        builder.setMessage(LocaleController.formatString(R.string.YeGramUpdateAvailableMessage, version));
        builder.setPositiveButton(LocaleController.getString(R.string.YeGramUpdateDownload),
                (dialog, which) -> downloadAndInstall(activity, resourcesProvider, apkUrl));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private static void downloadAndInstall(final Activity activity, final Theme.ResourcesProvider resourcesProvider, final String apkUrl) {
        final AlertDialog progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_LOADING, resourcesProvider);
        progressDialog.setCanCancel(true);
        progressDialog.setTitle(LocaleController.getString(R.string.YeGramDownloading));
        progressDialog.show();

        new Thread(() -> {
            try {
                final File apkFile = downloadApk(apkUrl, progressDialog);
                AndroidUtilities.runOnUIThread(() -> {
                    dismiss(progressDialog);
                    if (activity.isFinishing() || apkFile == null) {
                        return;
                    }
                    AndroidUtilities.openForView(apkFile, apkFile.getName(),
                            "application/vnd.android.package-archive", activity, resourcesProvider, false);
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    dismiss(progressDialog);
                    if (!activity.isFinishing()) {
                        showInfo(activity, resourcesProvider, LocaleController.getString(R.string.YeGramUpdateError));
                    }
                });
            }
        }).start();
    }

    private static File downloadApk(String apkUrl, final AlertDialog progressDialog) throws Exception {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            URL url = new URL(apkUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.connect();

            final int total = connection.getContentLength();
            // Use files-dir/cache so the path is exposed by the app FileProvider (see provider_paths.xml).
            File cacheDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File apkFile = new File(cacheDir, "yegram-update.apk");
            if (apkFile.exists()) {
                apkFile.delete();
            }

            input = new BufferedInputStream(connection.getInputStream());
            output = new FileOutputStream(apkFile);

            byte[] buffer = new byte[16 * 1024];
            int read;
            long downloaded = 0;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                downloaded += read;
                if (total > 0 && progressDialog != null) {
                    final int progress = (int) (downloaded * 100 / total);
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.setProgress(progress);
                        } catch (Exception ignore) {
                        }
                    });
                }
            }
            output.flush();
            return apkFile;
        } finally {
            try {
                if (output != null) output.close();
            } catch (Exception ignore) {
            }
            try {
                if (input != null) input.close();
            } catch (Exception ignore) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String findApkUrl(JSONObject release) throws JSONException {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "");
            if (name.toLowerCase().endsWith(".apk")) {
                return asset.optString("browser_download_url", null);
            }
        }
        return null;
    }

    private static String readUrl(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "yeGram-Android");
            connection.connect();

            InputStream input = new BufferedInputStream(connection.getInputStream());
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, read, "UTF-8"));
            }
            input.close();
            return sb.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        version = version.trim();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        // keep only digits and dots so "12.8.1 (6916)" -> "12.8.1"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                sb.append(c);
            } else if (c == ' ' || c == '-' || c == '(') {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Returns true if {@code latest} is a strictly higher version than {@code current}.
     */
    private static boolean isNewer(String latest, String current) {
        String[] l = latest.split("\\.");
        String[] c = current.split("\\.");
        int len = Math.max(l.length, c.length);
        for (int i = 0; i < len; i++) {
            int lv = i < l.length ? parseInt(l[i]) : 0;
            int cv = i < c.length ? parseInt(c[i]) : 0;
            if (lv != cv) {
                return lv > cv;
            }
        }
        return false;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void showInfo(Activity activity, Theme.ResourcesProvider resourcesProvider, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setMessage(message);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        builder.show();
    }

    private static void dismiss(AlertDialog dialog) {
        try {
            if (dialog != null) {
                dialog.dismiss();
            }
        } catch (Exception ignore) {
        }
    }
}
