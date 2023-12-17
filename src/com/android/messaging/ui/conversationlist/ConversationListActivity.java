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

package com.android.messaging.ui.conversationlist;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SearchView.SearchAutoComplete;

import androidx.appcompat.app.ActionBar;
import androidx.core.view.MenuItemCompat;

import com.android.messaging.R;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.ui.SearchActivity;
import com.android.messaging.ui.SnackBarManager;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.conversation.ConversationActivity;
import com.android.messaging.util.BugleActivityUtil;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.Trace;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.WhiteTestType;
import com.sprd.messaging.util.SystemAdapter;

public class ConversationListActivity extends AbstractConversationListActivity {
    public static final String TAG = "ConversationListActivity";

    private static final String IS_FROM_FOLDER_VIEW = "from_folder_view";
    private int mSubId;//sprd 497178
    // bug 495194 : add for search feature begin
    private MenuItem mSearchItem;
    private SearchView mSearchView;
    // add for bug 551962 begin
    private MenuItem simMenuItem;
    private SearchAutoComplete mSearchEditText;
    // add for bug 551962 end
    public static final int SEARCH_MAX_LENGTH = 512;
    // bug 495194 : add for search feature end
    private boolean isForeground = true;//for bug713363

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // bug 495194 : add for search feature begin
        launchThreadFromSearchResult(getIntent());
        // bug 495194 : add for search feature end
        Trace.beginSection("ConversationListActivity.onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_list_activity);
        Trace.endSection();
        invalidateActionBar();
        /*bug 1187508, begin*/
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager.hasUserRestriction(UserManager.DISALLOW_SMS)) {
            BugleActivityUtil.resetAlertDialog();
        }
        /*bug 1187508, end*/
    }

    private void launchThreadFromSearchResult(Intent intent) {
        if (intent != null && intent.hasExtra("come_from_searchActivity")) {
            // add for bug 543691 begin
            String convId = String.valueOf(intent.getIntExtra("convId", -1));
            UIIntents.get().launchConversationActivity(this, convId, null,
                    null, false);
            // add for bug 543691 end
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        launchThreadFromSearchResult(intent);
    }

    // add for bug 543695 begin
    // add for bug 551962 begin
    private SearchAutoComplete getSearchEditText() {
        return mSearchEditText;
    }
    // add for bug 551962 end
    private void clearSearchText() {
        System.out.println("enter clearSearchText()");
        if (getSearchEditText() != null) {
            System.out.println("getSearchEditText()!=null,the SearchEditText = "
                            + getSearchEditText().getText());
            if (!TextUtils.isEmpty(getSearchEditText().getText())) {
                getSearchEditText().setText("");
            }
        }
    }
    // add for bug 543695 end
    private void openKeyboard() {
        InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
    }
    public void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) this
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    // bug 495194 : add for search feature end

    @Override
    protected void updateActionBar(final ActionBar actionBar) {
        actionBar.setTitle(getString(R.string.app_name));
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setBackgroundDrawable(new ColorDrawable(
                getResources().getColor(R.color.action_bar_background_color)));
        actionBar.show();
        super.updateActionBar(actionBar);
    }

    @Override
    public void onResume() {
        super.onResume();
        isForeground = true;//for bug713363
        // Invalidate the menu as items that are based on settings may have changed
        // while not in the app (e.g. Talkback enabled/disable affects new conversation
        // button)
        supportInvalidateOptionsMenu();
        // add for bug 543695 begin
        //modify for bug 618789
        //clearSearchText();
        //0 means visiable
        if (getSearchEditText() != null && getSearchEditText().getVisibility() == View.VISIBLE){
            LogUtil.d(TAG, "after the resume, the search view should focused.");
            getSearchEditText().setFocusable(true);
            getSearchEditText().setFocusableInTouchMode(true);
            getSearchEditText().requestFocus();
        }
        //modify for bug 618789
        // add for bug 543695 end

        if(!OsUtil.hasRequiredPermissions()){
            OsUtil.requestMissingPermission(this);
        }
   }

    @Override
    public void onBackPressed() {
        if (isInConversationListSelectMode()) {
            exitMultiSelectState();
        } else {
            super.onBackPressed();
        }
    }
    // bug 495194 : add for search feature begin
    SearchView.OnQueryTextListener mQueryTextListener = new SearchView.OnQueryTextListener() {
        @Override
        public boolean onQueryTextSubmit(String query) {
            Intent intent = new Intent();
            intent.setClass(ConversationListActivity.this, SearchActivity.class);
            intent.putExtra(SearchManager.QUERY, query);
            startActivity(intent);
            mSearchItem.collapseActionView();
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            /* SPRD: Add for bug#191263. @{ */
            if (newText != null && newText.length() > SEARCH_MAX_LENGTH) {
                mSearchView.setQuery(
                        newText.substring(0, SEARCH_MAX_LENGTH - 1), false);
                UiUtils.showToastAtBottom(R.string.search_max_length);
            }
            /* @} */
            // add for bug 551962 begin
            setCloseBtnGone();
            // add for bug 551962 end
            return true;
        }
    };

    @Override
    public boolean onSearchRequested() {
        if (mSearchItem != null) {
            mSearchItem.expandActionView();
            //add for bug 584494 begin
            mSearchView.setFocusable(true);
            mSearchView.setFocusableInTouchMode(true);
            mSearchView.requestFocus();
            //add for bug 584494 end
            clearSearchText(); // Add by SPRD for bug 614074
        }
        return true;
    }
    // bug 495194 : add for search feature end

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (super.onCreateOptionsMenu(menu)) {
            return true;
        }
        getMenuInflater().inflate(R.menu.conversation_list_fragment_menu, menu);
        final MenuItem item = menu.findItem(R.id.action_debug_options);
        if (item != null) {
            final boolean enableDebugItems = DebugUtils.isDebugEnabled();
            item.setVisible(enableDebugItems).setEnabled(enableDebugItems);
        }
       //sprd bug497178 begin
        MenuItem menuItem = menu.findItem(R.id.action_wireless_alerts);
        if (menuItem != null && isCellBroadcastAppLinkEnabled()) {
            menuItem.setVisible(true);
        }else{
            menuItem.setVisible(false);
        }
       //sprd bug497178 end
        // bug 551962  : add for search feature begin
        initSearchView(menu);
        // bug 551962 : add for search feature end
        simMenuItem = menu.findItem(R.id.action_sim_sms);
        /**************************White Box testing start**********************************/
        final Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if(bundle != null){
            String testType = bundle.getString(WhiteTestType.WHITE_BOX_TEST_KEY);
            String testTask = bundle.getString(WhiteTestType.TEST_TASK_KEY);
            if(WhiteTestType.WHITE_BOX_TEST_VALUE.equals(testType) && WhiteTestType.TEST_SEARCH.equals(testTask)){
               // mTestHandler.sendEmptyMessageDelayed(SEARCH_MMSSMS_TEST,500);
                Message msg = mTestHandler.obtainMessage();
                msg.obj = bundle;//bundle.getString(WhiteTestType.TEST_SEARCH_VALUE);
                msg.what = SEARCH_MMSSMS_TEST;
                mTestHandler.sendMessageDelayed(msg,500);

            }
        }
        /**************************White Box testing end**********************************/
        return true;
    }
    // bug 551962 : add for search feature begin
    public void initSearchView(Menu menu){
        // bug 495194 : add for search feature begin
        mSearchItem = menu.findItem(R.id.menu_search_item);
        //add for switch of Search feature
        mSearchView = (SearchView) MenuItemCompat.getActionView(mSearchItem);
        mSearchView.setOnQueryTextListener(mQueryTextListener);
        mSearchView.setQueryHint(getString(R.string.search_hint));
        // mSearchView.setSearchTextSize(16);
        /*Delete  by SPRD for Bug:550566 Start */
        mSearchView.setIconifiedByDefault(false);
        mSearchView.setIconified(false);
        /*Delete  by SPRD for Bug:550566 End */
        mSearchView.clearFocus();
        /* SPRD:change the background of searchView. @{ */
        mSearchView.setBackgroundResource(R.drawable.search_bg);
        /* @} */
        /* [Add] by SPRD for Bug:[437363] 2015.09.10 Start */
        int searchSrcTextId = getResources().getIdentifier(
                "android:id/search_src_text", null, null);
        // add for bug 543695 begin
        mSearchEditText = (SearchAutoComplete) (mSearchView.findViewById(searchSrcTextId));
        mSearchEditText.setPadding(0, 10, 0, 10);
        mSearchEditText.setTextColor(Color.BLACK);
        mSearchEditText.setHintTextColor(Color.GRAY);
        mSearchEditText.setTextSize(18);
        mSearchEditText.setGravity(Gravity.CENTER_VERTICAL);

        mSearchEditText.setDropDownBackgroundResource(R.drawable.search_bg);
        setCloseBtnGone();
        // add for bug 543695 end
        mSearchView.setSubmitButtonEnabled(false);
        /* [Add] by SPRD for Bug:[437363] 2015.09.10 End */
        //add for bug 555163 begin
        MenuItemCompat.setOnActionExpandListener(mSearchItem,new SearchViewExpandListener());
        //add for bug 555163 end
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            SearchableInfo info = searchManager.getSearchableInfo(this
                    .getComponentName());
            mSearchView.setSearchableInfo(info);
        }
        // bug 495194 : add for search feature end
    }

    private void setCloseBtnGone(){
        int closeBtnId = getResources().getIdentifier(
                "android:id/search_close_btn", null, null);
        ImageView mCloseButton = null;
        if (mSearchView != null) {
            mCloseButton = (ImageView) mSearchView
                    .findViewById(closeBtnId);
        }
        if (mCloseButton != null) {
            mCloseButton.setImageDrawable(getResources().getDrawable(
                    R.drawable.ic_cancel_small_dark));
        }
    }
    // bug 551962 : add for search feature end

    // bug 478514: Add for MmsFolderView Feature -- Begin
    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        if (mActionMode != null) {
            return true;
        }
        if (simMenuItem != null) {
            final boolean simVisible = (PhoneUtils.getDefault().hasSim()
                    && SystemAdapter.getInstance().getActiveSubInfoList() != null
                    && !SystemAdapter.getInstance().getActiveSubInfoList().isEmpty());
            simMenuItem.setVisible(simVisible);
        }
        return true;
    }
    // bug 478514: Add for MmsFolderView Feature -- End

    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        switch(menuItem.getItemId()) {
        // bug 495194 : add for search feature begin
        case R.id.menu_search_item:
            mSearchView.setFocusable(true);
            mSearchView.setFocusableInTouchMode(true);
            mSearchView.requestFocus();
            // add for bug 543695 begin
            clearSearchText();
            // add for bug 543695 end
            return true;
            // bug 495194 : add for search feature end
            case R.id.action_start_new_conversation:
                onActionBarStartNewConversation();
                return true;
            case R.id.action_settings:
                onActionBarSettings();
                return true;
            case R.id.action_debug_options:
                onActionBarDebug();
                return true;
            case R.id.action_show_archived:
                onActionBarArchived();
                return true;
            //sprd bug497178 begin
            case R.id.action_wireless_alerts:
                 try {
                       startActivity(UIIntents.get().getWirelessAlertsIntent());
                  } catch (final ActivityNotFoundException e) {
                      // Handle so we shouldn't crash if the wireless alerts
                      // implementation is broken.
                      LogUtil.e(LogUtil.BUGLE_TAG,
                                   "Failed to launch wireless alerts activity", e);
                   }
                return true;
            //sprd bug497178 end
            case R.id.action_show_blocked_contacts:
                onActionBarBlockedParticipants();
                return true;

            // bug 478514: Add for SimMessage Feature -- Begin
            case R.id.action_sim_sms:
                try {
                    Intent intent = new Intent("sprd.intent.action.SIM_MESSAGE");
                    intent.putExtra("is_sim_sms", true);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            // bug 478514: Add for SimMessage Feature -- End
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onActionBarHome() {
        exitMultiSelectState();
    }

    public void onActionBarStartNewConversation() {
        UIIntents.get().launchCreateNewConversationActivity(this, null);
    }

    public void onActionBarSettings() {
        UIIntents.get().launchSettingsActivity(this);
    }

    public void onActionBarBlockedParticipants() {
        UIIntents.get().launchBlockedParticipantsActivity(this);
    }

    public void onActionBarArchived() {
        UIIntents.get().launchArchivedConversationsActivity(this);
    }

    //sprd bug497178 begin
    private boolean isCellBroadcastAppLinkEnabled() {
        if (!MmsConfig.get(mSubId).getShowCellBroadcast()) {
            return false;
        }
        try {
            final PackageManager pm = ConversationListActivity.this.getPackageManager();
            return pm.getApplicationEnabledSetting(UIIntents.CMAS_COMPONENT)
                    != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (final IllegalArgumentException ignored) {
            // CMAS app not installed.
        }
        return false;
    }
    //sprd bug497178 end

    @Override
    public boolean isSwipeAnimatable() {
        return !isInConversationListSelectMode();
    }

    @Override
    public void onWindowFocusChanged(final boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        final ConversationListFragment conversationListFragment =
                (ConversationListFragment) getFragmentManager().findFragmentById(
                        R.id.conversation_list_fragment);
        // When the screen is turned on, the last used activity gets resumed, but it gets
        // window focus only after the lock screen is unlocked.
        if (isActivityForeground()&& hasFocus && conversationListFragment != null) {//for bug713363
            conversationListFragment.setScrolledToNewestConversationIfNeeded();
        }

        if(hasFocus && !isSelectionMode() && getSearchEditText() != null && getSearchEditText().getVisibility() == View.VISIBLE){
            LogUtil.d(TAG, "onWindowFocusChanged,getSearchEditText view should focused. Visbility:"+getSearchEditText().getVisibility());
            getSearchEditText().setFocusable(true);
            getSearchEditText().setFocusableInTouchMode(true);
            getSearchEditText().requestFocus();
        }
    }

    //for bug713363 begin
    private boolean isActivityForeground() {
        return isForeground;
    }
    //for bug713363 end

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;//for bug713363
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(SnackBarManager.sInstance!=null){
            if(SnackBarManager.sInstance.getPopupWindow()!=null){
                SnackBarManager.sInstance.getPopupWindow().dismiss();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        /* SPRD: Bug 572242 add for hide the SoftInput. @{ */
        InputMethodManager imm = (InputMethodManager) getSystemService(ConversationActivity.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
    //add for bug 555163 begin
    class SearchViewExpandListener implements MenuItemCompat.OnActionExpandListener{
        @Override
        public boolean onMenuItemActionCollapse(MenuItem item) {
            // Do something when collapsed
            Log.d(TAG,"enter onMenuItemActionCollapse()");
            hideSoftInput(mSearchView);
            return true;  // Return true to collapse action view
        }

        @Override
        public boolean onMenuItemActionExpand(MenuItem item) {
            // Do something when expanded
            Log.d(TAG,"enter onMenuItemActionExpand()");
            //modify for bug 614063
            if (!isSelectionMode()) {
                openKeyboard();
            }
            //modify for bug 614063
            return true;  // Return true to expand action view
        }
    }
    //add for bug 555163 end

    /**************************White Box testing start**********************************/
    private final static int SEARCH_MMSSMS_TEST = 100;

    private Handler mTestHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle bundle = (Bundle)msg.obj;
            //bug 842523 start
            if(bundle !=null){
                String query = bundle.getString(WhiteTestType.TEST_SEARCH_VALUE);
                switch (msg.what) {
                    case SEARCH_MMSSMS_TEST:
                        Log.d(WhiteTestType.TAG,"---SEARCH_MMSSMS_TEST----query: "+query);
                        Intent intent = new Intent();
                        intent.setClass(ConversationListActivity.this, SearchActivity.class);
                        intent.putExtra(SearchManager.QUERY, query);
                        intent.putExtras(bundle);
                        startActivity(intent);
                        finish();
                        break;
                }
            }
            //bug 842523 end
        }
    };
	 /**************************White Box testing end**********************************/
}
