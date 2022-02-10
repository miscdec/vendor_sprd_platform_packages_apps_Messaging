//by sprd
package com.sprd.messaging.simmessage;

import android.content.Context;
import android.content.Intent;

import com.android.messaging.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

public class SimUtils {
    public static final String TAG = "SimUtils";

    /**
     * folder view and messaging common service, cancle notification, resend message,
     */
    private static final String KEY_COMM = "key_comm";
    public static final int KEY_COPY_SIMSMS_TO_PHONE = 4;
    public static final int KEY_COPY_SMS_TO_SIM = 5;
    public static final int KEY_DEL_SIM_SMS = 6;

    private static final String MESSAGING_COMMON_SERVICE_ACTION = "sprd.intent.action.FOLDER_VIEW_MESSAGING_COMM";

    public static void copySmsToSim(Context context, List<String> smsUriList, int subId) {
        LogUtil.d(TAG, "copySmsToSim ...");
        Intent intent = new Intent(MESSAGING_COMMON_SERVICE_ACTION);
        intent.setPackage(context.getPackageName());
        intent.putStringArrayListExtra("sms_uri_list", (ArrayList<String>) smsUriList);
        intent.putExtra("subId", subId);
        intent.putExtra(KEY_COMM, KEY_COPY_SMS_TO_SIM);

        try {
            context.startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteSingleSimSms(Context context, int smsIndex, int subId) {
        LogUtil.d(TAG, "deleteSimSms smsIndex/subId: " + smsIndex + "/" + subId);
        Intent intent = new Intent(MESSAGING_COMMON_SERVICE_ACTION);
        intent.setPackage(context.getPackageName());
        intent.putExtra("index_on_icc", smsIndex);
        intent.putExtra("subId", subId);
        intent.putExtra(KEY_COMM, KEY_DEL_SIM_SMS);

        try {
            context.startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void copySimSmsToPhone(Context context, int subId, String bobyText,
                                         long receivedTimestamp, int messageStatus,
                                         boolean isRead, String address) {
        LogUtil.d(TAG, "copySimSmsToPhone ...");
        Intent intent = new Intent(MESSAGING_COMMON_SERVICE_ACTION);
        intent.setPackage(context.getPackageName());
        intent.putExtra("subId", subId);
        intent.putExtra("bobyText", bobyText);
        intent.putExtra("receivedTimestamp", receivedTimestamp);
        intent.putExtra("messageStatus", messageStatus);
        intent.putExtra("isRead", isRead);
        intent.putExtra("address", address);
        intent.putExtra(KEY_COMM, KEY_COPY_SIMSMS_TO_PHONE);
        try {
            context.startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
