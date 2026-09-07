package com.radolyn.ayugram.proprietary;

import android.text.TextUtils;
import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.util.ArrayList;

public class AyuMessageUtils {

    public static void map(AyuSavePreferences prefs, AyuMessageBase msg) {
        if (prefs == null || prefs.getMessage() == null || msg == null) return;
        TLRPC.Message message = prefs.getMessage();
        msg.userId = prefs.getUserId();
        msg.dialogId = prefs.getDialogId();
        msg.topicId = prefs.getTopicId();
        msg.messageId = prefs.getMessageId();
        msg.groupedId = message.grouped_id;
        msg.peerId = MessageObject.getPeerId(message.peer_id);
        msg.fromId = MessageObject.getPeerId(message.from_id);
        msg.date = message.date;
        msg.flags = message.flags;
        msg.editDate = message.edit_date;
        msg.views = message.views;
        msg.entityCreateDate = prefs.getRequestCatchTime();
        msg.text = message.message;

        if (message.fwd_from != null) {
            msg.fwdFlags = message.fwd_from.flags;
            msg.fwdFromId = MessageObject.getPeerId(message.fwd_from.from_id);
            msg.fwdName = message.fwd_from.from_name;
            msg.fwdDate = message.fwd_from.date;
            msg.fwdPostAuthor = message.fwd_from.post_author;
        }

        if (message.reply_to != null) {
            msg.replyFlags = message.reply_to.flags;
            msg.replyMessageId = message.reply_to.reply_to_msg_id;
            msg.replyPeerId = MessageObject.getPeerId(message.reply_to.reply_to_peer_id);
            msg.replyTopId = message.reply_to.reply_to_top_id;
            msg.replyForumTopic = message.reply_to.forum_topic;
        }

        if (message.entities != null && !message.entities.isEmpty()) {
            try {
                NativeByteBuffer byteBuffer = new NativeByteBuffer(message.entities.size() * 32 + 32);
                byteBuffer.writeInt32(0x1cb5c415); // vector magic
                byteBuffer.writeInt32(message.entities.size());
                for (int i = 0; i < message.entities.size(); i++) {
                    message.entities.get(i).serializeToStream(byteBuffer);
                }
                msg.textEntities = byteBuffer.toByteArray();
                byteBuffer.reuse();
            } catch (Throwable ignored) {}
        }
    }

    public static void mapMedia(AyuSavePreferences prefs, AyuMessageBase entity, boolean copyMedia) {
        if (prefs == null || prefs.getMessage() == null || entity == null) return;
        TLRPC.Message message = prefs.getMessage();
        if (message.media == null) return;

        if (message.media.photo != null) {
            entity.documentType = AyuConstants.DOCUMENT_TYPE_PHOTO;
            File file = FileLoader.getInstance(prefs.getAccountId()).getPathToMessage(message);
            if (file != null && file.exists()) {
                entity.mediaPath = file.getAbsolutePath();
            }
        } else if (message.media.document != null) {
            TLRPC.Document doc = message.media.document;
            entity.documentType = AyuConstants.DOCUMENT_TYPE_FILE;
            entity.mimeType = doc.mime_type;
            File file = FileLoader.getInstance(prefs.getAccountId()).getPathToAttach(doc, true);
            if (file != null && file.exists()) {
                entity.mediaPath = file.getAbsolutePath();
            }
        }
    }

    public static void map(AyuMessageBase base, TLRPC.Message message, int currentAccount) {
        if (base == null || message == null) return;
        message.dialog_id = base.dialogId;
        message.grouped_id = base.groupedId;
        message.id = base.messageId;
        message.realId = base.messageId;
        message.date = base.date;
        message.flags = base.flags;
        message.edit_date = base.editDate;
        message.views = base.views;
        message.message = base.text;
        message.ayuDeleted = true;
        message.ayuNoforwards = true;

        if (base.peerId != 0) {
            message.peer_id = MessagesController.getInstance(currentAccount).getPeer(base.peerId);
        }
        if (base.fromId != 0) {
            message.from_id = MessagesController.getInstance(currentAccount).getPeer(base.fromId);
        }

        if (base.fwdFromId != 0 || !TextUtils.isEmpty(base.fwdName)) {
            message.fwd_from = new TLRPC.TL_messageFwdHeader();
            message.fwd_from.flags = base.fwdFlags;
            message.fwd_from.from_name = base.fwdName;
            message.fwd_from.date = base.fwdDate;
            message.fwd_from.post_author = base.fwdPostAuthor;
            if (base.fwdFromId != 0) {
                message.fwd_from.from_id = MessagesController.getInstance(currentAccount).getPeer(base.fwdFromId);
            }
        }

        if (base.replyMessageId != 0) {
            message.reply_to = new TLRPC.TL_messageReplyHeader();
            message.reply_to.flags = base.replyFlags;
            message.reply_to.reply_to_msg_id = base.replyMessageId;
            message.reply_to.reply_to_top_id = base.replyTopId;
            message.reply_to.forum_topic = base.replyForumTopic;
            if (base.replyPeerId != 0) {
                message.reply_to.reply_to_peer_id = MessagesController.getInstance(currentAccount).getPeer(base.replyPeerId);
            }
        }

        if (base.textEntities != null && base.textEntities.length > 0) {
            try {
                NativeByteBuffer byteBuffer = new NativeByteBuffer(base.textEntities.length);
                byteBuffer.writeBytes(base.textEntities);
                byteBuffer.position(0);
                int magic = byteBuffer.readInt32(false);
                int count = byteBuffer.readInt32(false);
                message.entities = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    TLRPC.MessageEntity entity = TLRPC.MessageEntity.TLdeserialize(byteBuffer, byteBuffer.readInt32(false), false);
                    if (entity != null) {
                        message.entities.add(entity);
                    }
                }
                byteBuffer.reuse();
            } catch (Throwable ignored) {}
        }
    }

    public static void mapMedia(AyuMessageBase base, TLRPC.Message message) {
        if (base == null || message == null || base.documentType == 0) return;
        if (!TextUtils.isEmpty(base.mediaPath)) {
            message.attachPath = base.mediaPath;
            File file = new File(base.mediaPath);
            if (base.documentType == AyuConstants.DOCUMENT_TYPE_PHOTO) {
                TLRPC.TL_messageMediaPhoto mediaPhoto = new TLRPC.TL_messageMediaPhoto();
                mediaPhoto.flags = 1;
                mediaPhoto.photo = new TLRPC.TL_photo();
                mediaPhoto.photo.has_stickers = false;
                mediaPhoto.photo.date = base.date;
                TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                photoSize.size = (int) file.length();
                photoSize.w = 800;
                photoSize.h = 600;
                photoSize.type = "y";
                photoSize.location = new com.radolyn.ayugram.utils.AyuFileLocation(base.mediaPath);
                mediaPhoto.photo.sizes.add(photoSize);
                message.media = mediaPhoto;
            } else if (base.documentType == AyuConstants.DOCUMENT_TYPE_FILE) {
                TLRPC.TL_messageMediaDocument mediaDoc = new TLRPC.TL_messageMediaDocument();
                mediaDoc.flags = 1;
                mediaDoc.document = new TLRPC.TL_document();
                mediaDoc.document.date = base.date;
                mediaDoc.document.localPath = base.mediaPath;
                mediaDoc.document.file_name = file.getName();
                mediaDoc.document.file_name_fixed = file.getName();
                mediaDoc.document.size = file.length();
                mediaDoc.document.mime_type = base.mimeType != null ? base.mimeType : "application/octet-stream";
                message.media = mediaDoc;
            }
        }
    }
}
