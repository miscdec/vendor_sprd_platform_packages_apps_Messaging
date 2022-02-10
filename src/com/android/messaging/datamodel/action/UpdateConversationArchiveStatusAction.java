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

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.util.Assert;
import java.util.ArrayList;
import android.os.SystemClock;
import android.util.Log;

public class UpdateConversationArchiveStatusAction extends Action {

    public static void archiveConversation(final String conversationId) {
        final UpdateConversationArchiveStatusAction action =
                new UpdateConversationArchiveStatusAction(conversationId, true /* isArchive */);
        action.start();
    }
    public static void archiveSprdConversation(final ArrayList<String> value) {
        final UpdateConversationArchiveStatusAction action =
                new UpdateConversationArchiveStatusAction(value, true /* isArchive */);
        action.start();
    }
    public static void unarchiveSprdConversation(final ArrayList<String> value) {
        final UpdateConversationArchiveStatusAction action =
                new UpdateConversationArchiveStatusAction(value, false /* isArchive */);
        action.start();
    }

    public static void unarchiveConversation(final String conversationId) {
        final UpdateConversationArchiveStatusAction action =
                new UpdateConversationArchiveStatusAction(conversationId, false /* isArchive */);
        action.start();
    }

    private static final String KEY_CONVERSATION_ID = "conversation_id";
    private static final String KEY_CONVERSATIONS_ID = "conversation_idS";
    private static final String KEY_IS_ARCHIVE = "is_archive";

    protected UpdateConversationArchiveStatusAction(
            final String conversationId, final boolean isArchive) {
        Assert.isTrue(!TextUtils.isEmpty(conversationId));
        actionParameters.putString(KEY_CONVERSATION_ID, conversationId);
        actionParameters.putBoolean(KEY_IS_ARCHIVE, isArchive);
    }

    protected UpdateConversationArchiveStatusAction(
            ArrayList<String> conversationIds, final boolean isArchive) {
        actionParameters.putStringArrayList(KEY_CONVERSATIONS_ID, conversationIds);
        actionParameters.putBoolean(KEY_IS_ARCHIVE, isArchive);
    }
    @Override
    protected Object executeAction() {
       /* final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final boolean isArchived = actionParameters.getBoolean(KEY_IS_ARCHIVE);

        final DatabaseWrapper db = DataModel.get().getDatabase();
        db.beginTransaction();
        try {
            BugleDatabaseOperations.updateConversationArchiveStatusInTransaction(
                    db, conversationId, isArchived);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        MessagingContentProvider.notifyConversationListChanged();
        MessagingContentProvider.notifyConversationMetadataChanged(conversationId);*/

        final String conversationId = actionParameters.getString(KEY_CONVERSATION_ID);
        final ArrayList<String> conversationIds=actionParameters.getStringArrayList(KEY_CONVERSATIONS_ID);
        final boolean isArchived = actionParameters.getBoolean(KEY_IS_ARCHIVE);
        if(conversationId!=null){
            final DatabaseWrapper db = DataModel.get().getDatabase();
            db.beginTransaction();
            try {
                BugleDatabaseOperations.updateConversationArchiveStatusInTransaction(
                        db, conversationId, isArchived);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            MessagingContentProvider.notifyConversationListChanged();
            MessagingContentProvider.notifyConversationMetadataChanged(conversationId);
        }else if(conversationIds!=null){
            final DatabaseWrapper db = DataModel.get().getDatabase();
            db.beginTransaction();
            try {
                for (final String conversation_Id : conversationIds){
                    BugleDatabaseOperations.updateConversationArchiveStatusInTransaction(
                            db, conversation_Id, isArchived);

                    // MessagingContentProvider.notifyConversationMetadataChanged(conversationId);
                }
                db.setTransactionSuccessful();
            }finally {
                db.endTransaction();
            }
            MessagingContentProvider.notifyConversationListChanged();
        }
        return null;
    }

    protected UpdateConversationArchiveStatusAction(final Parcel in) {
        super(in);
    }

    public static final Parcelable.Creator<UpdateConversationArchiveStatusAction> CREATOR
            = new Parcelable.Creator<UpdateConversationArchiveStatusAction>() {
        @Override
        public UpdateConversationArchiveStatusAction createFromParcel(final Parcel in) {
            return new UpdateConversationArchiveStatusAction(in);
        }

        @Override
        public UpdateConversationArchiveStatusAction[] newArray(final int size) {
            return new UpdateConversationArchiveStatusAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }
}
