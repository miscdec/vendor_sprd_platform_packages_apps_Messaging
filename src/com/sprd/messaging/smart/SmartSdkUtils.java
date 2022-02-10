/**
 * Created by SPRD on 2019/01/07.
 */
package com.sprd.messaging.smart;

import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.ParticipantRefresh;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.SafeAsyncTask;

public final class SmartSdkUtils {

    public static ISmartSdk createSmartSdk() {
        if (MmsConfig.getCmccSdkEnabled()) {
            return CmccSmartSdk.getInstance();
        } else if (MmsConfig.getCuccSdkEnabled()) {
            return CuccSmartSdk.getInstance();
        } else if (MmsConfig.isCtccOp()) {
            return CtccSmartSdk.getInstance();
        }
        return null;
    }

    public static void updateSmartInfoForNew(DatabaseWrapper dbWrapper, ParticipantData participant, String participantId) {
        participant.setId(participantId);
        SafeAsyncTask.executeOnThreadPool(new UpdateSmartMessageRunnable(dbWrapper, participant));
    }

    private static class UpdateSmartMessageRunnable implements Runnable {
        private DatabaseWrapper db;
        private ParticipantData participantData;

        public UpdateSmartMessageRunnable(DatabaseWrapper databaseWrapper, ParticipantData data) {
            this.db = databaseWrapper;
            this.participantData = data;
        }

        @Override
        public void run() {
            ParticipantRefresh.updateParticipantDataIfSmart(db.getContext(), participantData);
        }
    }

}

