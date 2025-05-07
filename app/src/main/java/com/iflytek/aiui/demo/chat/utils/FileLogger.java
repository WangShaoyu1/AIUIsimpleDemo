package com.iflytek.aiui.demo.chat.utils;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;  // 用于定义 JSON 媒体类型

public class FileLogger {
    private static final String TAG = "FileLogger";
    private static final String LOG_FILE_NAME = "aiui_log.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
//    private static final String SERVER_URL = "http://192.168.43.6:5002/log"; // Python 服务器地址
    private static final String SERVER_URL = "http://172.19.55.127:5002/log"; // Python 服务器地址
    private static final OkHttpClient client = new OkHttpClient();

    public static void log_local(String tag, String message) {
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

    public static void log(String tag, String message) {
        Log.d(TAG, "尝试发送日志: " + tag + " | " + message); // ① 确认方法被调用
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("tag", tag);
                json.put("message", message);

                // 修正点：使用新版 RequestBody.create()
                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(json.toString(), JSON);

                Request request = new Request.Builder()
                        .url(SERVER_URL)
                        .post(body)
                        .addHeader("Content-Type", "application/json") // 关键头！
                        .build();

                Log.d(TAG, "请求准备完成，开始发送..." + message); // ② 确认请求构造完成

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Failed to send log: " + response.code());
                    }
                    Log.d(TAG, "服务器响应: " + response.code() + " | " + response.body().string()); // ③ 关键日志
                }
            } catch (Exception e) {
                Log.e(TAG, "Network logging failed", e);
            }
        }).start();
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