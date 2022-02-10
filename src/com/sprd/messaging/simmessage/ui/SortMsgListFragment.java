//by sprd
package com.sprd.messaging.simmessage.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.messaging.R;
import com.android.messaging.ui.ListEmptyView;
import com.android.messaging.util.OsUtil;
import com.sprd.messaging.simmessage.data.SortMsgDataCollector;
import com.sprd.messaging.simmessage.data.SortMsgListData;
import com.sprd.messaging.simmessage.data.SortMsgListData.SortMsgListDataListener;
import com.sprd.messaging.simmessage.data.SortMsgListItemData;

public class SortMsgListFragment extends Fragment implements SortMsgListDataListener,
        SortMsgListItemView.HostInterface {
    public static final String TAG = "SortMsgListFragment";

    private SortMsgListData mListData;
    public SortMsgListAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private ListEmptyView mEmptyListMessageView;
    private SortMsgListFragmentHost mHost;

    public interface SortMsgListFragmentHost {
        void onMessageClicked(final SortMsgListItemData conversationListItemData);

        boolean isMessageSelected(final int messageId);

        boolean isInActionMode();

        void copySimSmsToPhone(int subId, String bobyText,
                               long receivedTimestamp, int messageStatus,
                               boolean isRead, String address);

        void deleteSimSms(int simSmsId, int subIdToDelSimSms);

        void updateOptionsMenu();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListData = new SortMsgListData(activity, this);
    }

    /**
     * Call this immediately after attaching the fragment
     */
    public void setHost(final SortMsgListFragmentHost host) {
        mHost = host;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new SortMsgListAdapter(getActivity(), null, this);
        reloadMessage();
    }

    private final int DEF_LOADER = SortMsgDataCollector.LOADER_ID_DEFAULT;

    public void reloadMessage() {
        if (OsUtil.hasRequiredPermissions()) {
            mListData.restartLoader(getLoaderManager(), DEF_LOADER);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.sort_msg_list_fragment,
                container, false);
        mRecyclerView = (RecyclerView) rootView.findViewById(android.R.id.list);
        mEmptyListMessageView = rootView.findViewById(R.id.no_msg_view);
        mEmptyListMessageView.setImageHint(R.drawable.ic_oobe_conv_list);

        final LinearLayoutManager manager = new LinearLayoutManager(getActivity()) {
            @Override
            public RecyclerView.LayoutParams generateDefaultLayoutParams() {
                return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        };
        mRecyclerView.setLayoutManager(manager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        return rootView;
    }

    @Override
    public void onSortMsgListCursorUpdated(SortMsgListData data, Cursor cursor) {
        mAdapter.swapCursor(cursor);
        updateEmptyListUi(cursor == null || cursor.getCount() == 0);
        if (mHost != null) {
            mHost.updateOptionsMenu();
        }
    }

    private void updateEmptyListUi(final boolean isEmpty) {
        if (isEmpty) {
            mEmptyListMessageView.setTextHint(R.string.sort_msg_list_empty_text);
            mEmptyListMessageView.setVisibility(View.VISIBLE);
            mEmptyListMessageView.setIsImageVisible(true);
            mEmptyListMessageView.setIsVerticallyCentered(true);
        } else {
            mEmptyListMessageView.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean isMessageSelected(int MessageId) {
        return mHost.isMessageSelected(MessageId);
    }

    @Override
    public void onMessageClicked(SortMsgListItemData sortMsgListItemData, boolean isLongClick,
                                 SortMsgListItemView sortMsgListItemView) {
        mHost.onMessageClicked(sortMsgListItemData);
    }

    @Override
    public void copySimSmsToPhone(int subId, String bobyText,
                                  long receivedTimestamp, int messageStatus,
                                  boolean isRead, String address) {
        mHost.copySimSmsToPhone(subId, bobyText,
                receivedTimestamp, messageStatus,
                isRead, address);
    }

    @Override
    public void deleteSimSms(int simSmsId, int subIdToDelSimSms) {
        mHost.deleteSimSms(simSmsId, subIdToDelSimSms);
    }

    @Override
    public boolean isInActionMode() {
        return mHost.isInActionMode();
    }


    public void updateUi() {
        mAdapter.notifyDataSetChanged();
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    @Override
    public boolean isSelectionMode() {
        return false;
    }


    private AlertDialog mDeleteSIMSMSDialog;

    @Override
    public void hasLongClickAction(final AlertDialog dialog) {
        mDeleteSIMSMSDialog = dialog;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mDeleteSIMSMSDialog != null && mDeleteSIMSMSDialog.isShowing()) {
            mDeleteSIMSMSDialog.dismiss();
            mDeleteSIMSMSDialog = null;
        }
    }
}
