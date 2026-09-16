package dev.donut.bidder;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.donut.bidder.auction.AuctionManager;
import dev.donut.bidder.chat.PayParser;
import dev.donut.bidder.config.BidderConfig;
import dev.donut.bidder.ui.AuctionHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class DonutBidderClient implements ClientModInitializer {

    public static final String MOD_ID = "donutbidder";

    private static KeyMapping panelKey;

    @Override
    public void onInitializeClient() {
        BidderConfig.load();
        AuctionManager.INSTANCE.setItemName(BidderConfig.get().itemName);

        registerKeybind();
        registerHud();
        registerDetection();
        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            AuctionManager.INSTANCE.tick();

            while (panelKey.consumeClick()) {
                BidderConfig cfg = BidderConfig.get();
                cfg.expandedPanel = !cfg.expandedPanel;
                BidderConfig.save();
            }
        });
    }

    // ------------------------------------------------------------- detection

    private void registerDetection() {
        // DonutSMP prints payment lines as system messages, which land here.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) {
                return;
            }
            handle(message);
        });

        // Player chat channel, in case the server routes it there instead.
        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, params, receptionTimestamp) -> handle(message));
    }

    private void handle(Component message) {
        String raw = message.getString();
        if (raw == null || raw.isEmpty() || !raw.contains("paid")) {
            return; // cheap early out, this runs on every chat line
        }

        PayParser.Payment incoming = PayParser.parseIncoming(raw);
        if (incoming != null) {
            AuctionManager.INSTANCE.onPayment(incoming.player(), incoming.amount());
        }
        // "You paid <player> $ ..." never matches, so your own refunds are ignored.
    }

    // -------------------------------------------------------------- hud / key

    private void registerKeybind() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        panelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.donutbidder.panel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));
    }

    private void registerHud() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(MOD_ID, "auction_panel"),
                new AuctionHud());
    }

    // -------------------------------------------------------------- commands

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("bidder")
                        .executes(ctx -> {
                            showStatus();
                            return 1;
                        })
                        .then(ClientCommandManager.literal("start").executes(ctx -> {
                            AuctionManager.INSTANCE.start();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("stop").executes(ctx -> {
                            AuctionManager.INSTANCE.stop();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset").executes(ctx -> {
                            AuctionManager.INSTANCE.reset();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("refunds").executes(ctx -> {
                            var refunds = AuctionManager.INSTANCE.refundList();
                            if (refunds.isEmpty()) {
                                AuctionManager.info("Nothing to refund.");
                            } else {
                                AuctionManager.info("Refunds owed:");
                                refunds.forEach(line -> AuctionManager.info("  " + line));
                            }
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("hud").executes(ctx -> {
                            BidderConfig cfg = BidderConfig.get();
                            cfg.hudEnabled = !cfg.hudEnabled;
                            BidderConfig.save();
                            AuctionManager.info("HUD " + (cfg.hudEnabled ? "shown" : "hidden"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("mode").executes(ctx -> {
                            BidderConfig cfg = BidderConfig.get();
                            cfg.cumulativeBids = !cfg.cumulativeBids;
                            BidderConfig.save();
                            AuctionManager.info(cfg.cumulativeBids
                                    ? "Bids now stack: every payment from a player adds up."
                                    : "Bids no longer stack: only a player's largest payment counts.");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("item")
                                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            AuctionManager.INSTANCE.setItemName(name);
                                            BidderConfig.get().itemName = name;
                                            BidderConfig.save();
                                            AuctionManager.info("Item set to " + name);
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("startbid")
                                .then(ClientCommandManager.argument("amount", StringArgumentType.greedyString())
                                        .executes(ctx -> setMoney(
                                                StringArgumentType.getString(ctx, "amount"), true))))
                        .then(ClientCommandManager.literal("raise")
                                .then(ClientCommandManager.argument("amount", StringArgumentType.greedyString())
                                        .executes(ctx -> setMoney(
                                                StringArgumentType.getString(ctx, "amount"), false))))
                        .then(ClientCommandManager.literal("time")
                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(5, 36000))
                                        .executes(ctx -> {
                                            BidderConfig cfg = BidderConfig.get();
                                            cfg.durationSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            BidderConfig.save();
                                            AuctionManager.info("Auction length set to "
                                                    + Money.clock(cfg.durationSeconds * 1000L));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("snipe")
                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(0, 300))
                                        .executes(ctx -> {
                                            BidderConfig cfg = BidderConfig.get();
                                            cfg.antiSnipeSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            BidderConfig.save();
                                            AuctionManager.info(cfg.antiSnipeSeconds == 0
                                                    ? "Anti-snipe disabled."
                                                    : "Anti-snipe set to " + cfg.antiSnipeSeconds + "s.");
                                            return 1;
                                        })))));
    }

    private static int setMoney(String raw, boolean startingBid) {
        double value = Money.parseUserInput(raw);
        if (value < 0) {
            AuctionManager.warn("Could not read \"" + raw + "\". Try 5m, 750k or 1200000.");
            return 0;
        }
        BidderConfig cfg = BidderConfig.get();
        if (startingBid) {
            cfg.startingBid = value;
            AuctionManager.info("Opening bid set to $ " + Money.format(value));
        } else {
            cfg.minIncrement = value;
            AuctionManager.info("Minimum raise set to $ " + Money.format(value));
        }
        BidderConfig.save();
        return 1;
    }

    private static void showStatus() {
        BidderConfig cfg = BidderConfig.get();
        AuctionManager auction = AuctionManager.INSTANCE;

        AuctionManager.info("Item: " + auction.itemName()
                + " | state: " + auction.state()
                + " | opening $ " + Money.format(cfg.startingBid)
                + " | raise $ " + Money.format(cfg.minIncrement)
                + " | length " + Money.clock(cfg.durationSeconds * 1000L)
                + " | anti-snipe " + cfg.antiSnipeSeconds + "s");

        if (auction.highestBidder() != null) {
            AuctionManager.info("Leading: " + auction.highestBidder()
                    + " at $ " + Money.format(auction.highestBid()));
        }

        hint("/bidder item <name>", "pick what you are selling");
        hint("/bidder startbid <amount>", "opening bid, e.g. 5m");
        hint("/bidder raise <amount>", "minimum raise between bids");
        hint("/bidder time <seconds>", "how long the auction runs");
        hint("/bidder snipe <seconds>", "extend the clock on late bids");
        hint("/bidder start | stop | reset", "run the auction");
        hint("/bidder refunds", "list who to pay back");
        hint("/bidder mode | hud", "toggle stacking bids / the panel");
        hint("Right Shift", "expand or shrink the panel");
    }

    private static void hint(String command, String what) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(
                Component.literal("  " + command + " ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("- " + what).withStyle(ChatFormatting.GRAY)),
                false);
    }
}
