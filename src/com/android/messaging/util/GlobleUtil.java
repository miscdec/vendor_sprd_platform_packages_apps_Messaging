package com.android.messaging.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.MessagePartData;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Set;

public class GlobleUtil {
    public static final String TAG = "GlobleUtil";
    /* Add by SPRD for bug 583225 2016.07.29 Start */
    public static int OP_MODE_UNDEFINE = -1;
    public static int OP_MODE_ADD_ATTACHMENT = 1;
    public static int OP_MODE_REPLACE_ATTACHMENT = 2;
    public static int mOpMode = OP_MODE_UNDEFINE;
    /* Add by SPRD for bug 583225 2016.07.29 End */
    public static boolean isSmilAttament = false;
    public static boolean isSmilAttamentAction = false;
    //add for jordan
    public static ConversationMessageData mConvMsgData = null;
    //add for jordan
    public static DraftMessageData mChangedDraftMessageData = null; // for save temp edited DraftmessagePartData

    public static MessagePartData mSmilmMessagePartData;

    // Add for bug 563344 Start
    public static HashMap<String, String> contentUriMap = new HashMap<String, String>();

    //sprd 572931 start
    private static InitObjectHandler mInitObjectHandler;
    //sprd 572931 end

    public static final int FDN_TOAST_MSG = 0x00000020;
    //sprd fix bug 562320 start
    public static final int SMIL_DRAFT_MSG = 0x00000021;

    public static final int INIT_DRAFT_MESSAGEPART = 0x00000120;
    // sprd fix bug 562320 start
    public static final int INIT_MESSAGEPART = 0x00000121;

    public static final String FILEPROVIDER_AUTHORITY = "unisoc.permission.FileProvider";

    public static HashMap<String, Handler> handlerSet = new HashMap<String, Handler>();

    public static Set<String> mStoreOrginPhoneNumbers = null; //bug 869111

    public static boolean mSharedToMessaging = false; //bug887450 set to true if share content to messaging

    public static final String TAG_LOADING_DIALOG = "TagLoadingDialog";
    public static final int MSG_CLOSE_LOADING_DIALOG = 20002;
    public static final int MSG_OPEN_LOADING_DIALOG = 20001;
    public static final int MMS_ERROR_FDN_CHECKED_LOADING_DIALOG = 20003;

    private static Object object = new Object();

    // sprd: 596495 fdn feature start
    public static int mGropMessagingTotalCount = 0;
    // sprd: 596495 fdn feature start

    private static WeakReference<DraftMessageData> weakRefForDraft;//bug 996083

    public static void registerHandler(String tag, Handler hanlder) {
        if (!handlerSet.containsKey(tag)) {
            handlerSet.put(tag, hanlder);
        }
    }

    public static void unRegisterHandler(String tag){
        if(handlerSet.containsKey(tag)){
            handlerSet.remove(tag);
        }
    }

    public static void sendMessage(String tag,int what){
        if(handlerSet.containsKey(tag)){
            handlerSet.get(tag).sendEmptyMessage(what);
        }
    }

    public static void sendMessageTakeBundle(String tag,Message msg){
        if(handlerSet.containsKey(tag)){
            handlerSet.get(tag).sendMessage(msg);
        }
    }

    public static Handler mGlobleHandler = new Handler(Factory.get().getApplicationContext().getMainLooper()){

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case FDN_TOAST_MSG:
                Toast.makeText(Factory.get().getApplicationContext(), (String) msg.obj, Toast.LENGTH_SHORT).show();
                break;
            }
            super.handleMessage(msg);
        }

    };

    //sprd 572931 start
    private static Handler getInitHandler() {
        if (mInitObjectHandler == null) {
            HandlerThread handlerThread = new HandlerThread("init_mDraftMessageData");
            handlerThread.start();
            mInitObjectHandler = new InitObjectHandler(
                    handlerThread.getLooper());
        }
        return mInitObjectHandler;
    }

    private static void sendEmptyMessaging(int messageFlag) {
        if (getInitHandler().hasMessages(messageFlag)) {
            getInitHandler().removeMessages(messageFlag);
        }
        getInitHandler().sendEmptyMessage(messageFlag);
    }


    static class InitObjectHandler extends Handler {

        InitObjectHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case INIT_MESSAGEPART:
                synchronized (object) {
                    try {
                        // FIXME: A better way may be do this in a new thread or
                        // save
                        // necessary class members in onSaveInstanceState.
                        Context context = Factory.get().getApplicationContext();
                        setConvMessageData(ConversationMessageData
			                                .queryConversationMessagesById(getConId(context),getMessageId(context)));
                        object.notify();
                    } catch (Exception e) {
                        object.notify();
                    }
                }
                break;

            case INIT_DRAFT_MESSAGEPART:
	                Log.i(TAG, "--getDraftMessageData()--- notify() before");
                synchronized (object) {
                    try {
                        setDraftMessageData (DraftMessageData
							.loadDraftMessageDataByConversationId(getConId(Factory.get().getApplicationContext())));
                        Log.i(TAG, "--getDraftMessageData()--- notify() in");
	                        object.notifyAll();
                        Log.i(TAG, "--getDraftMessageData()--- notify() after");
                    } catch (Exception e) {
	                        object.notifyAll();
                    }
                }
                break;
            }
            super.handleMessage(msg);
        }

    }
    //sprd 572931 end

    /* Add by SPRD for bug 561492 2016.05.19 Start */
    public static void saveConId(Context context,
            DraftMessageData draftMessageData) {
        SharedPreferences sp = context.getSharedPreferences("config",
                context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putString("conId", draftMessageData.getConversationId());
        editor.commit();
    }

    public static String getConId(Context context){
        SharedPreferences sp = context.getSharedPreferences("config", context.MODE_PRIVATE);
        return sp.getString("conId", "").toString();
    }
    /*Add by SPRD for bug 561492 2016.05.19 End*/

    // sprd 572931 start
    public static void setDraftMessageData(DraftMessageData draftData) {
        weakRefForDraft = new WeakReference<DraftMessageData>(draftData);//bug 996083
        if (draftData != null) {
            saveConId(Factory.get().getApplicationContext(), draftData);
        }
    }

    public static DraftMessageData getDraftMessageData() {
        DraftMessageData mDraftMessageData =  weakRefForDraft.get();//bug 996083

        if (mDraftMessageData == null){
            synchronized (object) {
                try {
                    sendEmptyMessaging(INIT_DRAFT_MESSAGEPART);
                    object.wait(200);
                } catch (Exception e) {
                    object.notifyAll();
                }
            }
        }

        try {
            if (mDraftMessageData == null) {
                Log.i(TAG,"--new DraftMessageData()--- ");
                mDraftMessageData = new DraftMessageData(getConId(Factory.get()
                        .getApplicationContext()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mDraftMessageData;
    }

    public static void setConvMessageData(ConversationMessageData convMsgData) {
        Context context = Factory.get().getApplicationContext();
        if (convMsgData != null) {
            saveConId(context, convMsgData.getConversationId());
            saveMessageId(context, convMsgData.getMessageId());
        }
        mConvMsgData = convMsgData;
    }

    public static ConversationMessageData getConvMessageData() {
        if (mConvMsgData == null) {
		 synchronized (object) {
            try {
                sendEmptyMessaging(INIT_MESSAGEPART);
                    object.wait();
            } catch (Exception e) {
                object.notify();
            }
         }
	    }
        return mConvMsgData;
    }
    //add for jordan

    public static void setSmilmDraftMessageData(MessagePartData data){
        mSmilmMessagePartData = data;
    }

    public static DraftMessageData getEditedDraftMessageDate(){
        return mChangedDraftMessageData;
    }

    public static void saveConId(Context context, String conversionId) {
        SharedPreferences sp = context.getSharedPreferences("config", context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putString("conId", conversionId);
        editor.commit();
    }

    public static void saveMessageId(Context context, String messageId) {
        SharedPreferences sp = context.getSharedPreferences("config", context.MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putString("k-msg-id", messageId);
        editor.commit();
    }

    public static String getMessageId(Context context) {
        SharedPreferences sp = context.getSharedPreferences("config", context.MODE_PRIVATE);
        return sp.getString("k-msg-id", "");
    }

    //Bug 1040316 begin
    private static boolean isFileUri(Uri sourceUri) {
        final String scheme = sourceUri.getScheme();
        if (scheme != null && scheme.equals(ContentResolver.SCHEME_FILE)) {
            return true;
        }
        return false;
    }

    public static Uri getFileProviderUri(final Context context, final Uri path) {
        Uri newUri = path;
        if (isFileUri(path)) {
            final File file = new File(path.getPath());
            newUri = FileProvider.getUriForFile(context, FILEPROVIDER_AUTHORITY, file);
            if (null == newUri) {
                Log.d(TAG, "transfer  <" + path + ">  to file provider uri error.");
            }
        }
        return newUri;
    }
    //Bug 1040316 end
}
