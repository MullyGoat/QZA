package com.qza.discord;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

public final class DiscordAlert {

    private static final String DEFAULT_RELAY = "https://qza-discord.qza.workers.dev";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static HttpClient client;

    private DiscordAlert() {
    }

    public static boolean linked() {
        String token = ConfigManager.get().discordAlertToken;
        return token != null && !token.isBlank();
    }

    public static boolean active() {
        QZAConfig cfg = ConfigManager.get();
        return cfg.discordAlertEnabled && linked()
                && (cfg.discordAlertDm || cfg.discordAlertChannel);
    }

    private static String url() {
        String configured = ConfigManager.get().discordAlertUrl;
        String base = configured == null || configured.isBlank()
                ? DEFAULT_RELAY : configured.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }
        return client;
    }

    public static void link(Consumer<String> onCode, Consumer<String> onError) {
        JsonObject body = new JsonObject();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {

            body.addProperty("ign", client.player.getGameProfile().name());
        }
        String existing = ConfigManager.get().discordAlertToken;
        if (existing != null && !existing.isBlank()) {
            body.addProperty("replaces", existing.trim());
        }

        send("/link/start", body, null, (answer, problem) -> {
            if (problem != null) {
                onError.accept(problem);
                return;
            }
            String code = string(answer, "code");
            String token = string(answer, "token");
            if (code.isEmpty() || token.isEmpty()) {
                onError.accept("The relay sent back an unusable code.");
                return;
            }

            QZAConfig cfg = ConfigManager.get();
            cfg.discordAlertToken = token;
            ConfigManager.save();
            onCode.accept(code);
        });
    }

    public static void unlink(Consumer<String> onOk, Consumer<String> onError) {
        if (!linked()) {
            onError.accept("This game is not linked to Discord.");
            return;
        }

        send("/unlink", new JsonObject(), token(), (answer, problem) -> {

            QZAConfig cfg = ConfigManager.get();
            cfg.discordAlertToken = "";
            ConfigManager.save();

            if (problem != null) {
                onError.accept("Unlinked here, but the relay did not confirm: " + problem
                        + " Run /unlink in Discord to be sure.");
            } else {
                onOk.accept("Unlinked. The relay has deleted what it stored about you.");
            }
        });
    }

    public static void partyFull(int size) {
        JsonObject body = new JsonObject();
        body.addProperty("event", "party_full");
        body.addProperty("size", size);
        post(body, null);
    }

    public static void test(Consumer<String> onOk, Consumer<String> onError) {
        QZAConfig cfg = ConfigManager.get();
        if (!linked()) {
            onError.accept("Not linked yet. Run /qza discord link first.");
            return;
        }
        if (!cfg.discordAlertDm && !cfg.discordAlertChannel) {
            onError.accept("Both the DM and the channel ping are off, so there is "
                    + "nowhere to send.");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("event", "test");
        post(body, error -> {
            if (error == null) {
                onOk.accept("Sent. Check Discord - if nothing arrives, the relay took it "
                        + "but Discord did not deliver it.");
            } else {
                onError.accept(error);
            }
        });
    }

    public static void announceCode(String code) {
        ChatUtil.send(Component.literal("Your link code is ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(code)
                        .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)
                                .withClickEvent(new ClickEvent.CopyToClipboard(code))
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Click to copy"))))));
        ChatUtil.send(Component.literal("Run ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/link " + code).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" in the QZA Discord within 10 minutes.")
                        .withStyle(ChatFormatting.GRAY)));
    }

    private static String token() {
        String token = ConfigManager.get().discordAlertToken;
        return token == null ? "" : token.trim();
    }

    private static void post(JsonObject body, Consumer<String> done) {
        QZAConfig cfg = ConfigManager.get();

        body.addProperty("dm", cfg.discordAlertDm);
        body.addProperty("channel", cfg.discordAlertChannel);

        send("/alert", body, token(), (answer, problem) -> {
            if (done != null) {
                done.accept(problem);
            }
        });
    }

    private static void send(String path, JsonObject body, String bearer,
                             java.util.function.BiConsumer<JsonObject, String> onDone) {
        String base = url();

        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "QZA-mod")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            QZA.LOGGER.warn("Discord relay address is not a usable URL: {}", base);
            onDone.accept(null, "\"" + base + "\" is not a usable URL.");
            return;
        }
        if (bearer != null && !bearer.isEmpty()) {
            builder.header("Authorization", "Bearer " + bearer);
        }

        http().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    JsonObject parsed = error == null ? parse(response.body()) : null;
                    String problem = interpret(response, error, parsed);
                    if (problem != null) {
                        QZA.LOGGER.warn("Discord relay {} failed: {}", path, problem);
                    }
                    Minecraft.getInstance().execute(() -> onDone.accept(parsed, problem));
                    return null;
                });
    }

    private static JsonObject parse(String body) {
        try {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String interpret(HttpResponse<String> response, Throwable error,
                                    JsonObject parsed) {
        if (error != null) {
            return "Could not reach the relay (" + error.getClass().getSimpleName() + ").";
        }

        if (parsed != null) {
            boolean ok = parsed.has("ok") && parsed.get("ok").isJsonPrimitive()
                    && parsed.get("ok").getAsBoolean();
            if (ok) {
                return null;
            }
            if (parsed.has("error") && parsed.get("error").isJsonPrimitive()) {
                return parsed.get("error").getAsString();
            }
        }

        return response.statusCode() == 200
                ? "The relay sent something unreadable."
                : "The relay returned " + response.statusCode() + ".";
    }

    private static String string(JsonObject source, String key) {
        return source != null && source.has(key) && source.get(key).isJsonPrimitive()
                ? source.get(key).getAsString() : "";
    }
}
