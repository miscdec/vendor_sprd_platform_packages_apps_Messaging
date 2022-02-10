package com.android.messaging.receiver;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.text.TextUtils;
import android.util.Log;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.util.PendingIntentConstants;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.widget.BugleWidgetProvider;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.DatabaseHelper.MessageColumns;
import com.android.messaging.datamodel.action.DeleteMessageAction;

public class WapPushDeleteReceiver extends BroadcastReceiver {
    private static final String TAG = "WapPushDeleteReceiver";
    private final static String WAP_PUSH_EXPRIRE_DELETE_ACTION = "com.android.mms.transaction.wappush_expire_delete";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "==========wap push===0==onReceive=====");
        if (PhoneUtils.getDefault().isSmsEnabled()) {
            if (WAP_PUSH_EXPRIRE_DELETE_ACTION.equals(intent.getAction())) {
                Log.d(TAG, "======wap push====1======wap push delete=====");
                deleteExpiredWapPushMessage(intent);
            }
        }
    }

    private static void deleteExpiredWapPushMessage(Intent intent) {
        String uri = intent.getStringExtra("uri");
        if (TextUtils.isEmpty(uri)) {
            return;
        }
        final long messageId = ContentUris.parseId(Uri.parse(uri));
        final ContentResolver cr = Factory.get().getApplicationContext().getContentResolver();
        Log.d(TAG, "====wap push===deleteExpiredWapPushMessage=====currentTimeMillis: "
                + System.currentTimeMillis() + "    messageId: " + messageId + "   uri: " + uri);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Cursor cursor = null;
                String messageUri = null;
                try {
                    cursor = cr.query(Sms.CONTENT_URI,
                            new String[]{"_id"}, " _id = '" + messageId
                                    + "'" + " and wap_push = '1'", null, null);
                    if (cursor == null || cursor.getCount() == 0) {
                        return;
                    }
                    int sms_id = -1;
                    cursor.moveToFirst();
                    do {
                        sms_id = cursor.getInt(0);
                    } while (cursor.moveToNext());
                    messageUri = "content://sms/" + sms_id;
                    Log.d(TAG, "========wap push====deleteExpiredWapPushMessage uri: " + messageUri);
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                if (messageUri == null) {
                    return;
                }
                try {
                    final DatabaseWrapper dbWrapper = DataModel.get().getDatabase();
                    cursor = dbWrapper.query(DatabaseHelper.MESSAGES_TABLE,
                            new String[]{"_id", MessageColumns.READ, MessageColumns.CONVERSATION_ID}, "sms_message_uri = ?",
                            new String[]{messageUri}, null, null, null);

                    int message_id = -1;
                    String conversatinoid = "";
                    int read = 0;
                    if (cursor == null || cursor.getCount() == 0) {
                        return;
                    }
                    cursor.moveToFirst();
                    do {
                        message_id = cursor.getInt(0);
                        conversatinoid = String.valueOf(cursor
                                .getInt(2));
                        read = cursor.getInt(1);
                    } while (cursor.moveToNext());
                    Log.d(TAG, "message_id:" + message_id + " conversatinoid:" + conversatinoid + " read:" + read);
                    if (read == 0) {
                        BugleNotifications.cancel(
                                PendingIntentConstants.SMS_NOTIFICATION_ID,
                                conversatinoid);
                    }
                    DeleteMessageAction.deleteMessage(String.valueOf(message_id));
                    Log.d(TAG, "===wap push====deleteExpiredWapPushMessage messageId: " + message_id);
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }).start();
    }
}
