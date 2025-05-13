package com.example.refileapp;

import android.content.*;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.app.AlertDialog;
import android.view.Menu;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.FileReader;

public class RefileActivity extends AppCompatActivity {

    private String currentTray = null;
    private int itemCount = 0;
    private File file;
    private BufferedWriter writer;

    private TextView trayText, countText, itemText;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String scanned = intent.getStringExtra("com.symbol.datawedge.data_string");
            if (scanned == null || scanned.isEmpty()) return;

            if (currentTray == null) {
                // Validate tray format: two letters + 5-6 digits
                if (scanned.matches("(?i)^[A-Z]{2}\\d{5,6}$")) {
                    currentTray = scanned.toUpperCase();
                    trayText.setText("Tray: " + currentTray);
                    appendToLog("Tray scanned: " + scanned + " - VALID");
                } else {
                    trayText.setText("Invalid tray barcode: " + scanned);
                    appendToLog("Tray scanned: " + scanned + " - INVALID");
                    playErrorTone(); // 👈 Play bonk for invalid tray
                    handler.postDelayed(() -> trayText.setText("Tray: (scan tray)"), 2000);
                }
            } else {
                String cleanedItem = cleanItemBarcode(scanned);
                Log.d("SCAN", "Scanned item: " + scanned + ", cleaned: " + cleanedItem);
                if (cleanedItem == null) {
                    Toast.makeText(context, "Invalid item barcode", Toast.LENGTH_SHORT).show();
                    appendToLog("Item scanned: " + scanned + " - INVALID");
                    playErrorTone(); // 👈 Play bonk for invalid item
                    return;
                }
                appendToLog("Item scanned: " + scanned + " - VALID (cleaned: " + cleanedItem + ")");

                String line = "REF" + currentTray + "#" + cleanedItem;
                writeToFile(line);
                itemText.setText("Item: " + cleanedItem);
                itemCount++;
                countText.setText("Items Scanned: " + itemCount);

                // Auto-clear the item text after 2 seconds
                handler.postDelayed(() -> itemText.setText("Item: (scan item)"), 2000);

                currentTray = null;
                trayText.setText("Tray: (scan tray)");
            }
        }
    };

    private String cleanItemBarcode(String raw) {
        if (raw == null) return null;

        // Codabar: possibly surrounded by non-digit start/stop characters
        if (raw.matches("^[A-D][0-9]{6,20}[A-D]$")) {
            // Remove first and last characters
            return raw.substring(1, raw.length() - 1);
        }

        // Codabar without start/stop: just digits, 6–20 digits
        if (raw.matches("^[0-9]{6,20}$")) {
            return raw;
        }

        // Code 39: allow letters, digits, and -.$/+% (but we’ll treat it loosely)
        if (raw.matches("^[A-Z0-9-\\. $/+%]{6,20}$")) {
            return raw;
        }

        // 6-character alphanumeric
        if (raw.matches("^[A-Z0-9]{6}$")) {
            return raw;
        }

        // If it doesn’t match any known pattern, return null to indicate invalid
        return null;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refile);

        trayText = findViewById(R.id.text_tray);
        countText = findViewById(R.id.text_count);
        itemText = findViewById(R.id.text_item);
        Button endBtn = findViewById(R.id.button_end);

        endBtn.setOnClickListener(v -> {
            closeWriter();
            finish();
        });

        setupFile();
    }

    private void setupFile() {
        try {
            file = FileHelper.getRefileFile();
            File dir = file.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            writer = new BufferedWriter(new FileWriter(file, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeToFile(String line) {
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeWriter() {
        try {
            if (writer != null) writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void playErrorTone() {
        ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        toneGen.startTone(ToneGenerator.TONE_SUP_ERROR, 300); // 300 ms error tone
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.example.refileapp.SCAN");
        registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(scanReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_view_log) {
            showScanLog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void appendToLog(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fullMessage = timestamp + " - " + message + "\n";

        File logFile = FileHelper.getScanLogFile();

        try {
            File dir = logFile.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }

            FileWriter writer = new FileWriter(logFile, true);
            writer.append(fullMessage);
            writer.close();
        } catch (IOException e) {
            Log.e("SCANLOG", "Failed to write scan log", e);
        }
    }

    private void showScanLog() {
        File logFile = FileHelper.getScanLogFile();

        StringBuilder logContents = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                logContents.append(line).append("\n");
            }
            reader.close();
        } catch (IOException e) {
            logContents.append("Could not read scan log.");
        }

        new AlertDialog.Builder(this)
                .setTitle("Scan Log")
                .setMessage(logContents.toString())
                .setPositiveButton("OK", null)
                .show();
    }

}