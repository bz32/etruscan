package org.recaplib.etruscan;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileHelper {

    private static final String APP_FOLDER_NAME = "etruscan";
    private static final String REFILE_FILENAME = "refile.dat";
    private static final String SCANLOG_FILENAME = "scanlog.txt";
    private static final String T2SHELF_FILENAME = "t2shelf.dat";

    private static File appDirectory;

    public static void init(Context context) {
        appDirectory = new File(context.getExternalFilesDir(null), APP_FOLDER_NAME);
        if (!appDirectory.exists()) appDirectory.mkdirs();
    }

    public static File getAppDirectory() {
        return appDirectory;
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

    public static boolean archiveFile(File file) {
        if (!file.exists()) return false;

        String name = file.getName();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String newName = name.replace(".dat", "-" + timestamp + ".dat");
        File newFile = new File(file.getParent(), newName);

        return file.renameTo(newFile);
    }

    public static String readFileContents(File f) {
        StringBuilder sb = new StringBuilder();
        if (f == null || !f.exists()) return "(file not found)";

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e("FileHelper", "readFileContents failed for " + f.getAbsolutePath(), e);
            return "(Error reading file: " + e.getMessage() + ")";
        }
        return sb.toString();
    }

}
