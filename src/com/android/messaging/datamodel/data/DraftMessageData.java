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

package com.android.messaging.datamodel.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import androidx.appcompat.mms.CarrierConfigValuesLoader;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MessageTextStats;
import com.android.messaging.datamodel.action.ReadDraftDataAction;
import com.android.messaging.datamodel.action.ReadDraftDataAction.ReadDraftDataActionListener;
import com.android.messaging.datamodel.action.ReadDraftDataAction.ReadDraftDataActionMonitor;
import com.android.messaging.datamodel.action.WriteDraftMessageAction;
import com.android.messaging.datamodel.binding.BindableData;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.sms.MmsSmsUtils;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.DoesNotRunOnMainThread;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.SprdLogUtil;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.WhiteTestType;
import com.sprd.messaging.drm.MessagingDrmSession;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/* @} */
public class DraftMessageData extends BindableData implements ReadDraftDataActionListener {

    /**
     * Interface for DraftMessageData listeners
     */
    public interface DraftMessageDataListener {
        @RunsOnMainThread
        void onDraftChanged(DraftMessageData data, int changeFlags);

        @RunsOnMainThread
        void onDraftAttachmentLimitReached(DraftMessageData data);

        @RunsOnMainThread
        void onDraftAttachmentLoadFailed();

        @RunsOnMainThread
        void onDraftAttachmentOverSizeReached(DraftMessageData data,
                final MessagePartData attachment);
        // Add for bug 563344
        void onDraftLoadDone(DraftMessageData data);
    }

    /**
     * Interface for providing subscription-related data to DraftMessageData
     */
    public interface DraftMessageSubscriptionDataProvider {
        int getConversationSelfSubId();
    }

    // Flags sent to onDraftChanged to help the receiver limit the amount of work done
    public static int ATTACHMENTS_CHANGED  =     0x0001;
    public static int MESSAGE_TEXT_CHANGED =     0x0002;
    public static int MESSAGE_SUBJECT_CHANGED =  0x0004;
    // Whether the self participant data has been loaded
    public static int SELF_CHANGED =             0x0008;
    public static int MESSAGE_ALARM_CHANGED =   0x0010;//for bug686558
    public static int ALL_CHANGED =              0x00FF;
    // ALL_CHANGED intentionally doesn't include WIDGET_CHANGED. ConversationFragment needs to
    // be notified if the draft it is looking at is changed externally (by a desktop widget) so it
    // can reload the draft.
    public static int WIDGET_CHANGED  =          0x0100;

    private final String mConversationId;
    private ReadDraftDataActionMonitor mMonitor;
    private final DraftMessageDataEventDispatcher mListeners;
    private DraftMessageSubscriptionDataProvider mSubscriptionDataProvider;

    private boolean mIncludeEmailAddress;
    private boolean mIsGroupConversation;
    private String mMessageText;
    private String mMessageSubject;
    private String mSelfId;
    private MessageTextStats mMessageTextStats;
    private boolean mSending;
    public long attachmentsFileSize;
    private String TAG = "DraftMessageData";

    private Date mAlarmDate=null;

    /* Add by SPRD for bug 563344 Start */
    public int mProtocol = MessageData.PROTOCOL_UNKNOWN;
    public String mSmsMessageUri = null;
    /* Add by SPRD for bug 563344 End */
    /** Keeps track of completed attachments in the message draft. This data is persisted to db */
    private final List<MessagePartData> mAttachments;

    /** A read-only wrapper on mAttachments surfaced to the UI layer for rendering */
    private final List<MessagePartData> mReadOnlyAttachments;

    /** Keeps track of pending attachments that are being loaded. The pending attachments are
     * transient, because they are not persisted to the database and are dropped once we go
     * to the background (after the UI calls saveToStorage) */
    private final List<PendingAttachmentData> mPendingAttachments;

    /** A read-only wrapper on mPendingAttachments surfaced to the UI layer for rendering */
    private final List<PendingAttachmentData> mReadOnlyPendingAttachments;

    /** Is the current draft a cached copy of what's been saved to the database. If so, we
     * may skip loading from database if we are still bound */
    public boolean mIsDraftCachedCopy;//changed to public from private , smil

    /** Whether we are currently asynchronously validating the draft before sending. */
    private CheckDraftForSendTask mCheckDraftForSendTask;

    /* SPRD: modified for bug 503091 begin */
    /* The limited size(10M) of the Attachment the message can append */
    private static final int ATTACHMENT_EXCEEDED_SIZE = 10*1024*1024;
    /* SPRD: modified for bug 503091 end */
    private boolean misSamePicture = false;
    private int mParticipantCount;

    public int getParticipantCount() {
        return mParticipantCount;
    }

    public DraftMessageData(final String conversationId) {
        mConversationId = conversationId;
        mAttachments = new ArrayList<MessagePartData>();
        mReadOnlyAttachments = Collections.unmodifiableList(mAttachments);
        mPendingAttachments = new ArrayList<PendingAttachmentData>();
        mReadOnlyPendingAttachments = Collections.unmodifiableList(mPendingAttachments);
        mListeners = new DraftMessageDataEventDispatcher();
        mMessageTextStats = new MessageTextStats();
        attachmentsFileSize = 0;
        mAlarmDate=null;
    }

    public void addListener(final DraftMessageDataListener listener) {
        mListeners.add(listener);
    }

    public void setSubscriptionDataProvider(final DraftMessageSubscriptionDataProvider provider) {
        mSubscriptionDataProvider = provider;
    }

    private void updateFromMessageData(final MessageData message, final String bindingId) {
        Log.d(TAG,"updateFromMessageData");
        // New attachments have arrived - only update if the user hasn't already edited
        Assert.notNull(bindingId);
        // The draft is now synced with actual MessageData and no longer a cached copy.
        mIsDraftCachedCopy = false;
        // Do not use the loaded draft if the user began composing a message before the draft loaded
        // During config changes (orientation), the text fields preserve their data, so allow them
        // to be the same and still consider the draft unchanged by the user
        boolean isShow = true;
        final Context context = Factory.get().getApplicationContext();
        mProtocol = MessageData.PROTOCOL_UNKNOWN;
        mSmsMessageUri = null;

        //  if (mPendingAttachments.isEmpty() && mAttachments.isEmpty() && (isDraftEmpty() || (TextUtils.equals(mMessageText, message.getMessageText()) && TextUtils.equals(mMessageSubject, message.getMmsSubje
        //                                       && mAttachments.isEmpty()))) {/*modified for Bug 808441 */
        if (mPendingAttachments.isEmpty() && mAttachments.isEmpty() ){//*modified for Bug 1164501 */
           //Modify by 762372 begin
           if(message.getAlarm()==0){
               setMessageAlarm(null,false);
           }else{
               setMessageAlarm(new Date(message.getAlarm()),false);
           }
           //Modify by 762372 end
            attachmentsFileSize = 0;
           final String draftText = message.getMessageText();
           if(!TextUtils.isEmpty(draftText)){//bug 1173682
               setMessageText(draftText);
           }
           final String draftSubject = message.getMmsSubject();
           if(!TextUtils.isEmpty(draftSubject)){//bug 1173682
               setMessageSubject(draftSubject);
           }
           Log.d(TAG, "message.getPart().size:" + message.getPart().size());
           LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "updateFromMessageData 1 alarm time"+message.getAlarm());
           if(message.getIsMms()) {
               //Bug971177 begin
               final String signatureText = mMessageTextStats.getSignatrueText();
               attachmentsFileSize += signatureText.getBytes().length;
               //Bug971177 end
           }
           for (final MessagePartData part : message.getParts()) {
               if (part.isAttachment() && getAttachmentCount() >= getAttachmentLimit()) {
                   dispatchAttachmentLimitReached();
                   break;
               }
               if (part instanceof PendingAttachmentData) {
                   Log.d(TAG,"part instanceof PendingAttachmentData");
                   // This is a pending attachment data from share intent (e.g. an shared image
                   // that we need to persist locally).
                   final PendingAttachmentData data = (PendingAttachmentData) part;
                   attachmentsFileSize += part.getmAttachmetSize();
                   boolean isAttachemtExceeded = attachmentsFileSize >= MmsConfig.getMaxMaxMessageSize() * 0.99;
                   Assert.equals(PendingAttachmentData.STATE_PENDING, data.getCurrentState());
                   if(isAttachemtExceeded){
                       if(isShow){
                           mListeners.onDraftAttachmentOverSizeReached(this, data);
                       }
                       attachmentsFileSize -= part.getmAttachmetSize();
                       isShow=false;
                       data.destroyAsync();
                   }else{
                       addOnePendingAttachmentNoNotify(data, bindingId);
                   }
               } else if (part.isAttachment()) {
                   double attachmentFileSize = part.getmAttachmetSize();
                   attachmentsFileSize += attachmentFileSize;
                   Log.d(TAG, "part.isAttachment()-mixSize=" + attachmentsFileSize + " "
                           +"attachmentFileSize=" + attachmentFileSize);
                   if(attachmentsFileSize >= MmsConfig.getMaxMaxMessageSize() * 0.99){
                       mListeners.onDraftAttachmentOverSizeReached(this, part);
                       attachmentsFileSize -= attachmentFileSize;
                   } else {
                       addOneAttachmentNoNotify(part);
                   }
               }
           }
           if(attachmentsFileSize > 0 && getIsMms()) {
               long attachmentsFileSizetemp=attachmentsFileSize*1000/1024;//add for bug 766638
               String formatSize = Formatter.formatFileSize(context, attachmentsFileSizetemp);
               UiUtils.showToastAtBottom(context.getString(R.string.mms_size) + formatSize.toUpperCase());
           }
           //Modify by 762372 begin
           if(0 == mPendingAttachments.size()) {
               /**************************White Box testing start**********************************/
               if(mWhiteTest != null && WhiteTestType.WHITE_BOX_TEST_KEY.equals(mWhiteTest.getDraftWhiteTest())){
                   mWhiteTest.sendMessageWhiteTest();
               }
               /**************************White Box testing end**********************************/
               mListeners.onDraftLoadDone(this);
           }
           //Modify by 762372 end
           LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "DraftMessageData dispatchChanged  ALL_CHANGED");
           dispatchChanged(ALL_CHANGED);
        } else {
           // The user has started a new message so we throw out the draft message data if there
           // is one but we also loaded the self metadata and need to let our listeners know.
           //Modify by 762372 begin
           if(message.getAlarm()==0){
               setMessageAlarm(null,false);
           }else{
               setMessageAlarm(new Date(message.getAlarm()),false);
           }
           //Modify by 762372 end
           /* SPRD: modified for bug 512976 begin */
           LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "DraftMessageData dispatchChanged  ALL_CHANGED 324");

           /* SPRD: modified for bug 512976 begin */
           /* Add by SPRD for bug 563344 Start */
           //Modify by 762372 begin
           if(0 == mPendingAttachments.size()) {
               mListeners.onDraftLoadDone(this);
           }
           dispatchChanged(ALL_CHANGED);
       }
        //Modify by 762372 end
        /* Add by SPRD for bug 563344 End */
    }

    /**
     * Create a MessageData object containing a copy of all the parts in this DraftMessageData.
     *
     * @param clearLocalCopy whether we should clear out the in-memory copy of the parts. If we
     *        are simply pausing/resuming and not sending the message, then we can keep
     * @return the MessageData for the draft, null if self id is not set
     */
    public MessageData createMessageWithCurrentAttachments(final boolean clearLocalCopy) {
        MessageData message = null;
        if (getIsMms()) {

            message = MessageData.createDraftMmsMessage(mConversationId, mSelfId,
                mMessageText, mMessageSubject,
                mProtocol /*Add for bug 563344*/ );
            for (final MessagePartData attachment : mAttachments) {
                message.addPart(attachment);
                //for bug710962 begin
                if(attachment != null && ContentType.APP_SMIL.equals(attachment.getContentType())){
                    message.setSmilProtocol();
                }
                //for bug710962 end
            }
        } else {
                message = MessageData.createDraftSmsMessage(mConversationId, mSelfId,
                    mMessageText);
        }
        if(mAlarmDate != null){
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "createMessageWithCurrentAttachments set  alarm."+mAlarmDate.getTime());
            message.setAlarm(mAlarmDate.getTime());
        }
        if (clearLocalCopy) {
            // The message now owns all the attachments and the text...
            clearLocalDraftCopy();
            dispatchChanged(ALL_CHANGED);
        } else {
            // The draft message becomes a cached copy for UI.
            mIsDraftCachedCopy = true;
        }

        return message;
    }

    /* Add by SPRD for bug 576767 Start */
    public void clearLocalDraftBeforeSending() {
        clearLocalDraftCopy();
        dispatchChanged(ALL_CHANGED);
    }
    /* Add by SPRD for bug 576767 End */

    private void clearLocalDraftCopy() {
        mIsDraftCachedCopy = false;
        mAttachments.clear();
        setMessageText("");
        setMessageSubject("");
		//613227
        setMessageAlarm(null);
    }

    public String getConversationId() {
        return mConversationId;
    }

    public String getMessageText() {
        return mMessageText;
    }

    public String getMessageSubject() {
        return mMessageSubject;
    }

    //613227
    public Date getAlarmDate(){
        return mAlarmDate;
    }

    public boolean getIsMms() {
        final int selfSubId = getSelfSubId();
        return MmsSmsUtils.getRequireMmsForEmailAddress(mIncludeEmailAddress, selfSubId) ||
                (mIsGroupConversation && MmsUtils.groupMmsEnabled(selfSubId)) ||
                mMessageTextStats.getMessageLengthRequiresMms() || !mAttachments.isEmpty() ||
                !TextUtils.isEmpty(mMessageSubject);
    }

    public boolean getIsGroupMmsConversation() {
        return getIsMms() && mIsGroupConversation;
    }

    public String getSelfId() {
        return mSelfId;
    }

    public int getNumMessagesToBeSent() {
        return mMessageTextStats.getNumMessagesToBeSent();
    }

    public int getCodePointsRemainingInCurrentMessage() {
        return mMessageTextStats.getCodePointsRemainingInCurrentMessage();
    }

    public int getSelfSubId() {
        return mSubscriptionDataProvider == null ? ParticipantData.DEFAULT_SELF_SUB_ID :
                mSubscriptionDataProvider.getConversationSelfSubId();
    }

    private void setMessageText(final String messageText, final boolean notify) {
        //Bug971177 begin
        attachmentsFileSize += ((null != messageText ? messageText.getBytes().length : 0) - (null != mMessageText ? mMessageText.getBytes().length : 0));
        if (attachmentsFileSize < 0) {
            attachmentsFileSize = 0;
        }
        //Bug971177 end
        mMessageText = messageText;
        mMessageTextStats.updateMessageTextStats(getSelfSubId(), mMessageText);
        if (notify) {
            dispatchChanged(MESSAGE_TEXT_CHANGED);
        }
    }

    private void setMessageSubject(final String subject, final boolean notify) {
        //Bug971177 begin
        attachmentsFileSize += ((null != subject ? subject.getBytes().length : 0) - (null != mMessageSubject ? mMessageSubject.getBytes().length : 0));
        if (attachmentsFileSize < 0) {
            attachmentsFileSize = 0;
        }
        //Bug971177 end
        mMessageSubject = subject;
        if (notify) {
            dispatchChanged(MESSAGE_SUBJECT_CHANGED);
        }
    }
    //613227
    private void setMessageAlarm(final Date alarm, final boolean notify) {

        if(alarm!=null){
           // LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "DraftMessageDate setMessageAlarm set  alarm:"+alarm.getTime());
        }else{
          //  LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "DraftMessageDate setMessageAlarm cancel  alarm.");
        }
        mAlarmDate = alarm;
        if (notify) {
            dispatchChanged(MESSAGE_ALARM_CHANGED);
        }
    }

    public void setMessageText(final String messageText) {
        setMessageText(messageText, false);
    }
    public void setMessageSubject(final String subject) {
        setMessageSubject(subject, false);
    }
    //613227
    public void setMessageAlarm(final Date alarm) {
        setMessageAlarm(alarm, false);
    }

    public void addAttachments(final Collection<? extends MessagePartData> attachments) {
        Log.d(TAG, "addAttachments, attachments.size:" + attachments.size());
        Log.d(TAG, "attachmentsFileSize:" + attachmentsFileSize);
        if(mAttachments.size() == 0 && mPendingAttachments.size() == 0)
            attachmentsFileSize = 0;
        // If the incoming attachments contains a single-only attachment, we need to clear
        // the existing attachments.
        for (final MessagePartData data : attachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the attachment we're adding can only
                // exist by itself.
                destroyAttachments();
                break;
            }
        }
        // If the existing attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }
        // If any of the pending attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mPendingAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }

        boolean reachedLimit = false;
        boolean oversize;
        for (final MessagePartData data : attachments) {
            // Don't break out of loop even if limit has been reached so we can destroy all
            // of the over-limit attachments.
            oversize = isAttachemtExceeded(data);
            attachmentsFileSize += data.getmAttachmetSize();
            /* Add by sprd for bug 599503 Start */
            Log.d(TAG, "DraftMessageData ----> addAttachment data.getContentUri() = "+data.getContentUri() + "***attachmentsFileSize: " +attachmentsFileSize);
            if (attachmentsFileSize > 0 && containsAttachment(data.getContentUri())) {
                attachmentsFileSize -= data.getmAttachmetSize();
                final Context context = Factory.get().getApplicationContext();
                UiUtils.showToastAtBottom(context.getString(R.string.can_not_add_same_picture));
                continue;
            }
            /* Add by sprd for bug 599503 End */
            if (oversize || attachmentsFileSize >= MmsConfig.getMaxMaxMessageSize() * 0.99) {
                mListeners.onDraftAttachmentOverSizeReached(this, data);
                attachmentsFileSize -= data.getmAttachmetSize();
            } else {
                reachedLimit |= addOneAttachmentNoNotify(data);
                if (reachedLimit) {
                    dispatchAttachmentLimitReached();
                    attachmentsFileSize -= data.getmAttachmetSize();
                }
            }
        }
        dispatchChanged(ATTACHMENTS_CHANGED);
    }

    public boolean containsAttachment(final Uri contentUri) {
        for (final MessagePartData existingAttachment : mAttachments) {
            if(existingAttachment.getContentUri() != null && contentUri != null){
               if (existingAttachment.getContentUri().equals(contentUri)) {
                   Log.d("DraftMessageData","==containsAttachment========1=====");
                   misSamePicture = true;
                   return true;
               }
            }
            //bug817886 begin
            try{
                if(existingAttachment.isDrmType()){
                    String path = MessagingDrmSession.get().getPath(contentUri);
                    String existingPath = MessagingDrmSession.get().getPath(existingAttachment.getContentUri());
                    if(existingPath.equals(path)){
                        Log.d(TAG," existing same drm attachment "+path);
                        misSamePicture = true;
                        return true;
                    }
                }
            }catch (Exception ex){

            }
            //bug817886 end
        }

        for (final PendingAttachmentData pendingAttachment : mPendingAttachments) {
            if (pendingAttachment.getContentUri().equals(contentUri)) {
                Log.d("DraftMessageData","==containsAttachment=======2=====");
                return true;
            }
            //bug817886 begin
            try{
                if(pendingAttachment.isDrmType()){
                    String path = MessagingDrmSession.get().getPath(contentUri);
                    String pendingPath = MessagingDrmSession.get().getPath(pendingAttachment.getContentUri());
                    if(pendingPath.equals(path)){
                        Log.d(TAG," pending same drm attachment "+path);
                        return true;
                    }
                }
            }catch (Exception ex){

            }
            //bug817886 end
        }
        misSamePicture = false;
        return false;
    }

    /**
     * Try to add one attachment to the attachment list, while guarding against duplicates and
     * going over the limit.
     * @return true if the attachment limit was reached, false otherwise
     */
    private boolean addOneAttachmentNoNotify(final MessagePartData attachment) {
        Assert.isTrue(attachment.isAttachment());
        Log.d(TAG, "addOneAttachmentNoNotify");
        final boolean reachedLimit = getAttachmentCount() >= getAttachmentLimit();
        if (reachedLimit || containsAttachment(attachment.getContentUri())) {
            // Never go over the limit. Never add duplicated attachments.
            //attachment.destroyAsync(); //bug507556
            return reachedLimit;
        } else {
            addAttachment(attachment, null /*pendingAttachment*/);
            return false;
        }
    }

    private void addAttachment(final MessagePartData attachment,
            final PendingAttachmentData pendingAttachment) {
        if (attachment != null && attachment.isSinglePartOnly()) {
            // clear any existing attachments because the attachment we're adding can only
            // exist by itself.
            destroyAttachments();
        }
        if (pendingAttachment != null && pendingAttachment.isSinglePartOnly()) {
            // clear any existing attachments because the attachment we're adding can only
            // exist by itself.
            destroyAttachments();
        }
        // If the existing attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }
        // If any of the pending attachments contain a single-only attachment, we need to clear the
        // existing attachments to make room for the incoming attachment.
        for (final MessagePartData data : mPendingAttachments) {
            if (data.isSinglePartOnly()) {
                // clear any existing attachments because the single attachment can only exist
                // by itself
                destroyAttachments();
                break;
            }
        }
        // Modify for bug 712159 revert to original
        if (attachment != null) {
            mAttachments.add(attachment);
        } else if (pendingAttachment != null) {
            mPendingAttachments.add(pendingAttachment);
        }
    }

    public void addPendingAttachment(final PendingAttachmentData pendingAttachment,
            final BindingBase<DraftMessageData> binding) {
        Log.d(TAG, "addPendingAttachment");
        if(mAttachments.size() == 0 && mPendingAttachments.size() == 0)
            attachmentsFileSize = 0;
        boolean reachedLimit = false;
        boolean oversize = false;
        oversize = isAttachemtExceeded(pendingAttachment);
        attachmentsFileSize += pendingAttachment.getmAttachmetSize();
         //-- Add by SPRD for bug 618360  Start
         //Log.d(TAG,"addPendingAttachment pendingAttachment.getmAttachmetSize():"+pendingAttachment.getmAttachmetSize());
         if(containsAttachment(pendingAttachment.getContentUri())){
             final Context context = Factory.get().getApplicationContext();
             attachmentsFileSize -= pendingAttachment.getmAttachmetSize();
             pendingAttachment.destroyAsync();
             UiUtils.showToastAtBottom(context.getString(R.string.can_not_add_same_attachment));
         } else if (oversize || attachmentsFileSize >= MmsConfig.getMaxMaxMessageSize() * 0.99) {
             mListeners
                     .onDraftAttachmentOverSizeReached(this, pendingAttachment);
             attachmentsFileSize -= pendingAttachment.getmAttachmetSize();
         } else {
             reachedLimit = addOnePendingAttachmentNoNotify(pendingAttachment,
                     binding.getBindingId());
         }
         //-- Add by SPRD for bug 618360  end --

        if (reachedLimit) {
            dispatchAttachmentLimitReached();
            attachmentsFileSize -= pendingAttachment.getmAttachmetSize();
        }
        dispatchChanged(ATTACHMENTS_CHANGED);
    }

    /**
     * Try to add one pending attachment, while guarding against duplicates and
     * going over the limit.
     * @return true if the attachment limit was reached, false otherwise
     */
    private boolean addOnePendingAttachmentNoNotify(final PendingAttachmentData pendingAttachment,
            final String bindingId) {
        Log.d(TAG, "addOnePendingAttachmentNoNotify");
        final boolean reachedLimit = getAttachmentCount() >= getAttachmentLimit();
        if(pendingAttachment.getContentUri() == null){
              return false;
        }
        if (reachedLimit || containsAttachment(pendingAttachment.getContentUri())) {
            // Never go over the limit. Never add duplicated attachments.
            pendingAttachment.destroyAsync();
            return reachedLimit;
        } else {
            Assert.isTrue(!mPendingAttachments.contains(pendingAttachment));
            Assert.equals(PendingAttachmentData.STATE_PENDING, pendingAttachment.getCurrentState());
            addAttachment(null /*attachment*/, pendingAttachment);
            pendingAttachment.loadAttachmentForDraft(this, bindingId);
            return false;
        }
    }

    public void setSelfId(final String selfId, final boolean notify) {
        LogUtil.d(LogUtil.BUGLE_TAG, "DraftMessageData: set selfId=" + selfId
                + " for conversationId=" + mConversationId);
        mSelfId = selfId;
        if (notify) {
            dispatchChanged(SELF_CHANGED);
        }
    }

    public boolean hasAttachments() {
        return !mAttachments.isEmpty();
    }

    public boolean hasPendingAttachments() {
        return !mPendingAttachments.isEmpty();
    }

    private int getAttachmentCount() {
        /* Add by SPRD for bug 563344 Start */
        int count = 0;
        if (MessageData.PROTOCOL_MMS_SMIL == mProtocol) {
            for (MessagePartData p : mAttachments) {
                if(p.isAttachment()) {
                    count++;
                }
            }
            return count + mPendingAttachments.size();
        } else {
        /* Add by SPRD for bug 563344 End */
            return mAttachments.size() + mPendingAttachments.size();
        }
    }
    public List<MessagePartData> getAttachments() {
        return mAttachments;
    }
    private int getVideoAttachmentCount() {
        int count = 0;
        for (MessagePartData part : mAttachments) {
            if (part.isVideo()) {
                count++;
            }
        }
        for (MessagePartData part : mPendingAttachments) {
            if (part.isVideo()) {
                count++;
            }
        }
        return count;
    }

    private int getAttachmentLimit() {
        // Add for bug 576557
        if (MessageData.PROTOCOL_MMS_SMIL != mProtocol) {
        return BugleGservices.get().getInt(
                BugleGservicesKeys.MMS_ATTACHMENT_LIMIT,
                BugleGservicesKeys.MMS_ATTACHMENT_LIMIT_DEFAULT);
        /* Add by sprd for bug 576557 Start */
        } else {
            return BugleGservices.get().getInt(
                    BugleGservicesKeys.MMS_ATTACHMENT_LIMIT,
                    BugleGservicesKeys.MMS_SMIL_ATTACHMENT_LIMIT_DEFAULT);
        }
        /* Add by sprd for bug 576557 End */
    }

    public void removeAttachment(final MessagePartData attachment) {
        for (final MessagePartData existingAttachment : mAttachments) {
            if(existingAttachment.getContentUri() != null && attachment.getContentUri() != null){
                if (existingAttachment.getContentUri().equals(attachment.getContentUri())) {
                    mAttachments.remove(existingAttachment);
                    existingAttachment.destroyAsync();
                    attachmentsFileSize -= existingAttachment.getmAttachmetSize();
                    dispatchChanged(ATTACHMENTS_CHANGED);
                    break;
                }
            }else{
                if(existingAttachment.getText() != null){
                    if (existingAttachment.getText().equals(attachment.getText())) {
                        mAttachments.remove(existingAttachment);
                        existingAttachment.destroyAsync();
                        attachmentsFileSize -= existingAttachment.getmAttachmetSize();
                        dispatchChanged(ATTACHMENTS_CHANGED);
                        break;
                    }
                }
            }
            //Smil edit ans showing end
        }
    }

    public void removeExistingAttachments(final Set<MessagePartData> attachmentsToRemove) {
        boolean removed = false;
        final Iterator<MessagePartData> iterator = mAttachments.iterator();
        while (iterator.hasNext()) {
            final MessagePartData existingAttachment = iterator.next();
            if (attachmentsToRemove.contains(existingAttachment)) {
                iterator.remove();
                existingAttachment.destroyAsync();
                attachmentsFileSize = 0;
                removed = true;
            }
        }
        if (removed) {
            dispatchChanged(ATTACHMENTS_CHANGED);
        }
    }

    public void removePendingAttachment(final PendingAttachmentData pendingAttachment) {
        for (final PendingAttachmentData existingAttachment : mPendingAttachments) {
           if(existingAttachment.getContentUri() != null){
             if (existingAttachment.getContentUri().equals(pendingAttachment.getContentUri())) {
                 mPendingAttachments.remove(pendingAttachment);
                 pendingAttachment.destroyAsync();
                 dispatchChanged(ATTACHMENTS_CHANGED);
                 break;
             }
           }
        }
    }

    public void updatePendingAttachment(final MessagePartData updatedAttachment,
            final PendingAttachmentData pendingAttachment) {
        Log.d(TAG, "updatePendingAttachment");
        for (final PendingAttachmentData existingAttachment : mPendingAttachments) {
            if (existingAttachment.getContentUri() != null && existingAttachment.getContentUri().equals(pendingAttachment.getContentUri())) {
                mPendingAttachments.remove(pendingAttachment);
                if (pendingAttachment.isSinglePartOnly()) {
                    updatedAttachment.setSinglePartOnly(true);
                }

                if(updatedAttachment.getmAttachmetSize() <= 0) {
                    long size=MmsUtils.getMediaFileSize(updatedAttachment.getContentUri());//UriUtil.getContentSize(updatedAttachment.getContentUri());
                    updatedAttachment.setmAttachmetSize(size);
                }
                mAttachments.add(updatedAttachment);

                if(attachmentsFileSize > 0) {
                    final Context context = Factory.get().getApplicationContext();
                    String formatSize = Formatter.formatFileSize(context, attachmentsFileSize);
                    //UiUtils.showToastAtBottom(context.getString(R.string.attachment_size) + formatSize);
                }
                /* Add by SPRD for bug 563344 Start */
                if(0 == mPendingAttachments.size()) {
                    /**************************White Box testing start**********************************/
                    if(mWhiteTest != null && WhiteTestType.WHITE_BOX_TEST_KEY.equals(mWhiteTest.getDraftWhiteTest())){
                        mWhiteTest.sendMessageWhiteTest();
                    }
                    /**************************White Box testing end**********************************/
                    mListeners.onDraftLoadDone(this);
                }
                /* Add by SPRD for bug 563344 End */
                dispatchChanged(ATTACHMENTS_CHANGED);
                return;
            }
        }

        // If we are here, this means the pending attachment has been dropped before the task
        // to load it was completed. In this case destroy the temporarily staged file since it
        // is no longer needed.
        /* Add by SPRD for bug 563344 Start */
        if(0 == mPendingAttachments.size()) {
            mListeners.onDraftLoadDone(this);
        }
        /* Add by SPRD for bug 563344 End */
        updatedAttachment.destroyAsync();
    }

    /**
     * Remove the attachments from the draft and notify any listeners.
     * @param flags typically this will be ATTACHMENTS_CHANGED. When attachments are cleared in a
     * widget, flags will also contain WIDGET_CHANGED.
     */
    public void clearAttachments(final int flags) {
        attachmentsFileSize = 0;
        //Bug 981027 begin
        if(getIsMms()) {
            attachmentsFileSize += null != mMessageText ? mMessageText.getBytes().length : 0;
            attachmentsFileSize += null != mMessageSubject ? mMessageSubject.getBytes().length : 0;
            final String signatureText = mMessageTextStats.getSignatrueText();
            attachmentsFileSize += signatureText.getBytes().length;
        }
        //Bug 981027 end
        destroyAttachments();
        dispatchChanged(flags);
    }

    public List<MessagePartData> getReadOnlyAttachments() {
        return mReadOnlyAttachments;
    }

    public List<PendingAttachmentData> getReadOnlyPendingAttachments() {
        return mReadOnlyPendingAttachments;
    }

    public boolean loadFromStorage(final BindingBase<DraftMessageData> binding,
            final MessageData optionalIncomingDraft, boolean clearLocalDraft) {
        LogUtil.d(LogUtil.BUGLE_TAG, "DraftMessageData: "
                + (optionalIncomingDraft == null ? "loading" : "setting")
                + " for conversationId=" + mConversationId);
        if (clearLocalDraft) {
            clearLocalDraftCopy();
        }
        final boolean isDraftCachedCopy = mIsDraftCachedCopy;
        mIsDraftCachedCopy = false;
        // Before reading message from db ensure the caller is bound to us (and knows the id)
        if (mMonitor == null && !isDraftCachedCopy && isBound(binding.getBindingId())) {
            if (optionalIncomingDraft != null && MessageData.shouldShowLoadingDialog(optionalIncomingDraft)) {
                showWaitingDialog();
            }
            mMonitor = ReadDraftDataAction.readDraftData(mConversationId,
                    optionalIncomingDraft, binding.getBindingId(), this);
            return true;
        }
        return false;
    }

    /**
     * Saves the current draft to db. This will save the draft and drop any pending attachments
     * we have. The UI typically goes into the background when this is called, and instead of
     * trying to persist the state of the pending attachments (the app may be killed, the activity
     * may be destroyed), we simply drop the pending attachments for consistency.
     */
    public void saveToStorage(final BindingBase<DraftMessageData> binding) {
        //903493 begin
        if (mMonitor != null){
                Log.d(TAG, "saveToStorage , if mMonitor is not null , it means that now is loading from storage , no need to save");
        }else{
                saveToStorageInternal(binding);
        }
        //903493 end
        /* SPRD: modified for bug 503072 begin */
        //dropPendingAttachments();
        /* SPRD: modified for bug 503072 end */
    }

    private void saveToStorageInternal(final BindingBase<DraftMessageData> binding) {
        // Create MessageData to store to db, but don't clear the in-memory copy so UI will
        // continue to display it.
        // If self id is null then we'll not attempt to change the conversation's self id.
        final MessageData message = createMessageWithCurrentAttachments(false /* clearLocalCopy */);
        // Before writing message to db ensure the caller is bound to us (and knows the id)
        if (isBound(binding.getBindingId())){
            WriteDraftMessageAction.writeDraftMessage(mConversationId, message);
        }
    }

    /*Add by SPRD for bug581044  2016.07.14 Start*/
    public  MessageData getMessageData(){
        return createMessageWithCurrentAttachments(false);
    }
    /*Add by SPRD for bug581044  2016.07.14 End*/

    /**
     * Called when we are ready to send the message. This will assemble/return the MessageData for
     * sending and clear the local draft data, both from memory and from DB. This will also bind
     * the message data with a self Id through which the message will be sent.
     *
     * @param binding the binding object from our consumer. We need to make sure we are still bound
     *        to that binding before saving to storage.
     */
    public MessageData prepareMessageForSending(final BindingBase<DraftMessageData> binding) {
        // We can't send the message while there's still stuff pending.
        Assert.isTrue(!hasPendingAttachments());
        mSending = true;
        // Assembles the message to send and empty working draft data.
        // If self id is null then message is sent with conversation's self id.
        Log.d(TAG, "prepareMessageForSending, attachmentsFileSize:" + attachmentsFileSize);
        final MessageData messageToSend =
                createMessageWithCurrentAttachments(true /* clearLocalCopy */);
        // Note sending message will empty the draft data in DB.
        mSending = false;
        attachmentsFileSize = 0;
        return messageToSend;
    }

    public boolean isSending() {
        return mSending;
    }

    public void setSending(boolean sending) {
        mSending = sending;
    }

    @Override // ReadDraftMessageActionListener.onReadDraftMessageSucceeded
    public void onReadDraftDataSucceeded(final ReadDraftDataAction action, final Object data,
            final MessageData message, final ConversationListItemData conversation) {
        final String bindingId = (String) data;

        // Before passing draft message on to ui ensure the data is bound to the same bindingid
        if (isBound(bindingId)) {
            mSelfId = message.getSelfId();
            mIsGroupConversation = conversation.getIsGroup();
            mParticipantCount = conversation.getParticipantCount();
            mIncludeEmailAddress = conversation.getIncludeEmailAddress();
            updateFromMessageData(message, bindingId);
            LogUtil.d(LogUtil.BUGLE_TAG, "DraftMessageData: draft loaded. "
                    + "conversationId=" + mConversationId + " selfId=" + mSelfId);
        } else {
            LogUtil.w(LogUtil.BUGLE_TAG, "DraftMessageData: draft loaded but not bound. "
                    + "conversationId=" + mConversationId);
        }
        closeWaitingDialog();
        mMonitor = null;
    }

    @Override // ReadDraftMessageActionListener.onReadDraftDataFailed
    public void onReadDraftDataFailed(final ReadDraftDataAction action, final Object data) {
        LogUtil.w(LogUtil.BUGLE_TAG, "DraftMessageData: draft not loaded. "
                + "conversationId=" + mConversationId);
        // The draft is now synced with actual MessageData and no longer a cached copy.
        mIsDraftCachedCopy = false;
        // Just clear the monitor - no update to draft data
        mMonitor = null;
        closeWaitingDialog();
    }

    private Boolean showing = false;
    private void showWaitingDialog() {
        synchronized (this) {
            if (showing) {
                return;
            }
            showing = true;
            GlobleUtil.sendMessage(GlobleUtil.TAG_LOADING_DIALOG, GlobleUtil.MSG_OPEN_LOADING_DIALOG);
        }
    }

    private void closeWaitingDialog() {
        synchronized (this) {
            if (!showing) {
                return;
            }
            showing = false;
            GlobleUtil.sendMessage(GlobleUtil.TAG_LOADING_DIALOG, GlobleUtil.MSG_CLOSE_LOADING_DIALOG);
        }
    }
    /**
     * Check if Bugle is default sms app
     * @return
     */
    public boolean getIsDefaultSmsApp() {
        return PhoneUtils.getDefault().isDefaultSmsApp();
    }

    @Override //BindableData.unregisterListeners
    protected void unregisterListeners() {
        if (mMonitor != null) {
            mMonitor.unregister();
        }
        mMonitor = null;
        mListeners.clear();
    }

    private void destroyAttachments() {
        for (final MessagePartData attachment : mAttachments) {
            attachment.destroyAsync();
        }
        mAttachments.clear();
        mPendingAttachments.clear();
    }

    public void dispatchChanged(final int changeFlags) {
        // No change is expected to be made to the draft if it is in cached copy state.
        if (mIsDraftCachedCopy) {
            return;
        }
        // Any change in the draft will cancel any pending draft checking task, since the
        // size/status of the draft may have changed.
        if (mCheckDraftForSendTask != null) {
            mCheckDraftForSendTask.cancel(true /* mayInterruptIfRunning */);
            mCheckDraftForSendTask = null;
        }
        mListeners.onDraftChanged(this, changeFlags);
    }

    private void dispatchAttachmentLimitReached() {
        mListeners.onDraftAttachmentLimitReached(this);
    }

    /**
     * Drop any pending attachments that haven't finished. This is called after the UI goes to
     * the background and we persist the draft data to the database.
     */
    private void dropPendingAttachments() {
        mPendingAttachments.clear();
    }

    private boolean isDraftEmpty() {
        return TextUtils.isEmpty(mMessageText) && (mAlarmDate==null) &&
                TextUtils.isEmpty(mMessageSubject);
    }

    public boolean isCheckingDraft() {
        return mCheckDraftForSendTask != null && !mCheckDraftForSendTask.isCancelled();
    }

    public void checkDraftForAction(final boolean checkMessageSize, final int selfSubId,
            final CheckDraftTaskCallback callback, final Binding<DraftMessageData> binding) {
        //Bug 869110 start
        try{
            new CheckDraftForSendTask(checkMessageSize, selfSubId, callback, binding)
                    .executeOnThreadPool((Void) null);
        }catch (Exception ex){
            LogUtil.d(LogUtil.BUGLE_TAG, "checkDraftForAction:" + ex);
        }
        //Bug 869110 end
    }

    /**
     * Allows us to have multiple data listeners for DraftMessageData
     */
    private class DraftMessageDataEventDispatcher
        extends ArrayList<DraftMessageDataListener>
        implements DraftMessageDataListener {

        @Override
        @RunsOnMainThread
        public void onDraftChanged(DraftMessageData data, int changeFlags) {
            Assert.isMainThread();
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftChanged(data, changeFlags);
            }
        }

        @Override
        @RunsOnMainThread
        public void onDraftAttachmentLimitReached(DraftMessageData data) {
            Assert.isMainThread();
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftAttachmentLimitReached(data);
            }
        }

        @Override
        @RunsOnMainThread
        public void onDraftAttachmentLoadFailed() {
            Assert.isMainThread();
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftAttachmentLoadFailed();
            }
        }
        public void onDraftAttachmentOverSizeReached(DraftMessageData data,final MessagePartData attachment) {
            Assert.isMainThread();
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftAttachmentOverSizeReached(data,attachment);
            }
        }

        /* Add by SPRD for bug 563344 Start */
        @Override
        public void onDraftLoadDone(DraftMessageData data) {
            for (final DraftMessageDataListener listener : this) {
                listener.onDraftLoadDone(data);
            }
        }
        /* Add by SPRD for bug 563344 End */
    }

    public interface CheckDraftTaskCallback {
        void onDraftChecked(DraftMessageData data, int result);
    }

    public class CheckDraftForSendTask extends SafeAsyncTask<Void, Void, Integer> {
        public static final int RESULT_PASSED = 0;
        public static final int RESULT_HAS_PENDING_ATTACHMENTS = 1;
        public static final int RESULT_NO_SELF_PHONE_NUMBER_IN_GROUP_MMS = 2;
        public static final int RESULT_MESSAGE_OVER_LIMIT = 3;
        public static final int RESULT_VIDEO_ATTACHMENT_LIMIT_EXCEEDED = 4;
        public static final int RESULT_SIM_NOT_READY = 5;
        public static final int RESULT_PARTICIPANT_LIMIT_EXCEEDED = 6;
        private final boolean mCheckMessageSize;
        private final int mSelfSubId;
        private final CheckDraftTaskCallback mCallback;
        private final String mBindingId;
        private final List<MessagePartData> mAttachmentsCopy;
        private int mPreExecuteResult = RESULT_PASSED;

        public CheckDraftForSendTask(final boolean checkMessageSize, final int selfSubId,
                final CheckDraftTaskCallback callback, final Binding<DraftMessageData> binding) {
            mCheckMessageSize = checkMessageSize;
            mSelfSubId = selfSubId;
            mCallback = callback;
            mBindingId = binding.getBindingId();
            // Obtain an immutable copy of the attachment list so we can operate on it in the
            // background thread.
            mAttachmentsCopy = new ArrayList<MessagePartData>(mAttachments);

            mCheckDraftForSendTask = this;
        }

        @Override
        protected void onPreExecute() {
            // Perform checking work that can happen on the main thread.
            if (hasPendingAttachments()) {
                mPreExecuteResult = RESULT_HAS_PENDING_ATTACHMENTS;
                return;
            }
            if (getIsGroupMmsConversation()) {
                try {
                    if (TextUtils.isEmpty(PhoneUtils.get(mSelfSubId).getSelfRawNumber(true))) {
                        mPreExecuteResult = RESULT_NO_SELF_PHONE_NUMBER_IN_GROUP_MMS;
                        return;
                    }
                } catch (IllegalStateException e) {
                    // This happens when there is no active subscription, e.g. on Nova
                    // when the phone switches carrier.
                    mPreExecuteResult = RESULT_SIM_NOT_READY;
                    return;
                }
            }
            if (getVideoAttachmentCount() > MmsUtils.MAX_VIDEO_ATTACHMENT_COUNT) {
                mPreExecuteResult = RESULT_VIDEO_ATTACHMENT_LIMIT_EXCEEDED;
                return;
            }
            if(MmsConfig.get(mSelfSubId).getRecipientLimit() < mParticipantCount) {
                mPreExecuteResult = RESULT_PARTICIPANT_LIMIT_EXCEEDED;
                return;
            }
        }

        @Override
        protected Integer doInBackgroundTimed(Void... params) {
            if (mPreExecuteResult != RESULT_PASSED) {
                return mPreExecuteResult;
            }

            if (mCheckMessageSize && getIsMessageOverLimit()) {
                return RESULT_MESSAGE_OVER_LIMIT;
            }
            return RESULT_PASSED;
        }

        @Override
        protected void onPostExecute(Integer result) {
            mCheckDraftForSendTask = null;
            // Only call back if we are bound to the original binding.
            if (isBound(mBindingId) && !isCancelled()) {
                mCallback.onDraftChecked(DraftMessageData.this, result);
            } else {
                if (!isBound(mBindingId)) {
                    LogUtil.w(LogUtil.BUGLE_TAG, "Message can't be sent: draft not bound");
                }
                if (isCancelled()) {
                    LogUtil.w(LogUtil.BUGLE_TAG, "Message can't be sent: draft is cancelled");
                }
            }
        }

        @Override
        protected void onCancelled() {
            mCheckDraftForSendTask = null;
        }

        /**
         * 1. Check if the draft message contains too many attachments to send
         * 2. Computes the minimum size that this message could be compressed/downsampled/encoded
         * before sending and check if it meets the carrier max size for sending.
         * @see MessagePartData#getMinimumSizeInBytesForSending()
         */
        @DoesNotRunOnMainThread
        private boolean getIsMessageOverLimit() {
            Assert.isNotMainThread();
            if (mAttachmentsCopy.size() > getAttachmentLimit()) {
                return true;
            }

            // Aggregate the size from all the attachments.
            long totalSize = 0;
            for (final MessagePartData attachment : mAttachmentsCopy) {
                totalSize += attachment.getMinimumSizeInBytesForSending();
            }
            return totalSize > MmsConfig.get(mSelfSubId).getMaxMessageSize();
        }
    }

    public void onPendingAttachmentLoadFailed(PendingAttachmentData data) {
        mListeners.onDraftAttachmentLoadFailed();
    }

    public int getDefaultMaxMessageSize() {
        return MmsConfig.get(getSelfSubId()).getMaxMessageSize();
    }

    private static int getAttachmentDataLength(final Context context,
            final Uri uri) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            try {
                return is == null ? 0 : is.available();
            } catch (final IOException e) {
                LogUtil.e(LogUtil.BUGLE_TAG, "getDataLength couldn't stream: "
                        + uri, e);
            }
        } catch (final FileNotFoundException e) {
            LogUtil.e(LogUtil.BUGLE_TAG, "getDataLength couldn't open: " + uri,
                    e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (final IOException e) {
                    LogUtil.e(LogUtil.BUGLE_TAG,
                            "getDataLength couldn't close: " + uri, e);
                }
            }
        }
        return 0;
    }

    /* SPRD: modified for bug 503091 begin */
    public boolean isAttachemtCollectionsExceeded(
            final Collection<? extends MessagePartData> attachments) {
        final Context context = Factory.get().getApplicationContext();
        int mmsize = (int)(MmsConfig.getMaxMaxMessageSize() * 0.99);
        boolean noShare = false;
        LogUtil.e(LogUtil.BUGLE_TAG, "isAttachemtCollectionsExceeded mmsize:" + mmsize);
        for (final MessagePartData data : attachments) {
            if(data.getContentUri() == null){
                 return false;
            }
            final Uri uri = data.getContentUri();
            //int size = getAttachmentDataLength(context, uri);
            long size = MmsUtils.getMediaFileSize(uri);
            String contentType = data.getContentType();
            if (data.isImage()) {
                final boolean isGif = ImageUtils.isGif(contentType, uri);
                if (isGif) {
                    if (size >= mmsize) {
                        noShare = true;
                    }
                } else {
                    if (size > ATTACHMENT_EXCEEDED_SIZE) {
                        noShare = true;
                    }
                }
            } else if (data.isAudio() || data.isVideo()) {
                if (size >= mmsize) {
                    noShare = true;
                }
            } else {
                noShare = false;
            }

        }
        return noShare;
    }
    /* SPRD: modified for bug 503091 end */

    /* SPRD: modified for bug 503091 begin */
    public boolean isAttachemtExceeded(final MessagePartData attachment) {
        final long starttime = SystemClock.elapsedRealtime();
        Log.d(TAG, "isAttachemtExceeded");
        final Context context = Factory.get().getApplicationContext();
        final Uri uri = attachment.getContentUri();
        Log.d(TAG, "uri:" + uri);
        String contentType = attachment.getContentType();
        boolean noShare = false;
        double size =0L;
        int mmsize=0;
        Log.d(TAG, "contentType:" + contentType);
        /* Add by SPRD for Bug 527835 Start */
        if(ContentType.isDrmType(contentType)){
        /* Add by SPRD for Bug 527835 end */
            String dataPath = MessagingDrmSession.get().getPath(uri);
            contentType = MessagingDrmSession.get().getDrmOrigMimeType(dataPath, ContentType.APP_DRM_CONTENT);
        }

        try {
            size=MmsUtils.getMediaFileSize(uri);
        }catch (Exception e){
            LogUtil.e(LogUtil.BUGLE_TAG, "isAttachemtExceeded...fail:" + e, e);
        }
        Log.d(TAG, "orignal size:" +  size);
        mmsize = MmsConfig.getMaxMaxMessageSize();
        LogUtil.e(LogUtil.BUGLE_TAG, "isAttachemtExceeded mmsize:" + mmsize+" size:"+size+" contentType:"+contentType
                +" MessagePartData contentType:"+attachment.getContentUri());
        if (ContentType.isImageType(contentType)) {
            final boolean isGif = ImageUtils.isGif(contentType, uri);
            if (isGif) {
                if (size >= mmsize) {
                    noShare = true;
                }
                attachment.setmAttachmetSize(size);
            } else {
                if (size > ATTACHMENT_EXCEEDED_SIZE) {
                    attachment.setmAttachmetSize(size);
                    noShare = true;
                }else{
                        int width = attachment.getWidth();
                        int height = attachment.getHeight();
                    int widthLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH_DEFAULT;/*MmsConfig.get(subId).getMaxImageWidth()*/
                    int heightLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT_DEFAULT;/*MmsConfig.get(subId).getMaxImageHeight()*/
                    if ((height > width) != (heightLimit > widthLimit)) {
                        final int temp = widthLimit;
                        widthLimit = heightLimit;
                        heightLimit = temp;
                    }
                        final int orientation = ImageUtils.getOrientation(context, uri);
                    Log.d(TAG, "width:" + width + ", height:" + height + "orientation:" + orientation);
                    //add bug for 774824 start
                    if(size <= mmsize && !MmsConfig.getCmccSdkEnabled()){
                        attachment.setmAttachmetSize(size);
                    } else if(size <= mmsize && width <= widthLimit
                            && height <= heightLimit
                            && (orientation == android.media.ExifInterface.ORIENTATION_UNDEFINED || orientation == android.media.ExifInterface.ORIENTATION_NORMAL)){
                        attachment.setmAttachmetSize(size);
                        // //add bug for 774824 end
                    } else {
                        if(attachment.shouldCompress()){
                            UiUtils.showToastAtBottom(context.getString(R.string.compressing));// add for bug 725726
                        }
                        byte[] imageSize = ImageUtils.ImageResizer.getResizedImageDataNoGif(width, height, orientation, widthLimit, heightLimit, mmsize, uri, context, contentType);
                        attachment.setIsCompressed(1);
                        if(imageSize != null) {
                            double afterCompressOfImageSize = imageSize.length;
                            attachment.setmAttachmetSize(afterCompressOfImageSize);
                            Log.d(TAG, "resized size:" + afterCompressOfImageSize);
                        } else {
                            attachment.setmAttachmetSize(size);
                        }
                    }
                }
            }
        } else if (/*ContentType.isAudioType(contentType)
                || ContentType.isVideoType(contentType)*/
                ContentType.isVCardType(contentType)
                || ContentType.canSharedContentType(contentType)
                )  {
            if (size >= mmsize) {
                noShare = true;
            }
            Log.d(TAG, "contentType:" + contentType);
            Log.d(TAG, "size:" + size);
            attachment.setmAttachmetSize(size);
        } else {
            noShare = false;
        }
        final long endttime = SystemClock.elapsedRealtime();
        Log.e("android70","isAttachemtExceeded time:"+(endttime-starttime));
        return noShare;
    }
    /* SPRD: modified for bug 503091 end */

    //Bug 1110154 begin
    private final static int ATTACHMENTDATA_CHANGED = 1;
    private final static int REMOVE_ATTACHMENTDATA = 2;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case ATTACHMENTDATA_CHANGED:
                    dispatchChanged(ATTACHMENTS_CHANGED);
                    break;
                case REMOVE_ATTACHMENTDATA:
                    if (msg.obj != null && msg.obj instanceof MessagePartData) {
                        removeAttachment((MessagePartData) msg.obj);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private class UpdateAttachmentsTask extends SafeAsyncTask<Void, Void, Integer> {
        private Context mContext;
        private List<MessagePartData> mAttachments;
        public UpdateAttachmentsTask(final Context context, final List<MessagePartData> attachments) {
            mContext = context;
            mAttachments = attachments;
        }

        @Override
        protected Integer doInBackgroundTimed(Void... params) {
            int removed = 0;
            for (final MessagePartData part : mAttachments) {
                if (part.isAttachment()) {
                    if (getAttachmentDataLength(mContext, part.getContentUri()) <= 0) {
                        removed = 1;
                        Message msg = mHandler.obtainMessage();
                        msg.what = REMOVE_ATTACHMENTDATA;
                        msg.obj = part;
                        mHandler.sendMessage(msg);
                    }
                }
            }
            return removed;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result > 0) {
                Message msg = mHandler.obtainMessage();
                msg.what = ATTACHMENTDATA_CHANGED;
                mHandler.sendMessage(msg);
            }
        }
    }
    //Bug 1110154 end

    /* SPRD: modified for bug 497374 begin */
    public void updateAttachmentsData() {
        if (!mAttachments.isEmpty()) {
            final Context context = Factory.get().getApplicationContext();
            List<MessagePartData> attachments = new ArrayList<MessagePartData>();
            for (final MessagePartData att : mAttachments) {
                attachments.add(att);
            }
            mIsDraftCachedCopy = false;
            //Bug 1110154 begin
            new UpdateAttachmentsTask(context, attachments).executeOnThreadPool((Void) null);
            //Bug 1110154 end
        }
    }
    /* SPRD: modified for bug 497374 end */

    /* Add by SPRD for Bug 522885 Start */
     public boolean isVcardFile(final String contentType, final Uri uri){
        boolean mfile = false;
        String authority=uri.getAuthority();
        String scheme=uri.getScheme();
        if(ContentType.isVCardType(contentType)){
            if(!scheme.equals("content")){
                mfile =true;
            }else{
                if(authority.equals("com.android.providers.downloads.documents")){
                    mfile =true;
                }else{
                    mfile =false;
                }
            }
        }
        return mfile;
    }
    /* Add by SPRD for Bug 522885 end */

    /* Add by SPRD for Bug 549991 Start */
    public long getAttachmentsFileSize() {
        return attachmentsFileSize;
    }
    /* Add by SPRD for Bug 549991 end */

    public void setAttachmentsFileSize(long size) {
        attachmentsFileSize = size;
    }

    public static DraftMessageData loadDraftMessageDataByConversationId(final String conversationId) {
        SprdLogUtil.dump("loadDraftMessageDataByConversationId....conversationId", conversationId);
        DraftMessageData draftMessageData = new DraftMessageData(conversationId);
        try {
            final DatabaseWrapper db = DataModel.get().getDatabase();
            final ConversationListItemData conversation =
                    ConversationListItemData.getExistingConversation(db, conversationId);
            if (conversation != null) {
                MessageData messageData = BugleDatabaseOperations.readDraftMessageData(db, conversationId, conversation.getSelfId());
                if (messageData != null) {
                    draftMessageData.updateFromMessageData(messageData);
                }
            }
        } catch (Exception e) {
            SprdLogUtil.dump("loadDraftMessageDataByConversationId....Exception", e);
        }
        return draftMessageData;
    }

    public void updateFromMessageData(final MessageData message) {
        if (message == null) {
            return;
        }
        attachmentsFileSize = 0;
        mIsDraftCachedCopy = false;
        if (isDraftEmpty() || (TextUtils.equals(mMessageText, message.getMessageText()) &&
                TextUtils.equals(mMessageSubject, message.getMmsSubject()) &&
                mAttachments.isEmpty())) {
            setMessageText(message.getMessageText(), true /* notify */);/*change to true for bug 688346*/
            setMessageSubject(message.getMmsSubject(), false /* notify */);
            // FIXME: Here, resume all part is not "PendingAttachmentData", if it is,
            // @see updateFromMessageData(MessageData, String)
            for (final MessagePartData part : message.getParts()) {
                if (part != null) {
                    double attachmentFileSize = part.getmAttachmetSize();
                    attachmentsFileSize += attachmentFileSize;
                    if (!containsAttachment(part.getContentUri())) {
                        addAttachment(part, null /*pendingAttachment*/);
                    }
                }
            }
            if(message.getIsMms()) {
                //Bug971177 begin
                final String signatureText = mMessageTextStats.getSignatrueText();
                attachmentsFileSize += signatureText.getBytes().length;
                //Bug971177 end
            }
            if(message.getAlarm()!=0){
                LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "updateFromMessageData alarm time"+message.getAlarm());
                Date alarmDate=new Date(message.getAlarm());
                setMessageAlarm(alarmDate,true);
            }
        }
    }
    /**************************White Box testing start**********************************/
    private DraftMessageWhiteTest mWhiteTest;
    public interface DraftMessageWhiteTest{
        String getDraftWhiteTest();
        void sendMessageWhiteTest();
    }
    public void setDraftWhiteTest(final DraftMessageWhiteTest test){
        mWhiteTest=test;
    }

    public void setTestSelfId(final String selfId) {
        mSelfId = selfId;
    }

    /**************************White Box testing end**********************************/
}
