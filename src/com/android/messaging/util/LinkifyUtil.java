//by sprd
package com.android.messaging.util;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.messaging.R;
import com.android.messaging.datamodel.data.ParticipantData;
import com.android.messaging.sms.MmsConfig;
import com.android.messaging.sms.MmsSmsUtils;
import com.sprd.messaging.util.SystemAdapter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;

public class LinkifyUtil extends Linkify {
    private static final String TAG = "LinkifyUtil";
    public static int mark = 0;
    private static final int PHONE_NUMBERS_MMS = 0x16;//for bug628776

    public static List<LinkSpec> computeNewLinks(Spannable text, int mask) {
        ArrayList<LinkSpec> links = new ArrayList<LinkSpec>();
        if (mask == 0) {
            return links;
        }
        return computeNewLinksInternal(text, mask);
    }

    public static void addLinkMovementMethod(TextView t) {
        MovementMethod m = t.getMovementMethod();

        if (!(m instanceof LinkMovementMethod)) {
            if (t.getLinksClickable()) {
                t.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    // run on UI thread
    public static void removeOldSpan(Spannable text) {
        try {
            URLSpan[] old = text.getSpans(0, text.length(), URLSpan.class);
            for (int i = old.length - 1; i >= 0; i--) {
                text.removeSpan(old[i]);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static List<LinkSpec> computeNewLinksInternal(Spannable text, int mask) {
        ArrayList<LinkSpec> links = new ArrayList<LinkSpec>();
        if (mask == 0) {
            return links;
        }

        if (MmsConfig.getCtccSdkEnabled()) {//bug 998436
            gatherDateLinks(links, text);
        }

        if ((mask & WEB_URLS) != 0) {
            gatherLinksSprd(links, text, SystemAdapter.WEB_URL_FOR_TEXTVIEW,//Patterns.WEB_URL_FOR_TEXTVIEW,
                    new String[]{"http://", "https://", "rtsp://"},
                    sUrlMatchFilter, null, mask);
        }

        if ((mask & EMAIL_ADDRESSES) != 0) {
            gatherLinksSprd(links, text, Patterns.EMAIL_ADDRESS,
                    new String[]{"mailto:"},
                    null, null, mask);
        }
        LogUtil.d(TAG, "getNumberVdfFilterEnable() = " + MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getNumberVdfFilterEnable());
        if ((mask & PHONE_NUMBERS) != 0) {
            gatherLinksSprd(links, text, Patterns.PHONE, new String[]{"tel:"}, sPhoneNumberMatchFilter, sPhoneNumberTransformFilter, mask);
        }

        if ((mask & MAP_ADDRESSES) != 0) {
            gatherMapLinks(links, text);
        }
        LogUtil.d(TAG, "getNumberFilter =" + MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getNumberFilterEnable());
        //for bug628776 begin
        if ((mask & PHONE_NUMBERS_MMS) != 0 &&
                MmsConfig.get(ParticipantData.DEFAULT_SELF_SUB_ID).getNumberFilterEnable()) {
            Pattern phoneNumberPattern = Pattern.compile(    // sdd = space, dot, or dash
                    "((\\+[0-9]+[\\- \\.]*)?"         // +<digits><sdd>*
                            + "(\\([0-9]+\\)[\\- \\.]*)?"     // (<digits>)<sdd>*
                            + "([0-9][0-9\\- \\. \\,]+[0-9])"     // <digit><digit|sdd>+<digit>
                            + "|[([\\*\\#]*[0-9]+[\\*\\#]*)]{3,255})");// USSD
            gatherLinksSprd(links, text, phoneNumberPattern,
                    new String[]{"tel:"},
                    null, null, mask);
        }
        //for bug628776 end
        pruneOverlaps(links);
        return links;
    }

    private static void gatherMapLinks(ArrayList<LinkSpec> links, Spannable s) {
        String string = s.toString();
        String address;
        int base = 0;

        try {
            while ((address = WebView.findAddress(string)) != null) {
                int start = string.indexOf(address);

                if (start < 0) {
                    break;
                }

                LinkSpec spec = new LinkSpec();
                int length = address.length();
                int end = start + length;

                spec.start = base + start;
                spec.end = base + end;
                string = string.substring(end);
                base += end;

                String encodedAddress = null;

                try {
                    encodedAddress = URLEncoder.encode(address, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    continue;
                }

                spec.url = "geo:0,0?q=" + encodedAddress;
                links.add(spec);
                mark = 4;
            }
        } catch (UnsupportedOperationException e) {
            // findAddress may fail with an unsupported exception on platforms without a WebView.
            // In this case, we will not append anything to the links variable: it would have died
            // in WebView.findAddress.
            return;
        }
    }

    private static void gatherLinksSprd(ArrayList<LinkSpec> links,
                                        Spannable s, Pattern pattern, String[] schemes,
                                        MatchFilter matchFilter, TransformFilter transformFilter, int mask) {
        Matcher m = pattern.matcher(s);
        int len = s.length();
        int findStart = -1;
        int findEnd = -1;
        int realEnd = 0;
        CharSequence cs;
        while (m.find()) {
            int start = m.start();
            int end = m.end();

            if (matchFilter == null || matchFilter.acceptMatch(s, start, end)) {
                LinkSpec spec = new LinkSpec();
                String url = makeUrl(m.group(0), schemes, m, transformFilter);
                StringBuilder urlBuilder = new StringBuilder(url);
                if ((mask & WEB_URLS) != 0) {
                    if (end < len - 1) {
                        cs = s.subSequence(end, len);
                        Matcher matcher = SystemAdapter.FILE_NAME.matcher(cs);//Patterns.FILE_NAME.matcher(cs);
                        while (matcher.find()) {
                            findStart = matcher.start();
                            findEnd = matcher.end();
                            if (findStart == 0 && findEnd < len) {
                                realEnd = end + findEnd;
                                end = realEnd;
                                urlBuilder.append(s.subSequence(findStart, findEnd).toString());
                                if (pattern.equals(SystemAdapter.WEB_URL_FOR_TEXTVIEW))//Patterns.WEB_URL_FOR_TEXTVIEW))
                                    mark = 1;
                                else
                                    mark = 2;
                                break;
                            }
                        }
                    }
                }
                // add for bug 629526 --begin
                if (schemes[0] != null && schemes[0].equals("tel:")) {
                    spec.url = urlBuilder.toString().replace(",", "");
                } else {
                    spec.url = urlBuilder.toString();
                }
                // add for bug 629526 --end
                spec.start = start;
                spec.end = end;

                links.add(spec);
            }
        }
    }

    private static void gatherDateLinks(ArrayList<LinkSpec> links, Spannable s) {//bug 998436
        Matcher m = SystemAdapter.SPRD_DATE.matcher(s);
        while (m.find()) {
            int start = m.start();
            int end = m.end();

            if (start >= 0 && start <= end) {
                LinkSpec spec = new LinkSpec();
                spec.url = MyURLSpan.TAG_SPRD_DATE + s.subSequence(start, end).toString();
                spec.start = start;
                spec.end = end;
                links.add(spec);
            }
        }
    }

    private static String makeUrl(String url, String[] prefixes,
                                  Matcher m, TransformFilter filter) {
        if (filter != null) {
            url = filter.transformUrl(m, url);
        }

        boolean hasPrefix = false;

        for (int i = 0; i < prefixes.length; i++) {
            if (url.regionMatches(true, 0, prefixes[i], 0,
                    prefixes[i].length())) {
                hasPrefix = true;

                // Fix capitalization if necessary
                if (!url.regionMatches(false, 0, prefixes[i], 0,
                        prefixes[i].length())) {
                    url = prefixes[i] + url.substring(prefixes[i].length());
                }

                break;
            }
        }

        if (!hasPrefix) {
            url = prefixes[0] + url;
        }

        return url;
    }

    private static void pruneOverlaps(ArrayList<LinkSpec> links) {
        Comparator<LinkSpec> c = new Comparator<LinkSpec>() {
            public final int compare(LinkSpec a, LinkSpec b) {
                if (a.start < b.start) {
                    return -1;
                }

                if (a.start > b.start) {
                    return 1;
                }

                if (a.end < b.end) {
                    return 1;
                }

                if (a.end > b.end) {
                    return -1;
                }

                return 0;
            }
        };

        Collections.sort(links, c);

        int len = links.size();
        int i = 0;

        while (i < len - 1) {
            LinkSpec a = links.get(i);
            LinkSpec b = links.get(i + 1);
            int remove = -1;

            if ((a.start <= b.start) && (a.end > b.start)) {
                if (b.end <= a.end) {
                    remove = i + 1;
                } else if ((a.end - a.start) > (b.end - b.start)) {
                    remove = i + 1;
                } else if ((a.end - a.start) < (b.end - b.start)) {
                    remove = i;
                }

                if (remove != -1) {
                    links.remove(remove);
                    len--;
                    continue;
                }

            }

            i++;
        }
    }

    //run on UI Thread
    public static void applyLinks(List<LinkSpec> links, Spannable text) {
        for (LinkSpec link : links) {
            URLSpan span = new MyURLSpan(link.url, mark);
            text.setSpan(span, link.start, link.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static class MyURLSpan extends URLSpan {//modify for bug650441
        private Context mContext;
        private int mark;
        /* Add by SPRD for bug 583100 2016.07.28 Start */
        private static final String TAG_SMS = "smsto:";
        private static final String TAG_TEL = "tel:";
        public static final String TAG_SPRD_DATE = "sprdDate:"; //bug 1066990
        private String mUrl = "";
        private ArrayList<String> mUrlList = null;
        private String addContact = "";
        //bug 708919 begin
        private int mIconHeight = -1;
        //bug 708919 end
        /* Add by SPRD for bug 583100 2016.07.28 End */
        private static AlertDialog mLinkJumpDialog;//modify for bug650441
        private static AlertDialog mTelLinkJumpDialog; //bug 882746

        MyURLSpan(String url, int mark) {
            super(url);
            this.mark = mark;
        }

        /*bug 1066990, begin*/
        @Override
        public void updateDrawState(TextPaint ds) {
            //super.updateDrawState(ds);
            if (MmsConfig.isCtccOp()) {
                ds.setColor(Color.BLUE);
            } else {
                ds.setColor(ds.linkColor);
            }
            ds.setUnderlineText(true);
        }

        private void showDateDialog(Context mContext, final String mUrl) {
            mUrlList = new ArrayList<String>();
            mUrlList.add(mContext.getString(R.string.event_create));
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(mContext,
                    android.R.layout.select_dialog_item, mUrlList) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = (TextView) super.getView(position, convertView, parent);
                    TextView textView = (TextView) v;
                    try {
                        if (0 == position) {
                            Drawable drawable = mContext.getPackageManager().getApplicationIcon("com.android.calendar");
                            if (drawable != null) {
                                int height = drawable.getIntrinsicHeight();
                                height = height > 96 ? 96 : height;
                                drawable.setBounds(0, 0, height, height);
                                textView.setCompoundDrawablePadding(10);
                                textView.setCompoundDrawables(drawable, null, null, null);
                            }
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    return v;
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    final String dateString = mUrl.substring(TAG_SPRD_DATE.length());
                    LogUtil.d(TAG, "onClick dateString = " + dateString);
                    final int year, month, day;
                    try {
                        year = Integer.parseInt(dateString.substring(0, 4));
                        month = Integer.parseInt(dateString.substring(5, 7));
                        day = Integer.parseInt(dateString.substring(8, 10));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                        Toast.makeText(mContext, mContext.getResources().getString(R.string.sprd_parse_date_fail), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Calendar calendar = Calendar.getInstance();
                    calendar.set(year, month - 1, day);
                    long beginTime = calendar.getTimeInMillis();
                    long endTime = beginTime + DateUtils.HOUR_IN_MILLIS;
                    try {
                        Intent intent = new Intent(Intent.ACTION_INSERT)
                                .setData(CalendarContract.Events.CONTENT_URI)
                                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
                                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime);
                        if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                            mContext.startActivity(intent);
                        } else {
                            LogUtil.d(TAG, "onClick calendar NOT FOUND...");
                        }
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                    }
                    try {
                        dialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
            };
            builder.setCancelable(true);
            builder.setAdapter(arrayAdapter, onClickListener);
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public final void onClick(DialogInterface dialog, int which) {
                    try {
                        dialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
            });
            builder.show();
        }
        /*bug 1066990, end*/

        @Override
        public void onClick(final View widget) {
            // TODO Auto-generated method stub
            mContext = widget.getContext();
            LogUtil.d(TAG, "uri:" + Uri.parse(getURL()));
            widget.setTag(true);//bug 1238925
            if (getURL().startsWith("http") || getURL().startsWith("https") || getURL().startsWith("rtsp")) {
                mark = 1;
            } else if (getURL().startsWith("mailto")) {
                mark = 2;
            } else if (getURL().startsWith("tel")) {
                mark = 3;
            } else if (getURL().startsWith("geo")) {
                mark = 4;
            } else if (getURL().startsWith(TAG_SPRD_DATE)) {//bug 998436, nothing to do
                showDateDialog(mContext, getURL());
                return;
            }
            int resId = 0;
            switch (mark) {
                case 1:
                    resId = R.string.browser;
                    break;
                case 2:
                    resId = R.string.email;
                    break;
                case 3:
                    resId = R.string.telephone;
                    break;
                case 4:
                    resId = R.string.map;
                    break;
            }
            /* Add by SPRD for bug 583100 2016.07.28 Start */
            mUrl = getURL();
            if (mUrl.startsWith("tel")) {
                mUrlList = new ArrayList<String>();
                addContact = mContext.getString(R.string.add_contact_confirmation_dialog_title);
                mUrlList.add(mUrl);
                mUrlList.add(TAG_SMS + mUrl.substring(TAG_TEL.length()));
                mUrlList.add(addContact);

                ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext,
                        android.R.layout.select_dialog_item, mUrlList) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View v = super.getView(position, convertView, parent);
                        try {
                            String url = getItem(position).toString();
                            TextView tv = (TextView) v;
                            Drawable d;
                            if (getItem(position).toString().equals(addContact)) {
                                d = mContext.getPackageManager().getApplicationIcon("com.android.contacts");
                            } else if (url.startsWith(TAG_SMS)) {
                                d = mContext.getPackageManager().getApplicationIcon("com.android.messaging");
                                //bug 708919 begin
                                if (d != null) {
                                    mIconHeight = d.getIntrinsicHeight();
                                }
                                //bug 708919 end
                            } else if (url.startsWith(TAG_TEL)) {
                                d = mContext.getPackageManager().getApplicationIcon("com.android.dialer");
                            } else {
                                d = mContext.getPackageManager().getActivityIcon(
                                        new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            }

                            if (d != null) {
                                //bug 708919 begin
                                int height = d.getIntrinsicHeight();
                                int width = d.getIntrinsicWidth();//bug746816
                                if (mIconHeight > 0 && height > mIconHeight) {
                                    height = mIconHeight;
                                }
                                //bug746816 begin
                                if (width < height) {
                                    height = width;
                                }
                                if (height > 96) {
                                    height = 96;
                                }
                                //bug790946 begin
                                DisplayMetrics metrics = new DisplayMetrics();
                                WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
                                windowManager.getDefaultDisplay().getMetrics(metrics);
                                if (metrics.widthPixels == 480) {
                                    if (height > 72) {
                                        height = 72;
                                    }
                                }
                                //bug790946 end
                                //bug746816 end
                                d.setBounds(0, 0, height, height);
                                //bug 708919 end
                                tv.setCompoundDrawablePadding(10);
                                tv.setCompoundDrawables(d, null, null, null);
                            }

                            if (url.startsWith(TAG_TEL)) {
                                final BidiFormatter bidiFormatter = BidiFormatter.getInstance(); //by 1245524
                                String number = bidiFormatter.unicodeWrap(url.substring(TAG_TEL.length()),
                                                      TextDirectionHeuristics.LTR); //by 1245524
                                if (!TextUtils.isEmpty(number)) {
                                    url = number;
                                } else {
                                    url = url.substring(TAG_TEL.length());
                                }
                            } else if (url.startsWith(TAG_SMS)) {
                                final BidiFormatter bidiFormatter = BidiFormatter.getInstance(); //by 1245524
                                String number = bidiFormatter.unicodeWrap(url.substring(TAG_SMS.length()),
                                                      TextDirectionHeuristics.LTR); //by 1245524
                                if (!TextUtils.isEmpty(number)) {
                                    url = number;
                                } else {
                                    url = url.substring(TAG_SMS.length());
                                }
                            }
                            tv.setText(url);
                        } catch (android.content.pm.PackageManager.NameNotFoundException ex) {
                            TextView tv = (TextView) v;
                            tv.setCompoundDrawables(null, null, null, null);
                        }
                        return v;
                    }
                };

                closeTelLinkJumpDialog(); //bug 882746
                AlertDialog.Builder b = new AlertDialog.Builder(mContext);
                DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                    @Override
                    public final void onClick(DialogInterface dialog, int which) {
                        if (which >= 0) {
                            Uri uri = Uri.parse(mUrlList.get(which));
                            if (mUrlList.get(which).equals(addContact)) {
                                if (mUrl == null) {
                                    return;
                                }
                                launchAddContactActivity(mContext, mUrl.substring(TAG_TEL.length()));
                            } else if (mUrlList.get(which).startsWith(TAG_SMS)) {
                                Intent sendtoIntent = new Intent(Intent.ACTION_SENDTO).setData(uri);
                                startExternalActivity(mContext, sendtoIntent);
                            } else if (mUrlList.get(which).startsWith(TAG_TEL)) {
                                //for bug628776 begin
                                if (mUrlList.get(which).startsWith(TAG_TEL + "*") || mUrlList.get(which).startsWith(TAG_TEL + "#")) {
                                    final Intent intent = new Intent(Intent.ACTION_CALL,
                                            Uri.fromParts("tel", mUrlList.get(which).substring(TAG_TEL.length()), null));
                                    try {
                                        startExternalActivity(mContext, intent);
                                    } catch (final SecurityException ex) {
                                        ex.printStackTrace();
                                    }
                                } else {
                                    enter(widget);
                                }
                                //for bug628776 end
                            }
                        }
                        try {
                            dialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };
                //b.setTitle(R.string.select_link_title);
                b.setCancelable(true);
                b.setAdapter(adapter, click);
                b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public final void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                mTelLinkJumpDialog = b.show(); //bug882746
                return;
            }
            /* Add by SPRD for bug 583100 2016.07.28 End */
            //add for bug650441 begin
            closeLinkJumpDialog();
            mLinkJumpDialog = new AlertDialog.Builder(mContext).setMessage(mContext.getString(R.string.is_redirect_to, mContext.getString(resId))).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    return;
                }
            }).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    enter(widget);
                }
            }).setCancelable(true).create();
            mLinkJumpDialog.show();
            //add for bug650441 end
        }

        //add for bug650441 begin
        public static void closeLinkJumpDialog() {
            if (mLinkJumpDialog != null) {
                if (mLinkJumpDialog.isShowing()) {
                    //modify for bug686740 begin
                    try {
                        mLinkJumpDialog.dismiss();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //modify for bug686740 end
                }
                mLinkJumpDialog = null;
            }
        }//add for bug650441 end

        //bug 882746 begin
        public static void closeTelLinkJumpDialog() {
            if (mTelLinkJumpDialog != null) {
                if (mTelLinkJumpDialog.isShowing()) {
                    try {
                        mTelLinkJumpDialog.dismiss();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                mTelLinkJumpDialog = null;
            }
        }
        //bug 882746 end

        public void enter(View widget) {
            super.onClick(widget);
        }

        /* Add by SPRD for bug 583100 2016.07.28 Start */
        public void launchAddContactActivity(final Context context, final String destination) {
            final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            final String destinationType = MmsSmsUtils.isEmailAddress(destination) ?
                    Intents.Insert.EMAIL : Intents.Insert.PHONE;
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(destinationType, destination);
            startExternalActivity(context, intent);
        }

        private void startExternalActivity(final Context context, final Intent intent) {
            try {
                context.startActivity(intent);
            } catch (final ActivityNotFoundException ex) {
                UiUtils.showToastAtBottom(R.string.activity_not_found_message);
            } catch (Exception ex) {
                UiUtils.showToastAtBottom(R.string.activity_not_found_message);
            }
        }
        /* Add by SPRD for bug 583100 2016.07.28 End */
    }
}
