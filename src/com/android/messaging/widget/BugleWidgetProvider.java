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

package com.android.messaging.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.widget.RemoteViews;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;
import android.util.Log;
import java.util.concurrent.atomic.AtomicInteger;

public class BugleWidgetProvider extends BaseWidgetProvider {
    public static final String ACTION_NOTIFY_CONVERSATIONS_CHANGED =
            "com.android.Bugle.intent.action.ACTION_NOTIFY_CONVERSATIONS_CHANGED";

    public static final int WIDGET_NEW_CONVERSATION_REQUEST_CODE = 986;
    /* Add by SPRD for optimization broadcast 2016.09.18 Start */
    public static final String BUGLE_WIDGET_CREATE = "bugle_widget_create";
    public static final String WIDGET_STATE = "widget_state";
    private SharedPreferences sp = Factory.get().getApplicationContext().getSharedPreferences(WIDGET_STATE, Context.MODE_PRIVATE);
    /* Add by SPRD for optimization broadcast 2016.09.18 End */

    //Bug 888919 begin
    private static AtomicInteger caller_count = new AtomicInteger(0);
    private static volatile long caller_time = 0;
    //Bug 888919 end
    /**
     * Update the widget appWidgetId
     */
    @Override
    protected void updateWidget(final Context context, final int appWidgetId) {
        if (OsUtil.hasRequiredPermissions()) {
            SafeAsyncTask.executeOnThreadPool(new Runnable() {
                @Override
                public void run() {
                    rebuildWidget(context, appWidgetId);
                }
            });
        } else {
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId,
                    UiUtils.getWidgetMissingPermissionView(context));
        }
    }
    /* Add by SPRD for optimization broadcast 2016.09.18 Start */
    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Editor editor = sp.edit();
        editor.putBoolean(BUGLE_WIDGET_CREATE, true);
        editor.commit();
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        Editor editor = sp.edit();
        editor.putBoolean(BUGLE_WIDGET_CREATE, false);
        editor.commit();
    }
    /* Add by SPRD for optimization broadcast 2016.09.18 End */

    @Override
    protected String getAction() {
        return ACTION_NOTIFY_CONVERSATIONS_CHANGED;
    }

    @Override
    protected int getListId() {
        return R.id.conversation_list;
    }

    public static void rebuildWidget(final Context context, final int appWidgetId) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "BugleWidgetProvider.rebuildWidget appWidgetId: " + appWidgetId);
        }
        final RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
                R.layout.widget_conversation_list);
        PendingIntent clickIntent;

        // Launch an intent to avoid ANRs
        final Intent intent = new Intent(context, WidgetConversationListService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        remoteViews.setRemoteAdapter(appWidgetId, R.id.conversation_list, intent);

        remoteViews.setTextViewText(R.id.widget_label, context.getString(R.string.app_name));

        // Open Bugle's app conversation list when click on header
        clickIntent = UIIntents.get().getWidgetPendingIntentForConversationListActivity(context);
        remoteViews.setOnClickPendingIntent(R.id.widget_header, clickIntent);

        // On click intent for Compose
        clickIntent = UIIntents.get().getWidgetPendingIntentForConversationActivity(context,
                null /*conversationId*/, WIDGET_NEW_CONVERSATION_REQUEST_CODE);
        remoteViews.setOnClickPendingIntent(R.id.widget_compose, clickIntent);

        // On click intent for Conversation
        // Note: the template intent has to be a "naked" intent without any extras. It turns out
        // that if the template intent does have extras, those particular extras won't get
        // replaced by the fill-in intent on each list item.
        clickIntent = UIIntents.get().getWidgetPendingIntentForConversationActivity(context,
                null /*conversationId*/, WIDGET_CONVERSATION_REQUEST_CODE);
        remoteViews.setPendingIntentTemplate(R.id.conversation_list, clickIntent);

        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, remoteViews);
    }

    /*
     * notifyDatasetChanged call when the conversation list changes so the Bugle widget will
     * update and reflect the changes
     */
    public static void notifyConversationListChanged(final Context context) {
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "notifyConversationListChanged");
        }
        //Bug 888919 begin
        long ct = System.currentTimeMillis();
        if(caller_count.get() == 0){
            caller_time = ct;
        }
        caller_count.getAndIncrement();
        if((ct - caller_time) < 80 && caller_count.get() >= 3){
            caller_count.set(0);
            Log.e("Broadcast-too-much","notifyConversationListChanged",new Exception());
        }else if((ct - caller_time) > 80){
            Log.d("notifyConversationListChanged","clear count");
            caller_count.set(0);
        }
        //Bug 888919 end
        final Intent intent = new Intent(ACTION_NOTIFY_CONVERSATIONS_CHANGED);
        intent.setClassName("com.android.messaging","com.android.messaging.widget.BugleWidgetProvider"); //Bug966368
        context.sendBroadcast(intent);
    }
}
