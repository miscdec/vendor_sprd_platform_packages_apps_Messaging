/**
 * Created by SPRD on 2019/01/07.
 */
package com.sprd.messaging.smart;

import android.content.Context;
import android.os.AsyncTask;

import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.util.LogUtil;

import cn.cmcc.online.smsapi.NCardViewHelper;
import cn.cmcc.online.smsapi.SmsPlus;
import cn.cmcc.online.smsapi.TerminalApi;
import cn.cmcc.online.smsapi.entity.CardView;
import cn.cmcc.online.smsapi.entity.SmsCardData;
import cn.cmcc.online.smsapi.entity.SmsPortData;

public class CmccSmartSdk implements ISmartSdk {

    private static CmccSmartSdk smartSdk = null;

    private CmccSmartSdk() {
    }

    public static CmccSmartSdk getInstance() {
        synchronized (CmccSmartSdk.class) {
            if (null == smartSdk) {
                smartSdk = new CmccSmartSdk();
            }
        }
        return smartSdk;
    }

    @Override
    public void getServiceInfo(Context context, String sendDestination, IServiceInfoCallBack callBack) {
        SmsPortData portData = TerminalApi.getPortDataManager(context).getPortInfo(context, sendDestination);
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "cmcc getServiceInfo, sendDestination" + sendDestination);
        if (portData != null && callBack != null) {
            final String portName = portData.getName();
            final String portLogo = portData.getLogoUrl();
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "cmcc getServiceInfo, portName/portLogo:" + portName + "/" + portLogo);
            callBack.getResult(new ServiceInfoEntity(portName, portLogo));
        }
    }

    @Override
    public void initSdk(Context context) {
        SmsPlus.init(context);
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "CMCC initSmartSdk----init");
    }

    @Override
    public void updateCardInfo(Context context, ConversationMessageData data, ICardInfoCallBack cardInfoCallBack) {
        new GetCardInfoAsyncTask(context, data, cardInfoCallBack).execute();
    }

    private final class GetCardInfoAsyncTask extends AsyncTask<Void, Void, SmsCardData> {
        private final NCardViewHelper nCardViewHelper;
        private Context mContext;
        private ConversationMessageData mData;
        private ICardInfoCallBack cardInfoCallBackImpl;

        public GetCardInfoAsyncTask(Context context, ConversationMessageData data, ICardInfoCallBack cardInfoCallBack) {
            this.mContext = context;
            this.mData = data;
            this.cardInfoCallBackImpl = cardInfoCallBack;
            nCardViewHelper = NCardViewHelper.getInstance(mContext);
        }

        @Override
        protected final SmsCardData doInBackground(final Void... params) {
            final String content = mData.getText();
            String mPort = mData.getSenderDisplayDestination();
            String mMessageId = mData.getMessageId();
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "GetCardInfoAsyncTask doInBackground, mPort: " + mPort + "; mMessageId=" + mMessageId);

            nCardViewHelper.setCardWidth(mContext, NCardViewHelper.CARD_WIDTH_280);
            SmsCardData cardData = nCardViewHelper.getCardData(mContext, Integer.parseInt(mMessageId), mPort, content, mData.getReceivedTimeStamp());
            return cardData;
        }

        @Override
        protected void onPostExecute(SmsCardData cardData) {
            int cardType = nCardViewHelper.getCardType(cardData);
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "GetCardInfoAsyncTask ui, cardType: " + cardType);
            if (cardType > -1) {
                CardView cardView = nCardViewHelper.obtainCardView(mContext, cardType, null);
                if (cardView.onUpdate(cardData) && cardInfoCallBackImpl != null) {
                    cardInfoCallBackImpl.refreshCardView(cardView);
                }
            }
        }
    }
}

