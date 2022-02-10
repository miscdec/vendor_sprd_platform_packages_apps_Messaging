
package com.sprd.messaging.sms.commonphrase.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.messaging.R;
import com.sprd.messaging.sms.commonphrase.model.ItemData;
import com.sprd.messaging.sms.commonphrase.model.PharserManager;
import com.android.messaging.util.UiUtils;
import java.util.ArrayList;
import java.util.Collection;

import static com.sprd.messaging.sms.commonphrase.model.IModify.OP_INSERT;

/* Modify by SPRD for Bug:505782 2015.11.30 Start */
public class PharserActivity extends AppCompatActivity implements PharserListAdapter.PharserHost {
    private AlertDialog mAddPhraseDialog;//for bug658890
    private AlertDialog mIsExit; //Bug 983075

    /* Modify by SPRD for Bug:505782 2015.11.30 End */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pharser_activity_ex);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        /* And by SPRD for Bug:505782 2015.11.30 Start */
        // Fixed know bug: Delete one or more phrases, pressing finish, and a new phrase,
        // switch system language, back to ui, the new phrase disappear.
        // Load data from DB:
        // 1. If Intent from caller has not extras, savedInstanceState is null.
        // 2. savedInstanceState dose not contain KEY_HAS_LOADED.
        // 3. If contains, the boolean value is false.
        if (savedInstanceState == null || !savedInstanceState.getBoolean(KEY_HAS_LOADED, false)) {
            Log.d(TAG, "onCreate...will load data from DB!");
            PharserManager.getInstance().LoadFromDatabase(getContext());
        }
        /* And by SPRD for Bug:505782 2015.11.30 End */

        PharserManager.getInstance().MapToArrayList(mDataList);
        init();
        reload(); //Add by SPRD for Bug:522913 2016.02.03

        //add for bug 621916
        if (savedInstanceState != null) {
            ArrayList<Integer> savedPositions = savedInstanceState.getIntegerArrayList(KEY_SELECTED_POSITIONS);
            if (!isEmptyCollection(savedPositions)) {
                for (Integer position : savedPositions) {
                    if (getAdarpter() != null && position >= 0 && position < getAdarpter().getCount()) {
                        Log.d(TAG, "restore select.");
                        getAdarpter().addSelectedItem(position);
                    }
                }
            }
        }
    }

    /* And by SPRD for Bug:509485 2015.12.11 Start */
    private void reload() {
        PharserManager.getInstance().reloadFromDB(this);
        OnDataChange();
    }
    /* And by SPRD for Bug:509485 2015.12.11 End */

    private void init() {
        mListView = (ListView) findViewById(R.id.pharser_list_view);
        mListView.setOnItemClickListener(new ListViewItemClick());
        mEmptyView = (View) findViewById(R.id.empty);

        mListAdapter = new PharserListAdapter(getContext(), getDataSource());
        /* And by SPRD for Bug:505782 2015.11.30 Start */
        mListAdapter.setHost(this);
        /* And by SPRD for Bug:505782 2015.11.30 End */

        getListView().setAdapter(mListAdapter);
        getAdarpter().notifyDataSetChanged();

        mCallback = new MultiModeCallBack(getContext());
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mListView.setMultiChoiceModeListener(mCallback);
        Log.d(TAG, "mListAdapter.getCount():" + mListAdapter.getCount());
        if (mListAdapter.getCount() == 0) {
            mListView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
    }

    public static ArrayList<String> intentCommonPhrase(int intentType, Context context) {
        Cursor cursor = PharserManager.LoadFromDatabase(intentType, context);
        try {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            } else {
                return PharserManager.intentArray(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.clear();
        menu.add(0, OP_INSERT, 0, R.string.add).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    public void startActionmode() {
        if (mActionMode == null) {
            mActionMode = startActionMode(mActionModeCallBack);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case OP_INSERT: {
                showItemDetailDialog();
                break;
            }
            case android.R.id.home:
                if (isEditPharse) {
                    createAlertDialog();
                    return true;
                }
                reload();
                finish();
                break;
        }
        return true;
    }

    private void showEditDialog(ItemData datainfo) {
        // TODO Auto-generated method stub
        AlertDialog.Builder builder = new AlertDialog.Builder(PharserActivity.this);
        builder.setTitle(R.string.edit_pharser).setMessage(datainfo.getPharser())
                .setPositiveButton(R.string.edit, new EditButtonListener(datainfo))
                .setNeutralButton(R.string.delete, new DeleteButtonListener(datainfo))
                .setNegativeButton(android.R.string.cancel,null).show();
    }

    private void OnDataChange() {
        mDataList.clear();
        PharserManager.getInstance().MapToArrayList(mDataList);
        for (ItemData item : mDataList) {
            item.Debug();
        }
        Log.d(TAG, "OnDataChange, getAdarpter().getCount():" + getAdarpter().getCount());
        if (getAdarpter().getCount() == 0) {
            mListView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mListView.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
        }
        getAdarpter().notifyDataSetChanged();
    }

    private Context getContext() {
        return PharserActivity.this;
    }

    private ListView getListView() {
        return mListView;
    }

    private PharserListAdapter getAdarpter() {
        return mListAdapter;
    }

    private ArrayList<ItemData> getDataSource() {
        return mDataList;
    }

    private ListView mListView;
    private ArrayList<ItemData> mDataList = new ArrayList<ItemData>();
    private PharserListAdapter mListAdapter;
    private View mEmptyView;

    private ActionMode mActionMode;
    private MultiModeCallBack mCallback;
    private static final int MAX_EDITABLE_LENGTH = 100;
    private static String TAG = "PharserActivity";

    private ActionMode.Callback mActionModeCallBack = new ActionModeCallback();

    /********************************************************************************************************************************************************************
     * @author ActionModeCallback
     ******************************************************************************************************************************************************************/
    private class ActionModeCallback implements ActionMode.Callback {

        @SuppressLint("NewApi")
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            switch (item.getItemId()) {
                case R.id.finish:
                    PharserManager.getInstance().WriteToDisk(getContext());
                    reload();//SPRD: Add for Bug 515773
                    isEditPharse = false;
                    mode.finish();
                    return true;
                case R.id.cancel:
                    reload();
                    isEditPharse = false;
                    mode.finish();
                    return false;
                case android.R.id.home:
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.finish_menu, menu);
            menu.findItem(R.id.finish).setVisible(true);
            menu.findItem(R.id.cancel).setVisible(true);
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }
    }

    //for 1180047 begin
    @Override
    public void onPause() {
        super.onPause();
        closeDialog(mAddPhraseDialog);
        closeDialog(mIsExit);
    }
    //for 1180047 end

    /********************************************************************************************************************************************************************
     * @author All Click Events
     ******************************************************************************************************************************************************************/
    private class BaseInfo {
        public BaseInfo(ItemData itemdata) {
            mitemdata = itemdata;
        }

        public BaseInfo(ItemData itemdata, EditText editText, String szOtherValue) {
            this(itemdata);
            mEditText = editText;
            mszValue = szOtherValue;
        }

        protected String getValues() {
            return mszValue;
        }

        protected ItemData getItemData() {
            return mitemdata;
        }

        private ItemData mitemdata;
        private String mszValue;

        private EditText mEditText;
    }

    private class DeleteButtonListener extends BaseInfo implements OnClickListener {
        public DeleteButtonListener(ItemData itemdata) {
            super(itemdata);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            PharserManager.getInstance().DelByID(getItemData().getRowID());
            OnDataChange();
            startActionmode();
            isEditPharse = true;
            try {
                dialog.dismiss();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class EditButtonListener extends BaseInfo implements OnClickListener {
        @SuppressLint("NewApi")
        public EditButtonListener(ItemData datainfo) {
            super(datainfo);
        }

        public void onClick(DialogInterface dialog, int which) {
            // TODO Auto-generated method stub
            dialog.dismiss();
            AlertDialog.Builder editDialogBuilder = new AlertDialog.Builder(PharserActivity.this);
            EditText edittext = new EditText(editDialogBuilder.getContext());
            edittext.computeScroll();
            edittext.setText(getItemData().getPharser());
            //for 787240 begin
            InputFilter[] filters = {new TextLengthFilter(PharserActivity.this,
                    MAX_EDITABLE_LENGTH,
                    R.string.exceed_text_length_limitation)};
            edittext.setFilters(filters);
            //for 787240 end
            edittext.requestFocus();
            Log.d(TAG, "after click 'Edit'====>the str is:" + getItemData().getPharser());
            AlertDialog editDialog = editDialogBuilder
                    .setTitle(R.string.edit_pharser)
                    .setView(edittext)
                    .setPositiveButton(
                            android.R.string.ok,
                            new UpdateButtonListener(getItemData(), edittext, edittext.getText()
                                    .toString()))
                    .setNegativeButton(android.R.string.cancel,null).create();
            editDialog.show();
            edittext.requestFocus();
            if (edittext.isFocused()) {
                editDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }

    }

    private class UpdateButtonListener extends BaseInfo implements OnClickListener {

        public UpdateButtonListener(ItemData datainfo, EditText editText, String szValue) {
            super(datainfo, editText, szValue);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            Log.d(TAG, "edit done,--after click ok====>current str is:"
                    + super.mEditText.getText().toString());

            if ((super.mEditText.getText().toString().trim().isEmpty())) {
                UiUtils.showToastAtBottom(R.string.empty_pharser_not_save);
            } else if (!(getValues().equals(super.mEditText.getText().toString()))) {
                PharserManager.getInstance().updateByID(getItemData().getRowID(),
                        super.mEditText.getText().toString(), null);
                OnDataChange();
                startActionmode();
                isEditPharse = true;
            }
        }
    }

    class ListViewItemClick implements OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            // TODO Auto-generated method stub
            if (getAdarpter() != null) {
                showEditDialog((ItemData) getAdarpter().getItem(position));
            }
        }
    }

    /********************************************************************************************************************************************************************
     * @author Show Edit Dialog
     ******************************************************************************************************************************************************************/
    private void showItemDetailDialog() {
        Log.d(TAG, "showItemDetailDialog");
        AlertDialog.Builder dialog = new AlertDialog.Builder(getContext());
        //mContextDialog=dialog;//for bug658890
        final EditText edit = new EditText(getContext());
        edit.setHint(R.string.type_to_pharser);
        //for 787240 begin
        InputFilter[] filters = {new TextLengthFilter(PharserActivity.this,
                MAX_EDITABLE_LENGTH,
                R.string.exceed_text_length_limitation)};
        edit.setFilters(filters);
        //for 787240 end
        //for bug658890 begin
        mAddPhraseDialog = dialog.setTitle(R.string.add_pharser).setView(edit)
                .setPositiveButton(R.string.positive, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        // TODO Auto-generated method stub
                        String str = edit.getText().toString();
                        if (str.trim().isEmpty()) {
                            UiUtils.showToastAtBottom(R.string.empty_pharser_not_save);
                        } else {
                            PharserManager.getInstance().addNewData(str, 1, 0);
                            OnDataChange();
                            startActionmode();
                            isEditPharse = true;
                        }

                    }
                    //delete for sprd bug 510265
                }).setNegativeButton(android.R.string.cancel,null).create();
        mAddPhraseDialog.show();
        //for bug658890 end
        //bug 990408 begin
        edit.requestFocus();
        if (edit.isFocused()) {
            mAddPhraseDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        //bug 990408 end
    }

    //for 1180047 begin
    private void closeDialog(AlertDialog dialog){
        if(null!=dialog) {
            if (dialog.isShowing()){
                dialog.dismiss();
            }
        }
    }
    //for 1180047 begin
    /****************************************************************************************************************
     * Multi-delete
     *************************************************************************************************************/
    public class MultiModeCallBack implements MultiChoiceModeListener {
        private View mMultiSelectActionBarView;
        // private TextView mSelectedCount;
        private Context mContext;

        public MultiModeCallBack(Context context) {
            mContext = context;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            switch (item.getItemId()) {
                case R.id.menu_cancel:
                    getAdarpter().setItemMultiCheckable(false);
                    getAdarpter().clearSelectedItem();
                    reload();
                    mode.finish();
                    break;
                case R.id.menu_delete:
                    Log.d(TAG, "SelectedItem.size()====>" + getAdarpter().SelectedItems().size());
                    for (ItemData itemdata : getAdarpter().SelectedItems()) {
                        PharserManager.getInstance().DelByID(itemdata.getRowID());
                    }
                    isEditPharse = true;
                    getAdarpter().notifyDataSetChanged();
                    mode.invalidate();
                    mode.finish();
                    OnDataChange();
                    if (getAdarpter().SelectedItems() != null) {
                        startActionmode();// If we d'not choose any item,should not invoke startActionmode()
                    }
                    break;
                default:
                    break;
            }
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            mode.getMenuInflater().inflate(R.menu.multi_actionmode_menu, menu);
            getAdarpter().setItemMultiCheckable(true);
            getAdarpter().notifyDataSetChanged();
            if (mMultiSelectActionBarView == null) {
                mMultiSelectActionBarView = LayoutInflater.from(mContext).inflate(
                        R.layout.list_multi_select_actionbar_ex, null);
            }
            mode.setCustomView(mMultiSelectActionBarView);
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            getAdarpter().setItemMultiCheckable(false);
            getAdarpter().clearSelectedItem();
            getAdarpter().notifyDataSetChanged();
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }

        @SuppressLint("NewApi")
        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                                              boolean checked) {
            if (getAdarpter() == null) return;
            if (checked) {
                ItemData item = null;
                Object obj = getAdarpter().getItem(position);
                if (obj == null) {
                    return;
                }
                if (obj instanceof ItemData){
                    item = (ItemData) obj;
                }
                getAdarpter().addSelectedItem(item.getIndexOfArray());
            } else {
                getAdarpter().cancelSelectedItem(position);
            }
            getAdarpter().notifyDataSetChanged();
            mode.invalidate();
        }
    }

    /* And by SPRD for Bug:505782 2015.11.30 Start */
    private final static String KEY_HAS_LOADED = "k-h-l";
    private final static String KEY_SELECTED_POSITIONS = "k-s-p";
    private ArrayList<Integer> mSelectedPositions = new ArrayList<>();

    private boolean isEmptyCollection(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_HAS_LOADED, true);
        Log.d(TAG, "onSaveInstanceState...mSelectedPositions=" + mSelectedPositions);
        if (!isEmptyCollection(mSelectedPositions)) {
            outState.putIntegerArrayList(KEY_SELECTED_POSITIONS, mSelectedPositions);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<Integer> savedPositions = savedInstanceState.getIntegerArrayList(KEY_SELECTED_POSITIONS);
        if (!isEmptyCollection(savedPositions)) {
            for (Integer position : savedPositions) {
                if (getAdarpter() != null && position >= 0 && position < getAdarpter().getCount()) {
                    Log.d(TAG, "restore select.");
                    getAdarpter().addSelectedItem(position);
                }
            }
        }
    }

    @Override
    public void onSelectedItemAdded(Integer position) {
        if (!mSelectedPositions.contains(position)) {
            mSelectedPositions.add(position);
        }
    }

    @Override
    public void onSelectedItemRemoved(Integer position) {
        // If condition must be set, avoid invoking method remove(int index);
        if (mSelectedPositions.contains(position)) {
            mSelectedPositions.remove(position);
        }
    }

    @Override
    public void onAllSelectedIntemRemoved() {
        mSelectedPositions.clear();
    }
    /* And by SPRD for Bug:505782 2015.11.30 End */

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isEditPharse) {
                createAlertDialog();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isEditPharse = false;

    private void createAlertDialog() {
        AlertDialog.Builder isExit = new AlertDialog.Builder(this);
        isExit.setCancelable(false);
        mIsExit = isExit.setTitle(R.string.exit_pharser_dialog_title).setMessage(R.string.exit_pharser_dialog_content).setPositiveButton(android.R.string.ok,
                new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if(!PharserActivity.this.isFinishing()&& !PharserActivity.this.isDestroyed())
                           dialog.dismiss();
                        PharserManager.getInstance().WriteToDisk(getContext());
                        reload();
                        isEditPharse = false;
                        finish();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if(!PharserActivity.this.isFinishing()&& !PharserActivity.this.isDestroyed())
                            dialog.dismiss();
                        reload();
                        isEditPharse = false;
                        finish();
                    }
                }).create();
        mIsExit.show(); //Bug 983075
    }

    public static class TextLengthFilter implements InputFilter {
        private Context mContext;
        private int mToast;
        private boolean mFlag;
        private int mLimit;

        public TextLengthFilter(Context context, int limit, int toast) {
            mContext = context;
            mToast = toast;
            mLimit = limit;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                                   int dstart, int dend) {
            int keep = mLimit - (dest.length() - (dend - dstart));
            if (keep <= 0) {
                Toast.makeText(mContext, mToast, Toast.LENGTH_SHORT).show();
                mFlag = true;
                return "";
            } else if (keep >= end - start) {
                return null; // keep original
            } else {
                keep += start;
                if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                    --keep;
                    if (keep == start) {
                        return "";
                    }
                }
                if (!mFlag) {
                    Toast.makeText(mContext, mToast, Toast.LENGTH_SHORT).show();
                }
                return source.subSequence(start, keep);
            }
        }
    }
    //for 787240 end
}
