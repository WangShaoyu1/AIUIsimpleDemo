package com.iflytek.aiui.demo.chat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.iflytek.aiui.Version;
import com.iflytek.aiui.demo.chat.ui.PrivacyPolicyDialog;
import com.iflytek.aiui.demo.chat.utils.FucUtil;
import com.iflytek.aiui.demo.chat.utils.SharedPrefUtil;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 主页面。
 */
public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_nlp_demo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NlpDemoActivity.class));
            }
        });

        findViewById(R.id.btn_voice_clone_demo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isVoiceCloneSupported()) {
                    // 声音复刻默认未开通，使用之前需要联系商务开通
                    // 联系方式：aiui_support@iflytek.com
                    startActivity(new Intent(MainActivity.this, VoiceCloneDemoActivity.class));
                } else {
                    Toast.makeText(MainActivity.this, "当前版本不支持", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (!SharedPrefUtil.isPrivacyPolicyAgreed(MainActivity.this)) {
            showPrivacyPolicyDialog();
        }
    }

    private String getAIUIVerInCfg() {
        String params = FucUtil.readAssetFile(this, "cfg/aiui_phone.cfg", "utf-8");

        try {
            JSONObject paramsJson = new JSONObject(params);

            return paramsJson.optJSONObject("global").optString("aiui_ver", "");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean isVoiceCloneSupported() {
        String aiuiVer = getAIUIVerInCfg();
        if (aiuiVer == null) {
            return false;
        }

        // 版本不小于6.6.0001.0040且aiui_ver不为1
        return !isV1LessThanV2(Version.getVersion(), "6.6.0001.0040") && !"1".equals(aiuiVer);
    }

    private boolean isV1LessThanV2(String v1, String v2) {
        int i = 0, j = 0;
        while (i < v1.length() && j < v2.length()) {
            if (v1.charAt(i) < v2.charAt(j)) {
                return true;
            }

            i++;
            j++;
        }

        return v1.length() < v2.length();
    }

    private void showPrivacyPolicyDialog() {
        SpannableString contentSpannable = new SpannableString("我们非常重视对您个人信息的保护，承诺严格按照"
                + "《AIUI SDK隐私政策》保护及处理你的信息，是否确定同意？");
        contentSpannable.setSpan(new NoLineClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                super.onClick(widget);

                Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
                intent.putExtra(WebViewActivity.KEY_URL, "https://aiui-doc.xf-yun" +
                        ".com/project-1/doc-191/");
                intent.putExtra(WebViewActivity.KEY_TITLE, "AIUI SDK隐私政策");
                startActivity(intent);
            }
        }, 22, 36, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        final PrivacyPolicyDialog privacyPolicyDialog = new PrivacyPolicyDialog(this);
        privacyPolicyDialog.setContentSpannableText(contentSpannable);
        privacyPolicyDialog.getContentTextView().setMovementMethod(LinkMovementMethod.getInstance());
        privacyPolicyDialog.setListeners(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                privacyPolicyDialog.cancel();

                SharedPrefUtil.saveIsPrivacyPolicyAgreed(MainActivity.this, true);
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.exit(0);
            }
        });
        privacyPolicyDialog.show();
    }

    static class NoLineClickableSpan extends ClickableSpan {
        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(true);
            ds.setColor(Color.parseColor("#FF1E83FF"));
        }

        @Override
        public void onClick(@NonNull View widget) {

        }
    }
}
