/**
 * Created by SPRD on 2019/01/07.
 */
package com.sprd.messaging.smart;

import android.content.Context;
import android.view.View;

import com.android.messaging.datamodel.data.ConversationMessageData;

public interface ISmartSdk {

    /**
     * @param context
     */
    void initSdk(Context context);

    /**
     * @param context
     * @param sendDestination
     * @return service info includes logo & name
     */
    void getServiceInfo(Context context, String sendDestination, IServiceInfoCallBack callBack);

    /**
     * @param context
     * @param data    message-data
     */
    void updateCardInfo(Context context, ConversationMessageData data, ICardInfoCallBack cardInfoCallBack);

    interface IServiceInfoCallBack {
        /**
         * @param serviceInfoEntity name & logo
         */
        void getResult(ServiceInfoEntity serviceInfoEntity);
    }

    interface ICardInfoCallBack {
        /**
         * @param cardView update ui
         */
        void refreshCardView(View cardView);
    }

}

