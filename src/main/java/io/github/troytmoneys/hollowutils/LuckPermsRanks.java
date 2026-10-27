package io.github.troytmoneys.hollowutils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Loaded only when the optional LuckPerms plugin is enabled. Offline data is fetched asynchronously. */
final class LuckPermsRanks implements RankHierarchy.Lookup {
    private final LuckPerms api;
    LuckPermsRanks() { this(LuckPermsProvider.get()); }
    LuckPermsRanks(LuckPerms api) { this.api=api; }
    static int maximumWeight(Collection<Group> groups) {
        return groups.stream().mapToInt(g->g.getWeight().orElse(0)).max().orElse(0);
    }
    @Override public CompletableFuture<RankHierarchy.Snapshot> load(Set<UUID> ids,Player context) {
        Map<UUID,CompletableFuture<User>> users=new HashMap<>();Set<UUID> fetched=new HashSet<>();
        for(UUID id:ids) {
            User cached=api.getUserManager().getUser(id);
            if(cached!=null)users.put(id,CompletableFuture.completedFuture(cached));
            else { fetched.add(id);users.put(id,api.getUserManager().loadUser(id)); }
        }
        return CompletableFuture.allOf(users.values().toArray(CompletableFuture[]::new)).<RankHierarchy.Snapshot>thenApply(ignored->new RankHierarchy.Snapshot() {
            private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
            public int weight(UUID id) {
                var future=users.get(id);
                if(future==null)throw new IllegalArgumentException("Punishment targets changed while ranks were loading. Run the command again.");
                User user=future.getNow(null);
                if(user==null)throw new IllegalArgumentException("LuckPerms user data is unavailable. Try again.");
                // Compute at the commit point, with the issuer's live world/server contexts.
                return maximumWeight(user.getInheritedGroups(api.getPlayerAdapter(Player.class).getQueryOptions(context)));
            }
            public void close() {
                if(closed.compareAndSet(false,true))cleanup(users,fetched);
            }
        }).whenComplete((snapshot,failure)-> { if(failure!=null)cleanup(users,fetched); });
    }
    private void cleanup(Map<UUID,CompletableFuture<User>> users,Set<UUID> fetched) {
        for(UUID id:fetched) {
            var future=users.get(id);
            if(future.isDone()&&!future.isCompletedExceptionally()&&!future.isCancelled()) {
                User user=future.getNow(null);
                if(user!=null)api.getUserManager().cleanupUser(user); // LP keeps users that are currently online.
            }
        }
    }
}
