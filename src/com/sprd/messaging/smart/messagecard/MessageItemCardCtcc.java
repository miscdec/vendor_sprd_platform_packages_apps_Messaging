//by sprd
package com.sprd.messaging.smart.messagecard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.android.messaging.R;
import com.android.messaging.ui.conversation.ScreenUtils;
import com.gstd.callme.UI.inter.ProcessMenuOperationListener;
import com.gstd.callme.engine.SmartSmsEngine;
import com.gstd.callme.outerentity.CardInfo;
import com.gstd.callme.outerentity.CardShowContent;
import com.gstd.callme.outerentity.MenuInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageItemCardCtcc extends LinearLayout {

    private Context mContext;
    private static final String TAG = "MessageItemCardCtcc";

    /**
     * 短信header背景
     */
    private ImageView mSmsCardHeaderImage;
    /**
     * 短信的title
     */
    private TextView mUnioncastSmsTitle;
    /**
     * 查看短信详情
     */
    private ImageView mUnioncastImageRight;
    /**
     * 短信的内容
     */
    private ViewGroup mUnioncastSmsBody;
    /**
     * 短信的操作
     */
    private LinearLayout mUnioncastSmsOperation;
    /**
     * 卡片的宽度
     */
    private int mWidth;
    /**
     * 卡片的高度
     */
    private int mHeight;
    /**
     * 短信详情内容
     */
    private String mBody;
    private String mAddress;
    private String mMsgId;
    private long mReceiveDate;

    private RelativeLayout mUnioncastSmsHeader;
    private LinearLayout mUnioncastSmsCard;
    private FrameLayout mUnioncastViewAd;

    private OnDetailClickButtonLitener onDetailClickButtonLitener;
    private View mUnioncastViewLine;

    public MessageItemCardCtcc(Context context) {
        this(context, null);
    }

    public MessageItemCardCtcc(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public MessageItemCardCtcc(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;
        initView();
    }

    private void initView() {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mUnioncastSmsCard = (LinearLayout) inflater.inflate(R.layout.cm_msg_card_item, this);
        mUnioncastSmsHeader = (RelativeLayout) mUnioncastSmsCard.findViewById(R.id.cm_msg_card_item_header);
        mUnioncastSmsTitle = (TextView) mUnioncastSmsCard.findViewById(R.id.cm_msg_card_item_title);
        mUnioncastImageRight = (ImageView) mUnioncastSmsCard.findViewById(R.id.cm_msg_card_item_right);
        mUnioncastSmsBody = mUnioncastSmsCard.findViewById(R.id.cm_msg_card_item_body);
        mUnioncastViewLine = (View) mUnioncastSmsCard.findViewById(R.id.cm_msg_card_item_viewLine);
        mUnioncastSmsOperation = (LinearLayout) mUnioncastSmsCard.findViewById(R.id.cm_msg_card_item_operation);
        mUnioncastViewAd = (FrameLayout) mUnioncastSmsCard.findViewById(R.id.cm_msg_card_item_ad);
    }

    public void setSmsCardInfo(CardInfo cardInfo) {
        setSmsCardHeader(cardInfo);
        addSmsBody(cardInfo);
        addSmsOperations(cardInfo);

        if (null != cardInfo) {
            if (null != cardInfo.getOperation() && cardInfo.getOperation().size() > 0) {
                mUnioncastSmsOperation.setBackgroundResource(R.drawable.cm_card_msg_content_bg_shape);
            } else {
                mUnioncastSmsBody.setBackgroundResource(R.drawable.cm_card_msg_content_bg_shape);
            }
        }
    }

    /**
     * set card head
     *
     * @param cardInfo
     */
    private void setSmsCardHeader(final CardInfo cardInfo) {
        mUnioncastSmsTitle.setText(cardInfo.getTitle());
        mUnioncastImageRight.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != onDetailClickButtonLitener) {
                    onDetailClickButtonLitener.detailClickButton(mAddress, mBody);
                }
            }
        });
    }

    /**
     * Set card content
     *
     * @param cardInfo
     */
    private void addSmsBody(CardInfo cardInfo) {
        mUnioncastSmsBody.removeAllViews();

        List<CardShowContent> keyWordsValues = cardInfo.getKeyWordsValues();
        if (null == keyWordsValues) {
            return;
        }

        if (keyWordsValues.size() == 1 && keyWordsValues.get(0).getKey().equals("no-parse-title")) {
            int padding = ScreenUtils.dp2px(mContext, 10);
            mUnioncastSmsBody.setPadding(padding, padding, padding, padding);
            TextView textview = new TextView(mContext);
            textview.setTextSize(15);
            textview.setText(mBody);
            textview.setPadding(40, 10, 15, 40);
            textview.setTextColor(Color.BLACK);
            mUnioncastSmsBody.addView(textview);
            return;
        }

        MessageItemCardBodyCtcc bodyView = new MessageItemCardBodyCtcc();
        mUnioncastSmsBody.addView(bodyView.buildBodyView(mContext, cardInfo));
    }

    /**
     * Add operation buttons to the card
     *
     * @param cardInfo
     */
    private void addSmsOperations(CardInfo cardInfo) {
        mUnioncastSmsOperation.removeAllViews();

        if (cardInfo.getOperation() == null || cardInfo.getOperation().size() == 0) {
            // card has no operation item
            mUnioncastSmsOperation.setVisibility(View.GONE);
            mUnioncastViewLine.setVisibility(View.GONE);
        } else {
            //card has operation items
            mUnioncastSmsOperation.setVisibility(View.VISIBLE);
            mUnioncastViewLine.setVisibility(View.VISIBLE);
            int menuCount = cardInfo.getOperation().size();

            for (int i = 0; i < menuCount; i++) {
                MenuInfo menu = cardInfo.getOperation().get(i);
                Map<String, String> operation = menu.getOperation();

                LinearLayout view = (LinearLayout) View.inflate(getContext(), R.layout.cm_card_operation_item_view, null);
                TextView perationText = (TextView) view.findViewById(R.id.cm_card_operation_item_view_text);

                LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                params.setMargins(0, 0, 0, 0);
                view.setLayoutParams(params);
                perationText.setText(menu.getTitle());

                view.setOnClickListener(new OnClickListenerImpl(menu, cardInfo));
                mUnioncastSmsOperation.addView(view);

                if (i != menuCount - 1) {
                    View lineView = new View(getContext());
                    lineView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.divider_line));
                    lineView.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                    mUnioncastSmsOperation.addView(lineView);
                }
            }
        }
    }


    private static final int SHOW_POPUPWINDOW = 1;

    private AlertDialog mShowAppStoreDialog;

    private void ShowAppStoreDialog(final String[] appStrings) {
        mShowAppStoreDialog = new AlertDialog.Builder(mContext).setTitle(R.string.action_download)
                .setMessage(appStrings[2])
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                Uri uri = Uri.parse("market://details?id=" + appStrings[0]);
                                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try {
                                    mContext.startActivity(intent);
                                } catch (Exception e) {
                                    Toast.makeText(mContext, R.string.no_market_store_app, Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        mShowAppStoreDialog.show();
    }

    /**
     * Handling card button click events
     */
    class OnClickListenerImpl implements OnClickListener {
        private MenuInfo menu;
        private CardInfo cardInfo;

        public OnClickListenerImpl(MenuInfo menu, CardInfo cardInfo) {
            super();
            this.menu = menu;
            this.cardInfo = cardInfo;
        }

        @Override
        public void onClick(View v) {
            try {
                SmartSmsEngine.getInstance().processMenuOperation((Activity) getContext(), menu.getOperation(), menu.getType(), new ProcessMenuOperationListener() {
                    @Override
                    public void sendSms(String content, String smsNumber) {
                        //Toast.makeText(mContext, "发送短信：号码-"+smsNumber+"; 内容-"+content, Toast.LENGTH_SHORT).show();
                        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
                        ArrayList<String> messages = smsManager.divideMessage(content);
                       /* for (String text : messages) {
                            smsManager.sendTextMessage(smsNumber, null, text, null, null);
                        }*/
                        smsManager.sendMultipartTextMessage(smsNumber, null, messages, null, null);
                    }

                    @Override
                    public void showPopupWindow(String strPackageName, String strPackageActName, String appName) {
                        final String[] appStrings = {strPackageName, strPackageActName, appName};
                        ShowAppStoreDialog(appStrings);
                    }

                    @Override
                    public void callPhone(String smsNumber) {
                        // Toast.makeText(mContext, "拨打电话：号码-"+smsNumber, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_CALL);
                        intent.setData(Uri.parse("tel:" + smsNumber));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    }

                    @Override
                    public void trafficBuy(String smsNumber) {
                        Toast.makeText(mContext, "流量购买：号码-" + smsNumber, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void scheduleOpen(long startTime, long endTime) {
                        // Toast.makeText(mContext, "日程管理：开始时间-"+startTime+"; 结束时间-"+endTime, Toast.LENGTH_SHORT).show();
                        String ALERT_APP_PACKAGE_NAME = "com.android.calendar";
                        String ALERT_APP_PACKAGE_LANUCH = "com.android.calendar.LaunchActivity";

                        Intent intent = new Intent();
                        intent.putExtra("beginTime", startTime);
                        intent.putExtra("endTime", endTime);
                        intent.setComponent(new ComponentName(ALERT_APP_PACKAGE_NAME, ALERT_APP_PACKAGE_LANUCH));
                        mContext.startActivity(intent);
                    }

                    @Override
                    public void openWebView(String url, String title, String param) {
                        //Toast.makeText(mContext, "打开网页：地址-"+url+"; 标题-"+title+"; 参数-"+param, Toast.LENGTH_SHORT).show();
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "http://" + url;
                        }
                        Intent intent = new Intent();
                        intent.setAction("android.intent.action.VIEW");
                        Uri content_url = Uri.parse(url);
                        intent.setData(content_url);
                        mContext.startActivity(intent);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setWidth(int mWidth) {
        this.mWidth = mWidth;
    }

    public void setHeight(int mHeight) {
        this.mHeight = mHeight;
    }

    public void setAddress(String address) {
        this.mAddress = address;
    }

    public void setBody(String body) {
        this.mBody = body;
    }

    public void setMsgId(String msgId) {
        mMsgId = msgId;
    }

    public void setReceiveDate(long receiveDate) {
        mReceiveDate = receiveDate;
    }

    public void setOnDetailClickButton(OnDetailClickButtonLitener onDetailClickButtonLitener) {
        this.onDetailClickButtonLitener = onDetailClickButtonLitener;
    }

    public interface OnDetailClickButtonLitener {
        /**
         * 短信详情弹窗
         *
         * @param mBody
         * @param mAdress
         */
        public void detailClickButton(String mAdress, String mBody);
    }
}

