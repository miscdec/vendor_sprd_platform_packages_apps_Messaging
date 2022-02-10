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

package com.android.messaging.ui.conversation;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;

import androidx.appcompat.mms.MmsManager;
import android.telephony.TelephonyManagerEx;
import android.text.TextUtils;
import android.util.Log;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.action.DeleteConversationAction;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.LaunchConversationData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.FileUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.TextUtil;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.UriUtil;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.util.WhiteTestType;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import com.android.messaging.ui.conversationlist.ConversationListActivity;

/**
 * Launches ConversationActivity for sending a message to, or viewing messages from, a specific
 * recipient.
 * <p>
 * (This activity should be marked noHistory="true" in AndroidManifest.xml)
 */
public class LaunchConversationActivity extends Activity implements
        LaunchConversationData.LaunchConversationDataListener {
    private final static String TAG = "LaunchConversationActivity";
    static final String SMS_BODY = "sms_body";
    static final String ADDRESS = "address";
    final Binding<LaunchConversationData> mBinding = BindingBase.createBinding(this);
    String mSmsBody;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (ActivityManager.isUserAMonkey()) {
                Resources.Theme current = getTheme();
                current.applyStyle(R.style.MonkeyInvisible, true);
            }
        }catch (Exception ex){
            Log.d(TAG, "onCreate", ex.fillInStackTrace());
        }
        if (UiUtils.redirectToPermissionCheckIfNeeded(this)) {
            return;
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();
        /**************************White Box testing start**********************************/
        Bundle bundle = intent.getExtras();
        if(bundle != null){
            String testType = bundle.getString(WhiteTestType.WHITE_BOX_TEST_KEY);
            String testTask = bundle.getString(WhiteTestType.TEST_TASK_KEY);
            Log.d(TAG,"testType:"+testType+"  testTask:"+testTask);
            if(WhiteTestType.WHITE_BOX_TEST_VALUE.equals(testType)){
                Log.d(WhiteTestType.TAG,"testType:"+testType+"  testTask:"+testTask);
                if(WhiteTestType.TEST_SEND.equals(testTask)){
                    SafeAsyncTask.executeOnThreadPool(new SendTestIntentRunnable());
                }else if(WhiteTestType.TEST_SEARCH.equals(testTask)){
                    TestHandleSearch(intent);
                }else if(WhiteTestType.TEST_INSERT_SMS.equals(testTask)){
                    SafeAsyncTask.executeOnThreadPool(new HandleTestStoreSmsRunnable());
                }else if(WhiteTestType.TEST_SIM_ENABLED.equals(testTask)){
                    //setSimEnabled(intent);
                }else if(WhiteTestType.TEST_DELETE.equals(testTask)){
                    SafeAsyncTask.executeOnThreadPool(new HandleTestDeleteSmsRunnable());
                }else{
                    Log.d(WhiteTestType.TAG,"Unsupported conversation intent actiont");
                }
                finish();
                return;
            }
        }
        /**************************White Box testing end**********************************/
        if (Intent.ACTION_SENDTO.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            String[] recipients = UriUtil.parseRecipientsFromSmsMmsUri(intent.getData());
            final boolean haveAddress = !TextUtils.isEmpty(intent.getStringExtra(ADDRESS));
            final boolean haveEmail = !TextUtils.isEmpty(intent.getStringExtra(Intent.EXTRA_EMAIL));
            if (recipients == null && (haveAddress || haveEmail)) {
                if (haveAddress) {
                    recipients = new String[] { intent.getStringExtra(ADDRESS) };
                } else {
                    recipients = new String[] { intent.getStringExtra(Intent.EXTRA_EMAIL) };
                }
            }
            mSmsBody = intent.getStringExtra(SMS_BODY);
            if (TextUtils.isEmpty(mSmsBody)) {
                // Used by intents sent from the web YouTube (and perhaps others).
                mSmsBody = getBody(intent.getData());
                if (TextUtils.isEmpty(mSmsBody)) {
                    // If that fails, try yet another method apps use to share text
                    if (ContentType.TEXT_PLAIN.equals(intent.getType())) {
                        mSmsBody = intent.getStringExtra(Intent.EXTRA_TEXT);
                    }
                }
            }
            if (!TextUtils.isEmpty(mSmsBody)) {
                if (mSmsBody.length() > MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getMaxTextLimit()) {
                       UiUtils.showToastAtBottom(R.string.exceed_text_length_limitation);
                       finish();
                       return;
                }
            }
            if (recipients != null) {
                int recipientLimit = MmsConfig.get(MmsManager.DEFAULT_SUB_ID).getRecipientLimit();
                if (recipients.length > recipientLimit) {
                    UiUtils.showToastAtBottom(getString(R.string.exceed_participant_limit,
                            recipientLimit, recipients.length - recipientLimit));
                } else {
                    mBinding.bind(DataModel.get().createLaunchConversationData(this));
                    mBinding.getData().getOrCreateConversation(mBinding, recipients);
                }
            } else {
                // No recipients were specified in the intent.
                // Start a new conversation with contact picker. The new conversation will be
                // primed with the (optional) message in mSmsBody.
                onGetOrCreateNewConversation(null);
            }
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG, "Unsupported conversation intent action : " + action);
        }
        // As of M, activities without a visible window must finish before onResume completes.
        finish();
    }

    private String getBody(final Uri uri) {
        if (uri == null) {
            return null;
        }
        String urlStr = uri.getSchemeSpecificPart();
        if (!urlStr.contains("?")) {
            return null;
        }
        urlStr = urlStr.substring(urlStr.indexOf('?') + 1);
        final String[] params = urlStr.split("&");
        for (final String p : params) {
            if (p.startsWith("body=")) {
                try {
                    return URLDecoder.decode(p.substring(5), "UTF-8");
                } catch (final UnsupportedEncodingException e) {
                    // Invalid URL, ignore
                }
            }
        }
        return null;
    }

    @Override
    public void onGetOrCreateNewConversation(final String conversationId) {
        final Context context = Factory.get().getApplicationContext();
		 /**************************White Box testing start**********************************/
        if(WhiteTestType.WHITE_BOX_TEST_VALUE.equals(mIsNOWhiteTest)){
            UIIntents.get().launchConversationTestActivity(
                    this, conversationId, mDraftMessage,null,false,mBundle);
        }else{
            UIIntents.get().launchConversationActivityWithParentStack(context, conversationId,
                    mSmsBody);
        }
		 /**************************White Box testing end**********************************/
    }

    @Override
    public void onGetOrCreateNewConversationFailed() {
        UiUtils.showToastAtBottom(R.string.conversation_creation_failure);
    }
    /**************************White Box testing start**********************************/
    private MessageData mDraftMessage;

    Bundle mBundle = new Bundle();

    private String mIsNOWhiteTest = "CONVERSATION";
    private final static int WHITE_BOX_TEST_SEND = 100;
    private final static int WHITE_BOX_TEST_INSERTSMS = 200;
    private final static int WHITE_BOX_TEST_DELETESMS = 300;
    private static TelephonyManagerEx mTmEx;

    private Handler mTestHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Intent intent = (Intent)msg.obj;
            final String action = intent.getAction();
            mBundle = intent.getExtras();
            //getRecipientsTest();
            String testType = mBundle.getString(WhiteTestType.TEST_TASK_KEY);
            switch(msg.what){
                case WHITE_BOX_TEST_SEND:
                    HanderWhiteTestConversation(intent,action);
                break;
                case WHITE_BOX_TEST_INSERTSMS:
                    WhiteTestType.getInstance().sendTestBroadcase(testType);
                break;
                case WHITE_BOX_TEST_DELETESMS:
                    WhiteTestType.getInstance().sendTestBroadcase(testType);
                    break;
                default:
                    break;
            }

        }
    };

    private void HanderWhiteTestConversation(Intent intent,String action){
        if (Intent.ACTION_SENDTO.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            String[] recipients = UriUtil.parseRecipientsFromSmsMmsUri(intent.getData());
            final boolean haveAddress = !TextUtils.isEmpty(intent.getStringExtra(ADDRESS));
            final boolean haveEmail = !TextUtils.isEmpty(intent.getStringExtra(Intent.EXTRA_EMAIL));
            Log.d(WhiteTestType.TAG,"HanderWhiteTestConversation");
            if (recipients == null && (haveAddress || haveEmail)) {
                if (haveAddress) {
                    recipients = new String[] { intent.getStringExtra(ADDRESS) };
                } else {
                    recipients = new String[] { intent.getStringExtra(Intent.EXTRA_EMAIL) };
                }
            }

            if (recipients != null) {
                int recipientLimit = MmsConfig.get(MmsManager.DEFAULT_SUB_ID).getRecipientLimit();
                if (recipients.length > recipientLimit) {
                    LogUtil.w(LogUtil.BUGLE_TAG, "recipients is too many ");
                } else {
                    Log.d(WhiteTestType.TAG,"mBinding.getData().getOrCreateConversation");
                    mIsNOWhiteTest = mBundle.getString(WhiteTestType.WHITE_BOX_TEST_KEY);
                    mBinding.bind(DataModel.get().createLaunchConversationData(this));
                    mBinding.getData().getOrCreateConversation(mBinding, recipients);
                }
            }
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG, "Unsupported conversation intent action : " + action);
        }
        // As of M, activities without a visible window must finish before onResume completes.
      //  finish();
    }


    private class SendTestIntentRunnable implements Runnable {
        @Override
        public void run() {
            Intent intent = getIntent();
            String action = intent.getAction();
            Bundle bundle = intent.getExtras();
            mDraftMessage = null;
            sendMmsSmsTest(intent,bundle);
        }
    }

    private void sendMmsSmsTest(Intent intent,Bundle bundle){
        //ArrayMap<String, Object> map = bundle.
        String test_value = null;
        String test_key = null;
        String contentType = null;
        Uri uri = null;
        Uri mediaUri=Uri.parse("content://media/external/file");
        final Context context = Factory.get().getApplicationContext();
        Cursor cursor = null;

        mDraftMessage = MessageData.createSharedMessage(null);
        Log.d(WhiteTestType.TAG,"=====sendMmsSmsTest==start=");
        for(String key : bundle.keySet()){
            if(isValidkeyTest(key)){
                test_value = bundle.getString(key);

                if(WhiteTestType.TEST_TEXT.equals(key)){
                    contentType = ContentType.TEXT_PLAIN;
                    addSharedTextPartToDraftTest(test_value);
                    String encodeType = bundle.getString(WhiteTestType.TEST_ENCODETYPE);
                    if(WhiteTestType.TEST_SEVENBIT.equals(encodeType)){
                        MmsConfig.setEncodeType("1");
                    }else if(WhiteTestType.TEST_UNICODE.equals(encodeType)){
                        MmsConfig.setEncodeType("3");
                    }

                }else{
                    //String pathvalue = test_value.substring(test_value.indexOf("//")+2);
                    String[] pathvalues = TextUtil.replaceUnicodeDigits(test_value).replace(';', ',').split(",");
                    for(String data : pathvalues){
                        data = data.substring(data.indexOf("//")+2);
                        final String[] projection = {  MediaStore.Images.Media._ID, MediaStore.Images.Media.MIME_TYPE};
                        try {
                            cursor = context.getContentResolver().query(mediaUri, projection,
                                    MediaStore.Images.Media.DATA+ "=?", new String[] { data }, null);
                            if (cursor != null && cursor.moveToFirst()) {
                                contentType = cursor.getString(1);
                                uri=Uri.withAppendedPath(mediaUri, "" +cursor.getString(0));
                            }

                        } catch (Exception ex){

                        }finally {
                            if (cursor != null)
                                cursor.close();
                        }

                        if(WhiteTestType.TEST_VCARD.equals(key)){
                            contentType = ContentType.TEXT_VCARD;
                        }
                        if(WhiteTestType.TEST_VCALENDAR.equals(key)){
                            contentType = ContentType.TEXT_VCALENDAR;
                        }
                        Log.d(WhiteTestType.TAG,"contentType:"+contentType+" uri:"+uri+ " data:"+data);
                        if(contentType != null &&ContentType.canSharedContentType(contentType)&&
                                uri != null){
                            addSharedPartToDraftTest(contentType,uri);
                        }
                    }
                }
            }

        }
        Log.d(WhiteTestType.TAG,"=====sendMmsSmsTest===mDraftMessage.getPart().size()="+mDraftMessage.getPart().size());
        if(mDraftMessage.getPart().size()>0){
            Message msg = mTestHandler.obtainMessage();
            msg.obj = intent;
            msg.what = WHITE_BOX_TEST_SEND;
            mTestHandler.sendMessageDelayed(msg,0);
        }else{
            mDraftMessage = null;
          //  finish();
        }

    }

    private void addSharedTextPartToDraftTest(final String messageText) {
        if (!TextUtils.isEmpty(messageText)) {
            mDraftMessage.addPart(MessagePartData.createTextMessagePart(messageText));
        }
    }

    private boolean isValidkeyTest(String value) {
        Log.d(WhiteTestType.TAG,"isValidkeyTest send tyep:"+value);
        return (WhiteTestType.TEST_TEXT.equals(value)||WhiteTestType.TEST_AUDIO.equals(value)||
                WhiteTestType.TEST_IMAGE.equals(value)||WhiteTestType.TEST_VCALENDAR.equals(value)||
                WhiteTestType.TEST_VCARD.equals(value)||WhiteTestType.TEST_VIDEO.equals(value));
    }

    private void addSharedPartToDraftTest(final String contentType, final Uri uri) {
        if (FileUtil.isInPrivateDir(uri)) {
            Assert.fail("Cannot send private file " + uri.toString());
        } else {
            mDraftMessage.addPart(PendingAttachmentData.createPendingAttachmentData(contentType,
                    uri));
        }
    }

    private void TestHandleSearch(Intent intent){
        intent.setClass(LaunchConversationActivity.this, ConversationListActivity.class);
        //intent.putExtra(SearchManager.QUERY, "qwer");
        startActivity(intent);
    }


    private class HandleTestStoreSmsRunnable implements Runnable {
        @Override
        public void run() {
            Intent intent = getIntent();
            WhiteTestType.getInstance().whiteTestStoreSms(intent);
            Message msg = mTestHandler.obtainMessage();
            msg.obj = getIntent();
            msg.what = WHITE_BOX_TEST_INSERTSMS;
            mTestHandler.sendMessageDelayed(msg,200);
        }
    }

//    private void setSimEnabled(Intent intent){
//        Bundle bundle = intent.getExtras();
//        final Context context = Factory.get().getApplicationContext();
//        mTmEx = TelephonyManagerEx.from(context);
//        for(String key : bundle.keySet()){
//            String test_value = bundle.getString(key);
//            int iphoneid =0;
//            boolean onoff =false;
//            if(isValidSimKey(key)){
//                onoff = "true".equals(test_value) ? true : false;
//                if(WhiteTestType.TEST_SIMONE_ENABLED.equals(key)){
//                    iphoneid = 0;
//                }else{
//                    iphoneid = 1;
//                }
//                Log.d(WhiteTestType.TAG,"iphoneid:"+iphoneid+" onoff:"+onoff);
//                try{
//                    if (mTmEx.isSimEnabled(iphoneid) != onoff) {
//                        mTmEx.setSimEnabled(iphoneid, onoff);
//                    }
//                }catch (Exception ex){
//                    ex.printStackTrace();
//                }
//
//
//            }
//
//        }
//    }

    private boolean isValidSimKey(String value){
        return (WhiteTestType.TEST_SIMONE_ENABLED.equals(value) || WhiteTestType.TEST_SIMTWO_ENABLED.equals(value));
    }

    private class HandleTestDeleteSmsRunnable implements Runnable {
        @Override
        public void run() {
           /* Intent intent = getIntent();
            WhiteTestType.getInstance().whiteTestStoreSms(intent);
            Message msg = mTestHandler.obtainMessage();
            msg.obj = getIntent();
            msg.what = WHITE_BOX_TEST_INSERTSMS;
            mTestHandler.sendMessageDelayed(msg,200);*/
            Log.d(WhiteTestType.TAG,"HandleTestDeleteSmsRunnable");
            int conversationCnt = WhiteTestType.getInstance().getTestConversationMaxCount();
            String szSms = "sms";
            String szMms = "pdu";
            long smsMaxCnt = WhiteTestType.getInstance().getTestSmsMmsMaxCount(szSms);
            long mmsMaxCnt = WhiteTestType.getInstance().getTestSmsMmsMaxCount(szMms);
            String[] szConversations = new String[conversationCnt];
            szConversations = new String[] {""};
            DeleteConversationAction.deleteConversation(
                    szConversations,
                    0,
                    smsMaxCnt,
                    mmsMaxCnt);

            Message msg = mTestHandler.obtainMessage();
            msg.obj = getIntent();
            msg.what = WHITE_BOX_TEST_DELETESMS;
            mTestHandler.sendMessageDelayed(msg,200);
        }
    }

    /**************************White Box testing end**********************************/
}
