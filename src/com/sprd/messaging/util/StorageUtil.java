package com.sprd.messaging.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;

import com.android.messaging.R;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.OsUtil;

import java.io.File;
import java.util.List;

/**
 * Created by sprd on 2019-01-03.
 */
public class StorageUtil {
    public static final String TAG = "StorageUtil";
    public static final int REQUEST_EXTERNAL_STORAGE_PERMISSION = 111;

    private StorageVolume sdVolume = null;
    private Activity mActivity = null;
    private AlertDialog mAlertDialog = null;

    public StorageUtil(){
    }

    public StorageUtil(Activity activity){
        this.mActivity = activity;
        updateExternalVolume();
    }

    private void updateExternalVolume(){
        if(null == mActivity){
            return;
        }
        for (StorageVolume volume : getVolumes()) {
            File volumePath = volume.getPathFile();
            if (!volume.isPrimary()
                    && Environment.MEDIA_MOUNTED.equals(EnvironmentEx.getExternalStoragePathState())
                    && volumePath != null
                    && volumePath.equals(EnvironmentEx.getExternalStoragePath())) {
                sdVolume = volume;
            }
        }
    }

    private List<StorageVolume> getVolumes() {
        final StorageManager sm = (StorageManager) mActivity.getSystemService(Context.STORAGE_SERVICE);
        return sm.getStorageVolumes();
    }

    public boolean sdcardExist(){
        return sdVolume != null && !OsUtil.isSecondaryUser();
    }

    public void requestSdcardPermmission(){
        if(sdVolume != null){
            Intent intent;
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P){// bug 1147932
                intent = sdVolume.createOpenDocumentTreeIntent();
            }else {
                intent = sdVolume.createAccessIntent(Environment.DIRECTORY_DOWNLOADS);
            }
            if (intent != null) {
                mActivity.startActivityForResult(intent, REQUEST_EXTERNAL_STORAGE_PERMISSION);
            }
        }
    }

    private static final String INTERNAL_BASE_DIR = "primary";

    private static final String SD_DOWNLOAD_DIR = "Download";

    public static boolean isDownloadDirInSD(final Uri uri){
        if(null == uri){
            return false;
        }
        final String documentId = DocumentsContract.getTreeDocumentId(uri);
        return !documentId.startsWith(INTERNAL_BASE_DIR) && documentId.endsWith(SD_DOWNLOAD_DIR);
    }

    public void showPermissionFailDialog(final Context context) {
        if (mAlertDialog != null && mAlertDialog.isShowing()) {
            return;
        }
        mAlertDialog = new AlertDialog.Builder(context)
                .setCancelable(false)
                .setTitle(R.string.error_external_storage_access)
                .setMessage(R.string.download_request_confirm)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    public void closeAlertDialog() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

    private String getDownloadParam() {
        String basePath = EnvironmentEx.getExternalStoragePath().toString();
        String[] pathName = basePath.split("/");
        return pathName[pathName.length - 1] + "%3A" + SD_DOWNLOAD_DIR;
    }

    public Uri getDownloadAccessUri(Context context) {
        final String downloadSuffix = getDownloadParam();
        List<UriPermission> uriPermissions = context.getContentResolver().getPersistedUriPermissions();
        for (UriPermission permission : uriPermissions) {
            LogUtil.d(TAG, "getDownloadAccessUri, permission: " + permission.toString());
            if (downloadSuffix != null && permission.getUri().toString().endsWith(downloadSuffix)) {
                return permission.getUri();
            }
        }
        return null;
    }
}
