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

/**
 * Posts alerts to the QZA Discord relay, which turns them into a direct message
 * and, for whoever runs the relay, a channel ping.
 *
 * The bot token lives on the relay, never here, for the same reason the Hypixel
 * key does: anything in the jar or the config folder is one screenshot away from
 * being public. All this end holds is a token the relay issued when this game
 * was linked, which is good for one thing only - messaging the Discord account
 * that claimed it.
 *
 * Every send is fire and forget. A relay that is down, misconfigured or slow
 * must never hold up a tick, so failures are logged and, when the player asked
 * for this themselves, reported back to them.
 */
public final class DiscordAlert {
    /**
     * The deployed relay, so players need no setup beyond linking. Not a
     * secret: it only holds the bot token server side, and will not message
     * anyone who has not linked themselves to it. Overridden by
     * discordAlertUrl in the config when that is set.
     */
    private static final String DEFAULT_RELAY = "https://qza-discord.qza.workers.dev";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static HttpClient client;

    private DiscordAlert() {
    }

    /** Whether this game has been linked to a Discord account. */
    public static boolean linked() {
        String token = ConfigManager.get().discordAlertToken;
        return token != null && !token.isBlank();
    }

    /** True when alerts are switched on and could actually go somewhere. */
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

    /**
     * Asks the relay for a code to type into Discord, and keeps the token that
     * code will belong to.
     *
     * The token is saved before the link is confirmed on purpose: the relay has
     * already tied it to the code, so losing it here would strand the link with
     * no way to finish it. Until somebody runs the code in Discord it is worth
     * nothing to anybody.
     */
    public static void link(Consumer<String> onCode, Consumer<String> onError) {
        JsonObject body = new JsonObject();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            // Shown once when confirming, so you can see which account you are
            // about to attach. The relay drops it with the code.
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

    /** Tells the relay to forget this game, and forgets the token here too. */
    public static void unlink(Consumer<String> onOk, Consumer<String> onError) {
        if (!linked()) {
            onError.accept("This game is not linked to Discord.");
            return;
        }

        send("/unlink", new JsonObject(), token(), (answer, problem) -> {
            // Cleared either way. If the relay cannot be reached the token here
            // is no use to anyone regardless, and leaving it behind would make
            // the game claim a link it no longer has.
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

    /**
     * The party just filled up. Silent about failures beyond the log, since
     * nobody asked for this one right now and a chat error would land while
     * they are in another window anyway.
     */
    public static void partyFull(int size) {
        JsonObject body = new JsonObject();
        body.addProperty("event", "party_full");
        body.addProperty("size", size);
        post(body, null);
    }

    /**
     * Runs the whole chain on demand so setup can be checked without waiting
     * for a party to fill. Speaks up either way.
     */
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

    /**
     * Puts the code in chat, big enough to read off the screen and clickable to
     * copy, with what to do with it underneath. Shared by the command and the
     * settings button so the instructions only exist once.
     */
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

    /**
     * @param done given null on success and a readable reason otherwise, or
     *             null itself when nobody is waiting on the answer
     */
    private static void post(JsonObject body, Consumer<String> done) {
        QZAConfig cfg = ConfigManager.get();

        // Which ways to send are the game's call, so the toggles in the GUI mean
        // something without a redeploy. Where each one lands is the relay's.
        body.addProperty("dm", cfg.discordAlertDm);
        body.addProperty("channel", cfg.discordAlertChannel);

        send("/alert", body, token(), (answer, problem) -> {
            if (done != null) {
                done.accept(problem);
            }
        });
    }

    /** @param onDone given the parsed body and null, or null and a reason */
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

    /** Null when the relay accepted it, otherwise something worth showing. */
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
