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

package com.android.messaging.ui.appsettings;

import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.telephony.SmsManagerEx;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.core.app.NavUtils;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.ParticipantRefresh;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.ApnDatabase;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.sprd.telephony.RadioInteractor;
import com.sprd.messaging.ui.smsc.ShowSmscEditDialogActivity;
import com.sprd.messaging.ui.smsc.SmscListActivity;
import com.sprd.messaging.ui.smsc.SmscManager;
import com.sprd.messaging.util.SystemAdapter;

import java.lang.reflect.Method;

public class PerSubscriptionSettingsActivity extends BugleActionBarActivity {
    public static final String TAG = "PerSubscriptionSettingsActivity";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final String title = getIntent().getStringExtra(
                UIIntents.UI_INTENT_EXTRA_PER_SUBSCRIPTION_SETTING_TITLE);
        if (!TextUtils.isEmpty(title)) {
            getSupportActionBar().setTitle(title);
        } else {
            // This will fall back to the default title, i.e. "Messaging settings," so No-op.
        }

        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        final PerSubscriptionSettingsFragment fragment = new PerSubscriptionSettingsFragment();
        ft.replace(android.R.id.content, fragment);
        ft.commit();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class PerSubscriptionSettingsFragment extends PreferenceFragment
            implements OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {
        private PhoneNumberPreference mPhoneNumberPreference;
        private Preference mGroupMmsPreference;
        private String mGroupMmsPrefKey;
        private String mPhoneNumberKey;
        private int mSubId;

        //by sprd, begin
        private int mSlotId = SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        // smsc
        private Preference mSmscPreference;
        private String mSmscPrefKey;
        // validity
        private ListPreference mSmsValidityPref;
        private ListPreference mMmsValidityPref;
        private final String MMS_VALIDITY_SETTING = "persist.radio.mmstime";
        // report
        private Preference mEnableReturnMmsReadReportsPreference;
        // retry times
        private Preference mSmsRetryTimesPref;
        private final String SEND_RETRIE_TIME = "persist.radio.retry_control";
        private final String MAX_SEND_RETRIES = "3";
        private final String MIN_SEND_RETRIES = "0";
        // savesim pref
        private Preference mSmsSaveSimPreference;
        // mms size limit
        public final String MMS_SIZE_LIMIT = "pref_key_mms_size_limit";
        private ListPreference mMmsSizeLimit;
        // sms wap
        private Preference mSmsWapPushPrefence;
        private String mSmsWapPrefKey;

        private SmsManagerEx smsManagerEx;
        private final int RequestCode = 1;
        //by sprd, end

        public PerSubscriptionSettingsFragment() {
            // Required empty constructor
        }

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            smsManagerEx = SmsManagerEx.getDefault();
            // Get sub id from launch intent
            final Intent intent = getActivity().getIntent();
            Assert.notNull(intent);
            mSubId = (intent != null) ? intent.getIntExtra(UIIntents.UI_INTENT_EXTRA_SUB_ID,
                    ParticipantData.DEFAULT_SELF_SUB_ID) : ParticipantData.DEFAULT_SELF_SUB_ID;
            //sprd, get real subid and slotid, begin
            if (!SubscriptionManager.isValidSubscriptionId(mSubId)) {
                mSubId = getRealSubId(mSubId);
                LogUtil.d(TAG, " onCreate real mSubId = " + mSubId);
            }
            mSlotId = SubscriptionManager.getSlotIndex(mSubId);
            LogUtil.d(TAG, " onCreate mSubId/mSlotId = " + mSubId + "/" + mSlotId);
            //sprd end
            final BuglePrefs subPrefs = Factory.get().getSubscriptionPrefs(mSubId);
            getPreferenceManager().setSharedPreferencesName(subPrefs.getSharedPreferencesName());
            addPreferencesFromResource(R.xml.preferences_per_subscription);

            mPhoneNumberKey = getString(R.string.mms_phone_number_pref_key);
            mPhoneNumberPreference = (PhoneNumberPreference) findPreference(mPhoneNumberKey);
            final PreferenceCategory advancedCategory = (PreferenceCategory)
                    findPreference(getString(R.string.advanced_category_pref_key));
            final PreferenceCategory mmsCategory = (PreferenceCategory)
                    findPreference(getString(R.string.mms_messaging_category_pref_key));

            if (!OsUtil.hasPhonePermission()) {
                OsUtil.requestMissingPermission(getActivity());
            } else {
                mPhoneNumberPreference.setDefaultPhoneNumber(
                        PhoneUtils.get(mSubId).getCanonicalForSelf(false/*allowOverride*/), mSubId);
            }

            mGroupMmsPrefKey = getString(R.string.group_mms_pref_key);
            mGroupMmsPreference = findPreference(mGroupMmsPrefKey);
            if (!MmsConfig.get(mSubId).getGroupMmsEnabled()) {
                // Always show group messaging setting even if the SIM has no number
                // If broadcast sms is selected, the SIM number is not needed
                // If group mms is selected, the phone number dialog will popup when message
                // is being sent, making sure we will have a self number for group mms.
                mmsCategory.removePreference(mGroupMmsPreference);
            } else {
                mGroupMmsPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference pref) {
                        GroupMmsSettingDialog.showDialog(getActivity(), mSubId);
                        return true;
                    }
                });
                updateGroupMmsPrefSummary();
            }

            if (!MmsConfig.get(mSubId).getSMSDeliveryReportsEnabled()) {
                final Preference deliveryReportsPref = findPreference(
                        getString(R.string.delivery_reports_pref_key));
                mmsCategory.removePreference(deliveryReportsPref);
            }

            // sms retry times
            mSmsRetryTimesPref = findPreference(getString(R.string.sms_retry_times_pref_key));
            if (!MmsConfig.get(mSubId).getSMSRetryTimesEnabled()) {
                advancedCategory.removePreference(mSmsRetryTimesPref);
            } else {
                mSmsRetryTimesPref.setOnPreferenceChangeListener(this);
            }

            //capacity, begin
            RadioInteractor radioInteractor = new RadioInteractor(getContext());
            String capacity = radioInteractor.getSimCapacity(mSlotId);
            LogUtil.d(TAG, "the capacity is:" + capacity);
            if (null == capacity) {
                capacity = "";
            } else {
                capacity = capacity.replace(":", "/");
            }
            Preference mSimMessageCapacity = findPreference(getString(R.string.capacity_sim_message_key));
            final String summary = getString(R.string.capacity_sim_message_summary);
            mSimMessageCapacity.setSummary(String.format(summary, capacity));
            //capacity, end

            // Access Point Names (APNs)
            final Preference apnsPref = findPreference(getString(R.string.sms_apns_key));

            if (MmsUtils.useSystemApnTable() && !ApnDatabase.doesDatabaseExist()) {
                // Don't remove the ability to edit the local APN prefs if this device lets us
                // access the system APN, but we can't find the MCC/MNC in the APN table and we
                // created the local APN table in case the MCC/MNC was in there. In other words,
                // if the local APN table exists, let the user edit it.
                advancedCategory.removePreference(apnsPref);
            } else {
                final PreferenceScreen apnsScreen = (PreferenceScreen) findPreference(
                        getString(R.string.sms_apns_key));
                apnsScreen.setIntent(UIIntents.get()
                        .getApnSettingsIntent(getPreferenceScreen().getContext(), mSubId));
            }

            //sprd, begin
            final Preference autoRetrieveMmsPreference = findPreference(getString(R.string.auto_retrieve_mms_pref_key));
            final Preference deliveryReportsPreference = findPreference(getString(R.string.delivery_reports_pref_key));
            final Preference mmsSendReportsPreference = findPreference(getString(R.string.send_reports_pref_key));
            final Preference smsSendReportsPreference = findPreference(getString(R.string.mms_send_reports_pref_key));
            final Preference mmsdeliveryReportsPreference = findPreference(getString(R.string.mms_delivery_reports_pref_key));
            //sprd, end
            // We want to disable preferences if we are not the default app, but we do all of the
            // above first so that the user sees the correct information on the screen
            if (!PhoneUtils.getDefault().isDefaultSmsApp()) {
                mGroupMmsPreference.setEnabled(false);
                //final Preference autoRetrieveMmsPreference = findPreference(getString(R.string.auto_retrieve_mms_pref_key));
                autoRetrieveMmsPreference.setEnabled(false);
                //final Preference deliveryReportsPreference = findPreference(getString(R.string.delivery_reports_pref_key));
                deliveryReportsPreference.setEnabled(false);
                //sprd, begin
                mmsSendReportsPreference.setEnabled(false);
                smsSendReportsPreference.setEnabled(false);
                mmsdeliveryReportsPreference.setEnabled(false);
                //sprd, end
            }

            // mms read report, begin
            final Preference mMmsReadReportsPreference = findPreference(getString(R.string.mms_read_reports_pref_key));
            mEnableReturnMmsReadReportsPreference = findPreference(getString(R.string.enable_return_mms_read_reports_pref_key));
            if (!MmsConfig.get(mSubId).getmMmsReadReportsEnable()) {
                mMmsReadReportsPreference.setEnabled(false);
                mEnableReturnMmsReadReportsPreference.setEnabled(false);
                mmsCategory.removePreference(mMmsReadReportsPreference);
                mmsCategory.removePreference(mEnableReturnMmsReadReportsPreference);
            }
            // mms read report, end

            // mms & sms, validity pref, begin
            if (OsUtil.hasPhonePermission()) {
                String mmsValidityPrefKey = getString(R.string.mms_validity_pref_key);
                mMmsValidityPref = (ListPreference) findPreference(mmsValidityPrefKey);
                if (!MmsConfig.getValidityMmsEnabled()
                        || (PhoneUtils.getDefault().getActiveSubscriptionCount() == 0)) {
                    mmsCategory.removePreference(mMmsValidityPref);
                } else if (null != mMmsValidityPref) {
                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
                    String curVal = sharedPref.getString(mmsValidityPrefKey
                            + mSubId, "604800");
                    SystemAdapter.getInstance().setProperty(getRealValidityKey(MMS_VALIDITY_SETTING), curVal);
                    mMmsValidityPref.setValue(curVal);
                    mMmsValidityPref.setSummary(mMmsValidityPref.getEntry());
                    mMmsValidityPref.setOnPreferenceChangeListener(this);
                }
            }

            mSmsValidityPref = (ListPreference) findPreference(getString(R.string.sms_validity_pref_key));
            if (!MmsConfig.getValiditySmsEnabled()
                    || (PhoneUtils.getDefault().getActiveSubscriptionCount() == 0)) {
                advancedCategory.removePreference(mSmsValidityPref);
            } else if (mSmsValidityPref != null) {
                try {
                    Method method = smsManagerEx.getClass().getMethod("getRelativeValidityPeriod", Context.class, int.class);
                    String currentValue = (String) method.invoke(smsManagerEx, getContext(), mSubId);
                    mSmsValidityPref.setValue(currentValue);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mSmsValidityPref.setSummary(mSmsValidityPref.getEntry());
                mSmsValidityPref.setOnPreferenceChangeListener(this);
            }
            // mms & sms, validity pref, end

            // sprd add for smsc begin
            mSmscPrefKey = getString(R.string.smsc_pref_key);
            mSmscPreference = findPreference(mSmscPrefKey);

            if (OsUtil.hasPhonePermission()) {
                if (PhoneUtils.getDefault().getActiveSubscriptionCount() == 0
                        || !MmsConfig.get(mSubId).getSmscShowEnabled()) {
                    advancedCategory.removePreference(mSmscPreference);
                } else {
                    final String mDisplayName = PhoneUtils.get(getRealSubId(mSubId)).getCarrierName();
                    mSmscPreference.setTitle(getString(R.string.pref_title_manage_simx_smsc, " " + mDisplayName));
                    if (getContext().getResources().getBoolean(R.bool.config_smsc_editable)) {
                        mSmscPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference pref) {
                                Intent intent;
                                if (!SmscManager.getInstance().isMultiSmsc(
                                        getRealSubId(mSubId))) {
                                    intent = new Intent(getActivity(), ShowSmscEditDialogActivity.class);
                                } else {
                                    intent = new Intent(getActivity(), SmscListActivity.class);
                                }
                                intent.putExtra("subId", getRealSubId(mSubId));
                                startActivityForResult(intent, RequestCode);
                                return true;
                            }
                        });
                    }
                }
            }
            // sprd add for smsc end

            // sms saved in sim
            mSmsSaveSimPreference = findPreference(getString(R.string.sms_save_to_sim_pref_key));
            if (OsUtil.isSecondaryUser()) {
                advancedCategory.removePreference(mSmsSaveSimPreference);
            }
            // sms wap
            mSmsWapPrefKey = getString(R.string.sms_wap_pref_key);
            mSmsWapPushPrefence = findPreference(mSmsWapPrefKey);
            if (!MmsConfig.enableSmsWapFeature() || OsUtil.isSecondaryUser()) {
                advancedCategory.removePreference(mSmsWapPushPrefence);
            }
            // mms size limit
            mMmsSizeLimit = (ListPreference) findPreference(MMS_SIZE_LIMIT);
            if (!MmsConfig.get(mSubId).enableSelectMmsSize()) {
                mmsCategory.removePreference(mMmsSizeLimit);
            } else if (mMmsSizeLimit != null) {
                SharedPreferences sharedPref = PreferenceManager
                        .getDefaultSharedPreferences(this.getActivity());
                String curVal = sharedPref.getString(MMS_SIZE_LIMIT + mSubId, "1024");
                mMmsSizeLimit.setValue(curVal);
                mMmsSizeLimit.setSummary(mMmsSizeLimit.getEntry());
                mMmsSizeLimit.setOnPreferenceChangeListener(this);
            }

            if (OsUtil.isSecondaryUser()) {
                final Preference autoRetrieveMmsWhenRoamingPreference2 = findPreference(getString(R.string.auto_retrieve_mms_when_roaming_pref_key));
                mmsCategory.removePreference(autoRetrieveMmsPreference);
                mmsCategory.removePreference(autoRetrieveMmsWhenRoamingPreference2);
                advancedCategory.removePreference(deliveryReportsPreference);
                advancedCategory.removePreference(mmsSendReportsPreference);
                mmsCategory.removePreference(smsSendReportsPreference);
                mmsCategory.removePreference(mmsdeliveryReportsPreference);
            }

            final IntentFilter mSimFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            getActivity().registerReceiver(mSimInOutReceiver, mSimFilter);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            switch (requestCode) {
                case RequestCode:
                    if (resultCode == RESULT_OK) {
                        String SmscData = data.getStringExtra("Smsc");
                        mSmscPreference.setSummary(SmscData);
                    }
                    break;
                default:
                    break;
            }
        }

        private void updateSmscSummary(int subId) {//for smsc
            String summary = SmscManager.getSmscString(getActivity(), subId);
            final BidiFormatter bidiFormatter = BidiFormatter.getInstance();//use LTR for ar
            mSmscPreference.setSummary(bidiFormatter.unicodeWrap(summary, TextDirectionHeuristics.LTR));
        }

        private void updateGroupMmsPrefSummary() {
            final boolean groupMmsEnabled = getPreferenceScreen().getSharedPreferences().getBoolean(
                    mGroupMmsPrefKey, getResources().getBoolean(R.bool.group_mms_pref_default));
            mGroupMmsPreference.setSummary(groupMmsEnabled ?
                    R.string.enable_group_mms : R.string.disable_group_mms);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
            updateSmscSummary(getRealSubId(mSubId));//sprd add for smsc
        }

        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                              final String key) {
            if (key.equals(mGroupMmsPrefKey)) {
                updateGroupMmsPrefSummary();
            } else if (key.equals(mPhoneNumberKey)) {
                // Save the changed phone number in preferences specific to the sub id
                final String newPhoneNumber = mPhoneNumberPreference.getText();
                final BuglePrefs subPrefs = BuglePrefs.getSubscriptionPrefs(mSubId);
                if (TextUtils.isEmpty(newPhoneNumber)) {
                    subPrefs.remove(mPhoneNumberKey);
                } else {
                    subPrefs.putString(getString(R.string.mms_phone_number_pref_key),
                            newPhoneNumber);
                }
                // Update the self participants so the new phone number will be reflected
                // everywhere in the UI.
                ParticipantRefresh.refreshSelfParticipants();
            } else if (key.equals(mSmscPrefKey)) {
                updateSmscSummary(getRealSubId(mSubId));
            } else if (key.equals(mSmsWapPrefKey)) {
                boolean smsWapEnable = sharedPreferences.getBoolean(key, true);
                handleSmsWapPreferenceChanged(smsWapEnable);
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        private int getRealSubId(int subId) {//by sprd
            if (OsUtil.hasPhonePermission()) {
                return PhoneUtils.getDefault().getEffectiveSubId(subId);
            } else {
                return -1;
            }
        }

        //add for bug 610115 start
        @Override
        public void onDestroy() {
            getActivity().unregisterReceiver(mSimInOutReceiver);
            super.onDestroy();
        }

        private BroadcastReceiver mSimInOutReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                int slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY, -1);
                LogUtil.d(TAG, "mSimInOutReceiver slotId/simStatus = " + slotId + "/" + simStatus);
                if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                    final int subIdEx = intent.getIntExtra(PhoneConstants.SUBSCRIPTION_KEY, -1);
                    LogUtil.d(TAG, "mSimInOutReceiver subIdEx = " + subIdEx);
                    if (SubscriptionManager.isValidSubscriptionId(subIdEx)) {
                        if (subIdEx == mSubId) {
                            getActivity().finish();
                        }
                    } else if (SubscriptionManager.isValidSlotIndex(mSlotId)) {
                        if (mSlotId == slotId) {
                            getActivity().finish();
                        }
                    } else {//single card
                        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                        if (TelephonyManager.SIM_STATE_ABSENT == telephonyManager.getSimState()) {
                            getActivity().finish();
                        }
                    }
                }
            }
        };

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            SharedPreferences.Editor prefEditor = PreferenceManager
                    .getDefaultSharedPreferences(this.getActivity()).edit();

            if (preference == mSmsRetryTimesPref) {
                if (((Boolean) newValue).booleanValue()) {
                    SystemAdapter.getInstance().setProperty(SEND_RETRIE_TIME, MAX_SEND_RETRIES);
                } else {
                    SystemAdapter.getInstance().setProperty(SEND_RETRIE_TIME, MIN_SEND_RETRIES);
                }
                return true;
            } else if (preference == mSmsValidityPref) {
                try {
                    Method method = smsManagerEx.getClass().getMethod("setRelativeValidityPeriod", Context.class, int.class, String.class);
                    method.invoke(smsManagerEx, getContext(), mSubId, newValue.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mSmsValidityPref.setValue(newValue.toString());
                mSmsValidityPref.setSummary(mSmsValidityPref.getEntry());
                return true;
            } else if (preference == mMmsValidityPref) {
                SystemAdapter.getInstance().setProperty(getRealValidityKey(MMS_VALIDITY_SETTING), newValue.toString());
                prefEditor.putString(this.getActivity().getString(R.string.mms_validity_pref_key)
                        + mSubId, newValue.toString());
                mMmsValidityPref.setValue(newValue.toString());
                mMmsValidityPref.setSummary(mMmsValidityPref.getEntry());
                prefEditor.commit();
                return true;
            } else if (preference == mMmsSizeLimit) {
                prefEditor.putString(MMS_SIZE_LIMIT + mSubId, newValue.toString());
                mMmsSizeLimit.setValue(newValue.toString());
                mMmsSizeLimit.setSummary(mMmsSizeLimit.getEntry());
                prefEditor.commit();
                MmsConfig.get(mSubId).setMmsSizeLimit(Integer.valueOf(newValue.toString()));
                return true;
            }
            return false;
        }

        private String getRealValidityKey(String key) {
            return key + getRealSubId(mSubId);
        }

        private void handleSmsWapPreferenceChanged(boolean newValue) {
            MmsConfig.setSmsWapPreference(mSubId, newValue);
        }
    }
}
