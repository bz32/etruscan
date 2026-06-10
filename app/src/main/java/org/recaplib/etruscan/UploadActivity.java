package org.recaplib.etruscan;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

public class UploadActivity extends Activity implements UploadTask.UploadListener {
    private EditText usernameInput, passwordInput;
    private String ftpServer;
    private int ftpPort;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        loadFtpConfig();  // Load config on screen load

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        progressBar = findViewById(R.id.progress_bar);

        TextView uploadMessage = findViewById(R.id.upload_message);
        uploadMessage.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());

        Button yesButton = findViewById(R.id.button_upload_yes);
        Button cancelButton = findViewById(R.id.button_upload_cancel);

        // Show info for files to be uploaded
        File refile = FileHelper.getRefileFile();
        File t2shelf = FileHelper.getT2ShelfFile();

        SpannableStringBuilder message = new SpannableStringBuilder();
        message.append("Provide your LAS server credentials to upload the following file(s) to LAS:\n\n");

        boolean foundFile = false;

        // Helper for adding clickable preview entries
        FilePreviewClick previewClick = f -> {
            try {
                String contents = FileHelper.readFileContents(f);
                new androidx.appcompat.app.AlertDialog.Builder(UploadActivity.this)
                        .setTitle(f.getName())
                        .setMessage(contents.isEmpty() ? "(File is empty)" : contents)
                        .setPositiveButton("Close", null)
                        .show();
            } catch (Exception e) {
                new androidx.appcompat.app.AlertDialog.Builder(UploadActivity.this)
                        .setTitle("Error")
                        .setMessage("Unable to read file contents.")
                        .setPositiveButton("Close", null)
                        .show();
            }
        };

        if (refile.exists()) {
            foundFile = true;

            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(refile.lastModified()));

            String line = "• refile.dat (last updated " + ts + ")\n";

            int start = message.length();
            message.append(line);

            // make clickable span
            ClickableSpan span = new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    previewClick.onClick(refile);
                }
            };
            message.setSpan(span, start, start + line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (t2shelf.exists()) {
            foundFile = true;

            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(t2shelf.lastModified()));

            String line = "• t2shelf.dat (last updated " + ts + ")\n";
            int start = message.length();
            message.append(line);

            ClickableSpan span = new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    previewClick.onClick(t2shelf);
                }
            };
            message.setSpan(span, start, start + line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (!foundFile) {
            yesButton.setEnabled(false);
            uploadMessage.setText("No uploadable .dat files found. Please return and perform scans first.");
        } else {
            uploadMessage.setText(message);
        }

        yesButton.setOnClickListener(v -> {
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

    private interface FilePreviewClick {
        void onClick(File file);
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