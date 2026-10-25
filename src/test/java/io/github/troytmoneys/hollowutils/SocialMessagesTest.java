package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialMessagesTest {
    private static final class Account {
        final UUID uuid=UUID.randomUUID();
        final Set<String> permissions=new HashSet<>(Set.of("hollowutils.msg"));
        final Set<UUID> hidden=new HashSet<>();
        final List<Component> inbox=new ArrayList<>();
        String scope="alpha";
        boolean muted;
        final Player player;
        Account(String name) {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
                case "getName" -> name;
                case "getUniqueId" -> uuid;
                case "hasPermission" -> permissions.contains(args[0]);
                case "canSee" -> !hidden.contains(((Player)args[0]).getUniqueId());
                case "sendMessage" -> { inbox.add((Component)args[0]); yield null; }
                case "hashCode" -> uuid.hashCode();
                case "equals" -> proxy==args[0];
                case "toString" -> name;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
    private SocialMessages service(Account... accounts) {
        Map<UUID,Account> index=new HashMap<>(); for(var a:accounts) index.put(a.uuid,a);
        return new SocialMessages(()->Arrays.stream(accounts).map(a->a.player).toList(),p->index.get(p.getUniqueId()).scope,
                p->index.get(p.getUniqueId()).muted,(p,w)->false);
    }
    @Test void allAliasesDeliverOnlyToParticipantsAndOptedInLocalStaff() {
        var a=new Account("Alice"); var b=new Account("Bob"); var spy=new Account("Staff"); var foreign=new Account("Foreign");
        spy.permissions.add("hollowutils.socialspy");foreign.permissions.add("hollowutils.socialspy");foreign.scope="beta";
        var service=service(a,b,spy,foreign);
        service.execute(spy.player,"socialspy",new String[]{"on"}); service.execute(foreign.player,"socialspy",new String[]{"on"});
        spy.inbox.clear();foreign.inbox.clear();
        for(String alias:List.of("msg","pm","message","whisper","privatemanager")) assertTrue(service.execute(a.player,alias,new String[]{"bOb","Hello"}));
        assertEquals(5,a.inbox.size());assertEquals(5,b.inbox.size());assertEquals(5,spy.inbox.size());assertEquals(0,foreign.inbox.size());
    }
    @Test void everyAliasHonorsMutesWithoutSendingAnything() {
        var a=new Account("Alice");var b=new Account("Bob");a.muted=true;var service=service(a,b);
        for(String alias:List.of("msg","pm","message","whisper","privatemanager")) assertThrows(IllegalArgumentException.class,()->service.execute(a.player,alias,new String[]{"Bob","Hello"}));
        assertTrue(a.inbox.isEmpty());assertTrue(b.inbox.isEmpty());assertTrue(service.complete(a.player,"msg",new String[]{""}).isEmpty());
    }
    @Test void hiddenAndForeignPlayersAreNotExposedByDeliveryOrCompletion() {
        var a=new Account("Alice");var hidden=new Account("Hidden");var foreign=new Account("Foreign");foreign.scope="beta";a.hidden.add(hidden.uuid);
        var service=service(a,hidden,foreign);
        assertEquals(List.of(),service.complete(a.player,"msg",new String[]{""}));
        String missing=assertThrows(IllegalArgumentException.class,()->service.execute(a.player,"msg",new String[]{"Nobody","hi"})).getMessage();
        for(String name:List.of("Hidden","Foreign")) assertEquals(missing,assertThrows(IllegalArgumentException.class,()->service.execute(a.player,"pm",new String[]{name,"hi"})).getMessage());
        assertTrue(hidden.inbox.isEmpty());assertTrue(foreign.inbox.isEmpty());
    }
    @Test void spyPermissionIsRecheckedAndReconnectClearsSubscription() {
        var a=new Account("Alice");var b=new Account("Bob");var spy=new Account("Staff");spy.permissions.add("hollowutils.socialspy");
        var service=service(a,b,spy);service.execute(spy.player,"socialspy",new String[]{"on"});spy.inbox.clear();
        spy.permissions.remove("hollowutils.socialspy");service.execute(a.player,"msg",new String[]{"Bob","first"});assertTrue(spy.inbox.isEmpty());
        spy.permissions.add("hollowutils.socialspy");service.quit(new PlayerQuitEvent(spy.player,Component.empty()));
        service.execute(a.player,"msg",new String[]{"Bob","second"});assertTrue(spy.inbox.isEmpty());
        assertThrows(IllegalArgumentException.class,()->service.execute(a.player,"socialspy",new String[]{"on"}));
    }
}
