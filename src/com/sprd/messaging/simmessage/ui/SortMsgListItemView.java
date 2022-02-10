//by sprd
package com.sprd.messaging.simmessage.ui;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.messaging.R;
import com.android.messaging.util.LinkSpec;
import com.android.messaging.util.LinkifyUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.Typefaces;
import com.sprd.messaging.simmessage.data.SortMsgDataCollector;
import com.sprd.messaging.simmessage.data.SortMsgListItemData;

import java.util.List;
import java.util.Locale;

public class SortMsgListItemView extends FrameLayout implements OnClickListener,
        OnLongClickListener, OnLayoutChangeListener {
    private static final String TAG = "SortMsgListItemView";
    public static final String ACTION_CALL_PRIVILEGED = "android.intent.action.CALL_PRIVILEGED";

    private int mListItemReadColor;
    private int mListItemUnreadColor;
    private Typeface mListItemReadTypeface;
    private Typeface mListItemUnreadTypeface;
    private AlertDialog mContextDialog;
    private final SortMsgListItemData mData;
    private ViewGroup mSwipeableContainer;
    private TextView mConversationNameView;
    private TextView mSnippetTextView;
    private TextView mTimestampTextView;
    private TextView mSimNameTextView;
    private ImageView mContactIconView;
    private ImageView mContactCheckmarkView;
    private HostInterface mHostInterface;

    public SortMsgListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mData = new SortMsgListItemData();
    }

    public interface HostInterface {
        boolean isMessageSelected(final int messageId);

        void onMessageClicked(final SortMsgListItemData sortMsgListItemData, boolean isLongClick,
                              final SortMsgListItemView sortMsgListItemView);

        boolean isSelectionMode();

        boolean isInActionMode();

        void copySimSmsToPhone(int subId, String bobyText,
                               long receivedTimestamp, int messageStatus,
                               boolean isRead, String address);

        void deleteSimSms(int simSmsId, int subIdToDelSimSms);

        void hasLongClickAction(final AlertDialog dialog);
    }

    public void bind(final Cursor cursor, final HostInterface hostInterface) {
        mHostInterface = hostInterface;
        mData.bind(cursor);

        mSwipeableContainer.setOnClickListener(this);
        mSwipeableContainer.setOnLongClickListener(this);
        mSnippetTextView.setOnClickListener(this);//add for bug 936790
        mSnippetTextView.setTag(false);
        mContactIconView.setOnClickListener(this);// add for bug 943285
        mContactIconView.setOnLongClickListener(this); //UNISOC add for bug 1392015
        mConversationNameView.setOnClickListener(this);  //UNISOC add for bug 1392019

        // image view
        final boolean isSelected = mHostInterface.isMessageSelected(mData.getMessageId());
        if (isSelected) {
            mContactCheckmarkView.setVisibility(VISIBLE);
            mContactIconView.setVisibility(GONE);
        } else {
            setContactIcon(mData.getMessageStatus(), mData.getIsRead());
            mContactIconView.setVisibility(VISIBLE);
            mContactCheckmarkView.setVisibility(GONE);
        }
        setMessageName();
        setSnippet(getSnippetText());

        final String formattedTimestamp = mData.getFormattedTimestamp(getContext());
        if (mData.getIsSendRequested()) {
            mTimestampTextView.setText(R.string.message_status_sending);
        } else {
            mTimestampTextView.setText(formattedTimestamp);
            mTimestampTextView.append(" ");
        }

        // sim card indicator
        final boolean simNameVisible = mData.isActiveSubscription();
        if (simNameVisible) {
            String simNameText = mData.getSubscriptionName();
            final String displayName = TextUtils.isEmpty(simNameText) ? " " : simNameText;
            mSimNameTextView.setText(displayName);
            mSimNameTextView.setTextColor(mData.getSubscriptionColor());
            mSimNameTextView.setVisibility(VISIBLE);
        } else {
            mSimNameTextView.setVisibility(GONE);
        }
    }

    private void setMessageName() {
        if (mData.getIsRead() || mData.getIsDrft()) {
            mConversationNameView.setTextColor(mListItemReadColor);
            mConversationNameView.setTypeface(mListItemReadTypeface);
        } else {
            mConversationNameView.setTextColor(mListItemUnreadColor);
            mConversationNameView.setTypeface(mListItemUnreadTypeface);
        }

        final String conversationName = mData.getParticipantName();
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        mConversationNameView.setText(bidiFormatter.unicodeWrap(conversationName, TextDirectionHeuristics.LTR));
        if (View.LAYOUT_DIRECTION_LTR == TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())) {
            computeLinkfyInUIThread(mConversationNameView);
        }
    }

    private void setSnippet(String messageBody) {
        if (!TextUtils.isEmpty(messageBody)) {
            mSnippetTextView.setText(messageBody);
            computeLinkfyInUIThread(mSnippetTextView);
        }
    }

    private void computeLinkfyInUIThread(TextView text) {
        try {
            if (TextUtils.isEmpty(text.getText())) {
                return;
            }
            List<LinkSpec> links;
            SpannableString s = null;
            CharSequence t = text.getText();
            if (t instanceof Spannable) {
                links = LinkifyUtil.computeNewLinks((Spannable) t, Linkify.ALL);
            } else {
                s = SpannableString.valueOf(t);
                links = LinkifyUtil.computeNewLinks(s, Linkify.ALL);
            }

            if (links.size() == 0) {
                return;
            }

            if (s == null) {
                LinkifyUtil.applyLinks(links, (Spannable) t);
                LinkifyUtil.addLinkMovementMethod(text);
            } else {
                LinkifyUtil.applyLinks(links, s);
                LinkifyUtil.addLinkMovementMethod(text);
                text.setText(s);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private boolean processClick(final View v, final boolean isLongClick) {
        if ((v == mSwipeableContainer || v == mSnippetTextView || v == mContactIconView
                || v == mConversationNameView) && mHostInterface != null) {
            mHostInterface.onMessageClicked(mData, isLongClick, this);
            return true;
        }
        return false;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                               int oldTop, int oldRight, int oldBottom) {
        if (v == mConversationNameView) {
            setMessageName();
        } else if (v == mSnippetTextView) {
            setSnippet(getSnippetText());
        }
    }

    private OnClickListener mContextMenuItemClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.copySimSmsToPhone:
                    mHostInterface.copySimSmsToPhone(mData.getSubId(),
                            mData.getBobyText(),
                            mData.getReceivedTimestamp(),
                            mData.getMessageStatus(),
                            mData.getIsRead(),
                            mData.getDisplayDestination());
                    break;
                case R.id.delSimSms:
                    int subId = mData.getSubId();
                    int messageId = mData.getMessageId();
                    mHostInterface.deleteSimSms(messageId, subId);
                    break;
                default:
                    break;
            }
            mContextDialog.dismiss();
        }
    };

    private void showCustomContextMenu() {
        if (null == mContextDialog) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            LinearLayout layout = (LinearLayout) inflater.inflate(
                    R.layout.sort_msg_list_context_menu_layout, null);
            mContextDialog = new Builder(getContext())
                    .setTitle(R.string.message_context_menu_title)
                    .setView(layout)
                    .create();

            TextView copySimSmsToPhoneView = layout.findViewById(R.id.copySimSmsToPhone);
            TextView delSimSmsView = layout.findViewById(R.id.delSimSms);
            copySimSmsToPhoneView.setOnClickListener(mContextMenuItemClickListener);
            delSimSmsView.setOnClickListener(mContextMenuItemClickListener);
        }

        if (!mContextDialog.isShowing()) {
            mContextDialog.show();
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (!mHostInterface.isInActionMode()) {
            showCustomContextMenu();
            mHostInterface.hasLongClickAction(mContextDialog);
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == mSnippetTextView && (boolean) v.getTag()) {//bug 1238925
            mSnippetTextView.setTag(false);
            return;
        }
        processClick(v, false);
    }

    @Override
    protected void onFinishInflate() {
        mSwipeableContainer = (ViewGroup) findViewById(R.id.swipeableContent);
        mContactIconView = findViewById(R.id.conversation_icon);
        mContactCheckmarkView = findViewById(R.id.conversation_checkmark);
        mConversationNameView = (TextView) findViewById(R.id.conversation_name);
        mSnippetTextView = (TextView) findViewById(R.id.conversation_snippet);
        mTimestampTextView = (TextView) findViewById(R.id.conversation_timestamp);
        mSimNameTextView = (TextView) findViewById(R.id.sim_name);

        mConversationNameView.addOnLayoutChangeListener(this);
        mSnippetTextView.addOnLayoutChangeListener(this);
        IgnoreLinkLongClickHelper.ignoreLinkLongClick(mSnippetTextView, this);
        IgnoreLinkLongClickHelper.ignoreLinkLongClick(mConversationNameView, this);

        final Resources resources = getContext().getResources();
        mListItemReadColor = resources.getColor(R.color.conversation_list_item_read);
        mListItemUnreadColor = resources.getColor(R.color.conversation_list_item_unread);

        mListItemReadTypeface = Typefaces.getRobotoNormal();
        mListItemUnreadTypeface = Typefaces.getRobotoBold();

        if (OsUtil.isAtLeastL()) {
            setTransitionGroup(true);
        }
        mSnippetTextView.setEllipsize(null);
    }

    private String getSnippetText() {
        return mData.getBobyText();
    }

    public boolean isAnimating() {
        // TODO Auto-generated method stub
        return false;
    }

    /**
     * A helper class that allows us to handle long clicks on linkified message text view (i.e. to
     * select the message) so it's not handled by the link spans to launch apps for the links.
     */
    private static class IgnoreLinkLongClickHelper implements OnLongClickListener, OnTouchListener {
        private boolean mIsLongClick;
        private final OnLongClickListener mDelegateLongClickListener;

        /**
         * Ignore long clicks on linkified texts for a given text view.
         *
         * @param textView          the TextView to ignore long clicks on
         * @param longClickListener a delegate OnLongClickListener to be called when the view is
         *                          long clicked.
         */
        public static void ignoreLinkLongClick(final TextView textView,
                                               @Nullable final OnLongClickListener longClickListener) {
            final IgnoreLinkLongClickHelper helper =
                    new IgnoreLinkLongClickHelper(longClickListener);
            textView.setOnLongClickListener(helper);
            textView.setOnTouchListener(helper);
        }

        private IgnoreLinkLongClickHelper(@Nullable final OnLongClickListener longClickListener) {
            mDelegateLongClickListener = longClickListener;
        }

        @Override
        public boolean onLongClick(final View v) {
            // Record that this click is a long click.
            mIsLongClick = true;
            if (mDelegateLongClickListener != null) {
                return mDelegateLongClickListener.onLongClick(v);
            }
            return false;
        }

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP && mIsLongClick) {
                mIsLongClick = false;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mIsLongClick = false;
            }
            return false;
        }
    }

    private void setContactIcon(int status, boolean isRead) {
        int sortTypeb = SortMsgDataCollector.getSortTypeByStatus(status);
        switch (sortTypeb) {
            case SortMsgDataCollector.MSG_BOX_INBOX:
                mContactIconView.setImageResource(isRead ? R.drawable.msg_readed : R.drawable.msg_unread);
                break;
            case SortMsgDataCollector.MSG_BOX_SENT:
                mContactIconView.setImageResource(R.drawable.ic_sent);
                break;
            case SortMsgDataCollector.MSG_BOX_OUTBOX:
                mContactIconView.setImageResource(R.drawable.ic_outbox);
                break;
            default:
                break;
        }
    }
}
