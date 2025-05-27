package com.example.refileapp;

import android.os.Environment;
import android.util.Log;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileHelper {

    private static final String APP_FOLDER_NAME = "RefileApp";
    private static final String REFILE_FILENAME = "refile.dat";
    private static final String SCANLOG_FILENAME = "scanlog.txt";
    private static final String T2SHELF_FILENAME = "t2shelf.dat";

    // Get the app-specific folder (Documents/RefileApp)
    public static File getAppDirectory() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), APP_FOLDER_NAME);
        if (!dir.exists()) {
            dir.mkdirs();  // Ensure the directory exists
        }
        return dir;
    }

    // Get the full path to refile.dat
    public static File getRefileFile() {
        return new File(getAppDirectory(), REFILE_FILENAME);
    }

    // Get the full path to scanlog.txt
    public static File getScanLogFile() {
        return new File(getAppDirectory(), SCANLOG_FILENAME);
    }

    // Get the full path to t2shelf.dat
    public static File getT2ShelfFile() {
        return new File(getAppDirectory(), T2SHELF_FILENAME);
    }
    public static void appendToLog(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String fullMessage = timestamp + " - " + message + "\n";

        File logFile = getScanLogFile();
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


    // Optional: generic helper to get any file inside the app directory
    public static File getAppFile(String filename) {
        return new File(getAppDirectory(), filename);
    }


}
