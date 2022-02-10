package com.android.messaging.util.operater;

import com.android.messaging.sms.MmsConfig;

import android.os.Bundle;
import android.util.Log;

public class CuccParameter extends BaseParameter {

    private static final String TAG = "CuccParameter";

    protected Bundle getBundle() {
        Bundle b = new Bundle();
        // edit content;
        b.putBoolean(MmsConfig.CONTENT_EDIT_ENABLED, true);

        // Attachment Save to Flash
        b.putBoolean(MmsConfig.SAVE_ATTACHMENTS_TO_EXTERNAL, true);

        // Display Messaging user Memory Infomation
        b.putBoolean(MmsConfig.INTERNAL_MEMORY_USAGE_ENABLED, true);

        // Play Beep while Calling;
        b.putBoolean(MmsConfig.BEEP_ON_CALL_STATE, true);

        //hide encode type setting for Bug 686840
        b.putInt(MmsConfig.ENCODETYPE_PREFE_STATUS, -1);

        return b;
    }

}
