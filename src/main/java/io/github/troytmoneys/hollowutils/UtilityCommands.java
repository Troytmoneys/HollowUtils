package io.github.troytmoneys.hollowutils;

import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.*;

/** Essentials-style commands, keeping player lookup inside the caller's logical server. */
final class UtilityCommands {
    static final Set<String> COMMANDS = Set.of("gmc", "gms", "gmsp", "gma", "gamemode", "gm", "heal");
    static final List<String> MODES = List.of("survival", "creative", "adventure", "spectator");
    private final Supplier<? extends Collection<? extends Player>> players;
    private final BiPredicate<Player, Player> sameServer;
    private final ToDoubleFunction<Player> maximumHealth;

    UtilityCommands(Supplier<? extends Collection<? extends Player>> players, BiPredicate<Player, Player> sameServer) {
        this(players, sameServer, player -> {
            var attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute == null) throw new IllegalArgumentException("Cannot determine maximum health.");
            return attribute.getValue();
        });
    }
    UtilityCommands(Supplier<? extends Collection<? extends Player>> players, BiPredicate<Player, Player> sameServer,
                    ToDoubleFunction<Player> maximumHealth) {
        this.players = players; this.sameServer = sameServer; this.maximumHealth = maximumHealth;
    }
    static String permission(String name) { return name.equals("heal") ? "hollowutils.heal" : "hollowutils.gamemode"; }
    static GameMode parseMode(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> throw new IllegalArgumentException("Use survival, creative, adventure or spectator (0–3).");
        };
    }
    static GameMode shortcut(String name) {
        return switch (name) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gma" -> GameMode.ADVENTURE;
            case "gmsp" -> GameMode.SPECTATOR;
            default -> throw new IllegalArgumentException("Unknown gamemode shortcut.");
        };
    }
    static void requirePermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) throw new IllegalArgumentException("You do not have permission (" + permission + ").");
    }
    Player target(CommandSender sender, String name, String othersPermission) {
        if (name == null) {
            if (sender instanceof Player player) return player;
            throw new IllegalArgumentException("Console must specify a player.");
        }
        // No selectors, UUID lookup, partial matches or global existence information.
        var matches = players.get().stream().filter(p -> p.getName().equalsIgnoreCase(name))
                .filter(p -> !(sender instanceof Player source) || (sameServer.test(source, p) && source.canSee(p))).toList();
        if(matches.isEmpty())throw new IllegalArgumentException("Player not found in your server.");
        if(matches.size()!=1)throw new IllegalArgumentException("Multiple visible players use that name. Remove a conflicting disguise before targeting that name.");
        Player target=matches.getFirst();
        if (!(sender instanceof Player source) || !source.getUniqueId().equals(target.getUniqueId())) requirePermission(sender, othersPermission);
        return target;
    }
    List<String> targetNames(CommandSender sender, String othersPermission) {
        return players.get().stream()
                .filter(p -> !(sender instanceof Player source) || (sameServer.test(source, p) && source.canSee(p)))
                .filter(p -> (sender instanceof Player source && source.getUniqueId().equals(p.getUniqueId())) || sender.hasPermission(othersPermission))
                .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    boolean execute(CommandSender sender, String name, String[] args) {
        String permission = permission(name);
        requirePermission(sender, permission);
        boolean fullMode = name.equals("gm") || name.equals("gamemode");
        int targetIndex = fullMode ? 1 : 0;
        if (args.length > targetIndex + 1 || (fullMode && args.length == 0)) return false;
        GameMode mode = name.equals("heal") ? null : fullMode ? parseMode(args[0]) : shortcut(name);
        if (mode != null) requirePermission(sender, "hollowutils.gamemode." + mode.name().toLowerCase(Locale.ROOT));
        Player target = target(sender, args.length > targetIndex ? args[targetIndex] : null, permission + ".others");
        if (name.equals("heal")) {
            if (target.isDead()) throw new IllegalArgumentException("Cannot heal a dead player; wait for respawn.");
            target.setHealth(maximumHealth.applyAsDouble(target));
            target.setFoodLevel(20);
            target.setSaturation(20);
            target.setExhaustion(0);
            target.setRemainingAir(target.getMaximumAir());
            target.setFireTicks(0);
            target.setFallDistance(0);
            tell(sender, "Healed " + target.getName() + ": hearts, hunger and air restored; fire extinguished.");
            if (sender != target) tell(target, "You have been healed.");
        } else {
            target.setGameMode(mode);
            // Other plugins can cancel PlayerGameModeChangeEvent; report the actual outcome.
            if (target.getGameMode() != mode) throw new IllegalArgumentException("Gamemode change was cancelled by the server.");
            tell(sender, "Set " + target.getName() + " to " + mode.name().toLowerCase(Locale.ROOT) + ".");
            if (sender != target) tell(target, "Your gamemode is now " + mode.name().toLowerCase(Locale.ROOT) + ".");
        }
        return true;
    }
    List<String> complete(CommandSender sender, String name, String[] args) {
        if (!sender.hasPermission(permission(name))) return List.of();
        boolean fullMode = name.equals("gm") || name.equals("gamemode");
        if (fullMode && args.length == 1) return MODES.stream()
                .filter(mode -> sender.hasPermission("hollowutils.gamemode." + mode)).toList();
        if (args.length == (fullMode ? 2 : 1)) return targetNames(sender, permission(name) + ".others");
        return List.of();
    }
    private static void tell(CommandSender sender, String message) { Responses.success(sender,"Player",message); }
}
