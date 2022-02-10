/**
 * Created by SPRD on 2019/01/07.
 */
package com.sprd.messaging.smart;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.ui.conversation.Constants;
import com.android.messaging.ui.conversation.ScreenUtils;
import com.android.messaging.util.LogUtil;
import com.gstd.callme.UI.inter.ICardCallback;
import com.gstd.callme.UI.inter.IOrgCallback;
import com.gstd.callme.engine.SmartSmsEngineManager;
import com.gstd.callme.interf.ISmartMms;
import com.gstd.callme.outerentity.OrgInfo;
import com.gstd.callme.outerentity.RequestParam;
import com.sprd.messaging.smart.messagecard.MessageItemCardCtcc;

public class CtccSmartSdk implements ISmartSdk {

    private static CtccSmartSdk smartSdk = null;

    private CtccSmartSdk() {
    }

    public static CtccSmartSdk getInstance() {
        synchronized (CtccSmartSdk.class) {
            if (null == smartSdk) {
                smartSdk = new CtccSmartSdk();
            }
        }
        return smartSdk;
    }

    private final int GET_ORG_INFO = 1;

    private class RetryHandler extends Handler {
        private Context mContext;
        private String port;
        private IServiceInfoCallBack serviceInfoCallBack;
        private int count = 0;
        private final int MAX_COUNT = 3;

        RetryHandler(Context context, String sendDestination, IServiceInfoCallBack callBack) {
            super(context.getMainLooper());
            this.mContext = context;
            this.port = sendDestination;
            this.serviceInfoCallBack = callBack;
        }

        @Override
        public void handleMessage(Message msg) {
            //super.handleMessage(msg);
            switch (msg.what) {
                case GET_ORG_INFO:
                    LogUtil.d(LogUtil.BUGLE_SMART_TAG, "ctcc, handleMessage port/count:" + port + "/" + count);
                    if (count < MAX_COUNT) {
                        count++;
                        getOrgInfo(mContext, port, serviceInfoCallBack);
                    } else {
                        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "ctcc, handleMessage remove this:" + this);
                        arrayMap.remove(this);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private ArrayMap<String, RetryHandler> arrayMap = new ArrayMap<>();

    @Override
    synchronized public void getServiceInfo(Context context, String sendDestination, IServiceInfoCallBack callBack) {
        boolean result = isServiceAddress(context, sendDestination);
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "ctcc getServiceInfo, sendDestination/smart:" + sendDestination + "/" + result);
        if (result) {
            RetryHandler retryHandler = new RetryHandler(context, sendDestination, callBack);
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "first send, sendDestination/retryHandler:" + sendDestination + "/" + retryHandler);
            arrayMap.put(sendDestination, retryHandler);
            retryHandler.sendEmptyMessage(GET_ORG_INFO);
        }
    }

    private void getOrgInfo(Context context, final String sendDestination, IServiceInfoCallBack callBack) {
        asyncGetOrgInfo(context, sendDestination, new IOrgCallback() {
            @Override
            public void onSuccess(OrgInfo info) {
                LogUtil.d(LogUtil.BUGLE_SMART_TAG, "ctcc getOrgInfo, onSuccess,info:" + info);
                if (info != null && info.isValidOrgInfo()) {
                    RetryHandler retryHandler = arrayMap.get(sendDestination);
                    if (retryHandler != null) {
                        arrayMap.remove(sendDestination);
                    }
                    callBack.getResult(new ServiceInfoEntity(info.getName(), info.getLogo()));
                } else {
                    RetryHandler retryHandler = arrayMap.get(sendDestination);
                    LogUtil.d(LogUtil.BUGLE_SMART_TAG, "retry send, sendDestination/retryHandler:" + sendDestination + "/" + retryHandler);
                    if (retryHandler != null) {
                        retryHandler.sendEmptyMessage(GET_ORG_INFO);
                    }
                }
            }

            @Override
            public void onFail() {
                RetryHandler retryHandler = arrayMap.get(sendDestination);
                LogUtil.d(LogUtil.BUGLE_SMART_TAG, "ctcc onFail,retry sendDestination/retryHandler:" + sendDestination + "/" + retryHandler);
                if (retryHandler != null) {
                    retryHandler.sendEmptyMessage(GET_ORG_INFO);
                }
            }
        });
    }

    @Override
    public void initSdk(Context context) {
        SmartSmsEngineManager.getInstance().init(context);
        SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine().setDebugable(true);
        boolean initFinished = SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine().isInitFinished();
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "CTCC initSdk----initFinished:" + initFinished);

        //CTA
        SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine().setCTA(context, true);
        boolean ctaAuthorized = SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine().getCTA(context);
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "CTCC initSdk----ctaAuthorized:" + ctaAuthorized);

        //CARD WIDTH
        Constants.CARD_WIDTH = ScreenUtils.getScreenWidth(context) / 4 * 3;
    }

    public boolean isServiceAddress(Context mContext, String mPort) {
        ISmartMms smartMms = SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine();
        if (TextUtils.isEmpty(mPort) || null == smartMms) {
            return false;
        }
        return smartMms.isServiceAddress(mContext, mPort);
    }

    public void asyncGetOrgInfo(Context mContext, String mPort, IOrgCallback callback) {
        RequestParam requestParam = new RequestParam.ParamBuilder().setNumber(mPort).build();
        SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine().asyncGetOrgInfo(mContext, requestParam, callback);
    }

    public boolean shouldGetCard(Context context, ConversationMessageData mData) {
        return mData.getIsIncoming() && isServiceAddress(context, mData.getSenderDisplayDestination());
    }

    @Override
    public void updateCardInfo(Context context, ConversationMessageData data, ICardInfoCallBack cardInfoCallBack) {
        final String content = data.getText();
        String mPort = data.getSenderDisplayDestination();
        String frommMessageSmsUrigetId = getIdFromUri(data.getSmsMessageUri());
        long receivedTimsStamp = data.getReceivedTimeStamp();
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, " showCtccCard, mPort/mMessageSmsUri/content:" + mPort + "/" + data.getSmsMessageUri()
                + "/" + content);

        RequestParam requestParam = new RequestParam.ParamBuilder().setNumber(mPort).setBody(content).setId(Long.parseLong(frommMessageSmsUrigetId)).build();
        SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine().asyncGetCardInfo(context, requestParam,
                new CtccCardCallBack(context, mPort, content, frommMessageSmsUrigetId, receivedTimsStamp, cardInfoCallBack));
    }

    class CtccCardCallBack implements ICardCallback {
        private String mPort;
        private String mBody;
        private String mId;
        private ICardInfoCallBack cardInfoCallBackImpl;
        private long receivedTimsStamp;
        private Context mContext;

        public CtccCardCallBack(Context context, String port, String body, String id, long timeStamp, ICardInfoCallBack cardInfoCallBack) {
            this.mContext = context;
            this.mPort = port;
            this.mBody = body;
            this.mId = id;
            this.receivedTimsStamp = timeStamp;
            this.cardInfoCallBackImpl = cardInfoCallBack;
        }

        @Override
        public void onSuccess(RequestParam requestParam, com.gstd.callme.outerentity.CardInfo cardInfo) {
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, " CtccCardCallBack, onSuccess, mPort/mId/cardInfo:" + mPort + "/" + mId + "/" + cardInfo);
            MessageItemCardCtcc messageItemCard = null;
            if (cardInfo != null && !TextUtils.isEmpty(cardInfo.getId())) {
                LogUtil.d(LogUtil.BUGLE_SMART_TAG, " ctcc----showCard success");
                messageItemCard = new MessageItemCardCtcc(mContext);
                messageItemCard.setWidth(Constants.CARD_WIDTH);
                messageItemCard.setBody(mBody);
                messageItemCard.setAddress(mPort);
                messageItemCard.setMsgId(mId);
                messageItemCard.setReceiveDate(receivedTimsStamp);
                messageItemCard.setSmsCardInfo(cardInfo);
            }
            if (cardInfoCallBackImpl != null) {
                cardInfoCallBackImpl.refreshCardView(messageItemCard);
            }
        }

        @Override
        public void onFailure(String errorMsg) {
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, " CtccCardCallBack, onFailure,mPort/errorMsg:" + mPort + "/" + errorMsg);
            if (cardInfoCallBackImpl != null) {
                cardInfoCallBackImpl.refreshCardView(null);
            }
        }
    }

    private String getIdFromUri(final String uriString) {
        final int idIndex = uriString.lastIndexOf("/") + 1;
        return uriString.substring(idIndex);
    }
}

