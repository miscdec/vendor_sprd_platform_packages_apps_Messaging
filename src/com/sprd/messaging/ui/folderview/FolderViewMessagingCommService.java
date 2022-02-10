
package com.sprd.messaging.ui.folderview;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Telephony;

import androidx.core.app.NotificationManagerCompat;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.ParticipantRefresh;
import com.android.messaging.datamodel.action.CopySmsToSimAction;
import com.android.messaging.datamodel.action.DeleteSimSmsAction;
import com.android.messaging.datamodel.action.ResendMessageAction;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PendingIntentConstants;
import com.android.messaging.util.UiUtils;

import java.util.ArrayList;

public class FolderViewMessagingCommService extends Service implements
        CopySmsToSimAction.CopySmsToSimActionListener{
    public static final String TAG = "FolderViewMessagingCommService";
    public static final String KEY_COMM = "key_comm";
    public static final int INVAIL_KEY = -1;
    public static final int KEY_SMS_NOTIFICATION_ID = 0;
    public static final int KEY_MESSAGE_RESEND = 1;
    public static final int KEY_MSG_SEND_ERROR = 2;
    public static final int KEY_PARTICIPANTS_REFRESH = 3;
    public static final int KEY_COPY_SIMSMS_TO_PHONE = 4;
    private static final int KEY_COPY_SMS_TO_SIM = 5;
    private static final int KEY_DEL_SIM_SMS = 6;
    private static final int KEY_CALL_ON_ACTIVITYRESUME = 7;//Add for Bug 574455
    private static final String SMS_NOTIFICATION_TAG = ":sms:";
    private static final String SMS_ERROR_NOTIFICATION_TAG = ":error:";
    public static final String UI_INTENT_EXTRA_MESSAGE_ID = "message_id";
    public static final String UI_INTENT_EXTRA_SMS_URI_LIST = "sms_uri_list";
    public static final int INVAILD_MESSAGE_ID = -1;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onCreate() {
        LogUtil.d(TAG, "onCreate");
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void updateSmsNotificationDatabase() {
        // Everything in telephony should already have the seen bit set.
        // Possible exception are messages which did not have seen set and
        // were sync'ed into bugle.
        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();

        try {
            final ContentValues values = new ContentValues();
            values.put(MessageColumns.SEEN, 1);
            final int count = db.update(DatabaseHelper.MESSAGES_TABLE, values, MessageColumns.SEEN
                    + " = 0 AND (" + MessageColumns.STATUS + " != "
                    + MessageData.BUGLE_STATUS_OUTGOING_FAILED + " AND " + MessageColumns.STATUS
                    + " != " + MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED + ")", null);
            LogUtil.d(TAG, "updateSmsNotificationDatabase:count="+count);
            if (count > 0) {
                MessagingContentProvider.notifyMessageListViewChanged();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void updateSentErrorNotificationDatabase() {
        // Everything in telephony should already have the seen bit set.
        // Possible exception are messages which did not have seen set and
        // were sync'ed into bugle.
        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();

        try {
            final ContentValues values = new ContentValues();
            values.put(MessageColumns.SEEN, 1);
            final int count = db.update(DatabaseHelper.MESSAGES_TABLE, values, MessageColumns.SEEN
                    + " = 0 AND (" + MessageColumns.STATUS + " = "
                    + MessageData.BUGLE_STATUS_OUTGOING_FAILED + " OR " + MessageColumns.STATUS
                    + " = " + MessageData.BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED + ")", null);
            LogUtil.d(TAG, "updateSentErrorNotificationDatabase:count="+count);
            if (count > 0) {
                MessagingContentProvider.notifyMessageListViewChanged();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String buildNotificationTag(final int type, final String conversationId) {
        String tag = null;
        switch (type) {
            case PendingIntentConstants.SMS_NOTIFICATION_ID:
                tag = buildNotificationTag(SMS_NOTIFICATION_TAG, conversationId);
                break;
            case PendingIntentConstants.MSG_SEND_ERROR:
                tag = buildNotificationTag(SMS_ERROR_NOTIFICATION_TAG, null);
                break;
        }
        return tag;
    }

    private String buildNotificationTag(final String name, final String conversationId) {
        final Context context = Factory.get().getApplicationContext();
        if (conversationId != null) {
            return context.getPackageName() + name + ":" + conversationId;
        } else {
            return context.getPackageName() + name;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d(TAG, "onStartCommand");
        if (intent == null) {
            LogUtil.e(TAG, "onStartCommand intent is null");
            return Service.START_NOT_STICKY;
        }
        int key = intent.getIntExtra(KEY_COMM, INVAIL_KEY);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        int messageId = intent.getIntExtra(UI_INTENT_EXTRA_MESSAGE_ID, INVAILD_MESSAGE_ID);
        final int subId = intent.getIntExtra("subId", -1);
        LogUtil.e(TAG, "key=" + key);
        switch (key) {
            case KEY_SMS_NOTIFICATION_ID:
                final String smsNotificationTag = buildNotificationTag(
                        PendingIntentConstants.SMS_NOTIFICATION_ID, null);
                notificationManager.cancel(smsNotificationTag,
                        PendingIntentConstants.SMS_NOTIFICATION_ID);
                //650386 start
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        updateSmsNotificationDatabase();
                        }
                }).start();
                //650386 end
                break;
            case KEY_MESSAGE_RESEND:
                if (messageId != INVAILD_MESSAGE_ID) {
                    ResendMessageAction.resendMessage(String.valueOf(messageId));
                }
                break;
            case KEY_MSG_SEND_ERROR:
                final String sentErrorNotificationTag = buildNotificationTag(
                        PendingIntentConstants.MSG_SEND_ERROR, null);
                notificationManager.cancel(sentErrorNotificationTag,
                        PendingIntentConstants.MSG_SEND_ERROR);
                updateSentErrorNotificationDatabase();
                break;
            case KEY_PARTICIPANTS_REFRESH:
                ParticipantRefresh.refreshParticipantsIfNeeded();
                break;
            /**Bug 489223 begin */
            case KEY_COPY_SIMSMS_TO_PHONE:
                final String bobyText = intent.getStringExtra("bobyText");
                final long receivedTimestamp = intent.getLongExtra("receivedTimestamp", -1L);
                int messageStatus = intent.getIntExtra("messageStatus", 0);
                if (messageStatus >= 100 && messageStatus <= 107) {
                    messageStatus = 1;
                }
                final int type = messageStatus;
                final boolean isRead = intent.getBooleanExtra("isRead", false);
                final String address = intent.getStringExtra("address");
                final Context context = Factory.get().getApplicationContext();
                final long threadId = MmsUtils.getOrCreateSmsThreadId(context, address);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                         /*Modify by SPRD for 556034 Start*/
                         Uri copyToPhoneUri;
                         copyToPhoneUri = MmsUtils.insertSmsMessage(context, Telephony.Sms.CONTENT_URI, subId,
                                                  address, bobyText,
                                                  receivedTimestamp, Telephony.Sms.STATUS_NONE,
                                                  type, threadId);
                         if (copyToPhoneUri != null) {
                             mCopyToPhoneHandler.sendEmptyMessage(MSG_COPY_SIMSMS_TO_PHONE_SUCC);
                         } else {
                             mCopyToPhoneHandler.sendEmptyMessage(MSG_COPY_SIMSMS_TO_PHONE_FAIL);
                         }
                         /*Modify by SPRD for 556034 End*/
                    }
                }).start();
                break;
            case KEY_COPY_SMS_TO_SIM:
                ArrayList<String> smsUriList = intent.getStringArrayListExtra(UI_INTENT_EXTRA_SMS_URI_LIST);
                if (smsUriList != null) {
                    CopySmsToSimAction.copySmsToSim(smsUriList, subId, this);
                }
                break;
            case KEY_DEL_SIM_SMS:
                final int indexOnIcc = intent.getIntExtra("index_on_icc", INVAILD_MESSAGE_ID);

                if (indexOnIcc != INVAILD_MESSAGE_ID) {
                    DeleteSimSmsAction.deleteMessage(indexOnIcc, subId);
                }
                break;
            /**Bug 489223 end */
            /**Add for Bug 574455 begin*/
            case KEY_CALL_ON_ACTIVITYRESUME:
                DataModel.get().onActivityResume();
                Factory.get().onActivityResume();
                break;
            /**Add for Bug 574455 end*/
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCopySmsToSimFull() {
        UiUtils.showToastAtBottom(R.string.sim_space_not_enough);
    }

    /*Add by SPRD for 556034 Start*/
    private static final int MSG_COPY_SIMSMS_TO_PHONE_SUCC = 10;
    private static final int MSG_COPY_SIMSMS_TO_PHONE_FAIL = 11;
    private Handler mCopyToPhoneHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_COPY_SIMSMS_TO_PHONE_SUCC:
                UiUtils.showToastAtBottom(R.string.move_message_to_phone_memory);
                break;
            case MSG_COPY_SIMSMS_TO_PHONE_FAIL:
                UiUtils.showToastAtBottom(R.string.can_not_copy_the_message);
                break;
            }
        };
    };
    /*Add by SPRD for 556034 End*/
}
