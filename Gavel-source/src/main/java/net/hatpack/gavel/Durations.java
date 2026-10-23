package net.hatpack.gavel;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {

    public static final long PERMANENT = 0;

    private static final Pattern PART = Pattern.compile("(\\d+)(mo|y|w|d|h|m|s)");
    private static final long SECOND = 1000, MINUTE = 60 * SECOND, HOUR = 60 * MINUTE, DAY = 24 * HOUR;

    private Durations() {
    }

    /**
     * "30m", "1d12h", "2w", "perm" ... returns millis, {@link #PERMANENT} for forever,
     * or null if it isn't a duration at all
     */
    public static Long parse(String input) {
        if (input == null) return null;
        String s = input.toLowerCase(Locale.ROOT).trim();
        if (s.equals("perm") || s.equals("permanent") || s.equals("forever") || s.equals("p")) return PERMANENT;
        if (s.isEmpty()) return null;

        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return null;
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "y" -> 365 * DAY;
                case "mo" -> 30 * DAY;
                case "w" -> 7 * DAY;
                case "d" -> DAY;
                case "h" -> HOUR;
                case "m" -> MINUTE;
                default -> SECOND;
            };
        }
        return end == s.length() && total > 0 ? total : null;
    }

    /** 93784000 -> "1d 2h 3m" */
    public static String format(long millis) {
        if (millis <= 0) return "0s";
        long[] units = {DAY, HOUR, MINUTE, SECOND};
        String[] names = {"d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < units.length && shown < 3; i++) {
            long n = millis / units[i];
            if (n > 0) {
                if (!out.isEmpty()) out.append(' ');
                out.append(n).append(names[i]);
                millis -= n * units[i];
                shown++;
            }
        }
        return out.toString();
    }
}
