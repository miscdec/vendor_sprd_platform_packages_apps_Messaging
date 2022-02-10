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

package com.android.messaging.ui.mediapicker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.util.OsUtil;
import android.provider.ContactsContract;
import android.content.Intent;
import android.app.Activity;
import java.util.Date;
import com.android.messaging.util.LogUtil;
import android.content.res.Configuration;
/**
 * Chooser which allows the user to set Alarm
 */
class AlarmAttachMediaChooser extends MediaChooser implements
        AlarmAttachView.AlarmAttachViewListener {
    private View mEnabledView;
    private View mMissingPermissionView;

    AlarmAttachMediaChooser(final MediaPicker mediaPicker) {
        super(mediaPicker);
    }

    @Override
    public int getSupportedMediaTypes() {
        return MediaPicker.MEDIA_TYPE_ALARM;
    }

    @Override
    public int[] getIconResource() {
        return new int[] {R.drawable.ic_attach_alarm_light, R.drawable.ic_attach_alarm_dark};
    }

    @Override
    public int getIconDescriptionResource() {
        return R.string.attach_alarm;
    }

    public void onAlarmDateAttachPickerTouch(TextView tv) {
        mMediaPicker.launchAlarmDateAttachPicker(tv);
    }

    public void onAlarmTimeAttachPickerTouch(TextView tv) {
        mMediaPicker.launchAlarmTimeAttachPicker(tv);
    }

    public void onSetMessageAlarmTimeTouch(final boolean set) {
        mMediaPicker.PickerSetMessageAlarmTime(set);
    }

    public void deliverConfigurationChanged(Configuration newConfig,TextView dateTv,TextView timeTv){
        mMediaPicker.deliverConfigurationChanged(newConfig,dateTv,timeTv);
    }

    @Override
    protected View createView(final ViewGroup container) {
        final LayoutInflater inflater = getLayoutInflater();
        final AlarmAttachView view = (AlarmAttachView) inflater.inflate(
                R.layout.mediapicker_alarm_chooser,
                container /* root */, false /* attachToRoot */);
        mEnabledView = view.findViewById(R.id.mediapicker_enabled);
        mMissingPermissionView = view
                .findViewById(R.id.missing_permission_view);

        view.setHostInterface(this);
        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "AlarmAttachMediaChooser  createView");
        if(mMediaPicker!=null){
            mMediaPicker.ChangeMessageAlarmTime(mMediaPicker.getDraftMessageDataModel().getData().getAlarmDate());
        }
        view.setAlarmData(mMediaPicker.getDraftMessageDataModel().getData().getAlarmDate());
        return view;
    }

    @Override
    int getActionBarTitleResId() {
        return R.string.attach_alarm;
    }

    @Override
    protected void setSelected(final boolean selected) {
        super.setSelected(selected);
        
    }


    @Override
    protected void onRequestPermissionsResult(final int requestCode,
            final String permissions[], final int[] grantResults) {
    }
}
