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

package com.android.messaging.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
// Add for bug 563344
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;

import com.android.messaging.R;
import com.android.messaging.Factory;
import com.android.messaging.annotation.VisibleForAnimation;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.MediaPickerMessagePartData;
// Add for bug 563344
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.MultiAttachmentLayout.OnAttachmentClickListener;
import com.android.messaging.ui.animation.PopupTransitionAnimation;
import com.android.messaging.ui.conversation.ComposeMessageView;
import com.android.messaging.ui.conversation.ConversationFragment;
import com.android.messaging.util.Assert;
import com.android.messaging.util.ThreadUtil;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.ContentType;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import com.sprd.messaging.drm.MessagingDrmSession;
import android.widget.Toast;
import android.content.DialogInterface;
import android.app.AlertDialog;
import com.android.messaging.R;


public class AttachmentPreview extends ScrollView implements OnAttachmentClickListener {
    private FrameLayout mAttachmentView;
    private ComposeMessageView mComposeMessageView;
    private ImageButton mCloseButton;
    private int mAnimatedHeight = -1;
    private Animator mCloseGapAnimator;
    private boolean mPendingFirstUpdate;
    private Handler mHandler;
    private Runnable mHideRunnable;
    private boolean mPendingHideCanceled;
    /* Add by SPRD for bug 563344 Start */
    private boolean mIsCloseButtonPressed = false;
    private boolean mShouldUseSmilView = false;
    /* Add by SPRD for bug 563344 End */

    private static final int CLOSE_BUTTON_REVEAL_STAGGER_MILLIS = 300;

    private final Context mContext;
    private static final String TAG = "AttachmentPreview";

    public AttachmentPreview(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCloseButton = (ImageButton) findViewById(R.id.close_button);
        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                mIsCloseButtonPressed = true;   // Add for bug 563344
                mComposeMessageView.clearAttachments();
            }
        });

        mAttachmentView = (FrameLayout) findViewById(R.id.attachment_view);

        // The attachment preview is a scroll view so that it can show the bottom portion of the
        // attachment whenever the space is tight (e.g. when in landscape mode). Per design
        // request we'd like to make the attachment view always scrolled to the bottom.
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(final View v, final int left, final int top, final int right,
                                       final int bottom, final int oldLeft, final int oldTop, final int oldRight,
                                       final int oldBottom) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        final int childCount = getChildCount();
                        if (childCount > 0) {
                            final View lastChild = getChildAt(childCount - 1);
                            scrollTo(getScrollX(), lastChild.getBottom() - getHeight());
                        }
                    }
                });
            }
        });
        mPendingFirstUpdate = true;
    }

    public void setComposeMessageView(final ComposeMessageView composeMessageView) {
        mComposeMessageView = composeMessageView;
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mAnimatedHeight >= 0) {
            setMeasuredDimension(getMeasuredWidth(), mAnimatedHeight);
        }
    }

    private void cancelPendingHide() {
        mPendingHideCanceled = true;
    }

    public void hideAttachmentPreview() {
        if (getVisibility() != GONE) {
            //-- Add by SPRD for bug 618415  Start --
            //UiUtils.revealOrHideViewWithAnimation(mCloseButton, GONE,
            //        null /* onFinishRunnable */);
            //-- Add by SPRD for bug 618415  end --
            startCloseGapAnimationOnAttachmentClear();

            if (mAttachmentView.getChildCount() > 0) {
                mPendingHideCanceled = false;
                final View viewToHide = mAttachmentView.getChildCount() > 1 ?
                        mAttachmentView : mAttachmentView.getChildAt(0);
                UiUtils.revealOrHideViewWithAnimation(viewToHide, INVISIBLE,
                        new Runnable() {
                            @Override
                            public void run() {
                                // Only hide if we are didn't get overruled by showing
                                if (!mPendingHideCanceled) {
                                    mAttachmentView.removeAllViews();
                                    setVisibility(GONE);
                                }
                            }
                        });
            } else {
                mAttachmentView.removeAllViews();
                setVisibility(GONE);
            }
        }
    }

    // returns true if we have attachments
    public boolean onAttachmentsChanged(final DraftMessageData draftMessageData) {
        final boolean isFirstUpdate = mPendingFirstUpdate;
        //Add by SPRD for bug 563344
        mShouldUseSmilView = ((MessageData.PROTOCOL_MMS_SMIL == draftMessageData.mProtocol) &&
                (draftMessageData.getAttachments().size() > 0));

        final List<MessagePartData> attachments = draftMessageData.getReadOnlyAttachments();
        final List<PendingAttachmentData> pendingAttachments =
                draftMessageData.getReadOnlyPendingAttachments();
        // Any change in attachments would invalidate the animated height animation.
        cancelCloseGapAnimation();
        mPendingFirstUpdate = false;
        /* Add by SPRD for bug 563344 Start */
        final int combinedAttachmentCount;
        if (!mShouldUseSmilView) {
            combinedAttachmentCount = attachments.size() + pendingAttachments.size();
        } else {
            combinedAttachmentCount = 1;
        }
        /* Add by SPRD for bug 563344 End */
        //final int combinedAttachmentCount = attachments.size() + pendingAttachments.size();
        mCloseButton.setContentDescription(getResources()
                .getQuantityString(R.plurals.attachment_preview_close_content_description,
                        combinedAttachmentCount));
        if (combinedAttachmentCount == 0 ||
                mIsCloseButtonPressed /* Add for bug 563344 */) {
            mHideRunnable = new Runnable() {
                @Override
                public void run() {
                    mHideRunnable = null;
                    // Only start the hiding if there are still no attachments
                    if (attachments.size() + pendingAttachments.size() == 0) {
                        hideAttachmentPreview();
                    }
                }
            };
            if (draftMessageData.isSending()) {
                // Wait to hide until the message is ready to start animating
                // We'll execute immediately when the animation triggers
                mHandler.postDelayed(mHideRunnable,
                        ConversationFragment.MESSAGE_ANIMATION_MAX_WAIT);
            } else {
                // Run immediately when clearing attachments
                mHideRunnable.run();
            }
            /* Add by SPRD for bug 563344 Start */
            draftMessageData.mProtocol = MessageData.PROTOCOL_UNKNOWN;
            mIsCloseButtonPressed = false;
            /* Add by SPRD for bug 563344 End */
            return false;
        }

        cancelPendingHide();  // We're showing
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            mAttachmentView.setVisibility(VISIBLE);

            // Don't animate in the close button if this is the first update after view creation.
            // This is the initial draft load from database for pre-existing drafts.
            if (!isFirstUpdate) {
                // Reveal the close button after the view animates in.
                mCloseButton.setVisibility(INVISIBLE);
                ThreadUtil.getMainThreadHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //// UiUtils.revealOrHideViewWithAnimation(mCloseButton, VISIBLE,
                        //  null /* onFinishRunnable */);
                        mCloseButton.setVisibility(VISIBLE);
                    }
                }, 10);//UiUtils.MEDIAPICKER_TRANSITION_DURATION + CLOSE_BUTTON_REVEAL_STAGGER_MILLIS);
            }
        }

        // Merge the pending attachment list with real attachment.  Design would prefer these be
        // in LIFO order user can see added images past the 5th one but we also want them to be in
        // order and we want it to be WYSIWYG.
        final List<MessagePartData> combinedAttachments = new ArrayList<>();
        /* Add by SPRD for bug 563344 Start */
        if (!mShouldUseSmilView) {
            combinedAttachments.addAll(attachments);
            combinedAttachments.addAll(pendingAttachments);
        }
        /* Add by SPRD for bug 563344 End */
//        combinedAttachments.addAll(attachments);
//        combinedAttachments.addAll(pendingAttachments);

        final LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        if (combinedAttachmentCount > 1) {
            MultiAttachmentLayout multiAttachmentLayout = null;
            Rect transitionRect = null;
            if (mAttachmentView.getChildCount() > 0) {
                final View firstChild = mAttachmentView.getChildAt(0);
                if (firstChild instanceof MultiAttachmentLayout) {
                    Assert.equals(1, mAttachmentView.getChildCount());
                    multiAttachmentLayout = (MultiAttachmentLayout) firstChild;
                    multiAttachmentLayout.bindAttachments(combinedAttachments,
                            null /* transitionRect */, combinedAttachmentCount);
                } else {
                    transitionRect = new Rect(firstChild.getLeft(), firstChild.getTop(),
                            firstChild.getRight(), firstChild.getBottom());
                }
            }
            if (multiAttachmentLayout == null) {
                multiAttachmentLayout = AttachmentPreviewFactory.createMultiplePreview(
                        getContext(), this);
                multiAttachmentLayout.bindAttachments(combinedAttachments, transitionRect,
                        combinedAttachmentCount);
                mAttachmentView.removeAllViews();
                mAttachmentView.addView(multiAttachmentLayout);
            }
        } else if (!mShouldUseSmilView)/* Add for bug 563344 */ {
            final MessagePartData attachment = combinedAttachments.get(0);
            boolean shouldAnimate = true;
            if (mAttachmentView.getChildCount() > 0) {
                // If we are going from N->1 attachments, try to use the current bounds
                // bounds as the starting rect.
                shouldAnimate = false;
                final View firstChild = mAttachmentView.getChildAt(0);
                if (firstChild instanceof MultiAttachmentLayout &&
                        attachment instanceof MediaPickerMessagePartData) {
                    final View leftoverView = ((MultiAttachmentLayout) firstChild)
                            .findViewForAttachment(attachment);
                    if (leftoverView != null) {
                        final Rect currentRect = UiUtils.getMeasuredBoundsOnScreen(leftoverView);
                        if (!currentRect.isEmpty() &&
                                attachment instanceof MediaPickerMessagePartData) {
                            ((MediaPickerMessagePartData) attachment).setStartRect(currentRect);
                            shouldAnimate = true;
                        }
                    }
                }
            }
            Log.d(TAG, "mAttachmentView.getChildCount():" + mAttachmentView.getChildCount());
            // if(mAttachmentView.getChildCount() < 1) {
            mAttachmentView.removeAllViews();
            final View attachmentView = AttachmentPreviewFactory.createAttachmentPreview(
                    layoutInflater, attachment, mAttachmentView,
                    AttachmentPreviewFactory.TYPE_SINGLE, true /* startImageRequest */, this);
            if (attachmentView != null) {
                mAttachmentView.addView(attachmentView);
                if (shouldAnimate) {
                    tryAnimateViewIn(attachment, attachmentView);
                }
            }
            //}
        /* Add by SPRD for bug 563344 Start */
        } else {
            mAttachmentView.removeAllViews();
//            final MessagePartData smilIcon = MessagePartData.createMediaMessagePart(ContentType.IMAGE_PNG,
//                    Uri.parse("android.resource://com.android.messaging/" + R.drawable.smil_mms_icon),
//                    MessagePartData.UNSPECIFIED_SIZE, MessagePartData.UNSPECIFIED_SIZE);
//            final View smilIconView = AttachmentPreviewFactory.createAttachmentPreview(
//                    layoutInflater, smilIcon, mAttachmentView,
//                    AttachmentPreviewFactory.TYPE_SINGLE, true /* startImageRequest */, this);
//            if (smilIconView != null) {
//                mAttachmentView.addView(smilIconView);
//                tryAnimateViewIn(smilIcon, smilIconView);
//            }
            /* Modify by SPRD for bug 578200 Start */
            MessagePartData smilThumbnailData = null;
            String smilText = null;
            for (MessagePartData item : attachments) {
                if (ContentType.TEXT_PLAIN.equals(item.getContentType())) {
                    if (smilText == null) {
                        smilText = item.getText();
                    }
                } else if (!ContentType.APP_SMIL.equals(item.getContentType()) && smilThumbnailData == null) {
                    smilThumbnailData = item;
                }
            }
            if (null != smilThumbnailData) {
                smilThumbnailData.setText(smilText);
            } else {
                smilThumbnailData = MessagePartData.createTextMessagePart(smilText);
            }
            final View smilPreview = AttachmentPreviewFactory.createAttachmentPreview(layoutInflater, smilThumbnailData,
                    mAttachmentView, AttachmentPreviewFactory.TYPE_SMIL, true /* startImageRequest */, this);
            if (smilPreview != null) {
                mAttachmentView.addView(smilPreview);
            }
            /* Modify by SPRD for bug 578200 End */
        }
        /* Add by SPRD for bug 563344 End */
        return true;
    }

    public void onMessageAnimationStart() {
        if (mHideRunnable == null) {
            return;
        }

        // Run the hide animation at the same time as the message animation
        mHandler.removeCallbacks(mHideRunnable);
        setVisibility(View.INVISIBLE);
        mHideRunnable.run();
    }

    static void tryAnimateViewIn(final MessagePartData attachmentData, final View view) {
        if (attachmentData instanceof MediaPickerMessagePartData) {
            final Rect startRect = ((MediaPickerMessagePartData) attachmentData).getStartRect();
            new PopupTransitionAnimation(startRect, view).startAfterLayoutComplete();
        }
    }

    @VisibleForAnimation
    public void setAnimatedHeight(final int animatedHeight) {
        if (mAnimatedHeight != animatedHeight) {
            mAnimatedHeight = animatedHeight;
            requestLayout();
        }
    }

    /**
     * Kicks off an animation to animate the layout change for closing the gap between the
     * message list and the compose message box when the attachments are cleared.
     */
    private void startCloseGapAnimationOnAttachmentClear() {
        // Cancel existing animation.
        cancelCloseGapAnimation();
        mCloseGapAnimator = ObjectAnimator.ofInt(this, "animatedHeight", getHeight(), 0);
        mCloseGapAnimator.start();
    }

    private void cancelCloseGapAnimation() {
        if (mCloseGapAnimator != null) {
            mCloseGapAnimator.cancel();
            mCloseGapAnimator = null;
        }
        mAnimatedHeight = -1;
    }

    private void displayPhoto(final MessagePartData attachment, final Rect viewBoundsOnScreen) {
        if (!(attachment instanceof PendingAttachmentData) && attachment.isImage()) {
            mComposeMessageView.displayPhoto(attachment.getContentUri(), viewBoundsOnScreen);
        }
    }

    private void checkDrmRightsConsume(final MessagePartData attachment, final Rect viewBoundsOnScreen) {
        AlertDialog.Builder builder = MessagingDrmSession.get().showProtectInfo(mContext, attachment.getDrmDataPath(), true/*is picture?*/);

        /* Modify by SPRD for Bug:524873  2015.01.21 Start */
//        builder.setTitle(mContext.getString(R.string.drm_consume_title))
//               .setMessage(mContext.getString(R.string.drm_consume_hint))
        builder.setPositiveButton(mContext.getString(R.string.ok_drm_rights_consume),
        /* Modify by SPRD for Bug:524873  2015.01.21 End */

                new AlertDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            displayPhoto(attachment, viewBoundsOnScreen);
                        } catch (Exception e) {
                            Toast.makeText(
                                    mContext,
                                    mContext.getString(R.string.drm_no_application_open),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel_drm_rights_consume), null).show();
    }

    @Override
    public boolean onAttachmentClick(final MessagePartData attachment,
                                     final Rect viewBoundsOnScreen, final boolean longPress) {
        if (longPress) {
            mComposeMessageView.onAttachmentPreviewLongClicked();
            return true;
        }

        String contentType = attachment.getContentType();
        // Start at bug1570382
        Log.d(TAG, "DRM is not support now!");
        /*
        boolean isDrm = ContentType.isDrmType(contentType);
        Log.d(TAG, " content type is " + contentType + " is drm " + isDrm);
        if (isDrm) {
            //String path = attachment.getDrmDataPath();
            String drmOrigType = attachment.getDrmOrigContentType();
            Log.d(TAG, " orig content type is " + drmOrigType + " path " + attachment.getDrmDataPath());
            if (drmOrigType != null && drmOrigType.startsWith("audio/")) {
                //Log.d(TAG, " orig content type is "+drmOrigType+" path "+attachment.getDrmDataPath());
                return true;
            }
            Log.d(TAG, " content type is " + contentType + " path " + attachment.getDrmDataPath());
            if (attachment.getDrmFileRightsStatus() == false) {
                Factory.get().getUIIntents().launchDrmRightRequestActivity(mContext, attachment);
                return true;
            }
            checkDrmRightsConsume(attachment, viewBoundsOnScreen);
            return true;
        }
        */
        // End at bug1570382
        Log.d(TAG, "======1===vcard====content type is " + contentType);
        if (!(attachment instanceof PendingAttachmentData) && attachment.isImage()) {
            Log.d(TAG, "======2===vcard====content type is " + contentType);
            mComposeMessageView.displayPhoto(attachment.getContentUri(), viewBoundsOnScreen);
            return true;
        }
        Log.d(TAG, "======3===vcard====content type is " + contentType);
        if (!(attachment instanceof PendingAttachmentData)
                && attachment.isVCard()) {
            Log.d(TAG, "======4===vcard====content type is " + contentType);
            final Uri vCardUri = attachment.getContentUri();
            Log.d(TAG, "======5===vcard====content type is " + vCardUri);
            UIIntents.get().launchVCardDetailActivity(mContext, vCardUri);
            return true;
        }

        return false;
    }
}
