package dev.donut.bidder.auction;

import dev.donut.bidder.Money;
import dev.donut.bidder.config.BidderConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the state of a single auction: what is being sold, how long is left,
 * who the highest bidder is, and who needs refunding at the end.
 *
 * Nothing here sends or fabricates a payment. It only reads what the server
 * already told the client.
 */
public final class AuctionManager {

    public static final AuctionManager INSTANCE = new AuctionManager();

    public enum State { IDLE, RUNNING, ENDED }

    private State state = State.IDLE;

    private String itemName = "Unnamed item";
    private long endsAt;
    private long lastRemaining;

    /** total paid per player during this auction */
    private final Map<String, Double> totals = new LinkedHashMap<>();
    private final List<Bid> history = new ArrayList<>();

    private String highestBidder;
    private double highestBid;

    private String lastWinner;
    private double lastWinningBid;

    private AuctionManager() {
    }

    // ------------------------------------------------------------------ state

    public State state() {
        return state;
    }

    public String itemName() {
        return itemName;
    }

    public void setItemName(String name) {
        this.itemName = (name == null || name.isBlank()) ? "Unnamed item" : name.trim();
    }

    public String highestBidder() {
        return highestBidder;
    }

    public double highestBid() {
        return highestBid;
    }

    public String lastWinner() {
        return lastWinner;
    }

    public double lastWinningBid() {
        return lastWinningBid;
    }

    public List<Bid> history() {
        return history;
    }

    public long remainingMillis() {
        if (state != State.RUNNING) {
            return lastRemaining;
        }
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    /** The total a bidder has to reach to take the lead. */
    public double requiredNextBid() {
        BidderConfig cfg = BidderConfig.get();
        if (highestBidder == null) {
            return cfg.startingBid;
        }
        return highestBid + cfg.minIncrement;
    }

    // ---------------------------------------------------------------- control

    public void start() {
        BidderConfig cfg = BidderConfig.get();
        totals.clear();
        history.clear();
        highestBidder = null;
        highestBid = 0;
        state = State.RUNNING;
        endsAt = System.currentTimeMillis() + cfg.durationSeconds * 1000L;
        lastRemaining = cfg.durationSeconds * 1000L;

        info("Auction started for " + itemName
                + " - opening bid $ " + Money.format(cfg.startingBid)
                + ", raises of $ " + Money.format(cfg.minIncrement)
                + ", " + Money.clock(remainingMillis()) + " on the clock.");
    }

    public void stop() {
        if (state == State.RUNNING) {
            finish();
        } else {
            state = State.IDLE;
        }
    }

    public void reset() {
        state = State.IDLE;
        totals.clear();
        history.clear();
        highestBidder = null;
        highestBid = 0;
        lastRemaining = 0;
        info("Auction reset.");
    }

    /** Called every client tick. */
    public void tick() {
        if (state == State.RUNNING && System.currentTimeMillis() >= endsAt) {
            finish();
        }
    }

    // ------------------------------------------------------------------- bids

    /** Feed an incoming payment into the auction. */
    public void onPayment(String player, double amount) {
        BidderConfig cfg = BidderConfig.get();

        if (state != State.RUNNING) {
            if (cfg.warnOnPaymentOutsideAuction) {
                warn(player + " paid $ " + Money.format(amount) + " while no auction was running.");
            }
            return;
        }

        // Ignore your own name, just in case the server echoes something odd.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && player.equalsIgnoreCase(mc.player.getName().getString())) {
            return;
        }

        double previousTotal = totals.getOrDefault(player, 0D);
        double newTotal = cfg.cumulativeBids ? previousTotal + amount : Math.max(previousTotal, amount);
        totals.put(player, newTotal);

        double required = requiredNextBid();
        boolean isLeader = player.equalsIgnoreCase(highestBidder);
        boolean accepted = newTotal >= required || (isLeader && newTotal > highestBid);

        history.add(new Bid(player, amount, newTotal, System.currentTimeMillis(), accepted));
        while (history.size() > 200) {
            history.remove(0);
        }

        if (!accepted) {
            warn(player + " bid $ " + Money.format(newTotal)
                    + " but the next bid must reach $ " + Money.format(required) + " - refund them.");
            return;
        }

        String outbid = highestBidder;
        boolean leadChanged = outbid == null || !outbid.equalsIgnoreCase(player);

        highestBidder = player;
        highestBid = newTotal;

        // anti-snipe: push the clock back out if the lead changes near the end
        if (cfg.antiSnipeSeconds > 0 && remainingMillis() < cfg.antiSnipeSeconds * 1000L) {
            endsAt = System.currentTimeMillis() + cfg.antiSnipeSeconds * 1000L;
            info("Timer extended to " + Money.clock(remainingMillis()) + " (anti-snipe).");
        }

        if (leadChanged) {
            info("New highest bidder: " + player + " with $ " + Money.format(newTotal)
                    + (outbid == null ? "" : " (outbid " + outbid + ")"));
        } else {
            info(player + " raised their own bid to $ " + Money.format(newTotal));
        }
    }

    // ------------------------------------------------------------------- end

    private void finish() {
        state = State.ENDED;
        lastRemaining = 0;
        lastWinner = highestBidder;
        lastWinningBid = highestBid;

        if (highestBidder == null) {
            warn("Auction for " + itemName + " ended with no valid bids.");
            return;
        }

        info("Auction ended - " + highestBidder + " won " + itemName
                + " for $ " + Money.format(highestBid));

        List<String> refunds = refundList();
        if (!refunds.isEmpty()) {
            info("Refunds owed:");
            for (String line : refunds) {
                info("  " + line);
            }
        }

        if (BidderConfig.get().exportLogOnEnd) {
            exportLog();
        }
    }

    /** Everyone who paid but did not win, with the amount to send back. */
    public List<String> refundList() {
        List<String> out = new ArrayList<>();
        totals.entrySet().stream()
                .filter(e -> highestBidder == null || !e.getKey().equalsIgnoreCase(highestBidder))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> out.add("/pay " + e.getKey() + " " + (long) Math.ceil(e.getValue())));
        return out;
    }

    public List<Map.Entry<String, Double>> leaderboard() {
        List<Map.Entry<String, Double>> list = new ArrayList<>(totals.entrySet());
        list.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        return list;
    }

    private void exportLog() {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("donutbidder");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path file = dir.resolve("auction_" + stamp + ".txt");

            StringBuilder sb = new StringBuilder();
            sb.append("Item: ").append(itemName).append('\n');
            sb.append("Winner: ").append(lastWinner)
                    .append(" @ ").append(Money.format(lastWinningBid)).append("\n\n");
            sb.append("All bidders:\n");
            for (Map.Entry<String, Double> e : leaderboard()) {
                sb.append("  ").append(e.getKey()).append(" - ")
                        .append(Money.format(e.getValue())).append('\n');
            }
            sb.append("\nRefund commands:\n");
            for (String line : refundList()) {
                sb.append("  ").append(line).append('\n');
            }

            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            info("Saved log to donutbidder/" + file.getFileName());
        } catch (IOException e) {
            warn("Could not save auction log: " + e.getMessage());
        }
    }

    // --------------------------------------------------------------- helpers

    public static void info(String body) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(
                Component.literal("[Bidder] ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(body).withStyle(ChatFormatting.WHITE)),
                false);
    }

    public static void warn(String body) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(
                Component.literal("[Bidder] ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(body).withStyle(ChatFormatting.GRAY)),
                false);
    }
}
