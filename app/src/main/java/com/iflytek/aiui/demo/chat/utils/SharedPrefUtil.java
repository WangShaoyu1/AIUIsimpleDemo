package com.iflytek.aiui.demo.chat.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * SharedPref工具类。
 */
public class SharedPrefUtil {
    private static final String PREF_NAME = "settings";
    private static final String IS_PRIVACY_POLICY_AGREED = "is_privacy_policy_agreed";
    private static final String JSONARRAY_VOICE_RES_ID = "jsonarray_voice_res_id";

    public static boolean isPrivacyPolicyAgreed(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return pref.getBoolean(IS_PRIVACY_POLICY_AGREED, false);
    }

    public static void saveIsPrivacyPolicyAgreed(Context context, boolean isAgreed) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pref.edit()
                .putBoolean(IS_PRIVACY_POLICY_AGREED, isAgreed)
                .apply();
    }

    public static void clearResIdList(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pref.edit()
                .putString(JSONARRAY_VOICE_RES_ID, "")
                .apply();
    }

    public static List<String> getVoiceResIdList(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String arrStr = pref.getString(JSONARRAY_VOICE_RES_ID, "");

        JSONArray jsonArray = null;
        if (TextUtils.isEmpty(arrStr)) {
            jsonArray = new JSONArray();
        } else {
            try {
                jsonArray = new JSONArray(arrStr);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        List<String> result = new ArrayList<>();
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    result.add(jsonArray.getString(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    public static void removeVoiceResId(Context context, String resId) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String arrStr = pref.getString(JSONARRAY_VOICE_RES_ID, "");

        JSONArray jsonArray = null;
        if (TextUtils.isEmpty(arrStr)) {
            jsonArray = new JSONArray();
        } else {
            try {
                jsonArray = new JSONArray(arrStr);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            if (jsonArray != null) {
                int i = 0;
                while (i < jsonArray.length()) {
                    if (jsonArray.getString(i).equals(resId)) {
                        break;
                    }

                    i++;
                }

                jsonArray.remove(i);

                pref.edit()
                        .putString(JSONARRAY_VOICE_RES_ID, jsonArray.toString())
                        .apply();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static List<String> setVoiceRes(Context context, JSONArray resStrArray) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pref.edit()
                .putString(JSONARRAY_VOICE_RES_ID, resStrArray.toString())
                .apply();

        List<String> result = new ArrayList<>();
        for (int i = 0; i < resStrArray.length(); i++) {
            try {
                result.add(resStrArray.getString(i));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static void addVoiceResId(Context context, String resId) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String arrStr = pref.getString(JSONARRAY_VOICE_RES_ID, "");

        JSONArray jsonArray = null;
        if (TextUtils.isEmpty(arrStr)) {
            jsonArray = new JSONArray();
        } else {
            try {
                jsonArray = new JSONArray(arrStr);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (jsonArray != null) {
            JSONArray newArray = new JSONArray();
            newArray.put(resId);

            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    newArray.put(jsonArray.getString(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            pref.edit()
                    .putString(JSONARRAY_VOICE_RES_ID, newArray.toString())
                    .apply();
        }
    }
}
