//by sprd
package com.sprd.messaging.simmessage.ui;

import android.database.Cursor;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.Menu;
import android.view.MenuItem;

import com.android.messaging.R;
import com.sprd.messaging.simmessage.data.SortMsgDataCollector;
import com.sprd.messaging.simmessage.data.SortMsgListItemData;

import java.util.ArrayList;

public class MultiSelectActionModeCallback implements Callback {

    public interface Listener {
        void onActionBarDelete(ArrayList<Integer> messages);

        void onActionBarTitleUpdate(int count);

        void updateUi();
    }

    private final ArrayList<Integer> mSelectedMessages = new ArrayList<>();
    private Listener mListener;
    private MenuItem mSelectMenu;
    private MenuItem mDeleteMenu;
    private Cursor mCursor;
    private SortMsgListAdapter mAdapter;

    public MultiSelectActionModeCallback(final Listener listener, SortMsgListAdapter adapter) {
        mListener = listener;
        mAdapter = adapter;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        actionMode.getMenuInflater().inflate(R.menu.multi_select_menu, menu);
        mSelectMenu = menu.findItem(R.id.action_select);
        mDeleteMenu = menu.findItem(R.id.action_delete);
        updateActionMenuStatus();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mSelectedMessages.clear();
        if (mListener != null) {
            mListener.updateUi();
        }
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.action_select:
                selectAllOrNone();
                return true;
            case R.id.action_delete:
                mListener.onActionBarDelete(mSelectedMessages);
                return true;
            case android.R.id.home:
                actionMode.finish();
                return true;
            default:
                return false;
        }
    }

    /* pulic area, begin */
    public void toggleSelect(final SortMsgListItemData listItemData) {
        final Integer id = listItemData.getMessageId();
        if (mSelectedMessages.contains(id)) {
            mSelectedMessages.remove(id);
        } else {
            mSelectedMessages.add(id);
        }
        updateActionMenuStatus();
    }

    public boolean isSelected(final int selectedId) {
        return mSelectedMessages.contains(selectedId);
    }
    /* public area, end*/

    private void selectAllOrNone() {
        updateCursor();
        if (null == mCursor || mCursor.isClosed()) {
            return;
        }
        final int selectedCount = mSelectedMessages.size();
        mSelectedMessages.clear();
        if (selectedCount < mCursor.getCount()) {
            mCursor.moveToFirst();
            do {
                Integer msgId = mCursor.getInt(SortMsgDataCollector.MESSAGE_ID);
                mSelectedMessages.add(msgId);
            } while (mCursor.moveToNext());
        }
        updateActionMenuStatus();
        if (mListener != null) {
            mListener.updateUi();
        }
    }

    private void updateActionMenuStatus() {
        if (mDeleteMenu != null) {
            final boolean hasSelectedItem = !mSelectedMessages.isEmpty();
            mDeleteMenu.setEnabled(hasSelectedItem);
            mDeleteMenu.setIcon(hasSelectedItem ? R.drawable.ic_delete_light : R.drawable.ic_delete_dark);
        }
        updateCursor();
        if (mCursor != null && mSelectMenu != null) {
            final boolean seletedAll = (mCursor.getCount() == mSelectedMessages.size());
            if (seletedAll) {
                mSelectMenu.setTitle(R.string.menu_select_none);  //UNISOC add for bug1394216
                mSelectMenu.setIcon(R.drawable.ic_cancel_light);
            } else {
                mSelectMenu.setTitle(R.string.muti_select_all);   //UNISOC add for bug1394216
                mSelectMenu.setIcon(R.drawable.ic_checkmark_light);
            }
        }
        if (mListener != null) {
            mListener.onActionBarTitleUpdate(mSelectedMessages.size());
        }
    }

    private void updateCursor() {
        mCursor = mAdapter.getCursor();
    }
}
