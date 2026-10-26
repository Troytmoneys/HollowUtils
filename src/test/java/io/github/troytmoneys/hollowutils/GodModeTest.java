package io.github.troytmoneys.hollowutils;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GodModeTest {
    private static final class Account {
        final UUID id=UUID.randomUUID();final Set<String> permissions=new HashSet<>();
        String scope="alpha";final Player player;
        Account(String name) {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
                case "getName" -> name;
                case "getUniqueId" -> id;
                case "isDead" -> false;
                case "hasPermission" -> permissions.contains(args[0]);
                case "canSee" -> true;
                case "setFireTicks","setFallDistance","sendMessage" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
    private GodMode god(Account... accounts) {
        var index=new HashMap<UUID,Account>();for(var a:accounts)index.put(a.id,a);
        var targets=new UtilityCommands(()->Arrays.stream(accounts).map(a->a.player).toList(),
                (a,b)->index.get(a.getUniqueId()).scope.equals(index.get(b.getUniqueId()).scope),p->20);
        return new GodMode(targets,p->index.get(p.getUniqueId()).scope);
    }
    @Test void togglesProtectionAndFoodCancellationOnlyWhenEnabled() {
        var a=new Account("Alice");a.permissions.add("hollowutils.god");var mode=god(a);
        mode.execute(a.player,new String[0]);assertTrue(mode.active(a.player));
        var protectedFood=new FoodLevelChangeEvent(a.player,0);mode.food(protectedFood);assertTrue(protectedFood.isCancelled());
        mode.execute(a.player,new String[]{"off"});assertFalse(mode.active(a.player));
        var normalFood=new FoodLevelChangeEvent(a.player,0);mode.food(normalFood);assertFalse(normalFood.isCancelled());
    }
    @Test void staffCanGrantToNonOpButNeedOthersPermissionAndCannotCrossGroups() {
        var staff=new Account("Staff");var target=new Account("Target");staff.permissions.add("hollowutils.god");var mode=god(staff,target);
        assertThrows(IllegalArgumentException.class,()->mode.execute(staff.player,new String[]{"on","Target"}));assertFalse(mode.active(target.player));
        staff.permissions.add("hollowutils.god.others");mode.execute(staff.player,new String[]{"on","Target"});assertTrue(mode.active(target.player));
        assertThrows(IllegalArgumentException.class,()->mode.execute(target.player,new String[]{"on"}));
        target.scope="beta";assertFalse(mode.active(target.player));
        assertThrows(IllegalArgumentException.class,()->mode.execute(staff.player,new String[]{"on","Target"}));
    }
    @Test void groupChangesAndLogoutRemoveTheGrantInsteadOfLeakingItAcrossServers() {
        var a=new Account("Alice");a.permissions.add("hollowutils.god");var mode=god(a);
        mode.execute(a.player,new String[]{"on"});mode.change(a.player);assertTrue(mode.active(a.player));
        a.scope="beta";mode.change(a.player);a.scope="alpha";assertFalse(mode.active(a.player));
        mode.execute(a.player,new String[]{"on"});mode.quit(new PlayerQuitEvent(a.player,Component.empty()));assertFalse(mode.active(a.player));
    }
}
