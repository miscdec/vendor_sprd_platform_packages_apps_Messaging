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

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.text.Editable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.datamodel.MessagingContentProvider;
import com.android.messaging.datamodel.action.DeleteConversationAction;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.binding.ImmutableBindingRef;
import com.android.messaging.datamodel.data.ConversationData;
import com.android.messaging.datamodel.data.ConversationData.ConversationDataListener;
import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.datamodel.data.ConversationParticipantsData;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.DraftMessageData.DraftMessageDataListener;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.datamodel.data.SubscriptionListData.SubscriptionListEntry;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.ConversationDrawables;
import com.android.messaging.ui.SnackBar;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.contact.AddContactsConfirmationDialog;
import com.android.messaging.ui.contact.ContactRecipientAutoCompleteView;
import com.android.messaging.ui.conversation.ComposeMessageView.IComposeMessageViewHost;
import com.android.messaging.ui.conversation.ConversationInputManager.ConversationInputHost;
import com.android.messaging.ui.conversation.ConversationMessageView.ConversationMessageViewHost;
import com.android.messaging.ui.mediapicker.MediaPicker;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.Assert;
import com.android.messaging.util.AvatarUriUtil;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.BuglePrefsKeys;
import com.android.messaging.util.ChangeDefaultSmsAppHelper;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.ImeUtil;
import com.android.messaging.util.LinkifyUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.TextUtil;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.UriUtil;
import com.google.common.annotations.VisibleForTesting;
import com.gstd.callme.UI.inter.IOrgCallback;
import com.gstd.callme.engine.SmartSmsEngineManager;
import com.sprd.messaging.drm.MessagingDrmSession;
import com.sprd.messaging.simmessage.SimUtils;
import com.sprd.messaging.smart.CtccSmartSdk;
import com.sprd.messaging.smart.SmartMessageObserver;
import com.sprd.messaging.smart.SmartMessageServer;
import com.sprd.messaging.sms.commonphrase.ui.ShowPharseActivity;
import com.sprd.messaging.ui.smsmergeforward.SmsMergeForwardActivity;
import com.sprd.messaging.util.FeatureOption;
import com.sprd.messaging.util.StorageUtil;
import com.sprd.messaging.util.SystemAdapter;
import com.sprd.messaging.util.Utils;
import com.unicom.callme.UI.inter.ProcessMenuOperationListener;
import com.unicom.callme.engine.SmartSmsEngine;
import com.unicom.callme.outerentity.CardInfo;
import com.unicom.callme.outerentity.MenuInfo;
import com.unicom.callme.outerentity.OrgInfo;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import cn.cmcc.online.smsapi.TerminalApi;
import cn.cmcc.online.smsapi.entity.SmsPortData;


/**
 * Shows a list of messages/parts comprising a conversation.
 */
public class ConversationFragment extends Fragment implements ConversationDataListener,
        IComposeMessageViewHost, ConversationMessageViewHost, ConversationInputHost,
        DraftMessageDataListener, SmartMessageObserver {

    private LinearLayout mServerMenu;    /* add for SmartMessage ServerMenu */
    private String mPort;    /* add for SmartMessage  Port*/
    private AlertDialog mCancelDialog = null;  //bug 1196417
    public interface ConversationFragmentHost extends ImeUtil.ImeStateHost {
        void onStartComposeMessage();

        void onConversationMetadataUpdated();

        boolean shouldResumeComposeMessage();

        void onFinishCurrentConversation();

        void invalidateActionBar();

        ActionMode startActionMode(ActionMode.Callback callback);

        void dismissActionMode();

        ActionMode getActionMode();

        void onConversationMessagesUpdated(int numberOfMessages);

        void onConversationParticipantDataLoaded(int numberOfParticipants);

        boolean isActiveAndFocused();

        /*Add by SPRD for bug581044  2016.07.08 Start*/
        void setKeepingMessageData(MessageData data);

        String getOldConversationId();

        void onSendMessageAction();        //if user click send button for sending message, user will can't edit recipients
        /*Add by SPRD for bug581044  2016.07.08 End*/

        void needReadEmailAttachmentPermission();

        void updateContactPickerFragmentMenu();
        /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
        ConversationActivityUiState getConversationUiState();
        /*Modify by SPRD for bug843334  Start*/
        ContactRecipientAutoCompleteView getContactView();
         /*Modify by SPRD for bug843334  end*/
        /*Add by SPRD for edit contact in draft 2016.09.06 End*/
		/**************************White Box testing Start**********************************/
        String getWhiteTest();
        String getestSim();
        void sendConverActivityBroadcase();
        /**************************White Box testing end**********************************/
    }

    private Context mContext;
    public static final String FRAGMENT_TAG = "conversation";

    public static final String TAG = "ConversationFragment";
    public static final String SMART_TAG = LogUtil.BUGLE_SMART_TAG;   /* add for SmartMessage start*/

    static final int REQUEST_CHOOSE_ATTACHMENTS = 2;
    public static final int REQUEST_INSERT_PHRASE = 3;//sprd add for common message
    private static final int JUMP_SCROLL_THRESHOLD = 15;
    // We animate the message from draft to message list, if we the message doesn't show up in the
    // list within this time limit, then we just do a fade in animation instead
    public static final int MESSAGE_ANIMATION_MAX_WAIT = 500;

    private ComposeMessageView mComposeMessageView;
    private RecyclerView mRecyclerView;
    private ConversationMessageAdapter mAdapter;
    private ConversationFastScroller mFastScroller;

    private Menu mActionBarMenuList;
    private boolean mIsOnlyDraft = false;

    private View mConversationComposeDivider;
    private ChangeDefaultSmsAppHelper mChangeDefaultSmsAppHelper;

    private String mConversationId;
    /*Add by SPRD for bug581044  2016.07.08 Start*/
    private boolean isEmptyRecipientConversaton = false;
    /*Add by SPRD for bug581044  2016.07.08 End*/
    // If the fragment receives a draft as part of the invocation this is set
    private MessageData mIncomingDraft;

    // This binding keeps track of our associated ConversationData instance
    // A binding should have the lifetime of the owning component,
    //  don't recreate, unbind and bind if you need new data
    @VisibleForTesting
    final Binding<ConversationData> mBinding = BindingBase.createBinding(this);

    // Saved Instance State Data - only for temporal data which is nice to maintain but not
    // critical for correctness.
    private static final String SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY = "conversationViewState";
    private Parcelable mListState;
    private static final String CLEAR_LOCAL_DRAFT_STATE = "clear_local_draft_state";

    private ConversationFragmentHost mHost;

    protected List<Integer> mFilterResults;

    // The minimum scrolling distance between RecyclerView's scroll change event beyong which
    // a fling motion is considered fast, in which case we'll delay load image attachments for
    // perf optimization.
    private int mFastFlingThreshold;

    // ConversationMessageView that is currently selected
    private ConversationMessageView mSelectedMessage;

    /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
    private ConversationActivityUiState mUistate;
    /*Add by SPRD for edit contact in draft 2016.09.06 End*/

    // Attachment data for the attachment within the selected message that was long pressed
    private MessagePartData mSelectedAttachment;

    AlertDialog mSimSelectDialog;

    private AlertDialog mDraftConfirmDialog;//add for bug650418
    private AlertDialog mDeleteConfirmDialog; //Bug 918700
    /*modify it by 811806 begin*/
    private DisplayMetrics mDisplayMetrics;
    private static final double ratio = 0.5;//for bug864431
    /*modify it by 811806 end*/

    private boolean mIsRequestingPermission = false;
    private final String mReadEmailAttachPermission = "com.android.email.permission.READ_ATTACHMENT";
    private final String mEmailUriHeader = "content://com.android.email.attachmentprovider";
    private final String mChooserActivity = "com.android.internal.app.ChooserActivity";

    // Normally, as soon as draft message is loaded, we trust the UI state held in
    // ComposeMessageView to be the only source of truth (incl. the conversation self id). However,
    // there can be external events that forces the UI state to change, such as SIM state changes
    // or SIM auto-switching on receiving a message. This receiver is used to receive such
    // local broadcast messages and reflect the change in the UI.
    private final BroadcastReceiver mConversationSelfIdChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String conversationId =
                    intent.getStringExtra(UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID);
            final String selfId =
                    intent.getStringExtra(UIIntents.UI_INTENT_EXTRA_CONVERSATION_SELF_ID);
            Assert.notNull(conversationId);
            Assert.notNull(selfId);
            // spread: fixed for bug  522393 start
            if (mBinding.isBound()) {
                if (TextUtils.equals(mBinding.getData().getConversationId(), conversationId) && !MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getUsingSimInSettingsEnabled()) {/*modified for Bug 724272*/
                    mComposeMessageView.updateConversationSelfIdOnExternalChange(selfId);
                }
            }
            // spread: fixed for bug  522393 end
        }
    };

    // Flag to prevent writing draft to DB on pause
    private boolean mSuppressWriteDraft;
    /* Add by SPRD for bug 583553 2016.07.27 Start */
    private boolean mLoadDraftDone = false;
    private boolean mOnAttachmentChosen = false;
    private int mOldAttachmentCount = 0;
    /* Add by SPRD for bug 583553 2016.07.27 End */

    // Indicates whether local draft should be cleared due to external draft changes that must
    // be reloaded from db
    private boolean mClearLocalDraft;
    private ImmutableBindingRef<DraftMessageData> mDraftMessageDataModel;

    // flag for mediaPicker paused when
    private boolean mPauseRusume = false;
    //sprd fix bug 562320 start
    private SmileMessageHandle mSmilDraftListen;

    private static class SmileMessageHandle extends Handler {
        private WeakReference<ConversationFragment> wref;

        public SmileMessageHandle(ConversationFragment act) {
            wref = new WeakReference<ConversationFragment>(act);
        }

        @Override
        public void handleMessage(Message msg) {
            ConversationFragment frag = wref.get();
            Log.d(TAG, "smil handle message "+msg);
            if (frag == null) {
                return;
            }
            switch (msg.what) {
                case GlobleUtil.SMIL_DRAFT_MSG:
                    if (GlobleUtil.getEditedDraftMessageDate() == null) {
                        Log.d(TAG, "smilhandler[ smil attachment is : " + GlobleUtil.isSmilAttament + "  mClearLocalDraft: " + frag.mClearLocalDraft+"]");
                        //mComposeMessageView.setDraftMessage(null);
                        // ConversationMessageData messageData = mSelectedMessage.getData();
                        frag.mComposeMessageView.requestDraftMessage(true);
                    }
                    break;
            }
        }
    }
    //sprd fix bug 562320 end

    private boolean isScrolledToBottom() {
        if (mRecyclerView.getChildCount() == 0) {
            return true;
        }
        final View lastView = mRecyclerView.getChildAt(mRecyclerView.getChildCount() - 1);
        int lastVisibleItem = ((LinearLayoutManager) mRecyclerView
                .getLayoutManager()).findLastVisibleItemPosition();
        if (lastVisibleItem < 0) {
            // If the recyclerView height is 0, then the last visible item position is -1
            // Try to compute the position of the last item, even though it's not visible
            final long id = mRecyclerView.getChildItemId(lastView);
            final RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForItemId(id);
            if (holder != null) {
                lastVisibleItem = holder.getAdapterPosition();
            }
        }
        final int totalItemCount = mRecyclerView.getAdapter().getItemCount();
        final boolean isAtBottom = (lastVisibleItem + 1 == totalItemCount);
        return isAtBottom && lastView.getBottom() <= mRecyclerView.getHeight();
    }

    private void scrollToBottom(final boolean smoothScroll) {
        if (mAdapter.getItemCount() > 0) {
            scrollToPosition(mAdapter.getItemCount() - 1, smoothScroll);
        }
    }

    private int mScrollToDismissThreshold;
    private final RecyclerView.OnScrollListener mListScrollListener =
            new RecyclerView.OnScrollListener() {
                // Keeps track of cumulative scroll delta during a scroll event, which we may use to
                // hide the media picker & co.
                private int mCumulativeScrollDelta;
                private boolean mScrollToDismissHandled;
                private boolean mWasScrolledToBottom = true;
                private int mScrollState = RecyclerView.SCROLL_STATE_IDLE;

                @Override
                public void onScrollStateChanged(final RecyclerView view, final int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        // Reset scroll states.
                        mCumulativeScrollDelta = 0;
                        mScrollToDismissHandled = false;
                    } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        mRecyclerView.getItemAnimator().endAnimations();
                    }
                    mScrollState = newState;

                    if (RecyclerView.SCROLL_STATE_IDLE == mScrollState) {
                        final LinearLayoutManager layoutManager =
                                (LinearLayoutManager) mRecyclerView.getLayoutManager();
                        final int firstVisibleItemPosition =
                                layoutManager.findFirstVisibleItemPosition();
                        final int lastVisibleItemPosition =
                                layoutManager.findLastVisibleItemPosition();
                        mAdapter.setScollState(newState, firstVisibleItemPosition, lastVisibleItemPosition);
                    }

                }

                @Override
                public void onScrolled(final RecyclerView view, final int dx, final int dy) {
                    if (mScrollState == RecyclerView.SCROLL_STATE_DRAGGING &&
                            !mScrollToDismissHandled) {
                        mCumulativeScrollDelta += dy;
                        // Dismiss the keyboard only when the user scroll up (into the past).
                        if (mCumulativeScrollDelta < -mScrollToDismissThreshold) {
                            mComposeMessageView.hideAllComposeInputs(false /* animate */);
                            mScrollToDismissHandled = true;
                        }
                    }
                    if (mWasScrolledToBottom != isScrolledToBottom()) {
                        mConversationComposeDivider.animate().alpha(isScrolledToBottom() ? 0 : 1);
                        mWasScrolledToBottom = isScrolledToBottom();
                    }
                }
            };

    //Bug 912660 begin
    public void saveAttachmentToStorage(final Uri uri) {
        if (mSelectedMessage == null ||getActivity() == null) {
            return ;
        }
        final ConversationMessageData data = mSelectedMessage.getData();
        final SaveAttachmentTask saveAttachmentTask = new SaveAttachmentTask(
                getActivity());
        for (final MessagePartData part : data.getAttachments()) {
            // FIXME: drm files cannot be saved? delete it, if possible.
            /*Modify by SPRD for Bug:574142 Start*/
            if (part.isDrmType() || ContentType.isDrmType(part.getContentType())) {
                UiUtils.showToastAtBottom(R.string.drm_can_not_processed);
            } else {
                if (uri != null) {
                    saveAttachmentTask.addAttachmentToSave(part.getContentUri(),
                            part.getContentType(), uri);
                } else {
                    saveAttachmentTask.addAttachmentToSave(part.getContentUri(),
                            part.getContentType());
                }
            }
            /*Modify by SPRD for Bug:574142 End*/
        }
        if (saveAttachmentTask.getAttachmentCount() > 0) {
            saveAttachmentTask.executeOnThreadPool();
            //mHost.dismissActionMode();
        }
    }
    //Bug 912660 end

    private final ActionMode.Callback mMessageActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(final ActionMode actionMode, final Menu menu) {
            if (mSelectedMessage == null ||getActivity() == null) {/*add for Bug 766488 */
                return false;
            }
            final ConversationMessageData data = mSelectedMessage.getData();
            final MenuInflater menuInflater = getActivity().getMenuInflater();
            //for bug 757753
            if (menuInflater == null) {
                return false;
            }
            menuInflater.inflate(R.menu.conversation_fragment_select_menu, menu);
            menu.findItem(R.id.action_download).setVisible(data.getShowDownloadMessage());
            menu.findItem(R.id.action_send).setVisible(data.getShowResendMessage());

            // ShareActionProvider does not work with ActionMode. So we use a normal menu item.
            menu.findItem(R.id.share_message_menu).setVisible(data.getCanForwardMessage());
            Log.d(TAG, "lxg mSelectedAttachment != null:" + (mSelectedAttachment != null));
            menu.findItem(R.id.save_attachment).setVisible(mSelectedAttachment != null);
            menu.findItem(R.id.forward_message_menu).setVisible(data.getCanForwardMessage());

            // TODO: We may want to support copying attachments in the future, but it's
            // unclear which attachment to pick when we make this context menu at the message level
            // instead of the part level
            menu.findItem(R.id.copy_text).setVisible(data.getCanCopyMessageToClipboard());
            //add for bug 566254 begin
            final List<SubscriptionInfo> InfoList = SystemAdapter.getInstance().getActiveSubInfoList();
            int subId = 0;
            if (InfoList != null && InfoList.size() != 0)
                subId = InfoList.get(0).getSubscriptionId();
            //if find active sim(s), message type is sms and it's body lenght > 0, show action_copy_to_sim icon for copy sms to sim card
            if ((InfoList != null && InfoList.size() != 0)
                    && canStoreToSim(subId, data)) {
                menu.findItem(R.id.action_copy_to_sim).setVisible(true);
            } else {
                menu.findItem(R.id.action_copy_to_sim).setVisible(false);
            }
            //add for bug 566254 end
            return true;
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode actionMode, final Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode actionMode, final MenuItem menuItem) {
            final ConversationMessageData data = mSelectedMessage.getData();
            final String messageId = data.getMessageId();
            switch (menuItem.getItemId()) {
                case R.id.save_attachment:
                    if (OsUtil.hasStoragePermission()) {
                        //Bug 912660 begin
                        StorageUtil storageUtil = new StorageUtil(getActivity());
                        if(storageUtil.sdcardExist()){
                            final Uri uri = new StorageUtil().getDownloadAccessUri(getActivity());
                            Log.d(TAG, "save_attachment, uri:" + uri);
                            if(uri != null){
                                saveAttachmentToStorage(uri);
                            }else{
                                storageUtil.requestSdcardPermmission();
                            }
                        }else {
                            saveAttachmentToStorage(null);
                        }
                        //Bug 912660 end
                    } else {
                        getActivity().requestPermissions(
                               new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
                    }
                    return true;
                case R.id.action_delete_message:
                    if (mSelectedMessage != null) {
                        deleteMessage(messageId);
                    }
                    return true;
                case R.id.action_download:
                    if (mSelectedMessage != null) {
                        retryDownload(data);
                        //mHost.dismissActionMode();
                    }
                    return true;
                case R.id.action_send:
                    if (mSelectedMessage != null) {
                        retrySend(messageId);
                        mHost.dismissActionMode();
                    }
                    return true;
                case R.id.copy_text:
                    Assert.isTrue(data.hasText());
                    final ClipboardManager clipboard = (ClipboardManager) getActivity()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(
                            ClipData.newPlainText(null /* label */, data.getText()));
                    //mHost.dismissActionMode();
                    return true;
                case R.id.details_menu:
                    MessageDetailsDialog.show(
                            getActivity(), data, mBinding.getData().getParticipants(),
                            mBinding.getData().getSelfParticipantById(data.getSelfParticipantId()));
                    //mHost.dismissActionMode();
                    return true;
                case R.id.share_message_menu:
                    if (!isChooserActivityExists()){
                        shareMessage(data);
                    }
                    //mHost.dismissActionMode();
                    return true;
                case R.id.forward_message_menu:
                    // TODO: Currently we are forwarding one part at a time, instead of
                    // the entire message. Change this to forwarding the entire message when we
                    // use message-based cursor in conversation.
                    final MessageData message = mBinding.getData().createForwardedMessage(data);
                    UIIntents.get().launchForwardMessageActivity(getActivity(), message);
                    //mHost.dismissActionMode();
                    return true;

                case R.id.action_copy_to_sim:
                    List<String> smsUriList = new ArrayList<>();
                    String smsUri = data.getSmsMessageUri();
                    smsUriList.add(smsUri);
                    //createSimSelectDialog(smsUriList);
                    final List<SubscriptionInfo> InfoList = SystemAdapter.getInstance().getActiveSubInfoList();
                    int subId = 0;
                    if (InfoList != null && InfoList.size() != 0) {
                        subId = InfoList.get(0).getSubscriptionId();
                        SimUtils.copySmsToSim(getContext(), smsUriList, subId);
                    }
                    //mHost.dismissActionMode();
                    return true;
            }
            return false;
        }

        private void playSlideshowMessage(final ConversationMessageData data) {
            String mmsUri = data.getSmsMessageUri();
            Uri uri = Uri.parse(mmsUri);
            UIIntents.get().launchSlideshowActivity(mContext, uri, 0);
        }

        private void shareMessage(final ConversationMessageData data) {
            // Figure out what to share.
            MessagePartData attachmentToShare = mSelectedAttachment;
            // If the user long-pressed on the background, we will share the text (if any)
            // or the first attachment.
            if (mSelectedAttachment == null
                    && TextUtil.isAllWhitespace(data.getText())) {
                final List<MessagePartData> attachments = data.getAttachments();
                if (attachments.size() > 0) {
                    attachmentToShare = attachments.get(0);
                }
            }

            final Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            if (attachmentToShare == null) {
                shareIntent.putExtra(Intent.EXTRA_TEXT, data.getText());
                shareIntent.setType("text/plain");
            } else {
                shareIntent.putExtra(
                        Intent.EXTRA_STREAM, attachmentToShare.getContentUri());
                shareIntent.setType(attachmentToShare.getContentType());
            }
            final CharSequence title = getResources().getText(R.string.action_share);
            startActivity(Intent.createChooser(shareIntent, title));
        }

        @Override
        public void onDestroyActionMode(final ActionMode actionMode) {
            selectMessage(null);
        }
    };

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        /*Modify by SPRD for bug843334  Start*/
        if(mHost !=null && mHost.getContactView() !=null){
            ImeUtil.get().showImeKeyboard(getActivity(), mHost.getContactView());
        }
        /*Modify by SPRD for bug843334  end*/
        mFastFlingThreshold = getResources().getDimensionPixelOffset(
                R.dimen.conversation_fast_fling_threshold);
        mAdapter = new ConversationMessageAdapter(getActivity(), null, this,
                null,
                // Sets the item click listener on the Recycler item views.
                new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        final ConversationMessageView messageView = (ConversationMessageView) v;
                        handleMessageClick(messageView);
                    }
                },
                new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(final View view) {
                        selectMessage((ConversationMessageView) view);
                        return true;
                    }
                }
        );
        //sprd fix bug 562320 start
        mSmilDraftListen = new SmileMessageHandle(this);
        GlobleUtil.registerHandler(TAG, mSmilDraftListen);
        //sprd fix bug 562320 end
        if (savedInstanceState != null) {
            mClearLocalDraft = savedInstanceState.getBoolean(CLEAR_LOCAL_DRAFT_STATE);
            Log.d(TAG, "savedInstanceState is not null and mClearLocalDraft:" + mClearLocalDraft);
        }
        /*modify it by 811806 begin*/
        mDisplayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mDisplayMetrics);
        /*modify it by 811806 end*/
        getActivity().registerReceiver(mSimInOutReceiver, mSimFilter);
        getActivity().registerReceiver(mAirPlaneModeReceiver, mAirPlaneModeFilter);
        mContext = getActivity();
        if(Utils.hasNavigationBar(mContext)){
            Log.w(TAG,"isNavigationBarShowing"+Utils.isNavigationBarShowing(getActivity()));
            ContentResolver cr = getActivity().getContentResolver();
            cr.registerContentObserver(
                    Settings.System.getUriFor("show_navigationbar"), false, NavigationBarShowingObserver);
        }
    }

    /**
     * setConversationInfo() may be called before or after onCreate(). When a user initiate a
     * conversation from compose, the ConversationActivity creates this fragment and calls
     * setConversationInfo(), so it happens before onCreate(). However, when the activity is
     * restored from saved instance state, the ConversationFragment is created automatically by
     * the fragment, before ConversationActivity has a chance to call setConversationInfo(). Since
     * the ability to start loading data depends on both methods being called, we need to start
     * loading when onActivityCreated() is called, which is guaranteed to happen after both.
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Delay showing the message list until the participant list is loaded.
        mRecyclerView.setVisibility(View.INVISIBLE);

        /*add by sprd for Bug 630177 start*/
        if (mHost == null) {
            Log.d(TAG, "onActivityCreated mHost is null");
            return;
        }
        /*add by sprd for Bug 630177 end*/

        mBinding.ensureBound();
        mBinding.getData().init(getLoaderManager(), mBinding);

        // Build the input manager with all its required dependencies and pass it along to the
        // compose message view.
        final ConversationInputManager inputManager = new ConversationInputManager(
                getActivity(), this, mComposeMessageView, mHost, getFragmentManagerToUse(),
                mBinding, mComposeMessageView.getDraftDataModel(), savedInstanceState);
        mComposeMessageView.setInputManager(inputManager);
        mComposeMessageView.setConversationDataModel(BindingBase.createBindingReference(mBinding));
        mHost.invalidateActionBar();

        mDraftMessageDataModel =
                BindingBase.createBindingReference(mComposeMessageView.getDraftDataModel());
        mDraftMessageDataModel.getData().addListener(this);
        mLastOrientation = mContext.getResources().getConfiguration().orientation;  /*added by sprd  for Bug 649312  */
    }
    private ContentObserver NavigationBarShowingObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
               mHandler.postDelayed(new Runnable() {
                     @Override
                     public void run() {
                     Log.d(SMART_TAG, " refreshPopupWindowByNavigationBarShowingxxxx ");
                         refreshPopupWindowByNavigationBarShowing();
                     }
              }, 200);
        }
    };

    /* add for SmartMessage end*/
    public void onAttachmentChoosen() {
        // Attachment has been choosen in the AttachmentChooserActivity, so clear local draft
        // and reload draft on resume.
        mClearLocalDraft = true;
        // Add for bug 583553
        mOnAttachmentChosen = true;
    }

    private int getScrollToMessagePosition() {
        final Activity activity = getActivity();
        if (activity == null) {
            return -1;
        }

        final Intent intent = activity.getIntent();
        if (intent == null) {
            return -1;
        }

        return intent.getIntExtra(UIIntents.UI_INTENT_EXTRA_MESSAGE_POSITION, -1);
    }

    private void clearScrollToMessagePosition() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final Intent intent = activity.getIntent();
        if (intent == null) {
            return;
        }
        intent.putExtra(UIIntents.UI_INTENT_EXTRA_MESSAGE_POSITION, -1);
    }

    private final Handler mHandler = new Handler();

    private AlertDialog mShowAppStoreDialog;
    private void ShowAppStoreDialog(final String [] appStrings){
        mShowAppStoreDialog = new AlertDialog.Builder(mContext).setTitle(R.string.action_download)
                .setMessage(appStrings[2]).setPositiveButton(android.R.string.ok,
                        new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                Uri uri = Uri.parse("market://details?id=" + appStrings[0]);
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                boolean b = hasMarketApp(mContext, intent);
                                if (b) {
                                    try {
                                        mContext.startActivity(intent);
                                    } catch (Exception e) {
                                        Toast.makeText(mContext, R.string.no_market_store_app, Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(mContext, R.string.no_market_store_app, Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mShowAppStoreDialog.show();
    }

    /**
     * @param context
     * @param marketIntent
     * @return true/false
     */
    public static boolean hasMarketApp(Context context, Intent marketIntent) {
        List<ResolveInfo> localList = context.getPackageManager().queryIntentActivities(marketIntent, PackageManager.GET_RESOLVED_FILTER);
        if ((localList != null) && (localList.size() > 0)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.conversation_fragment, container, false);
        mServerMenu = (LinearLayout)view.findViewById(R.id.serverMenu);    /* add for SmartMessage */

        mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        final LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        manager.setStackFromEnd(true);
        manager.setReverseLayout(false);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(manager);
        //for bug687038 begin
//                if(!MmsConfig.getCmccSdkEnabled()){
//        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
//                    }
        //for bug687038 end
        mRecyclerView.setAdapter(mAdapter);
        // for Bug 757256 begin
        ((SimpleItemAnimator)(mRecyclerView.getItemAnimator())).setSupportsChangeAnimations(false);
        //for Bug 757256 end

        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY);
        }

        mConversationComposeDivider = view.findViewById(R.id.conversation_compose_divider);
        mScrollToDismissThreshold = ViewConfiguration.get(getActivity()).getScaledTouchSlop();
        mRecyclerView.addOnScrollListener(mListScrollListener);
        mFastScroller = ConversationFastScroller.addTo(mRecyclerView,
                UiUtils.isRtlMode() ? ConversationFastScroller.POSITION_LEFT_SIDE :
                        ConversationFastScroller.POSITION_RIGHT_SIDE);

        mComposeMessageView = (ComposeMessageView)
                view.findViewById(R.id.message_compose_view_container);
        //bug 990370, begin
        mComposeMessageView.setOnArchiveChangedListener(data -> setMessageContentStatus(data));
        //bug 990370, end
        // Bind the compose message view to the DraftMessageData
//        mComposeMessageView.bind(DataModel.get().createDraftMessageData(
//                mBinding.getData().getConversationId()), this);

        /* Modify by SPRD for Bug:525178 2016.01.27 Start */
        //add for 600739 start
        if (mBinding.isBound()) {
            mPreDraftMessageData = DataModel.get().createDraftMessageData(
                    mBinding.getData().getConversationId());
        } else {
            //Bug 626575 start
            Log.d(TAG, "onCreateView  bind: mConversationId:" + mConversationId);
           /*add by sprd for Bug 630177 start*/
            if (null==mConversationId) {
                Log.d(TAG, "onCreateView  bind: getActivity()=" + getActivity());
                if (getActivity()!=null){
                    getActivity().finish();
                }
                return view;
            }
            /*add by sprd for  Bug 630177 end*/
            mBinding.bind(DataModel.get().createConversationData(getActivity(), this, mConversationId));
            mPreDraftMessageData = DataModel.get().createDraftMessageData(
                    mBinding.getData().getConversationId());
        }
        Log.d(TAG, "onCreateView mConversationId = " + mConversationId);
        //Bug 626575 end
        //add for 600739 end
        if (mPreDraftMessageData != null && savedInstanceState != null) {
            ArrayList<MessagePartData> list = savedInstanceState.getParcelableArrayList(KEY_PRE_ATTACHMENT);
            if (list != null && list.size() > 0) {
                Log.d(TAG, "restore message attachment data from saved instance.");
                //add for bug 638856 --begin
                if(OsUtil.hasSmsPermission()) {
                    boolean hasEmailPermission = true;
                    for (final MessagePartData data : (Collection<? extends MessagePartData>) list) {
                        // Don't break out of loop even if limit has been reached so we can destroy all
                        // of the over-limit attachments.
                        final Uri uri = data.getContentUri();
                        if (uri != null && uri.toString().contains(mEmailUriHeader) && !OsUtil.hasEmailPermission()) {
                            hasEmailPermission = false;
                            mHost.needReadEmailAttachmentPermission();
                        }
                    }
                    if (hasEmailPermission) {
                        mPreDraftMessageData.addAttachments(list);
                    }
                }
                //add for bug 638856 --end
            }
        }
        /* Modify by SPRD for Bug:525178 2016.01.27 End */
        if (mPreDraftMessageData != null) {
            mComposeMessageView.bind(mPreDraftMessageData, this);
        }
        if(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
            mIsOnlyDraft = BugleDatabaseOperations.isOnlyDraftConversation(mConversationId);
        }
        return view;
    }

    private void scrollToPosition(final int targetPosition, final boolean smoothScroll) {
        if (smoothScroll) {
            final int maxScrollDelta = JUMP_SCROLL_THRESHOLD;

            final LinearLayoutManager layoutManager =
                    (LinearLayoutManager) mRecyclerView.getLayoutManager();
            final int firstVisibleItemPosition =
                    layoutManager.findFirstVisibleItemPosition();
            final int delta = targetPosition - firstVisibleItemPosition;
            final int intermediatePosition;

            if (delta > maxScrollDelta) {
                intermediatePosition = Math.max(0, targetPosition - maxScrollDelta);
            } else if (delta < -maxScrollDelta) {
                final int count = layoutManager.getItemCount();
                intermediatePosition = Math.min(count - 1, targetPosition + maxScrollDelta);
            } else {
                intermediatePosition = -1;
            }
            if (intermediatePosition != -1) {
                mRecyclerView.scrollToPosition(intermediatePosition);
            }
            mRecyclerView.smoothScrollToPosition(targetPosition);
        } else {
            mRecyclerView.scrollToPosition(targetPosition);
        }
    }

    private int getScrollPositionFromBottom() {
        final LinearLayoutManager layoutManager =
                (LinearLayoutManager) mRecyclerView.getLayoutManager();
        final int lastVisibleItem =
                layoutManager.findLastVisibleItemPosition();
        return Math.max(mAdapter.getItemCount() - 1 - lastVisibleItem, 0);
    }

    /**
     * Display a photo using the Photoviewer component.
     */
    @Override
    public void displayPhoto(final Uri photoUri, final Rect imageBounds, final boolean isDraft) {
        boolean isDrm = false;
        try {
            String dataPath = MessagingDrmSession.get().getPath(photoUri);
            Log.d(TAG, " uri is " + photoUri + " path is " + dataPath);
            if (dataPath != null && MessagingDrmSession.get().drmCanHandle(dataPath, null)) {
                Log.d(TAG, " is drm data ");
                isDrm = true;
            }
        } catch (Exception ex) {
            Log.d(TAG, " drm ex " + ex);
        }
        displayPhoto(photoUri, imageBounds, isDraft, mConversationId, getActivity(), isDrm);
    }

    public static void displayPhoto(final Uri photoUri, final Rect imageBounds,
                                    final boolean isDraft, final String conversationId, final Activity activity, boolean isDrm) {
        final Uri imagesUri =
                isDraft ? MessagingContentProvider.buildDraftImagesUri(conversationId)
                        : MessagingContentProvider.buildConversationImagesUri(conversationId);
        if (isDrm) {
            UIIntents.get().launchFullScreenPhotoViewerForDrm(activity, photoUri, imageBounds, photoUri);
        } else {
            UIIntents.get().launchFullScreenPhotoViewer(activity, photoUri, imageBounds, imagesUri);
        }
    }

    /*Add by SPRD for bug581044  2016.07.08 Start*/
    @Override
    public boolean isEmptyRecipientConversaton() {
        return isEmptyRecipientConversaton;
    }
    /*Add by SPRD for bug581044  2016.07.08 End*/

    private void selectMessage(final ConversationMessageView messageView) {
        selectMessage(messageView, null /* attachment */);
    }


    private void selectMessage(final ConversationMessageView messageView,
                               final MessagePartData attachment) {
        mSelectedMessage = messageView;
        if (mSelectedMessage == null) {
            mAdapter.setSelectedMessage(null);
            mHost.dismissActionMode();
            mSelectedAttachment = null;
            return;
        }
        //bug741760 beign
        mSelectedAttachment = attachment;
        if (messageView != null && messageView.getDrmPathLocked()) {
            Log.d(TAG, " selectMessage messageView locked ");
            mHost.dismissActionMode();
            UiUtils.showToastAtBottom(R.string.drm_can_not_processed);
            return;
        }
        mAdapter.setSelectedMessage(messageView.getData().getMessageId());
        //bug741760 end
        mHost.startActionMode(mMessageActionModeCallback);
    }

    /* And by SPRD for Bug:525178 2016.01.27 Start */
    private DraftMessageData mPreDraftMessageData;
    private static final String KEY_PRE_ATTACHMENT = "--k-p-a";
    /* And by SPRD for Bug:525178 2016.01.27 End */

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mListState != null) {
            outState.putParcelable(SAVED_INSTANCE_STATE_LIST_VIEW_STATE_KEY, mListState);
        }

        /* And by SPRD for Bug:525178 2016.01.27 Start */
        if (mPreDraftMessageData != null && mPreDraftMessageData.hasAttachments()) {
            ArrayList<MessagePartData> list = new ArrayList<>();
            for (MessagePartData mpd : mPreDraftMessageData.getReadOnlyAttachments()) {
                Log.d(TAG, "content type:" + mpd.getContentType() + ", is attachment:" + mpd.isAttachment());
                if (mpd != null && ContentType.APP_SMIL.equals(mpd.getContentType())) {
                    //for bug710962 begin
                    //mClearLocalDraft = true;
                    list.add(mpd);
                    //for bug710962 end
                }
                if (mpd != null && (mpd.isAttachment() || mpd.isText())) {//for bug710962
                    list.add(mpd);
                }
            }
            outState.putParcelableArrayList(KEY_PRE_ATTACHMENT, list);
        }
        /* And by SPRD for Bug:525178 2016.01.27 Start */
        outState.putBoolean(CLEAR_LOCAL_DRAFT_STATE, mClearLocalDraft);
        mComposeMessageView.saveInputState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        SmartMessageServer.getInstance().addRegister(this);//bug 1008873
        if (mIncomingDraft == null) {
            if (GlobleUtil.mSharedToMessaging   //if other app share content to messaging, reload draft from database.
                    && !entryFromShareAction()) {   //Bug 1004415
                GlobleUtil.mSharedToMessaging = false;
                mPreDraftMessageData.mIsDraftCachedCopy = false;
                mClearLocalDraft = true;
            }
            mComposeMessageView.requestDraftMessage(mClearLocalDraft);
        } else {
            mComposeMessageView.setDraftMessage(mIncomingDraft);
            mIncomingDraft = null;
        }
        mClearLocalDraft = false;

        // On resume, check if there's a pending request for resuming message compose. This
        // may happen when the user commits the contact selection for a group conversation and
        // goes from compose back to the conversation fragment.
        if (mHost.shouldResumeComposeMessage()) {
            mComposeMessageView.resumeComposeMessage();
        }

        setConversationFocus();

        // On resume, invalidate all message views to show the updated timestamp.
        mAdapter.notifyDataSetChanged();

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mConversationSelfIdChangeReceiver,
                new IntentFilter(UIIntents.CONVERSATION_SELF_ID_CHANGE_BROADCAST_ACTION));
    }

    void setConversationFocus() {
        if (mHost.isActiveAndFocused()) {
            mBinding.getData().setFocus();
            // spread: fixe for bug 516158 start
            if (mPauseRusume) {
                mComposeMessageView.resumeComposeMessage();
                mPauseRusume = false;
            }
            // spread: fixe for bug 516158 end
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem addPhraseMenu = menu.findItem(R.id.action_add_phrase);
        if (null != addPhraseMenu) {
            if (MessageData.PROTOCOL_MMS_SMIL == mComposeMessageView.getDraftDataModel().getData().mProtocol) {
                addPhraseMenu.setVisible(false);
            } else {
                addPhraseMenu.setVisible(true);
            }
        }
        /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
        final MenuItem editContactMenu = menu.findItem(R.id.action_edit_contact);
        if (null != editContactMenu) {
            menu.findItem(R.id.action_edit_contact).setVisible(mIsOnlyDraft
                    && MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled());
        }
        /*Add by SPRD for edit contact in draft 2016.09.06 End*/
        //bug 990333, begin
        final MenuItem archiveMenu = menu.findItem(R.id.action_archive);
        if (archiveMenu != null) {
            final ConversationData data = mBinding.getData();
            if(data != null && data.getIsArchived()){
                archiveMenu.setVisible(false);
            }else {
                archiveMenu.setVisible(hasContent());
            }
        }
        //bug 990333, end
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (mHost.getActionMode() != null) {
            return;
        }
        inflater.inflate(R.menu.conversation_menu, menu);
        mActionBarMenuList = menu;

        final ConversationData data = mBinding.getData();

        // Disable the "people & options" item if we haven't loaded participants yet.
        menu.findItem(R.id.action_people_and_options).setEnabled(data.getParticipantsLoaded());

        // See if we can show add contact action.
        final ParticipantData participant = data.getOtherParticipant();
        final boolean addContactActionVisible = (participant != null
                && TextUtils.isEmpty(participant.getLookupKey()));
        menu.findItem(R.id.action_add_contact).setVisible(addContactActionVisible);

        // See if we should show archive or unarchive.
        final boolean isArchived = data.getIsArchived();
        menu.findItem(R.id.action_archive).setVisible(!isArchived);
        menu.findItem(R.id.action_unarchive).setVisible(isArchived);

        // Conditionally enable the phone call button.
        final boolean supportCallAction = (PhoneUtils.getDefault().isVoiceCapable() &&
                data.getParticipantPhoneNumber() != null);
        menu.findItem(R.id.action_call).setVisible(supportCallAction);
        menu.findItem(R.id.action_add_phrase).setVisible(true);//sprd add for common message
        menu.findItem(R.id.action_sms_merge_forward).setVisible(true);//sprd add for sms merge forward
        menu.removeItem(R.id.goto_smil);//923080
        menu.findItem(R.id.action_edit_contact).setVisible(mIsOnlyDraft
                && MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_people_and_options:
                Assert.isTrue(mBinding.getData().getParticipantsLoaded());
                UIIntents.get().launchPeopleAndOptionsActivity(getActivity(), mConversationId);
                return true;

            case R.id.action_call:
                final String phoneNumber = mBinding.getData().getParticipantPhoneNumber();
                Assert.notNull(phoneNumber);
                final View targetView = getActivity().findViewById(R.id.action_call);
                Point centerPoint;
                if (targetView != null) {
                    final int screenLocation[] = new int[2];
                    targetView.getLocationOnScreen(screenLocation);
                    final int centerX = screenLocation[0] + targetView.getWidth() / 2;
                    final int centerY = screenLocation[1] + targetView.getHeight() / 2;
                    centerPoint = new Point(centerX, centerY);
                } else {
                    // In the overflow menu, just use the center of the screen.
                    final Display display = getActivity().getWindowManager().getDefaultDisplay();
                    centerPoint = new Point(display.getWidth() / 2, display.getHeight() / 2);
                }
                UIIntents.get().launchPhoneCallActivity(getActivity(), phoneNumber, centerPoint);
                return true;

            case R.id.action_archive:
                mBinding.getData().archiveConversation(mBinding);
                closeConversation(mConversationId);
                return true;

            case R.id.action_unarchive:
                mBinding.getData().unarchiveConversation(mBinding);
                return true;

            case R.id.action_settings:
                return true;

            case R.id.action_add_contact:
                Log.d(TAG, "action_add_contact: mConversationId: " + mConversationId);
                final ParticipantData participant = mBinding.getData().getOtherParticipant();
                Assert.notNull(participant);
                final String destination = participant.getSendDestination();//Bug 849992
                final Uri avatarUri = AvatarUriUtil.createAvatarUri(participant);
                (new AddContactsConfirmationDialog(getActivity(), avatarUri, destination)).show();
                return true;

            case R.id.action_delete:
                if (PhoneUtils.getDefault().isDefaultSmsApp()) {//for bug712929
                    mCancelDialog = new AlertDialog.Builder(getActivity())
                            .setTitle(getResources().getQuantityString(
                                    R.plurals.delete_conversations_confirmation_dialog_title, 1))
                            .setPositiveButton(R.string.delete_conversation_confirmation_button,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(final DialogInterface dialog,
                                                            final int button) {
                                            deleteConversation();
                                        }
                                    })
                            .setNegativeButton(R.string.delete_conversation_decline_button, null)
                            .show();
                } else {
                    warnOfMissingActionConditions(false /*sending*/,
                            null /*commandToRunAfterActionConditionResolved*/);
                }
                return true;
            //sprd add for common message begin
            case R.id.action_add_phrase:
                Intent intent = new Intent(getActivity(), ShowPharseActivity.class);
                startActivityForResult(intent, REQUEST_INSERT_PHRASE);
                return true;
            //sprd add for common message end
            //Sprd add for sms merge forward begin
            case R.id.action_sms_merge_forward:
                Log.d(TAG, "click sms merge menu: mConversationId: " + mConversationId);
                Intent i = new Intent(getActivity(), SmsMergeForwardActivity.class);
                i.putExtra("thread_id", mConversationId);
                i.putExtra("SMS_MERGE_FORWARD_FROM", "ConversationFragment");
                startActivity(i);
                return true;
            //Sprd add for sms merge forward end
            /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
            case R.id.action_edit_contact:
                editParticipantsInDraft();
                return true;
            /*Add by SPRD for edit contact in draft 2016.09.06 End*/
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc} from ConversationDataListener
     */
    @Override
    public void onConversationMessagesCursorUpdated(final ConversationData data,
                                                    final Cursor cursor, final ConversationMessageData newestMessage,
                                                    final boolean isSync) {
        Log.d(TAG, "onConversationMessagesCursorUpdated");
        mBinding.ensureBound(data);

        // This needs to be determined before swapping cursor, which may change the scroll state.
        final boolean scrolledToBottom = isScrolledToBottom();
        final int positionFromBottom = getScrollPositionFromBottom();

        // If participants not loaded, assume 1:1 since that's the 99% case
        final boolean oneOnOne =
                !data.getParticipantsLoaded() || data.getOtherParticipant() != null;
        mAdapter.setOneOnOne(oneOnOne, false /* invalidate */);

        // Ensure that the action bar is updated with the current data.
        invalidateOptionsMenu();
        final Cursor oldCursor = mAdapter.swapCursor(cursor);

        if (cursor != null && oldCursor == null) {
            if (mListState != null) {
                mRecyclerView.getLayoutManager().onRestoreInstanceState(mListState);
                // RecyclerView restores scroll states without triggering scroll change events, so
                // we need to manually ensure that they are correctly handled.
                mListScrollListener.onScrolled(mRecyclerView, 0, 0);
            }
        }

        if (isSync) {
            // This is a message sync. Syncing messages changes cursor item count, which would
            // implicitly change RV's scroll position. We'd like the RV to keep scrolled to the same
            // relative position from the bottom (because RV is stacked from bottom), so that it
            // stays relatively put as we sync.
            final int position = Math.max(mAdapter.getItemCount() - 1 - positionFromBottom, 0);
            scrollToPosition(position, false /* smoothScroll */);
        } else if (newestMessage != null) {
            // Show a snack bar notification if we are not scrolled to the bottom and the new
            // message is an incoming message.
            if (!scrolledToBottom && newestMessage.getIsIncoming()) {
                // If the conversation activity is started but not resumed (if another dialog
                // activity was in the foregrond), we will show a system notification instead of
                // the snack bar.
                if (mBinding.getData().isFocused()) {
                    UiUtils.showSnackBarWithCustomAction(getActivity(),
                            getView().getRootView(),
                            getString(R.string.in_conversation_notify_new_message_text),
                            SnackBar.Action.createCustomAction(new Runnable() {
                                                                   @Override
                                                                   public void run() {
                                                                       scrollToBottom(true /* smoothScroll */);
                                                                       mComposeMessageView.hideAllComposeInputs(false /* animate */);
                                                                   }
                                                               },
                                    getString(R.string.in_conversation_notify_new_message_action)),
                            null /* interactions */,
                            SnackBar.Placement.above(mComposeMessageView));
                }
            } else {
                // We are either already scrolled to the bottom or this is an outgoing message,
                // scroll to the bottom to reveal it.
                // Don't smooth scroll if we were already at the bottom; instead, we scroll
                // immediately so RecyclerView's view animation will take place.
                scrollToBottom(!scrolledToBottom);
            }
        }

        if (cursor != null) {
            Log.d(TAG, "onConversationMessagesCursorUpdated cursor.getCount():" + cursor.getCount());
            mHost.onConversationMessagesUpdated(cursor.getCount());

            // Are we coming from a widget click where we're told to scroll to a particular item?
            final int scrollToPos = getScrollToMessagePosition();
            if (scrollToPos >= 0) {
                if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.VERBOSE)) {
                    LogUtil.v(LogUtil.BUGLE_TAG, "onConversationMessagesCursorUpdated " +
                            " scrollToPos: " + scrollToPos +
                            " cursorCount: " + cursor.getCount());
                }
                scrollToPosition(scrollToPos, true /*smoothScroll*/);
                clearScrollToMessagePosition();
            }
        }

        mHost.invalidateActionBar();
    }

    /**
     * {@inheritDoc} from ConversationDataListener
     */
    @Override
    public void onConversationMetadataUpdated(final ConversationData conversationData) {
        mBinding.ensureBound(conversationData);
        Log.d(TAG, "onConversationMetadataUpdated");
        if (mSelectedMessage != null && mSelectedAttachment != null) {
            // We may have just sent a message and the temp attachment we selected is now gone.
            // and it was replaced with some new attachment.  Since we don't know which one it
            // is we shouldn't reselect it (unless there is just one) In the multi-attachment
            // case we would just deselect the message and allow the user to reselect, otherwise we
            // may act on old temp data and may crash.
            final List<MessagePartData> currentAttachments = mSelectedMessage.getData().getAttachments();
            if (currentAttachments.size() == 1) {
                mSelectedAttachment = currentAttachments.get(0);
            } else if (!currentAttachments.contains(mSelectedAttachment)) {
                selectMessage(null);
            }
        }
        // Ensure that the action bar is updated with the current data.
        invalidateOptionsMenu();
        mHost.onConversationMetadataUpdated();
        mAdapter.notifyDataSetChanged();
        setConversationFocus();
    }

    public void setConversationInfo(final Context context, final String conversationId,
                                    final MessageData draftData) {
        // TODO: Eventually I would like the Factory to implement
        // Factory.get().bindConversationData(mBinding, getActivity(), this, conversationId));

        //modify for bug 611547
        mConversationId = conversationId;
        mIncomingDraft = draftData;
        //modify for bug 611547
        Log.d(TAG, "setConversationInfo: draftData:" + draftData);
        if (!mBinding.isBound()) {
            mBinding.bind(DataModel.get().createConversationData(context, this, conversationId));
            /*Add by SPRD for bug581044  2016.07.08 Start*/
            if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
                final String oldConversation = mHost.getOldConversationId();
                Log.d(TAG, " setConversationInfo oldConversationId:" + oldConversation + " conversationId," + conversationId);
                if (oldConversation != null && !oldConversation.equals(conversationId)) {
                    /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
                    if(BugleDatabaseOperations.isEmptyConversation(oldConversation)
                            || BugleDatabaseOperations.isOnlyDraftConversation(oldConversation)){
                        new Thread(){
                            public void run() {
                                if (BugleDatabaseOperations.deleteLocalConversation(oldConversation, System.currentTimeMillis())) {
                                    LogUtil.i(TAG, "DeleteConversationAction: Deleted local conversation "
                                            + oldConversation);
                                } else {
                                    LogUtil.w(TAG, "DeleteConversationAction: Could not delete local conversation "
                                            + oldConversation);
                                }
                            }
                        }.start();
                    }
                }
                isEmptyRecipientConversaton = BugleDatabaseOperations.isEmptyConversation(conversationId);
            }
            /*Add by SPRD for bug581044  2016.07.08 End*/
        } else {
            Assert.isTrue(TextUtils.equals(mBinding.getData().getConversationId(), conversationId));
        }
    }

    @Override
    public void onDestroy() {
            // Unbind all the views that we bound to data
        if (mBinding == null || !mBinding.isBound()) {
            Log.d(TAG, "onDestroy(): mBinding is null or is not bound!mBinding:" + mBinding);
        } else {
            if (mComposeMessageView != null) {
                mComposeMessageView.unbind();
            }
            mBinding.unbind();
        }
         closeDraftConfirmDialog();
        closeDeleteConfirmDialog(); //Bug 918700
        if (popupWindow != null) {
            popupWindow.dismiss();/*add for Bug 651351 */
        }
        if (mWarningConvertToSlidesDialog != null) {
            mWarningConvertToSlidesDialog.dismiss();/*add for Bug 654604  */
        }
        // And unbind this fragment from its data
        mConversationId = null;
        getActivity().unregisterReceiver(mSimInOutReceiver);
        getActivity().unregisterReceiver(mAirPlaneModeReceiver);
        //sprd fix bug 562320 start
        GlobleUtil.unRegisterHandler(TAG);
       if(NavigationBarShowingObserver!=null&&Utils.hasNavigationBar(mContext)){
         getActivity().getContentResolver().unregisterContentObserver(NavigationBarShowingObserver);}

        if(WarnDialog!=null){
        Log.w(TAG,"WarnDialog-dismiss");
         WarnDialog.dismiss();
          WarnDialog=null;
        }
        //sprd fix bug 562320 end

        //bug begin 1196417
        if (null !=  mCancelDialog) {
            mCancelDialog.dismiss();
            mCancelDialog = null;
        }
        //bug end 1196417
        super.onDestroy(); /*modified for Bug 796017*/
    }

    void suppressWriteDraft() {
        mSuppressWriteDraft = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        SmartMessageServer.getInstance().removeRegister(this);//bug 1008873
       /*add by sprd for Bug 626075 start*/
        if (getActivity().isInMultiWindowMode()){
            closeDraftConfirmDialog();//add for bug650418
            LinkifyUtil.MyURLSpan.closeLinkJumpDialog();//add for bug650441
            return;
        }
       /*add by sprd for Bug 626075 end*/
        mLoadDraftDone = false;
        mOnAttachmentChosen = false;
        Log.d(TAG, " onPause setKeepingMessageData: mSuppressWriteDraft="+mSuppressWriteDraft + " mComposeMessageView="+ mComposeMessageView);
        if (mComposeMessageView != null && !mSuppressWriteDraft) {
            mComposeMessageView.writeDraftMessage();
        }
        /*Add by SPRD for bug581044  2016.07.08 Start*/
        if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled() && mComposeMessageView != null) {
            mHost.setKeepingMessageData(mComposeMessageView.getKeepingMessageData());
            if (!isValidConversation() && mPreDraftMessageData != null) {/*Add by SPRD for Bug 839775 Start*/
                Log.d(TAG, " onPause setKeepingMessageData:  mIsDraftCachedCopy= " +mPreDraftMessageData.mIsDraftCachedCopy);
                mPreDraftMessageData.mIsDraftCachedCopy = false;
            }/*Add by SPRD for Bug 839775 end*/
        }
        /*Add by SPRD for bug581044  2016.07.08 End*/
        mSuppressWriteDraft = false;
        mBinding.getData().unsetFocus();
        mListState = mRecyclerView.getLayoutManager().onSaveInstanceState();

        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mConversationSelfIdChangeReceiver);
    }

    /*add by sprd for Bug 626075 start*/
    @Override
    public void onStop() {
        if (getActivity().isInMultiWindowMode()){
            mLoadDraftDone = false;
            mOnAttachmentChosen = false;
            Log.d(TAG, " onStop setKeepingMessageData: mSuppressWriteDraft="+mSuppressWriteDraft + " mComposeMessageView="+ mComposeMessageView);
            if (mComposeMessageView != null && !mSuppressWriteDraft) {
                mComposeMessageView.writeDraftMessage();
            }
            /*Add by SPRD for bug581044  2016.07.08 Start*/
            if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled() && mComposeMessageView != null) {
                mHost.setKeepingMessageData(mComposeMessageView.getKeepingMessageData());
                if (!isValidConversation() && mPreDraftMessageData != null) {/*Add by SPRD for Bug 839775 Start*/
                    mPreDraftMessageData.mIsDraftCachedCopy = false;
               }/*Add by SPRD for Bug 839775 end*/
            }
            /*Add by SPRD for bug581044  2016.07.08 End*/
            mSuppressWriteDraft = false;
            mBinding.getData().unsetFocus();
            mListState = mRecyclerView.getLayoutManager().onSaveInstanceState();

            LocalBroadcastManager.getInstance(getActivity())
                    .unregisterReceiver(mConversationSelfIdChangeReceiver);
        }

        //add for bug 661426 --begin
        if (mDialog != null && mDialog.isShowing()){
            Log.d(TAG,"mDialog.isShowing()="+mDialog.isShowing());
            mDialog.dismiss();
        }
        //add for bug 661426 --end
        super.onStop();
    }
    /*add by sprd for Bug 626075 end*/

    private int mLastOrientation = -1; /*add by sprd  for Bug 649312  */

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mRecyclerView.getItemAnimator().endAnimations();
        /*add by sprd  for Bug 649312 start */
        if (newConfig.orientation != mLastOrientation) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshPopupWindow();
                }
            }, 200);
        }
        mLastOrientation = newConfig.orientation;
        /*add by sprd  for Bug 649312 end */
    }

    // TODO: Remove isBound and replace it with ensureBound after b/15704674.
    public boolean isBound() {
        return mBinding.isBound();
    }

    private FragmentManager getFragmentManagerToUse() {
        return OsUtil.isAtLeastJB_MR1() ? getChildFragmentManager() : getFragmentManager();
    }

    public MediaPicker getMediaPicker() {
        return (MediaPicker) getFragmentManagerToUse().findFragmentByTag(
                MediaPicker.FRAGMENT_TAG);
    }

    @Override
    public void sendMessage(final MessageData message) {
        if (isReadyForAction()) {
            if (ensureKnownRecipients()) {
                if(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
                    mIsOnlyDraft = false;
                }
                /* Add by SPRD for bug 563344 Start */
                if (MessageData.PROTOCOL_MMS_SMIL == message.getProtocol()) {
                    Log.d(TAG, "sendMessage: message protocol is PROTOCOL_MMS_SMIL");
                } else {
                /* Add by SPRD for bug 563344 End */
                    // Merge the caption text from attachments into the text body of
                    // the messages
                    message.consolidateText();
                }
                mBinding.getData().sendMessage(mBinding, message);
                mComposeMessageView.resetMediaPickerState();
                if(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()){
                    mIncomingDraft = null;/*Bug 823966*/
                    mHost.setKeepingMessageData(null); /*Bug 835213*/
                    mHost.onSendMessageAction();
                }
            } else {
                LogUtil.w(LogUtil.BUGLE_TAG, "Message can't be sent: conv participants not loaded");
            }
        } else {
            warnOfMissingActionConditions(true /*sending*/,
                    new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(message);
                        }
                    });
        }
    }

    public void setHost(final ConversationFragmentHost host) {
        mHost = host;
    }

    public String getConversationName() {
        /*add for Bug 654604 begin*/
        if (mBinding == null || !mBinding.isBound()) {
            Log.d(TAG, "mBinding is null or is not bound!mBinding:" + mBinding);
            return " ";
        }
        /*add for Bug 654604 end*/

        //add for bug 637364 --begin
        String ConversationName = mBinding.getData().getConversationName();
        if (ConversationName != null) {
            return ConversationName;
        } else {
            return " ";
        }
        //add for bug 637364 --end
    }

    @Override
    public void onComposeEditTextFocused() {
        mHost.onStartComposeMessage();
    }

    @Override
    public void onAttachmentsCleared() {
        // When attachments are removed, reset transient media picker state such as image selection.
        mComposeMessageView.resetMediaPickerState();
    }

    /**
     * Called to check if all conditions are nominal and a "go" for some action, such as deleting
     * a message, that requires this app to be the default app. This is also a precondition
     * required for sending a draft.
     * @return true if all conditions are nominal and we're ready to send a message
     */
    @Override
    public boolean isReadyForAction() {
        return UiUtils.isReadyForAction();
    }

    /**
     * When there's some condition that prevents an operation, such as sending a message,
     * call warnOfMissingActionConditions to put up a snackbar and allow the user to repair
     * that condition.
     * @param sending - true if we're called during a sending operation
     * @param commandToRunAfterActionConditionResolved - a runnable to run after the user responds
     *                  positively to the condition prompt and resolves the condition. If null,
     *                  the user will be shown a toast to tap the send button again.
     */
    @Override
    public void warnOfMissingActionConditions(final boolean sending,
                                              final Runnable commandToRunAfterActionConditionResolved) {
        if (mChangeDefaultSmsAppHelper == null) {
            mChangeDefaultSmsAppHelper = new ChangeDefaultSmsAppHelper();
        }
        mChangeDefaultSmsAppHelper.warnOfMissingActionConditions(sending,
                commandToRunAfterActionConditionResolved, mComposeMessageView,
                getView().getRootView(),
                getActivity(), this);
    }

    private boolean ensureKnownRecipients() {
        final ConversationData conversationData = mBinding.getData();

        if (!conversationData.getParticipantsLoaded()) {
            // We can't tell yet whether or not we have an unknown recipient
            return false;
        }

        final ConversationParticipantsData participants = conversationData.getParticipants();
        for (final ParticipantData participant : participants) {


            if (participant.isUnknownSender()) {
                UiUtils.showToast(R.string.unknown_sender);
                return false;
            }
        }

        return true;
    }

    public void retryDownload(final String messageId) {
        if (isReadyForAction()) {
            mBinding.getData().downloadMessage(mBinding, messageId);
        } else {
            warnOfMissingActionConditions(false /*sending*/,
                    null /*commandToRunAfterActionConditionResolved*/);
        }
    }

    public void retryDownload(final ConversationMessageData data) {
        if (isReadyForAction()) {
            mBinding.getData().downloadMessage(mBinding, data.getMessageId());
        } else {
            warnOfMissingActionConditions(false /*sending*/,
                    null /*commandToRunAfterActionConditionResolved*/);
        }
    }

    public void retrySend(final String messageId) {
        if (isReadyForAction()) {
            if (ensureKnownRecipients()) {
                mBinding.getData().resendMessage(mBinding, messageId);
            }
        } else {
            warnOfMissingActionConditions(true /*sending*/,
                    new Runnable() {
                        @Override
                        public void run() {
                            retrySend(messageId);
                        }

                    });
        }
    }

    void deleteMessage(final String messageId) {
        //if (isReadyForAction()) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
            .setTitle(R.string.delete_message_confirmation_dialog_title)
            .setMessage(R.string.delete_message_confirmation_dialog_text)
            .setPositiveButton(R.string.delete_message_confirmation_button,
                new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        //Bug 918700 begin
                        if (mBinding.isBound()) {
                            mBinding.getData().deleteMessage(mBinding, messageId);
                        }
                        //Bug 918700 end
                        mHost.dismissActionMode();//bug 1361317
                    }
                })
            .setNegativeButton(android.R.string.cancel, null);
        if (OsUtil.isAtLeastJB_MR1()) {
            builder.setOnDismissListener(new OnDismissListener() {
                @Override
                public void onDismiss(final DialogInterface dialog) {
                    //mHost.dismissActionMode(); // bug 1253296
                }
            });
        } else {
            builder.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(final DialogInterface dialog) {
                    mHost.dismissActionMode();
                }
            });
        }
        //Bug 918700 begin
        if (!getActivity().isFinishing() && !getActivity().isDestroyed()) {
            mDeleteConfirmDialog = builder.create();
            mDeleteConfirmDialog.show();
        }
        //Bug 918700 end
        //} else {
        //    warnOfMissingActionConditions(false /*sending*/,
        //            null /*commandToRunAfterActionConditionResolved*/);
        //    mHost.dismissActionMode();
        //}
    }

    //Bug 918700 begin
    private void closeDeleteConfirmDialog(){
        if (mDeleteConfirmDialog != null) {
            if (mDeleteConfirmDialog.isShowing()) {
                mDeleteConfirmDialog.dismiss();
            }
            mDeleteConfirmDialog = null;
        }
    }
    //Bug 918700 end

    public void deleteConversation() {
        if (PhoneUtils.getDefault().isDefaultSmsApp()) {//for bug712929
            final Context context = getActivity();
            //modify for bug664709 begin
            if (mBinding.isBound()) {
                mBinding.getData().deleteConversation(mBinding);
                closeConversation(mConversationId);
            }else{
                Log.d(TAG,"[ConFrag]===deleteConversation===mBinding.getData() is null!");
                return;
            }//modify for bug664709 end
        } else {
            warnOfMissingActionConditions(false /*sending*/,
                    null /*commandToRunAfterActionConditionResolved*/);
        }
    }

    @Override
    public void closeConversation(final String conversationId) {
        if (TextUtils.equals(conversationId, mConversationId)) {
            mHost.onFinishCurrentConversation();
            // TODO: Explicitly transition to ConversationList (or just go back)?
        }
    }

    @Override
    public void onConversationParticipantDataLoaded(final ConversationData data) {
        mBinding.ensureBound(data);
        if (mBinding.getData().getParticipantsLoaded()) {
            final boolean oneOnOne = mBinding.getData().getOtherParticipant() != null;
            mAdapter.setOneOnOne(oneOnOne, true /* invalidate */);

            // refresh the options menu which will enable the "people & options" item.
            invalidateOptionsMenu();

            mHost.invalidateActionBar();

            mRecyclerView.setVisibility(View.VISIBLE);
            mHost.onConversationParticipantDataLoaded
                    (mBinding.getData().getNumberOfParticipantsExcludingSelf());

            /* smart message, begin*/
            if (MmsConfig.supportSmartSdk()) {
                updateServiceMenu(mContext, mBinding.getData().getParticipantPhoneNumber());
            }
            /* smart message, end*/
        }
    }

    @Override
    public void onSubscriptionListDataLoaded(final ConversationData data) {
        mBinding.ensureBound(data);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void promptForSelfPhoneNumber() {
        if (mComposeMessageView != null) {
            // Avoid bug in system which puts soft keyboard over dialog after orientation change
            ImeUtil.hideSoftInput(getActivity(), mComposeMessageView);
        }

        final FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        final EnterSelfPhoneNumberDialog dialog = EnterSelfPhoneNumberDialog
                .newInstance(getConversationSelfSubId());
       // dialog.setTargetFragment(this, 0/*requestCode*/);
        dialog.show(ft, null/*tag*/);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (mChangeDefaultSmsAppHelper == null) {
            mChangeDefaultSmsAppHelper = new ChangeDefaultSmsAppHelper();
        }
        mChangeDefaultSmsAppHelper.handleChangeDefaultSmsResult(requestCode, resultCode, null);
        //sprd add for common message begin
        if (requestCode == REQUEST_INSERT_PHRASE) {
            if (data != null) {
                boolean noCommonPhrase = data.getBooleanExtra("k-n-p", false);
                if (noCommonPhrase) {
                    UiUtils.showToastAtBottom(R.string.no_common_phrase);
                    return;
                }
                String insertText = data.getStringExtra("clickPhrase");
                if (insertText != null) {
                    //for bug667297 begin
                    int index = mComposeMessageView.getComposeEditText().getSelectionStart();
                    Editable edit = mComposeMessageView.getComposeEditText().getEditableText();
                    if( index < 0 || index >= edit.length() ){
                        edit.append(insertText);
                    }else{
                        edit.insert(index,insertText);
                    }
                    //Bug971264 begin
                    mComposeMessageView.requestFocus();
                }
            }
        }
        //sprd add for common message end
    }

    public boolean hasMessages() {
        return mAdapter != null && mAdapter.getItemCount() > 0;
    }

    public boolean onBackPressed() {
        if (mComposeMessageView.onBackPressed()) {
            return true;
        }/*Add by SPRD for bug 646250 Start*/
          else {
            if(isEmptyRecipientConversaton() && mComposeMessageView.isValidConversation()){
                showDiscardDraftConfirmDialog(mContext, new DiscardDraftListener());
                return true;
            //add for bug 675873 --begin
            } else if (mAdapter.getItemCount()==0
                    && mComposeMessageView.isDraftLoadDone() //bug869816
                    && !mComposeMessageView.isValidConversation()){
                Log.d(TAG,"deleteConversation1");
                DeleteConversationAction.deleteConversation(mConversationId, System.currentTimeMillis());
            }
            //add for bug 675873 --end
        }
        /*Add by SPRD for bug 646250 End*/
        return false;
    }

    /*add for bug 653194 start*/
    public boolean isValidConversation() {
        if (mComposeMessageView!= null && mComposeMessageView.isValidConversation()){/*Bug 696478*/
            return true;
        }
        return false;
    }
    /*add for bug 653194 end*/

    /*Add by SPRD for bug 646250 Start*/
    private class DiscardDraftListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            /*added for Bug 823139 start*/
            Log.d(TAG," DiscardDraftListener: deleteConversation");
            DeleteConversationAction.deleteConversation(mConversationId, System.currentTimeMillis());
            /*added for Bug 823139 end*/
            mComposeMessageView.clearDraft();

            if(getActivity()!=null){//add for bug650418 begin
                getActivity().finish();
            }//add for bug650418 end
        }
    }

    private void closeDraftConfirmDialog(){
        if (mDraftConfirmDialog != null) {
                mDraftConfirmDialog.dismiss();
            mDraftConfirmDialog = null;
        }
    }

    private void showDiscardDraftConfirmDialog(Context context,
            OnClickListener listener) {
        mDraftConfirmDialog = new AlertDialog.Builder(context)
                .setMessage(R.string.discard_message_reason)
                .setPositiveButton(R.string.confirm_convert, listener)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    public boolean onNavigationUpPressed() {
        return mComposeMessageView.onNavigationUpPressed();
    }

    //Bug 1177603 begin
    public boolean isDraftLoadDone() {
        return mComposeMessageView.isDraftLoadDone();
    }
    //Bug 1177603 end

    private void checkDrmRightsConsume(final MessagePartData attachment, final Rect imageBounds) {
        AlertDialog.Builder builder = MessagingDrmSession.get().showProtectInfo(getContext(), attachment.getDrmDataPath(), true/*is picture*/);
        builder.setPositiveButton(mContext.getString(R.string.ok_drm_rights_consume),
                new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            displayPhoto(attachment.getContentUri(), imageBounds, false);
                        } catch (Exception e) {
                            Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.drm_no_application_open),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel_drm_rights_consume), null).show();
    }

    @Override
    public boolean onAttachmentClick(final ConversationMessageView messageView,
                                     final MessagePartData attachment, final Rect imageBounds, final boolean longPress) {
        if (longPress) {
            if (attachment.isDrmType()) {
                mHost.dismissActionMode();
                UiUtils.showToastAtBottom(R.string.drm_can_not_processed);
                return true;
            }
            selectMessage(messageView, attachment);
            return true;
        } else if (messageView.getData().getOneClickResendMessage()) {
            handleMessageClick(messageView);
            return true;
        }

        if (attachment.isImage()) {
            // Start at bug1570382
            Log.d(TAG, "DRM is not support now!");
            /*
            String contentType = attachment.getContentType();
            boolean isDrm = ContentType.isDrmType(contentType);
            if (isDrm) {
                Log.d(TAG, " content type is " + contentType + " path " + attachment.getDrmDataPath());
                if (attachment.getDrmFileRightsStatus() == false) {
                    UIIntents.get().launchDrmRightRequestActivity(getContext(), attachment);
                    return true;
                }
                checkDrmRightsConsume(attachment, imageBounds);
                return true;
            }
            */
            // End at bug1570382
            displayPhoto(attachment.getContentUri(), imageBounds, false /* isDraft */);
        }

        if (attachment.isVCard()) {
            UIIntents.get().launchVCardDetailActivity(getActivity(), attachment.getContentUri());
        }
        if (attachment.isVCalendar() && !MediaScratchFileProvider.AUTHORITY.equals(attachment.getContentUri().getAuthority())) {
            UIIntents.get().launchVCalendarActivity(getActivity(), attachment.getContentUri());
        }
        if (!(attachment.isMedia())) {
            getImportAlertMessage(attachment);
        }

        return false;
    }

    private void handleMessageClick(final ConversationMessageView messageView) {
        if (messageView != mSelectedMessage) {
            final ConversationMessageData data = messageView.getData();
            final boolean isReadyToSend = isReadyForAction();
            if (data.getOneClickResendMessage()) {
                // Directly resend the message on tap if it's failed
                retrySend(data.getMessageId());
                selectMessage(null);
            } else if (data.getShowResendMessage() && isReadyToSend) {
                // Select the message to show the resend/download/delete options
                selectMessage(messageView);
            } else if (data.getShowDownloadMessage() && isReadyToSend) {
                // Directly download the message on tap
                retryDownload(data);
            } else {
                // Let the toast from warnOfMissingActionConditions show and skip
                // selecting
                if(!isSmartMessage()){//add for 648089 click white cell not refresh
                warnOfMissingActionConditions(false /*sending*/,
                        null /*commandToRunAfterActionConditionResolved*/);
                selectMessage(null);
                }
            }
        } else {
            selectMessage(null);
        }
    }

    private static class AttachmentToSave {
        public Uri uri;
        public String contentType;
        public Uri persistedUri;
        // Saved status.
        public Status status;

        AttachmentToSave(final Uri uri, final String contentType) {
            this.uri = uri;
            this.contentType = contentType;
        }

        public enum Status {SUCCESS, FAILED_FULL_STORAGE}

        ;
    }

    public static class SaveAttachmentTask extends SafeAsyncTask<Void, Void, Void> {
        private Context mContext;
        private final List<AttachmentToSave> mAttachmentsToSave = new ArrayList<>();
        private Uri mExternalFolderUri = null; //Bug 912660

        public SaveAttachmentTask(final Context context, final Uri contentUri,
                                  final String contentType) {
            mContext = context;
            addAttachmentToSave(contentUri, contentType);
        }

        public SaveAttachmentTask(final Context context) {
            mContext = context;
        }

        public void addAttachmentToSave(final Uri contentUri, String contentType) {
            AttachmentToSave savedAttachment = new AttachmentToSave(contentUri, contentType);
            mAttachmentsToSave.add(savedAttachment);
        }

        //Bug 912660 begin
        public void addAttachmentToSave(final Uri contentUri, String contentType, final Uri targetUri) {
            addAttachmentToSave(contentUri, contentType);
            mExternalFolderUri = targetUri;
        }
        //Bug 912660 end

        ProgressDialog mWaitDialg;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            boolean showDlaog = true;
            if (mContext instanceof Activity) {
                Activity a = (Activity) mContext;
                if (a.isFinishing() || a.isDestroyed()) {
                    showDlaog = false;
                }
            }
            if (showDlaog) {
                mWaitDialg = new ProgressDialog(mContext);
                mWaitDialg.setCancelable(true);
                mWaitDialg.setIndeterminate(true);
                mWaitDialg.setMessage(mContext.getString(R.string.dlg_save_attachment));
                mWaitDialg.show();
            }
        }

        public int getAttachmentCount() {
            return mAttachmentsToSave.size();
        }

        @Override
        protected Void doInBackgroundTimed(final Void... arg) {
            final File appDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES),
                    mContext.getResources().getString(R.string.app_name));
            final File downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            for (final AttachmentToSave attachment : mAttachmentsToSave) {
                if (attachment != null) {
                    // In a new thread.
                    if (TextUtils.isEmpty(attachment.contentType)
                            && attachment.uri != null
                            && attachment.uri.toString().startsWith("content://mms/part/")) {
                        attachment.contentType = UriUtil.getContentFromPartUri(attachment.uri);
                    }
                    if (!TextUtils.isEmpty(attachment.contentType)) {
                        if (mExternalFolderUri != null) {
                            attachment.persistedUri = UriUtil.persistContent(attachment.uri,
                                    mExternalFolderUri, attachment.contentType);
                        } else {
                            final File savedDir = getSaveDir();
                            if (savedDir != null){
                                attachment.persistedUri = UriUtil.persistContent(attachment.uri,
                                        savedDir, attachment.contentType);
                            }
                        }
                    }
                }
            }
            return null;
        }

        private File getSaveDir() {
            return mkdirs(EnvironmentEx.getInternalStoragePath(), EnvironmentEx.getInternalStoragePathState());
        }

        private File mkdirs(File root, String state) {
            File dir = null;
            if (isMounted(state) && isSizeAvailable(root)) {
                dir = new File(root, Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) {
                    if (!dir.mkdirs()) {
                        dir = null;
                    }
                }
            }
            return dir;
        }

        private boolean isMounted(String state) {
            return Environment.MEDIA_MOUNTED.equals(state);
        }

        private boolean isSizeAvailable(File dir) {
            try {
                if (dir != null && dir.exists()) {
                    StatFs statfs = new StatFs(dir.getPath());
                    // FIXME: full message size or one media size in message ?
                    // in byte.
                    return (long) statfs.getBlockSize() * (long) statfs.getAvailableBlocks()
                            - MmsConfig.getMaxMaxMessageSize() * 1024 > 0;
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        @Override
        protected void onPostExecute(final Void result) {
            int failCount = 0;
            int imageCount = 0;
            int videoCount = 0;
            int otherCount = 0;
            for (final AttachmentToSave attachment : mAttachmentsToSave) {
                /*Modify by SPRD for Bug:536961 {@ */
                String contentType = attachment.contentType;
                Log.d(TAG, "lxg saved attachment type : " + contentType
                        + ", path=" + (attachment.persistedUri != null ? attachment.persistedUri.getPath() : null));
                if (contentType!=null&&contentType.equals(ContentType.APP_SMIL))
                    continue;
                // add for bug 533699 begin
                if ((attachment.persistedUri == null)) {
                /*@}*/
                    failCount++;
                    continue;
                }

                // Inform MediaScanner about the new file
                final Intent scanFileIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scanFileIntent.setData(attachment.persistedUri);
                mContext.sendBroadcast(scanFileIntent);

                if (ContentType.isImageType(attachment.contentType)) {
                    imageCount++;
                } else if (ContentType.isVideoType(attachment.contentType)) {
                    videoCount++;
                } else {
                    otherCount++;
                    // Inform DownloadManager of the file so it will show in the "downloads" app
                    final DownloadManager downloadManager =
                            (DownloadManager) mContext.getSystemService(
                                    Context.DOWNLOAD_SERVICE);
                    final String filePath = attachment.persistedUri.getPath();
                    final File file = new File(filePath);

                    if (file.exists()) {
                        /*Add by SPRD for Bug:542891  2016.03.24 Start*/
                        if ((ContentType.TEXT_VCALENDAR).equals(attachment.contentType)
                                || (ContentType.TEXT_VCARD).equals(attachment.contentType)) {
                            contentType = contentType.toLowerCase();
                        }
                        /*Add by SPRD for Bug:542891  2016.03.24 End*/
                        downloadManager.addCompletedDownload(
                                file.getName() /* title */,
                                mContext.getString(
                                        R.string.attachment_file_description) /* description */,
                                true /* isMediaScannerScannable */,
                                contentType,
                                file.getAbsolutePath(),
                                file.length(),
                                false /* showNotification */);
                    }
                }
            }

            String message = "";
            if (failCount > 0) {
                // FIXME: handle filed staus.
                message = mContext.getResources().getQuantityString(
                        R.plurals.attachment_save_error, failCount, failCount);
            } else if (failCount < mAttachmentsToSave.size()) {
//                int messageId = R.plurals.attachments_saved;
//                if (otherCount > 0) {
//                    if (imageCount + videoCount == 0) {
//                        messageId = R.plurals.attachments_saved_to_downloads;
//                    }
//                } else {
//                    if (videoCount == 0) {
//                        messageId = R.plurals.photos_saved_to_album;
//                    } else if (imageCount == 0) {
//                        messageId = R.plurals.videos_saved_to_album;
//                    } else {
//                        messageId = R.plurals.attachments_saved_to_album;
//                    }
//                }
                int messageId = R.plurals.attachments_saved_dir;
                String dirName = Environment.DIRECTORY_DOWNLOADS;
                final int count = imageCount + videoCount + otherCount;
                message = mContext.getResources().getQuantityString(
                        messageId, count, count, dirName);
            }
            /*Bug 700333 start*/
            try{
                if (mWaitDialg != null && mWaitDialg.isShowing()) {
                    mWaitDialg.dismiss();
                }
            } catch (Exception e) {
               System.out.println("mWaitDialg cancel failed");
            }/*Bug 700333 end*/
            if (!TextUtils.isEmpty(message)) {
                UiUtils.showToastAtBottom(message);
            }
        }
    }

    private void invalidateOptionsMenu() {
        final Activity activity = getActivity();
        // TODO: Add the supportInvalidateOptionsMenu call to the host activity.
        if (activity == null || !(activity instanceof BugleActionBarActivity)) {
            return;
        }
        ((BugleActionBarActivity) activity).supportInvalidateOptionsMenu();
    }

    @Override
    public void setOptionsMenuVisibility(final boolean visible) {
        setHasOptionsMenu(visible);
        /*add for 815765 begin*/
        if(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
            mHost.updateContactPickerFragmentMenu();
        }/*add for 815765 end*/
    }

    // spread: fixe for bug 516158 start
    @Override
    public void dimissPaused(boolean pausefromMediaPicker) {
        mPauseRusume = pausefromMediaPicker;
    }
    // spread: fixe for bug 516158 end

    @Override
    public int getConversationSelfSubId() {
        final String selfParticipantId = mComposeMessageView.getConversationSelfId();
        if (mBinding.isBound()) {
            final ParticipantData self = mBinding.getData().getSelfParticipantById(selfParticipantId);
            // If the self id or the self participant data hasn't been loaded yet, fallback to
            // the default setting.
            return self == null ? ParticipantData.DEFAULT_SELF_SUB_ID :
                    (PhoneUtils.getDefault().isActiveSubId(self.getSubId())? self.getSubId():ParticipantData.DEFAULT_SELF_SUB_ID);//for bug841310
        }
        return ParticipantData.DEFAULT_SELF_SUB_ID;
    }

    @Override
    public void invalidateActionBar() {
        mHost.invalidateActionBar();
    }

    @Override
    public void dismissActionMode() {
        mHost.dismissActionMode();
    }

    @Override
    public void selectSim(final SubscriptionListEntry subscriptionData) {
        mComposeMessageView.selectSim(subscriptionData);
        mHost.onStartComposeMessage();
    }

    @Override
    public void onStartComposeMessage() {
        mHost.onStartComposeMessage();
    }

    @Override
    public SubscriptionListEntry getSubscriptionEntryForSelfParticipant(
            final String selfParticipantId, final boolean excludeDefault) {
        // TODO: ConversationMessageView is the only one using this. We should probably
        // inject this into the view during binding in the ConversationMessageAdapter.
        return mBinding.getData().getSubscriptionEntryForSelfParticipant(selfParticipantId,
                excludeDefault);
    }

    @Override
    public SimSelectorView getSimSelectorView() {
        return (SimSelectorView) getView().findViewById(R.id.sim_selector);
    }

    @Override
    public MediaPicker createMediaPicker() {
        return new MediaPicker(getActivity());
    }

    @Override
    public void notifyOfAttachmentLoadFailed() {
        UiUtils.showToastAtBottom(R.string.attachment_load_failed_dialog_message);
    }

    @Override
    public void warnOfExceedingMessageLimit(final boolean sending, final boolean tooManyVideos) {
        warnOfExceedingMessageLimit(sending, mComposeMessageView, mConversationId,
                getActivity(), tooManyVideos);
    }
    private AlertDialog WarnDialog=null;
    public  void warnOfExceedingMessageLimit(final boolean sending,
                                                   final ComposeMessageView composeMessageView, final String conversationId,
                                                   final Activity activity, final boolean tooManyVideos) {
          if (activity.isFinishing() || activity.isDestroyed()) {
           return;
          }
        Log.w(TAG,"warnOfExceedingMessageLimit"+activity);
        final AlertDialog.Builder builder =
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.mms_attachment_limit_reached);

        if (sending) {
            if (tooManyVideos) {
                builder.setMessage(R.string.video_attachment_limit_exceeded_when_sending);
            } else {
                builder.setMessage(R.string.attachment_limit_reached_dialog_message_when_sending)
                        .setNegativeButton(R.string.attachment_limit_reached_send_anyway,
                                new OnClickListener() {
                                    @Override
                                    public void onClick(final DialogInterface dialog,
                                                        final int which) {
                                        composeMessageView.sendMessageIgnoreMessageSizeLimit();
                                    }
                                });
            }
            builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    if(!isEmptyRecipientConversaton()){     //add for Bug 1409809
                        showAttachmentChooser(conversationId, activity);
                    }
                }
            });
        } else {
            builder.setMessage(R.string.attachment_limit_reached_dialog_message_when_composing)
                    .setPositiveButton(android.R.string.ok, null);
        }
        WarnDialog=builder.create();
        WarnDialog.show();
    }

    @Override
    public void showAttachmentChooser() {
        showAttachmentChooser(mConversationId, getActivity());
    }

    public static void showAttachmentChooser(final String conversationId,
                                             final Activity activity) {
        UIIntents.get().launchAttachmentChooserActivity(activity,
                conversationId, REQUEST_CHOOSE_ATTACHMENTS);
    }

    private void updateActionAndStatusBarColor(final ActionBar actionBar) {
        final int themeColor = ConversationDrawables.get().getConversationThemeColor();
        actionBar.setBackgroundDrawable(new ColorDrawable(themeColor));
        UiUtils.setStatusBarColor(getActivity(), themeColor);
    }

    public void updateActionBar(final ActionBar actionBar) {
        if (mComposeMessageView == null || !mComposeMessageView.updateActionBar(actionBar)) {
            updateActionAndStatusBarColor(actionBar);
            // We update this regardless of whether or not the action bar is showing so that we
            // don't get a race when it reappears.
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setDisplayHomeAsUpEnabled(true);
            // Reset the back arrow to its default
            actionBar.setHomeAsUpIndicator(0);
            View customView = actionBar.getCustomView();
            if (customView == null || customView.getId() != R.id.conversation_title_container) {
                final LayoutInflater inflator = (LayoutInflater)
                        getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                customView = inflator.inflate(R.layout.action_bar_conversation_name, null);
                /*bug 754109, remove click event, begin*/
                /*
                customView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        onBackPressed();
                    }
                });
                */
                /*bug 754109, end*/
                actionBar.setCustomView(customView);
            }

            final TextView conversationNameView =
                    (TextView) customView.findViewById(R.id.conversation_title);
            final String conversationName = getConversationName();
            if (!TextUtils.isEmpty(conversationName)) {
                // RTL : To format conversation title if it happens to be phone numbers.
                final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
                //modify for bug846965 begin
                String formattedName;
                if(mBinding.isBound() && 1 == mBinding.getData().getNumberOfParticipantsExcludingSelf() ){
                    formattedName = bidiFormatter.unicodeWrap(
                    TextUtils.ellipsize(
                        conversationName,
                        conversationNameView.getPaint(),
                        getCurrentWidthByOrientation(),/*for bug864431*/
                        TextUtils.TruncateAt.END).toString(),
                    TextDirectionHeuristicsCompat.LTR);
                }else{
                    formattedName = bidiFormatter.unicodeWrap(
                    UiUtils.commaEllipsize(
                            conversationName,
                            conversationNameView.getPaint(),
                            getCurrentWidthByOrientation() /*conversationNameView.getWidth()*//*for 811806*/,
                            getString(R.string.plus_one),
                            getString(R.string.plus_n)).toString(),
                    TextDirectionHeuristicsCompat.LTR);
                }//modify for bug846965 end
                conversationNameView.setText(formattedName);
                // In case phone numbers are mixed in the conversation name, we need to vocalize it.
                final String vocalizedConversationName =
                        AccessibilityUtil.getVocalizedPhoneNumber(getResources(), conversationName);
                conversationNameView.setContentDescription(vocalizedConversationName);

                 /*add for Bug 806979 start*/
                if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()){
                    if (!mIsOnlyDraft && (mAdapter == null || mAdapter.getItemCount() == 0)){
                        mIsOnlyDraft = true;//when change the language, need to show action_edit_contact menu
                   } else if (mIsOnlyDraft && mAdapter != null && mAdapter.getItemCount() > 0){
                        mIsOnlyDraft = false;//when received a new message, need to hide  action_edit_contact menu
                   }
                    Log.d(TAG, "updateActionBar:setTitle  mIsOnlyDraft = " + mIsOnlyDraft);
                }/*add for Bug 806979 end*/

                getActivity().setTitle(conversationName);

            } else {
                final String appName = getString(R.string.app_name);
                conversationNameView.setText(appName);
                getActivity().setTitle(appName);
            }

            // When conversation is showing and media picker is not showing, then hide the action
            // bar only when we are in landscape mode, with IME open.
            if (mHost.isImeOpen() && UiUtils.isLandscapeMode()) {
                actionBar.hide();
            } else {
                actionBar.show();
            }
        }
    }
    /*Width calculation is dynamic, which cause the actionbar to display incompletely, modify it by 811806 begin*/
    private int getCurrentWidthByOrientation(){
        if(Configuration.ORIENTATION_PORTRAIT == mLastOrientation){
            return (int)(mDisplayMetrics.widthPixels*ratio);
        }
        return (int)(mDisplayMetrics.heightPixels*ratio);
    }
    /*modify it by 811806 end*/

    @Override
    public boolean shouldShowSubjectEditor() {
        return true;
    }

    @Override
    public boolean shouldHideAttachmentsWhenSimSelectorShown() {
        return false;
    }

    @Override
    public void showHideSimSelector(final boolean show) {
        // no-op for now
    }

    @Override
    public int getSimSelectorItemLayoutId() {
        return R.layout.sim_selector_item_view;
    }

    @Override
    public Uri getSelfSendButtonIconUri() {
        return null;    // use default button icon uri
    }

    @Override
    public int overrideCounterColor() {
        return -1;      // don't override the color
    }

    @Override
    public void onAttachmentsChanged(final boolean haveAttachments) {
        // no-op for now
    }

    @Override
    public void onDraftChanged(final DraftMessageData data, final int changeFlags) {
        Log.d(TAG, "onDraftChanged: mLoadDraftDone = " + mLoadDraftDone + ", changeFlags:[" + changeFlags + "]");
        //bug 990370, begin
        if (FeatureOption.SPRD_ARCHIVE_MENU_DYNAMIC_SUPPORT) {
            setMessageContentStatus(data);
        }
        //bug 990370, end
        mDraftMessageDataModel.ensureBound(data);
        // We're specifically only interested in ATTACHMENTS_CHANGED from the widget. Ignore
        // other changes. When the widget changes an attachment, we need to reload the draft.
        if (changeFlags ==
                (DraftMessageData.WIDGET_CHANGED | DraftMessageData.ATTACHMENTS_CHANGED)) {
            mClearLocalDraft = true;        // force a reload of the draft in onResume
        }
        // Add by SPRD for bug 563344
        //sprd 572931 start
        GlobleUtil.setDraftMessageData(data);
        //sprf 572931 end
        if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
            mHost.updateContactPickerFragmentMenu();
        }
    }

    @Override
    public void onDraftAttachmentLimitReached(final DraftMessageData data) {
        // no-op for now
    }

    @Override
    public void onDraftAttachmentLoadFailed() {
        // no-op for now
    }

    @Override
    public void onDraftAttachmentOverSizeReached(final DraftMessageData data,
                                                 final MessagePartData attachment) {
    }

    @Override
    public int getAttachmentsClearedFlags() {
        return DraftMessageData.ATTACHMENTS_CHANGED;
    }

    /* SPRD: modified for bug 503091 begin */
    //add for bug 661426--begin
    private AlertDialog mDialog;
    public void showAttachmentExceededDialog(int str) {
        mDialog  = new AlertDialog.Builder(getActivity()).setTitle(R.string.warning)
                .setMessage(str).setPositiveButton(android.R.string.ok,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        // DraftMessageData draftMessageData = GlobleUtil.getDraftMessageData();
                        //long attachmentSize = draftMessageData == null?0:draftMessageData.getAttachmentsFileSize();
                        //  Log.d("CameraMediaChooser","=======showAttachmentExceededDialog===1=====attachmentSize: "+attachmentSize);
                        // mComposeMessageView.clearAttachments();
                        //  Log.d("CameraMediaChooser","=========showAttachmentExceededDialog===2=====attachmentSize: "+attachmentSize);
                    }
                }
        ).create();
        mDialog.show();
        //add for bug 661426--end
    }

    /* SPRD: modified for bug 503091 end */
    public void getImportAlertMessage(final MessagePartData attachment) {
        final Activity activity = getActivity();
        String msg;
        if (attachment.isVCalendar()) {
            msg = activity.getString(R.string.confirm_import_vcalendar);
        } else {
            msg = activity.getString(R.string.confirm_save_otherfiles);
        }
        AlertDialog.Builder adb = new AlertDialog.Builder(activity);
        adb.setMessage(msg);
        adb.setPositiveButton(R.string.confirm_import, new OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                if (attachment.isVCalendar()) {
                    UIIntents.get().launchVCalendarActivity(activity,
                            attachment.getContentUri());
                } else if (!attachment.isMedia()) {
                    // FIXME: drm files cannot be saved? delete it, if possible.
                    if (ContentType.isDrmType(attachment.getContentType())) {
                        UiUtils.showToastAtBottom(R.string.drm_can_not_processed);
                    } else {
                        if (OsUtil.hasStoragePermission()) {
                            SaveAttachmentTask saveAttachmentTask = new SaveAttachmentTask(
                                    getActivity());
                            saveAttachmentTask.addAttachmentToSave(attachment.getContentUri(),
                                    attachment.getContentType());
                            if (saveAttachmentTask.getAttachmentCount() > 0) {
                                saveAttachmentTask.executeOnThreadPool();
                            }
                        } else {
                            getActivity().requestPermissions(
                                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
                        }
                    }
                }
            }
        });
        adb.setNegativeButton(R.string.cancel_import, null);
        adb.show();
    }

    /* Add by SPRD for bug 563344 Start */
    @Override
    public void onDraftLoadDone(DraftMessageData data) {
        /* Add by SPRD for bug 583553 2016.07.27 Start */
        mLoadDraftDone = true;
        clearIntentExtraDraft(); //Bug 914655
        int newAttachmentCount = data.getAttachments().size();
        if (mOnAttachmentChosen && mOldAttachmentCount != newAttachmentCount) {
            mOnAttachmentChosen = false;
        }
        mOldAttachmentCount = newAttachmentCount;
        Log.d(TAG, "onDraftLoadDone: mLoadDraftDone = " + mLoadDraftDone +
                ", mOldAttachmentCount = " + mOldAttachmentCount);
        /* Add by SPRD for bug 583553 2016.07.27 End */
    }

    /* Add by SPRD for bug 563344 End */
    @Override
    public void StartSlideshow() {
        if (shouldWarningConvertToSlides()) {
            warningConvertToSlides();
        }
    }

    /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
    private void editParticipantsInDraft(){
        mHost.getConversationUiState().editParticipantsInDraft();
    }
    /*Add by SPRD for edit contact in draft 2016.09.06 End*/

    private AlertDialog mWarningConvertToSlidesDialog = null;

    /* Add by SPRD for bug 585286 2016.08.08 Start */
    private void warningConvertToSlides() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.mms_convert_to_smil_title).setMessage(R.string.mms_convert_to_smil_message);
        builder.setPositiveButton(R.string.confirm_convert, null);
        builder.setNegativeButton(R.string.cancel_convert, null);
        mWarningConvertToSlidesDialog = builder.show();
    }
    /* Add by SPRD for bug 585286 2016.08.08 End */

    /* Add by SPRD for bug 579252 2016.08.12 Start */
    private boolean shouldWarningConvertToSlides() {
        DraftMessageData draft = mComposeMessageView.getDraftDataModel().getData();
        if (draft.mProtocol == MessageData.PROTOCOL_MMS_SMIL) return false;
        if (draft.getAttachments().size() > 1) {
            return true;
        } else if (draft.getAttachments().size() == 1) {
            MessagePartData p = draft.getAttachments().get(0);
            return !(p.isAudio() || p.isVideo() || p.isImage());
        }
        return false;
    }
    /* Add by SPRD for bug 579252 2016.08.12 Start */

    private void createSimSelectDialog(final List<String> smsUriList) {
        final ArrayList<String> simList = new ArrayList<String>();
        if (!OsUtil.hasPhonePermission()) {
            OsUtil.requestMissingPermission(getActivity());
        } else {
            final List<SubscriptionInfo> infoList = SystemAdapter.getInstance().getActiveSubInfoList();
            if (infoList != null && infoList.size() != 0) {
                Iterator iterator = infoList.iterator();
                while (iterator.hasNext()) {
                    SubscriptionInfo subscriptionInfo = (SubscriptionInfo) iterator.next();
                    String simNameText = subscriptionInfo.getDisplayName().toString();
                    String displayName = TextUtils.isEmpty(simNameText) ? getActivity()
                            .getString(R.string.sim_slot_identifier,
                                    subscriptionInfo.getSimSlotIndex() + 1) : simNameText;
                    simList.add(displayName);
                }
            }

            ArrayAdapter<String> simAdapter = new ArrayAdapter<String>(getActivity(),
                    R.layout.display_options, simList) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    String simName = getItem(position);
                    TextView tv = (TextView) v;
                    tv.setText(simName);
                    return v;
                }
            };

            DialogInterface.OnClickListener simSelectListener = new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    SubscriptionInfo subscriptionInfo = infoList.get(which);
                    int subId = subscriptionInfo.getSubscriptionId();
                    SimUtils.copySmsToSim(getContext(), smsUriList, subId);
                }
            };
            AlertDialog.Builder simSelectDialog = new AlertDialog.Builder(getActivity());
            simSelectDialog.setTitle(R.string.select_sim);
            simSelectDialog.setAdapter(simAdapter, simSelectListener);
            simSelectDialog.setNegativeButton(android.R.string.cancel, null);
            mSimSelectDialog = simSelectDialog.show();
        }
    }

    //add for bug 610115 start
    private IntentFilter mSimFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
    private BroadcastReceiver mSimInOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "receive sim state changed.");
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            Log.d(TAG, "sim status is:" + simStatus);
            if (intent.getAction() == TelephonyIntents.ACTION_SIM_STATE_CHANGED){
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)){
                    if (mSimSelectDialog != null && mSimSelectDialog.isShowing()) {
                        mSimSelectDialog.dismiss();
                    }
                }
            }
        }
    };
    //add for bug 610115 end

    private IntentFilter mAirPlaneModeFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    private BroadcastReceiver mAirPlaneModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isAirPlaneMode = intent.getBooleanExtra("state", false);
            Log.d(TAG, "mAirReceiver onReceive()  enable = " + isAirPlaneMode);
            if (isAirPlaneMode) {
                if (mSimSelectDialog != null && mSimSelectDialog.isShowing()) {
                    mSimSelectDialog.dismiss();
                }
            }
        }
    };

    /*smart message, begin*/
    private boolean isSmartMessage() {
        final ConversationData conversationData = mBinding.getData();
        ParticipantData participant = conversationData.getOtherParticipant();
        if (conversationData.getParticipantsLoaded()
                && participant != null
                && participant.getProfilePhotoUri() != null
                && participant.getProfilePhotoUri().startsWith("http")) {
            return true;
        }
        Log.d(LogUtil.BUGLE_SMART_TAG, " isSmartMessage=false!");
        return false;
    }

    class CtccOnClickListenerImpl implements View.OnClickListener {
        private com.gstd.callme.outerentity.MenuInfo menu;

        public CtccOnClickListenerImpl(com.gstd.callme.outerentity.MenuInfo menu, com.gstd.callme.outerentity.CardInfo cardInfo) {
            super();
            this.menu = menu;
        }

        @Override
        public void onClick(View v) {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }

            try {
                SmartSmsEngineManager.getInstance().getDefaultSmartSmsEngine().processMenuOperation((Activity) getContext(), menu.getOperation(), menu.getType(),
                        new com.gstd.callme.UI.inter.ProcessMenuOperationListener() {
                    @Override
                    public void sendSms(String content, String smsNumber) {
                        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
                        ArrayList<String> texts = smsManager.divideMessage(content);
                        smsManager.sendMultipartTextMessage(smsNumber, null, texts, null, null);
                    }

                    @Override
                    public void showPopupWindow(String strPackageName, String strPackageActName, String appName) {
                        ShowAppStoreDialog(new String[]{strPackageName, strPackageActName, appName});
                    }

                    @Override
                    public void callPhone(String smsNumber) {
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + smsNumber));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    }

                    @Override
                    public void trafficBuy(String smsNumber) {
                        Toast.makeText(mContext, "trafficBuy Number:"+smsNumber, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void scheduleOpen(long startTime, long endTime) {
                        String ALERT_APP_PACKAGE_NAME = "com.android.calendar";
                        String ALERT_APP_PACKAGE_LANUCH = "com.android.calendar.LaunchActivity";

                        Intent intent = new Intent();
                        intent.putExtra("beginTime", startTime);
                        intent.putExtra("endTime", endTime);
                        intent.setComponent(new ComponentName(ALERT_APP_PACKAGE_NAME, ALERT_APP_PACKAGE_LANUCH));
                        mContext.startActivity(intent);
                    }

                    @Override
                    public void openWebView(String url, String title, String param) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "http://" + url;
                        }
                        Intent intent = new Intent();
                        intent.setAction("android.intent.action.VIEW");
                        Uri content_url = Uri.parse(url);
                        intent.setData(content_url);
                        mContext.startActivity(intent);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class OnClickListenerImpl implements View.OnClickListener {
        private MenuInfo menu;
        private CardInfo cardInfo;

        public OnClickListenerImpl(MenuInfo menu, CardInfo cardInfo) {
            super();
            this.menu = menu;
            this.cardInfo = cardInfo;
        }

        @Override
        public void onClick(View v) {
            if (popupWindow != null && popupWindow.isShowing()) {
                popupWindow.dismiss();
            }

            try {
                SmartSmsEngine.getInstance().processMenuOperation((Activity) getContext(), menu.getOperation(), menu.getType(), new ProcessMenuOperationListener() {
                    @Override
                    public void sendSms(String content, String smsNumber) {
                        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
                        ArrayList<String> texts = smsManager.divideMessage(content);
                        smsManager.sendMultipartTextMessage(smsNumber, null, texts, null, null);
                    }

                    @Override
                    public void showPopupWindow(String strPackageName, String strPackageActName, String appName) {
                        ShowAppStoreDialog(new String[]{strPackageName, strPackageActName, appName});
                    }

                    @Override
                    public void callPhone(String smsNumber) {
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + smsNumber));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    }

                    @Override
                    public void trafficBuy(String smsNumber) {
                        Toast.makeText(mContext, "trafficBuy Number:"+smsNumber, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void scheduleOpen(long startTime, long endTime) {
                        String ALERT_APP_PACKAGE_NAME = "com.android.calendar";
                        String ALERT_APP_PACKAGE_LANUCH = "com.android.calendar.LaunchActivity";

                        Intent intent = new Intent();
                        intent.putExtra("beginTime", startTime);
                        intent.putExtra("endTime", endTime);
                        intent.setComponent(new ComponentName(ALERT_APP_PACKAGE_NAME, ALERT_APP_PACKAGE_LANUCH));
                        mContext.startActivity(intent);
                    }

                    @Override
                    public void openWebView(String url, String title, String param) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "http://" + url;
                        }
                        Intent intent = new Intent();
                        intent.setAction("android.intent.action.VIEW");
                        Uri content_url = Uri.parse(url);
                        intent.setData(content_url);
                        mContext.startActivity(intent);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Bug 914655 begin
    private void clearIntentExtraDraft() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final Intent intent = activity.getIntent();
        if (intent == null) {
            return;
        }
        intent.removeExtra(UIIntents.UI_INTENT_EXTRA_DRAFT_DATA);
    }
    //Bug 914655 end

    private int serverMenusNumber = 0;/*Bug 652299 */

    private boolean canStoreToSim (int subId, ConversationMessageData data) {
        return !MmsUtils.isAirplaneModeOn(subId) && data.getIsSms()
                && ((MmsConfig.get(subId).getFinalSendEmptyMessageFlag() > 0)
                || (MmsConfig.get(subId).getFinalSendEmptyMessageFlag() == 0
                && data.getText() != null && data.getText().length() > 0))
                && ((data.getStatus() >= MessageData.BUGLE_STATUS_INCOMING_COMPLETE
                && data.getStatus() <= MessageData.BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE)
                || (data.getStatus() == MessageData.BUGLE_STATUS_OUTGOING_COMPLETE
                || data.getStatus() == MessageData.BUGLE_STATUS_OUTGOING_DELIVERED));
    }

    public void peopleAndOptionsAction() {
        addActionBarMenu(R.id.action_people_and_options, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public void addCommonPhraseAction() {
        addActionBarMenu(R.id.action_add_phrase, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public void archiveConversationAction() {
        addActionBarMenu(R.id.action_archive, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public void unArchiveConversationAction() {
        addActionBarMenu(R.id.action_unarchive, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public void deleteConversationAction() {
        addActionBarMenu(R.id.action_delete, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public void forwardAction() {
        addActionBarMenu(R.id.action_sms_merge_forward, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public void slideShowAction() {
        //addActionBarMenu(R.id.goto_smil, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public boolean checkConvIsArchived() {
        if (mBinding.isBound()) { /*modified for Bug 796017 start*/
            return mBinding.getData().getIsArchived();
        }
        return false; /*modified for Bug 796017 end*/
    }

    public void callAction() {
        addActionBarMenu(R.id.action_call, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    public void addContactAction() {
        addActionBarMenu(R.id.action_add_contact, Menu.FLAG_PERFORM_NO_CLOSE);
    }

    private void addActionBarMenu(final int id, final int flags) {
        try {
            if (mActionBarMenuList != null) {
                mActionBarMenuList.performIdentifierAction(id, flags);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	 /**************************White Box testing start**********************************/
    public String getConverWhiteTest(){
        return mHost.getWhiteTest();
    }

    public String getConverTestSim(){
        return mHost.getestSim();
    }

    public void sendConverFramBroadcase(){
        mHost.sendConverActivityBroadcase();

    }

    /**************************White Box testing end**********************************/
    //bug 990370, begin
    private void setMessageContentStatus(DraftMessageData draftMessageData){
        if(null == draftMessageData){
            return;
        }
        if((getActivity() instanceof ConversationActivity) && MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()){
            ((ConversationActivity)getActivity()).updateArchiveMenu(draftMessageData);
        }
    }
    //bug 990370, end

    //bug 990333, begin
    private boolean hasContent(){
        if(mAdapter != null && mAdapter.getItemCount() > 0){
            return true;
        }
        DraftMessageData draft = mComposeMessageView.getDraftDataModel().getData();
        if(draft != null){
            String messageText = draft.getMessageText();
            if(messageText != null){
                messageText = messageText.trim();
            }
            return !TextUtils.isEmpty(messageText) || !TextUtils.isEmpty(draft.getMessageSubject()) || draft.hasAttachments();
        }
        return false;
    }
    //bug 990333, end

    /*
    * Bug 1004415 begin
    * entry conversation from share, use FLAG_ACTIVITY_CLEAR_TOP flag, so base activity is equal to
    * top activity in first task
    * entry conversation from conversation list, use default launch mode, base activity isn't equal
    * to top activity in first task
     */
    private boolean entryFromShareAction() {
        final ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RecentTaskInfo> infoList = null;
        try {
            infoList = am.getRecentTasks(1, ActivityManager.RECENT_WITH_EXCLUDED);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        if (infoList == null || infoList.size() == 0) {
            return false;
        }
        final String baseActivity = infoList.get(0).baseActivity.getClassName();
        final String topActivity = infoList.get(0).topActivity.getClassName();
        if (baseActivity.equals(topActivity)) {
            return true;
        } else {
            return false;
        }
    }
    /* Bug 1004415 end */

    /* Bug 1422403 begin
    * When you click the share button, judge whether the sharing interface exists,
    * and return true if it exists.
    * */
    private boolean isChooserActivityExists(){
        final ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RecentTaskInfo> shareList = null;
        try {
            shareList = manager.getRecentTasks(1, ActivityManager.RECENT_WITH_EXCLUDED);
        }catch (SecurityException e) {
            e.printStackTrace();
        }
        if (shareList == null || shareList.size() == 0){
            return false;
        }
        final String topActivity = shareList.get(0).topActivity.getClassName();
        if (mChooserActivity.equals(topActivity)){
            return true;
        }else {
            return false;
        }
    }
    /* Bug 1422403 end */

    @Override
    public void updateSmartMessage(String partId){//bug 1008873
      if(!isDetached() && !isSmartMessage()){
          try{
              mBinding.ensureBound();
              mBinding.getData().restart(getLoaderManager(), mBinding);
          }catch (Exception e){
              e.printStackTrace();
          }
      }
    }

    public void onPendingAttachmentAdded(final PendingAttachmentData pendingItem){
        mComposeMessageView.onPendingAttachmentAdded(pendingItem);
    }
    public void onTextContactsAdded(final String string){
        mComposeMessageView.onTextContactsAdded(string);
    }

    /*smart message, begin*/
    private int index;

    private void refreshPopupWindow() {
        if (popupWindow != null && popupWindow.isShowing()) {
            View clickedView = mServerMenu.getChildAt(index);
            if (clickedView != null) {
                updatePopupWindow(popupWindow, clickedView);
            }
        }
    }

    private void refreshPopupWindowByNavigationBarShowing() {
        if (popupWindow != null && popupWindow.isShowing()) {
            final View clickedView = mServerMenu.getChildAt(index);
            if (clickedView != null) {
                updatePopupWindow(popupWindow, clickedView);
            }
        }
    }

    private boolean mServerMenuShouldShow = true;

    @Override
    public void showServerMenu(boolean show) {
        mServerMenuShouldShow = show;
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "showServerMenu, getChildCount: " + mServerMenu.getChildCount());
        if (mServerMenu.getChildCount() > 0){/*add for Bug 652299 */
            mServerMenu.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateServiceMenu(Context mContext, String mPort) {
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "updateServiceMenu, mPort = " + mPort);
        if (MmsConfig.getCmccSdkEnabled()) {
            new GetMenuInfoAsyncTask(mContext, mPort).execute();
        } else if (MmsConfig.getCtccSdkEnabled()) {
            boolean result = CtccSmartSdk.getInstance().isServiceAddress(mContext, mPort);
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "CTCC updateServiceMenu, mPort/smart:" + mPort + "/" + result);
            if (result) {
                CtccSmartSdk.getInstance().asyncGetOrgInfo(mContext, mPort, new IOrgCallback() {
                    @Override
                    public void onSuccess(com.gstd.callme.outerentity.OrgInfo info) {
                        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "updateServiceMenu onSuccess OrgInfo:" + info);
                        if (info != null && info.isValidOrgInfo()) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        showServerMenuCtcc(info);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onFail() {
                        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "updateServiceMenu OrgInfo FAIL");
                    }
                });
            }
        } else if (MmsConfig.getCuccSdkEnabled()) {
            final OrgInfo orgInfo = SmartSmsEngine.getInstance().getOrgInfo(mContext, mPort);
            showServerMenuCucc(orgInfo);
        }
    }

    private int dp2px(int dp) {
        return (int) (mContext.getResources().getDisplayMetrics().density * dp + 0.5);
    }

    private void showPopupWindow(PopupWindow popupWindow, View view){
        final int yOffset;
        if((mLastOrientation == Configuration.ORIENTATION_PORTRAIT)
                && Utils.hasNavigationBar(mContext)
                && Utils.isNavigationBarShowing(mContext)){
            yOffset = view.getHeight() + dp2px(5) + Utils.getNavigationBarHeight(mContext);
        }else{
            yOffset = view.getHeight() + dp2px(5);
        }
        int[] location = new int[2] ;
        view.getLocationOnScreen(location);
        popupWindow.showAtLocation(view, Gravity.BOTTOM | Gravity.LEFT, location[0], yOffset);
    }

    private void updatePopupWindow(PopupWindow popupWindow, View view){
        int[] location = new int[2] ;
        view.getLocationOnScreen(location);
        final int yOffset;
        if((mLastOrientation == Configuration.ORIENTATION_PORTRAIT)
                && Utils.hasNavigationBar(mContext)
                && Utils.isNavigationBarShowing(mContext)){
            yOffset = view.getHeight() + dp2px(5) + Utils.getNavigationBarHeight(mContext);
        }else{
            yOffset = view.getHeight() + dp2px(5);
        }
        popupWindow.update(location[0], yOffset, view.getWidth(), popupWindow.getHeight());
    }

    private final class GetMenuInfoAsyncTask extends AsyncTask<Void, Void, List<cn.cmcc.online.smsapi.entity.Menu>> {
        private Context mContext;
        private String mServerPort;

        public GetMenuInfoAsyncTask(Context context, String port) {
            this.mContext = context;
            this.mServerPort = port;
        }

        @Override
        protected final List<cn.cmcc.online.smsapi.entity.Menu> doInBackground(final Void... params) {
            SmsPortData portData = TerminalApi.getPortDataManager(mContext).getPortInfo(mContext, mServerPort);
            return portData.getMenuList();
        }

        @Override
        protected void onPostExecute(List<cn.cmcc.online.smsapi.entity.Menu> menuList) {
            showServerMenuCmcc(menuList, mServerPort);
        }
    }

    private TextView getSingleMenuItem(String name, boolean hasSubMenu) {
        TextView textView = new TextView(mContext);
        textView.setText(name);
        textView.setPadding(5, 5, 5, 5);
        textView.setTextSize(14);
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundDrawable(mContext.getDrawable(R.drawable.menu_bg));
        if (hasSubMenu) {
            Drawable subMenuIcon = mContext.getDrawable(R.drawable.ic_smart_sms_menu);
            subMenuIcon.setBounds(1, 1, 48, 48);
            textView.setCompoundDrawables(subMenuIcon, null, null, null);
        }
        return textView;
    }

    private LinearLayout contentView;
    private PopupWindow popupWindow;

    private void generatePopupWindow(LinearLayout contentView, int width) {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        popupWindow = new PopupWindow(contentView);
        popupWindow.setWidth(width);
        popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setBackgroundDrawable(mContext.getDrawable(R.drawable.textview_border));
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
    }

    private void showServerMenuCmcc(List<cn.cmcc.online.smsapi.entity.Menu> menuList, String mServerPort) {
        if (menuList == null || menuList.size() == 0) {
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "showServerMenuCmcc menuList return");
            return;
        }
        mServerMenu.removeAllViews();
        mServerMenu.setVisibility(View.VISIBLE);
        for (final cn.cmcc.online.smsapi.entity.Menu menu : menuList) {
            final String name = menu.getName();
            final List<cn.cmcc.online.smsapi.entity.Menu> subMenuList = menu.getSubMenuList();
            final boolean hasSubMenu = (subMenuList != null && subMenuList.size() > 0);
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "showServerMenuCmcc hasSubMenu:" + hasSubMenu);

            TextView menuItem = getSingleMenuItem(menu.getName(), hasSubMenu);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1, 0.3F);
            mServerMenu.addView(menuItem, params);
            menuItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (hasSubMenu) {
                        contentView = new LinearLayout(mContext);
                        contentView.setOrientation(LinearLayout.VERTICAL);
                        contentView.setDividerDrawable(mContext.getDrawable(R.drawable.divider_horizontal));
                        contentView.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
                        generatePopupWindow(contentView, view.getWidth());
                        final InputMethodManager inputMethodManager =
                                (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        for (final cn.cmcc.online.smsapi.entity.Menu subMenu : subMenuList) {
                            TextView textView = new TextView(mContext);
                            textView.setTextSize(14);
                            textView.setText(subMenu.getName());
                            textView.setHeight(view.getHeight());
                            textView.setGravity(Gravity.CENTER);
                            textView.setBackgroundDrawable(mContext.getDrawable(R.drawable.menu_bg));
                            textView.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    TerminalApi.getActionProcessor(mContext).processPortServerAction(mContext, mServerPort, subMenu, null);
                                    if (popupWindow != null && popupWindow.isShowing()) {
                                        popupWindow.dismiss();
                                    }
                                }
                            });
                            contentView.addView(textView);
                        }
                        index = mServerMenu.indexOfChild(view);
                        if ((mLastOrientation == Configuration.ORIENTATION_PORTRAIT) && Utils.isNavigationBarShowing(mContext)) {
                            popupWindow.showAtLocation(view, Gravity.BOTTOM | Gravity.LEFT, view.getLeft(), view.getHeight() + dp2px(5) + Utils.getNavigationBarHeight(mContext));//20
                        } else {
                            popupWindow.showAtLocation(view, Gravity.BOTTOM | Gravity.LEFT, view.getLeft(), view.getHeight() + dp2px(5));
                        }
                    } else {
                        TerminalApi.getActionProcessor(mContext).processPortServerAction(mContext, mServerPort, menu, null);
                    }
                }
            });
        }
    }

    private void showServerMenuCucc(OrgInfo orgInfo) {
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, " showServerMenuCucc: orgInfo=" + orgInfo);
        if (orgInfo != null && orgInfo.getMenuInfoList() != null) {
            mServerMenu.removeAllViews();
            serverMenusNumber = orgInfo.getMenuInfoList().size();
            mServerMenu.setVisibility(View.VISIBLE);
            for (MenuInfo menuInfo : orgInfo.getMenuInfoList()) {
                final List<MenuInfo> subMenu = menuInfo.getSubMenu();
                boolean hasSubMenu = (subMenu != null && subMenu.size() > 0);
                TextView menuItem = getSingleMenuItem(menuInfo.getTitle(), hasSubMenu);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1, 0.3F);
                mServerMenu.addView(menuItem, params);
                menuItem.setOnClickListener(new OnClickListenerImpl(menuInfo, null) {
                    @Override
                    public void onClick(View view) {
                        if (hasSubMenu) {
                            contentView = new LinearLayout(mContext);
                            contentView.setOrientation(LinearLayout.VERTICAL);
                            contentView.setDividerDrawable(mContext.getDrawable(R.drawable.divider_horizontal));
                            contentView.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
                            generatePopupWindow(contentView, view.getWidth());
                            final InputMethodManager inputMethodManager =
                                    (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
                            for (int i = 0; i < subMenu.size(); i++) {
                                try {
                                    MenuInfo subMenuInfo = subMenu.get(i);
                                    TextView textView = new TextView(mContext);
                                    textView.setTextSize(14);
                                    textView.setText(subMenuInfo.getTitle());
                                    textView.setHeight(view.getHeight());
                                    textView.setGravity(Gravity.CENTER);
                                    textView.setBackgroundDrawable(mContext.getDrawable(R.drawable.menu_bg));
                                    textView.setOnClickListener(new OnClickListenerImpl(subMenuInfo, null));
                                    contentView.addView(textView);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            index = mServerMenu.indexOfChild(view);
                            showPopupWindow(popupWindow, view);
                        } else {
                            super.onClick(view);
                        }
                    }
                });
            }
        }
    }

    private void showServerMenuCtcc(com.gstd.callme.outerentity.OrgInfo orgInfo) {
        if (orgInfo != null && orgInfo.getMenuInfoList() != null) {
            mServerMenu.removeAllViews();
            mServerMenu.setVisibility(View.VISIBLE);
            for (com.gstd.callme.outerentity.MenuInfo menuInfo : orgInfo.getMenuInfoList()) {
                final String name = menuInfo.getTitle();
                final List<com.gstd.callme.outerentity.MenuInfo> subMenu = menuInfo.getSubMenu();
                boolean hasSubMenu = (subMenu != null && subMenu.size() > 0);
                TextView menuItem = getSingleMenuItem(name, hasSubMenu);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1, 0.3F);
                mServerMenu.addView(menuItem, params);
                menuItem.setOnClickListener(new CtccOnClickListenerImpl(menuInfo, null) {
                    @Override
                    public void onClick(View view) {
                        if (hasSubMenu) {
                            contentView = new LinearLayout(mContext);
                            contentView.setOrientation(LinearLayout.VERTICAL);
                            contentView.setDividerDrawable(mContext.getDrawable(R.drawable.divider_horizontal));
                            contentView.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
                            generatePopupWindow(contentView, view.getWidth());
                            final InputMethodManager inputMethodManager =
                                    (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
                            for (int i = 0; i < subMenu.size(); i++) {
                                try {
                                    com.gstd.callme.outerentity.MenuInfo subMenuInfo = subMenu.get(i);
                                    final String name = subMenuInfo.getTitle();
                                    TextView textView = new TextView(mContext);
                                    textView.setTextSize(14);
                                    textView.setText(name);
                                    textView.setHeight(view.getHeight());
                                    textView.setGravity(Gravity.CENTER);
                                    textView.setBackgroundDrawable(mContext.getDrawable(R.drawable.menu_bg));
                                    textView.setOnClickListener(new CtccOnClickListenerImpl(subMenuInfo, null));
                                    contentView.addView(textView);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            index = mServerMenu.indexOfChild(view);
                            showPopupWindow(popupWindow, view);
                        } else {
                            super.onClick(view);
                        }
                    }
                });
            }
        }
    }
    /*smart message, end*/
}
