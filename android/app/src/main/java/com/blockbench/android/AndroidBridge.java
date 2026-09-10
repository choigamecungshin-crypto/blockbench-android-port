package com.blockbench.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class AndroidBridge {

    private AndroidBridge() {}

    public static String call(Activity activity, String api, String arg) {

        if (api == null) api = "";
        if (arg == null) arg = "";

        try {
            switch (api) {

                case "app.getVersion":
                    return activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0)
                        .versionName;

                case "app.quit":
                    activity.finishAffinity();
                    return "true";

                case "clipboard.writeText":
                    writeClipboard(activity, arg);
                    return "true";

                case "clipboard.readText":
                    return readClipboard(activity);

                case "shell.openExternal":
                    openExternal(activity, arg);
                    return "true";

                case "shell.openPath":
                    openExternal(activity, arg);
                    return "true";

                case "shell.showItemInFolder":
                    openExternal(activity, arg);
                    return "true";

                case "android.version":
                    return Build.VERSION.RELEASE;

                case "android.sdk":
                    return String.valueOf(Build.VERSION.SDK_INT);

                case "android.model":
                    return Build.MODEL;

                case "android.manufacturer":
                    return Build.MANUFACTURER;

                case "process.pid":
                    return String.valueOf(android.os.Process.myPid());

                case "process.platform":
                case "os.platform":
                    return "android";

                case "os.arch":
                    return Build.SUPPORTED_ABIS.length > 0
                        ? Build.SUPPORTED_ABIS[0]
                        : "unknown";

                case "os.version":
                    return System.getProperty("os.version", "");

                case "os.homedir":
                case "os.userData":
                    return activity.getFilesDir().getAbsolutePath();

                case "os.tmpdir":
                    return activity.getCacheDir().getAbsolutePath();

                /*
                 * Filesystem
                 */

                case "fs.existsSync":
                    return String.valueOf(resolveFile(activity, arg).exists());

                case "fs.mkdirSync": {
                    File file = resolveFile(activity, arg);
                    return String.valueOf(file.mkdirs() || file.isDirectory());
                }

                case "fs.readdirSync": {
                    File file = resolveFile(activity, arg);
                    String[] list = file.list();
                    if (list == null) return "[]";

                    StringBuilder out = new StringBuilder("[");
                    for (int i = 0; i < list.length; i++) {
                        if (i > 0) out.append(",");
                        out.append("\"")
                           .append(escapeJson(list[i]))
                           .append("\"");
                    }
                    out.append("]");
                    return out.toString();
                }

                case "fs.unlinkSync": {
                    File file = resolveFile(activity, arg);
                    return String.valueOf(!file.exists() || file.delete());
                }

                case "fs.readFileSync": {
                    File file = resolveFile(activity, arg);
                    byte[] data = Files.readAllBytes(file.toPath());
                    return Base64.encodeToString(data, Base64.NO_WRAP);
                }

                case "fs.writeFileSync": {
                    /*
                     * arg format:
                     * PATH\nBASE64_DATA
                     */
                    int split = arg.indexOf('\n');

                    if (split < 0) {
                        return "ERROR:writeFileSync requires path and base64 data";
                    }

                    String path = arg.substring(0, split);
                    String base64 = arg.substring(split + 1);

                    File file = resolveFile(activity, path);

                    File parent = file.getParentFile();
                    if (parent != null) parent.mkdirs();

                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    Files.write(file.toPath(), data);

                    return "true";
                }

                case "fs.statSync": {
                    File file = resolveFile(activity, arg);

                    if (!file.exists()) {
                        return "ERROR:ENOENT";
                    }

                    return "{"
                        + "\"size\":" + file.length() + ","
                        + "\"isFile\":" + file.isFile() + ","
                        + "\"isDirectory\":" + file.isDirectory() + ","
                        + "\"mtime\":" + file.lastModified()
                        + "}";
                }

                case "fs.renameSync": {
                    int split = arg.indexOf('\n');

                    if (split < 0) {
                        return "ERROR:renameSync requires source and destination";
                    }

                    File from = resolveFile(activity, arg.substring(0, split));
                    File to = resolveFile(activity, arg.substring(split + 1));

                    File parent = to.getParentFile();
                    if (parent != null) parent.mkdirs();

                    Files.move(
                        from.toPath(),
                        to.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    );

                    return "true";
                }

                case "fs.copyFileSync": {
                    int split = arg.indexOf('\n');

                    if (split < 0) {
                        return "ERROR:copyFileSync requires source and destination";
                    }

                    File from = resolveFile(activity, arg.substring(0, split));
                    File to = resolveFile(activity, arg.substring(split + 1));

                    File parent = to.getParentFile();
                    if (parent != null) parent.mkdirs();

                    Files.copy(
                        from.toPath(),
                        to.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    );

                    return "true";
                }

                default:
                    return "ERROR:Unknown API " + api;
            }

        } catch (Exception e) {
            return "ERROR:" + e.toString();
        }
    }

    /*
     * Convert a JS path into an app-sandbox path.
     *
     * Absolute Android paths outside the sandbox are rejected.
     */
    private static File resolveFile(Activity activity, String path)
        throws IOException {

        if (path == null || path.isEmpty()) {
            throw new IOException("Empty path");
        }

        File root = activity.getFilesDir().getCanonicalFile();

        File file;

        if (path.startsWith(root.getAbsolutePath())) {
            file = new File(path);
        } else if (path.startsWith("/")) {
            throw new IOException("Path outside Blockbench sandbox: " + path);
        } else {
            file = new File(root, path);
        }

        file = file.getCanonicalFile();

        String rootPath = root.getPath();
        String filePath = file.getPath();

        if (!filePath.equals(rootPath) &&
            !filePath.startsWith(rootPath + File.separator)) {
            throw new IOException("Path traversal rejected");
        }

        return file;
    }

    private static String escapeJson(String text) {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static void writeClipboard(Activity activity, String text) {

        ClipboardManager manager =
            (ClipboardManager)
            activity.getSystemService(Context.CLIPBOARD_SERVICE);

        manager.setPrimaryClip(
            ClipData.newPlainText("Blockbench", text)
        );
    }

    private static String readClipboard(Activity activity) {

        ClipboardManager manager =
            (ClipboardManager)
            activity.getSystemService(Context.CLIPBOARD_SERVICE);

        if (!manager.hasPrimaryClip()) return "";

        ClipData data = manager.getPrimaryClip();

        if (data == null || data.getItemCount() == 0) return "";

        CharSequence text =
            data.getItemAt(0).coerceToText(activity);

        return text == null ? "" : text.toString();
    }

    private static void openExternal(Activity activity, String url) {

        Intent intent;

        if (url.startsWith("/")) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("file://" + url));
        } else {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        }

        activity.startActivity(intent);
    }
}
