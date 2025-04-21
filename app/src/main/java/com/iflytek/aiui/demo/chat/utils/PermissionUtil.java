package com.iflytek.aiui.demo.chat.utils;

import android.app.Activity;
import android.os.Build;

import androidx.core.app.ActivityCompat;

/**
 * 权限工具类。
 */
public class PermissionUtil {

    /**
     * 申请权限
     */
    public static void requestPermissions(Activity activity, String[] perms, int requestCode) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(activity, perms, requestCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
