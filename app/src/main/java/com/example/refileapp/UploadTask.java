package com.example.refileapp;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.content.Intent;
import android.view.View;
import android.widget.Toast;
import android.widget.ProgressBar;
import com.jcraft.jsch.*;

import java.io.File;
import java.io.FileInputStream;
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

    // Upload method, now using ExecutorService
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
        File refileFile = FileHelper.getRefileFile();

        if (!refileFile.exists()) {
            return "Error: refile.dat not found.";
        }

        Session session = null;
        Channel channel = null;

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, server, port);
            session.setPassword(password);

            // Recommended config for SFTP
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");  // For testing; in production you may want to verify host keys
            session.setConfig(config);

            session.connect();

            channel = session.openChannel("sftp");
            channel.connect();

            ChannelSftp sftp = (ChannelSftp) channel;

            FileInputStream fis = new FileInputStream(refileFile);
            sftp.cd(remotePath);  // Ensure this exists on server
            sftp.put(fis, "refile.dat");
            fis.close();

            return "Upload successful.";

        } catch (Exception e) {
            return "Upload failed: " + e.getMessage();
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }
}
