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

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.telephony.SubscriptionManager;
import android.text.Editable;
import android.text.Html;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.binding.ImmutableBindingRef;
import com.android.messaging.datamodel.data.ConversationData;
import com.android.messaging.datamodel.data.ConversationData.ConversationDataListener;
import com.android.messaging.datamodel.data.ConversationData.SimpleConversationDataListener;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.DraftMessageData.CheckDraftForSendTask;
import com.android.messaging.datamodel.data.DraftMessageData.CheckDraftTaskCallback;
import com.android.messaging.datamodel.data.DraftMessageData.DraftMessageDataListener;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.datamodel.data.SubscriptionListData.SubscriptionListEntry;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.ui.AttachmentPreview;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.PlainTextEditText;
import com.android.messaging.ui.conversation.ConversationInputManager.ConversationInputSink;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.MediaUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.ThreadUtil;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.WhiteTestType;
import com.sprd.messaging.drm.MessagingDrmSession;
import com.sprd.messaging.drm.MessagingUriUtil;
import com.sprd.messaging.util.FeatureOption;
import com.sprd.messaging.util.Utils;

import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

/**
 * This view contains the UI required to generate and send messages.
 */
public class ComposeMessageView extends LinearLayout
        implements TextView.OnEditorActionListener, DraftMessageDataListener, TextWatcher,
        ConversationInputSink, DraftMessageData.DraftMessageWhiteTest {

    public interface IComposeMessageViewHost extends
            DraftMessageData.DraftMessageSubscriptionDataProvider {
        void sendMessage(MessageData message);
        void onComposeEditTextFocused();
        void onAttachmentsCleared();
        void onAttachmentsChanged(final boolean haveAttachments);
        void displayPhoto(Uri photoUri, Rect imageBounds, boolean isDraft);
        void promptForSelfPhoneNumber();
        boolean isReadyForAction();
        void warnOfMissingActionConditions(final boolean sending,
                final Runnable commandToRunAfterActionConditionResolved);
        void warnOfExceedingMessageLimit(final boolean showAttachmentChooser,
                boolean tooManyVideos);
        void notifyOfAttachmentLoadFailed();
        void showAttachmentChooser();
        boolean shouldShowSubjectEditor();
        boolean shouldHideAttachmentsWhenSimSelectorShown();
        Uri getSelfSendButtonIconUri();
        int overrideCounterColor();
        int getAttachmentsClearedFlags();
        void showAttachmentExceededDialog(int str);
        void StartSlideshow();
        /*Add by SPRD for bug581044  2016.07.08 Start*/
        boolean isEmptyRecipientConversaton();
        /*Add by SPRD for bug581044  2016.07.08 End*/
        /*************************** White Box testing start**********************************/

        String getConverWhiteTest();

        String getConverTestSim();

        void sendConverFramBroadcase();
        /**************************White Box testing end**********************************/
    }

    private final boolean DEBUG = false;
    public static final int CODEPOINTS_REMAINING_BEFORE_COUNTER_SHOWN = 10;

    // There is no draft and there is no need for the SIM selector
    private static final int SEND_WIDGET_MODE_SELF_AVATAR = 1;
    // There is no draft but we need to show the SIM selector
    private static final int SEND_WIDGET_MODE_SIM_SELECTOR = 2;
    // There is a draft
    private static final int SEND_WIDGET_MODE_SEND_BUTTON = 3;

    private PlainTextEditText mComposeEditText;
    private PlainTextEditText mComposeSubjectText;
    private int selectionStart;
    private int selectionEnd;
    private TextView mCharCounter;
    private TextView mMmsIndicator;
    private TextView mMmsSize;
    private SimIconView mSelfSendIcon;
    private ImageButton mSendButton;
    private View mSubjectView;
    private ImageButton mDeleteSubjectButton;
    private AttachmentPreview mAttachmentPreview;
    private ImageButton mAttachMediaButton;
    private ImageButton mAlarmButton;

    /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
    private TextView mSignatureIndicator;
    /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */

    private final Binding<DraftMessageData> mBinding;
    private IComposeMessageViewHost mHost;
    private final Context mOriginalContext;
    private int mSendWidgetMode = SEND_WIDGET_MODE_SELF_AVATAR;

    // Shared data model object binding from the conversation.
    private ImmutableBindingRef<ConversationData> mConversationDataModel;

    // Centrally manages all the mutual exclusive UI components accepting user input, i.e.
    // media picker, IME keyboard and SIM selector.
    private ConversationInputManager mInputManager;

    /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
    //the signature text
    private String mSignatureText="";
    /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
    /* SPRD: modified for bug 497374 begin */
    private boolean mUpdateAttachment;
    /* SPRD: modified for bug 497374 end */
    private boolean mSending = false;   /* added by sprd for Bug 855832 */

    // Add by SPRD for bug 563344
    private boolean mEnableTextLengthLimitation = false;
    private boolean mIsDraftLoadDone = false;
    private boolean mShouldRestartIme;//add for bug 806832 for control restart of IME

    private final ConversationDataListener mDataListener = new SimpleConversationDataListener() {
        @Override
        public void onConversationMetadataUpdated(ConversationData data) {
            mConversationDataModel.ensureBound(data);
            updateVisualsOnDraftChanged();
        }

        @Override
        public void onConversationParticipantDataLoaded(ConversationData data) {
            mConversationDataModel.ensureBound(data);
            updateVisualsOnDraftChanged();
        }

        @Override
        public void onSubscriptionListDataLoaded(ConversationData data) {
            mConversationDataModel.ensureBound(data);
            updateOnSelfSubscriptionChange();
            updateVisualsOnDraftChanged();
        }
    };

    public ComposeMessageView(final Context context, final AttributeSet attrs) {
        super(new ContextThemeWrapper(context, R.style.ColorAccentBlueOverrideStyle), attrs);
        mOriginalContext = context;
        mBinding = BindingBase.createBinding(this);
    }

    /**
     * Host calls this to bind view to DraftMessageData object
     */
    public void bind(final DraftMessageData data, final IComposeMessageViewHost host) {
        mHost = host;
        mBinding.bind(data);
        data.addListener(this);
        data.setSubscriptionDataProvider(host);
        /**************************White Box testing start**********************************/
        data.setDraftWhiteTest(this);
        /**************************White Box testing end**********************************/

        final int counterColor = mHost.overrideCounterColor();
        if (counterColor != -1) {
            mCharCounter.setTextColor(counterColor);
        }
    }

    /**
     * Host calls this to unbind view
     */
    public void unbind() {
        if (mBinding != null && mBinding.isBound()) {/*Bug 701386*/
            mBinding.unbind();
        }
        mHost = null;
        /*add by sprd for Bug 630177  start*/
        if (mInputManager!=null) {
        /*add by sprd for Bug 630177  end*/
            mInputManager.onDetach();
        }
        /*add by sprd for Bug 653541  start*/
        if (mSendEmptyMsgConfirmDialog != null){
            mSendEmptyMsgConfirmDialog.dismiss();
            mSendEmptyMsgConfirmDialog = null;
        }
        /*add by sprd for Bug 653541  end*/
    }

    @Override
    protected void onFinishInflate() {
        mComposeEditText = (PlainTextEditText) findViewById(
                R.id.compose_message_text);
        mComposeEditText.setOnEditorActionListener(this);
        mComposeEditText.addTextChangedListener(this);
        mComposeEditText.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View v, final boolean hasFocus) {
                if (v == mComposeEditText && hasFocus) {
                    //bug 955039 begin
                    if(null != mInputManager){
                        mInputManager.HideMediaPickerFirst(true);
                    }
                    //bug 955039 end
                    if(mHost!=null){
                        mHost.onComposeEditTextFocused();
                    }
                }
            }
        });
        mComposeEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                //bug 955039 begin
                if(null != mInputManager){
                    mInputManager.HideMediaPickerFirst(true);
                }
                //bug 955039 end
                if (mHost.shouldHideAttachmentsWhenSimSelectorShown()) {
                    hideSimSelector();
                }
            }
        });

        // onFinishInflate() is called before self is loaded from db. We set the default text
        // limit here, and apply the real limit later in updateOnSelfSubscriptionChange().
        mComposeEditText.setFilters(new InputFilter[] {
                new BytesLengthFilter(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID)
                        .getMaxTextLimit()) });

        mSelfSendIcon = (SimIconView) findViewById(R.id.self_send_icon);
        mSelfSendIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean shown = mInputManager.toggleSimSelector(true /* animate */,
                        getSelfSubscriptionListEntry());
                hideAttachmentsWhenShowingSims(shown);
            }
        });
        mSelfSendIcon.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                if (mHost.shouldShowSubjectEditor()) {
                    showSubjectEditor();
                } else {
                    boolean shown = mInputManager.toggleSimSelector(true /* animate */,
                            getSelfSubscriptionListEntry());
                    hideAttachmentsWhenShowingSims(shown);
                }
                return true;
            }
        });

        mComposeSubjectText = (PlainTextEditText) findViewById(
                R.id.compose_subject_text);
        // We need the listener to change the avatar to the send button when the user starts
        // typing a subject without a message.
        mComposeSubjectText.addTextChangedListener(this);
        // onFinishInflate() is called before self is loaded from db. We set the default text
        // limit here, and apply the real limit later in updateOnSelfSubscriptionChange().
        mComposeSubjectText.setFilters(new InputFilter[] {
                new BytesLengthFilter(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID)
                        .getMaxSubjectLength())});  // Modify by SPRD for bug 542386

        mDeleteSubjectButton = (ImageButton) findViewById(R.id.delete_subject_button);
        mDeleteSubjectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                hideSubjectEditor();
                mComposeSubjectText.setText(null);
                mBinding.getData().setMessageSubject(null);
            }
        });

        mSubjectView = findViewById(R.id.subject_view);

        mSendButton = (ImageButton) findViewById(R.id.send_message_button);
        mSendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                //add 1146740 by sprd for ask-every-time  feature end
                 if(DEBUG) {
                     Log.d("ComposeMessageView-ask-everytime", "SendButton onClick()");
                }
                if (isAlertSelectDlg()&&!mIsAfterSimSelected) {
                        mIsSendButtonPressed = true;
                        boolean shown = mInputManager.toggleSimSelector(true,
                                getSelfSubscriptionListEntry());
                        hideAttachmentsWhenShowingSims(shown);
                        return;
                }
                //add 1146740 by sprd for ask-every-time  feature end
                 sendMessageIgnoreMessageSizeLimit();
            }
        });
        mSendButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(final View arg0) {
                boolean shown = mInputManager.toggleSimSelector(true /* animate */,
                        getSelfSubscriptionListEntry());
                hideAttachmentsWhenShowingSims(shown);
                if (mHost.shouldShowSubjectEditor()) {
                    showSubjectEditor();
                }
                return true;
            }
        });
        mSendButton.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
                super.onPopulateAccessibilityEvent(host, event);
                // When the send button is long clicked, we want TalkBack to announce the real
                // action (select SIM or edit subject), as opposed to "long press send button."
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
                    event.getText().clear();
                    event.getText().add(getResources()
                            .getText(shouldShowSimSelector(mConversationDataModel.getData()) ?
                            R.string.send_button_long_click_description_with_sim_selector :
                                R.string.send_button_long_click_description_no_sim_selector));
                    // Make this an announcement so TalkBack will read our custom message.
                    event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
                }
            }
        });

        mAttachMediaButton =
                (ImageButton) findViewById(R.id.attach_media_button);
        mAttachMediaButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                // Showing the media picker is treated as starting to compose the message.
                mInputManager.showHideMediaPicker(true /* show */, true /* animate */);
            }
        });

        mAlarmButton =
                (ImageButton) findViewById(R.id.alarm_button);

        mAlarmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                if(mBinding.getData().getAlarmDate()!=null){
                    UiUtils.showToastAtBottom(mBinding.getData().getAlarmDate().toString());
                }else{
                    UiUtils.showToastAtBottom("alarm not set");
                }
            }
        });

        mAttachmentPreview = (AttachmentPreview) findViewById(R.id.attachment_draft_view);
        mAttachmentPreview.setComposeMessageView(this);

        mCharCounter = (TextView) findViewById(R.id.char_counter);
        mMmsIndicator = (TextView) findViewById(R.id.mms_indicator);
        mMmsSize = (TextView) findViewById(R.id.mms_attachment_size);

        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin*/
        mSignatureIndicator = (TextView) findViewById(R.id.signature_indicator);
        /*Modify by SPRD for Bug:542982   Start*/
        if (MmsConfig.getSignatureEnabled()) {
             final Context context = Factory.get().getApplicationContext();
             final String prefKey = context.getString(R.string.signature_pref_key);
             mSignatureText = BuglePrefs.getApplicationPrefs().getString(prefKey, "");
        }

        if(mSignatureText != null && "".equals(mSignatureText.trim())){
            mSignatureIndicator.setVisibility(View.GONE);
        }else{
            mSignatureIndicator.setVisibility(View.VISIBLE);
            mSignatureIndicator.setText(mSignatureText);
        }
        /*Modify by SPRD for Bug:542982   End*/
        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
    }

    //add 1146740 sprd for ask-everytime  feature begin
    public static boolean mIsSendButtonPressed = false;
    private boolean mIsAfterSimSelected        = false;
    private void setIsSendButtonPressed(boolean is_pressed){
        if(DEBUG)  Log.d("ComposeMessageView-ask-everytime","[ComposeMessageView]=====setIsSendButtonPressed===is_pressed:"+is_pressed);
        mIsSendButtonPressed = is_pressed;
    }

    private void setIsAfterSimSelected(boolean is_selected){
        if(DEBUG)  Log.d("ComposeMessageView-ask-everytime","[ComposeMessageView]=====setIsAfterSimSelected===is_selected:"+is_selected);
        mIsAfterSimSelected = is_selected;
    }

    public boolean isAlertSelectDlg() {
        if (needSelectSubscription(mOriginalContext)
                /*&& (!ConversationFragment.isReply)*/) {
            if(DEBUG) Log.d("ComposeMessageView-ask-everytime","[ComposeMessageView]=====isAlertSelectDlg===return true");
            return true;
        }
        return false;
    }
    public boolean needSelectSubscription(Context context) {
        int defaultSubscription = SubscriptionManager.getDefaultSmsSubscriptionId();
        if(DEBUG) Log.d("ComposeMessageView-ask-everytime", "defaultSubscription==" + defaultSubscription+"  ; conversationSelfId:"+getConversationSelfId());
        if ((defaultSubscription == SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE
               || !SubscriptionManager.isValidSubscriptionId(defaultSubscription))&&(PhoneUtils.getDefault().getActiveSubscriptionCount() > 1)) {
            return true;
        } else {
            return false;
        }
    }
    //add 1146740 by sprd for ask-everytime  feature end
    private void hideAttachmentsWhenShowingSims(final boolean simPickerVisible) {
        if (!mHost.shouldHideAttachmentsWhenSimSelectorShown()) {
            return;
        }
        final boolean haveAttachments = mBinding.getData().hasAttachments();
        if (simPickerVisible && haveAttachments) {
            mHost.onAttachmentsChanged(false);
            mAttachmentPreview.hideAttachmentPreview();
        } else {
            mHost.onAttachmentsChanged(haveAttachments);
            mAttachmentPreview.onAttachmentsChanged(mBinding.getData());
        }
    }

    public void setInputManager(final ConversationInputManager inputManager) {
        mInputManager = inputManager;
    }

    public void setConversationDataModel(final ImmutableBindingRef<ConversationData> refDataModel) {
        mConversationDataModel = refDataModel;
        mConversationDataModel.getData().addConversationDataListener(mDataListener);
    }

    ImmutableBindingRef<DraftMessageData> getDraftDataModel() {
        return BindingBase.createBindingReference(mBinding);
    }

    // returns true if it actually shows the subject editor and false if already showing
    private boolean showSubjectEditor() {
        // show the subject editor
        if (mSubjectView.getVisibility() == View.GONE) {
            mSubjectView.setVisibility(View.VISIBLE);
            mSubjectView.requestFocus();
            return true;
        }
        return false;
    }

    private void hideSubjectEditor() {
        mSubjectView.setVisibility(View.GONE);
        /*SPRD for bug 699561 20170704 add start*/
        if (null != mComposeEditText)
        /*SPRD for bug 699561 20170704 end*/
            mComposeEditText.requestFocus();
    }

    /**
     * {@inheritDoc} from TextView.OnEditorActionListener
     */
    @Override // TextView.OnEditorActionListener.onEditorAction
    public boolean onEditorAction(final TextView view, final int actionId, final KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            //sendMessageInternal(true /* checkMessageSize */);
            /*SPRD for bug 1368950 start*/
            if (isAlertSelectDlg() && !mIsAfterSimSelected)  {
                UiUtils.showToast(R.string.no_preferred_sim_selected);
                return true;
            }
            /*SPRD for bug 1368950 end*/
            sendMessageIgnoreMessageSizeLimit();
            return true;
        }
        return false;
    }

    private void sendMessageInternal(final boolean checkMessageSize) {
        LogUtil.i(LogUtil.BUGLE_TAG, "UI initiated message sending in conversation " +
                mBinding.getData().getConversationId());
        //bug 620043 begin
        final PhoneUtils phoneUtils = PhoneUtils.get(mHost.getConversationSelfSubId());
        int subId=mBinding.getData().getSelfSubId();
        LogUtil.d(LogUtil.BUGLE_TAG, "getSelfSubId:["+subId);
        //for bug841310 begin
        if(!PhoneUtils.getDefault().isActiveSubId(subId)){
             subId = SubscriptionManager.getDefaultSmsSubscriptionId();/*for 814413, should use the correct systemDefaultSubId */
             LogUtil.d(LogUtil.BUGLE_TAG, "getDefaultSmsSubscriptionId:["+subId);
             PhoneUtils.setDefaultSubid(-1);
        }//for bug841310 end
        LogUtil.d("AirplaneSend","mHost.getConversationSelfSubId() = " + mHost.getConversationSelfSubId());
        if(phoneUtils.isAirplaneModeOn()){
             if (!Utils.isVowifiSmsEnable(subId)) {
                 UiUtils.showToastAtBottom(getContext().getString(R.string.send_message_failure_airplane_mode));
                 return;
             }
        }
        //bug 620043 end

        if (mBinding.getData().isCheckingDraft()) {
            // Don't send message if we are currently checking draft for sending.
            LogUtil.w(LogUtil.BUGLE_TAG, "Message can't be sent: still checking draft");
            return;
        }
        // Check the host for pre-conditions about any action.
        if (mHost.isReadyForAction()) {
            mInputManager.showHideSimSelector(false /* show */, true /* animate */);
            /* Add by SPRD for bug 563344 Start */
            //check alarm time
            Long timeInMillis=new GregorianCalendar().getTimeInMillis();
            Date nowdate = new Date(timeInMillis);
            DraftMessageData checkDraftMessage = mBinding.getData();
            if(checkDraftMessage.getAlarmDate()!=null){
                 if( nowdate.after(checkDraftMessage.getAlarmDate())) {
                     UiUtils.showToastAtBottom(getContext().getString(R.string.please_reset_time));
                     return;
                 }
            }
            /* Add by SPRD for bug 563344 End */
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
            String prefixSignature;
            if (MmsConfig.getSignatureEnabled() && (mSignatureText != null && !("".equals(mSignatureText.trim())))) {
                prefixSignature = "--" + mSignatureText;
            } else {
                prefixSignature = "";
            }
            //mBinding.getData().setSending(true);/* deleted by sprd for Bug 672659*/
            final String messageToSend = mComposeEditText.getText().toString() + prefixSignature;
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
            mBinding.getData().setMessageText(messageToSend);
            final String subject = mComposeSubjectText.getText().toString();
            mBinding.getData().setMessageSubject(subject);
            // Asynchronously check the draft against various requirements before sending.
            mBinding.getData().checkDraftForAction(checkMessageSize,
                    mHost.getConversationSelfSubId(), new CheckDraftTaskCallback() {
                @Override
                public void onDraftChecked(DraftMessageData data, int result) {
                    mBinding.ensureBound(data);
                    switch (result) {
                        case CheckDraftForSendTask.RESULT_PASSED:
                            LogUtil.d(LogUtil.BUGLE_TAG, "checkDraftForAction RESULT_PASSED");
                            // Continue sending after check succeeded.
                            /*Add by SPRD for bug581044  2016.07.08 Start*/
                            if(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()){
                                boolean isEmpty = mHost.isEmptyRecipientConversaton();
                                LogUtil.w(LogUtil.BUGLE_TAG, "sendMessageInternal  isEmpty:" + isEmpty);
                                if(isEmpty){
                                    UiUtils.showToastAtBottom(R.string.invalid_destination);
                                    return;
                                }
                            }
                            /*Add by SPRD for bug581044  2016.07.08 End*/
                            mBinding.getData().setSending(true);/* added by sprd for Bug 672659*/
                            mSending = true;    /* added by sprd for Bug 855832 */
                            final MessageData message = mBinding.getData()
                                    .prepareMessageForSending(mBinding);
                            //spread: function for sending empty msg start
                            if(message.getIsMms()){
                                if (message != null && message.hasContent()) {
                                    //for bug687038 begin
                                    ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            playSentSound();
                                        }
                                    },500);//for bug687038 end
                                    mHost.sendMessage(message);
                                    hideSubjectEditor();
                                    if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
                                        AccessibilityUtil.announceForAccessibilityCompat(
                                                ComposeMessageView.this, null,
                                                R.string.sending_message);
                                    }
                                }
                            }else{
                                if(TextUtils.getTrimmedLength(message.getMessageText()) == 0){
                                    if((MmsConfig.get(mHost.getConversationSelfSubId()).getFinalSendEmptyMessageFlag() == 1)
                                            || (MmsConfig.get(mHost.getConversationSelfSubId()).getFinalSendEmptyMessageFlag() == 0 && message.getMessageText().length() > 0)) {
                                           confirmSendEmptyMsg(message);
                                    }else{
                                        if (message != null && message.hasContent()) {
                                            //for bug687038 begin
                                            ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    playSentSound();
                                                }
                                            },500);//for bug687038 end
                                            mHost.sendMessage(message);
                                            hideSubjectEditor();
                                            if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
                                                AccessibilityUtil.announceForAccessibilityCompat(
                                                        ComposeMessageView.this, null,
                                                        R.string.sending_message);
                                            }
                                        }
                                    }
                                }else{
                                    if (message != null && message.hasContent()) {
                                        //for bug1362886 begin
                                        if (!TextUtils.isEmpty(prefixSignature) && prefixSignature.equals(message.getMessageText())) {
                                            break;
                                        }
                                        //for bug1362886 end
                                        //for bug687038 begin
                                        ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                playSentSound();
                                            }
                                        },500);//for bug687038 end
                                        mHost.sendMessage(message);
                                        hideSubjectEditor();
                                        if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
                                            AccessibilityUtil.announceForAccessibilityCompat(
                                                    ComposeMessageView.this, null,
                                                    R.string.sending_message);
                                        }
                                    }
                                }
                            }
                            //spread: function for sending empty msg end
                            break;

                        case CheckDraftForSendTask.RESULT_HAS_PENDING_ATTACHMENTS:
                            // Cannot send while there's still attachment(s) being loaded.
                            UiUtils.showToastAtBottom(
                                    R.string.cant_send_message_while_loading_attachments);
                            break;

                        case CheckDraftForSendTask.RESULT_NO_SELF_PHONE_NUMBER_IN_GROUP_MMS:
                            mHost.promptForSelfPhoneNumber();
                            break;

                        case CheckDraftForSendTask.RESULT_MESSAGE_OVER_LIMIT:
                            Assert.isTrue(checkMessageSize);
                            mHost.warnOfExceedingMessageLimit(
                                    true /*sending*/, false /* tooManyVideos */);
                            break;

                        case CheckDraftForSendTask.RESULT_VIDEO_ATTACHMENT_LIMIT_EXCEEDED:
                            Assert.isTrue(checkMessageSize);
                            mHost.warnOfExceedingMessageLimit(
                                    true /*sending*/, true /* tooManyVideos */);
                            break;

                        case CheckDraftForSendTask.RESULT_SIM_NOT_READY:
                            // Cannot send if there is no active subscription
                            UiUtils.showToastAtBottom(
                                    R.string.cant_send_message_without_active_subscription);
                            break;
                        case CheckDraftForSendTask.RESULT_PARTICIPANT_LIMIT_EXCEEDED:
                            UiUtils.showExceedRecipientLimitDialog(getContext(),
                                    MmsConfig.get(mBinding.getData().getSelfSubId()).getRecipientLimit(),
                                    mBinding.getData().getParticipantCount());
                            break;
                        default:
                            break;
                    }
                }
            }, mBinding);
        } else {
            mHost.warnOfMissingActionConditions(true /*sending*/,
                    new Runnable() {
                        @Override
                        public void run() {
                            //sendMessageInternal(checkMessageSize);
                            sendMessageIgnoreMessageSizeLimit();
                        }

            });
        }
    }

    private AlertDialog mSendEmptyMsgConfirmDialog; /*add by sprd for Bug 653541*/
    //spread : add for new function send empty msg begin
    private void confirmSendEmptyMsg(final MessageData message){
        //  set flag else return  606
        AlertDialog.Builder builder = new AlertDialog.Builder(mOriginalContext);
        builder.setTitle(R.string.empty_msg);
        builder.setMessage(R.string.send_empty_msg);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
               dialog.dismiss();
               // sprd 572208  begin
               playSentSound();
               if (mHost ==null) return;/*add for Bug 653541*/
               mHost.sendMessage(message);
               hideSubjectEditor();
               if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
                   AccessibilityUtil.announceForAccessibilityCompat(
                        ComposeMessageView.this, null,
                        R.string.sending_message);
               }
           }
         // sprd 572208  end
        });

        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                return;
            }
        });
        mSendEmptyMsgConfirmDialog = builder.show();/*add by sprd for Bug 653541*/
    }
    // spread : add for new function send empty msg end

    public static void playSentSound() {
        // Check if this setting is enabled before playing
        final BuglePrefs prefs = BuglePrefs.getApplicationPrefs();
        final Context context = Factory.get().getApplicationContext();
        final String prefKey = context.getString(R.string.send_sound_pref_key);
        final boolean defaultValue = context.getResources().getBoolean(
                R.bool.send_sound_pref_default);
        if (!prefs.getBoolean(prefKey, defaultValue)) {
            return;
        }
        MediaUtil.get().playSound(context, R.raw.message_sent, null /* completionListener */);
    }

    /**
     * {@inheritDoc} from DraftMessageDataListener
     */
    @Override // From DraftMessageDataListener
    public void onDraftChanged(final DraftMessageData data, final int changeFlags) {
        // As this is called asynchronously when message read check bound before updating text
        mBinding.ensureBound(data);
        // Add by SPRD for bug 563344
        final boolean shouldUseSmilView = ( (MessageData.PROTOCOL_MMS_SMIL == data.mProtocol) &&
                                            (data.getAttachments().size() > 0) );

        // We have to cache the values of the DraftMessageData because when we set
        // mComposeEditText, its onTextChanged calls updateVisualsOnDraftChanged,
        // which immediately reloads the text from the subject and message fields and replaces
        // what's in the DraftMessageData.

        final String subject = data.getMessageSubject();
//        final String message = data.getMessageText();
        /* Add by SPRD for bug 563344 Start */
        final String message;
        if (!shouldUseSmilView) {
            message = data.getMessageText();
            //mComposeEditText.setEnabled(true);
            //mAttachMediaButton.setEnabled(true);
            mComposeEditText.setFocusable(true);
            mComposeEditText.setFocusableInTouchMode(true);
        } else {
            message = "";
            //mComposeEditText.setEnabled(false);
            //mAttachMediaButton.setEnabled(false);
            mComposeEditText.setFocusable(false);
        }
        /* Add by SPRD for bug 563344 End */

        LogUtil.d(LogUtil.BUGLE_TAG, "ComposeMessageView changeFlags:["+changeFlags+"]");

        if ((changeFlags & DraftMessageData.MESSAGE_ALARM_CHANGED) ==
                DraftMessageData.MESSAGE_ALARM_CHANGED) {
                Date date= data.getAlarmDate();
                mShouldRestartIme=false;
            if(date!=null){
                mAlarmButton.setVisibility(View.VISIBLE);
            }else{
                mAlarmButton.setVisibility(View.GONE);
            }
        }

        if ((changeFlags & DraftMessageData.MESSAGE_SUBJECT_CHANGED) ==
                DraftMessageData.MESSAGE_SUBJECT_CHANGED) {
            setUpdateVisuals(false);
            mComposeSubjectText.setText(subject);
            setUpdateVisuals(true);
            mShouldRestartIme=false;

            // Set the cursor selection to the end since setText resets it to the start
            mComposeSubjectText.setSelection(mComposeSubjectText.getText().length());
        }

        if ((changeFlags & DraftMessageData.MESSAGE_TEXT_CHANGED) ==
                DraftMessageData.MESSAGE_TEXT_CHANGED) {
            setUpdateVisuals(false);
            mComposeEditText.setText(message);
            setUpdateVisuals(true);
            mShouldRestartIme=false;

            // Set the cursor selection to the end since setText resets it to the start
            mComposeEditText.setSelection(mComposeEditText.getText().length());
        }
        if ((changeFlags & DraftMessageData.SELF_CHANGED) == DraftMessageData.SELF_CHANGED) {
            updateOnSelfSubscriptionChange();
            mShouldRestartIme=true;//for bug839468
        }
        if ((changeFlags & DraftMessageData.ATTACHMENTS_CHANGED) ==
                DraftMessageData.ATTACHMENTS_CHANGED) {
            /* Add for bug 590159 Start */
            if (MessageData.PROTOCOL_MMS_SMIL == data.mProtocol && mIsDraftLoadDone && data.getAttachments().size() <= 2) {
                for(Iterator<MessagePartData> i = data.getAttachments().iterator(); i.hasNext();) {
                    if (ContentType.APP_SMIL.equals(i.next().getContentType())) {
                        i.remove();
                        break;
                    }
                }
                data.mProtocol = MessageData.PROTOCOL_UNKNOWN;
            }
            /* Add for bug 590159 End */
            mShouldRestartIme=true;
            final boolean haveAttachments = mAttachmentPreview.onAttachmentsChanged(data);
            mHost.onAttachmentsChanged(haveAttachments);
        }
        updateVisualsOnDraftChanged();
        /* Add by SPRD for Bug 549991 Start */
        GlobleUtil.setDraftMessageData(data);
        /* Add by SPRD for Bug 549991 end */
    }

    @Override   // From DraftMessageDataListener
    public void onDraftAttachmentLimitReached(final DraftMessageData data) {
        mBinding.ensureBound(data);
        mHost.warnOfExceedingMessageLimit(false /* sending */, false /* tooManyVideos */);
    }

    private void updateOnSelfSubscriptionChange() {
        // Refresh the length filters according to the selected self's MmsConfig.
        mComposeEditText.setFilters(new InputFilter[] {
                new BytesLengthFilter(MmsConfig.get(mBinding.getData().getSelfSubId())
                        .getMaxTextLimit()) });
        mComposeSubjectText.setFilters(new InputFilter[] {
                new BytesLengthFilter(MmsConfig.get(mBinding.getData().getSelfSubId())
                        .getMaxSubjectLength())});  // Modify by SPRD for bug 542386
    }

    /* Add by SPRD for bug 542386 Start */
    private static class BytesLengthFilter implements InputFilter{
        private final int mMax;

        public BytesLengthFilter(int max) {
            mMax = max;
        }
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                int dstart, int dend) {
            int sourceSubBytes = source.subSequence(start, end).toString().getBytes().length;
            int destSubBytes = dest.subSequence(dstart, dend).toString().getBytes().length;
            int originalBytes = dest.toString().getBytes().length;
            int bytesKeep = mMax - (originalBytes - destSubBytes);
            if (bytesKeep <= 0) {
                UiUtils.showToast(R.string.exceed_text_length_limitation);
                return "";
            } else if (bytesKeep >= sourceSubBytes) {
                return null; // keep original
            } else if (!containsEmoji(source.subSequence(start, end).toString())){
                //add for bug 652725 --begin
                Log.d("BytesLengthFilter","containsEmoji ="+false);
                int endIndex = 1;
                // Search for the longest sub-sequence that under the limitation
                while (bytesKeep >= source.subSequence(start, endIndex).toString().getBytes().length) {
                    endIndex++;
                }
                endIndex--;
                UiUtils.showToast(
                        R.string.exceed_text_length_limitation);
                if (endIndex == start) {
                    return "";
                }
                return source.subSequence(start, endIndex);
            }else{
                UiUtils.showToast(
                        R.string.exceed_text_length_limitation);
                return "";
            }
            //add for bug 652725 --end
        }

        /**
         * @return the maximum length enforced by this input filter
         */
        public int getMax() {
            return mMax;
        }
        //add for bug 652725 --begin
        private boolean containsEmoji(String source) {
            int len = source.length();
            for (int i = 0; i < len; i++) {
                char codePoint = source.charAt(i);
                if (!isEmojiCharacter(codePoint)) {
                    return true;
                }
            }
            return false;
        }

        private  boolean isEmojiCharacter(char codePoint) {
            return (codePoint == 0x0) || (codePoint == 0x9) || (codePoint == 0xA) ||
                (codePoint == 0xD) || ((codePoint >= 0x20) && (codePoint <= 0xD7FF)) ||
                ((codePoint >= 0xE000) && (codePoint <= 0xFFFD)) /*|| ((codePoint >= 0x10000)
                && (codePoint <= 0x10FFFF))*/;
        }
        //add for bug 652725 --end
    }
    /* Add by SPRD for bug 542386 End */

    @Override
    public void onMediaItemsSelected(final Collection<MessagePartData> items) {
        Log.d("CompoeMessageView","onMediaItemsSelected  !mBinding.isBound:" + !mBinding.isBound());
        if (!mBinding.isBound()) return;
        mBinding.getData().addAttachments(items);
        announceMediaItemState(true /* isSelected */);
    }

    @Override
    public void onMediaItemsUnselected(final MessagePartData item) {
        mBinding.getData().removeAttachment(item);
        announceMediaItemState(false /*isSelected*/);
    }
    @Override
    public void onTextContactsAdded(final String string) {
        Log.d("wenbo","CompoeMessageView  onTextContactsAdded");
        mComposeEditText.requestFocus();
        mComposeEditText.append(string);
        mComposeEditText.setSelection(mComposeEditText.getText().length());
    }
    @Override
    public void onPendingAttachmentAdded(final PendingAttachmentData pendingItem) {
       /*modified by sprdfor Bug 847338 start*/
        try {
            Uri orgUri= pendingItem.getContentUri();
            if (MessagingUriUtil.isMediaDocument(orgUri)) {
                Log.d("CompoeMessageView","addPendingAttachment orgUri:" + orgUri);
                String path = MessagingUriUtil.getPath(mOriginalContext, orgUri);
                Log.d("CompoeMessageView","addPendingAttachment path:" + path);
                if (null != path) {
                    Uri uri = Uri.parse("file://" + path);
                    pendingItem.setContentUri(uri);
                    Log.d("CompoeMessageView","addPendingAttachment uri set=" + uri);
                }
            }
            mBinding.getData().addPendingAttachment(pendingItem, mBinding);
            resumeComposeMessage();
        } catch (Exception ex){
            Log.d("CompoeMessageView","addPendingAttachment Exception ex" + ex);
        }
       /*modified by sprdfor Bug 847338 end*/
    }

    @Override
    public void onPendingAlarmAdded(final Date date) {
        // FAN TO DO
        if(date ==null){
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "onPendingAlarmAdded cancel alarm.");
            mAlarmButton.setVisibility(View.GONE);
        }else{
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "draftMessageData set  alarm.");
            mAlarmButton.setVisibility(View.VISIBLE);
        }
        mBinding.getData().setMessageAlarm(date);
        resumeComposeMessage();
    }

    @Override
    public void StartSlideshow() {
        mHost.StartSlideshow();
    }

    private void announceMediaItemState(final boolean isSelected) {
        final Resources res = getContext().getResources();
        final String announcement = isSelected ? res.getString(
                R.string.mediapicker_gallery_item_selected_content_description) :
                    res.getString(R.string.mediapicker_gallery_item_unselected_content_description);
        AccessibilityUtil.announceForAccessibilityCompat(
                this, null, announcement);
    }

    private void announceAttachmentState() {
        if (AccessibilityUtil.isTouchExplorationEnabled(getContext())) {
            int attachmentCount = mBinding.getData().getReadOnlyAttachments().size()
                    + mBinding.getData().getReadOnlyPendingAttachments().size();
            final String announcement = getContext().getResources().getQuantityString(
                    R.plurals.attachment_changed_accessibility_announcement,
                    attachmentCount, attachmentCount);
            AccessibilityUtil.announceForAccessibilityCompat(
                    this, null, announcement);
        }
    }

    @Override
    public void resumeComposeMessage() {
        mComposeEditText.requestFocus();
        mInputManager.showHideImeKeyboard(true, true);
        announceAttachmentState();
    }

    public void clearAttachments() {
        mBinding.getData().clearAttachments(mHost.getAttachmentsClearedFlags());
        mHost.onAttachmentsCleared();
    }

     /*Add by SPRD for bug 646250 Start*/
    public void clearDraft() {
        mComposeSubjectText.setText("");
        mComposeEditText.setText("");
        if(mBinding.getData()!=null){ //add for bug650418 begin
            mBinding.getData().clearAttachments(DraftMessageData.ALL_CHANGED);
        }//add for bug650418 end
    }

    // bug869816 begin
    public boolean isDraftLoadDone() {
        return mIsDraftLoadDone;
    }
    // bug869816 end

    public boolean isValidConversation() {
        if (mBinding.getData() == null) {//add for  Bug 680611 begin
            LogUtil.w(LogUtil.BUGLE_TAG, "isValidConversation draftMessageData is null.");
            return false;
        }//add for  Bug 680611 end
        long attSize = mBinding.getData().getAttachmentsFileSize();
        String messageText = mBinding.getData().getMessageText();
        String SubjectText = mBinding.getData().getMessageSubject();
        Date alarmDate = mBinding.getData().getAlarmDate();//add for 726770
        LogUtil.d(LogUtil.BUGLE_TAG, "isValidConversation check alarm");//add for 724510
        LogUtil.d(LogUtil.BUGLE_TAG, "isValidConversation: attSize = " + attSize + ", messageText = " + messageText + ", mSending = " + mSending);//add for 724510
        if ((messageText != null && !TextUtils.isEmpty(messageText)) || (attSize > 0) || mSending   /* mSending was added by sprd for Bug 855832 */
                || (SubjectText != null && !TextUtils.isEmpty(SubjectText))||(alarmDate != null)) {//alarmDate add for 726770
            return true;
        }
        return false;
    }
    /*Add by SPRD for bug 646250 End*/

    public void requestDraftMessage(boolean clearLocalDraft) {
        /* Add by SPRD for bug 576767 Start */
        if (clearLocalDraft) {
            mComposeSubjectText.setText("");
            hideSubjectEditor();
            mComposeEditText.setText("");   // Add for bug 581658
        }
        /* Add by SPRD for bug 576767 End */
        mBinding.getData().loadFromStorage(mBinding, null, clearLocalDraft);
        /* SPRD: modified for bug 497374 begin */
        if (mUpdateAttachment) {
            mBinding.getData().updateAttachmentsData();
        }
        mUpdateAttachment=false;
        /* SPRD: modified for bug 497374 end */
    }

    public void setDraftMessage(final MessageData message) {
        mBinding.getData().loadFromStorage(mBinding, message, false);
    }

    public void writeDraftMessage() {
        final String messageText = mComposeEditText.getText().toString();
        mBinding.getData().setMessageText(messageText);

        final String subject = mComposeSubjectText.getText().toString();
        mBinding.getData().setMessageSubject(subject);

        //for bug698029 begin
        final String conversationId = mBinding.getData().getConversationId();
        boolean isEmptyRecipientConversaton = BugleDatabaseOperations.isEmptyConversation(conversationId);
        Log.d("ComposeMessageView","writeDraftMessage conversationId :"+conversationId+"; isEmptyRecipientConversaton: " + isEmptyRecipientConversaton);
        if(!isEmptyRecipientConversaton){
            mBinding.getData().saveToStorage(mBinding);
        }
        //for bug698029 end
        /* SPRD: modified for bug 497374 begin */
        mUpdateAttachment = true;
        /* SPRD: modified for bug 497374 end */
    }

    /*Add by SPRD for bug581044  2016.07.08 Start*/
    public MessageData getKeepingMessageData(){
        final String messageText = mComposeEditText.getText().toString();
        mBinding.getData().setMessageText(messageText);
        final String subject = mComposeSubjectText.getText().toString();
        Log.d("","getKeepingMessageData subject: " + subject + " messageText:" + messageText);
        mBinding.getData().setMessageSubject(subject);
        return mBinding.getData().getMessageData();
    }
    /*Add by SPRD for bug581044  2016.07.08 End*/

    private void updateConversationSelfId(final String selfId, final boolean notify) {
        mBinding.getData().setSelfId(selfId, notify);
    }

    private Uri getSelfSendButtonIconUri() {
        final Uri overridenSelfUri = mHost.getSelfSendButtonIconUri();
        if (overridenSelfUri != null) {
            return overridenSelfUri;
        }
        final SubscriptionListEntry subscriptionListEntry = getSelfSubscriptionListEntry();

        if (subscriptionListEntry != null) {
            return subscriptionListEntry.selectedIconUri;
        }

        // Fall back to default self-avatar in the base case.
        final ParticipantData self = mConversationDataModel.getData().getDefaultSelfParticipant();
        return self == null ? null : AvatarUriUtil.createAvatarUri(self);
    }

    private SubscriptionListEntry getSelfSubscriptionListEntry() {
        if (mBinding.isBound()) {
            return mConversationDataModel.getData().getSubscriptionEntryForSelfParticipant(
                    mBinding.getData().getSelfId(), false /* excludeDefault */);
        } else {
            return null;
        }
    }

    private boolean isDataLoadedForMessageSend() {
        // Check data loading prerequisites for sending a message.
        return mConversationDataModel != null && mConversationDataModel.isBound() &&
                mConversationDataModel.getData().getParticipantsLoaded();
    }

    private void updateVisualsOnDraftChanged() {
        final String messageText = mComposeEditText.getText().toString();
        final DraftMessageData draftMessageData = mBinding.getData();
        final InputMethodManager inputMethodManager = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        /*add for Bug 639290  start*/
        if (draftMessageData==null) {
            LogUtil.w(LogUtil.BUGLE_TAG, "draftMessageData is  null.");
            return;
        }
        /*add for Bug 639290  end*/
        Log.d("andy", "updateVisualsOnDraftChanged: mShouldRestartIme = " + mShouldRestartIme);
        if(ActivityManager.isUserAMonkey()){//bug825495
            return;
        }
        if(!draftMessageData.isSending())
        draftMessageData.setMessageText(messageText);

        final String subject = mComposeSubjectText.getText().toString();
        draftMessageData.setMessageSubject(subject);
        if (!TextUtils.isEmpty(subject)) {
             mSubjectView.setVisibility(View.VISIBLE);
        }

        // spread :add for new function for send empty msg start
        final boolean hasMessageText ;
        //sprd 572208 start
        if (MmsConfig.get(mHost.getConversationSelfSubId()).getFinalSendEmptyMessageFlag() == 0) {        //only send sms with space message body
            if (messageText != null && messageText.length() > 0) {
                hasMessageText = true;
            } else {  //if message body don't include any char(eg. space), can't send it
                hasMessageText = false;     // if you want to send sms which message body don't include any char(eg. space), set this line to hasMessageText = true
            }
        } else if((MmsConfig.get(mHost.getConversationSelfSubId()).getFinalSendEmptyMessageFlag() == 1)
                || (MmsConfig.get(mHost.getConversationSelfSubId()).getFinalSendEmptyMessageFlag() == 2)){
            hasMessageText = true;
            //sprd 572208 end
        } else {
            hasMessageText = (TextUtils.getTrimmedLength(messageText) > 0);
        }
        // spread :add for new function for send empty msg end

        final boolean hasSubject = (subject.length() > 0);  //Bug1095172
        final boolean hasWorkingDraft = hasMessageText || hasSubject ||
                mBinding.getData().hasAttachments();

        // Update the SMS text counter.
        final int messageCount = draftMessageData.getNumMessagesToBeSent();
        final int codePointsRemaining = draftMessageData.getCodePointsRemainingInCurrentMessage();
        // Show the counter only if:
        // - We are not in MMS mode
        // - We are going to send more than one message OR we are getting close
        boolean showCounter = false;
        /*Modify by SPRD for Bug:562207 Start*/
        if (!draftMessageData.getIsMms() && !"".equals(messageText)/* && (messageCount > 1 ||
                 codePointsRemaining <= CODEPOINTS_REMAINING_BEFORE_COUNTER_SHOWN)*/) {
        /*Modify by SPRD for Bug:562207 End*/
            showCounter = true;
        }

        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
        if (MmsConfig.getSignatureEnabled() && (mSignatureText != null && !("".equals(mSignatureText.trim())))) {
            mSignatureIndicator.setVisibility(View.VISIBLE);
            mSignatureIndicator.setText(mSignatureText);
        } else {
            mSignatureIndicator.setVisibility(View.GONE);
        }
        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
        if (draftMessageData.getIsMms()) {  //display mms attachment's size indiaction first, if message is mms type
            final long attSize = mBinding.getData().getAttachmentsFileSize()*1000/1024; //Modify for bug 712358
            final String formatAttSize = Formatter.formatFileSize(getContext(), attSize);
            final long mmsMaxSize = mBinding.getData().getDefaultMaxMessageSize()*1000/1024; //Modify for bug 712358
            final String formatMmsMaxSize = Formatter.formatFileSize(getContext(), mmsMaxSize);
            mCharCounter.setVisibility(View.VISIBLE);//modify for bug 750113
            mMmsSize.setVisibility(View.VISIBLE);
            mMmsSize.setText((formatAttSize + "/" + formatMmsMaxSize).toUpperCase());
        } else if (showCounter) {   // show sms text counter if message type is sms
            // Update the remaining characters and number of messages required.
            final String counterText = messageCount > 1 ? codePointsRemaining + " / " +
                    messageCount : String.valueOf(codePointsRemaining);
            mCharCounter.setText(counterText);
            mCharCounter.setVisibility(View.VISIBLE);
            mMmsSize.setVisibility(View.GONE);
        } else {    // others, hide them
//modify for bug 750113
            mCharCounter.setVisibility(View.INVISIBLE);/*modified for Bug 770210  begin*/
            mMmsSize.setVisibility(View.INVISIBLE);/*modified for Bug 770210  end*/
        }

        // Update the send message button. Self icon uri might be null if self participant data
        // and/or conversation metadata hasn't been loaded by the host.
        final Uri selfSendButtonUri = getSelfSendButtonIconUri();
        int sendWidgetMode = SEND_WIDGET_MODE_SELF_AVATAR;
        if (selfSendButtonUri != null) {
            if (hasWorkingDraft && isDataLoadedForMessageSend()) {
                //UiUtils.revealOrHideViewWithAnimation(mSendButton, VISIBLE, null);
                mSendButton.setVisibility(VISIBLE);
                if (isOverriddenAvatarAGroup()) {
                    // If the host has overriden the avatar to show a group avatar where the
                    // send button sits, we have to hide the group avatar because it can be larger
                    // than the send button and pieces of the avatar will stick out from behind
                    // the send button.
                    //UiUtils.revealOrHideViewWithAnimation(mSelfSendIcon, GONE, null);
                    mSelfSendIcon.setVisibility(GONE);
                }
                mMmsIndicator.setVisibility(draftMessageData.getIsMms() ? VISIBLE : INVISIBLE);
                mMmsSize.setVisibility(draftMessageData.getIsMms() ? VISIBLE : GONE);
                mCharCounter.setVisibility(draftMessageData.getIsMms() ? GONE : VISIBLE);
                sendWidgetMode = SEND_WIDGET_MODE_SEND_BUTTON;
            } else {
                mSelfSendIcon.setImageResourceUri(selfSendButtonUri);
                if (isOverriddenAvatarAGroup()) {
                    //UiUtils.revealOrHideViewWithAnimation(mSelfSendIcon, VISIBLE, null);
                    mSelfSendIcon.setVisibility(VISIBLE);
                }
                //UiUtils.revealOrHideViewWithAnimation(mSendButton, GONE, null);
                mSendButton.setVisibility(GONE);
                mMmsIndicator.setVisibility(INVISIBLE);
                mMmsSize.setVisibility(GONE);
                if (shouldShowSimSelector(mConversationDataModel.getData())) {
                    sendWidgetMode = SEND_WIDGET_MODE_SIM_SELECTOR;
                }
            }
        } else {
            mSelfSendIcon.setImageResourceUri(null);
        }

        if (mSendWidgetMode != sendWidgetMode || sendWidgetMode == SEND_WIDGET_MODE_SIM_SELECTOR) {
            setSendButtonAccessibility(sendWidgetMode);
            mSendWidgetMode = sendWidgetMode;
        }

        if (MessageData.PROTOCOL_MMS_SMIL == draftMessageData.mProtocol) {
            mComposeEditText.setHint(R.string.smil_edit_hint);
            return;
        }
        // Update the text hint on the message box depending on the attachment type.
        final List<MessagePartData> attachments = draftMessageData.getReadOnlyAttachments();
        final int attachmentCount = attachments.size();
        if (attachmentCount == 0) {
            final SubscriptionListEntry subscriptionListEntry =
                    mConversationDataModel.getData().getSubscriptionEntryForSelfParticipant(
                            mBinding.getData().getSelfId(), false /* excludeDefault */);
            if (subscriptionListEntry == null) {
                mComposeEditText.setHint(R.string.compose_message_view_hint_text);
            } else {
                mComposeEditText.setHint(Html.fromHtml(getResources().getString(
                        R.string.compose_message_view_hint_text_multi_sim,
                        subscriptionListEntry.displayName)));
            }
            /**
             *  Add for bug 806832 for the IME  display in fullscreen under
             * Horizontal screen,the view including the display mHint Text(such as send message,send photos,etc) belongs to the IME,
             * while the mHint Text pass to the IME when it start,and no update when the mComposeEditText's mHint
             * changed,so now we start the IME when the mComposeEditText's mHint changed
             */
            if (mShouldRestartIme) {
                if (inputMethodManager != null) {
                    inputMethodManager.restartInput(mComposeEditText);
                }
            }
        } else {
            int type = -1;
            for (final MessagePartData attachment : attachments) {
                int newType;
                if (attachment.isImage()) {
                    newType = ContentType.TYPE_IMAGE;
                } else if (attachment.isAudio()) {
                    newType = ContentType.TYPE_AUDIO;
                } else if (attachment.isVideo()) {
                    newType = ContentType.TYPE_VIDEO;
                } else if (attachment.isVCard()) {
                    newType = ContentType.TYPE_VCARD;
                } else {
                    newType = ContentType.TYPE_OTHER;
                }

                if (type == -1) {
                    type = newType;
                } else if (type != newType || type == ContentType.TYPE_OTHER) {
                    type = ContentType.TYPE_OTHER;
                    break;
                }
            }

            switch (type) {
                case ContentType.TYPE_IMAGE:
                    mComposeEditText.setHint(getResources().getQuantityString(
                            R.plurals.compose_message_view_hint_text_photo, attachmentCount));
                    break;

                case ContentType.TYPE_AUDIO:
                    mComposeEditText.setHint(getResources().getQuantityString(
                            R.plurals.compose_message_view_hint_text_audio, attachmentCount));
                    break;

                case ContentType.TYPE_VIDEO:
                    mComposeEditText.setHint(getResources().getQuantityString(
                            R.plurals.compose_message_view_hint_text_video, attachmentCount));
                    break;

                case ContentType.TYPE_VCARD:
                    mComposeEditText.setHint(getResources().getQuantityString(
                            R.plurals.compose_message_view_hint_text_vcard, attachmentCount));
                    break;

                case ContentType.TYPE_OTHER:
                    mComposeEditText.setHint(getResources().getQuantityString(
                            R.plurals.compose_message_view_hint_text_attachments, attachmentCount));
                    break;

                default:
                    Assert.fail("Unsupported attachment type!");
                    break;
            }

            /**
             *  Add for bug 806832 for the IME  display in fullscreen under
             * Horizontal screen,this view including the display mHint Text(such as send message,send photos,etc) belongs to the IME,
             * while the mHint Text pass to the IME when it start,and no update when the mComposeEditText's mHint
             * changed,so now we start the IME when the mComposeEditText's mHint changed
             */
            if (mShouldRestartIme) {
                if (inputMethodManager != null) {
                    inputMethodManager.restartInput(mComposeEditText);
                }
            }
        }
    }

    private void setSendButtonAccessibility(final int sendWidgetMode) {
        switch (sendWidgetMode) {
            case SEND_WIDGET_MODE_SELF_AVATAR:
                // No send button and no SIM selector; the self send button is no longer
                // important for accessibility.
                mSelfSendIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                mSelfSendIcon.setContentDescription(null);
                mSendButton.setVisibility(View.GONE);
                setSendWidgetAccessibilityTraversalOrder(SEND_WIDGET_MODE_SELF_AVATAR);
                break;

            case SEND_WIDGET_MODE_SIM_SELECTOR:
                mSelfSendIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                mSelfSendIcon.setContentDescription(getSimContentDescription());
                setSendWidgetAccessibilityTraversalOrder(SEND_WIDGET_MODE_SIM_SELECTOR);
                break;

            case SEND_WIDGET_MODE_SEND_BUTTON:
                mMmsIndicator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                mMmsIndicator.setContentDescription(null);
                setSendWidgetAccessibilityTraversalOrder(SEND_WIDGET_MODE_SEND_BUTTON);
                break;
        }
    }

    private String getSimContentDescription() {
        final SubscriptionListEntry sub = getSelfSubscriptionListEntry();
        if (sub != null) {
            return getResources().getString(
                    R.string.sim_selector_button_content_description_with_selection,
                    sub.displayName);
        } else {
            return getResources().getString(
                    R.string.sim_selector_button_content_description);
        }
    }

    // Set accessibility traversal order of the components in the send widget.
    private void setSendWidgetAccessibilityTraversalOrder(final int mode) {
        if (OsUtil.isAtLeastL_MR1()) {
            mAttachMediaButton.setAccessibilityTraversalBefore(R.id.compose_message_text);
            switch (mode) {
                case SEND_WIDGET_MODE_SIM_SELECTOR:
                    mComposeEditText.setAccessibilityTraversalBefore(R.id.self_send_icon);
                    break;
                case SEND_WIDGET_MODE_SEND_BUTTON:
                    mComposeEditText.setAccessibilityTraversalBefore(R.id.send_message_button);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void afterTextChanged(final Editable editable) {

        /*Add by SPRD for bug 558909  2016.05.05 Start*/
        if(editable == mComposeSubjectText.getText())
            return;
        // Modify for bug 791412 start
//        /*Add by SPRD for bug 558909  2016.05.05 End*/
//        selectionStart = mComposeEditText.getSelectionStart();
//        selectionEnd = mComposeEditText.getSelectionEnd();
//        /* SPRD: modified for bug 544824  begin */
//        if(mBinding.isBound()){
//            if (mComposeEditText.getText().length() == MmsConfig
//                    .get(mBinding.getData().getSelfSubId()).getMaxTextLimit()) {
//                UiUtils.showToast(
//                        R.string.exceed_text_length_limitation);
//                return;
//            }
//            if(selectionStart > 1 && mComposeEditText.getText().length() > MmsConfig
//                    .get(mBinding.getData().getSelfSubId()).getMaxTextLimit()
//                    /*Add for bug 563344*/) {
//                UiUtils.showToast(
//                        R.string.exceed_text_length_limitation);
//                // Add by SPRD for bug 563344
//               // mEnableTextLengthLimitation = false;
//                //editable.delete(selectionStart - 1, selectionEnd);
//                    if (MmsConfig.get(mBinding.getData().getSelfSubId())
//                            .getMaxTextLimit() < selectionEnd) {
//                        if (editable.length() >= selectionEnd)editable.delete(MmsConfig
//                                .get(mBinding.getData().getSelfSubId())
//                                .getMaxTextLimit(), selectionEnd);
//                    } else {
//                        if (editable.length() >= selectionEnd)editable.delete(selectionStart - 1, selectionEnd);
//                    }
//                mComposeEditText.setText(editable);
//                mComposeEditText.setSelection(mComposeEditText.length());
//            }
//        }
        /* SPRD: modified for bug 544824  end */
       // Modify for bug 791412 end
    }

    @Override
    public void beforeTextChanged(final CharSequence s, final int start, final int count,
            final int after) {
           // Modify for bug 791412 start
//        if(mBinding.isBound()){
//            mComposeEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MmsConfig
//                    .get(mBinding.getData().getSelfSubId()).getMaxTextLimit())});
//            /* Add by SPRD for bug 563344 Start */
//            if (mComposeEditText.getText().length() != 0 && mComposeEditText.getText().length() <= MmsConfig
//                    .get(mBinding.getData().getSelfSubId()).getMaxTextLimit()) {
//                //mEnableTextLengthLimitation = true;
//            }
//            /* Add by SPRD for bug 563344 End */
//        }
        // Modify for bug 791412 start
        if (mHost!=null&&mHost.shouldHideAttachmentsWhenSimSelectorShown()) {
            hideSimSelector();
        }
    }

    private void hideSimSelector() {
        if (mInputManager.showHideSimSelector(false /* show */, true /* animate */)) {
            // Now that the sim selector has been hidden, reshow the attachments if they
            // have been hidden.
            hideAttachmentsWhenShowingSims(false /*simPickerVisible*/);
        }
    }

    @Override
    public void onTextChanged(final CharSequence s, final int start, final int before,
            final int count) {
        final BugleActionBarActivity activity = (mOriginalContext instanceof BugleActionBarActivity)
                ? (BugleActionBarActivity) mOriginalContext : null;
        if (activity != null && activity.getIsDestroyed()) {
            LogUtil.v(LogUtil.BUGLE_TAG, "got onTextChanged after onDestroy");

            // if we get onTextChanged after the activity is destroyed then, ah, wtf
            // b/18176615
            // This appears to have occurred as the result of orientation change.
            return;
        }
        mBinding.ensureBound();
        if (getUpdateVisuals()) {//bug825495
            mShouldRestartIme = false;
            updateVisualsOnDraftChanged();
        }
        //bug 990370, begin
        if(FeatureOption.SPRD_ARCHIVE_MENU_DYNAMIC_SUPPORT && archiveChangedListener != null){
            archiveChangedListener.onArchiveChanged(mBinding.getData());
        }
        //bug 990370, end
    }

    @Override
    public PlainTextEditText getComposeEditText() {
        return mComposeEditText;
    }

    public void displayPhoto(final Uri photoUri, final Rect imageBounds) {
        mHost.displayPhoto(photoUri, imageBounds, true /* isDraft */);
    }

    public void updateConversationSelfIdOnExternalChange(final String selfId) {
        updateConversationSelfId(selfId, true /* notify */);
    }

    /**
     * The selfId of the conversation. As soon as the DraftMessageData successfully loads (i.e.
     * getSelfId() is non-null), the selfId in DraftMessageData is treated as the sole source
     * of truth for conversation self id since it reflects any pending self id change the user
     * makes in the UI.
     */
    public String getConversationSelfId() {
        if(mBinding.isBound())
        return mBinding.getData().getSelfId();
        return "";
    }

    public void selectSim(SubscriptionListEntry subscriptionData) {
        final String oldSelfId = getConversationSelfId();
        final String newSelfId = subscriptionData.selfParticipantId;
        Assert.notNull(newSelfId);
        // Don't attempt to change self if self hasn't been loaded, or if self hasn't changed.
        if ((oldSelfId == null || TextUtils.equals(oldSelfId, newSelfId))&& !mIsSendButtonPressed) {
            return;
        }
        updateConversationSelfId(newSelfId, true /* notify */);
        if(mIsSendButtonPressed && isAlertSelectDlg()){
    if(mSendButton != null ){
        setIsAfterSimSelected(true);
        if(DEBUG)  Log.d("ComposeMessageView-ask-everytime","[ComposeMessageView]=====selectSim===invoke performClick to sendMessage");
 //       mSendButton.performClick();
                 sendMessageIgnoreMessageSizeLimit();
                 setIsSendButtonPressed(false);
                 setIsAfterSimSelected(false);
        }
        }//modify 1146740 by sprd for ask-everytime  feature end
    }

    public void hideAllComposeInputs(final boolean animate) {
        mInputManager.hideAllInputs(animate);
    }

    public void saveInputState(final Bundle outState) {
        mInputManager.onSaveInputState(outState);
    }

    public void resetMediaPickerState() {
        mInputManager.resetMediaPickerState();
    }

    public boolean onBackPressed() {
        return mInputManager.onBackPressed();
    }

    public boolean onNavigationUpPressed() {
        return mInputManager.onNavigationUpPressed();
    }

    public boolean updateActionBar(final ActionBar actionBar) {
        return mInputManager != null ? mInputManager.updateActionBar(actionBar) : false;
    }

    public static boolean shouldShowSimSelector(final ConversationData convData) {
        return OsUtil.isAtLeastL_MR1() &&
                convData.getSelfParticipantsCountExcludingDefault(true /* activeOnly */) > 1;
    }

    public void sendMessageIgnoreMessageSizeLimit() {
        sendMessageInternal(false /* checkMessageSize */);
    }

    public void onAttachmentPreviewLongClicked() {
        //add for Bug 815358 start
        if (mHost.isEmptyRecipientConversaton()) {
           UiUtils.showToast(R.string.add_contact_toast);
           return;
        }
        //add for Bug 815358 end
        mHost.showAttachmentChooser();
    }

    @Override
    public void onDraftAttachmentLoadFailed() {
        mHost.notifyOfAttachmentLoadFailed();
    }

    @Override
    public void onDraftAttachmentOverSizeReached(final DraftMessageData data,
            final MessagePartData attachment) {
        mHost.showAttachmentExceededDialog(getAttachemtString(attachment));
    }

    private boolean isOverriddenAvatarAGroup() {
        final Uri overridenSelfUri = mHost.getSelfSendButtonIconUri();
        if (overridenSelfUri == null) {
            return false;
        }
        return AvatarUriUtil.TYPE_GROUP_URI.equals(AvatarUriUtil.getAvatarType(overridenSelfUri));
    }

    @Override
    public void setAccessibility(boolean enabled) {
        if (enabled) {
            mAttachMediaButton.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            mComposeEditText.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            mSendButton.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setSendButtonAccessibility(mSendWidgetMode);
        } else {
            mSelfSendIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            mComposeEditText.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            mSendButton.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            mAttachMediaButton.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    private int getAttachemtString(final MessagePartData attachment) {
        /* Add by SPRD for Bug 527835 Start */
        int stringid =R.string.share_nofile_exceeded;
        final Uri uri = attachment.getContentUri();
        String contentType = attachment.getContentType();
        if(ContentType.isDrmType(contentType)){
            String dataPath = MessagingDrmSession.get().getPath(uri);
            contentType = MessagingDrmSession.get().getDrmOrigMimeType(dataPath, ContentType.APP_DRM_CONTENT);
        }
        if (ContentType.isImageType(contentType)) {
            stringid = R.string.share_image_exceeded;
        } else if(ContentType.isAudioType(contentType)) {
            stringid = R.string.share_audio_exceeded;
        } else if(ContentType.isVideoType(contentType)) {
            stringid = R.string.share_video_exceeded;
        }else if(ContentType.isVCardType(contentType)){
            //UNISOC:Add for bug 1362676
            stringid=R.string.share_vcard_exceeded;
        }
        /* Add by SPRD for Bug 527835 end */
        return stringid;
    }

    /* Add by SPRD for bug 563344 Start */
    @Override
    public void onDraftLoadDone(DraftMessageData data) {
        Log.d("ComposeMessageView", "onDraftLoadDone");
        mIsDraftLoadDone = true;
    }
    /* Add by SPRD for bug 563344 End */

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        try {
            if (visibility==View.VISIBLE) {
                DraftMessageData draft = mBinding.getData();
                boolean hasDrmData = false;
                for (MessagePartData part : draft.getAttachments()) {
                    if (ContentType.isDrmType(part.getContentType())) {
                        hasDrmData = true;
                        break;
                    }
                }
                if (hasDrmData) {
                    draft.dispatchChanged(DraftMessageData.ATTACHMENTS_CHANGED);
                }
            }
        }catch (Exception ex){
            Log.e("ComposeMessageView", "onWindowVisibilityChanged: ex "+ex.fillInStackTrace());
        }
        super.onWindowVisibilityChanged(visibility);
    }
    private static  boolean mIsNoupdateVisuals=true;
    private void setUpdateVisuals(boolean state){
        mIsNoupdateVisuals=state;
    }
    private boolean getUpdateVisuals(){
        return mIsNoupdateVisuals;
    }
	  /**************************
     * White Box testing start
     **********************************/
    private final static int SEND_MMSSMS_TEST = 100;
    private final static int SEND_BORDCAST_TEST = 200;

    private Handler mTestHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case SEND_MMSSMS_TEST:
                    Log.d(WhiteTestType.TAG, "---------sendMessageWhiteTest-----------");
                    final Uri selfSendButtonUri = getSelfSendButtonIconUri();
                    final String messageText = mComposeEditText.getText().toString();
                    final boolean hasMessageText;
                    if (messageText != null && messageText.length() > 0) {
                        hasMessageText = true;
                    } else {
                        hasMessageText = false;
                    }
                    final String subject = mComposeSubjectText.getText().toString();
                    final boolean hasSubject = (TextUtils.getTrimmedLength(subject) > 0);
                    final boolean hasWorkingDraft = hasMessageText || hasSubject ||
                            mBinding.getData().hasAttachments();
                    if (selfSendButtonUri != null && hasWorkingDraft && isDataLoadedForMessageSend()) {
                        sendMessageIgnoreMessageSizeLimit();
                    }
                    mTestHandler.sendEmptyMessageDelayed(SEND_BORDCAST_TEST, 300);
                    break;
                case SEND_BORDCAST_TEST:
                    mHost.sendConverFramBroadcase();
                    break;
            }
        }
    };


    @Override
    public String getDraftWhiteTest() {
        return mHost!=null?mHost.getConverWhiteTest():"";//bug833343
    }

    @Override
    public void sendMessageWhiteTest() {
        selectTestSim(mHost.getConverTestSim());
        mTestHandler.sendEmptyMessageDelayed(SEND_MMSSMS_TEST, 300);
    }


    public void selectTestSim(String sim) {
        List<SubscriptionListEntry> testListEntry = mConversationDataModel.getData().getSubscriptionListData().getActiveSubscriptionEntriesExcludingDefault();
        if (testListEntry.size() > 1) {
            String newSelfid = null;
            if (WhiteTestType.TEST_SIM_ONE.equals(sim)) {
                newSelfid = testListEntry.get(0).selfParticipantId;
            } else if (WhiteTestType.TEST_SIM_TWO.equals(sim)) {
                newSelfid = testListEntry.get(1).selfParticipantId;
            }

            if (newSelfid != null && mBinding.isBound()) {
                mBinding.getData().setTestSelfId(newSelfid);
            }
            Log.d(WhiteTestType.TAG,"switch "+sim);
        }


    }
    /**************************White Box testing end**********************************/
    //bug 990370, begin
    private OnArchiveChangedListener archiveChangedListener = null;

    public interface OnArchiveChangedListener{
        void onArchiveChanged(DraftMessageData data);
    }

    public void setOnArchiveChangedListener(OnArchiveChangedListener listener){
        this.archiveChangedListener = listener;
    }
    //bug 990370, end
}
