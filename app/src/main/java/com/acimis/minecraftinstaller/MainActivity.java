package com.acimis.minecraftinstaller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private PermissionHelper permissionHelper;
    private ImageView mascotImageView;
    private TextView statusTextView;
    private Button installButton;
    private Button retryButton;

    private boolean isInstalling = false;
    private ObjectAnimator mascotAnimator;

    private BroadcastReceiver installReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Constants.ACTION_INSTALL_COMPLETE.equals(action)) {
                String fileName = intent.getStringExtra("file_name");
                String fileType = intent.getStringExtra("file_type");
                onInstallSuccess(fileName, fileType);
            } else if (Constants.ACTION_INSTALL_ERROR.equals(action)) {
                String error = intent.getStringExtra("error");
                onInstallError(error);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupPermissionHelper();
        setupClickListeners();
        startMascotAnimation();

        // Handle file shared to app
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }

    private void initializeViews() {
        mascotImageView = findViewById(R.id.mascot_image);
        statusTextView = findViewById(R.id.status_text);
        installButton = findViewById(R.id.install_button);
        retryButton = findViewById(R.id.retry_button);

        // Set initial state
        statusTextView.setText("Ready to install Minecraft content!");
        retryButton.setVisibility(View.GONE);
    }

    private void setupPermissionHelper() {
        permissionHelper = new PermissionHelper(this);
    }

    private void setupClickListeners() {
        installButton.setOnClickListener(v -> {
            if (!isInstalling) {
                selectFile();
            }
        });

        retryButton.setOnClickListener(v -> {
            retryButton.setVisibility(View.GONE);
            installButton.setVisibility(View.VISIBLE);
            statusTextView.setText("Ready to install Minecraft content!");
            startMascotAnimation();
        });
    }

    private void startMascotAnimation() {
        if (mascotAnimator != null) {
            mascotAnimator.cancel();
        }

        // Simple bounce animation
        mascotAnimator = ObjectAnimator.ofFloat(mascotImageView, "translationY", 0f, -30f, 0f);
        mascotAnimator.setDuration(2000);
        mascotAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        mascotAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mascotAnimator.start();
    }

    private void stopMascotAnimation() {
        if (mascotAnimator != null) {
            mascotAnimator.cancel();
            mascotImageView.setTranslationY(0f);
        }
    }

    private void selectFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(Intent.createChooser(intent, "Select Minecraft File"),
                    Constants.REQUEST_CODE_PICK_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri fileUri = intent.getData();
            if (fileUri != null) {
                String fileName = getFileName(fileUri);
                if (FileUtils.isValidMinecraftFile(fileName)) {
                    processSelectedFile(fileUri, fileName);
                } else {
                    showUnsupportedFileDialog();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri fileUri = data.getData();
                String fileName = getFileName(fileUri);

                if (FileUtils.isValidMinecraftFile(fileName)) {
                    processSelectedFile(fileUri, fileName);
                } else {
                    showUnsupportedFileDialog();
                }
            }
        } else if (requestCode == Constants.REQUEST_CODE_SAF_PERMISSION) {
            if (permissionHelper.handleSAFPermissionResult(requestCode, resultCode, data)) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission required to install files", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;

        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            }
        }

        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }

        return fileName != null ? fileName : "unknown_file";
    }

    private void processSelectedFile(Uri fileUri, String fileName) {
        if (!permissionHelper.hasSAFPermission()) {
            // Show explanation and request permission
            new AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("This app needs access to your device storage to install Minecraft content. Please select your device's main storage when prompted.")
                    .setPositiveButton("Grant Permission", (dialog, which) -> {
                        permissionHelper.requestSAFPermission(this);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Start installation
        startInstallation(fileUri, fileName);
    }

    private void startInstallation(Uri fileUri, String fileName) {
        isInstalling = true;
        stopMascotAnimation();

        // Update UI
        installButton.setVisibility(View.GONE);
        statusTextView.setText("Installing " + fileName + "...");

        // Start installation service
        InstallerService.startInstall(this, fileUri, fileName);
    }

    private void onInstallSuccess(String fileName, String fileType) {
        isInstalling = false;

        // Show success animation
        showSuccessAnimation();

        // Update status
        String message = fileName + " installed successfully!";
        statusTextView.setText(message);

        // Show install button again after delay
        statusTextView.postDelayed(() -> {
            installButton.setVisibility(View.VISIBLE);
            statusTextView.setText("Ready to install more content!");
            startMascotAnimation();
        }, 3000);

        Toast.makeText(this, "Installation complete!", Toast.LENGTH_LONG).show();
    }

    private void onInstallError(String error) {
        isInstalling = false;

        statusTextView.setText("Installation failed: " + error);
        installButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);

        stopMascotAnimation();

        Toast.makeText(this, "Installation failed. Tap retry to try again.", Toast.LENGTH_LONG).show();
    }

    private void showSuccessAnimation() {
        // Simple success animation - scale up and down
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mascotImageView, "scaleX", 1f, 1.5f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mascotImageView, "scaleY", 1f, 1.5f, 1f);

        scaleX.setDuration(Constants.ANIMATION_DURATION_LONG);
        scaleY.setDuration(Constants.ANIMATION_DURATION_LONG);

        scaleX.start();
        scaleY.start();
    }

    private void showUnsupportedFileDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Unsupported File")
                .setMessage("This app only supports .mcworld and .mcpack files. Please select a valid Minecraft content file.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(installReceiver,
                new IntentFilter(Constants.ACTION_INSTALL_COMPLETE));
        LocalBroadcastManager.getInstance(this).registerReceiver(installReceiver,
                new IntentFilter(Constants.ACTION_INSTALL_ERROR));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(installReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mascotAnimator != null) {
            mascotAnimator.cancel();
        }
    }
}
