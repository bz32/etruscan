package com.example.refileapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startButton = findViewById(R.id.button_start);
        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RefileActivity.class);
            startActivity(intent);
        });

        Button uploadButton = findViewById(R.id.button_upload_las);
        uploadButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, UploadActivity.class);
            startActivity(intent);
        });
    }
}