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
package com.android.messaging.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.SettingsEx;
import android.text.TextUtils;
import android.telephony.TelephonyManager;
import android.telephony.SmsManager;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.sms.MmsConfig;
import com.sprd.messaging.util.SystemAdapter;

import java.io.File;
import android.Manifest;
import android.telephony.TelephonyManagerEx;

public class RingtoneUtil {
    public static final String TAG = "RingtoneUtil";
    /**
     * Return a ringtone Uri for the string representation passed in. Use the app
     * and system defaults as fallbacks
     *
     * @param ringtoneString is the ringtone to resolve
     * @return the Uri of the ringtone or the fallback ringtone
     */
    /* SPRD: modified for bug 499870 begin */
    public static Uri getNotificationRingtoneUri(String ringtoneString, final int subId) {
        if (ringtoneString == null) {
            // No override specified, fall back to system-wide setting.
            final BuglePrefs prefs = BuglePrefs.getApplicationPrefs();
            final Context context = Factory.get().getApplicationContext();
            final String prefKey = context.getString(R.string.notification_sound_pref_key);
            ringtoneString = prefs.getString(prefKey, null);
        }

        if (!TextUtils.isEmpty(ringtoneString)) {
            // We have set a value, even if it is the default Uri at some point
            return Uri.parse(ringtoneString);
        } else if (ringtoneString == null) {
            // We have no setting specified (== null), so we default to the system default
            return Settings.System.DEFAULT_NOTIFICATION_URI;
        } else {
            // An empty string (== "") here is the result of selecting "None" as the ringtone
            return null;
        }
    }

    private static boolean isAudioFileExists(Context context, Uri uri) {
        /*modified for Bug 788966 start */
//        TelephonyManagerEx telephonyManager = TelephonyManagerEx.from(context);
//        if (telephonyManager != null) {
//            return telephonyManager.isRingtongUriAvailable(uri);
//        }
//        return false;
        /**SPRD: Add for Bug 609127 begin*/
       if (OsUtil.hasStoragePermission()){
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Audio.Media.DATA}, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    if (cursor.moveToFirst()) {
                        File filePath = new File(cursor.getString(column_index));
                        if (filePath.exists()) {
                            LogUtil.d("RingtoneUtil", "filePath exists! column_index = " + column_index);
                            return true;
                        }
                    }
                }
                return false;
            } catch (SQLiteException sqle) {
                LogUtil.e("RingtoneUtil", sqle.toString());
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }
        }
        return false;
        /**SPRD: Add for Bug 609127 end*/
    }

    /**SPRD: Add for Bug 557301 begin*/
    public static Uri getDefaultRintoneUri(Context context) {
//        String notificationUriString = Settings.System.getString(context.getContentResolver(),
//                SettingsEx.SystemEx.DEFAULT_NOTIFICATION);
        String notificationUriString = null;  //weicn
        Uri mDefaultNotificationUri = (notificationUriString != null ? Uri.parse(notificationUriString)
                : null);
        LogUtil.d(TAG, " default uri = " + mDefaultNotificationUri);
        return mDefaultNotificationUri;
    }
    /**SPRD: Add for Bug 557301 end*/

    /*modified for Bug 788966 start */
    public static Uri getDefaultRintoneUri(Context context, final int phoneId) {
        String def = Settings.System.getString(context.getContentResolver(), "default_message" + phoneId);/*SettingEx.SystemEx.DEFAULT_MESSAGE*/
        LogUtil.d(TAG, "getDefaultRintoneUri:def = " + def);
        if (TextUtils.isEmpty(def)) {
            //return Settings.System.DEFAULT_NOTIFICATION_URI;
//            def = Settings.System.getString(context.getContentResolver(),
//                SettingsEx.SystemEx.DEFAULT_NOTIFICATION);
            def = null;  //weicn
        }
        Uri defaultNotificationUri = (def != null ? Uri.parse(def): null);
        LogUtil.d("RingtoneUtil", " default uri = " + defaultNotificationUri);
        return defaultNotificationUri;
    }

}
