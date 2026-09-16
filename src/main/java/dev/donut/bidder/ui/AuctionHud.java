package dev.donut.bidder.ui;

import dev.donut.bidder.Money;
import dev.donut.bidder.auction.AuctionManager;
import dev.donut.bidder.config.BidderConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.Map;

/**
 * The auction panel, drawn straight onto the HUD.
 *
 * Compact mode is a small always-on strip; expanded mode adds the standings and
 * the live rules. Toggle with the keybind (Right Shift by default).
 */
public final class AuctionHud implements HudElement {

    /** The money colour, opaque ARGB. */
    private static final int ACCENT = 0xFF00FC00;
    private static final int PANEL = 0xC00E0E12;
    private static final int PANEL_HEAD = 0xE016161C;
    private static final int EDGE = 0xFF26262F;
    private static final int TEXT = 0xFFE8E8EC;
    private static final int MUTED = 0xFF8A8A96;
    private static final int RED = 0xFFFF5C5C;

    private static final int WIDTH = 188;

    @Override
    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        BidderConfig cfg = BidderConfig.get();
        AuctionManager auction = AuctionManager.INSTANCE;

        if (!cfg.hudEnabled || auction.state() == AuctionManager.State.IDLE) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        Font font = mc.font;
        boolean expanded = cfg.expandedPanel;
        boolean running = auction.state() == AuctionManager.State.RUNNING;
        long remaining = auction.remainingMillis();

        List<Map.Entry<String, Double>> board = auction.leaderboard();
        int rows = expanded ? Math.min(5, board.size()) : 0;

        int x = cfg.hudX;
        int y = cfg.hudY;
        int height = 60 + (rows > 0 ? 13 + rows * 10 : 0) + (expanded ? 13 : 0);

        // panel body
        context.fill(x, y, x + WIDTH, y + height, PANEL);
        context.fill(x, y, x + WIDTH, y + 15, PANEL_HEAD);
        context.fill(x, y + 15, x + WIDTH, y + 16, EDGE);
        context.fill(x, y, x + 2, y + height, ACCENT);

        int tx = x + 8;
        int ty = y + 4;

        // header: title + clock
        context.drawString(font, "DONUT BIDDER", tx, ty, ACCENT);
        String clock = running ? Money.clock(remaining) : "ENDED";
        int clockColor = running ? (remaining < 10_000 ? RED : TEXT) : MUTED;
        context.drawString(font, clock, x + WIDTH - 8 - font.width(clock), ty, clockColor);
        ty += 16;

        // item
        context.drawString(font, clip(font, auction.itemName(), WIDTH - 16), tx, ty, TEXT);
        ty += 12;

        // leader + winning bid
        String leader = auction.highestBidder() == null ? "no bids yet" : auction.highestBidder();
        context.drawString(font, clip(font, leader, 110), tx, ty, auction.highestBidder() == null ? MUTED : 0xFFFFFFFF);
        String bid = "$ " + Money.format(auction.highestBid());
        context.drawString(font, bid, x + WIDTH - 8 - font.width(bid), ty, ACCENT);
        ty += 13;

        // timer bar
        int barW = WIDTH - 16;
        context.fill(tx, ty, tx + barW, ty + 3, 0xFF23232C);
        if (running) {
            long total = Math.max(1L, BidderConfig.get().durationSeconds * 1000L);
            float pct = Math.min(1F, remaining / (float) total);
            int filled = Math.max(1, (int) (barW * pct));
            context.fill(tx, ty, tx + filled, ty + 3, remaining < 10_000 ? RED : ACCENT);
        }
        ty += 11;

        if (!expanded) {
            return;
        }

        // what the next bid has to reach
        String need = "next bid: $ " + Money.format(auction.requiredNextBid());
        context.drawString(font, need, tx, ty, MUTED);
        ty += 13;

        if (rows > 0) {
            context.drawString(font, "STANDINGS", tx, ty, MUTED);
            ty += 13;
            for (int i = 0; i < rows; i++) {
                Map.Entry<String, Double> e = board.get(i);
                boolean lead = e.getKey().equalsIgnoreCase(auction.highestBidder());
                context.drawString(font, (i + 1) + ". " + clip(font, e.getKey(), 100), tx, ty, lead ? TEXT : MUTED);
                String amt = "$ " + Money.format(e.getValue());
                context.drawString(font, amt, x + WIDTH - 8 - font.width(amt), ty, lead ? ACCENT : MUTED);
                ty += 10;
            }
        }
    }

    private static String clip(Font font, String s, int max) {
        if (font.width(s) <= max) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (font.width(sb.toString() + c + "...") > max) {
                break;
            }
            sb.append(c);
        }
        return sb + "...";
    }
}
