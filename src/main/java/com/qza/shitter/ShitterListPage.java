package com.qza.shitter;

import com.qza.util.ChatUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Renders /shitter list as a Hypixel-friends-list style paginated block with
 * clickable page arrows. 8 entries per page.
 */
public final class ShitterListPage {

    public static final int PER_PAGE = 8;
    private static final String DIVIDER = "-----------------------------------------------------";

    private ShitterListPage() {
    }

    public static int pageCount() {
        return Math.max(1, (ShitterList.size() + PER_PAGE - 1) / PER_PAGE);
    }

    public static void print(int requestedPage) {
        List<ShitterEntry> entries = ShitterList.all();

        if (entries.isEmpty()) {
            ChatUtil.raw(Component.literal(DIVIDER)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.STRIKETHROUGH));
            ChatUtil.raw(Component.literal("            Shitter List")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            ChatUtil.raw(Component.literal("  Nobody yet. Add one with ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("/shitter add <ign> <reason>")
                            .withStyle(ChatFormatting.GREEN)));
            ChatUtil.raw(Component.literal(DIVIDER)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.STRIKETHROUGH));
            return;
        }

        int pages = pageCount();
        int page = Math.max(1, Math.min(requestedPage, pages));
        int from = (page - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, entries.size());

        ChatUtil.raw(Component.literal(DIVIDER)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.STRIKETHROUGH));

        ChatUtil.raw(Component.literal("            ")
                .append(Component.literal("Shitter List ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("(Page " + page + " of " + pages + ")")
                        .withStyle(ChatFormatting.GRAY)));

        for (int i = from; i < to; i++) {
            ShitterEntry entry = entries.get(i);
            MutableComponent line = Component.literal(" " + (i + 1) + ". ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(entry.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(entry.reasonOrDefault()).withStyle(ChatFormatting.RED));

            line.setStyle(line.getStyle()
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Click to remove " + entry.name)
                                    .withStyle(ChatFormatting.YELLOW)))
                    .withClickEvent(new ClickEvent.SuggestCommand("/shitter remove " + entry.name)));

            ChatUtil.raw(line);
        }

        ChatUtil.raw(footer(page, pages));
        ChatUtil.raw(Component.literal(DIVIDER)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.STRIKETHROUGH));
    }

    private static MutableComponent footer(int page, int pages) {
        MutableComponent footer = Component.literal("           ");

        if (page > 1) {
            footer.append(arrow("<<", page - 1, "Previous page"));
        } else {
            footer.append(Component.literal("<<").withStyle(ChatFormatting.DARK_GRAY));
        }

        footer.append(Component.literal("  Page " + page + " of " + pages + "  ")
                .withStyle(ChatFormatting.GRAY));

        if (page < pages) {
            footer.append(arrow(">>", page + 1, "Next page"));
        } else {
            footer.append(Component.literal(">>").withStyle(ChatFormatting.DARK_GRAY));
        }

        return footer;
    }

    private static MutableComponent arrow(String label, int targetPage, String tooltip) {
        MutableComponent text = Component.literal(label)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        text.setStyle(text.getStyle()
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal(tooltip).withStyle(ChatFormatting.YELLOW)))
                .withClickEvent(new ClickEvent.RunCommand("/shitter list " + targetPage)));
        return text;
    }
}
