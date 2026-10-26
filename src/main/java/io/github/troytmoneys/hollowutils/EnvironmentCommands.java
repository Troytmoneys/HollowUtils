package io.github.troytmoneys.hollowutils;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class EnvironmentCommands implements Listener {
    static final Set<String> COMMANDS=Set.of("flyspeed","walkspeed","fly","day","night","sunrise","sunset","sun","rain","thunder");
    static final Map<String,Long> TIMES=Map.of("day",1000L,"night",13000L,"sunrise",23000L,"sunset",12000L);
    private final HollowUtils plugin;
    private final YamlConfiguration data=new YamlConfiguration();
    private final File file;
    private final MovementControls movement;
    EnvironmentCommands(HollowUtils plugin,UtilityCommands targets) throws IOException {
        this.plugin=plugin;file=new File(plugin.getDataFolder(),"movement.yml");
        if(file.exists()) try { data.load(file); } catch(Exception e) { throw new IOException("Cannot read movement.yml",e); }
        movement=new MovementControls(targets,this::state,this::save);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        for(Player player:Bukkit.getOnlinePlayers()) state(player).apply(player);
    }
    private String key(Player player) { return plugin.serverId(player)+"."+player.getUniqueId(); }
    private MovementControls.State state(Player player) {
        String key=key(player);
        return new MovementControls.State(data.getBoolean(key+".flight",false),(float)data.getDouble(key+".fly-speed",.1),(float)data.getDouble(key+".walk-speed",.2));
    }
    private void save(Player player,MovementControls.State next) {
        String key=key(player);var old=state(player);
        data.set(key+".flight",next.flight());data.set(key+".fly-speed",next.flySpeed());data.set(key+".walk-speed",next.walkSpeed());
        try {
            Path temp=Files.createTempFile(plugin.getDataFolder().toPath(),"movement-",".tmp");
            try {
                Files.writeString(temp,data.saveToString());
                try { Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                catch(AtomicMoveNotSupportedException e) { Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch(IOException e) {
            data.set(key+".flight",old.flight());data.set(key+".fly-speed",old.flySpeed());data.set(key+".walk-speed",old.walkSpeed());
            throw new IllegalStateException("Could not save movement settings; nothing was changed.",e);
        }
    }
    boolean execute(CommandSender sender,String name,String[] args) {
        UtilityCommands.requirePermission(sender,"hollowutils."+name);
        if(MovementControls.COMMANDS.contains(name)) return movement.execute(sender,name,args);
        String scope;
        if(sender instanceof Player p) { if(args.length!=0)return false;scope=plugin.serverId(p); }
        else { if(args.length!=1) throw new IllegalArgumentException("Console syntax: /"+name+" <server>.");scope=args[0]; }
        List<World> worlds=plugin.serverWorlds(scope);
        if(TIMES.containsKey(name)) {
            long time=TIMES.get(name);
            for(World world:worlds) world.setTime(time);
            if(worlds.stream().anyMatch(w->w.getTime()!=time)) throw new IllegalArgumentException("A plugin prevented the time change in one or more worlds. Some worlds may have changed.");
            Responses.success(sender,"Time",scope+" · "+name+" · "+time+" ticks");
        } else {
            weather(worlds,name);
            Responses.success(sender,"Weather",scope+" · "+name+" · 5 minutes");
        }
        return true;
    }
    static void weather(List<World> worlds,String name) {
        if(!Set.of("sun","rain","thunder").contains(name)) throw new IllegalArgumentException("Unknown weather type.");
            List<World> skies=worlds.stream().filter(w->w.getEnvironment()==World.Environment.NORMAL).toList();
            if(skies.isEmpty()) throw new IllegalArgumentException("No overworld is loaded for weather changes.");
            boolean storm=!name.equals("sun"),thunder=name.equals("thunder");
            for(World world:skies) {
                world.setStorm(storm);world.setThundering(thunder);
                world.setClearWeatherDuration(storm?0:6000);
                world.setWeatherDuration(6000);world.setThunderDuration(6000);
            }
            if(skies.stream().anyMatch(w->w.hasStorm()!=storm||w.isThundering()!=thunder)) throw new IllegalArgumentException("A plugin prevented the weather change in one or more worlds. Some worlds may have changed.");
    }
    List<String> complete(CommandSender sender,String name,String[] args) {
        return MovementControls.COMMANDS.contains(name)?movement.complete(sender,name,args):List.of();
    }
    private void apply(Player player) {
        try { state(player).apply(player); }
        catch(Exception e) { plugin.getLogger().warning("Could not restore movement for "+player.getUniqueId()+": "+e.getMessage());MovementControls.State.DEFAULT.apply(player); }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void changed(PlayerChangedWorldEvent event) {
        if(!plugin.sameGroup(event.getPlayer(),event.getFrom())) apply(event.getPlayer());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent event) { apply(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTask(plugin,()->{ if(event.getPlayer().isOnline()) apply(event.getPlayer()); });
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin,()->{ if(event.getPlayer().isOnline()) apply(event.getPlayer()); });
    }
}
