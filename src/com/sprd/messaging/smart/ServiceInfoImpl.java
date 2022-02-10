/**
 * Created by SPRD on 2019/01/07.
 */
package com.sprd.messaging.smart;

import android.content.ContentValues;
import android.os.AsyncTask;
import android.text.TextUtils;

import com.android.messaging.datamodel.BugleDatabaseOperations;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.util.LogUtil;

public final class ServiceInfoImpl implements ISmartSdk.IServiceInfoCallBack {
    private ParticipantData participantData;

    private ServiceInfoImpl() {
    }

    public ServiceInfoImpl(ParticipantData data) {
        this.participantData = data;
    }

    @Override
    public void getResult(ServiceInfoEntity serviceInfoEntity) {
        final String portName = serviceInfoEntity.getPortName();
        final String portLogo = serviceInfoEntity.getPortLogo();
        final boolean isSmartMessage = (!TextUtils.isEmpty(portName) && !TextUtils.isEmpty(portLogo));
        if (isSmartMessage) {
            participantData.setContactId(ParticipantData.PARTICIPANT_CONTACT_ID_SERVICEPORT);
            participantData.setFullName(portName);
            participantData.setProfilePhotoUri(portLogo);
            new UpdateDbAsyncTask(DataModel.get().getDatabase(), participantData).execute();
        }
    }

    private static final class UpdateDbAsyncTask extends AsyncTask<Void, Void, Void> {
        private DatabaseWrapper db;
        private ParticipantData participantData;

        public UpdateDbAsyncTask(DatabaseWrapper databaseWrapper, ParticipantData data) {
            this.db = databaseWrapper;
            this.participantData = data;
        }

        @Override
        protected final Void doInBackground(final Void... params) {
            updateSingleParticipant(db, participantData);
            BugleDatabaseOperations.refreshConversationsForParticipant(participantData.getId());
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            SmartMessageServer.getInstance().notifyObserver(participantData.getId());
        }
    }

    public static void updateSingleParticipant(final DatabaseWrapper db, final ParticipantData participantData) {
        final ContentValues values = new ContentValues();
        LogUtil.d(LogUtil.BUGLE_SMART_TAG, "updateSingleParticipant, partId:" + participantData.getId());
        if (participantData.getContactId() < 0) {
            values.put(DatabaseHelper.ParticipantColumns.CONTACT_ID, participantData.getContactId());
            values.put(DatabaseHelper.ParticipantColumns.FULL_NAME, participantData.getFullName());
        }
        values.put(DatabaseHelper.ParticipantColumns.PROFILE_PHOTO_URI, participantData.getProfilePhotoUri());

        db.beginTransaction();
        try {
            db.update(DatabaseHelper.PARTICIPANTS_TABLE, values, DatabaseHelper.ParticipantColumns._ID + "=?",
                    new String[]{participantData.getId()});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}