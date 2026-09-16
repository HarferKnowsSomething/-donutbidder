package dev.donut.bidder.chat;

import dev.donut.bidder.Money;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads incoming server messages and pulls out real payments.
 *
 * Target line, exactly as the server prints it:
 *   tntsalot paid you $ 1.6M
 *   Near001Baka paid you $ 45M
 *   CovetousGolf206 paid you $ 10M
 *
 * The mirrored "You paid <player> $ ..." line is deliberately ignored so your
 * own refunds never get counted as bids.
 */
public final class PayParser {

    /** Strips legacy colour codes some servers leave inside the component text. */
    private static final Pattern FORMATTING = Pattern.compile("(?i)\u00A7[0-9A-FK-ORX]");

    private static final Pattern PAID_YOU = Pattern.compile(
            "(?i)^([A-Za-z0-9_]{2,16})\\s+paid\\s+you\\s+\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([KMBT])?\\.?$");

    private PayParser() {
    }

    public record Payment(String player, double amount) {
    }

    public static String clean(String raw) {
        return FORMATTING.matcher(raw).replaceAll("").trim();
    }

    /** @return the payment, or null if this line is not an incoming payment. */
    public static Payment parseIncoming(String rawMessage) {
        String line = clean(rawMessage);
        Matcher m = PAID_YOU.matcher(line);
        if (!m.matches()) {
            return null;
        }
        try {
            return new Payment(m.group(1), Money.parse(m.group(2), m.group(3)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
