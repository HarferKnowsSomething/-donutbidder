package dev.donut.bidder;

import java.util.Locale;

/** Parsing + formatting for DonutSMP style money values ($ 1.6M, $ 45M, $ 3.4K ...). */
public final class Money {

    /** The money colour you asked for, as an opaque ARGB int. */
    public static final int MONEY_COLOR = 0xFF00FC00;

    private Money() {
    }

    /** ("1.6", "M") -> 1_600_000.0 */
    public static double parse(String rawNumber, String suffix) {
        double value = Double.parseDouble(rawNumber.replace(",", ""));
        if (suffix == null || suffix.isEmpty()) {
            return value;
        }
        return switch (Character.toUpperCase(suffix.charAt(0))) {
            case 'K' -> value * 1_000D;
            case 'M' -> value * 1_000_000D;
            case 'B' -> value * 1_000_000_000D;
            case 'T' -> value * 1_000_000_000_000D;
            default -> value;
        };
    }

    /** Accepts "5m", "2.5 M", "750k", "1,000". Returns -1 when unparseable. */
    public static double parseUserInput(String input) {
        if (input == null) return -1;
        String s = input.trim().replace("$", "").replace(",", "").replace(" ", "");
        if (s.isEmpty()) return -1;
        String suffix = "";
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            suffix = String.valueOf(last);
            s = s.substring(0, s.length() - 1);
        }
        try {
            return parse(s, suffix);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 1600000 -> "1.6M" */
    public static String format(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000D) return trim(value / 1_000_000_000_000D) + "T";
        if (abs >= 1_000_000_000D) return trim(value / 1_000_000_000D) + "B";
        if (abs >= 1_000_000D) return trim(value / 1_000_000D) + "M";
        if (abs >= 1_000D) return trim(value / 1_000D) + "K";
        return trim(value);
    }

    private static String trim(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** mm:ss */
    public static String clock(long millis) {
        if (millis < 0) millis = 0;
        long total = millis / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }
}
