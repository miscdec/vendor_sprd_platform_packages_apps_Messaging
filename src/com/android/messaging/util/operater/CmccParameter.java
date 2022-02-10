package com.android.messaging.util.operater;

import com.android.messaging.sms.MmsConfig;

import android.os.Bundle;
import android.util.Log;

public class CmccParameter extends BaseParameter {

    private static final String TAG = "CmccParameter";

    protected Bundle getBundle() {
        Bundle b = new Bundle();

        // Forword Use Smil;
        b.putBoolean(MmsConfig.FORWARD_MESSAGE_USING_SMIL, true);

        // Play Beep when Calling
        b.putBoolean(MmsConfig.BEEP_ON_CALL_STATE, true);

        // Edit Context
        b.putBoolean(MmsConfig.CONTENT_EDIT_ENABLED, true);

        // using Cmcc Var
        b.putBoolean(MmsConfig.IS_CMCC_PARAM, true);

        //hide encode type setting for Bug 686840
        b.putInt(MmsConfig.ENCODETYPE_PREFE_STATUS, -1);

        return b;
    }

}
