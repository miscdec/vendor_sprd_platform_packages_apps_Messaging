//by sprd
package com.sprd.messaging.simmessage.data;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.messaging.util.LogUtil;
import com.sprd.messaging.simmessage.ui.SortMsgListActivity;

import java.util.Arrays;

public class SortMsgListData implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final String TAG = "SortMsgListData";
    private final SortMsgListDataListener mListener;
    private final Context mContext;
    private SharedPreferences mPreferences;

    private int getSubIdShow() {
        return mPreferences.getInt(SortMsgDataCollector.SHOW_SIM_MESSAGE_BY_SUB_ID, SortMsgDataCollector.SHOW_ALL_MESSAGE);
    }

    public interface SortMsgListDataListener {
        void onSortMsgListCursorUpdated(SortMsgListData data, Cursor cursor);
    }

    public SortMsgListData(final Context context, final SortMsgListDataListener listener) {
        mListener = listener;
        mContext = context;
        if (null == mPreferences) {
            mPreferences = mContext.getSharedPreferences(SortMsgListActivity.PREFERENCES_NAME,
                    mContext.MODE_PRIVATE);
        }
    }

    public void initLoader(final LoaderManager loaderManager, int loaderId) {
        loaderManager.initLoader(loaderId, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        LogUtil.d(TAG, "onCreateLoader");
        Loader<Cursor> loader;
        String where = null;
        String order;
        Uri uri;
        String[] selectionArgs = null;

        final int subIdShow = getSubIdShow();
        uri = SortMsgDataCollector.SIM_MESSAGE_LIST_VIEW_URI;
        String[] projections = SortMsgDataCollector.getSimMessageListViewProjection();//for bug695582

        final Uri simSmsUri = Uri.parse("content://sms/icc_load");
        if (initEnv(mContext, simSmsUri)) {
            LogUtil.d(TAG, "initEnv is true");
        }
        mContext.getContentResolver().delete(SortMsgDataCollector.CLEAR_SIM_MESSAGES_URI, null, null);

        if (subIdShow != SortMsgDataCollector.SHOW_ALL_MESSAGE) {
            where = "sub_id=" + subIdShow;
        }
        order = mPreferences.getString(SortMsgDataCollector.getMsgOrderKey(), "");
        LogUtil.d(TAG, "onCreateLoader where:" + where + ",projections:" + Arrays.toString(projections));
        loader = new CursorLoader(mContext, uri, projections, where, selectionArgs, order);
        return loader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor arg1) {
        if (arg1 != null) {
            LogUtil.d(TAG, "onLoadFinished");
            mListener.onSortMsgListCursorUpdated(this, arg1);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
    }

    public void restartLoader(final LoaderManager loaderManager, int loaderId) {
        loaderManager.restartLoader(loaderId, null, this);
    }

    private boolean initEnv(Context context, Object obj) {
        if (null == context) {
            return false;
        }

        Uri uri = (Uri) obj;
        Cursor cursor = context.getContentResolver().query(uri, new String[]{"_id"}, null, null, null);
        if (cursor == null) {
            return false;
        } else {
            cursor.close();
            return true;
        }
    }
}