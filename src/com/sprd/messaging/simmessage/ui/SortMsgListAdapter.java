//by sprd
package com.sprd.messaging.simmessage.ui;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.ui.CursorRecyclerAdapter;

/**
 * Provides an interface to expose Message List Cursor data to a UI widget like a ListView.
 */
public class SortMsgListAdapter extends CursorRecyclerAdapter<SortMsgListAdapter.SortMsgListViewHolder> {

    private final SortMsgListItemView.HostInterface mClivHostInterface;

    public SortMsgListAdapter(final Context context, final Cursor cursor,
                              final SortMsgListItemView.HostInterface clivHostInterface) {
        super(context, cursor, 0);
        mClivHostInterface = clivHostInterface;
        setHasStableIds(true);
    }

    @Override
    public void bindViewHolder(final SortMsgListViewHolder holder, final Context context,
                               final Cursor cursor) {
        final SortMsgListItemView sortMsgListItemView = holder.mView;
        sortMsgListItemView.bind(cursor, mClivHostInterface);
    }

    @Override
    public SortMsgListViewHolder createViewHolder(final Context context,
                                                  final ViewGroup parent, final int viewType) {
        final LayoutInflater layoutInflater = LayoutInflater.from(context);
        final SortMsgListItemView itemView = (SortMsgListItemView) layoutInflater.inflate(R.layout.message_list_item_view, null);
        return new SortMsgListViewHolder(itemView);
    }

    /**
     * ViewHolder that holds a SortMsgListItemView.
     */
    public static class SortMsgListViewHolder extends RecyclerView.ViewHolder {
        final SortMsgListItemView mView;

        SortMsgListViewHolder(final SortMsgListItemView itemView) {
            super(itemView);
            mView = itemView;
        }
    }
}
