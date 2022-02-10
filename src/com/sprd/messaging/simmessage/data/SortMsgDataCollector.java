//by sprd
package com.sprd.messaging.simmessage.data;

import android.net.Uri;

public class SortMsgDataCollector {
    public static final String AUTHORITY = "com.android.messaging.datamodel.MessagingContentProvider";
    private static final String CONTENT_AUTHORITY = "content://" + AUTHORITY + '/';
    private static final String SIM_MESSAGE_LIST_VIEW_QUERY = "sim_message_list_view_query";
    private static final String CLEAR_SIM_MESSAGES = "clear_sim_messages";
    public static final Uri SIM_MESSAGE_LIST_VIEW_URI = Uri.parse(CONTENT_AUTHORITY
            + SIM_MESSAGE_LIST_VIEW_QUERY);
    public static final Uri CLEAR_SIM_MESSAGES_URI = Uri.parse(CONTENT_AUTHORITY
            + CLEAR_SIM_MESSAGES);
    public static final String DISPLAY_DESTINATION = "display_destination";
    public static final String RECEIVED_TIMESTAMP = "received_timestamp";
    public static final String MESSAGE_READ = "read";

    public static final String KEY_ORDER = "order_position";
    public static final int LOADER_ID_DEFAULT = 100;
    public static final String ORDER_BY_PHONE_NUMBER_DESC = DISPLAY_DESTINATION + " DESC";
    public static final String ORDER_BY_PHONE_NUMBER_ASC = DISPLAY_DESTINATION + " ASC";
    public static final String ORDER_BY_TIME_DESC = RECEIVED_TIMESTAMP + " DESC";
    public static final String ORDER_BY_TIME_ASC = RECEIVED_TIMESTAMP + " ASC";

    public static String getMsgOrderKey() {
        return KEY_ORDER;
    }

    // bug 489223: add for Custom XML SQL Query begin
    public static final int MSG_UNKNOW = -1;
    public static final int MSG_BOX_INBOX = 0;
    public static final int MSG_BOX_SENT = 1;
    public static final int MSG_BOX_OUTBOX = 2;
    public static final int MSG_BOX_DRAFT = 3;

    public static String getOrderByPhoneNumberDesc() {
        return ORDER_BY_PHONE_NUMBER_DESC;
    }

    public static String getOrderByPhoneNumberAsc() {
        return ORDER_BY_PHONE_NUMBER_ASC;
    }

    public static String getOrderByReceivedTimeDesc() {
        return ORDER_BY_TIME_DESC;
    }

    public static String getOrderByReceivedTimeAsc() {
        return ORDER_BY_TIME_ASC;
    }

    public static int getSortTypeByStatus(int status) {
        switch (status) {
            // Received:
            case BUGLE_STATUS_INCOMING_COMPLETE:
            case BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD:
            case BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD:
            case BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING:
            case BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD:
            case BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING:
            case BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED:
            case BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE:
                return MSG_BOX_INBOX;
            // Sent:
            case BUGLE_STATUS_OUTGOING_COMPLETE:
            case BUGLE_STATUS_OUTGOING_DELIVERED:
                return MSG_BOX_SENT;
            // Sending & Fail:
            case BUGLE_STATUS_OUTGOING_YET_TO_SEND:
            case BUGLE_STATUS_OUTGOING_SENDING:
            case BUGLE_STATUS_OUTGOING_RESENDING:
            case BUGLE_STATUS_OUTGOING_AWAITING_RETRY:
            case BUGLE_STATUS_OUTGOING_FAILED:
            case BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER:
                return MSG_BOX_OUTBOX;
            // Draft:
            case BUGLE_STATUS_OUTGOING_DRAFT:
                return MSG_BOX_DRAFT;
            default:
                return MSG_UNKNOW;
        }
    }

    // Outgoing
    public static final int BUGLE_STATUS_OUTGOING_COMPLETE = 1;
    public static final int BUGLE_STATUS_OUTGOING_DELIVERED = 2;
    public static final int BUGLE_STATUS_OUTGOING_DRAFT = 3;
    public static final int BUGLE_STATUS_OUTGOING_YET_TO_SEND = 4;
    public static final int BUGLE_STATUS_OUTGOING_SENDING = 5;
    public static final int BUGLE_STATUS_OUTGOING_RESENDING = 6;
    public static final int BUGLE_STATUS_OUTGOING_AWAITING_RETRY = 7;
    public static final int BUGLE_STATUS_OUTGOING_FAILED = 8;
    public static final int BUGLE_STATUS_OUTGOING_FAILED_EMERGENCY_NUMBER = 9;
    // Incoming
    public static final int BUGLE_STATUS_INCOMING_COMPLETE = 100;
    public static final int BUGLE_STATUS_INCOMING_YET_TO_MANUAL_DOWNLOAD = 101;
    public static final int BUGLE_STATUS_INCOMING_RETRYING_MANUAL_DOWNLOAD = 102;
    public static final int BUGLE_STATUS_INCOMING_MANUAL_DOWNLOADING = 103;
    public static final int BUGLE_STATUS_INCOMING_RETRYING_AUTO_DOWNLOAD = 104;
    public static final int BUGLE_STATUS_INCOMING_AUTO_DOWNLOADING = 105;
    public static final int BUGLE_STATUS_INCOMING_DOWNLOAD_FAILED = 106;
    public static final int BUGLE_STATUS_INCOMING_EXPIRED_OR_NOT_AVAILABLE = 107;
    // All incoming messages expect to have status >=
    // BUGLE_STATUS_FIRST_INCOMING
    public static final int BUGLE_STATUS_FIRST_INCOMING = BUGLE_STATUS_INCOMING_COMPLETE;

    public static boolean getIsIncoming(int status) {
        return (status >= BUGLE_STATUS_FIRST_INCOMING);
    }

    public static final int MESSAGE_ID = 0;
    public static final int CONVERSATION_ID = MESSAGE_ID + 1;
    public static final int SENDER_ID = CONVERSATION_ID + 1;
    public static final int RECEIVED_TIMESTAMP_INDEX = SENDER_ID + 1;
    public static final int MESSAGE_STATUS = RECEIVED_TIMESTAMP_INDEX + 1;
    public static final int READ = MESSAGE_STATUS + 1;
    public static final int SMS_MESSAGE_URI = READ + 1;
    public static final int RAW_STATUS = SMS_MESSAGE_URI + 1;
    public static final int SELF_ID = RAW_STATUS + 1;
    public static final int NAME = SELF_ID + 1;
    public static final int SUB_ID = NAME + 1;
    public static final int SIM_SLOT_ID = SUB_ID + 1;
    public static final int SUBSCRIPTION_NAME = SIM_SLOT_ID + 1;
    public static final int SUBSCRIPTION_COLOR = SUBSCRIPTION_NAME + 1;
    public static final int TEXT = SUBSCRIPTION_COLOR + 1;
    public static final int CONTENT_TYPE = TEXT + 1;
    public static final int DISPLAY_DESTINATION_INDEX = CONTENT_TYPE + 1;
    public static final int FULL_NAME = DISPLAY_DESTINATION_INDEX + 1;


    public static String[] getSimMessageListViewProjection() {
        return new String[]{
                "_id",
                "conversation_id",
                "sender_id",
                "received_timestamp",
                "message_status",
                "read",
                "sms_message_uri",
                "raw_status",
                "self_id",
                "name",
                "sub_id",
                "sim_slot_id",
                "subscription_name",
                "subscription_color",
                "text",
                "content_type",
                "display_destination",
                "full_name",
        };
    }

    /**
     * sim process
     */
    // We always use -1 as default/invalid sub id although system may give us anything negative
    public static final int DEFAULT_SELF_SUB_ID = -1;
    // Active slot ids are non-negative. Using -1 to designate to inactive self participants.
    public static final int INVALID_SLOT_ID = -1;

    public static final String SHOW_SIM_MESSAGE_BY_SUB_ID = "show_message_on_which_sim";
    public static final int SHOW_ALL_MESSAGE = 0;
}