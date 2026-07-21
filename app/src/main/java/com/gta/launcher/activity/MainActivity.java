package com.gta.launcher.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.gta.game.R;
import com.gta.game.SAMP;
import com.gta.launcher.distribution.DistributionManifest;
import com.gta.launcher.distribution.DistributionManager;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Button startButton;
    private Button downloadCacheButton;
    private Button updateButton;
    private TextView title1;
    private TextView title2;
    private TextView authorText;
    private TextView cacheText;
    private boolean storagePermissionGranted = false;
    private DistributionManager distributionManager;
    private DistributionManifest distributionManifest;

    private final ActivityResultLauncher<String[]> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                for (Boolean granted : permissions.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    storagePermissionGranted = true;
                    Log.d("MainActivity", "All storage permissions granted");
                    startGameIfReady();
                } else {
                    storagePermissionGranted = false;
                    Log.e("MainActivity", "Storage permissions denied");
                    Toast.makeText(this, "Для работы игры необходим доступ к хранилищу", Toast.LENGTH_LONG).show();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        requestManageStoragePermission();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        storagePermissionGranted = true;
                        Log.d("MainActivity", "Manage storage permission granted");
                        startGameIfReady();
                    } else {
                        storagePermissionGranted = false;
                        Log.e("MainActivity", "Manage storage permission denied");
                        Toast.makeText(this, "Приложение требует полный доступ к хранилищу", Toast.LENGTH_LONG).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("MainActivity", "onCreate started");

        try {
            setFullScreenMode();
            setContentView(R.layout.main_activity);

            Log.d("MainActivity", "ContentView set");

            distributionManager = new DistributionManager(this);
            initViews();
            setupClickListeners();
            refreshDistributionState();
            checkAndRequestStoragePermission();

            Log.d("MainActivity", "onCreate completed successfully");

        } catch (Exception e) {
            Log.e("MainActivity", "Critical error in onCreate: " + e.getMessage(), e);
            finish();
        }
    }

    private void refreshDistributionState() {
        if (distributionManager == null || !distributionManager.hasManifestUrl()) {
            setCacheStatus(getString(R.string.distribution_not_configured));
            return;
        }

        setCacheStatus(getString(R.string.distribution_checking));
        distributionManager.fetchManifest(new DistributionManager.ManifestCallback() {
            @Override
            public void onSuccess(DistributionManifest manifest) {
                distributionManifest = manifest;
                renderDistributionState();
            }

            @Override
            public void onError(Exception error) {
                Log.e("MainActivity", "Failed to fetch distribution manifest: " + error.getMessage(), error);
                setCacheStatus(getString(R.string.distribution_remote_error));
            }
        });
    }

    private void renderDistributionState() {
        if (distributionManifest == null) {
            return;
        }

        String cacheState = distributionManager.describeCacheState(distributionManifest);
        String currentClient = getCurrentClientVersionLabel();
        String remoteClient = distributionManifest.getClientVersionName().isEmpty()
                ? String.format(Locale.US, "Remote client v%d", distributionManifest.getClientVersionCode())
                : String.format(Locale.US, "Remote client %s (%d)", distributionManifest.getClientVersionName(), distributionManifest.getClientVersionCode());

        setCacheStatus(cacheState + "\n" + currentClient + "\n" + remoteClient);
    }

    private String getCurrentClientVersionLabel() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return String.format(Locale.US, "Client v%s (%d)", info.versionName, versionCode);
        } catch (Exception e) {
            Log.e("MainActivity", "Unable to read current package version", e);
            return "Client v?";
        }
    }

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                storagePermissionGranted = true;
                Log.d("MainActivity", "Already have manage storage permission");
            } else {
                requestManageStoragePermission();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{
                        android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO,
                        android.Manifest.permission.READ_MEDIA_AUDIO
                };
            } else {
                permissions = new String[]{
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
            }

            boolean allGranted = true;
            for (String perm : permissions) {
                if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                storagePermissionGranted = true;
                Log.d("MainActivity", "Already have storage permissions");
            } else {
                requestStoragePermissionLauncher.launch(permissions);
            }
        } else {
            storagePermissionGranted = true;
        }
    }

    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                manageStorageLauncher.launch(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                manageStorageLauncher.launch(intent);
            }
        }
    }

    private void startGameIfReady() {
        if (!storagePermissionGranted) {
            Toast.makeText(this, "Нет доступа к хранилищу. Игра не может быть запущена.", Toast.LENGTH_LONG).show();
            return;
        }

        if (distributionManifest == null) {
            startGame();
            return;
        }

        if (!distributionManager.isCacheInstalled(distributionManifest)) {
            promptCacheDownload(true);
            return;
        }

        if (distributionManager.isClientUpdateAvailable(distributionManifest)) {
            promptClientUpdate();
            return;
        }

        startGame();
    }

    private void promptCacheDownload(final boolean autoPlayAfterDownload) {
        if (distributionManifest == null) {
            if (distributionManager.hasManifestUrl()) {
                Toast.makeText(this, getString(R.string.distribution_checking), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.distribution_not_configured), Toast.LENGTH_LONG).show();
            }
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.distribution_download_dialog_title)
                .setMessage(R.string.distribution_download_dialog_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> downloadCache(autoPlayAfterDownload))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptClientUpdate() {
        if (distributionManifest == null) {
            Toast.makeText(this, getString(R.string.distribution_checking), Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.distribution_update_dialog_title)
                .setMessage(R.string.distribution_update_dialog_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> downloadClientUpdate())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void downloadCache(final boolean autoPlayAfterDownload) {
        if (distributionManifest == null) {
            Toast.makeText(this, getString(R.string.distribution_remote_error), Toast.LENGTH_LONG).show();
            return;
        }

        setCacheStatus(getString(R.string.distribution_cache_missing));
        distributionManager.downloadAndInstallCache(distributionManifest, new DistributionManager.ProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                setCacheStatus(String.format(Locale.US,
                        getString(R.string.distribution_progress_format),
                        "Cache",
                        percent));
            }
        }, new DistributionManager.FileCallback() {
            @Override
            public void onSuccess(java.io.File file) {
                setCacheStatus(getString(R.string.distribution_cache_done));
                renderDistributionState();
                if (autoPlayAfterDownload) {
                    startGame();
                }
            }

            @Override
            public void onError(Exception error) {
                Log.e("MainActivity", "Cache download failed: " + error.getMessage(), error);
                setCacheStatus(getString(R.string.distribution_download_error));
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadClientUpdate() {
        if (distributionManifest == null) {
            Toast.makeText(this, getString(R.string.distribution_remote_error), Toast.LENGTH_LONG).show();
            return;
        }

        setCacheStatus(getString(R.string.distribution_client_update_available));
        distributionManager.downloadAndPromptInstallClient(distributionManifest, this, new DistributionManager.ProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                setCacheStatus(String.format(Locale.US,
                        getString(R.string.distribution_progress_format),
                        "Client",
                        percent));
            }
        }, new DistributionManager.FileCallback() {
            @Override
            public void onSuccess(java.io.File file) {
                setCacheStatus(getString(R.string.distribution_client_update_done));
            }

            @Override
            public void onError(Exception error) {
                Log.e("MainActivity", "Client update failed: " + error.getMessage(), error);
                setCacheStatus(getString(R.string.distribution_download_error));
                Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setFullScreenMode() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );

            getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            );

            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(params);
            }

            if (getSupportActionBar() != null) {
                getSupportActionBar().hide();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in setFullScreenMode: " + e.getMessage());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            try {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                );
            } catch (Exception e) {
                Log.e("MainActivity", "Error in onWindowFocusChanged: " + e.getMessage());
            }
        }
    }

    private void initViews() {
        try {
            title1 = findViewById(R.id.title1);
            title2 = findViewById(R.id.title2);
            authorText = findViewById(R.id.authorText);
            cacheText = findViewById(R.id.cacheText);
            startButton = findViewById(R.id.startButton);
            downloadCacheButton = findViewById(R.id.downloadCacheButton);
            updateButton = findViewById(R.id.updateButton);
        } catch (Exception e) {
            Log.e("MainActivity", "Error in initViews: " + e.getMessage(), e);
        }
    }

    private void setupClickListeners() {
        try {
            startButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                                    }
                                })
                                .start();

                        if (storagePermissionGranted) {
                            startGameIfReady();
                        } else {
                            checkAndRequestStoragePermission();
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error in start button click: " + e.getMessage(), e);
                    }
                }
            });

            downloadCacheButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        withManifest(new ManifestAction() {
                            @Override
                            public void run(DistributionManifest manifest) {
                                if (manifest == null) {
                                    Toast.makeText(MainActivity.this, getString(R.string.distribution_remote_error), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                downloadCache(false);
                            }
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error in download cache click: " + e.getMessage(), e);
                    }
                }
            });

            updateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        withManifest(new ManifestAction() {
                            @Override
                            public void run(DistributionManifest manifest) {
                                if (manifest == null) {
                                    Toast.makeText(MainActivity.this, getString(R.string.distribution_remote_error), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                if (distributionManager.isClientUpdateAvailable(manifest)) {
                                    promptClientUpdate();
                                } else {
                                    Toast.makeText(MainActivity.this, "Client already up to date", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error in update button click: " + e.getMessage(), e);
                    }
                }
            });

            authorText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://t.me/kuzia15"));
                        startActivity(browserIntent);
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error opening tg: " + e.getMessage(), e);
                    }
                }
            });

        } catch (Exception e) {
            Log.e("MainActivity", "Error setting up click listeners: " + e.getMessage(), e);
        }
    }

    private interface ManifestAction {
        void run(DistributionManifest manifest);
    }

    private void withManifest(final ManifestAction action) {
        if (distributionManifest != null) {
            action.run(distributionManifest);
            return;
        }

        if (distributionManager == null || !distributionManager.hasManifestUrl()) {
            action.run(null);
            return;
        }

        setCacheStatus(getString(R.string.distribution_checking));
        distributionManager.fetchManifest(new DistributionManager.ManifestCallback() {
            @Override
            public void onSuccess(DistributionManifest manifest) {
                distributionManifest = manifest;
                renderDistributionState();
                action.run(manifest);
            }

            @Override
            public void onError(Exception error) {
                Log.e("MainActivity", "Failed to fetch distribution manifest: " + error.getMessage(), error);
                setCacheStatus(getString(R.string.distribution_remote_error));
                action.run(null);
            }
        });
    }

    private void setCacheStatus(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (cacheText != null) {
                    cacheText.setText(text);
                }
            }
        });
    }

    private void startGame() {
        try {
            Log.d("MainActivity", "Starting game");
            Intent gameIntent = new Intent(MainActivity.this, SAMP.class);
            startActivity(gameIntent);
        } catch (Exception e) {
            Log.e("MainActivity", "Error starting game: " + e.getMessage(), e);
        }
    }

    public static void hideKeyboard(Activity activity) {
        try {
            InputMethodManager inputManager = (InputMethodManager) activity
                    .getSystemService(Context.INPUT_METHOD_SERVICE);

            View currentFocusedView = activity.getCurrentFocus();
            if (currentFocusedView != null) {
                inputManager.hideSoftInputFromWindow(currentFocusedView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error hiding keyboard: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            handler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            Log.e("MainActivity", "Error in onDestroy: " + e.getMessage(), e);
        }
    }
}
