package org.recaplib.etruscan;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ItemToShelfActivity extends AppCompatActivity {

    private TextView shelfText, itemText, counterText;
    private EditText positionInput;
    private Button addItemButton, nextShelfButton, endSessionButton;

    private String currentShelfBarcode = "";
    private int itemsOnCurrentShelf = 0;
    private int totalItems = 0;
    private int totalShelves = 0;

    private BufferedWriter writer;

    private RecyclerView summaryRecyclerView;
    private SummaryAdapter<ShelfItemEntry> summaryAdapter;
    private List<ShelfItemEntry> entries = new ArrayList<>();

    private enum ScanState {
        WAITING_FOR_SHELF,
        WAITING_FOR_ITEM
    }

    private ScanState scanState = ScanState.WAITING_FOR_SHELF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_item_to_shelf);

        shelfText = findViewById(R.id.shelfText);
        itemText = findViewById(R.id.itemText);
        counterText = findViewById(R.id.counterText);
        positionInput = findViewById(R.id.positionInput);
        addItemButton = findViewById(R.id.addItemButton);
        nextShelfButton = findViewById(R.id.nextShelfButton);
        endSessionButton = findViewById(R.id.endSessionButton);

        summaryRecyclerView = findViewById(R.id.its_summary_list);
        entries = new ArrayList<>();
        summaryAdapter = new SummaryAdapter<>(entries);
        summaryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        summaryRecyclerView.setAdapter(summaryAdapter);

        if (savedInstanceState != null) {
            currentShelfBarcode = savedInstanceState.getString("currentShelfBarcode", "");
            itemsOnCurrentShelf = savedInstanceState.getInt("itemsOnCurrentShelf", 0);
            totalItems = savedInstanceState.getInt("totalItems", 0);
            totalShelves = savedInstanceState.getInt("totalShelves", 0);
            scanState = ScanState.valueOf(savedInstanceState.getString("scanState", ScanState.WAITING_FOR_SHELF.name()));

            ArrayList<String> shelves = savedInstanceState.getStringArrayList("entryShelves");
            ArrayList<String> positions = savedInstanceState.getStringArrayList("entryPositions");
            ArrayList<String> items = savedInstanceState.getStringArrayList("entryItems");
            if (shelves != null && positions != null && items != null) {
                for (int i = 0; i < shelves.size(); i++) {
                    entries.add(new ShelfItemEntry(shelves.get(i), positions.get(i), items.get(i)));
                }
                summaryAdapter.notifyDataSetChanged();
            }
        } else {
            loadExistingEntries();
        }

        setupFile();
        setupUIHandlers();
        updateUIForState();
        updateCounter();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("currentShelfBarcode", currentShelfBarcode);
        outState.putInt("itemsOnCurrentShelf", itemsOnCurrentShelf);
        outState.putInt("totalItems", totalItems);
        outState.putInt("totalShelves", totalShelves);
        outState.putString("scanState", scanState.name());

        ArrayList<String> shelves = new ArrayList<>();
        ArrayList<String> positions = new ArrayList<>();
        ArrayList<String> items = new ArrayList<>();
        for (ShelfItemEntry entry : entries) {
            shelves.add(entry.shelf);
            positions.add(entry.position);
            items.add(entry.item);
        }
        outState.putStringArrayList("entryShelves", shelves);
        outState.putStringArrayList("entryPositions", positions);
        outState.putStringArrayList("entryItems", items);
    }

    private void loadExistingEntries() {
        File itsFile = FileHelper.getI2ShelfFile();
        if (!itsFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(itsFile))) {
            List<ShelfItemEntry> loaded = new ArrayList<>();
            HashSet<String> shelves = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                // Format: ITS{shelf}#{pos2}{item}
                if (line.startsWith("ITS") && line.contains("#")) {
                    String[] parts = line.substring(3).split("#", 2);
                    if (parts.length == 2 && parts[1].length() > 2) {
                        String shelf = parts[0];
                        String position = parts[1].substring(0, 2);
                        String item = parts[1].substring(2);
                        loaded.add(new ShelfItemEntry(shelf, position, item));
                        shelves.add(shelf);
                    }
                }
            }
            // Show newest first, matching the scan-time insertion order
            for (int i = loaded.size() - 1; i >= 0; i--) {
                entries.add(loaded.get(i));
            }
            totalItems = loaded.size();
            totalShelves = shelves.size();
            summaryAdapter.notifyDataSetChanged();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupFile() {
        try {
            File itsFile = FileHelper.getI2ShelfFile();
            writer = new BufferedWriter(new FileWriter(itsFile, true));
        } catch (IOException e) {
            FileHelper.appendToLog("Opening i2shelf.dat: " + e.getMessage());
            Toast.makeText(this, "Failed to open i2shelf.dat", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupUIHandlers() {
        addItemButton.setOnClickListener(v -> {
            itemText.setText(" - Item:");
            positionInput.setText("");

            // Disable button until a valid item is scanned again
            addItemButton.setEnabled(false);
            nextShelfButton.setEnabled(false);

            scanState = ScanState.WAITING_FOR_ITEM;
            updateUIForState();

            FileHelper.appendToLog("Prepared for additional item on shelf " + currentShelfBarcode);
        });

        nextShelfButton.setOnClickListener(v -> {
            if (itemsOnCurrentShelf == 0) {
                Toast.makeText(this, "Scan at least one item for this shelf first", Toast.LENGTH_SHORT).show();
                return;
            }

            itemsOnCurrentShelf = 0;
            currentShelfBarcode = "";
            shelfText.setText("Shelf:");
            itemText.setText(" - Item:");
            positionInput.setText("");

            scanState = ScanState.WAITING_FOR_SHELF;
            updateUIForState();

            FileHelper.appendToLog("Shelf complete. Awaiting next shelf scan.");
        });

        endSessionButton.setOnClickListener(v -> {
            try {
                if (writer != null) {
                    writer.close();
                    writer = null;
                }
                Toast.makeText(this, "Session saved to i2shelf.dat", Toast.LENGTH_LONG).show();
                FileHelper.appendToLog("Session ended. File closed.");
            } catch (IOException e) {
                Toast.makeText(this, "Error saving i2shelf.dat", Toast.LENGTH_LONG).show();
                FileHelper.appendToLog("Error closing i2shelf.dat: " + e.getMessage());
            }

            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void handleScannedBarcode(String scanned) {
        switch (scanState) {
            case WAITING_FOR_SHELF:
                if (!BarcodeUtils.validateShelf(scanned)) {
                    Toast.makeText(this, "Invalid shelf barcode", Toast.LENGTH_SHORT).show();
                    return;
                }

                currentShelfBarcode = scanned;
                shelfText.setText("Shelf: " + currentShelfBarcode);
                itemsOnCurrentShelf = 0;
                totalShelves++;
                updateCounter();
                positionInput.setText("");
                itemText.setText(" - Item:");
                scanState = ScanState.WAITING_FOR_ITEM;
                updateUIForState();
                break;

            case WAITING_FOR_ITEM:
                String position = positionInput.getText().toString().trim();
                if (!BarcodeUtils.validatePosition(position)) {
                    Toast.makeText(this, "Enter valid shelf position (00-99) before scanning item", Toast.LENGTH_SHORT).show();
                    return;
                }

                String cleanedItem = BarcodeUtils.cleanItemBarcode(scanned);
                if (cleanedItem == null) {
                    Toast.makeText(this, "Invalid item barcode", Toast.LENGTH_SHORT).show();
                    FileHelper.appendToLog("Item scanned: " + scanned + " - INVALID");
                    return;
                }

                itemText.setText(" - Item: " + cleanedItem);
                String line = "ITS" + currentShelfBarcode + "#" + position + cleanedItem;
                writeToFile(line);
                addEntry(currentShelfBarcode, position, cleanedItem);
                FileHelper.appendToLog("Scanned item " + cleanedItem + " at position " + position + " on shelf " + currentShelfBarcode);

                itemsOnCurrentShelf++;
                totalItems++;
                updateCounter();

                // Enable the buttons after successful item scan
                addItemButton.setEnabled(true);
                nextShelfButton.setEnabled(true);

                // Remain in WAITING_FOR_ITEM to allow more items
                break;
        }
    }

    private void writeToFile(String line) {
        try {
            if (writer != null) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            } else {
                Toast.makeText(this, "File writer not initialized", Toast.LENGTH_SHORT).show();
                FileHelper.appendToLog("Writer was null when writing line: " + line);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error writing to i2shelf.dat", Toast.LENGTH_LONG).show();
            FileHelper.appendToLog("Writing line to i2shelf.dat: " + e.getMessage());
        }
    }

    private void addEntry(String shelf, String position, String item) {
        entries.add(0, new ShelfItemEntry(shelf, position, item));
        summaryAdapter.notifyItemInserted(0);
        summaryRecyclerView.scrollToPosition(0);
    }

    private void updateCounter() {
        counterText.setText("Scanned: " + totalItems + " items on " + totalShelves + " shelves");
    }

    private void updateUIForState() {
        switch (scanState) {
            case WAITING_FOR_SHELF:
                shelfText.setText("Shelf:");
                itemText.setText(" - Item:");
                addItemButton.setEnabled(false);
                positionInput.setText("");
                nextShelfButton.setEnabled(false);
                break;

            case WAITING_FOR_ITEM:
                shelfText.setText("Shelf: " + currentShelfBarcode);
                itemText.setText(" - Item:");
                positionInput.setEnabled(true);
                positionInput.setText("");
                positionInput.requestFocus();

                addItemButton.setEnabled(false);  // Enable only after valid item scanned
                nextShelfButton.setEnabled(itemsOnCurrentShelf > 0); // Enable if at least one item added

                break;
        }
    }

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.hasExtra("com.symbol.datawedge.data_string")) {
                String scanned = intent.getStringExtra("com.symbol.datawedge.data_string");
                if (scanned != null) {
                    handleScannedBarcode(scanned.trim());
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction("org.recaplib.etruscan.SCAN");  // Must match DataWedge Intent Action
        registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(scanReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            // ignore
            FileHelper.appendToLog("Closing writer onDestroy: " + e.getMessage());
        }
    }
}
