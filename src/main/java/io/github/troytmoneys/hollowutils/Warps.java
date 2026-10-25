package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.io.IOException;
import java.util.*;

final class Warps implements Listener {
    static final Set<String> COMMANDS=Set.of("createwarp","warp","deletewarp","warpgui");
    private final HollowUtils plugin;
    private final WarpStore store;
    Warps(HollowUtils plugin) throws IOException {
        this.plugin=plugin; store=new WarpStore(plugin.getDataFolder().toPath().resolve("warps.yml"));
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    private void say(CommandSender sender,String message) {
        sender.sendMessage(Component.text("▎ Warps  ",NamedTextColor.AQUA).append(Component.text(message,NamedTextColor.GRAY)));
    }
    boolean execute(CommandSender sender,String name,String[] args) throws IOException {
        UtilityCommands.requirePermission(sender,"hollowutils."+name);
        if (!(sender instanceof Player p)) throw new IllegalArgumentException("Warp commands require an in-game player.");
        String scope=plugin.serverId(p);
        if (!plugin.hasServer(scope)) throw new IllegalArgumentException("You must be in a managed server.");
        switch (name) {
            case "createwarp" -> {
                if (args.length!=1) return false;
                Location l=p.getLocation();
                store.create(scope,args[0],new WarpStore.Destination(l.getWorld().getUID(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch()));
                say(p,"Created "+args[0]+" in "+scope+".");
            }
            case "deletewarp" -> { if(args.length!=1) return false; store.delete(scope,args[0]); say(p,"Deleted "+args[0]+" from "+scope+"."); }
            case "warp" -> { if(args.length>1) return false; if(args.length==0) open(p,0); else teleport(p,args[0]); }
            case "warpgui" -> { if(args.length!=0) return false; open(p,0); }
        }
        return true;
    }
    private void teleport(Player p,String name) {
        UtilityCommands.requirePermission(p,"hollowutils.warp");
        var destination=store.get(plugin.serverId(p),name);
        if(destination==null) throw new IllegalArgumentException("No warp with that name in this server.");
        World world=Bukkit.getWorld(destination.world());
        if(world==null || !plugin.sameGroup(p,world)) throw new IllegalArgumentException("Warp world is unavailable or no longer belongs to this server.");
        Location location=new Location(world,destination.x(),destination.y(),destination.z(),destination.yaw(),destination.pitch());
        if(location.getY()<world.getMinHeight() || location.getY()>=world.getMaxHeight() || !world.getWorldBorder().isInside(location))
            throw new IllegalArgumentException("Warp is outside the playable world. Ask staff to recreate it.");
        p.closeInventory();
        if(!p.teleport(location)) throw new IllegalArgumentException("Teleport was cancelled.");
        say(p,"Travelled to "+name+".");
    }
    private static final class Menu implements InventoryHolder {
        final String scope; final int page; final List<String> names; Inventory inventory;
        Menu(String scope,int page,List<String> names) { this.scope=scope; this.page=page; this.names=names; }
        public Inventory getInventory() { return inventory; }
    }
    private ItemStack icon(Material material,String title,String lore) {
        ItemStack item=new ItemStack(material); var meta=item.getItemMeta();
        meta.displayName(Component.text(title,NamedTextColor.AQUA)); meta.lore(List.of(Component.text(lore,NamedTextColor.GRAY)));
        item.setItemMeta(meta); return item;
    }
    private void open(Player p,int requested) {
        UtilityCommands.requirePermission(p,"hollowutils.warpgui");
        UtilityCommands.requirePermission(p,"hollowutils.warp");
        String scope=plugin.serverId(p); List<String> names=store.names(scope);
        int last=Math.max(0,(names.size()-1)/45), page=Math.clamp(requested,0,last);
        Menu menu=new Menu(scope,page,names);
        menu.inventory=Bukkit.createInventory(menu,54,Component.text("Warps · "+scope+" · "+(page+1)));
        for(int i=page*45;i<Math.min(names.size(),(page+1)*45);i++) menu.inventory.setItem(i%45,icon(Material.ENDER_PEARL,names.get(i),"Click to teleport"));
        if(page>0) menu.inventory.setItem(45,icon(Material.ARROW,"Previous page","Browse warps"));
        menu.inventory.setItem(49,icon(Material.COMPASS,names.isEmpty()?"No warps yet":names.size()+" warps","Only this server's warps are shown"));
        if(page<last) menu.inventory.setItem(53,icon(Material.ARROW,"Next page","Browse warps"));
        p.openInventory(menu.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)) return;
        int slot=event.getRawSlot();
        if(slot<0 || slot>=54) return;
        Bukkit.getScheduler().runTask(plugin,() -> {
            if(!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder()!=menu) return;
            try {
                UtilityCommands.requirePermission(p,"hollowutils.warpgui");
                if(!plugin.serverId(p).equals(menu.scope)) { p.closeInventory(); return; }
                if(slot<45) { int index=menu.page*45+slot; if(index<menu.names.size()) teleport(p,menu.names.get(index)); }
                else if(slot==45 && menu.page>0) open(p,menu.page-1);
                else if(slot==53 && (menu.page+1)*45<menu.names.size()) open(p,menu.page+1);
            } catch(IllegalArgumentException e) { say(p,e.getMessage()); }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if(event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true); }
    List<String> complete(CommandSender sender,String name,String[] args) {
        if(args.length!=1 || !(sender instanceof Player p) || !sender.hasPermission("hollowutils."+name)) return List.of();
        return Set.of("warp","deletewarp").contains(name)?store.names(plugin.serverId(p)):List.of();
    }
}
