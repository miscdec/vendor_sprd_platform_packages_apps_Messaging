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

package com.android.messaging.sms;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManagerEx;
import android.text.TextUtils;
import android.util.Log;

import androidx.appcompat.mms.CarrierConfigValuesLoader;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.BuglePrefsKeys;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.SprdLogUtil;
import com.android.messaging.util.operater.AbsOperaterFactory;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MMS configuration.
 *
 * This is now a wrapper around the BugleCarrierConfigValuesLoader, which does
 * the actual loading and stores the values in a Bundle. This class provides getter
 * methods for values used in the app, which is easier to use than the raw loader
 * class.
 */
public class MmsConfig {
    private static final String TAG = LogUtil.BUGLE_TAG;

    private static final int DEFAULT_MAX_TEXT_LENGTH = 2000;

    /*
     * Key types
     */
    public static final String KEY_TYPE_INT = "int";
    public static final String KEY_TYPE_BOOL = "bool";
    public static final String KEY_TYPE_STRING = "string";
    public static final String SMS_ENCODE_TYPE = "persist.radio.sms_encode_type";

    private static final Map<String, String> sKeyTypeMap = Maps.newHashMap();
    private static boolean mIsTechain = false;
    private static boolean sCmccSdkEnabled = false;
    private static boolean sCuccSdkEnabled = false;
    private static boolean sCtccSdkEnabled = false;
    private static String mSheapGrowthLimit = "96m";
    private static boolean mNotifyAsSimColor = true;
    private static boolean mOsSupportDelayedSending = true;

    static {
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLED_MMS, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLED_TRANS_ID, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLED_NOTIFY_WAP_MMSC, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ALIAS_ENABLED, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ALLOW_ATTACH_AUDIO, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLE_MULTIPART_SMS, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLE_SMS_DELIVERY_REPORTS,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLE_GROUP_MMS, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_CELL_BROADCAST_APP_LINKS, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLE_MMS_READ_REPORTS, KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ENABLE_MMS_DELIVERY_REPORTS,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_SUPPORT_HTTP_CHARSET_HEADER,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_RECIPIENT_LIMIT, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_HTTP_SOCKET_TIMEOUT, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ALIAS_MIN_CHARS, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_ALIAS_MAX_CHARS, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_SMS_TO_MMS_TEXT_THRESHOLD, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD,
                KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_TEXT_SIZE, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_MAX_SUBJECT_LENGTH, KEY_TYPE_INT);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_UA_PROF_TAG_NAME, KEY_TYPE_STRING);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_HTTP_PARAMS, KEY_TYPE_STRING);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_EMAIL_GATEWAY_NUMBER, KEY_TYPE_STRING);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONFIG_NAI_SUFFIX, KEY_TYPE_STRING);
        // add for sprd 596503
        sKeyTypeMap.put(CarrierConfigValuesLoader.SAVE_ATTACHMENTS_TO_EXTERNAL,
                KEY_TYPE_BOOL);
        //add for sprd 601442 start
        sKeyTypeMap.put(CarrierConfigValuesLoader.FORWARD_MESSAGE_USING_SMIL,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.BEEP_ON_CALL_STATE,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.CONTENT_EDIT_ENABLED,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.IS_CMCC_PARAM,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.INTERNAL_MEMORY_USAGE_ENABLED,
                KEY_TYPE_BOOL);
        sKeyTypeMap.put(CarrierConfigValuesLoader.SMSC_EDITEABLE,
                KEY_TYPE_BOOL);
        //modified for sprd 613681
        sKeyTypeMap.put(CarrierConfigValuesLoader.ENCODETYPE_PREFE_STATUS,
                KEY_TYPE_INT);
         //add for sprd 601442 end
        //bug 740202 begin
        sKeyTypeMap.put(CarrierConfigValuesLoader.UNREAD_SMS_LIGHT_COLOR,
                KEY_TYPE_STRING);
        //bug 740202 end
    }

    // A map that stores all MmsConfigs, one per active subscription. For pre-LMSim, this will
    // contain just one entry with the default self sub id; for LMSim and above, this will contain
    // all active sub ids but the default subscription id - the default subscription id will be
    // resolved to an active sub id during runtime.
    private static final Map<Integer, MmsConfig> sSubIdToMmsConfigMap = Maps.newHashMap();
    // The fallback values
    private static final MmsConfig sFallback =
            new MmsConfig(ParticipantData.DEFAULT_SELF_SUB_ID, new Bundle());

    // Per-subscription configuration values.
    private final Bundle mValues;
    private final int mSubId;
    private static boolean mEnabledSmsValidity=true;
    private static boolean mEnabledMmsValidity=true;
    //489220 begin
    private static boolean mMmsReadReportsEnable = true;
    //489220 end
    private final static String KEY_SP_SINATURE = "k-sp-signature";//Add for Bug:533513
    private final static String KEY_SP_ENCODETYPE = "k-sp-encodetype";//Add for Encode Type feature bug:562203

    public final static String ENCODETYPE_PREFE_STATUS = "encodetype_prefe_status";
    public final static String SMSC_EDITEABLE = "smsc_editable";
    public final static String ORANGE_NUMBER_FILTER = "orange_number_filter";
    public final static String VDF_NUMBER_FILTER = "vdf_number_filter";
    public final static String FORWARD_MESSAGE_USING_SMIL = "forward_message_using_smil";
    public final static String BEEP_ON_CALL_STATE = "beep_oncall_state";
    // sprd :581044 2016-07-21 start
    public final static String IS_CMCC_PARAM = "is_cmcc_param";
    // sprd :581044 2016-07-21 END
    // sprd :586862 2016-08-10 start
    public final static String CONTENT_EDIT_ENABLED = "content_edit_enabled";
    // sprd :586862 2016-08-10 end
    /*Add By SPRD for Bug:588863 Start*/
    public final static String INTERNAL_MEMORY_USAGE_ENABLED = "internal_memory_usage_enabled";
    /*Add By SPRD for Bug:588863 End*/
    public final static String SAVE_ATTACHMENTS_TO_EXTERNAL = "save_attachments_to_external";
    public final static String USING_SIM_IN_SETTINGS = "using_sim_in_settings";
    public final static String SPPORT_REPLACE_SMS = "supportreplacesms";//for bug694631
    private final static String KEY_CTCC_SMARTSMS = "key_ctcc_smartsms";
    private int mMmsSizeLimit = 0;

    /**
     * Retrieves the MmsConfig instance associated with the given {@code subId}
     */
    public static MmsConfig get(final int subId) {
        final int realSubId = PhoneUtils.getDefault().getEffectiveSubId(subId);
        synchronized (sSubIdToMmsConfigMap) {
            final MmsConfig mmsConfig = sSubIdToMmsConfigMap.get(realSubId);
            if (mmsConfig == null) {
                // The subId is no longer valid. Fall back to the default config.
                //add bug 809935 start
              /*  LogUtil.d(LogUtil.BUGLE_TAG, "Get mms config: invalid subId. subId=" + subId
                        + ", real subId=" + realSubId
                        + ", map=" + sSubIdToMmsConfigMap.keySet());*/
                //add bug 809935 end
                //return sFallback;/*modified for Bug 724272 start*/
                final MmsConfig mmsConfigDef = sSubIdToMmsConfigMap.get(ParticipantData.DEFAULT_SELF_SUB_ID);/*modified for Bug 775358 start*/
                if (mmsConfigDef != null) {
                    return mmsConfigDef;/*modified for Bug 775358 end*/
                } else {
                    return sFallback;
                }/*modified for Bug 724272 end*/
            }
            return mmsConfig;
        }
    }

    private MmsConfig(final int subId, final Bundle values) {
        mSubId = subId;
        mValues = values;
        AbsOperaterFactory.CreateProductByType(null).setParameter(this);
    }

    /**
     * Same as load() but doing it using an async thread from SafeAsyncTask thread pool.
     */
    public static void loadAsync() {
        SafeAsyncTask.executeOnThreadPool(new Runnable() {
            @Override
            public void run() {
                load();
            }
        });
    }

    /**
     * Reload the device and per-subscription settings.
     */
    public static synchronized void load() {
        final BugleCarrierConfigValuesLoader loader = Factory.get().getCarrierConfigValuesLoader();
        // Rebuild the entire MmsConfig map.
        sSubIdToMmsConfigMap.clear();
        loader.reset();
        if (OsUtil.isAtLeastL_MR1()) {
            final List<SubscriptionInfo> subInfoRecords =
                    PhoneUtils.getDefault().toLMr1().getActiveSubscriptionInfoList();
           /* if (subInfoRecords == null ||subInfoRecords.size()==0)*/ {/*modified for Bug 724272 start*//*modified by 775358*/
               // LogUtil.w(TAG, "Loading mms config failed: no active SIM");
               // return;
                final Bundle values = loader.get(ParticipantData.DEFAULT_SELF_SUB_ID);
                Log.d(TAG, " load addMmsConfig" + values);
                addMmsConfig(new MmsConfig(ParticipantData.DEFAULT_SELF_SUB_ID, values));
                /*modified for Bug 724272 end*/
            }
            for (SubscriptionInfo subInfoRecord : subInfoRecords) {
                final int subId = subInfoRecord.getSubscriptionId();
                final Bundle values = loader.get(subId);
                Log.d(TAG, " OsUtil.isAtLeastL_MR1 load addMmsConfig" + values);
                addMmsConfig(new MmsConfig(subId, values));
            }
        } else {
            final Bundle values = loader.get(ParticipantData.DEFAULT_SELF_SUB_ID);
            Log.d(TAG, " load addMmsConfig" + values);
            addMmsConfig(new MmsConfig(ParticipantData.DEFAULT_SELF_SUB_ID, values));
        }
    }

    private static void addMmsConfig(MmsConfig mmsConfig) {
//        Assert.isTrue(OsUtil.isAtLeastL_MR1() !=
//                (mmsConfig.mSubId == ParticipantData.DEFAULT_SELF_SUB_ID)); /*deleted for Bug 724272*/
        sSubIdToMmsConfigMap.put(mmsConfig.mSubId, mmsConfig);
    }

    /* Add by SPRD for bug 609520 16/11/28 Start */
    public static void setIsTechain(boolean isTechain) {
        mIsTechain = isTechain;
     }
    public static boolean getIsTechain() {
        return mIsTechain;
     }
    /* Add by SPRD for bug 609520 16/11/28 End */

    public static boolean getCmccSdkEnabled() {
        /*SPRD: 844785 disable mPermissionsPreference for CCSA4 feature @{ */
        if (SystemProperties.get("persist.support.securetest").equals("1")) {
            Log.d(TAG, "persist.support.securetest =1 ");
            return false;
        }
        /* @} */
       return false;//sCmccSdkEnabled;
    }
    public static boolean getCmccSdkEnabledForCmccTest() {
        /*SPRD: 1248798 for cmcc test not send response @{ */

       return sCmccSdkEnabled;
    }
    public static boolean getCuccSdkEnabled() {
       return sCuccSdkEnabled;
    }

    /**
     * @return
     * ui for ctcc, showing smart message by considering carrier config and switch status
     */
    public static boolean getCtccSdkEnabled() {
       return isCtccOp() && getCtccSmartSmsPreference();
    }

    /**
     * @return
     * indicate current carrier.
     * init smart sdk params and show switch button in settings if ctcc project
     */
    public static boolean isCtccOp(){
        //UNISOC: Modify for Bug#1559830
        Log.d(TAG, "CTCC carrier does not test smart sms, so isCtccOp returns false !");
        return false/*sCtccSdkEnabled*/;
    }

    public static void checkSmartSdkEnabled() {
        try {
            String product = SystemProperties.get("ro.carrier");
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "checkSmartSdkEnabled product = " + product);
            if (TextUtils.isEmpty(product)){
                return;
            }
            if (product.toLowerCase().contains("cmcc")) {
                sCmccSdkEnabled = true;
            } else if (product.toLowerCase().contains("cucc")) {
                sCuccSdkEnabled = true;
            } else if (product.toLowerCase().contains("ctcc")) {
                sCtccSdkEnabled = true;
            } else {
                //sCuccSdkEnabled = true;
            }
        } catch (Exception e) {
            LogUtil.e(LogUtil.BUGLE_SMART_TAG, "checkSmartSdkEnabled Error", e);
        }
    }

    //for bug694631 begin
    public boolean getIsSupportReplceSms() {
        return mValues.getBoolean(SPPORT_REPLACE_SMS,false);
    }
    //for bug694631 end
    public int getSmsToMmsTextThreshold() {
        return mValues.getInt(CarrierConfigValuesLoader.CONFIG_SMS_TO_MMS_TEXT_THRESHOLD,
                CarrierConfigValuesLoader.CONFIG_SMS_TO_MMS_TEXT_THRESHOLD_DEFAULT);
    }

    public int getSmsToMmsTextLengthThreshold() {
        return mValues.getInt(CarrierConfigValuesLoader.CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD,
                CarrierConfigValuesLoader.CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD_DEFAULT);
    }

    public boolean enableSelectMmsSize() {
        return mValues.getBoolean(CarrierConfigValuesLoader.SELECT_MMS_SIZE,
                CarrierConfigValuesLoader.SELECT_MMS_SIZE_DEFAULT);
    }

    public int getMaxMessageSize() {
        if (enableSelectMmsSize()) {
            if (mMmsSizeLimit == 0)
                setMmsLimitSize();
            return mMmsSizeLimit * 1024;
        } else {
            return mValues.getInt(CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE,
                               CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE_DEFAULT);
        }
    }

    public void setMmsSizeLimit(int limit) {
        mMmsSizeLimit = limit;
    }

    public void setMmsLimitSize() {
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(Factory.get().getApplicationContext());
        String temp = null;
        int mmsSizeLimit;
        if (sp != null) {
            temp = sp.getString("pref_key_mms_size_limit", "1024");
        }
        if (temp != null && 0 == temp.compareTo("300")) {
            mmsSizeLimit = 300;
        } else if (temp != null && 0 == temp.compareTo("600")) {
            mmsSizeLimit = 600;
        } else {
            mmsSizeLimit = 1024;
        }
        setMmsSizeLimit(mmsSizeLimit);
    }

    //modified for sprd 613681
    public int getEncodePrefeStatus() {
        return mValues.getInt(ENCODETYPE_PREFE_STATUS, 0);/* modifyed for bug 645227 */
    }

    //add new function for empty msg begin

    /**
     * Return the value flag for sending Empty sms and Whether show the dialog or not
     * show : 0x00000001  no show : 0x00010000
     */

    public int getFinalSendEmptyMessageFlag() {
        int value = mValues.getInt(CarrierConfigValuesLoader.SEND_EMPTY_MESSAGE,
                CarrierConfigValuesLoader.SEND_EMPTY_MESSAGE_DEFAULT);
        return value;
    }

    //add new function for empty msg end
    //489220 begin
    public boolean getmMmsReadReportsEnable(){//for 670862
        //for 670862 begin
        mMmsReadReportsEnable = mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ENABLE_MMS_READ_REPORTS,
                CarrierConfigValuesLoader.CONFIG_ENABLE_MMS_READ_REPORTS_DEFAULT);
        //for 670862 end
        return mMmsReadReportsEnable;
    }
    //489220 end

    // add for bug 605280 --begin
    public static boolean getFdnContactFittingEnable(){
        if(!OsUtil.hasPhonePermission()) {
            return false;
        }

        final SubscriptionManager subscriptionManager = SubscriptionManager.from(Factory.get().getApplicationContext());
        List<SubscriptionInfo> subInfoList = subscriptionManager.getActiveSubscriptionInfoList();
        //for no SIM card case begin
        if(subInfoList == null){
            return false;
        }
        //for no SIM card case end
        int size = subInfoList.size();
        if (size==0){
            return false;
        }
        for (int i =0 ;i< size; i++){
            int subid = subInfoList.get(i).getSubscriptionId();
            boolean simEnable = checkFdnEnable(subid);
            android.util.Log.e(TAG,"subid ="+subid + "," +"simEnable ="+simEnable);
            if(simEnable){
                return true;
            }
        }
        return false;
    }

    private static boolean checkFdnEnable(int subid){
        TelephonyManagerEx mTelephonyManagerEx =TelephonyManagerEx.from(Factory.get().getApplicationContext());
        try {
            return mTelephonyManagerEx.getIccFdnEnabled(subid);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    //add for bug 605280 --end

    /**
     * Return the largest MaxMessageSize for any subid
     */
    public static int getMaxMaxMessageSize() {
        int maxMax = 0;
        for (MmsConfig config : sSubIdToMmsConfigMap.values()) {
            maxMax = Math.max(maxMax, config.getMaxMessageSize());
        }
        return maxMax > 0 ? maxMax : sFallback.getMaxMessageSize();
    }

    public boolean getTransIdEnabled() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ENABLED_TRANS_ID,
                CarrierConfigValuesLoader.CONFIG_ENABLED_TRANS_ID_DEFAULT);
    }

    public String getEmailGateway() {
        return mValues.getString(CarrierConfigValuesLoader.CONFIG_EMAIL_GATEWAY_NUMBER,
                CarrierConfigValuesLoader.CONFIG_EMAIL_GATEWAY_NUMBER_DEFAULT);
    }

    public int getMaxImageHeight() {
        return mValues.getInt(CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT,
                CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT_DEFAULT);
    }

    public int getMaxImageWidth() {
        return mValues.getInt(CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH,
                CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH_DEFAULT);
    }

    private static final int MAX_RECIPIENT_LIMIT = 50;
    public int getRecipientLimit() {
        int limit = mValues.getInt(CarrierConfigValuesLoader.CONFIG_RECIPIENT_LIMIT,
                CarrierConfigValuesLoader.CONFIG_RECIPIENT_LIMIT_DEFAULT);
        if(limit > MAX_RECIPIENT_LIMIT) {
            limit = MAX_RECIPIENT_LIMIT;
        }
        return limit < 0 ? MAX_RECIPIENT_LIMIT : limit;
    }

    public int getMaxTextLimit() {
        final int max = mValues.getInt(CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_TEXT_SIZE,
                CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_TEXT_SIZE_DEFAULT);
        return max > -1 ? max : DEFAULT_MAX_TEXT_LENGTH;
    }

    public boolean getMultipartSmsEnabled() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ENABLE_MULTIPART_SMS,
                CarrierConfigValuesLoader.CONFIG_ENABLE_MULTIPART_SMS_DEFAULT);
    }

    public boolean getSendMultipartSmsAsSeparateMessages() {
        return mValues.getBoolean(
                CarrierConfigValuesLoader.CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES,
                CarrierConfigValuesLoader.CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES_DEFAULT);
    }

    public boolean getSMSDeliveryReportsEnabled() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ENABLE_SMS_DELIVERY_REPORTS,
                CarrierConfigValuesLoader.CONFIG_ENABLE_SMS_DELIVERY_REPORTS_DEFAULT);
    }

    public boolean getNotifyWapMMSC() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ENABLED_NOTIFY_WAP_MMSC,
                CarrierConfigValuesLoader.CONFIG_ENABLED_NOTIFY_WAP_MMSC_DEFAULT);
    }

    public boolean isAliasEnabled() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ALIAS_ENABLED,
                CarrierConfigValuesLoader.CONFIG_ALIAS_ENABLED_DEFAULT);
    }

    public int getAliasMinChars() {
        return mValues.getInt(CarrierConfigValuesLoader.CONFIG_ALIAS_MIN_CHARS,
                CarrierConfigValuesLoader.CONFIG_ALIAS_MIN_CHARS_DEFAULT);
    }

    //bug 740202 begin
    public String getUnreadSmsLightColor() {

        if (Boolean.valueOf(com.android.messaging.sms.SystemProperties.get("ro.build.blade_v8.prj"))) {
            return "green";
        }

        return mValues.getString(CarrierConfigValuesLoader.UNREAD_SMS_LIGHT_COLOR,
                CarrierConfigValuesLoader.UNREAD_SMS_LIGHT_COLOR_DEFAULT);
    }
    //bug 740202 end

    public int getAliasMaxChars() {
        return mValues.getInt(CarrierConfigValuesLoader.CONFIG_ALIAS_MAX_CHARS,
                CarrierConfigValuesLoader.CONFIG_ALIAS_MAX_CHARS_DEFAULT);
    }

    public boolean getAllowAttachAudio() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ALLOW_ATTACH_AUDIO,
                CarrierConfigValuesLoader.CONFIG_ALLOW_ATTACH_AUDIO_DEFAULT);
    }

    public int getMaxSubjectLength() {
        return mValues.getInt(CarrierConfigValuesLoader.CONFIG_MAX_SUBJECT_LENGTH,
                CarrierConfigValuesLoader.CONFIG_MAX_SUBJECT_LENGTH_DEFAULT);
    }

    public boolean getGroupMmsEnabled() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_ENABLE_GROUP_MMS,
                CarrierConfigValuesLoader.CONFIG_ENABLE_GROUP_MMS_DEFAULT);
    }

    public boolean getSupportMmsContentDisposition() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION,
                CarrierConfigValuesLoader.CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION_DEFAULT);
    }

    public boolean getShowCellBroadcast() {
        return mValues.getBoolean(CarrierConfigValuesLoader.CONFIG_CELL_BROADCAST_APP_LINKS,
                CarrierConfigValuesLoader.CONFIG_CELL_BROADCAST_APP_LINKS_DEFAULT);
    }

    // sprd for smsc begin
    public boolean getSmscShowEnabled() {
        return true;
    }

    public boolean getSmscEditable() {
        return mValues.getBoolean(SMSC_EDITEABLE,
                false);//modify for bug 912733
    }
    // sprd for smsc end

    // sprd for orange --begin
    public boolean getNumberFilterEnable() {
        return mValues.getBoolean(ORANGE_NUMBER_FILTER,
            false);
    }
    // sprd for orange --end

    //sprd for vdf --begin
    public boolean getNumberVdfFilterEnable() {
        return mValues.getBoolean(VDF_NUMBER_FILTER,
            false);
    }
    //sprd for vdf -- begin
    /*modify by SPRD for Bug:533513  2016.03.10 Start */
    /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. begin */
    public static boolean getSignatureEnabled() {
        //return true;
        final BuglePrefs prefs = Factory.get().getApplicationPrefs();
        return prefs.getBoolean(KEY_SP_SINATURE,false);
    }
    /*SPRD: add for Bug 489231--new feature,enable signature text append to a message. end */

    public static void setSignatureEnable(boolean signatureEnable) {
         final BuglePrefs prefs = Factory.get().getApplicationPrefs();
         prefs.putBoolean(KEY_SP_SINATURE, signatureEnable);
    }
    /*modify by SPRD for Bug:533513  2016.03.10 End */

    /*Add by SPRD for Bug:562203 Encode Type feature  Start */
    public static String getEncodeType() {
        final BuglePrefs prefs = Factory.get().getApplicationPrefs();
        return prefs.getString(KEY_SP_ENCODETYPE, "-1");
    }

    public static void setEncodeType(String encode) {
        final BuglePrefs prefs = Factory.get().getApplicationPrefs();
        prefs.putString(KEY_SP_ENCODETYPE, encode);
    }
    public static void  setCtccSmartSmsPreference(boolean on){
                  final BuglePrefs prefs = Factory.get().getApplicationPrefs();
         prefs.putBoolean(KEY_CTCC_SMARTSMS, on);
    }

    public static boolean  getCtccSmartSmsPreference(){
        final BuglePrefs prefs = Factory.get().getApplicationPrefs();
        return prefs.getBoolean(KEY_CTCC_SMARTSMS,DEFAULT_CTCC_SWITCH_STATUS);
    }
    /*Add by SPRD for Bug:562203 Encode Type feature  End */

    public Object getValue(final String key) {
        return mValues.get(key);
    }

    public Set<String> keySet() {
        return mValues.keySet();
    }

    public static String getKeyType(final String key) {
        return sKeyTypeMap.get(key);
    }

    public void update(final String type, final String key, final String value) {
        BugleCarrierConfigValuesLoader.update(mValues, type, key, value);
    }

    // sprd :581044 2016-07-21 start
    public boolean isCmcc() {
        return mValues.getBoolean(IS_CMCC_PARAM, false);
    }
    // sprd :581044 2016-07-21 end

    // sprd :586862 2016-08-10 start
    public boolean getContentEditEnabled(){
        return mValues.getBoolean(CONTENT_EDIT_ENABLED, true);
    }
    // sprd :586862 2016-08-10 end

    // sprd :584098 2016-08-10 start
    public static boolean isFilterEmail() {
        return true;
    }
    // sprd :584098 2016-08-10 end

    /* SPRD: Modify for bug 509830 begin */
    public static boolean getKeepOrgSoundVibrate() {
         /*control the value by SystemProperties  for Bug 847221 */
         boolean keepOrgSoundVibrate = SystemProperties.getBoolean("ro.config.google.sound", false);
         Log.d(TAG, "getKeepOrgSoundVibrate : " + keepOrgSoundVibrate);
         return keepOrgSoundVibrate;
    }
    /* SPRD: Modify for bug 509830 end */

    /*Add By SPRD for Bug:588863 Start*/
    public boolean getInternalMemoryUsageEnabled(){
        return mValues.getBoolean(INTERNAL_MEMORY_USAGE_ENABLED, false);
    }
    /*Add By SPRD for Bug:588863 End*/

    public boolean getUsingSimInSettingsEnabled() {
        return mValues.getBoolean(USING_SIM_IN_SETTINGS, false);
    }

    public boolean getSMSRetryTimesEnabled() {
        return mValues
                .getBoolean(
                        CarrierConfigValuesLoader.CONFIG_ENABLE_SMS_RETRY_TIMES,
                        CarrierConfigValuesLoader.CONFIG_ENABLE_SMS_RETRY_TIMES_DEFAULT);
    }

    /**
     * Return the largest MaxTxtFileSize for any subid
     */
    public static int getMaxMaxTxtFileSize() {
        int maxMax = 0;
        for (MmsConfig config : sSubIdToMmsConfigMap.values()) {
            maxMax = Math.max(maxMax, config.getMaxTxtFileSize());
        }
        return maxMax > 0 ? maxMax : sFallback.getMaxTxtFileSize();
    }

    public int getMaxTxtFileSize() {
        return mValues.getInt(
                CarrierConfigValuesLoader.CONFIG_MAX_TXT_FILE_SIZE,
                CarrierConfigValuesLoader.CONFIG_MAX_TXT_FILE_SIZE_DEFAULT);
    }

    public static boolean getValiditySmsEnabled() {
        return mEnabledSmsValidity;
    }

    public static boolean getValidityMmsEnabled() {
        return mEnabledMmsValidity;
    }

    /* And by SPRD for Bug:530742 2016.02.02 Start */
    public int getSharedImageLimit() {
        final int limit = mValues.getInt(CarrierConfigValuesLoader.CONFIG_SHARED_IMAGE_LIMIT,
                CarrierConfigValuesLoader.CONFIG_SHARED_IMAGE_LIMIT_DEFAULT);
        return limit < 0 ? Integer.MAX_VALUE : limit;
    }
    /* And by SPRD for Bug:530742 2016.02.02 Start */

    /* Sprd add for sms merge forward begin */
    public int getSMSMergeForwardMaxItems() {
        return mValues
                .getInt(
                        CarrierConfigValuesLoader.CONFIG_SMS_MERGE_FORWARD_MAX_TIMES,
                        CarrierConfigValuesLoader.CONFIG_SMS_MERGE_FORWARD_MAX_DEFAULT);
    }

    public int getSMSMergeForwardMinItems() {
        return mValues
                .getInt(
                        CarrierConfigValuesLoader.CONFIG_SMS_MERGE_FORWARD_MIN_TIMES,
                        CarrierConfigValuesLoader.CONFIG_SMS_MERGE_FORWARD_MIN_DEFAULT);
    }
    /* Sprd add for sms merge forward end */

    // sprd 554003 start 2016/4/21
    public static HashMap<String, String> mHashmap = new HashMap<String, String>();

    public void setSmsModemStorage(String szSubId, String smsModemStorage) {
        mHashmap.put(szSubId, smsModemStorage);
    }

    public String getSmsModemStorage(String szSubid) {
        if (mHashmap.containsKey(szSubid)) {
            return mHashmap.get(szSubid);
        } else {
            return "";
        }
    }
    // sprd 554003 end 2016/4/21

    public static boolean enableSmsWapFeature() {
        return true;
    }

    public static void setSmsWapPreference(int subId, boolean on) {
        SprdLogUtil.dump("setSmsWapPreference...subId", subId, "enabled", on);
        final BuglePrefs prefs = Factory.get().getSubscriptionPrefs(subId);
        prefs.putBoolean(BuglePrefsKeys.KEY_SMS_WAP_PREF, on);
    }

    public static boolean isSmsWapEnabled(int subId) {
        final BuglePrefs prefs = Factory.get().getSubscriptionPrefs(subId);
        boolean enabled = prefs.getBoolean(BuglePrefsKeys.KEY_SMS_WAP_PREF, true);
        SprdLogUtil.dump("isSmsWapEnabled...subId", subId, "enabled", enabled);
        return enabled;
    }
 
    public void setValues(Bundle b) {
        if(b != null && mValues != null) {
            mValues.putAll(b);
        }
    }

    public boolean getSaveAttachmentsToExternal(){
        if (mValues!=null)
            return mValues.getBoolean(SAVE_ATTACHMENTS_TO_EXTERNAL, false);
        return false;
    }
    public static void setGrowthLimit(){
        mSheapGrowthLimit =SystemProperties.get("dalvik.vm.heapgrowthlimit");
    }
    public static String getGrowthLimit(){
        return mSheapGrowthLimit;
    }

    public static boolean notifyAsSimColor() {
        return mNotifyAsSimColor;
    }

    public static boolean osSupportDelayedSending() {
        return mOsSupportDelayedSending;
    }

    public static boolean supportSmartSdk(){
        return getCmccSdkEnabled() || getCuccSdkEnabled() || getCtccSdkEnabled();
    }

    private static final boolean DEFAULT_CTCC_SWITCH_STATUS = true;

    private static boolean currentSwitchStatus = DEFAULT_CTCC_SWITCH_STATUS;

    public static boolean isSwitchChanged(){
        if(isCtccOp() && currentSwitchStatus != getCtccSmartSmsPreference()){
            currentSwitchStatus = getCtccSmartSmsPreference();
            return true;
        }
        return false;
    }
}
