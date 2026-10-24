package tiktokpopup.tiktok;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tiktokpopup.Log;
import tiktokpopup.net.Http;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Koneksi ke live TikTok: resolve room id, WebSocket + polling IM fetch, auto-reconnect.
 * Jika user belum live, klien menunggu dan mencoba lagi tiap beberapa detik.
 */
public final class TikTokClient {
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("\"roomId\"\\s*:\\s*\"?(\\d+)\"?");
    private static final Pattern ROOM_ID_ALT = Pattern.compile("room_id=(\\d+)");

    private static final int MAX_SEEN_MESSAGE_IDS = 5000;
    private static final long HANDSHAKE_TIMEOUT_MS = 20_000L;
    private static final long RECONNECT_DELAY_MS = 3_000L;
    private static final long NOT_LIVE_RETRY_MS = 10_000L;

    private final String username;
    private final TikTokListener listener;
    private volatile boolean running;
    private volatile boolean debugEvents;
    private volatile Thread workerThread;
    private volatile Thread fetchThread;
    private volatile TikTokWebSocket webSocket;
    private String roomId;
    private volatile String fetchCursor = "";
    private final Set<Long> seenMessageIds = new LinkedHashSet<>();
    private TikTokListener.State lastState;

    public TikTokClient(String username, TikTokListener listener) {
        this.username = username.trim().replace("@", "");
        this.listener = listener;
        this.running = true;
        start();
    }

    public String getUsername() {
        return username;
    }

    public boolean isRunning() {
        return running;
    }

    /** Log semua event TikTok yang tidak dikenali ke latest.log (untuk diagnosis). */
    public void setDebugEvents(boolean enabled) {
        this.debugEvents = enabled;
        TikTokWebSocket ws = webSocket;
        if (ws != null) {
            ws.setDebugEvents(enabled);
        }
    }

    public void disconnect() {
        running = false;
        disconnectWebSocket();
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void start() {
        workerThread = new Thread(this::connectLoop, "TikTokLive-" + username);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void setState(TikTokListener.State state, String detail) {
        if (!running && state != TikTokListener.State.DISCONNECTED) {
            return;
        }
        if (state == lastState && state != TikTokListener.State.ERROR) {
            return;
        }
        lastState = state;
        try {
            listener.onState(state, detail);
        } catch (RuntimeException e) {
            Log.LOGGER.warn("TikTok state listener failed", e);
        }
    }

    private void connectLoop() {
        while (running) {
            try {
                if (roomId == null) {
                    setState(TikTokListener.State.CONNECTING, username);
                    roomId = resolveRoomId(username);
                    Log.LOGGER.info("TikTok room resolved for @{}: {}", username, roomId);
                }

                String ttwid = Http.fetchTtwidCookie(username);
                connectWebSocket(ttwid);

                long handshakeStarted = System.currentTimeMillis();
                boolean announcedOpen = false;
                while (running && webSocket != null && webSocket.isRunning()) {
                    if (!announcedOpen && webSocket.isOpen()) {
                        announcedOpen = true;
                        setState(TikTokListener.State.CONNECTED, username);
                    }
                    if (webSocket.isConnecting()
                        && System.currentTimeMillis() - handshakeStarted > HANDSHAKE_TIMEOUT_MS) {
                        Log.LOGGER.warn("TikTok WSS handshake timed out for @{}", username);
                        break;
                    }
                    Thread.sleep(500);
                }
                if (running) {
                    disconnectWebSocket();
                    if (roomId != null) {
                        setState(TikTokListener.State.CONNECTING, username);
                    }
                    sleepQuietly(RECONNECT_DELAY_MS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (NotLiveException e) {
                if (!running) {
                    break;
                }
                disconnectWebSocket();
                roomId = null;
                seenMessageIds.clear();
                setState(TikTokListener.State.WAITING_FOR_LIVE, username);
                sleepQuietly(NOT_LIVE_RETRY_MS);
            } catch (Exception e) {
                if (!running || Thread.currentThread().isInterrupted() || isInterruption(e)) {
                    break;
                }
                Log.LOGGER.warn("TikTok error for @{}: {}", username, e.getMessage());
                disconnectWebSocket();
                roomId = null;
                seenMessageIds.clear();
                setState(TikTokListener.State.ERROR, String.valueOf(e.getMessage()));
                sleepQuietly(NOT_LIVE_RETRY_MS);
            }
        }
        setState(TikTokListener.State.DISCONNECTED, username);
    }

    private void connectWebSocket(String ttwid) {
        String currentRoomId = roomId;
        webSocket = new TikTokWebSocket(currentRoomId, ttwid, new TikTokWebSocket.ChatHandler() {
            @Override
            public void onChat(long msgId, String uniqueId, String displayName, String text, TikTokRoles roles) {
                if (markSeen(msgId)) {
                    listener.onChat(uniqueId, displayName, text, roles);
                }
            }

            @Override
            public void onGift(long msgId, String uniqueId, String displayName, String giftName, int repeatCount) {
                if (markSeen(msgId)) {
                    listener.onGift(uniqueId, displayName, giftName, repeatCount);
                }
            }

            @Override
            public void onFollow(long msgId, String uniqueId, String displayName) {
                if (markSeen(msgId)) {
                    listener.onFollow(uniqueId, displayName);
                }
            }

            @Override
            public void onFans(long msgId, String uniqueId, String displayName) {
                if (markSeen(msgId)) {
                    listener.onFans(uniqueId, displayName);
                }
            }

            @Override
            public void onLikes(long msgId, String uniqueId, String displayName, int added, long total) {
                if (markSeen(msgId)) {
                    listener.onLikes(uniqueId, displayName, added, total);
                }
            }

            @Override
            public void onViewers(long viewers) {
                listener.onViewers(viewers);
            }

            @Override
            public void onLiveEnded() {
                Log.LOGGER.info("TikTok live ended for @{}", username);
                roomId = null;
                seenMessageIds.clear();
                disconnectWebSocket();
                setState(TikTokListener.State.LIVE_ENDED, username);
            }
        });
        webSocket.setDebugEvents(debugEvents);

        TikTokImFetch.Result bootstrap = TikTokImFetch.tryFetch(currentRoomId, "");
        if (bootstrap != null) {
            fetchCursor = bootstrap.cursor();
            // Riwayat hanya dipakai untuk statistik awal; chat/gift lama tidak memicu notif/sound
            webSocket.ingestStatsOnly(bootstrap.messages());
            Log.LOGGER.info("TikTok IM fetch for @{}: {} messages", username, bootstrap.messages().size());
        }
        webSocket.connect(TikTokWssUrl.build(
            currentRoomId,
            bootstrap != null ? bootstrap.cursor() : "",
            bootstrap != null ? bootstrap.internalExt() : "",
            bootstrap != null && bootstrap.hasPushServer() ? bootstrap.pushServer() : null
        ));
        startFetchPoll(currentRoomId, webSocket, bootstrap);
    }

    private void startFetchPoll(String currentRoomId, TikTokWebSocket socket, TikTokImFetch.Result bootstrap) {
        stopFetchPoll();
        if (bootstrap == null) {
            return;
        }
        fetchThread = new Thread(() -> {
            long intervalMs = bootstrap.fetchIntervalMs();
            while (running && webSocket == socket && socket.isRunning()) {
                sleepQuietly(intervalMs);
                if (!running || webSocket != socket) {
                    break;
                }
                TikTokImFetch.Result next = TikTokImFetch.tryFetch(currentRoomId, fetchCursor);
                if (next == null) {
                    continue;
                }
                if (next.hasCursor()) {
                    fetchCursor = next.cursor();
                }
                intervalMs = next.fetchIntervalMs();
                socket.ingest(next.messages());
            }
        }, "TikTokFetch-" + username);
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    private void stopFetchPoll() {
        Thread thread = fetchThread;
        fetchThread = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** Buang duplikat berdasarkan msgId; false = sudah pernah diproses. */
    private boolean markSeen(long msgId) {
        synchronized (seenMessageIds) {
            if (msgId != 0 && !seenMessageIds.add(msgId)) {
                return false;
            }
            while (seenMessageIds.size() > MAX_SEEN_MESSAGE_IDS) {
                var iterator = seenMessageIds.iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
        }
        return true;
    }

    private void disconnectWebSocket() {
        stopFetchPoll();
        TikTokWebSocket ws = webSocket;
        webSocket = null;
        if (ws != null) {
            ws.disconnect();
        }
    }

    private static String resolveRoomId(String user) throws IOException {
        // Cara utama: api-live (halaman /live sekarang ditutup WAF).
        // sourceType=54 wajib, tanpa itu API membalas params_error.
        String apiUrl = "https://www.tiktok.com/api-live/user/room/?aid=1988&sourceType=54&uniqueId="
            + URLEncoder.encode(user, StandardCharsets.UTF_8);
        String apiBody = Http.get(apiUrl, "https://www.tiktok.com/");
        String roomId = null;
        Integer status = null;
        try {
            JsonObject json = JsonParser.parseString(apiBody).getAsJsonObject();
            if (json.has("data") && json.get("data").isJsonObject()) {
                JsonObject data = json.getAsJsonObject("data");
                if (data.has("user") && data.get("user").isJsonObject()) {
                    JsonObject userObj = data.getAsJsonObject("user");
                    if (userObj.has("roomId") && !userObj.get("roomId").isJsonNull()) {
                        roomId = userObj.get("roomId").getAsString();
                    }
                    if (userObj.has("status") && !userObj.get("status").isJsonNull()) {
                        status = userObj.get("status").getAsInt();
                    }
                }
            }
        } catch (Exception ignored) {
            // bukan JSON: coba cara cadangan di bawah
        }

        // Status 2 = sedang live; 4 = selesai/offline
        if (status != null && status != 2) {
            throw new NotLiveException("@" + user + " is not live right now (status " + status + ")");
        }
        if (roomId != null && !roomId.isBlank() && !"0".equals(roomId)) {
            return roomId;
        }

        // Cadangan: parse HTML halaman /live (bisa diblokir WAF)
        String liveUrl = "https://www.tiktok.com/@" + URLEncoder.encode(user, StandardCharsets.UTF_8) + "/live";
        String html = Http.get(liveUrl, "https://www.tiktok.com/");
        roomId = firstMatch(ROOM_ID_PATTERN, html);
        if (roomId == null) {
            roomId = firstMatch(ROOM_ID_ALT, html);
        }
        if (roomId == null || roomId.isBlank()) {
            throw new NotLiveException("No live room found for @" + user + " (user not found or not live)");
        }
        return roomId;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Ganti username menginterupsi HTTP dan dibungkus IOException; itu penghentian, bukan error. */
    private static boolean isInterruption(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
