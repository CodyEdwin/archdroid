package com.archdroid;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.archdroid.bootstrap.BootstrapManager;
import com.archdroid.terminal.TerminalView;
import com.google.android.material.snackbar.Snackbar;

/**
 * Main Activity for ArchDroid - Arch Linux for Android
 * This activity handles the terminal interface and bootstrapping process
 */
public class MainActivity extends AppCompatActivity implements BootstrapManager.BootstrapListener {

    private TerminalView terminalView;
    private BootstrapManager bootstrapManager;
    private ProgressBar progressBar;
    private TextView statusText;
    private View loadingOverlay;
    private View loadingContainer;

    private final ActivityResultLauncher<Intent> storagePermissionLauncher;
    private final ActivityResultLauncher<String[]> permissionsLauncher;

    public MainActivity() {
        // Storage permission launcher for Android 11+
        this.storagePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (android.os.Environment.isExternalStorageManager()) {
                        checkAndBootstrap();
                    } else {
                        showPermissionError();
                    }
                }
            }
        );

        // Multiple permissions launcher for Android 10 and below
        this.permissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {
                boolean allGranted = true;
                for (Boolean granted : permissions.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    checkAndBootstrap();
                } else {
                    showPermissionError();
                }
            }
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeComponents();
        checkAndBootstrap();
    }

    private void initializeViews() {
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("ArchDroid");
        }

        // Initialize views
        terminalView = findViewById(R.id.terminal_view);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        loadingOverlay = findViewById(R.id.loading_overlay);
        loadingContainer = findViewById(R.id.loading_container);
    }

    private void initializeComponents() {
        bootstrapManager = new BootstrapManager(this);
        bootstrapManager.setBootstrapListener(this);
    }

    private void checkAndBootstrap() {
        // Check device architecture
        if (!isArm64Device()) {
            showErrorDialog(
                "Incompatible Device",
                "This application requires an ARM64 (AArch64) device. " +
                "Your device architecture is not supported."
            );
            return;
        }

        // Check if Arch Linux is already installed
        if (!bootstrapManager.isArchLinuxInstalled()) {
            showBootstrapDialog();
        } else {
            hideLoading();
            startTerminalSession();
        }
    }

    private boolean isArm64Device() {
        String[] supportedAbis = Build.SUPPORTED_ABIS;
        for (String abi : supportedAbis) {
            if (abi.equals("arm64-v8a") || abi.equals("aarch64")) {
                return true;
            }
        }
        return false;
    }

    private void showBootstrapDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Install Arch Linux")
            .setMessage(
                "ArchDroid will download and extract the Arch Linux ARM64 root filesystem " +
                "(approximately 600MB). This may take several minutes depending on your " +
                "internet connection.\n\nDo you want to proceed?"
            )
            .setPositiveButton("Install", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    showLoading("Initializing bootstrap...");
                    bootstrapManager.startBootstrap();
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .setCancelable(false)
            .show();
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            storagePermissionLauncher.launch(intent);
        } else {
            // Android 10 and below
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            permissionsLauncher.launch(permissions);
        }
    }

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return android.os.Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void showLoading(String message) {
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingContainer.setVisibility(View.VISIBLE);
        statusText.setText(message);
        progressBar.setProgress(0);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
        loadingContainer.setVisibility(View.GONE);
    }

    private void startTerminalSession() {
        String launchScript = BootstrapManager.getLaunchScriptPath(this);
        terminalView.startSession(launchScript);
        terminalView.requestFocus();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (title.contains("Failed") || title.contains("Incompatible")) {
                        finish();
                    }
                }
            })
            .setCancelable(false)
            .show();
    }

    private void showPermissionError() {
        new AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(
                "Storage permissions are required to store the Linux filesystem. " +
                "Please grant the permissions and try again."
            )
            .setPositiveButton("Grant", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    requestPermissions();
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            })
            .setCancelable(false)
            .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new_session) {
            terminalView.createNewSession();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_reset) {
            showResetDialog();
            return true;
        } else if (id == R.id.action_help) {
            showHelpDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Reset Environment")
            .setMessage(
                "This will delete all data, installed packages, and configurations. " +
                "You will need to reinstall Arch Linux.\n\nAre you sure you want to reset?"
            )
            .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    bootstrapManager.resetEnvironment();
                    recreate();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Help")
            .setMessage(
                "ArchDroid provides a complete Arch Linux environment on Android.\n\n" +
                "First Steps:\n" +
                "1. Update packages: pacman -Syu\n" +
                "2. Install software: pacman -S package_name\n" +
                "3. Access storage: cd /storage\n\n" +
                "For Arch Linux documentation, visit:\n" +
                "https://wiki.archlinux.org"
            )
            .setPositiveButton("OK", null)
            .show();
    }

    // BootstrapListener implementation
    @Override
    public void onBootstrapStarted(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }

    @Override
    public void onProgressUpdate(long current, long total) {
        runOnUiThread(() -> {
            if (total > 0) {
                int progress = (int) ((current * 100) / total);
                progressBar.setProgress(progress);
                statusText.setText("Downloading: " + progress + "%");
            }
        });
    }

    @Override
    public void onBootstrapComplete() {
        runOnUiThread(() -> {
            hideLoading();
            startTerminalSession();
            Toast.makeText(this, "Arch Linux installed successfully!", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBootstrapFailed(String error) {
        runOnUiThread(() -> {
            hideLoading();
            showErrorDialog("Bootstrap Failed", error);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        terminalView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        terminalView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        terminalView.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Confirm before exiting
        new AlertDialog.Builder(this)
            .setTitle("Exit ArchDroid")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Exit", (dialog, which) -> finish())
            .setNegativeButton("Cancel", null)
            .show();
    }
}
