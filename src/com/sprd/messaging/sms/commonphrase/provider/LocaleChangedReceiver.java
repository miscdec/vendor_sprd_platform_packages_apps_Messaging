/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.sprd.messaging.sms.commonphrase.provider;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IContentProvider;

/**
 * Locale change intent receiver that invokes {@link PhaserProvider#onLocaleChanged} to update
 * the database for the new locale.
 */
 //add this class for bug708941 begin
public class LocaleChangedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        android.util.Log.d("PhaserProvider","[LocaleChangeReceiver]===onReceive====action:"+intent.getAction());
        ContentResolver cr = context.getContentResolver();
        IContentProvider iprovider =cr.acquireProvider(PhaserProvider.AUTHORITY);
        ContentProvider provider = ContentProvider.coerceToLocalContentProvider(iprovider);
        if (provider instanceof PhaserProvider) {
              ((PhaserProvider)provider).onLocaleChanged();
        }
        cr.releaseProvider(iprovider);
    }
}
//add this class for bug708941 end