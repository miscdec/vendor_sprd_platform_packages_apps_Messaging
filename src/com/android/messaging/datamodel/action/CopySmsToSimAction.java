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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.SmsManager;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.sprd.telephony.RadioInteractor;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class CopySmsToSimAction extends Action implements Parcelable {
    private static final String TAG = "CopySmsToSimAction";
    public static final Uri ICC_URI = Uri.parse("content://sms/icc");
    private static final Context mContext = Factory.get().getApplicationContext();

    public interface CopySmsToSimActionListener{
        @Assert.RunsOnMainThread
        void onCopySmsToSimFull();
    }

    public static void copySmsToSim(final List<String> smsUriList, int subId,
                                    CopySmsToSimActionListener listener) {
        final CopySmsToSimActionMonitor monitor = new CopySmsToSimActionMonitor(listener);
        final CopySmsToSimAction action = new CopySmsToSimAction(smsUriList, subId, monitor.getActionKey());
        action.start(monitor);
    }

//    private static final String KEY_MESSAGE_ID = "message_id";
    private static final String KEY_SMS_RUI_LIST = "sms_uri_list";
    private static final String KEY_SUB_ID = "sub_id";

    private CopySmsToSimAction(List<String> smsUriList, int subId, String actionKey) {
        super(actionKey);
//        actionParameters.putString(KEY_SMS_RUI, messageId);
        actionParameters.putInt(KEY_SUB_ID, subId);
        actionParameters.putStringArrayList(KEY_SMS_RUI_LIST, (ArrayList<String>)smsUriList);
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

    private int getSimCapLeft(int subId) {
        try{
            Log.d(TAG, "get sim capacity begin");
            RadioInteractor mRadioInteractor = new RadioInteractor(mContext);
            String capacity = mRadioInteractor
                              .getSimCapacity(MmsUtils.tanslateSubIdToPhoneId(mContext, subId));
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
     * Delete the message.
     */
    @Override
    protected Object executeAction() {
//        requestBackgroundWork();
        //        final String smsUri = actionParameters.getString(KEY_SMS_RUI);
        final int subId = actionParameters.getInt(KEY_SUB_ID);
        final List<String> smsUriList = actionParameters.getStringArrayList(KEY_SMS_RUI_LIST);
        int countLeft = getSimCapLeft(subId);
        int selectedCount = smsUriList.size();
        if (countLeft <= 0 || selectedCount > countLeft) {
            Log.d(TAG, "size is ok smsUriList = " + smsUriList + " countLeft = " + countLeft + " selectedCount = " + selectedCount);
            return null;
        }
        final DatabaseWrapper db = DataModel.get().getDatabase();
        String smsTextQuery = "SELECT " + DatabaseHelper.PartColumns.TEXT + " FROM " + DatabaseHelper.PARTS_TABLE
                + " WHERE " + DatabaseHelper.PartColumns.MESSAGE_ID
                + "=(SELECT " + DatabaseHelper.MessageColumns._ID + " FROM " + DatabaseHelper.MESSAGES_TABLE
                + " WHERE " + DatabaseHelper.MessageColumns.SMS_MESSAGE_URI + "=?)";
        SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
        String smsText = "";
        for (String smsUri : smsUriList) {
            Cursor cursor = null;
            final String[] queryArgs = {smsUri};
            try {
                cursor = db.rawQuery(smsTextQuery, queryArgs);

                if (cursor != null && cursor.moveToFirst()) {
                    Assert.isTrue(cursor.getCount() == 1);
                    smsText = cursor.getString(0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.close();
            }
            selectedCount += smsManager.divideMessage(smsText).size() - 1;
            Log.d(TAG, "Need divide smsUriList = " + smsUriList + " countLeft = " + countLeft
                    + " selectedCount = " + selectedCount);
            if (selectedCount > countLeft) {
                return null;
            }
        }
//        LogUtil.d(TAG, "CopySmsToSimAction " + " subId:[" + subId + "]");
        MmsUtils.copySmsMessageToSim(smsUriList, subId);
        //Uri simSmsUri = ICC_URI.buildUpon().appendPath(String.valueOf(indexOnIcc)).build();
        return "success";
    }

    private CopySmsToSimAction(final Parcel in) {
        super(in);
    }

    public static final Creator<CopySmsToSimAction> CREATOR
            = new Creator<CopySmsToSimAction>() {
        @Override
        public CopySmsToSimAction createFromParcel(final Parcel in) {
            return new CopySmsToSimAction(in);
        }

        @Override
        public CopySmsToSimAction[] newArray(final int size) {
            return new CopySmsToSimAction[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel parcel, final int flags) {
        writeActionToParcel(parcel, flags);
    }

    /**
     * A monitor that notifies a listener upon completion
     */
    public static class CopySmsToSimActionMonitor extends ActionMonitor
            implements ActionMonitor.ActionCompletedListener {
        private final CopySmsToSimActionListener mListener;

        CopySmsToSimActionMonitor(final CopySmsToSimActionListener listener) {
            super(STATE_CREATED, generateUniqueActionKey("CopySmsToSimAction"), "CopySmsToSim");
            setCompletedListener(this);
            mListener = listener;
        }

        @Override
        public void onActionSucceeded(final ActionMonitor monitor,
                                      final Action action, final Object data, final Object result) {
            if (result == null) {
                mListener.onCopySmsToSimFull();
            }
        }

        @Override
        public void onActionFailed(final ActionMonitor monitor,
                                   final Action action, final Object data, final Object result) {
            // TODO: Currently onActionFailed is only called if there is an error in
            // processing requests, not for errors in the local processing.
            Assert.fail("Unreachable");
            mListener.onCopySmsToSimFull();
        }
    }

}
