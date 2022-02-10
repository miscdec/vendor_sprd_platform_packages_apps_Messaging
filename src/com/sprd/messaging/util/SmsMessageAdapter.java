package com.sprd.messaging.util;

import android.telephony.SmsMessage;

public class SmsMessageAdapter{
    public SmsMessageAdapter(SmsMessage message){
        mSmsMessage = message;
    }
    public SmsMessage mSmsMessage;
    public String mRecipientAddress;
}
