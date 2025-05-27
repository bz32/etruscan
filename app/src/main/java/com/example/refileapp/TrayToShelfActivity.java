package com.example.refileapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TrayToShelfActivity extends AppCompatActivity {

    private TextView shelfText, trayText, counterText;
    private EditText positionInput;
    private Button addTrayButton, nextShelfButton, endSessionButton;

    private String currentShelfBarcode = "";
    private int traysOnCurrentShelf = 0;
    private int totalTrays = 0;
    private int totalShelves = 0;

    private BufferedWriter writer;

    private enum ScanState {
        WAITING_FOR_SHELF,
        WAITING_FOR_TRAY
    }

    private ScanState scanState = ScanState.WAITING_FOR_SHELF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tray_to_shelf);

        // UI elements
        shelfText = findViewById(R.id.shelfText);
        trayText = findViewById(R.id.trayText);
        counterText = findViewById(R.id.counterText);
        positionInput = findViewById(R.id.positionInput);
        addTrayButton = findViewById(R.id.addTrayButton);
        nextShelfButton = findViewById(R.id.nextShelfButton);
        endSessionButton = findViewById(R.id.endSessionButton);

        setupFile();
        setupUIHandlers();
        updateUIForState();

    }

    private void setupFile() {
        try {
            File ttsFile = FileHelper.getT2ShelfFile();
            writer = new BufferedWriter(new FileWriter(ttsFile, true));
        } catch (IOException e) {
            FileHelper.appendToLog("Opening t2shelf.dat: " + e.getMessage());
            Toast.makeText(this, "Failed to open t2shelf.dat", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void setupUIHandlers() {
        addTrayButton.setOnClickListener(v -> {
            trayText.setText("Tray:");
            positionInput.setText("");

            // Disable button until a valid tray is scanned again
            addTrayButton.setEnabled(false);
            nextShelfButton.setEnabled(false);

            // After adding tray, we want to wait for next tray or shelf,
            // so scanState stays in WAITING_FOR_TRAY for additional trays.
            scanState = ScanState.WAITING_FOR_TRAY;
            updateUIForState();

            FileHelper.appendToLog("Prepared for additional tray on shelf " + currentShelfBarcode);
        });

        nextShelfButton.setOnClickListener(v -> {
            if (traysOnCurrentShelf == 0) {
                Toast.makeText(this, "Scan at least one tray for this shelf first", Toast.LENGTH_SHORT).show();
                return;
            }

            traysOnCurrentShelf = 0;
            currentShelfBarcode = "";
            shelfText.setText("Shelf:");
            trayText.setText("Tray:");
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
                Toast.makeText(this, "Session saved to t2shelf.dat", Toast.LENGTH_LONG).show();
                FileHelper.appendToLog("Session ended. File closed.");
            } catch (IOException e) {
                Toast.makeText(this, "Error saving t2shelf.dat", Toast.LENGTH_LONG).show();
                FileHelper.appendToLog("Error closing t2shelf.dat: " + e.getMessage());
            }

            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private void handleScannedBarcode(String scanned) {
        switch (scanState) {
            case WAITING_FOR_SHELF:
                if (!validateShelf(scanned)) {
                    Toast.makeText(this, "Invalid shelf barcode", Toast.LENGTH_SHORT).show();
                    return;
                }

                currentShelfBarcode = scanned.length() == 5 ? "0" + scanned : scanned;
                shelfText.setText("Shelf: " + currentShelfBarcode);
                traysOnCurrentShelf = 0;
                totalShelves++;
                updateCounter();
                positionInput.setText("");  // Clear any leftover or autofilled value
                scanState = ScanState.WAITING_FOR_TRAY;
                updateUIForState();
                break;

            case WAITING_FOR_TRAY:
                String position = positionInput.getText().toString().trim();
                if (!validatePosition(position)) {
                    Toast.makeText(this, "Enter valid shelf position (00-99) before scanning tray", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!validateTray(scanned)) {
                    Toast.makeText(this, "Invalid tray barcode", Toast.LENGTH_SHORT).show();
                    return;
                }

                trayText.setText("Tray: " + scanned);
                String line = "TTS" + currentShelfBarcode + position + scanned;
                writeToFile(line);
                FileHelper.appendToLog("Scanned tray " + scanned + " at position " + position + " on shelf " + currentShelfBarcode);

                traysOnCurrentShelf++;
                totalTrays++;
                updateCounter();

                // Enable the buttons after successful tray scan
                addTrayButton.setEnabled(true);
                nextShelfButton.setEnabled(true);

                // Remain in WAITING_FOR_TRAY to allow more trays
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
            Toast.makeText(this, "Error writing to t2shelf.dat", Toast.LENGTH_LONG).show();
            FileHelper.appendToLog("Writing line to t2shelf.dat: " + e.getMessage());
        }
    }

    private void updateCounter() {
        counterText.setText("Scanned: " + totalTrays + " trays on " + totalShelves + " shelves");
    }

    private void updateUIForState() {
        switch (scanState) {
            case WAITING_FOR_SHELF:
                shelfText.setText("Shelf:");
                trayText.setText("Tray:");
                addTrayButton.setEnabled(false);
                positionInput.setText("");
                nextShelfButton.setEnabled(false);
                // Possibly show a Toast or UI hint: "Please scan a shelf barcode"
                break;

            case WAITING_FOR_TRAY:
                shelfText.setText("Shelf: " + currentShelfBarcode);
                trayText.setText("Tray:");
                positionInput.setEnabled(true);
                positionInput.setText("");
                positionInput.requestFocus();

                addTrayButton.setEnabled(false);  // Enable only after valid tray scanned
                nextShelfButton.setEnabled(traysOnCurrentShelf > 0); // Enable if at least one tray added

                break;
        }
    }

    private boolean validatePosition(String pos) {
        return pos.matches("\\d{2}");
    }

    private boolean validateTray(String tray) {
        return tray.matches("[A-Z]{2}\\d{5,6}");
    }

    private boolean validateShelf(String shelf) {
        return shelf.matches("\\d{5,6}");
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
        filter.addAction("com.example.refileapp.SCAN");  // Must match DataWedge Intent Action
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
