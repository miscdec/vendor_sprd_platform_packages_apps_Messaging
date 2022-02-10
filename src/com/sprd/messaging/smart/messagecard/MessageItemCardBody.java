package com.sprd.messaging.smart.messagecard;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.unicom.callme.outerentity.CardInfo;
import com.unicom.callme.outerentity.CardShowContent;
import com.unicom.callme.outerentity.SmsCardModel;
import com.unicom.callme.utils.LogHelper;

import java.util.List;
import com.android.messaging.R;
import com.android.messaging.ui.conversation.ScreenUtils;

public class MessageItemCardBody{


    private static final String TAG = "MessageItemCardBody";


    public MessageItemCardBody() {
    }

    public View buildBodyView(Context context, CardInfo cardInfo) {
        View view;

        switch (cardInfo.getModelNumber()) {
            case SmsCardModel.SMS_CARD_DEFAULT:
                view = normalView(context, cardInfo);
                break;
            case SmsCardModel.SMS_CARD_PLANE_TICKET:
                // 飞机
                view = planeView(context, cardInfo);
                break;

            case SmsCardModel.SMS_CARD_TRAIN_TICKETS:
                // 火车
                view = trainView(context, cardInfo);
                break;

            default:
                view = normalView(context, cardInfo);
                break;
        }

        return view;
    }

    /**
     * 普通
     */
    private View normalView(Context context, CardInfo cardInfo) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        List<CardShowContent> keyWordsValues = cardInfo.getKeyWordsValues();
        int padding = ScreenUtils.dp2px(context, 10);

        // 普通
        for (CardShowContent item : keyWordsValues) {

            LogHelper.d(TAG, "title=" + item.getKey() + ",value=" + item.getValue());

            if ("no-parse-title".equals(item.getKey())) {
                continue;
            }

            LinearLayout itemView = (LinearLayout) View.inflate(context, R.layout.cm_card_body_item_view, null);
            linearLayout.setPadding(padding, padding, padding, padding);

            TextView mSmsBodyKey = (TextView) itemView.findViewById(R.id.cm_card_body_item_view_key);
            TextView mSmsBodyValue = (TextView) itemView.findViewById(R.id.cm_card_body_item_view_value);

            mSmsBodyKey.setText(item.getKey());
            mSmsBodyValue.setText(item.getValue());

            linearLayout.addView(itemView);
        }

        return linearLayout;
    }

    /**
     * 飞机票
     */
    private View planeView(Context context, CardInfo cardInfo) {
        LogHelper.d(TAG, cardInfo.toString());

        View view = View.inflate(context, R.layout.cm_sms_card_body_plane_ticket, null);

        TextView tvDeparturePlace = (TextView) view.findViewById(R.id.sms_card_body_plane_departure_place);
        TextView tvDepartureTime = (TextView) view.findViewById(R.id.sms_card_body_plane_departure_time);
        TextView tvDepartureDate = (TextView) view.findViewById(R.id.sms_card_body_plane_departure_date);

        TextView tvDestinationPlace = (TextView) view.findViewById(R.id.sms_card_body_plane_destination_place);
        TextView tvDestinationTime = (TextView) view.findViewById(R.id.sms_card_body_plane_destination_time);
        TextView tvDestinationDate = (TextView) view.findViewById(R.id.sms_card_body_plane_destination_date);

        TextView tvFlightNum = (TextView) view.findViewById(R.id.sms_card_body_plane_flight_number);

        List<CardShowContent> keyWordsValues = cardInfo.getKeyWordsValues();
        for (CardShowContent kv : keyWordsValues) {

            if ("起飞地点".equals(kv.getKey())) {
                tvDeparturePlace.setText(kv.getValue());
            } else if ("起飞时间".equals(kv.getKey())) {
                tvDepartureTime.setText(kv.getValue());
            } else if ("起飞日期".equals(kv.getKey())) {
                tvDepartureDate.setText(kv.getValue());
            } else if ("到达地点".equals(kv.getKey())) {
                tvDestinationPlace.setText(kv.getValue());
            } else if ("到达时间".equals(kv.getKey())) {
                tvDestinationTime.setText(kv.getValue());
            } else if ("到达日期".equals(kv.getKey())) {
                tvDestinationDate.setText(kv.getValue());
            } else if ("航班号".equals(kv.getKey())) {
                tvFlightNum.setText(kv.getValue());
            }

        }

        return view;
    }

    /**
     * 火车票
     */
    private View trainView(Context context, CardInfo cardInfo) {
        View view = View.inflate(context, R.layout.cm_sms_card_body_train_tickets, null);

        TextView tvDeparturePlace = (TextView) view.findViewById(R.id.cm_sms_card_body_train_departure_place);
        TextView tvTrainNumber = (TextView) view.findViewById(R.id.cm_sms_card_body_train_number);
        /// TextView tvDestinationPlace = (TextView) view.findViewById(R.id.cm_sms_card_body_train_destination_place);

        TextView tvDepartureTime = (TextView) view.findViewById(R.id.cm_sms_card_body_train_departure_time);
        TextView tvDepartureDate = (TextView) view.findViewById(R.id.cm_sms_card_body_train_departure_date);

        View mNumberLine = view.findViewById(R.id.cm_sms_card_body_train_seat_line);
        TextView tvSeatNumber = (TextView) view.findViewById(R.id.cm_sms_card_body_train_seat_number);

        List<CardShowContent> keyWordsValues = cardInfo.getKeyWordsValues();

        for (CardShowContent kv : keyWordsValues) {

            if ("出发地点".equals(kv.getKey())) {
                tvDeparturePlace.setText(kv.getValue());
            } else if ("车次".equals(kv.getKey())) {
                tvTrainNumber.setText(kv.getValue());
            } else if ("出发日期".equals(kv.getKey())) {
                tvDepartureDate.setText(kv.getValue());
            } else if ("出发时间".equals(kv.getKey())) {
                tvDepartureTime.setText(kv.getValue());
            } else if ("座位".equals(kv.getKey())) {
                tvSeatNumber.setText(kv.getValue());
            }

        }

        if (TextUtils.isEmpty(tvSeatNumber.getText().toString())) {
            mNumberLine.setVisibility(View.GONE);
            tvSeatNumber.setVisibility(View.GONE);
        } else {
            mNumberLine.setVisibility(View.VISIBLE);
            tvSeatNumber.setVisibility(View.VISIBLE);
        }

        return view;
    }
}

