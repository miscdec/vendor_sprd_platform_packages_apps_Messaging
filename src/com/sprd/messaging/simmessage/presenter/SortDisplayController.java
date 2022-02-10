//by sprd
package com.sprd.messaging.simmessage.presenter;

import android.content.Context;
import android.content.SharedPreferences;

import com.sprd.messaging.simmessage.data.SortMsgDataCollector;

public class SortDisplayController {
    public static final String TAG = "SortDisplayController";
    private static SortDisplayController mInstance = null;

    private SortDisplayController() {
    }

    private SortDisplayController(Context context) {
        initSharedPreferences(context);
    }

    public static synchronized SortDisplayController getInstance(Context context) {
        synchronized (SortDisplayController.class) {
            if (mInstance == null) {
                mInstance = new SortDisplayController(context.getApplicationContext());
            }
        }
        return mInstance;
    }

    private SharedPreferences mPreferences;
    private SharedPreferences.Editor mEditor;
    private final String PREFERENCES_NAME = "displaycontrol";

    public void putIntForDisplayOption(int value) {
        mEditor.putInt(SortMsgDataCollector.SHOW_SIM_MESSAGE_BY_SUB_ID, value);
        mEditor.commit();
    }

    public int getValueForDisplayOption() {
        return mPreferences.getInt(SortMsgDataCollector.SHOW_SIM_MESSAGE_BY_SUB_ID,
                SortMsgDataCollector.SHOW_ALL_MESSAGE);
    }

    private String orderValueArray[] = {SortMsgDataCollector.getOrderByReceivedTimeDesc(),
            SortMsgDataCollector.getOrderByReceivedTimeAsc(),
            SortMsgDataCollector.getOrderByPhoneNumberDesc(),
            SortMsgDataCollector.getOrderByPhoneNumberAsc()};

    public void putStringForOrderType(int whichButton) {
        if (whichButton < orderValueArray.length) {
            mEditor.putString(SortMsgDataCollector.getMsgOrderKey(), orderValueArray[whichButton]);
            mEditor.commit();
        }
    }

    private String getOrderValue() {
        String orderKey = SortMsgDataCollector.getMsgOrderKey();
        return mPreferences.getString(orderKey, "");
    }

    public int getCheckedItemByOrderValue() {
        int checkedItem;
        switch (getOrderValue()) {
            case SortMsgDataCollector.ORDER_BY_TIME_DESC:
                checkedItem = 0;
                break;
            case SortMsgDataCollector.ORDER_BY_TIME_ASC:
                checkedItem = 1;
                break;
            case SortMsgDataCollector.ORDER_BY_PHONE_NUMBER_DESC:
                checkedItem = 2;
                break;
            case SortMsgDataCollector.ORDER_BY_PHONE_NUMBER_ASC:
                checkedItem = 3;
                break;
            default:
                checkedItem = 0;
                break;
        }
        return checkedItem;
    }

    private void initSharedPreferences(Context context) {
        if (null == mPreferences) {
            mPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }
        if (null == mEditor) {
            mEditor = mPreferences.edit();
        }
    }
}
