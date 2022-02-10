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
package com.android.messaging.datamodel;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.messaging.util.Assert;
import com.android.messaging.util.LogUtil;

public class DatabaseUpgradeHelper {
    private static final String TAG = LogUtil.BUGLE_DATABASE_TAG;

    public void doOnUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        Assert.isTrue(newVersion >= oldVersion);
        Log.d(TAG, "oldVersion:" + oldVersion + ", newVersion:" + newVersion);
        if (oldVersion == newVersion) {
            return;
        }

        LogUtil.i(TAG, "Database upgrade started from version " + oldVersion + " to " + newVersion);

        switch (oldVersion) {
            case 0:
               //[Add] for bug: [547931] begin
                DatabaseHelper.rebuildTables(db);
               //[Add] for bug: [547931] end
            case 1:
                if (newVersion <= 1) {
                    return;
                }
                db.beginTransaction();
                try {
                    DatabaseHelper.upgradeDatabaseToVersion2(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break;
                }finally {
                    db.endTransaction();
                }

            case 2:
                  //add for bug688310 begin
                  if(newVersion <= 2){
                      return;
                  }
                  db.beginTransaction();
                  try{
                      DatabaseHelper.upgradeDatabaseToVersion3(db);
                      db.setTransactionSuccessful();
                  } catch (Throwable ex) {
                      Log.e(TAG, ex.getMessage(), ex);
                      break;
                }finally {
                      db.endTransaction();
                }
                //add for bug688310 end
            case 3:
                // add for bug 725726 start
                  if(newVersion <= 3){
                      return;
                  }
                  db.beginTransaction();
                  try{
                      DatabaseHelper.upgradeDatabaseToVersion4(db);
                      db.setTransactionSuccessful();
                  } catch (Throwable ex) {
                      Log.e(TAG, ex.getMessage(), ex);
                      break;
                }finally {
                      db.endTransaction();
                }
                return;
           // add for bug 725726 end
            default :
                Log.e(TAG, "default ");
                return ;
        }

        // Add future upgrade code here
    }

    public void onDowngrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        DatabaseHelper.rebuildTables(db);
        LogUtil.e(TAG, "Database downgrade requested for version " +
                oldVersion + " version " + newVersion + ", forcing db rebuild!");
    }
}
