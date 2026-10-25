package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.function.*;

final class SocialMessages implements Listener {
    static final Set<String> COMMANDS=Set.of("msg","pm","message","whisper","privatemanager","socialspy");
    private final Supplier<? extends Collection<? extends Player>> players;
    private final Function<Player,String> scopes;
    private final Predicate<Player> muted;
    private final BiPredicate<Player,org.bukkit.World> sameWorldGroup;
    private final Set<UUID> spies=new HashSet<>();
    SocialMessages(HollowUtils plugin) {
        this(Bukkit::getOnlinePlayers,plugin::serverId,plugin::isMuted,plugin::sameGroup);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    SocialMessages(Supplier<? extends Collection<? extends Player>> players, Function<Player,String> scopes,
                   Predicate<Player> muted, BiPredicate<Player,org.bukkit.World> sameWorldGroup) {
        this.players=players; this.scopes=scopes; this.muted=muted; this.sameWorldGroup=sameWorldGroup;
    }
    private boolean sameGroup(Player source,Player target) {
        String scope=scopes.apply(source);
        return ServerGroup.validId(scope) && scope.equals(scopes.apply(target));
    }
    static String permission(String name) { return "hollowutils."+(name.equals("socialspy") ? "socialspy" : "msg"); }
    private List<Player> recipients(Player source) {
        return players.get().stream().filter(p -> !p.getUniqueId().equals(source.getUniqueId()))
                .filter(p -> sameGroup(source,p) && source.canSee(p)).map(p -> (Player)p).toList();
    }
    boolean execute(CommandSender sender,String name,String[] args) {
        UtilityCommands.requirePermission(sender,permission(name));
        if(!(sender instanceof Player source)) throw new IllegalArgumentException("Private messaging and social spy require an in-game player.");
        if(name.equals("socialspy")) {
            if(args.length>1) return false;
            boolean enabled;
            if(args.length==0) enabled=!spies.contains(source.getUniqueId());
            else if(args[0].equalsIgnoreCase("on")) enabled=true;
            else if(args[0].equalsIgnoreCase("off")) enabled=false;
            else throw new IllegalArgumentException("Use /socialspy [on|off].");
            if(enabled) spies.add(source.getUniqueId()); else spies.remove(source.getUniqueId());
            source.sendMessage(Component.text("▎ Social Spy  ",NamedTextColor.AQUA).append(Component.text(enabled ? "ON · observing this server's private messages" : "OFF",NamedTextColor.GRAY)));
            return true;
        }
        if(args.length<2) return false;
        if(muted.test(source)) throw new IllegalArgumentException("You are muted in this server and cannot send private messages.");
        Player target=recipients(source).stream().filter(p -> p.getName().equalsIgnoreCase(args[0])).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not found in your server."));
        String text=String.join(" ",Arrays.copyOfRange(args,1,args.length));
        if(text.isBlank()||text.length()>500) throw new IllegalArgumentException("Messages must be 1–500 characters.");
        target.sendMessage(Component.text("✉ "+source.getName()+" → you  ",NamedTextColor.AQUA).append(Component.text(text,NamedTextColor.WHITE)));
        source.sendMessage(Component.text("✉ You → "+target.getName()+"  ",NamedTextColor.AQUA).append(Component.text(text,NamedTextColor.WHITE)));
        for(Player spy:players.get()) {
            if(!spies.contains(spy.getUniqueId()) || !spy.hasPermission(permission("socialspy"))) continue;
            if(spy.getUniqueId().equals(source.getUniqueId())||spy.getUniqueId().equals(target.getUniqueId())) continue;
            if(!sameGroup(source,spy)) continue;
            spy.sendMessage(Component.text("[Spy] "+source.getName()+" → "+target.getName()+"  ",NamedTextColor.DARK_GRAY).append(Component.text(text,NamedTextColor.GRAY)));
        }
        return true;
    }
    List<String> complete(CommandSender sender,String name,String[] args) {
        if(!sender.hasPermission(permission(name))||!(sender instanceof Player p)||args.length!=1) return List.of();
        if(name.equals("socialspy")) return List.of("on","off");
        return muted.test(p) ? List.of() : recipients(p).stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    @EventHandler public void quit(PlayerQuitEvent event) { spies.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void changed(PlayerChangedWorldEvent event) {
        if(!sameWorldGroup.test(event.getPlayer(),event.getFrom())) spies.remove(event.getPlayer().getUniqueId());
    }
}
