package org.recaplib.etruscan;

import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class RefileActivity extends AppCompatActivity {

    private String currentTray = null;
    private int itemCount = 0;
    private File file;
    private BufferedWriter writer;

    private TextView trayText, countText, itemText;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private RecyclerView summaryRecyclerView;
    private ScanPairAdapter scanPairAdapter;
    private List<ScanPair> scanPairs = new ArrayList<>();


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
                    FileHelper.appendToLog("Tray scanned: " + scanned + " - VALID");
                } else {
                    trayText.setText("Invalid tray barcode: " + scanned);
                    FileHelper.appendToLog("Tray scanned: " + scanned + " - INVALID");
                    playErrorTone(); // 👈 Play bonk for invalid tray
                    handler.postDelayed(() -> trayText.setText("Tray: (scan tray)"), 2000);
                }
            } else {
                String cleanedItem = cleanItemBarcode(scanned);
                Log.d("SCAN", "Scanned item: " + scanned + ", cleaned: " + cleanedItem);
                if (cleanedItem == null) {
                    Toast.makeText(context, "Invalid item barcode", Toast.LENGTH_SHORT).show();
                    FileHelper.appendToLog("Item scanned: " + scanned + " - INVALID");
                    playErrorTone(); // 👈 Play bonk for invalid item
                    return;
                }
                FileHelper.appendToLog("Item scanned: " + scanned + " - VALID (cleaned: " + cleanedItem + ")");

                String line = "REF" + currentTray + "#" + cleanedItem;
                writeToFile(line);
                itemText.setText("Item: " + cleanedItem);
                itemCount++;
                countText.setText("Items Scanned: " + itemCount);

                addScan(currentTray, cleanedItem);

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
        setRequestedOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_refile);

        trayText = findViewById(R.id.text_tray);
        countText = findViewById(R.id.text_count);
        itemText = findViewById(R.id.text_item);
        Button endBtn = findViewById(R.id.button_end);

        summaryRecyclerView = findViewById(R.id.refile_summary_list);
        scanPairs = new ArrayList<>();
        scanPairAdapter = new ScanPairAdapter(scanPairs);

        summaryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        summaryRecyclerView.setAdapter(scanPairAdapter);

        endBtn.setOnClickListener(v -> {
            closeWriter();
            finish();
        });

        if (savedInstanceState != null) {
            currentTray = savedInstanceState.getString("currentTray");
            itemCount = savedInstanceState.getInt("itemCount", 0);
            ArrayList<String> trays = savedInstanceState.getStringArrayList("scanPairTrays");
            ArrayList<String> items = savedInstanceState.getStringArrayList("scanPairItems");
            if (trays != null && items != null) {
                for (int i = 0; i < trays.size(); i++) {
                    scanPairs.add(new ScanPair(trays.get(i), items.get(i)));
                }
                scanPairAdapter.notifyDataSetChanged();
            }
            trayText.setText(currentTray != null ? "Tray: " + currentTray : "Tray: (scan tray)");
            countText.setText("Items Scanned: " + itemCount);
        } else {
            loadExistingScans();
        }

        setupFile();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("currentTray", currentTray);
        outState.putInt("itemCount", itemCount);
        ArrayList<String> trays = new ArrayList<>();
        ArrayList<String> items = new ArrayList<>();
        for (ScanPair pair : scanPairs) {
            trays.add(pair.tray);
            items.add(pair.item);
        }
        outState.putStringArrayList("scanPairTrays", trays);
        outState.putStringArrayList("scanPairItems", items);
    }

    private void loadExistingScans() {
        File refileFile = FileHelper.getRefileFile();
        if (!refileFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(refileFile))) {
            List<ScanPair> loaded = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // Format: REF{tray}#{item}
                if (line.startsWith("REF") && line.contains("#")) {
                    String[] parts = line.substring(3).split("#", 2);
                    if (parts.length == 2) {
                        loaded.add(new ScanPair(parts[0], parts[1]));
                    }
                }
            }
            // Show newest first, matching the scan-time insertion order
            for (int i = loaded.size() - 1; i >= 0; i--) {
                scanPairs.add(loaded.get(i));
            }
            itemCount = loaded.size();
            scanPairAdapter.notifyDataSetChanged();
            countText.setText("Items Scanned: " + itemCount);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        IntentFilter filter = new IntentFilter("org.recaplib.etruscan.SCAN");
        registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(scanReceiver);
    }

    private void addScan(String tray, String item) {
        // Insert at the start of the list
        scanPairs.add(0, new ScanPair(tray, item));
        scanPairAdapter.notifyItemInserted(0);

        // Optionally scroll to the top so the newest item is visible
        summaryRecyclerView.scrollToPosition(0);
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