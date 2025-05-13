package com.example.refileapp;

import android.os.Environment;
import java.io.File;
public class FileHelper {

    private static final String APP_FOLDER_NAME = "RefileApp";
    private static final String REFILE_FILENAME = "refile.dat";
    private static final String SCANLOG_FILENAME = "scanlog.txt";

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

    // Optional: generic helper to get any file inside the app directory
    public static File getAppFile(String filename) {
        return new File(getAppDirectory(), filename);
    }


}
