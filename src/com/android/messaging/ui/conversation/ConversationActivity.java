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
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;

import com.android.messaging.R;
import com.android.messaging.datamodel.action.DeleteConversationAction;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.SnackBarManager;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.contact.ContactPickerFragment;
import com.android.messaging.ui.contact.ContactPickerFragment.ContactPickerFragmentHost;
import com.android.messaging.ui.contact.ContactRecipientAutoCompleteView;
import com.android.messaging.ui.conversation.ConversationActivityUiState.ConversationActivityUiStateHost;
import com.android.messaging.ui.conversation.ConversationFragment.ConversationFragmentHost;
import com.android.messaging.ui.conversationlist.ConversationListActivity;
import com.android.messaging.ui.mediapicker.VcardPicker;
import com.android.messaging.util.Assert;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.WhiteTestType;
import com.sprd.messaging.util.StorageUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConversationActivity extends BugleActionBarActivity
        implements ContactPickerFragmentHost, ConversationFragmentHost,
        ConversationActivityUiStateHost {
    private static final String TAG = "ConversationActivity";//by sprd
    public static final int FINISH_RESULT_CODE = 1;
    public static boolean sContactConfirmAct = false;

    private static final String SAVED_INSTANCE_STATE_UI_STATE_KEY = "uistate";
    private static final String SAVED_INSTANCE_IS_VALID_CON = "isValidCon";//Fit for lang-change by bug854037
    private static final String SAVED_INSTANCE_EMPTY_RECIPIENT = "isEmptyRecipient"; //Fit for lang-change by bug1368245
    private ProgressDialog mWaitingDialog;
    private final AtomicInteger mWaitingDialogCount = new AtomicInteger();
    private boolean isResumed = false;
    private ConversationActivityUiState mUiState;

    // Fragment transactions cannot be performed after onSaveInstanceState() has been called since
    // it will cause state loss. We don't want to call commitAllowingStateLoss() since it's
    // dangerous. Therefore, we note when instance state is saved and avoid performing UI state
    // updates concerning fragments past that point.
    private boolean mInstanceStateSaved;

    // Tracks whether onPause is called.
    private boolean mIsPaused;
    /*Add by SPRD for bug581044  2016.07.08 Start*/
    private String oldConversationId = null;
    private boolean isValidConversation; /*add for bug 653194 */
    /*Add by SPRD for bug581044  2016.07.08 End*/
    private boolean mContactPickerNeedLoadDataFromDb = true;  //Bug 1004355
    private StorageUtil mStorageUtil = null;
    //add for 1357062
    private boolean mEmptyRecipientInConversationFrag;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_activity);

        final Intent intent = getIntent();
        // Do our best to restore UI state from saved instance state.
        Log.d(TAG,"onCreate: savedInstanceState = " + savedInstanceState);
        if (savedInstanceState != null) {
            mUiState = savedInstanceState.getParcelable(SAVED_INSTANCE_STATE_UI_STATE_KEY);
            isValidConversation = savedInstanceState.getBoolean(SAVED_INSTANCE_IS_VALID_CON);//Fit for lang-change by bug854037
            mEmptyRecipientInConversationFrag = savedInstanceState.getBoolean(SAVED_INSTANCE_EMPTY_RECIPIENT);//Fit for bug1368245
            //add by Bug 725766  start
            boolean isConversationOnly =  (mUiState.getConversationContactUiState() == ConversationActivityUiState.STATE_CONVERSATION_ONLY);
            Log.d(TAG,"onCreate: isConversationOnly = " + isConversationOnly);
            if (isConversationOnly) getIntent().removeExtra(UIIntents.UI_INTENT_EXTRA_DRAFT_DATA);
            //add by Bug 725766  end
        } else {
            if (intent.
                    getBooleanExtra(UIIntents.UI_INTENT_EXTRA_GOTO_CONVERSATION_LIST, false)) {
                // See the comment in BugleWidgetService.getViewMoreConversationsView() why this
                // is unfortunately necessary. The Bugle desktop widget can display a list of
                // conversations. When there are more conversations that can be displayed in
                // the widget, the last item is a "More conversations" item. The way widgets
                // are built, the list items can only go to a single fill-in intent which points
                // to this ConversationActivity. When the user taps on "More conversations", we
                // really want to go to the ConversationList. This code makes that possible.
                /*Modify by SPRD for bug526121 20160201  Start*/
                final Intent convListIntent = new Intent(this, ConversationListActivity.class);
                convListIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(convListIntent);
                finish();
                /*Modify by SPRD for bug526121 20160201  End*/
                return;
            }
        }

        // If saved instance state doesn't offer a clue, get the info from the intent.
        if (mUiState == null) {
            final String conversationId = intent.getStringExtra(
                    UIIntents.UI_INTENT_EXTRA_CONVERSATION_ID);
            mUiState = new ConversationActivityUiState(conversationId);
        }
        mUiState.setHost(this);
        mInstanceStateSaved = false;

        // Don't animate UI state change for initial setup.
        updateUiState(false /* animate */);

        // See if we're getting called from a widget to directly display an image or video
        final String extraToDisplay =
                intent.getStringExtra(UIIntents.UI_INTENT_EXTRA_ATTACHMENT_URI);
        if (!TextUtils.isEmpty(extraToDisplay)) {
            final String contentType =
                    intent.getStringExtra(UIIntents.UI_INTENT_EXTRA_ATTACHMENT_TYPE);
            final Rect bounds = UiUtils.getMeasuredBoundsOnScreen(
                    findViewById(R.id.conversation_and_compose_container));
            //if (ContentType.isImageType(contentType)) {
            //    final Uri imagesUri = MessagingContentProvider.buildConversationImagesUri(
            //            mUiState.getConversationId());
            //    UIIntents.get().launchFullScreenPhotoViewer(
            //            this, Uri.parse(extraToDisplay), bounds, imagesUri);
            //} else if (ContentType.isVideoType(contentType)) {
            //    UIIntents.get().launchFullScreenVideoViewer(this, Uri.parse(extraToDisplay));
            //}
        }
        GlobleUtil.registerHandler(GlobleUtil.TAG_LOADING_DIALOG, mWaitingDialogHandler);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        // After onSaveInstanceState() is called, future changes to mUiState won't update the UI
        // anymore, because fragment transactions are not allowed past this point.
        // For an activity recreation due to orientation change, the saved instance state keeps
        // using the in-memory copy of the UI state instead of writing it to parcel as an
        // optimization, so the UI state values may still change in response to, for example,
        // focus change from the framework, making mUiState and actual UI inconsistent.
        // Therefore, save an exact "snapshot" (clone) of the UI state object to make sure the
        // restored UI state ALWAYS matches the actual restored UI components.
        outState.putParcelable(SAVED_INSTANCE_STATE_UI_STATE_KEY, mUiState.clone());
        outState.putBoolean(SAVED_INSTANCE_IS_VALID_CON,isValidConversation);//Fit for lang-change by bug854037
        outState.putBoolean(SAVED_INSTANCE_EMPTY_RECIPIENT, mEmptyRecipientInConversationFrag);//Fit for bug1368245
        mInstanceStateSaved = true;
    }

    //Bug 945427 begin
    @Override
    public void onRestoreInstanceState(Bundle state) {
        setContactPickerNeedLoadDataFromDb(false);   //Bug 1004355
        super.onRestoreInstanceState(state);
    }

    //Bug 1004355 begin
    @Override
    public boolean contactPickerNeedLoadDataFromDb() {
        return mContactPickerNeedLoadDataFromDb;
    }
    //Bug 1004355 end
    //Bug 945427 end

    //Bug 1004355 begin
    @Override
    public void setContactPickerNeedLoadDataFromDb(final boolean loadDataFromDb) {
        mContactPickerNeedLoadDataFromDb = loadDataFromDb;
    }
    //Bug 1004355 end

    @Override
    protected void onResume() {
        super.onResume();

        // we need to reset the mInstanceStateSaved flag since we may have just been restored from
        // a previous onStop() instead of an onDestroy().
        mInstanceStateSaved = false;
        mIsPaused = false;
        isResumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
        isResumed = false;
        /* SPRD: Bug 572242 add for hide the SoftInput. @{ */
        InputMethodManager imm = (InputMethodManager) getSystemService(ConversationActivity.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        /* @} */
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        final ConversationFragment conversationFragment = getConversationFragment();
        // When the screen is turned on, the last used activity gets resumed, but it gets
        // window focus only after the lock screen is unlocked.
        if (hasFocus && conversationFragment != null) {
            conversationFragment.setConversationFocus();
        }
    }

    @Override
    public void onDisplayHeightChanged(final int heightSpecification) {
        super.onDisplayHeightChanged(heightSpecification);
        invalidateActionBar();
    }

    @Override
    protected void onDestroy() {
        if (mUiState != null) {
            mUiState.setHost(null);
        }
        GlobleUtil.unRegisterHandler(GlobleUtil.TAG_LOADING_DIALOG);
        //Bug1186028 begin
        if (mStorageUtil != null) {
            mStorageUtil.closeAlertDialog();
        }
        //Bug1186028 end
        //Bug968927 begin
        if(null != SnackBarManager.sInstance){
            if(null != SnackBarManager.sInstance.getPopupWindow()){
                SnackBarManager.sInstance.getPopupWindow().dismiss();
            }
        }
        closeDraftConfirmDialog();
        //Bug968927 end
        android.util.Log.d(TAG,"[Con]====onDestroy====");
        super.onDestroy();/*modified for Bug 796017*/
    }

    @Override
    public void updateActionBar(final ActionBar actionBar) {
        super.updateActionBar(actionBar);
        Log.d(TAG, "updateActionBar start ");
        final ConversationFragment conversation = getConversationFragment();
        final ContactPickerFragment contactPicker = getContactPicker();
        if (contactPicker != null && mUiState.shouldShowContactPickerFragment()) {
            contactPicker.updateActionBar(actionBar);
             //added  by sprd for Bug 815733 end
            if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()){
                if (isMediaPickerOpen()) {
                    conversation.getMediaPicker().updateActionBar(actionBar);
                    if (contactPicker.getToolbar() != null) contactPicker.getToolbar().setVisibility(View.GONE);/*modified for Bug 819631 start*/
                } else {
                    if (contactPicker.getToolbar() != null) contactPicker.getToolbar().setVisibility(View.VISIBLE);/*modified for Bug 819631 end*/
                }
            }
             //added  by sprd for Bug 815733 end
        } else if (conversation != null && mUiState.shouldShowConversationFragment()) {
            conversation.updateActionBar(actionBar);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        if (super.onOptionsItemSelected(menuItem)) {
            return true;
        }
        if (menuItem.getItemId() == android.R.id.home) {
            onNavigationUpPressed();
            return true;
        }
        return false;
    }

    public void onNavigationUpPressed() {
        // Let the conversation fragment handle the navigation up press.
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null && conversationFragment.onNavigationUpPressed()) {
            return;
        }
        //add for bug 675873 --begin
        if (conversationFragment != null && !conversationFragment.hasMessages()
                && conversationFragment.isDraftLoadDone()  //Bug 1177603
                && !conversationFragment.isValidConversation()){/*modified for Bug 682299 */
            Log.d(TAG,"onNavigationUpPressed deleteConversation");
            final String conversationId = mUiState.getConversationId();
            DeleteConversationAction.deleteConversation(conversationId, System.currentTimeMillis());
        }
        //add for bug 675873 --end
        onFinishCurrentConversation();
    }

    @Override
    public void onBackPressed() {
        // If action mode is active dismiss it
        if (getActionMode() != null) {
            Log.d(TAG, "enter onBackPressed(): dismissActionMode");
            dismissActionMode();
            return;
        }
        // Let the conversation fragment handle the back press.
        final ConversationFragment conversationFragment = getConversationFragment();
        final ContactPickerFragment contactPickerFragment = getContactPicker();
        Log.d(TAG, "enter onBackPressed(): conversationFragment= "+conversationFragment);
        /*if (conversationFragment != null && conversationFragment.onBackPressed()) {
            return;
        }
        super.onBackPressed();*/
        // add for bug 523612 begin
        if ((conversationFragment != null) && (!conversationFragment.isVisible() || conversationFragment.onBackPressed())) {
            Log.d(TAG, "enter onBackPressed():conversationFragment.isVisible()"+ conversationFragment.isVisible());
            return;
        }

         /*add for bug 653194 start*/
         final String conversationId = mUiState.getConversationId();
         Log.d(TAG, "enter onBackPressed():  conversationId:" + conversationId);
         if (conversationId != null) {
             boolean isEmptyRecipientConversaton = mEmptyRecipientInConversationFrag;//Fit the feature of contact-edit by bug854037
             //Bug 905660 begin
             if (conversationFragment != null) {
                 isValidConversation = conversationFragment.isValidConversation();
				 isEmptyRecipientConversaton = conversationFragment.isEmptyRecipientConversaton();
             }

             //Bug 905660 end
             Log.d(TAG, "onBackPressed(): isEmptyRecipientConversaton = " + isEmptyRecipientConversaton + "; isValidConversation ="+ isValidConversation);
             if (isEmptyRecipientConversaton && isValidConversation) {
                showDiscardDraftConfirmDialog(this, new DiscardDraftListener());
                return;
             } else if (contactPickerFragment != null && !isValidConversation) { //Bug 905660 begin
                 DeleteConversationAction.deleteConversation(conversationId, System.currentTimeMillis());
             } //Bug 905660 end
         }
         /*add for bug 653194 end*/

        if (mInstanceStateSaved) {
            Log.d(TAG, "ConversationActivity onBackPressed ");
            return;
        }
        super.onBackPressed();
        // add for bug 523612 end
    }

    /*Add by SPRD for bug 646250 Start*/
    private class DiscardDraftListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int whichButton) {
            DeleteConversationAction.deleteConversation(mUiState.getConversationId(), System.currentTimeMillis());
            ConversationActivity.this.finish();
        }
    }
    private void closeDraftConfirmDialog(){
        if (mDraftConfirmDialog != null) {
            mDraftConfirmDialog.dismiss();
            mDraftConfirmDialog = null;
        }
    }

    private AlertDialog mDraftConfirmDialog;
    private void showDiscardDraftConfirmDialog(Context context,
            OnClickListener listener) {
        mDraftConfirmDialog = new AlertDialog.Builder(context)
                .setMessage(R.string.discard_message_reason)
                .setPositiveButton(R.string.confirm_convert, listener)
                .setNegativeButton(R.string.cancel, null)
                .show();

    }

    private ContactPickerFragment getContactPicker() {
        return (ContactPickerFragment) getFragmentManager().findFragmentByTag(
                ContactPickerFragment.FRAGMENT_TAG);
    }

    private ConversationFragment getConversationFragment() {
        return (ConversationFragment) getFragmentManager().findFragmentByTag(
                ConversationFragment.FRAGMENT_TAG);
    }

    @Override // From ContactPickerFragmentHost
    public void onGetOrCreateNewConversation(final String conversationId) {
        Assert.isTrue(conversationId != null);
        mUiState.onGetOrCreateConversation(conversationId);
    }

    @Override // From ContactPickerFragmentHost
    public void onBackButtonPressed() {
        onBackPressed();
    }

    @Override // From ContactPickerFragmentHost
    public void onInitiateAddMoreParticipants() {
        mUiState.onAddMoreParticipants();
    }

    @Override //ContactPickerFragmentHost
    public void peopleAndOptionsAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.peopleAndOptionsAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override //ContactPickerFragmentHost
    public void addCommonPhraseAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.addCommonPhraseAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override // From ContactPickerFragmentHost
    public void archiveConversationAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.archiveConversationAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override // From ContactPickerFragmentHost
    public void unArchiveConversationAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.unArchiveConversationAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override // From ContactPickerFragmentHost
    public void deleteConversationAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.deleteConversationAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override // From ContactPickerFragmentHost
    public void forwardAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.forwardAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override // From ContactPickerFragmentHost
    public void slideShowAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.slideShowAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override // From ContactPickerFragmentHost
    public boolean checkConvIsArchived() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            return conversationFragment.checkConvIsArchived();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
            return false;
        }
    }

    @Override // From ContactPickerFragmentHost
    public void callAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.callAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    @Override // From ContactPickerFragmentHost
    public void addContactAction() {
        final ConversationFragment conversationFragment = getConversationFragment();
        if (conversationFragment != null) {
            conversationFragment.addContactAction();
        } else {
            Log.d(TAG, "ConversationFragment is missing when option menu item clicked action from ContactPickerFragment");
        }
    }

    /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
    @Override // From ContactPickerFragmentHost
    public String getConversationId() {
        return mUiState.getConversationId();
    }

    @Override // From ConversationFragmentHost
    public ConversationActivityUiState getConversationUiState() {
        return mUiState;
    }
    /*Add by SPRD for edit contact in draft 2016.09.06 End*/

    @Override
    public void onParticipantCountChanged(final boolean canAddMoreParticipants) {
        mUiState.onParticipantCountUpdated(canAddMoreParticipants);
    }

    @Override // From ConversationFragmentHost
    public void onStartComposeMessage() {
        mUiState.onStartMessageCompose();
    }

    @Override // From ConversationFragmentHost
    public void onConversationMetadataUpdated() {
        invalidateActionBar();
        if(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
            updateContactPickerFragmentMenu();
        }
    }

    @Override // From ConversationFragmentHost
    public void onConversationMessagesUpdated(final int numberOfMessages) {
        mUiState.ConversationMessagesUpdated(numberOfMessages);/*add for Bug 806979 */
    }

    @Override // From ConversationFragmentHost
    public void onConversationParticipantDataLoaded(final int numberOfParticipants) {
    }

    @Override // From ConversationFragmentHost
    public boolean isActiveAndFocused() {
        return !mIsPaused && hasWindowFocus();
    }

    @Override // From ConversationActivityUiStateListener
    public void onConversationContactPickerUiStateChanged(final int oldState, final int newState,
                                                          final boolean animate) {
        Assert.isTrue(oldState != newState);
         /*add for Bug 705416 start:after sending, need to remove the draft data*/
        Log.d(TAG, "onConversationContactPickerUiStateChanged newState:" + newState + "; oldState=" + oldState);
        if (newState == ConversationActivityUiState.STATE_CONVERSATION_ONLY
                    && oldState==ConversationActivityUiState.STATE_HYBRID_WITH_CONVERSATION_AND_CHIPS_VIEW) {
            final Intent intent = getIntent();
            intent.removeExtra(UIIntents.UI_INTENT_EXTRA_DRAFT_DATA);
            Log.d(TAG, "onConversationContactPickerUiStateChanged removeExtra:UI_INTENT_EXTRA_DRAFT_DATA" );
        } /*add for Bug 705416 end*/
        updateUiState(animate);
    }

    /*Add by SPRD for bug58104  2016.07.08 Start*/
    @Override // From ConversationFragmentHost
    public void setKeepingMessageData(MessageData data) {
        mUiState.setMessageData(data);
    }

    @Override // From ConversationFragmentHost
    public String getOldConversationId() {
        return oldConversationId;
    }

    @Override   // from ConversationFragmentHost
    public void onSendMessageAction() {
        mUiState.onSendMessageAction();
    }
    /*Add by SPRD for bug581044  2016.07.08 End*/

    private void updateUiState(final boolean animate) {
        /*Modify by SPRD for bug585425  2016.08.08 Start*/
        if (mInstanceStateSaved || mIsPaused || this.isDestroyed()) {
            return;
        }
        /*Modify by SPRD for bug585425  2016.08.08 End*/

        Assert.notNull(mUiState);
        final Intent intent = getIntent();
        final String conversationId = mUiState.getConversationId();

        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        final boolean needConversationFragment = mUiState.shouldShowConversationFragment();
        final boolean needContactPickerFragment = mUiState.shouldShowContactPickerFragment();
        ContactPickerFragment contactPickerFragment = getContactPicker();
        ConversationFragment conversationFragment = getConversationFragment();
        // Set up the conversation fragment.
        if (needConversationFragment) {
            Assert.notNull(conversationId);
            if (conversationFragment == null) {
                conversationFragment = new ConversationFragment();
                fragmentTransaction.add(R.id.conversation_fragment_container,
                        conversationFragment, ConversationFragment.FRAGMENT_TAG);
            }
            final MessageData draftData = intent.getParcelableExtra(
                    UIIntents.UI_INTENT_EXTRA_DRAFT_DATA);
            Log.d(TAG, "updateUiState draftdata:" + draftData);
            if (!needContactPickerFragment) {
                // Once the user has committed the audience,remove the draft data from the
                // intent to prevent reuse
                intent.removeExtra(UIIntents.UI_INTENT_EXTRA_DRAFT_DATA);
                Log.d(TAG, "updateUiState setIntent removeExtra UI_INTENT_EXTRA_DRAFT_DATA");
                setIntent(intent);/*add for Bug 705416*/
            }
            conversationFragment.setHost(this);
            /*Modify by SPRD for bug581044  2016.07.08 Start*/
            if (!MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
                conversationFragment.setConversationInfo(this, conversationId, draftData);
            } else {
                oldConversationId = mUiState.getOldConversationId();
                //Bug931261 begin
                final MessageData messageData = mUiState.getmMessageData();
                //Bug931261 end
                Log.d(TAG, "updateUiState draftdata:" + draftData + " messageData:" + messageData);
                if (messageData != null && messageData.hasContent())
                    conversationFragment.setConversationInfo(this, conversationId, messageData);
                else
                    conversationFragment.setConversationInfo(this, conversationId, draftData);
            }
            /*Modify by SPRD for bug581044  2016.07.08 End*/
        } else if (conversationFragment != null) {
            // Don't save draft to DB when removing conversation fragment and switching to
            // contact picking mode.  The draft is intended for the new group.
            isValidConversation = conversationFragment.isValidConversation();  /*add for bug 653194 */
            // add for 1357062
            mEmptyRecipientInConversationFrag = conversationFragment.isEmptyRecipientConversaton();
            //Bug 895166 begin
            //if this conversation has valid recipients, save draft to DB when removing
            // conversation and switching to contact picking mode, otherwise don's save
            if (contactPickerFragment == null || !contactPickerFragment.hasValidRecipients()) {
                conversationFragment.suppressWriteDraft();
            }
            //Bug 895166 end
            fragmentTransaction.remove(conversationFragment);
        }

        // Set up the contact picker fragment.
        if (needContactPickerFragment) {
            if (contactPickerFragment == null) {
                contactPickerFragment = new ContactPickerFragment();
                fragmentTransaction.add(R.id.contact_picker_fragment_container,
                        contactPickerFragment, ContactPickerFragment.FRAGMENT_TAG);
            }
            contactPickerFragment.setHost(this);
            contactPickerFragment.setContactPickingMode(mUiState.getDesiredContactPickingMode(),
                    animate);
        } else if (contactPickerFragment != null) {
            fragmentTransaction.remove(contactPickerFragment);
        }

        //Changed by SPRD for Bug 615604 at 20161119 begin
        fragmentTransaction.commitAllowingStateLoss();
        //Changed by SPRD for Bug 615604 at 20161119 end
        invalidateActionBar();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG,"dispatchKeyEvent : keyCode "+ event.getKeyCode());
        if(event.getKeyCode()==KeyEvent.KEYCODE_MENU) {
            final ContactPickerFragment contactPicker = getContactPicker();
            if (contactPicker != null) {
                Toolbar toolbar = contactPicker.getToolbar();
                if (toolbar.getVisibility() == View.VISIBLE) {
                    toolbar.showOverflowMenu();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onFinishCurrentConversation() {
        // Simply finish the current activity. The current design is to leave any empty
        // conversations as is.
        if (OsUtil.isAtLeastL()) {
            finishAfterTransition();
        } else {
            finish();
        }
    }

    @Override
    public boolean shouldResumeComposeMessage() {
        return mUiState.shouldResumeComposeMessage();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
                                    final Intent data) {
        if (requestCode == ConversationFragment.REQUEST_CHOOSE_ATTACHMENTS &&
                resultCode == RESULT_OK) {
            final ConversationFragment conversationFragment = getConversationFragment();
            if (conversationFragment != null) {
                conversationFragment.onAttachmentChoosen();
            } else {
                Log.d(TAG, "ConversationFragment is missing after launching " +
                        "AttachmentChooserActivity!");
            }
        } else if (resultCode == FINISH_RESULT_CODE) {
            finish();
        }else if (requestCode == StorageUtil.REQUEST_EXTERNAL_STORAGE_PERMISSION
                && resultCode == Activity.RESULT_OK) {//Add by sprd
            final ConversationFragment conversationFragment = getConversationFragment();
            if (data != null && conversationFragment != null) {
                final Uri uri = data.getData();
                if(StorageUtil.isDownloadDirInSD(uri)){
                    final int takeFalgs = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFalgs);
                    conversationFragment.saveAttachmentToStorage(uri);
                } else {
                    mStorageUtil = new StorageUtil();
                    mStorageUtil.showPermissionFailDialog(this);
                }
            }
        }
    }

    public void showWaitingDialog() {
        try {
            mWaitingDialogCount.incrementAndGet();
            if (mWaitingDialog == null) {
                mWaitingDialog = new ProgressDialog(this);
                mWaitingDialog.setOnDismissListener(
                        new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                mWaitingDialogCount.set(0);
                            }
                        });
                mWaitingDialog.setCanceledOnTouchOutside(false);
                mWaitingDialog.setCancelable(false);
                mWaitingDialog.setIndeterminate(true);
                mWaitingDialog.setMessage(getString(R.string.smil_wait_comparing_att_size));
            }
            mWaitingDialog.show();
        } catch (Exception ex) {
            Log.d(TAG, " showWaitingDialog ", ex.fillInStackTrace());
        } finally {
            Log.d(TAG, "showWaitingDialog mWaitingDialogCount = " + mWaitingDialogCount.get());
        }
    }

    public void closeWaitingDialog() {
        try {
            Log.d(TAG, "closeWaitingDialog mWaitingDialogCount = " + mWaitingDialogCount.get());
            if (ConversationActivity.this.isFinishing() || ConversationActivity.this.isDestroyed()) {
                mWaitingDialogCount.set(0);
                return;
            }
            if (mWaitingDialogCount.decrementAndGet() > 0) {
                return;
            }
            if (mWaitingDialog != null) {
                if (mWaitingDialog.isShowing()) {
                    mWaitingDialog.cancel();
                }
                mWaitingDialog = null;
                if (mWaitingDialogHandler.hasMessages(GlobleUtil.MSG_CLOSE_LOADING_DIALOG)) {
                    mWaitingDialogHandler.removeMessages(GlobleUtil.MSG_CLOSE_LOADING_DIALOG);
                }
            }
        } catch (Exception ex) {
            Log.d(TAG, " closeWaitingDialog ", ex.fillInStackTrace());
        }
    }
    //bug 1178144 begin
    private void showFdnDialog(String number){
        Log.d(TAG,"showFdnDialog");
        try {
            final AlertDialog builder = new AlertDialog.Builder(this).setMessage(this.getString(com.android.internal
                    .R.string.fdn_send_fail_add_number, number))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            dialog.dismiss();
                        }
                    }).create();
            builder.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            builder.show();
        }catch (Exception ex) {
            Log.d(TAG, " showFdnDialog ", ex.fillInStackTrace());
        }
    }
    //bug 1178144 end
    private final static int CLOSE_LOADING_DIALOG_TIMEOUT = 30 * 1000;  //30s
    private final static int LOADING_DIALOG_GAP = 100;  //100ms
    private Handler mWaitingDialogHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case GlobleUtil.MSG_OPEN_LOADING_DIALOG:
                        if (!isResumed) {
                            mWaitingDialogHandler.sendMessageDelayed(mWaitingDialogHandler.obtainMessage(GlobleUtil.MSG_OPEN_LOADING_DIALOG),
                                    LOADING_DIALOG_GAP);
                            break;
                        }
                        showWaitingDialog();
                        mWaitingDialogHandler.sendMessageDelayed(mWaitingDialogHandler.obtainMessage(GlobleUtil.MSG_CLOSE_LOADING_DIALOG),
                                CLOSE_LOADING_DIALOG_TIMEOUT);
                        break;
                    case GlobleUtil.MSG_CLOSE_LOADING_DIALOG:
                        //Bug 1217425 begin
                        if (mWaitingDialogHandler.hasMessages(GlobleUtil.MSG_OPEN_LOADING_DIALOG)) {
                            mWaitingDialogHandler.removeMessages(GlobleUtil.MSG_OPEN_LOADING_DIALOG);
                        }
                        //Bug 1217425 end
                        closeWaitingDialog();
                        break;
                    case GlobleUtil.MMS_ERROR_FDN_CHECKED_LOADING_DIALOG:    //bug 1178144
                        Bundle bundle = msg.getData();
                        String number = bundle.getString("numberCode");
                        showFdnDialog(number);
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                Log.d(TAG, " handle loading dialog message ", ex.fillInStackTrace());
            }
        }
    };

    private boolean mIsRequestingPermission = false;
    private final String mReadEmailAttachPermission = "com.android.email.permission.READ_ATTACHMENT";
    @Override   // from ConversatinFragment.ConversationFragmentHost
    public void needReadEmailAttachmentPermission() {
        this.finish();      // deny permission, so finish this activity
    }

    @Override   // from ConversatinFragment.ConversationFragmentHost
    public void updateContactPickerFragmentMenu() {
        final ContactPickerFragment contactPickerFragment = getContactPicker();
        if (contactPickerFragment != null) {
            if (mUiState.getDesiredContactPickingMode() == ContactPickerFragment.MODE_CHIPS_ONLY && !isMediaPickerOpen()) {//add for 815765
                contactPickerFragment.showOrHideAllOptionsMenu(true);
            } else {
                contactPickerFragment.showOrHideAllOptionsMenu(false);
            }
        }
    }
    /*SPRD add for 815765. From ConversationInputManager updateHostOptionsMenu(),
      the original design is: If the mediapicker is opened, the menu  is not displayed*/
    private boolean isMediaPickerOpen(){
        final ConversationFragment conversationFragment = getConversationFragment();
        if(conversationFragment!=null){
            return  (conversationFragment.getMediaPicker()!=null && conversationFragment.getMediaPicker().isOpen());
        }
        return false;
    }/*add for 815765 end*/

    /*Modify by SPRD for bug843334  Start*/
    @Override
    public ContactRecipientAutoCompleteView getContactView(){
        ContactRecipientAutoCompleteView conactview =null;
        ContactPickerFragment contactPickerFragment = getContactPicker();
        if(contactPickerFragment != null){
            conactview = contactPickerFragment.getAutoCompleteView();
        }
        return conactview;
    }
    /*Modify by SPRD for bug843334  end*/

	 /**************************White Box testing start**********************************/

    @Override
    public String getWhiteTest(){
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        String testType = null;
        if(bundle != null){
            testType = bundle.getString(WhiteTestType.WHITE_BOX_TEST_KEY);
        }
        return testType;
    }


    @Override
    public String getestSim(){
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        String testSimNum = null;
        if(bundle != null){
            String testType = bundle.getString(WhiteTestType.WHITE_BOX_TEST_KEY);
            String testTask = bundle.getString(WhiteTestType.TEST_TASK_KEY);
            if(WhiteTestType.WHITE_BOX_TEST_VALUE.equals(testType)){
                testSimNum = bundle.getString(WhiteTestType.TEST_SIM_NUMBER_KEY);
            }
        }

        return testSimNum;
    }



    @Override
    public void sendConverActivityBroadcase(){
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        //bug 842523 start
        if(bundle != null){
            String testType = bundle.getString(WhiteTestType.TEST_TASK_KEY);
            WhiteTestType.getInstance().sendTestBroadcase(testType);
        }
        //bug 842523 end
        finish();
    }
    /**************************White Box testing end**********************************/
    //bug 990370 begin
    public void updateArchiveMenu(DraftMessageData draftMessageData) {
        final ContactPickerFragment contactPickerFragment = getContactPicker();
        if (contactPickerFragment != null && !contactPickerFragment.isRecipientEmpty()) {
            String messageText = draftMessageData.getMessageText();
            if(messageText != null){
                messageText = messageText.trim();
            }
            boolean hasContent = draftMessageData.hasAttachments() || !TextUtils.isEmpty(messageText)
                    || !TextUtils.isEmpty(draftMessageData.getMessageSubject());
            boolean contactsPicked = mUiState.getDesiredContactPickingMode() == ContactPickerFragment.MODE_CHIPS_ONLY;
            contactPickerFragment.setArchiveVisible(hasContent && contactsPicked);
        }
    }
    //bug 990370 end


    public VcardPicker.VcardPickerListener getVcardPickerListener(){
        return mVcardPickerListener;
    }

    private VcardPicker.VcardPickerListener mVcardPickerListener = new VcardPicker.VcardPickerListener() {
        @Override
        public void onVcardSelected(PendingAttachmentData pendingItem) {
            final ConversationFragment conversationFragment = getConversationFragment();
            if (conversationFragment != null){
                conversationFragment.onPendingAttachmentAdded(pendingItem);
            }

        }

        @Override
        public void onTextSelected(HashMap<String, String> contacts) {
            if (contacts == null || contacts.entrySet() == null) {
                return;
            }
            StringBuilder text = new StringBuilder("");
            Iterator it = contacts.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                if (entry.getValue() != null) {
                    text.append((String) entry.getValue());
                    text.append("\n");
                }
                if (entry.getKey() != null) {
                    text.append((String) entry.getKey());
                    text.append("\n");
                }
            }
            final ConversationFragment conversationFragment = getConversationFragment();
            if (conversationFragment != null){
                conversationFragment.onTextContactsAdded(text.toString());
            }
        }
    };

}
