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
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.hardware.Camera.CameraInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.util.Log;

import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.DraftMessageData.DraftMessageSubscriptionDataProvider;
import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.datamodel.media.ImageRequest;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.ui.mediapicker.camerafocus.FocusOverlayManager;
import com.android.messaging.ui.mediapicker.camerafocus.RenderOverlay;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.UriUtil;
import com.google.common.annotations.VisibleForTesting;
import com.android.messaging.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class which manages interactions with the camera, but does not do any UI.  This class is
 * designed to be a singleton to ensure there is one component managing the camera and releasing
 * the native resources.
 * In order to acquire a camera, a caller must:
 * <ul>
 *     <li>Call selectCamera to select front or back camera
 *     <li>Call setSurface to control where the preview is shown
 *     <li>Call openCamera to request the camera start preview
 * </ul>
 * Callers should call onPause and onResume to ensure that the camera is release while the activity
 * is not active.
 * This class is not thread safe.  It should only be called from one thread (the UI thread or test
 * thread)
 */
class MediaCameraManager implements FocusOverlayManager.Listener {
    /**
     * Wrapper around the framework camera API to allow mocking different hardware scenarios while
     * unit testing
     */
    interface CameraWrapper {
        int getNumberOfCameras();
        void getCameraInfo(int index, CameraInfo cameraInfo);
        Camera open(int cameraId);
        /** Add a wrapper for release because a final method cannot be mocked */
        void release(Camera camera);
    }

    /**
     * Callbacks for the camera manager listener
     */
    interface CameraManagerListener {
        void onCameraError(int errorCode, Exception e);
        void onCameraChanged();
        void onCameraOpen();
        void onCameraClose();
    }

    /**
     * Callback when taking image or video
     */
    interface MediaCallback {
        static final int MEDIA_CAMERA_CHANGED = 1;
        static final int MEDIA_NO_DATA = 2;

        void onMediaReady(Uri uriToMedia, String contentType, int width, int height);
        void onMediaFailed(Exception exception);
        void onMediaInfo(int what);

        // add for #597485 start
        void onStartVideoRecord();
        // add for #597485 end
    }

    // Error codes
    static final int ERROR_OPENING_CAMERA = 1;
    static final int ERROR_SHOWING_PREVIEW = 2;
    static final int ERROR_INITIALIZING_VIDEO = 3;
    static final int ERROR_STORAGE_FAILURE = 4;
    static final int ERROR_RECORDING_VIDEO = 5;
    static final int ERROR_HARDWARE_ACCELERATION_DISABLED = 6;
    static final int ERROR_TAKING_PICTURE = 7;
    static final int ERROR_RAMAINING_MESSAGE_SIZE = 8;

    private static final String TAG = "MediaCameraManager";
    private static final int NO_CAMERA_SELECTED = -1;

    private static MediaCameraManager sInstance;

    private static boolean mIsCameraOpened;//bug508563 
    /*Add by SPRD for bug587034  2016.08.16 Start*/
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private int mDegrees = -1;
    /*Add by SPRD for bug587034  2016.08.16 End*/
    private boolean isStartPreview;
    /*Add by SPRD for bug1119738   2019.07.29 Start*/
    private boolean mTouchBeenMove = false;
    private int mTouchDownX = 0;
    private int mTouchDownY = 0;
    static final int TOUCH_RANGE = 6;
    /*Add by SPRD for bug1119738   2019.07.29 end*/
    /** Default camera wrapper which directs calls to the framework APIs */
    private static CameraWrapper sCameraWrapper = new CameraWrapper() {
        @Override
        public int getNumberOfCameras() {
            return Camera.getNumberOfCameras();
        }

        @Override
        public void getCameraInfo(final int index, final CameraInfo cameraInfo) {
            Camera.getCameraInfo(index, cameraInfo);
        }

        @Override
        public Camera open(final int cameraId) {
            mIsCameraOpened = true; //bug508563 
            return Camera.open(cameraId);
        }

        @Override
        public void release(final Camera camera) {
            mIsCameraOpened = false;//bug508563 
            camera.release();
        }
    };

    // add for #1034864 start
    private static final int MSG_CAMERA_REQUEST_PENDING = 3;
    private static final int CAMERA_REQUEST_PENDING_TIME = 5000;
    private CameraManager mCameraManager;
    private Handler mCameraRequestHandler;
    private Handler mMainHandler;
    private boolean mPaused;
    private ConcurrentHashMap<String, Boolean> mCameraAvailableMap;
    private CameraManager.AvailabilityCallback mAvailabilityCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(String cameraId) {
            Log.i(TAG, "onCameraavailable cameraId: " + cameraId + " mPaused :" + mPaused);
            if (mCameraAvailableMap != null)
                mCameraAvailableMap.put(cameraId, true);
            boolean requestPending = mCameraRequestHandler.hasMessages(MSG_CAMERA_REQUEST_PENDING);
            if (requestPending && !mPaused && checkAllCameraAvailable()) {
                mCameraRequestHandler.removeMessages(MSG_CAMERA_REQUEST_PENDING);
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, " mMainHandler openCamera");
                        openCamera();
                    }
                });
            }
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            Log.i(TAG, "onCameraUnavailable cameraId :" + cameraId);
            if (mCameraAvailableMap != null)
                mCameraAvailableMap.put(cameraId, false);
        }
    };
    // add for #1034864 end
    /**
     * The CameraInfo for the currently selected camera
     */
    private final CameraInfo mCameraInfo;

    /**
     * The index of the selected camera or NO_CAMERA_SELECTED if a camera hasn't been selected yet
     */
    private int mCameraIndex;

    /** True if the device has front and back cameras */
    private final boolean mHasFrontAndBackCamera;

    /** True if the camera should be open (may not yet be actually open) */
    private boolean mOpenRequested;

    /** True if the camera is requested to be in video mode */
    private boolean mVideoModeRequested;

    /** The media recorder for video mode */
    private MmsVideoRecorder mMediaRecorder;

    /** Callback to call with video recording updates */
    private MediaCallback mVideoCallback;

    /** The preview view to show the preview on */
    private CameraPreview mCameraPreview;

    /** The helper classs to handle orientation changes */
    private OrientationHandler mOrientationHandler;

    /** Tracks whether the preview has hardware acceleration */
    private boolean mIsHardwareAccelerationSupported;

    /**
     * The task for opening the camera, so it doesn't block the UI thread
     * Using AsyncTask rather than SafeAsyncTask because the tasks need to be serialized, but don't
     * need to be on the UI thread
     * TODO: If we have other AyncTasks (not SafeAsyncTasks) this may contend and we may
     * need to create a dedicated thread, or synchronize the threads in the thread pool
     */
    private AsyncTask<Integer, Void, Camera> mOpenCameraTask;

    /**
     * The camera index that is queued to be opened, but not completed yet, or NO_CAMERA_SELECTED if
     * no open task is pending
     */
    private int mPendingOpenCameraIndex = NO_CAMERA_SELECTED;

    /** The instance of the currently opened camera */
    private Camera mCamera;

    /** The rotation of the screen relative to the camera's natural orientation */
    private int mRotation;

    /** The callback to notify when errors or other events occur */
    private CameraManagerListener mListener;

    /** True if the camera is currently in the process of taking an image */
    private boolean mTakingPicture;

    private boolean mPictureLoading;//Bug 853400

    /** Provides subscription-related data to access per-subscription configurations. */
    private DraftMessageSubscriptionDataProvider mSubscriptionDataProvider;

    /** Manages auto focus visual and behavior */
    private final FocusOverlayManager mFocusOverlayManager;

    private MediaCameraManager() {
        mCameraInfo = new CameraInfo();
        mCameraIndex = NO_CAMERA_SELECTED;

        // Check to see if a front and back camera exist
        boolean hasFrontCamera = false;
        boolean hasBackCamera = false;
        final CameraInfo cameraInfo = new CameraInfo();
        final int cameraCount = sCameraWrapper.getNumberOfCameras();
        try {
            for (int i = 0; i < cameraCount; i++) {
                sCameraWrapper.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    hasFrontCamera = true;
                } else if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                    hasBackCamera = true;
                }
                if (hasFrontCamera && hasBackCamera) {
                    break;
                }
            }
        } catch (final RuntimeException e) {
            LogUtil.e(TAG, "Unable to load camera info", e);
        }
        mHasFrontAndBackCamera = hasFrontCamera && hasBackCamera;
        mFocusOverlayManager = new FocusOverlayManager(this, Looper.getMainLooper());

        // Assume the best until we are proven otherwise
        mIsHardwareAccelerationSupported = true;

        // add for #1034864 start
        mCameraAvailableMap = new ConcurrentHashMap<String, Boolean>();
        mMainHandler = new Handler();
        HandlerThread CameraRequest = new HandlerThread("CameraRequest Task");
        CameraRequest.start();
        mCameraRequestHandler = new Handler(CameraRequest.getLooper()) {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_CAMERA_REQUEST_PENDING:
                        if (mPaused)
                            return;
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(TAG, " mMainHandler openCamera 33333");
                                openCamera();
                            }
                        });
                        break;
                    default:
                        break;
                }
            }
        };
        //
        mCameraManager = (CameraManager) Factory.get().getApplicationContext().getSystemService(Context.CAMERA_SERVICE);
        mCameraManager.registerAvailabilityCallback(mAvailabilityCallback, mCameraRequestHandler);
        // add for #1034864 end
    }

    // add for #1034864 start
    public boolean checkAllCameraAvailable() {
        if (mCameraAvailableMap == null)
            return false;
        Iterator iter = mCameraAvailableMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            String mCameraId = (String) entry.getKey();
            boolean mCameraAvailable = (boolean) entry.getValue();
            Log.i(TAG, "checkAllCameraAvailable mCameraId =" + mCameraId + " mCameraAvailable="
                    + mCameraAvailable);
            if (!mCameraAvailable)
                return false;
        }
        return true;
    }
    // add for #1034864 end
    /**
     * Gets the singleton instance
     */
    static MediaCameraManager get() {
        if (sInstance == null) {
            sInstance = new MediaCameraManager();
        }
        return sInstance;
    }

    //bug508563 begin
    public boolean isCameraOpen(){
         return mIsCameraOpened;
    }
    //bug508563 end
    /** Allows tests to inject a custom camera wrapper */
    @VisibleForTesting
    static void setCameraWrapper(final CameraWrapper cameraWrapper) {
        sCameraWrapper = cameraWrapper;
        sInstance = null;
    }

    /**
     * Sets the surface to use to display the preview
     * This must only be called AFTER the CameraPreview has a texture ready
     * @param preview The preview surface view
     */
    void setSurface(final CameraPreview preview) {
        if (preview == mCameraPreview) {
            return;
        }

        if (preview != null) {
            Assert.isTrue(preview.isValid());
            preview.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(final View view, final MotionEvent motionEvent) {
                    if ((motionEvent.getActionMasked() & MotionEvent.ACTION_UP) ==
                            MotionEvent.ACTION_UP) {
                        mFocusOverlayManager.setPreviewSize(view.getWidth(), view.getHeight());
                        /*Add by SPRD for bug1119738   2019.07.29 Start*/
                        Log.d(TAG, "setPreviewSize getWidth:"+view.getWidth()+" getHeight"+view.getHeight());
                        int nowx = (int) motionEvent.getX();
                        int nowy = (int) motionEvent.getY();

                        if(Math.abs(nowx-mTouchDownX)<TOUCH_RANGE && Math.abs(nowy-mTouchDownY)<TOUCH_RANGE && !mTouchBeenMove) {
                            Log.d(TAG, "touch tap x:"+Math.abs(nowx-mTouchDownX)+" y:"+Math.abs(nowy-mTouchDownY));
                            mFocusOverlayManager.onSingleTapUp(
                                    (int) motionEvent.getX() + view.getLeft(),
                                    (int) motionEvent.getY() + view.getTop());
                        }else{
                            Log.d(TAG, "touch been move , not tap x:"+Math.abs(nowx-mTouchDownX)+" y:"+Math.abs(nowy-mTouchDownY));
                        }
                    }else if((motionEvent.getActionMasked() & MotionEvent.ACTION_MOVE) ==
                            MotionEvent.ACTION_MOVE){
                        Log.d(TAG, "ACTION_MOVE");
                        mTouchBeenMove = true ;
                    }else if((motionEvent.getActionMasked() & MotionEvent.ACTION_DOWN) ==
                            MotionEvent.ACTION_DOWN){
                        Log.d(TAG, "ACTION_DOWN");
                        mTouchBeenMove = false ;
                        mTouchDownX = (int) motionEvent.getX() ;
                        mTouchDownY = (int) motionEvent.getY() ;
                    }
                    /*Add by SPRD for bug1119738   2019.07.29 end*/
                    return true;
                }
            });
        }
        mCameraPreview = preview;
        tryShowPreview();
    }

    void setRenderOverlay(final RenderOverlay renderOverlay) {
        mFocusOverlayManager.setFocusRenderer(renderOverlay != null ?
                renderOverlay.getPieRenderer() : null);
    }

    /** Convenience function to swap between front and back facing cameras */
    void swapCamera() {
        Assert.isTrue(mCameraIndex >= 0);
        selectCamera(mCameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT ?
                CameraInfo.CAMERA_FACING_BACK :
                CameraInfo.CAMERA_FACING_FRONT);
    }

    /**
     * Selects the first camera facing the desired direction, or the first camera if there is no
     * camera in the desired direction
     * @param desiredFacing One of the CameraInfo.CAMERA_FACING_* constants
     * @return True if a camera was selected, or false if selecting a camera failed
     */
    boolean selectCamera(final int desiredFacing) {
        try {
            // We already selected a camera facing that direction
            if (mCameraIndex >= 0 && mCameraInfo.facing == desiredFacing) {
                return true;
            }

            final int cameraCount = sCameraWrapper.getNumberOfCameras();
            Assert.isTrue(cameraCount > 0);

            mCameraIndex = NO_CAMERA_SELECTED;
            setCamera(null);
            final CameraInfo cameraInfo = new CameraInfo();
            for (int i = 0; i < cameraCount; i++) {
                sCameraWrapper.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == desiredFacing) {
                    mCameraIndex = i;
                    sCameraWrapper.getCameraInfo(i, mCameraInfo);
                    break;
                }
            }

            // There's no camera in the desired facing direction, just select the first camera
            // regardless of direction
            if (mCameraIndex < 0) {
                mCameraIndex = 0;
                sCameraWrapper.getCameraInfo(0, mCameraInfo);
            }
            Log.d(TAG," selectCamera mOpenRequested : " +mOpenRequested + " openCamera");
            if (mOpenRequested) {
                // The camera is open, so reopen with the newly selected camera
                openCamera();
            }
            return true;
        } catch (final RuntimeException e) {
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.selectCamera", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_OPENING_CAMERA, e);
            }
            return false;
        }
    }

    int getCameraIndex() {
        return mCameraIndex;
    }

    void selectCameraByIndex(final int cameraIndex) {
        if (mCameraIndex == cameraIndex || cameraIndex==NO_CAMERA_SELECTED) {
            return;
        }

        try {
            mCameraIndex = cameraIndex;
            sCameraWrapper.getCameraInfo(mCameraIndex, mCameraInfo);
            if (mOpenRequested) {
                Log.d(TAG ," selectCameraByIndex opencamera  ");
                openCamera();
            }
        } catch (final RuntimeException e) {
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.selectCameraByIndex", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_OPENING_CAMERA, e);
            }
        }
    }

    @VisibleForTesting
    CameraInfo getCameraInfo() {
        if (mCameraIndex == NO_CAMERA_SELECTED) {
            return null;
        }
        return mCameraInfo;
    }

    /** @return True if this device has camera capabilities */
    boolean hasAnyCamera() {
        return sCameraWrapper.getNumberOfCameras() > 0;
    }

    /** @return True if the device has both a front and back camera */
    boolean hasFrontAndBackCamera() {
        return mHasFrontAndBackCamera;
    }

    /**
     * Opens the camera on a separate thread and initiates the preview if one is available
     */
    void openCamera() {
        // add for #1034864 start
        if (!checkAllCameraAvailable()) {
            mCameraRequestHandler.sendEmptyMessageDelayed(MSG_CAMERA_REQUEST_PENDING, CAMERA_REQUEST_PENDING_TIME);
            Log.d(TAG ,"opencamera  return  notAvailable ");
            return;
        }
        Log.d(TAG, " openCamera  mCameraIndex : " + mCameraIndex);
        if (mCameraIndex == NO_CAMERA_SELECTED) {
            // Ensure a selected camera if none is currently selected. This may happen if the
            // camera chooser is not the default media chooser.
            selectCamera(CameraInfo.CAMERA_FACING_BACK);
        }
        // add for #1034864 end
        mOpenRequested = true;
        // We're already opening the camera or already have the camera handle, nothing more to do
        Log.d(TAG," mPendingOpenCameraIndex :" +mPendingOpenCameraIndex +" mCamera is not null ? " +(mCamera != null));
        if (mPendingOpenCameraIndex == mCameraIndex || mCamera != null) {
            return;
        }

        // True if the task to open the camera has to be delayed until the current one completes
        boolean delayTask = false;

        // Cancel any previous open camera tasks
        if (mOpenCameraTask != null) {
            mPendingOpenCameraIndex = NO_CAMERA_SELECTED;
            delayTask = true;
        }
        Log.d(TAG," delayTask :" +delayTask);
        mPendingOpenCameraIndex = mCameraIndex;
        Log.d(TAG," mPendingOpenCameraIndex :" +mPendingOpenCameraIndex);
        mOpenCameraTask = new AsyncTask<Integer, Void, Camera>() {
            private Exception mException;

            @Override
            protected Camera doInBackground(final Integer... params) {
                try {
                    final int cameraIndex = params[0];
                    LogUtil.v(TAG, "Opening camera " + mCameraIndex);
                    return sCameraWrapper.open(cameraIndex);
                } catch (final Exception e) {
                    LogUtil.e(TAG, "Exception while opening camera", e);
                    mException = e;
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final Camera camera) {
                // If we completed, but no longer want this camera, then release the camera
                if (mOpenCameraTask != this || !mOpenRequested) {
                    releaseCamera(camera);
                    cleanup();
                    if (mFocusOverlayManager!=null){
                        mFocusOverlayManager.resume();
	             }
                    return;
                }
                Log.d(TAG," normal onPostExecute : cleanup");
                cleanup();

                if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                    LogUtil.v(TAG, "Opened camera " + mCameraIndex + " " + (camera != null));
                }

                setCamera(camera);

                if (mFocusOverlayManager!=null){
                    mFocusOverlayManager.resume();
	         }

                if (camera == null) {
                    if (mListener != null) {
                        mListener.onCameraError(ERROR_OPENING_CAMERA, mException);
                    }
                    LogUtil.e(TAG, "Error opening camera");
                }
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                if (mFocusOverlayManager!=null){
                    mFocusOverlayManager.resume();
	         }
                cleanup();
            }

            private void cleanup() {
                Log.d(TAG,"  cleanup");
                mPendingOpenCameraIndex = NO_CAMERA_SELECTED;
                if (mOpenCameraTask != null && mOpenCameraTask.getStatus() == Status.PENDING) {
                    // If there's another task waiting on this one to complete, start it now
                    mOpenCameraTask.execute(mCameraIndex);
                } else {
                    mOpenCameraTask = null;
                }

            }
        };
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "Start opening camera " + mCameraIndex);
        }

        if (!delayTask) {
            if (mFocusOverlayManager!=null){
                  mFocusOverlayManager.pause();
	     }
            mOpenCameraTask.execute(mCameraIndex);
        }
    }

    boolean isVideoMode() {
        return mVideoModeRequested;
    }

    boolean isRecording() {
        return mVideoModeRequested && mVideoCallback != null;
    }

    void setVideoMode(final boolean videoMode) {
        if (mVideoModeRequested == videoMode) {
            return;
        }
        mVideoModeRequested = videoMode;
        tryInitOrCleanupVideoMode();
    }

    /** Closes the camera releasing the resources it uses */
    void closeCamera() {
        mOpenRequested = false;
        /* Modify by SPRD for Bug:524362  2015.01.14 Start */
        mTakingPicture = false;
        mPictureLoading = false;//Bug 853400
        /* Modify by SPRD for Bug:524362  2015.01.14 End */
        setCamera(null);
        isStartPreview = false;
    }

    /** Temporarily closes the camera if it is open */
    void onPause() {
        Log.d(TAG,"MediaCameraManager onPause");
        if(mListener!=null){
            mListener.onCameraClose();
        }
        setCamera(null);
        mPaused = true;
    }

    /** Reopens the camera if it was opened when onPause was called */
    void onResume() {
        mPaused = false;
        Log.d(TAG, "MediaCameraManager onResume mOpenRequested  :" + mOpenRequested +" openCamera");
        if (mOpenRequested)
            openCamera();
    }

    // add for #1034864 start
    void onDestroy() {
        if (mCameraAvailableMap != null) {
            mCameraAvailableMap.clear();
            mCameraAvailableMap = null;
        }
        if (mCameraManager != null) {
            mCameraManager.unregisterAvailabilityCallback(mAvailabilityCallback);
        }
        if (sInstance != null) {
            sInstance = null;
        }
    }
    // add for #1034864 end
    /**
     * Sets the listener which will be notified of errors or other events in the camera
     * @param listener The listener to notify
     */
    void setListener(final CameraManagerListener listener) {
        Assert.isMainThread();
        mListener = listener;
        if (!mIsHardwareAccelerationSupported && mListener != null) {
            mListener.onCameraError(ERROR_HARDWARE_ACCELERATION_DISABLED, null);
        }
    }

    void setSubscriptionDataProvider(final DraftMessageSubscriptionDataProvider provider) {
        mSubscriptionDataProvider = provider;
    }

    void takePicture(final float heightPercent, @NonNull final MediaCallback callback) {
        Assert.isTrue(!mVideoModeRequested);
        Assert.isTrue(!mTakingPicture);
        Assert.notNull(callback);
        if (mCamera == null) {
            // The caller should have checked isCameraAvailable first, but just in case, protect
            // against a null camera by notifying the callback that taking the picture didn't work
            callback.onMediaFailed(null);
            return;
        }
        final Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] bytes, final Camera camera) {
                mPictureLoading = true;//Bug 853400
                mTakingPicture = false;
                if (mCamera != camera) {
                    // This may happen if the camera was changed between front/back while the
                    // picture is being taken.
                    callback.onMediaInfo(MediaCallback.MEDIA_CAMERA_CHANGED);
                    return;
                }

                if (bytes == null) {
                    callback.onMediaInfo(MediaCallback.MEDIA_NO_DATA);
                    return;
                }

                final Camera.Size size = camera.getParameters().getPictureSize();
                int width;
                int height;
                if (mRotation == 90 || mRotation == 270) {
                    width = size.height;
                    height = size.width;
                } else {
                    width = size.width;
                    height = size.height;
                }
                new ImagePersistTask(
                        width, height, heightPercent, bytes, mCameraPreview.getContext(), callback)
                        .executeOnThreadPool();
            }
        };

        mTakingPicture = true;
        try {
            mCamera.takePicture(
                    null /* shutter */,
                    null /* raw */,
                    null /* postView */,
                    jpegCallback);
        } catch (final RuntimeException e) {
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.takePicture", e);
            mTakingPicture = false;
            if (mListener != null && !mPictureLoading) {//Bug 853400
                mListener.onCameraError(ERROR_TAKING_PICTURE, e);
            }
        }
    }

    void startVideo(final MediaCallback callback) {
        Assert.notNull(callback);
        Assert.isTrue(!isRecording());
        mVideoCallback = callback;
        tryStartVideoCapture();
    }

    /**
     * Asynchronously releases a camera
     * @param camera The camera to release
     */
    private void releaseCamera(final Camera camera) {
        if (camera == null) {
            return;
        }
        camera.setPreviewCallback(null);
        mFocusOverlayManager.onCameraReleased();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... params) {
                if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                    LogUtil.v(TAG, "Releasing camera " + mCameraIndex);
                }
                sCameraWrapper.release(camera);
                return null;
            }
        }.execute();
    }

    private void releaseMediaRecorder(final boolean cleanupFile) {
        if (mMediaRecorder == null) {
            return;
        }
        mVideoModeRequested = false;

        if (cleanupFile) {
            mMediaRecorder.cleanupTempFile();
            if (mVideoCallback != null) {
                final MediaCallback callback = mVideoCallback;
                mVideoCallback = null;
                // Notify the callback that we've stopped recording
                callback.onMediaReady(null /*uri*/, null /*contentType*/, 0 /*width*/,
                        0 /*height*/);
            }
        }

        mMediaRecorder.release();
        mMediaRecorder = null;

        if (mCamera != null) {
            try {
                mCamera.reconnect();
            } catch (final IOException e) {
                LogUtil.e(TAG, "IOException in MediaCameraManager.releaseMediaRecorder", e);
                if (mListener != null) {
                    mListener.onCameraError(ERROR_OPENING_CAMERA, e);
                }
            } catch (final RuntimeException e) {
                LogUtil.e(TAG, "RuntimeException in MediaCameraManager.releaseMediaRecorder", e);
                if (mListener != null) {
                    mListener.onCameraError(ERROR_OPENING_CAMERA, e);
                }
            }
        }
        restoreRequestedOrientation();
    }

    /** Updates the orientation of the camera to match the orientation of the device */
    private void updateCameraOrientation() {
        if (mCamera == null || mCameraPreview == null || mTakingPicture) {
            return;
        }

        final WindowManager windowManager =
                (WindowManager) mCameraPreview.getContext().getSystemService(
                        Context.WINDOW_SERVICE);

        int degrees = 0;
        switch (windowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        /*Add by SPRD for bug587034  2016.08.16 Start*/
        mDegrees = degrees;
        /*Add by SPRD for bug587034  2016.08.16 End*/

        // The display orientation of the camera (this controls the preview image).
        int orientation;

        // The clockwise rotation angle relative to the orientation of the camera. This affects
        // pictures returned by the camera in Camera.PictureCallback.
        int rotation;
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            orientation = (mCameraInfo.orientation + degrees) % 360;
            rotation = orientation;
            // compensate the mirror but only for orientation
            orientation = (360 - orientation) % 360;
        } else {  // back-facing
            orientation = (mCameraInfo.orientation - degrees + 360) % 360;
            rotation = orientation;
        }
        mRotation = rotation;
        if (mMediaRecorder == null) {
            try {
                mCamera.setDisplayOrientation(orientation);
                final Camera.Parameters params = mCamera.getParameters();
                params.setRotation(rotation);
                mCamera.setParameters(params);
            } catch (final RuntimeException e) {
                LogUtil.e(TAG, "RuntimeException in MediaCameraManager.updateCameraOrientation", e);
                if (mListener != null) {
                    mListener.onCameraError(ERROR_OPENING_CAMERA, e);
                }
            }
        }
    }

    /** Sets the current camera, releasing any previously opened camera */
    private void setCamera(final Camera camera) {
        Log.d(TAG," setCamera  camera is null ?" + (camera ==null));
        if (mCamera == camera) {
            return;
        }

        releaseMediaRecorder(true /* cleanupFile */);
        releaseCamera(mCamera);
        mCamera = camera;
        if (mCamera != null && needSilent()) {//Add for bug 785294
            mCamera.enableShutterSound(false);
        }
        tryShowPreview();
        if (mListener != null) {
            mListener.onCameraChanged();
        }
    }

    private boolean needSilent() {//Add for bug 785294
        final AudioManager audioManager = (AudioManager) Factory.get().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        return audioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT ||
                audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;
    }

    /** Shows the preview if the camera is open and the preview is loaded */
    private void tryShowPreview() {
        if (mCameraPreview == null || mCamera == null) {
            if (mOrientationHandler != null) {
                mOrientationHandler.disable();
                mOrientationHandler = null;
            }
            releaseMediaRecorder(true /* cleanupFile */);
            mFocusOverlayManager.onPreviewStopped();
            Log.d(TAG," tryShowPreview failed");
            return;
        }
        try {
            mCamera.stopPreview();
            updateCameraOrientation();

            final Camera.Parameters params = mCamera.getParameters();
            final Camera.Size pictureSize = chooseBestPictureSize();
            final Camera.Size previewSize = chooseBestPreviewSize(pictureSize);
            params.setPreviewSize(previewSize.width, previewSize.height);
            params.setPictureSize(pictureSize.width, pictureSize.height);
            logCameraSize("Setting preview size: ", previewSize);
            logCameraSize("Setting picture size: ", pictureSize);
            mCameraPreview.setSize(previewSize, mCameraInfo.orientation);
            for (final String focusMode : params.getSupportedFocusModes()) {
                if (TextUtils.equals(focusMode, Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    // Use continuous focus if available
                    params.setFocusMode(focusMode);
                    break;
                }
            }

            mCamera.setParameters(params);
            mCameraPreview.startPreview(mCamera);
            mCamera.startPreview();
            mCamera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] bytes, Camera camera) {
                    if(isStartPreview)
                        return;
                    if(!isStartPreview){
                        isStartPreview = true;
                    }
                    Log.d("MediaCameraManager"," onPreviewFrame");
                    if(mListener!=null){
                        mListener.onCameraOpen();
                    }
                }
            });
            mCamera.setAutoFocusMoveCallback(new Camera.AutoFocusMoveCallback() {
                @Override
                public void onAutoFocusMoving(final boolean start, final Camera camera) {
                    mFocusOverlayManager.onAutoFocusMoving(start);
                }
            });
            mFocusOverlayManager.setParameters(mCamera.getParameters());
            mFocusOverlayManager.setMirror(mCameraInfo.facing == CameraInfo.CAMERA_FACING_BACK);
            mFocusOverlayManager.onPreviewStarted();
            tryInitOrCleanupVideoMode();
            if (mOrientationHandler == null) {
                mOrientationHandler = new OrientationHandler(mCameraPreview.getContext());
                mOrientationHandler.enable();
            }
        } catch (final IOException e) {
            LogUtil.e(TAG, "IOException in MediaCameraManager.tryShowPreview", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_SHOWING_PREVIEW, e);
            }
        } catch (final RuntimeException e) {
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.tryShowPreview", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_SHOWING_PREVIEW, e);
            }
        }
    }

    private void tryInitOrCleanupVideoMode() {
        if (!mVideoModeRequested || mCamera == null || mCameraPreview == null) {
            releaseMediaRecorder(true /* cleanupFile */);
            return;
        }

        if (mMediaRecorder != null) {
            return;
        }

        try {
            mCamera.unlock();
             /* Add by SPRD for Bug 788856  Start */
            final int maxMessageSize = (int)(getMmsConfig().getMaxMessageSize() * 0.98);
             /* Add by SPRD for Bug 788856  end */
            /* Add by SPRD for Bug 549991 Start */
            DraftMessageData draftMessageData = GlobleUtil.getDraftMessageData();
            long attachmentSize = draftMessageData == null?0:draftMessageData.getAttachmentsFileSize();
            LogUtil.d(TAG, "attachmentSize:" + attachmentSize);
            /* Add by SPRD for Bug 549991 end */
            int remainMessageSize = maxMessageSize-(int)attachmentSize;
            if (remainMessageSize <= 0){
                throw new MaxMessageSizeException("remain attachment size is not positive");
            }
            /* SPRD: modified for bug 549991 start */
            mMediaRecorder = new MmsVideoRecorder(mCamera, mCameraIndex, mRotation, remainMessageSize);
            /* SPRD: modified for bug 549991 end */
            mMediaRecorder.prepare();
        } catch (final FileNotFoundException e) {
            LogUtil.e(TAG, "FileNotFoundException in MediaCameraManager.tryInitOrCleanupVideoMode", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_STORAGE_FAILURE, e);
            }
            setVideoMode(false);
            return;
        } catch (final IOException e) {
            LogUtil.e(TAG, "IOException in MediaCameraManager.tryInitOrCleanupVideoMode", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_INITIALIZING_VIDEO, e);
            }
            setVideoMode(false);
            return;
        }catch (final MaxMessageSizeException e) {
            LogUtil.e(TAG, "MaxMessageSizeException in MediaCameraManager.tryInitOrCleanupVideoMode", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_RAMAINING_MESSAGE_SIZE, e);
            }
            setVideoMode(false);
            return;
        }
        catch (final RuntimeException e) {
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.tryInitOrCleanupVideoMode", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_INITIALIZING_VIDEO, e);
            }
            setVideoMode(false);
            return;
        }

        tryStartVideoCapture();
    }

    private class MaxMessageSizeException extends RuntimeException {
        public MaxMessageSizeException(String messages){
            super(messages);
        }
    }

    private void tryStartVideoCapture() {
        if (mMediaRecorder == null || mVideoCallback == null) {
            return;
        }

        mMediaRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(final MediaRecorder mediaRecorder, final int what,
                    final int extra) {
                if (mListener != null) {
                    mListener.onCameraError(ERROR_RECORDING_VIDEO, null);
                }
                restoreRequestedOrientation();
            }
        });

        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(final MediaRecorder mediaRecorder, final int what, final int extra) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    stopVideo();
                }
            }
        });

        try {
            mMediaRecorder.start();
            // add for #597485 start
            Log.d(TAG, "Camera.AutoFocusCallback mVideoCallback = "
                    + mVideoCallback + System.currentTimeMillis());
            if (mVideoCallback != null)
                mVideoCallback.onStartVideoRecord();
            // add for #597485 end
            final Activity activity = UiUtils.getActivity(mCameraPreview.getContext());
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            lockOrientation();
        } catch (final IllegalStateException e) {
            LogUtil.e(TAG, "IllegalStateException in MediaCameraManager.tryStartVideoCapture", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_RECORDING_VIDEO, e);
            }
            setVideoMode(false);
            restoreRequestedOrientation();
        } catch (final RuntimeException e) {
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.tryStartVideoCapture", e);
            if (mListener != null) {
                mListener.onCameraError(ERROR_RECORDING_VIDEO, e);
            }
            setVideoMode(false);
            restoreRequestedOrientation();
        }
    }

    void stopVideo() {
        int width = ImageRequest.UNSPECIFIED_SIZE;
        int height = ImageRequest.UNSPECIFIED_SIZE;
        Uri uri = null;
        String contentType = null;
        try {
            final Activity activity = UiUtils.getActivity(mCameraPreview.getContext());
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mMediaRecorder.stop();
            width = mMediaRecorder.getVideoWidth();
            height = mMediaRecorder.getVideoHeight();
            uri = mMediaRecorder.getVideoUri();
            contentType = mMediaRecorder.getContentType();

            if (MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getSaveAttachmentsToExternal() /*is vodafine*/){
                //Log.d("tony", " mCameraPreview context is "+mCameraPreview.getContext());
                UiUtils.showSaveAttachmentInfo(mCameraPreview.getContext(), new UiUtils.ShowSaveAttachmentInfoCallback() {
                    @Override
                    public void save(final Uri uri) {
                        new SafeAsyncTask<Uri, Void, Void>(60*1000, true){
                            @Override
                            protected Void doInBackgroundTimed(Uri... params) {
                                try{
                                    Uri persistUri = UriUtil.persistContent(uri, new File(UriUtil.getValidSaveAttachmentPath()), ContentType.VIDEO_MP4);
                                    //Log.d("tony", "persistUri is "+persistUri);
                                    UriUtil.scannerSaveAttachment(Factory.get().getApplicationContext(), persistUri);
                                }catch (Exception ex){
                                    Log.d(TAG,"exception ", ex.fillInStackTrace());
                                }
                                return null;
                            }

                            @Override
                            protected void onPostExecute(Void aVoid) {
                                UiUtils.showToast(R.string.attachment_save_success);
                            }

                            @Override
                            protected void onCancelled() {
                                //
                            }
                        }.executeOnThreadPool();
                    }

                    @Override
                    public void ingore(final Uri uri) {

                    }
                }, uri);
            }

        } catch (final RuntimeException e) {
            // MediaRecorder.stop will throw a RuntimeException if the video was too short, let the
            // finally clause call the callback with null uri and handle cleanup
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.stopVideo", e);
        } finally {
            final MediaCallback videoCallback = mVideoCallback;
            mVideoCallback = null;
            releaseMediaRecorder(false /* cleanupFile */);
            if (uri == null) {
                tryInitOrCleanupVideoMode();
            }
            try{
                videoCallback.onMediaReady(uri, contentType, width, height);
            }catch (Exception e){
                LogUtil.e(TAG, "Exception in MediaCameraManager.stopVideo videoCallback", e);
            }
        }
    }

    boolean isCameraAvailable() {
        return mCamera != null && !mTakingPicture && mIsHardwareAccelerationSupported;
    }

    /**
     * External components call into this to report if hardware acceleration is supported.  When
     * hardware acceleration isn't supported, we need to report an error through the listener
     * interface
     * @param isHardwareAccelerationSupported True if the preview is rendering in a hardware
     *                                        accelerated view.
     */
    void reportHardwareAccelerationSupported(final boolean isHardwareAccelerationSupported) {
        Assert.isMainThread();
        if (mIsHardwareAccelerationSupported == isHardwareAccelerationSupported) {
            // If the value hasn't changed nothing more to do
            return;
        }

        mIsHardwareAccelerationSupported = isHardwareAccelerationSupported;
        if (!isHardwareAccelerationSupported) {
            LogUtil.e(TAG, "Software rendering - cannot open camera");
            if (mListener != null) {
                mListener.onCameraError(ERROR_HARDWARE_ACCELERATION_DISABLED, null);
            }
        }
    }

    /** Returns the scale factor to scale the width/height to max allowed in MmsConfig */
    private float getScaleFactorForMaxAllowedSize(final int width, final int height,
            final int maxWidth, final int maxHeight) {
        if (maxWidth <= 0 || maxHeight <= 0) {
            // MmsConfig initialization runs asynchronously on application startup, so there's a
            // chance (albeit a very slight one) that we don't have it yet.
            LogUtil.w(LogUtil.BUGLE_TAG, "Max image size not loaded in MmsConfig");
            return 1.0f;
        }

        if (width <= maxWidth && height <= maxHeight) {
            // Already meeting requirements.
            return 1.0f;
        }

        return Math.min(maxWidth * 1.0f / width, maxHeight * 1.0f / height);
    }

    private MmsConfig getMmsConfig() {
        if(!GlobleUtil.isSmilAttament){
          final int subId = mSubscriptionDataProvider != null ?
                mSubscriptionDataProvider.getConversationSelfSubId() :
                ParticipantData.DEFAULT_SELF_SUB_ID;
                return MmsConfig.get(subId);
        }else{
            final int subId = mSubscriptionDataProvider != null ?
                    -1 :
                    ParticipantData.DEFAULT_SELF_SUB_ID;
               return MmsConfig.get(subId);
        }

    }

    /**
     * Choose the best picture size by trying to find a size close to the MmsConfig's max size,
     * which is closest to the screen aspect ratio
     */
    private Camera.Size chooseBestPictureSize() {
        final Context context = mCameraPreview.getContext();
        final Resources resources = context.getResources();
        final DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        final int displayOrientation = resources.getConfiguration().orientation;
        int cameraOrientation = mCameraInfo.orientation;

        int screenWidth;
        int screenHeight;
        if (displayOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Rotate the camera orientation 90 degrees to compensate for the rotated display
            // metrics. Direction doesn't matter because we're just using it for width/height
            cameraOrientation += 90;
        }

        // Check the camera orientation relative to the display.
        // For 0, 180, 360, the screen width/height are the display width/height
        // For 90, 270, the screen width/height are inverted from the display
        if (cameraOrientation % 180 == 0) {
            screenWidth = displayMetrics.widthPixels;
            screenHeight = displayMetrics.heightPixels;
        } else {
            screenWidth = displayMetrics.heightPixels;
            screenHeight = displayMetrics.widthPixels;
        }

        final MmsConfig mmsConfig = getMmsConfig();
        final int maxWidth = mmsConfig.getMaxImageWidth();
        final int maxHeight = mmsConfig.getMaxImageHeight();

        // Constrain the size within the max width/height defined by MmsConfig.
        final float scaleFactor = getScaleFactorForMaxAllowedSize(screenWidth, screenHeight,
                maxWidth, maxHeight);
        screenWidth *= scaleFactor;
        screenHeight *= scaleFactor;

        final float aspectRatio = BugleGservices.get().getFloat(
                BugleGservicesKeys.CAMERA_ASPECT_RATIO,
                screenWidth / (float) screenHeight);
        final List<Camera.Size> sizes = new ArrayList<Camera.Size>(
                mCamera.getParameters().getSupportedPictureSizes());
        final int maxPixels = maxWidth * maxHeight;

        // Sort the sizes so the best size is first
        Collections.sort(sizes, new SizeComparator(maxWidth, maxHeight, aspectRatio, maxPixels));

        return sizes.get(0);
    }

    /**
     * Chose the best preview size based on the picture size.  Try to find a size with the same
     * aspect ratio and size as the picture if possible
     */
    private Camera.Size chooseBestPreviewSize(final Camera.Size pictureSize) {
        final List<Camera.Size> sizes = new ArrayList<Camera.Size>(
                mCamera.getParameters().getSupportedPreviewSizes());
        final float aspectRatio = pictureSize.width / (float) pictureSize.height;
        final int capturePixels = pictureSize.width * pictureSize.height;

        // Sort the sizes so the best size is first
        Collections.sort(sizes, new SizeComparator(Integer.MAX_VALUE, Integer.MAX_VALUE,
                aspectRatio, capturePixels));

        return sizes.get(0);
    }

    private class OrientationHandler extends OrientationEventListener {
        OrientationHandler(final Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(final int orientation) {
            /*Modify by SPRD for bug587034  2016.08.16 Start*/
            //updateCameraOrientation();
            if(orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                return;
            }
            int newOrientation = roundOrientation(orientation, mOrientation);
            System.out.println("MediaCameraManager onOrientationChanged: mOrientation="+ mOrientation +" orientation="+ orientation +" newOrientation=" + newOrientation);
            if(mOrientation != newOrientation || windowsHasRotation()){
                mOrientation = orientation;
                System.out.println("MediaCameraManager updateCameraOrientation !!!");
            updateCameraOrientation();
        }
            /*Modify by SPRD for bug587034  2016.08.16 End*/
        }
    }

    /*Add by SPRD for bug587034  2016.08.16 Start*/
    private  int roundOrientation(int orientation, int orientationHistory) {
        boolean changeOrientation = false;
        if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
            changeOrientation = true;
        } else {
           int dist = Math.abs(orientation - orientationHistory);
           dist = Math.min(dist, 360 - dist);
           changeOrientation = (dist > 45);
        }
        if (changeOrientation) {
           return ((orientation + 45) / 90 * 90) % 360;
        }
        return orientationHistory;
    }

    private boolean windowsHasRotation() {
        if(mCameraPreview == null) {
            return false;
        }
        final WindowManager windowManager =
                (WindowManager) mCameraPreview.getContext().getSystemService(
                        Context.WINDOW_SERVICE);
        final int rotation = windowManager.getDefaultDisplay().getRotation();
        System.out.println("MediaCameraManager windowsHasRotation: rotation=" + rotation + " mDegrees:" + mDegrees);
        switch (rotation) {
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if(mDegrees != rotation * 90) {
                    return true;
                } else {
                    return false;
                }
            default:
                return false;
        }
    }
    /*Add by SPRD for bug587034  2016.08.16 End*/

    private static class SizeComparator implements Comparator<Camera.Size> {
        private static final int PREFER_LEFT = -1;
        private static final int PREFER_RIGHT = 1;

        // The max width/height for the preferred size. Integer.MAX_VALUE if no size limit
        private final int mMaxWidth;
        private final int mMaxHeight;

        // The desired aspect ratio
        private final float mTargetAspectRatio;

        // The desired size (width x height) to try to match
        private final int mTargetPixels;

        public SizeComparator(final int maxWidth, final int maxHeight,
                              final float targetAspectRatio, final int targetPixels) {
            mMaxWidth = maxWidth;
            mMaxHeight = maxHeight;
            mTargetAspectRatio = targetAspectRatio;
            mTargetPixels = targetPixels;
        }

        /**
         * Returns a negative value if left is a better choice than right, or a positive value if
         * right is a better choice is better than left.  0 if they are equal
         */
        @Override
        public int compare(final Camera.Size left, final Camera.Size right) {
            // If one size is less than the max size prefer it over the other
            if ((left.width <= mMaxWidth && left.height <= mMaxHeight) !=
                    (right.width <= mMaxWidth && right.height <= mMaxHeight)) {
                return left.width <= mMaxWidth ? PREFER_LEFT : PREFER_RIGHT;
            }

            // If one is closer to the target aspect ratio, prefer it.
            final float leftAspectRatio = left.width / (float) left.height;
            final float rightAspectRatio = right.width / (float) right.height;
            final float leftAspectRatioDiff = Math.abs(leftAspectRatio - mTargetAspectRatio);
            final float rightAspectRatioDiff = Math.abs(rightAspectRatio - mTargetAspectRatio);
            if (leftAspectRatioDiff != rightAspectRatioDiff) {
                return (leftAspectRatioDiff - rightAspectRatioDiff) < 0 ?
                        PREFER_LEFT : PREFER_RIGHT;
            }

            // At this point they have the same aspect ratio diff and are either both bigger
            // than the max size or both smaller than the max size, so prefer the one closest
            // to target size
            final int leftDiff = Math.abs((left.width * left.height) - mTargetPixels);
            final int rightDiff = Math.abs((right.width * right.height) - mTargetPixels);
            return leftDiff - rightDiff;
        }
    }

    @Override // From FocusOverlayManager.Listener
    public void autoFocus() {
        LogUtil.d(TAG," mCamera is null :" +(mCamera == null));
        if (mCamera == null) {
            return;
        }

        try {
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(final boolean success, final Camera camera) {
                    mFocusOverlayManager.onAutoFocus(success, false /* shutterDown */);
                }
            });
        } catch (final RuntimeException e) {
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.autoFocus", e);
            // If autofocus fails, the camera should have called the callback with success=false,
            // but some throw an exception here
            mFocusOverlayManager.onAutoFocus(false /*success*/, false /*shutterDown*/);
        }
    }

    @Override // From FocusOverlayManager.Listener
    public void cancelAutoFocus() {
        if (mCamera == null) {
            return;
        }
        try {
            mCamera.cancelAutoFocus();
        } catch (final RuntimeException e) {
            // Ignore
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager.cancelAutoFocus", e);
        }
    }

    @Override // From FocusOverlayManager.Listener
    public boolean capture() {
        return false;
    }

    @Override // From FocusOverlayManager.Listener
    public void setFocusParameters() {
        if (mCamera == null) {
            return;
        }
        try {
            final Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(mFocusOverlayManager.getFocusMode());
            if (parameters.getMaxNumFocusAreas() > 0) {
                // Don't set focus areas (even to null) if focus areas aren't supported, camera may
                // crash
                parameters.setFocusAreas(mFocusOverlayManager.getFocusAreas());
            }
            parameters.setMeteringAreas(mFocusOverlayManager.getMeteringAreas());
            mCamera.setParameters(parameters);
        } catch (final RuntimeException e) {
            // This occurs when the device is out of space or when the camera is locked
            LogUtil.e(TAG, "RuntimeException in MediaCameraManager setFocusParameters");
        }
    }

    private void logCameraSize(final String prefix, final Camera.Size size) {
        // Log the camera size and aspect ratio for help when examining bug reports for camera
        // failures
        LogUtil.i(TAG, prefix + size.width + "x" + size.height +
                " (" + (size.width / (float) size.height) + ")");
    }


    private Integer mSavedOrientation = null;

    private void lockOrientation() {
        // when we start recording, lock our orientation
        final Activity a = UiUtils.getActivity(mCameraPreview.getContext());
        final WindowManager windowManager =
                (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
        final int rotation = windowManager.getDefaultDisplay().getRotation();

        mSavedOrientation = a.getRequestedOrientation();
        switch (rotation) {
            case Surface.ROTATION_0:
                a.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case Surface.ROTATION_90:
                a.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case Surface.ROTATION_180:
                a.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;
            case Surface.ROTATION_270:
                a.setRequestedOrientation(
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
        }

    }

    private void restoreRequestedOrientation() {
        if (mSavedOrientation != null) {
         if(mCameraPreview != null){ //757273
                final Activity a = UiUtils.getActivity(mCameraPreview.getContext());
                if (a != null) {
                    a.setRequestedOrientation(mSavedOrientation);
                }
                mSavedOrientation = null;
         }
        }
    }

    static boolean hasCameraPermission() {
        return OsUtil.hasPermission(Manifest.permission.CAMERA);
    }

    /* Add by SPRD for Bug 549991 Start */
    long getVideoSizeLimit() {
        /* SPRD: modified for bug 572239 start */
        if (mMediaRecorder != null) {
            return mMediaRecorder.getSizeLimit();
        } else {
            return 0;
        }
        /* SPRD: modified for bug 572239 end */
    }
    /* Add by SPRD for Bug 549991 end */

    /*bug 1121918, begin*/
    private boolean startRecordingFlag;

    public void setStartRecordingFlag(boolean flag){
        startRecordingFlag = flag;
    }

    public boolean getStartRecordingFlag(){
        return startRecordingFlag;
    }
    /*bug 1121918, end*/
}
