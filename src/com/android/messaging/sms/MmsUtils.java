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

package com.android.messaging.sms;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Addr;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.TelephonyEx;
import android.provider.TelephonyEx.MmsEx;
import android.provider.TelephonyEx.SmsEx;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.mms.CarrierConfigValuesLoader;

import com.android.internal.telephony.ISub;
import com.android.messaging.Factory;
import com.android.messaging.R;
import com.android.messaging.datamodel.BugleNotifications;
import com.android.messaging.datamodel.CursorQueryData;
import com.android.messaging.datamodel.DataModel;
import com.android.messaging.datamodel.DatabaseHelper;
import com.android.messaging.datamodel.DatabaseWrapper;
import com.android.messaging.datamodel.MediaScratchFileProvider;
import com.android.messaging.datamodel.action.DownloadMmsAction;
import com.android.messaging.datamodel.action.ProcessDeliveryReportAction;
import com.android.messaging.datamodel.action.SendMessageAction;
import com.android.messaging.datamodel.data.ConversationMessageData;
import com.android.messaging.datamodel.data.DraftMessageData;
import com.android.messaging.datamodel.data.MessageData;
import com.android.messaging.datamodel.data.MessagePartData;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.mmslib.InvalidHeaderValueException;
import com.android.messaging.mmslib.MmsException;
import com.android.messaging.mmslib.SqliteWrapper;
import com.android.messaging.mmslib.pdu.CharacterSets;
import com.android.messaging.mmslib.pdu.DeliveryInd;
import com.android.messaging.mmslib.pdu.EncodedStringValue;
import com.android.messaging.mmslib.pdu.GenericPdu;
import com.android.messaging.mmslib.pdu.NotificationInd;
import com.android.messaging.mmslib.pdu.PduBody;
import com.android.messaging.mmslib.pdu.PduComposer;
import com.android.messaging.mmslib.pdu.PduHeaders;
import com.android.messaging.mmslib.pdu.PduParser;
import com.android.messaging.mmslib.pdu.PduPart;
import com.android.messaging.mmslib.pdu.PduPersister;
import com.android.messaging.mmslib.pdu.ReadOrigInd;
import com.android.messaging.mmslib.pdu.ReadRecInd;
import com.android.messaging.mmslib.pdu.RetrieveConf;
import com.android.messaging.mmslib.pdu.SendConf;
import com.android.messaging.mmslib.pdu.SendReq;
import com.android.messaging.receiver.MmsSmsEventReceiver;
import com.android.messaging.sms.SmsSender.SendResult;
import com.android.messaging.util.Assert;
import com.android.messaging.util.BugleGservices;
import com.android.messaging.util.BugleGservicesKeys;
import com.android.messaging.util.BuglePrefs;
import com.android.messaging.util.ContactUtil;
import com.android.messaging.util.ContentType;
import com.android.messaging.util.DebugUtils;
import com.android.messaging.util.EmailAddress;
import com.android.messaging.util.GlobleUtil;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.ImageUtils.ImageResizer;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.MediaMetadataRetrieverWrapper;
import com.android.messaging.util.OsUtil;
import com.android.messaging.util.PhoneUtils;
import com.google.common.base.Joiner;
import com.sprd.messaging.drm.MessagingDrmSession;
import com.sprd.messaging.util.SmsMessageAdapter;
import com.sprd.messaging.util.SystemAdapter;
import com.sprd.messaging.util.Utils;
import com.android.internal.util.HexDump;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
/**
 * Utils for sending sms/mms messages.
 */
public class MmsUtils {
    private static final String TAG = LogUtil.BUGLE_TAG;

    public static final boolean DEFAULT_DELIVERY_REPORT_MODE  = false;
    public static final boolean DEFAULT_READ_REPORT_MODE = false;
    public static final long DEFAULT_EXPIRY_TIME_IN_SECONDS = 7 * 24 * 60 * 60;
    public static final int DEFAULT_PRIORITY = PduHeaders.PRIORITY_NORMAL;
    private static final int ATTACHMENT_EXCEEDED_SIZE = 10*1024*1024;
    public static final int MAX_SMS_RETRY = 3;
    // 489220 begin
    public static final String NOTIFY_SHOW_MMS_REPORT_ACTION = "com.sprd.notify_show_mms_report_action";
    public static final String NOTIFY_SHOW_MMS_SMS_REPORT_ACTION ="com.sprd.notify_show_mms_sms_report_action";
    //489220 end

    public static final String ACTION_SERVICE_ALARM_MESSAGE ="com.sprd.alarmMessageRing";
    //762218 begin end
    public static final String ACTION_TOAST_COPY_MESSAGE_TO_SIM =
            "com.android.providers.telephony.TOAST_COPY_MESSAGE_TO_SIM";
    /**
     * MMS request succeeded
     */
    public static final int MMS_REQUEST_SUCCEEDED = 0;
    /**
     * MMS request failed with a transient error and can be retried automatically
     */
    public static final int MMS_REQUEST_AUTO_RETRY = 1;
    /**
     * MMS request failed with an error and can be retried manually
     */
    public static final int MMS_REQUEST_MANUAL_RETRY = 2;
    /**
     * MMS request failed with a specific error and should not be retried
     */
    public static final int MMS_REQUEST_NO_RETRY = 3;

    public static final String getRequestStatusDescription(final int status) {
        switch (status) {
            case MMS_REQUEST_SUCCEEDED:
                return "SUCCEEDED";
            case MMS_REQUEST_AUTO_RETRY:
                return "AUTO_RETRY";
            case MMS_REQUEST_MANUAL_RETRY:
                return "MANUAL_RETRY";
            case MMS_REQUEST_NO_RETRY:
                return "NO_RETRY";
            default:
                return String.valueOf(status) + " (check MmsUtils)";
        }
    }

    public static final int PDU_HEADER_VALUE_UNDEFINED = 0;

    private static final int DEFAULT_DURATION = 0; //ms//sprd 600980

    // amount of space to leave in a MMS for text and overhead.
    private static final int MMS_MAX_SIZE_SLOP = 1024;
    public static final long INVALID_TIMESTAMP = 0L;
    private static String[] sNoSubjectStrings;

    public static class MmsInfo {
        public Uri mUri;
        public int mMessageSize;
        public PduBody mPduBody;
    }

    // Sync all remote messages apart from drafts
    private static final String REMOTE_SMS_SELECTION = String.format(
            Locale.US,
            "(%s IN (%d, %d, %d, %d, %d))",
            Sms.TYPE,
            Sms.MESSAGE_TYPE_INBOX,
            Sms.MESSAGE_TYPE_OUTBOX,
            Sms.MESSAGE_TYPE_QUEUED,
            Sms.MESSAGE_TYPE_FAILED,
            Sms.MESSAGE_TYPE_SENT);

    private static final String REMOTE_SMS_SELECTION_ALARM = String.format(
            Locale.US,
            "(%s IN (%d, %d, %d, %d, %d, %d))",
            Sms.TYPE,
            Sms.MESSAGE_TYPE_INBOX,
            Sms.MESSAGE_TYPE_OUTBOX,
            Sms.MESSAGE_TYPE_QUEUED,
            Sms.MESSAGE_TYPE_FAILED,
            Sms.MESSAGE_TYPE_SENT,
            SmsEx.MESSAGE_TYPE_ALARM);

    private static final String REMOTE_MMS_SELECTION = String.format(
            Locale.US,
            "((%s IN (%d, %d, %d, %d)) AND (%s IN (%d, %d, %d)))",
            Mms.MESSAGE_BOX,
            Mms.MESSAGE_BOX_INBOX,
            Mms.MESSAGE_BOX_OUTBOX,
            Mms.MESSAGE_BOX_SENT,
            Mms.MESSAGE_BOX_FAILED,
            Mms.MESSAGE_TYPE,
            PduHeaders.MESSAGE_TYPE_SEND_REQ,
            PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND,
            PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);

    private static final String REMOTE_MMS_SELECTION_ALARM = String.format(
            Locale.US,
            "((%s IN (%d, %d, %d, %d, %d)) AND (%s IN (%d, %d, %d)))",
            Mms.MESSAGE_BOX,
            Mms.MESSAGE_BOX_INBOX,
            Mms.MESSAGE_BOX_OUTBOX,
            Mms.MESSAGE_BOX_SENT,
            Mms.MESSAGE_BOX_FAILED,
            MmsEx.MESSAGE_BOX_ALARM,
            Mms.MESSAGE_TYPE,
            PduHeaders.MESSAGE_TYPE_SEND_REQ,
            PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND,
            PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF);

    /**
     * Type selection for importing sms messages.
     *
     * @return The SQL selection for importing sms messages
     */
    public static String getSmsTypeSelectionSql() {
        if (!MmsConfig.osSupportDelayedSending()) {
            return REMOTE_SMS_SELECTION;
        } else {
            return REMOTE_SMS_SELECTION_ALARM;
        }
    }

    /**
     * Type selection for importing mms messages.
     *
     * @return The SQL selection for importing mms messages. This selects the message type,
     * not including the selection on timestamp.
     */
    public static String getMmsTypeSelectionSql() {
        if (!MmsConfig.osSupportDelayedSending()) {
            return REMOTE_MMS_SELECTION;
        } else {
            return REMOTE_MMS_SELECTION_ALARM;
        }
    }

    // SMIL spec: http://www.w3.org/TR/SMIL3

    private static final String sSmilImagePart =
            "<par dur=\"" + DEFAULT_DURATION + "ms\">" +
                "<img src=\"%s\" region=\"Image\" />" +
            "</par>";

    private static final String sSmilVideoPart =
            "<par dur=\"%2$dms\">" +
                "<video src=\"%1$s\" dur=\"%2$dms\" region=\"Image\" />" +
            "</par>";

    private static final String sSmilAudioPart =
            "<par dur=\"%2$dms\">" +
                    "<audio src=\"%1$s\" dur=\"%2$dms\" />" +
            "</par>";

    private static final String sSmilTextPart =
            "<par dur=\"" + DEFAULT_DURATION + "ms\">" +
                "<text src=\"%s\" region=\"Text\" />" +
            "</par>";

    private static final String sSmilPart =
            "<par dur=\"" + DEFAULT_DURATION + "ms\">" +
                "<ref src=\"%s\" />" +
            "</par>";

    private static final String sSmilTextOnly =
            "<smil>" +
                "<head>" +
                    "<layout>" +
                        "<root-layout/>" +
                        "<region id=\"Text\" top=\"0\" left=\"0\" "
                          + "height=\"100%%\" width=\"100%%\"/>" +
                    "</layout>" +
                "</head>" +
                "<body>" +
                       "%s" +  // constructed body goes here
                "</body>" +
            "</smil>";

    private static final String sSmilVisualAttachmentsOnly =
            "<smil>" +
                "<head>" +
                    "<layout>" +
                        "<root-layout/>" +
                        "<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" "
                          + "height=\"100%%\" width=\"100%%\"/>" +
                    "</layout>" +
                "</head>" +
                "<body>" +
                       "%s" +  // constructed body goes here
                "</body>" +
            "</smil>";

    private static final String sSmilVisualAttachmentsWithText =
            "<smil>" +
                "<head>" +
                    "<layout>" +
                        "<root-layout/>" +
                        "<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" "
                          + "height=\"80%%\" width=\"100%%\"/>" +
                        "<region id=\"Text\" top=\"80%%\" left=\"0\" height=\"20%%\" "
                          + "width=\"100%%\"/>" +
                    "</layout>" +
                "</head>" +
                "<body>" +
                       "%s" +  // constructed body goes here
                "</body>" +
            "</smil>";

    private static final String sSmilVisualAttachmentsWithTextTop =
            "<smil>" +
                "<head>" +
                    "<layout>" +
                        "<root-layout/>" +
                        "<region id=\"Text\"  top=\"0\" left=\"0\" height=\"20%%\" "
                          + "width=\"100%%\"/>" +
                        "<region id=\"Image\" fit=\"meet\"  top=\"20%%\" left=\"0\" "
                          + "height=\"80%%\" width=\"100%%\"/>" +
                    "</layout>" +
                "</head>" +
                "<body>" +
                       "%s" +  // constructed body goes here
                "</body>" +
            "</smil>";


    private static final String sSmilNonVisualAttachmentsOnly =
            "<smil>" +
                "<head>" +
                    "<layout>" +
                        "<root-layout/>" +
                    "</layout>" +
                "</head>" +
                "<body>" +
                       "%s" +  // constructed body goes here
                "</body>" +
            "</smil>";

    private static final String sSmilNonVisualAttachmentsWithText = sSmilTextOnly;

    public static final String MMS_DUMP_PREFIX = "mmsdump-";
    public static final String SMS_DUMP_PREFIX = "smsdump-";

    public static final int MIN_VIDEO_BYTES_PER_SECOND = 4 * 1024;
    public static final int MIN_IMAGE_BYTE_SIZE = 16 * 1024;
    public static final int MAX_VIDEO_ATTACHMENT_COUNT = 1;

    private static final String sSmilPartHeader = "<par dur=\"" + DEFAULT_DURATION + "ms\">";
    private static final String sSmilPartTail = "</par>";
    private static final String sSmilImage = "<img src=\"%s\" region=\"Image\" />";
    private static final String sSmilVideo = "<video src=\"%1$s\" dur=\"%2$dms\" region=\"Image\" />";
    private static final String sSmilAudio = "<audio src=\"%1$s\" dur=\"%2$dms\" />";
    private static final String sSmilText = "<text src=\"%s\" region=\"Text\" />";
    private static final String sSmil = "<ref src=\"%s\" />";

    private static int getPictureLength(final Context context, final MessagePartData messagePart,
            int widthLimit, int heightLimit, final int maxPartSize, final String contentType) {
        final Uri imageUri = messagePart.getContentUri();
        int width = messagePart.getWidth();
        int height = messagePart.getHeight();
       // BitmapFactory.Options options = new BitmapFactory.Options();
       // options.inJustDecodeBounds = true;
       // BitmapFactory.decodeFile(new File(imageUri.getPath()).getAbsolutePath(), options);
		//sprd 600980 start
//        final int width = options.outWidth;
//        final int height = options.outHeight;
       // final int width = options.outWidth == 0 ? messagePart.getWidth() : options.outWidth;
        //final int height = options.outHeight == 0 ? messagePart.getHeight() : options.outHeight;
		//sprd 600980 end
        //sprd 623241 start
        if(width==-1||height==-1){
            setImageWidthHeight(messagePart,imageUri);
            width = messagePart.getWidth();
            height = messagePart.getHeight();
        }
        //sprd 623241 end
        if ((height > width) != (heightLimit > widthLimit)) {
            final int temp = widthLimit;
            widthLimit = heightLimit;
            heightLimit = temp;
        }
        //add bug for 803124  start
        int imageSize = getDataLength(context, imageUri);
        if(imageSize <= maxPartSize && !MmsConfig.getCmccSdkEnabled()){
            Log.d("smil-s", "cmmc: already sized");
            return imageSize;
        }

        final int orientation = ImageUtils.getOrientation(context, imageUri);
        //add bug for 803124  end
        if (imageSize <= 0) {
            Log.e("smil-s", "getPictureLength: Can't get image", new Exception());
            return 0;
        }

        Log.d("smil-s", "getPictureLength: source size: " + imageSize + " width: " + width + " widthLimit: "
                + widthLimit + " height: " + height + " heightLimit: " + heightLimit + ", imageUri:" + imageUri);

        if((imageSize > ATTACHMENT_EXCEEDED_SIZE)||(contentType==ContentType.IMAGE_GIF))
            return imageSize;

        if (imageSize <= maxPartSize
                && width <= widthLimit
                && height <= heightLimit
                && (orientation == android.media.ExifInterface.ORIENTATION_UNDEFINED || orientation == android.media.ExifInterface.ORIENTATION_NORMAL)) {
            Log.d("smil-s", "getPictureLength: already sized");
            return imageSize;
        } else {
            final byte[] data = ImageResizer.getResizedImageData(width, height, orientation,
                    widthLimit, heightLimit, maxPartSize, imageUri, context, contentType);
            if (data == null) {
                final OutOfMemoryError e = new OutOfMemoryError();
                Log.e("smil-s", "getPictureLength: Can't resize image: not enough memory?", e);
                return imageSize;
            }
            Log.d("smil-s", "getPictureLength: resize = " + data.length);
            return data.length;
        }
    }

    public static long getAttachmentsLength(final Context context,
            final DraftMessageData draftMsgData) {
        final PduBody pb = new PduBody();

        long totalLength = 0;
        int countImage = 0;
        try{
            for (final MessagePartData part : draftMsgData.getAttachments()) {
                if (part.isAttachment()) {
                    final String contentType = part.getContentType();
                    if (ContentType.isImageType(contentType)) {
                        countImage++;
                    } else if (ContentType.isVCardType(contentType)) {
                        totalLength += getDataLength(context, part.getContentUri());
                    } else if(part.getContentUri() != null  && !part.getContentUri().equals("")){
                        totalLength += getMediaFileSize(part.getContentUri());
                    }
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        final long minSize = countImage * MIN_IMAGE_BYTE_SIZE;
        //should be the with getAttachmentsLength(), so just take the default value.
//      final int byteBudget = MmsConfig.get(subId).getMaxMessageSize() - totalLength
//              - MMS_MAX_SIZE_SLOP;
      final long byteBudget = CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE_DEFAULT
              - totalLength - MMS_MAX_SIZE_SLOP;
        final double budgetFactor = minSize > 0 ? Math.max(1.0, byteBudget / ((double) minSize))
                : 1;
        final int bytesPerImage = MmsConfig.getMaxMaxMessageSize();//(int) (budgetFactor * MIN_IMAGE_BYTE_SIZE);

        //draft message ignore which subid, so just take the default value.
//      final int widthLimit = MmsConfig.get(subId).getMaxImageWidth();
//      final int heightLimit = MmsConfig.get(subId).getMaxImageHeight();
        final int widthLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH_DEFAULT;
        final int heightLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT_DEFAULT;

        // Actually add the attachments, shrinking images appropriately.
        totalLength = 0;
        try{
            for (final MessagePartData part : draftMsgData.getAttachments()) {
                String contentType = part.getContentType();
                Log.d("smil-s", "MmsUtils.getAttachmentsLength: contentType="+contentType+".........begin");
                long attachmentSize = 0;
                if (ContentType.isImageType(contentType)) {
                    final boolean isGif = ImageUtils.isGif(contentType, part.getContentUri());
                    contentType = isGif ? ContentType.IMAGE_GIF : contentType;
                    attachmentSize = getPictureLength(context, part, widthLimit, heightLimit,
                            bytesPerImage, contentType);
                } else if (ContentType.isVideoType(contentType)) {
                    attachmentSize = getMediaFileSize(part.getContentUri());
                } else if (ContentType.isVCardType(contentType)) {
                    attachmentSize = getDataLength(context, part.getContentUri());
                } else if (ContentType.isAudioType(contentType)) {
                    attachmentSize =  getMediaFileSize(part.getContentUri());
                } else if (ContentType.isVcalendarType(contentType)) {
                    attachmentSize = getDataLength(context, part.getContentUri());
                } else if (ContentType.isTextType(contentType)) {
                    final String text = part.getText();
                    if (text != null) {
                        attachmentSize = text.getBytes().length;
                    }
                } else if(part.getContentUri() != null){
                    attachmentSize = getMediaFileSize(part.getContentUri());
                }
                part.setmAttachmetSize(attachmentSize);
                totalLength += attachmentSize;
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return totalLength;
    }

    public static long getAttachmentsLength(final Context context,
            final List<MessagePartData> draftMsgAttachements) {
        final PduBody pb = new PduBody();

        long totalLength = 0;
        int countImage = 0;
        for (final MessagePartData part : draftMsgAttachements) {
            if (part.isAttachment()) {
                final String contentType = part.getContentType();
                if (ContentType.isImageType(contentType)) {
                    countImage++;
                } else if (ContentType.isVCardType(contentType)) {
                    totalLength += getDataLength(context, part.getContentUri());
                } else if(part.getContentUri() != null  && !part.getContentUri().equals("")){
                    totalLength += getMediaFileSize(part.getContentUri());
                }
            }
        }
        final long minSize = countImage * MIN_IMAGE_BYTE_SIZE;
        //should be the with getAttachmentsLength(), so just take the default value.
//      final int byteBudget = MmsConfig.get(subId).getMaxMessageSize() - totalLength
//              - MMS_MAX_SIZE_SLOP;
      final long byteBudget = CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE_DEFAULT
              - totalLength - MMS_MAX_SIZE_SLOP;
        final double budgetFactor = minSize > 0 ? Math.max(1.0, byteBudget / ((double) minSize))
                : 1;
        final int bytesPerImage = MmsConfig.getMaxMaxMessageSize();//(int) (budgetFactor * MIN_IMAGE_BYTE_SIZE);

        //draft message ignore which subid, so just take the default value.
//      final int widthLimit = MmsConfig.get(subId).getMaxImageWidth();
//      final int heightLimit = MmsConfig.get(subId).getMaxImageHeight();
        final int widthLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH_DEFAULT;
        final int heightLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT_DEFAULT;

        // Actually add the attachments, shrinking images appropriately.
        totalLength = 0;

        for (final MessagePartData part : draftMsgAttachements) {
            String contentType = part.getContentType();
            Log.d("smil-s", "MmsUtils.getAttachmentsLength: contentType="+contentType+".........begin"+" part.getContentUri():"+part.getContentUri());
            Uri uri=part.getContentUri();
            String scheme;
            String authority;
            //add for bug 614704 --begin
            long attachmentSize = 0;
            long documentSize =0;
            if (ContentType.isImageType(contentType)) {
                final boolean isGif = ImageUtils.isGif(contentType, part.getContentUri());
                // add for bug 616653  start
                scheme=uri.getScheme();
                authority=uri.getAuthority();
                if (("content".equalsIgnoreCase(scheme))&&(("com.android.externalstorage.documents".equals(authority))||
                        ("com.android.providers.downloads.documents".equals(authority)))){
                    documentSize = getTotalLength(uri,context);
                    if(isGif){
                        attachmentSize = documentSize;
                    }else{
                        if(documentSize>ATTACHMENT_EXCEEDED_SIZE){
                            attachmentSize = documentSize;
                        }else{
                            attachmentSize = isGif ? getMediaFileSize(part.getContentUri()) : getPictureLength(context, part, widthLimit, heightLimit,
                                    bytesPerImage, contentType);
                        }

                    }
                }else{
                    attachmentSize = isGif ? getMediaFileSize(part.getContentUri()) : getPictureLength(context, part, widthLimit, heightLimit,
                            bytesPerImage, contentType);
                }
                // add for bug 616653  end
            } else if (ContentType.isVideoType(contentType)) {
                // add for bug 620391  start
                scheme=uri.getScheme();
                authority=uri.getAuthority();
                if (("content".equalsIgnoreCase(scheme))&&("com.android.externalstorage.documents".equals(authority))){
                    attachmentSize =getTotalLength(uri,context);
                }else{
                    attachmentSize = getMediaFileSize(part.getContentUri());
                }
                // add for bug 620391  end
            } else if (ContentType.isVCardType(contentType)) {
                //totalLength += getDataLength(context, part.getContentUri());
                attachmentSize = getDataLength(context, part.getContentUri());
            } else if (ContentType.isAudioType(contentType)) {
                // add for bug 620391  start
                scheme=uri.getScheme();
                authority=uri.getAuthority();
                if (("content".equalsIgnoreCase(scheme))&&("com.android.externalstorage.documents".equals(authority))){
                    attachmentSize =getTotalLength(uri,context);
                }else{
                    attachmentSize =  getMediaFileSize(part.getContentUri());
                }
                // add for bug 620391  end
            } else if (ContentType.isVcalendarType(contentType)) {
                attachmentSize = getDataLength(context, part.getContentUri());
            } else if (ContentType.isTextType(contentType)) {
                final String text = part.getText();
                if (text != null) {
                    attachmentSize = text.getBytes().length;
                }
            } else if(part.getContentUri() != null){
                attachmentSize =  getMediaFileSize(part.getContentUri());
            }
            Log.d("DraftMessageData","getAttachmentsLength-attachmentSize="+attachmentSize);
            part.setmAttachmetSize(attachmentSize);
            totalLength += attachmentSize;
            //add for bug 614704 --end
        }
        return totalLength;
    }

    /**add for Bug 558980 begin*/
    public static int getPartsLength(final Context context,
                                     final ConversationMessageData data) {
        final PduBody pb = new PduBody();
        int index = 0;
        int totalLength = 0;
        int countImage = 0;
        for (final MessagePartData part : data.getParts()) {
            if (part.isAttachment()) {
                final String contentType = part.getContentType();
                if (ContentType.isImageType(contentType)) {
                    countImage++;
                } else if (ContentType.isVCardType(contentType)) {
                    totalLength += getDataLength(context, part.getContentUri());
                } else if(part.getContentUri() != null  && !part.getContentUri().equals("")){
                    totalLength += getMediaFileSize(part.getContentUri());
                }
            }
        }
        final long minSize = countImage * MIN_IMAGE_BYTE_SIZE;
        //should be the with getAttachmentsLength(), so just take the default value.
//      final int byteBudget = MmsConfig.get(subId).getMaxMessageSize() - totalLength
//              - MMS_MAX_SIZE_SLOP;
        final int byteBudget = CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE_DEFAULT
                - totalLength - MMS_MAX_SIZE_SLOP;
        final double budgetFactor = minSize > 0 ? Math.max(1.0, byteBudget / ((double) minSize))
                : 1;
        final int bytesPerImage = MmsConfig.getMaxMaxMessageSize();//(int) (budgetFactor * MIN_IMAGE_BYTE_SIZE);

        //draft message ignore which subid, so just take the default value.
//      final int widthLimit = MmsConfig.get(subId).getMaxImageWidth();
//      final int heightLimit = MmsConfig.get(subId).getMaxImageHeight();
        final int widthLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH_DEFAULT;
        final int heightLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT_DEFAULT;

        // Actually add the attachments, shrinking images appropriately.
        totalLength = 0;
        boolean hasText = false;
        for (final MessagePartData part : data.getParts()) {
            String srcName;
            if (part.isAttachment()) {
                String contentType = part.getContentType();
                if (ContentType.isImageType(contentType)) {
                    // There's a good chance that if we selected the image from our media picker the
                    // content type is image/*. Fix the content type here for gifs so that we only
                    // need to open the input stream once. All other gif vs static image checks will
                    // only have to do a string comparison which is much cheaper.
                    final boolean isGif = ImageUtils.isGif(contentType, part.getContentUri());
                    contentType = isGif ? ContentType.IMAGE_GIF : contentType;
                    srcName = String.format(isGif ? "image%06d.gif" : "image%06d.jpg", index);
                    totalLength += isGif ? getDataLength(context, part.getContentUri()):addPicturePart(context, pb, index, part,
                            widthLimit, heightLimit, bytesPerImage, srcName, contentType);
                } else if (ContentType.isVideoType(contentType)) {
                    srcName = String.format("video%06d.mp4", index);
                    final int length = addVideoPart(context, pb, part, srcName);
                    totalLength += length;
                } else if (ContentType.isVCardType(contentType)) {
                    srcName = String.format("contact%06d.vcf", index);
                    totalLength += addVCardPart(context, pb, part, srcName);
                } else if (ContentType.isAudioType(contentType)) {
                    srcName = String.format("recording%06d.amr", index);
                    totalLength += addOtherPart(context, pb, part, srcName);
                    final int duration = getMediaDurationMs(context, part, -1);
                    Assert.isTrue(duration != -1);
                } else if (ContentType.isVcalendarType(contentType)) {
                    srcName = String.format("calendar%06d.vcs", index);
                    totalLength += addVCalendarPart(context, pb, part, srcName);
                } else {
                    srcName = String.format("other%06d.dat", index);
                    totalLength += addOtherPart(context, pb, part, srcName);
                }
                index++;
            }
            if (!TextUtils.isEmpty(part.getText())) {
                hasText = true;
            }
        }

        if (hasText) {
            final String srcName = String.format("text.%06d.txt", index);
            final String text = getMessageText(data.getParts());
            totalLength += addTextPart(context, pb, text, srcName);
        }
        return totalLength;
    }
    /**add for Bug 558980 end*/

    /**add for Bug 558980 begin*/
    private final static String getMessageText(List<MessagePartData> parts) {
        final String separator = System.getProperty("line.separator");
        final StringBuilder text = new StringBuilder();
        for (final MessagePartData part : parts) {
            if (!part.isAttachment() && !TextUtils.isEmpty(part.getText()) && ContentType.isTextType(part.getContentType())) {
                if (text.length() > 0) {
                    text.append(separator);
                }
                text.append(part.getText());
            }
        }
        return text.toString();
    }
    /**add for Bug 558980 end*/

    public static MmsInfo makePduBodyEx(final Context context, final MessageData message,
            final int subId, final String smil) {
        final PduBody pb = new PduBody();

        // Compute data size requirements for this message: count up images and
        // total size of
        // non-image attachments.
        int totalLength = 0;
        int countImage = 0;
        for (final MessagePartData part : message.getParts()) {
            if (part.isAttachment()) {
                final String contentType = part.getContentType();
                if (ContentType.isImageType(contentType)) {
                    countImage++;
                } else if (ContentType.isVCardType(contentType)) {
                    totalLength += getDataLength(context, part.getContentUri());
                } else {
                    totalLength += getMediaFileSize(part.getContentUri());
                }
            }
        }
        final long minSize = countImage * MIN_IMAGE_BYTE_SIZE;
        //should be the with getAttachmentsLength(), so just take the default value.
//        final int byteBudget = MmsConfig.get(subId).getMaxMessageSize() - totalLength
//                - MMS_MAX_SIZE_SLOP;
        final int byteBudget = CarrierConfigValuesLoader.CONFIG_MAX_MESSAGE_SIZE_DEFAULT
                - totalLength - MMS_MAX_SIZE_SLOP;
        final double budgetFactor = minSize > 0 ? Math.max(1.0, byteBudget / ((double) minSize))
                : 1;
        final int bytesPerImage = MmsConfig.getMaxMaxMessageSize();//(int) (budgetFactor * MIN_IMAGE_BYTE_SIZE);
//        final int widthLimit = MmsConfig.get(subId).getMaxImageWidth();
//        final int heightLimit = MmsConfig.get(subId).getMaxImageHeight();
        //should be the with getAttachmentsLength(), so just take the default value.
        final int widthLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_WIDTH_DEFAULT;
        final int heightLimit = CarrierConfigValuesLoader.CONFIG_MAX_IMAGE_HEIGHT_DEFAULT;

        // Actually add the attachments, shrinking images appropriately.
        int index = 0;
        totalLength = 0;

        for (final MessagePartData part : message.getParts()) {
            String srcName;
            String contentType = part.getContentType();
            Log.d("smil-s", "MmsUtils.makePduBodyEx: contentType="+contentType+"==========begin");
            if (ContentType.isImageType(contentType)) {
                final boolean isGif = ImageUtils.isGif(contentType, part.getContentUri());
                contentType = isGif ? ContentType.IMAGE_GIF : contentType;
                srcName = String.format(isGif ? "image%06d.gif" : "image%06d.jpg", index);
                totalLength += addPicturePart(context, pb, index, part, widthLimit, heightLimit,
                        bytesPerImage, srcName, contentType);
                Log.d("smil-s", "MmsUtils.makePduBodyEx: srcName="+srcName);
            } else if (ContentType.isVideoType(contentType)) {
                srcName = String.format("video%06d.mp4", index);
                final int length = addVideoPart(context, pb, part, srcName);
                totalLength += length;
                Log.d("smil-s", "MmsUtils.makePduBodyEx: srcName="+srcName);
            } else if (ContentType.isVCardType(contentType)) {
                srcName = String.format("contact%06d.vcf", index);
                totalLength += addVCardPart(context, pb, part, srcName);
                Log.d("smil-s", "MmsUtils.makePduBodyEx: srcName="+srcName);
            } else if (ContentType.isAudioType(contentType)) {
                srcName = String.format("recording%06d.amr", index);
                totalLength += addOtherPart(context, pb, part, srcName);
                final int duration = getMediaDurationMs(context, part, -1);
                Assert.isTrue(duration != -1);
                Log.d("smil-s", "MmsUtils.makePduBodyEx: srcName="+srcName);
            } else if (ContentType.isVcalendarType(contentType)) {
                srcName = String.format("calendar%06d.vcs", index);
                totalLength += addVCalendarPart(context, pb, part, srcName);
                Log.d("smil-s", "MmsUtils.makePduBodyEx: srcName="+srcName);
            } else if (ContentType.isTextType(contentType)) {
                srcName = String.format("text.%06d.txt", index);
                final String text = part.getText();
                totalLength += addTextPart(context, pb, text, srcName);
                Log.d("smil-s", "MmsUtils.makePduBodyEx: srcName="+srcName);
            } else {
                srcName = String.format("other%06d.dat", index);
                totalLength += addOtherPart(context, pb, part, srcName);
                Log.d("smil-s", "MmsUtils.makePduBodyEx: srcName="+srcName);
            }
            index++;
        }

        addSmilPartEx(pb, smil);

        final MmsInfo mmsInfo = new MmsInfo();
        mmsInfo.mPduBody = pb;
        mmsInfo.mMessageSize = totalLength;

        return mmsInfo;
    }

    private static void addSmilPartEx(final PduBody pb, String smil) {
        final PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        smilPart.setData(smil.getBytes());
        pb.addPart(0, smilPart);
    }

    public static MmsInfo makePduBody(final Context context, final MessageData message,
            final int subId) {
        final PduBody pb = new PduBody();
        boolean textTop = false;
        // Compute data size requirements for this message: count up images and total size of
        // non-image attachments.
        int totalLength = 0;
        int countImage = 0;
        for (final MessagePartData part : message.getParts()) {
            if (part.isAttachment()) {
                final String contentType = part.getContentType();
                if (ContentType.isImageType(contentType)) {
                    countImage++;
                } else if (ContentType.isVCardType(contentType)) {
                    totalLength += getDataLength(context, part.getContentUri());
                } else {
                    totalLength += getMediaFileSize(part.getContentUri());
                }
            }
        }
        final long minSize = countImage * MIN_IMAGE_BYTE_SIZE;
        final int byteBudget = MmsConfig.get(subId).getMaxMessageSize() - totalLength
                - MMS_MAX_SIZE_SLOP;
        final double budgetFactor =
                minSize > 0 ? Math.max(1.0, byteBudget / ((double) minSize)) : 1;
        final int bytesPerImage = MmsConfig.getMaxMaxMessageSize();//(int) (budgetFactor * MIN_IMAGE_BYTE_SIZE);
        final int widthLimit = MmsConfig.get(subId).getMaxImageWidth();
        final int heightLimit = MmsConfig.get(subId).getMaxImageHeight();

        // Actually add the attachments, shrinking images appropriately.
        int index = 0;
        totalLength = 0;
        boolean hasVisualAttachment = false;
        boolean hasNonVisualAttachment = false;
        boolean hasText = false;
        final StringBuilder smilBody = new StringBuilder();
        for (final MessagePartData part : message.getParts()) {
            String srcName;
            if (part.isAttachment()) {
                String contentType = part.getContentType();
                if (ContentType.isImageType(contentType)) {
                    // There's a good chance that if we selected the image from our media picker the
                    // content type is image/*. Fix the content type here for gifs so that we only
                    // need to open the input stream once. All other gif vs static image checks will
                    // only have to do a string comparison which is much cheaper.
                    final boolean isGif = ImageUtils.isGif(contentType, part.getContentUri());
                    contentType = isGif ? ContentType.IMAGE_GIF : contentType;
                    srcName = String.format(isGif ? "image%06d.gif" : "image%06d.jpg", index);
                    smilBody.append(String.format(sSmilImagePart, srcName));
                    totalLength += addPicturePart(context, pb, index, part,
                            widthLimit, heightLimit, bytesPerImage, srcName, contentType);
                    hasVisualAttachment = true;
                    if (hasText){
                         textTop = true;
                    }
                } else if (ContentType.isVideoType(contentType)) {
                    srcName = String.format("video%06d.mp4", index);
                    final int length = addVideoPart(context, pb, part, srcName);
                    totalLength += length;
                    smilBody.append(String.format(sSmilVideoPart, srcName,
                            getMediaDurationMs(context, part, DEFAULT_DURATION)));
                    hasVisualAttachment = true;
                    if (hasText){
                         textTop = true;
                    }
                } else if (ContentType.isVCardType(contentType)) {
                    srcName = String.format("contact%06d.vcf", index);
                    totalLength += addVCardPart(context, pb, part, srcName);
                    smilBody.append(String.format(sSmilPart, srcName));
                    hasNonVisualAttachment = true;
                } else if (ContentType.isAudioType(contentType)) {
                    srcName = String.format("recording%06d.amr", index);
                    totalLength += addOtherPart(context, pb, part, srcName);
                    final int duration = getMediaDurationMs(context, part, -1);
                    Assert.isTrue(duration != -1);
                    smilBody.append(String.format(sSmilAudioPart, srcName, duration));
                    hasNonVisualAttachment = true;
                } else if (ContentType.isVcalendarType(contentType)) {
                    srcName = String.format("calendar%06d.vcs", index);
                    totalLength += addVCalendarPart(context, pb, part, srcName);
                    smilBody.append(String.format(sSmilPart, srcName));
                    hasNonVisualAttachment = true;
                } else {
                    srcName = String.format("other%06d.dat", index);
                    totalLength += addOtherPart(context, pb, part, srcName);
                    smilBody.append(String.format(sSmilPart, srcName));
                }
                index++;
            }
            if (!TextUtils.isEmpty(part.getText())) {
                hasText = true;
            }
        }

        if (hasText) {
            final String srcName = String.format("text.%06d.txt", index);
            final String text = message.getMessageText();
            totalLength += addTextPart(context, pb, text, srcName);

            // Append appropriate SMIL to the body.
            smilBody.append(String.format(sSmilTextPart, srcName));
        }

        final String smilTemplate = getSmilTemplate(hasVisualAttachment,
                hasNonVisualAttachment, hasText, textTop);
        addSmilPart(pb, smilTemplate, smilBody.toString());

        final MmsInfo mmsInfo = new MmsInfo();
        mmsInfo.mPduBody = pb;
        mmsInfo.mMessageSize = totalLength;

        return mmsInfo;
    }

    private static int getMediaDurationMs(final Context context, final MessagePartData part,
            final int defaultDurationMs) {
        Assert.notNull(context);
        Assert.notNull(part);
        Assert.isTrue(ContentType.isAudioType(part.getContentType()) ||
                ContentType.isVideoType(part.getContentType()));

        final MediaMetadataRetrieverWrapper retriever = new MediaMetadataRetrieverWrapper();
        try {
            retriever.setDataSource(part.getContentUri());
            return retriever.extractInteger(
                    MediaMetadataRetriever.METADATA_KEY_DURATION, defaultDurationMs);
        } catch (final IOException e) {
            LogUtil.i(LogUtil.BUGLE_TAG, "Error extracting duration from " + part.getContentUri(), e);
            return defaultDurationMs;
        } finally {
            retriever.release();
        }
    }

    private static void setPartContentLocationAndId(final PduPart part, final String srcName) {
        // Set Content-Location.
        part.setContentLocation(srcName.getBytes());

        // Set Content-Id.
        final int index = srcName.lastIndexOf(".");
        final String contentId = (index == -1) ? srcName : srcName.substring(0, index);
        part.setContentId(contentId.getBytes());
    }

    private static int addTextPart(final Context context, final PduBody pb,
            final String text, final String srcName) {
        final PduPart part = new PduPart();

        // Set Charset if it's a text media.
        part.setCharset(CharacterSets.UTF_8);

        // Set Content-Type.
        part.setContentType(ContentType.TEXT_PLAIN.getBytes());

        // Set Content-Location.
        setPartContentLocationAndId(part, srcName);

        part.setData(text.getBytes());

        pb.addPart(part);

        return part.getData().length;
    }

    private static int addPicturePart(final Context context, final PduBody pb, final int index,
            final MessagePartData messagePart, int widthLimit, int heightLimit,
            final int maxPartSize, final String srcName, final String contentType) {
        final Uri imageUri = messagePart.getContentUri();
        final int width = messagePart.getWidth();
        final int height = messagePart.getHeight();

        // Swap the width and height limits to match the orientation of the image so we scale the
        // picture as little as possible.
        if ((height > width) != (heightLimit > widthLimit)) {
            final int temp = widthLimit;
            widthLimit = heightLimit;
            heightLimit = temp;
        }

        final int orientation = ImageUtils.getOrientation(context, imageUri);
        int imageSize = (int)getMediaFileSize(imageUri); //getDataLength(context, imageUri);  //Bug 999715
        if (imageSize <= 0) {
            LogUtil.e(TAG, "Can't get image", new Exception());
            return 0;
        }

        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "addPicturePart size: " + imageSize + " width: "
                    + width + " widthLimit: " + widthLimit
                    + " height: " + height
                    + " heightLimit: " + heightLimit);
        }

        PduPart part;
        // Check if we're already within the limits - in which case we don't need to resize.
        // The size can be zero here, even when the media has content. See the comment in
        // MediaModel.initMediaSize. Sometimes it'll compute zero and it's costly to read the
        // whole stream to compute the size. When we call getResizedImageAsPart(), we'll correctly
        // set the size.
        Log.d("smil_show_cp", "imageSize--->"+imageSize+"==maxPartSize() ---> "+maxPartSize);
        Log.d("smil_show_cp", "messagePart.getWidth() ---> "+width+"---widthLimit---->"+widthLimit);
        Log.d("smil_show_cp", "messagePart.getHeight() ---> "+height+"---widthLimit---->"+heightLimit);
        Log.d("smil_show_cp", "orientation ---> "+orientation+"---android.media.ExifInterface.ORIENTATION_UNDEFINED---->"+android.media.ExifInterface.ORIENTATION_UNDEFINED);
        Log.d("smil_show_cp", "orientation ---> "+orientation+"---android.media.ExifInterface.ORIENTATION_UNDEFINED---->"+android.media.ExifInterface.ORIENTATION_NORMAL);
        //add bug for 803048  start
        if (imageSize <= maxPartSize &&
                ((width <= widthLimit &&
                height <= heightLimit &&
                (orientation == android.media.ExifInterface.ORIENTATION_UNDEFINED ||
                orientation == android.media.ExifInterface.ORIENTATION_NORMAL))||
                !MmsConfig.getCmccSdkEnabled())) {
            //add bug for 803048  end
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "addPicturePart - already sized");
            }
            Log.d("smil_show_cp", "-----part.setDataUri(imageUri)---> ");
            part = new PduPart();
            part.setDataUri(imageUri);
            part.setContentType(contentType.getBytes());
        } else {
            part = getResizedImageAsPart(widthLimit, heightLimit, maxPartSize,
                    width, height, orientation, imageUri, context, contentType);
            if (part == null) {
//                final OutOfMemoryError e = new OutOfMemoryError();
//                LogUtil.e(TAG, "Can't resize image: not enough memory?", e);
//                throw e;
                part = new PduPart();
                part.setDataUri(imageUri);
                part.setContentType(contentType.getBytes());
            }else{
                part.setDataUri(imageUri);
                part.setContentType(contentType.getBytes());
            }
            if (part != null && part.getData() != null) {
                imageSize = part.getData().length;
            }
            Log.d("smil_show_cp", "-----not part.setDataUri(imageUri)---> ");
            //add for bug 628890 --begin
            //imageSize = part.getData().length;
            //add for bug 628890 --end
        }

        setPartContentLocationAndId(part, srcName);

        //modify for bug 603769 start
        try {
            pb.addPart(index, part);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "the attachment has unormal file where the size is 0");
        }
        //modify for bug 603769 end

        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "addPicturePart size: " + imageSize);
        }

        return imageSize;
    }

    private static void addPartForUri(final Context context, final PduBody pb,
            String srcName, final Uri uri, final String contentType) {
        final PduPart part = new PduPart();
        part.setDataUri(uri);
        part.setContentType(contentType.getBytes());

       if (ContentType.isDrmType(contentType)) {
           try {
               String filePath = MessagingDrmSession.get().getPath(uri);
               Log.d("MmsUtils", "filePath is " + filePath);
               if (filePath != null) {
                   int dotIndex = filePath.lastIndexOf("/");
                   if (dotIndex > 0) {
                       String drmFilename = filePath.substring(dotIndex+1, filePath.length());
                       part.setName(drmFilename.getBytes());
                       part.setFilename(drmFilename.getBytes());
                       srcName = drmFilename;
                       String drmOrigType = MessagingDrmSession.get().getDrmOrigMimeType(filePath, contentType);
                       if (drmOrigType!=null){
                           if (drmOrigType.startsWith("image/")){
                               part.setContentType("image/dcf".getBytes());
                           }else if (drmOrigType.startsWith("audio/")){
                               part.setContentType("audio/dcf".getBytes());
                           }else if (drmOrigType.startsWith("video/")){
                               part.setContentType("video/dcf".getBytes());
                           }
                       }
                       //part.setContentType("application/oct-stream".getBytes());
                       Log.d("MmsUtils", "drmFilename is " + drmFilename+" drmOrigType "+drmOrigType+" contenttype is "+new String(part.getContentType()));
                   }
               }
           } catch (Exception ex) {
           }
       }
        setPartContentLocationAndId(part, srcName);
        pb.addPart(part);
    }

    private static int addVCardPart(final Context context, final PduBody pb,
            final MessagePartData messagePart, final String srcName) {
        final Uri vcardUri = messagePart.getContentUri();
        final String contentType = messagePart.getContentType();
        final int vcardSize = getDataLength(context, vcardUri);
        if (vcardSize <= 0) {
            LogUtil.e(TAG, "Can't get vcard", new Exception());
            return 0;
        }

        addPartForUri(context, pb, srcName, vcardUri, contentType);

        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "addVCardPart size: " + vcardSize);
        }

        return vcardSize;
    }
    private static int addVCalendarPart(final Context context, final PduBody pb,
            final MessagePartData messagePart, final String srcName) {
        final Uri vCalendarUri = messagePart.getContentUri();
        final String contentType = messagePart.getContentType();
        final int vcalendarSize = getDataLength(context, vCalendarUri);
        if (vcalendarSize <= 0) {
            LogUtil.e(TAG, "Can't get vcalendar", new Exception());
            return 0;
        }
        addPartForUri(context, pb, srcName, vCalendarUri, contentType);
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "addVCalendarPart size: " + vcalendarSize);
        }
        return vcalendarSize;
    }

    /**
     * Add video part recompressing video if necessary.  If recompression fails, part is not
     * added.
     */
    private static int addVideoPart(final Context context, final PduBody pb,
            final MessagePartData messagePart, final String srcName) {
        final Uri attachmentUri = messagePart.getContentUri();
        String contentType = messagePart.getContentType();

        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "addPart attachmentUrl: " + attachmentUri.toString());
        }

        if (TextUtils.isEmpty(contentType)) {
            contentType = ContentType.VIDEO_3G2;
        }

        addPartForUri(context, pb, srcName, attachmentUri, contentType);
        return (int) getMediaFileSize(attachmentUri);
    }

    private static int addOtherPart(final Context context, final PduBody pb,
            final MessagePartData messagePart, final String srcName) {
        final Uri attachmentUri = messagePart.getContentUri();
        //modify for bug 533847 begin
        String contentType = messagePart.getContentType();
        if (contentType == null || contentType.isEmpty()){//Modify for Bug:536649
            contentType = ContentType.APP_OCT;
        }
        //modify for bug 533847 end
        if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
            LogUtil.v(TAG, "addPart attachmentUrl: " + attachmentUri.toString());
        }

        final int dataSize = (int) getMediaFileSize(attachmentUri);

        addPartForUri(context, pb, srcName, attachmentUri, contentType);

        return dataSize;
    }

    private static void addSmilPart(final PduBody pb, final String smilTemplate,
            final String smilBody) {
        final PduPart smilPart = new PduPart();
        smilPart.setContentId("smil".getBytes());
        smilPart.setContentLocation("smil.xml".getBytes());
        smilPart.setContentType(ContentType.APP_SMIL.getBytes());
        final String smil = String.format(smilTemplate, smilBody);
        smilPart.setData(smil.getBytes());
        pb.addPart(0, smilPart);
    }

    private static String getSmilTemplate(final boolean hasVisualAttachments,
            final boolean hasNonVisualAttachments, final boolean hasText, final boolean textTop) {
        if (hasVisualAttachments) {
            if (textTop){
                return hasText ? sSmilVisualAttachmentsWithTextTop : sSmilVisualAttachmentsOnly;
            }else{
                return hasText ? sSmilVisualAttachmentsWithText : sSmilVisualAttachmentsOnly;
            }
        }
        if (hasNonVisualAttachments) {
            return hasText ? sSmilNonVisualAttachmentsWithText : sSmilNonVisualAttachmentsOnly;
        }
        return sSmilTextOnly;
    }

    private static int getDataLength(final Context context, final Uri uri) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            try {
                return is == null ? 0 : is.available();
            } catch (final IOException e) {
                LogUtil.e(TAG, "getDataLength couldn't stream: " + uri, e);
            }
        } catch (final FileNotFoundException e) {
            LogUtil.e(TAG, "getDataLength couldn't open: " + uri, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (final IOException e) {
                    LogUtil.e(TAG, "getDataLength couldn't close: " + uri, e);
                }
            }
        }
        return 0;
    }

    /**
     * Returns {@code true} if group mms is turned on,
     * {@code false} otherwise.
     *
     * For the group mms feature to be enabled, the following must be true:
     *  1. the feature is enabled in mms_config.xml (currently on by default)
     *  2. the feature is enabled in the SMS settings page
     *
     * @return true if group mms is supported
     */
    public static boolean groupMmsEnabled(final int subId) {
        final Context context = Factory.get().getApplicationContext();
        final Resources resources = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String groupMmsKey = resources.getString(R.string.group_mms_pref_key);
        final boolean groupMmsEnabledDefault = resources.getBoolean(R.bool.group_mms_pref_default);
        final boolean groupMmsPrefOn = prefs.getBoolean(groupMmsKey, groupMmsEnabledDefault);
        return MmsConfig.get(subId).getGroupMmsEnabled() && groupMmsPrefOn;
    }

    /**
     * Get a version of this image resized to fit the given dimension and byte-size limits. Note
     * that the content type of the resulting PduPart may not be the same as the content type of
     * this UriImage; always call {@link PduPart#getContentType()} to get the new content type.
     *
     * @param widthLimit The width limit, in pixels
     * @param heightLimit The height limit, in pixels
     * @param byteLimit The binary size limit, in bytes
     * @param width The image width, in pixels
     * @param height The image height, in pixels
     * @param orientation Orientation constant from ExifInterface for rotating or flipping the
     *                    image
     * @param imageUri Uri to the image data
     * @param context Needed to open the image
     * @return A new PduPart containing the resized image data
     */
    private static PduPart getResizedImageAsPart(final int widthLimit,
            final int heightLimit, final int byteLimit, final int width, final int height,
            final int orientation, final Uri imageUri, final Context context, final String contentType) {
        final PduPart part = new PduPart();

        final byte[] data = ImageResizer.getResizedImageData(width, height, orientation,
                widthLimit, heightLimit, byteLimit, imageUri, context, contentType);
        if (data == null) {
            if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                LogUtil.v(TAG, "Resize image failed.");
            }
            return null;
        }

        part.setData(data);
        // Any static images will be compressed into a jpeg
        final String contentTypeOfResizedImage = ImageUtils.isGif(contentType, imageUri)
                ? ContentType.IMAGE_GIF : ContentType.IMAGE_JPEG;
        part.setContentType(contentTypeOfResizedImage.getBytes());

        return part;
    }

    /**
     * Get media file size
     */
    public static long getMediaFileSize(final Uri uri) {
        final Context context = Factory.get().getApplicationContext();
     // Spread: fixed for bug :521561 start
        if (ActivityManager.isUserAMonkey()) {
            // Spread: fixed for bug :518331 start
            if (!OsUtil.hasStoragePermission()) {
                LogUtil.e(
                        TAG,
                        "MmsUtils.getMediaFileSize: cound not find media file: beacause have not has the pemission ");
                return 0L;
            }
            // Spread: fixed for bug :518331 end
        }
     // Spread: fixed for bug :521561 end
        AssetFileDescriptor fd = null;
        try {
            fd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            if (fd != null) {
                return fd.getParcelFileDescriptor().getStatSize();
            }
        } catch (final FileNotFoundException e) {
            LogUtil.e(TAG, "MmsUtils.getMediaFileSize: cound not find media file: " + e, e);
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (final IOException e) {
                    LogUtil.e(TAG, "MmsUtils.getMediaFileSize: failed to close " + e, e);
                }
            }
        }
        return 0L;
    }

    // Code for extracting the actual phone numbers for the participants in a conversation,
    // given a thread id.

    private static final Uri ALL_THREADS_URI =
            Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();

    private static final String[] RECIPIENTS_PROJECTION = {
        Threads._ID,
        Threads.RECIPIENT_IDS
    };

    private static final int RECIPIENT_IDS  = 1;

    public static List<String> getRecipientsByThread(final long threadId) {
        final String spaceSepIds = getRawRecipientIdsForThread(threadId);
        if (!TextUtils.isEmpty(spaceSepIds)) {
            final Context context = Factory.get().getApplicationContext();
            return getAddresses(context, spaceSepIds);
        }
        return null;
    }

    // NOTE: There are phones on which you can't get the recipients from the thread id for SMS
    // until you have a message in the conversation!
    public static String getRawRecipientIdsForThread(final long threadId) {
        if (threadId <= 0) {
            return null;
        }
        final Context context = Factory.get().getApplicationContext();
        final ContentResolver cr = context.getContentResolver();
        final Cursor thread = cr.query(
                ALL_THREADS_URI,
                RECIPIENTS_PROJECTION, "_id=?", new String[] { String.valueOf(threadId) }, null);
        if (thread != null) {
            try {
                if (thread.moveToFirst()) {
                    // recipientIds will be a space-separated list of ids into the
                    // canonical addresses table.
                    return thread.getString(RECIPIENT_IDS);
                }
            } finally {
                thread.close();
            }
        }
        return null;
    }

    private static final Uri SINGLE_CANONICAL_ADDRESS_URI =
            Uri.parse("content://mms-sms/canonical-address");

    private static List<String> getAddresses(final Context context, final String spaceSepIds) {
        final List<String> numbers = new ArrayList<String>();
        final String[] ids = spaceSepIds.split(" ");
        for (final String id : ids) {
            long longId;

            try {
                longId = Long.parseLong(id);
                if (longId < 0) {
                    LogUtil.e(TAG, "MmsUtils.getAddresses: invalid id " + longId);
                    continue;
                }
            } catch (final NumberFormatException ex) {
                LogUtil.e(TAG, "MmsUtils.getAddresses: invalid id. " + ex, ex);
                // skip this id
                continue;
            }

            // TODO: build a single query where we get all the addresses at once.
            Cursor c = null;
            try {
                c = context.getContentResolver().query(
                        ContentUris.withAppendedId(SINGLE_CANONICAL_ADDRESS_URI, longId),
                        null, null, null, null);
            } catch (final Exception e) {
                LogUtil.e(TAG, "MmsUtils.getAddresses: query failed for id " + longId, e);
            }
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        final String number = c.getString(0);
                        if (!TextUtils.isEmpty(number)) {
                            numbers.add(number);
                        } else {
                            LogUtil.w(TAG, "Canonical MMS/SMS address is empty for id: " + longId);
                        }
                    }
                } finally {
                    c.close();
                }
            }
        }
        if (numbers.isEmpty()) {
            LogUtil.w(TAG, "No MMS addresses found from ids string [" + spaceSepIds + "]");
        }
        return numbers;
    }

    // Get telephony SMS thread ID
    public static long getOrCreateSmsThreadId(final Context context, final String dest) {
        // use destinations to determine threadId
        final Set<String> recipients = new HashSet<String>();
        recipients.add(dest);
        try {
            return MmsSmsUtils.Threads.getOrCreateThreadId(context, recipients);
        } catch (final IllegalArgumentException e) {
            LogUtil.e(TAG, "MmsUtils: getting thread id failed: " + e);
            return -1;
        }
    }

    // Get telephony SMS thread ID
    public static long getOrCreateThreadId(final Context context, final List<String> dests) {
        if (dests == null || dests.size() == 0) {
            return -1;
        }
        // use destinations to determine threadId
        final Set<String> recipients = new HashSet<String>(dests);
        try {
            return MmsSmsUtils.Threads.getOrCreateThreadId(context, recipients);
        } catch (final IllegalArgumentException e) {
            LogUtil.e(TAG, "MmsUtils: getting thread id failed: " + e);
            return -1;
        }
    }

    /**
     * Add an SMS to the given URI with thread_id specified.
     *
     * @param resolver the content resolver to use
     * @param uri the URI to add the message to
     * @param subId subId for the receiving sim
     * @param address the address of the sender
     * @param body the body of the message
     * @param subject the psuedo-subject of the message
     * @param date the timestamp for the message
     * @param read true if the message has been read, false if not
     * @param threadId the thread_id of the message
     * @return the URI for the new message
     */
    private static Uri addMessageToUri(final ContentResolver resolver,
            final Uri uri, final int subId, final String address, final String body,
            final String subject, final Long date, final boolean read, final boolean seen,
            final int status, final int type, final long threadId,final long alarmtime) {
        final ContentValues values = new ContentValues(8);

        values.put(Telephony.Sms.ADDRESS, address);
        if (date != null) {
            values.put(Telephony.Sms.DATE, date);
        }
        values.put(Telephony.Sms.READ, read ? 1 : 0);
        values.put(Telephony.Sms.SEEN, seen ? 1 : 0);
        values.put(Telephony.Sms.SUBJECT, subject);
        values.put(Telephony.Sms.BODY, body);
        if (MmsConfig.osSupportDelayedSending()) {
            values.put(TelephonyEx.SmsEx.ALARM, alarmtime);
        }
        if (OsUtil.isAtLeastL_MR1()) {
            values.put(Telephony.Sms.SUBSCRIPTION_ID, subId);
        }
        if (status != Telephony.Sms.STATUS_NONE) {
            values.put(Telephony.Sms.STATUS, status);
        }
        if (type != Telephony.Sms.MESSAGE_TYPE_ALL) {
            values.put(Telephony.Sms.TYPE, type);
        }
        if (threadId != -1L) {
            values.put(Telephony.Sms.THREAD_ID, threadId);
        }
        return resolver.insert(uri, values);
    }

    // Insert an SMS message to telephony
    public static Uri insertSmsMessage(final Context context, final Uri uri, final int subId,
            final String dest, final String text, final long timestamp, final int status,
            final int type, final long threadId) {

        return insertSmsMessage( context,   uri,   subId,
              dest,   text,   timestamp,   status,
              type,   threadId ,0);
    }

    // Insert an SMS message to telephony
    public static Uri insertSmsMessage(final Context context, final Uri uri, final int subId,
            final String dest, final String text, final long timestamp, final int status,
            final int type, final long threadId,final long alarmtime) {
        Uri response = null;
        try {
            response = addMessageToUri(context.getContentResolver(), uri, subId, dest,
                    text, null /* subject */, timestamp, true /* read */,
                    true /* seen */, status, type, threadId,alarmtime);
            //if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "Mmsutils: Inserted SMS message into telephony (type = " + type + ") alarmtime:"+alarmtime
                        + ", uri: " + response);
            //}
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "MmsUtils: persist sms message failure " + e, e);
        } catch (final IllegalArgumentException e) {
            LogUtil.e(TAG, "MmsUtils: persist sms message failure " + e, e);
        }
        return response;
    }

    // Update SMS message type in telephony; returns true if it succeeded.
    public static boolean updateSmsMessageSendingStatus(final Context context, final Uri uri,
            final int type, final long date) {
        try {
            final ContentResolver resolver = context.getContentResolver();
            final ContentValues values = new ContentValues(2);

            values.put(Telephony.Sms.TYPE, type);
            values.put(Telephony.Sms.DATE, date);
            final int cnt = resolver.update(uri, values, null, null);
            if (cnt == 1) {
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "Mmsutils: Updated sending SMS " + uri + "; type = " + type
                            + ", date = " + date + " (millis since epoch)");
                }
                return true;
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "MmsUtils: update sms message failure " + e, e);
        } catch (final IllegalArgumentException e) {
            LogUtil.e(TAG, "MmsUtils: update sms message failure " + e, e);
        }
        return false;
    }

    // Persist a sent MMS message in telephony
    private static Uri insertSendReq(final Context context, final GenericPdu pdu, final int subId,
            final String subPhoneNumber) {
        final PduPersister persister = PduPersister.getPduPersister(context);
        Uri uri = null;
        try {
            // Persist the PDU
            uri = persister.persist(
                    pdu,
                    Mms.Sent.CONTENT_URI,
                    subId,
                    subPhoneNumber,
                    null/*preOpenedFiles*/);
            // Update mms table to reflect sent messages are always seen and read
            final ContentValues values = new ContentValues(1);
            values.put(Mms.READ, 1);
            values.put(Mms.SEEN, 1);
            SqliteWrapper.update(context, context.getContentResolver(), uri, values, null, null);
        } catch (final MmsException e) {
            LogUtil.e(TAG, "MmsUtils: persist mms sent message failure " + e, e);
        }
        return uri;
    }

    // Persist a alarm sent MMS message in telephony
    private static Uri insertAlarmSendReq(final Context context, final GenericPdu pdu, final int subId,
            final String subPhoneNumber,final long alarmTime) {
        final PduPersister persister = PduPersister.getPduPersister(context);
        Uri uri = null;
        try {
            // Persist the PDU
            uri = persister.persist(
                    pdu,
                    MmsEx.Alarmbox.CONTENT_URI,
                    subId,
                    subPhoneNumber,
                    null/*preOpenedFiles*/);
            // Update mms table to reflect sent messages are always seen and read
            final ContentValues values = new ContentValues(3);
            values.put(Mms.READ, 1);
            values.put(Mms.SEEN, 1);
            values.put(MmsEx.ALARM, alarmTime);
            SqliteWrapper.update(context, context.getContentResolver(), uri, values, null, null);
        } catch (final MmsException e) {
            LogUtil.e(TAG, "MmsUtils: persist mms sent message failure " + e, e);
        }
        return uri;
    }

    // Persist a received MMS message in telephony
    public static Uri insertReceivedMmsMessage(final Context context,
            final RetrieveConf retrieveConf, final int subId, final String subPhoneNumber,
            final long receivedTimestampInSeconds, final String contentLocation) {
        final PduPersister persister = PduPersister.getPduPersister(context);
        Uri uri = null;
        try {
            uri = persister.persist(
                    retrieveConf,
                    Mms.Inbox.CONTENT_URI,
                    subId,
                    subPhoneNumber,
                    null/*preOpenedFiles*/);

            final ContentValues values = new ContentValues(2);
            // Update mms table with local time instead of PDU time
            values.put(Mms.DATE, receivedTimestampInSeconds);
            // Also update the content location field from NotificationInd so that
            // wap push dedup would work even after the wap push is deleted
            values.put(Mms.CONTENT_LOCATION, contentLocation);
            SqliteWrapper.update(context, context.getContentResolver(), uri, values, null, null);
            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "MmsUtils: Inserted MMS message into telephony, uri: " + uri);
            }
        } catch (final MmsException e) {
            LogUtil.e(TAG, "MmsUtils: persist mms received message failure " + e, e);
            // Just returns empty uri to RetrieveMmsRequest, which triggers a permanent failure
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "MmsUtils: update mms received message failure " + e, e);
            // Time update failure is ignored.
        }
        return uri;
    }

    // Update MMS message type in telephony; returns true if it succeeded.
    public static boolean updateMmsMessageSendingStatus(final Context context, final Uri uri,
            final int box, final long timestampInMillis) {
        try {
            final ContentResolver resolver = context.getContentResolver();
            final ContentValues values = new ContentValues();

            final long timestampInSeconds = timestampInMillis / 1000L;
            values.put(Telephony.Mms.MESSAGE_BOX, box);
            values.put(Telephony.Mms.DATE, timestampInSeconds);
            final int cnt = resolver.update(uri, values, null, null);
            if (cnt == 1) {
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "Mmsutils: Updated sending MMS " + uri + "; box = " + box
                            + ", date = " + timestampInSeconds + " (secs since epoch)");
                }
                return true;
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "MmsUtils: update mms message failure " + e, e);
        } catch (final IllegalArgumentException e) {
            LogUtil.e(TAG, "MmsUtils: update mms message failure " + e, e);
        }
        return false;
    }

    /**
     * Parse values from a received sms message
     *
     * @param context
     * @param msgs The received sms message content
     * @param error The received sms error
     * @return Parsed values from the message
     */
    public static ContentValues parseReceivedSmsMessage(
            final Context context, final SmsMessage[] msgs, final int error) {
        final SmsMessage sms = msgs[0];
        final ContentValues values = new ContentValues();

        values.put(Sms.ADDRESS, sms.getDisplayOriginatingAddress());
        values.put(Sms.BODY, buildMessageBodyFromPdus(msgs));
        if (MmsUtils.hasSmsDateSentColumn()) {
            // TODO:: The boxing here seems unnecessary.
            values.put(Sms.DATE_SENT, Long.valueOf(sms.getTimestampMillis()));
        }
        values.put(Sms.PROTOCOL, sms.getProtocolIdentifier());
        if (sms.getPseudoSubject().length() > 0) {
            values.put(Sms.SUBJECT, sms.getPseudoSubject());
        }
        values.put(Sms.REPLY_PATH_PRESENT, sms.isReplyPathPresent() ? 1 : 0);
        values.put(Sms.SERVICE_CENTER, sms.getServiceCenterAddress());
        // Error code
        values.put(Sms.ERROR_CODE, error);

        return values;
    }

    // Some providers send formfeeds in their messages. Convert those formfeeds to newlines.
    private static String replaceFormFeeds(final String s) {
        return s == null ? "" : s.replace('\f', '\n');
    }

    // Parse the message body from message PDUs
    private static String buildMessageBodyFromPdus(final SmsMessage[] msgs) {
        if (msgs.length == 1) {
            // There is only one part, so grab the body directly.
            return replaceFormFeeds(msgs[0].getDisplayMessageBody());
        } else {
            // Build up the body from the parts.
            final StringBuilder body = new StringBuilder();
            for (final SmsMessage msg : msgs) {
                try {
                    // getDisplayMessageBody() can NPE if mWrappedMessage inside is null.
                    body.append(msg.getDisplayMessageBody());
                } catch (final NullPointerException e) {
                    // Nothing to do
                }
            }
            return replaceFormFeeds(body.toString());
        }
    }

    // Parse the message date
    public static Long getMessageDate(final SmsMessage sms, long now) {
        // Use now for the timestamp to avoid confusion with clock
        // drift between the handset and the SMSC.
        // Check to make sure the system is giving us a non-bogus time.
        final Calendar buildDate = new GregorianCalendar(2011, 8, 18);    // 18 Sep 2011
        final Calendar nowDate = new GregorianCalendar();
        nowDate.setTimeInMillis(now);
        if (nowDate.before(buildDate)) {
            // It looks like our system clock isn't set yet because the current time right now
            // is before an arbitrary time we made this build. Instead of inserting a bogus
            // receive time in this case, use the timestamp of when the message was sent.
            now = sms.getTimestampMillis();
        }
        return now;
    }

    /**
     * cleanseMmsSubject will take a subject that's says, "<Subject: no subject>", and return
     * a null string. Otherwise it will return the original subject string.
     * @param resources So the function can grab string resources
     * @param subject the raw subject
     * @return
     */
    public static String cleanseMmsSubject(final Resources resources, final String subject) {
        if (TextUtils.isEmpty(subject)) {
            return null;
        }
        if (sNoSubjectStrings == null) {
            sNoSubjectStrings =
                    resources.getStringArray(R.array.empty_subject_strings);
        }
        for (final String noSubjectString : sNoSubjectStrings) {
            if (subject.equalsIgnoreCase(noSubjectString)) {
                return null;
            }
        }
        return subject;
    }

    // return a semicolon separated list of phone numbers from a smsto: uri.
    public static String getSmsRecipients(final Uri uri) {
        String recipients = uri.getSchemeSpecificPart();
        final int pos = recipients.indexOf('?');
        if (pos != -1) {
            recipients = recipients.substring(0, pos);
        }
        recipients = replaceUnicodeDigits(recipients).replace(',', ';');
        return recipients;
    }

    // This function was lifted from Telephony.PhoneNumberUtils because it was @hide
    /**
     * Replace arabic/unicode digits with decimal digits.
     * @param number
     *            the number to be normalized.
     * @return the replaced number.
     */
    private static String replaceUnicodeDigits(final String number) {
        final StringBuilder normalizedDigits = new StringBuilder(number.length());
        for (final char c : number.toCharArray()) {
            final int digit = Character.digit(c, 10);
            if (digit != -1) {
                normalizedDigits.append(digit);
            } else {
                normalizedDigits.append(c);
            }
        }
        return normalizedDigits.toString();
    }

    /**
     * @return Whether the data roaming is enabled
     */
    private static boolean isDataRoamingEnabled() {
        boolean dataRoamingEnabled = false;
        final ContentResolver cr = Factory.get().getApplicationContext().getContentResolver();
        if (OsUtil.isAtLeastJB_MR1()) {
            dataRoamingEnabled = (Settings.Global.getInt(cr, Settings.Global.DATA_ROAMING, 0) != 0);
        } else {
            dataRoamingEnabled = (Settings.System.getInt(cr, Settings.System.DATA_ROAMING, 0) != 0);
        }
        return dataRoamingEnabled;
    }

    /**
     * @return Whether to auto retrieve MMS
     */
    public static boolean allowMmsAutoRetrieve(final int subId) {
        final Context context = Factory.get().getApplicationContext();
        final Resources resources = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final boolean autoRetrieve = prefs.getBoolean(
                resources.getString(R.string.auto_retrieve_mms_pref_key),
                resources.getBoolean(R.bool.auto_retrieve_mms_pref_default));
        if (autoRetrieve) {
            final boolean autoRetrieveInRoaming = prefs.getBoolean(
                    resources.getString(R.string.auto_retrieve_mms_when_roaming_pref_key),
                    resources.getBoolean(R.bool.auto_retrieve_mms_when_roaming_pref_default));
            final PhoneUtils phoneUtils = PhoneUtils.get(subId);
            if ((autoRetrieveInRoaming && phoneUtils.isDataRoamingEnabled())
                    || !phoneUtils.isRoaming()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parse the message row id from a message Uri.
     *
     * @param messageUri The input Uri
     * @return The message row id if valid, otherwise -1
     */
    public static long parseRowIdFromMessageUri(final Uri messageUri) {
        try {
            if (messageUri != null) {
                return ContentUris.parseId(messageUri);
            }
        } catch (final UnsupportedOperationException e) {
            // Nothing to do
        } catch (final NumberFormatException e) {
            // Nothing to do
        }
        return -1;
    }

    public static SmsMessageAdapter getSmsMessageFromDeliveryReport(final Intent intent ,final String format) {
        final byte[] pdu = intent.getByteArrayExtra("pdu");
     //  Log.w(TAG,"delivery------------ report"+HexDump.toHexString(pdu));
     //for 1249768 cdma or gsm format
        SmsMessage sms = SmsMessage.createFromPdu(pdu,format);
        SmsMessageAdapter adapter = new SmsMessageAdapter(sms);
      adapter.mRecipientAddress = sms.getDisplayOriginatingAddress();
        return  adapter;
    }

    /**
     * Update the status and date_sent column of sms message in telephony provider
     *
     * @param smsMessageUri
     * @param status
     * @param timeSentInMillis
     */
    public static void updateSmsStatusAndDateSent(final Uri smsMessageUri, final int status,
            final long timeSentInMillis) {
        if (smsMessageUri == null) {
            return;
        }
        final ContentValues values = new ContentValues();
        values.put(Sms.STATUS, status);
        if (MmsUtils.hasSmsDateSentColumn()) {
            values.put(Sms.DATE_SENT, timeSentInMillis);
        }
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        resolver.update(smsMessageUri, values, null/*where*/, null/*selectionArgs*/);
    }

    /**
     * Get the SQL selection statement for matching messages with media.
     *
     * Example for MMS part table:
     * "((ct LIKE 'image/%')
     *   OR (ct LIKE 'video/%')
     *   OR (ct LIKE 'audio/%')
     *   OR (ct='application/ogg'))
     *
     * @param contentTypeColumn The content-type column name
     * @return The SQL selection statement for matching media types: image, video, audio
     */
    public static String getMediaTypeSelectionSql(final String contentTypeColumn) {
        return String.format(
                Locale.US,
                "((%s LIKE '%s') OR (%s LIKE '%s') OR (%s LIKE '%s') OR (%s='%s'))",
                contentTypeColumn,
                "image/%",
                contentTypeColumn,
                "video/%",
                contentTypeColumn,
                "audio/%",
                contentTypeColumn,
                ContentType.AUDIO_OGG);
    }

    // Max number of operands per SQL query for deleting SMS messages
    public static final int MAX_IDS_PER_QUERY = 128;

    /**
     * Delete MMS messages with media parts.
     *
     * Because the telephony provider constraints, we can't use JOIN and delete messages in one
     * shot. We have to do a query first and then batch delete the messages based on IDs.
     *
     * @return The count of messages deleted.
     */
    public static int deleteMediaMessages() {
        // Do a query first
        //
        // The WHERE clause has two parts:
        // The first part is to select the exact same types of MMS messages as when we import them
        // (so that we don't delete messages that are not in local database)
        // The second part is to select MMS with media parts, including image, video and audio
        final String selection = String.format(
                Locale.US,
                "%s AND (%s IN (SELECT %s FROM part WHERE %s))",
                getMmsTypeSelectionSql(),
                Mms._ID,
                Mms.Part.MSG_ID,
                getMediaTypeSelectionSql(Mms.Part.CONTENT_TYPE));
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        final Cursor cursor = resolver.query(Mms.CONTENT_URI,
                new String[]{ Mms._ID },
                selection,
                null/*selectionArgs*/,
                null/*sortOrder*/);
        int deleted = 0;
        if (cursor != null) {
            final long[] messageIds = new long[cursor.getCount()];
            try {
                int i = 0;
                while (cursor.moveToNext()) {
                    messageIds[i++] = cursor.getLong(0);
                }
            } finally {
                cursor.close();
            }
            final int totalIds = messageIds.length;
            if (totalIds > 0) {
                // Batch delete the messages using IDs
                // We don't want to send all IDs at once since there is a limit on SQL statement
                for (int start = 0; start < totalIds; start += MAX_IDS_PER_QUERY) {
                    final int end = Math.min(start + MAX_IDS_PER_QUERY, totalIds); // excluding
                    final int count = end - start;
                    final String batchSelection = String.format(
                            Locale.US,
                            "%s IN %s",
                            Mms._ID,
                            getSqlInOperand(count));
                    final String[] batchSelectionArgs =
                            getSqlInOperandArgs(messageIds, start, count);
                    final int deletedForBatch = resolver.delete(
                            Mms.CONTENT_URI,
                            batchSelection,
                            batchSelectionArgs);
                    if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                        LogUtil.d(TAG, "deleteMediaMessages: deleting IDs = "
                                + Joiner.on(',').skipNulls().join(batchSelectionArgs)
                                + ", deleted = " + deletedForBatch);
                    }
                    deleted += deletedForBatch;
                }
            }
        }
        return deleted;
    }

    /**
     * Get the (?,?,...) thing for the SQL IN operator by a count
     *
     * @param count
     * @return
     */
    public static String getSqlInOperand(final int count) {
        if (count <= 0) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("(?");
        for (int i = 0; i < count - 1; i++) {
            sb.append(",?");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Get the args for SQL IN operator from a long ID array
     *
     * @param ids The original long id array
     * @param start Start of the ids to fill the args
     * @param count Number of ids to pack
     * @return The long array with the id args
     */
    private static String[] getSqlInOperandArgs(
            final long[] ids, final int start, final int count) {
        if (count <= 0) {
            return null;
        }
        final String[] args = new String[count];
        for (int i = 0; i < count; i++) {
            args[i] = Long.toString(ids[start + i]);
        }
        return args;
    }

    /**
     * Delete SMS and MMS messages that are earlier than a specific timestamp
     *
     * @param cutOffTimestampInMillis The cut-off timestamp
     * @return Total number of messages deleted.
     */
    public static int deleteMessagesOlderThan(final long cutOffTimestampInMillis) {
        int deleted = 0;
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        // Delete old SMS
        final String smsSelection = String.format(
                Locale.US,
                "%s AND (%s<=%d)",
                getSmsTypeSelectionSql(),
                Sms.DATE,
                cutOffTimestampInMillis);
        deleted += resolver.delete(Sms.CONTENT_URI, smsSelection, null/*selectionArgs*/);
        // Delete old MMS
        final String mmsSelection = String.format(
                Locale.US,
                "%s AND (%s<=%d)",
                getMmsTypeSelectionSql(),
                Mms.DATE,
                cutOffTimestampInMillis / 1000L);
        deleted += resolver.delete(Mms.CONTENT_URI, mmsSelection, null/*selectionArgs*/);
        return deleted;
    }

    /**
     * Update the read status of SMS/MMS messages by thread and timestamp
     *
     * @param threadId The thread of sms/mms to change
     * @param timestampInMillis Change the status before this timestamp
     */
    public static void updateSmsReadStatus(final long threadId, final long timestampInMillis) {
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        final ContentValues values = new ContentValues();
        values.put("read", 1);
        values.put("seen", 1); /* If you read it you saw it */
        final String smsSelection = String.format(
                Locale.US,
                "%s=%d AND %s<=%d AND %s=0",
                Sms.THREAD_ID,
                threadId,
                Sms.DATE,
                timestampInMillis,
                Sms.READ);
        resolver.update(
                Sms.CONTENT_URI,
                values,
                smsSelection,
                null/*selectionArgs*/);
        final String mmsSelection = String.format(
                Locale.US,
                "%s=%d AND %s<=%d AND %s=0",
                Mms.THREAD_ID,
                threadId,
                Mms.DATE,
                timestampInMillis / 1000L,
                Mms.READ);
        resolver.update(
                Mms.CONTENT_URI,
                values,
                mmsSelection,
                null/*selectionArgs*/);
    }

    /**
     * Update the read status of a single MMS message by its URI
     *
     * @param mmsUri
     * @param read
     */
    public static void updateReadStatusForMmsMessage(final Uri mmsUri, final boolean read) {
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        final ContentValues values = new ContentValues();
        values.put(Mms.READ, read ? 1 : 0);
        resolver.update(mmsUri, values, null/*where*/, null/*selectionArgs*/);
    }

    /**
     * Update the seen status of SMS/MMS messages by thread and timestamp by 856985 begin
     *
     * @param threadId The thread of sms/mms to change
     * @param timestampInMillis Change the status before this timestamp
     */
    public static void updateSmsSeenStatus(final long threadId, final long timestampInMillis) {
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        final ContentValues values = new ContentValues();
        values.put("seen", 1);
        final String smsSelection = String.format(
                Locale.US,
                "%s=%d AND %s<=%d AND %s=0",
                Sms.THREAD_ID,
                threadId,
                Sms.DATE,
                timestampInMillis,
                Sms.SEEN);
        resolver.update(
                Sms.CONTENT_URI,
                values,
                smsSelection,
                null/*selectionArgs*/);
        final String mmsSelection = String.format(
                Locale.US,
                "%s=%d AND %s<=%d AND %s=0",
                Mms.THREAD_ID,
                threadId,
                Mms.DATE,
                timestampInMillis / 1000L,
                Mms.SEEN);
        resolver.update(
                Mms.CONTENT_URI,
                values,
                mmsSelection,
                null/*selectionArgs*/);
    }//by 856985 end

    public static class AttachmentInfo {
        public String mUrl;
        public String mContentType;
        public int mWidth;
        public int mHeight;
    }

    /**
     * Convert byte array to Java String using a charset name
     *
     * @param bytes
     * @param charsetName
     * @return
     */
    public static String bytesToString(final byte[] bytes, final String charsetName) {
        if (bytes == null) {
            return null;
        }
        try {
            return new String(bytes, charsetName);
        } catch (final UnsupportedEncodingException e) {
            LogUtil.e(TAG, "MmsUtils.bytesToString: " + e, e);
            return new String(bytes);
        }
    }

    /**
     * Convert a Java String to byte array using a charset name
     *
     * @param string
     * @param charsetName
     * @return
     */
    public static byte[] stringToBytes(final String string, final String charsetName) {
        if (string == null) {
            return null;
        }
        try {
            return string.getBytes(charsetName);
        } catch (final UnsupportedEncodingException e) {
            LogUtil.e(TAG, "MmsUtils.stringToBytes: " + e, e);
            return string.getBytes();
        }
    }

    private static final String[] TEST_DATE_SENT_PROJECTION = new String[] { Sms.DATE_SENT };
    private static Boolean sHasSmsDateSentColumn = null;
    /**
     * Check if date_sent column exists on ICS and above devices. We need to do a test
     * query to figure that out since on some ICS+ devices, somehow the date_sent column does
     * not exist. http://b/17629135 tracks the associated compliance test.
     *
     * @return Whether "date_sent" column exists in sms table
     */
    public static boolean hasSmsDateSentColumn() {
        if (sHasSmsDateSentColumn == null) {
            Cursor cursor = null;
            try {
                final Context context = Factory.get().getApplicationContext();
                final ContentResolver resolver = context.getContentResolver();
                cursor = SqliteWrapper.query(
                        context,
                        resolver,
                        Sms.CONTENT_URI,
                        TEST_DATE_SENT_PROJECTION,
                        null/*selection*/,
                        null/*selectionArgs*/,
                        Sms.DATE_SENT + " ASC LIMIT 1");
                sHasSmsDateSentColumn = true;
            } catch (final SQLiteException e) {
                LogUtil.w(TAG, "date_sent in sms table does not exist", e);
                sHasSmsDateSentColumn = false;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return sHasSmsDateSentColumn;
    }

    private static final String[] TEST_CARRIERS_PROJECTION =
            new String[] { Telephony.Carriers.MMSC };
    private static Boolean sUseSystemApn = null;
    /**
     * Check if we can access the APN data in the Telephony provider. Access was restricted in
     * JB MR1 (and some JB MR2) devices. If we can't access the APN, we have to fall back and use
     * a private table in our own app.
     *
     * @return Whether we can access the system APN table
     */
    public static boolean useSystemApnTable() {
        if (sUseSystemApn == null) {
            Cursor cursor = null;
            try {
                final Context context = Factory.get().getApplicationContext();
                final ContentResolver resolver = context.getContentResolver();
                cursor = SqliteWrapper.query(
                        context,
                        resolver,
                        Telephony.Carriers.CONTENT_URI,
                        TEST_CARRIERS_PROJECTION,
                        null/*selection*/,
                        null/*selectionArgs*/,
                        null);
                sUseSystemApn = true;
            } catch (final SecurityException e) {
                LogUtil.w(TAG, "Can't access system APN, using internal table", e);
                sUseSystemApn = false;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return sUseSystemApn;
    }

    // For the internal debugger only
    public static void setUseSystemApnTable(final boolean turnOn) {
        if (!turnOn) {
            // We're not turning on to the system table. Instead, we're using our internal table.
            final int osVersion = OsUtil.getApiVersion();
            if (osVersion != android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                // We're turning on local APNs on a device where we wouldn't normally have the
                // local APN table. Build it here.

                final SQLiteDatabase database = ApnDatabase.getApnDatabase().getWritableDatabase();

                // Do we already have the table?
                Cursor cursor = null;
                try {
                    cursor = database.query(ApnDatabase.APN_TABLE,
                            ApnDatabase.APN_PROJECTION,
                            null, null, null, null, null, null);
                } catch (final Exception e) {
                    // Apparently there's no table, create it now.
                    ApnDatabase.forceBuildAndLoadApnTables();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }
        sUseSystemApn = turnOn;
    }

    /**
     * Checks if we should dump sms, based on both the setting and the global debug
     * flag
     *
     * @return if dump sms is enabled
     */
    public static boolean isDumpSmsEnabled() {
        if (!DebugUtils.isDebugEnabled()) {
            return false;
        }
        return getDumpSmsOrMmsPref(R.string.dump_sms_pref_key, R.bool.dump_sms_pref_default);
    }

    /**
     * Checks if we should dump mms, based on both the setting and the global debug
     * flag
     *
     * @return if dump mms is enabled
     */
    public static boolean isDumpMmsEnabled() {
        if (!DebugUtils.isDebugEnabled()) {
            return false;
        }
        return getDumpSmsOrMmsPref(R.string.dump_mms_pref_key, R.bool.dump_mms_pref_default);
    }

    /**
     * Load the value of dump sms or mms setting preference
     */
    private static boolean getDumpSmsOrMmsPref(final int prefKeyRes, final int defaultKeyRes) {
        final Context context = Factory.get().getApplicationContext();
        final Resources resources = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getApplicationPrefs();
        final String key = resources.getString(prefKeyRes);
        final boolean defaultValue = resources.getBoolean(defaultKeyRes);
        return prefs.getBoolean(key, defaultValue);
    }

    public static final Uri MMS_PART_CONTENT_URI = Uri.parse("content://mms/part");

    /**
     * Load MMS from telephony
     *
     * @param mmsUri The MMS pdu Uri
     * @return A memory copy of the MMS pdu including parts (but not addresses)
     */
    public static DatabaseMessages.MmsMessage loadMms(final Uri mmsUri) {
        final Context context = Factory.get().getApplicationContext();
        final ContentResolver resolver = context.getContentResolver();
        DatabaseMessages.MmsMessage mms = null;
        Cursor cursor = null;
        // Load pdu first
        try {
            cursor = SqliteWrapper.query(context, resolver,
                    mmsUri,
                    DatabaseMessages.MmsMessage.getProjection(),
                    null/*selection*/, null/*selectionArgs*/, null/*sortOrder*/);
            if (cursor != null && cursor.moveToFirst()) {
                mms = DatabaseMessages.MmsMessage.get(cursor);
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "loadMms: query pdu failure: " + e, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (mms == null) {
            return null;
        }
        // Load parts except SMIL
        // TODO: we may need to load SMIL part in the future.
        final long rowId = MmsUtils.parseRowIdFromMessageUri(mmsUri);
        final String selection = String.format(
                Locale.US,
                "%s != '%s' AND %s = ?",
                Mms.Part.CONTENT_TYPE,
                ContentType.APP_SMIL,
                Mms.Part.MSG_ID);
        cursor = null;
        try {
            cursor = SqliteWrapper.query(context, resolver,
                    MMS_PART_CONTENT_URI,
                    DatabaseMessages.MmsPart.PROJECTION,
                    selection,
                    new String[] { Long.toString(rowId) },
                    null/*sortOrder*/);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    mms.addPart(DatabaseMessages.MmsPart.get(cursor, true/*loadMedia*/));
                }
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "loadMms: query parts failure: " + e, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return mms;
    }

    /**
     * Get the sender of an MMS message
     *
     * @param recipients The recipient list of the message
     * @param mmsUri The pdu uri of the MMS
     * @return The sender phone number of the MMS
     */
    public static String getMmsSender(final List<String> recipients, final String mmsUri) {
        final Context context = Factory.get().getApplicationContext();
        // We try to avoid the database query.
        // If this is a 1v1 conv., then the other party is the sender
        if (recipients != null && recipients.size() == 1) {
            return recipients.get(0);
        }
        // Otherwise, we have to query the MMS addr table for sender address
        // This should only be done for a received group mms message
        Cursor c = null;
        try {
            c = SqliteWrapper.query(
                    context,
                    context.getContentResolver(),
                    Uri.withAppendedPath(Uri.parse(mmsUri), "addr"),
                    new String[]{Mms.Addr.ADDRESS, Mms.Addr.CHARSET},
                    Mms.Addr.TYPE + "=" + PduHeaders.FROM,
                    null/*selectionArgs*/,
                    null/*sortOrder*/);
            if (c != null) {
                if (c.moveToFirst()) {
                    return DatabaseMessages.MmsAddr.get(c);
                }
            }
        } catch (SQLiteException e) {
            LogUtil.e(TAG, "getMmsSender: query parts failure: " + e, e);
        } finally {
            if (c != null)
                c.close();
        }
        return null;
    }

    public static int bugleStatusForMms(final boolean isOutgoing, final boolean isNotification,
            final int messageBox) {
        int bugleStatus = MessageData.BUGLE_STATUS_UNKNOWN;
        // For a message we sync either
        if (isOutgoing) {
            if (messageBox == Mms.MESSAGE_BOX_OUTBOX || messageBox == Mms.MESSAGE_BOX_FAILED) {
                // Not sent counts as failed and available for manual resend
                bugleStatus = MessageData.BUGLE_STATUS_OUTGOING_FAILED;
            } else {
                // Otherwise outgoing message is complete
                bugleStatus = MessageData.BUGLE_STATUS_OUTGOING_COMPLETE;
            }
        } else if (isNotification) {
            // Incoming MMS notifications we sync count as failed and available for manual download
            bugleStatus = MessageData.BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD;
        } else {
            // Other incoming MMS messages are complete
            bugleStatus = MessageData.BUGLE_STATUS_INCOMING_COMPLETE;
        }
        return bugleStatus;
    }

    public static MessageData createMmsMessage(final DatabaseMessages.MmsMessage mms,
            final String conversationId, final String participantId, final String selfId,
            final int bugleStatus) {
        Assert.notNull(mms);
        final boolean isNotification = (mms.mMmsMessageType ==
                PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND);
        final int rawMmsStatus = (bugleStatus < MessageData.BUGLE_STATUS_FIRST_INCOMING
                ? mms.mRetrieveStatus : mms.mResponseStatus);

        final MessageData message = MessageData.createMmsMessage(mms.getUri(),
                participantId, selfId, conversationId, isNotification, bugleStatus,
                mms.mContentLocation, mms.mTransactionId, mms.mPriority, mms.mSubject,
                mms.mSeen, mms.mRead, mms.getSize(), rawMmsStatus,
                mms.mExpiryInMillis, mms.mSentTimestampInMillis, mms.mTimestampInMillis);

        for (final DatabaseMessages.MmsPart part : mms.mParts) {
            final ArrayList<MessagePartData> messagePartList = MmsUtils.createMmsMessagePart(part);
            // Import media and text parts (skip SMIL and others)
            for (MessagePartData p : messagePartList) {
                if (p != null) {
                    message.addPart(p);
                }
            }
        }

        if (!message.getParts().iterator().hasNext()) {
            message.addPart(MessagePartData.createEmptyMessagePart());
        }

        return message;
    }

    /*Modify by SPRD for Bug550257  2016.04.14 Start*/
    public static ArrayList<MessagePartData> createMmsMessagePart(final DatabaseMessages.MmsPart part) {
        ArrayList<MessagePartData> messagePartList = new ArrayList<MessagePartData>();
        if (part.isText()) {
            final int mmsTextLengthLimit =
                    BugleGservices.get().getInt(BugleGservicesKeys.MMS_TEXT_LIMIT,
                            BugleGservicesKeys.MMS_TEXT_LIMIT_DEFAULT);
            String text = part.mText;
            if (text != null && text.length() > mmsTextLengthLimit) {
                // Limit the text to a reasonable value. We ran into a situation where a vcard
                // with a photo was sent as plain text. The massive amount of text caused the
                // app to hang, ANR, and eventually crash in native text code.
                int partLen = 0;
                String textPart;
                for (int lastList = text.length(); lastList > 0; lastList -= mmsTextLengthLimit) {
                    textPart = text.substring(partLen,
                            lastList < mmsTextLengthLimit ? (lastList + partLen) : (partLen + mmsTextLengthLimit));
                    messagePartList.add(MessagePartData.createTextMessagePart(textPart));
                    partLen += mmsTextLengthLimit;
                }
            } else if (text != null) {
                messagePartList.add(MessagePartData.createTextMessagePart(text));
            }
        } else if (part.isMedia()) {
            messagePartList.add(MessagePartData.createMediaMessagePart(part.mContentType,
                    part.getDataUri(), MessagePartData.UNSPECIFIED_SIZE,
                    MessagePartData.UNSPECIFIED_SIZE));
        }else{
            messagePartList.add(MessagePartData.createMediaMessagePart(part.mContentType,
                    part.getDataUri(), MessagePartData.UNSPECIFIED_SIZE,
                    MessagePartData.UNSPECIFIED_SIZE));
        }
        return messagePartList;
    }
    /*Modify by SPRD for Bug550257  2016.04.14 End*/

    public static class StatusPlusUri {
        // The request status to be as the result of the operation
        // e.g. MMS_REQUEST_MANUAL_RETRY
        public final int status;
        // The raw telephony status
        public final int rawStatus;
        // The raw telephony URI
        public final Uri uri;
        // The operation result code from system api invocation (sent by system)
        // or mapped from internal exception (sent by app)
        public final int resultCode;

        public StatusPlusUri(final int status, final int rawStatus, final Uri uri) {
            this.status = status;
            this.rawStatus = rawStatus;
            this.uri = uri;
            resultCode = MessageData.UNKNOWN_RESULT_CODE;
        }

        public StatusPlusUri(final int status, final int rawStatus, final Uri uri,
                final int resultCode) {
            this.status = status;
            this.rawStatus = rawStatus;
            this.uri = uri;
            this.resultCode = resultCode;
        }
    }

    public static class SendReqResp {
        public SendReq mSendReq;
        public SendConf mSendConf;

        public SendReqResp(final SendReq sendReq, final SendConf sendConf) {
            mSendReq = sendReq;
            mSendConf = sendConf;
        }
    }

    /**
     * Returned when sending/downloading MMS via platform APIs. In that case, we have to wait to
     * receive the pending intent to determine status.
     */
    public static final StatusPlusUri STATUS_PENDING = new StatusPlusUri(-1, -1, null);

    public static StatusPlusUri downloadMmsMessage(final Context context, final Uri notificationUri,
            final int subId, final String subPhoneNumber, final String transactionId,
            final String contentLocation, final boolean autoDownload,
            final long receivedTimestampInSeconds, Bundle extras) {
        if (TextUtils.isEmpty(contentLocation)) {
            LogUtil.e(TAG, "MmsUtils: Download from empty content location URL");
            return new StatusPlusUri(
                    MMS_REQUEST_NO_RETRY, MessageData.RAW_TELEPHONY_STATUS_UNDEFINED, null);
        }

        if (!isMmsDataAvailable(subId)) {
            LogUtil.e(TAG,
                    "MmsUtils: failed to download message, no data available");
            return new StatusPlusUri(MMS_REQUEST_MANUAL_RETRY,
                    MessageData.RAW_TELEPHONY_STATUS_UNDEFINED,
                    null,
                    SmsManager.MMS_ERROR_NO_DATA_NETWORK);
        }
        int status = MMS_REQUEST_MANUAL_RETRY;
        try {
            RetrieveConf retrieveConf = null;
            if (DebugUtils.isDebugEnabled() &&
                    MediaScratchFileProvider
                            .isMediaScratchSpaceUri(Uri.parse(contentLocation))) {
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "MmsUtils: Reading MMS from dump file: " + contentLocation);
                }
                final String fileName = Uri.parse(contentLocation).getPathSegments().get(1);
                final byte[] data = DebugUtils.receiveFromDumpFile(fileName);
                retrieveConf = receiveFromDumpFile(data);
            } else {
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "MmsUtils: Downloading MMS via MMS lib API; notification "
                            + "message: " + notificationUri);
                }
                if (OsUtil.isAtLeastL_MR1()) {
                    if (subId < 0) {
                        LogUtil.e(TAG, "MmsUtils: Incoming MMS came from unknown SIM");
                        throw new MmsFailureException(MMS_REQUEST_NO_RETRY,
                                "Message from unknown SIM");
                    }
                } else {
                    Assert.isTrue(subId == ParticipantData.DEFAULT_SELF_SUB_ID);
                }
                if (extras == null) {
                    extras = new Bundle();
                }
                extras.putParcelable(DownloadMmsAction.EXTRA_NOTIFICATION_URI, notificationUri);
                extras.putInt(DownloadMmsAction.EXTRA_SUB_ID, subId);
                extras.putString(DownloadMmsAction.EXTRA_SUB_PHONE_NUMBER, subPhoneNumber);
                extras.putString(DownloadMmsAction.EXTRA_TRANSACTION_ID, transactionId);
                extras.putString(DownloadMmsAction.EXTRA_CONTENT_LOCATION, contentLocation);
                extras.putBoolean(DownloadMmsAction.EXTRA_AUTO_DOWNLOAD, autoDownload);
                extras.putLong(DownloadMmsAction.EXTRA_RECEIVED_TIMESTAMP,
                        receivedTimestampInSeconds);

                MmsSender.downloadMms(context, subId, contentLocation, extras);
                return STATUS_PENDING; // Download happens asynchronously; no status to return
            }
            return insertDownloadedMessageAndSendResponse(context, notificationUri, subId,
                    subPhoneNumber, transactionId, contentLocation, autoDownload,
                    receivedTimestampInSeconds, retrieveConf);

        } catch (final MmsFailureException e) {
            LogUtil.e(TAG, "MmsUtils: failed to download message " + notificationUri, e);
            status = e.retryHint;
        } catch (final InvalidHeaderValueException e) {
            LogUtil.e(TAG, "MmsUtils: failed to download message " + notificationUri, e);
        }
        return new StatusPlusUri(status, PDU_HEADER_VALUE_UNDEFINED, null);
    }

    public static StatusPlusUri insertDownloadedMessageAndSendResponse(final Context context,
            final Uri notificationUri, final int subId, final String subPhoneNumber,
            final String transactionId, final String contentLocation,
            final boolean autoDownload, final long receivedTimestampInSeconds,
            final RetrieveConf retrieveConf) {
        final byte[] transactionIdBytes = stringToBytes(transactionId, "UTF-8");
        Uri messageUri = null;
        int status = MMS_REQUEST_MANUAL_RETRY;
        int retrieveStatus = PDU_HEADER_VALUE_UNDEFINED;

        retrieveStatus = retrieveConf.getRetrieveStatus();
        if (retrieveStatus == PduHeaders.RETRIEVE_STATUS_OK) {
            status = MMS_REQUEST_SUCCEEDED;
        } else if (retrieveStatus >= PduHeaders.RETRIEVE_STATUS_ERROR_TRANSIENT_FAILURE &&
                retrieveStatus < PduHeaders.RETRIEVE_STATUS_ERROR_PERMANENT_FAILURE) {
            status = MMS_REQUEST_AUTO_RETRY;
        } else {
            // else not meant to retry download
            status = MMS_REQUEST_NO_RETRY;
            LogUtil.e(TAG, "MmsUtils: failed to retrieve message; retrieveStatus: "
                    + retrieveStatus);
        }
        final ContentValues values = new ContentValues(1);
        values.put(Mms.RETRIEVE_STATUS, retrieveConf.getRetrieveStatus());
        SqliteWrapper.update(context, context.getContentResolver(),
                notificationUri, values, null, null);

        if (status == MMS_REQUEST_SUCCEEDED) {
            // Send response of the notification
            if (autoDownload) {
                sendNotifyResponseForMmsDownload(context, subId, transactionIdBytes,
                        contentLocation, PduHeaders.STATUS_RETRIEVED);
            } else {
                sendAcknowledgeForMmsDownload(context, subId, transactionIdBytes, contentLocation);
            }

            // Insert downloaded message into telephony
            final Uri inboxUri = MmsUtils.insertReceivedMmsMessage(context, retrieveConf, subId,
                    subPhoneNumber, receivedTimestampInSeconds, contentLocation);
            messageUri = ContentUris.withAppendedId(Mms.CONTENT_URI, ContentUris.parseId(inboxUri));
        } else if (status == MMS_REQUEST_AUTO_RETRY) {
            // For a retry do nothing
        } else if (status == MMS_REQUEST_MANUAL_RETRY && autoDownload) {
            // Failure from autodownload - just treat like manual download
            sendNotifyResponseForMmsDownload(context, subId, transactionIdBytes,
                    contentLocation, PduHeaders.STATUS_DEFERRED);
        }
        return new StatusPlusUri(status, retrieveStatus, messageUri);
    }

    /**
     * Send response for MMS download - catches and ignores errors
     */
    public static void sendNotifyResponseForMmsDownload(final Context context, final int subId,
            final byte[] transactionId, final String contentLocation, final int status) {
        try {
            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "MmsUtils: Sending M-NotifyResp.ind for received MMS, status: "
                        + String.format("0x%X", status));
            }
            if (contentLocation == null) {
                LogUtil.w(TAG, "MmsUtils: Can't send NotifyResp; contentLocation is null");
                return;
            }
            if (transactionId == null) {
                LogUtil.w(TAG, "MmsUtils: Can't send NotifyResp; transaction id is null");
                return;
            }
            if (!isMmsDataAvailable(subId)) {
                LogUtil.w(TAG, "MmsUtils: Can't send NotifyResp; no data available");
                return;
            }
            MmsSender.sendNotifyResponseForMmsDownload(
                    context, subId, transactionId, contentLocation, status);
        } catch (final MmsFailureException e) {
            LogUtil.e(TAG, "sendNotifyResponseForMmsDownload: failed to retrieve message " + e, e);
        } catch (final InvalidHeaderValueException e) {
            LogUtil.e(TAG, "sendNotifyResponseForMmsDownload: failed to retrieve message " + e, e);
        }
    }

    /**
     * Send acknowledge for mms download - catched and ignores errors
     */
    public static void sendAcknowledgeForMmsDownload(final Context context, final int subId,
            final byte[] transactionId, final String contentLocation) {
        try {
            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, "MmsUtils: Sending M-Acknowledge.ind for received MMS");
            }
            if (contentLocation == null) {
                LogUtil.w(TAG, "MmsUtils: Can't send AckInd; contentLocation is null");
                return;
            }
            if (transactionId == null) {
                LogUtil.w(TAG, "MmsUtils: Can't send AckInd; transaction id is null");
                return;
            }
            if (!isMmsDataAvailable(subId)) {
                LogUtil.w(TAG, "MmsUtils: Can't send AckInd; no data available");
                return;
            }
            MmsSender.sendAcknowledgeForMmsDownload(context, subId, transactionId, contentLocation);
        } catch (final MmsFailureException e) {
            LogUtil.e(TAG, "sendAcknowledgeForMmsDownload: failed to retrieve message " + e, e);
        } catch (final InvalidHeaderValueException e) {
            LogUtil.e(TAG, "sendAcknowledgeForMmsDownload: failed to retrieve message " + e, e);
        }
    }

    /**
     * Try parsing a PDU without knowing the carrier. This is useful for importing
     * MMS or storing draft when carrier info is not available
     *
     * @param data The PDU data
     * @return Parsed PDU, null if failed to parse
     */
    private static GenericPdu parsePduForAnyCarrier(final byte[] data) {
        GenericPdu pdu = null;
        try {
            pdu = (new PduParser(data, true/*parseContentDisposition*/)).parse();
        } catch (final RuntimeException e) {
            LogUtil.d(TAG, "parsePduForAnyCarrier: Failed to parse PDU with content disposition",
                    e);
        }
        if (pdu == null) {
            try {
                pdu = (new PduParser(data, false/*parseContentDisposition*/)).parse();
            } catch (final RuntimeException e) {
                LogUtil.d(TAG,
                        "parsePduForAnyCarrier: Failed to parse PDU without content disposition",
                        e);
            }
        }
        return pdu;
    }

    private static RetrieveConf receiveFromDumpFile(final byte[] data) throws MmsFailureException {
        final GenericPdu pdu = parsePduForAnyCarrier(data);
        if (pdu == null || !(pdu instanceof RetrieveConf)) {
            LogUtil.e(TAG, "receiveFromDumpFile: Parsing retrieved PDU failure");
            throw new MmsFailureException(MMS_REQUEST_MANUAL_RETRY, "Failed reading dump file");
        }
        return (RetrieveConf) pdu;
    }

    private static boolean isMmsDataAvailable(final int subId) {
       /* if (OsUtil.isAtLeastL_MR1()) {
            // L_MR1 above may support sending mms via wifi
            return true;
        }*/
        if(MmsConfig.getCmccSdkEnabledForCmccTest()){
			//we user cmcc smart sdk open,cmcc test open it
	if(!PhoneUtils.get(subId).isDataRoamingEnabled()
	    &&PhoneUtils.get(subId).isRoaming())
	{
		LogUtil.e(TAG,"In Roaming we not send response in roaming and roaming not enable");
		return false;
	}
	}
        final PhoneUtils phoneUtils = PhoneUtils.get(subId);
        return !phoneUtils.isAirplaneModeOn() && /*phoneUtils.isMobileDataEnabled() &&*/ phoneUtils.hasSim();

    }

    private static boolean isSmsDataAvailable(final int subId) {
       /* if (OsUtil.isAtLeastL_MR1()) {
            // L_MR1 above may support sending sms via wifi
            return true;
        }*/
        final PhoneUtils phoneUtils = PhoneUtils.get(subId);
        return !phoneUtils.isAirplaneModeOn() && phoneUtils.hasSim();
    }

    public static boolean isMobileDataEnabled(final int subId) {
        final PhoneUtils phoneUtils = PhoneUtils.get(subId);
        return phoneUtils.isMobileDataEnabled();
    }

    public static boolean isAirplaneModeOn(final int subId) {
        final PhoneUtils phoneUtils = PhoneUtils.get(subId);
        return phoneUtils.isAirplaneModeOn();
    }

    public static StatusPlusUri sendMmsMessage(final Context context, final int subId,
            final Uri messageUri, final Bundle extras) {
        int status = MMS_REQUEST_MANUAL_RETRY;

        int rawStatus = MessageData.RAW_TELEPHONY_STATUS_UNDEFINED;

        if (!isMmsDataAvailable(subId)) {
            LogUtil.w(TAG, "MmsUtils: failed to send message, no data available");
            return new StatusPlusUri(MMS_REQUEST_MANUAL_RETRY,
                    MessageData.RAW_TELEPHONY_STATUS_UNDEFINED,
                    messageUri,
                    SmsManager.MMS_ERROR_NO_DATA_NETWORK);
        }
        final PduPersister persister = PduPersister.getPduPersister(context);
        try {
            final SendReq sendReq = (SendReq) persister.load(messageUri);
            if (sendReq == null) {
                LogUtil.w(TAG, "MmsUtils: Sending MMS was deleted; uri = " + messageUri);
                return new StatusPlusUri(MMS_REQUEST_NO_RETRY,
                        MessageData.RAW_TELEPHONY_STATUS_UNDEFINED, messageUri);
            }
            if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                LogUtil.d(TAG, String.format("MmsUtils: Sending MMS, message uri: %s", messageUri));
            }
            extras.putInt(SendMessageAction.KEY_SUB_ID, subId);
            MmsSender.sendMms(context, subId, messageUri, sendReq, extras);
            return STATUS_PENDING;
        }catch (final IllegalArgumentException e) {
//            android.util.Log.d("jinqiming","IllegalArgumentException");
//            if (e.getMessage().contains("Invalid FDN destinationAddress")) {
//                //LogUtil.e(TAG, "MmsUtils: Invalid FDN destinationAddress " + e, e);
//                android.util.Log.d("jinqiming","sendMmsMessage");
//                // fdn feature toast end
//                boolean showToat = true ;
//                if (GlobleUtil.mGropMmsMessagingTotalCount ==0) {
//                    showToat = false;
//                }
//                android.util.Log.d("jinqiming","showToat ="+showToat);
//                if (showToat) {
//                    GlobleUtil.mGropMmsMessagingTotalCount = 0;
//                    Message msgflag = GlobleUtil.mGlobleHandler.obtainMessage();
//                    msgflag.what = GlobleUtil.FDN_TOAST_MSG;
//                    msgflag.obj = Factory.get().getApplicationContext()
//                            .getString(R.string.fdn_check_failure);
//                    GlobleUtil.mGlobleHandler.sendMessage(msgflag);
//                }
//                //status = SendResult.FAILURE_LEVEL_PERMANENT;
//            }
            LogUtil.e(TAG, "MmsUtils: invalid message to send " + e, e);
        } catch (final MmsFailureException e) {
            status = e.retryHint;
            rawStatus = e.rawStatus;
            LogUtil.e(TAG, "MmsUtils: failed to send message " + e, e);
        } catch (final InvalidHeaderValueException e) {
            LogUtil.e(TAG, "MmsUtils: failed to send message " + e, e);
        } catch (final MmsException e) {
            LogUtil.e(TAG, "MmsUtils: failed to send message " + e, e);
        }
        // If we get here, some exception occurred
        return new StatusPlusUri(status, rawStatus, messageUri);
    }

    //489220 begin
    public static String getFrom(Context context, Uri uri) {
        String msgId = uri.getLastPathSegment();
        Uri.Builder builder = Mms.CONTENT_URI.buildUpon();

        builder.appendPath(msgId).appendPath("addr");

        Cursor c = null;
        try {
            c = SqliteWrapper.query(context, context.getContentResolver(),
                    builder.build(), new String[]{Addr.ADDRESS, Addr.CHARSET},
                    Addr.TYPE + "=" + PduHeaders.FROM, null, null);
            if (c == null || c.getCount() == 0) {
                return context.getString(R.string.hidden_sender_address);
            }
            if (c.moveToFirst()) {
                String from = c.getString(0);

                if (!TextUtils.isEmpty(from)) {
                    byte[] bytes = PduPersister.getBytes(from);
                    int charset = c.getInt(1);
                    return new EncodedStringValue(charset, bytes)
                            .getString();
                }
            }
        } catch (SQLiteException e) {
            LogUtil.e(TAG, "getFrom: query pdu failure: " + e, e);
        } finally{
            if (c != null) {
                c.close();
            }
        }
        return context.getString(R.string.hidden_sender_address);
    }

    public static void sendReadRec(final Context context, final int subId, String to, String messageId, int status) {
        EncodedStringValue[] sender = new EncodedStringValue[1];
        sender[0] = new EncodedStringValue(to);
        try {
            final ReadRecInd readRec = new ReadRecInd(
                    new EncodedStringValue(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR.getBytes()),
                    messageId.getBytes(),
                    PduHeaders.CURRENT_MMS_VERSION,
                    status,
                    sender);

            readRec.setDate(System.currentTimeMillis() / 1000);

            MmsSender.sendMmsReadRec(context, subId, null, readRec, null);
        } catch (InvalidHeaderValueException e) {
            LogUtil.e(TAG, "Invalide header value", e);
        } catch (MmsFailureException e) {
            LogUtil.e(TAG, "sendReadRec message failed", e);
        }
    }

    private static long findThreadId(Context context, GenericPdu pdu, int type) {
        String messageId;

        if (type == PduHeaders.MESSAGE_TYPE_DELIVERY_IND) {
            messageId = new String(((DeliveryInd) pdu).getMessageId());
        } else {
            messageId = new String(((ReadOrigInd) pdu).getMessageId());
        }
        StringBuilder sb = new StringBuilder('(');
        sb.append(Mms.MESSAGE_ID);
        sb.append('=');
        sb.append(DatabaseUtils.sqlEscapeString(messageId));
        sb.append(" AND ");
        sb.append(Mms.MESSAGE_TYPE);
        sb.append('=');
        sb.append(PduHeaders.MESSAGE_TYPE_SEND_REQ);
        Cursor cursor = null;
        try {
            cursor = SqliteWrapper.query(context,
                    context.getContentResolver(), Mms.CONTENT_URI,
                    new String[]{Mms.THREAD_ID}, sb.toString(), null, null);

            if (cursor != null) {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            }
        } catch (SQLiteException e) {
            LogUtil.e(TAG, "findThreadId: query pdu failure: " + e, e);
        } finally{
            if (cursor != null)
                cursor.close();
        }

        return -1;
    }

    /**
     * Try to find transactionId in Telephony DB by a DeliveryInd pdu
     * @param context
     * @param pdu
     * @param type pdu type
     * @return The transactionId if found or null
     */
    private static String findTransactionId(Context context, GenericPdu pdu,
            int type) {
        String transactionId;
        String messageId;
        if (type == PduHeaders.MESSAGE_TYPE_DELIVERY_IND) {
            messageId = new String(((DeliveryInd) pdu).getMessageId());
        } else {
            LogUtil.e(TAG, "findTransactionId: Wrong pdu type!");
            return null;
        }
        StringBuilder selection = new StringBuilder(128);
        selection.append(Mms.MESSAGE_ID);
        selection.append('=');
        selection.append(DatabaseUtils.sqlEscapeString(messageId));
        selection.append(" AND ");
        selection.append(Mms.MESSAGE_TYPE);
        selection.append('=');
        selection.append(PduHeaders.MESSAGE_TYPE_SEND_REQ);

        Cursor cursor = null;
        try {
            cursor = SqliteWrapper.query(context,
                    context.getContentResolver(), Mms.CONTENT_URI,
                    new String[]{Mms.TRANSACTION_ID}, selection.toString(),
                    null, null);
            if (cursor != null) {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        } catch (SQLiteException e) {
            LogUtil.e(TAG, "findTransactionId: query pdu failure: " + e, e);
        } finally{
            if (cursor != null)
                cursor.close();
        }
        return null;
    }
    /**
     * Try to find the smsMessageUri in Messaging DB by given transactionId
     * @param transactionId
     * @return The smsMessageUri if found or null
     */
    private static Uri findSmsMessageUriByTransactionId(String transactionId) {
        final DatabaseWrapper db = DataModel.get().getDatabase();
        if (null == transactionId) {
            LogUtil.e(TAG,
                    "findSmsMessageUriByTransactionId: transactionId is null!");
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = db.query(DatabaseHelper.MESSAGES_TABLE, // searchTable
                    new String[]{DatabaseHelper.MessageColumns.SMS_MESSAGE_URI}, // projection
                    DatabaseHelper.MessageColumns.MMS_TRANSACTION_ID + "= ?", // selection
                    new String[]{transactionId}, // selectionArgs
                    null, null, null);
            if (cursor != null) {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    return Uri.parse(cursor.getString(0));
                }
            }
        } catch (SQLiteException e) {
            LogUtil.e(TAG, "findSmsMessageUriByTransactionId: query pdu failure: " + e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
        LogUtil.e(TAG,
                "findSmsMessageUriByTransactionId: Can't find SmsMessageUri!");
        return null;
    }
    // 489220 end

    public static StatusPlusUri updateSentMmsMessageStatus(final Context context,
            final Uri messageUri, final SendConf sendConf) {
        int status = MMS_REQUEST_MANUAL_RETRY;
        final int respStatus = sendConf.getResponseStatus();

        final ContentValues values = new ContentValues(2);
        values.put(Mms.RESPONSE_STATUS, respStatus);
        final byte[] messageId = sendConf.getMessageId();
        if (messageId != null && messageId.length > 0) {
            values.put(Mms.MESSAGE_ID, PduPersister.toIsoString(messageId));
        }
        SqliteWrapper.update(context, context.getContentResolver(),
                messageUri, values, null, null);
        if (respStatus == PduHeaders.RESPONSE_STATUS_OK) {
            status = MMS_REQUEST_SUCCEEDED;
        } else if (respStatus == PduHeaders.RESPONSE_STATUS_ERROR_TRANSIENT_FAILURE ||
                respStatus == PduHeaders.RESPONSE_STATUS_ERROR_TRANSIENT_NETWORK_PROBLEM ||
                respStatus == PduHeaders.RESPONSE_STATUS_ERROR_TRANSIENT_PARTIAL_SUCCESS) {
            status = MMS_REQUEST_AUTO_RETRY;
        } else {
            // else permanent failure
            LogUtil.e(TAG, "MmsUtils: failed to send message; respStatus = "
                    + String.format("0x%X", respStatus));
        }
        return new StatusPlusUri(status, respStatus, messageUri);
    }

    public static void clearMmsStatus(final Context context, final Uri uri) {
        // Messaging application can leave invalid values in STATUS field of M-Notification.ind
        // messages.  Take this opportunity to clear it.
        // Downloading status just kept in local db and not reflected into telephony.
        final ContentValues values = new ContentValues(1);
        values.putNull(Mms.STATUS);
        SqliteWrapper.update(context, context.getContentResolver(),
                    uri, values, null, null);
    }

    // Selection for new dedup algorithm:
    // ((m_type<>130) OR (exp>NOW)) AND (date>NOW-7d) AND (date<NOW+7d) AND (ct_l=xxxxxx)
    // i.e. If it is NotificationInd and not expired or not NotificationInd
    //      AND message is received with +/- 7 days from now
    //      AND content location is the input URL
    private static final String DUP_NOTIFICATION_QUERY_SELECTION =
            "((" + Mms.MESSAGE_TYPE + "<>?) OR (" + Mms.EXPIRY + ">?)) AND ("
                    + Mms.DATE + ">?) AND (" + Mms.DATE + "<?) AND (" + Mms.CONTENT_LOCATION +
                    "=?)";
    // Selection for old behavior: only checks NotificationInd and its content location
    private static final String DUP_NOTIFICATION_QUERY_SELECTION_OLD =
            "(" + Mms.MESSAGE_TYPE + "=?) AND (" + Mms.CONTENT_LOCATION + "=?)";

    private static final int MAX_RETURN = 32;
    private static String[] getDupNotifications(final Context context, final NotificationInd nInd) {
        final byte[] rawLocation = nInd.getContentLocation();
        if (rawLocation != null) {
            final String location = new String(rawLocation);
            // We can not be sure if the content location of an MMS is globally and historically
            // unique. So we limit the dedup time within the last 7 days
            // (or configured by gservices remotely). If the same content location shows up after
            // that, we will download regardless. Duplicated message is better than no message.
            String selection;
            String[] selectionArgs;
            final long timeLimit = BugleGservices.get().getLong(
                    BugleGservicesKeys.MMS_WAP_PUSH_DEDUP_TIME_LIMIT_SECS,
                    BugleGservicesKeys.MMS_WAP_PUSH_DEDUP_TIME_LIMIT_SECS_DEFAULT);
            if (timeLimit > 0) {
                // New dedup algorithm
                selection = DUP_NOTIFICATION_QUERY_SELECTION;
                final long nowSecs = System.currentTimeMillis() / 1000;
                final long timeLowerBoundSecs = nowSecs - timeLimit;
                // Need upper bound to protect against clock change so that a message has a time
                // stamp in the future
                final long timeUpperBoundSecs = nowSecs + timeLimit;
                selectionArgs = new String[] {
                        Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                        Long.toString(nowSecs),
                        Long.toString(timeLowerBoundSecs),
                        Long.toString(timeUpperBoundSecs),
                        location
                };
            } else {
                // If time limit is 0, we revert back to old behavior in case the new
                // dedup algorithm behaves badly
                selection = DUP_NOTIFICATION_QUERY_SELECTION_OLD;
                selectionArgs = new String[] {
                        Integer.toString(PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND),
                        location
                };
            }
            Cursor cursor = null;
            try {
                cursor = SqliteWrapper.query(
                        context, context.getContentResolver(),
                        Mms.CONTENT_URI, new String[] { Mms._ID },
                        selection, selectionArgs, null);
                final int dupCount = cursor.getCount();
                if (dupCount > 0) {
                    // We already received the same notification before.
                    // Don't want to return too many dups. It is only for debugging.
                    final int returnCount = dupCount < MAX_RETURN ? dupCount : MAX_RETURN;
                    final String[] dups = new String[returnCount];
                    for (int i = 0; cursor.moveToNext() && i < returnCount; i++) {
                        dups[i] = cursor.getString(0);
                    }
                    return dups;
                }
            } catch (final SQLiteException e) {
                LogUtil.e(TAG, "query failure: " + e, e);
            } finally {
                if (cursor != null)
                    cursor.close();
            }
        }
        return null;
    }

    /**
     * Try parse the address using RFC822 format. If it fails to parse, then return the
     * original address
     *
     * @param address The MMS ind sender address to parse
     * @return The real address. If in RFC822 format, returns the correct email.
     */
    private static String parsePotentialRfc822EmailAddress(final String address) {
        if (address == null || !address.contains("@") || !address.contains("<")) {
            return address;
        }
        final Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(address);
        if (tokens != null && tokens.length > 0) {
            for (final Rfc822Token token : tokens) {
                if (token != null && !TextUtils.isEmpty(token.getAddress())) {
                    return token.getAddress();
                }
            }
        }
        return address;
    }

    public static DatabaseMessages.MmsMessage processReceivedPdu(final Context context,
            final byte[] pushData, final int subId, final String subPhoneNumber) {
        // Parse data

        // Insert placeholder row to telephony and local db
        // Get raw PDU push-data from the message and parse it
        final PduParser parser = new PduParser(pushData,
                MmsConfig.get(subId).getSupportMmsContentDisposition());
        final GenericPdu pdu = parser.parse();

        if (null == pdu) {
            LogUtil.e(TAG, "Invalid PUSH data");
            return null;
        }

        final PduPersister p = PduPersister.getPduPersister(context);
        // 489220 begin
        ContentResolver cr = context.getContentResolver();
        // 489220 end
        final int type = pdu.getMessageType();

        Uri messageUri = null;

        // 489220 begin
        try {
            switch (type) {
            case PduHeaders.MESSAGE_TYPE_DELIVERY_IND:
            case PduHeaders.MESSAGE_TYPE_READ_ORIG_IND: {

                long threadId = findThreadId(context, pdu, type);
                LogUtil.d(TAG, "threadId:[" + threadId + "] type:[" + type
                        + "]");

                /* Add by SPRD for bug 531941 Start */
                String transactionId = findTransactionId(context, pdu, type);
                LogUtil.d(TAG, "find TransactionId from DeliveryInd dup: "
                        + "transactionId = " + transactionId);
                /* Add by SPRD for bug 531941 End */
                if (threadId == -1) {
                    // The associated SendReq isn't found, therefore skip
                    // processing this PDU.
                    LogUtil.d(TAG,
                            "The associated SendReq isn't found, therefore skip processing this PDU");
                    break;
                }

                Uri uri = p.persist(pdu, Mms.Inbox.CONTENT_URI, subId,
                        subPhoneNumber, null);
                // Update thread ID for ReadOrigInd & DeliveryInd.
                ContentValues values = new ContentValues(1);
                values.put(Mms.THREAD_ID, threadId);
                SqliteWrapper.update(context, cr, uri, values, null, null);
                LogUtil.d(TAG, "uri = " + uri);

                EncodedStringValue encodedStringValue = null;
                String report = null;
                String address = null;
                if (type == PduHeaders.MESSAGE_TYPE_DELIVERY_IND) {
                    DeliveryInd deliveryInd = (DeliveryInd) pdu;
                    if (null != deliveryInd.getTo()) {
                        encodedStringValue = (deliveryInd.getTo())[0];
                        if (null != encodedStringValue) {
                            // address =
                            // Contact.get(encodedStringValue.getString(),
                            // true).getName();
                            address = encodedStringValue.getString();
                        }
                    }
                    int status = deliveryInd.getStatus();
                    if (status == PduHeaders.STATUS_FORWARDED
                            || status == PduHeaders.STATUS_RETRIEVED) {
                        /* Add by SPRD for bug 531941 Start */
                        Uri smsMessageUri = findSmsMessageUriByTransactionId(transactionId);
                        LogUtil.d(
                                TAG,
                                "processReceivedPdu: type = MESSAGE_TYPE_DELIVERY_IND, "
                                        + "findSmsMessageUriByTransactionId: smsMessageUri = "
                                        + smsMessageUri);
                        if (smsMessageUri != null) {
                            ProcessDeliveryReportAction.deliveryReportReceived(
                                    smsMessageUri, 0);
                        }
                        /* Add by SPRD for bug 531941 End */
                        report = context
                                .getString(R.string.delivery_toast_body);
                    } else if (status == PduHeaders.STATUS_EXPIRED) {
                        report = context
                                .getString(R.string.delivery_toast_body_expired);
                    } else if (status == PduHeaders.STATUS_REJECTED) {
                        report = context
                                .getString(R.string.delivery_toast_body_fail_rejected);
                    } else {
                        report = context
                                .getString(R.string.delivery_toast_body_fail);
                    }
                } else {
                    ReadOrigInd readOrigInd = (ReadOrigInd) pdu;
                    if (null != readOrigInd.getTo()) {
                        encodedStringValue = (readOrigInd.getTo())[0];
                        if (null != encodedStringValue) {
                            // address =
                            // Contact.get(encodedStringValue.getString(),
                            // true).getName();
                            address = encodedStringValue.getString();
                        }
                    }
                    int status = readOrigInd.getReadStatus();
                    if (status == PduHeaders.READ_STATUS_READ) {
                        report = context
                                .getString(R.string.read_report_toast_body);
                    } else {
                        report = context
                                .getString(R.string.read_report_toast_body_fail);
                    }
                }
                    /* Add by SPRD for bug 589352 Start */
                    showReportWithContactName(context, address, report);
                    /* Add by SPRD for bug 589352 end */
                break;
            }
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND: {
                final NotificationInd nInd = (NotificationInd) pdu;

                if (MmsConfig.get(subId).getTransIdEnabled()) {
                    final byte[] contentLocationTemp = nInd
                            .getContentLocation();
                    if ('=' == contentLocationTemp[contentLocationTemp.length - 1]) {
                        final byte[] transactionIdTemp = nInd
                                .getTransactionId();
                        final byte[] contentLocationWithId = new byte[contentLocationTemp.length
                                + transactionIdTemp.length];
                        System.arraycopy(contentLocationTemp, 0,
                                contentLocationWithId, 0,
                                contentLocationTemp.length);
                        System.arraycopy(transactionIdTemp, 0,
                                contentLocationWithId,
                                contentLocationTemp.length,
                                transactionIdTemp.length);
                        nInd.setContentLocation(contentLocationWithId);
                    }
                }
                final String[] dups = getDupNotifications(context, nInd);
                if (dups == null) {
                    // TODO: Do we handle Rfc822 Email Addresses?
                    // final String contentLocation =
                    // MmsUtils.bytesToString(nInd.getContentLocation(),
                    // "UTF-8");
                    // final byte[] transactionId = nInd.getTransactionId();
                    // final long messageSize = nInd.getMessageSize();
                    // final long expiry = nInd.getExpiry();
                    // final String transactionIdString =
                    // MmsUtils.bytesToString(transactionId, "UTF-8");

                    // final EncodedStringValue fromEncoded = nInd.getFrom();
                    // An mms ind received from email address will have from
                    // address shown as
                    // "John Doe <johndoe@foobar.com>" but the actual received
                    // message will only
                    // have the email address. So let's try to parse the RFC822
                    // format to get the
                    // real email. Otherwise we will create two conversations
                    // for the MMS
                    // notification and the actual MMS message if auto retrieve
                    // is disabled.
                    // final String from = parsePotentialRfc822EmailAddress(
                    // fromEncoded != null ? fromEncoded.getString() : null);

                    Uri inboxUri = null;
                    try {
                        inboxUri = p.persist(pdu, Mms.Inbox.CONTENT_URI, subId,
                                subPhoneNumber, null);
                        messageUri = ContentUris.withAppendedId(
                                Mms.CONTENT_URI, ContentUris.parseId(inboxUri));
                    } catch (final MmsException e) {
                        LogUtil.e(TAG,
                                "Failed to save the data from PUSH: type="
                                        + type, e);
                    }
                } else {
                    LogUtil.w(TAG,
                            "Received WAP Push is a dup: "
                                    + Joiner.on(',').join(dups));
                    if (LogUtil.isLoggable(TAG, LogUtil.VERBOSE)) {
                        LogUtil.w(
                                TAG,
                                "Dup WAP Push url="
                                        + new String(nInd.getContentLocation()));
                    }
                }
                break;
            }
            default:
                LogUtil.e(TAG, "Received unrecognized WAP Push, type=" + type);
            }
        } catch (MmsException e) {
            LogUtil.e(TAG, "Failed to save the data from PUSH: type=" + type, e);
        } catch (RuntimeException e) {
            LogUtil.e(TAG, "Unexpected RuntimeException.", e);
        }
        // 489220 end

        DatabaseMessages.MmsMessage mms = null;
        if (messageUri != null) {
            mms = MmsUtils.loadMms(messageUri);
        }
        return mms;
    }

    /* Add by SPRD for bug 589352 Start */
    private static HandlerThread mGetDisplayNameHandlerThread = null;

    private static HandlerThread getHandlerThread() {
        if (null == mGetDisplayNameHandlerThread) {
            mGetDisplayNameHandlerThread = new HandlerThread("display_name");
        }
        return mGetDisplayNameHandlerThread;
    }
    // Add for bug 732616 start
    public static void showSmsDeliveryReport(final Context context,final Uri smsMessageUri){
        Cursor telecursor = null;
        String address = null;
        String displayName =null;
        String reportString =null;
        LogUtil.d(TAG, "showSmsDeliveryReport:"+smsMessageUri);
        try{
            final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
            telecursor = resolver.query(smsMessageUri,new String[] {Sms.ADDRESS},null,null/*selectionArgs*/,null/*sortOrder*/);
            if(telecursor != null){
                if(telecursor.moveToFirst()){
                    address = telecursor.getString(0);
                    LogUtil.d(TAG, "Get ADDRESS:"+address);
                }
            }
        }catch(Exception e){
            LogUtil.d(TAG, "Get ADDRESS:"+e.toString());
        }finally{
            if(telecursor != null){
                telecursor.close();
            }
        }
        CursorQueryData cursorQueryData = ContactUtil.lookupDestination(context, address);
        Cursor cursor = null;
        try {
            cursor = cursorQueryData.performSynchronousQuery();
            if (cursor == null || cursor.getCount() == 0) {
                displayName = null;
            } else {
                cursor.moveToFirst();
                do {
                    int nameFieldColumnIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    displayName = cursor.getString(nameFieldColumnIndex);
                    break;
                } while (cursor.moveToNext());
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        String report = context.getString(R.string.delivery_toast_body);
        if (displayName != null) {
            reportString = String.format(report, displayName);
        } else {
            final BidiFormatter bidiFormatter = BidiFormatter.getInstance();//by bug 1220639
            final String numberText = bidiFormatter.unicodeWrap(address, TextDirectionHeuristics.LTR);
            reportString = String.format(report, numberText);
        }

        LogUtil.d(TAG, "Report message is: " + report);
        Intent notify = new Intent(NOTIFY_SHOW_MMS_SMS_REPORT_ACTION);
        notify.setClassName("com.android.messaging","com.android.messaging.receiver.MmsSmsEventReceiver"); //Bug950322
        notify.putExtra("report", reportString);
        context.sendBroadcast(notify);
    }
    // Add for bug 732616 end
    public static void showReportWithContactName(final Context context, final String address, final String report) {
        HandlerThread handlerThread = getHandlerThread();
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                String reportString;
                String displayName = null;
                super.handleMessage(msg);
                CursorQueryData cursorQueryData = ContactUtil.lookupDestination(context, address);
                Cursor cursor = null;
                try {
                    cursor = cursorQueryData.performSynchronousQuery();
                    if (cursor == null || cursor.getCount() == 0) {
                        displayName = null;
                    } else {
                        cursor.moveToFirst();
                        do {
                            int nameFieldColumnIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                            displayName = cursor.getString(nameFieldColumnIndex);
                            break;
                        } while (cursor.moveToNext());
                    }
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                if (displayName != null) {
                    reportString = String.format(report, displayName);
                } else {
                    reportString = String.format(report, address);
                }

                LogUtil.d(TAG, "Report message is: " + report);
                Intent notify = new Intent(NOTIFY_SHOW_MMS_SMS_REPORT_ACTION);
                notify.setClassName("com.android.messaging","com.android.messaging.receiver.MmsSmsEventReceiver"); //Bug950322
                notify.putExtra("report", reportString);
                context.sendBroadcast(notify);
                quitHandlerThread();
            }
        };

        Message msg = handler.obtainMessage();
        msg.sendToTarget();
    }

    private static void quitHandlerThread() {
        if (null != mGetDisplayNameHandlerThread) {
            mGetDisplayNameHandlerThread.quitSafely();
            mGetDisplayNameHandlerThread = null;
        }
    }
    /* Add by SPRD for bug 589352 end */

    public static Uri insertSendingMmsMessage(final Context context, final List<String> recipients,
            final MessageData content, final int subId, final String subPhoneNumber,
            final long timestamp) {
        final SendReq sendReq = createMmsSendReq(
                context, subId, recipients.toArray(new String[recipients.size()]), content,
                //DEFAULT_DELIVERY_REPORT_MODE,
                //DEFAULT_READ_REPORT_MODE,
                //489220 begin 
                isMmsDeliveryReportRequired(subId),
                isMmsReadReportRequired(subId),
                //489220 end 
                DEFAULT_EXPIRY_TIME_IN_SECONDS,
                DEFAULT_PRIORITY,
                timestamp);
        Uri messageUri = null;
        if (sendReq != null) {
            Uri outboxUri = null;
            if(MmsConfig.osSupportDelayedSending() && content.getAlarm()!=0){
                outboxUri = MmsUtils.insertAlarmSendReq(context, sendReq, subId, subPhoneNumber,content.getAlarm());
            }else{
                outboxUri = MmsUtils.insertSendReq(context, sendReq, subId, subPhoneNumber);
            }
            if (outboxUri != null) {
                messageUri = ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI,
                        ContentUris.parseId(outboxUri));
                if (LogUtil.isLoggable(TAG, LogUtil.DEBUG)) {
                    LogUtil.d(TAG, "Mmsutils: Inserted sending MMS message into telephony, uri: "
                            + outboxUri);
                }
            } else {
                LogUtil.e(TAG, "insertSendingMmsMessage: failed to persist message into telephony");
            }
        }
        return messageUri;
    }

    public static MessageData readSendingMmsMessage(final Uri messageUri,
            final String conversationId, final String participantId, final String selfId) {
        MessageData message = null;
        if (messageUri != null) {
            final DatabaseMessages.MmsMessage mms = MmsUtils.loadMms(messageUri);

            // Make sure that the message has not been deleted from the Telephony DB
            if (mms != null) {
                // Transform the message
                message = MmsUtils.createMmsMessage(mms, conversationId, participantId, selfId,
                        MessageData.BUGLE_STATUS_OUTGOING_RESENDING);
            }
        }
        return message;
    }

    /**
     * Create an MMS message with subject, text and image
     *
     * @return Both the M-Send.req and the M-Send.conf for processing in the caller
     * @throws MmsException
     */
    private static SendReq createMmsSendReq(final Context context, final int subId,
            final String[] recipients, final MessageData message,
            final boolean requireDeliveryReport, final boolean requireReadReport,
            final long expiryTime, final int priority, final long timestampMillis) {
        Assert.notNull(context);
        if (recipients == null || recipients.length < 1) {
            throw new IllegalArgumentException("MMS sendReq no recipient");
        }

        // Make a copy so we don't propagate changes to recipients to outside of this method
        final String[] recipientsCopy = new String[recipients.length];
        // Don't send phone number as is since some received phone number is malformed
        // for sending. We need to strip the separators.
        for (int i = 0; i < recipients.length; i++) {
            final String recipient = recipients[i];
            if (EmailAddress.isValidEmail(recipients[i])) {
                // Don't do stripping for emails
                recipientsCopy[i] = recipient;
            } else {
                recipientsCopy[i] = stripPhoneNumberSeparators(recipient);
            }
        }


        //feature fdn begin for Mms
//        SmsManager  smsManager = SmsManager.getDefault();
//        List<String> listContacts = new ArrayList<String>(recipientsCopy.length);
//        for(String contactnumber : recipients){
//            listContacts.add(contactnumber);
//        }
//        List<String> templist = new ArrayList<String>();
//        boolean[] sendFilter = smsManager.checkFdnContacts(listContacts);
//        for(int i = 0 ; i < sendFilter.length ; i++){
//             if(sendFilter[i]){
//                 templist.add(listContacts.get(i));
//             }
//        }
//        if(templist.size() > 0 ){
//            /* Delete by SPRD for bug 558251 2016/05/11 Start */
//            //String tempt = "";
//            //for (int i = 0; i < templist.size(); i++) {
//            //    tempt = tempt +"["+templist.get(i)+"]";
//            //}
//            /* Delete by SPRD for bug 558251 2016/05/11 End */
//            Message msgflag = GlobleUtil.mGlobleHandler.obtainMessage();
//            msgflag.what = GlobleUtil.FDN_TOAST_MSG;
//            //msgflag.obj = "Fdn limit for contacts :"+tempt;
//            msgflag.obj = context.getString(R.string.fdn_check_failure);
//            GlobleUtil.mGlobleHandler.sendMessage(msgflag);
//            //tempt = null;
//            return null;
//        /* Add by SPRD for bug 558251 2016/05/11 Start */
//        } else {
//            listContacts.clear();
//            listContacts.add("*99#");
//            sendFilter = smsManager.checkFdnContacts(listContacts);
//            if (sendFilter[0]) {
//                Message msgflag = GlobleUtil.mGlobleHandler.obtainMessage();
//                msgflag.what = GlobleUtil.FDN_TOAST_MSG;
//                msgflag.obj = context.getString(R.string.data_call_fdn_check_failure);
//                GlobleUtil.mGlobleHandler.sendMessage(msgflag);
//                return null;
//            }
//        }
//        listContacts = null;
//        sendFilter = null;
//        templist = null;
        /* Add by SPRD for bug 558251 2016/05/11 End */
        //feature fdn end for Mms

        SendReq sendReq = null;
        try {
            sendReq = createSendReq(context, subId, recipientsCopy,
                    message, requireDeliveryReport,
                    requireReadReport, expiryTime, priority, timestampMillis);
        } catch (final InvalidHeaderValueException e) {
            LogUtil.e(TAG, "InvalidHeaderValue creating sendReq PDU", e);
        } catch (final OutOfMemoryError e) {
            LogUtil.e(TAG, "Out of memory error creating sendReq PDU", e);
        }
        return sendReq;
    }

    /**
     * Stripping out the invalid characters in a phone number before sending
     * MMS. We only keep alphanumeric and '*', '#', '+'.
     */
    private static String stripPhoneNumberSeparators(final String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        final int len = phoneNumber.length();
        final StringBuilder ret = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            final char c = phoneNumber.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '+' || c == '*' || c == '#') {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    /**
     * Create M-Send.req for the MMS message to be sent.
     *
     * @return the M-Send.req
     * @throws InvalidHeaderValueException if there is any error in parsing the input
     */
    static SendReq createSendReq(final Context context, final int subId,
            final String[] recipients, final MessageData message,
            final boolean requireDeliveryReport,
            final boolean requireReadReport, final long expiryTime, final int priority,
            final long timestampMillis)
            throws InvalidHeaderValueException {
        final SendReq req = new SendReq();
        // From, per spec
        final String lineNumber = PhoneUtils.get(subId).getCanonicalForSelf(true/*allowOverride*/);
        if (!TextUtils.isEmpty(lineNumber)) {
            req.setFrom(new EncodedStringValue(lineNumber));
        }
        // To
        final EncodedStringValue[] encodedNumbers = EncodedStringValue.encodeStrings(recipients);
        if (encodedNumbers != null) {
            req.setTo(encodedNumbers);
            //req.setSubID(int subId);
        }
        // Subject
        if (!TextUtils.isEmpty(message.getMmsSubject())) {
            req.setSubject(new EncodedStringValue(message.getMmsSubject()));
        }
        // Date
        req.setDate(timestampMillis / 1000L);
        // Body


        String smil = null;
        final MmsInfo bodyInfo;
        for (Iterator<MessagePartData> it = message.getParts().iterator(); it.hasNext();) {
            MessagePartData part = it.next();
            if (ContentType.APP_SMIL.equals(part.getContentType())) {
                Log.e("smil-s", "MmsUtils.createSendReq: delete smil part from MessageData.");
                smil = part.getText();
                Log.e("smil-s", "MmsUtils.createSendReq: smil = "+smil);
                it.remove();
                break;
            }
        }
        if (!TextUtils.isEmpty(smil)) {
            bodyInfo = MmsUtils.makePduBodyEx(context, message, subId, smil);
        } else {
            bodyInfo = MmsUtils.makePduBody(context, message, subId);
        }


        req.setBody(bodyInfo.mPduBody);
        // Message size
        req.setMessageSize(bodyInfo.mMessageSize);
        // Message class
        req.setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.getBytes());
        // Expiry
        long expiry = getMmsValidity(expiryTime, subId);
        req.setExpiry(expiry);
        // Priority
        req.setPriority(priority);
        // Delivery report
        req.setDeliveryReport(requireDeliveryReport ? PduHeaders.VALUE_YES : PduHeaders.VALUE_NO);
        // Read report
        req.setReadReport(requireReadReport ? PduHeaders.VALUE_YES : PduHeaders.VALUE_NO);
        return req;
    }

    public static boolean isDeliveryReportRequired(final int subId) {
        if (!MmsConfig.get(subId).getSMSDeliveryReportsEnabled()) {
            return false;
        }
        final Context context = Factory.get().getApplicationContext();
        final Resources res = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String deliveryReportKey = res.getString(R.string.delivery_reports_pref_key);
        final boolean defaultValue = res.getBoolean(R.bool.delivery_reports_pref_default);
        return prefs.getBoolean(deliveryReportKey, defaultValue);
    }

    // 489220 begin
    public static boolean isMmsDeliveryReportRequired(final int subId) {
        final Context context = Factory.get().getApplicationContext();
        final Resources res = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String mmsDeliveryReportKey = res
                .getString(R.string.mms_delivery_reports_pref_key);
        final boolean defaultValue = res
                .getBoolean(R.bool.mms_delivery_reports_pref_default);
        return prefs.getBoolean(mmsDeliveryReportKey, defaultValue);
    }

    public static boolean isMmsReadReportRequired(final int subId) {
        final Context context = Factory.get().getApplicationContext();
        final Resources res = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String mmsReadReportKey = res
                .getString(R.string.mms_read_reports_pref_key);
        final boolean defaultValue = res
                .getBoolean(R.bool.mms_read_reports_pref_default);
        return prefs.getBoolean(mmsReadReportKey, defaultValue);
    }

    public static boolean isEnabelReturnMmsReadReport(final int subId) {

        final Context context = Factory.get().getApplicationContext();
        final Resources res = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String enableReturnMmsReadReportKey = res
                .getString(R.string.enable_return_mms_read_reports_pref_key);
        final boolean defaultValue = res
                .getBoolean(R.bool.enable_return_mms_read_reports_pref_default);
        return prefs.getBoolean(enableReturnMmsReadReportKey, defaultValue);
    }

    public static boolean isEnabelReturnMmsDeliveryReport(final int subId) {

        final Context context = Factory.get().getApplicationContext();
        final Resources res = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String enableReturnMmsDeliveryReportKey = res
                .getString(R.string.enable_return_mms_delivery_reports_pref_key);
        final boolean defaultValue = res
                .getBoolean(R.bool.enable_return_mms_delivery_reports_pref_default);
        return prefs.getBoolean(enableReturnMmsDeliveryReportKey, defaultValue);
    }
    // 489220 end

    public static boolean isEnabelMmsSendReport(final int subId) {

        final Context context = Factory.get().getApplicationContext();
        final Resources res = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String enableMmsSendReportKey = res.getString(R.string.mms_send_reports_pref_key);
        final boolean defaultValue = res.getBoolean(R.bool.mms_send_reports_pref_default);
        return prefs.getBoolean(enableMmsSendReportKey, defaultValue);
    }

    public static boolean isEnabelSmsSendReport(final int subId) {

        final Context context = Factory.get().getApplicationContext();
        final Resources res = context.getResources();
        final BuglePrefs prefs = BuglePrefs.getSubscriptionPrefs(subId);
        final String enableSmsSendReportKey = res.getString(R.string.send_reports_pref_key);
        final boolean defaultValue = res.getBoolean(R.bool.send_reports_pref_default);
        return prefs.getBoolean(enableSmsSendReportKey, defaultValue);
    }

    public static int sendSmsMessage(final String recipient, final String messageText,
            final Uri requestUri, final int subId,
            final String smsServiceCenter, final boolean requireDeliveryReport) {
        if(!Utils.isVowifiSmsEnable(subId)){
            if (!isSmsDataAvailable(subId)) {
            LogUtil.w(TAG, "MmsUtils: can't send SMS without radio");
            return MMS_REQUEST_MANUAL_RETRY;
            }
         }
        final Context context = Factory.get().getApplicationContext();
        int status = MMS_REQUEST_MANUAL_RETRY;
        try {
            // Send a single message
            final SendResult result = SmsSender.sendMessage(
                    context,
                    subId,
                    recipient,
                    messageText,
                    smsServiceCenter,
                    requireDeliveryReport,
                    requestUri);
            if (!result.hasPending()) {
                // not timed out, check failures
                final int failureLevel = result.getHighestFailureLevel();
                switch (failureLevel) {
                    case SendResult.FAILURE_LEVEL_NONE:
                        status = MMS_REQUEST_SUCCEEDED;
                        break;
                    case SendResult.FAILURE_LEVEL_TEMPORARY:
                        status = MMS_REQUEST_AUTO_RETRY;
                        LogUtil.e(TAG, "MmsUtils: SMS temporary failure");
                        break;
                    case SendResult.FAILURE_LEVEL_PERMANENT:
                        LogUtil.e(TAG, "MmsUtils: SMS permanent failure");
                        break;
                }
            } else {
                // Timed out
                LogUtil.e(TAG, "MmsUtils: sending SMS timed out");
            }
        // sprd: 596495 fdn feature begin
        }catch(IllegalArgumentException e){
            if (e.getMessage().contains("Invalid FDN destinationAddress")) {
                Log.i("xuexue", "GlobleUtil.mGropMessagingTotalCount--->   "+GlobleUtil.mGropMessagingTotalCount);
                // fdn feature toast end
                boolean showToat = false;
                if (GlobleUtil.mGropMessagingTotalCount > 1
                        || GlobleUtil.mGropMessagingTotalCount == 1) {
                    showToat = true;
                    GlobleUtil.mGropMessagingTotalCount = 0;
                }

                //if (showToat) {
                    Message msgflag = GlobleUtil.mGlobleHandler.obtainMessage();
                    msgflag.what = GlobleUtil.FDN_TOAST_MSG;
                    msgflag.obj = Factory.get().getApplicationContext()
                            .getString(R.string.fdn_check_failure);
                    GlobleUtil.mGlobleHandler.sendMessage(msgflag);
                //}
                status = SendResult.FAILURE_LEVEL_PERMANENT;
            }
          //sprd: 596495 fdn feature toast end
        }catch (SmsException e) {
            // sprd add for smsc
            if (SmsSender.SMSC_EMPTY_FAILURE.equals(e.getMessage())) {
                final boolean hasActiveSubscription =
                        SubscriptionManager.from(context).getActiveSubscriptionInfoCount() > 0;
                new Thread() {
                    @Override
                    public void run() {
                        Looper.prepare();
                        int errInfoId;
                        if (hasActiveSubscription) {
                            errInfoId = R.string.smsc_list_empty_toast;
                        } else {
                            errInfoId = R.string.err_sent_no_sim;
                        }
                        Toast.makeText(context, errInfoId, Toast.LENGTH_LONG).show();
                        Looper.loop();
                    }
                }.start();
                if (hasActiveSubscription) {
                    BugleNotifications.notifySmscEmpty(subId);
                }
            }
            // sprd add for smsc
        } catch (final Exception e) {
            LogUtil.e(TAG, "MmsUtils: failed to send SMS " + e, e);
        }
        return status;
    }

    /**
     * Delete SMS and MMS messages in a particular thread
     *
     * @return the number of messages deleted
     */

    private static String getMaxCondition(  final long  smax, final long mmax){
        if(smax >0 && mmax >0 ){
       return " maxSms="+smax+" & maxMms="+ mmax+" ";
        }
        else if(smax >0 )
        {
            return " maxSms="+smax+" ";
        }
        else if( mmax > 0 ){
            return  " maxMms="+ mmax +" ";
        }
        else{
            return  null;
        }
    }

    private static String  getThread( final long[]  threadId, final int nLimit, Integer nStart){
             if( threadId == null || threadId.length <=0){
                 return null;
             }
             int nStartPoint = nStart;
             int nStringLen = 0;
             String szRet = new String("");
             for(; nStartPoint <( nStart + nLimit); ++nStartPoint){
                 if(nStartPoint == nStart ){
                 szRet = szRet + " thread_id  in (" + threadId[nStartPoint] +",";
                 }else
                 {
                     szRet = szRet + " " + threadId[nStartPoint] +",";
                 }
             }
             nStart =  nStart + nLimit;
             nStringLen = szRet.length() -1;
             return szRet.substring(0, nStringLen);
    }
    private static final int MAX_LIMIT = 100;
    private static  String[] getThreadCondition(final long[]  threadId, final int nLimit ){
           if(threadId == null || threadId.length <=0 || threadId[0] == -1)  {
               return null;
           }
           int nRom = 0;
           int nIndex =0;
           int nLen = threadId.length;
           int nRetLen = nLen / nLimit +( (nLen %  nLimit) ==0 ? 0 :1);
           Integer nStart = new Integer(0);
           String szRet[] = new String[nRetLen];
           for(   nIndex =0; nIndex <nRetLen -1; ){
               szRet[nIndex++]= getThread(threadId, MAX_LIMIT, nStart);
               nStart = nStart + MAX_LIMIT;
           }
           nRom =  nLen % MAX_LIMIT;
           if( nRom != 0){
                 szRet[nIndex]= getThread(threadId, nRom, nStart);
           }
           if(nRetLen >= 1 && nRom == 0){
               szRet[nIndex]= getThread(threadId, MAX_LIMIT, nStart);
           }
           return szRet;
    }

    public static int deleteMultiThread(final long[]  threadId, final long cutOffTimestampInMillis,  final long  smax, final long mmax) {
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        LogUtil.i(TAG,"jessica enter deleteMultiThread ");
        String szSingleThreadCondition = "";
        String[] szThreadCondition = getThreadCondition(threadId, MAX_LIMIT);
        String    szMaxCondition = getMaxCondition(smax, mmax);
        String[] szMax = (szMaxCondition ==null)? null  :  new String[]{szMaxCondition};
        LogUtil.i(TAG,"jessica enter deleteMultiThread szMaxCondition = " + szMaxCondition);
        int szRet = -1;
        try{
         if(szThreadCondition == null ){
            szRet = resolver.delete( Telephony.Threads.CONTENT_URI,  "_id <= ?" ,   szMax);
         }else
         {
         for( String szCondition  : szThreadCondition){
             if( !TextUtils.isEmpty(szCondition)){
                 if(smax == 0 && mmax == 0){
                      szSingleThreadCondition = szCondition + ")" ;
                 }else
                 {
                     szSingleThreadCondition = szCondition +")  and _id <= ?" ;
                 }
                LogUtil.i(TAG,"jessica enter deleteMultiThread szSingleThreadCondition = " + szSingleThreadCondition);
                szRet = resolver.delete(Telephony.Threads.CONTENT_URI,  szSingleThreadCondition,  szMax);
             }
           }
         }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        LogUtil.i(TAG,"jessica enter deleteMultiThread szRet = " + szRet);
    return szRet;
    }

    /**
     * Delete SMS and MMS messages in a particular thread
     *
     * @return the number of messages deleted
     */
    public static int deleteThread(final long threadId, final long cutOffTimestampInMillis) {
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        final Uri threadUri = ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId);
        if (cutOffTimestampInMillis < Long.MAX_VALUE) {
            return resolver.delete(threadUri, Sms.DATE + "<=?",
                    new String[] { Long.toString(cutOffTimestampInMillis) });
        } else {
            return resolver.delete(threadUri, null /* smsSelection */, null /* selectionArgs */);
        }
    }

    /**
     * Delete single SMS and MMS message
     *
     * @return number of rows deleted (should be 1 or 0)
     */
    public static int deleteMessage(final Uri messageUri) {
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        return resolver.delete(messageUri, null /* selection */, null /* selectionArgs */);
    }

    public static byte[] createDebugNotificationInd(final String fileName) {
        byte[] pduData = null;
        try {
            final Context context = Factory.get().getApplicationContext();
            // Load the message file
            final byte[] data = DebugUtils.receiveFromDumpFile(fileName);
            final RetrieveConf retrieveConf = receiveFromDumpFile(data);
            // Create the notification
            final NotificationInd notification = new NotificationInd();
            final long expiry = System.currentTimeMillis() / 1000 + 600;
            notification.setTransactionId(fileName.getBytes());
            notification.setMmsVersion(retrieveConf.getMmsVersion());
            notification.setFrom(retrieveConf.getFrom());
            notification.setSubject(retrieveConf.getSubject());
            notification.setExpiry(expiry);
            notification.setMessageSize(data.length);
            notification.setMessageClass(retrieveConf.getMessageClass());

            final Uri.Builder builder = MediaScratchFileProvider.getUriBuilder();
            builder.appendPath(fileName);
            final Uri contentLocation = builder.build();
            notification.setContentLocation(contentLocation.toString().getBytes());

            // Serialize
            pduData = new PduComposer(context, notification).make();
            if (pduData == null || pduData.length < 1) {
                throw new IllegalArgumentException("Empty or zero length PDU data");
            }
        } catch (final MmsFailureException e) {
            // Nothing to do
        } catch (final InvalidHeaderValueException e) {
            // Nothing to do
        }
        return pduData;
    }

    public static int mapRawStatusToErrorResourceId(final int bugleStatus, final int rawStatus) {
        int stringResId = R.string.message_status_send_failed;
        switch (rawStatus) {
            case PduHeaders.RESPONSE_STATUS_ERROR_SERVICE_DENIED:
                stringResId = R.string.user_not_exist;
                break;
            case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_SERVICE_DENIED:
            //case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_REPLY_CHARGING_LIMITATIONS_NOT_MET:
            //case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_REPLY_CHARGING_REQUEST_NOT_ACCEPTED:
            //case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_REPLY_CHARGING_FORWARDING_DENIED:
            //case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_REPLY_CHARGING_NOT_SUPPORTED:
            //case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_ADDRESS_HIDING_NOT_SUPPORTED:
            //case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_LACK_OF_PREPAID:
                stringResId = R.string.mms_failure_outgoing_service;
                break;
            case PduHeaders.RESPONSE_STATUS_ERROR_SENDING_ADDRESS_UNRESOLVED:
            case PduHeaders.RESPONSE_STATUS_ERROR_TRANSIENT_SENDNG_ADDRESS_UNRESOLVED:
            case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_SENDING_ADDRESS_UNRESOLVED:
                stringResId = R.string.mms_failure_outgoing_address;
                break;
            case PduHeaders.RESPONSE_STATUS_ERROR_MESSAGE_FORMAT_CORRUPT:
            case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_MESSAGE_FORMAT_CORRUPT:
                stringResId = R.string.mms_failure_outgoing_corrupt;
                break;
            case PduHeaders.RESPONSE_STATUS_ERROR_CONTENT_NOT_ACCEPTED:
            case PduHeaders.RESPONSE_STATUS_ERROR_PERMANENT_CONTENT_NOT_ACCEPTED:
                stringResId = R.string.mms_failure_outgoing_content;
                break;
            case PduHeaders.RESPONSE_STATUS_ERROR_UNSUPPORTED_MESSAGE:
            //case PduHeaders.RESPONSE_STATUS_ERROR_MESSAGE_NOT_FOUND:
            //case PduHeaders.RESPONSE_STATUS_ERROR_TRANSIENT_MESSAGE_NOT_FOUND:
                stringResId = R.string.mms_failure_outgoing_unsupported;
                break;
            case MessageData.RAW_TELEPHONY_STATUS_MESSAGE_TOO_BIG:
                stringResId = R.string.mms_failure_outgoing_too_large;
                break;
        }
        return stringResId;
    }

    /**
     * The absence of a connection type.
     */
    public static final int TYPE_NONE = -1;

    public static int getConnectivityEventNetworkType(final Context context, final Intent intent) {
        final ConnectivityManager connMgr = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (OsUtil.isAtLeastJB_MR1()) {
            return intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, TYPE_NONE);
        } else {
            final NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                    ConnectivityManager.EXTRA_NETWORK_INFO);
            if (info != null) {
                return info.getType();
            }
        }
        return TYPE_NONE;
    }

    /**
     * Dump the raw MMS data into a file
     *
     * @param rawPdu The raw pdu data
     * @param pdu The parsed pdu, used to construct a dump file name
     */
    public static void dumpPdu(final byte[] rawPdu, final GenericPdu pdu) {
        if (rawPdu == null || rawPdu.length < 1) {
            return;
        }
        final String dumpFileName = MmsUtils.MMS_DUMP_PREFIX + getDumpFileId(pdu);
        final File dumpFile = DebugUtils.getDebugFile(dumpFileName, true);
        if (dumpFile != null) {
            try {
                final FileOutputStream fos = new FileOutputStream(dumpFile);
                final BufferedOutputStream bos = new BufferedOutputStream(fos);
                try {
                    bos.write(rawPdu);
                    bos.flush();
                } finally {
                    bos.close();
                    if (fos != null) {
                        try {
                            fos.close();
                        } catch (final IOException e) {
                        // no action we can take here
                        }
                    }
                }
                DebugUtils.ensureReadable(dumpFile);
            } catch (final IOException e) {
                LogUtil.e(TAG, "dumpPdu: " + e, e);
            }
        }
    }

    /**
     * Get the dump file id based on the parsed PDU
     * 1. Use message id if not empty
     * 2. Use transaction id if message id is empty
     * 3. If all above is empty, use random UUID
     *
     * @param pdu the parsed PDU
     * @return the id of the dump file
     */
    private static String getDumpFileId(final GenericPdu pdu) {
        String fileId = null;
        if (pdu != null && pdu instanceof RetrieveConf) {
            final RetrieveConf retrieveConf = (RetrieveConf) pdu;
            if (retrieveConf.getMessageId() != null) {
                fileId = new String(retrieveConf.getMessageId());
            } else if (retrieveConf.getTransactionId() != null) {
                fileId = new String(retrieveConf.getTransactionId());
            }
        }
        if (TextUtils.isEmpty(fileId)) {
            fileId = UUID.randomUUID().toString();
        }
        return fileId;
    }

    private static long getMmsValidity(long expiryTime, int subId) {
        long expiry = expiryTime;
        String expiryStr = null;
        final Context context = Factory.get().getApplicationContext();
        expiryStr = Settings.Global.getString(context.getContentResolver(), "persist.radio.mmstime" + subId);
        LogUtil.i(TAG, "getMmsValidity()--->>      validity = " + expiryStr + ", subId = "
                + subId);
        try {
            expiry = Long.parseLong(expiryStr);
        } catch (NumberFormatException exception) {
            LogUtil.e(TAG, "getMmsValidity  Exception : " + exception);
        }
        return expiry;
    }

    //489223 begin
    public static void copySmsMessageToSim(List<String> smsUriList, int subId) {
        final Context context = Factory.get().getApplicationContext();
        ContentResolver resolver = context.getContentResolver();
        final ContentValues values = new ContentValues(1);
        values.put("sub_id", String.valueOf(subId));
        for (String smsUri : smsUriList) {
            Pattern pattern = Pattern.compile("inbox/+");
            Matcher matcher = pattern.matcher(smsUri.toString());
            String uriString = matcher.replaceAll("");
            SqliteWrapper.insert(context, resolver, Uri.parse(uriString), values);
        }
    }

    public static int deleteSimSmsMessage(final Uri messageUri) {
        final ContentResolver resolver = Factory.get().getApplicationContext().getContentResolver();
        return resolver.delete(messageUri, null /* selection */, null /* selectionArgs */);
    }
    //489223 end

    // sprd #542214 start
    public static int tanslatePhoneIdToSubId(Context context, int phoneId) {
        if (!(phoneId >= 0 && phoneId < TelephonyManager.getDefault()
                .getSimCount())) {
            Log.d(TAG, "this phoneId:" + phoneId + " is invalid.");
            return -1;
        }
        int[] subId = null;
        try {
            ISub iSub = ISub.Stub
                    .asInterface(ServiceManager.getService("isub"));
            if (iSub != null) {
                subId = iSub.getSubId(phoneId);
            }
        } catch (RemoteException ex) {
//            // ignore it
        }

        return subId[0];
    }

    public static int tanslateSubIdToPhoneId(Context context, int subId) {
        int phoneId = SystemAdapter.getInstance().getPhoneId(subId);
        return phoneId;
    }
    // sprd #542214 end
    // add for bug 551694 begin
    public static boolean isSubjectNeedUtf8() {
        Locale l = Locale.getDefault();
        String language = l.getLanguage();
        String country = l.getCountry().toLowerCase();
        boolean bool = "zh".equalsIgnoreCase(language)
                || "en".equalsIgnoreCase(language);
        System.out.println("isSubjectNeedUtf8 = [" + bool + "] language = ["
                + language + "], country = [" + country + "]");
        return bool;
    }

    public static String toUtf8String(String str) {
        if (str == null) {
            System.out.println("enter toutf8String, str is null");
            return "";
        }
        String strTemp = str;
        try {
            if (str.equals(new String(strTemp.getBytes("iso-8859-1"),
                    "iso-8859-1"))) {
                System.out.println("str is iso-8859-1,will decode to UTF-8");
                return new String(str.getBytes("iso-8859-1"), "UTF-8");
            } else if (str.equals(new String(str.getBytes("UTF-8"), "UTF-8"))) {
                System.out.println("the str is utf8");
                return str;
            }
            System.out.println("the str is other encode");
            return str;
        } catch (final Exception e) {
            // Impossible to reach here!
            Log.e(TAG, "UTF_8 must be supported!", e);
            return "";
        }
    }
    // add for bug 551694 end
    //sprd 581016  start
    public static Bundle pduBobyToBundle(PduBody body){
        Bundle bundle = new Bundle();
        if (body == null){
            return bundle;
        }
        int partNum = body.getPartsNum();
        bundle.putInt("partNum", partNum);
        for(int j=0; j<partNum; j++){
            Log.d("smil_show","\r\n<<<=========" +
                    "======"+j + "  Start ============================>>>");
            PduPart part = body.getPart(j);
            bundle.putInt("Charset"+j,part.getCharset());
            bundle.putByteArray("ContentLocation"+j, part.getContentLocation());
            bundle.putByteArray("ContentType"+j, part.getContentType());
            bundle.putByteArray("ContentDisposition"+j, part.getContentDisposition());
            bundle.putByteArray("ContentTransferEncoding"+j, part.getContentTransferEncoding());
            bundle.putByteArray("Name"+j, part.getName());
            bundle.putByteArray("Filename"+j, part.getFilename());
            bundle.putByteArray("Data"+j, part.getData());
            bundle.putByteArray("ContentId"+j, part.getContentId());
            Uri uri = part.getDataUri();
            String sztype = new String(part.getContentType());
            if (uri != null){
                bundle.putString("DataUri"+j, uri.toString());
            }else if(sztype != null && sztype.startsWith("image")){
                Log.d("smil_show",  "["+j +"]th  Uri is empty because  Resize");
                bundle.putString("DataUri"+j, "context://resize/image");
            }
            else if(sztype != null && sztype.startsWith("text/plain")){
                Log.d("smil_show",  "["+j +"]th  Uri is empty because  Text View ");
                bundle.putString("DataUri"+j, "context://text/plain/empty");
            }else if(sztype != null && sztype.startsWith("application/smil")){
                Log.d("smil_show", "smil ["+new String( part.getData())+"]");
            }
            else{
                Log.d("smil_show",  "["+j +"]th  Uri is empty");
                bundle.putString("DataUri"+j, "");
            }
            Log.d("smil_show", "["+j +"]th Mime Type is ["+sztype+"]");
            Log.d("smil_show", "["+j +"]th uri is ["+ part.getDataUri()+"]");
            Log.d("smil_show","\r\n<<<=============="+j + " End ============================>>>");
        }
        return bundle;
    }
    //sprd 581016 end
    // add for bug 616653  start
    public static long getCursorLongValue(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        if (index == -1) return -1;
        final String value = cursor.getString(index);
        if (value == null) return -1;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
    // add for bug 616653  end
    // add for bug 620391  start
    public static long getTotalLength(Uri uri,final Context context){
        Cursor cursor = null;
        long totalLength=0;
        String SIZE = "_size";
        try {
            cursor = context.getContentResolver().query(uri, null,
                    null, null, null);
            if (cursor != null&& cursor.moveToFirst()) {
                totalLength=getCursorLongValue(cursor, SIZE);
            }
        } catch (final Exception exception){
            LogUtil.e(TAG,"uri com.android.externalstorage.documents exception="+exception, exception);
        }finally {
            if (cursor != null){
                cursor.close();
            }
            return totalLength;
        }

    }
    // add for bug 620391  end
    //sprd 623241 start
    public static void setImageWidthHeight(MessagePartData data,Uri uri){
        final Context context = Factory.get().getApplicationContext();
        final ContentResolver cr = context.getContentResolver();
        BitmapFactory.Options options = new BitmapFactory.Options();
        InputStream inputStream = null;
        // Find incoming image size
        try {
            options.inJustDecodeBounds = true;
            inputStream = cr.openInputStream(uri);
            BitmapFactory.decodeStream(inputStream, null, options);
            data.setWith(options.outWidth);
            data.setHeight(options.outHeight);
            options.inJustDecodeBounds = false;
        } catch (final FileNotFoundException e) {
            LogUtil.e(TAG, "Could not open file corresponding to uri " + uri, e);
        } catch (final NullPointerException e) {
            LogUtil.e(TAG, "NPE trying to open the uri " + uri, e);
        } catch (final SecurityException e) {
            LogUtil.e(TAG, "SecurityException trying to open the uri " + uri, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (final IOException e) {
                    // Nothing to do
                }
            }
        }
    }
    //sprd 623241 end
    //bug 633234 : six queues begin
	public static ArrayList<Integer> getSubIds() {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(Factory.get().getApplicationContext());
        if(subscriptionManager == null){
            Log.d(TAG, "subscriptionManager == null");
            return null;
        }
        List<SubscriptionInfo> subInfoList = subscriptionManager.getActiveSubscriptionInfoList();
        if(subInfoList == null){
            Log.d(TAG, "subInfoList == null");
            return null;
        }
        Iterator<SubscriptionInfo> iterator = subInfoList.iterator();
        if (iterator == null) {
            Log.d(TAG, "iterator == null");
            return null;
        }

        ArrayList<Integer> arrayList = new ArrayList<Integer>(
                subInfoList.size());
        while (iterator.hasNext()) {
            SubscriptionInfo subInfo = iterator.next();
            int phoneId = subInfo.getSimSlotIndex();
            arrayList.add(subInfo.getSubscriptionId());
        }
        return arrayList;
    }
	public static int getSlotId(int subId) {
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(Factory.get().getApplicationContext());
        if(subscriptionManager == null){
            Log.d(TAG, "subscriptionManager == null");
            return -1;
        }
        //bug 1169107 begin
        if(subscriptionManager.getActiveSubscriptionInfo(subId) == null){
            Log.d(TAG, "subscriptionManager.getActiveSubscriptionInfo(subId) == null");
            return -1;
        }
        return subscriptionManager.getActiveSubscriptionInfo(subId).getSimSlotIndex();
        //bug 1169107 end
    }
    //bug 633234 : six queues end

	//613227
    public static void setAlarmForMessage(long alarmTime){
        final Context context=Factory.get().getApplicationContext();
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return;
        }
        AlarmManager alarmManager=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent intent=new Intent(context,MmsSmsEventReceiver.class);
        intent.setAction(ACTION_SERVICE_ALARM_MESSAGE);
        intent.putExtra("AlarmMessageTime", alarmTime);
        PendingIntent pendingIntent=PendingIntent.getBroadcast(context,(int)alarmTime/1000, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "setAlarmForMessage setExact alarmTime:["+alarmTime+"] alarm_count:["+((int)alarmTime/1000)+"]");
    }

    public static boolean checkTimeAndNow(long alarmTime){
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return false;
        }
        Calendar alarmMessageDate = new GregorianCalendar();
        alarmMessageDate.setTimeInMillis(alarmTime);
        Calendar nowDate = new GregorianCalendar();
        long now = System.currentTimeMillis();
        nowDate.setTimeInMillis(now);

        if (nowDate.before(alarmMessageDate)) {
            return true;
        }else{
            return false;
        }
    }

    private static boolean checkTimeIfNeedTurnOnAlarm(long alarmTime){
        if(alarmTime == 0){
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "checkTimeIfNeedTurnOnAlarm alarmTime can't be 0");
            return false;
        }
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return false;
        }
        Calendar alarmMessageDate = new GregorianCalendar();
        alarmMessageDate.setTimeInMillis(alarmTime);
        Calendar nowDate = new GregorianCalendar();
        long now = System.currentTimeMillis();
        nowDate.setTimeInMillis(now);

	    if (nowDate.before(alarmMessageDate)) {
	        setAlarmForMessage(alarmTime);
			return true;
	    }
		return false;
	}

        // This must match the column IDs below.
    private static final String[] ALARM_PROJECTION = new String[] {
            Sms._ID,        //0
            Sms.SUBSCRIPTION_ID
    };
    private static final String[] ALARM_PROJECTION_ALARM = new String[] {
            Sms._ID,        //0
            Sms.SUBSCRIPTION_ID,
            SmsEx.ALARM,
    };
	// This must match the column IDs below.

    // This must match ALARM_PROJECTION.
    private static final int SEND_COLUMN_ID         		= 0;
    private static final int SEND_COLUMN_SUBSCRIPTION_ID  	= 1;
    private static final int SEND_COLUMN_ALARM  		= 2;
    // This must match ALARM_PROJECTION.
    private static final String[] MMS_ALARMBOX_PROJECTION = {
            Mms._ID, // 0
            Mms.THREAD_ID,
            Mms.MESSAGE_ID,
            Mms.SUBSCRIPTION_ID
    };

    private static final String[] MMS_ALARMBOX_PROJECTION_ALARM = {
            Mms._ID, // 0
            Mms.THREAD_ID,
            Mms.MESSAGE_ID,
            Mms.SUBSCRIPTION_ID,
            MmsEx.ALARM,
    };

    // This must match MMS_ALARMBOX_PROJECTION.
    private static final int MMS_SEND_COLUMN_ID         		= 0;
    private static final int MMS_SEND_COLUMN_THREAD_ID      	= 1;
    private static final int MMS_SEND_COLUMN_MESSAGE_ID  		= 2;
    private static final int MMS_SEND_COLUMN_SUBSCRIPTION_ID  	= 3;
    private static final int MMS_SEND_COLUMN_ALARM = 4;
    // This must match MMS_ALARMBOX_PROJECTION.

    public static void reSetAllAlarmMmsMessage(){
        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmMmsMessage");
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return;
        }
        final Context context=Factory.get().getApplicationContext();
        ContentResolver resolver = context.getContentResolver();
        long alarmTime = 0;
        Cursor c = null;
        try {
            c = SqliteWrapper.query(context, resolver,
                    MmsEx.Alarmbox.CONTENT_URI, MMS_ALARMBOX_PROJECTION_ALARM, null, null,
                    null);
            if (c != null) {
                if (c.moveToFirst()) {
                    do {
                        alarmTime = c.getLong(MMS_SEND_COLUMN_ALARM);
                        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmMmsMessage alarmTime:[" + alarmTime + "]");
                        checkTimeIfNeedTurnOnAlarm(alarmTime);
                    } while (c.moveToNext());
                } else {
                    LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmMmsMessage no alarm message");
                }
            } else {
                LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmMmsMessage c is null");
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "reSetAllAlarmMmsMessage failure: " + e, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public static void reSetAllAlarmSmsMessage(){
        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmSmsMessage");
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return;
        }
        final Uri uri = Uri.parse("content://sms/alarm");
        final Context context=Factory.get().getApplicationContext();
        ContentResolver resolver = context.getContentResolver();
        String selection = Sms.TYPE + " = " + SmsEx.MESSAGE_TYPE_ALARM;
        int subId = -1;
        long alarmTime = 0;
        Cursor c = null;
        try {
            c = SqliteWrapper.query(context, resolver, uri,
                    ALARM_PROJECTION_ALARM, selection, null, "date ASC");
            if (c != null) {
                if (c.moveToFirst()) {
                    do {
                        alarmTime = c.getLong(SEND_COLUMN_ALARM);
                        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmSmsMessage alarmTime:[" + alarmTime + "]");
                        if (!checkTimeIfNeedTurnOnAlarm(alarmTime)) {
                            int msgId = c.getInt(SEND_COLUMN_ID);
                            Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
                            ContentValues values = new ContentValues(1);
                            values.put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED);
                            SqliteWrapper.update(context, context.getContentResolver(), msgUri, values, null, null);
                        }
                    } while (c.moveToNext());
                } else {
                    LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmSmsMessage no alarm message");
                }
            } else {
                LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "reSetAllAlarmSmsMessage c is null");
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "reSetAllAlarmMmsMessage failure: " + e, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public static void reSetAllAlarmMessage(){
        LogUtil.d(LogUtil.BUGLE_ALARM_TAG,"reSetAllAlarmMessage");
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return;
        }
        reSetAllAlarmSmsMessage();
        reSetAllAlarmMmsMessage();
    }
    public static void moveAlarmMessageToQueued(long alarmTime){
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return;
        }
        moveAlarmSmsMessageToQueued(alarmTime);
        moveAlarmMmsMessageToQueued(alarmTime);
    }
    private static void moveAlarmMmsMessageToQueued(long alarmTime){
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return;
        }
        String selection = Mms.MESSAGE_BOX+" = "+ MmsEx.MESSAGE_BOX_ALARM + " and " + MmsEx.ALARM+" = " + alarmTime;
        final Context context=Factory.get().getApplicationContext();
        ContentResolver resolver = context.getContentResolver();
        PduPersister p = PduPersister.getPduPersister(context);
        Uri mmsUri = Mms.CONTENT_URI;
        Cursor c = null;
        try {
            c = SqliteWrapper.query(context, resolver,
                    MmsEx.Alarmbox.CONTENT_URI, MMS_ALARMBOX_PROJECTION_ALARM, selection, null,
                    null);
            if (c != null) {
                if (c.moveToFirst()) {
                    do {
                        int cId = c.getInt(MMS_SEND_COLUMN_ID);
                        int messageId = c.getInt(MMS_SEND_COLUMN_MESSAGE_ID);
                        int subId = c.getInt(MMS_SEND_COLUMN_SUBSCRIPTION_ID);
                        int threadId = c.getInt(MMS_SEND_COLUMN_THREAD_ID);
                        mmsUri = Uri.parse(mmsUri + "/" + cId);
                        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "move mmsUri [" + mmsUri + "] to Mms.Outbox ");
                        try {
                            p.move(mmsUri, Mms.Outbox.CONTENT_URI);
                        } catch (MmsException e) {
                            LogUtil.e(LogUtil.BUGLE_ALARM_TAG, "Failed to move message to outbox and mark as error: "
                                    + mmsUri, e);
                        }
                        LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "moveAlarmMmsMessageToQueued mmsUri:[" + mmsUri + "] messageId:[" + messageId + "] subId:[" + subId + "] threadId:[" + threadId + "]");
                        ContentValues values = new ContentValues(7);
                        values.put(PendingMessages.PROTO_TYPE, MmsSms.MMS_PROTO);
                        values.put(PendingMessages.MSG_ID, messageId);
                        values.put(PendingMessages.MSG_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ);
                        values.put(PendingMessages.ERROR_TYPE, 0);
                        values.put(PendingMessages.ERROR_CODE, 0);
                        values.put(PendingMessages.RETRY_INDEX, 0);
                        values.put(PendingMessages.DUE_TIME, 0);
                        values.put(PendingMessages.SUBSCRIPTION_ID, subId);
                        SqliteWrapper.insert(context, resolver, PendingMessages.CONTENT_URI, values);
                    } while (c.moveToNext());
                }
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "reSetAllAlarmMmsMessage failure: " + e, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
    private static void moveAlarmSmsMessageToQueued(long alarmTime){
        if (!MmsConfig.osSupportDelayedSending()) {
            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "Os don't support delayed send messag...");
            return;
        }
        // get all the queued messages from the database
        final Uri uri = Uri.parse("content://sms/alarm");
        String selection = Sms.TYPE + " = " + SmsEx.MESSAGE_TYPE_ALARM + " and " + SmsEx.ALARM + " = " + alarmTime;

        final Context context=Factory.get().getApplicationContext();
        int subId = -1;
        LogUtil.d(LogUtil.BUGLE_ALARM_TAG,"moveAlarmSmsMessageToQueued selection:["+selection);
        Cursor c = null;
        try {
            c = SqliteWrapper.query(context, context.getContentResolver(), uri,
                    ALARM_PROJECTION_ALARM, selection, null, "date ASC");
            if (c != null) {
                if (c.moveToFirst()) {
                    do {
                        int msgId = c.getInt(SEND_COLUMN_ID);
                        subId = c.getInt(SEND_COLUMN_SUBSCRIPTION_ID);
                        long date_sent = c.getLong(SEND_COLUMN_ALARM);
                        Uri msgUri = ContentUris.withAppendedId(Sms.CONTENT_URI, msgId);
                        ContentValues values = new ContentValues(1);
                        values.put(Sms.DATE, date_sent);
                        SqliteWrapper.update(context, context.getContentResolver(), msgUri, values, null, null);

                        boolean moved = Sms.moveMessageToFolder(context, msgUri, Sms.MESSAGE_TYPE_QUEUED, 0);
                        if (!moved) {
                            LogUtil.d(LogUtil.BUGLE_ALARM_TAG, "SmsMessageSender.sendMessage: couldn't move message " +
                                    "to queued: " + msgUri);
                        }
                    } while (c.moveToNext());
                }
            }
        } catch (final SQLiteException e) {
            LogUtil.e(TAG, "reSetAllAlarmMmsMessage failure: " + e, e);
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}
