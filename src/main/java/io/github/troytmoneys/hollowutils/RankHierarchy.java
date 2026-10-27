package io.github.troytmoneys.hollowutils;

import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;

final class RankHierarchy {
    record Check(UUID target, boolean issuer) { }
    interface Snapshot extends AutoCloseable {
        int weight(UUID uuid);
        @Override void close();
    }
    interface Lookup { CompletableFuture<Snapshot> load(Set<UUID> ids,Player context); }
    private RankHierarchy() { }
    static void require(UUID actor,List<Check> checks,ToIntFunction<UUID> weights) {
        int staff=weights.applyAsInt(actor);
        for(Check check:checks) {
            if(check.issuer()&&actor.equals(check.target()))continue; // Staff may revoke their own records.
            int target=weights.applyAsInt(check.target());
            if(staff<=target)throw new IllegalArgumentException(check.issuer()
                    ?"You cannot revoke punishments issued by staff of equal or higher LuckPerms weight."
                    :"You cannot punish a player of equal or higher LuckPerms weight.");
        }
    }
}
