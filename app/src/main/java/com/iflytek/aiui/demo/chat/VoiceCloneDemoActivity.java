package com.iflytek.aiui.demo.chat;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.iflytek.aiui.AIUIAgent;
import com.iflytek.aiui.AIUIConstant;
import com.iflytek.aiui.AIUIEvent;
import com.iflytek.aiui.AIUIListener;
import com.iflytek.aiui.AIUIMessage;
import com.iflytek.aiui.AIUISetting;
import com.iflytek.aiui.Version;
import com.iflytek.aiui.demo.chat.utils.DeviceUtil;
import com.iflytek.aiui.demo.chat.utils.FucUtil;
import com.iflytek.aiui.demo.chat.utils.PermissionUtil;
import com.iflytek.aiui.demo.chat.utils.SharedPrefUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 声音复刻页面。
 */
public class VoiceCloneDemoActivity extends Activity {
    private static final String TAG = "VoiceCloneDemo";
    private static final int REQUEST_CODE = 12345;

    private Toast mToast;

    private EditText mResultEdt;
    private Spinner mResIdSpn;
    private Button mCreateBtn;
    private Button mDestroyBtn;
    private Button mRecordBtn;
    private Button mVoiceRegBtn;
    private Button mVoiceQueryBtn;
    private Button mVoiceDelBtn;
    private Button mStartTTSBtn;
    private Button mStopTTSBtn;
    private Button mInstructionBtn;

    private boolean mIsRecording;

    private List<String> mResIdList;
    private String mCurResId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_clone);

        initUI();
    }

    private void initUI() {
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        mResultEdt = findViewById(R.id.edt_result);
        mResIdSpn = findViewById(R.id.spn_res_id);
        mCreateBtn = findViewById(R.id.btn_create);
        mDestroyBtn = findViewById(R.id.btn_destroy);
        mRecordBtn = findViewById(R.id.btn_record);
        mVoiceRegBtn = findViewById(R.id.btn_voice_reg);
        mVoiceQueryBtn = findViewById(R.id.btn_voice_query);
        mVoiceDelBtn = findViewById(R.id.btn_voice_del);
        mStartTTSBtn = findViewById(R.id.btn_start_tts);
        mStopTTSBtn = findViewById(R.id.btn_stop_tts);
        mInstructionBtn = findViewById(R.id.btn_instruction);

        refreshResIdSpinner();

        mCreateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createAgent();
            }
        });

        mDestroyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                destroyAgent();
            }
        });

        mRecordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    PermissionUtil.requestPermissions(VoiceCloneDemoActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_CODE);
                    return;
                }

                if (mIsRecording) {
                    if (mStopRecordTask != null) {
                        mStopRecordTask.cancel();
                        mStopRecordTask = null;
                    }

                    stopRecord();
                } else {
                    startRecord();
                }
            }
        });

        mVoiceRegBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                regVoice();
            }
        });

        // 查询已注册的声音id
        mVoiceQueryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queryVoice();
            }
        });

        mVoiceDelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                delVoice();
            }
        });

        mStartTTSBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startTTS();
            }
        });

        mStopTTSBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTTS();
            }
        });

        mInstructionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInstruction(true);
            }
        });

        displayToEdit("sdk_ver: " + Version.getVersion() + "\n");
        showInstruction(false);
    }

    private void refreshResIdSpinner() {
        mResIdList = SharedPrefUtil.getVoiceResIdList(this);

        ArrayAdapter<String> resIdAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, mResIdList);
        resIdAdapter.setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item);
        mResIdSpn.setAdapter(resIdAdapter);

        mResIdSpn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mCurResId = mResIdList.get(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        if (!mResIdList.isEmpty()) {
            mResIdSpn.setSelection(0);
            mCurResId = mResIdList.get(0);
        } else {
            mCurResId = "";
        }

        resIdAdapter.notifyDataSetChanged();
    }

    private void showInstruction(boolean clearOld) {
        String instruction = getString(R.string.voice_clone_instruction);
        displayToEdit(instruction, clearOld);
    }

    private void clearDisplay() {
        mResultEdt.setText("");
    }

    private void displayToEdit(String content) {
        displayToEdit(content, false);
    }

    private void displayToEdit(String content, boolean clearOld) {
        if (clearOld || mResultEdt.getLineCount() > 100) {
            mResultEdt.setText("");
        }

        mResultEdt.append(content);
        mResultEdt.append("\n");
        mResultEdt.setSelection(mResultEdt.getText().length());
    }

    private void showTip(final String str) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }

    private static final String AUDIO_FILE_NAME = "voice_clone.pcm";

    private FileOutputStream mFos;

    private AIUIAgent mAIUIAgent = null;

    private String getAudioFilePath() {
        return getFilesDir().getAbsolutePath() + "/" + AUDIO_FILE_NAME;
    }

    private final AIUIListener mAIUIListener = new AIUIListener() {
        @Override
        public void onEvent(AIUIEvent aiuiEvent) {
            switch (aiuiEvent.eventType) {
                case AIUIConstant.EVENT_START_RECORD: {
                    displayToEdit("录音已开始，你可以朗读以下文字片段，也可以自由说一段话：");
                    displayToEdit(getString(R.string.voice_clone_script));

                    if (mFos == null) {
                        try {
                            mFos = new FileOutputStream(getAudioFilePath());
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                } break;

                case AIUIConstant.EVENT_AUDIO:{
                    if (mFos != null) {
                        byte[] audio = aiuiEvent.data.getByteArray("audio");
                        if (audio != null) {
                            try {
                                mFos.write(audio);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } break;

                case AIUIConstant.EVENT_STOP_RECORD: {
                    displayToEdit("\n录音已停止，音频路径：" + getAudioFilePath());

                    if (mFos != null) {
                        try {
                            mFos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        mFos = null;
                    }
                } break;

                case AIUIConstant.EVENT_CMD_RETURN: {
                    if (aiuiEvent.arg1 == AIUIConstant.CMD_VOICE_CLONE) {
                        /**
                         * retCode取值如下：
                         *
                         * | 错误码 | 说明                                   |
                         * | :----- | :-------------------------------|
                         * | 900400 | 参数非法                               |
                         * | 900401 | 应用鉴权异常                           |
                         * | 900402 | 授权已经用完，复刻资源数量已经达到上限 |
                         * | 900403 | 没有授权，请先开通                     |
                         * | 900500 | 内部服务错误                           |
                         * | 900501 | 数据库异常                             |
                         * | 900601 | 声音复刻失败                           |
                         */
                        int retCode = aiuiEvent.arg2;
                        int dtype = aiuiEvent.data.getInt(AIUIConstant.KEY_SYNC_DTYPE, -1);
                        if (dtype == AIUIConstant.VOICE_CLONE_REG) {
                            // 声音注册结果
                            if (retCode == AIUIConstant.SUCCESS) {
                                String resId = aiuiEvent.data.getString(AIUIConstant.KEY_RES_ID, "");
                                SharedPrefUtil.addVoiceResId(VoiceCloneDemoActivity.this, resId);

                                displayToEdit("\n注册成功，res_id=" + resId);

                                refreshResIdSpinner();
                            } else {
                                showTip("注册失败，error=" + retCode + "，" + getErrorDes(retCode));
                            }
                        } else if (dtype == AIUIConstant.VOICE_CLONE_DEL) {
                            // 声音删除结果
                            if (retCode == AIUIConstant.SUCCESS) {
                                SharedPrefUtil.removeVoiceResId(VoiceCloneDemoActivity.this, mCurResId);

                                displayToEdit("\n删除成功");

                                refreshResIdSpinner();
                            } else {
                                showTip("删除失败，error=" + retCode + "，" + getErrorDes(retCode));
                            }
                        } else if (dtype == AIUIConstant.VOICE_CLONE_RES_QUERY) {
                            // 已注册声音查询结果
                            if (retCode == AIUIConstant.SUCCESS) {
                                String result = aiuiEvent.data.getString("result", "");

                                try {
                                    JSONObject resultJson = new JSONObject(result);

                                    if (!resultJson.isNull("data")) {
                                        JSONArray dataArray = resultJson.getJSONArray("data");
                                        if (dataArray != null) {
                                            displayToEdit("\n查询结果：\n" + dataArray);

                                            JSONArray resIdArray = new JSONArray();
                                            for (int i = 0; i < dataArray.length(); i++) {
                                                JSONObject resJson = dataArray.getJSONObject(i);
                                                resIdArray.put(resJson.optString("res_id", ""));
                                            }

                                            SharedPrefUtil.setVoiceRes(VoiceCloneDemoActivity.this, resIdArray);
                                            refreshResIdSpinner();
                                        } else {
                                            showTip("资源id为空");
                                        }
                                    } else {
                                        showTip("没有注册资源");
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                showTip("查询失败，error=" + retCode + "，" + getErrorDes(retCode));
                            }
                        }
                    }
                }
            }
        }
    };

    private String getAIUIParams() {
        String params = FucUtil.readAssetFile(this, "cfg/aiui_phone.cfg", "utf-8");

        try {
            JSONObject paramsJson = new JSONObject(params);

            boolean isWakeupEnable = !"off".equals(paramsJson.optJSONObject("speech").optString(
                    "wakeup_mode"));
            if (isWakeupEnable) {
                FucUtil.copyAssetFolder(this, "ivw", "/sdcard/AIUI/ivw");
            }

            params = paramsJson.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return params;
    }

    private void createAgent() {
        if (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE);
            return;
        }

        if (null == mAIUIAgent) {
            Log.i(TAG, "createAgent");

            // 为每一个设备设置对应唯一的SN（最好使用设备硬件信息(mac地址，设备序列号等）生成），以便正确统计装机量，避免刷机或者应用卸载重装导致装机量重复计数
            String deviceId = DeviceUtil.getDeviceId(this);
            Log.i(TAG, "deviceId=" + deviceId);

            AIUISetting.setNetLogLevel(AIUISetting.LogLevel.debug);
            AIUISetting.setSystemInfo(AIUIConstant.KEY_SERIAL_NUM, deviceId);

            // 6.6.xxxx.xxxx版本SDK设置用户唯一标识uid（可选，AIUI后台服务需要，不设置则会使用上面的deviceId作为uid）
            // 5.6.xxxx.xxxx版本SDK不能也不需要设置uid
            // AIUISetting.setSystemInfo(AIUIConstant.KEY_UID, deviceId);
            mAIUIAgent = AIUIAgent.createAgent(this, getAIUIParams(), mAIUIListener);
        }

        if (null == mAIUIAgent) {
            final String strErrorTip = "创建AIUIAgent失败！";
            showTip(strErrorTip);

            displayToEdit(strErrorTip, true);
        } else {
            initRecordText();

            showTip("AIUIAgent已创建");
        }
    }

    private void destroyAgent() {
        if (null != mAIUIAgent) {
            Log.i(TAG, "destroyAgent");

            mAIUIAgent.destroy();
            mAIUIAgent = null;

            initRecordText();
            mIsRecording = false;

            if (mStopRecordTask != null) {
                mStopRecordTask.cancel();
            }

            showTip("AIUIAgent已销毁");
        }
    }

    private static final int MIN_RECORD_TIME_SEC = 20;

    private static final int MAX_RECORD_TIME_SEC = 40;

    private Timer mRecordTimer;

    private CountTask mStopRecordTask;

    private class CountTask extends TimerTask {
        private int mRecordTimeSec = 0;

        @Override
        public void run() {
            mRecordTimeSec++;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRecordBtn.setText("已录制" + mRecordTimeSec + "秒，点击将停止录音");
                }
            });

            if (mRecordTimeSec == MAX_RECORD_TIME_SEC) {
                mIsRecording = false;
                stopRecord();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initRecordText();
                    }
                });

                cancel();
            }
        }
    }

    private void startRecord() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        AIUIMessage startRecord = new AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0,
                "data_type=audio,sample_rate=24000,need_write=false", null);
        mAIUIAgent.sendMessage(startRecord);

        if (mRecordTimer == null) {
            mRecordTimer = new Timer();
        }

        mStopRecordTask = new CountTask();
        mRecordTimer.schedule(mStopRecordTask, 0, 1000);

        clearDisplay();
        mIsRecording = true;
    }

    private void stopRecord() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        AIUIMessage stopRecord = new AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0,
                "data_type=audio", null);
        mAIUIAgent.sendMessage(stopRecord);

        initRecordText();
        mIsRecording = false;
    }

    /**
     * 注册声音。
     *
     * 注意：同一个终端可注册的声音数量有限，超限会报错。
     */
    private void regVoice() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        String path = getAudioFilePath();
        long timeLenSec = getAudioFileTimeLenSec(path);

        if (timeLenSec == -1) {
            showTip("音频文件不存在，请先录制");
            return;
        }

        if (timeLenSec < MIN_RECORD_TIME_SEC) {
            showTip("音频长度不足" + MIN_RECORD_TIME_SEC + "秒");
            return;
        }

        try {
            JSONObject paramsJson = new JSONObject();
            paramsJson.put(AIUIConstant.KEY_RES_PATH, path);

            AIUIMessage regVoice = new AIUIMessage(AIUIConstant.CMD_VOICE_CLONE,
                    AIUIConstant.VOICE_CLONE_REG, 0, paramsJson.toString(), null);
            mAIUIAgent.sendMessage(regVoice);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 查询已注册声音。
     */
    private void queryVoice() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        AIUIMessage queryVoice = new AIUIMessage(AIUIConstant.CMD_VOICE_CLONE,
                AIUIConstant.VOICE_CLONE_RES_QUERY, 0, "", null);
        mAIUIAgent.sendMessage(queryVoice);
    }

    /**
     * 删除声音。
     *
     * 注意：当注册超限之后，需要删除已注册的声音后才能继续注册。
     */
    private void delVoice() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        if (TextUtils.isEmpty(mCurResId)) {
            showTip("本地不存在声音res_id，请先注册");
            return;
        }

        try {
            JSONObject paramsJson = new JSONObject();
            paramsJson.put(AIUIConstant.KEY_RES_ID, mCurResId);

            AIUIMessage delVoice = new AIUIMessage(AIUIConstant.CMD_VOICE_CLONE,
                    AIUIConstant.VOICE_CLONE_DEL, 0, paramsJson.toString(), null);
            mAIUIAgent.sendMessage(delVoice);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void startTTS() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        if (TextUtils.isEmpty(mCurResId)) {
            showTip("本地不存在声音res_id，请先注册");
            return;
        }

        String text = "科大讯飞是亚太地区知名的智能语音和人工智能上市企业，致力于让机器能听会说，能理解会思考，用人工智能建设美好世界。";

        displayToEdit(text, true);

        try {
            // 使用声音复刻，合成时发音人要设置成x5_clone，并携带已注册的res_id
            String params = "vcn=x5_clone,res_id=" + mCurResId;
            byte[] textData = text.getBytes("utf-8");

            AIUIMessage startTTS = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.START, 0,
                    params,
                    textData);
            mAIUIAgent.sendMessage(startTTS);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void stopTTS() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        AIUIMessage cancelTTS = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.CANCEL, 0,
                "",
                null);
        mAIUIAgent.sendMessage(cancelTTS);
    }

    private void initRecordText() {
        mRecordBtn.setText("开始录音");
    }

    private long getAudioFileTimeLenSec(String path) {
        long lengthBytes = FucUtil.getFileLengthBytes(path);
        if (lengthBytes == -1) {
            return lengthBytes;
        }

        return lengthBytes / (24000 * 2);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        destroyAgent();

        if (mRecordTimer != null) {
            mRecordTimer.cancel();
        }
    }

    private final Map<Integer, String> mErrorDesMap = new HashMap<Integer, String>() {{
        put(900400, "参数非法");
        put(900401, "应用鉴权异常");
        put(900402, "授权已经用完，复刻资源数量已经达到上限");
        put(900403, "没有授权，请先开通");
        put(900500, "内部服务错误");
        put(900501, "数据库异常");
        put(900601, "声音复刻失败");
    }};

    private String getErrorDes(int error) {
        return mErrorDesMap.get(error);
    }
}
