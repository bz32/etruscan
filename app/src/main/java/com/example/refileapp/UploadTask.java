package com.example.refileapp;

import android.content.Context;
import com.jcraft.jsch.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadTask {
    private final String server;
    private final int port;
    private final String remotePath;
    private final String username;
    private final String password;
    private final Context context;
    private final UploadListener listener;  // listener to notify UploadActivity

    public interface UploadListener {
        void onUploadStarted();
        void onUploadFinished(String result);
    }

    public UploadTask(Context context, String server, int port, String remotePath, String username, String password, UploadListener listener) {
        this.context = context;
        this.server = server;
        this.port = port;
        this.remotePath = remotePath;
        this.username = username;
        this.password = password;
        this.listener = listener;
    }

    public void upload() {
        // Notify activity about the upload start (UI updates handled in the activity)
        if (listener != null) {
            listener.onUploadStarted();
        }

        // Use ExecutorService to execute the task in the background
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> {
            String result = doUpload();

            // Notify the activity when the task is complete
            if (listener != null) {
                listener.onUploadFinished(result);
            }
        });
    }

    // Background task logic
    private String doUpload() {

        List<File> filesToUpload = new ArrayList<>();
        if (FileHelper.getRefileFile().exists()) filesToUpload.add(FileHelper.getRefileFile());
        if (FileHelper.getT2ShelfFile().exists()) filesToUpload.add(FileHelper.getT2ShelfFile());

        if (filesToUpload.isEmpty()) return "No data files found to upload.";

        Session session = null;
        Channel channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, server, port);
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect();

            channel = session.openChannel("sftp");
            channel.connect();

            ChannelSftp sftp = (ChannelSftp) channel;

            sftp.cd(remotePath);  // Ensure this exists on server

            for (File file : filesToUpload) {
                FileInputStream fis = new FileInputStream(file);
                sftp.put(fis, file.getName());
                fis.close();

                String newName = file.getName().replace(".dat", "-" + timestamp() + ".dat");
                File renamed = new File(file.getParent(), newName);
                if (!file.renameTo(renamed)) {
                    return "Upload succeeded but failed to rename " + file.getName();
                }
            }

            return "Upload successful.";

        } catch (Exception e) {
            return "Upload failed: " + e.getMessage();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }
}
