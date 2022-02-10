//by sprd
package com.sprd.messaging.simmessage.data;

import android.content.Context;
import android.database.Cursor;

import com.android.messaging.util.Dates;

public class SortMsgListItemData {
    private int mMessageId;
    private int mConversationId;
    private int mSenderId;
    private long mReceivedTimestamp;
    private int mMessageStatus;
    private boolean mIsRead;
    private String mSmsMessageUri;
    private int mRawStatus;
    private String mSelfId;
    private String mParticipantName;
    private int mSubId;
    private int mSlotId;
    private String mSubscriptionName;
    private int mSubscriptionColor;
    private String mBobyText;
    private String mContentType;
    private String mDisplayDestination;
    private String mFullName;
    private String mFirstName;

    public void bind(final Cursor cursor) {
        mMessageId = cursor.getInt(SortMsgDataCollector.MESSAGE_ID);
        mConversationId = cursor.getInt(SortMsgDataCollector.CONVERSATION_ID);
        mSenderId = cursor.getInt(SortMsgDataCollector.SENDER_ID);
        mReceivedTimestamp = cursor.getLong(SortMsgDataCollector.RECEIVED_TIMESTAMP_INDEX);
        mMessageStatus = cursor.getInt(SortMsgDataCollector.MESSAGE_STATUS);
        mIsRead = (cursor.getInt(SortMsgDataCollector.READ) != 0);
        mSmsMessageUri = cursor.getString(SortMsgDataCollector.SMS_MESSAGE_URI);
        mRawStatus = cursor.getInt(SortMsgDataCollector.RAW_STATUS);
        mSelfId = cursor.getString(SortMsgDataCollector.SELF_ID);
        mParticipantName = cursor.getString(SortMsgDataCollector.NAME);
        mSubId = cursor.getInt(SortMsgDataCollector.SUB_ID);
        mSlotId = cursor.getInt(SortMsgDataCollector.SIM_SLOT_ID);
        mSubscriptionColor = cursor.getInt(SortMsgDataCollector.SUBSCRIPTION_COLOR);
        mSubscriptionName = cursor.getString(SortMsgDataCollector.SUBSCRIPTION_NAME);
        mBobyText = cursor.getString(SortMsgDataCollector.TEXT);
        mContentType = cursor.getString(SortMsgDataCollector.CONTENT_TYPE);
        mDisplayDestination = cursor.getString(SortMsgDataCollector.DISPLAY_DESTINATION_INDEX);
        mFullName = cursor.getString(SortMsgDataCollector.FULL_NAME);
        mFirstName = cursor.getString(SortMsgDataCollector.FULL_NAME);
    }

    public int getMessageId() {
        return mMessageId;
    }

    public int getConversationId() {
        return mConversationId;
    }

    public int getSenderId() {
        return mSenderId;
    }

    public long getReceivedTimestamp() {
        return mReceivedTimestamp;
    }

    public int getMessageStatus() {
        return mMessageStatus;
    }

    public boolean getIsRead() {
        return mIsRead;
    }

    public String getSmsMessageUri() {
        return mSmsMessageUri;
    }

    public int getRawStatus() {
        return mRawStatus;
    }

    public String getSelfId() {
        return mSelfId;
    }

    public String getParticipantName() {
        return mParticipantName;
    }

    public int getSubId() {
        return mSubId;
    }

    public int getSlotId() {
        return mSlotId;
    }

    public int getDisplaySlotId() {
        return getSlotId() + 1;
    }

    public String getDisplayDestination() {
        return mDisplayDestination;
    }

    public String getFullName() {
        return mFullName;
    }

    public String getFirstName() {
        return mFirstName;
    }

    public int getSubscriptionColor() {
        // Force the alpha channel to 0xff to ensure the returned color is solid.
        return mSubscriptionColor | 0xff000000;
    }

    public String getSubscriptionName() {
        return mSubscriptionName;
    }

    public boolean isActiveSubscription() {
        return mSlotId != SortMsgDataCollector.INVALID_SLOT_ID;
    }

    public String getBobyText() {
        return mBobyText;
    }

    public String getContentType() {
        return mContentType;
    }

    public boolean getIsDrft() {
        return (mMessageStatus == SortMsgDataCollector.BUGLE_STATUS_OUTGOING_DRAFT);
    }

    public final boolean getIsSendRequested() {
        return (mMessageStatus == SortMsgDataCollector.BUGLE_STATUS_OUTGOING_YET_TO_SEND
                || mMessageStatus == SortMsgDataCollector.BUGLE_STATUS_OUTGOING_AWAITING_RETRY
                || mMessageStatus == SortMsgDataCollector.BUGLE_STATUS_OUTGOING_SENDING || mMessageStatus == SortMsgDataCollector.BUGLE_STATUS_OUTGOING_RESENDING);
    }

    public String getFormattedTimestamp(Context context) {
        return Dates.getMessageTimeString(mReceivedTimestamp).toString();
    }
}
