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

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.SafeAsyncTask;

import android.provider.ContactsContract.Contacts;
import android.util.Log;

import java.util.ArrayList;

import com.android.messaging.util.ContentType;
import com.android.messaging.util.UriUtil;

import java.util.HashMap;


/**
 * Wraps around the functionalities to allow the user to pick vcard from the
 * Contacts picker. Instances of this class must be tied to a Fragment which is
 * able to delegate activity result callbacks.
 */
public class VcardPicker {

    /**
     * An interface for a listener that listens for when a document has been
     * picked.
     */
    public interface VcardPickerListener {
        /**
         * Called when Contacts is selected from picker. At this point, the file
         * hasn't been actually loaded and staged in the temp directory, so we
         * are passing in a pending MessagePartData, which the consumer should
         * use to display a placeholder image.
         *
         * @param pendingItem
         *            a temporary attachment data for showing the placeholder
         *            state.
         */
        void onVcardSelected(PendingAttachmentData pendingItem);
        void onTextSelected(HashMap<String, String> contacts);
    }

    // The owning fragment.
    private final Fragment mFragment;

    // The listener on the picker events.
    private final VcardPickerListener mListener;

    /**
     * Creates a new instance of VcardPicker.
     *
     * @param activity
     *            The activity that owns the picker, or the activity that hosts
     *            the owning fragment.
     */
    public VcardPicker(final Fragment fragment,
            final VcardPickerListener listener) {
        mFragment = fragment;
        mListener = listener;
    }

    /**
     * Intent out to open an image/video from document picker.
     */
    public void launchPicker() {
        UIIntents.get().launchVcardPicker(mFragment);
    }
    public void launchTextPicker(){
        UIIntents.get().launchTextPicker(mFragment);
    }

    /**
     * Must be called from the fragment/activity's onActivityResult().
     */
    public void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
        if (requestCode == UIIntents.REQUEST_PICK_VCARD_PICKER
                && resultCode == Activity.RESULT_OK) {
            ArrayList<String> lookupStringKeys = data
                    .getStringArrayListExtra("result");
            //bug708744 begin
            if(lookupStringKeys == null){
                lookupStringKeys = data.getStringArrayListExtra("com.android.contacts.action.CONTACT_IDS");
            }
            //bug708744 end
            if (lookupStringKeys != null) {
                final Uri vcardUri = getVcardUri(lookupStringKeys);
                if (vcardUri != null) {
                    prepareVcardForAttachment(vcardUri);
                }
            }

        } else if (requestCode == UIIntents.REQUEST_PICK_CONTACT_AS_TEXT
                && resultCode == Activity.RESULT_OK) {
            HashMap<String, String> contacts = (HashMap<String, String>) data
                    .getSerializableExtra("result");
            //bug708744 begin
            if(contacts == null){
                contacts = (HashMap<String, String>) data.getSerializableExtra("com.android.contacts.action.CONTACT_IDS");
            }
            //bug708744 end
            mListener.onTextSelected(contacts);
        }
    }

    public static Uri getVcardUri(ArrayList<String> lookupKeys) {
        StringBuilder uriListBuilder = new StringBuilder();
        int index = 0;
        for (String key : lookupKeys) {
            if (index != 0)
                uriListBuilder.append(':');
            uriListBuilder.append(key);
            index++;
        }
        String lookupKeyStrings = lookupKeys.size() > 1 ? Uri
                .encode(uriListBuilder.toString()) : uriListBuilder.toString();
        Uri uri = Uri.withAppendedPath(
                lookupKeys.size() > 1 ? Contacts.CONTENT_MULTI_VCARD_URI
                        : Contacts.CONTENT_VCARD_URI, lookupKeyStrings);
        return uri;
    }

    private void prepareVcardForAttachment(final Uri vcardUri) {
        // Notify our listener with a PendingAttachmentData containing the
        // metadata.
        //add for bug 774769  start
        new SafeAsyncTask<Void, Void, Uri>() {

            protected Uri doInBackgroundTimed(final Void... params) {
                GlobleUtil.sendMessage(GlobleUtil.TAG_LOADING_DIALOG, GlobleUtil.MSG_OPEN_LOADING_DIALOG);
                Log.d("VcardPicker","vcardUri:"+vcardUri);
                Uri persistedUri =  UriUtil.persistContentToScratchSpace(vcardUri);
                Log.d("VcardPicker","persistedUri:"+persistedUri);
                return persistedUri;
                /* Modify by SPRD for Bug:527552 Start */
              //  return ContentType.TEXT_LOWER_VCARD;
                /* Modify by SPRD for Bug:527552 end */
            }

            @Override
            protected void onPostExecute(final Uri uri) {
                // Ask the listener to create a temporary placeholder item to
                // show the progress.
                //Modify by 762372 start
                final PendingAttachmentData pendingItem = PendingAttachmentData
                        .createPendingAttachmentData(ContentType.TEXT_LOWER_VCARD, uri);
                mListener.onVcardSelected(pendingItem);
                //Modify by 762372 end
                GlobleUtil.sendMessage(GlobleUtil.TAG_LOADING_DIALOG, GlobleUtil.MSG_CLOSE_LOADING_DIALOG);
            }
        }.executeOnThreadPool();
    }
    //add for bug 774769  end
}
