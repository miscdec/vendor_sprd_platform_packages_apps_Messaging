/**
 * Created by SPRD on 2019/01/07.
 */
package com.sprd.messaging.smart;

import android.content.Context;

import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.ui.conversation.Constants;
import com.android.messaging.ui.conversation.ScreenUtils;
import com.android.messaging.util.LogUtil;
import com.sprd.messaging.smart.messagecard.MessageItemCardCucc;
import com.unicom.callme.engine.SmartSmsEngine;
import com.unicom.callme.outerentity.CardInfo;
import com.unicom.callme.outerentity.OrgInfo;
import com.unicom.callme.outerentity.SmsInfo;

public class CuccSmartSdk implements ISmartSdk {

    private static CuccSmartSdk smartSdk = null;

    private CuccSmartSdk() {
    }

    public static CuccSmartSdk getInstance() {
        synchronized (CtccSmartSdk.class) {
            if (null == smartSdk) {
                smartSdk = new CuccSmartSdk();
            }
        }
        return smartSdk;
    }

    @Override
    public void getServiceInfo(Context context, String sendDestination, IServiceInfoCallBack callBack) {
        OrgInfo orgInfo = SmartSmsEngine.getInstance().getOrgInfo(context, sendDestination);
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "cucc getServiceInfo number/orgInfo = " + sendDestination + "/" + orgInfo);
        String portName = "";
        String portLogo = "";
        if (orgInfo != null && callBack != null) {
            portName = orgInfo.getName();
            portLogo = orgInfo.getLogo();
            callBack.getResult(new ServiceInfoEntity(portName, portLogo));
        }
    }

    @Override
    public void initSdk(Context context) {
        boolean initResult = SmartSmsEngine.getInstance().init(context, "1006585400006", null, true, true);
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "CUCC initSmartSdk----initResult:" + initResult);
        Constants.CARD_WIDTH = ScreenUtils.getScreenWidth(context) / 4 * 3;
    }

    private String getIdFromUri(final String uriString) {//bug 1001718
        final int idIndex = uriString.lastIndexOf("/") + 1;
        return uriString.substring(idIndex);
    }

    @Override
    public void updateCardInfo(Context context, ConversationMessageData data, ICardInfoCallBack cardInfoCallBack) {
        final String content = data.getText();
        String mPort = data.getSenderDisplayDestination();
        String frommMessageSmsUrigetId = getIdFromUri(data.getSmsMessageUri());
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, " showCuccCard mMessageSmsUri:" + data.getSmsMessageUri() + ";frommMessageSmsUrigetId:" + frommMessageSmsUrigetId);
        try {
            SmsInfo.Builder smsInfoBuilder = new SmsInfo.Builder()
                    .setId(frommMessageSmsUrigetId)
                    .setPhoneNumber(mPort)
                    .setBody(content)
                    .setReceiveTime(System.currentTimeMillis());

            SmsInfo smsInfo = new SmsInfo(smsInfoBuilder);
            final CardInfo msgCardInfo = SmartSmsEngine.getInstance().getMsgCardInfo(context, smsInfo);
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, " cucc----msgCardInfo" + msgCardInfo);
            MessageItemCardCucc messageItemCard = null;
            if (msgCardInfo != null) {
                messageItemCard = new MessageItemCardCucc(context);
                messageItemCard.setBody(content);
                messageItemCard.setAddress(mPort);
                messageItemCard.setMsgId(frommMessageSmsUrigetId);
                messageItemCard.setReceiveDate(data.getReceivedTimeStamp());
                messageItemCard.setSmsCardInfo(msgCardInfo);
            }
            if (cardInfoCallBack != null) {
                cardInfoCallBack.refreshCardView(messageItemCard);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

