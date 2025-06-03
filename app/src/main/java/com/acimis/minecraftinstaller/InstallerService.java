package com.acimis.minecraftinstaller;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class InstallerService extends IntentService {
    private static final String ACTION_INSTALL = "com.minecraftinstaller.INSTALL";
    private static final String EXTRA_FILE_URI = "file_uri";
    private static final String EXTRA_FILE_NAME = "file_name";

    private PermissionHelper permissionHelper;

    public InstallerService() {
        super("InstallerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        permissionHelper = new PermissionHelper(this);
    }

    public static void startInstall(Context context, Uri fileUri, String fileName) {
        Intent intent = new Intent(context, InstallerService.class);
        intent.setAction(ACTION_INSTALL);
        intent.putExtra(EXTRA_FILE_URI, fileUri);
        intent.putExtra(EXTRA_FILE_NAME, fileName);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (ACTION_INSTALL.equals(intent.getAction())) {
            Uri fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
            String fileName = intent.getStringExtra(EXTRA_FILE_NAME);

            if (fileUri != null && fileName != null) {
                performInstall(fileUri, fileName);
            }
        }
    }

    private void performInstall(Uri fileUri, String fileName) {
        try {
            // Get Minecraft base folder
            DocumentFile minecraftFolder = permissionHelper.getMinecraftFolder();
            if (minecraftFolder == null) {
                sendErrorBroadcast("Could not access Minecraft folder");
                return;
            }

            // Detect file type
            FileUtils.MinecraftFileType fileType = FileUtils.detectFileType(this, fileUri);
            if (fileType == FileUtils.MinecraftFileType.UNKNOWN) {
                sendErrorBroadcast("Unsupported file type");
                return;
            }

            // Get target folder based on file type
            DocumentFile targetBaseFolder = getTargetFolder(minecraftFolder, fileType);
            if (targetBaseFolder == null) {
                sendErrorBroadcast("Could not create target folder");
                return;
            }

            // Create folder for this content
            String sanitizedName = FileUtils.sanitizeFileName(fileName);
            DocumentFile contentFolder = targetBaseFolder.createDirectory(sanitizedName);
            if (contentFolder == null) {
                sendErrorBroadcast("Could not create content folder");
                return;
            }

            // Extract zip contents
            boolean success = FileUtils.extractZipToFolder(this, fileUri, contentFolder);

            if (success) {
                sendSuccessBroadcast(fileName, fileType);
            } else {
                sendErrorBroadcast("Failed to extract content");
            }

        } catch (Exception e) {
            sendErrorBroadcast("Installation failed: " + e.getMessage());
        }
    }

    private DocumentFile getTargetFolder(DocumentFile minecraftFolder, FileUtils.MinecraftFileType fileType) {
        switch (fileType) {
            case WORLD:
                return findOrCreateFolder(minecraftFolder, "minecraftWorlds");
            case RESOURCE_PACK:
                return findOrCreateFolder(minecraftFolder, "resource_packs");
            case BEHAVIOR_PACK:
                return findOrCreateFolder(minecraftFolder, "behavior_packs");
            default:
                return null;
        }
    }

    private DocumentFile findOrCreateFolder(DocumentFile parent, String name) {
        // Try to find existing folder
        for (DocumentFile file : parent.listFiles()) {
            if (file.isDirectory() && name.equals(file.getName())) {
                return file;
            }
        }

        // Create new folder
        return parent.createDirectory(name);
    }

    private void sendSuccessBroadcast(String fileName, FileUtils.MinecraftFileType fileType) {
        Intent intent = new Intent(Constants.ACTION_INSTALL_COMPLETE);
        intent.putExtra("file_name", fileName);
        intent.putExtra("file_type", fileType.name());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendErrorBroadcast(String error) {
        Intent intent = new Intent(Constants.ACTION_INSTALL_ERROR);
        intent.putExtra("error", error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}