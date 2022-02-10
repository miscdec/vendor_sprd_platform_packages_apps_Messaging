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

package com.android.messaging.ui.photoviewer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import androidx.loader.content.Loader;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ShareActionProvider;
import android.widget.Toast;

import com.android.ex.photo.PhotoViewController;
import com.android.ex.photo.adapters.PhotoPagerAdapter;
import com.android.ex.photo.loaders.PhotoBitmapLoaderInterface.BitmapResult;
import com.android.messaging.R;
import com.android.messaging.datamodel.ConversationImagePartsView.PhotoViewQuery;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.ui.conversation.ConversationFragment;
import com.android.messaging.util.Dates;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;

import android.util.Log;
import com.sprd.messaging.drm.MessagingDrmSession;
import android.drm.DecryptHandle;
//import com.sprd.messaging.drm.DecryptHandle;
import com.sprd.messaging.drm.MessagingDrmHelper;
import com.sprd.messaging.util.StorageUtil;

/**
 * Customizations for the photoviewer to display conversation images in full screen.
 */
public class BuglePhotoViewController extends PhotoViewController implements BuglePhotoBitmapLoader.OnLoadListener {
    private ShareActionProvider mShareActionProvider;
    private MenuItem mShareItem;
    private MenuItem mSaveItem;
    private boolean mIsDrmUri;
    private DecryptHandle mDecryptHandle;
    private BuglePhotoViewActivity mHost;
    private static final String TAG = "BuglePhotoViewController";

    public BuglePhotoViewController(final ActivityInterface activity) {
        super(activity);
    }

    @Override
    public Loader<BitmapResult> onCreateBitmapLoader(
            final int id, final Bundle args, final String uri) {
        switch (id) {
            case BITMAP_LOADER_AVATAR:
            case BITMAP_LOADER_THUMBNAIL:
            case BITMAP_LOADER_PHOTO:
                try {
                    Uri dataUri = Uri.parse(uri);
                    String dataPath = MessagingDrmSession.get().getPath(dataUri);
                    Log.d(TAG, " uri is " + dataUri + " path is " + dataPath);
                    if (dataPath != null && MessagingDrmSession.get().drmCanHandle(dataPath, null)) {
                        Log.d(TAG, " is drm data ");
                        mIsDrmUri = true;
                    }
                } catch (Exception ex) {
                    Log.d(TAG, " drm ex " + ex);
                }
                return new BuglePhotoBitmapLoader(getActivity().getContext(), uri, this);
            default:
                LogUtil.e(LogUtil.BUGLE_TAG,
                        "Photoviewer unable to open bitmap loader with unknown id: " + id);
                return null;
        }
    }

    @Override
    public void updateActionBar() {
        final Cursor cursor = getCursorAtProperPosition();

        if (mSaveItem == null || cursor == null) {
            // Load not finished, called from framework code before ready
            return;
        }
        // Show the name as the title
        mActionBarTitle = cursor.getString(PhotoViewQuery.INDEX_SENDER_FULL_NAME);
        if (TextUtils.isEmpty(mActionBarTitle)) {
            // If the name is not known, fall back to the phone number
            mActionBarTitle = cursor.getString(PhotoViewQuery.INDEX_DISPLAY_DESTINATION);
        }

        // Show the timestamp as the subtitle
        final long receivedTimestamp = cursor.getLong(PhotoViewQuery.INDEX_RECEIVED_TIMESTAMP);
        mActionBarSubtitle = Dates.getMessageTimeString(receivedTimestamp).toString();

        setActionBarTitles(getActivity().getActionBarInterface());
        mSaveItem.setVisible(!isTempFile());

        updateShareActionProvider();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (mIsDrmUri) {
            Log.d(TAG, " onCreateLoader mIsDrmUri " + mIsDrmUri);
            return null;
        }
        Log.d(TAG, " onCreateLoader id " + id);
        return super.onCreateLoader(id, args);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (mIsDrmUri) {
            Log.d(TAG, " onLoadFinished mIsDrmUri " + mIsDrmUri);
            return;
        }
        Log.d(TAG, " onLoadFinished mIsDrmUri " + mIsDrmUri);
        super.onLoadFinished(loader, data);
    }

    private void updateShareActionProvider() {
        final PhotoPagerAdapter adapter = getAdapter();
        final Cursor cursor = getCursorAtProperPosition();
        if (mShareActionProvider == null || mShareItem == null || adapter == null ||
                cursor == null) {
            // Not enough stuff loaded to update the share action
            /*added by SPRD for Bug 712799 start*/
            if (mShareItem != null) mShareItem.setVisible(false);
             /*added by SPRD for Bug 712799 end*/
            return;
        }
        final String photoUri = adapter.getPhotoUri(cursor);
        if (isTempFile()) {
            Log.d(TAG, " updateShareActionProvider isTempFile true"); /*added by SPRD for Bug 712799 */
            mShareItem.setVisible(false);
            return;
        }
        //Bug 1040316 begin
        Uri fp = GlobleUtil.getFileProviderUri(getActivity().getContext(), Uri.parse(photoUri));
        if (null == fp) {
            Log.e(TAG, "updateShareActionProvider <" + photoUri + "> isn't file.");
            mShareItem.setVisible(false);
            return;
        }
        //Bug 1040316 end
        final String contentType = adapter.getContentType(cursor);

        final Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType(contentType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fp);  //Bug 1040316 Uri.parse(photoUri) -> fp
        mShareActionProvider.setShareIntent(shareIntent);
        mShareItem.setVisible(true);
    }

    /**
     * Checks whether the current photo is a temp file.  A temp file can be deleted at any time, so
     * we need to disable share and save options because the file may no longer be there.
     */
    private boolean isTempFile() {
        final Cursor cursor = getCursorAtProperPosition();
        final Uri photoUri = Uri.parse(getAdapter().getPhotoUri(cursor));
        return MediaScratchFileProvider.isMediaScratchSpaceUri(photoUri);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        Log.d(TAG, " onCreateOptionsMenu mIsDrmUri " + mIsDrmUri);
        if (mIsDrmUri) {
            return true;
        }
        ((Activity) getActivity()).getMenuInflater().inflate(R.menu.photo_view_menu, menu);
        // Get the ShareActionProvider
        mShareItem = menu.findItem(R.id.action_share);
        mShareActionProvider = (ShareActionProvider) mShareItem.getActionProvider();
        updateShareActionProvider();

        mSaveItem = menu.findItem(R.id.action_save);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        /*added by SPRD for Bug 712799 start*/
        Log.d(TAG, " onPrepareOptionsMenu mIsEmpty " + mIsEmpty);
        if (mShareItem != null) mShareItem.setVisible(!mIsEmpty);
        if (mSaveItem != null) mSaveItem.setVisible(!mIsEmpty);
         /*added by SPRD for Bug 712799 end*/
        return !mIsEmpty;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            if (OsUtil.hasStoragePermission()) {
                //bug 993043 begin
                StorageUtil storageUtil = new StorageUtil((Activity)getActivity());
                if(storageUtil.sdcardExist()){
                    final Uri uri = new StorageUtil().getDownloadAccessUri((Activity)getActivity());
                    Log.d(TAG, "save_attachment, uri:" + uri);
                    if(uri != null){
                        savePhotoView(uri);
                    }else{
                        storageUtil.requestSdcardPermmission();
                    }
                }else{
                    savePhotoView(null);
                }
                //bug 993043 end
            } else {
                ((Activity)getActivity()).requestPermissions(
                        new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public PhotoPagerAdapter createPhotoPagerAdapter(final Context context,
                                                     final FragmentManager fm, final Cursor c, final float maxScale) {
        return new BuglePhotoPageAdapter(context, fm, c, maxScale, mDisplayThumbsFullScreen);
    }

    public void onLoading(DecryptHandle decryptHandle) {
        mDecryptHandle = decryptHandle;
    }

    @Override
    public void onPause() {
        if (mIsDrmUri) {
            if (mDecryptHandle != null) {
                MessagingDrmHelper.setDrmPlayStatusStop(mDecryptHandle);
            }
            if (mHost != null) mHost.finish();
        }
    }

    public void setHost(BuglePhotoViewActivity host) {
        mHost = host;
    }
    private Toast mToast;
    //bug 993043 begin
    public void savePhotoView(Uri externalUri){
        ConversationFragment.SaveAttachmentTask saveAttachmentTask = new ConversationFragment.SaveAttachmentTask((Activity) getActivity());
        final PhotoPagerAdapter adapter = getAdapter();
        final Cursor cursor = getCursorAtProperPosition();
        if (cursor == null) {
            final Context context = getActivity().getContext();
            final String error = context.getResources().getQuantityString(
                    R.plurals.attachment_save_error, 1, 1);
             if(mToast != null){
                 mToast.cancel();
              }
            mToast=Toast.makeText(context, error, Toast.LENGTH_SHORT);
            mToast .show();
            return;
        }
        final String photoUri = adapter.getPhotoUri(cursor);
        if(externalUri != null){
            saveAttachmentTask.addAttachmentToSave(Uri.parse(photoUri), adapter.getContentType(cursor),externalUri);
        }else{
            saveAttachmentTask.addAttachmentToSave(Uri.parse(photoUri), adapter.getContentType(cursor));
        }
        saveAttachmentTask.executeOnThreadPool();
    }
    //bug 993043 end
}
