package com.qza.chat;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import com.qza.mixin.ChatComponentInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ChatCleaner {
    private static final int SEPARATOR_MIN = 5;
    private static final int STRUCK_SPACES_MIN = 10;
    private static final String SEPARATOR_CHARS = "-▬━─=_";
    private static final int CENTRED_SPACES = 4;
    private static final int MAX_BLOCK_ROWS = 40;
    private static final int LATE_CLOSER_TICKS = 2;
    private static final double CAP_SHARE = 0.88;
    private static final int CAP_MARGIN = 6;
    private static final int CAP_MIN = 80;

    private static final Pattern HEADER = Pattern.compile("^-{2,} ?[^-].{0,40}? ?-{2,}$");

    private static final String[] CHAT_PREFIXES = {
            "Party > ", "Guild > ", "Officer > ", "Co-op > ", "Coop > ", "From ", "To "};

    private static final String[] NPC_PREFIXES = {"[NPC] ", "[SKULL] ", "[BOMB] "};

    private static final Pattern[] USELESS = compile(
            "Warping you to your SkyBlock island\\.\\.\\.",
            "Warping\\.\\.\\.",
            "Sending to server .+",
            "Queuing\\.\\.\\. .+",
            "Welcome to Hypixel SkyBlock!",
            "Latest update: SkyBlock .+",
            "You are playing on profile: .+",
            "Profile ID: .+",
            "Clicking sketchy links can result in your account",
            "being stolen!",
            "Link looks suspicious\\? - Don't click it!",
            "Blacklisted modifications are a bannable offense!",
            "\\[WATCHDOG ANNOUNCEMENT]",
            "Watchdog has banned [\\d,]+ players in the last 7 days\\.",
            "Staff have banned an additional [\\d,]+ players in the last 7 days\\.",
            "(?:>>> )?\\[[^\\]]+] \\w{1,16} [a-z ]+ the lobby!(?: <<<)?",
            "You earned [\\d,.]+ GEXP from playing .+!",
            "You earned [\\d,.]+ Event EXP from playing SkyBlock!",
            "BONUS! Temporarily earn \\d+% more skill experience!",
            "\\+\\d+ Kill Combo.*",
            "Your Kill Combo has expired! You reached a \\d+ Kill Combo!",
            "Inventory full\\? Don't forget to check out your Storage inside the SkyBlock Menu!",
            "You have \\d+ unclaimed event rewards!",
            "Event rewards are deleted after 10 SkyBlock years!",
            "Your Auto Recombobulator recombobulated .+!",
            "Hoppity's Hunt has begun!.*",
            "Only up to 2 rules may trigger at once!",
            "Some of your autopet rules did not trigger\\.",

            "There are blocks in the way!",
            "This ability is on cooldown for .+",
            "This item is on cooldown.*",
            "This item's ability is temporarily disabled!",
            "Your Ultimate is currently on cooldown for .+",
            "You do not have enough mana to do this!",
            "You need at least .+ mana to activate this!",
            "You don't have enough charges to break this block right now!",
            ".+ is ready to use! Press DROP to activate it!",
            "Throwing Axe is now available!",
            "Guided Sheep is now available!",
            "Your Berserk ULTIMATE Ragnarok is now available!",
            "Used (?:Ragnarok|Throwing Axe)!",
            "Creeper Veil (?:Activated|De-activated)!",
            "You cannot use abilities in this room!",
            "You cannot do that in this room!",
            "This creature is immune to this kind of magic!",
            "Your .+ hit [\\d,]+ (?:enemy|enemies) for [\\d,.]+ damage\\.",
            "Your Spirit Pet hit .+ for [\\d,.]+ damage\\.",

            ".+ healed you for [\\d,.]+ health!",
            "You were healed for [\\d,.]+ health by .+",
            "Your fairy healed .+ for [\\d,.]+ health!",
            ".+'s fairy healed you for [\\d,.]+ health!",
            "Your Spirit Pet healed .+ for [\\d,.]+ health!",
            "Your tether with .+ healed you for [\\d,.]+ health\\.",
            ".+ formed a tether with you!",
            "BUFF! You were splashed by .+ with Healing VIII!",
            "BUFF! You have gained Healing V!",
            "You gained [\\d,.]+ HP worth of absorption for 3s from .+!",
            ".+ granted you [\\d,.]+ strength for 20 seconds!",
            "Your bone plating reduced the damage you took by .+!",
            ".+ picked up your .+ Orb!",
            ".*You picked up a .+ Orb from .+ healing you for .+",
            "Mute silenced you!",

            "Goldor's TNT Trap hit you for [\\d,.]+ true damage\\.",
            "Necron's Nuclear Frenzy hit you for [\\d,.]+ damage\\.",
            "Goldor's Greatsword hit you for [\\d,.]+ damage\\.",
            "The .+ Trap hit you for [\\d,.]+ damage!",
            "A Crypt Wither Skull exploded, hitting you for [\\d,.]+ damage\\.",
            "The Frozen Adventurer used Ice Spray on you!",
            "The Lost Adventurer used Dragon's Breath on you!",
            "The Mage's Magma burnt you for [\\d,.]+ true damage\\.",

            "Your .+ stats are doubled because you are the only player using this class!",
            "(?:Archer|Mage|Berserk|Tank|Healer) Milestone .+",
            "ESSENCE! .+ found .+ Essence!",
            ".+ found a Wither Essence! Everyone gains an extra essence!",
            "RIGHT CLICK on .+ to open it\\. This key can only be used to open 1 door!",
            "You hear the sound of something opening\\.\\.\\.",
            "A shiver runs down your spine\\.\\.\\.",
            "This lever has already been used\\.",
            "Someone has already activated this lever!",
            "This chest has already been searched!",
            "You have already opened this dungeon chest!",
            "That chest is locked!",
            ".+ has obtained (?:Superboom TNT(?: x\\d+)?|Revive Stone|Premium Flesh|Beating Heart)!",
            "\\[SKULL] .+",
            "\\[BOMB] Creeper: .+",
            "\\[Sacks] .+",
            "Your active Potion Effects have been paused and stored\\..*",
            "You are not allowed to use Potion Effects while in Dungeon.*");

    private static final List<List<Cell>> block = new ArrayList<>();
    private static boolean open;
    private static MessageSignature blockSignature;
    private static GuiMessageSource blockSource;
    private static GuiMessageTag blockTag;
    private static long ticks;
    private static long flushedAt = Long.MIN_VALUE / 2;
    private static boolean emitting;
    private static ChatComponent chat;

    private ChatCleaner() {
    }

    private record Cell(Style style, char ch, boolean code, int seg) {
    }

    private static Pattern[] compile(String... patterns) {
        Pattern[] out = new Pattern[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            out[i] = Pattern.compile(patterns[i]);
        }
        return out;
    }

    public static boolean emitting() {
        return emitting;
    }

    public static boolean hidesSeparators() {
        return ConfigManager.get().chatHideSeparators;
    }

    public static boolean isSeparator(String plain) {
        if (plain == null) {
            return false;
        }
        int marks = 0;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c == ' ') {
                continue;
            }
            if (SEPARATOR_CHARS.indexOf(c) < 0) {
                return false;
            }
            marks++;
        }
        return marks >= SEPARATOR_MIN;
    }

    public static void add(ChatComponent target, Component contents, MessageSignature signature,
                           GuiMessageSource source, GuiMessageTag tag) {
        chat = target;
        QZAConfig cfg = ConfigManager.get();
        if (source == GuiMessageSource.PLAYER
                || !(cfg.chatHideUseless || cfg.chatHideSeparators || cfg.qzaChatEnabled)) {
            flush();
            emit(contents, contents, signature, source, tag);
            return;
        }

        List<List<Cell>> rows = rows(contents);
        if (rows.size() > 1) {
            flush();
            List<List<Cell>> kept = new ArrayList<>();
            for (List<Cell> row : rows) {
                if (!useless(row, source, cfg)) {
                    kept.add(row);
                }
            }
            emitRows(kept, kept.size() == rows.size() ? contents : null, signature, source, tag);
            return;
        }

        List<Cell> row = rows.get(0);
        if (separator(row)) {
            if (open && blockSource == source) {
                block.add(row);
                close();
                return;
            }
            flush();
            if (ticks - flushedAt <= LATE_CLOSER_TICKS) {
                if (!cfg.chatHideSeparators) {
                    emitRows(List.of(row), contents, signature, source, tag);
                }
                return;
            }
            openBlock(row, signature, source, tag);
            return;
        }
        if (useless(row, source, cfg)) {
            return;
        }
        if (HEADER.matcher(visible(row).trim()).matches()) {
            flush();
            openBlock(row, signature, source, tag);
            return;
        }
        if (open && blockSource == source) {
            block.add(row);
            if (block.size() >= MAX_BLOCK_ROWS) {
                flush();
            }
            return;
        }
        flush();
        emitRows(List.of(row), contents, signature, source, tag);
    }

    public static void tick() {
        ticks++;
        if (open) {
            flush();
        }
    }

    public static void reset() {
        block.clear();
        open = false;
        flushedAt = Long.MIN_VALUE / 2;
    }

    private static void openBlock(List<Cell> row, MessageSignature signature, GuiMessageSource source,
                                  GuiMessageTag tag) {
        block.clear();
        block.add(row);
        open = true;
        blockSignature = signature;
        blockSource = source;
        blockTag = tag;
    }

    private static void close() {
        List<List<Cell>> rows = new ArrayList<>(block);
        block.clear();
        open = false;
        emitRows(rows, null, blockSignature, blockSource, blockTag);
    }

    private static void flush() {
        if (!open) {
            return;
        }
        close();
        flushedAt = ticks;
    }

    private record Tidy(List<List<Cell>> rows, boolean[] centred) {
        boolean anyCentred() {
            for (boolean c : centred) {
                if (c) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void emitRows(List<List<Cell>> rows, Component original, MessageSignature signature,
                                 GuiMessageSource source, GuiMessageTag tag) {
        boolean hide = hidesSeparators();
        List<List<Cell>> plain = new ArrayList<>();
        for (List<Cell> row : rows) {
            if (!(hide && separator(row))) {
                plain.add(row);
            }
        }
        Tidy tidy = tidy(rows, hide);

        Component bubble = tidy.rows().isEmpty() ? null : build(tidy.rows());
        boolean untouched = original != null && rows.size() == 1 && sameText(tidy.rows(), rows);
        if (bubble != null && untouched) {
            bubble = original;
        }
        Component vanilla;
        if (ChatStyle.enabled()) {
            vanilla = untouched || tidy.rows().size() < 2 ? bubble : build(padded(tidy));
        } else if (plain.isEmpty()) {
            vanilla = null;
        } else {
            vanilla = original != null && plain.size() == rows.size() ? original : build(plain);
        }

        List<Component> split = null;
        boolean[] centred = null;
        if (tidy.rows().size() > 1 && tidy.anyCentred()) {
            split = new ArrayList<>(tidy.rows().size());
            for (List<Cell> row : tidy.rows()) {
                split.add(build(List.of(row)));
            }
            centred = tidy.centred();
        }
        emit(vanilla, bubble, split, centred, signature, source, tag);
    }

    private static void emit(Component vanilla, Component bubble, MessageSignature signature,
                             GuiMessageSource source, GuiMessageTag tag) {
        emit(vanilla, bubble, null, null, signature, source, tag);
    }

    private static void emit(Component vanilla, Component bubble, List<Component> rows, boolean[] centred,
                             MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
        if (bubble != null) {
            ChannelHistory.onAnyChatLine(bubble, bubble.getString(), source == GuiMessageSource.SYSTEM_CLIENT,
                    rows, centred);
        }
        if (vanilla == null || chat == null) {
            return;
        }
        emitting = true;
        try {
            ((ChatComponentInvoker) chat).qza$addMessage(vanilla, signature, source, tag);
        } finally {
            emitting = false;
        }
    }

    private static boolean sameText(List<List<Cell>> a, List<List<Cell>> b) {
        return a.size() == b.size() && a.get(0).size() == b.get(0).size();
    }

    private static Tidy tidy(List<List<Cell>> rows, boolean hide) {
        int cap = cap();
        List<List<Cell>> out = new ArrayList<>();
        List<Boolean> centred = new ArrayList<>();
        for (List<Cell> row : rows) {
            if (separator(row)) {
                if (!hide) {
                    out.add(clip(trim(row, true), cap));
                    centred.add(false);
                }
                continue;
            }
            if (visible(row).isBlank()) {
                continue;
            }
            int lead = leadingSpaces(row);
            out.add(trim(row, lead >= CENTRED_SPACES));
            centred.add(lead >= CENTRED_SPACES);
        }
        boolean[] flags = new boolean[centred.size()];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = centred.get(i);
        }
        return new Tidy(out, flags);
    }

    private static List<List<Cell>> padded(Tidy tidy) {
        List<List<Cell>> out = new ArrayList<>(tidy.rows());
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        int[] widths = new int[out.size()];
        for (int i = 0; i < out.size(); i++) {
            widths[i] = font.width(build(List.of(out.get(i))));
            widest = Math.max(widest, widths[i]);
        }
        int room = Math.min(widest, cap());
        int space = Math.max(1, font.width(" "));
        for (int i = 0; i < out.size(); i++) {
            if (!tidy.centred()[i] || widths[i] >= room) {
                continue;
            }
            int pad = Math.round(((room - widths[i]) / 2f) / space);
            if (pad <= 0) {
                continue;
            }
            List<Cell> row = new ArrayList<>(pad + out.get(i).size());
            for (int k = 0; k < pad; k++) {
                row.add(new Cell(Style.EMPTY, ' ', false, -1));
            }
            row.addAll(out.get(i));
            out.set(i, row);
        }
        return out;
    }

    private static int cap() {
        Minecraft client = Minecraft.getInstance();
        Options options = client.options;
        double scale = Math.max(0.1, options.chatScale().get());
        int width = (int) Math.floor(ChatComponent.getWidth(options.chatWidth().get()) / scale);
        return Math.max(CAP_MIN, (int) (width * CAP_SHARE) - CAP_MARGIN);
    }

    private static List<Cell> clip(List<Cell> row, int cap) {
        Font font = Minecraft.getInstance().font;
        List<Cell> out = new ArrayList<>(row);
        while (!out.isEmpty() && font.width(build(List.of(out))) > cap) {
            int last = out.size() - 1;
            while (last >= 0 && out.get(last).code()) {
                last--;
            }
            if (last < 0) {
                break;
            }
            out.remove(last);
        }
        return out;
    }

    private static boolean useless(List<Cell> row, GuiMessageSource source, QZAConfig cfg) {
        if (source != GuiMessageSource.SYSTEM_SERVER || !cfg.chatHideUseless) {
            return false;
        }
        String text = visible(row).trim();
        if (text.isEmpty() || chatLike(text)) {
            return false;
        }
        for (Pattern pattern : USELESS) {
            if (pattern.matcher(text).matches()) {
                return true;
            }
        }
        return false;
    }

    private static boolean chatLike(String text) {
        for (String prefix : CHAT_PREFIXES) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        for (String prefix : NPC_PREFIXES) {
            if (text.startsWith(prefix)) {
                return false;
            }
        }
        return ChannelParser.speakerAnywhere(text) != null;
    }

    private static boolean separator(List<Cell> row) {
        String text = visible(row);
        if (isSeparator(text)) {
            return true;
        }
        if (!text.isBlank()) {
            return false;
        }
        int struck = 0;
        boolean strike = false;
        int seg = Integer.MIN_VALUE;
        for (int i = 0; i < row.size(); i++) {
            Cell cell = row.get(i);
            if (cell.seg() != seg) {
                seg = cell.seg();
                strike = cell.style().isStrikethrough();
            }
            if (cell.code()) {
                if (cell.ch() != '§') {
                    char code = Character.toLowerCase(cell.ch());
                    if (code == 'm') {
                        strike = true;
                    } else if (code == 'r' || Character.digit(code, 16) >= 0) {
                        strike = false;
                    }
                }
                continue;
            }
            if (cell.ch() == ' ' && (strike || cell.style().isStrikethrough())) {
                struck++;
            }
        }
        return struck >= STRUCK_SPACES_MIN;
    }

    private static String visible(List<Cell> row) {
        StringBuilder out = new StringBuilder(row.size());
        for (Cell cell : row) {
            if (!cell.code()) {
                out.append(cell.ch());
            }
        }
        return out.toString();
    }

    private static int leadingSpaces(List<Cell> row) {
        int spaces = 0;
        for (Cell cell : row) {
            if (cell.code()) {
                continue;
            }
            if (cell.ch() != ' ') {
                break;
            }
            spaces++;
        }
        return spaces;
    }

    private static List<Cell> trim(List<Cell> row, boolean leading) {
        int last = row.size() - 1;
        while (last >= 0 && (row.get(last).code() || row.get(last).ch() == ' ')) {
            last--;
        }
        List<Cell> out = new ArrayList<>(row.size());
        boolean started = !leading;
        for (int i = 0; i < row.size(); i++) {
            Cell cell = row.get(i);
            if (i > last) {
                if (cell.code()) {
                    out.add(cell);
                }
                continue;
            }
            if (!started && !cell.code() && cell.ch() == ' ') {
                continue;
            }
            if (!cell.code()) {
                started = true;
            }
            out.add(cell);
        }
        return out;
    }

    private static List<List<Cell>> rows(Component component) {
        List<List<Cell>> rows = new ArrayList<>();
        List<List<Cell>> current = new ArrayList<>();
        current.add(new ArrayList<>());
        int[] seg = {0};
        component.visit((style, text) -> {
            int id = seg[0]++;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    rows.add(current.get(0));
                    current.set(0, new ArrayList<>());
                    continue;
                }
                if (c == '\r') {
                    continue;
                }
                if (c == '§' && i + 1 < text.length()) {
                    current.get(0).add(new Cell(style, c, true, id));
                    current.get(0).add(new Cell(style, text.charAt(i + 1), true, id));
                    i++;
                    continue;
                }
                current.get(0).add(new Cell(style, c, false, id));
            }
            return Optional.empty();
        }, Style.EMPTY);
        rows.add(current.get(0));
        return rows;
    }

    private static MutableComponent build(List<List<Cell>> rows) {
        MutableComponent out = Component.empty();
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                out.append(Component.literal("\n"));
            }
            StringBuilder text = new StringBuilder();
            Style style = Style.EMPTY;
            int seg = Integer.MIN_VALUE;
            for (Cell cell : rows.get(r)) {
                if (cell.seg() != seg && !text.isEmpty()) {
                    out.append(Component.literal(text.toString()).setStyle(style));
                    text.setLength(0);
                }
                seg = cell.seg();
                style = cell.style();
                text.append(cell.ch());
            }
            if (!text.isEmpty()) {
                out.append(Component.literal(text.toString()).setStyle(style));
            }
        }
        return out;
    }
}
