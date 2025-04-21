package com.iflytek.aiui.demo.chat.util;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.UUID;

/**
 * 设备工具类。
 */
public class DeviceUtils {
    private static final String TAG = "DeviceUtils";
    private static final String ID_CACHE_FILE = "/sdcard/android_id.txt";

    /**
     * 获取设备id。
     *
     * @param context 上下文
     * @return
     */
    public static String getDeviceId(Context context) {
        String deviceId = "";

        try {
            File idFile = new File(ID_CACHE_FILE);

            do {
                deviceId = readDeviceId(idFile);
                if (!TextUtils.isEmpty(deviceId)) {
                    break;
                }

//                 deviceId = getAndroidId(context);
//                 if (!TextUtils.isEmpty(deviceId)) {
//                     break;
//                 }

                deviceId = UUID.randomUUID().toString();
                deviceId = deviceId.replaceAll("-", "");
            } while (false);

            writeDeviceId(deviceId, idFile);

            return deviceId;
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return "";
    }

    private static String readDeviceId(File cacheFile) {
        try {
            RandomAccessFile f = new RandomAccessFile(cacheFile, "r");
            byte[] bytes = new byte[(int) f.length()];

            f.readFully(bytes);
            f.close();

            return new String(bytes, Charset.defaultCharset());
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return "";
    }

    private static void writeDeviceId(String deviceId, File cacheFile) throws IOException {
        if (cacheFile.exists()) {
            cacheFile.deleteOnExit();
        }

        FileOutputStream out = new FileOutputStream(cacheFile);
        out.write(deviceId.getBytes(Charset.defaultCharset()));
        out.close();
    }

    public static String getAndroidId(Context context) {
        return Settings.System.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * 获取Wifi Mac。
     *
     * @param context
     * @return
     */
    public static String getWifiMac(Context context) {
        String result = "";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface iF = interfaces.nextElement();
                    byte[] addr = iF.getHardwareAddress();
                    long sum = 0;
                    for (byte item : addr) {
                        sum += Math.abs(item);
                    }
                    if (addr == null || addr.length == 0 || sum < Byte.MAX_VALUE) {
                        continue;
                    }
                    //其他网卡（如rmnet0）的MAC，跳过
                    if ("wlan0".equalsIgnoreCase(iF.getName()) || "eth0".equalsIgnoreCase(iF.getName())) {
                        StringBuilder buf = new StringBuilder();
                        for (byte b : addr) {
                            buf.append(String.format("%02X:", b));
                        }
                        if (buf.length() > 0) {
                            buf.deleteCharAt(buf.length() - 1);
                        }
                        String mac = buf.toString();
                        if (mac.length() > 0) {
                            result = mac;
                            return result;
                        }
                    }

                }
            } catch (Exception e) {
                Log.w(TAG, e.toString());
            }
        } else {
            try {
                // MAC地址
                WifiManager wifi =
                        (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    WifiInfo wiinfo = wifi.getConnectionInfo();
                    result = wiinfo.getMacAddress();
                }
            } catch (Throwable e) {
                Log.w(TAG, "failed to get mac");
            }
        }

        return result;
    }
}
