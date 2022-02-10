package com.android.messaging.receiver;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Telephony.Sms.Intents;
import android.util.Log;

import com.android.messaging.Factory;
import com.android.messaging.sms.MmsUtils;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.sprd.telephony.RadioInteractor;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.PendingIntentConstants;
//import com.android.internal.telephony.PhoneConstants;
import com.android.messaging.R;
import com.android.messaging.sms.MmsConfig;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;//768842
/**
 * Receiver that listens on storage status changes
 */
public class SimStatusReceiver extends BroadcastReceiver {
    RadioInteractor mRadioInteractor = new RadioInteractor(Factory.get()
            .getApplicationContext());
    // sprd #554003 start 2016/4/21
    public static final String SIM_MESSAGE_DELETE_ACTION = "com.android.providers.telephony.SIM_MESSAGE_DELETE";
    public static final String SUB_ID = "sub_id";
    public static final String SLOT_KEY = "slot";

    // sprd #554003 end 2016/4/21
    @Override
    public void onReceive(final Context context, final Intent intent) {
        Log.d("SimStatusReceiver",
                "onReceive intent.getAction() = " + intent.getAction());
        if (Intents.SIM_FULL_ACTION.equals(intent.getAction())) {
            int phoneId = intent.getIntExtra(SLOT_KEY, -1);//PhoneConstants.SLOT_KEY, -1);
            Log.d("SimStatusReceiver", "onReceive phoneId = " + phoneId);
            String mSim = mRadioInteractor.querySmsStorageMode(phoneId);
            int subId = MmsUtils.tanslatePhoneIdToSubId(context, phoneId);
            final Resources res = context.getResources();
            SharedPreferences.Editor prefEditor = PreferenceManager
                    .getDefaultSharedPreferences(context).edit();
            if ("SM".equals(mSim)) {
                postSimFullNotification();
                boolean retValue = mRadioInteractor.storeSmsToSim(false,
                        phoneId);
                if (retValue) {
                    // prefEditor
                    // .putString(
                    // res.getString(R.string.sms_save_to_sim_pref_key)
                    // + subId, "false");
                    // prefEditor.commit();
                    MmsConfig.get(subId).setSmsModemStorage(String.valueOf(subId), "ME");
                }
                Log.d("SimStatusReceiver",
                        "onReceive  storeSmsToSim false retValue = " + retValue
                                + " mSim = " + mSim);
            }
        }
        // sprd #554003 start 2016/4/21
        if (SIM_MESSAGE_DELETE_ACTION.equals(intent.getAction())) {
            int SubId = Integer.parseInt(intent.getStringExtra(SUB_ID));
            int phoneId = MmsUtils.tanslateSubIdToPhoneId(context, SubId);
            final Resources res = context.getResources();
            SharedPreferences sharedPref = PreferenceManager
                    .getDefaultSharedPreferences(context);
            String value = sharedPref.getString(
                    res.getString(R.string.sms_save_to_sim_pref_key) + SubId,
                    Boolean.toString(res
                            .getBoolean(R.bool.sms_save_to_sim_pref_default)));
            boolean enable = Boolean.valueOf(value);
            Log.d("SimStatusReceiver", "SIM_MESSAGE_DELETE_ACTION  SubId = "
                    + SubId + " sharedPref value = " + value);
            if (enable
                    && ("ME".equals(MmsConfig.get(SubId).getSmsModemStorage(
                    intent.getStringExtra(SUB_ID))) || ""
                    .equals(MmsConfig.get(SubId).getSmsModemStorage(
                            intent.getStringExtra(SUB_ID))))) {
                boolean retValue = mRadioInteractor.storeSmsToSim(enable,
                        phoneId);
                if (retValue) {
                    MmsConfig.get(SubId).setSmsModemStorage(String.valueOf(SubId), "SM");
                }
                Log.d("SimStatusReceiver",
                        "SIM_MESSAGE_DELETE_ACTION  storeSmsToSim true retValue = "
                                + retValue + " SubId = " + SubId);
            }
        }
        // sprd #554003 end 2016/4/21
        //add bug 842576   start
        String action = intent.getAction();
//758842 begin
        if (action !=null && action.equals(UIIntents.ACTION_TOAST_COPY_MESSAGE_TO_SIM)) {
            //add bug 842576   end
            final String msg = intent.getStringExtra("msg");
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }
        }
        //758842 end
    }

    /**
     * Post sms storage low notification
     */
    private static void postSimFullNotification() {
        final Context context = Factory.get().getApplicationContext();
        final Resources resources = context.getResources();
        final PendingIntent pendingIntent = UIIntents.get()
                .getPendingIntentForLowStorageNotifications(context);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context);
        builder.setContentTitle(
                resources.getString(R.string.sms_sim_full_title))
                .setTicker(
                        resources
                                .getString(R.string.sms_sim_full_notification_ticker))
                .setSmallIcon(R.drawable.ic_failed_light)
                .setPriority(Notification.PRIORITY_DEFAULT)
                // .setOngoing(true) // Can't be swiped off
                .setAutoCancel(true); // Don't auto cancel
        // .setContentIntent(pendingIntent);

        final NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle(
                builder);
        bigTextStyle.bigText(resources.getString(R.string.sms_sim_full_text));
        final Notification notification = bigTextStyle.build();

        final NotificationManagerCompat notificationManager = NotificationManagerCompat
                .from(Factory.get().getApplicationContext());

        //Bug950322 begin
//        notificationManager.notify(
//                // getNotificationTag(),
//                PendingIntentConstants.SMS_SIM_FULL_NOTIFICATION_ID,
//                notification);
        Notification.Builder b = Notification.Builder.recoverBuilder(
                Factory.get().getApplicationContext(),notification);
        b.setChannelId(PendingIntentConstants.MESSAGING_CHANNAL_ID);
        Notification nN = b.build();
        notificationManager.notify(PendingIntentConstants.SMS_SIM_FULL_NOTIFICATION_ID, nN);
        //Bug950322 end
    }

    /**
     * Cancel the notification
     */
    public static void cancelStorageLowNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat
                .from(Factory.get().getApplicationContext());
        notificationManager.cancel(// getNotificationTag(),
                PendingIntentConstants.SMS_SIM_FULL_NOTIFICATION_ID);
    }
}