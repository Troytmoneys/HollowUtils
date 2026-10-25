package io.github.troytmoneys.hollowutils;

import java.util.*;
import java.util.regex.Pattern;

final class ServerGroup {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{0,23}");
    final String id;
    final List<String> worlds;
    final Set<UUID> whitelist = new HashSet<>();
    boolean whitelisted;
    boolean maintenance;
    boolean deleting;

    ServerGroup(String id, List<String> worlds) {
        if (!validId(id) || worlds.isEmpty()) throw new IllegalArgumentException("Invalid server definition");
        this.id = id;
        this.worlds = List.copyOf(worlds);
    }
    static boolean validId(String id) { return id != null && ID.matcher(id).matches(); }
    static List<String> worldNames(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Use 1–24 lowercase letters, numbers, _ or -, starting with a letter.");
        return List.of("hu_" + id, "hu_" + id + "_nether", "hu_" + id + "_the_end");
    }
    boolean accessible(UUID uuid, boolean bypass) {
        return !deleting && (bypass || (!maintenance && (!whitelisted || whitelist.contains(uuid))));
    }
}
