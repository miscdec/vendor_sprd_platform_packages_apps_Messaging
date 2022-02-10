package com.android.messaging.ui.conversation;

import com.unicom.callme.outerentity.CardInfo;
import com.unicom.callme.outerentity.SmsInfo;
import com.unicom.callme.outerentity.TextLinkBean;

import java.util.List;

/**
 * 封装短信信息（包含卡片信息和关键信息）
 */

public class MySmsInfo extends SmsInfo {
    //短信类型：1-接收的短信 2-发出的短信
    private int type;
    //短信对应的卡片信息（如果有的话）
    private CardInfo cardInfo;
    //短信中的关键信息（日期、网址、电话等，如果有的话）
    private List<TextLinkBean> textLinkBeanList;

    public MySmsInfo(Builder builder) {
        super(builder);
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public CardInfo getCardInfo() {
        return cardInfo;
    }

    public void setCardInfo(CardInfo cardInfo) {
        this.cardInfo = cardInfo;
    }

    public List<TextLinkBean> getTextLinkBeanList() {
        return textLinkBeanList;
    }

    public void setTextLinkBeanList(List<TextLinkBean> textLinkBeanList) {
        this.textLinkBeanList = textLinkBeanList;
    }
}
