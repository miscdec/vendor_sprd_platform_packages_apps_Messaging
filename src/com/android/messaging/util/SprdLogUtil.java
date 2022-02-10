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
package com.android.messaging.util;


import java.util.Arrays;

public class SprdLogUtil {
    // on-off
    private static boolean DEBUG = true;

    // common
    public static void d(String tag, String msg) {
        if (DEBUG) {
            LogUtil.d(tag, msg);
        }
    }

    // common
    public static void d(String tag, String msg, Exception e) {
        if (DEBUG) {
            LogUtil.d(tag, msg, e);
        }
    }

    public static void dump(Object... o) {
        dump(LogUtil.BUGLE_TAG + "Log", o);
    }

    private static void dump(String tag, Object... o) {
        if (!DEBUG) {
            return;
        }
        String msg = null;
        if (o == null) {
            msg = "null";
        } else if (o.length == 0) {
            //
        } else if (o.length == 1) {
            if (o[0] != null) {
                msg = o[0].toString();
            }
        } else if (o.length > 1) {
            if (o.length % 2 == 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < o.length; i += 2) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    if (o[i] instanceof String) {
                        // name
                        sb.append(o[i]);
                        sb.append("=");
                        // value
                        String vs;
                        if (o[i + 1] != null) {
                            if (o[i + 1] instanceof Object[]) {
                                vs = Arrays.deepToString((Object[]) o[i + 1]);
                            } else {
                                vs = o[i + 1].toString();
                            }
                        } else {
                            vs = "null";
                        }
                        sb.append(vs);
                    }
                }
                msg = sb.toString();
            }
        }
        if (msg == null || msg.length() == 0) {
            msg = Arrays.deepToString(o);
        }
        if (msg.length() > 0) {
            dLog(tag, msg);
        }
    }

    private static void dLog(String tag, String msg) {
        if (DEBUG) {
            if (msg != null) {
                if (tag != null) {
                    tag = tag.trim();
                }
                if (tag == null || tag.length() == 0) {
                    tag = LogUtil.BUGLE_TAG;
                }
                LogUtil.d(tag, msg);
            }
        }
    }
}
