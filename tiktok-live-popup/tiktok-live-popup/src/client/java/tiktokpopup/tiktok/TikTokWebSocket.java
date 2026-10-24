package tiktokpopup.tiktok;

import tiktokpopup.Log;
import tiktokpopup.net.Http;
import tiktokpopup.tiktok.proto.TikTokProto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

final class TikTokWebSocket {
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    private static final long STALE_CHECK_INTERVAL_MS = 5_000L;
    private static final long STALE_TIMEOUT_MS = 60_000L;
    private static final int MAX_FRAME_BYTES = 16 * 1024 * 1024;
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();

    /** Callback ke {@link TikTokClient}; dipanggil dari thread jaringan. */
    interface ChatHandler {
        void onChat(long msgId, String uniqueId, String displayName, String text, TikTokRoles roles);

        /** Gift; {@code giftName} bisa kosong jika TikTok tidak mengirim detail. */
        void onGift(long msgId, String uniqueId, String displayName, String giftName, int repeatCount);

        void onFollow(long msgId, String uniqueId, String displayName);

        /** Fans club / subscribe. */
        void onFans(long msgId, String uniqueId, String displayName);

        /** {@code added} = jumlah like pada tap ini, {@code total} = total like room (0 jika tidak dikirim). */
        void onLikes(long msgId, String uniqueId, String displayName, int added, long total);

        void onViewers(long viewers);

        void onLiveEnded();
    }

    private enum SocketState { CONNECTING, OPEN, DEAD }

    private final String roomId;
    private final String ttwid;
    private final ChatHandler handler;
    private volatile boolean running;
    private volatile SocketState state = SocketState.DEAD;
    private volatile WebSocket webSocket;
    private Thread maintenanceThread;
    private volatile boolean debugEvents;
    private volatile boolean statsOnly;

    TikTokWebSocket(String roomId, String ttwid, ChatHandler handler) {
        this.roomId = roomId;
        this.ttwid = ttwid;
        this.handler = handler;
    }

    void setDebugEvents(boolean enabled) {
        this.debugEvents = enabled;
    }

    void connect() {
        connect(TikTokWssUrl.build(roomId));
    }

    void connect(String url) {
        running = true;
        state = SocketState.CONNECTING;
        HTTP_CLIENT.newWebSocketBuilder()
            .header("Cookie", buildCookieHeader())
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://www.tiktok.com")
            .header("Referer", "https://www.tiktok.com/")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .buildAsync(URI.create(url), new Listener())
            .whenComplete((ws, error) -> {
                if (error != null && running) {
                    state = SocketState.DEAD;
                    Log.LOGGER.warn("TikTok WSS handshake failed: {}", error.getMessage());
                }
            });
    }

    void disconnect() {
        running = false;
        state = SocketState.DEAD;
        WebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
        Thread thread = maintenanceThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    boolean isRunning() {
        return running && state != SocketState.DEAD;
    }

    boolean isOpen() {
        return running && state == SocketState.OPEN;
    }

    boolean isConnecting() {
        return running && state == SocketState.CONNECTING;
    }

    /** Pesan dari HTTP {@code /im/fetch/} — diproses sama seperti frame WSS. */
    void ingest(List<TikTokProto.ProtoMap> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (TikTokProto.ProtoMap message : messages) {
            dispatchMessage(message);
        }
    }

    /** Hanya proses statistik (like/viewer) dari riwayat; chat/gift lama tidak memicu notif. */
    void ingestStatsOnly(List<TikTokProto.ProtoMap> messages) {
        statsOnly = true;
        try {
            ingest(messages);
        } finally {
            statsOnly = false;
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final ByteArrayOutputStream binaryBuffer = new ByteArrayOutputStream();
        private final AtomicLong lastDataMs = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong lastHeartbeatSentMs = new AtomicLong(0L);

        @Override
        public void onOpen(WebSocket ws) {
            webSocket = ws;
            state = SocketState.OPEN;
            sendBinary(ws, TikTokFrames.buildHeartbeat(roomId));
            sendBinary(ws, TikTokFrames.buildEnterRoom(roomId));
            lastHeartbeatSentMs.set(System.currentTimeMillis());
            startMaintenance(ws);
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            lastDataMs.set(System.currentTimeMillis());
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            if (binaryBuffer.size() + chunk.length > MAX_FRAME_BYTES) {
                binaryBuffer.reset();
                Log.LOGGER.warn("TikTok WSS frame too large");
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "frame too large");
                ws.request(1);
                return null;
            }
            binaryBuffer.write(chunk, 0, chunk.length);

            if (last) {
                byte[] raw = binaryBuffer.toByteArray();
                binaryBuffer.reset();
                try {
                    processFrame(raw, ws);
                } catch (Exception e) {
                    Log.LOGGER.debug("TikTok WSS frame skip: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            webSocket = null;
            state = SocketState.DEAD;
            if (running) {
                Log.LOGGER.info("TikTok WSS closed ({} {}): reconnecting", statusCode, reason);
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            state = SocketState.DEAD;
            if (running) {
                Log.LOGGER.warn("TikTok WSS error: {}", error.getMessage());
            }
        }

        private void startMaintenance(WebSocket ws) {
            maintenanceThread = daemon("TikTokWss-" + roomId, () -> {
                while (running && webSocket == ws) {
                    try {
                        Thread.sleep(STALE_CHECK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (!running || webSocket != ws) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastDataMs.get() > STALE_TIMEOUT_MS) {
                        Log.LOGGER.info("TikTok WSS stale for @{}, reconnecting", roomId);
                        state = SocketState.DEAD;
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "stale");
                        return;
                    }
                    if (now - lastHeartbeatSentMs.get() >= HEARTBEAT_INTERVAL_MS) {
                        sendBinary(ws, TikTokFrames.buildHeartbeat(roomId));
                        lastHeartbeatSentMs.set(now);
                    }
                }
            });
        }
    }

    private void processFrame(byte[] raw, WebSocket ws) throws IOException {
        TikTokProto.ProtoMap frame;
        try {
            frame = TikTokProto.decode(raw);
        } catch (IllegalArgumentException e) {
            Log.LOGGER.debug("TikTok WSS outer frame parse skip: {}", e.getMessage());
            return;
        }
        String payloadType = frame.getString(7);
        if ("hb".equals(payloadType) || "ack".equals(payloadType)) {
            return;
        }

        byte[] payload = TikTokFrames.decompressIfGzipped(frame.getRawBytes(8));
        TikTokProto.ProtoMap response;
        try {
            response = TikTokProto.decode(payload);
        } catch (IllegalArgumentException e) {
            Log.LOGGER.debug("TikTok WSS payload parse skip: {}", e.getMessage());
            return;
        }

        boolean needsAck = response.getBool(9);
        byte[] internalExt = response.getRawBytes(5);
        if (needsAck && internalExt.length > 0) {
            long logId = frame.getVarint(2);
            sendBinary(ws, TikTokFrames.buildAck(logId, internalExt));
        }

        for (TikTokProto.ProtoMap message : response.getRepeatedMessages(1)) {
            dispatchMessage(message);
        }
    }

    private String buildCookieHeader() {
        String cookies = Http.cookieHeader("https://www.tiktok.com/");
        if (ttwid == null || ttwid.isBlank()) {
            return cookies;
        }
        if (cookies.contains("ttwid=")) {
            return cookies;
        }
        return cookies.isEmpty() ? "ttwid=" + ttwid : cookies + "; ttwid=" + ttwid;
    }

    private void dispatchMessage(TikTokProto.ProtoMap message) {
        try {
            String method = message.getString(1);
            byte[] payload = message.getRawBytes(2);
            long wrapperId = message.getVarint(3);
            if (statsOnly) {
                if (methodEndsWith(method, "WebcastLikeMessage")) {
                    handleLike(payload, wrapperId);
                } else if (methodEndsWith(method, "WebcastRoomUserSeqMessage")) {
                    handleRoomUserSeq(payload);
                }
                return;
            }
            if (methodEndsWith(method, "WebcastEmoteChatMessage")) {
                handleEmoteChat(payload, wrapperId);
            } else if (methodEndsWith(method, "WebcastChatMessage")) {
                handleChat(payload, wrapperId);
            } else if (methodEndsWith(method, "WebcastGiftMessage")) {
                handleGift(payload, wrapperId);
            } else if (methodEndsWith(method, "WebcastSocialMessage")) {
                handleSocial(payload, wrapperId);
            } else if (methodEndsWith(method, "WebcastLikeMessage")) {
                handleLike(payload, wrapperId);
            } else if (methodEndsWith(method, "WebcastRoomUserSeqMessage")) {
                handleRoomUserSeq(payload);
            } else if (methodContains(method, "fansclub") || methodContains(method, "fanslevel")
                || methodContains(method, "subnotify") || methodContains(method, "subscribe")) {
                handleFansMessage(method, payload, wrapperId);
            } else if (methodEndsWith(method, "WebcastControlMessage")) {
                TikTokProto.ProtoMap control = TikTokProto.decode(payload);
                if (control.getInt(2) == 3) {
                    handler.onLiveEnded();
                }
            } else if (debugEvents && method != null && !method.isBlank()
                && !methodEndsWith(method, "WebcastMemberMessage")) {
                Log.LOGGER.info("TikTok event (unhandled): {}", method);
            }
        } catch (IllegalArgumentException e) {
            Log.LOGGER.debug("TikTok WSS message parse skip: {}", e.getMessage());
        }
    }

    private void handleChat(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap chat = TikTokProto.decode(payload);
        String text = extractChatText(chat);
        if (text.isBlank()) {
            return;
        }
        UserIdentity user = extractUserSafe(chat);
        handler.onChat(resolveMsgId(chat, wrapperMsgId), user.uniqueId(), user.displayName(), text, user.roles());
    }

    /** Emote-only chat: gambar emote tidak bisa dirender di HUD, tampilkan sebagai teks. */
    private void handleEmoteChat(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap chat = TikTokProto.decode(payload);
        UserIdentity user = extractUserSafe(chat);
        handler.onChat(resolveMsgId(chat, wrapperMsgId), user.uniqueId(), user.displayName(), "[emote]", user.roles());
    }

    private void handleGift(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap gift = TikTokProto.decode(payload);
        TikTokProto.ProtoMap details = gift.getMessage(15);
        // Gift beruntun dikirim tiap tap: hanya tampilkan saat streak selesai
        if (details.getVarint(11) == 1L && gift.getVarint(9) == 0L) {
            return;
        }
        UserIdentity user = extractUserSafe(gift, 7);
        handler.onGift(
            resolveMsgId(gift, wrapperMsgId),
            user.uniqueId(),
            user.displayName(),
            readableText(details.getString(16)),
            (int) Math.max(1L, gift.getVarint(5))
        );
    }

    /** WebcastSocialMessage dipakai untuk follow, share, dan (kadang) subscribe. */
    private void handleSocial(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap social = TikTokProto.decode(payload);
        TikTokProto.ProtoMap eventDetails = social.getMessage(1).getMessage(8);
        String hint = (eventDetails.getString(1) + ' ' + eventDetails.getString(2))
            .toLowerCase(java.util.Locale.ROOT);
        UserIdentity user = extractUserSafe(social, 2);
        long msgId = resolveMsgId(social, wrapperMsgId);
        if (hint.contains("follow")) {
            handler.onFollow(msgId, user.uniqueId(), user.displayName());
        } else if (hint.contains("subscribe") || hint.contains("fans")) {
            handler.onFans(msgId, user.uniqueId(), user.displayName());
        } else if (debugEvents) {
            Log.LOGGER.info("TikTok social event (ignored): {}", hint.trim());
        }
    }

    /**
     * WebcastLikeMessage: field 2 = jumlah like pada tap ini, field 3 = total like room,
     * field 5 = user.
     */
    private void handleLike(byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap like = TikTokProto.decode(payload);
        UserIdentity user = extractUserSafe(like, 5);
        handler.onLikes(
            resolveMsgId(like, wrapperMsgId),
            user.uniqueId(),
            user.displayName(),
            (int) Math.max(0L, like.getVarint(2)),
            Math.max(0L, like.getVarint(3))
        );
    }

    /** WebcastRoomUserSeqMessage: field 3 = jumlah viewer saat ini. */
    private void handleRoomUserSeq(byte[] payload) {
        TikTokProto.ProtoMap seq = TikTokProto.decode(payload);
        long viewers = seq.getVarint(3);
        if (viewers > 0L) {
            handler.onViewers(viewers);
        }
    }

    /**
     * Pesan fans club / subscribe. Format protobuf-nya bervariasi, jadi user dicari
     * dari beberapa field kandidat.
     */
    private void handleFansMessage(String method, byte[] payload, long wrapperMsgId) {
        TikTokProto.ProtoMap msg = TikTokProto.decode(payload);
        UserIdentity user = findUserInMessage(msg);
        if (user == null) {
            if (debugEvents) {
                Log.LOGGER.info("TikTok fans event without user: {}", method);
            }
            return;
        }
        handler.onFans(resolveMsgId(msg, wrapperMsgId), user.uniqueId(), user.displayName());
    }

    private static UserIdentity findUserInMessage(TikTokProto.ProtoMap msg) {
        for (int field : new int[] {2, 4, 3, 5, 6, 7, 8, 1}) {
            try {
                TikTokProto.ProtoMap candidate = msg.getMessage(field);
                String name = firstNonBlank(candidate.getString(38), candidate.getString(3));
                if (!name.isBlank() && (candidate.getVarint(1) != 0L || candidate.getString(38).length() > 0)) {
                    return extractUser(candidate);
                }
            } catch (RuntimeException ignored) {
                // coba field berikutnya
            }
        }
        return null;
    }

    private static long resolveMsgId(TikTokProto.ProtoMap chat, long wrapperMsgId) {
        try {
            return firstNonZero(chat.getMessage(1).getVarint(2), wrapperMsgId);
        } catch (RuntimeException ignored) {
            return wrapperMsgId;
        }
    }

    private static UserIdentity extractUserSafe(TikTokProto.ProtoMap chat) {
        return extractUserSafe(chat, 2);
    }

    private static UserIdentity extractUserSafe(TikTokProto.ProtoMap message, int userField) {
        try {
            return extractUser(message.getMessage(userField));
        } catch (RuntimeException ignored) {
            return new UserIdentity("Viewer", "Viewer", TikTokRoles.NONE);
        }
    }

    private static boolean methodEndsWith(String method, String suffix) {
        return method != null && method.endsWith(suffix);
    }

    private static boolean methodContains(String method, String lowerNeedle) {
        return method != null && method.toLowerCase(java.util.Locale.ROOT).contains(lowerNeedle);
    }

    private static long firstNonZero(long... values) {
        for (long value : values) {
            if (value != 0L) {
                return value;
            }
        }
        return 0L;
    }

    /**
     * Текст комментария: field 3 (content) и более новые CommentContent (22/29/…).
     * Не трогаем field 1 (common) и 2 (user) — там ник и служебные строки.
     */
    private static final int[] CHAT_TEXT_FIELDS = {3, 4, 22, 29, 30, 31};

    private static String extractChatText(TikTokProto.ProtoMap chat) {
        for (int field : CHAT_TEXT_FIELDS) {
            String text = readableFromField(chat, field);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private static String readableFromField(TikTokProto.ProtoMap map, int field) {
        String direct = readableText(map.getString(field));
        if (!direct.isBlank()) {
            return direct;
        }
        TikTokProto.ProtoMap nested = map.getMessage(field);
        for (int inner : new int[] {1, 2, 3}) {
            String text = readableText(nested.getString(inner));
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    /**
     * Отсекает protobuf, прочитанный как UTF-8 (много U+FFFD / control).
     * Считает кодпоинты, а не UTF-16 char'ы: иначе один эмодзи («😂»)
     * выглядел как 1 printable / 2 length и выбрасывался.
     */
    private static String readableText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int codePoints = 0;
        int printable = 0;
        int replacement = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            codePoints++;
            if (cp == 0xFFFD) {
                replacement++;
                continue;
            }
            if (!Character.isISOControl(cp) || cp == '\n' || cp == '\r' || cp == '\t') {
                printable++;
            }
        }
        if (codePoints == 0 || replacement * 2 >= codePoints || printable * 2 < codePoints) {
            return "";
        }
        return value.trim();
    }

    private static UserIdentity extractUser(TikTokProto.ProtoMap user) {
        String displayName = firstNonBlank(user.getString(3), user.getString(38), "Viewer");
        String uniqueId = firstNonBlank(user.getString(38), user.getString(3));
        if (uniqueId.isBlank() && user.getVarint(1) != 0L) {
            uniqueId = Long.toString(user.getVarint(1));
        }
        if (uniqueId.isBlank()) {
            uniqueId = displayName;
        }
        return new UserIdentity(uniqueId, displayName, rolesFromTikTokUser(user));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** Thread daemon biasa. */
    private Thread daemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private record UserIdentity(
        String uniqueId,
        String displayName,
        TikTokRoles roles
    ) {}

    /** Follower dari {@code FollowInfo.follow_status}; subscriber/mod/host dari teks badge user. */
    private static TikTokRoles rolesFromTikTokUser(TikTokProto.ProtoMap user) {
        boolean follower = tikTokFollowStatus(user) >= 1L;
        String hints = collectTikTokUserRoleHints(user).toLowerCase(java.util.Locale.ROOT);
        boolean subscriber = containsAny(hints, "subscriber", "subscribe", "member", "fanclub", "fan_club");
        boolean moderator = containsAny(hints, "moderator", "admin");
        boolean broadcaster = containsAny(hints, "anchor", "host", "owner", "streamer");
        return new TikTokRoles(follower, subscriber, moderator, broadcaster);
    }

    private static long tikTokFollowStatus(TikTokProto.ProtoMap user) {
        long status = user.getMessage(22).getVarint(3);
        if (status > 0L) {
            return status;
        }
        return user.getMessage(64).getVarint(3);
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Собирает текстовые подсказки о ролях из protobuf-пользователя TikTok. */
    private static String collectTikTokUserRoleHints(TikTokProto.ProtoMap user) {
        try {
            StringBuilder hints = new StringBuilder();
            for (int field : new int[] {9, 11, 22, 46, 61, 102}) {
                appendIfPresent(hints, user.getString(field));
                TikTokProto.ProtoMap nested = user.getMessage(field);
                appendIfPresent(hints, nested.getString(1));
                appendIfPresent(hints, nested.getString(2));
                appendIfPresent(hints, nested.getString(3));
                for (TikTokProto.ProtoMap badge : nested.getRepeatedMessages(1)) {
                    appendIfPresent(hints, badge.getString(1));
                    appendIfPresent(hints, badge.getString(2));
                    appendIfPresent(hints, badge.getString(3));
                }
            }
            return hints.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void appendIfPresent(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(value).append(' ');
        }
    }

    private void sendBinary(WebSocket ws, byte[] payload) {
        if (ws == null || ws.isOutputClosed()) {
            return;
        }
        ws.sendBinary(ByteBuffer.wrap(payload), true).exceptionally(error -> {
            if (running) {
                Log.LOGGER.warn("TikTok WSS send failed: {}", error.getMessage());
            }
            return null;
        });
    }
}
