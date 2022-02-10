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

package com.android.messaging.datamodel.action;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Telephony.Sms;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.ParticipantRefresh;
import com.android.messaging.datamodel.SyncManager;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.sms.MmsSmsUtils;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PendingIntentConstants;
import com.android.messaging.util.UiUtils;
import com.android.sprd.telephony.RadioInteractor;
import com.sprd.messaging.simmessage.SimUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Action used to "receive" an incoming message
 */
public class ReceiveSmsMessageAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    private static final String KEY_MESSAGE_VALUES = "message_values";

    /**
     * Create a message received from a particular number in a particular conversation
     */
    public ReceiveSmsMessageAction(final ContentValues messageValues) {
        actionParameters.putParcelable(KEY_MESSAGE_VALUES, messageValues);
    }

    @Override
    protected Object executeAction() {
        final Context context = Factory.get().getApplicationContext();
        final ContentValues messageValues = actionParameters.getParcelable(KEY_MESSAGE_VALUES);
        final DatabaseWrapper db = DataModel.get().getDatabase();

        // Get the SIM subscription ID
        Integer subId = messageValues.getAsInteger(Sms.SUBSCRIPTION_ID);
        if (subId == null) {
            subId = ParticipantData.DEFAULT_SELF_SUB_ID;
        }
		//for bug694631 begin
        boolean isReplace = false;
        boolean hasReplaced = false;

        if(MmsConfig.get(subId).getIsSupportReplceSms()){
            if(messageValues.getAsBoolean(MessageData.PROTOCOL_REPLACE) != null){
                isReplace = messageValues.getAsBoolean(MessageData.PROTOCOL_REPLACE);
                LogUtil.d(TAG, "Is replace:"+isReplace);
                messageValues.remove(MessageData.PROTOCOL_REPLACE);
            }
        }
        //for bug694631 end
        // Make sure we have a sender address
        String address = messageValues.getAsString(Sms.ADDRESS);
        if (TextUtils.isEmpty(address)) {
            LogUtil.w(TAG, "Received an SMS without an address; using unknown sender.");
            address = ParticipantData.getUnknownSenderDestination();
            messageValues.put(Sms.ADDRESS, address);
        }
        final ParticipantData rawSender = ParticipantData.getFromRawPhoneBySimLocale(
                address, subId);

        // TODO: Should use local timestamp for this?
        final long received = messageValues.getAsLong(Sms.DATE);
        // Inform sync that message has been added at local received timestamp
        final SyncManager syncManager = DataModel.get().getSyncManager();
        syncManager.onNewMessageInserted(received);

        // Make sure we've got a thread id
        final long threadId = MmsSmsUtils.Threads.getOrCreateThreadId(context, address);
        messageValues.put(Sms.THREAD_ID, threadId);
        final boolean blocked = BugleDatabaseOperations.isBlockedDestination(
                db, rawSender.getNormalizedDestination());
        final String conversationId = BugleDatabaseOperations.
                getOrCreateConversationFromRecipient(db, threadId, blocked, rawSender);

        final boolean messageInFocusedConversation =
                DataModel.get().isFocusedConversation(conversationId);
        final boolean messageInObservableConversation =
                DataModel.get().isNewMessageObservable(conversationId);

        MessageData message = null;
        // Only the primary user gets to insert the message into the telephony db and into bugle's
        // db. The secondary user goes through this path, but skips doing the actual insert. It
        // goes through this path because it needs to compute messageInFocusedConversation in order
        // to calculate whether to skip the notification and play a soft sound if the user is
        // already in the conversation.
        if (!OsUtil.isSecondaryUser()) {
            final boolean read = messageValues.getAsBoolean(Sms.Inbox.READ)
                    || messageInFocusedConversation;
            // If you have read it you have seen it
            final boolean seen = read || messageInObservableConversation || blocked;
            messageValues.put(Sms.Inbox.READ, read ? Integer.valueOf(1) : Integer.valueOf(0));

            // incoming messages are marked as seen in the telephony db
            messageValues.put(Sms.Inbox.SEEN, seen);//by 856985 begin
            //for bug694631 begin
            Uri messageUri = null;
            if(MmsConfig.get(subId).getIsSupportReplceSms() && isReplace){
                messageUri = replaceMessage(context,messageValues);
                if(messageUri != null){
                    hasReplaced = true;
                }
            }
            if(messageUri == null){
            // Insert into telephony
                    messageUri = context.getContentResolver().insert(Sms.Inbox.CONTENT_URI,
                    messageValues);
            }//for bug694631 end

            if (messageUri != null) {
                //Modify for 733536 begin
                //Modify for 782000 begin
                Pattern pattern = Pattern.compile("inbox/+");
                Matcher matcher = pattern.matcher(messageUri.toString());
                String uriString = matcher.replaceAll("");
                saveToSimIfNeeded(context, subId, Uri.parse(uriString));
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "ReceiveSmsMessageAction: Inserted SMS message into telephony, "
                            + "uri = " + uriString);
                }
                //Modify for 782000 end
                //Modify for 733536 end
            } else {
                LogUtil.e(TAG, "ReceiveSmsMessageAction: Failed to insert SMS into telephony!");
            }

            final String text = messageValues.getAsString(Sms.BODY);
            final String subject = messageValues.getAsString(Sms.SUBJECT);
            final long sent = messageValues.getAsLong(Sms.DATE_SENT);
            final ParticipantData self = ParticipantData.getSelfParticipant(subId);
            final Integer pathPresent = messageValues.getAsInteger(Sms.REPLY_PATH_PRESENT);
            final String smsServiceCenter = messageValues.getAsString(Sms.SERVICE_CENTER);
            String conversationServiceCenter = null;
            // Only set service center if message REPLY_PATH_PRESENT = 1
            if (pathPresent != null && pathPresent == 1 && !TextUtils.isEmpty(smsServiceCenter)) {
                conversationServiceCenter = smsServiceCenter;
            }
            db.beginTransaction();
            try {
                final String participantId =
                        BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, rawSender);
                final String selfId =
                        BugleDatabaseOperations.getOrCreateParticipantInTransaction(db, self);

                message = MessageData.createReceivedSmsMessage(messageUri, conversationId,
                        participantId, selfId, text, subject, sent, received, seen, read);
                message.bindSubId(subId);
                //for bug694631 begin
                if(MmsConfig.get(subId).getIsSupportReplceSms() && hasReplaced){
                    MessageData oldMessage = BugleDatabaseOperations.readMessageData(db, messageUri);
                    if(oldMessage != null){
                        message.updateMessageId(oldMessage.getMessageId());
                    }
                    BugleDatabaseOperations.updateMessageInTransaction(db, message);
                }else{
                    BugleDatabaseOperations.insertNewMessageInTransaction(db, message);
                }//for bug694631 end

                BugleDatabaseOperations.updateConversationMetadataInTransaction(db, conversationId,
                        message.getMessageId(), message.getReceivedTimeStamp(), blocked,
                        conversationServiceCenter, !MmsConfig.get(subId).getUsingSimInSettingsEnabled() /* shouldAutoSwitchSelfId */);

                final ParticipantData sender = ParticipantData.getFromId(db, participantId);
                BugleActionToasts.onMessageReceived(conversationId, sender, message);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            LogUtil.i(TAG, "ReceiveSmsMessageAction: Received SMS message " + message.getMessageId()
                    + " in conversation " + message.getConversationId()
                    + ", uri = " + messageUri);

            ProcessPendingMessagesAction.scheduleProcessPendingMessagesAction(false, this);
        } else {
            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "ReceiveSmsMessageAction: Not inserting received SMS message for "
                        + "secondary user.");
            }
        }
        // Show a notification to let the user know a new message has arrived
        /* SPRD: modified for bug 499870 begin */
        BugleNotifications.update(false/*silent*/, conversationId, BugleNotifications.UPDATE_ALL, subId);
        /* SPRD: modified for bug 499870 end */

       if (MmsConfig.getCuccSdkEnabled()&& (message!=null)) {
           LogUtil.d(LogUtil.BUGLE_SMART_TAG, "cucc Received SMS  refresh Participant :" + rawSender);
           ParticipantRefresh.refreshParticipantAndUpdate(message.getParticipantId());
       }

        MessagingContentProvider.notifyMessagesChanged(conversationId);
        MessagingContentProvider.notifyPartsChanged();

        return message;
    }
	//for bug694631 begin
    // This must match the column IDs below.
    private final static String[] REPLACE_PROJECTION = new String[] {
        Sms._ID,
        Sms.ADDRESS,
        Sms.PROTOCOL
    };

    // This must match REPLACE_PROJECTION.
    private static final int REPLACE_COLUMN_ID = 0;

    private Uri replaceMessage(Context context, final ContentValues messageValues) {
        ContentResolver resolver = context.getContentResolver();

        String originatingAddress = messageValues.getAsString(Sms.ADDRESS);
        int protocolIdentifier = messageValues.getAsInteger(Sms.PROTOCOL);

        String selection =
                Sms.ADDRESS + " = ? AND " +
                Sms.PROTOCOL + " = ?";
        String[] selectionArgs = new String[] {
            originatingAddress, Integer.toString(protocolIdentifier)
        };

        Cursor cursor = context.getContentResolver().query(Sms.Inbox.CONTENT_URI,
                            REPLACE_PROJECTION, selection, selectionArgs, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    long messageId = cursor.getLong(REPLACE_COLUMN_ID);
                    Uri messageUri = ContentUris.withAppendedId(
                            Sms.CONTENT_URI, messageId);
                    context.getContentResolver().update(messageUri,
                                        messageValues, null, null);
                    return messageUri;
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }//for bug694631 end

    private ReceiveSmsMessageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<ReceiveSmsMessageAction> CREATOR
            = new Parcelable.Creator<ReceiveSmsMessageAction>() {
        @Override
        public ReceiveSmsMessageAction createFromParcel(final Parcel in) {
            return new ReceiveSmsMessageAction(in);
        }

        @Override
        public ReceiveSmsMessageAction[] newArray(final int size) {
            return new ReceiveSmsMessageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }

    private void saveToSimIfNeeded(Context context, int subId, Uri messageUri) {
        final Resources resources = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final boolean saveToSim = prefs.getBoolean(
                resources.getString(R.string.sms_save_to_sim_pref_key),
                resources.getBoolean(R.bool.sms_save_to_sim_pref_default));

        if (saveToSim) {
            if (getSimCapLeft(context, subId) <= 0) {
                postSimFullNotification();
                return;
            }
            List<String> smsUriList = new ArrayList<>();
            smsUriList.add(messageUri.toString());
            SimUtils.copySmsToSim(context, smsUriList, subId);
        }
        LogUtil.d(TAG, "saveToSim = " + saveToSim);
    }

    private int getSimCapLeft(Context context, int subId) {
        try{
            Log.d(TAG, "get sim capacity begin");
            RadioInteractor mRadioInteractor = new RadioInteractor(context);
            String capacity = mRadioInteractor
                    .getSimCapacity(MmsUtils.tanslateSubIdToPhoneId(context, subId));
            if(capacity!=null){
                String[] capList = capacity.split(":");
                if (capList !=null&&capList.length==2){
                    String  used = capList[0];
                    String  all = capList[1];
                    Log.d(TAG, "sim capacity: all is "+all+" used is "+used);
                    if (Integer.parseInt(used) >= Integer.parseInt(all)){
                        return 0;
                    } else {
                        return Integer.parseInt(all) - Integer.parseInt(used);
                    }
                }
            }
        }catch(Exception ex){
            Log.d(TAG, "sim capacity error");
        }
        Log.d(TAG, "get sim capacity end");
        return 0;
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
                .setAutoCancel(true); // Don't auto cancel

        final NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle(
                builder);
        bigTextStyle.bigText(resources.getString(R.string.sms_sim_full_text));
        final Notification notification = bigTextStyle.build();

        final NotificationManagerCompat notificationManager = NotificationManagerCompat
                .from(Factory.get().getApplicationContext());

        Notification.Builder b = Notification.Builder.recoverBuilder(
                Factory.get().getApplicationContext(),notification);
        b.setChannelId(PendingIntentConstants.MESSAGING_CHANNAL_ID);
        Notification nN = b.build();
        notificationManager.notify(PendingIntentConstants.SMS_SIM_FULL_NOTIFICATION_ID, nN);
    }
}
