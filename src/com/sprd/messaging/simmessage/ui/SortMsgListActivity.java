//by sprd
package com.sprd.messaging.simmessage.ui;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.ActionBar;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.messaging.R;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.sprd.messaging.simmessage.SimUtils;
import com.sprd.messaging.simmessage.data.SortMsgListItemData;
import com.sprd.messaging.simmessage.presenter.SortDisplayController;

import java.util.ArrayList;
import java.util.List;

public class SortMsgListActivity extends BaseActionBarActivity implements MultiSelectActionModeCallback.Listener,
        SortMsgListFragment.SortMsgListFragmentHost {
    public static final String TAG = "SortMsgListActivity";
    public static final String PREFERENCES_NAME = "displaycontrol";
    private SortMsgListFragment mSortMsgListFragment;
    private ArrayList<Integer> mMessagesId = new ArrayList<Integer>();
    private ActionBar mActionBar;
    private int mSimSmsId;
    private int mSubIdToDelSimSms;
    private boolean isSIMMultiDelete = false;
    private int mDeletCount;
    public static final Uri ICC_URI = Uri.parse("content://sms/icc");
    private SimSmsDelObserver mSimDelObserver;
    private AlertDialog mSimSelectDialog;
    private SubscriptionManager subscriptionManager;
    private SortDisplayController sortDisplayController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sort_msg_list_activity);
        sortDisplayController = SortDisplayController.getInstance(this);
        subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        invalidateActionBar();

        registerReceiver(mSimInOutReceiver, mSimFilter);
        mSimDelObserver = new SimSmsDelObserver();
        getContentResolver().registerContentObserver(ICC_URI, true, mSimDelObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OsUtil.hasRequiredPermissions()) {
            OsUtil.requestMissingPermission(this);
        }
        invalidateActionBar();
        if (mActionMode == null) {
            supportInvalidateOptionsMenu();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isInMultiWindowMode()) {
            closeSimSelectDialog();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mSimInOutReceiver);
        getContentResolver().unregisterContentObserver(mSimDelObserver);
        if (mSimSelectDialog != null) {
            mSimSelectDialog.dismiss();
        }
        if (mDeleteDialog != null) {
            mDeleteDialog.dismiss();
        }
    }

    @Override
    public void onAttachFragment(final Fragment fragment) {
        if (fragment instanceof SortMsgListFragment) {
            mSortMsgListFragment = (SortMsgListFragment) fragment;
            mSortMsgListFragment.setHost(this);
        }
    }

    @Override
    protected void updateActionBar(ActionBar actionBar) {
        mActionBar = actionBar;
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setTitle(getResources().getString(R.string.simsms_title));
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (super.onCreateOptionsMenu(menu)) {
            return true;
        }
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.sort_msg_list_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mActionMode != null) {
            return true;
        }
        // del message
        Cursor cursor = mSortMsgListFragment.mAdapter.getCursor();
        MenuItem deleteMessages = menu.findItem(R.id.action_delete_messages);
        final boolean isEmpty = (null == cursor || mSortMsgListFragment.mAdapter.getItemCount() == 0);
        deleteMessages.setEnabled(!isEmpty);

        // display option
        TelephonyManager telephonyManager = (TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager.getPhoneCount() < 2) {
            menu.removeItem(R.id.action_display_option);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void updateOptionsMenu(){
        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_display_option:
                createSimSelectDialog();
                return true;
            case R.id.action_sortby:
                createOrderTypeDialog();
                return true;
            case R.id.action_delete_messages:
                startMultiSelectActionMode();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createSimSelectDialog() {
        if (null == mSimSelectDialog) {
            int subIdShow = sortDisplayController.getValueForDisplayOption();
            int choiceItem = 0;
            final ArrayList<String> simList = new ArrayList<String>();
            simList.add(SortMsgListActivity.this.getString(R.string.folder_display_all));
            final List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();
            if (subscriptionInfoList != null && subscriptionInfoList.size() > 0) {
                for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
                    String simNameText = subscriptionInfo.getDisplayName().toString();
                    String displayName = TextUtils.isEmpty(simNameText) ? SortMsgListActivity.this
                            .getString(R.string.sim_slot_identifier, subscriptionInfo.getSimSlotIndex() + 1) : simNameText;
                    simList.add(displayName);
                    if (subscriptionInfo.getSubscriptionId() == subIdShow) {
                        choiceItem = simList.size() - 1;
                    }
                }
            }
            ArrayAdapter<String> simAdapter = new ArrayAdapter<String>(this, R.layout.display_options, simList);
            DialogInterface.OnClickListener simSelectListener = new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    int realValue = 0;
                    if (which > 0) {
                        SubscriptionInfo subscriptionInfo = subscriptionInfoList.get(which - 1);
                        realValue = subscriptionInfo.getSubscriptionId();
                    }
                    sortDisplayController.putIntForDisplayOption(realValue);
                    mSortMsgListFragment.reloadMessage();
                    dialog.dismiss();
                }
            };
            mSimSelectDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.folder_display_option)
                    .setSingleChoiceItems(simAdapter, choiceItem, simSelectListener)
                    .setNegativeButton(android.R.string.cancel, null).create();
        }
        if (!mSimSelectDialog.isShowing()) {
            mSimSelectDialog.show();
        }
    }

    //for bug669717 begin
    private void closeSimSelectDialog() {
        if (mSimSelectDialog != null && mSimSelectDialog.isShowing()) {
            try {
                mSimSelectDialog.dismiss();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mSimSelectDialog = null;
            }
        }
    }
    //for bug669717 end

    private void createOrderTypeDialog() {
        int checkedItem = sortDisplayController.getCheckedItemByOrderValue();
        final AlertDialog.Builder orderTypeDialog = new AlertDialog.Builder(this);
        orderTypeDialog.setTitle(R.string.folder_sortby);
        orderTypeDialog.setSingleChoiceItems(R.array.sort_type, checkedItem,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        sortDisplayController.putStringForOrderType(whichButton);
                        try {
                            mSortMsgListFragment.reloadMessage();
                        } catch (Exception e) {
                        }
                        dialog.dismiss();
                    }
                });
        orderTypeDialog.setNegativeButton(android.R.string.cancel, null);
        orderTypeDialog.show();
    }

    private MultiSelectActionModeCallback multiSelectActionModeCallback;

    protected void startMultiSelectActionMode() {
        if (null == multiSelectActionModeCallback) {
            multiSelectActionModeCallback = new MultiSelectActionModeCallback(this, mSortMsgListFragment.mAdapter);
        }
        startActionMode(multiSelectActionModeCallback);
    }

    private void holdSelectMessages(final ArrayList<Integer> messagesId) {
        mMessagesId = messagesId;
    }

    protected void confirmDeleteMessage() {
        if (mMessagesId.size() > 0 && isInSelectMode()) {
            isSIMMultiDelete = true;
            mDeletCount = 0;
            mSimDelObserver.showProgerss(mMessagesId);
        } else {//delete a single message not by action mode
            SimUtils.deleteSingleSimSms(this, mSimSmsId, mSubIdToDelSimSms);
        }
    }

    @Override
    public void onActionBarDelete(final ArrayList<Integer> messages) {
        holdSelectMessages(messages);
        createDeleteMessageDialog(R.string.delete_messages_confirmation_dialog_title);
    }

    protected void exitMultiSelectState() {
        dismissActionMode();
    }

    @Override
    public void onActionBarTitleUpdate(int count) {
        UpdateSelectMessageCount(count);
    }

    @Override
    public void onMessageClicked(SortMsgListItemData listItemData) {
        if (isInSelectMode()) {
            multiSelectActionModeCallback.toggleSelect(listItemData);
            updateUi();
        }
    }

    private boolean isInSelectMode() {
        return getActionModeCallback() != null;
    }

    @Override
    public boolean isMessageSelected(int messageId) {
        return isInSelectMode() && multiSelectActionModeCallback.isSelected(messageId);
    }

    @Override
    public void copySimSmsToPhone(int subId, String bobyText,
                                  long receivedTimestamp, int messageStatus,
                                  boolean isRead, String address) {
        SimUtils.copySimSmsToPhone(this, subId, bobyText,
                receivedTimestamp, messageStatus, isRead, address);
    }

    @Override
    public void deleteSimSms(int simSmsId, int subIdToDelSimSms) {
        mSimSmsId = simSmsId;
        mSubIdToDelSimSms = subIdToDelSimSms;
        createDeleteMessageDialog(R.string.delete_message_confirmation_dialog_title);
    }

    @Override
    public boolean isInActionMode() {
        return (mActionMode != null);
    }

    //add for bug 556823 begin
    private Cursor getSubIdByMessageId(int messageId) {
        final ContentResolver resolver = getApplicationContext().getContentResolver();
        Uri iccUri = Uri.parse("content://sms/icc");
        String iccWhere = "_id=" + messageId;
        return resolver.query(iccUri, null, iccWhere, null, null);
    }

    class SimSmsDelObserver extends ContentObserver {

        private List<Integer> mDelList = new ArrayList<Integer>();
        private ProgressDialog mProgressDialog;

        /**
         * Creates a content observer.
         */
        public SimSmsDelObserver() {
            super(new Handler());
            mProgressDialog = createProgressDialog();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            if (isSIMMultiDelete) {
                mDeletCount++;
                mProgressDialog.setProgress(mDeletCount);
                if (mDelList.size() == mDeletCount) {
                    mProgressDialog.dismiss();
                    exitMultiSelectState();
                    isSIMMultiDelete = false;
                    return;
                }
                excuteDelSimSms();
            }
        }

        private ProgressDialog createProgressDialog() {
            ProgressDialog dialog = new ProgressDialog(SortMsgListActivity.this);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setTitle(R.string.action_delete_message);
            dialog.setCancelable(false);
            return dialog;
        }

        public void showProgerss(ArrayList<Integer> delList) {
            mDelList = delList;
            mProgressDialog.setMax(mDelList.size());
            excuteDelSimSms();
            mProgressDialog.show();
        }

        private void excuteDelSimSms() {
            Cursor iccCursor = getSubIdByMessageId(mDelList.get(mDeletCount));
            if (iccCursor == null || iccCursor.getCount() == 0) {
                Log.d(TAG, "WRONG MESSAGE ID!");
                if (iccCursor != null) {
                    iccCursor.close();
                }
                return;
            }
            iccCursor.moveToFirst();
            int iccSubId = iccCursor.getInt(iccCursor.getColumnIndex("sub_id"));
            SimUtils.deleteSingleSimSms(SortMsgListActivity.this, mDelList.get(mDeletCount), iccSubId);
            iccCursor.close();
        }
    }

    private static String LAST_SIM_STATUS = IccCardConstants.INTENT_VALUE_ICC_ABSENT;
    private IntentFilter mSimFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
    private BroadcastReceiver mSimInOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            LogUtil.d(TAG, " The simStatus" + simStatus);
            if (intent.getAction() == TelephonyIntents.ACTION_SIM_STATE_CHANGED) {
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(LAST_SIM_STATUS)
                        && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (TelephonyManager.SIM_STATE_ABSENT == telephonyManager.getSimState()) {
                        finish();
                    }
                    return;
                } else if (!IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(LAST_SIM_STATUS)
                        && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    LAST_SIM_STATUS = simStatus;
                    finish();
                }
                LAST_SIM_STATUS = simStatus;
            }
        }
    };

    @Override
    public void updateUi() {
        if (mSortMsgListFragment != null) {
            mSortMsgListFragment.updateUi();
        }
    }

    private AlertDialog mDeleteDialog;

    protected void createDeleteMessageDialog(int titleId) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(titleId)
                .setMessage(R.string.delete_message_confirmation_dialog_text)
                .setPositiveButton(R.string.delete_message_confirmation_button,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface dialog, final int which) {
                                confirmDeleteMessage();
                            }
                        }).setNegativeButton(android.R.string.cancel, null);
        mDeleteDialog = builder.create();
        mDeleteDialog.show();
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String permissions[], final int[] grantResults) {
        if (requestCode != 0 || grantResults.length < 1) {
            return;
        }
        boolean hasGranted = true;
        for (int permissionValue : grantResults) {
            if (PackageManager.PERMISSION_DENIED == permissionValue) {
                hasGranted = false;
                break;
            }
        }
        if (hasGranted) {
            mSortMsgListFragment.reloadMessage();
        } else {
            finish();
        }
    }
}
