package com.blockbench.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
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
                case "shell.openPath":
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
                 * WebView screenshot
                 */
                case "screenshot.webview":
                    return screenshotWebView(activity);

                /*
                 * Filesystem
                 */
                case "fs.existsSync":
                    return String.valueOf(resolveFile(activity, arg).exists());

                case "fs.statSync": {
                    File file = resolveFile(activity, arg);

                    StringBuilder result = new StringBuilder();
                    result.append("{");
                    result.append("\"size\":").append(file.length()).append(",");
                    result.append("\"isFile\":").append(file.isFile()).append(",");
                    result.append("\"isDirectory\":").append(file.isDirectory());
                    result.append("}");

                    return result.toString();
                }

                case "fs.readFileSync": {
                    File file = resolveFile(activity, arg);

                    byte[] data = Files.readAllBytes(file.toPath());

                    return Base64.encodeToString(
                        data,
                        Base64.NO_WRAP
                    );
                }

                case "fs.writeFileSync": {
                    int split = arg.indexOf('\n');

                    if (split < 0) {
                        return "ERROR:writeFileSync requires path and data";
                    }

                    String path = arg.substring(0, split);
                    String data = arg.substring(split + 1);

                    File file = resolveFile(activity, path);

                    File parent = file.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }

                    Files.write(
                        file.toPath(),
                        data.getBytes(StandardCharsets.UTF_8)
                    );

                    return "true";
                }

                case "fs.mkdirSync": {
                    File file = resolveFile(activity, arg);
                    file.mkdirs();
                    return "true";
                }

                case "fs.unlinkSync": {
                    File file = resolveFile(activity, arg);

                    if (file.exists() && !file.delete()) {
                        return "ERROR:Could not delete file";
                    }

                    return "true";
                }

                case "fs.renameSync": {
                    int split = arg.indexOf('\n');

                    if (split < 0) {
                        return "ERROR:renameSync requires source and destination";
                    }

                    File from = resolveFile(
                        activity,
                        arg.substring(0, split)
                    );

                    File to = resolveFile(
                        activity,
                        arg.substring(split + 1)
                    );

                    File parent = to.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }

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

                    File from = resolveFile(
                        activity,
                        arg.substring(0, split)
                    );

                    File to = resolveFile(
                        activity,
                        arg.substring(split + 1)
                    );

                    File parent = to.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }

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

    private static String screenshotWebView(Activity activity)
        throws Exception {

        if (!(activity instanceof MainActivity)) {
            throw new Exception("Activity is not MainActivity");
        }

        MainActivity mainActivity = (MainActivity) activity;

        WebView webView = mainActivity
            .getBridge()
            .getWebView();

        if (webView == null) {
            throw new Exception("WebView is null");
        }

        final Bitmap[] bitmapHolder = new Bitmap[1];

        Runnable capture = () -> {
            int width = webView.getWidth();
            int height = webView.getHeight();

            if (width <= 0 || height <= 0) {
                return;
            }

            Bitmap bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            );

            Canvas canvas = new Canvas(bitmap);
            webView.draw(canvas);

            bitmapHolder[0] = bitmap;
        };

        if (android.os.Looper.myLooper()
                == android.os.Looper.getMainLooper()) {

            capture.run();

        } else {

            final Object lock = new Object();

            activity.runOnUiThread(() -> {
                try {
                    capture.run();
                } finally {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            });

            synchronized (lock) {
                lock.wait(5000);
            }
        }

        Bitmap bitmap = bitmapHolder[0];

        if (bitmap == null) {
            throw new Exception("Could not capture WebView");
        }

        ByteArrayOutputStream output =
            new ByteArrayOutputStream();

        bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            output
        );

        bitmap.recycle();

        return "data:image/png;base64," +
            Base64.encodeToString(
                output.toByteArray(),
                Base64.NO_WRAP
            );
    }

    private static File resolveFile(
        Activity activity,
        String path
    ) throws IOException {

        if (path == null || path.isEmpty()) {
            throw new IOException("Empty path");
        }

        File root = activity
            .getFilesDir()
            .getCanonicalFile();

        File file;

        if (path.startsWith(root.getAbsolutePath())) {
            file = new File(path);

        } else if (path.startsWith("/")) {
            throw new IOException(
                "Path outside Blockbench sandbox: " + path
            );

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
            .replace("\r", "\\r");
    }

    private static void writeClipboard(
        Activity activity,
        String text
    ) {

        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager)
            activity.getSystemService(
                android.content.Context.CLIPBOARD_SERVICE
            );

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "Blockbench",
                    text
                )
            );
        }
    }

    private static String readClipboard(
        Activity activity
    ) {

        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager)
            activity.getSystemService(
                android.content.Context.CLIPBOARD_SERVICE
            );

        if (clipboard == null ||
            !clipboard.hasPrimaryClip()) {

            return "";
        }

        CharSequence text =
            clipboard.getPrimaryClip()
                .getItemAt(0)
                .coerceToText(activity);

        return text == null
            ? ""
            : text.toString();
    }

    private static void openExternal(
        Activity activity,
        String url
    ) {

        if (url == null || url.isEmpty()) {
            return;
        }

        try {
            Uri uri = Uri.parse(url);
            android.content.Intent intent =
                new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    uri
                );
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
