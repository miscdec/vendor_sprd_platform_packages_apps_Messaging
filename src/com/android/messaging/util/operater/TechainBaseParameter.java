
package com.android.messaging.util.operater;

import com.android.messaging.sms.MmsConfig;
import android.os.Bundle;
import android.util.Log;


public class TechainBaseParameter implements IParameter {

    protected TechainBaseParameter() {
        Log.e(TAG, "Enter [" + getClass().toString() + "]");
    }


    public void setParameter(Object data) {
        mParameter = data;
        MmsConfig.setIsTechain(true);
        setBundle(getBundle());
    }

    protected Bundle getBundle() {
        //Log.e(TAG, " Invoke Base getBundle()", new Exception("Base Get Bundle"));
        return null;
    }

    protected void setBundle(Bundle b) {
        if (b != null && getParameter() != null && (getParameter() instanceof MmsConfig)) {
            ((MmsConfig) getParameter()).setValues(b);
        } else {
            Log.e(TAG, "seBundle b = [" + b + "] getParameter() = [" + getParameter() + "]");
        }
    }

    private Object getParameter() {
        return mParameter;
    }

    private Object mParameter; // May be null.

    protected static final String TAG = "TechainBaseParameter";


}
