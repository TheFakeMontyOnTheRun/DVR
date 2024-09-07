package com.drbeef.dvr;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipExtractor {

    private final ExecutorService executorService;
    private final Handler handler;

    public ZipExtractor() {
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
    }

    public void extractZipInBackground(String zipFilePath, String outputDirectoryPath, OnZipExtractListener listener) {
        executorService.execute(() -> {
            try {
                extractZip(zipFilePath, outputDirectoryPath);
                handler.post(listener::onSuccess);
            } catch (IOException e) {
                handler.post(() -> listener.onFailure(e));
            }
        });
    }

    private void extractZip(String zipFilePath, String outputDirectoryPath) throws IOException {
        File outputDir = new File(outputDirectoryPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                File outputFile = new File(outputDirectoryPath, zipEntry.getName());

                if (zipEntry.isDirectory()) {
                    if (!outputFile.exists()) {
                        outputFile.mkdirs();
                    }
                } else {
                    // Remove the zip filename, as the main game expects it at the root of /DVR
                    String finalFilename = outputFile.getPath().replace("/freedoom-0.10.1", "");
                    try (FileOutputStream fileOutputStream = new FileOutputStream(finalFilename)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, length);
                        }
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    public interface OnZipExtractListener {
        void onSuccess();

        void onFailure(Exception e);
    }
}
