package com.android.messaging.util.operater;

import android.text.TextUtils;
import java.security.InvalidParameterException;
import com.android.messaging.sms.SystemProperties;
import android.util.Log;

public class AbsOperaterFactory {

    public static IParameter CreateProductByType(String szType){
            return getDefaultImpl();
    }
    private static IParameter getDefaultImpl() {
        return mins;
    }
    private static IParameter mins = new TechainBaseParameter();
}
