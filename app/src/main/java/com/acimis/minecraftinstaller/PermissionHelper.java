package com.acimis.minecraftinstaller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;
import java.util.List;

public class PermissionHelper {
    private static final String PREFS_NAME = "minecraft_installer_prefs";
    private static final String KEY_SAF_URI = "saf_uri";

    private Context context;
    private SharedPreferences prefs;

    public PermissionHelper(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasSAFPermission() {
        String savedUri = prefs.getString(KEY_SAF_URI, null);
        if (savedUri == null) return false;

        // Check if we still have permission for this URI
        List<UriPermission> permissions = context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission permission : permissions) {
            if (permission.getUri().toString().equals(savedUri) && permission.isWritePermission()) {
                return true;
            }
        }
        return false;
    }

    public void requestSAFPermission(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
        activity.startActivityForResult(intent, Constants.REQUEST_CODE_SAF_PERMISSION);
    }

    public boolean handleSAFPermissionResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_CODE_SAF_PERMISSION && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();

                // Take persistable permission
                context.getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                // Save URI
                prefs.edit().putString(KEY_SAF_URI, uri.toString()).apply();
                return true;
            }
        }
        return false;
    }

    public DocumentFile getMinecraftFolder() {
        String savedUri = prefs.getString(KEY_SAF_URI, null);
        if (savedUri == null) return null;

        try {
            Uri uri = Uri.parse(savedUri);
            DocumentFile baseFolder = DocumentFile.fromTreeUri(context, uri);

            if (baseFolder != null && baseFolder.exists()) {
                // Navigate to or create Minecraft folders
                return createMinecraftFolderStructure(baseFolder);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private DocumentFile createMinecraftFolderStructure(DocumentFile baseFolder) {
        // Create Android folder if it doesn't exist
        DocumentFile androidFolder = findOrCreateFolder(baseFolder, "Android");
        if (androidFolder == null) return null;

        // Create data folder
        DocumentFile dataFolder = findOrCreateFolder(androidFolder, "data");
        if (dataFolder == null) return null;

        // Create com.mojang.minecraftpe folder
        DocumentFile minecraftFolder = findOrCreateFolder(dataFolder, "com.mojang.minecraftpe");
        if (minecraftFolder == null) return null;

        // Create files folder
        DocumentFile filesFolder = findOrCreateFolder(minecraftFolder, "files");
        if (filesFolder == null) return null;

        // Create games folder
        DocumentFile gamesFolder = findOrCreateFolder(filesFolder, "games");
        if (gamesFolder == null) return null;

        // Create com.mojang folder
        return findOrCreateFolder(gamesFolder, "com.mojang");
    }

    private DocumentFile findOrCreateFolder(DocumentFile parent, String name) {
        // First try to find existing folder
        for (DocumentFile file : parent.listFiles()) {
            if (file.isDirectory() && name.equals(file.getName())) {
                return file;
            }
        }

        // Create new folder if not found
        return parent.createDirectory(name);
    }
}