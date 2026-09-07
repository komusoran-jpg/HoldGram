package com.radolyn.ayugram.proprietary;

import android.util.Pair;
import android.util.SparseArray;
import com.radolyn.ayugram.AyuConfig;
import com.radolyn.ayugram.database.AyuData;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.List;

public class AyuHistoryHook {

    public static Pair<Integer, Integer> getMinAndMaxIds(ArrayList<MessageObject> messArr) {
        if (messArr == null || messArr.isEmpty()) {
            return new Pair<>(0, 0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < messArr.size(); i++) {
            MessageObject obj = messArr.get(i);
            if (obj != null) {
                int id = obj.getId();
                if (id < min) min = id;
                if (id > max) max = id;
            }
        }
        if (min == Integer.MAX_VALUE) min = 0;
        if (max == Integer.MIN_VALUE) max = 0;
        return new Pair<>(min, max);
    }

    public static void doHook(int currentAccount, ArrayList<MessageObject> messArr, SparseArray<MessageObject>[] messagesDict, int startId, int endId, long dialogId, int limit, long topicId, boolean isSecretChat) {
        if (!AyuConfig.saveDeletedMessages) {
            return;
        }
        try {
            long userId = UserConfig.getInstance(currentAccount).getClientUserId();
            List<DeletedMessageFull> deleted = AyuData.getDeletedMessageDao().getMessages(userId, dialogId, topicId, startId, endId, limit);
            if (deleted == null || deleted.isEmpty()) {
                return;
            }
            for (int i = 0; i < deleted.size(); i++) {
                DeletedMessageFull full = deleted.get(i);
                if (full == null || full.message == null) continue;
                TLRPC.Message tlMsg = new TLRPC.TL_message();
                AyuMessageUtils.map(full.message, tlMsg, currentAccount);
                AyuMessageUtils.mapMedia(full.message, tlMsg);
                tlMsg.ayuDeleted = true;
                MessageObject obj = new MessageObject(currentAccount, tlMsg, false, false);

                boolean exists = false;
                for (int j = 0; j < messArr.size(); j++) {
                    if (messArr.get(j).getId() == obj.getId()) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    messArr.add(obj);
                    if (messagesDict != null && messagesDict.length > 0 && messagesDict[0] != null) {
                        messagesDict[0].put(obj.getId(), obj);
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
