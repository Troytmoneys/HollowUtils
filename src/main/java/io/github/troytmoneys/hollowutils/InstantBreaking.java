package io.github.troytmoneys.hollowutils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.function.Function;

final class InstantBreaking implements Listener {
    private final Function<Player,String> scope;
    private final Map<UUID,String> enabled=new HashMap<>();
    InstantBreaking(Function<Player,String> scope) { this.scope=scope; }
    boolean execute(CommandSender sender,String[] args) {
        UtilityCommands.requirePermission(sender,"hollowutils.togglebreakdelay");
        if(!(sender instanceof Player p))throw new IllegalArgumentException("Instant breaking requires an in-game player.");
        if(args.length>1)return false;
        String server=scope.apply(p);
        if(!ServerGroup.validId(server))throw new IllegalArgumentException("You must be in a managed server.");
        boolean on;
        if(args.length==0)on=!server.equals(enabled.get(p.getUniqueId()));
        else if(args[0].equalsIgnoreCase("on"))on=true;
        else if(args[0].equalsIgnoreCase("off"))on=false;
        else throw new IllegalArgumentException("Use /togglebreakdelay [on|off].");
        if(on)enabled.put(p.getUniqueId(),server);else enabled.remove(p.getUniqueId());
        Responses.success(sender,"Instant breaking",on?"Enabled in "+server:"Disabled");return true;
    }
    boolean permits(Player p,boolean cancelled,boolean breakable) {
        String server=scope.apply(p);
        return !cancelled&&breakable&&ServerGroup.validId(server)&&server.equals(enabled.get(p.getUniqueId()))&&p.hasPermission("hollowutils.togglebreakdelay");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(BlockDamageEvent event) {
        if(permits(event.getPlayer(),event.isCancelled(),event.getBlock().getType().getHardness()>=0))event.setInstaBreak(true);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { enabled.remove(event.getPlayer().getUniqueId()); }
    void change(Player player) { if(!Objects.equals(enabled.get(player.getUniqueId()),scope.apply(player)))enabled.remove(player.getUniqueId()); }
    @EventHandler public void changed(PlayerChangedWorldEvent event) { change(event.getPlayer()); }
}
