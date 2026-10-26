package io.github.troytmoneys.hollowutils;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

record Punishment(String id, String server, String type, String subject, String targetName,
                  String actor, String actorName, String reason, long created, long expires,
                  long revoked, String revokedBy) {
    boolean active(long now) { return !type.equals("KICK") && revoked == 0 && (expires == 0 || expires > now); }
    boolean applies(String scope, String kind, String identity, long now) {
        return server.equals(scope) && type.equals(kind) && subject.equals(identity) && active(now);
    }
    Punishment revoke(long now, String by) {
        return new Punishment(id, server, type, subject, targetName, actor, actorName, reason, created, expires, now, by);
    }
    static long expiry(String text,long now) {
        if (text.equalsIgnoreCase("infinite")) return 0;
        try { return Math.addExact(now,duration(text)); }
        catch(ArithmeticException e) { throw new IllegalArgumentException("Expiry time is too large."); }
    }
    static long duration(String text) {
        var matcher = Pattern.compile("([1-9][0-9]*)([smhdw])", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException("Use a duration such as 30m, 12h, 7d, 2w, or infinite.");
        try {
            long unit = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> 1000; case "m" -> 60_000; case "h" -> 3_600_000; case "d" -> 86_400_000; default -> 604_800_000;
            };
            long millis = Math.multiplyExact(Long.parseLong(matcher.group(1)), unit);
            if (millis > Duration.ofDays(3650).toMillis()) throw new IllegalArgumentException("Maximum duration is 3650 days.");
            return millis;
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Duration is too large."); }
    }
}
