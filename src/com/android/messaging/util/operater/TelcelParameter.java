package com.android.messaging.util.operater;

import com.android.messaging.sms.MmsConfig;
import android.os.Bundle;
import androidx.appcompat.mms.CarrierConfigValuesLoader;
import android.util.Log;
import com.sprd.messaging.util.SystemAdapter;

public class TelcelParameter extends BaseParameter {
    private static final String TAG = "TelcelParameter";
    private static final int CONFIG_TELCEL_MAX_MESSAGE_SIZE_DEFAULT = 1024 * 1024;

    protected Bundle getBundle() {
        Bundle b = new Bundle();

        // Set Telcel Max Attachement 1M
        b.putInt(CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE, CONFIG_TELCEL_MAX_MESSAGE_SIZE_DEFAULT);

        // Show Encode Type ;
        int  encodeType = Integer.parseInt(MmsConfig.getEncodeType());
        Log.d(TAG,  "encodeType get from Preference = " + encodeType );
        if (encodeType == -1 ){
            encodeType = 0;
        }
        Log.d(TAG,  "encodeType  = " + encodeType );
        b.putInt(MmsConfig.ENCODETYPE_PREFE_STATUS, encodeType);
        SystemAdapter.getInstance().setProperty(MmsConfig.SMS_ENCODE_TYPE,  String.valueOf(encodeType) );

        // Set SMSC Can Edit ;
        b.putBoolean(MmsConfig.SMSC_EDITEABLE, false);

        return b;
    }
}
