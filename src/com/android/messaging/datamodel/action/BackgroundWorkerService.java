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

package com.android.messaging.datamodel.action;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.core.app.JobIntentService;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DataModelException;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.sms.MmsUtils;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.LoggingTimer;
import com.google.common.annotations.VisibleForTesting;

import java.util.List;

/**
 * Background worker service is an initial example of a background work queue handler
 * Used to actually "send" messages which may take some time and should not block ActionService
 * or UI
 */
public class BackgroundWorkerService extends JobIntentService {
    private static final String TAG = LogUtil.BUGLE_DATAMODEL_TAG;
    protected static final boolean VERBOSE = false;

    /**
     * Unique job ID for this service.
     */
    public static final int JOB_ID = 1001;

    protected final ActionService mHost;

    public BackgroundWorkerService() {
        super();
        mHost = DataModel.get().getActionService();
    }

    /**
     * Queue a list of requests from action service to this worker
     */
    public static void queueBackgroundWork(final List<Action> actions) {
        for (final Action action : actions) {
            startServiceWithAction(action, 0);
        }
    }

    // ops
    @VisibleForTesting
    protected static final int OP_PROCESS_REQUEST = 400;
    //bug 633234 : six queues begin
    // ops
    @VisibleForTesting
    protected static final int OP_PROCESS_REQUEST_SIM1 = 401;
    // ops
    @VisibleForTesting
    protected static final int OP_PROCESS_REQUEST_SIM2 = 402;
    // ops
    @VisibleForTesting
    protected static final int OP_PROCESS_REQUEST_SIM1MMS = 403;
    // ops
    @VisibleForTesting
    protected static final int OP_PROCESS_REQUEST_SIM2MMS = 404;
    // ops
    @VisibleForTesting
    protected static final int OP_PROCESS_REQUEST_MMS = 405;
    //bug 633234 : six queues end
    // extras
    @VisibleForTesting
    protected static final String EXTRA_OP_CODE = "op";
    @VisibleForTesting
    protected static final String EXTRA_ACTION = "action";
    @VisibleForTesting
    protected static final String EXTRA_ATTEMPT = "retry_attempt";

    /**
     * Queue action intent to the BackgroundWorkerService after acquiring wake lock
     */
    protected static void startServiceWithAction(final Action action,
            final int retryCount) {
        final Intent intent = new Intent();
        intent.putExtra(EXTRA_ACTION, action);
        intent.putExtra(EXTRA_ATTEMPT, retryCount);
        //bug 633234 : six queues begin
        final Context context = Factory.get().getApplicationContext();
        int op_process_request=0;
        int subId=-1;
        int protocol=-1;
        if(action instanceof SendMessageAction){
            LogUtil.d(TAG, "this action is SendMessageAction ");
           SendMessageAction tSendMessageAction=(SendMessageAction)action;
            subId=tSendMessageAction.getSubId();
            protocol=tSendMessageAction.getProtocol();
            int slotId=MmsUtils.getSlotId(subId);
            LogUtil.d(TAG, "subId:["+subId+"] slotId:["+slotId+"] protocol:["+protocol+"] ");
            switch(slotId){
                case 0:
                    if(protocol == MessageData.PROTOCOL_SMS){
                        intent.setClass(context, BackgroundWorkerServiceSim1.class);
                        op_process_request=OP_PROCESS_REQUEST_SIM1;
                    }else{
                        intent.setClass(context, BackgroundWorkerServiceSim1Mms.class);
                        op_process_request=OP_PROCESS_REQUEST_SIM1MMS;
                    }
                    break;
                case 1:
                    if(protocol == MessageData.PROTOCOL_SMS){
                        intent.setClass(context, BackgroundWorkerServiceSim2.class);
                        op_process_request=OP_PROCESS_REQUEST_SIM2;
                    }else{
                        intent.setClass(context, BackgroundWorkerServiceSim2Mms.class);
                        op_process_request=OP_PROCESS_REQUEST_SIM2MMS;
                    }
                    break;
                default:
                    if(protocol == MessageData.PROTOCOL_SMS){
                        intent.setClass(context, BackgroundWorkerService.class);
                        op_process_request=OP_PROCESS_REQUEST;
                    }else{
                        intent.setClass(context, BackgroundWorkerServiceHistorySubidMms.class);
                        op_process_request=OP_PROCESS_REQUEST_MMS;
                    }
                    break;
            }
        }else{
            intent.setClass(context, BackgroundWorkerService.class);
            op_process_request=OP_PROCESS_REQUEST;
        }
        startServiceWithIntent(op_process_request, intent);
		//bug 633234 : six queues end
    }

    /**
     * Queue intent to the BackgroundWorkerService.
     */
    private static void startServiceWithIntent(final int opcode, final Intent intent) {
        final Context context = Factory.get().getApplicationContext();

        intent.setClass(context, BackgroundWorkerService.class);
        intent.putExtra(EXTRA_OP_CODE, opcode);

        enqueueWork(context, intent);
    }

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, BackgroundWorkerService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(final Intent intent) {
        if (intent == null) {
            // Shouldn't happen but sometimes does following another crash.
            LogUtil.w(TAG, "BackgroundWorkerService.onHandleIntent: Called with null intent");
            return;
        }
        final int opcode = intent.getIntExtra(EXTRA_OP_CODE, 0);
        LogUtil.d(TAG, "onHandleIntent opcode:"+opcode);

        switch(opcode) {
            case OP_PROCESS_REQUEST:
            case OP_PROCESS_REQUEST_MMS:
            case OP_PROCESS_REQUEST_SIM1:
            case OP_PROCESS_REQUEST_SIM2:
            case OP_PROCESS_REQUEST_SIM1MMS:
            case OP_PROCESS_REQUEST_SIM2MMS:
                final Action action = intent.getParcelableExtra(EXTRA_ACTION);
                final int attempt = intent.getIntExtra(EXTRA_ATTEMPT, -1);
                doBackgroundWork(action, attempt);
                break;

            default:
                LogUtil.w(TAG, "Unrecognized opcode in BackgroundWorkerService " + opcode);
                throw new RuntimeException("Unrecognized opcode in BackgroundWorkerService");
        }
    }

    /**
     * Local execution of background work for action on ActionService thread
     */
    private void doBackgroundWork(final Action action, final int attempt) {
        action.markBackgroundWorkStarting();
        Bundle response = null;
        try {
            final LoggingTimer timer = new LoggingTimer(
                    TAG, action.getClass().getSimpleName() + "#doBackgroundWork");
            timer.start();

            response = action.doBackgroundWork();

            timer.stopAndLog();
            action.markBackgroundCompletionQueued();
            mHost.handleResponseFromBackgroundWorker(action, response);
        } catch (final Exception exception) {
            final boolean retry = false;
            LogUtil.e(TAG, "Error in background worker", exception);
            if (!(exception instanceof DataModelException)) {
                // DataModelException is expected (sort-of) and handled in handleFailureFromWorker
                // below, but other exceptions should crash ENG builds
                Assert.fail("Unexpected error in background worker - abort");
            }
            if (retry) {
                action.markBackgroundWorkQueued();
                startServiceWithAction(action, attempt + 1);
            } else {
                action.markBackgroundCompletionQueued();
                mHost.handleFailureFromBackgroundWorker(action, exception);
            }
        }
    }
}
