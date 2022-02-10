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

package com.android.messaging.ui.contact;

import android.app.Activity;
import android.app.Fragment;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.Explode;
import android.transition.Transition;
import android.transition.Transition.EpicenterCallback;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.mms.MmsManager;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.Toolbar.OnMenuItemClickListener;

import com.android.ex.chips.RecipientEntry;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.action.ActionMonitor;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction.GetOrCreateConversationActionListener;
import com.android.messaging.datamodel.action.GetOrCreateConversationAction.GetOrCreateConversationActionMonitor;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.data.ContactListItemData;
import com.android.messaging.datamodel.data.ContactPickerData;
import com.android.messaging.datamodel.data.ContactPickerData.ContactPickerDataListener;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.sms.MmsSmsUtils;
import com.android.messaging.ui.CustomHeaderPagerViewHolder;
import com.android.messaging.ui.CustomHeaderViewPager;
import com.android.messaging.ui.animation.ViewGroupItemVerticalExplodeAnimation;
import com.android.messaging.ui.contact.ContactRecipientAutoCompleteView.ContactChipsChangeListener;
import com.android.messaging.ui.conversation.ConversationActivity;
import com.android.messaging.util.Assert;
import com.android.messaging.util.Assert.RunsOnMainThread;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.ImeUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.UiUtils;
import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Shows lists of contacts to start conversations with.
 */
public class ContactPickerFragment extends Fragment implements ContactPickerDataListener,
        ContactListItemView.HostInterface, ContactChipsChangeListener, OnMenuItemClickListener,
        GetOrCreateConversationActionListener {
    public static final String FRAGMENT_TAG = "contactpicker";

    // Undefined contact picker mode. We should never be in this state after the host activity has
    // been created.
    public static final int MODE_UNDEFINED = 0;

    // The initial contact picker mode for starting a new conversation with one contact.
    public static final int MODE_PICK_INITIAL_CONTACT = 1;

    // The contact picker mode where one initial contact has been picked and we are showing
    // only the chips edit box.
    public static final int MODE_CHIPS_ONLY = 2;

    // The contact picker mode for picking more contacts after starting the initial 1-1.
    public static final int MODE_PICK_MORE_CONTACTS = 3;

    // The contact picker mode when max number of participants is reached.
    public static final int MODE_PICK_MAX_PARTICIPANTS = 4;

    public interface ContactPickerFragmentHost {
        void onGetOrCreateNewConversation(String conversationId);
        void onBackButtonPressed();
        void onInitiateAddMoreParticipants();
        void onParticipantCountChanged(boolean canAddMoreParticipants);
        void invalidateActionBar();
        void peopleAndOptionsAction();
        void addCommonPhraseAction();
        void archiveConversationAction();
        void unArchiveConversationAction();
        void deleteConversationAction();
        void forwardAction();
        void slideShowAction();
        boolean checkConvIsArchived();
        void callAction();
        void addContactAction();
        /*Add by SPRD for edit contact in draft 2016.09.06 Start*/
        String getConversationId();
        /*Add by SPRD for edit contact in draft 2016.09.06 End*/
        boolean contactPickerNeedLoadDataFromDb();  //Bug 945427
    }

    @VisibleForTesting
    final Binding<ContactPickerData> mBinding = BindingBase.createBinding(this);

    private ContactPickerFragmentHost mHost;
    private ContactRecipientAutoCompleteView mRecipientTextView;
    private CustomHeaderViewPager mCustomHeaderViewPager;
    private AllContactsListViewHolder mAllContactsListViewHolder;
    private FrequentContactsListViewHolder mFrequentContactsListViewHolder;
    private View mRootView;
    private View mPendingExplodeView;
    private View mComposeDivider;
    private Toolbar mToolbar;
    private int mContactPickingMode = MODE_UNDEFINED;
    /*Add by SPRD for bug581044  2016.07.08 Start*/
    private ArrayList<ParticipantData> mParticipants = null;
    private boolean isNewAction = false;
    private PhoneUtils nPhoneUtils;
    private boolean isProfileSupported = false;
    /*Add by SPRD for bug581044  2016.07.08 End*/

    // Keeps track of the currently selected phone numbers in the chips view to enable fast lookup.
    private Set<String> mSelectedPhoneNumbers = null;
    private Set<String> mSelOrginPhoneNumbers = null;//by sprd

    //by sprd, begin
    protected View getRootView() {
        return mRootView;
    }

    public ContactRecipientAutoCompleteView getAutoCompleteView() {
        return mRecipientTextView;
    }

    protected int getContactPickMode() {
        return mContactPickingMode;
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }
    //by sprd, end

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAllContactsListViewHolder = new AllContactsListViewHolder(getActivity(), this);
        mFrequentContactsListViewHolder = new FrequentContactsListViewHolder(getActivity(), this);

        if (ContactUtil.hasReadContactsPermission()) {
            mBinding.bind(DataModel.get().createContactPickerData(getActivity(), this));
            mBinding.getData().init(getLoaderManager(), mBinding);
        }
    }

    /**
     * {@inheritDoc} from Fragment
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.contact_picker_fragment, container, false);
        mRecipientTextView = (ContactRecipientAutoCompleteView)
                view.findViewById(R.id.recipient_text_view);
        mRecipientTextView.setThreshold(0);
        mRecipientTextView.setDropDownAnchor(R.id.compose_contact_divider);
        mRecipientTextView.setContactChipsListener(this);
        mRecipientTextView.setDropdownChipLayouter(new ContactDropdownLayouter(inflater,
                getActivity(), this));
        mRecipientTextView.setAdapter(new ContactRecipientAdapter(getActivity(), this));
        mRecipientTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before,
                    final int count) {
                /*Add by SPRD for bug581044  2016.07.31 Start*/
                if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
                    if (isNewAction && mContactPickingMode == MODE_CHIPS_ONLY && !TextUtils.isEmpty(s)) {
                        isNewAction = false;
                        if (mHost != null){
                            mHost.onInitiateAddMoreParticipants();
                        }
                    }
                }
                /*Add by SPRD for bug581044  2016.07.31 End*/
            }

            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count,
                    final int after) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                updateTextInputButtonsVisibility();
            }
        });

        final CustomHeaderPagerViewHolder[] viewHolders = {
                mFrequentContactsListViewHolder,
                mAllContactsListViewHolder };

        mCustomHeaderViewPager = (CustomHeaderViewPager) view.findViewById(R.id.contact_pager);
        mCustomHeaderViewPager.setViewHolders(viewHolders);
        mCustomHeaderViewPager.setViewPagerTabHeight(CustomHeaderViewPager.DEFAULT_TAB_STRIP_SIZE);
        mCustomHeaderViewPager.setBackgroundColor(getResources()
                .getColor(R.color.contact_picker_background));

        // The view pager defaults to the frequent contacts page.
        mCustomHeaderViewPager.setCurrentItem(0);

        mToolbar = (Toolbar) view.findViewById(R.id.toolbar);
        mToolbar.setNavigationIcon(R.drawable.ic_arrow_back_light);
        mToolbar.setNavigationContentDescription(R.string.back);
        mToolbar.setNavigationOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                if(mHost != null) {
                    mHost.onBackButtonPressed();
                }
            }
        });

        mToolbar.inflateMenu(R.menu.compose_menu);
        mToolbar.setOnMenuItemClickListener(this);

        mComposeDivider = view.findViewById(R.id.compose_contact_divider);
        mRootView = view;
        return view;
    }

    /**
     * {@inheritDoc}
     *
     * Called when the host activity has been created. At this point, the host activity should
     * have set the contact picking mode for us so that we may update our visuals.
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Assert.isTrue(mContactPickingMode != MODE_UNDEFINED);
        updateVisualsForContactPickingMode(false /* animate */);
        if(mHost != null) {
            mHost.invalidateActionBar();
        }
        /*Add by SPRD for bug581044  2016.07.08 Start*/
        if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
            if (mHost != null && mHost.getConversationId() != null) {
                if (mHost.contactPickerNeedLoadDataFromDb()) {
                    new LoadRecipientAsyncTask().executeOnExecutor(Executors.newSingleThreadExecutor());
                }
            isNewAction = false;
            } else {//create new message
                isNewAction = false;
                createNewMessageConversation();
            }
        }
        /*Add by SPRD for bug581044  2016.07.08 End*/
    }

    /*Add by SPRD for bug581044  2016.07.08 Start*/
    public void  createNewMessageConversation(){
        if (mContactPickingMode == MODE_PICK_INITIAL_CONTACT) {
            mParticipants = new ArrayList<ParticipantData>();
            RecipientEntry recipientEntry = RecipientEntry.constructFakePhoneEntry("", true);
            ParticipantData participantData = ParticipantData.getFromRecipientEntry(recipientEntry);
            mParticipants.add(participantData);
            if (mParticipants.size() > 0 && mMonitor == null) {
               mMonitor = GetOrCreateConversationAction.getOrCreateConversation(mParticipants,
                    null, this);
               isNewAction = (mSelectedPhoneNumbers == null || mSelectedPhoneNumbers.size() == 0); //Bug 916372
            }
        }
    }
    /*Add by SPRD for bug581044  2016.07.08 End*/

    @Override
    public void onDestroy() {
        // We could not have bound to the data if the permission was denied.
        if (mBinding.isBound()) {
            mBinding.unbind();
        }

        if (mMonitor != null) {
            mMonitor.unregister();
        }
        mMonitor = null;
        super.onDestroy();/*modified for Bug 796017*/
    }

    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_ime_dialpad_toggle:
                final int baseInputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE;
                if ((mRecipientTextView.getInputType() & InputType.TYPE_CLASS_PHONE) !=
                        InputType.TYPE_CLASS_PHONE) {
                    mRecipientTextView.setInputType(baseInputType | InputType.TYPE_CLASS_PHONE);
                    menuItem.setIcon(R.drawable.ic_ime_light);
                } else {
                    mRecipientTextView.setInputType(baseInputType | InputType.TYPE_CLASS_TEXT);
                    menuItem.setIcon(R.drawable.ic_numeric_dialpad);
                }
                ImeUtil.get().showImeKeyboard(getActivity(), mRecipientTextView);
                return true;

            case R.id.action_add_more_participants:
                if(mHost != null) {
                    mHost.onInitiateAddMoreParticipants();
                }
                return true;

            case R.id.action_confirm_participants:
                //maybeGetOrCreateConversation();
                if (TextUtils.isEmpty(mRecipientTextView.getText())) {
                    UiUtils.showToast(R.string.no_few_participants);
                    return true;
                }
                mRecipientTextView.onEditorAction(mRecipientTextView, EditorInfo.IME_ACTION_DONE, null);
                return true;

            case R.id.action_delete_text:
                Assert.equals(MODE_PICK_INITIAL_CONTACT, mContactPickingMode);
                mRecipientTextView.setText("");
                return true;

            case R.id.action_people_and_options:
                // add for bug 797069 start
                try{
                    mHost.peopleAndOptionsAction();
                }catch (Exception ex){
                    ex.printStackTrace();
                }
                // add for bug 797069 end
                return true;
            case R.id.action_add_phrase:
                mHost.addCommonPhraseAction();
                return true;
            case R.id.action_archive:
                mHost.archiveConversationAction();
                return true;
            case R.id.action_unarchive:
                mHost.unArchiveConversationAction();
                return true;
            case R.id.action_delete:
                mHost.deleteConversationAction();
                return true;
            case R.id.action_sms_merge_forward:
                mHost.forwardAction();
                return true;
            case R.id.action_goto_smil:
                mHost.slideShowAction();
                return true;
            case R.id.action_call:
                mHost.callAction();
                return true;
            case R.id.action_add_contact:
                mHost.addContactAction();
                return true;
        }
        return false;
    }

    /*Add by SPRD for Bug:571963 Start*/
    private void closeImeKeyboard() {
        Assert.notNull(mRecipientTextView);
        mRecipientTextView.requestFocus();

        // showImeKeyboard() won't work until the layout is ready, so wait until layout is complete
        // before showing the soft keyboard.
        UiUtils.doOnceAfterLayoutChange(mRootView, new Runnable() {
            @Override
            public void run() {
                final Activity activity = getActivity();
                if (activity != null) {
                    ImeUtil.get().hideImeKeyboard(getActivity(), mRecipientTextView);
                }
            }
        });
        mRecipientTextView.invalidate();
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (resetConfiguration(newConfig)) {
            closeImeKeyboard();
        }
    }

    // bug 865871 begin
    private void translateSelOrginPhoneNumbers() {
        if (mSelectedPhoneNumbers != null) {
            mSelectedPhoneNumbers.clear();
        }
        if (mSelOrginPhoneNumbers != null && mSelOrginPhoneNumbers.size() > 0) {    //bug 869108
            if (mSelectedPhoneNumbers == null) {
                mSelectedPhoneNumbers = new HashSet<String>();
            }
            Iterator it = mSelOrginPhoneNumbers.iterator();
            while (it.hasNext()) {
                String mstring = it.next().toString();
                if (!mstring.equals("")) {
                    mSelectedPhoneNumbers.add(PhoneUtils.getDefault().getCanonicalBySystemLocale(mstring).replace(" ", ""));
                }
            }
        }
    }
    // bug 865871 end

    public boolean mIsLandscape = false;
    // returns true if landscape/portrait configuration has changed
    private boolean resetConfiguration(Configuration config) {
        boolean isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (mIsLandscape != isLandscape) {
            mIsLandscape = isLandscape;
            return true;
        }
        return false;
    }
    /*Add by SPRD for Bug:571963 End*/

    @Override // From ContactPickerDataListener
    public void onAllContactsCursorUpdated(final Cursor data) {
        mBinding.ensureBound();
        mAllContactsListViewHolder.onContactsCursorUpdated(data);
        if (!isVisible()) {
            return;
        }
        //Bug 916372 begin
        if (mRecipientTextView != null && 1 == mRecipientTextView.getRecipientCount()) {
            LogUtil.d(FRAGMENT_TAG, " mRecipientTextView.updateContactChips ");
            mRecipientTextView.updateContactChips();
        }
        //Bug 916372 end
    }

    @Override // From ContactPickerDataListener
    public void onFrequentContactsCursorUpdated(final Cursor data) {
        mBinding.ensureBound();
        mFrequentContactsListViewHolder.onContactsCursorUpdated(data);
        if (data != null && data.getCount() == 0) {
            // Show the all contacts list when there's no frequents.
            mCustomHeaderViewPager.setCurrentItem(1);
        }
    }

    /* Add by SPRD for Bug:523024 2016.01.26 Start */
    private boolean mIsNewConversationCreating;
    /* Add by SPRD for Bug:523024 2016.01.26 End */

    @Override // From ContactListItemView.HostInterface
    public void onContactListItemClicked(final ContactListItemData item,
            final ContactListItemView view) {
        /* Add by SPRD for Bug:523024 2016.01.26 Start */
        if(mIsNewConversationCreating) {
            return;
        }
        /* Add by SPRD for Bug:523024 2016.01.26 End */
        if (!isContactSelected(item)) {
            if (mContactPickingMode == MODE_PICK_INITIAL_CONTACT) {
                /* Add by SPRD for Bug:523024 2016.01.26 Start */
                mIsNewConversationCreating = true;
                /* Add by SPRD for Bug:523024 2016.01.26 End */
                mPendingExplodeView = view;
            }
            String number = item.getDestination().toString().replace(" ", "");
            RecipientEntry entry = item.getRecipientEntry();
            if (entry != null && entry.isValid() && entry.getDestination() != null
                    && NumberMatchUtils.getNumberMatchUtils().getNumberMatchNotify().OnNotify(0, 0, 0, entry.getDestination(), null) == NumberMatchUtils.getNumberMatchUtils().SUCC) {
                /*Add by SPRD for bug581554  2016.08.17 Start*/
                try {
                    mRecipientTextView.appendRecipientEntry(item.getRecipientEntry());
                } catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
                /*Add by SPRD for bug581554  2016.08.17 End*/
            } else {
                UiUtils.showToast(R.string.contact_invalid);
                if (mContactPickingMode == MODE_PICK_INITIAL_CONTACT) {
                    mIsNewConversationCreating = false;
                }
            }
           // mRecipientTextView.appendRecipientEntry(item.getRecipientEntry());
        } else if (mContactPickingMode != MODE_PICK_INITIAL_CONTACT) {
            //Bug 965084 begin
            try {
                mRecipientTextView.removeRecipientEntry(item.getRecipientEntry());
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
            //Bug 965084 end
        }
    }

    @Override // From ContactListItemView.HostInterface
    public boolean isContactSelected(final ContactListItemData item) {
        return mSelectedPhoneNumbers != null &&
                mSelectedPhoneNumbers.contains(PhoneUtils.getDefault().getCanonicalBySystemLocale(
                        item.getDestination().toString().replace(" ","")));
    }

    /**
     * Call this immediately after attaching the fragment, or when there's a ui state change that
     * changes our host (i.e. restore from saved instance state).
     */
    public void setHost(final ContactPickerFragmentHost host) {
        mHost = host;
    }

    public void setContactPickingMode(final int mode, final boolean animate) {
        if (mContactPickingMode != mode) {
            // Guard against impossible transitions.
            Assert.isTrue(
                    // We may start from undefined mode to any mode when we are restoring state.
                    (mContactPickingMode == MODE_UNDEFINED) ||
                    (mContactPickingMode == MODE_PICK_INITIAL_CONTACT && mode == MODE_CHIPS_ONLY) ||
                    (mContactPickingMode == MODE_CHIPS_ONLY && mode == MODE_PICK_MORE_CONTACTS) ||
                    (mContactPickingMode == MODE_PICK_MORE_CONTACTS && mode == MODE_CHIPS_ONLY) ||
                    (mContactPickingMode == MODE_PICK_MORE_CONTACTS && mode == MODE_PICK_MAX_PARTICIPANTS) ||
                    (mContactPickingMode == MODE_PICK_MAX_PARTICIPANTS && mode == MODE_PICK_MORE_CONTACTS));
            /* Add by SPRD for Bug:523024 2016.01.26 Start */
            if(mode == MODE_PICK_MORE_CONTACTS) {
                mIsNewConversationCreating = false;
            }
            /* Add by SPRD for Bug:523024 2016.01.26 End */
            mContactPickingMode = mode;
            updateVisualsForContactPickingMode(animate);
        }
    }

    private void showImeKeyboard() {
        Assert.notNull(mRecipientTextView);
        mRecipientTextView.requestFocus();

        // showImeKeyboard() won't work until the layout is ready, so wait until layout is complete
        // before showing the soft keyboard.
        UiUtils.doOnceAfterLayoutChange(mRootView, new Runnable() {
            @Override
            public void run() {
                final Activity activity = getActivity();
                if (activity != null) {
                    ImeUtil.get().showImeKeyboard(activity, mRecipientTextView);
                }
            }
        });
        mRecipientTextView.invalidate();
    }

    private void updateVisualsForContactPickingMode(final boolean animate) {
        // Don't update visuals if the visuals haven't been inflated yet.
        if (mRootView != null) {
            final Menu menu = mToolbar.getMenu();
            final MenuItem addMoreParticipantsItem = menu.findItem(
                    R.id.action_add_more_participants);
            final MenuItem confirmParticipantsItem = menu.findItem(
                    R.id.action_confirm_participants);
            switch (mContactPickingMode) {
                case MODE_PICK_INITIAL_CONTACT:
                    addMoreParticipantsItem.setVisible(false);
                    confirmParticipantsItem.setVisible(false);
                    /*Modify by SPRD for bug581044  2016.07.08 Start*/
                    if(!MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
                        mCustomHeaderViewPager.setVisibility(View.VISIBLE);
                    } else {
                        mCustomHeaderViewPager.setVisibility(View.GONE);
                        showOrHideAllOptionsMenu(true);
                    }
                    /*Modify by SPRD for bug581044  2016.07.08 End*/
                    mComposeDivider.setVisibility(View.INVISIBLE);
                    mRecipientTextView.setEnabled(true);
                     /*Modify by SPRD for bug843334  Start*/
                    if(!MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()){
                        showImeKeyboard();
                    }
                     /*Modify by SPRD for bug843334   end*/
                    break;

                case MODE_CHIPS_ONLY:
                    if (animate) {
                        if (mPendingExplodeView == null) {
                            // The user didn't click on any contact item, so use the toolbar as
                            // the view to "explode."
                            mPendingExplodeView = mToolbar;
                        }
                        startExplodeTransitionForContactLists(false /* show */);

                        ViewGroupItemVerticalExplodeAnimation.startAnimationForView(
                                mCustomHeaderViewPager, mPendingExplodeView, mRootView,
                                true /* snapshotView */, 0/*UiUtils.COMPOSE_TRANSITION_DURATION*/);
                        showHideContactPagerWithAnimation(false /* show */);
                    } else {
                        mCustomHeaderViewPager.setVisibility(View.GONE);
                    }
                    // sprd: fixed for bug 548270 start
                    if(GlobleUtil.isSmilAttament){
                        addMoreParticipantsItem.setVisible(false);
                    }else{
                        addMoreParticipantsItem.setVisible(true);
                    }
                    // sprd: fixed for bug 548270 end
                    confirmParticipantsItem.setVisible(false);
                    mComposeDivider.setVisibility(View.VISIBLE);
                    mRecipientTextView.setEnabled(true);
                    showOrHideAllOptionsMenu(true);//bug 990483
                    break;

                case MODE_PICK_MORE_CONTACTS:
                    if (animate) {
                        // Correctly set the start visibility state for the view pager and
                        // individual list items (hidden initially), so that the transition
                        // manager can properly track the visibility change for the explode.
                        mCustomHeaderViewPager.setVisibility(View.VISIBLE);
                        toggleContactListItemsVisibilityForPendingTransition(false /* show */);
                        startExplodeTransitionForContactLists(true /* show */);
                    }
                    addMoreParticipantsItem.setVisible(false);
                    confirmParticipantsItem.setVisible(true);
                    mCustomHeaderViewPager.setVisibility(View.VISIBLE);
                    mComposeDivider.setVisibility(View.INVISIBLE);
                    mRecipientTextView.setEnabled(true);
                    showOrHideAllOptionsMenu(false);
                    showImeKeyboard();
                    break;

                case MODE_PICK_MAX_PARTICIPANTS:
                    addMoreParticipantsItem.setVisible(false);
                    confirmParticipantsItem.setVisible(true);
                    mCustomHeaderViewPager.setVisibility(View.VISIBLE);
                    mComposeDivider.setVisibility(View.INVISIBLE);
                    // TODO: Verify that this is okay for accessibility
                    // mRecipientTextView.setEnabled(false);
                    showOrHideAllOptionsMenu(false);
                    break;

                default:
                    Assert.fail("Unsupported contact picker mode!");
                    break;
            }
            updateTextInputButtonsVisibility();
        }
    }

    private void updateTextInputButtonsVisibility() {
        final Menu menu = mToolbar.getMenu();
        final MenuItem keypadToggleItem = menu.findItem(R.id.action_ime_dialpad_toggle);
        final MenuItem deleteTextItem = menu.findItem(R.id.action_delete_text);
        if (mContactPickingMode == MODE_PICK_INITIAL_CONTACT) {
            if (TextUtils.isEmpty(mRecipientTextView.getText())) {
                deleteTextItem.setVisible(false);
                /*Modify by SPRD for bug581044  2016.07.08 Start*/
                //keypadToggleItem.setVisible(true);
                keypadToggleItem.setVisible(!MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled());
                /*Modify by SPRD for bug581044  2016.07.08 End*/
                mIsNewConversationCreating = false;//bug 555230
            } else {
                deleteTextItem.setVisible(true);
                keypadToggleItem.setVisible(false);
            }
        } else {
            deleteTextItem.setVisible(false);
            keypadToggleItem.setVisible(false);
        }
    }

    private void maybeGetOrCreateConversation() {
        mParticipants = mRecipientTextView.getRecipientParticipantDataForConversationCreation();
        if (ContactPickerData.isTooManyParticipants(mParticipants.size())) {
            final int currentCount = mParticipants.size();
            final int recipientLimit = MmsConfig.get(MmsManager.DEFAULT_SUB_ID).getRecipientLimit();
            if (currentCount > recipientLimit) {
                mRecipientTextView.requestFocus();
                UiUtils.showToastAtBottom(getString(R.string.exceed_participant_limit,
                        recipientLimit, currentCount - recipientLimit));
            }
        } else if (mParticipants.size() > 0 && mMonitor == null) {
            mMonitor = GetOrCreateConversationAction.getOrCreateConversation(mParticipants,
                    null, this);
        }
    }

    /**
     * Watches changes in contact chips to determine possible state transitions (e.g. creating
     * the initial conversation, adding more participants or finish the current conversation)
     */
    @Override
    public void onContactChipsChanged(final int oldCount, final int newCount) {
        Assert.isTrue(oldCount != newCount);
        int recipientLimit = MmsConfig.get(MmsManager.DEFAULT_SUB_ID).getRecipientLimit();
        if (newCount > recipientLimit) {
            UiUtils.showToastAtBottom(getString(R.string.exceed_participant_limit,
                    recipientLimit, newCount - recipientLimit));
        }
       LogUtil.d(FRAGMENT_TAG,"onContactChipsChanged:mContactPickingMode: "+mContactPickingMode +" newCount ="+ newCount);
       if (mContactPickingMode == MODE_PICK_INITIAL_CONTACT) {
            // Initial picking mode. Start a conversation once a recipient has been picked.
            maybeGetOrCreateConversation();
        } else if (mContactPickingMode == MODE_CHIPS_ONLY) {
            // oldCount == 0 means we are restoring from savedInstanceState to add the existing
            // chips, don't switch to "add more participants" mode in this case.
            final boolean contentEditEnabled = MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled();/*modified for bug  795393 start*/
            if (oldCount > 0 && (mRecipientTextView.isFocused() || contentEditEnabled)) {/*modified for bug  795393 end*/
                // Chips only mode. The user may have picked an additional contact or deleted the
                // only existing contact. Either way, switch to picking more participants mode.
                if(mHost != null) {
                    mHost.onInitiateAddMoreParticipants();
                }
            }
            //for bug790916 begin, should check recipient when getContentEditEnabled = true
            if (contentEditEnabled){ /*modified for bug  795393 start*/
                maybeGetOrCreateConversation();
                if (mRecipientTextView.isFocused()) {
                    mRecipientTextView.clearFocus();
                }
                ConversationActivity.sContactConfirmAct = true;
            }
            //for bug790916 end
        }

        if(mHost != null) {
            mHost.onParticipantCountChanged(ContactPickerData.getCanAddMoreParticipants(newCount));
        }

        // Refresh our local copy of the selected chips set to keep it up-to-date.
        // bug 865871 begin
        // mSelectedPhoneNumbers =  mRecipientTextView.getSelectedDestinations();
        mSelOrginPhoneNumbers =  mRecipientTextView.getSelectedDestinations();
        translateSelOrginPhoneNumbers();
        // bug 865871 end
        invalidateContactLists();
    }

    /**
     * Listens for notification that invalid contacts have been removed during resolving them.
     * These contacts were not local contacts, valid email, or valid phone numbers
     */
    @Override
    public void onInvalidContactChipsPruned(final int prunedCount) {
        Assert.isTrue(prunedCount > 0);
        /* SPRD: modify for Bug 521135 begin */
        if (ConversationActivity.sContactConfirmAct) {
            mRecipientTextView.requestFocus();
            UiUtils.showToast(R.plurals.add_invalid_contact_error, prunedCount);
            ConversationActivity.sContactConfirmAct = false;
        }
        /* SPRD: modify for Bug 521135 end */
    }

    //Bug 916372 begin
    @Override
    public void onContactChipsReplaced() {
        mParticipants = mRecipientTextView.getParticipantDataAfterConversationCreated();
    }
    //Bug 916372 end

    /**
     * Listens for notification that the user has pressed enter/done on the keyboard with all
     * contacts in place and we should create or go to the existing conversation now
     */
    @Override
    public void onEntryComplete() {
        if (mContactPickingMode == MODE_PICK_INITIAL_CONTACT ||
                mContactPickingMode == MODE_PICK_MORE_CONTACTS ||
                mContactPickingMode == MODE_PICK_MAX_PARTICIPANTS) {
            // Avoid multiple calls to create in race cases (hit done right after selecting contact)
            maybeGetOrCreateConversation();
        }
    }

    private void invalidateContactLists() {
        mAllContactsListViewHolder.invalidateList();
        mFrequentContactsListViewHolder.invalidateList();
    }

    /**
     * Kicks off a scene transition that animates visibility changes of individual contact list
     * items via explode animation.
     * @param show whether the contact lists are to be shown or hidden.
     */
    private void startExplodeTransitionForContactLists(final boolean show) {
        if (!OsUtil.isAtLeastL()) {
            // Explode animation is not supported pre-L.
            return;
        }
        final Explode transition = new Explode();
        final Rect epicenter = mPendingExplodeView == null ? null :
            UiUtils.getMeasuredBoundsOnScreen(mPendingExplodeView);
        transition.setDuration(UiUtils.COMPOSE_TRANSITION_DURATION);
        transition.setInterpolator(UiUtils.EASE_IN_INTERPOLATOR);
        transition.setEpicenterCallback(new EpicenterCallback() {
            @Override
            public Rect onGetEpicenter(final Transition transition) {
                return epicenter;
            }
        });

        // Kick off the delayed scene explode transition. Anything happens after this line in this
        // method before the next frame will be tracked by the transition manager for visibility
        // changes and animated accordingly.
        TransitionManager.beginDelayedTransition(mCustomHeaderViewPager,
                transition);

        toggleContactListItemsVisibilityForPendingTransition(show);
    }

    /**
     * Toggle the visibility of contact list items in the contact lists for them to be tracked by
     * the transition manager for pending explode transition.
     */
    private void toggleContactListItemsVisibilityForPendingTransition(final boolean show) {
        if (!OsUtil.isAtLeastL()) {
            // Explode animation is not supported pre-L.
            return;
        }
        mAllContactsListViewHolder.toggleVisibilityForPendingTransition(show, mPendingExplodeView);
        mFrequentContactsListViewHolder.toggleVisibilityForPendingTransition(show,
                mPendingExplodeView);
    }

    public void showHideContactPagerWithAnimation(final boolean show) {//by sprd, private->public
        final boolean isPagerVisible = (mCustomHeaderViewPager.getVisibility() == View.VISIBLE);
        if (show == isPagerVisible) {
            return;
        }

        mCustomHeaderViewPager.animate().alpha(show ? 1F : 0F)
            .setStartDelay(!show ? UiUtils.COMPOSE_TRANSITION_DURATION : 0)
            .withStartAction(new Runnable() {
                @Override
                public void run() {
                    mCustomHeaderViewPager.setVisibility(View.VISIBLE);
                    mCustomHeaderViewPager.setAlpha(show ? 0F : 1F);
                }
            })
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    mCustomHeaderViewPager.setVisibility(show ? View.VISIBLE : View.GONE);
                    mCustomHeaderViewPager.setAlpha(1F);
                }
            });
    }

    @Override
    public void onContactCustomColorLoaded(final ContactPickerData data) {
        mBinding.ensureBound(data);
        invalidateContactLists();
    }

    public void updateActionBar(final ActionBar actionBar) {
        // Hide the action bar for contact picker mode. The custom ToolBar containing chips UI
        // etc. will take the spot of the action bar.
        actionBar.hide();
        UiUtils.setStatusBarColor(getActivity(),
                getResources().getColor(R.color.compose_notification_bar_background));
    }

    private GetOrCreateConversationActionMonitor mMonitor;

    @Override
    @RunsOnMainThread
    public void onGetOrCreateConversationSucceeded(final ActionMonitor monitor,
            final Object data, final String conversationId) {
        Assert.isTrue(monitor == mMonitor);
        Assert.isTrue(conversationId != null);
        LogUtil.d(FRAGMENT_TAG, "onGetOrCreateConversationSucceeded=========conversationId: "+conversationId);
        mRecipientTextView.setInputType(InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_CLASS_TEXT);
        if (mHost != null) {
            mHost.onGetOrCreateNewConversation(conversationId);
        }
        mParticipants = mRecipientTextView.getParticipantDataAfterConversationCreated(); //Bug #895259
        mMonitor = null;
    }

    @Override
    @RunsOnMainThread
    public void onGetOrCreateConversationFailed(final ActionMonitor monitor,
            final Object data) {
        Assert.isTrue(monitor == mMonitor);
        LogUtil.e(LogUtil.BUGLE_TAG, "onGetOrCreateConversationFailed");
        mMonitor = null;
        /* Add by SPRD for Bug:523024 2016.01.26 Start */
        mIsNewConversationCreating = false;
        /* Add by SPRD for Bug:523024 2016.01.26 End */
    }

    @Override
    public void onResume() {
        super.onResume();
        nPhoneUtils = PhoneUtils.getDefault();
        isProfileSupported = ContactUtil.isWorkProfileSupported();
    }

    @Override
    public PhoneUtils getPhoneUtils() {
        if (nPhoneUtils == null) {
            nPhoneUtils = PhoneUtils.getDefault();
        }
        return nPhoneUtils;
    }

    @Override
    public boolean getIsProfileSupported(){
        return isProfileSupported;
    }

    public void showOrHideAllOptionsMenu(final boolean visible) {
        if(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getContentEditEnabled()) {
            final boolean isEmptyConv = (mSelectedPhoneNumbers == null || mSelectedPhoneNumbers.size() == 0);
            final boolean isArchived = (mHost == null)? false : mHost.checkConvIsArchived();//for bug802238 NullPointer
            LogUtil.d(FRAGMENT_TAG, "showOrHideAllOptionsMenu >> visible = " + visible + ", isEmptyConv " + isEmptyConv + ", isArchived = " + isArchived);
            isNewAction = isEmptyConv;   //889464 //Bug 916372
            mToolbar.getMenu().findItem(R.id.action_people_and_options).setVisible(visible && !isEmptyConv);
            mToolbar.getMenu().findItem(R.id.action_add_phrase).setVisible(visible);
            mToolbar.getMenu().findItem(R.id.action_delete).setVisible(visible);
            mToolbar.getMenu().findItem(R.id.action_archive).setVisible(visible && !isEmptyConv && !isArchived && archiveVisible);
            mToolbar.getMenu().findItem(R.id.action_unarchive).setVisible(visible && !isEmptyConv && isArchived);
            mToolbar.getMenu().findItem(R.id.action_sms_merge_forward).setVisible(visible && !isEmptyConv);
            mToolbar.getMenu().findItem(R.id.action_add_contact).setVisible(visible && addContactActionVisible());
            mToolbar.getMenu().findItem(R.id.action_call).setVisible(visible && supportCallAction());
        }
    }

    private boolean supportCallAction() {
        if (mParticipants != null && mParticipants.size() == 1) {
            final String phoneNumber = mParticipants.get(0).getSendDestination();
            return (PhoneUtils.getDefault().isVoiceCapable() &&
                    !TextUtils.isEmpty(phoneNumber) && MmsSmsUtils.isPhoneNumber(phoneNumber));
        } else {
            return false;
        }
    }

    private boolean addContactActionVisible() {
        if (mParticipants != null && mParticipants.size() == 1) {
            final String sendDest = mParticipants.get(0).getSendDestination();
            return (!TextUtils.isEmpty(sendDest) && (MmsSmsUtils.isPhoneNumber(sendDest) || MmsSmsUtils.isEmailAddress(sendDest))
                    && TextUtils.isEmpty(mParticipants.get(0).getLookupKey()));
        }
        return false;
    }

    //Bug 895166 begin
    public boolean hasValidRecipients() {
        final int recipientLimit = MmsConfig.get(MmsManager.DEFAULT_SUB_ID).getRecipientLimit();
        final int currentCount = (mParticipants == null ? 0 : mParticipants.size());
        return currentCount > 0 && currentCount <= recipientLimit;
    }
    //Bug 895166 end

    //bug 990370, begin
    private boolean archiveVisible = true;

    public boolean isRecipientEmpty(){
        return TextUtils.isEmpty(mRecipientTextView.getText());
    }

    public void setArchiveVisible(boolean flag){
        if(archiveVisible == flag){
            return;
        }
        archiveVisible = flag;
        final boolean isEmptyConv = (mSelectedPhoneNumbers == null || mSelectedPhoneNumbers.size() == 0);
        final boolean isArchived = (mHost != null) && mHost.checkConvIsArchived();
        mToolbar.getMenu().findItem(R.id.action_archive).setVisible(flag && !isEmptyConv && !isArchived);
    }
    //bug 990370, end

    /*bug 1198998, begin*/
    private final class LoadRecipientAsyncTask extends AsyncTask<Void, Void, List<RecipientEntry>> {//bug 1198998

        @Override
        protected final List<RecipientEntry> doInBackground(final Void... params) {
            List<RecipientEntry> recipients = BugleDatabaseOperations
                    .getRecipientEntryByConversationId(mHost.getConversationId());
            return recipients;
        }

        @Override
        protected void onPostExecute(List<RecipientEntry> recipients) {
            if (recipients != null) {
                mParticipants = new ArrayList<ParticipantData>();
                if (mSelOrginPhoneNumbers == null) {
                    mSelOrginPhoneNumbers = new HashSet<String>();
                }
                HashSet<String> recipNumSet = mRecipientTextView.getRecipNumSet();/*add for bug 787311 start*/
                for (RecipientEntry entry : recipients) {
                    String destination = entry.getDestination();
                    if (!recipNumSet.contains(destination)){
                        mRecipientTextView.appendRecipientEntry(entry);
                        mSelOrginPhoneNumbers.add(destination);
                        ParticipantData participantData = ParticipantData.getFromRecipientEntry(entry);
                        mParticipants.add(participantData);
                        recipNumSet.add(destination);
                    }
                }
                translateSelOrginPhoneNumbers();// bug 865871
            }
        }
    }
    /*bug 1198998, end*/
}
