package com.acimis.minecraftinstaller;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.button.MaterialButton;
import com.acimis.minecraftinstaller.R;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private static final String MINECRAFT_PACKAGE = "com.mojang.minecraftpe";
    private static final String MINECRAFT_WORLDS_PATH = "minecraftWorlds";
    private static final String MINECRAFT_RESOURCE_PACKS_PATH = "resource_packs";

    private ActivityResultLauncher<Intent> openDocumentLauncher;
    private ActivityResultLauncher<Intent> openDirectoryLauncher;
    private Uri minecraftDirectoryUri;
    private Uri pendingFileUri; // Store the file URI when waiting for directory access
    private ProgressBar progressBar;
    private TextView statusText;
    private MaterialButton installButton;
    private MaterialButton setupButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        installButton = findViewById(R.id.installButton);
        setupButton = findViewById(R.id.setupButton);

        installButton.setOnClickListener(v -> openFilePicker());
        setupButton.setOnClickListener(v -> requestMinecraftDirectoryAccess());

        setupActivityResultLaunchers();
    }

    private void setupActivityResultLaunchers() {
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleSelectedFile(uri);
                        }
                    }
                }
        );

        openDirectoryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        minecraftDirectoryUri = result.getData().getData();
                        if (minecraftDirectoryUri != null) {
                            updateStatus("Directory selected successfully!");
                            getContentResolver().takePersistableUriPermission(
                                    minecraftDirectoryUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                            updateStatus("Minecraft directory access granted!");
                            
                            // Now process the pending file if there is one
                            if (pendingFileUri != null) {
                                Uri fileToProcess = pendingFileUri;
                                pendingFileUri = null; // Clear the pending file
                                handleSelectedFile(fileToProcess);
                            }
                        } else {
                            showError("No directory selected. Please try again.");
                        }
                    } else {
                        // User cancelled directory selection
                        pendingFileUri = null;
                        updateStatus("Directory selection cancelled");
                    }
                }
        );
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        String[] mimeTypes = {
                "application/x-minecraft-pack",   // .mcpack
                "application/x-minecraft-world",  // .mcworld
                "application/zip",                // Fallback
                "application/x-zip-compressed",   // Fallback
                "application/octet-stream"        // Generic binary
        };

        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.putExtra(Intent.EXTRA_TITLE, "Select Minecraft File");

        openDocumentLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        String fileName = getFileName(uri);
        if (fileName == null) {
            showError("Could not read file name");
            return;
        }

        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".mcworld")) {
            extractAndInstallWorld(uri);
        } else if (lowerFileName.endsWith(".mcpack")) {
            extractAndInstallResourcePack(uri);
        } else {
            showError("Please select a .mcworld or .mcpack file");
        }
    }

    private String getFileName(Uri uri) {
        DocumentFile documentFile = DocumentFile.fromSingleUri(this, uri);
        return documentFile != null ? documentFile.getName() : null;
    }

    private void extractAndInstallWorld(Uri uri) {
        if (minecraftDirectoryUri == null) {
            pendingFileUri = uri; // Store the file URI for later processing
            updateStatus("Need directory access - launching folder picker...");
            requestMinecraftDirectoryAccess();
            return;
        }

        try {
            showProgress("Installing world...");
            DocumentFile minecraftDir = DocumentFile.fromTreeUri(this, minecraftDirectoryUri);
            
            if (minecraftDir == null) {
                showError("Could not access Minecraft directory. Please try setting up the folder again.");
                return;
            }
            
            // Navigate to or create the minecraftWorlds folder
            DocumentFile worldsDir = findOrCreateFolder(minecraftDir, MINECRAFT_WORLDS_PATH);
            if (worldsDir == null) {
                showError("Could not access or create minecraftWorlds folder");
                return;
            }

            String worldName = getFileName(uri).replace(".mcworld", "");
            // Sanitize the world name
            worldName = sanitizeFileName(worldName);
            
            // Check if world already exists and create a unique name
            String finalWorldName = getUniqueFolderName(worldsDir, worldName);
            DocumentFile worldDir = worldsDir.createDirectory(finalWorldName);
            
            if (worldDir == null) {
                showError("Could not create world folder");
                return;
            }

            updateStatus("Extracting world files...");
            extractZipFile(uri, worldDir);
            updateStatus("World '" + finalWorldName + "' installed successfully!");
        } catch (IOException e) {
            showError("Error installing world: " + e.getMessage());
        } finally {
            hideProgress();
        }
    }

    private void extractAndInstallResourcePack(Uri uri) {
        if (minecraftDirectoryUri == null) {
            pendingFileUri = uri; // Store the file URI for later processing
            updateStatus("Need directory access - launching folder picker...");
            requestMinecraftDirectoryAccess();
            return;
        }

        try {
            showProgress("Installing resource pack...");
            DocumentFile minecraftDir = DocumentFile.fromTreeUri(this, minecraftDirectoryUri);
            
            if (minecraftDir == null) {
                showError("Could not access Minecraft directory. Please try setting up the folder again.");
                return;
            }
            
            // Navigate to or create the resource_packs folder
            DocumentFile resourcePacksDir = findOrCreateFolder(minecraftDir, MINECRAFT_RESOURCE_PACKS_PATH);
            if (resourcePacksDir == null) {
                showError("Could not access or create resource_packs folder");
                return;
            }

            String packName = getFileName(uri).replace(".mcpack", "");
            // Sanitize the pack name
            packName = sanitizeFileName(packName);
            
            // Check if pack already exists and create a unique name
            String finalPackName = getUniqueFolderName(resourcePacksDir, packName);
            DocumentFile packDir = resourcePacksDir.createDirectory(finalPackName);
            
            if (packDir == null) {
                showError("Could not create resource pack folder");
                return;
            }

            updateStatus("Extracting resource pack files...");
            extractZipFile(uri, packDir);
            updateStatus("Resource pack '" + finalPackName + "' installed successfully!");
        } catch (IOException e) {
            showError("Error installing resource pack: " + e.getMessage());
        } finally {
            hideProgress();
        }
    }

    private DocumentFile findOrCreateFolder(DocumentFile parent, String folderName) {
        // First try to find existing folder
        for (DocumentFile file : parent.listFiles()) {
            if (file.isDirectory() && folderName.equals(file.getName())) {
                return file;
            }
        }
        
        // Create new folder if not found
        return parent.createDirectory(folderName);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";
        // Replace invalid characters with underscores
        return fileName.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
    }

    private String getUniqueFolderName(DocumentFile parent, String baseName) {
        String name = baseName;
        int counter = 1;
        
        // Check if folder already exists
        while (folderExists(parent, name)) {
            name = baseName + "_" + counter;
            counter++;
        }
        
        return name;
    }

    private boolean folderExists(DocumentFile parent, String folderName) {
        for (DocumentFile file : parent.listFiles()) {
            if (file.isDirectory() && folderName.equals(file.getName())) {
                return true;
            }
        }
        return false;
    }

    private void extractZipFile(Uri zipUri, DocumentFile destinationDir) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(zipUri);
             ZipArchiveInputStream zipStream = new ZipArchiveInputStream(inputStream)) {

            ZipArchiveEntry entry;
            while ((entry = zipStream.getNextZipEntry()) != null) {
                if (!entry.isDirectory()) {
                    // Handle nested directories in the zip
                    String entryName = entry.getName();
                    DocumentFile targetFile = createNestedFile(destinationDir, entryName);
                    
                    if (targetFile != null) {
                        try (OutputStream outputStream = getContentResolver().openOutputStream(targetFile.getUri())) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = zipStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, len);
                            }
                        }
                    }
                }
            }
        }
    }

    private DocumentFile createNestedFile(DocumentFile baseDir, String relativePath) {
        String[] pathParts = relativePath.split("/");
        DocumentFile currentDir = baseDir;
        
        // Create directories for all parts except the last one
        for (int i = 0; i < pathParts.length - 1; i++) {
            String dirName = pathParts[i];
            if (!dirName.isEmpty()) {
                currentDir = findOrCreateFolder(currentDir, dirName);
                if (currentDir == null) return null;
            }
        }
        
        // Create the file
        String fileName = pathParts[pathParts.length - 1];
        return currentDir.createFile("application/octet-stream", fileName);
    }

    private void requestMinecraftDirectoryAccess() {
        updateStatus("Starting directory selection process...");
        
        // Samsung-specific handling for tablets
        String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();
        boolean isSamsung = manufacturer.contains("samsung");
        boolean isTablet = getResources().getConfiguration().screenLayout >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE;
        
        if (isSamsung && isTablet) {
            updateStatus("Samsung tablet detected - using optimized approach");
            // Try Samsung-specific approach first
            trySamsungTabletApproach();
            return;
        }
        
        // Use a simpler approach - just launch the document tree picker without complex setup
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        
        // Don't set initial URI - let user navigate from root
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        
        updateStatus("Please navigate to: Android/data/com.mojang.minecraftpe/files/games/com.mojang");
        showError("Select the 'com.mojang' folder inside Minecraft");
        
        try {
            openDirectoryLauncher.launch(intent);
        } catch (Exception e) {
            updateStatus("Error launching picker: " + e.getMessage());
            showError("Failed to launch directory picker. Please try again.");
        }
    }
    
    private void trySamsungTabletApproach() {
        // Samsung tablets often need a different approach
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        
        // Try to set initial path to internal storage
        try {
            Uri initialUri = DocumentsContract.buildRootUri(
                    "com.android.externalstorage.documents",
                    "primary"
            );
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
            updateStatus("Set initial path to internal storage");
        } catch (Exception e) {
            updateStatus("Could not set initial path: " + e.getMessage());
        }
        
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        
        updateStatus("Samsung Tablet: Navigate to Android/data/com.mojang.minecraftpe/files/games/com.mojang");
        showError("Select the 'com.mojang' folder inside Minecraft");
        
        try {
            openDirectoryLauncher.launch(intent);
        } catch (Exception e) {
            updateStatus("Samsung approach failed: " + e.getMessage());
            // Fallback to standard approach
            tryStandardApproach();
        }
    }
    
    private void tryStandardApproach() {
        updateStatus("Trying standard approach...");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        
        updateStatus("Please navigate to: Android/data/com.mojang.minecraftpe/files/games/com.mojang");
        showError("Select the 'com.mojang' folder inside Minecraft");
        
        try {
            openDirectoryLauncher.launch(intent);
        } catch (Exception e) {
            updateStatus("Standard approach also failed: " + e.getMessage());
            showError("Please use the 'Setup Minecraft Folder' button to manually set the directory.");
        }
    }

    private void showProgress(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            statusText.setText(message);
            installButton.setEnabled(false);
        });
    }

    private void hideProgress() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            installButton.setEnabled(true);
        });
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }
}