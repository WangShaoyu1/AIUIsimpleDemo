package com.iflytek.aiui.demo.chat.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final String LOG_FILE_NAME = "aiui_log.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public static void log(String tag, String message) {
        try {
            // 获取外部存储目录
            File externalDir = Environment.getExternalStorageDirectory();
            File logFile = new File(externalDir, LOG_FILE_NAME);

            // 检查是否有写入权限
            if (externalDir.canWrite()) {
                FileWriter writer = new FileWriter(logFile, true);
                writer.append(DATE_FORMAT.format(new Date()))
                      .append(" [")
                      .append(tag)
                      .append("] ")
                      .append(message)
                      .append("\n");
                writer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log to file", e);
        }
    }

    // 添加信息级别的日志方法
    public static void i(String tag, String message) {
        log("INFO - " + tag, message);
    }

    // 添加调试级别的日志方法
    public static void d(String tag, String message) {
        log("DEBUG - " + tag, message);
    }
}