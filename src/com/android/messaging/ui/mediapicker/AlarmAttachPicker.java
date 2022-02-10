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

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.SafeAsyncTask;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import java.util.ArrayList;

import com.android.messaging.util.ContentType;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.MediaStore;
import com.android.messaging.util.UriUtil;
import com.android.messaging.sms.MmsUtils;
import android.media.RingtoneManager;

import com.sprd.messaging.drm.MessagingDrmSession;
import com.sprd.messaging.drm.MessagingDrmHelper;
import android.util.Log;
import com.android.messaging.util.ContentType;
import com.android.messaging.ui.VdataUtils;

import com.android.messaging.util.UiUtils;
import com.android.messaging.R;

import com.android.messaging.util.GlobleUtil;

import android.widget.DatePicker;
import android.widget.TimePicker;
import android.content.Intent;
import java.util.GregorianCalendar;
import java.util.Date;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import java.text.SimpleDateFormat;
import android.widget.TextView;
import android.app.AlertDialog;
import android.view.View;
import com.android.messaging.util.LogUtil;

import android.widget.Toast;
import android.content.res.Configuration;

import android.widget.ImageButton;
/**
 * Wraps around the functionalities to allow the user to pick AudioAttach from the
 * Contacts picker. Instances of this class must be tied to a Fragment which is
 * able to delegate activity result callbacks.
 */
public class AlarmAttachPicker {

    /**
     * An interface for a listener that listens for when a document has been
     * picked.
     */
    public interface AlarmAttachPickerListener {
        /**
         * Called when Contacts is selected from picker. At this point, the file
         * hasn't been actually loaded and staged in the temp directory, so we
         * are passing in a pending MessagePartData, which the consumer should
         * use to display a placeholder image.
         *
         * @param pendingItem
         *            a temporary attachment data for showing the placeholder
         *            state.
         */
        void onAlarmAttachSelected(Date date);
    }

    // The owning fragment.
    private final Fragment mFragment;

    // The listener on the picker events.
    private final AlarmAttachPickerListener mListener;
    private static final String TAG="AlarmAttachPicker";
    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");

    private int mYear=0;
    private int mMonth=0;
    private int mDay=0;
    private int mHour=0;
    private int mMinute=0;

    private Date mDate = null;
    private boolean mTakeDate =false;
    private boolean mTakeTime =false;

    private TextView mDatetv;
    private TextView mTimetv;

    //for bug671490  begin
    private DatePickerDialog mDatePickerDialog;
    private TimePickerDialog mTimePickerDialog;
    //for bug671490  end
    /**
     * Creates a new instance of AudioAttachPicker.
     *
     * @param activity
     *            The activity that owns the picker, or the activity that hosts
     *            the owning fragment.
     */
    public AlarmAttachPicker(final Fragment fragment,
            final AlarmAttachPickerListener listener) {
        mFragment = fragment;
        mListener = listener;
    }

    /**
     * Intent out to open an image/video from document picker.
     */
    public void launchDatePicker(TextView tv) {
        mDatetv=tv;
        //for bug671490  begin
        //DatePickerDialog datePickerDialog;
        /*
        Long timeInMillis=new GregorianCalendar().getTimeInMillis();
        Date date = new Date(timeInMillis);
        datePickerDialog = new DatePickerDialog(mFragment.getContext(), mDateSet, date.getYear()+1900, date.getMonth(), date.getDay());
        Log.d(TAG, "date Year:"+date.getYear()+"] Month:"+date.getMonth()+"] Day:"+date.getDay());
        */
        if(!mTakeDate){
            Long timeInMillis=new GregorianCalendar().getTimeInMillis();
            Date date = new Date(timeInMillis);
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "AlarmAttachPicker pick date year:["+date.getYear()+"] mon:["+date.getMonth()+"] day:["+date.getDate()+"]");

            mDatePickerDialog = new DatePickerDialog(mFragment.getContext(), mDateSet, date.getYear()+1900, date.getMonth(), date.getDate());
        }else{
            mDatePickerDialog = new DatePickerDialog(mFragment.getContext(), mDateSet, mYear+1900, mMonth, mDay);
        }
        mDatePickerDialog.show();
        //for bug671490 end
    }

    public void launchTimePicker(TextView tv) {
        mTimetv=tv;
        //for bug671490  begin
        //TimePickerDialog timePickerDialog;

        if(!mTakeTime){
            Long timeInMillis=new GregorianCalendar().getTimeInMillis();
            Date date = new Date(timeInMillis+60*1000*2);
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "AlarmAttachPicker pick time hour:["+date.getHours()+"] min:["+date.getMinutes()+"]");

            mTimePickerDialog = new TimePickerDialog(mFragment.getContext(), mTimeSet, date.getHours(), date.getMinutes(), true);
        }else{
            mTimePickerDialog = new TimePickerDialog(mFragment.getContext(), mTimeSet, mHour, mMinute, true);
        }
        mTimePickerDialog.show();
        //708889 start
        int toggle_mode_icon = mTimePickerDialog.getContext().getResources().getIdentifier("android:id/toggle_mode", null, null);
         LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "toggle_mode_icon id ["+toggle_mode_icon+"]");
        ImageButton toggle_mode_imagebutton =(ImageButton)mTimePickerDialog.findViewById(toggle_mode_icon);
        if(toggle_mode_imagebutton != null){
            toggle_mode_imagebutton.setVisibility(View.GONE);
        }else{
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "toggle_mode_icon is null ");
        }
        //708889 end
        //for bug671490  end
    }

    DatePickerDialog.OnDateSetListener mDateSet= new DatePickerDialog.OnDateSetListener(){
        @Override
        public void onDateSet(DatePicker datePicker, int year, int monthInYear, int dayInMonth) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "OnDateSetListener pick date year:["+year+"] mon:["+monthInYear+"] day:["+dayInMonth+"]");

            mYear=year-1900;
            mMonth=monthInYear;
            mDay=dayInMonth;
            Long timeInMillis=new GregorianCalendar(year,monthInYear,dayInMonth).getTimeInMillis();
            Date date = new Date(timeInMillis);
            String dateString = sdfDate.format(date);
            mDate=date;
            mDatetv.setText(dateString);
            mTakeDate=true;
        }
    };

    TimePickerDialog.OnTimeSetListener mTimeSet =new TimePickerDialog.OnTimeSetListener(){
        @Override
        public void onTimeSet(TimePicker timePicker, int hour, int Minute) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "OnTimeSetListener pick time hour:["+hour+"] min:["+Minute+"]");

            mHour=hour;
            mMinute=Minute;
            Long timeInMillis=new GregorianCalendar(mYear+1900,mMonth,mDay,mHour,mMinute).getTimeInMillis();
            Date date = new Date(timeInMillis);
            mDate=date;
            String timeString = sdfTime.format(date);
            mTimetv.setText(timeString);
            mTakeTime=true;
        }
    };

  public void deliverConfigurationChanged(Configuration newConfig,TextView dateTv,TextView timeTv){
       Log.d("yaping-9","[AlarmAttachPicker]====deliverConfigurationChanged====newConfig:"+newConfig+";  dateTv:"+dateTv+"  ; timeTv:"+timeTv);
       if(mTimePickerDialog!=null&&mTimePickerDialog.isShowing()){
            closeAllAlarmDialog();
       }
       if(mDatePickerDialog!=null&&mDatePickerDialog.isShowing()){
            closeAllAlarmDialog();
       }
  }

    //add for bug671490  begin
   public void closeAllAlarmDialog(){
        closeAlarmDialog(mTimePickerDialog);
        closeAlarmDialog(mDatePickerDialog);
   }
    private void closeAlarmDialog(AlertDialog dialog){
        if (dialog != null) {
            if (dialog.isShowing()) {
                try{
                    dialog.dismiss();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
            dialog = null;
        }
    }//add for bug671490  end

    public void SetMessageAlarmTime(final boolean set) {
        Date date=null;
        if(set){

            if(!mTakeDate){
                UiUtils.showToastAtBottom(R.string.please_set_date);
                return;
            }
            if(!mTakeTime){
                UiUtils.showToastAtBottom(R.string.please_set_time);
                return;
            }
            Long timeInMillis=new GregorianCalendar().getTimeInMillis();
            Date nowdate = new Date(timeInMillis);
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "SetMessageAlarmTime mYear:["+mYear+"], mMonth:["+mMonth+"], mDay:["+mDay+"], mHour:["+mHour+"], mMinute:["+mMinute+"]");
            date=new Date(mYear, mMonth, mDay, mHour, mMinute);
            if(nowdate.after(date)){
                Toast.makeText(mFragment.getContext(),mFragment.getContext().getString(R.string.donotset_time_before_now), Toast.LENGTH_LONG).show();

                LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "AlarmAttachPicker nowdate.after(date)");
                return;
            }
        }else{
            mTakeDate=false;
            mTakeTime=false;
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "SetMessageAlarmTime clean(date)");
        }
        prepareAlarmAttachForAttachment(date);

    }

    public void ChangeAlarmTime(final Date date) {

        if(date ==null){
            mTakeDate=false;
            mTakeTime=false;
            mYear=0;
            mMonth=0;
            mDay=0;
            mHour=0;
            mMinute=0;
        }else{
            mTakeDate=true;
            mTakeTime=true;
            mYear=date.getYear();
            mMonth=date.getMonth();
            mDay=date.getDate();
            mHour=date.getHours();
            mMinute=date.getMinutes();
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "ChangeAlarmTime mYear:["+mYear+"], mMonth:["+mMonth+"], mDay:["+mDay+"], mHour:["+mHour+"], mMinute:["+mMinute+"]");
        }
    }

    /**
     * Must be called from the fragment/activity's onActivityResult().
     */
    public void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
        if (requestCode == UIIntents.REQUEST_PICK_AUDIO_PICKER
                && resultCode == Activity.RESULT_OK) {
            if (data != null) {

            }
        }

    }

    private void prepareAlarmAttachForAttachment(final Date date) {
        // Notify our listener with a PendingAttachmentData containing the
        // metadata.
        new SafeAsyncTask<Void, Void, String>() {

            protected String doInBackgroundTimed(final Void... params) {
                return "Alarm";
            }

            @Override
            protected void onPostExecute(final String content) {

                mListener.onAlarmAttachSelected(date);
            }
        }.executeOnThreadPool();
    }

}
