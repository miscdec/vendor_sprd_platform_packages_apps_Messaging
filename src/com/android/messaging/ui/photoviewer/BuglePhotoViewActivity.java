package com.android.messaging.ui.photoviewer;

import android.content.Intent;
import android.app.Activity;
import android.net.Uri;

import com.android.ex.photo.PhotoViewActivity;
import com.android.ex.photo.PhotoViewController;
import com.android.messaging.util.OsUtil;
import com.sprd.messaging.util.StorageUtil;
/**
 * Activity to display the conversation images in full-screen. Most of the customization is in
 * {@link BuglePhotoViewController}.
 */
public class BuglePhotoViewActivity extends PhotoViewActivity {
    @Override
    public PhotoViewController createController() {
        if(!OsUtil.hasSmsPermission()){
               OsUtil.requestMissingPermission(this);
        }
        BuglePhotoViewController  controller = new BuglePhotoViewController(this);
        controller.setHost(this);
        return controller;
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {//by sprd
        if (requestCode == StorageUtil.REQUEST_EXTERNAL_STORAGE_PERMISSION && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                final Uri uri = data.getData();
                final StorageUtil storageUtil = new StorageUtil();
                if(StorageUtil.isDownloadDirInSD(uri)){
                    final int takeFalgs = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFalgs);
                    if(getController() instanceof BuglePhotoViewController){
                        ((BuglePhotoViewController)getController()).savePhotoView(uri);
                    }
                }else {
                    storageUtil.showPermissionFailDialog(this);
                }
            }
        }
    }

}
