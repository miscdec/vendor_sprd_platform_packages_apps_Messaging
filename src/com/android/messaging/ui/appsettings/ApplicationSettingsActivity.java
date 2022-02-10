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
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
//import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.PreferenceCategory;
import android.preference.RingtonePreference;
import android.preference.TwoStatePreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.provider.Settings;
import androidx.core.app.NavUtils;
import android.text.TextUtils;
import android.text.Editable;
import android.text.Selection;
import android.text.InputFilter;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import java.util.List;

import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.content.Context;
import android.util.Log;
/*Add By SPRD for bug:588863 {@*/
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.os.Environment;
import android.database.Cursor;
import android.text.format.Formatter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.android.messaging.Factory;
/*@}*/
import com.android.messaging.R;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.LicenseActivity;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.sprd.messaging.sms.commonphrase.ui.PharserActivity;
import com.sprd.messaging.util.SystemAdapter;
import com.android.messaging.datamodel.data.ParticipantData;
import android.app.Fragment;

public class ApplicationSettingsActivity extends BugleActionBarActivity {
    private boolean topLevel;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(mSimInOutReceiver,mSimFilter);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        topLevel = getIntent().getBooleanExtra(
                UIIntents.UI_INTENT_EXTRA_TOP_LEVEL_SETTINGS, false);
        if (topLevel) {
            getSupportActionBar().setTitle(getString(R.string.settings_activity_title));
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(android.R.id.content, new ApplicationSettingsFragment());
        ft.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (super.onCreateOptionsMenu(menu)) {
            return true;
        }
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            NavUtils.navigateUpFromSameTask(this);
            return true;
        case R.id.action_license:
            final Intent intent = new Intent(this, LicenseActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class ApplicationSettingsFragment extends PreferenceFragment implements
            OnSharedPreferenceChangeListener {
        private static final String TAG = "ApplicationSettingsFragment";
        private String mNotificationsEnabledPreferenceKey;
        private TwoStatePreference mNotificationsEnabledPreference;
        private String mRingtonePreferenceKey;
        private RingtonePreference mRingtonePreference;
        private Preference mVibratePreference;
        private String mSmsDisabledPrefKey;
        private Preference mSmsDisabledPreference;
        private String mSmsEnabledPrefKey;
        private Preference mSmsEnabledPreference;
        /*Add by SPRD for Bug:533513  2016.03.10 Start */
        private String mSignatureEenablePreferenceKey;
        private TwoStatePreference mSignatureEnabledPreference;
        private boolean isSignatureEnable;
        /*Add by SPRD for Bug:533513  2016.03.10 End */
        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
        private String mSignaturePrefKey;
        private EditTextPreference mSignaturePreference;
        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
        private boolean mIsSmsPreferenceClicked;

        /*Add by SPRD for Bug:562203 Encode Type feature  Start */
        private Preference mEncodeTypeSettingPreference;
        private String mEncodeTypePreKey;
        private ListPreference mEncodeTypePreference;
        public static final String SMS_ENCODE_TYPE = "persist.radio.sms_encode_type";
        /*Add by SPRD for Bug:562203 Encode Type feature  End */

        /*Add By SPRD for bug:588863 Start*/
        private Preference mInternalMemoryUsagePrefence;
        private String mInternalMemoryUsageKey;
        private static String MMS_URL = "content://mms/part/";
        private String mProjection[] = {"_id","text"};
        private Context mContext;
        /*Add By SPRD for bug:588863 End*/

        // sprd add for common message begin
        private Preference mCommonPhrasePreference;
        private String mCommonPhrasePrefKey;
        // sprd add for common message end
        private PreferenceScreen advancedScreen;

        public ApplicationSettingsFragment() {
            // Required empty constructor
        }
        private Preference mCtccSmartSmsPreference;
        private String mCtccSmartSmsPrefKey;

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mContext = Factory.get().getApplicationContext();//Add By SPRD for bug:588863
            getPreferenceManager().setSharedPreferencesName(BuglePrefs.SHARED_PREFERENCES_NAME);
            addPreferencesFromResource(R.xml.preferences_application);

            mNotificationsEnabledPreferenceKey =
                    getString(R.string.notifications_enabled_pref_key);
            mNotificationsEnabledPreference = (TwoStatePreference) findPreference(
                    mNotificationsEnabledPreferenceKey);
            mRingtonePreferenceKey = getString(R.string.notification_sound_pref_key);
            mRingtonePreference = (RingtonePreference) findPreference(mRingtonePreferenceKey);
            mVibratePreference = findPreference(
                    getString(R.string.notification_vibration_pref_key));
            /**
             *SPRD: Add for bug 509830 begin
             * hide the ringtone and vibrate setting when the config is not origin
             */
            if (!MmsConfig.getKeepOrgSoundVibrate()) {
                getPreferenceScreen().removePreference(mRingtonePreference);
                getPreferenceScreen().removePreference(mVibratePreference);
            }
            /* SPRD: Add for bug 509830 end */
            mSmsDisabledPrefKey = getString(R.string.sms_disabled_pref_key);
            mSmsDisabledPreference = findPreference(mSmsDisabledPrefKey);
            mSmsEnabledPrefKey = getString(R.string.sms_enabled_pref_key);
            mSmsEnabledPreference = findPreference(mSmsEnabledPrefKey);
            /*Add by SPRD for Bug:533513  2016.03.10 Start */
            mSignatureEenablePreferenceKey = getString(R.string.pref_key_signature_enable);
            mSignatureEnabledPreference = (TwoStatePreference)findPreference(mSignatureEenablePreferenceKey);
            /*Add by SPRD for Bug:533513  2016.03.10 End */
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
            mSignaturePrefKey = getString(R.string.signature_pref_key);
            mSignaturePreference = (EditTextPreference) findPreference(mSignaturePrefKey);
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
            mIsSmsPreferenceClicked = false;
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */

            final String mEncodeTypeSettingPreKey = getString(R.string.pref_key_encode_type_setting);
            mEncodeTypeSettingPreference = findPreference(mEncodeTypeSettingPreKey);
            if (mEncodeTypeSettingPreference != null) {
                getPreferenceScreen().removePreference(mEncodeTypeSettingPreference);
            }
            mEncodeTypePreKey = getString(R.string.pref_key_encode_type);
            mEncodeTypePreference = (ListPreference) findPreference(mEncodeTypePreKey);

            // sprd add for common message begin
            mCommonPhrasePrefKey = getString(R.string.phrase_pref_key);
            mCommonPhrasePreference = findPreference(mCommonPhrasePrefKey);
            // sprd add for common message end

            /*Add By SPRD for bug:588863 Start*/
            final PreferenceCategory otherCategory = (PreferenceCategory) findPreference(getString(R.string.pref_other_settings_key));
            mInternalMemoryUsageKey = getString(R.string.pref_key_internal_memory_usage);
            mInternalMemoryUsagePrefence = findPreference(mInternalMemoryUsageKey);
            if (!MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getInternalMemoryUsageEnabled()) { /*modified for Bug 724272*/
                otherCategory.removePreference(mInternalMemoryUsagePrefence);
            }

            /*Modify by SPRD for Bug:533513  2016.03.10 Start */
//            if (!MmsConfig.getSignatureEnabled()) {//if signature disabled, hide the signature preference
//                getPreferenceScreen().removePreference(mSignaturePreference);
//            } else {
//                mSignaturePreference.getEditText()
//                        .setFilters(new InputFilter[] {
//                             new InputFilter.LengthFilter(50)
//                         });
//            }
            updateSignatureOnOrOff();
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
            mSignaturePreference.getEditText().setFilters(new InputFilter[] {
                    new InputFilter.LengthFilter(50)
                        });
            updateSignatureSammary();
            /*Modify by SPRD for Bug:533513  2016.03.10 End */

            final SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            /* SPRD: Modify for bug 509830 begin */
            if (MmsConfig.getKeepOrgSoundVibrate()) {
                updateSoundSummary(prefs);
            }
            /* SPRD: Modify for bug 509830 end */
            if (!DebugUtils.isDebugEnabled()) {
                final Preference debugCategory = findPreference(getString(
                        R.string.debug_pref_key));
                getPreferenceScreen().removePreference(debugCategory);
            }
            advancedScreen = (PreferenceScreen) findPreference(getString(R.string.advanced_pref_key));
            final boolean topLevel = getActivity().getIntent().getBooleanExtra(
                    UIIntents.UI_INTENT_EXTRA_TOP_LEVEL_SETTINGS, false);
            if (topLevel) {
                advancedScreen.setIntent(UIIntents.get()
                        .getAdvancedSettingsIntent(getPreferenceScreen().getContext()));
            } else {
                // Hide the Advanced settings screen if this is not top-level; these are shown at
                // the parent SettingsActivity.
                getPreferenceScreen().removePreference(advancedScreen);
                advancedScreen = null;
            }
            /* add by sprd, begin*/
            if(!OsUtil.hasPhonePermission()){
                OsUtil.requestMissingPermission(getActivity());
            }else if(advancedScreen != null){
                List<SubscriptionInfo> sublist = SystemAdapter.getInstance()//SmsManager.getDefault()
                        .getActiveSubInfoList();
                if (sublist == null || sublist.size() == 0) {
                    Log.d(TAG, "onCreate, sim: "
                            + ((sublist == null) ? -1 : sublist.size()));
                    advancedScreen.setEnabled(false);
                    advancedScreen.setShouldDisableView(true);
                }
            }
            mCtccSmartSmsPrefKey= getString(R.string.ctcc_smart_sms_pref_key);
            mCtccSmartSmsPreference=findPreference(mCtccSmartSmsPrefKey);
            if(!MmsConfig.isCtccOp()){
                otherCategory.removePreference(mCtccSmartSmsPreference);
            }
           /*add by sprd, end*/
        }

        @Override
        public boolean onPreferenceTreeClick (PreferenceScreen preferenceScreen,
                Preference preference) {
            if (preference.getKey() ==  mSmsDisabledPrefKey ||
                    preference.getKey() == mSmsEnabledPrefKey) {
                mIsSmsPreferenceClicked = true;
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
            } else if (MmsConfig.getSignatureEnabled() && preference.getKey() == mSignaturePrefKey) {
                EditText signEdit = mSignaturePreference.getEditText();
                Editable sEditAble = signEdit.getText();
                Selection.setSelection(sEditAble, sEditAble.length());
                signEdit.requestFocus();//Add Unisoc bug1395263
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
            }
            // sprd add for common message begin
            else if (preference.getKey() == mCommonPhrasePrefKey) {
                Intent intent = new Intent(getActivity(), PharserActivity.class);
                startActivity(intent);
            } 
            // sprd add for common message end
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        /*Add By SPRD for bug:588863 Start*/
        private void setInternalMemoryUsageSummary() {
            ContentResolver cr = mContext.getContentResolver();
            Uri uri = Uri.parse(MMS_URL);
            AsyncQueryHandler queryHandler = new AsyncQueryHandler(cr) {
                protected void onQueryComplete(int token, Object cookie, Cursor c) {
                    showInternalMemoryUsage(c);
                }
            };
            queryHandler.startQuery(0, null, uri, mProjection, null, null, null);
        }

        private void showInternalMemoryUsage(Cursor cursor) {
            CheckInternalMemoryUsageTask showMemoryUsageTask = new CheckInternalMemoryUsageTask(cursor);
            showMemoryUsageTask.execute();
        }

        /* SPRD: Add this method for multi-sim setting. @{ */
        private class CheckInternalMemoryUsageTask extends AsyncTask {
            Cursor mCursor;
            long mMessageSize;

            public CheckInternalMemoryUsageTask(Cursor cursor) {
                mCursor = cursor;
                mMessageSize = 0;
            }

            @Override
            protected void onPreExecute() {
                mInternalMemoryUsagePrefence
                        .setSummary(getString(R.string.pref_internal_memory_usage_progress_summary));
            }

            @Override
            protected Object doInBackground(Object... params) {
                // Get free memory and total memory
                File path = Environment.getDataDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSize();
                long availableBlocks = stat.getAvailableBlocks();
                long totalBlocks = stat.getBlockCount();
                long availableSize = availableBlocks * blockSize - 500 * 1024;
                long finalTotalSize = totalBlocks * blockSize;
                if (availableSize < 0) {
                    availableSize = 0;
                }
                if (finalTotalSize < 0) {
                    finalTotalSize = 0;
                }

                // Get Mms usage memory
                if (mCursor == null) {
                    return null;
                }
                if (mCursor.getCount() > 0 && mCursor.moveToFirst()) {
                    do {
                        try {
                            Uri uri = Uri.parse(MMS_URL + mCursor.getString(0));
                            ContentResolver cr = mContext.getContentResolver();
                            ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
                            if (pfd == null) {
                                String text = mCursor.getString(1);
                                if (!TextUtils.isEmpty(text)) {
                                    mMessageSize += text.getBytes().length;
                                }
                                continue;
                            }
                            mMessageSize += pfd.getStatSize();
                            pfd.close();
                        } catch (FileNotFoundException e) {
                            Log.e(TAG, "File not found: " + e);
                        } catch (IOException ex) {
                            Log.e(TAG, "IOException ex: " + ex);
                        }
                    } while (mCursor.moveToNext() && !getActivity().isFinishing());
                }
                return formatSize(mMessageSize) + " / " + formatSize(availableSize) + " / "
                        + formatSize(finalTotalSize);
            }

            @Override
            protected void onPostExecute(Object result) {
                if (result != null) {
                    mInternalMemoryUsagePrefence.setSummary(result.toString());
                }
                if (mCursor != null) {
                    mCursor.close();
                }
            }
        }

        private String formatSize(long size) {
            return Formatter.formatFileSize(mContext, size);
        }
        /*Add By SPRD for bug:588863 End*/

        private void updateSoundSummary(final SharedPreferences sharedPreferences) {
            // The silent ringtone just returns an empty string
            String ringtoneName = mRingtonePreference.getContext().getString(
                    R.string.silent_ringtone);

            String ringtoneString = sharedPreferences.getString(mRingtonePreferenceKey, null);

            // Bootstrap the default setting in the preferences so that we have a valid selection
            // in the dialog the first time that the user opens it.
            if (ringtoneString == null) {
                ringtoneString = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
                final SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(mRingtonePreferenceKey, ringtoneString);
                editor.apply();
            }

            if (!TextUtils.isEmpty(ringtoneString)) {
                final Uri ringtoneUri = Uri.parse(ringtoneString);
                final Ringtone tone = RingtoneManager.getRingtone(mRingtonePreference.getContext(),
                        ringtoneUri);

                if (tone != null) {
                    ringtoneName = tone.getTitle(mRingtonePreference.getContext());
                }
            }

            mRingtonePreference.setSummary(ringtoneName);
        }

        private void updateSmsEnabledPreferences() {
            if (!OsUtil.isAtLeastKLP()) {
                getPreferenceScreen().removePreference(mSmsDisabledPreference);
                getPreferenceScreen().removePreference(mSmsEnabledPreference);
            } else {
                final String defaultSmsAppLabel = getString(R.string.default_sms_app,
                        PhoneUtils.getDefault().getDefaultSmsAppLabel());
                boolean isSmsEnabledBeforeState;
                boolean isSmsEnabledCurrentState;
                if (PhoneUtils.getDefault().isDefaultSmsApp()) {
                    if (getPreferenceScreen().findPreference(mSmsEnabledPrefKey) == null) {
                        getPreferenceScreen().addPreference(mSmsEnabledPreference);
                        isSmsEnabledBeforeState = false;
                    } else {
                        isSmsEnabledBeforeState = true;
                    }
                    isSmsEnabledCurrentState = true;
                    getPreferenceScreen().removePreference(mSmsDisabledPreference);
                    mSmsEnabledPreference.setSummary(defaultSmsAppLabel);
                } else {
                    if (getPreferenceScreen().findPreference(mSmsDisabledPrefKey) == null) {
                        getPreferenceScreen().addPreference(mSmsDisabledPreference);
                        isSmsEnabledBeforeState = true;
                    } else {
                        isSmsEnabledBeforeState = false;
                    }
                    isSmsEnabledCurrentState = false;
                    getPreferenceScreen().removePreference(mSmsEnabledPreference);
                    mSmsDisabledPreference.setSummary(defaultSmsAppLabel);
                }
                updateNotificationsPreferences();
            }
            mIsSmsPreferenceClicked = false;
        }

        private void updateNotificationsPreferences() {
            final boolean canNotify = !OsUtil.isAtLeastKLP()
                    || PhoneUtils.getDefault().isDefaultSmsApp();
            mNotificationsEnabledPreference.setEnabled(canNotify);
        }

        /*Add by SPRD for Bug:533513  2016.03.10 Start */
        private void updateSignatureOnOrOff() {
            isSignatureEnable = mSignatureEnabledPreference.isChecked();
            mSignatureEnabledPreference.setChecked(isSignatureEnable);
            MmsConfig.setSignatureEnable(isSignatureEnable);
        }
        /*Add by SPRD for Bug:533513  2016.03.10 End */
           private void handleCTCCSmartSmsPreferenceChanged(boolean newValue) {
           MmsConfig.setCtccSmartSmsPreference(newValue);
         }
        /*Modify by SPRD for Bug:533513  2016.03.10 Start */
        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
        private void updateSignatureSammary() {
            //mSignaturePreference.setSummary(mSignaturePreference.getText());
            String defaultSummary = getString(R.string.pref_signature_input_summary);
            if("".equals(mSignaturePreference.getText()) || "" == mSignaturePreference.getText()) {
                mSignaturePreference.setSummary(defaultSummary);
            } else {
                mSignaturePreference.setSummary(mSignaturePreference.getText());
            }
        }
        /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
        /*Modify by SPRD for Bug:533513  2016.03.10 End */

        /*Add by SPRD for Bug:562203 Encode Type feature  Start */
        private void updateEncodeTypePreference() {
            SharedPreferences sharedPref = PreferenceManager
                    .getDefaultSharedPreferences(this.getActivity());
            if (mEncodeTypePreference != null) {
                //add for bug630656 start
                String encodeType = sharedPref.getString(mEncodeTypePreKey, "-1");
                if ("-1".equals(MmsConfig.getEncodeType())){/*add for Bug 762330*/
                    encodeType = String.valueOf(MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getEncodePrefeStatus());/*modified for Bug 724272*/
                    Log.d(TAG,  "updateEncodeTypePreference: encodeType get from Property = " + encodeType );
                }
                Log.d(TAG,  "updateEncodeTypePreference: encodeType = " + encodeType + "mEncodeTypePreference.getEntry() = " +
                        mEncodeTypePreference.getEntry());
                //add for bug630656 end
                mEncodeTypePreference.setValue(encodeType);
                mEncodeTypePreference.setSummary(mEncodeTypePreference.getEntry());
                mEncodeTypePreference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        // TODO Auto-generated method stub
                        SharedPreferences.Editor prefEditor = PreferenceManager
                                .getDefaultSharedPreferences(mEncodeTypePreference.getContext()).edit();
                        prefEditor.putString(mEncodeTypePreference.getContext().
                                getString(R.string.pref_key_encode_type), newValue.toString());
                        Log.d(TAG, "updateEncodeTypePreference  encodeType:"+newValue.toString());
                        mEncodeTypePreference.setValue(newValue.toString());
                        mEncodeTypePreference.setSummary(mEncodeTypePreference.getEntry());
                        MmsConfig.setEncodeType(newValue.toString());
                        SystemAdapter.getInstance().setProperty(SMS_ENCODE_TYPE, newValue.toString());//smsManager.setProperty(SMS_ENCODE_TYPE, newValue.toString());
                        prefEditor.apply();
                        return true;
                    }
               });
            }
        }
        /*Add by SPRD for Bug:562203 Encode Type feature  End */

        @Override
        public void onStart() {
            super.onStart();
            // We do this on start rather than on resume because the sound picker is in a
            // separate activity.
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            updateSmsEnabledPreferences();
            updateNotificationsPreferences();
            updateEncodeTypePreference();//Add by SPRD for Bug:562203 Encode Type feature
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
            if (MmsConfig.getSignatureEnabled()) {
                updateSignatureSammary();
            }
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
            if(!OsUtil.hasPhonePermission()){
                Log.d(TAG, "=======zhongjihao===onResume===0====");
                OsUtil.requestMissingPermission(getActivity());
            }else if(advancedScreen != null){
                Log.d(TAG, "=======zhongjihao===onResume===1====");
                List<SubscriptionInfo> sublist = SystemAdapter.getInstance()//SmsManager.getDefault()
                        .getActiveSubInfoList();
                if (sublist == null || sublist.size() == 0) {
                    Log.d(TAG, "=======zhongjihao===onResume=======sim: "
                                    + ((sublist == null) ? -1 : sublist.size()));
                    advancedScreen.setEnabled(false);
                    advancedScreen.setShouldDisableView(true);
                }else{
                    advancedScreen.setEnabled(true);
                    advancedScreen.setShouldDisableView(true);
                }
            }

            /*Add By SPRD for bug:588863 Start*/
            if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getInternalMemoryUsageEnabled()) {/*modified for Bug 724272 */
                if (mInternalMemoryUsagePrefence != null) {
                    setInternalMemoryUsageSummary();
                }
            }
            /*Add By SPRD for bug:588863 End*/
        }

        @Override
        public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                final String key) {
            /*Add by SPRD for Bug:533513  2016.03.10 Start */
            if(key.equals(mSignatureEenablePreferenceKey)){
                updateSignatureOnOrOff();
            }
            /*Add by SPRD for Bug:533513  2016.03.10 End */

            /*Add by SPRD for Bug:562203 Encode Type feature  Start */
            if (key.equals(mEncodeTypePreference)) {
                updateEncodeTypePreference();
                Log.d(TAG, "ApplicationSettingsActivity onSharedPreferenceChanged and encodeType has been changed!");
            }
            /*Add by SPRD for Bug:562203 Encode Type feature  End */
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
            if(key.equals(mSignaturePrefKey)) {  //Bug 973101
                updateSignatureSammary();
            /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */
            /* SPRD: Modify for bug 509830 begin */
            }else if (key.equals(mNotificationsEnabledPreferenceKey)) {
                updateNotificationsPreferences();
            } else if (MmsConfig.getKeepOrgSoundVibrate() && key.equals(mRingtonePreferenceKey)) {
                updateSoundSummary(sharedPreferences);
            /* SPRD: Modify for bug 509830 end */
            }
             else if (key.equals(mCtccSmartSmsPrefKey)){
                            boolean CtccsmsEnable =  sharedPreferences.getBoolean(key, true);
                 Log.d(TAG,"onSharedPreferenceChanged mCTCCsmsPrefKey 22"+CtccsmsEnable);
               handleCTCCSmartSmsPreferenceChanged(CtccsmsEnable);
             }
        }

        @Override
        public void onStop() {
            super.onStop();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        /*bug 1060626, begin*/
        public void enableAdvancedScreen(boolean enabled){
            if(null == advancedScreen){
                return;
            }
            if(enabled && !advancedScreen.isEnabled()){
                advancedScreen.setEnabled(true);
                return;
            }

            if(!enabled){
                advancedScreen.setEnabled(false);
            }
        }
        /*bug 1060626, end*/
    }

    //add for bug 610115 start
    @Override
    public void onDestroy(){
        unregisterReceiver(mSimInOutReceiver);
        super.onDestroy();
    }

    private static String LAST_SIM_STATUS = IccCardConstants.INTENT_VALUE_ICC_ABSENT;
    private IntentFilter mSimFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

    private BroadcastReceiver mSimInOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            Log.d("ApplicationSettingsFragment", " The simStatus"+ simStatus);
            if(IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus)) {
                TelephonyManager telephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
                if(topLevel && TelephonyManager.SIM_STATE_ABSENT == telephonyManager.getSimState()){
                    updateAdvancedScreen(false);
                }
            }else if(IccCardConstants.INTENT_VALUE_ICC_READY.equals(simStatus)){
                updateAdvancedScreen(true);
            }
        }
    };
    //add for bug 610115 end
    /*bug 1060626, begin*/
    private void updateAdvancedScreen(boolean enabled){
        Fragment fragment = getFragmentManager().findFragmentById(android.R.id.content);
        if(fragment != null && fragment instanceof ApplicationSettingsFragment){
            ((ApplicationSettingsFragment)fragment).enableAdvancedScreen(enabled);
        }
    }
    /*bug 1060626, end*/
}
