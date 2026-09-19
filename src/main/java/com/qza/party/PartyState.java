package com.qza.party;

import com.qza.QZA;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.client.Minecraft;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PartyState {

    public static final int MAX_SIZE = 5;

    private static final long FRESH_MILLIS = 5_000L;
    private static final long WAIT_MILLIS = 2_000L;

    private static volatile Snapshot latest;
    private static volatile CompletableFuture<Snapshot> pending;

    private PartyState() {
    }

    public record Snapshot(boolean inParty, UUID leader, Set<UUID> members, long at) {
        public int size() {
            return inParty ? Math.max(1, members.size()) : 1;
        }

        public boolean full() {
            return size() >= MAX_SIZE;
        }

        public boolean fresh() {
            return System.currentTimeMillis() - at <= FRESH_MILLIS;
        }

        public boolean ledBy(UUID who) {
            return who != null && who.equals(leader);
        }
    }

    public static void init() {
        try {
            HypixelModAPI.getInstance().createHandler(ClientboundPartyInfoPacket.class,
                    packet -> accept(packet));
        } catch (Throwable e) {
            QZA.LOGGER.warn("Could not subscribe to Hypixel party info", e);
        }
    }

    private static void accept(ClientboundPartyInfoPacket packet) {
        Snapshot snapshot = new Snapshot(packet.isInParty(),
                packet.getLeader().orElse(null),
                Set.copyOf(packet.getMembers()), System.currentTimeMillis());
        latest = snapshot;

        CompletableFuture<Snapshot> waiting = pending;
        pending = null;
        if (waiting != null) {
            waiting.complete(snapshot);
        }
    }

    public static Snapshot cached() {
        return latest;
    }

    public static CompletableFuture<Snapshot> request() {
        Snapshot known = latest;
        if (known != null && known.fresh()) {
            return CompletableFuture.completedFuture(known);
        }

        CompletableFuture<Snapshot> existing = pending;
        if (existing != null) {
            return existing;
        }

        CompletableFuture<Snapshot> future = new CompletableFuture<>();
        pending = future;

        boolean sent;
        try {
            sent = Minecraft.getInstance().getConnection() != null
                    && HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
        } catch (Throwable e) {
            QZA.LOGGER.warn("Could not ask Hypixel for party info", e);
            sent = false;
        }

        if (!sent) {
            pending = null;
            return CompletableFuture.completedFuture(known);
        }

        return future.completeOnTimeout(known, WAIT_MILLIS, TimeUnit.MILLISECONDS);
    }
}
