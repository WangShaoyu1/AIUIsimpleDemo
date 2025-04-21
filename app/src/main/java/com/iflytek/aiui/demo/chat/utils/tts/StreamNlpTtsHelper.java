package com.iflytek.aiui.demo.chat.utils.tts;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * tts工具类。与AIUIAgent配合使用，用于大模型返回的流式nlp应答文本合成。
 * <p>
 * 注意：必须在主线程调用各方法。
 */
public class StreamNlpTtsHelper {
    public static final int STATUS_BEGIN = 0;

    public static final int STATUS_CONTINUE = 1;

    public static final int STATUS_END = 2;

    public static final int STATUS_ALLONE = 3;

    private static final String REGEX_SENTENCE_DIVIDER = "[,.;，。；]";

    private static class InTextSeg {
        public String mText;
        public int mIndex;
        public int mStatus;

        public InTextSeg(String text, int index, int status) {
            mText = text;
            mIndex = index;
            mStatus = status;
        }

        public int getTextLen() {
            if (TextUtils.isEmpty(mText)) {
                return 0;
            }

            return mText.length();
        }

        public boolean isBegin() {
            return mStatus == STATUS_BEGIN;
        }

        public boolean isEmpty() {
            return TextUtils.isEmpty(mText);
        }

        public boolean isEnd() {
            return mStatus == STATUS_END;
        }
    }

    public static class OutTextSeg {
        public String mTag;
        public int mIndex;
        public String mText;
        public int mStatus;
        public int mOffset;

        public OutTextSeg(int index, String text, int status, int offset) {
            mTag = "stream_nlp_tts-" + System.currentTimeMillis() + "-" + index;
            mIndex = index;
            mText = text;
            mStatus = status;
            mOffset = offset;
        }

        public int getTextLen() {
            if (TextUtils.isEmpty(mText)) {
                return 0;
            }

            return mText.length();
        }

        public String getTag() {
            return mTag;
        }

        public boolean isBegin() {
            return mStatus == STATUS_BEGIN;
        }

        public boolean isEmpty() {
            return TextUtils.isEmpty(mText);
        }

        public boolean isEnd() {
            return mStatus == STATUS_END;
        }

        @Override
        public String toString() {
            return "OutTextSeg{" +
                    "mTag='" + mTag + '\'' +
                    ", mIndex=" + mIndex +
                    ", mText='" + mText + '\'' +
                    ", mStatus=" + mStatus +
                    ", mOffset=" + mOffset +
                    '}';
        }
    }

    private final List<InTextSeg> mInTextSegList = new ArrayList<>();

    private final List<OutTextSeg> mOutTextSegList = new ArrayList<>();

    private int mTextMinLimit = 100;

    private int mOrderedEndSegIndex = -1;

    private int mTotalOrderedTextLen = 0;

    private int mFetchedTextLen = 0;

    private StringBuilder mOrderedTextSb = new StringBuilder();

    public StreamNlpTtsHelper(Listener listener) {
        mOutListener = listener;
    }

    public void setTextMinLimit(int limit) {
        mTextMinLimit = limit;
    }

    private boolean isAddCompleted() {
        if (mInTextSegList.isEmpty()) {
            return false;
        }

        int size = mInTextSegList.size();
        InTextSeg last = mInTextSegList.get(size - 1);
        if (last.isEnd() && size == last.mIndex + 1) {
            return true;
        }

        return false;
    }

    /**
     * 添加合成文本。
     *
     * @param text   stream_nlp返回的answer文本
     * @param index  stream_nlp返回的index
     * @param status stream_nlp返回的状态
     */
    public void addText(String text, int index, int status) {
        if (isAddCompleted()) {
            return;
        }

        InTextSeg seg = new InTextSeg(text, index, status);

        int begin = mInTextSegList.size() - 1;
        int pos = begin;
        while (pos >= 0) {
            InTextSeg cur = mInTextSegList.get(pos);
            if (index < cur.mIndex) {
                pos--;
            } else {
                break;
            }
        }

        if (pos == begin) {
            // list为空，或者插入位置为尾部
            mInTextSegList.add(seg);
        } else {
            mInTextSegList.add(pos + 1, seg);
        }

        for (int i = mOrderedEndSegIndex + 1; i < mInTextSegList.size(); i++) {
            InTextSeg cur = mInTextSegList.get(i);
            if (i == cur.mIndex) {
                mOrderedEndSegIndex = i;
                mTotalOrderedTextLen += cur.getTextLen();

                // 把有序文本段追加到builder
                mOrderedTextSb.append(cur.mText);
            } else {
                break;
            }
        }

        if (mFetchStatus == FetchStatus.INIT || mFetchStatus == FetchStatus.INTERRUPTED) {
            processOrderedText();
        }
    }

    private int mOutTextSegIndex = 0;

    private OutTextSeg fetchOrderedText() {
        boolean needFetch = true;
        int tryFetchLen = 0;

        if (isAddCompleted()) {
            // 已经接收完成，取limit和剩余长度的最小值
            tryFetchLen = Math.min(mTextMinLimit, mTotalOrderedTextLen - mFetchedTextLen);
        } else {
            // 没接收完成
            if (mTotalOrderedTextLen - mFetchedTextLen < mTextMinLimit) {
                // 剩余的长度不够，则这次不需要取
                needFetch = false;
            } else {
                // 剩余长度足够，尝试取limit长度
                tryFetchLen = mTextMinLimit;
            }
        }

        if (!needFetch) {
            return null;
        }

        // 取剩余部分（这里向前取一个长度），在里面查找第一个分隔符位置
        String leftPart = mOrderedTextSb.substring(mFetchedTextLen + tryFetchLen - 1);
        Pattern p = Pattern.compile(REGEX_SENTENCE_DIVIDER);
        Matcher m = p.matcher(leftPart);

        int dividerPos = 0;
        if (m.find()) {
            dividerPos = m.start();
        } else {
            if (!isAddCompleted()) {
                // 没找到且没收完成，不处理
                return null;
            }

            // 已接收完成，取到结尾即可
            dividerPos = leftPart.length() - 1;
        }

        // 得到真实的获取长度和文本
        tryFetchLen += dividerPos;
        String fetchedText = mOrderedTextSb.substring(mFetchedTextLen,
                mFetchedTextLen + tryFetchLen);

        int status;
        if (isAddCompleted() && dividerPos == leftPart.length() - 1) {
            // 这一次把文本全取完了
            status = STATUS_END;
        } else {
            if (mOutTextSegList.isEmpty()) {
                status = STATUS_BEGIN;
            } else {
                status = STATUS_CONTINUE;
            }
        }

        OutTextSeg outTextSeg = new OutTextSeg(mOutTextSegIndex++, fetchedText, status,
                mFetchedTextLen);
        mFetchedTextLen += fetchedText.length();
        mOutTextSegList.add(outTextSeg);

        return outTextSeg;
    }

    private OutTextSeg mCurOutTextSeg;

    private boolean mFoundFirstStatusBeg = false;

    private int mTtsFrameIndex = 1;

    /**
     * 在AIUI返回合成结果时调用，传入原始合成结果。
     *
     * @param tag          结果中的标签
     * @param bizParamJson 结果描述
     * @param audio        音频数据
     */
    public void onOriginTtsData(String tag, JSONObject bizParamJson, byte[] audio) {
        if (mCurOutTextSeg == null || !mCurOutTextSeg.mTag.equals(tag)) {
            mCurOutTextSeg = findTextSegByTag(tag);
        }

        if (mCurOutTextSeg == null) {
            return;
        }

        boolean isLastSeg = mCurOutTextSeg.isEnd();

        try {
            JSONObject data = bizParamJson.getJSONArray("data").getJSONObject(0);
            JSONObject content = data.getJSONArray("content").getJSONObject(0);

            int dts = content.getInt("dts");
            int originDts = dts;

            // 修正局部文本位置为全局位置
            int text_start = content.getInt("text_start") + mCurOutTextSeg.mOffset;
            int text_end = content.getInt("text_end") + mCurOutTextSeg.mOffset;

            // 修正局部dts为全局dts
            if (dts == STATUS_CONTINUE) {
                // continue状态不用变
            } else {
                if (dts == STATUS_BEGIN) {
                    if (!mFoundFirstStatusBeg) {
                        mFoundFirstStatusBeg = true;
                    } else {
                        dts = STATUS_CONTINUE;
                    }
                } else if (dts == STATUS_END) {
                    if (!isLastSeg) {
                        dts = STATUS_CONTINUE;
                    }
                } else if (dts == STATUS_ALLONE) {
                    if (!mFoundFirstStatusBeg) {
                        mFoundFirstStatusBeg = true;

                        if (!isLastSeg) {
                            dts = STATUS_CONTINUE;
                        }
                    } else {
                        if (isLastSeg) {
                            dts = STATUS_END;
                        } else {
                            dts = STATUS_CONTINUE;
                        }
                    }
                }
            }

            // 修改局部percent为全局
            int text_percent = content.getInt("text_percent");
            if (!isAddCompleted()) {
                // 由于文本没有添加完，总长度未定，这里的全局进度算不了，直接取0
                text_percent = 0;
            } else {
                if (text_percent == 100 && isLastSeg) {
                    // 最后一个文本的100进度不用变
                } else {
                    int localOffset = text_percent * mCurOutTextSeg.getTextLen() / 100;
                    int globalOffset = mCurOutTextSeg.mOffset + localOffset;
                    text_percent = (int) (globalOffset * 100 / (float) mTotalOrderedTextLen);
                }
            }

            content.put("dts", dts);
            content.put("text_start", text_start);
            content.put("text_end", text_end);
            content.put("text_percent", text_percent);
            content.put("frame_id", mTtsFrameIndex++);

            if (mOutListener != null) {
                mOutListener.onTtsData(bizParamJson, audio);
            }

            // 这里要用原始的dts来判断
            if (originDts == STATUS_END || originDts == STATUS_ALLONE) {
                if (isLastSeg) {
                    // 全部处理完成
                    if (mOutListener != null) {
                        mOutListener.onFinish(mOrderedTextSb.toString());
                    }

                    clear();
                } else {
                    // 处理下一个
                    processOrderedText();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取全量文本。
     *
     * @return 全量文本，当没有接收完全时返回空
     */
    public String getFullText() {
        if (isAddCompleted()) {
            return mOrderedTextSb.toString();
        }

        return "";
    }

    /**
     * 清除待合成文本和状态，在合成出错或者取消合成时调用。
     */
    public void clear() {
        mInTextSegList.clear();
        mOutTextSegList.clear();
        mOrderedEndSegIndex = -1;
        mOrderedTextSb = new StringBuilder();
        mTotalOrderedTextLen = 0;
        mFetchedTextLen = 0;
        mFetchStatus = FetchStatus.INIT;
        mOutTextSegIndex = 0;
        mTtsFrameIndex = 1;
    }

    private OutTextSeg findTextSegByTag(String tag) {
        for (OutTextSeg seg : mOutTextSegList) {
            if (seg.mTag.equals(tag)) {
                return seg;
            }
        }

        return null;
    }

    public interface Listener {
        void onText(OutTextSeg textSeg);

        void onTtsData(JSONObject bizParamJson, byte[] audio);

        void onFinish(String fullText);
    }

    private final Listener mOutListener;

    private void processOrderedText() {
        OutTextSeg outTextSeg = fetchOrderedText();
        if (outTextSeg != null) {
            switch (mFetchStatus) {
                case INTERRUPTED:
                case INIT: {
                    mFetchStatus = FetchStatus.STARTED;
                }
                break;
            }

            if (!outTextSeg.isEmpty()) {
                if (mOutListener != null) {
                    mOutListener.onText(outTextSeg);
                }
            } else {
                if (outTextSeg.isEnd()) {
                    // 最后一段合成文本为空，直接造一个假结果
                    mockLastOutSegTtsResult(outTextSeg);
                }
            }
        } else {
            switch (mFetchStatus) {
                case STARTED: {
                    mFetchStatus = FetchStatus.INTERRUPTED;
                }
            }
        }
    }

    private void mockLastOutSegTtsResult(OutTextSeg lastSeg) {
        try {
            JSONObject contentJson = new JSONObject();
            contentJson.put("cancel", "0");
            contentJson.put("cnt_id", "0");
            contentJson.put("dte", "speex-wb;7");
            contentJson.put("dtf", "audio/L16;rate=16000");
            contentJson.put("dts", STATUS_ALLONE);
            contentJson.put("error", "");
            contentJson.put("frame_id", 1);
            contentJson.put("text_end", lastSeg.mOffset + lastSeg.getTextLen());
            contentJson.put("text_percent", 100);
            contentJson.put("text_seg", "");
            contentJson.put("text_start", lastSeg.mOffset);
            contentJson.put("url", "0");

            JSONArray contentArray = new JSONArray();
            contentArray.put(contentJson);

            JSONObject paramsJson = new JSONObject();
            paramsJson.put("cmd", "tts");
            paramsJson.put("lrst", "1");
            paramsJson.put("rstid", 1);
            paramsJson.put("sub", "tts");

            JSONObject dataJson = new JSONObject();
            dataJson.put("content", contentArray);
            dataJson.put("params", paramsJson);

            JSONArray dataArray = new JSONArray();
            dataArray.put(dataJson);

            JSONObject bizParamJson = new JSONObject();
            bizParamJson.put("data", dataArray);

            onOriginTtsData(lastSeg.mTag, bizParamJson, new byte[0]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

//    private final TextHandler mHandler;

    private FetchStatus mFetchStatus = FetchStatus.INIT;

    private enum FetchStatus {
        INIT,
        STARTED,
        INTERRUPTED
    }
}
