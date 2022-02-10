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

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import android.content.ContentValues;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.datamodel.DatabaseHelper;

/**
 * Action used to move alarm message.
 */
public class MoveAlarmMessageAction extends Action implements Parcelable {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;

    public static void MoveAlarmMessage(final long alarm) {
        final MoveAlarmMessageAction action = new MoveAlarmMessageAction(alarm);
        action.start();
    }

    private static final String KEY_ALARM_TIME = "alarm_time";

    private MoveAlarmMessageAction(final long alarm) {
        super();
        actionParameters.putLong(KEY_ALARM_TIME, alarm);
    }

    // Doing this work in the background so that we're not competing with sync
    // which could bring the deleted message back to life between the time we deleted
    // it locally and deleted it in telephony (sync is also done on doBackgroundWork).
    //
    // Previously this block of code deleted from telephony first but that can be very
    // slow (on the order of seconds) so this was modified to first delete locally, trigger
    // the UI update, then delete from telephony.
    @Override
    protected Bundle doBackgroundWork() {
        
        return null;
    }

    /**
     * Delete the message.
     */
    @Override
    protected Object executeAction() {
        final DatabaseWrapper db = DataModel.get().getDatabase();

        final long alarm = actionParameters.getLong(KEY_ALARM_TIME);

        if (alarm!=0) {
            final ContentValues values = new ContentValues();
            values.put(DatabaseHelper.MessageColumns.STATUS,
                    MessageData.BUGLE_STATUS_OUTGOING_YET_TO_SEND);
            BugleDatabaseOperations.updateAlarmMessageRow(db, alarm, values);
            MmsUtils.moveAlarmMessageToQueued(alarm);
            LogUtil.d(TAG, "MoveAlarmMessageAction: alarm: " + alarm);
            //MessagingContentProvider.notifyMessagesChanged(message.getConversationId());
            ProcessPendingMessagesAction.scheduleProcessPendingMessagesAction(false, this);
        }
        return null;
    }

    private MoveAlarmMessageAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<MoveAlarmMessageAction> CREATOR
            = new Parcelable.Creator<MoveAlarmMessageAction>() {
        @Override
        public MoveAlarmMessageAction createFromParcel(final Parcel in) {
            return new MoveAlarmMessageAction(in);
        }

        @Override
        public MoveAlarmMessageAction[] newArray(final int size) {
            return new MoveAlarmMessageAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
