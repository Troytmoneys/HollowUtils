package io.github.troytmoneys.hollowutils;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MovementControlsTest {
    private static final class Account {
        final UUID uuid=UUID.randomUUID();final Set<String> permissions=new HashSet<>();
        final Map<String,Object> actual=new HashMap<>();
        MovementControls.State saved=MovementControls.State.DEFAULT;
        GameMode mode=GameMode.SURVIVAL;boolean dead;boolean failSave;
        final Player player;
        Account(String name) {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
                case "getName" -> name;
                case "getUniqueId" -> uuid;
                case "hasPermission" -> permissions.contains(args[0]);
                case "canSee" -> true;
                case "getGameMode" -> mode;
                case "isDead" -> dead;
                case "setAllowFlight","setFlying","setFlySpeed","setWalkSpeed","setFallDistance" -> { actual.put(method.getName(),args[0]);yield null; }
                case "sendMessage" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
    private MovementControls controls(Account... accounts) {
        var targets=new UtilityCommands(()->Arrays.stream(accounts).map(a->a.player).toList(),(a,b)->!b.getName().equals("Foreign"),p->20);
        var map=new HashMap<UUID,Account>();for(var a:accounts)map.put(a.uuid,a);
        return new MovementControls(targets,p->map.get(p.getUniqueId()).saved,(p,state)->{
            Account a=map.get(p.getUniqueId());if(a.failSave)throw new IllegalStateException("Disk failure");a.saved=state;
        });
    }
    @Test void validatesNumericSpeedRangeIncludingNonfiniteValues() {
        assertEquals(.25f,MovementControls.speed("2.5",true));assertEquals(0,MovementControls.speed("0",false));
        assertEquals(1,MovementControls.speed("10",true));assertEquals(.1f,MovementControls.speed("reset",true));assertEquals(.2f,MovementControls.speed("RESET",false));
        for(String value:List.of("NaN","Infinity","-1","10.01","fast","1e999"))assertThrows(IllegalArgumentException.class,()->MovementControls.speed(value,true));
    }
    @Test void toggleAndDisableFlightClearFlyingBeforeRemovingPermission() {
        var a=new Account("Alice");a.permissions.add("hollowutils.fly");var commands=controls(a);
        assertTrue(commands.execute(a.player,"fly",new String[0]));assertTrue(a.saved.flight());assertEquals(true,a.actual.get("setAllowFlight"));
        assertTrue(commands.execute(a.player,"fly",new String[]{"off"}));assertFalse(a.saved.flight());
        assertEquals(false,a.actual.get("setFlying"));assertEquals(false,a.actual.get("setAllowFlight"));assertEquals(0f,a.actual.get("setFallDistance"));
    }
    @Test void targetingOthersRequiresPermissionAndDoesNotCrossServers() {
        var a=new Account("Alice");var b=new Account("Bob");var foreign=new Account("Foreign");
        a.permissions.add("hollowutils.walkspeed");var commands=controls(a,b,foreign);
        assertThrows(IllegalArgumentException.class,()->commands.execute(a.player,"walkspeed",new String[]{"5","Bob"}));assertTrue(b.actual.isEmpty());
        a.permissions.add("hollowutils.walkspeed.others");commands.execute(a.player,"walkspeed",new String[]{"5","Bob"});assertEquals(.5f,b.actual.get("setWalkSpeed"));
        assertThrows(IllegalArgumentException.class,()->commands.execute(a.player,"walkspeed",new String[]{"5","Foreign"}));assertTrue(foreign.actual.isEmpty());
        assertFalse(commands.complete(a.player,"walkspeed",new String[]{"5",""}).contains("Foreign"));
    }
    @Test void changingOrResettingOneSpeedPreservesTheOtherAndFlightState() {
        var a=new Account("Alice");a.permissions.addAll(List.of("hollowutils.flyspeed","hollowutils.walkspeed"));
        a.saved=new MovementControls.State(true,.7f,.8f);var commands=controls(a);
        commands.execute(a.player,"flyspeed",new String[]{"reset"});assertEquals(new MovementControls.State(true,.1f,.8f),a.saved);
        commands.execute(a.player,"walkspeed",new String[]{"reset"});assertEquals(new MovementControls.State(true,.1f,.2f),a.saved);
    }
    @Test void failedPersistenceAndInvalidInputNeverMutateThePlayer() {
        var a=new Account("Alice");a.permissions.add("hollowutils.flyspeed");var commands=controls(a);
        assertThrows(IllegalArgumentException.class,()->commands.execute(a.player,"flyspeed",new String[]{"NaN"}));assertTrue(a.actual.isEmpty());
        a.failSave=true;assertThrows(IllegalStateException.class,()->commands.execute(a.player,"flyspeed",new String[]{"5"}));assertTrue(a.actual.isEmpty());
        assertEquals(MovementControls.State.DEFAULT,a.saved);
    }
    @Test void resettingGroupMovementPreservesVanillaCreativeFlight() {
        var a=new Account("Alice");a.mode=GameMode.CREATIVE;MovementControls.State.DEFAULT.apply(a.player);
        assertEquals(true,a.actual.get("setAllowFlight"));assertFalse(a.actual.containsKey("setFlying"));
        a.mode=GameMode.SURVIVAL;MovementControls.State.DEFAULT.apply(a.player);
        assertEquals(false,a.actual.get("setAllowFlight"));assertEquals(false,a.actual.get("setFlying"));
    }
    @Test void deadPlayersAndCreativeFlightTogglesAreRejected() {
        var a=new Account("Alice");a.permissions.add("hollowutils.fly");var commands=controls(a);
        a.mode=GameMode.CREATIVE;assertThrows(IllegalArgumentException.class,()->commands.execute(a.player,"fly",new String[]{"off"}));
        a.mode=GameMode.SURVIVAL;a.dead=true;assertThrows(IllegalArgumentException.class,()->commands.execute(a.player,"fly",new String[]{"on"}));assertTrue(a.actual.isEmpty());
    }
}
