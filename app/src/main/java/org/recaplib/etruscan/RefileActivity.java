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
    private String currentShelf = null;
    private int itemCount = 0;
    private File file;
    private File boxFile;
    private BufferedWriter writer;
    private BufferedWriter boxWriter;

    private TextView trayText, countText, itemText;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private RecyclerView summaryRecyclerView;
    private SummaryAdapter<RefileEntry> summaryAdapter;
    private List<RefileEntry> entries = new ArrayList<>();


    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String scanned = intent.getStringExtra("com.symbol.datawedge.data_string");
            if (scanned == null || scanned.isEmpty()) return;

            if (currentTray == null && currentShelf == null) {
                String upper = scanned.toUpperCase();
                if (BarcodeUtils.validateTray(upper)) {
                    currentTray = upper;
                    trayText.setText("Tray: " + currentTray);
                    FileHelper.appendToLog("Tray scanned: " + scanned + " - VALID");
                } else if (BarcodeUtils.validateShelf(scanned)) {
                    currentShelf = scanned;
                    trayText.setText("Shelf: " + currentShelf);
                    FileHelper.appendToLog("Shelf scanned: " + scanned + " - VALID");
                } else {
                    trayText.setText("Invalid tray/shelf barcode: " + scanned);
                    FileHelper.appendToLog("Tray/shelf scanned: " + scanned + " - INVALID");
                    playErrorTone(); // 👈 Play bonk for invalid tray/shelf
                    handler.postDelayed(() -> trayText.setText("Tray/Shelf: (scan tray or shelf)"), 2000);
                }
            } else {
                String cleanedItem = BarcodeUtils.cleanItemBarcode(scanned);
                Log.d("SCAN", "Scanned item: " + scanned + ", cleaned: " + cleanedItem);
                if (cleanedItem == null) {
                    Toast.makeText(context, "Invalid item barcode", Toast.LENGTH_SHORT).show();
                    FileHelper.appendToLog("Item scanned: " + scanned + " - INVALID");
                    playErrorTone(); // 👈 Play bonk for invalid item
                    return;
                }
                FileHelper.appendToLog("Item scanned: " + scanned + " - VALID (cleaned: " + cleanedItem + ")");

                if (currentTray != null) {
                    String line = "REF" + currentTray + "#" + cleanedItem;
                    writeToFile(writer, line);
                    addEntry("Tray", currentTray, cleanedItem);
                } else {
                    String line = "BRF" + currentShelf + "#" + cleanedItem;
                    writeToFile(boxWriter, line);
                    addEntry("Shelf", currentShelf, cleanedItem);
                }

                itemText.setText("Item: " + cleanedItem);
                itemCount++;
                countText.setText("Items Scanned: " + itemCount);

                // Auto-clear the item text after 2 seconds
                handler.postDelayed(() -> itemText.setText("Item: (scan item)"), 2000);

                currentTray = null;
                currentShelf = null;
                trayText.setText("Tray/Shelf: (scan tray or shelf)");
            }
        }
    };

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
        entries = new ArrayList<>();
        summaryAdapter = new SummaryAdapter<>(entries);

        summaryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        summaryRecyclerView.setAdapter(summaryAdapter);

        endBtn.setOnClickListener(v -> {
            closeWriter();
            finish();
        });

        if (savedInstanceState != null) {
            currentTray = savedInstanceState.getString("currentTray");
            currentShelf = savedInstanceState.getString("currentShelf");
            itemCount = savedInstanceState.getInt("itemCount", 0);
            ArrayList<String> types = savedInstanceState.getStringArrayList("entryTypes");
            ArrayList<String> containerIds = savedInstanceState.getStringArrayList("entryContainerIds");
            ArrayList<String> items = savedInstanceState.getStringArrayList("entryItems");
            if (types != null && containerIds != null && items != null) {
                for (int i = 0; i < types.size(); i++) {
                    entries.add(new RefileEntry(types.get(i), containerIds.get(i), items.get(i)));
                }
                summaryAdapter.notifyDataSetChanged();
            }
            if (currentTray != null) {
                trayText.setText("Tray: " + currentTray);
            } else if (currentShelf != null) {
                trayText.setText("Shelf: " + currentShelf);
            } else {
                trayText.setText("Tray/Shelf: (scan tray or shelf)");
            }
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
        outState.putString("currentShelf", currentShelf);
        outState.putInt("itemCount", itemCount);
        ArrayList<String> types = new ArrayList<>();
        ArrayList<String> containerIds = new ArrayList<>();
        ArrayList<String> items = new ArrayList<>();
        for (RefileEntry entry : entries) {
            types.add(entry.containerType);
            containerIds.add(entry.containerId);
            items.add(entry.item);
        }
        outState.putStringArrayList("entryTypes", types);
        outState.putStringArrayList("entryContainerIds", containerIds);
        outState.putStringArrayList("entryItems", items);
    }

    private void loadExistingScans() {
        List<RefileEntry> loaded = new ArrayList<>();
        loaded.addAll(readEntries(FileHelper.getRefileFile(), "REF", "Tray"));
        loaded.addAll(readEntries(FileHelper.getBoxRefFile(), "BRF", "Shelf"));

        // Show newest first; cross-file chronological order can't be recovered exactly
        // since neither file records timestamps, so trays and shelves are each kept in
        // their own file order.
        for (int i = loaded.size() - 1; i >= 0; i--) {
            entries.add(loaded.get(i));
        }
        itemCount = loaded.size();
        summaryAdapter.notifyDataSetChanged();
        countText.setText("Items Scanned: " + itemCount);
    }

    private List<RefileEntry> readEntries(File f, String prefix, String label) {
        List<RefileEntry> result = new ArrayList<>();
        if (!f.exists()) return result;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(prefix) && line.contains("#")) {
                    String[] parts = line.substring(prefix.length()).split("#", 2);
                    if (parts.length == 2) {
                        result.add(new RefileEntry(label, parts[0], parts[1]));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    private void setupFile() {
        try {
            file = FileHelper.getRefileFile();
            File dir = file.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            writer = new BufferedWriter(new FileWriter(file, true));

            boxFile = FileHelper.getBoxRefFile();
            boxWriter = new BufferedWriter(new FileWriter(boxFile, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeToFile(BufferedWriter w, String line) {
        try {
            w.write(line);
            w.newLine();
            w.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeWriter() {
        try {
            if (writer != null) writer.close();
            if (boxWriter != null) boxWriter.close();
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

    private void addEntry(String containerType, String containerId, String item) {
        // Insert at the start of the list
        entries.add(0, new RefileEntry(containerType, containerId, item));
        summaryAdapter.notifyItemInserted(0);

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
