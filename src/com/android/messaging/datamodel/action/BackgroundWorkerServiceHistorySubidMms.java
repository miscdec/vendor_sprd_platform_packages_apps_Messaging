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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DataModelException;
import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.LoggingTimer;
import com.android.messaging.util.WakeLockHelper;
import com.google.common.annotations.VisibleForTesting;

import java.util.List;

/**
 * Background worker service is an initial example of a background work queue handler
 * Used to actually "send" messages which may take some time and should not block ActionService
 * or UI
 */
public class BackgroundWorkerServiceHistorySubidMms extends BackgroundWorkerService {
    private static final String TAG = "BackgroundWorkerServiceHistorySubidMms";



    
}

