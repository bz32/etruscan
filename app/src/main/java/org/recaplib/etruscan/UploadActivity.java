package org.recaplib.etruscan;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class UploadActivity extends Activity implements UploadTask.UploadListener {
    private EditText usernameInput, passwordInput;
    private String ftpServer;
    private int ftpPort;
    private ProgressBar progressBar;
    private final Map<CheckBox, File> fileCheckboxes = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        loadFtpConfig();  // Load config on screen load

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        progressBar = findViewById(R.id.progress_bar);

        TextView uploadMessage = findViewById(R.id.upload_message);
        LinearLayout filesContainer = findViewById(R.id.files_container);

        Button yesButton = findViewById(R.id.button_upload_yes);
        Button cancelButton = findViewById(R.id.button_upload_cancel);

        // Show info for files to be uploaded
        File refile = FileHelper.getRefileFile();
        File boxref = FileHelper.getBoxRefFile();
        File t2shelf = FileHelper.getT2ShelfFile();
        File i2shelf = FileHelper.getI2ShelfFile();

        boolean foundFile = false;

        if (refile.exists()) {
            foundFile = true;
            addFileRow(filesContainer, refile);
        }

        if (boxref.exists()) {
            foundFile = true;
            addFileRow(filesContainer, boxref);
        }

        if (t2shelf.exists()) {
            foundFile = true;
            addFileRow(filesContainer, t2shelf);
        }

        if (i2shelf.exists()) {
            foundFile = true;
            addFileRow(filesContainer, i2shelf);
        }

        if (!foundFile) {
            yesButton.setEnabled(false);
            uploadMessage.setText("No uploadable .dat files found. Please return and perform scans first.");
        } else {
            uploadMessage.setText("Provide your LAS server credentials to upload the checked file(s) to LAS. Tap a filename to preview its contents.");
        }

        yesButton.setOnClickListener(v -> {
            List<File> selectedFiles = getSelectedFiles();
            if (selectedFiles.isEmpty()) {
                Toast.makeText(UploadActivity.this, "Check at least one file to upload.", Toast.LENGTH_SHORT).show();
                return;
            }

            // First, check Wi-Fi on UI thread
            if (!isWifiConnected()) {
                Toast.makeText(UploadActivity.this, "You must be on Wi-Fi to proceed.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                return;
            }

            // Run VPN/server check on a background thread
            new Thread(() -> {
                boolean reachable = isLasServerReachable(ftpServer, 22);

                runOnUiThread(() -> {
                    if (!reachable) {
                        // Show VPN warning on UI thread
                        Toast.makeText(UploadActivity.this,
                                "You must be connected to the VPN to proceed.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Proceed with upload if both checks pass
                    String username = usernameInput.getText().toString();
                    String password = passwordInput.getText().toString();

                    // Save password securely
                    savePassword(password);

                    // Construct dynamic upload path based on entered username
                    String ftpUploadPath = "/home/" + username;

                    // Proceed with upload
                    progressBar.setVisibility(View.VISIBLE);

                    UploadTask uploadTask = new UploadTask(
                            UploadActivity.this,
                            ftpServer,
                            ftpPort,
                            ftpUploadPath,
                            username,
                            password,
                            selectedFiles,
                            UploadActivity.this
                    );
                    uploadTask.upload();
                });
            }).start();
        });

        cancelButton.setOnClickListener(v -> finish());

        Button viewLogButton = findViewById(R.id.button_view_log);
        viewLogButton.setOnClickListener(v -> showScanLog());
    }

    private void addFileRow(LinearLayout container, File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(true);
        fileCheckboxes.put(checkBox, file);

        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(file.lastModified()));
        TextView label = new TextView(this);
        label.setText(file.getName() + " (last updated " + ts + ")");
        label.setTextSize(16);
        label.setPadding(8, 0, 0, 0);
        label.setOnClickListener(v -> {
            String contents = FileHelper.readFileContents(file);
            new androidx.appcompat.app.AlertDialog.Builder(UploadActivity.this)
                    .setTitle(file.getName())
                    .setMessage(contents.isEmpty() ? "(File is empty)" : contents)
                    .setPositiveButton("Close", null)
                    .show();
        });

        row.addView(checkBox);
        row.addView(label);
        container.addView(row);
    }

    private List<File> getSelectedFiles() {
        List<File> selected = new ArrayList<>();
        for (Map.Entry<CheckBox, File> entry : fileCheckboxes.entrySet()) {
            if (entry.getKey().isChecked()) {
                selected.add(entry.getValue());
            }
        }
        return selected;
    }

    private boolean isWifiConnected() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager == null) return false;

        Network activeNetwork = connManager.getActiveNetwork();
        if (activeNetwork == null) return false;

        NetworkCapabilities capabilities = connManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public boolean isLasServerReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000); // 2 sec timeout
            return true; // reachable = VPN is active
        } catch (IOException e) {
            return false; // unreachable = not on VPN
        }
    }

    private void loadFtpConfig() {
        Properties props = new Properties();
        try (InputStream is = getAssets().open("config.properties")) {
            props.load(is);
            ftpServer = props.getProperty("ftp_server");
            ftpPort = Integer.parseInt(props.getProperty("ftp_port"));

            Log.d("CONFIG", "FTP Config loaded: " + ftpServer + ":" + ftpPort);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load FTP config", Toast.LENGTH_LONG).show();
        }
    }

    private void savePassword(String password) {
        try {
            String encrypted = KeystoreHelper.encryptPassword(password);
            SharedPreferences prefs = getSharedPreferences("FTP_Settings", MODE_PRIVATE);
            prefs.edit().putString("password", encrypted).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getPassword() {
        SharedPreferences prefs = getSharedPreferences("FTP_Settings", MODE_PRIVATE);
        String encrypted = prefs.getString("password", null);
        if (encrypted != null) {
            try {
                return KeystoreHelper.decryptPassword(encrypted);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void showScanLog() {
        File logFile = FileHelper.getScanLogFile();
        String contents = FileHelper.readFileContents(logFile);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Scan Log")
                .setMessage(contents.trim().isEmpty() ? "(Log is empty)" : contents)
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    public void onUploadStarted() {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.VISIBLE);
            Button yesButton = findViewById(R.id.button_upload_yes);
            if (yesButton != null) {
                yesButton.setEnabled(false);  // Disable the "Yes" button to prevent double-clicking
            }
        });
    }

    @Override
    public void onUploadFinished(String result) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);  // Hide progress bar after completion
            Toast.makeText(UploadActivity.this, result, Toast.LENGTH_LONG).show();

            Button yesButton = findViewById(R.id.button_upload_yes);
            if (yesButton != null) {
                yesButton.setEnabled(true);  // Re-enable the "Yes" button
            }

            // If successful, redirect back to the main activity
            if (result.equals("Upload successful.")) {
                Intent intent = new Intent(UploadActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}