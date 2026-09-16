package dev.donut.bidder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to .minecraft/config/donutbidder.json */
public class BidderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("donutbidder.json");
    private static BidderConfig instance;

    // ---- auction rules
    public String itemName = "Unnamed item";
    public double startingBid = 1_000_000D;
    public double minIncrement = 500_000D;
    public int durationSeconds = 120;
    public int antiSnipeSeconds = 15;

    /** true: a player's payments stack up. false: only their single largest payment counts. */
    public boolean cumulativeBids = true;

    // ---- display
    public boolean hudEnabled = true;
    public boolean expandedPanel = true;
    public int hudX = 6;
    public int hudY = 6;
    public boolean warnOnPaymentOutsideAuction = true;
    public boolean exportLogOnEnd = true;

    public static BidderConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                instance = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), BidderConfig.class);
            }
        } catch (Exception ignored) {
            // fall through to defaults
        }
        if (instance == null) {
            instance = new BidderConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(get()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // not worth crashing the client over
        }
    }
}
