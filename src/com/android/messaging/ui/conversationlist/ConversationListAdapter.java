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

package com.android.messaging.ui.conversationlist;

import android.content.Context;
import android.database.Cursor;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.messaging.R;
import com.android.messaging.ui.CursorRecyclerAdapter;
import com.android.messaging.ui.conversationlist.ConversationListItemView.HostInterface;
import com.android.messaging.util.LogUtil;
import com.sprd.messaging.smart.SmartSdkUtils;

/**
 * Provides an interface to expose Conversation List Cursor data to a UI widget like a ListView.
 */
public class ConversationListAdapter
        extends CursorRecyclerAdapter<ConversationListAdapter.ConversationListViewHolder>{

    private final ConversationListItemView.HostInterface mClivHostInterface;

    public ConversationListAdapter(final Context context, final Cursor cursor,
            final ConversationListItemView.HostInterface clivHostInterface) {
        super(context, cursor, 0);
        mClivHostInterface = clivHostInterface;
        setHasStableIds(true);
    }

    /**
     * @see com.android.messaging.ui.CursorRecyclerAdapter#bindViewHolder(
     * androidx.recyclerview.widget.RecyclerView.ViewHolder, android.content.Context,
     * android.database.Cursor)
     */
    @Override
    public void bindViewHolder(final ConversationListViewHolder holder, final Context context,
            final Cursor cursor) {
        final ConversationListItemView conversationListItemView = holder.mView;
        /*sprd, begin*/
        conversationListItemView.bind(cursor, mClivHostInterface, this);
        //conversationListItemView.bind(cursor, mClivHostInterface);
        /*sprd, end*/
    }

    @Override
    public ConversationListViewHolder createViewHolder(final Context context,
            final ViewGroup parent, final int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        final ConversationListItemView itemView =
                (ConversationListItemView) layoutInflater.inflate(
                        R.layout.conversation_list_item_view, null);
        return new ConversationListViewHolder(itemView);
    }

    /**
     * ViewHolder that holds a ConversationListItemView.
     */
    public static class ConversationListViewHolder extends RecyclerView.ViewHolder {
        final ConversationListItemView mView;

        public ConversationListViewHolder(final ConversationListItemView itemView) {
            super(itemView);
            mView = itemView;
        }
    }

    /*smart message, begin*/
    public void refreshListItem(int position) {
        if(!mClivHostInterface.isComputingLayout() && !mScrolling && isVisiblePosition(position)){
            LogUtil.d(LogUtil.BUGLE_SMART_TAG, "callback, refresh single item pos:" + position);
            notifyItemChanged(position);
        }
    }

    private int firstPos;
    private int lastPos;

    public void setVisiblePosition(int first,int last){
        this.firstPos = first;
        this.lastPos = last;
    }

    private boolean isVisiblePosition(int position){
        return position >= firstPos && position <= lastPos;
    }
    /*smart message, end*/

    /*sprd, begin*/
    private boolean mScrolling = false;

    public void setScrolling(boolean scrolling){
        this.mScrolling = scrolling;
    }

    public boolean isScrolling(){
        return mScrolling;
    }
    /*sprd, end*/
}
