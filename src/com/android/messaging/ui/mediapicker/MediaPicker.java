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

package com.android.messaging.ui.mediapicker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.binding.Binding;
import com.android.messaging.datamodel.binding.BindingBase;
import com.android.messaging.datamodel.binding.ImmutableBindingRef;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.DraftMessageData.DraftMessageSubscriptionDataProvider;
import com.android.messaging.datamodel.data.MediaPickerData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.ui.BugleActionBarActivity;
import com.android.messaging.ui.FixedViewPagerAdapter;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.ui.conversation.ConversationActivity;
import com.android.messaging.ui.mediapicker.AlarmAttachPicker.AlarmAttachPickerListener;
import com.android.messaging.ui.mediapicker.AudioAttachPicker.AudioAttachPickerListener;
import com.android.messaging.ui.mediapicker.DocumentImagePicker.SelectionListener;
import com.android.messaging.ui.mediapicker.VcalendarAttachPicker.VcalendarAttachPickerListener;
import com.android.messaging.ui.mediapicker.VcardPicker.VcardPickerListener;
import com.android.messaging.ui.mediapicker.VideoAttachPicker.VideoAttachPickerListener;
import com.android.messaging.util.AccessibilityUtil;
import com.android.messaging.util.Assert;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.UiUtils;
import com.google.common.annotations.VisibleForTesting;
import com.sprd.gallery3d.aidl.IFloatWindowController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Fragment used to select or capture media to be added to the message
 */
public class MediaPicker extends Fragment implements DraftMessageSubscriptionDataProvider {
    /** The listener interface for events from the media picker */
    public interface MediaPickerListener {
        /** Called when the media picker is opened so the host can accommodate the UI */
        void onOpened();

        /**
         * Called when the media picker goes into or leaves full screen mode so the host can
         * accommodate the fullscreen UI
         */
        void onFullScreenChanged(boolean fullScreen);

        /**
         * Called when the user selects one or more items
         * @param items The list of items which were selected
         */
        void onItemsSelected(Collection<MessagePartData> items, boolean dismissMediaPicker);

        /**
         * Called when the user unselects one item.
         */
        void onItemUnselected(MessagePartData item);

        /**
         * Called when the media picker is closed.  Always called immediately after onItemsSelected
         */
        void onDismissed();

        /**
         * Called when media item selection is confirmed in a multi-select action.
         */
        void onConfirmItemSelection();

        /**
         * Called when a pending attachment is added.
         * @param pendingItem the pending attachment data being loaded.
         */
        void onPendingItemAdded(PendingAttachmentData pendingItem);

        void onPendingAlarmAdded(Date date);


        /**
         * Called when a new media chooser is selected.
         */
        void onChooserSelected(final int chooserIndex);

        // spread: fixe for bug 516158 start
        /**
         * Called when a new media is paused.
         */
        void onPaused(boolean onpaused);
        // spread: fixe for bug 516158 end
        void StartSlideshow();
        void onTextContactsAdded(HashMap<String, String> contacts);
    }

    /** The tag used when registering and finding this fragment */
    public static final String FRAGMENT_TAG = "mediapicker";

    // Media type constants that the media picker supports
    public static final int MEDIA_TYPE_DEFAULT     = 0x0000;
    public static final int MEDIA_TYPE_NONE        = 0x0000;
    public static final int MEDIA_TYPE_IMAGE       = 0x0001;
    public static final int MEDIA_TYPE_VIDEO       = 0x0002;
    public static final int MEDIA_TYPE_AUDIO       = 0x0004;
    public static final int MEDIA_TYPE_VCARD       = 0x0008;
    public static final int MEDIA_TYPE_LOCATION    = 0x0010;
    public static final int MEDIA_TYPE_ATTACH_VIDEO = 0x000A;
    public static final int MEDIA_TYPE_ATTACH_AUDIO = 0x000B;
    public static final int MEDIA_TYPE_ATTACH_VCALENDAR = 0x000C;
    public static final int MEDIA_TYPE_SLIDESHOW = 0x000D;
    public static final int MEDIA_TYPE_ALARM       = 0x0010;
    private static final int MEDA_TYPE_INVALID     = 0x0020;
    public static final int MEDIA_TYPE_ALL         = 0xFFFF;

    /** The listener to call when events occur */
    private MediaPickerListener mListener;

    /** The handler used to dispatch calls to the listener */
    private Handler mListenerHandler;

    /** The bit flags of media types supported */
    private int mSupportedMediaTypes;

    /** The list of choosers which could be within the media picker */
    private final MediaChooser[] mChoosers;

    /** The list of currently enabled choosers */
    private final ArrayList<MediaChooser> mEnabledChoosers;

    /** The currently selected chooser */
    private MediaChooser mSelectedChooser;

    /** The main panel that controls the custom layout */
    private MediaPickerPanel mMediaPickerPanel;

    /** The linear layout that holds the icons to select individual chooser tabs */
    private LinearLayout mTabStrip;

    /** The view pager to swap between choosers */
    private ViewPager mViewPager;

    /** The current pager adapter for the view pager */
    private FixedViewPagerAdapter<MediaChooser> mPagerAdapter;

    /** True if the media picker is visible */
    private boolean mOpen;

    /** The theme color to use to make the media picker match the rest of the UI */
    private int mThemeColor;

    @VisibleForTesting
    final Binding<MediaPickerData> mBinding = BindingBase.createBinding(this);

    /** Handles picking image from the document picker */
    private DocumentImagePicker mDocumentImagePicker;

    private VcardPicker mVcardPicker;
    private VideoAttachPicker mVideoAttachPicker;
    private AudioAttachPicker mAudioAttachPicker;
    private VcalendarAttachPicker mVcalendarAttachPicker;
    private AlarmAttachPicker mAlarmAttachPicker;
    /** Provides subscription-related data to access per-subscription configurations. */
    private DraftMessageSubscriptionDataProvider mSubscriptionDataProvider;

    /** Provides access to DraftMessageData associated with the current conversation */
    private ImmutableBindingRef<DraftMessageData> mDraftMessageDataModel;

    /* Add for bug 525151 Start */
    private boolean mIsRequestingPermission = false;
    private final String IS_REQUESTING_PERMISSION_KEY = "MediaPicker.mIsRequestingPermission";
    /* Add for bug 525151 End */


    private Animation.AnimationListener mOpenListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
            if (mSelectedChooser instanceof CameraMediaChooser){
                ((CameraMediaChooser) mSelectedChooser).onAnimationEnd();
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    };

    public MediaPicker() {
        this(Factory.get().getApplicationContext());
    }
    public MediaPicker(final Context context, final boolean noslide) {
        mBinding.bind(DataModel.get().createMediaPickerData(context));
        mEnabledChoosers = new ArrayList<MediaChooser>();
        System.out.println("mediapicker no slide chooser");
        if (noslide) {
            mChoosers = new MediaChooser[] {
                        new CameraMediaChooser(this,context),
                        new GalleryMediaChooser(this),
                        new AudioMediaChooser(this),
                        new VideoAttachMediaChooser(this),
                        new AudioAttachMediaChooser(this),
            };
        } else {
            if (MmsConfig.osSupportDelayedSending()) {
                mChoosers = new MediaChooser[] {
                        new CameraMediaChooser(this, context),
                        new GalleryMediaChooser(this),
                        new AudioMediaChooser(this),
                        new VideoAttachMediaChooser(this),
                        new AudioAttachMediaChooser(this),
                        new VcardMediaChooser(this),
                        //new SlideshowMediaChooser(this),
                        new AlarmAttachMediaChooser(this)
                };
            } else {
                mChoosers = new MediaChooser[]{
                        new CameraMediaChooser(this, context),
                        new GalleryMediaChooser(this),
                        new AudioMediaChooser(this),
                        new VideoAttachMediaChooser(this),
                        new AudioAttachMediaChooser(this),
                        new VcardMediaChooser(this),
                        //new SlideshowMediaChooser(this)
                };
            }
        }
        mOpen = false;
        setSupportedMediaTypes(MEDIA_TYPE_ALL);
    }
    public MediaPicker(final Context context) {
        mBinding.bind(DataModel.get().createMediaPickerData(context));
        mEnabledChoosers = new ArrayList<MediaChooser>();
        System.out.println("mediapicker slide  chooser");
        if (MmsConfig.osSupportDelayedSending()) {
            mChoosers = new MediaChooser[]{
                    //spread: add for audio focus function listener start
                    new CameraMediaChooser(this, context),
                    //spread: add for audio focus function listener end
                    new GalleryMediaChooser(this),
                    new AudioMediaChooser(this),
                    new VideoAttachMediaChooser(this),
                    new AudioAttachMediaChooser(this),
                    new VcardMediaChooser(this),
                    //new SlideshowMediaChooser(this),
                    new AlarmAttachMediaChooser(this),
                    //  new VcalendarAttachMediaChooser(this),
            };
        } else {
            mChoosers = new MediaChooser[]{
                    //spread: add for audio focus function listener start
                    new CameraMediaChooser(this, context),
                    //spread: add for audio focus function listener end
                    new GalleryMediaChooser(this),
                    new AudioMediaChooser(this),
                    new VideoAttachMediaChooser(this),
                    new AudioAttachMediaChooser(this),
                    new VcardMediaChooser(this),
                    //new SlideshowMediaChooser(this),
                    //  new VcalendarAttachMediaChooser(this),
            };
        }
        mOpen = false;
        setSupportedMediaTypes(MEDIA_TYPE_ALL);
    }

    private boolean mIsAttached;
    private int mStartingMediaTypeOnAttach = MEDA_TYPE_INVALID;
    private boolean mAnimateOnAttach;

    @Override
    public void onAttach (final Activity activity) {
        super.onAttach(activity);
        mIsAttached = true;
        if (mStartingMediaTypeOnAttach != MEDA_TYPE_INVALID) {
            // open() was previously called. Do the pending open now.
            doOpen(mStartingMediaTypeOnAttach, mAnimateOnAttach);
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /* Add for bug 525151 Start */
        if (null != savedInstanceState) {
            mIsRequestingPermission = savedInstanceState.getBoolean(IS_REQUESTING_PERMISSION_KEY);
        }
        /* Add for bug 525151 End */
        mBinding.getData().init(getLoaderManager());
        mDocumentImagePicker = new DocumentImagePicker(this,
                new SelectionListener() {
            @Override
            public void onDocumentSelected(final PendingAttachmentData data) {
                if (mBinding.isBound()) {
                    dispatchPendingItemAdded(data);
                }
            }
        });

        if(getActivity() instanceof ConversationActivity){
            VcardPickerListener listener = ((ConversationActivity)getActivity()).getVcardPickerListener();
            mVcardPicker = new VcardPicker(this,listener);
        }else{
            mVcardPicker = new VcardPicker(this, new VcardPickerListener() {
            @Override
            public void onVcardSelected(final PendingAttachmentData data) {
                if (mBinding.isBound()) {
                    //add for bug 774769  start
                   // dispatchPendingItemAdded(data);
                    dispatchVcardPendingItemAdded(data);
                    //add for bug 774769  end
                }
            }
            @Override
            public void onTextSelected(final HashMap<String, String> contacts) {
                if (mBinding.isBound()) {
                    dispatchPendingTextContactsAdded(contacts);
                    Log.d("wenbo","MediaPicker  onTextSelected()");
                }
            }
        });
        }
        mVideoAttachPicker = new VideoAttachPicker(this,
                new VideoAttachPickerListener() {
                    @Override
                    public void onVideoAttachSelected(
                            final PendingAttachmentData data) {
                        if (mBinding.isBound()) {
                            dispatchPendingItemAdded(data);
                        }
                    }
                });
        mAudioAttachPicker = new AudioAttachPicker(this,
                new AudioAttachPickerListener() {
                    @Override
                    public void onAudioAttachSelected(
                            final PendingAttachmentData data) {
                        if (mBinding.isBound()) {
                            dispatchPendingItemAdded(data);
                        }
                    }
                });
        mAlarmAttachPicker = new AlarmAttachPicker(this,
                new AlarmAttachPickerListener() {
                    @Override
                    public void onAlarmAttachSelected(
                            final Date data) {
                           if (mBinding.isBound()) {
                            dispatchAlarmAdded(data);
                        }
                    }
                });
        mVcalendarAttachPicker = new VcalendarAttachPicker(this,
                new VcalendarAttachPickerListener() {
                    @Override
                    public void onVcalendarAttachSelected(
                            final PendingAttachmentData data) {
                if (mBinding.isBound()) {
                    dispatchPendingItemAdded(data);
                }
            }
        });
        mServiceBinded =false;
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState) {
        mMediaPickerPanel = (MediaPickerPanel) inflater.inflate(
                R.layout.mediapicker_fragment,
                container,
                false);
        mMediaPickerPanel.setMediaPicker(this);
        mTabStrip = (LinearLayout) mMediaPickerPanel.findViewById(R.id.mediapicker_tabstrip);
        mTabStrip.setBackgroundColor(mThemeColor);
        for (final MediaChooser chooser : mChoosers) {
            chooser.onCreateTabButton(inflater, mTabStrip);
            final boolean enabled = (chooser.getSupportedMediaTypes() & mSupportedMediaTypes) !=
                    MEDIA_TYPE_NONE;
            final ImageButton tabButton = chooser.getTabButton();
            if (tabButton != null) {
                tabButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
                mTabStrip.addView(tabButton);
            }
        }

        /* And by SPRD for Bug:523092  2016.01.12 Start */
        // from onAttach -> doOpen, mSelectedChooser was set.
        // Handle init state.
        setMediaChooserTabButtonImage(mSelectedChooser, true);
        /* And by SPRD for Bug:523092  2016.01.12 End */

        mViewPager = (ViewPager) mMediaPickerPanel.findViewById(R.id.mediapicker_view_pager);
        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(
                    final int position,
                    final float positionOffset,
                    final int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                // The position returned is relative to if we are in RtL mode. This class never
                // switches the indices of the elements if we are in RtL mode so we need to
                // translate the index back. For example, if the user clicked the item most to the
                // right in RtL mode we would want the index to appear as 0 here, however the
                // position returned would the last possible index.
                if (UiUtils.isRtlMode()) {
                    position = mEnabledChoosers.size() - 1 - position;
                }
                selectChooser(mEnabledChoosers.get(position));
            }

            @Override
            public void onPageScrollStateChanged(final int state) {
            }
        });
        // Camera initialization is expensive, so don't realize offscreen pages if not needed.
        mViewPager.setOffscreenPageLimit(0);
        mViewPager.setAdapter(mPagerAdapter);
        final boolean isTouchExplorationEnabled = AccessibilityUtil.isTouchExplorationEnabled(
                getActivity());
        mMediaPickerPanel.setFullScreenOnly(isTouchExplorationEnabled);
        mMediaPickerPanel.setAnimationListener(mOpenListener);
        if (mSelectedChooser instanceof CameraMediaChooser){
            ((CameraMediaChooser) mSelectedChooser).onAnimationOpen();
        }
        if (mSelectedChooser instanceof GalleryMediaChooser) {//by 1186186
            mMediaPickerPanel.setAnimationEventListener((GalleryMediaChooser) mSelectedChooser);
        }
        mMediaPickerPanel.setExpanded(mOpen, true, mEnabledChoosers.indexOf(mSelectedChooser));
        return mMediaPickerPanel;
    }

    /* Add for bug 525151 Start */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_REQUESTING_PERMISSION_KEY, mIsRequestingPermission);
    }
    /* Add for bug 525151 End */

    @Override
    public void onPause() {
        super.onPause();
        if(mSelectedChooser != null && mSelectedChooser instanceof CameraMediaChooser){
           mSelectedChooser.onRecordStopByPause();
        }
        MediaCameraManager.get().onPause();
        for (final MediaChooser chooser : mEnabledChoosers) {
            chooser.onPause();
        }

        if(mListener != null){
             mListener.onPaused(true);
        }
        // spread: fixe for bug 516158 end
    }

    @Override
    public void onResume() {
        super.onResume();
        MediaCameraManager.get().onResume();
        for (final MediaChooser chooser : mEnabledChoosers) {
            chooser.onResume();
        }
        // spread: fixe for bug 516158 start
        if(mListener != null){
             mListener.onPaused(false);
        }
        // spread: fixe for bug 516158 end
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // mDocumentImagePicker.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UIIntents.REQUEST_PICK_IMAGE_FROM_DOCUMENT_PICKER) {
            mDocumentImagePicker
                    .onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == UIIntents.REQUEST_PICK_VCARD_PICKER || requestCode == UIIntents.REQUEST_PICK_CONTACT_AS_TEXT) {
            mVcardPicker.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == UIIntents.REQUEST_PICK_VIDEO_PICKER) {
            mVideoAttachPicker.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == UIIntents.REQUEST_PICK_AUDIO_PICKER) {
            mAudioAttachPicker.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == UIIntents.REQUEST_PICK_ALARM_PICKER) {
            mAlarmAttachPicker.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode == UIIntents.REQUEST_PICK_VCALENDAR_PICKER) {
            mVcalendarAttachPicker.onActivityResult(requestCode, resultCode,
                    data);
        }
    }

    //for bug671490  begin
    @Override
    public void onStop() {
        mAlarmAttachPicker.closeAllAlarmDialog();
        super.onStop();
    }
    //for bug671490  end

    @Override
    public void onDestroy() {
        super.onDestroy();
        MediaCameraManager.get().onDestroy();
        mBinding.unbind();
    }

    /**
     * Sets the theme color to make the media picker match the surrounding UI
     * @param themeColor The new theme color
     */
    public void setConversationThemeColor(final int themeColor) {
        mThemeColor = themeColor;
        if (mTabStrip != null) {
            mTabStrip.setBackgroundColor(mThemeColor);
        }

        for (final MediaChooser chooser : mEnabledChoosers) {
            chooser.setThemeColor(mThemeColor);
        }
    }

    /**
     * Gets the current conversation theme color.
     */
    public int getConversationThemeColor() {
        return mThemeColor;
    }

    public void setDraftMessageDataModel(final BindingBase<DraftMessageData> draftBinding) {
        mDraftMessageDataModel = Binding.createBindingReference(draftBinding);
    }

    public ImmutableBindingRef<DraftMessageData> getDraftMessageDataModel() {
        return mDraftMessageDataModel;
    }

    public void setSubscriptionDataProvider(final DraftMessageSubscriptionDataProvider provider) {
        mSubscriptionDataProvider = provider;
    }

    @Override
    public int getConversationSelfSubId() {
        return mSubscriptionDataProvider.getConversationSelfSubId();
    }

    /**
     * Opens the media picker and optionally shows the chooser for the supplied media type
     * @param startingMediaType The media type of the chooser to open if {@link #MEDIA_TYPE_DEFAULT}
     *                          is used, then the default chooser from saved shared prefs is opened
     */
    public void open(final int startingMediaType, final boolean animate) {
        mOpen = true;
        if (mIsAttached) {
            doOpen(startingMediaType, animate);
        } else {
            // open() can get called immediately after the MediaPicker is created. In that case,
            // we defer doing work as it may require an attached fragment (eg. calling
            // Fragment#requestPermission)
            mStartingMediaTypeOnAttach = startingMediaType;
            mAnimateOnAttach = animate;
        }
    }

    private void doOpen(int startingMediaType, final boolean animate) {
        final boolean isTouchExplorationEnabled = AccessibilityUtil.isTouchExplorationEnabled(
                // getActivity() will be null at this point
                Factory.get().getApplicationContext());
        // If no specific starting type is specified (i.e. MEDIA_TYPE_DEFAULT), try to get the
        // last opened chooser index from shared prefs.
        if (startingMediaType == MEDIA_TYPE_DEFAULT) {
            final int selectedChooserIndex = mBinding.getData().getSelectedChooserIndex();
            if (selectedChooserIndex >= 0 && selectedChooserIndex < mEnabledChoosers.size()) {
                selectChooser(mEnabledChoosers.get(selectedChooserIndex));
            } else {
                // This is the first time the picker is being used
                if (isTouchExplorationEnabled) {
                    // Accessibility defaults to audio attachment mode.

                    /* Modify by SPRD for Bug:523092  2015.01.12 Start */
//                    startingMediaType = MEDIA_TYPE_AUDIO;
                    startingMediaType = MEDIA_TYPE_VIDEO;
                    /* Modify by SPRD for Bug:523092  2015.01.12 end */

                }
            }
        }

        if (mSelectedChooser == null) {
            for (final MediaChooser chooser : mEnabledChoosers) {
                if (startingMediaType == MEDIA_TYPE_DEFAULT ||
                        (startingMediaType & chooser.getSupportedMediaTypes()) != MEDIA_TYPE_NONE) {
                    selectChooser(chooser);
                    break;
                }
            }
        }

        if (mSelectedChooser == null) {
            // Fall back to the first chooser.
            selectChooser(mEnabledChoosers.get(0));
        }

        if (mMediaPickerPanel != null) {
            mMediaPickerPanel.setFullScreenOnly(isTouchExplorationEnabled);
            if (mSelectedChooser instanceof CameraMediaChooser){
                if (animate)((CameraMediaChooser) mSelectedChooser).onAnimationOpen();
            }
            mMediaPickerPanel.setAnimationListener(mOpenListener);
            mMediaPickerPanel.setExpanded(true, animate,
                    mEnabledChoosers.indexOf(mSelectedChooser));
        }
    }

    /** @return True if the media picker is open */
    public boolean isOpen() {
        return mOpen;
    }

    /**
     * Sets the list of media types to allow the user to select
     * @param mediaTypes The bit flags of media types to allow.  Can be any combination of the
     *                   MEDIA_TYPE_* values
     */
    void setSupportedMediaTypes(final int mediaTypes) {
        mSupportedMediaTypes = mediaTypes;
        mEnabledChoosers.clear();
        boolean selectNextChooser = false;
        for (final MediaChooser chooser : mChoosers) {
            final boolean enabled = (chooser.getSupportedMediaTypes() & mSupportedMediaTypes) !=
                    MEDIA_TYPE_NONE;
            if (enabled) {
                // TODO Add a way to inform the chooser which media types are supported
                mEnabledChoosers.add(chooser);
                if (selectNextChooser) {
                    selectChooser(chooser);
                    selectNextChooser = false;
                }
            } else if (mSelectedChooser == chooser) {
                selectNextChooser = true;
            }
            final ImageButton tabButton = chooser.getTabButton();
            if (tabButton != null) {
                tabButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
            }
        }

        if (selectNextChooser && mEnabledChoosers.size() > 0) {
            selectChooser(mEnabledChoosers.get(0));
        }
        final MediaChooser[] enabledChoosers = new MediaChooser[mEnabledChoosers.size()];
        mEnabledChoosers.toArray(enabledChoosers);
        mPagerAdapter = new FixedViewPagerAdapter<MediaChooser>(enabledChoosers);
        if (mViewPager != null) {
            mViewPager.setAdapter(mPagerAdapter);
        }

        // Only rebind data if we are currently bound. Otherwise, we must have not
        // bound to any data yet and should wait until onCreate() to bind data.
        if (mBinding.isBound() && getActivity() != null) {
            mBinding.unbind();
            mBinding.bind(DataModel.get().createMediaPickerData(getActivity()));
            mBinding.getData().init(getLoaderManager());
        }
    }

    ViewPager getViewPager() {
        return mViewPager;
    }

    /** Hides the media picker, and frees up any resources it’s using */
    public void dismiss(final boolean animate) {
        ////add for bug 635659 --begin
        Log.d(FRAGMENT_TAG , "dismiss -mServiceBinded ="+mServiceBinded);
        mOpen = false;
        if (mMediaPickerPanel != null) {
            mMediaPickerPanel.setExpanded(false, animate, MediaPickerPanel.PAGE_NOT_SET);
        }
        if (mServiceBinded){
            mContext.unbindService(mIFloatWindowServiceConn);
            mServiceBinded=false;
            getActivity().setDisablePreviewScreenshots(false);
        }
        mSelectedChooser = null;
        //add for bug 635659 --end
    }

    /**
     * Sets the listener for the media picker events
     * @param listener The listener which will receive events
     */
    public void setListener(final MediaPickerListener listener) {
        Assert.isMainThread();
        mListener = listener;
        mListenerHandler = listener != null ? new Handler() : null;
    }

    /** @return True if the media picker is in full-screen mode */
    public boolean isFullScreen() {
        return mMediaPickerPanel != null && mMediaPickerPanel.isFullScreen();
    }

    public void setFullScreen(final boolean fullScreen) {
        mMediaPickerPanel.setFullScreenView(fullScreen, true);
    }

    public void updateActionBar(final ActionBar actionBar) {
        if (getActivity() == null) {
            return;
        }
        if (isFullScreen() && mSelectedChooser != null) {
            mSelectedChooser.updateActionBar(actionBar);
        } else {
            actionBar.hide();
        }
    }
    //add for bug 635659 --begin
    private Context mContext =Factory.get().getApplicationContext();
    private boolean mServiceBinded;//add for bug 651659
    private void isCamera(final int index){
        Intent floatWindowIntent = new Intent();
        floatWindowIntent.setAction("android.gallery3d.action.FloatWindowAIDLService");/* intent->gallery3d modified for bug768591*/
        floatWindowIntent.setPackage("com.android.gallery3d");
        if (index==0){
            mServiceBinded = mContext.bindService(floatWindowIntent, mIFloatWindowServiceConn, Context.BIND_AUTO_CREATE);
            Log.d("mediapicker","isCamera-mServiceBinded ="+mServiceBinded);
            getActivity().setDisablePreviewScreenshots(true);
        }else if (mServiceBinded){
            mContext.unbindService(mIFloatWindowServiceConn);
            mServiceBinded = false;
            getActivity().setDisablePreviewScreenshots(false);
        }
    }

    private ServiceConnection mIFloatWindowServiceConn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mServiceBinded = false;
            Log.d("mediapicker","onServiceDisconnected-mServiceBinded ="+mServiceBinded);
            // TODO Auto-generated method stub
        }

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            // TODO Auto-generated method stub
            IFloatWindowController iFloatWindowController = IFloatWindowController.Stub.asInterface(arg1);
            try {
                if (iFloatWindowController.closeFloatWindow()){
                    Toast.makeText(mContext, R.string.close_float_video,Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    };
    //add for bug 635659 --end
    /**
     * Selects a new chooser
     * @param newSelectedChooser The newly selected chooser
     */
    void selectChooser(final MediaChooser newSelectedChooser) {
        if (mSelectedChooser == newSelectedChooser) {
            return;
        }


        if (mSelectedChooser != null) {
            mSelectedChooser.setSelected(false);
        } else {
            // May be the mEnabledChoosers set has changed,
            // so reset other choosers to avoid that some choosers
            // are selected.
            for (final MediaChooser chooser : mEnabledChoosers) {
                if (chooser != newSelectedChooser) {
                    chooser.setSelected(false);
                    setMediaChooserTabButtonImage(chooser, false);
                }
            }
        }
        MediaChooser lastSelectedChooser = mSelectedChooser;
        mSelectedChooser = newSelectedChooser;
        if (mSelectedChooser != null) {
            mSelectedChooser.setSelected(true);
        }

        final int chooserIndex = mEnabledChoosers.indexOf(mSelectedChooser);
        //add for bug 635659 --begin
        Log.d(FRAGMENT_TAG,"chooserIndex ="+chooserIndex);
        isCamera(chooserIndex);
        //add for bug 635659 --end
        if (mViewPager != null) {
            mViewPager.setCurrentItem(chooserIndex, true /* smoothScroll */);

            /* And by SPRD for Bug:523092  2016.01.12 Start */
            // Handle user select state
            setMediaChooserTabButtonImage(lastSelectedChooser, false);
            setMediaChooserTabButtonImage(mSelectedChooser, true);
            /* And by SPRD for Bug:523092  2016.01.12 End */
        }

        if (isFullScreen()) {
            invalidateOptionsMenu();
        }

        // Save the newly selected chooser's index so we may directly switch to it the
        // next time user opens the media picker.
        mBinding.getData().saveSelectedChooserIndex(chooserIndex);
        if (mMediaPickerPanel != null) {
            mMediaPickerPanel.onChooserChanged();
        }
        dispatchChooserSelected(chooserIndex);
    }

    public boolean canShowIme() {
        if (mSelectedChooser != null) {
            return mSelectedChooser.canShowIme();
        }
        return false;
    }

    public boolean onBackPressed() {
        return mSelectedChooser != null && mSelectedChooser.onBackPressed();
    }

    void invalidateOptionsMenu() {
        ((BugleActionBarActivity) getActivity()).supportInvalidateOptionsMenu();
    }

    void dispatchOpened() {
        setHasOptionsMenu(false);
        mOpen = true;
        mPagerAdapter.notifyDataSetChanged();
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onOpened();
                }
            });
        }
        if (mSelectedChooser != null) {
            mSelectedChooser.onFullScreenChanged(false);
            mSelectedChooser.onOpenedChanged(true);
        }
    }

    void dispatchDismissed() {
        setHasOptionsMenu(false);
        mOpen = false;
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                  mListener.onDismissed();
                }
            });
        }
        if (mSelectedChooser != null) {
            mSelectedChooser.onOpenedChanged(false);
        }
    }

    void dispatchFullScreen(final boolean fullScreen) {
        setHasOptionsMenu(fullScreen);
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onFullScreenChanged(fullScreen);
                }
            });
        }
        if (mSelectedChooser != null) {
            mSelectedChooser.onFullScreenChanged(fullScreen);
        }
    }

    void dispatchItemsSelected(final MessagePartData item, final boolean dismissMediaPicker) {
        final List<MessagePartData> items = new ArrayList<MessagePartData>(1);
        items.add(item);
        dispatchItemsSelected(items, dismissMediaPicker);
    }

    void dispatchItemsSelected(final Collection<MessagePartData> items,
            final boolean dismissMediaPicker) {
        if (mListener != null) {
            mListenerHandler.postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    mListener.onItemsSelected(items, dismissMediaPicker);
                }
            });
        }

        if (isFullScreen() && !dismissMediaPicker) {
            invalidateOptionsMenu();
        }
    }

    void dispatchItemUnselected(final MessagePartData item) {
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onItemUnselected(item);
                }
            });
        }

        if (isFullScreen()) {
            invalidateOptionsMenu();
        }
    }

    void dispatchConfirmItemSelection() {
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConfirmItemSelection();
                }
            });
        }
    }

    void dispatchPendingItemAdded(final PendingAttachmentData pendingItem) {
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onPendingItemAdded(pendingItem);
                }
            });
        }

        if (isFullScreen()) {
            invalidateOptionsMenu();
        }
    }

    void dispatchAlarmAdded(final Date date) {
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onPendingAlarmAdded(date);
                }
            });
        }

        if (isFullScreen()) {
            invalidateOptionsMenu();
        }
    }
      void dispatchPendingTextContactsAdded(final HashMap<String, String> contacts) {
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("wenbo","dispatchPendingTextContactsAdded");
                    mListener.onTextContactsAdded(contacts);
                }
            });
        }

        if (isFullScreen()) {
            invalidateOptionsMenu();
        }
    }

    void dispatchChooserSelected(final int chooserIndex) {
        if (mListener != null) {
            mListenerHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onChooserSelected(chooserIndex);
                }
            });
        }
    }

    public boolean canSwipeDownChooser() {
        return mSelectedChooser == null ? false : mSelectedChooser.canSwipeDown();
    }

    public boolean isChooserHandlingTouch() {
        return mSelectedChooser == null ? false : mSelectedChooser.isHandlingTouch();
    }

    public void stopChooserTouchHandling() {
        if (mSelectedChooser != null) {
            mSelectedChooser.stopTouchHandling();
        }
    }

    boolean getChooserShowsActionBarInFullScreen() {
        return mSelectedChooser == null ? false : mSelectedChooser.getActionBarTitleResId() != 0;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (mSelectedChooser != null) {
            mSelectedChooser.onCreateOptionsMenu(inflater, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return (mSelectedChooser != null && mSelectedChooser.onOptionsItemSelected(item)) ||
                super.onOptionsItemSelected(item);
    }

    PagerAdapter getPagerAdapter() {
        return mPagerAdapter;
    }

    public void resetViewHolderState() {
        mPagerAdapter.resetState();
    }

    /**
     * Launch an external picker to pick item from document picker as attachment.
     */
    public void launchDocumentPicker() {
        //sprd 600520 start
        if (GlobleUtil.isSmilAttament) {
            GlobleUtil.isSmilAttamentAction = true;
        }
        //sprd 600520 end
        mDocumentImagePicker.launchPicker();
    }

    public void launchVcardPicker() {
        final LinearLayout mCreateDialog = (LinearLayout) this.getActivity().getLayoutInflater().inflate(
                    R.layout.contact_dialog, null);
                TextView mAsText = (TextView) mCreateDialog.findViewById(R.id.m_asText);
                TextView mAsVcard = (TextView) mCreateDialog.findViewById(R.id.m_asVcard);
                Context LaunchContext=(Context) this.getActivity();
                dismiss(false);//for bug 819401
                final AlertDialog dialog = new AlertDialog.Builder(LaunchContext)
                    .setView(mCreateDialog)
                    .setTitle(R.string.add_contacts_dialog_title)
                    .create();
                    dialog.show();

                mAsText.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    mVcardPicker.launchTextPicker();
                    }
                });
                mAsVcard.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (dialog != null) {
                        dialog.dismiss();
                     }
                    mVcardPicker.launchPicker();
                }
            });
    }
    public void launchVideoAttachPicker() {
        mVideoAttachPicker.launchPicker();
    }
    public void launchAudioAttachPicker() {
        mAudioAttachPicker.launchPicker();
    }
    public void launchVcalendarAttachPicker() {
        mVcalendarAttachPicker.launchPicker();
    }
	//613227
    public void launchAlarmDateAttachPicker(TextView tv) {
        mAlarmAttachPicker.launchDatePicker(tv);
    }

    public void launchAlarmTimeAttachPicker(TextView tv) {
        mAlarmAttachPicker.launchTimePicker(tv);
    }

    public void PickerSetMessageAlarmTime(final boolean set) {
        mAlarmAttachPicker.SetMessageAlarmTime(set);
    }

    public void ChangeMessageAlarmTime(final Date date) {
        mAlarmAttachPicker.ChangeAlarmTime(date);
    }

    public void deliverConfigurationChanged(Configuration newConfig,TextView dateTv,TextView timeTv){
       mAlarmAttachPicker.deliverConfigurationChanged(newConfig,dateTv,timeTv);
    }

    public void launchSlideshowPicker() {
        mListener.StartSlideshow();
    }
    public ImmutableBindingRef<MediaPickerData> getMediaPickerDataBinding() {
        return BindingBase.createBindingReference(mBinding);
    }

    protected static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    protected static final int LOCATION_PERMISSION_REQUEST_CODE = 2;
    protected static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 3;
    protected static final int GALLERY_PERMISSION_REQUEST_CODE = 4;

    /* Add for bug 525151 Start */
    public void requestPermissionsFromMediaPicker(String[] permissions, int requestCode) {
        if (mIsRequestingPermission) {
            return;
        } else {
            mIsRequestingPermission = true;
            //sprd 600520 start
            if (GlobleUtil.isSmilAttament) {
                GlobleUtil.isSmilAttamentAction = true;
            }
            //sprd 600520 end
            requestPermissions(permissions, requestCode);
        }
    }
    /* Add for bug 525151 End */

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String permissions[], final int[] grantResults) {
        if (mSelectedChooser != null) {
            //sprd 600520 start
            if (GlobleUtil.isSmilAttament) {
                GlobleUtil.isSmilAttamentAction = true;
            }
            //sprd 600520 end
            mSelectedChooser.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        // Add for bug 525151
        mIsRequestingPermission = false;
        //add for bug 642547 --begin
        if (permissions != null && permissions.length > 0
			&& permissions[0].equals(Manifest.permission.CAMERA) && grantResults[0] == 0 ){
            Log.d(FRAGMENT_TAG,"onRequestPermissionsResult - mServiceBinded ="+mServiceBinded);
            if (mServiceBinded){
                mContext.unbindService(mIFloatWindowServiceConn);
                mServiceBinded=false;
            }
            isCamera(0);
        }
        //add for bug 642547 --end
    }


    /* And by SPRD for Bug:523092  2016.01.12 Start */
    public void setMediaChooserTabButtonImage(MediaChooser chooser, boolean isLight) {
        if(chooser != null) {
            ImageButton tabButton = chooser.getTabButton();
            if(tabButton != null) {
                tabButton.setImageResource(chooser.getIconResource()[isLight ? 0 : 1]);
            }
        }
    }
    /* And by SPRD for Bug:523092  2016.01.12 End */

    //add for bug 774769  start
    void dispatchVcardPendingItemAdded(final PendingAttachmentData pendingItem) {
        if (mListener != null) {
            mListener.onPendingItemAdded(pendingItem);
        }

        if (isFullScreen()) {
            invalidateOptionsMenu();
        }
    }
    //add for bug 774769  end
}
