/*
 * This is the source code of AyuGram for Android.
 *
 * We do not and cannot prevent the use of our code,
 * but be respectful and credit the original author.
 *
 * Copyright @Radolyn, 2023
 */

package com.radolyn.ayugram;

import org.telegram.messenger.BuildVars;

public class AyuConstants {
    public static final long[] OFFICIAL_CHANNELS = {};
    public static final long[] DEVS = {};

    public static final int DOCUMENT_TYPE_NONE = 0;
    public static final int DOCUMENT_TYPE_PHOTO = 1;
    public static final int DOCUMENT_TYPE_STICKER = 2;
    public static final int DOCUMENT_TYPE_FILE = 3;

    public static final int OPTION_HISTORY = 1338_01;
    public static final int OPTION_TTL = 1338_02;
    public static final int OPTION_READ_UNTIL = 1338_03;

    public static final int DRAWER_TOGGLE_GHOST = 1000;
    public static final int DRAWER_KILL_APP = 1001;

    public static final int MESSAGE_EDITED_NOTIFICATION = 6968;
    public static final int MESSAGES_DELETED_NOTIFICATION = 6969;
    public static final int AYUSYNC_STATE_CHANGED = 6970;
    public static final int AYUSYNC_LAST_SENT_CHANGED = 6971;
    public static final int AYUSYNC_LAST_RECEIVED_CHANGED = 6972;
    public static final int AYUSYNC_REGISTER_STATUS_CODE_CHANGED = 6973;

    public static String DEFAULT_DELETED_MARK = "🧹";
    public static String DEFAULT_AYUSYNC_SERVER = "127.0.0.1";
    public static String AYU_DATABASE = "holdgram-data";

    public static String APP_GITHUB = "komusoran-jpg/HoldGram";
    public static String APP_NAME = "HoldGram";

    public static String BUILD_STORE_PACKAGE = "com.android.vending";
    public static String BUILD_ORIGINAL_PACKAGE = "org.telegram.messenger";
}
