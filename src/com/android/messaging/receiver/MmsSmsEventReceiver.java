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

package com.android.messaging.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.android.messaging.util.LogUtil;

import android.os.AsyncTask;
import android.os.Handler;
import android.widget.Toast;
import com.android.messaging.util.OsUtil;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.datamodel.action.MoveAlarmMessageAction;
/**
 * Class that receives event from messaging.
 */
public final class MmsSmsEventReceiver extends BroadcastReceiver {
    private static final String TAG = "MmsSmsEventReceiver";
    private Handler mHandler = new Handler();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        LogUtil.d(TAG, "onReceive");
	 String action = intent.getAction();
        //add bug 842566  start
	 if(action != null && action.equals(MmsUtils.ACTION_TOAST_COPY_MESSAGE_TO_SIM)){//762218 begin
		    final String msg = intent.getStringExtra("msg");
		    if(msg != null){
			 LogUtil.d(TAG,"  Toast.makeText:"+msg);
		        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
		    }
	 }else{//762218 end
              new MmsSmsEventPushTask(context).execute(intent);
	 }
        //add bug 842566  end
    }

    private class MmsSmsEventPushTask extends AsyncTask<Intent, Void, Void> {
        private Context mContext;

        public MmsSmsEventPushTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Intent... intents) {
            Intent intent = intents[0];
            String action = intent.getAction();

            LogUtil.d(TAG, "MmsSmsEventReceiver received:    action  = " + action);

            if (action == null) {
                return null;
            }
            if (action.equals(MmsUtils.NOTIFY_SHOW_MMS_SMS_REPORT_ACTION)) {

                final String report = intent.getStringExtra("report");
                if (report != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            LogUtil.d(TAG, "Toast.makeText report:" + report);

                            Toast.makeText(mContext, report, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }else if(action.equals(MmsUtils.ACTION_SERVICE_ALARM_MESSAGE)){
			    //613227
			    if(OsUtil.isSecondaryUser()){
                            LogUtil.d(TAG," can not send alarm msg in SecondaryUser");
                            return null;
                        }
                        if (!OsUtil.hasRequiredPermissions()) {
                            LogUtil.d(TAG," can not MoveAlarmMessage alarm msg without Permissions");
                            return null;
                        }
			    long alarmTime = intent.getLongExtra("AlarmMessageTime",0);
			    LogUtil.d(TAG,action+" is coming alarmTime:["+alarmTime+"]");
                MoveAlarmMessageAction.MoveAlarmMessage(alarmTime);
			
		}
            return null;
        }
    }
}
