package io.github.troytmoneys.hollowutils;

import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UtilityCommandsTest {
    private static final class Account {
        final UUID uuid = UUID.randomUUID();
        final String name;
        final Set<String> permissions = new HashSet<>();
        final Map<String, Object> values = new HashMap<>();
        final Player player;
        boolean dead;
        boolean cancelMode;
        Account(String name) {
            this.name = name;
            values.put("GameMode", GameMode.SURVIVAL);
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> uuid;
                    case "hasPermission" -> permissions.contains(args[0]);
                    case "canSee" -> true;
                    case "isDead" -> dead;
                    case "getGameMode" -> values.get("GameMode");
                    case "setGameMode" -> { if (!cancelMode) values.put("GameMode", args[0]); yield null; }
                    case "setHealth", "setFoodLevel", "setSaturation", "setExhaustion", "setFireTicks", "setFallDistance" -> {
                        values.put(method.getName().substring(3), args[0]); yield null;
                    }
                    case "sendMessage" -> null;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
        }
    }
    private UtilityCommands utility(Account... accounts) {
        return new UtilityCommands(() -> Arrays.stream(accounts).map(a -> a.player).toList(),
                (source, target) -> !target.getName().equals("OtherServer"), p -> 40.0);
    }
    @Test void parsesEveryGamemodeAliasAndRejectsUnknownValues() {
        for (String name : List.of("0", "s", "SURVIVAL")) assertEquals(GameMode.SURVIVAL, UtilityCommands.parseMode(name));
        for (String name : List.of("1", "c", "creative")) assertEquals(GameMode.CREATIVE, UtilityCommands.parseMode(name));
        for (String name : List.of("2", "a", "adventure")) assertEquals(GameMode.ADVENTURE, UtilityCommands.parseMode(name));
        for (String name : List.of("3", "sp", "spectator")) assertEquals(GameMode.SPECTATOR, UtilityCommands.parseMode(name));
        assertThrows(IllegalArgumentException.class, () -> UtilityCommands.parseMode("@a"));
        assertEquals(GameMode.CREATIVE, UtilityCommands.shortcut("gmc"));
        assertEquals(GameMode.SURVIVAL, UtilityCommands.shortcut("gms"));
        assertEquals(GameMode.SPECTATOR, UtilityCommands.shortcut("gmsp"));
        assertEquals(GameMode.ADVENTURE, UtilityCommands.shortcut("gma"));
    }
    @Test void healRestoresCustomMaxHealthHungerAndClearsFireAndExhaustion() {
        Account self = new Account("Self"); self.permissions.add("hollowutils.heal");
        assertTrue(utility(self).execute(self.player, "heal", new String[0]));
        assertEquals(40.0, self.values.get("Health"));
        assertEquals(20, self.values.get("FoodLevel"));
        assertEquals(20f, self.values.get("Saturation"));
        assertEquals(0f, self.values.get("Exhaustion"));
        assertEquals(0, self.values.get("FireTicks"));
        assertEquals(0f, self.values.get("FallDistance"));
    }
    @Test void healDoesNotResurrectDeadPlayers() {
        Account self = new Account("Self"); self.permissions.add("hollowutils.heal"); self.dead = true;
        assertThrows(IllegalArgumentException.class, () -> utility(self).execute(self.player, "heal", new String[0]));
        assertFalse(self.values.containsKey("Health"));
    }
    @Test void othersPermissionIsRequiredButExplicitSelfIsAllowed() {
        Account self = new Account("Self"), other = new Account("Other");
        self.permissions.add("hollowutils.heal");
        var commands = utility(self, other);
        assertTrue(commands.execute(self.player, "heal", new String[]{"SELF"}));
        assertThrows(IllegalArgumentException.class, () -> commands.execute(self.player, "heal", new String[]{"Other"}));
        assertFalse(other.values.containsKey("Health"));
        self.permissions.add("hollowutils.heal.others");
        assertTrue(commands.execute(self.player, "heal", new String[]{"Other"}));
        assertEquals(40.0, other.values.get("Health"));
    }
    @Test void crossServerPlayersAreNotTargetableOrSuggestedEvenWithOthersPermission() {
        Account self = new Account("Self"), foreign = new Account("OtherServer");
        self.permissions.addAll(List.of("hollowutils.heal", "hollowutils.heal.others"));
        var commands = utility(self, foreign);
        assertEquals(List.of("Self"), commands.complete(self.player, "heal", new String[]{""}));
        String missing = assertThrows(IllegalArgumentException.class, () -> commands.execute(self.player, "heal", new String[]{"Missing"})).getMessage();
        assertEquals(missing, assertThrows(IllegalArgumentException.class, () -> commands.execute(self.player, "heal", new String[]{"OtherServer"})).getMessage());
        assertThrows(IllegalArgumentException.class, () -> commands.execute(self.player, "heal", new String[]{"@a"}));
    }
    @Test void gamemodeEnforcesModePermissionAndChangesTarget() {
        Account self = new Account("Self"), other = new Account("Other");
        self.permissions.add("hollowutils.gamemode");
        var commands = utility(self, other);
        assertThrows(IllegalArgumentException.class, () -> commands.execute(self.player, "gmc", new String[0]));
        self.permissions.add("hollowutils.gamemode.creative");
        assertTrue(commands.execute(self.player, "gmc", new String[0]));
        assertEquals(GameMode.CREATIVE, self.values.get("GameMode"));
        assertThrows(IllegalArgumentException.class, () -> commands.execute(self.player, "gm", new String[]{"1", "Other"}));
        self.permissions.add("hollowutils.gamemode.others");
        assertTrue(commands.execute(self.player, "gamemode", new String[]{"creative", "Other"}));
        assertEquals(GameMode.CREATIVE, other.values.get("GameMode"));
    }
    @Test void cancelledGamemodeChangeIsReportedAsFailure() {
        Account self = new Account("Self"); self.cancelMode = true;
        self.permissions.addAll(List.of("hollowutils.gamemode", "hollowutils.gamemode.creative"));
        assertThrows(IllegalArgumentException.class, () -> utility(self).execute(self.player, "gmc", new String[0]));
        assertEquals(GameMode.SURVIVAL, self.values.get("GameMode"));
    }
    @Test void missingOrExtraArgumentsNeverApplyChanges() {
        Account self = new Account("Self"); self.permissions.addAll(List.of("hollowutils.gamemode", "hollowutils.heal"));
        var commands = utility(self);
        assertFalse(commands.execute(self.player, "gm", new String[0]));
        assertFalse(commands.execute(self.player, "heal", new String[]{"Self", "extra"}));
        assertFalse(self.values.containsKey("Health"));
    }
    @Test void consoleMustSpecifyTargetAndCanActAcrossServers() {
        Account foreign = new Account("OtherServer");
        CommandSender console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> true;
                    case "sendMessage" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var commands = utility(foreign);
        assertThrows(IllegalArgumentException.class, () -> commands.execute(console, "heal", new String[0]));
        assertTrue(commands.execute(console, "heal", new String[]{"OtherServer"}));
    }
}
