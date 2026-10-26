package io.github.troytmoneys.hollowutils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.function.Function;

final class GodMode implements Listener {
    static final Set<String> COMMANDS=Set.of("god");
    private final UtilityCommands targets;
    private final Function<Player,String> scope;
    private final Map<UUID,String> enabled=new HashMap<>();
    GodMode(HollowUtils plugin,UtilityCommands targets) {
        this(targets,plugin::serverId);plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    GodMode(UtilityCommands targets,Function<Player,String> scope) { this.targets=targets;this.scope=scope; }
    boolean execute(CommandSender sender,String[] args) {
        UtilityCommands.requirePermission(sender,"hollowutils.god");
        if(args.length>2)return false;
        Boolean on=null;String who=null;
        if(args.length>=1) {
            if(args[0].equalsIgnoreCase("on"))on=true;
            else if(args[0].equalsIgnoreCase("off"))on=false;
            else if(args.length==1)who=args[0];
            else throw new IllegalArgumentException("Use /god [on|off] [player].");
        }
        if(args.length==2)who=args[1];
        Player target=targets.target(sender,who,"hollowutils.god.others");
        String server=scope.apply(target);
        if(!ServerGroup.validId(server))throw new IllegalArgumentException("Target must be in a managed server.");
        if(target.isDead())throw new IllegalArgumentException("Wait for the player to respawn.");
        boolean state=on==null?!active(target):on;
        if(state) { enabled.put(target.getUniqueId(),server);target.setFireTicks(0);target.setFallDistance(0); }
        else enabled.remove(target.getUniqueId());
        Responses.success(sender,"God mode",target.getName()+" · "+(state?"enabled":"disabled")+" · "+server);
        if(sender!=target)Responses.info(target,"God mode",state?"Enabled":"Disabled");return true;
    }
    boolean active(Player p) { String server=scope.apply(p);return ServerGroup.validId(server)&&server.equals(enabled.get(p.getUniqueId())); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event) {
        if(event.getEntity() instanceof Player p&&active(p))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void food(FoodLevelChangeEvent event) {
        if(event.getEntity() instanceof Player p&&active(p))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void combust(EntityCombustEvent event) {
        if(event.getEntity() instanceof Player p&&active(p))event.setCancelled(true);
    }
    void change(Player p) { if(!Objects.equals(enabled.get(p.getUniqueId()),scope.apply(p)))enabled.remove(p.getUniqueId()); }
    @EventHandler public void changed(PlayerChangedWorldEvent event) { change(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { enabled.remove(event.getPlayer().getUniqueId()); }
    List<String> complete(CommandSender sender,String[] args) {
        if(!sender.hasPermission("hollowutils.god"))return List.of();
        if(args.length==2)return targets.targetNames(sender,"hollowutils.god.others");
        if(args.length==1) { List<String> options=new ArrayList<>(List.of("on","off"));options.addAll(targets.targetNames(sender,"hollowutils.god.others"));return options; }
        return List.of();
    }
}
