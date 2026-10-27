package io.github.troytmoneys.hollowutils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.*;
import net.luckperms.api.platform.PlayerAdapter;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LuckPermsRanksTest {
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private Group group(AtomicInteger weight) {
        return proxy(Group.class,(p,m,a)->switch(m.getName()) { case "getWeight"->OptionalInt.of(weight.get());default->throw new UnsupportedOperationException(m.getName()); });
    }
    @Test void offlineRankLoadsDoNotBlockAndUseLiveInheritedWeightsWithIssuerContext() {
        UUID staff=UUID.randomUUID(),target=UUID.randomUUID();AtomicInteger high=new AtomicInteger(100),low=new AtomicInteger(10);
        QueryOptions context=proxy(QueryOptions.class,(p,m,a)->{throw new UnsupportedOperationException(m.getName());});
        User staffUser=proxy(User.class,(p,m,a)-> { assertEquals("getInheritedGroups",m.getName());assertSame(context,a[0]);return List.of(group(new AtomicInteger(5)),group(high)); });
        User targetUser=proxy(User.class,(p,m,a)-> { assertEquals("getInheritedGroups",m.getName());assertSame(context,a[0]);return List.of(group(low)); });
        var offline=new CompletableFuture<User>();List<User> cleaned=new ArrayList<>();
        UserManager manager=proxy(UserManager.class,(p,m,a)->switch(m.getName()) {
            case "getUser"->a[0].equals(staff)?staffUser:null;
            case "loadUser"->offline;
            case "cleanupUser"->{cleaned.add((User)a[0]);yield null;}
            default->throw new UnsupportedOperationException(m.getName());
        });
        PlayerAdapter<?> adapter=proxy(PlayerAdapter.class,(p,m,a)->context);
        LuckPerms api=proxy(LuckPerms.class,(p,m,a)->switch(m.getName()) { case "getUserManager"->manager;case "getPlayerAdapter"->adapter;default->throw new UnsupportedOperationException(m.getName()); });
        Player source=proxy(Player.class,(p,m,a)->{throw new UnsupportedOperationException(m.getName());});
        var pending=new LuckPermsRanks(api).load(Set.of(staff,target),source);
        assertFalse(pending.isDone());offline.complete(targetUser);var snapshot=pending.join();
        assertEquals(100,snapshot.weight(staff));assertEquals(10,snapshot.weight(target));
        high.set(1);assertThrows(IllegalArgumentException.class,()->RankHierarchy.require(staff,List.of(new RankHierarchy.Check(target,false)),snapshot::weight));
        assertThrows(IllegalArgumentException.class,()->snapshot.weight(UUID.randomUUID()));
        snapshot.close();snapshot.close();assertEquals(1,cleaned.size());assertSame(targetUser,cleaned.getFirst());
    }
}
