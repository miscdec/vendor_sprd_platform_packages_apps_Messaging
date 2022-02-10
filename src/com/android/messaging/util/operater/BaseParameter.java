
package com.android.messaging.util.operater;

import android.os.Bundle;
import android.util.Log;

import com.android.messaging.sms.MmsConfig;
import com.sprd.messaging.util.SystemAdapter;

/*
 * some parameter should set in the subclass of BaseParameter,for example CMCC , telcel and so on.
 * */
public class BaseParameter implements IParameter {
    protected static final String TAG = "Operator_Parameter";

    private Object mParameter; // May be null.

    public void setParameter(Object data) {
        mParameter = data;
        setBundle(getBundle());
    }

    protected Bundle getBundle() {
        Bundle b = new Bundle();
        // Show Encode Type ;
        int  encodeType =-1;
        String encodeTypePro =  SystemAdapter.getInstance().getProperty(MmsConfig.SMS_ENCODE_TYPE);
        try {
             encodeType = Integer.parseInt(encodeTypePro);
        } catch (NumberFormatException e) {
             Log.e(TAG, "Failed to parse encodeType " + encodeType);
        }
        Log.d(TAG, "BaseParameter encodeType = " + encodeType);
        b.putInt(MmsConfig.ENCODETYPE_PREFE_STATUS, encodeType);
        return b;
    }

    protected void setBundle(Bundle b) {
        if (b != null && mParameter instanceof MmsConfig) {
            ((MmsConfig) mParameter).setValues(b);
        }
    }

}
