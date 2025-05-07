package com.iflytek.aiui.demo.chat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
import com.iflytek.aiui.demo.chat.utils.FileLogger;
import com.iflytek.aiui.demo.chat.utils.tts.StreamNlpTtsHelper;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * 语义理解demo。
 */
public class NlpDemoActivity extends Activity implements OnClickListener {
    private static final String TAG = "NlpDemo";
    private static final int REQUEST_CODE = 12345;

    private Toast mToast;
    private TextView mTimeSpentText;
    private EditText mNlpText;//语义理解结果，或者，大模型语义结果
    private TextView mAsrText; //语音识别结果
    private TextView mCbmTidyText;// 结构化语义结果，判断用户的具体意图，列表
    private TextView mCbmSemanticText; // 结构化语义结果
    private TextView mCbmToolPkText; // 智能体结果
    private TextView mCbmRetrievalClassifyText; // 文档检索结果
    private AIUIAgent mAIUIAgent = null;
    private boolean mIsWakeupEnable = false;
    private int mAIUIState = AIUIConstant.STATE_IDLE;

    private String mSyncSid = "";

    LinkedList<JSONObject> cbmSemanticList = new LinkedList<>(); // cbm_semantic,结构化语义用，有多条的时候
    LinkedList<JSONObject> nlpList = new LinkedList<>();// nlp，有多条时候，需要有一个列表新增

    @SuppressLint("ShowToast")
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_nlp_demo);
        initUI();
    }

    private void initUI() {
        // 防止多次创建Toast对象，后面通过setText显示文字，show显示Toast
        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        findViewById(R.id.nlp_create).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.nlp_destroy).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.nlp_start).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.nlp_stop_rec).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.text_nlp_start).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.sync_contacts).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.sync_query).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.tts_start).setOnClickListener(NlpDemoActivity.this);
        findViewById(R.id.tts_stop).setOnClickListener(NlpDemoActivity.this);

        mTimeSpentText = findViewById(R.id.txt_time_spent);
        mNlpText = findViewById(R.id.nlp_text);
        mNlpText.append("sdk_ver: " + Version.getVersion());

        mStreamNlpTtsHelper = new StreamNlpTtsHelper(mStreamNlpTtsListener);
        mStreamNlpTtsHelper.setTextMinLimit(20);

        mAsrText = findViewById(R.id.asr_result);
        mCbmTidyText = findViewById(R.id.cbm_tidy_result);
        mCbmSemanticText = findViewById(R.id.cbm_semantic_result);
        mCbmToolPkText = findViewById(R.id.cbm_tool_pk_result);
        mCbmRetrievalClassifyText = findViewById(R.id.cbm_retrieval_classify_result);
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
            JSONObject paramsJson = new JSONObject(params);//将字符串解析为JSON对象

            mIsWakeupEnable = !"off".equals(paramsJson.optJSONObject("speech").optString("wakeup_mode"));
            if (mIsWakeupEnable) {
                FucUtil.copyAssetFolder(this, "ivw", "/sdcard/AIUI/ivw");
            }

            String aiuiVer = paramsJson.optJSONObject("global").optString("aiui_ver", "");
            mIsAIUI_V2 = (TextUtils.isEmpty(aiuiVer) || "2".equals(aiuiVer));

            params = paramsJson.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return params;
    }

    private void createAgent() {
        // checkCallingOrSelfPermission是Activity类的一个方法，用于检查当前应用是否拥有指定的权限，它接收一个权限字符串作为参数，并返回一个整数值
        // Mainfest.permission 是Android系统中用于管理权限的类，WRITE_EXTERNAL_STORAGE是该类的一个常量，表示写入外部存储的权限
        // PackageManager是Android系统中用于管理应用包信息的类，PERMISSION_GRANTED是该类的一个常量，表示权限已被授予。
        if (checkCallingOrSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE);
            return;
        }

        if (null == mAIUIAgent) {
            FileLogger.i(TAG, "createAgent");

            // 为每一个设备设置对应唯一的SN（最好使用设备硬件信息(mac地址，设备序列号等）生成），以便正确统计装机量，避免刷机或者应用卸载重装导致装机量重复计数
            String deviceId = DeviceUtil.getDeviceId(this);
            FileLogger.i(TAG, "deviceId=" + deviceId);

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
            FileLogger.i(TAG, "destroyAgent");

            mAIUIAgent.destroy();
            mAIUIAgent = null;

            showTip("AIUIAgent已销毁");
        }
    }

    private void startVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent为空，请先创建");
            return;
        }

        if (checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE);
            return;
        }

        FileLogger.i(TAG, "startVoiceNlp");

        mNlpText.setText("");
        mAsrText.setText("");
        mCbmSemanticText.setText("");
        mCbmRetrievalClassifyText.setText("");
        mCbmToolPkText.setText("");

        // 先发送唤醒消息，改变AIUI内部状态，只有唤醒状态才能接收语音输入
        // 默认为oneshot模式，即一次唤醒后就进入休眠。可以修改aiui_phone.cfg中speech参数的 interact_mode 为continuous以支持持续交互
        if (!mIsWakeupEnable) {
            AIUIMessage wakeupMsg = new AIUIMessage(AIUIConstant.CMD_WAKEUP, 0, 0, "", null);
            mAIUIAgent.sendMessage(wakeupMsg);
        }

        // 打开AIUI内部录音机，开始录音。若要使用上传的个性化资源增强识别效果，则在参数中添加pers_param设置
        // 个性化资源使用方法可参见http://doc.xfyun.cn/aiui_mobile/的用户个性化章节
        // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
        String params = "sample_rate=16000,data_type=audio,pers_param={\"uid\":\"\"},tag=audio-tag";
        AIUIMessage startRecord = new AIUIMessage(AIUIConstant.CMD_START_RECORD, 0, 0, params, null);

        mAIUIAgent.sendMessage(startRecord);
    }

    private void stopVoiceNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        FileLogger.i(TAG, "stopVoiceNlp");

        // 停止录音
        String params = "sample_rate=16000,data_type=audio";
        AIUIMessage stopRecord = new AIUIMessage(AIUIConstant.CMD_STOP_RECORD, 0, 0, params, null);

        mAIUIAgent.sendMessage(stopRecord);
    }

    // 语义合成
    private void startTextNlp() {
        if (null == mAIUIAgent) {
            showTip("AIUIAgent 为空，请先创建");
            return;
        }

        FileLogger.i(TAG, "startTextNlp");

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

        String text = "科大讯飞是亚太地区知名的智能语音和人工智能上市企业，致力于让机器能听会说，能理解会思考，用人工智能建设美好世界。";

        try {
            // 在输入参数中设置tag，则对应结果中也将携带该tag，可用于关联输入输出
            String params = "vcn=x2_xiaojuan,volume=100,tag=tts-tag";
            byte[] textData = text.getBytes("utf-8");

            AIUIMessage ttsMessage = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.START, 0, params, textData);
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

        AIUIMessage ttsMessage = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.CANCEL, 0, "", null);
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
            AIUIMessage syncAthena = new AIUIMessage(AIUIConstant.CMD_SYNC, mIsAIUI_V2 ? AIUIConstant.SYNC_DATA_UPLOAD : AIUIConstant.SYNC_DATA_SCHEMA, 0, paramJson.toString(), syncData);

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
            AIUIMessage syncQuery = new AIUIMessage(AIUIConstant.CMD_QUERY_SYNC_STATUS, AIUIConstant.SYNC_DATA_SCHEMA, 0, queryJson.toString(), null);
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
            AIUIMessage syncAthena = new AIUIMessage(AIUIConstant.CMD_SYNC, AIUIConstant.SYNC_DATA_DOWNLOAD, 0, paramJson.toString(), syncData);

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
        @SuppressLint("SetTextI18n")
        @Override
        public void onEvent(AIUIEvent event) {
            FileLogger.i(TAG, "onEvent, eventType=" + event.eventType);
            switch (event.eventType) {
                case AIUIConstant.EVENT_CONNECTED_TO_SERVER:
                    showTip("已连接服务器");
                    break;

                case AIUIConstant.EVENT_SERVER_DISCONNECTED:
                    showTip("与服务器断连");
                    break;

                case AIUIConstant.EVENT_WAKEUP:
                    showTip("进入识别状态");
                    Log.i(TAG, event.info);
                    FileLogger.i(TAG, event.info);
                    // 重新唤醒后，清空这两个变量
                    cbmSemanticList.clear();
                    nlpList.clear();
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
                        String cnt_id = content.getString("cnt_id");

                        if (content.has("cnt_id") && !"tts".equals(sub)) {
                            String cntStr = new String(event.data.getByteArray(cnt_id), "utf-8");// 这个很关键
                            JSONObject cntJson = new JSONObject(cntStr);

                            // 打印识别结果到日志
                            FileLogger.i(TAG, "sub = " + sub + ";" + "content = " + content + ";" + "cnt_id = " + cnt_id + ";" + "sid = " + sid + ";" + "tag = " + tag);
                            FileLogger.i(TAG, "cntStr = " + cntStr);

                            // 获取从数据发送完到获取结果的耗时，单位：ms；也可以通过键名"bos_rslt"获取从开始发送数据到获取结果的耗时
                            long eosRsltTime = event.data.getLong("eos_rslt", -1);
                            mTimeSpentText.setText(sub + ":" + eosRsltTime + "ms");

                            if (TextUtils.isEmpty(cntStr)) {
                                return;
                            }


                            if ("iat".equals(sub)) {
                                // 获取语音识别内容,并显示出来, 处理 mAseResult的内容
                                JSONObject textJson;
                                textJson = cntJson.optJSONObject("text");

                                if (textJson != null && !textJson.optBoolean("ls")) {
                                    mAsrText.setText(processTextObject(cntJson));
                                }

                                FileLogger.i(TAG, "asr_result: " + processTextObject(cntJson));
                            }

                            if ("cbm_tidy".equals(sub)) {
                                // 语义规整：进行关键信息提取和意图拆分，判断用户一次说了多少个意图
                                JSONObject cbmTidyJson = new JSONObject(cntJson.optJSONObject("cbm_tidy").optString("text"));
                                String query = cbmTidyJson.getString("query");
                                JSONArray intentArray = cbmTidyJson.getJSONArray("intent");
                                String intents = "";

                                for (int i = 0; i < intentArray.length(); i++) {
                                    JSONObject item = intentArray.getJSONObject(i);
                                    intents += item.optString("value") + "\r\n";
                                }

                                mCbmTidyText.setText("query：" + query + "\r\n" + "意图：" + intents);

                                FileLogger.i(TAG, "cbm_tidy_result: " + "query：" + query + "；意图：" + intents);
                            }

                            if ("cbm_semantic".equals(sub)) {
                                // 结构化语义结果
                                JSONObject cbmSemanticJson = new JSONObject(cntJson.optJSONObject("cbm_semantic").optString("text"));
                                String result = cbmSemanticJson.optJSONObject("answer").optString("text");
                                JSONObject cbmMetaJson = new JSONObject(cntJson.optJSONObject("cbm_meta").optString("text")).optJSONObject("cbm_semantic");
                                Integer intentIndex = cbmMetaJson.optInt("intent"); // 第几个意图
                                JSONObject temp = new JSONObject();
                                temp.put("intentIndex", intentIndex);
                                temp.put("result", result);

                                cbmSemanticList.add(temp); // 增加元素

                                // 按照意图序号排序
                                Collections.sort(cbmSemanticList, (a, b) -> {
                                    int diff = a.optInt("intentIndex") - b.optInt("intentIndex");
                                    Log.i(TAG, "比较结果-cbm_semantic: " + diff); // 确保打印
                                    return diff;
                                });

                                mCbmSemanticText.setText(String.valueOf(cbmSemanticList));
                                mCbmSemanticText.append("\n");

                                FileLogger.i(TAG, "cbm_semantic_result: " + String.valueOf(cbmSemanticList));
                            }

                            if ("cbm_tool_pk".equals(sub)) {
                                // 智能体结果
                                JSONObject cbmToolPkJson = new JSONObject(cntJson.optJSONObject("cbm_tool_pk").optString("text"));

                                mCbmToolPkText.setText("cbm_tool_pk: " + String.valueOf(cbmToolPkJson));

                                FileLogger.i(TAG, "cbm_tool_pk_result: " + String.valueOf(cbmToolPkJson));
                            }

                            if ("cbm_retrieval_classify".equals(sub)) {
                                // 文档检索结果
                                JSONObject cbmRetrievalClassifyJson = new JSONObject(cntJson.optJSONObject("cbm_retrieval_classify").optString("text"));
                                mCbmRetrievalClassifyText.setText("cbm_retrieval_classify_result" + String.valueOf(cbmRetrievalClassifyJson));

                                FileLogger.i(TAG, "cbm_retrieval_classify: " + String.valueOf(cbmRetrievalClassifyJson));
                            }

                            if ("nlp".equals(sub)) {
                                // 大模型语义结果，分两种情况，有没有在AIUI后台开启：星火大模型。有可能nlp_origin字段为cbm_semantic，注意区分
                                JSONObject nlpJson = cntJson.optJSONObject("nlp");
                                String textJsonString = nlpJson.optString("text");
                                String result = ""; // 每次流式返回的nlp内容
                                String allResultStr = ""; // 处理拼接后的字符串
                                boolean isPureText = !textJsonString.contains("intent");
                                result = isPureText ? textJsonString : (new JSONObject(textJsonString)).optJSONObject("intent").optJSONObject("answer").optString("text");

                                JSONObject cbmMetaJson = new JSONObject(cntJson.optJSONObject("cbm_meta").optString("text")).optJSONObject("nlp");
                                Integer intentIndex = cbmMetaJson.optInt("intent"); // 第几个意图

                                JSONObject temp = new JSONObject();
                                temp.put("intentIndex", intentIndex);
                                temp.put("result", result);

                                nlpList.add(temp); // 增加元素

                                // 按照意图序号排序
                                Collections.sort(nlpList, (a, b) -> {
                                    int diff = a.optInt("intentIndex") - b.optInt("intentIndex");
                                    Log.i(TAG, "比较结果-nlp: " + diff); // 确保打印
                                    return diff;
                                });

                                // 将nlpList中的result字段拼接起来
                                allResultStr = nlpList.stream()
                                        .filter(obj -> obj.has("result"))
                                        .map(obj -> obj.optString("result"))
                                        .collect(Collectors.joining());

                                mNlpText.setText(allResultStr);
                                // mNlpText.setText( String.valueOf(nlpList));
                                mNlpText.append("\n");

                                FileLogger.i(TAG, "nlpResult: " + result);
                            }


                        }
                        if ("tts".equals(sub)) {
                            if (!mCurTtsSid.equals(sid)) {
                                mCurTtsSid = sid;
                                mIsValidTTSAudioArrived = false;
                            }

                            int dts = content.getInt("dts");
                            byte[] audio = event.data.getByteArray(cnt_id);

                            assert audio != null;
                            if (audio.length > 0) {
                                if (!mIsValidTTSAudioArrived) {
                                    mIsValidTTSAudioArrived = true;
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
                        int dtype = event.data.getInt(AIUIConstant.KEY_SYNC_DTYPE, -1);
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
                                if (AIUIConstant.SUCCESS == retCode) {
                                    String base64 = event.data.getString("text", "");
                                    String content = new String(Base64.decode(base64, Base64.DEFAULT));
                                    String text = "下载到的实体内容：\n" + content;

                                    mNlpText.setText(text);
                                }
                            }
                            break;
                        }
                    } else if (AIUIConstant.CMD_QUERY_SYNC_STATUS == event.arg1) {    // 数据同步状态查询的返回
                        // 获取同步类型
                        int syncType = event.data.getInt(AIUIConstant.KEY_SYNC_DTYPE, -1);
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

    // 在Android中，更新UI操作必须在UI线程上进行，否则会抛出异常
    private void showTip(final String str) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
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

            AIUIMessage startTTS = new AIUIMessage(AIUIConstant.CMD_TTS, AIUIConstant.START, 0, params, textData);
            mAIUIAgent.sendMessage(startTTS);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private StreamNlpTtsHelper mStreamNlpTtsHelper;

    private final StreamNlpTtsHelper.Listener mStreamNlpTtsListener = new StreamNlpTtsHelper.Listener() {
        @Override
        public void onText(StreamNlpTtsHelper.OutTextSeg textSeg) {
            FileLogger.d(TAG, "streamNlpTts, onText, textSeg=" + textSeg);

            startIncTTS(textSeg);
        }

        @Override
        public void onTtsData(JSONObject bizParamJson, byte[] audio) {
            FileLogger.d(TAG, "streamNlpTts, onTtsData, params=" + bizParamJson.toString());
        }

        @Override
        public void onFinish(String fullText) {
            FileLogger.d(TAG, "streamNlpTts, onFinish, fullText=" + fullText);
        }
    };


    private String processTextObject(JSONObject textObject) {
        try {
            StringBuilder resultBuilder = new StringBuilder();
            JSONArray wsArray = textObject.optJSONObject("text").getJSONArray("ws");

            for (int i = 0; i < wsArray.length(); i++) {
                JSONObject wsItem = wsArray.getJSONObject(i); // getJSONObject，也可使用optJSONObject来替代，如果类型不匹配，不会抛出异常，返回null
                JSONArray cwArray = wsItem.getJSONArray("cw");// getJSONArray，也可使用optJSONArray来替代，如果类型不匹配，不会抛出异常，返回null

                for (int j = 0; j < cwArray.length(); j++) {
                    JSONObject cwItem = cwArray.getJSONObject(j);
                    resultBuilder.append(cwItem.getString("w"));
                }
            }

            return resultBuilder.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return ""; // 返回空字符串表示处理失败
        }
    }
}