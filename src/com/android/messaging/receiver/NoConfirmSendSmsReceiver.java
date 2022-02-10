package com.android.messaging.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.messaging.datamodel.action.InsertNewMessageAction;
import com.android.messaging.datamodel.data.ParticipantData;

// add for bug 921561
public class NoConfirmSendSmsReceiver extends BroadcastReceiver {

    private static final String TAG ="NoConfirmSendSmsReceiver";
    private static final String EXTRA_SUBSCRIPTION = "subscription";
    private static final String EXTRA_RECIPIENT="recipient";
    private static final String EXTRA_MESSAGE="messagetext";
    private static final String ACTION_SEND_VIA_MESSAGE = "sprd.intent.action.SEND_VIA_MESSAGE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action = " + action);
        if(!ACTION_SEND_VIA_MESSAGE.equals(action)){
            Log.d(TAG,"not sprd.intent.action.RESPOND_VIA_MESSAGE");
            return;
        }
        Bundle extras =intent.getExtras();
        if(extras == null){
            Log.d(TAG,"extras is null");
            return;
        }
        int subId =extras.getInt(EXTRA_SUBSCRIPTION, ParticipantData.DEFAULT_SELF_SUB_ID );
        String recipient=extras.getString(EXTRA_RECIPIENT);
        String messageText=extras.getString(EXTRA_MESSAGE);
        if(TextUtils.isEmpty(recipient) || TextUtils.isEmpty(messageText)){
            Log.d(TAG,"recipient or message is null");
            return;
        }
        Log.d(TAG, "onReceive: subId= " + subId + "recipient = " + recipient + "messageText = " + messageText);
        InsertNewMessageAction.insertNewMessage(subId, recipient, messageText, null);

   }
}
