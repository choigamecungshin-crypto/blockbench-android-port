package com.blockbench.android;

import android.util.Log;
import android.net.Uri;

import android.content.Context;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class AndroidFS {

    private final Context context;

    public AndroidFS(Context context) {
        this.context = context;
    }

    private File resolve(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            throw new IOException("Empty path");
        }

        File filesRoot = context.getFilesDir().getCanonicalFile();
        File cacheRoot = context.getCacheDir().getCanonicalFile();

        String packageName = context.getPackageName();
        File file;

        String userPrefix = "/data/user/0/" + packageName;
        String dataPrefix = "/data/data/" + packageName;

        if (path.startsWith(userPrefix)) {
            String relative = path.substring(userPrefix.length());

            if (relative.startsWith("/files")) {
                file = new File(
                    filesRoot,
                    relative.substring("/files".length())
                );
            } else if (relative.startsWith("/cache")) {
                file = new File(
                    cacheRoot,
                    relative.substring("/cache".length())
                );
            } else {
                throw new IOException("Outside sandbox: " + path);
            }

        } else if (path.startsWith(dataPrefix)) {
            String relative = path.substring(dataPrefix.length());

            if (relative.startsWith("/files")) {
                file = new File(
                    filesRoot,
                    relative.substring("/files".length())
                );
            } else if (relative.startsWith("/cache")) {
                file = new File(
                    cacheRoot,
                    relative.substring("/cache".length())
                );
            } else {
                throw new IOException("Outside sandbox: " + path);
            }

        } else if (path.equals(filesRoot.getAbsolutePath()) ||
                   path.startsWith(filesRoot.getAbsolutePath() + File.separator)) {

            file = new File(path);

        } else if (path.equals(cacheRoot.getAbsolutePath()) ||
                   path.startsWith(cacheRoot.getAbsolutePath() + File.separator)) {

            file = new File(path);

        } else if (path.startsWith("/")) {
            throw new IOException("Outside sandbox: " + path);

        } else {
            file = new File(filesRoot, path);
        }

        file = file.getCanonicalFile();

        String filesRootPath = filesRoot.getPath();
        String cacheRootPath = cacheRoot.getPath();
        String filePath = file.getPath();

        boolean insideFiles =
            filePath.equals(filesRootPath) ||
            filePath.startsWith(filesRootPath + File.separator);

        boolean insideCache =
            filePath.equals(cacheRootPath) ||
            filePath.startsWith(cacheRootPath + File.separator);

        if (!insideFiles && !insideCache) {
            throw new IOException("Path traversal rejected: " + path);
        }

        return file;
    }

    @JavascriptInterface
    public boolean existsSync(String path) {
        try {
            return resolve(path).exists();
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean mkdirSync(String path) {
        try {
            File file = resolve(path);
            return file.mkdirs() || file.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String[] readdirSync(String path) {
        try {
            String[] list = resolve(path).list();
            return list == null ? new String[0] : list;
        } catch (Exception e) {
            return new String[0];
        }
    }

    @JavascriptInterface
    public boolean unlinkSync(String path) {
        try {
            File file = resolve(path);
            return !file.exists() || file.delete();
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String readFileBase64Sync(String path) {
        try {
            File file = resolve(path);

            Log.d("BlockbenchFS", "READ: " + path);
            Log.d("BlockbenchFS", "RESOLVED: " + file.getAbsolutePath());
            Log.d("BlockbenchFS", "EXISTS: " + file.exists());
            Log.d("BlockbenchFS", "SIZE: " + file.length());

            byte[] data = Files.readAllBytes(file.toPath());

            Log.d("BlockbenchFS", "READ BYTES: " + data.length);

            return Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e("BlockbenchFS", "READ FAILED: " + path, e);
            return "";
        }
    }

    @JavascriptInterface
    public boolean writeFileBase64Sync(String path, String base64) {
        try {
            // Android Save As / SAF mapping
            String safUriString = context
                .getSharedPreferences("blockbench_saf", Context.MODE_PRIVATE)
                .getString(path, null);

            byte[] data = Base64.decode(base64, Base64.DEFAULT);

            if (safUriString != null) {
                Uri uri = Uri.parse(safUriString);

                try (OutputStream out =
                        context.getContentResolver().openOutputStream(uri, "wt")) {

                    if (out == null) {
                        throw new IOException("Unable to open SAF output stream");
                    }

                    out.write(data);
                    out.flush();
                }

                Log.d(
                    "BlockbenchFS",
                    "SAF WRITE OK: " + uri
                );

                return true;
            }

            File file = resolve(path);

            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            Files.write(file.toPath(), data);

            return true;
        } catch (Exception e) {
            Log.e("BlockbenchFS", "WRITE FAILED: " + path, e);
            return false;
        }
    }

    @JavascriptInterface
    public long statSizeSync(String path) {
        try {
            return resolve(path).length();
        } catch (Exception e) {
            return -1;
        }
    }

    @JavascriptInterface
    public boolean isFileSync(String path) {
        try {
            return resolve(path).isFile();
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean isDirectorySync(String path) {
        try {
            return resolve(path).isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public long mtimeSync(String path) {
        try {
            return resolve(path).lastModified();
        } catch (Exception e) {
            return 0;
        }
    }

    @JavascriptInterface
    public boolean renameSync(String oldPath, String newPath) {
        try {
            File from = resolve(oldPath);
            File to = resolve(newPath);

            File parent = to.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean copyFileSync(String oldPath, String newPath) {
        try {
            File from = resolve(oldPath);
            File to = resolve(newPath);

            File parent = to.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            Files.copy(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public String userDataPath() {
        return context.getFilesDir().getAbsolutePath();
    }

    @JavascriptInterface
    public String tempPath() {
        return context.getCacheDir().getAbsolutePath();
    }
}
