/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.messaging.util;


import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.datamodel.SyncManager;
import android.content.ContentResolver;

import java.util.ArrayList;

public final class WhiteTestType {

    public static final String WHITE_BOX_TEST_KEY = "white_box_test";
    public static final String WHITE_BOX_TEST_VALUE = "white_box_test";
    public static final String TEST_TASK_KEY  = "test_task";

    public static final String TEST_DELETE  = "test_delete";

    public static final String TEST_SEND  = "test_send";

    public static final String TEST_SEARCH  = "test_search";

    public static final String TEST_INSERT_SMS  = "test_insert_sms";
    public static final String TEST_SIM_ENABLED  = "test_sim_enabled";

    public static final String TEST_SIMONE_ENABLED  = "test_simone_enabled";

    public static final String TEST_SIMTWO_ENABLED  = "test_simtwo_enabled";




    public static final String TEST_SEARCH_VALUE  = "test_search_value";

    public static final String TEST_SIM_NUMBER_KEY         = "test_sim_number";
    public static final String TEST_SIM_ONE         = "sim_1";
    public static final String TEST_SIM_TWO         = "sim_2";

    public static final String TEST_RECIPIENTS_KEY        = "test_pecipints";

    public static final String TEST_TEXT         = "test_text";
    public static final String TEST_IMAGE        = "test_image";
    public static final String TEST_VIDEO         = "test_video";
    public static final String TEST_AUDIO         = "test_audio";
    public static final String TEST_VCARD        = "test_vcard";
    public static final String TEST_VCALENDAR       = "test_vcalendar";

    public static final String TEST_ENCODETYPE         = "test_encodetype";
    public static final String TEST_SEVENBIT         = "7bit";
    public static final String TEST_UNICODE        = "unicode";
    public static final String TEST_ENCODE_DEFAULT        = "default";

    private static WhiteTestType whiteTestType = null;

    public final static String TAG = "Messaging::whiteboxtest";

    public static final String RESULT_CODE  = "resultCode";



    private static final Uri SMSMMS_SEARCH_CONTENT_URI = Uri.parse("content://mms-sms/sqlite_sequence");
    // This class should never be instantiated.
    private WhiteTestType() {
    }

    public static WhiteTestType getInstance(){
        if(whiteTestType == null){
            whiteTestType = new WhiteTestType();
        }
        return whiteTestType;
    }

    public void whiteTestStoreSms(Intent intent){
        String action = intent.getAction();
        Bundle bundle = intent.getExtras();
        long timestamp = System.currentTimeMillis();
        final Context context = Factory.get().getApplicationContext();
        ContentValues values = new ContentValues();
        Log.d(TAG,"----whiteTestStoreSms----");
        for(String key : bundle.keySet()){
            if(key.contains("number_")){
                String recipient = key.substring(key.indexOf("_")+1);
                timestamp = System.currentTimeMillis();

                values.put("address", recipient);
                values.put("body", bundle.getString(key));
                values.put("date", timestamp);
                values.put("read", 1);
                values.put("type", 1);
                values.put("seen",  1);
                values.put("service_center", "+8613010776500");
                values.put(Telephony.Sms.THREAD_ID,
                        Telephony.Threads.getOrCreateThreadId(context, recipient));
                context.getContentResolver().insert(Telephony.Sms.CONTENT_URI, values);
                //  WhiteTestType.getInstance().getStoreSmsTest(recipient,bundle.getString(key));
                values.clear();
            }
        }
         SyncManager.immediateSync();
        Log.d(TAG,"whitetest will sync temptable data");
        Log.d(TAG,"whitetest insert sms SUCCEEDED");
        Uri uri = Uri.parse("content://mms-sms/createTempTable");
        Cursor cursor=null;
        int whiteCount = 0;
        try{
            if (!OsUtil.hasSmsPermission() || !OsUtil.hasPhonePermission()){
                LogUtil.d(TAG,"not Phone SmsPermission :");
                return;
            }
            cursor= DataModel.get().getDatabase().getContext().getContentResolver()
                    .query(uri, null, null, null, null);
        }catch (Exception e){
            LogUtil.d(TAG,"Exception e:"+e);
        }finally {
            if (cursor != null) {//1179167  for Coverity code
                cursor.close();
            }
        }

    }

    public void sendTestBroadcase(String testType){
        Log.d(TAG,"======sendTestBroadcase======testType"+testType);
        final Context context = Factory.get().getApplicationContext();
        Intent intent =new Intent();
        intent.setAction("com.message.white.test.completed");
        if(TEST_SEND.equals(testType)){
            intent.putExtra(TEST_TASK_KEY,TEST_SEND);
        }else if(TEST_SEARCH.equals(testType)){
            intent.putExtra(TEST_TASK_KEY,TEST_SEARCH);
        }else if(TEST_INSERT_SMS.equals(testType)){
            intent.putExtra(TEST_TASK_KEY,TEST_INSERT_SMS);
        }else if(TEST_DELETE.equals(testType)){
            intent.putExtra(TEST_TASK_KEY,TEST_DELETE);
        }
        context.sendBroadcast(intent);
    }

    public void sendTestBroadcase(String testType,int resultCode){
        Log.d(TAG,"======sendTestBroadcase======testType"+testType+" resultCode="+resultCode);
        final Context context = Factory.get().getApplicationContext();
        Intent intent =new Intent();
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setAction("com.message.white.test.completed");
        if(TEST_SEND.equals(testType)){
            intent.putExtra(TEST_TASK_KEY,TEST_SEND);
        }else if(TEST_SEARCH.equals(testType)){
            intent.putExtra(TEST_TASK_KEY,TEST_SEARCH);
        }else if(TEST_INSERT_SMS.equals(testType)){
            intent.putExtra(TEST_TASK_KEY,TEST_INSERT_SMS);
        }
        intent.putExtra(RESULT_CODE,resultCode);
        context.sendBroadcast(intent);
    }

    public int getTestConversationMaxCount(){
        int conversationCnt= 0;
        Cursor cursor = null;
        try {
            final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
            cursor = resolver.query(SMSMMS_SEARCH_CONTENT_URI,
                    null ,
                    "select _id from threads",
                    null,
                    null,
                    null);
            if (cursor != null) {
                conversationCnt = cursor.getCount();
                LogUtil.i(TAG,"jessica add  : onActionBarDelete ,conversationCnt is " + conversationCnt);
                cursor.close();
                cursor = null;
            }
        } catch (Exception e) {
            LogUtil.i(TAG,"Exception in isBloacked() ,Exception is " + e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return conversationCnt;
    }

    public long getTestSmsMmsMaxCount(String szArg){
        long MaxCnt= 0;
        Cursor cursor = null;
        try {

            final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
            cursor = resolver.query(SMSMMS_SEARCH_CONTENT_URI,
                    null ,
                   /* "select seq from sqlite_sequence where name =? ",*//*Bug modified for 791793 start*/
                    "select max(_id) from " + szArg +";",
                    null,
                    //new String[] { szArg, },
                    null,
                    null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                   // MaxCnt = cursor.getInt(cursor.getColumnIndex("seq"));
                    MaxCnt = cursor.getInt(0);
                    LogUtil.i(TAG,"jessica add  : onActionBarDelete ,szArg is " + szArg);
                }
                cursor.close();
                cursor = null;
            }
        } catch (Exception e) {
            LogUtil.i(TAG,"Exception in isBloacked() ,Exception is " + e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return MaxCnt;
    }

}
