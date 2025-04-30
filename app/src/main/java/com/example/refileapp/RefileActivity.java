package com.example.refileapp;

import android.content.*;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;

public class RefileActivity extends AppCompatActivity {

    private String currentTray = null;
    private int itemCount = 0;
    private File file;
    private BufferedWriter writer;

    private TextView trayText, countText, itemText;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String scanned = intent.getStringExtra("com.symbol.datawedge.data_string");
            if (scanned == null || scanned.isEmpty()) return;

            if (currentTray == null) {
                currentTray = scanned;
                trayText.setText("Tray: " + currentTray);
            } else {
                String cleanedItem = cleanItemBarcode(scanned);
                if (cleanedItem == null) {
                    Toast.makeText(context, "Invalid item barcode", Toast.LENGTH_SHORT).show();
                    return;
                }

                String line = "REF" + currentTray + "#" + cleanedItem;
                writeToFile(line);
                itemText.setText("Item: " + cleanedItem);
                itemCount++;
                countText.setText("Items Scanned: " + itemCount);
                currentTray = null;
                trayText.setText("Tray: (scan tray)");
            }
        }
    };

    private String cleanItemBarcode(String raw) {
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
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "RefileApp");
            if (!dir.exists()) dir.mkdirs();
            file = new File(dir, "refile.dat");
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

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.example.refileapp.SCAN");
        registerReceiver(scanReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(scanReceiver);
    }
}