package org.recaplib.etruscan;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start Refile session
        Button startRefileButton = findViewById(R.id.button_start);
        startRefileButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RefileActivity.class);
            startActivity(intent);
        });

        // Start Tray-to-shelf session
        Button startTrayToShelfButton = findViewById(R.id.button_start_tray_to_shelf);
        startTrayToShelfButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, TrayToShelfActivity.class);
            startActivity(intent);
        });

        // Upload to LAS
        Button uploadButton = findViewById(R.id.button_upload_las);
        uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UploadActivity.class);
            startActivity(intent);
        });

    }
}