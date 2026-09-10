package com.blockbench.android;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import androidx.activity.result.ActivityResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@CapacitorPlugin(name = "Blockbench")
public class BlockbenchPlugin extends Plugin {

    private String saveFileName = "untitled";

    // Normal Save: Android file picker
    @PluginMethod
    public void saveFile(PluginCall call) {
        String defaultName = call.getString("defaultName", "untitled");


        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, defaultName);

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        saveFileName = defaultName;

        startActivityForResult(
            call,
            intent,
            "saveFileResult"
        );
    }

    @ActivityCallback
    private void saveFileResult(
        PluginCall call,
        ActivityResult result
    ) {
        if (
            result == null ||
            result.getResultCode() != Activity.RESULT_OK
        ) {
            JSObject ret = new JSObject();
            ret.put("file", "");
            call.resolve(ret);
            return;
        }

        Intent data = result.getData();

        if (
            data == null ||
            data.getData() == null
        ) {
            JSObject ret = new JSObject();
            ret.put("file", "");
            call.resolve(ret);
            return;
        }

        Uri documentUri = data.getData();

        try {
            getActivity()
                .getContentResolver()
                .takePersistableUriPermission(
                    documentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
        } catch (Exception e) {
                "BlockbenchPlugin",
                "Could not persist file permission",
                e
            );
        }

        try {
            File exportDir = new File(
                getContext().getFilesDir(),
                "blockbench_exports"
            );

            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            File virtualFile = new File(
                exportDir,
                saveFileName
            );

            String virtualPath = virtualFile.getAbsolutePath();

            getContext()
                .getSharedPreferences(
                    "blockbench_saf",
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    virtualPath,
                    documentUri.toString()
                )
                .apply();

                "BlockbenchPlugin",
                "SAF file: " + documentUri
            );

                "BlockbenchPlugin",
                "Virtual path: " + virtualPath
            );

            JSObject ret = new JSObject();
            ret.put("file", virtualPath);

            call.resolve(ret);

        } catch (Exception e) {
                "BlockbenchPlugin",
                "Save file failed",
                e
            );

            JSObject ret = new JSObject();
            ret.put("file", "");
            call.resolve(ret);
        }
    }

    // Save As: Android folder picker
    @PluginMethod
    public void saveFolder(PluginCall call) {
        String defaultName = call.getString("defaultName", "untitled");


        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );

        saveFileName = defaultName;

        startActivityForResult(
            call,
            intent,
            "saveFolderResult"
        );
    }

    @ActivityCallback
    private void saveFolderResult(
        PluginCall call,
        ActivityResult result
    ) {
        if (
            result == null ||
            result.getResultCode() != Activity.RESULT_OK
        ) {
            JSObject ret = new JSObject();
            ret.put("file", "");
            call.resolve(ret);
            return;
        }

        Intent data = result.getData();

        if (
            data == null ||
            data.getData() == null
        ) {
            JSObject ret = new JSObject();
            ret.put("file", "");
            call.resolve(ret);
            return;
        }

        Uri treeUri = data.getData();

        try {
            getActivity()
                .getContentResolver()
                .takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
        } catch (Exception e) {
                "BlockbenchPlugin",
                "Could not persist folder permission",
                e
            );
        }

        try {
            File exportDir = new File(
                getContext().getFilesDir(),
                "blockbench_exports"
            );

            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            File virtualFile = new File(
                exportDir,
                saveFileName
            );

            String virtualPath = virtualFile.getAbsolutePath();

            // If the file already exists in the selected folder,
            // reuse its URI so Save As overwrites it instead of
            // creating "file (1).bbmodel".
            Uri documentUri = findChildDocument(
                treeUri,
                saveFileName
            );

            if (documentUri == null) {
                documentUri = createChildDocument(
                    treeUri,
                    saveFileName
                );
            } else {
                    "BlockbenchPlugin",
                    "Existing SAF file found, overwriting: " +
                    documentUri
                );
            }

            getContext()
                .getSharedPreferences(
                    "blockbench_saf",
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    virtualPath,
                    documentUri.toString()
                )
                .apply();

                "BlockbenchPlugin",
                "SAF folder: " + treeUri
            );

                "BlockbenchPlugin",
                "SAF file: " + documentUri
            );

                "BlockbenchPlugin",
                "Virtual path: " + virtualPath
            );

            JSObject ret = new JSObject();
            ret.put("file", virtualPath);

            call.resolve(ret);

        } catch (Exception e) {
                "BlockbenchPlugin",
                "Save As failed",
                e
            );

            JSObject ret = new JSObject();
            ret.put("file", "");
            call.resolve(ret);
        }
    }

    private Uri findChildDocument(
        Uri treeUri,
        String fileName
    ) {
        android.content.ContentResolver resolver =
            getContext().getContentResolver();

        android.database.Cursor cursor = null;

        try {
            String documentId =
                android.provider.DocumentsContract.getTreeDocumentId(
                    treeUri
                );

            Uri childrenUri =
                android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    documentId
                );

            cursor = resolver.query(
                childrenUri,
                new String[] {
                    android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
                },
                null,
                null,
                null
            );

            if (cursor == null) {
                return null;
            }

            int idIndex = cursor.getColumnIndex(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
            );

            int nameIndex = cursor.getColumnIndex(
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            );

            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);

                if (fileName.equals(name)) {
                    String childId = cursor.getString(idIndex);

                    return android.provider.DocumentsContract
                        .buildDocumentUriUsingTree(
                            treeUri,
                            childId
                        );
                }
            }

        } catch (Exception e) {
                "BlockbenchPlugin",
                "Could not search existing SAF file",
                e
            );
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private Uri createChildDocument(
        Uri treeUri,
        String fileName
    ) throws Exception {
        String mime = "application/octet-stream";

        if (fileName.toLowerCase().endsWith(".bbmodel")) {
            mime = "model/vnd.blockbench.bbmodel";
        }

        android.content.ContentResolver resolver =
            getContext().getContentResolver();

        // ACTION_OPEN_DOCUMENT_TREE returns a tree URI.
        // createDocument() needs the corresponding document URI.
        String documentId =
            android.provider.DocumentsContract.getTreeDocumentId(treeUri);

        Uri documentUri =
            android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                documentId
            );

            "BlockbenchPlugin",
            "Tree URI: " + treeUri
        );

            "BlockbenchPlugin",
            "Document URI: " + documentUri
        );

            "BlockbenchPlugin",
            "Document ID: " + documentId
        );

        Uri result =
            android.provider.DocumentsContract.createDocument(
                resolver,
                documentUri,
                mime,
                fileName
            );

        if (result == null) {
            throw new RuntimeException(
                "Could not create file in selected folder"
            );
        }

        return result;
    }

    public void call(PluginCall call) {
        String api = call.getString("api", "");
        String arg = call.getString("arg", "");

        if ("screenshot.webview".equals(api)) {
            try {
                WebView webView = getBridge().getWebView();
                Bitmap bitmap = Bitmap.createBitmap(
                    webView.getWidth(),
                    webView.getHeight(),
                    Bitmap.Config.ARGB_8888
                );
                Canvas canvas = new Canvas(bitmap);
                webView.draw(canvas);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                bitmap.recycle();

                JSObject ret = new JSObject();
                ret.put("result", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("WebView screenshot failed", e);
            }
            return;
        }

        if ("downloadFile".equals(api)) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(arg);

                String urlString = obj.getString("url");
                String path = obj.getString("path");


                new Thread(() -> {
                    HttpURLConnection connection = null;

                    try {
                        URL url = new URL(urlString);
                        connection = (HttpURLConnection) url.openConnection();

                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(15000);
                        connection.setReadTimeout(30000);
                        connection.setInstanceFollowRedirects(true);
                        connection.setRequestProperty(
                            "User-Agent",
                            "Blockbench Android"
                        );

                        int code = connection.getResponseCode();

                        if (code < 200 || code >= 300) {
                            throw new Exception("HTTP " + code);
                        }

                        File outFile = new File(path);
                        File parent = outFile.getParentFile();

                        if (parent != null && !parent.exists()) {
                            parent.mkdirs();
                        }

                        try (
                            InputStream in = connection.getInputStream();
                            FileOutputStream out = new FileOutputStream(outFile)
                        ) {
                            byte[] buffer = new byte[8192];
                            int len;

                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }

                            out.flush();
                        }

                            "BlockbenchPlugin",
                            "Native download OK: " + path
                        );

                        JSObject ret = new JSObject();
                        ret.put("result", new JSObject()
                            .put("path", path)
                            .put("statusCode", code)
                        );

                        call.resolve(ret);

                    } catch (Exception e) {
                            "BlockbenchPlugin",
                            "Native download failed",
                            e
                        );

                        JSObject ret = new JSObject();
                        ret.put("result", new JSObject()
                            .put("error", e.toString())
                        );

                        call.resolve(ret);

                    } finally {
                        if (connection != null) {
                            connection.disconnect();
                        }
                    }
                }).start();

                return;

            } catch (Exception e) {
                JSObject ret = new JSObject();
                ret.put("result", "ERROR:" + e);
                call.resolve(ret);
                return;
            }
        }

        String result = AndroidBridge.call(
            getActivity(),
            api,
            arg
        );

        JSObject ret = new JSObject();
        ret.put("result", result);
        call.resolve(ret);
    }

    @PluginMethod
    public void pickFile(PluginCall call) {

        boolean multiple = call.getBoolean("multiple", false);


        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);


        startActivityForResult(
            call,
            intent,
            "handleFilePicker"
        );
    }

    @ActivityCallback
    private void handleFilePicker(
        PluginCall call,
        ActivityResult result
    ) {

        if (call == null) {
            return;
        }

        if (result == null) {
        } else {
                "BlockbenchPicker",
                "resultCode = " + result.getResultCode()
            );
        }

        if (result == null ||
            result.getResultCode() != Activity.RESULT_OK ||
            result.getData() == null) {

            call.resolve(new JSObject());
            return;
        }

        Intent data = result.getData();

            "BlockbenchPicker",
            "Intent data = " + data
        );

        try {
            JSArray files = new JSArray();

            if (data.getClipData() != null) {

                for (int i = 0;
                     i < data.getClipData().getItemCount();
                     i++) {

                    Uri uri =
                        data.getClipData()
                            .getItemAt(i)
                            .getUri();

                        "BlockbenchPicker",
                        "Selected URI = " + uri
                    );

                    String copied = copyUriToSandbox(uri);

                        "BlockbenchPicker",
                        "Copied to = " + copied
                    );

                    files.put(copied);
                }

            } else {

                Uri uri = data.getData();

                if (uri != null) {
                        "BlockbenchPicker",
                        "Selected URI = " + uri
                    );

                    String copied = copyUriToSandbox(uri);

                        "BlockbenchPicker",
                        "Copied to = " + copied
                    );

                    files.put(copied);
                }
            }

                "BlockbenchPicker",
                "Returning files = " + files
            );

            JSObject ret = new JSObject();
            ret.put("files", files);

            call.resolve(ret);

        } catch (Exception e) {
                "BlockbenchPicker",
                "Picker failed",
                e
            );

            call.reject(
                "Failed to import file: " + e.getMessage(),
                e
            );
        }
    }

    private String copyUriToSandbox(Uri uri) throws Exception {

            "BlockbenchPicker",
            "copyUriToSandbox URI = " + uri
        );

        String name = getFileName(uri);

            "BlockbenchPicker",
            "Original filename = " + name
        );

        if (name == null || name.isEmpty()) {
            name = "imported_file";
        }

        File dir = new File(
            getContext().getCacheDir(),
            "blockbench_imports"
        );

        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception(
                "Cannot create import directory"
            );
        }

        File out = new File(
            dir,
            System.currentTimeMillis() + "_" + name
        );

            "BlockbenchPicker",
            "Output = " + out.getAbsolutePath()
        );

        InputStream input =
            getContext()
                .getContentResolver()
                .openInputStream(uri);

        if (input == null) {
            throw new Exception(
                "Cannot open URI: " + uri
            );
        }

        long total = 0;

        try (FileOutputStream output =
                 new FileOutputStream(out)) {

            byte[] buffer = new byte[8192];
            int length;

            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
                total += length;
            }
        }

        input.close();

            "BlockbenchPicker",
            "Copied " + total + " bytes"
        );

        return out.getAbsolutePath();
    }

    private String getFileName(Uri uri) {

        Cursor cursor =
            getContext()
                .getContentResolver()
                .query(
                    uri,
                    new String[]{
                        OpenableColumns.DISPLAY_NAME
                    },
                    null,
                    null,
                    null
                );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {

                    int index =
                        cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        );

                    if (index >= 0) {
                        return cursor.getString(index);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return null;
    }
}
