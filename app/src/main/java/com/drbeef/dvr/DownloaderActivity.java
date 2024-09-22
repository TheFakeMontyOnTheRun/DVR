package com.drbeef.dvr;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import doom.util.DoomTools;

public class DownloaderActivity extends AppCompatActivity {
    private long downloadId = -1;

    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (downloadId == id) {
                Log.i("DVR", "Download ID: " + downloadId);

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor c = dm.query(query);
                if (c.moveToFirst()) {
                    int colIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(colIndex)) {
                        int strId = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                        String uriString = c.getString(strId).replaceFirst("file:///", "/");
                        ZipExtractor zipExtractor = new ZipExtractor();
                        String outputDirectoryPath = context.getFilesDir().getPath() + "/DVR";

                        zipExtractor.extractZipInBackground(uriString, outputDirectoryPath, new ZipExtractor.OnZipExtractListener() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> startGame(context));
                            }

                            @Override
                            public void onFailure(Exception e) {
                                e.printStackTrace();
                            }
                        });

                    } else {
                        Log.w("DVR", "Download Unsuccessful, Status Code: " + c.getInt(colIndex));
                    }
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloader);
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        startGame(DownloaderActivity.this);
    }

    private void startGame(Context context) {
        if (!DoomTools.FORCE_USE_WAD_FROM_ASSETS && !DoomTools.wadsExist(context)) {
            if (downloadId == -1) {
                fetchAndExtractFreeDoom(context);
            }
        } else {
            startActivity(new Intent(context, MainActivity.class));
            finish();
        }
    }

    private void fetchAndExtractFreeDoom(Context context) {
        String url = "https://github.com/freedoom/freedoom/releases/download/v0.10.1/freedoom-0.10.1.zip";
        String fileName = url.substring(url.lastIndexOf('/') + 1);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setTitle(fileName);

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadId = dm.enqueue(request);
    }
}