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
import com.minecraftcompanion.R;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private static final String MINECRAFT_PACKAGE = "com.mojang.minecraftpe";
    private static final String MINECRAFT_WORLDS_PATH = "games/com.mojang/minecraftWorlds";
    private static final String MINECRAFT_RESOURCE_PACKS_PATH = "games/com.mojang/resource_packs";

    private ActivityResultLauncher<Intent> openDocumentLauncher;
    private ActivityResultLauncher<Intent> openDirectoryLauncher;
    private Uri minecraftDirectoryUri;
    private ProgressBar progressBar;
    private TextView statusText;
    private MaterialButton installButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        installButton = findViewById(R.id.installButton);

        installButton.setOnClickListener(v -> openFilePicker());

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
                            getContentResolver().takePersistableUriPermission(
                                    minecraftDirectoryUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                            updateStatus("Minecraft directory access granted!");
                        }
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
            requestMinecraftDirectoryAccess();
            return;
        }

        try {
            showProgress("Installing world...");
            DocumentFile minecraftDir = DocumentFile.fromTreeUri(this, minecraftDirectoryUri);
            DocumentFile worldsDir = minecraftDir.findFile(MINECRAFT_WORLDS_PATH);

            if (worldsDir == null) {
                worldsDir = minecraftDir.createDirectory(MINECRAFT_WORLDS_PATH);
            }

            String worldName = getFileName(uri).replace(".mcworld", "");
            DocumentFile worldDir = worldsDir.createDirectory(worldName);

            extractZipFile(uri, worldDir);
            updateStatus("World installed successfully!");
        } catch (IOException e) {
            showError("Error installing world: " + e.getMessage());
        } finally {
            hideProgress();
        }
    }

    private void extractAndInstallResourcePack(Uri uri) {
        if (minecraftDirectoryUri == null) {
            requestMinecraftDirectoryAccess();
            return;
        }

        try {
            showProgress("Installing resource pack...");
            DocumentFile minecraftDir = DocumentFile.fromTreeUri(this, minecraftDirectoryUri);
            DocumentFile resourcePacksDir = minecraftDir.findFile(MINECRAFT_RESOURCE_PACKS_PATH);

            if (resourcePacksDir == null) {
                resourcePacksDir = minecraftDir.createDirectory(MINECRAFT_RESOURCE_PACKS_PATH);
            }

            String packName = getFileName(uri).replace(".mcpack", "");
            DocumentFile packDir = resourcePacksDir.createDirectory(packName);

            extractZipFile(uri, packDir);
            updateStatus("Resource pack installed successfully!");
        } catch (IOException e) {
            showError("Error installing resource pack: " + e.getMessage());
        } finally {
            hideProgress();
        }
    }

    private void extractZipFile(Uri zipUri, DocumentFile destinationDir) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(zipUri);
             ZipArchiveInputStream zipStream = new ZipArchiveInputStream(inputStream)) {

            ZipArchiveEntry entry;
            while ((entry = zipStream.getNextZipEntry()) != null) {
                if (!entry.isDirectory()) {
                    DocumentFile file = destinationDir.createFile(
                            "application/octet-stream",
                            entry.getName()
                    );

                    if (file != null) {
                        try (OutputStream outputStream = getContentResolver().openOutputStream(file.getUri())) {
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

    private void requestMinecraftDirectoryAccess() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        Uri initialUri = DocumentsContract.buildRootUri(
                "com.android.externalstorage.documents",
                "primary:Android/data"
        );

        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        openDirectoryLauncher.launch(intent);
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