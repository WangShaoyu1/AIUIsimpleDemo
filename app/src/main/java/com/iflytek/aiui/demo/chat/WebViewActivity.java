package com.iflytek.aiui.demo.chat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * Created by hj at 2023/4/3 11:11
 *
 * 显示网页的Activity。
 */
public class WebViewActivity extends Activity {
    private static final String TAG = "WebViewActivity";

    public static final String KEY_TITLE = "title";
    public static final String KEY_URL = "url";

    private TextView mTitleText;
    private WebView mContentWebView;
    private ProgressBar mProgressBar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view);

        mTitleText = findViewById(R.id.txt_title);
        mContentWebView = findViewById(R.id.webv_content);
        mProgressBar = findViewById(R.id.pbar_request);

        String title = getIntent().getStringExtra(KEY_TITLE);
        String url = getIntent().getStringExtra(KEY_URL);

        if (!TextUtils.isEmpty(title)) {
            mTitleText.setText(title);
        }

        mContentWebView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        WebSettings settings = mContentWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        mContentWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                mProgressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);

                mProgressBar.setVisibility(View.GONE);
            }
        });

        mProgressBar.setVisibility(View.VISIBLE);
        mContentWebView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (mContentWebView.canGoBack()) {
            mContentWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
