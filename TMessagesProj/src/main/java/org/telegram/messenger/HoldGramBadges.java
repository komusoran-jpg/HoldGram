package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CombinedDrawable;

import java.util.HashMap;
import java.util.Map;

public class HoldGramBadges {

    public static final int BADGE_NONE = 0;
    public static final int BADGE_VERIFIED = 1;
    public static final int BADGE_SHIELD = 2;
    public static final int BADGE_CROWN = 3;
    public static final int BADGE_LIGHTNING = 4;
    public static final int BADGE_FLAME = 5;
    public static final int BADGE_STAR = 6;
    public static final int BADGE_DIAMOND = 7;

    private static final String PREF_NAME = "holdgram_badges";
    private static SharedPreferences preferences;
    private static int selfBadge = BADGE_SHIELD;
    private static boolean selfBadgeEnabled = true;
    private static final Map<Long, Integer> userBadges = new HashMap<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        if (ApplicationLoader.applicationContext == null) return;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        selfBadge = preferences.getInt("self_badge", BADGE_SHIELD);
        selfBadgeEnabled = preferences.getBoolean("self_badge_enabled", true);

        String customStr = preferences.getString("custom_badges", "");
        if (!customStr.isEmpty()) {
            String[] parts = customStr.split(";");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    try {
                        long uid = Long.parseLong(kv[0]);
                        int btype = Integer.parseInt(kv[1]);
                        userBadges.put(uid, btype);
                    } catch (Exception ignore) {}
                }
            }
        }
        initialized = true;
    }

    private static void ensureInit() {
        if (!initialized) init();
    }

    public static int getSelfBadge() {
        ensureInit();
        return selfBadgeEnabled ? selfBadge : BADGE_NONE;
    }

    public static void setSelfBadge(int badge) {
        ensureInit();
        selfBadge = badge;
        selfBadgeEnabled = (badge != BADGE_NONE);
        if (preferences != null) {
            preferences.edit()
                    .putInt("self_badge", selfBadge)
                    .putBoolean("self_badge_enabled", selfBadgeEnabled)
                    .apply();
        }
        notifyBadgesChanged();
    }

    public static boolean isSelfBadgeEnabled() {
        ensureInit();
        return selfBadgeEnabled;
    }

    public static void setSelfBadgeEnabled(boolean enabled) {
        ensureInit();
        selfBadgeEnabled = enabled;
        if (preferences != null) {
            preferences.edit().putBoolean("self_badge_enabled", enabled).apply();
        }
        notifyBadgesChanged();
    }

    public static int getBadgeForUser(long userId, int currentAccount) {
        ensureInit();
        long clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        if (userId != 0 && userId == clientUserId) {
            return selfBadgeEnabled ? selfBadge : BADGE_NONE;
        }
        Integer custom = userBadges.get(userId);
        return custom != null ? custom : BADGE_NONE;
    }

    public static boolean hasBadge(TLRPC.User user, int currentAccount) {
        if (user == null) return false;
        return getBadgeForUser(user.id, currentAccount) != BADGE_NONE;
    }

    public static boolean hasBadge(long userId, int currentAccount) {
        return getBadgeForUser(userId, currentAccount) != BADGE_NONE;
    }

    public static void setUserBadge(long userId, int badge) {
        ensureInit();
        if (badge == BADGE_NONE) {
            userBadges.remove(userId);
        } else {
            userBadges.put(userId, badge);
        }
        saveCustomBadges();
        notifyBadgesChanged();
    }

    private static void saveCustomBadges() {
        if (preferences == null) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Long, Integer> entry : userBadges.entrySet()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        preferences.edit().putString("custom_badges", sb.toString()).apply();
    }

    public static Drawable getBadgeDrawableForUser(TLRPC.User user, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        if (user == null) return null;
        int badge = getBadgeForUser(user.id, currentAccount);
        if (badge == BADGE_NONE) return null;
        return getBadgeDrawable(badge, ApplicationLoader.applicationContext, resourcesProvider);
    }

    public static Drawable getBadgeDrawable(int badgeType, Context context, Theme.ResourcesProvider resourcesProvider) {
        int color = Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider);
        return getBadgeDrawable(badgeType, context, color);
    }

    public static Drawable getBadgeDrawable(int badgeType, Context context, int tintColor) {
        if (context == null || badgeType == BADGE_NONE) return null;

        Drawable drawable = null;
        int overrideColor = tintColor;

        switch (badgeType) {
            case BADGE_VERIFIED: {
                Drawable verifiedBackground = ContextCompat.getDrawable(context, R.drawable.verified_area);
                Drawable verifiedCheck = ContextCompat.getDrawable(context, R.drawable.verified_check);
                if (verifiedBackground != null && verifiedCheck != null) {
                    verifiedBackground = verifiedBackground.mutate();
                    verifiedCheck = verifiedCheck.mutate();
                    verifiedBackground.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.MULTIPLY));
                    verifiedCheck.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_verifiedCheck), PorterDuff.Mode.MULTIPLY));
                    return new CombinedDrawable(verifiedBackground, verifiedCheck);
                }
                break;
            }
            case BADGE_SHIELD:
                drawable = ContextCompat.getDrawable(context, R.drawable.holdgram_badge_shield);
                overrideColor = Color.parseColor("#3390EC");
                break;
            case BADGE_CROWN:
                drawable = ContextCompat.getDrawable(context, R.drawable.holdgram_badge_crown);
                overrideColor = Color.parseColor("#FFA800");
                break;
            case BADGE_LIGHTNING:
                drawable = ContextCompat.getDrawable(context, R.drawable.holdgram_badge_lightning);
                overrideColor = Color.parseColor("#FFD600");
                break;
            case BADGE_FLAME:
                drawable = ContextCompat.getDrawable(context, R.drawable.holdgram_badge_fire);
                overrideColor = Color.parseColor("#FF5252");
                break;
            case BADGE_STAR:
                drawable = ContextCompat.getDrawable(context, R.drawable.holdgram_badge_star);
                overrideColor = Color.parseColor("#8E24AA");
                break;
            case BADGE_DIAMOND:
                drawable = ContextCompat.getDrawable(context, R.drawable.holdgram_badge_diamond);
                overrideColor = Color.parseColor("#00E5FF");
                break;
        }

        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(overrideColor, PorterDuff.Mode.SRC_IN));
        }
        return drawable;
    }

    public static String getBadgeName(int badgeType) {
        switch (badgeType) {
            case BADGE_VERIFIED: return "Галочка верификации";
            case BADGE_SHIELD: return "Щит HoldGram";
            case BADGE_CROWN: return "Корона";
            case BADGE_LIGHTNING: return "Молния";
            case BADGE_FLAME: return "Огонь";
            case BADGE_STAR: return "Звезда";
            case BADGE_DIAMOND: return "Алмаз";
            default: return "Отключено";
        }
    }

    public static String getBadgeEmoji(int badgeType) {
        switch (badgeType) {
            case BADGE_VERIFIED: return "✅";
            case BADGE_SHIELD: return "🛡️";
            case BADGE_CROWN: return "👑";
            case BADGE_LIGHTNING: return "⚡";
            case BADGE_FLAME: return "🔥";
            case BADGE_STAR: return "⭐";
            case BADGE_DIAMOND: return "💎";
            default: return "❌";
        }
    }

    public static void notifyBadgesChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
            }
        });
    }
}
