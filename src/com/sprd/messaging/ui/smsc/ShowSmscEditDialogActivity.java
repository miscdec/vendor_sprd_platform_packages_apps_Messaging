//by sprd
package com.sprd.messaging.ui.smsc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.text.BidiFormatter;
import android.text.InputFilter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.messaging.R;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.util.LogUtil;

public class ShowSmscEditDialogActivity extends Activity {
    public static final String TAG = "ShowSmscEditDialogActivity";

    private int mSubId;
    private int mSlotIndex;
    private IntentFilter mSimFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
    private AlertDialog editDialog;

    private Context getContext() {
        return ShowSmscEditDialogActivity.this;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.smsc_dialog_background_ex);
        mSubId = getIntent().getIntExtra("subId", -1);
        mSlotIndex = SubscriptionManager.getSlotIndex(mSubId);
        LogUtil.d(TAG, "onCreate ,mSubId/mSlotIndex = " + mSubId + "/" + mSlotIndex);
        registerReceiver(mSimInOutReceiver, mSimFilter);
        show();
    }

    private void show() {
        AlertDialog.Builder editDialogBuilder = new AlertDialog.Builder(getContext());
        final EditText editSmsc = new EditText(editDialogBuilder.getContext());
        String content = SmscManager.getSmscString(getContext(), mSubId);
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();//use LTR for ar
        editSmsc.setText(bidiFormatter.unicodeWrap(content, TextDirectionHeuristics.LTR));
        editSmsc.setSelection(content.length());
		editSmsc.setEnabled(getContext().getResources().getBoolean(R.bool.config_smsc_editable));
        //editSmsc.setEnabled(MmsConfig.get(mSubId).getSmscEditable());
        editSmsc.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        final String oldSmsc = editSmsc.getText().toString();
        editDialog = editDialogBuilder
                .setView(editSmsc)
                .setTitle(R.string.pref_title_manage_sim_smsc)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newSmsc = editSmsc.getText().toString();
                        if ((!TextUtils.isEmpty(newSmsc)) && (!newSmsc.equals(oldSmsc))) {
                            final boolean setResult = SmscManager.setSmscString(getContext(), newSmsc, mSubId);
                            if (!setResult) {
                                editSmsc.setText(SmscManager.getSmscString(getContext(), mSubId));
                            }
                        }
                        /*Add by SPRD for bug550266  2016.4.19 Start*/
                        Intent intent = new Intent();
                        intent.putExtra("Smsc", newSmsc);
                        setResult(RESULT_OK, intent);
                        /*Add by SPRD for bug550266  2016.4.19 End*/
                        if (TextUtils.isEmpty(newSmsc)) {
                            Toast.makeText(getContext(), R.string.smsc_cannot_be_null,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        LogUtil.d(TAG, "========onDismiss====");
                        finish();
                    }
                }).create();
        editDialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (editDialog.isShowing()) {
            editDialog.dismiss();
        }
        unregisterReceiver(mSimInOutReceiver);
    }

    private BroadcastReceiver mSimInOutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String simStatus = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
            int slotId = intent.getIntExtra(PhoneConstants.PHONE_KEY, -1);
            LogUtil.d(TAG, " mSimInOutReceiver, simStatus/slotId = " + simStatus + "/" + slotId);
            if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(simStatus) && mSlotIndex == slotId) {
                if (editDialog.isShowing()) {
                    editDialog.dismiss();
                }
            }
        }
    };
}
