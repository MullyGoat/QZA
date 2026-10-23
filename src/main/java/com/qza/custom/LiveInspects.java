package com.qza.custom;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.tweaks.ItemInspect;
import com.qza.util.SkyBlockArea;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.player.ProfileKeyPair;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class LiveInspects {
    private static final URI LIVE = URI.create("wss://qza-items.qza.workers.dev/live");
    private static final String HELLO_PREFIX = "qza-live\n";
    private static final long RETRY_MS = 30_000L;
    private static final long KEY_RETRY_MS = 600_000L;
    private static final long PING_MS = 40_000L;

    private static volatile WebSocket socket;
    private static volatile boolean connecting;
    private static volatile boolean ready;
    private static volatile long failedAt;
    private static volatile long retryAfter = RETRY_MS;
    private static long pingedAt;
    private static CompletableFuture<?> outbox = CompletableFuture.completedFuture(null);

    private LiveInspects() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(LiveInspects::tick);
    }

    private static boolean wanted(Minecraft mc) {
        QZAConfig cfg = ConfigManager.get();
        return cfg.sharedItemsEnabled && (cfg.sharedItemsShow || cfg.sharedItemsShare) && mc.player != null
                && SkyBlockArea.onSkyBlock();
    }

    private static void tick(Minecraft mc) {
        if (!wanted(mc)) {
            close();
            return;
        }
        long now = System.currentTimeMillis();
        WebSocket ws = socket;
        if (ws == null) {
            if (!connecting && now - failedAt > retryAfter) {
                connect(mc);
            }
            return;
        }
        if (now - pingedAt > PING_MS) {
            pingedAt = now;
            queue(() -> ws.sendPing(ByteBuffer.allocate(0)));
        }
    }

    private static void connect(Minecraft mc) {
        connecting = true;
        User user = mc.getUser();
        mc.getProfileKeyPairManager().prepareKeyPair().thenCompose(found -> {
            ProfileKeyPair pair = found.orElse(null);
            if (pair == null) {
                throw new CompletionException(new GeneralSecurityException("no Minecraft chat key"));
            }
            JsonObject hello;
            try {
                hello = SharedItems.signed(user, pair, HELLO_PREFIX, null);
            } catch (GeneralSecurityException e) {
                throw new CompletionException(e);
            }
            hello.addProperty("type", "hello");
            return SharedItems.http().newWebSocketBuilder().buildAsync(LIVE, new Listener(hello.toString()));
        }).whenComplete((ws, t) -> {
            connecting = false;
            if (t != null) {
                Throwable cause = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
                failed(cause instanceof GeneralSecurityException ? KEY_RETRY_MS : RETRY_MS);
                QZA.LOGGER.info("QZA couldn't connect to live inspects: {}", cause.getMessage());
            } else {
                socket = ws;
                pingedAt = System.currentTimeMillis();
            }
        });
    }

    private static void failed(long wait) {
        failedAt = System.currentTimeMillis();
        retryAfter = wait;
        socket = null;
        ready = false;
    }

    private static synchronized void queue(Supplier<CompletableFuture<WebSocket>> send) {
        outbox = outbox.handle((v, t) -> null).thenCompose(v -> send.get());
    }

    private static void close() {
        WebSocket ws = socket;
        socket = null;
        ready = false;
        if (ws != null) {
            queue(() -> ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye"));
        }
    }

    public static void sent(String anim, double speed) {
        WebSocket ws = socket;
        QZAConfig cfg = ConfigManager.get();
        if (ws == null || !ready || !cfg.sharedItemsEnabled || !cfg.sharedItemsShare || anim == null) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("type", "inspect");
        body.addProperty("a", anim);
        body.addProperty("s", speed);
        String text = body.toString();
        queue(() -> ws.sendText(text, true));
    }

    static void handle(String text) {
        JsonObject body;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return;
            }
            body = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String type = body.has("type") ? body.get("type").getAsString() : "";
        switch (type) {
            case "ready" -> {
                ready = true;
                retryAfter = RETRY_MS;
            }
            case "error" -> QZA.LOGGER.info("QZA live inspects refused the connection: {}",
                    body.has("error") ? body.get("error").getAsString() : "");
            case "inspect" -> {
                UUID player = uuid(body.has("u") ? body.get("u").getAsString() : "");
                String anim = body.has("a") ? body.get("a").getAsString() : "";
                double speed = body.has("s") && body.get("s").isJsonPrimitive() ? body.get("s").getAsDouble() : 1.0;
                if (player != null && ConfigManager.get().sharedItemsShow) {
                    Minecraft.getInstance().execute(() -> ItemInspect.remote(player, anim, speed));
                }
            }
            default -> {
            }
        }
    }

    private static UUID uuid(String flat) {
        if (flat.length() != 32) {
            return null;
        }
        try {
            return UUID.fromString(flat.toLowerCase(Locale.ROOT)
                    .replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final String hello;
        private final StringBuilder text = new StringBuilder();

        private Listener(String hello) {
            this.hello = hello;
        }

        @Override
        public void onOpen(WebSocket ws) {
            queue(() -> ws.sendText(hello, true));
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String message = text.toString();
                text.setLength(0);
                handle(message);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
            if (socket == ws) {
                failed(RETRY_MS);
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (socket == ws) {
                failed(RETRY_MS);
            }
            QZA.LOGGER.info("QZA live inspects disconnected: {}", error.getMessage());
        }
    }
}
