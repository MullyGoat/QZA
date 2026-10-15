package com.qza.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.qza.QZA;
import com.qza.chat.ChatFocus;
import com.qza.compat.Mc;
import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.stats.StatsApi;
import com.qza.util.ChatUtil;
import com.qza.util.DungeonState;
import com.qza.util.IgnUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonScore {
    public static final int TARGET = 300;

    private static final int BLOOD_FALLBACK_TICKS = 110;
    private static final int PAIR_TICKS = 40;
    private static final int MAX_CRYPTS = 5;
    private static final int MAX_BATS = 5;
    private static final int UNKNOWN_ROOMS = 36;
    private static final double ENTRANCE_SCALE = 0.7;
    private static final double ROUNDING = 1.0E-9;
    private static final long ELECTION_REFRESH_MS = 30L * 60_000L;
    private static final String ELECTION_URL = "https://api.hypixel.net/v2/resources/skyblock/election";
    private static final EquipmentSlot[] ARMOUR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final Pattern MORT = Pattern.compile(
            "^\\[NPC] Mort: (Here, I found this map when I first entered the dungeon\\."
                    + "|Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!)$");
    private static final Pattern SECRETS_PERCENT = Pattern.compile("^Secrets Found: ([\\d.]+)%$");
    private static final Pattern SECRETS_FOUND = Pattern.compile("^Secrets Found: (\\d+)$");
    private static final Pattern CRYPTS = Pattern.compile("^Crypts: (\\d+)$");
    private static final Pattern ROOMS = Pattern.compile("^Completed Rooms: (\\d+)$");
    private static final Pattern TEAM_DEATHS = Pattern.compile("^Team Deaths: (\\d+)$");
    private static final Pattern PUZZLES = Pattern.compile("^Puzzles: \\((\\d+)\\)$");
    private static final Pattern PUZZLE = Pattern.compile("^(\\w+(?: \\w+)*|\\?\\?\\?): \\[([✖✔✦])] ?(?:\\(\\w+\\))?$");
    private static final Pattern TIC_TAC_TOE = Pattern.compile("^PUZZLE SOLVED! \\w{1,16} tied Tic Tac Toe! Good job!$");
    private static final Pattern WEIRDOS = Pattern.compile("^PUZZLE SOLVED! \\w{1,16} wasn't fooled by \\w+! Good job!$");
    private static final Pattern QUIZ = Pattern.compile(
            "^\\[STATUE] Oruo the Omniscient: \\w{1,16} answered the final question correctly!$");
    private static final Pattern TAB_TIME = Pattern.compile("^Time: (?:(\\d+)h ?)?(?:(\\d+)m ?)?(?:(\\d+)s)?$");
    private static final Pattern CLEARED = Pattern.compile("^Cleared: (\\d+)%");
    private static final Pattern ELAPSED = Pattern.compile(
            "^Time Elapsed: (?:(\\d+)h ?)?(?:(\\d+)m ?)?(?:(\\d+)s)?");
    private static final Pattern DEATH = Pattern.compile("^☠ (\\w{1,16}) .+ became a ghost\\.$");
    private static final Pattern PARTY = Pattern.compile("^Party > (?:\\[[^]]+] )?(\\w{1,16}): (.+)$");
    private static final Pattern CHARM_MIMIC = Pattern.compile(
            "^CHARM You charmed a Mimic and captured (?:its Shard|\\d+ Shards from it)\\.$",
            Pattern.CASE_INSENSITIVE);
    private static final String PRINCE_SCORE = "A Prince falls. +1 Bonus Score";
    private static final String BAT_SCORE = "A Bat has been slain. +1 Bonus Score";
    private static final String WATCHER_DONE = "[BOSS] The Watcher: You have proven yourself. You may pass.";
    private static final String[] MIMIC_WORDS = {
            "mimic dead", "mimic killed", "mimic slain", "mimic obliterated", "mimic exorcised",
            "mimic destroyed", "mimic annhilated", "child destroyed", "breefing killed", "breefing dead",
            "$skytils-dungeon-score-mimic$"};
    private static final String[] PRINCE_WORDS = {
            "prince dead", "prince killed", "prince slain", "$skytils-dungeon-score-prince$"};
    private static final String[] BAT_WORDS = {
            "bat dead", "bat killed", "bat slain", "$skytils-dungeon-score-bat$"};

    private static final Set<String> batKillers = new HashSet<>();
    private static final Set<String> chatPuzzles = new HashSet<>();
    private static boolean started;
    private static boolean inBoss;
    private static boolean watcherDone;
    private static boolean bloodDone;
    private static boolean mimicKilled;
    private static boolean princeKilled;
    private static boolean alerted;
    private static boolean partyTold;
    private static int chatDeaths;
    private static String firstDead;
    private static volatile Boolean firstDeadSpirit;
    private static volatile int run;
    private static int bloodIn = -1;
    private static int clearedPercent = -1;
    private static int roomsAtWatcher = -1;
    private static int lastRooms = -1;
    private static int lastCleared = -1;
    private static int roomsChangedAt;
    private static int clearedChangedAt;
    private static int clock;
    private static double heldTotal = -1;
    private static Snapshot last;
    private static Breakdown lastBreakdown;

    private static volatile boolean paul;
    private static volatile long electionCheckedAt;
    private static HttpClient http;

    private DungeonScore() {
    }

    public record Snapshot(int floor, boolean master, double secretPercent, int secretsFound, int completedRooms,
                           int clearedPercent, boolean bloodDone, boolean inBoss, int puzzles, int puzzlesDone,
                           int deaths, boolean firstDeathSpirit, int crypts, boolean mimic, boolean prince,
                           int bats, boolean paul, int seconds, double roomTotal) {
    }

    public record Breakdown(int skill, int explore, int speed, int bonus, int total) {
    }

    public static int score() {
        return lastBreakdown == null ? 0 : lastBreakdown.total();
    }

    public static Snapshot last() {
        return last;
    }

    public static void onChatMessage(String raw) {
        if (raw == null || !DungeonState.inDungeon()) {
            return;
        }
        String message = IgnUtil.stripCodes(raw).trim();
        if (message.isEmpty()) {
            return;
        }
        if (handle(message) && started) {
            update(Minecraft.getInstance());
        }
    }

    private static boolean handle(String message) {
        if (MORT.matcher(message).matches()) {
            begin();
            return true;
        }
        if (TIC_TAC_TOE.matcher(message).matches()) {
            return chatPuzzles.add("Tic Tac Toe");
        }
        if (WEIRDOS.matcher(message).matches()) {
            return chatPuzzles.add("Three Weirdos");
        }
        if (QUIZ.matcher(message).matches()) {
            return chatPuzzles.add("Quiz");
        }
        if (message.equals(WATCHER_DONE)) {
            if (!watcherDone) {
                watcherDone = true;
                bloodIn = BLOOD_FALLBACK_TICKS;
            }
            return true;
        }
        if (message.startsWith("[BOSS] ") && !message.startsWith("[BOSS] The Watcher")) {
            boolean was = inBoss;
            inBoss = true;
            return !was;
        }
        Matcher death = DEATH.matcher(message);
        if (death.matches()) {
            onDeath(death.group(1));
            return true;
        }
        if (message.equalsIgnoreCase(PRINCE_SCORE)) {
            if (!princeKilled) {
                princeKilled = true;
                announce("Prince Killed");
            }
            return true;
        }
        if (message.equalsIgnoreCase(BAT_SCORE)) {
            if (batKillers.size() < MAX_BATS && batKillers.add(selfName())) {
                announce("Bat Killed");
            }
            return true;
        }
        if (CHARM_MIMIC.matcher(message).matches()) {
            onMimicKilled();
            return true;
        }

        Matcher party = PARTY.matcher(message);
        if (!party.matches()) {
            return false;
        }
        String name = party.group(1);
        String body = party.group(2).toLowerCase(Locale.ROOT);
        boolean self = ChatFocus.isSelf(name);
        if (!self && body.contains(String.valueOf(TARGET)) && body.contains("score")) {
            partyTold = true;
        }
        if (!mimicKilled && contains(body, MIMIC_WORDS) && mimicFloor()) {
            mimicKilled = true;
        }
        if (!princeKilled && contains(body, PRINCE_WORDS)) {
            princeKilled = true;
        }
        if (!self && contains(body, BAT_WORDS) && batKillers.size() < MAX_BATS) {
            batKillers.add(name.toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private static void begin() {
        if (!started) {
            started = true;
            refreshElection();
        }
    }

    private static void onDeath(String who) {
        chatDeaths++;
        if (firstDead != null) {
            return;
        }
        firstDead = who.equals("You") ? selfName() : who;
        int asked = run;
        StatsApi.fetch(firstDead).thenAccept(result -> {
            if (run == asked && result.ok() && result.stats().spiritPet() != null) {
                firstDeadSpirit = result.stats().spiritPet();
            }
        });
    }

    public static void onZombieDeath(Zombie zombie) {
        if (!zombie.isBaby() || !DungeonState.inDungeon()) {
            return;
        }
        for (EquipmentSlot slot : ARMOUR) {
            if (!zombie.getItemBySlot(slot).isEmpty()) {
                return;
            }
        }
        onMimicKilled();
    }

    private static void onMimicKilled() {
        if (mimicKilled || !mimicFloor()) {
            return;
        }
        mimicKilled = true;
        announce("Mimic Killed");
    }

    private static boolean mimicFloor() {
        return !inBoss && DungeonState.catacombsFloor() >= 6;
    }

    private static void announce(String text) {
        if (ConfigManager.get().scoreBonusAnnounce) {
            ChatUtil.sendCommand("pc " + text);
        }
    }

    private static boolean contains(String body, String[] words) {
        for (String word : words) {
            if (body.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String selfName() {
        Minecraft client = Minecraft.getInstance();
        return client.getUser() == null ? "" : client.getUser().getName().toLowerCase(Locale.ROOT);
    }

    public static void tick(Minecraft client) {
        if (client.level == null || !DungeonState.inDungeon()) {
            return;
        }
        if (!started && runTimerStarted()) {
            begin();
        }
        if (!started) {
            return;
        }
        if (bloodIn > 0 && --bloodIn == 0) {
            bloodDone = true;
        }
        clock++;
        update(client);
        ScoreLog.tick(last, lastBreakdown);
    }

    private static boolean runTimerStarted() {
        for (String line : DungeonState.sidebarLines()) {
            Matcher m = ELAPSED.matcher(line);
            if (m.find() && seconds(m) > 0) {
                return true;
            }
        }
        return false;
    }

    private static void update(Minecraft client) {
        if (client.level == null) {
            return;
        }
        Snapshot snapshot = read(client);
        if (snapshot == null) {
            return;
        }
        last = snapshot;
        lastBreakdown = breakdown(snapshot);
        if (lastBreakdown.total() >= TARGET && !alerted) {
            alerted = true;
            alert(snapshot);
        }
    }

    private static Snapshot read(Minecraft client) {
        int floor = DungeonState.catacombsFloor();
        if (floor < 0) {
            return null;
        }
        double secretPercent = 0;
        int secretsFound = 0;
        int crypts = 0;
        int rooms = 0;
        int teamDeaths = -1;
        int puzzles = 0;
        Set<String> solved = new HashSet<>(chatPuzzles);
        int tabSeconds = -1;
        ClientPacketListener connection = client.getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getListedOnlinePlayers()) {
                Component display = info.getTabListDisplayName();
                if (display == null) {
                    continue;
                }
                String line = IgnUtil.stripCodes(display.getString()).trim();
                Matcher m;
                if ((m = SECRETS_PERCENT.matcher(line)).matches()) {
                    secretPercent = parseDouble(m.group(1));
                } else if ((m = SECRETS_FOUND.matcher(line)).matches()) {
                    secretsFound = Integer.parseInt(m.group(1));
                } else if ((m = CRYPTS.matcher(line)).matches()) {
                    crypts = Integer.parseInt(m.group(1));
                } else if ((m = ROOMS.matcher(line)).matches()) {
                    rooms = Integer.parseInt(m.group(1));
                } else if ((m = TEAM_DEATHS.matcher(line)).matches()) {
                    teamDeaths = Integer.parseInt(m.group(1));
                } else if ((m = PUZZLES.matcher(line)).matches()) {
                    puzzles = Integer.parseInt(m.group(1));
                } else if ((m = TAB_TIME.matcher(line)).matches()) {
                    tabSeconds = seconds(m);
                } else if ((m = PUZZLE.matcher(line)).matches() && m.group(2).equals("✔")) {
                    solved.add(m.group(1));
                }
            }
        }

        int seconds = -1;
        for (String line : DungeonState.sidebarLines()) {
            Matcher m;
            if ((m = CLEARED.matcher(line)).find()) {
                int cleared = Integer.parseInt(m.group(1));
                if (watcherDone && clearedPercent >= 0 && cleared != clearedPercent) {
                    bloodDone = true;
                }
                clearedPercent = cleared;
            } else if ((m = ELAPSED.matcher(line)).find()) {
                seconds = seconds(m);
            }
        }
        if (watcherDone) {
            if (roomsAtWatcher < 0) {
                roomsAtWatcher = rooms;
            } else if (rooms > roomsAtWatcher) {
                bloodDone = true;
            }
        }
        double roomTotal = roomTotal(rooms, clearedPercent);

        return new Snapshot(floor, DungeonState.catacombsMaster(), secretPercent, secretsFound, rooms,
                Math.max(0, clearedPercent), bloodDone, inBoss, puzzles, Math.min(puzzles, solved.size()),
                Math.max(teamDeaths, chatDeaths), firstDeadSpirit == null || firstDeadSpirit,
                crypts, mimicKilled, princeKilled, batKillers.size(), paul,
                seconds >= 0 ? seconds : Math.max(0, tabSeconds), roomTotal);
    }

    private static double roomTotal(int rooms, int cleared) {
        if (rooms != lastRooms) {
            lastRooms = rooms;
            roomsChangedAt = clock;
        }
        if (cleared != lastCleared) {
            lastCleared = cleared;
            clearedChangedAt = clock;
        }
        int newer = Math.max(roomsChangedAt, clearedChangedAt);
        int older = Math.min(roomsChangedAt, clearedChangedAt);
        boolean waiting = clock - newer < PAIR_TICKS && newer - older > PAIR_TICKS;
        if (waiting && heldTotal > 0) {
            return Math.max(heldTotal, rooms);
        }
        if (rooms <= 0 || cleared <= 0) {
            return -1;
        }
        heldTotal = estimateRooms(rooms, cleared);
        return heldTotal;
    }

    static double estimateRooms(int completed, int cleared) {
        return Math.floor(completed / (cleared / 100.0) + 0.4);
    }

    public static int calculate(Snapshot s) {
        return breakdown(s).total();
    }

    public static Breakdown breakdown(Snapshot s) {
        boolean entrance = s.floor() == 0;

        int bonus = Math.min(s.crypts(), MAX_CRYPTS);
        if (s.mimic() && s.floor() >= 6) {
            bonus += 2;
        }
        if (s.prince()) {
            bonus += 1;
        }
        bonus += Math.min(s.bats(), MAX_BATS);
        if (s.paul()) {
            bonus += 10;
        }

        int limit = timeLimit(s.floor(), s.master());
        int speed = s.seconds() <= limit ? 100
                : Math.max(0, (int) (100f - speedDeduction((s.seconds() - limit) * 100f / limit)));

        int extraRooms = (s.bloodDone() ? 0 : 1) + (entrance || s.inBoss() ? 0 : 1);
        int effectiveRooms = s.completedRooms() + extraRooms;
        double totalRooms = totalRooms(s);
        double ratio = totalRooms <= 0 ? 0 : effectiveRooms / totalRooms;

        double needed = secretsNeeded(s.floor(), s.master());
        double secretsShare = s.secretPercent() / 100.0 / needed;
        if (s.secretsFound() > 0 && s.secretPercent() > 0) {
            double totalSecrets = Math.floor((100.0 / s.secretPercent() * s.secretsFound()) + 0.5);
            if (totalSecrets > 0) {
                secretsShare = Math.max(secretsShare, s.secretsFound() / (totalSecrets * needed));
            }
        }
        int secretsScore = (int) clamp(Math.floor((secretsShare * 40.0) + ROUNDING), 0, 40);
        int roomScore = (int) clamp(Math.floor((ratio * 60.0) + ROUNDING), 0, 60);
        int explore = secretsScore + roomScore;

        int skillRooms = (int) clamp(Math.floor((ratio * 80.0) + ROUNDING), 0, 80);
        int failedPuzzles = Math.max(0, s.puzzles() - s.puzzlesDone());
        int skill = (int) clamp(20 + skillRooms - (failedPuzzles * 10) - deathPenalty(s), 20, 100);

        if (entrance) {
            skill = (int) Math.round(skill * ENTRANCE_SCALE);
            explore = (int) Math.round(explore * ENTRANCE_SCALE);
            speed = (int) Math.round(speed * ENTRANCE_SCALE);
            bonus = (int) Math.round(bonus * ENTRANCE_SCALE);
        }
        return new Breakdown(skill, explore, speed, bonus, skill + explore + speed + bonus);
    }

    private static double totalRooms(Snapshot s) {
        if (s.roomTotal() > 0) {
            return Math.max(s.roomTotal(), s.completedRooms());
        }
        if (s.completedRooms() > 0 && s.clearedPercent() > 0) {
            return estimateRooms(s.completedRooms(), s.clearedPercent());
        }
        return UNKNOWN_ROOMS;
    }

    private static int deathPenalty(Snapshot s) {
        if (s.deaths() <= 0) {
            return 0;
        }
        return (s.deaths() * 2) - (s.firstDeathSpirit() ? 1 : 0);
    }

    private static int timeLimit(int floor, boolean master) {
        if (master) {
            return switch (floor) {
                case 6 -> 600;
                case 7 -> 840;
                default -> 480;
            };
        }
        return switch (floor) {
            case 0 -> 1200;
            case 4, 6 -> 720;
            case 7 -> 840;
            default -> 600;
        };
    }

    private static double secretsNeeded(int floor, boolean master) {
        if (master) {
            return 1.0;
        }
        return switch (floor) {
            case 0, 1 -> 0.3;
            case 2 -> 0.4;
            case 3 -> 0.5;
            case 4 -> 0.6;
            case 5 -> 0.7;
            case 6 -> 0.85;
            default -> 1.0;
        };
    }

    private static float speedDeduction(float percentOver) {
        float over = percentOver;
        float deduction = 0f;
        float[][] steps = {{20f, 2f}, {20f, 3.5f}, {10f, 4f}, {10f, 5f}};
        for (float[] step : steps) {
            if (over <= 0f) {
                return deduction;
            }
            deduction += Math.min(over, step[0]) / step[1];
            over -= step[0];
        }
        if (over > 0f) {
            deduction += over / 6f;
        }
        return deduction;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int seconds(Matcher m) {
        return (toInt(m.group(1)) * 3600) + (toInt(m.group(2)) * 60) + toInt(m.group(3));
    }

    private static int toInt(String text) {
        return text == null ? 0 : Integer.parseInt(text);
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void renderHud(GuiGraphicsExtractor graphics, Font font) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.scoreHudEnabled || !started || last == null || !DungeonState.inDungeon()) {
            return;
        }
        SplitTimers.draw(graphics, font, lines(cfg, last, lastBreakdown),
                cfg.scoreHudX, cfg.scoreHudY, cfg.scoreHudScale);
    }

    public static List<String> preview() {
        QZAConfig cfg = ConfigManager.get();
        if (started && last != null) {
            return lines(cfg, last, lastBreakdown);
        }
        Snapshot example = new Snapshot(7, true, 100, 45, 28, 100, true, true, 3, 3, 1, true,
                5, true, false, 1, false, 312, -1);
        return lines(cfg, example, breakdown(example));
    }

    private static List<String> lines(QZAConfig cfg, Snapshot s, Breakdown b) {
        List<String> out = new ArrayList<>();
        String colour = rankColour(b.total());
        out.add("§7Score: " + colour + b.total() + " §8(" + colour + rank(b.total()) + "§8)");
        if (!cfg.scoreHudBreakdown) {
            return out;
        }
        out.add("§7Skill §f" + b.skill() + " §8| §7Explore §f" + b.explore()
                + " §8| §7Speed §f" + b.speed() + " §8| §7Bonus §f" + b.bonus());
        StringBuilder details = new StringBuilder("§7Deaths §f").append(s.deaths())
                .append(" §8| §7Crypts §f").append(s.crypts()).append("§7/").append(MAX_CRYPTS);
        if (s.puzzles() > 0) {
            details.append(" §8| §7Puzzles §f").append(s.puzzlesDone()).append("§7/").append(s.puzzles());
        }
        if (s.floor() >= 6) {
            details.append(" §8| §7Mimic ").append(s.mimic() ? "§a✔" : "§c✖");
        }
        details.append(" §8| §7Prince ").append(s.prince() ? "§a✔" : "§c✖");
        if (s.bats() > 0) {
            details.append(" §8| §7Bats §f").append(s.bats());
        }
        if (s.paul()) {
            details.append(" §8| §bPaul");
        }
        out.add(details.toString());
        return out;
    }

    private static String rank(int score) {
        if (score >= 300) {
            return "S+";
        }
        if (score >= 270) {
            return "S";
        }
        if (score >= 230) {
            return "A";
        }
        if (score >= 160) {
            return "B";
        }
        return score >= 100 ? "C" : "D";
    }

    private static String rankColour(int score) {
        if (score >= 300) {
            return "§6";
        }
        if (score >= 270) {
            return "§e";
        }
        if (score >= 230) {
            return "§5";
        }
        if (score >= 160) {
            return "§a";
        }
        return score >= 100 ? "§9" : "§c";
    }

    public static void resetPlacement() {
        QZAConfig defaults = new QZAConfig();
        QZAConfig cfg = ConfigManager.get();
        cfg.scoreHudX = defaults.scoreHudX;
        cfg.scoreHudY = defaults.scoreHudY;
        cfg.scoreHudScale = defaults.scoreHudScale;
    }

    private static void alert(Snapshot s) {
        QZAConfig cfg = ConfigManager.get();
        if (!cfg.score300Alert) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Mc.showTitle(Component.literal(TARGET + " Score!").withStyle(ChatFormatting.RED), 0, 40, 10);
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
        String floor = s.floor() == 0 ? "E" : (s.master() ? "M" : "F") + s.floor();
        ChatUtil.send(Component.literal(String.valueOf(TARGET)).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" score reached in ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(elapsed(s.seconds())).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(floor).withStyle(s.master() ? ChatFormatting.RED : ChatFormatting.GREEN)));
        if (cfg.score300Party && !partyTold) {
            partyTold = true;
            ChatUtil.sendCommand("pc " + TARGET + " Score!");
        }
    }

    static String elapsed(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes > 0 ? minutes + "m " + rest + "s" : rest + "s";
    }

    private static void refreshElection() {
        long now = System.currentTimeMillis();
        if (now - electionCheckedAt < ELECTION_REFRESH_MS) {
            return;
        }
        electionCheckedAt = now;
        HttpRequest request = HttpRequest.newBuilder(URI.create(ELECTION_URL))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", QZA.MOD_NAME)
                .GET()
                .build();
        client().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        paul = hasEzpz(response.body());
                    }
                })
                .exceptionally(error -> {
                    QZA.LOGGER.warn("Could not read the SkyBlock mayor", error);
                    electionCheckedAt = 0L;
                    return null;
                });
    }

    static boolean hasEzpz(String body) {
        try {
            JsonObject mayor = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("mayor");
            if (mayor == null) {
                return false;
            }
            JsonArray perks = mayor.getAsJsonArray("perks");
            if (perks != null) {
                for (JsonElement perk : perks) {
                    if (isEzpz(perk)) {
                        return true;
                    }
                }
            }
            JsonObject minister = mayor.getAsJsonObject("minister");
            return minister != null && isEzpz(minister.get("perk"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isEzpz(JsonElement perk) {
        return perk != null && perk.isJsonObject() && perk.getAsJsonObject().has("name")
                && perk.getAsJsonObject().get("name").getAsString().equalsIgnoreCase("EZPZ");
    }

    private static synchronized HttpClient client() {
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        }
        return http;
    }

    public static void reset() {
        run++;
        batKillers.clear();
        started = false;
        inBoss = false;
        watcherDone = false;
        bloodDone = false;
        mimicKilled = false;
        princeKilled = false;
        alerted = false;
        partyTold = false;
        chatDeaths = 0;
        firstDead = null;
        firstDeadSpirit = null;
        chatPuzzles.clear();
        bloodIn = -1;
        clearedPercent = -1;
        roomsAtWatcher = -1;
        lastRooms = -1;
        lastCleared = -1;
        roomsChangedAt = 0;
        clearedChangedAt = 0;
        clock = 0;
        heldTotal = -1;
        ScoreLog.end();
        last = null;
        lastBreakdown = null;
    }
}
