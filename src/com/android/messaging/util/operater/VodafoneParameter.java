/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.messaging.util.operater;

import com.android.messaging.sms.MmsConfig;
import com.sprd.messaging.util.SystemAdapter;
import android.os.Bundle;
import androidx.appcompat.mms.CarrierConfigValuesLoader;
import android.util.Log;

public class VodafoneParameter extends BaseParameter {

    private static final String TAG = "VodafoneParameter";
    protected Bundle getBundle() {
        Bundle b = new Bundle();

        // Sms To Mms 5;
        b.putInt(CarrierConfigValuesLoader.CONFIG_SMS_TO_MMS_TEXT_THRESHOLD, 5);

        // Set Attachment Save To External SD
        b.putBoolean(MmsConfig.SAVE_ATTACHMENTS_TO_EXTERNAL, true);

        // default Encode Type is 7bit for Bug 694629
        int  encodeType = Integer.parseInt(MmsConfig.getEncodeType());
        Log.d(TAG,  "encodeType get from Preference = " + encodeType );
        if (encodeType == -1 ){
            encodeType = 1;//7bit Bug 716224
        }
        Log.d(TAG,  "encodeType  = " + encodeType );
        b.putInt(MmsConfig.ENCODETYPE_PREFE_STATUS, encodeType);
        SystemAdapter.getInstance().setProperty(MmsConfig.SMS_ENCODE_TYPE,  String.valueOf(encodeType) );

        return b;
    }

}
