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
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.Chronometer;
import android.widget.ImageButton;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.MediaPickerMessagePartData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.ui.mediapicker.MediaCameraManager.MediaCallback;
import com.android.messaging.ui.mediapicker.camerafocus.RenderOverlay;
import com.android.messaging.util.Assert;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.MediaUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.UiUtils;
import java.util.List;
import java.util.ArrayList;
/**
 * Chooser which allows the user to take pictures or video without leaving the current app/activity
 */
class CameraMediaChooser extends MediaChooser implements
        MediaCameraManager.CameraManagerListener {
    private static final String TAG = "CameraMediaChooser";
    private CameraPreview.CameraPreviewHost mCameraPreviewHost;
    private ImageButton mFullScreenButton;
    private ImageButton mSwapCameraButton;
    private ImageButton mSwapModeButton;
    private ImageButton mCaptureButton;
    private ImageButton mCancelVideoButton;
    private Chronometer mVideoCounter;
    private boolean mVideoCancelled;
    private boolean mStopByPause = false;
    private int mErrorToast;
    /* SPRD: add for Bug 497188 begin */
    private int mCameraErrorCode;
    /* SPRD: add for Bug 497188 end */
    private View mEnabledView;
    private View mButtonContainer;
    private View mUnEnableView;
    private View mMissingPermissionView;

    private boolean isPaused = false;

    //spread: add for audio focus function listener start
    private AudioManager mAudioManager;
    private Context mContext;
    //spread: add for audio focus function listener end

    CameraMediaChooser(final MediaPicker mediaPicker ,Context context) {
        super(mediaPicker);
        mContext = context;
        //spread: add for audio focus function listener start
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        //spread: add for audio focus function listener end
    }

    @Override
    public int getSupportedMediaTypes() {
        if (MediaCameraManager.get().hasAnyCamera()) {
            return MediaPicker.MEDIA_TYPE_IMAGE | MediaPicker.MEDIA_TYPE_VIDEO;
        } else {
            return MediaPicker.MEDIA_TYPE_NONE;
        }
    }

    @Override
    public View destroyView() {
        MediaCameraManager.get().closeCamera();
        MediaCameraManager.get().setListener(null);
        MediaCameraManager.get().setSubscriptionDataProvider(null);
        return super.destroyView();
    }

    private class AnimationAcceleratorViews {
        public View mEnabledView;
        public View mPreviewView;
        public View mRenderView;
        public void setVisibility(int visibility){
            mEnabledView.setVisibility(visibility);
            mPreviewView.setVisibility(visibility);
            mRenderView.setVisibility(visibility);
        }
    };

    private AnimationAcceleratorViews mAcceleratorViews;
    /*bug 1145485, begin*/
    private long swapModeTime;
    private final long IGNORE_INTERVAL_MS = 500;
    /*bug 1145485, end*/

    @Override
    protected View createView(final ViewGroup container) {
        Log.d(TAG,"createView");
        /*bug 1145485, begin*/
        swapModeTime = 0;
        /*bug 1145485, end*/
        MediaCameraManager.get().setListener(this);
        MediaCameraManager.get().setSubscriptionDataProvider(this);
        MediaCameraManager.get().setVideoMode(false);
        /*bug 1121918,begin*/
        MediaCameraManager.get().setStartRecordingFlag(false);
        /*bug 1121918, end*/
        final LayoutInflater inflater = getLayoutInflater();
        final CameraMediaChooserView view = (CameraMediaChooserView) inflater.inflate(
                R.layout.mediapicker_camera_chooser,
                container /* root */,
                false /* attachToRoot */);
        mCameraPreviewHost = (CameraPreview.CameraPreviewHost) view.findViewById(
                R.id.camera_preview);
        mCameraPreviewHost.getView().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent motionEvent) {
                if (MediaCameraManager.get().isVideoMode()) {
                    // Prevent the swipe down in video mode because video is always captured in
                    // full screen
                    return true;
                }

                return false;
            }
        });

        final View shutterVisual = view.findViewById(R.id.camera_shutter_visual);

        mFullScreenButton = (ImageButton) view.findViewById(R.id.camera_fullScreen_button);
        mFullScreenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                mMediaPicker.setFullScreen(true);
            }
        });

        mSwapCameraButton = (ImageButton) view.findViewById(R.id.camera_swapCamera_button);
        mSwapCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                /*bug 1145485, begin*/
                final boolean ignoreEvent = (SystemClock.elapsedRealtime() - swapModeTime < IGNORE_INTERVAL_MS);
                if(ignoreEvent || MediaCameraManager.get().getStartRecordingFlag()){
                    return;
                }
                /*bug 1145485, end*/
                /* SPRD: add for Bug 497188 begin */
                mCameraErrorCode = 0;
                /* SPRD: add for Bug 497188 end */
                MediaCameraManager.get().swapCamera();
            }
        });

        mCaptureButton = (ImageButton) view.findViewById(R.id.camera_capture_button);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final float heightPercent = Math.min(mMediaPicker.getViewPager().getHeight() /
                        (float) mCameraPreviewHost.getView().getHeight(), 1);

                if (MediaCameraManager.get().isRecording()) {
                    MediaCameraManager.get().stopVideo();
                    mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
                } else {
                    final MediaCameraManager.MediaCallback callback = new MediaCameraManager.MediaCallback() {
                        @Override
                        public void onMediaReady(
                                final Uri uriToVideo, final String contentType,
                                final int width, final int height) {
                            /* And by SPRD for Bug:531051 2016.02.01 Start */
                            if(mAudioManager != null) {
                                mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
                            }
                            /* And by SPRD for Bug:531051 2016.02.01 End */
                            mVideoCounter.stop();
                            if(mStopByPause){
                                mStopByPause = false;
                                final Rect startRect = new Rect();
                                if (mView != null) {
                                    mView.getGlobalVisibleRect(startRect);
                                }
                                final List<MessagePartData> items = new ArrayList<MessagePartData>(1);
                                items.add(new MediaPickerMessagePartData(startRect,contentType,uriToVideo, width, height));
                                if(mMediaPicker.getDraftMessageDataModel() != null){
                                    mMediaPicker.getDraftMessageDataModel().getData().addAttachments(items);
                                }
                                return;
                            }
                            if (mVideoCancelled || uriToVideo == null) {
                                mVideoCancelled = false;
                            } else {
                                final Rect startRect = new Rect();
                                // It's possible to throw out the chooser while taking the
                                // picture/video.  In that case, still use the attachment, just
                                // skip the startRect
                                if (mView != null) {
                                    mView.getGlobalVisibleRect(startRect);
                                }
                                // modify for bug 725726 start
                                MessagePartData item = new MediaPickerMessagePartData(startRect, contentType,
                                                uriToVideo, width, height);
                                item.setIsCompressed(1);
                                mMediaPicker.dispatchItemsSelected(item,true /* dismissMediaPicker */);
                                // modify for bug 725726 end
                            }
                            updateViewState();
                            MediaCameraManager.get().setStartRecordingFlag(false);//bug 1156144
                        }

                        @Override
                        public void onMediaFailed(final Exception exception) {
                            UiUtils.showToastAtBottom(R.string.camera_media_failure);
                            updateViewState();
                        }

                        @Override
                        public void onMediaInfo(final int what) {
                            if (what == MediaCallback.MEDIA_NO_DATA) {
                                UiUtils.showToastAtBottom(R.string.camera_media_failure);
                            }
                            updateViewState();
                        }

                        // add for #597485 start
                        public void onStartVideoRecord() {
                            Log.d(TAG,
                                    "onStartVideoRecord = "
                                            + System.currentTimeMillis());
                            mVideoCounter.setBase(SystemClock.elapsedRealtime());
                            mVideoCounter.start();
                            /*bug 1121918,begin*/
                            MediaCameraManager.get().setStartRecordingFlag(true);
                            /*bug 1121918, end*/

                        }
                        // add for #597485 end

                    };
                    if (MediaCameraManager.get().isVideoMode()) {
                        /* Add by SPRD for bug 549991 Start */
                        DraftMessageData draftMessageData;
                        if (null != mMediaPicker && null != mMediaPicker.getDraftMessageDataModel()) {
                            draftMessageData = mMediaPicker.getDraftMessageDataModel().getData();
                        } else {
                            // This may be called in smil mode
                            draftMessageData = GlobleUtil.getDraftMessageData();
                        }
                        long sizeLimit = MediaCameraManager.get().getVideoSizeLimit();
                        if (draftMessageData != null &&  sizeLimit<=(9*1024)
                                && sizeLimit != 0) {
                            mMediaPicker.setFullScreen(false);
                            try{
                                Context alertContext = (Context)mMediaPicker.getActivity();
                                if (alertContext!=null){
                                    new AlertDialog.Builder(alertContext).setTitle(R.string.warning)
                                        .setMessage(R.string.share_video_exceeded)
                                        .setPositiveButton(android.R.string.ok, null).show();
                                }else{
                                    UiUtils.showToastAtBottom(R.string.share_video_exceeded);
                                }
                                mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
                            }catch(Exception ex){
                                Log.e(TAG, "show alert dialog warning "+ex);
                            }
                        }
                        /* Add by SPRD for bug 549991 end */
                        else {
                            MediaCameraManager.get().startVideo(callback);
                            //mVideoCounter.setBase(SystemClock.elapsedRealtime());
                            //mVideoCounter.start();
                            updateViewState();
                        }
                    } else {
                        /*bug 1121918,begin*/
                        if(MediaCameraManager.get().getStartRecordingFlag()){
                            return;
                        }
                        /*bug 1121918, end*/
                        showShutterEffect(shutterVisual);
                        /* SPRD: add for Bug 497188 begin */
                        if (mCameraErrorCode == 0) {
                            MediaUtil.get().playSound(getContext(), R.raw.shutter, null /* completionListener */);
                        }
                        /* SPRD: add for Bug 497188 end */
                        mCameraErrorCode = 0;//for bug 825781
                        try{
                            MediaCameraManager.get().takePicture(heightPercent, callback);
                        }catch (Exception ex){
                            Log.e(TAG, "takePicture failed "+ex);
                        }
                        updateViewState();
                    }
                }
            }
        });

        mSwapModeButton = (ImageButton) view.findViewById(R.id.camera_swap_mode_button);
        mSwapModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                final boolean isSwitchingToVideo = !MediaCameraManager.get().isVideoMode();
                /*bug 1145485, begin*/
                if(isSwitchingToVideo){
                    swapModeTime = SystemClock.elapsedRealtime();
                }
                /*bug 1145485, end*/
                if (isSwitchingToVideo && !OsUtil.hasRecordAudioPermission()) {
                    requestRecordAudioPermission();
                } else {
                    mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                            AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                    onSwapMode();
                }
            }
        });

        mCancelVideoButton = (ImageButton) view.findViewById(R.id.camera_cancel_button);
        mCancelVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                mVideoCancelled = true;
                MediaCameraManager.get().stopVideo();
                mMediaPicker.dismiss(true);
                mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
            }
        });

        mVideoCounter = (Chronometer) view.findViewById(R.id.camera_video_counter);

        MediaCameraManager.get().setRenderOverlay((RenderOverlay) view.findViewById(R.id.focus_visual));

        mEnabledView = view.findViewById(R.id.mediapicker_enabled);
        mMissingPermissionView = view.findViewById(R.id.missing_permission_view);

        // Must set mView before calling updateViewState because it operates on mView
        mView = view;
        mUnEnableView = view.findViewById(R.id.camera_unenabled);
        mButtonContainer = view.findViewById(R.id.camera_button_container);
        updateViewState();
        updateForPermissionState(MediaCameraManager.hasCameraPermission());

        mAcceleratorViews = new AnimationAcceleratorViews();
        mAcceleratorViews.mEnabledView = mEnabledView;
        mAcceleratorViews.mRenderView = view.findViewById(R.id.focus_visual);
        mAcceleratorViews.mPreviewView = mCameraPreviewHost.getView();

        Log.d(TAG, " CreateView mIsAnimationOpen "+mIsAnimationOpen);
        if (mIsAnimationOpen) {
            Log.d(TAG,"createView setVisibility(View.INVISIBLE)");
            mAcceleratorViews.setVisibility(View.INVISIBLE);
        }else{
            mAcceleratorViews.setVisibility(View.VISIBLE);
        }

        return view;
    }

    @Override
    /* Modify by SPRD for Bug:523092  2016.01.12 Start */
    /*
    public int getIconResource() {
        return R.drawable.ic_camera_light;
    }*/
    public int[] getIconResource() {
        return new int[] {R.drawable.ic_camera_light, R.drawable.ic_camera_dark};
    }
    /* Modify by SPRD for Bug:523092  2015.01.12 end */

    @Override
    public int getIconDescriptionResource() {
        return R.string.mediapicker_cameraChooserDescription;
    }

    /**
     * Updates the view when entering or leaving full-screen camera mode
     * @param fullScreen
     */
    @Override
    void onFullScreenChanged(final boolean fullScreen) {
        super.onFullScreenChanged(fullScreen);
        if (!fullScreen && MediaCameraManager.get().isVideoMode()) {
            MediaCameraManager.get().setVideoMode(false);
        }
        updateViewState();
    }

    /**
     * Initializes the control to a default state when it is opened / closed
     * @param open True if the control is opened
     */
    @Override
    void onOpenedChanged(final boolean open) {
        super.onOpenedChanged(open);
        updateViewState();
    }

    @Override
    protected void setSelected(final boolean selected) {
        super.setSelected(selected);
        if (selected) {
            if (MediaCameraManager.hasCameraPermission()) {
                // If an error occurred before the chooser was selected, show it now
                showErrorToastIfNeeded();
            } else {
                requestCameraPermission();
            }
        }
    }

    private void requestCameraPermission() {
        mMediaPicker.requestPermissionsFromMediaPicker(new String[] { Manifest.permission.CAMERA },
                MediaPicker.CAMERA_PERMISSION_REQUEST_CODE);
    }

    private void requestRecordAudioPermission() {
        mMediaPicker.requestPermissionsFromMediaPicker(new String[] { Manifest.permission.RECORD_AUDIO },
                MediaPicker.RECORD_AUDIO_PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onRequestPermissionsResult(
            final int requestCode, final String permissions[], final int[] grantResults) {
        if (grantResults == null || grantResults.length == 0){
             Log.i(TAG, "onRequestPermissionsResult   grantResults is invalid");
             return;
        }

        if (requestCode == MediaPicker.CAMERA_PERMISSION_REQUEST_CODE) {
            final boolean permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            updateForPermissionState(permissionGranted);
            if (permissionGranted) {
                if (mCameraPreviewHost!=null)mCameraPreviewHost.onCameraPermissionGranted();
                //mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                //AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } else if (requestCode == MediaPicker.RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            Assert.isFalse(MediaCameraManager.get().isVideoMode());
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                // Switch to video mode
                onSwapMode();
            } else {
                // Stay in still-photo mode
            }
        }
    }

    private void updateForPermissionState(final boolean granted) {
        // onRequestPermissionsResult can sometimes get called before createView().
        Log.d(TAG, " updateForPermissionState granted: " + granted + " mUnEnableView is null" + (mUnEnableView == null));
        if (mUnEnableView == null) {
            return;
        }

        //mEnabledView.setVisibility(granted ? View.VISIBLE : View.GONE);
        mUnEnableView.setVisibility(granted ? View.VISIBLE : View.GONE);
        mEnabledView.setVisibility(granted ? View.GONE : View.VISIBLE);
        mMissingPermissionView.setVisibility(granted ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean canSwipeDown() {
        if (MediaCameraManager.get().isVideoMode()) {
            return true;
        }
        return super.canSwipeDown();
    }

    /**
     * Handles an error from the camera manager by showing the appropriate error message to the user
     * @param errorCode One of the MediaCameraManager.ERROR_* constants
     * @param e The exception which caused the error, if any
     */
    @Override
    public void onCameraError(final int errorCode, final Exception e) {
        /* SPRD: add for Bug 497188 begin */
        mCameraErrorCode = errorCode;
        /* SPRD: add for Bug 497188 begin */
        switch (errorCode) {
            case MediaCameraManager.ERROR_OPENING_CAMERA:
            case MediaCameraManager.ERROR_SHOWING_PREVIEW:
                mErrorToast = R.string.camera_error_opening;
                break;
            case MediaCameraManager.ERROR_INITIALIZING_VIDEO:
                mErrorToast = R.string.camera_error_video_init_fail;
                updateViewState();
                break;
            case MediaCameraManager.ERROR_STORAGE_FAILURE:
                mErrorToast = R.string.camera_error_storage_fail;
                updateViewState();
                break;
            case MediaCameraManager.ERROR_TAKING_PICTURE:
                mErrorToast = R.string.camera_error_failure_taking_picture;
                break;
            case MediaCameraManager.ERROR_RAMAINING_MESSAGE_SIZE:
                mErrorToast = R.string.share_video_exceeded;
                break;
            case MediaCameraManager.ERROR_RECORDING_VIDEO:
                mErrorToast = R.string.fail_start_record_videoaudio;
                break;
            default:
                mErrorToast = R.string.camera_error_unknown;
                LogUtil.w(LogUtil.BUGLE_TAG, "Unknown camera error:" + errorCode);
                break;
        }
        //SPREAD: FIX FOR BUG 503533 STARD
        if(mErrorToast == R.string.camera_error_storage_fail){
            return;
        }
        //SPREAD: FIX FOR BUG 503533 END
        showErrorToastIfNeeded();
    }

    private void showErrorToastIfNeeded() {
        if (mErrorToast != 0 && mSelected) {
            UiUtils.showToastAtBottom(mErrorToast);
            mErrorToast = 0;
        }
    }

    @Override
    public void onCameraChanged() {
        updateViewState();
    }

    @Override
    public void onCameraOpen() {
        Log.d(TAG, " onCameraOpen");
        mUnEnableView.setVisibility(View.GONE);
        mEnabledView.setVisibility(View.VISIBLE);
        mButtonContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCameraClose() {
        Log.d(TAG, " onCameraClose");
        mUnEnableView.setVisibility(View.VISIBLE);
        mEnabledView.setVisibility(View.GONE);
        mButtonContainer.setVisibility(View.GONE);
    }

    private void onSwapMode() {
        MediaCameraManager.get().setVideoMode(!MediaCameraManager.get().isVideoMode());
        if (MediaCameraManager.get().isVideoMode()) {
            mMediaPicker.setFullScreen(true);

            // For now we start recording immediately
            if (mCaptureButton!=null)mCaptureButton.performClick();
        }
        updateViewState();
    }

    private void showShutterEffect(final View shutterVisual) {
        final float maxAlpha = getContext().getResources().getFraction(
                R.fraction.camera_shutter_max_alpha, 1 /* base */, 1 /* pBase */);

        // Divide by 2 so each half of the animation adds up to the full duration
        final int animationDuration = getContext().getResources().getInteger(
                R.integer.camera_shutter_duration) / 2;

        final AnimationSet animation = new AnimationSet(false /* shareInterpolator */);
        final Animation alphaInAnimation = new AlphaAnimation(0.0f, maxAlpha);
        alphaInAnimation.setDuration(animationDuration);
        animation.addAnimation(alphaInAnimation);

        final Animation alphaOutAnimation = new AlphaAnimation(maxAlpha, 0.0f);
        alphaOutAnimation.setStartOffset(animationDuration);
        alphaOutAnimation.setDuration(animationDuration);
        animation.addAnimation(alphaOutAnimation);

        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(final Animation animation) {
                shutterVisual.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(final Animation animation) {
                shutterVisual.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(final Animation animation) {
            }
        });
        shutterVisual.startAnimation(animation);
    }

    /** Updates the state of the buttons and overlays based on the current state of the view */
    private void updateViewState() {
        Log.d(TAG, "CameraMediaChooser updateViewState : mView is null" + (mView == null));
        if (mView == null) {
            return;
        }

        final Context context = getContext();
        if (context == null) {
            // Context is null if the fragment was already removed from the activity
            return;
        }
        final boolean fullScreen = mMediaPicker.isFullScreen();
        final boolean videoMode = MediaCameraManager.get().isVideoMode();
        final boolean isRecording = MediaCameraManager.get().isRecording();
        final boolean isCameraAvailable = isCameraAvailable();
        final Camera.CameraInfo cameraInfo = MediaCameraManager.get().getCameraInfo();
        final boolean frontCamera = cameraInfo != null && cameraInfo.facing ==
                Camera.CameraInfo.CAMERA_FACING_FRONT;

        mView.setSystemUiVisibility(
                fullScreen ? View.SYSTEM_UI_FLAG_LOW_PROFILE :
                View.SYSTEM_UI_FLAG_VISIBLE);

        mFullScreenButton.setVisibility(/*!fullScreen ? View.VISIBLE : */View.GONE);
        mFullScreenButton.setEnabled(isCameraAvailable);
        mSwapCameraButton.setVisibility(
                /*fullScreen && */!isRecording && MediaCameraManager.get().hasFrontAndBackCamera() ?
                        View.VISIBLE : View.GONE);
        mSwapCameraButton.setImageResource(frontCamera ?
                R.drawable.ic_camera_front_light :
                R.drawable.ic_camera_rear_light);
        mSwapCameraButton.setEnabled(isCameraAvailable);

        mCancelVideoButton.setVisibility(isRecording ? View.VISIBLE : View.GONE);
        mVideoCounter.setVisibility(isRecording ? View.VISIBLE : View.GONE);

        mSwapModeButton.setImageResource(videoMode ?
                R.drawable.ic_mp_camera_small_light :
                R.drawable.ic_mp_video_small_light);
        mSwapModeButton.setContentDescription(context.getString(videoMode ?
                R.string.camera_switch_to_still_mode : R.string.camera_switch_to_video_mode));
        mSwapModeButton.setVisibility(isRecording ? View.GONE : View.VISIBLE);
        mSwapModeButton.setEnabled(isCameraAvailable);

        if (isRecording) {
            mCaptureButton.setImageResource(R.drawable.ic_mp_capture_stop_large_light);
            mCaptureButton.setContentDescription(context.getString(
                    R.string.camera_stop_recording));
        } else if (videoMode) {
            mCaptureButton.setImageResource(R.drawable.ic_mp_video_large_light);
            mCaptureButton.setContentDescription(context.getString(
                    R.string.camera_start_recording));
        } else {
            mCaptureButton.setImageResource(R.drawable.ic_checkmark_large_light);
            mCaptureButton.setContentDescription(context.getString(
                    R.string.camera_take_picture));
        }
        mCaptureButton.setEnabled(isCameraAvailable);
    }

    @Override
    int getActionBarTitleResId() {
        return 0;
    }

    /**
     * Returns if the camera is currently ready camera is loaded and not taking a picture.
     * otherwise we should avoid taking another picture, swapping camera or recording video.
     */
    private boolean isCameraAvailable() {
        return MediaCameraManager.get().isCameraAvailable();
    }

    //spread: add for audio focus function listener start
    private OnAudioFocusChangeListener mOnAudioFocusChangeListener = new OnAudioFocusChangeListener(){

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:

               break;
            case AudioManager.AUDIOFOCUS_LOSS:
                mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:

                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:

                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:

                break;
            }

        }

    };
    //spread: add for audio focus function listener start

    //spread: add for bug 504080 start
    public void onResume() {
        boolean hasCameraPermission = MediaCameraManager.hasCameraPermission();
        if (!hasCameraPermission && isPaused) {
            Log.i(TAG, "onResume mSelected " + mSelected + " camera open " + MediaCameraManager.get().isCameraOpen());
            if (mSelected && MediaCameraManager.get().isCameraOpen()/*is CameraOpen?*/) {
                requestCameraPermission();
            }
            isPaused = false;
        }
        Log.d(TAG, "onResume hasCameraPermission: " +hasCameraPermission );
        if (hasCameraPermission)
            updateForPermissionState(hasCameraPermission);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause mSelected " + mSelected + " camera open " + MediaCameraManager.get().isCameraOpen());
        super.onPause();
        isPaused = true;
    }

    @Override
    public void onRecordStopByPause(){
        if (MediaCameraManager.get().isRecording()) {
            mStopByPause = true;
            MediaCameraManager.get().stopVideo();
            MediaCameraManager.get().setStartRecordingFlag(false);     //add for Bug 1409719
        }
    }
    //spread: add for bug 504080 end
    @Override
    public boolean onBackPressed() {
        if (MediaCameraManager.get().isRecording()) {
             MediaCameraManager.get().stopVideo();
            /* And by SPRD for Bug:531051 2016.02.01 Start */
            if(mAudioManager != null) {
                mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
            }
            /* And by SPRD for Bug:531051 2016.02.01 End */
        }
        return false;
    }

    public void onAnimationEnd(){
        Log.d(TAG, " onAnimationEnd mIsAnimationOpen "+mIsAnimationOpen);
        if (mAcceleratorViews!=null){
            mAcceleratorViews.setVisibility(View.VISIBLE);
        }
        mIsAnimationOpen = false;

    }

    public void onAnimationOpen(){
        Log.d(TAG, " onAnimationOpen mIsAnimationOpen "+mIsAnimationOpen);
        mIsAnimationOpen = true;
    }

    private boolean mIsAnimationOpen;

}
