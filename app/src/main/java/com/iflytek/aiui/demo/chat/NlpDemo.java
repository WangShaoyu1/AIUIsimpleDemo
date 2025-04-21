package com.iflytek.aiui.demo.chat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.iflytek.aiui.AIUIAgent;
import com.iflytek.aiui.AIUIConstant;
import com.iflytek.aiui.AIUIEvent;
import com.iflytek.aiui.AIUIListener;
import com.iflytek.aiui.AIUIMessage;
import com.iflytek.aiui.AIUISetting;
import com.iflytek.aiui.Version;
import com.iflytek.aiui.demo.chat.ui.PrivacyPolicyDialog;
import com.iflytek.aiui.demo.chat.util.DeviceUtils;
import com.iflytek.aiui.demo.chat.util.FucUtil;
import com.iflytek.aiui.demo.chat.util.tts.StreamNlpTtsHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

/**
 * 语义理解demo。
 */
public class NlpDemo extends Activity implements OnClickListener {
    private static final String TAG = "NlpDemo";

    private Toast mToast;
    private TextView mTimeSpentText;
    private EditText mNlpText;

    private AIUIAgent mAIUIAgent = null;
    private boolean mIsWakeupEnable = false;
    private int mAIUIState = AIUIConstant.STATE_IDLE;

    private String mSyncSid = "";

    @SuppressLint("ShowToast")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.nlpdemo);

        initLayout();

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
    }

    private void initLayout() {
        findViewById(R.id.nlp_create).setOnClickListener(NlpDemo.this);
        findViewById(R.id.nlp_destroy).setOnClickListener(NlpDemo.this);
        findViewById(R.id.nlp_start).setOnClickListener(NlpDemo.this);
        findViewById(R.id.nlp_stop_rec).setOnClickListener(NlpDemo.this);
        findViewById(R.id.text_nlp_start).setOnClickListener(NlpDemo.this);
        findViewById(R.id.sync_contacts).setOnClickListener(NlpDemo.this);
        findViewById(R.id.sync_query).setOnClickListener(NlpDemo.this);
        findViewById(R.id.tts_start).setOnClickListener(NlpDemo.this);
        findViewById(R.id.tts_stop).setOnClickListener(NlpDemo.this);

        mTimeSpentText = findViewById(R.id.txt_time_spent);
        mNlpText = findViewById(R.id.nlp_text);
        mNlpText.append("sdk_ver: " + Version.getVersion());

        if (!isPrivacyPolicyAgreed()) {
            showPrivacyPolicyDialog();
        }

        mStreamNlpTtsHelper = new StreamNlpTtsHelper(mStreamNlpTtsListener);
        mStreamNlpTtsHelper.setTextMinLimit(20);
    }

    private void showPrivacyPolicyDialog() {
        SpannableString contentSpannable = new SpannableString("我们非常重视对您个人信息的保护，承诺严格按照"
                + "《AIUI SDK隐私政策》保护及处理你的信息，是否确定同意？");
        contentSpannable.setSpan(new NoLineClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                super.onClick(widget);

                Intent intent = new Intent(NlpDemo.this, WebViewActivity.class);
                intent.putExtra(WebViewActivity.KEY_URL, "https://aiui-doc.xf-yun" +
                        ".com/project-1/doc-191/");
                intent.putExtra(WebViewActivity.KEY_TITLE, "AIUI SDK隐私政策");
                startActivity(intent);
            }
        }, 22, 36, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        final PrivacyPolicyDialog privacyPolicyDialog = new PrivacyPolicyDialog(this);
        privacyPolicyDialog.setContentSpannableText(contentSpannable);
        privacyPolicyDialog.getContentTextView().setMovementMethod(LinkMovementMethod.getInstance());
        privacyPolicyDialog.setListeners(new OnClickListener() {
            @Override
            public void onClick(View v) {
                privacyPolicyDialog.cancel();

                saveIsPrivacyPolicyAgreed(true);
            }
        }, new OnClickListener() {
            @Override
            public void onClick(View v) {
                System.exit(0);
            }
        });
        privacyPolicyDialog.show();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            // 创建AIUIAgent
            case R.id.nlp_create:
                createAgent();
                break;

            // 销毁AIUIAgent
            case R.id.nlp_destroy:
                destroyAgent();
                break;

            // 开始文本语义
            case R.id.text_nlp_start:
                startTextNlp();
                break;

            // 开始语音语义
            case R.id.nlp_start:
                startVoiceNlp();
                break;

            // 停止语音语义
            case R.id.nlp_stop_rec:
                stopVoiceNlp();
                break;

            // 同步联系人
            case R.id.sync_contacts:
                syncContacts();
                break;

            // 联系人资源打包状态查询
            case R.id.sync_query:
                if (mIsAIUI_V2) {
                    // AIUI_V2服务要用download来查看上传的实体内容
                    syncDownload();
                } else {
                    syncQuery();
                }
                break;

            case R.id.tts_start:
                startTTS();
                break;

            case R.id.tts_stop:
                stopTTS();
                break;

            default:
                break;
        }
    }

    private boolean mIsAIUI_V2;

    private String getAIUIParams() {
        String params = FucUtil.readAssetFile(this, "cfg/aiui_phone.cfg", "utf-8");

        try {
            JSONObject paramsJson = new JSONObject(params);

            mIsWakeupEnable = !"off".equals(paramsJson.optJSONObject("speech").optString(
                    "wakeup_mode"));
            if (mIsWakeupEnable) {
                FucUtil.copyAssetFolder(this, "ivw", "/sdcard/AIUI/ivw");
            }

            String aiuiVer = paramsJson.optJSONObject("global").optString("aiui_ver", "");
            mIsAIUI_V2 = (TextUtils.isEmpty(aiuiVer) || "2".equals(aiuiVer));

            params = paramsJson.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            ;
        }

        return params;
    }

    private void createAgent() {
        if (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
            return;
        }

        if (null == mAIUIAgent) {
            Log.i(TAG, "createAgent");

            // 为每一个设备设置对应唯一的SN（最好使用设备硬件信息(mac地址，设备序列号等）生成），以便正确统计装机量，避免刷机或者应用卸载重装导致装机量重复计数
            String deviceId = DeviceUtils.getDeviceId(this);
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

            mNlpText.setText(strErrorTip);
        } else {
            showTip("AIUIAgent已创建");
        }
    }

    private void destroyAgent() {
        if (null != mAIUIAgent) {
            Log.i(TAG, "destroyAgent");

            mAIUIAgent.destroy();
            mAIUIAgent = null;

            showTip("AIUIAgent已销毁");
        } else {
            showTip("AIUIAgent为空");
        }
    }

    private void startVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        if (checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO});
            return;
        }

        Log.i(TAG, "startVoiceNlp");

        mNlpText.setText("");

        // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
        // 默认为oneshot模式，即一次唤醒后就进入休眠。可以修改aiui_phone.cfg中speech参数的interact_mode为continuous以支持持续交互
        if (!mIsWakeupEnable) {
            AIUIMessage wakeupMsg = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
            mAIUIAgent.sendMessage(wakeupMsg);
        }

        // 打开AIUI内部录音机，开始录音。若要使用上传的个性化资源增强识别效果，则在参数中添加pers_param设置
        // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
        // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
        String params = "sample_rate=16000,data_type=audio,pers_param={\"uid\":\"\"},tag=audio-tag";
        AIUIMessage startRecord = new AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, params,
				null);

        mAIUIAgent.sendMessage(startRecord);
    }

    private void stopVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        Log.i(TAG, "stopVoiceNlp");

        // 停止录音
        String params = "sample_rate=16000,data_type=audio";
        AIUIMessage stopRecord = new AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0, params, null);

        mAIUIAgent.sendMessage(stopRecord);
    }

    private void startTextNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        Log.i(TAG, "startTextNlp");

        AIUIMessage wakeupMsg = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
        mAIUIAgent.sendMessage(wakeupMsg);

        String text = "合肥明天的天气怎么样？";
        mNlpText.setText(text);

        try {
            // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
            String params = "data_type=text,tag=text-tag";
            byte[] textData = text.getBytes("utf-8");

            AIUIMessage write = new AIUIMessage(AIUIConstant.CMD_WRITE, 0, 0, params, textData);
            mAIUIAgent.sendMessage(write);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void startTTS() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        String text = "我叫小飞飞，是你的好朋友";

        try {
            // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
            String params = "vcn=x2_xiaojuan,volume=100,tag=tts-tag";
            byte[] textData = text.getBytes("utf-8");

            AIUIMessage ttsMessage = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.START, 0,
                    params,
                    textData);
            mAIUIAgent.sendMessage(ttsMessage);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void stopTTS() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        AIUIMessage ttsMessage = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.CANCEL, 0,
                "",
                null);
        mAIUIAgent.sendMessage(ttsMessage);
    }

    private void syncContacts() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        try {
            // 从文件中读取联系人示例数据
            String dataStr = FucUtil.readAssetFile(this, "data/data_contact.txt", "utf-8");
            mNlpText.setText(dataStr);

            // 数据进行no_wrap Base64编码
            String dataStrBase64 = Base64.encodeToString(dataStr.getBytes("utf-8"), Base64.NO_WRAP);

            JSONObject syncSchemaJson = new JSONObject();
            JSONObject dataParamJson = new JSONObject();

            // 设置id_name为uid，即用户级个性化资源
            // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
            dataParamJson.put("id_name", "uid");

            // 设置res_name为联系人
            dataParamJson.put("res_name", "IFLYTEK.telephone_contact");

            if (mIsAIUI_V2) {
                // 这里一定要设置成自己的命名空间。
                // AIUI开放平台的命名空间，在「技能工作室-我的实体-动态实体密钥」中查看（链接：https://aiui.xfyun.cn/studio/entity）
                dataParamJson.put("name_space", "XXX");
            }

            syncSchemaJson.put("param", dataParamJson);
            syncSchemaJson.put("data", dataStrBase64);

            // 传入的数据一定要为utf-8编码
            byte[] syncData = syncSchemaJson.toString().getBytes("utf-8");

            // 给该次同步加上自定义tag，在返回结果中可通过tag将结果和调用对应起来
            JSONObject paramJson = new JSONObject();
            paramJson.put("tag", "sync-tag");

            // 用schema数据同步上传联系人
            // 注：数据同步请在连接服务器之后进行，否则可能失败
            // AIUI_V2服务上传schema要用SYNC_DATA_UPLOAD
            AIUIMessage syncAthena = new AIUIMessage(AIUIConstant.CMD_SYNC,
                    mIsAIUI_V2 ? AIUIConstant.SYNC_DATA_UPLOAD : AIUIConstant.SYNC_DATA_SCHEMA, 0,
                    paramJson.toString(), syncData);

            mAIUIAgent.sendMessage(syncAthena);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private void syncQuery() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        if (TextUtils.isEmpty(mSyncSid)) {
            showTip("sid 为空");
            return;
        }

        try {
            // 构造查询json字符串，填入同步schema数据返回的sid
            JSONObject queryJson = new JSONObject();
            queryJson.put("sid", mSyncSid);

            // 发送同步数据状态查询消息，设置arg1为schema数据类型，params为查询字符串
            AIUIMessage syncQuery = new AIUIMessage(AIUIConstant.CMD_QUERY_SYNC_STATUS,
                    AIUIConstant.SYNC_DATA_SCHEMA, 0, queryJson.toString(), null);
            mAIUIAgent.sendMessage(syncQuery);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void syncDownload() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        try {
            JSONObject syncSchemaJson = new JSONObject();
            JSONObject dataParamJson = new JSONObject();

            // 设置id_name为uid，即用户级个性化资源
            // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
            dataParamJson.put("id_name", "uid");

            // 设置res_name为联系人
            dataParamJson.put("res_name", "IFLYTEK.telephone_contact");

            if (mIsAIUI_V2) {
                // aiui开放平台的命名空间，在「技能工作室-我的实体-动态实体密钥」中查看
                dataParamJson.put("name_space", "CITYHN");
            }

            syncSchemaJson.put("param", dataParamJson);

            // 传入的数据一定要为utf-8编码
            byte[] syncData = syncSchemaJson.toString().getBytes("utf-8");

            // 给该次同步加上自定义tag，在返回结果中可通过tag将结果和调用对应起来
            JSONObject paramJson = new JSONObject();
            paramJson.put("tag", "sync-tag");

            // 用schema数据同步上传联系人
            // 注：数据同步请在连接服务器之后进行，否则可能失败
            // AIUI_V2服务上传要用SYNC_DATA_UPLOAD
            AIUIMessage syncAthena = new AIUIMessage(AIUIConstant.CMD_SYNC,
                    AIUIConstant.SYNC_DATA_DOWNLOAD, 0,
                    paramJson.toString(), syncData);

            mAIUIAgent.sendMessage(syncAthena);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private boolean mIsValidTTSAudioArrived = false;

    private String mCurTtsSid = "";

    private final AIUIListener mAIUIListener = new AIUIListener() {
        @Override
        public void onEvent(AIUIEvent event) {
            Log.i(TAG, "onEvent, eventType=" + event.eventType);

            switch (event.eventType) {
                case AIUIConstant.EVENT_CONNECTED_TO_SERVER:
                    showTip("已连接服务器");
                    break;

                case AIUIConstant.EVENT_SERVER_DISCONNECTED:
                    showTip("与服务器断连");
                    break;

                case AIUIConstant.EVENT_WAKEUP:
                    showTip("进入识别状态");
                    break;

                case AIUIConstant.EVENT_RESULT: {
                    try {
                        JSONObject bizParamJson = new JSONObject(event.info);
                        JSONObject data = bizParamJson.getJSONArray("data").getJSONObject(0);
                        JSONObject params = data.getJSONObject("params");
                        JSONObject content = data.getJSONArray("content").getJSONObject(0);
                        String sub = params.optString("sub");

                        // 获取该路会话的id，将其提供给支持人员，有助于问题排查
                        // 也可以从Json结果中看到
                        String sid = event.data.getString("sid");
                        String tag = event.data.getString("tag");

                        if (content.has("cnt_id") && !"tts".equals(sub)) {
                            String cnt_id = content.getString("cnt_id");
                            String cntStr = new String(event.data.getByteArray(cnt_id), "utf-8");

                            // 获取从数据发送完到获取结果的耗时，单位：ms
                            // 也可以通过键名"bos_rslt"获取从开始发送数据到获取结果的耗时
                            long eosRsltTime = event.data.getLong("eos_rslt", -1);
                            mTimeSpentText.setText(sub + ":" + eosRsltTime + "ms");

                            if (TextUtils.isEmpty(cntStr)) {
                                return;
                            }

                            JSONObject cntJson = new JSONObject(cntStr);

                            if (mNlpText.getLineCount() > 1000) {
                                mNlpText.setText("");
                            }

                            mNlpText.append("\n");
                            mNlpText.append(cntJson.toString());
                            mNlpText.setSelection(mNlpText.getText().length());

                            if ("nlp".equals(sub)) {
                                // 解析得到语义结果
                                String resultStr = cntJson.optString("intent");
                                Log.i(TAG, resultStr);
                            }

                            mNlpText.append("\n");
                        }

                        if ("tts".equals(sub)) {
                            if (!mCurTtsSid.equals(sid)) {
                                mCurTtsSid = sid;
                                mIsValidTTSAudioArrived = false;
                            }

                            int dts = content.getInt("dts");
                            String cnt_id = content.getString("cnt_id");
                            byte[] audio = event.data.getByteArray(cnt_id);

                            assert audio != null;
                            if (audio.length > 0) {
                                if (!mIsValidTTSAudioArrived) {
                                    mIsValidTTSAudioArrived = true;

                                    long eosRsltTime = event.data.getLong("eos_rslt", -1);
                                    mTimeSpentText.setText(sub + ":" + eosRsltTime + "ms");
                                }
                            }

                            if (mStreamNlpTtsHelper != null) {
                                mStreamNlpTtsHelper.onOriginTtsData(tag, bizParamJson, audio);
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        mNlpText.append("\n");
                        mNlpText.append(e.getLocalizedMessage());
                    }

                }
                break;

                case AIUIConstant.EVENT_ERROR: {
                    mNlpText.append("\n");
                    mNlpText.append("错误: " + event.arg1 + "\n" + event.info);

                    if (!TextUtils.isEmpty(event.info) && event.info.contains("tts")) {
                        if (mStreamNlpTtsHelper != null) {
                            mStreamNlpTtsHelper.clear();
                        }
                    }
                }
                break;

                case AIUIConstant.EVENT_VAD: {
                    if (AIUIConstant.VAD_BOS == event.arg1) {
                        showTip("找到vad_bos");
                    } else if (AIUIConstant.VAD_EOS == event.arg1) {
                        showTip("找到vad_eos");
                    } else {
                        showTip("" + event.arg2);
                    }
                }
                break;

                case AIUIConstant.EVENT_START_RECORD: {
                    showTip("已开始录音");
                }
                break;

                case AIUIConstant.EVENT_STOP_RECORD: {
                    showTip("已停止录音");
                }
                break;

                case AIUIConstant.EVENT_STATE: {    // 状态事件
                    mAIUIState = event.arg1;

                    if (AIUIConstant.STATE_IDLE == mAIUIState) {
                        // 闲置状态，AIUI未开启
                        showTip("STATE_IDLE");
                    } else if (AIUIConstant.STATE_READY == mAIUIState) {
                        // AIUI已就绪，等待唤醒
                        showTip("STATE_READY");
                    } else if (AIUIConstant.STATE_WORKING == mAIUIState) {
                        // AIUI工作中，可进行交互
                        showTip("STATE_WORKING");
                    }
                }
                break;

                case AIUIConstant.EVENT_CMD_RETURN: {
                    if (AIUIConstant.CMD_SYNC == event.arg1) {    // 数据同步的返回
                        int dtype = event.data.getInt("sync_dtype", -1);
                        int retCode = event.arg2;

                        switch (dtype) {
                            case AIUIConstant.SYNC_DATA_UPLOAD:
                            case AIUIConstant.SYNC_DATA_SCHEMA: {
                                if (AIUIConstant.SUCCESS == retCode) {
                                    // 上传成功，记录上传会话的sid，以用于查询数据打包状态
                                    // 注：上传成功并不表示数据打包成功，打包成功与否应以同步状态查询结果为准，数据只有打包成功后才能正常使用
                                    mSyncSid = event.data.getString("sid");

                                    // 获取上传调用时设置的自定义tag
                                    String tag = event.data.getString("tag");

                                    // 获取上传调用耗时，单位：ms
                                    long timeSpent = event.data.getLong("time_spent", -1);
                                    if (-1 != timeSpent) {
                                        mTimeSpentText.setText(timeSpent + "ms");
                                    }

                                    showTip("上传成功，sid=" + mSyncSid + "，tag=" + tag + "，你可以试着说“打电话给刘德华”");
                                } else {
                                    mSyncSid = "";
                                    showTip("上传失败，错误码：" + retCode);
                                }
                            }
                            break;

                            case AIUIConstant.SYNC_DATA_DOWNLOAD: {
                                if (AIUIConstant.SUCCESS == retCode ) {
                                    String base64 = event.data.getString("text", "");
                                    String content = new String(Base64.decode(base64,
                                            Base64.DEFAULT));
                                    String text = "下载到的实体内容：\n" + content;

                                    mNlpText.setText(text);
                                }
                            } break;
                        }
                    } else if (AIUIConstant.CMD_QUERY_SYNC_STATUS == event.arg1) {    // 数据同步状态查询的返回
                        // 获取同步类型
                        int syncType = event.data.getInt("sync_dtype", -1);
                        if (AIUIConstant.SYNC_DATA_QUERY == syncType) {
                            // 若是同步数据查询，则获取查询结果，结果中error字段为0则表示上传数据打包成功，否则为错误码
                            String result = event.data.getString("result");

                            showTip(result);
                        }
                    }
                }
                break;

                default:
                    break;
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (null != mAIUIAgent) {
            mAIUIAgent.destroy();
            mAIUIAgent = null;
        }
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

    /**
     * 申请权限
     */
    private void requestPermissions(String[] perms) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(this, perms, 0x0010);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startIncTTS(StreamNlpTtsHelper.OutTextSeg seg) {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        try {
            // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
            String params = "data_type=text,tag=" + seg.getTag();
            if (!seg.isBegin()) {
                params = params + ",cancel_last=false";
            }

            byte[] textData = seg.mText.getBytes("utf-8");

            AIUIMessage startTTS = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.START, 0,
                    params, textData);
            mAIUIAgent.sendMessage(startTTS);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private StreamNlpTtsHelper mStreamNlpTtsHelper;

    private final StreamNlpTtsHelper.Listener mStreamNlpTtsListener = new StreamNlpTtsHelper.Listener() {
        @Override
        public void onText(StreamNlpTtsHelper.OutTextSeg textSeg) {
            Log.d(TAG, "streamNlpTts, onText, textSeg=" + textSeg);

            startIncTTS(textSeg);
        }

        @Override
        public void onTtsData(JSONObject bizParamJson, byte[] audio) {
            Log.d(TAG, "streamNlpTts, onTtsData, params=" + bizParamJson.toString());
        }

        @Override
        public void onFinish(String fullText) {
            Log.d(TAG, "streamNlpTts, onFinish, fullText=" + fullText);
        }
    };

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

    private static final String PREF_NAME = "settings";
    private static final String IS_PRIVACY_POLICY_AGREED = "is_privacy_policy_agreed";

    private boolean isPrivacyPolicyAgreed() {
        SharedPreferences pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return pref.getBoolean(IS_PRIVACY_POLICY_AGREED, false);
    }

    private void saveIsPrivacyPolicyAgreed(boolean isAgreed) {
        SharedPreferences pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pref.edit()
                .putBoolean(IS_PRIVACY_POLICY_AGREED, isAgreed)
                .apply();
    }
}
