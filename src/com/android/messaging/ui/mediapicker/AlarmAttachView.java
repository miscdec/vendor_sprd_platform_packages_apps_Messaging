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

import android.content.Context;

import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ImageButton;
import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.UiUtils;
import android.util.Log;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.os.Handler;
import android.content.res.Configuration;
/**
 * Hosts an alarm for msg.
 */
public class AlarmAttachView extends FrameLayout {
    
    public interface AlarmAttachViewListener {
       
        void onAlarmDateAttachPickerTouch(TextView tv);
        void onAlarmTimeAttachPickerTouch(TextView tv);
        void onSetMessageAlarmTimeTouch(final boolean set);
        void deliverConfigurationChanged(Configuration newConfig,TextView dateTv,TextView timeTv);
    }

    private View mAlarmView;
    private TextView mHintTextView;
    private AlarmAttachViewListener mListener;
    /*
    private ImageButton mAlarmSetupButton;
    private ImageButton mAlarmCancelButton;
    */
    private TextView mAlarmSetupButton;
    private TextView mAlarmCancelButton;
    private TextView mDate;
    private TextView mTime;
    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");

   
    public AlarmAttachView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

    }

    public void setHostInterface(final AlarmAttachViewListener hostInterface) {
        mListener = hostInterface;
    }

    public void setAlarmData(final Date date) {
        if(date != null){
            String dateString = sdfDate.format(date);
            String timeString = sdfTime.format(date);
            mDate.setText(dateString);
            mTime.setText(timeString);
        }else{
            mDate.setText(getResources().getString(R.string.alarm_message_date) );
            mTime.setText(getResources().getString(R.string.alarm_message_time) );
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAlarmView = findViewById(R.id.alarm_attach_button);
        mHintTextView = (TextView) findViewById(R.id.hint_text);
        mDate=(TextView)findViewById(R.id.alarm_message_date);
        mTime=(TextView)findViewById(R.id.alarm_message_time);

        mDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                mListener.onAlarmDateAttachPickerTouch(mDate);
            }
        });
         mTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                mListener.onAlarmTimeAttachPickerTouch(mTime);
            }
        });
        //mAlarmSetupButton = (ImageButton) findViewById(R.id.alarm_setup);
        mAlarmSetupButton = (TextView) findViewById(R.id.alarm_setup);
        mAlarmSetupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                mListener.onSetMessageAlarmTimeTouch(true);
            }
        });
        //mAlarmCancelButton = (ImageButton) findViewById(R.id.alarm_cancel);
        mAlarmCancelButton = (TextView) findViewById(R.id.alarm_cancel);
       
        mAlarmCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View clickView) {
                mListener.onSetMessageAlarmTimeTouch(false);
            }
        });
    }

    @Override
    public void dispatchConfigurationChanged(Configuration newConfig) {
        super.dispatchConfigurationChanged(newConfig);
        /*add by sprd  for Bug 649312 start */
        Log.d("yaping-9", "[AlarmAttachView]===dispatchConfigurationChanged=====orientation:"+ newConfig.orientation );
        if (newConfig.orientation != -1) {
            mListener.deliverConfigurationChanged(newConfig,mDate,mTime);
        }
        /*add by sprd  for Bug 649312 end */
    }

}
