package com.qza.chat;

import com.qza.config.ConfigManager;
import com.qza.config.QZAConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class ChatStyle {
    private static final int PANEL_BG = 0x77000000;
    private static final int PANEL_BORDER = 0x33FFFFFF;
    private static final int BUBBLE_BG = 0x8C23282E;
    private static final int BUBBLE_BORDER = 0x40FFFFFF;
    private static final int NOTE_BG = 0x8C321A38;
    private static final int NOTE_BORDER = 0x80FF55FF;
    private static final int INPUT_BG = 0x66000000;
    private static final int INPUT_BORDER = 0x40FFFFFF;
    private static final int TRACK = 0x33FFFFFF;
    private static final int BAR = 0x99FFFFFF;
    private static final int BAR_NEW = 0xCCFF55FF;

    private static final int LEFT = 4;
    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;
    private static final int GAP = 3;
    private static final int TEXT_X = LEFT + PAD_X;
    private static final int GLYPH_H = 9;
    private static final int PANEL_EDGE = 17;

    private ChatStyle() {
    }

    public static boolean enabled() {
        QZAConfig cfg = ConfigManager.get();
        return cfg.qzaChatEnabled && cfg.chatQzaStyle;
    }

    public record Frame(Font font, List<GuiMessage.Line> lines, int scroll, boolean newSinceScroll,
                        int screenHeight, int ticks, ChatComponent.DisplayMode mode, float scale,
                        int maxWidth, int lineHeight, int pageHeight, float textOpacity,
                        Component queue, Component restricted, Component restrictedHover) {
    }

    private record Entry(int from, int shown, int top, int bottom, int width, float alpha) {
    }

    private static int step(int lineHeight) {
        return Math.max(GLYPH_H, lineHeight) + 1;
    }

    private static int bubbleHeight(int lines, int step) {
        return (lines * step) + (PAD_Y * 2) - 1;
    }

    public static void render(ChatComponent.ChatGraphicsAccess graphics, Frame frame) {
        boolean foreground = frame.mode().foreground;
        boolean restricted = frame.mode().showRestrictedPrompt;
        if (frame.lines().isEmpty() && !restricted && !foreground) {
            return;
        }

        float scale = frame.scale();
        int step = step(frame.lineHeight());
        int chatBottom = Mth.floor((frame.screenHeight() - 40) / scale);
        int chatTop = chatBottom - frame.pageHeight();
        int panelRight = frame.maxWidth() + PANEL_EDGE;

        graphics.updatePose(pose -> pose.scale(scale, scale));

        if (foreground) {
            graphics.fill(0, chatTop - 3, panelRight, chatBottom + 2, PANEL_BG);
            outline(graphics, 0, chatTop - 3, panelRight, chatBottom + 2, PANEL_BORDER);
        }

        FormattedCharSequence restrictedLine = null;
        int restrictedTop = chatTop;
        int restrictedW = 0;
        if (restricted) {
            int full = frame.font().width(frame.restricted());
            restrictedLine = full > frame.maxWidth()
                    ? ComponentRenderUtils.clipText(frame.restrictedHover(), frame.font(), frame.maxWidth())
                    : frame.restricted().getVisualOrderText();
            restrictedW = Math.min(full, frame.maxWidth()) + (PAD_X * 2);
            chatTop += bubbleHeight(1, step) + GAP;
        }

        List<Entry> entries = layout(frame, chatBottom, chatTop, step, foreground);

        for (Entry entry : entries) {
            int bg = fade(BUBBLE_BG, entry.alpha());
            int border = fade(BUBBLE_BORDER, entry.alpha());
            if ((bg >>> 24) == 0 && (border >>> 24) == 0) {
                continue;
            }
            graphics.fill(LEFT, entry.top(), LEFT + entry.width(), entry.bottom(), bg);
            outline(graphics, LEFT, entry.top(), LEFT + entry.width(), entry.bottom(), border);
            GuiMessageTag tag = frame.lines().get(entry.from()).tag();
            if (tag != null) {
                graphics.handleTag(1, entry.top(), LEFT - 1, entry.bottom(), entry.alpha() * frame.textOpacity(), tag);
            }
        }

        boolean queued = frame.queue() != null;
        int queueTop = chatBottom + 4;
        int queueW = queued ? frame.font().width(frame.queue()) + (PAD_X * 2) : 0;
        if (queued) {
            graphics.fill(LEFT, queueTop, LEFT + queueW, queueTop + bubbleHeight(1, step), fade(BUBBLE_BG, 0.5f));
            outline(graphics, LEFT, queueTop, LEFT + queueW, queueTop + bubbleHeight(1, step), fade(BUBBLE_BORDER, 0.5f));
        }
        if (restrictedLine != null) {
            graphics.fill(LEFT, restrictedTop, LEFT + restrictedW, restrictedTop + bubbleHeight(1, step), NOTE_BG);
            outline(graphics, LEFT, restrictedTop, LEFT + restrictedW, restrictedTop + bubbleHeight(1, step), NOTE_BORDER);
        }

        if (foreground && !frame.lines().isEmpty()) {
            drawScrollbar(graphics, frame, entries, chatTop, chatBottom, panelRight, step);
        }

        graphics.updatePose(pose -> pose.translate(TEXT_X, 0));

        for (Entry entry : entries) {
            float opacity = entry.alpha() * frame.textOpacity();
            boolean hovered = false;
            for (int row = 0; row < entry.shown(); row++) {
                GuiMessage.Line line = frame.lines().get(entry.from() + entry.shown() - 1 - row);
                int textTop = entry.top() + PAD_Y + (row * step);
                hovered |= graphics.handleMessage(textTop, opacity, line.content());
                GuiMessageTag tag = line.tag();
                if (tag != null && tag.icon() != null) {
                    boolean force = row == entry.shown() - 1 && line.endOfEntry() && hovered;
                    graphics.handleTagIcon(line.getTagIconLeft(frame.font()), textTop + GLYPH_H, force, tag, tag.icon());
                }
            }
        }

        if (queued) {
            graphics.handleMessage(queueTop + PAD_Y, 0.5f * frame.textOpacity(), frame.queue().getVisualOrderText());
        }
        if (restrictedLine != null) {
            graphics.handleMessage(restrictedTop + PAD_Y, frame.textOpacity(), restrictedLine);
        }

        graphics.updatePose(pose -> pose.translate(-TEXT_X, 0));
    }

    private static List<Entry> layout(Frame frame, int bottom, int limitTop, int step, boolean foreground) {
        List<GuiMessage.Line> lines = frame.lines();
        List<Entry> out = new ArrayList<>();
        int size = lines.size();
        int y = bottom;
        int i = Math.max(0, frame.scroll());

        while (i < size) {
            int end = i + 1;
            while (end < size && !lines.get(end).endOfEntry()) {
                end++;
            }
            float alpha = foreground ? 1f : fadeFor(frame.ticks(), lines.get(i).addedTime());
            if (alpha <= 1.0E-5F) {
                i = end;
                continue;
            }

            int shown = end - i;
            int top = y - bubbleHeight(shown, step);
            boolean last = false;
            if (top < limitTop) {
                shown = (y - limitTop - (PAD_Y * 2) + 1) / step;
                if (shown <= 0) {
                    break;
                }
                top = y - bubbleHeight(shown, step);
                last = true;
            }

            int widest = 0;
            for (int k = i; k < i + shown; k++) {
                GuiMessage.Line line = lines.get(k);
                int w = frame.font().width(line.content());
                GuiMessageTag tag = line.tag();
                if (tag != null && tag.icon() != null) {
                    w = line.getTagIconLeft(frame.font()) + tag.icon().width;
                }
                widest = Math.max(widest, w);
            }
            out.add(new Entry(i, shown, top, y, widest + (PAD_X * 2), alpha));

            if (last) {
                break;
            }
            y = top - GAP;
            i = end;
        }
        return out;
    }

    public static int maxScroll(List<GuiMessage.Line> lines, int pageHeight, int lineHeight) {
        int step = step(lineHeight);
        int used = 0;
        int start = lines.size();
        int i = lines.size() - 1;
        while (i >= 0) {
            int j = i;
            while (j > 0 && !lines.get(j).endOfEntry()) {
                j--;
            }
            int cost = bubbleHeight(i - j + 1, step) + (used > 0 ? GAP : 0);
            if (used + cost > pageHeight) {
                if (start == lines.size()) {
                    start = j;
                }
                break;
            }
            used += cost;
            start = j;
            i = j - 1;
        }
        return Math.max(0, Math.min(start, lines.size() - 1));
    }

    private static void drawScrollbar(ChatComponent.ChatGraphicsAccess graphics, Frame frame, List<Entry> entries,
                                      int chatTop, int chatBottom, int panelRight, int step) {
        int max = maxScroll(frame.lines(), chatBottom - chatTop, frame.lineHeight());
        if (max <= 0) {
            return;
        }
        int shown = 0;
        for (Entry entry : entries) {
            shown += entry.shown();
        }
        int total = frame.lines().size();
        int track = chatBottom - chatTop;
        int barH = Math.max(8, Math.min(track, track * Math.max(1, shown) / Math.max(1, total)));
        float at = Mth.clamp(frame.scroll() / (float) max, 0f, 1f);
        int barBottom = chatBottom - Math.round(at * (track - barH));
        int x = panelRight - 4;
        graphics.fill(x, chatTop, x + 2, chatBottom, TRACK);
        graphics.fill(x, barBottom - barH, x + 2, barBottom, frame.newSinceScroll() ? BAR_NEW : BAR);
    }

    public static void inputBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0, y0, x1, y1, INPUT_BG);
        graphics.fill(x0, y0, x1, y0 + 1, INPUT_BORDER);
        graphics.fill(x0, y1 - 1, x1, y1, INPUT_BORDER);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, INPUT_BORDER);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, INPUT_BORDER);
    }

    private static void outline(ChatComponent.ChatGraphicsAccess graphics, int x0, int y0, int x1, int y1, int colour) {
        if ((colour >>> 24) == 0) {
            return;
        }
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, colour);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, colour);
    }

    private static int fade(int colour, float alpha) {
        int a = Math.round(((colour >>> 24) & 0xFF) * Mth.clamp(alpha, 0f, 1f));
        return (a << 24) | (colour & 0xFFFFFF);
    }

    private static float fadeFor(int ticks, int addedTime) {
        double t = (ticks - addedTime) / 200.0;
        t = Mth.clamp((1.0 - t) * 10.0, 0.0, 1.0);
        return (float) (t * t);
    }
}
