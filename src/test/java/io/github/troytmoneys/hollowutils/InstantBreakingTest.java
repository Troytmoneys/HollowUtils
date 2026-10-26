package io.github.troytmoneys.hollowutils;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InstantBreakingTest {
    private static final class Account {
        String scope="alpha";boolean permission=true;final UUID id=UUID.randomUUID();final Player player;
        Account() {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
                case "hasPermission" -> permission;
                case "getUniqueId" -> id;
                case "sendMessage" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
    @Test void explicitAndImplicitTogglesDoNotOverrideCancelledOrUnbreakableBlocks() {
        var a=new Account();var mode=new InstantBreaking(p->a.scope);
        assertFalse(mode.permits(a.player,false,true));
        assertTrue(mode.execute(a.player,new String[0]));assertTrue(mode.permits(a.player,false,true));
        assertFalse(mode.permits(a.player,true,true));assertFalse(mode.permits(a.player,false,false));
        mode.execute(a.player,new String[]{"off"});assertFalse(mode.permits(a.player,false,true));
        mode.execute(a.player,new String[]{"on"});assertTrue(mode.permits(a.player,false,true));
    }
    @Test void crossServerTransitionsClearTheToggleWhileDimensionTravelKeepsIt() {
        var a=new Account();var mode=new InstantBreaking(p->a.scope);mode.execute(a.player,new String[]{"on"});
        mode.change(a.player);assertTrue(mode.permits(a.player,false,true));
        a.scope="beta";assertFalse(mode.permits(a.player,false,true));mode.change(a.player);
        a.scope="alpha";assertFalse(mode.permits(a.player,false,true));
    }
    @Test void permissionRevocationTakesEffectAtTheNextBlockAndInvalidOptionsDoNotEnableIt() {
        var a=new Account();var mode=new InstantBreaking(p->a.scope);
        assertThrows(IllegalArgumentException.class,()->mode.execute(a.player,new String[]{"invalid"}));assertFalse(mode.permits(a.player,false,true));
        mode.execute(a.player,new String[]{"on"});a.permission=false;assertFalse(mode.permits(a.player,false,true));
        assertThrows(IllegalArgumentException.class,()->mode.execute(a.player,new String[]{"on"}));
    }
}
