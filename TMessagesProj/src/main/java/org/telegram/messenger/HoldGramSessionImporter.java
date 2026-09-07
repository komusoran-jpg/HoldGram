package org.telegram.messenger;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class HoldGramSessionImporter {

    public static class SessionData {
        public int dcId = 2;
        public byte[] authKey; // 256 bytes
        public long userId = 0;
        public String phone = "";
        public String serverAddress = null;
        public int port = 443;
    }

    public static final String[] DC_DEFAULT_IPS = {
            "",
            "149.154.175.53",  // DC 1
            "149.154.167.50",  // DC 2
            "149.154.175.100", // DC 3
            "149.154.167.91",  // DC 4
            "91.108.56.130"   // DC 5
    };

    /**
     * Auto-detect and parse session from file (JSON, SQLite .session, or TData zip)
     */
    public static SessionData parseFile(File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("Файл не найден");
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".json")) {
            return parseJSON(readFileToString(file));
        } else if (name.endsWith(".session")) {
            return parseSQLiteSession(file);
        } else if (name.endsWith(".zip") || file.isDirectory()) {
            return parseTData(file);
        } else {
            // Try SQLite first, then JSON
            try {
                return parseSQLiteSession(file);
            } catch (Throwable e1) {
                try {
                    return parseJSON(readFileToString(file));
                } catch (Throwable e2) {
                    throw new IllegalArgumentException("Неподдерживаемый формат файла: " + name);
                }
            }
        }
    }

    /**
     * Parse JSON session
     */
    public static SessionData parseJSON(String jsonStr) throws Exception {
        JSONObject obj = new JSONObject(jsonStr);
        SessionData data = new SessionData();

        if (obj.has("dc_id")) {
            data.dcId = obj.getInt("dc_id");
        } else if (obj.has("dc")) {
            data.dcId = obj.getInt("dc");
        } else if (obj.has("datacenter_id")) {
            data.dcId = obj.getInt("datacenter_id");
        }

        if (obj.has("user_id")) {
            data.userId = obj.getLong("user_id");
        } else if (obj.has("id")) {
            data.userId = obj.getLong("id");
        }

        if (obj.has("phone")) {
            data.phone = obj.getString("phone");
        }

        if (obj.has("server_address")) {
            data.serverAddress = obj.getString("server_address");
        }
        if (obj.has("port")) {
            data.port = obj.getInt("port");
        }

        // Auth key parsing
        String authKeyStr = "";
        if (obj.has("auth_key")) {
            authKeyStr = obj.getString("auth_key");
        } else if (obj.has("auth_key_hex")) {
            authKeyStr = obj.getString("auth_key_hex");
        } else if (obj.has("session_key")) {
            authKeyStr = obj.getString("session_key");
        }

        if (!authKeyStr.isEmpty()) {
            data.authKey = parseKeyBytes(authKeyStr);
        }

        if (data.authKey == null || data.authKey.length != 256) {
            throw new IllegalArgumentException("Некорректный auth_key в JSON (ожидается 256 байт)");
        }

        return data;
    }

    /**
     * Parse SQLite .session (Telethon or Pyrogram)
     */
    public static SessionData parseSQLiteSession(File dbFile) throws Exception {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            SessionData data = new SessionData();

            // Try Telethon format: SELECT dc_id, server_address, port, auth_key FROM sessions
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("SELECT dc_id, server_address, port, auth_key FROM sessions LIMIT 1", null);
                if (cursor != null && cursor.moveToFirst()) {
                    data.dcId = cursor.getInt(0);
                    data.serverAddress = cursor.getString(1);
                    data.port = cursor.getInt(2);
                    data.authKey = cursor.getBlob(3);
                }
            } catch (Throwable ignore) {
            } finally {
                if (cursor != null) cursor.close();
            }

            // If not found, try Pyrogram format: SELECT dc_id, test_mode, auth_key, user_id FROM sessions
            if (data.authKey == null) {
                try {
                    cursor = db.rawQuery("SELECT dc_id, auth_key, user_id FROM sessions LIMIT 1", null);
                    if (cursor != null && cursor.moveToFirst()) {
                        data.dcId = cursor.getInt(0);
                        data.authKey = cursor.getBlob(1);
                        data.userId = cursor.getLong(2);
                    }
                } catch (Throwable ignore) {
                } finally {
                    if (cursor != null) cursor.close();
                }
            }

            if (data.authKey == null || data.authKey.length != 256) {
                throw new IllegalArgumentException("Не удалось извлечь 256-байтный auth_key из .session SQLite базы");
            }

            if (data.dcId < 1 || data.dcId > 5) {
                data.dcId = 2; // Default to DC 2
            }

            return data;
        } finally {
            if (db != null) {
                try { db.close(); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Parse Telethon or Pyrogram StringSession from text
     */
    public static SessionData parseStringSession(String str) throws Exception {
        if (str == null) throw new IllegalArgumentException("Строка сессии пуста");
        str = str.trim();

        // If JSON passed as string
        if (str.startsWith("{")) {
            return parseJSON(str);
        }

        byte[] raw;
        try {
            raw = Base64.decode(str, Base64.DEFAULT);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ошибка декодирования Base64 строки сессии");
        }

        SessionData data = new SessionData();

        // Telethon StringSession (starts with 1)
        if (str.startsWith("1") && raw.length >= 263) {
            data.dcId = raw[0] & 0xFF;
            // Auth key is last 256 bytes
            data.authKey = Arrays.copyOfRange(raw, raw.length - 256, raw.length);
            return data;
        }

        // Pyrogram StringSession (length around 267..275 bytes or starting with version prefix)
        if (raw.length >= 265) {
            data.dcId = raw[0] & 0xFF;
            // Next 256 bytes are auth key
            data.authKey = Arrays.copyOfRange(raw, 2, 258);
            if (raw.length >= 266) {
                // user id follows
                long uid = 0;
                for (int i = 0; i < 8 && 258 + i < raw.length; i++) {
                    uid |= ((long) (raw[258 + i] & 0xFF)) << (i * 8);
                }
                data.userId = uid;
            }
            return data;
        }

        // Generic 256-byte raw base64 key
        if (raw.length == 256) {
            data.authKey = raw;
            data.dcId = 2;
            return data;
        }

        throw new IllegalArgumentException("Неизвестный формат StringSession (длина: " + raw.length + " байт)");
    }

    /**
     * Parse TData from folder or zip
     */
    public static SessionData parseTData(File file) throws Exception {
        File workDir = file;
        File tempDir = null;

        if (file.isFile() && file.getName().toLowerCase().endsWith(".zip")) {
            tempDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "tdata_extract_" + System.currentTimeMillis());
            tempDir.mkdirs();
            unzip(file, tempDir);
            workDir = tempDir;
        }

        try {
            // Find tdata directory inside
            File tdata = findTDataDir(workDir);
            if (tdata == null) {
                tdata = workDir;
            }

            // Search for key file and account file
            File keyFile = new File(tdata, "key_datas");
            if (!keyFile.exists()) {
                keyFile = new File(tdata, "key_data");
            }

            // Find account files like D87FB6624206DC08 or maps or data
            File[] files = tdata.listFiles();
            File accountFile = null;
            if (files != null) {
                for (File f : files) {
                    String fn = f.getName();
                    if (fn.length() >= 16 && !fn.startsWith("key_") && !fn.endsWith("s") && !fn.equals("settings")) {
                        accountFile = f;
                        break;
                    }
                }
            }

            if (accountFile == null) {
                accountFile = new File(tdata, "data");
            }

            if (!accountFile.exists()) {
                throw new IllegalArgumentException("Не найден файл данных аккаунта в tdata");
            }

            // If standard tdata without local passcode, read raw decrypted stream or direct keys
            byte[] accBytes = readFileToBytes(accountFile);
            SessionData data = extractKeyFromTDataStream(accBytes);
            if (data != null) {
                return data;
            }

            throw new IllegalArgumentException("Не удалось прочитать ключ из TData (возможно, установлен облачный пароль на TDesktop)");
        } finally {
            if (tempDir != null) {
                deleteDir(tempDir);
            }
        }
    }

    private static SessionData extractKeyFromTDataStream(byte[] bytes) {
        // TData files have TDF$ magic (0x54444624) or encrypted blocks
        // We search for standard 256-byte auth key sequence with valid DC id (1..5)
        if (bytes == null || bytes.length < 264) return null;

        // Search through stream for DC ID + 256 bytes auth key pattern
        for (int i = 0; i <= bytes.length - 260; i++) {
            int dc = bytes[i] & 0xFF;
            if (dc >= 1 && dc <= 5) {
                byte[] candidate = Arrays.copyOfRange(bytes, i + 4, i + 4 + 256);
                if (isValidAuthKey(candidate)) {
                    SessionData data = new SessionData();
                    data.dcId = dc;
                    data.authKey = candidate;
                    return data;
                }
            }
        }
        return null;
    }

    private static boolean isValidAuthKey(byte[] key) {
        if (key == null || key.length != 256) return false;
        // Check not all zeroes
        int zeroes = 0;
        for (byte b : key) {
            if (b == 0) zeroes++;
        }
        return zeroes < 200; // Realistic auth key has high entropy
    }

    /**
     * Import session into Telegram Android account slot!
     */
    public static boolean importSession(SessionData session, int targetSlot) {
        try {
            if (session == null || session.authKey == null || session.authKey.length != 256) {
                FileLog.e("HoldGramSessionImporter: Invalid session auth key");
                return false;
            }

            int slot = targetSlot;
            if (slot < 0) {
                // Find first free slot
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (!AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                        slot = a;
                        break;
                    }
                }
                if (slot < 0) {
                    slot = UserConfig.selectedAccount; // Fallback to current
                }
            }

            File filesDir = ApplicationLoader.getFilesDirFixed();
            File accountDir = slot == 0 ? filesDir : new File(filesDir, "account" + slot);
            accountDir.mkdirs();

            // 1. Calculate authKeyPermId: lower 64 bits of SHA1(authKey) in little endian
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] sha1 = md.digest(session.authKey);
            long authKeyId = 0;
            for (int i = 0; i < 8; i++) {
                authKeyId |= ((long) (sha1[12 + i] & 0xFF)) << (i * 8);
            }

            // 2. Determine IP address for DC
            String ip = session.serverAddress;
            if (ip == null || ip.isEmpty()) {
                if (session.dcId >= 1 && session.dcId <= 5) {
                    ip = DC_DEFAULT_IPS[session.dcId];
                } else {
                    ip = DC_DEFAULT_IPS[2];
                }
            }
            int port = session.port > 0 ? session.port : 443;

            // 3. Serialize tgnet.dat
            SerializedData data = new SerializedData();
            data.writeInt32(5); // configVersion
            data.writeBool(false); // testBackend
            data.writeBool(false); // clientBlocked
            data.writeString("en"); // lastInitSystemLangcode
            data.writeBool(true); // hasCurrentDatacenter
            data.writeInt32(session.dcId); // currentDatacenterId
            data.writeInt32(0); // timeDifference
            data.writeInt32((int) (System.currentTimeMillis() / 1000)); // lastDcUpdateTime
            data.writeInt64(Utilities.random.nextLong()); // pushSessionId
            data.writeBool(false); // registeredForInternalPush
            data.writeInt32((int) (System.currentTimeMillis() / 1000)); // lastServerTime
            data.writeInt32(0); // sessionsToDestroy count
            data.writeInt32(1); // datacenters count

            // Datacenter serialization (version 13 format)
            data.writeInt32(13); // datacenter version
            data.writeInt32(session.dcId);
            data.writeInt32(0); // lastInitVersion
            data.writeInt32(0); // lastInitMediaVersion

            // 4 address arrays:
            // 0: IPv4
            data.writeInt32(1); // count = 1
            data.writeString(ip);
            data.writeInt32(port);
            data.writeInt32(0); // flags
            data.writeString(""); // secret

            // 1: IPv6
            data.writeInt32(0);
            // 2: IPv4Download
            data.writeInt32(0);
            // 3: IPv6Download
            data.writeInt32(0);

            data.writeBool(false); // isCdnDatacenter
            data.writeInt32(256); // authKeyPerm length
            data.writeBytes(session.authKey);
            data.writeInt64(authKeyId); // authKeyPermId
            data.writeInt32(0); // authKeyTemp len
            data.writeInt64(0); // authKeyTempId
            data.writeInt32(0); // authKeyMediaTemp len
            data.writeInt64(0); // authKeyMediaTempId
            data.writeInt32(1); // authorized = true
            data.writeInt32(0); // serverSalts count
            data.writeInt32(0); // mediaServerSalts count

            // Write to file tgnet.dat
            File tgnetFile = new File(accountDir, "tgnet.dat");
            try (FileOutputStream fos = new FileOutputStream(tgnetFile)) {
                fos.write(data.toByteArray());
                fos.flush();
            }
            data.cleanup();

            // 4. Configure UserConfig for this slot
            UserConfig userConfig = UserConfig.getInstance(slot);
            userConfig.clearConfig();

            TLRPC.User user = new TLRPC.TL_user();
            user.id = session.userId != 0 ? session.userId : 777000;
            user.first_name = "HoldGram User";
            user.flags = 0;
            if (session.phone != null && !session.phone.isEmpty()) {
                user.phone = session.phone;
            }
            userConfig.setCurrentUser(user);
            userConfig.clientUserId = user.id;
            userConfig.saveConfig(true);

            // 5. Connect and update DC settings
            final int finalSlot = slot;
            AndroidUtilities.runOnUIThread(() -> {
                ConnectionsManager.getInstance(finalSlot).checkConnection();
                ConnectionsManager.getInstance(finalSlot).updateDcSettings();
                MessagesController.getInstance(finalSlot).loadAppConfig(true);
            });

            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static byte[] parseKeyBytes(String str) {
        str = str.trim();
        if (str.length() == 512) {
            // Hex string 512 chars -> 256 bytes
            return Utilities.hexToBytes(str);
        }
        try {
            byte[] decoded = Base64.decode(str, Base64.DEFAULT);
            if (decoded.length == 256) return decoded;
        } catch (Exception ignore) {}
        return null;
    }

    private static String readFileToString(File file) throws Exception {
        byte[] bytes = readFileToBytes(file);
        return new String(bytes, "UTF-8");
    }

    private static byte[] readFileToBytes(File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static File findTDataDir(File dir) {
        if (dir.getName().equalsIgnoreCase("tdata")) return dir;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    if (child.getName().equalsIgnoreCase("tdata")) return child;
                    File found = findTDataDir(child);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static void unzip(File zipFile, File targetDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
