package com.iflytek.aiui.demo.chat.ui;

import android.app.Dialog;
import android.content.Context;
import android.text.SpannableString;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.iflytek.aiui.demo.chat.R;

/**
 * Created by hj at 2023/4/3 09:59
 *
 * 隐私弹框。
 */
public class PrivacyPolicyDialog extends Dialog {
    private final TextView mTitleText;
    private final TextView mContentText;

    private final Button mNegativeButton;
    private final Button mPositiveButton;

    public PrivacyPolicyDialog(@NonNull Context context) {
        super(context);

        setContentView(R.layout.layout_privacy_dialog);
        setCancelable(false);

        mTitleText = findViewById(R.id.txt_title);
        mContentText = findViewById(R.id.txt_content);
        mNegativeButton = findViewById(R.id.btn_negative);
        mPositiveButton = findViewById(R.id.btn_positive);
    }

    public void setTitle(String title) {
        mTitleText.setText(title);
    }

    public void setContent(String content) {
        mContentText.setText(content);
    }

    public TextView getContentTextView() {
        return mContentText;
    }

    public void setContentSpannableText(SpannableString spannableText) {
        mContentText.setText(spannableText);
    }

    public void setPositiveButtonText(String text) {
        mPositiveButton.setText(text);
    }

    public void setNegativeButtonText(String text) {
        mNegativeButton.setText(text);
    }

    public void setListeners(View.OnClickListener positiveListener,
                            View.OnClickListener negativeListener) {
        mPositiveButton.setOnClickListener(positiveListener);
        mNegativeButton.setOnClickListener(negativeListener);
    }
}
