package io.github.troytmoneys.hollowutils;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class RandomTeleport {
    static final Set<String> COMMANDS=Set.of("rtp");
    private record Request(CommandSender sender,Player target,String scope,World world) { }
    private final HollowUtils plugin;private final UtilityCommands targets;
    private final Map<UUID,Request> pending=new HashMap<>();
    private final Map<String,Long> cooldowns=new HashMap<>();
    RandomTeleport(HollowUtils plugin,UtilityCommands targets) { this.plugin=plugin;this.targets=targets; }
    boolean execute(CommandSender sender,String[] args) {
        UtilityCommands.requirePermission(sender,"hollowutils.rtp");
        if(args.length>1)return false;
        Player target=targets.target(sender,args.length==1?args[0]:null,"hollowutils.rtp.others");
        if(target.isDead())throw new IllegalArgumentException("Wait for the player to respawn.");
        if(pending.containsKey(target.getUniqueId()))throw new IllegalArgumentException("A random teleport is already being prepared for this player.");
        cooldowns.entrySet().removeIf(entry->entry.getValue()<=System.currentTimeMillis());
        String scope=plugin.serverId(target),key=scope+":"+target.getUniqueId();
        if(cooldowns.getOrDefault(key,0L)>System.currentTimeMillis())throw new IllegalArgumentException("Please wait before requesting another random teleport.");
        World world=plugin.serverWorlds(scope).stream().filter(w->w.getEnvironment()==World.Environment.NORMAL).findFirst()
                .orElseThrow(()->new IllegalArgumentException("This server has no loaded overworld."));
        double min=plugin.getConfig().getDouble("rtp.min-radius",128),max=plugin.getConfig().getDouble("rtp.max-radius",5000);
        if(!Double.isFinite(min)||!Double.isFinite(max)||min<0||max<=min||max>29_000_000)throw new IllegalArgumentException("RTP radii are invalid; ask staff to check configuration.");
        int attempts=Math.clamp(plugin.getConfig().getInt("rtp.max-attempts",16),1,64);
        int cooldown=Math.clamp(plugin.getConfig().getInt("rtp.cooldown-seconds",30),0,3600);
        Request request=new Request(sender,target,scope,world);pending.put(target.getUniqueId(),request);
        cooldowns.put(key,System.currentTimeMillis()+cooldown*1000L);
        Responses.info(sender,"Random teleport","Searching for safe ground in "+scope+"…");
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(pending.get(target.getUniqueId())==request)finish(request,"Search timed out. Your location was not changed.");
        },600L);
        attempt(request,min,max,attempts);
        return true;
    }
    private boolean valid(Request request) {
        if(pending.get(request.target().getUniqueId())!=request)return false;
        Player target=request.target();
        if(!target.isOnline()||target.isDead()||!plugin.serverId(target).equals(request.scope())||Bukkit.getWorld(request.world().getUID())!=request.world()) {
            finish(request,"Random teleport cancelled because the player or destination changed.");return false;
        }
        if(!request.sender().hasPermission("hollowutils.rtp")) { finish(request,"Random teleport cancelled because permission was removed.");return false; }
        if(!(request.sender() instanceof Player self&&self.getUniqueId().equals(target.getUniqueId()))&&!request.sender().hasPermission("hollowutils.rtp.others")) {
            finish(request,"Permission to teleport another player was removed.");return false;
        }
        if(request.sender() instanceof Player source) {
            if(!source.isOnline()||!plugin.serverId(source).equals(request.scope())||!source.canSee(target)) {
                finish(request,"Random teleport cancelled because the requesting player changed server or disconnected.");return false;
            }
        }
        return true;
    }
    private void attempt(Request request,double min,double max,int remaining) {
        if(!valid(request))return;
        if(remaining==0) { finish(request,"No safe location was found. Your location was not changed.");return; }
        World world=request.world();WorldBorder border=world.getWorldBorder();Location center=border.getCenter(),spawn=world.getSpawnLocation();
        double half=border.getSize()/2-2;
        var bounds=new RtpSearch.Bounds(Math.max(-29_999_980,center.getX()-half),Math.min(29_999_980,center.getX()+half),Math.max(-29_999_980,center.getZ()-half),Math.min(29_999_980,center.getZ()+half));
        var point=RtpSearch.sample(ThreadLocalRandom.current(),spawn.getX(),spawn.getZ(),min,max,bounds);
        if(point.isEmpty()) { finish(request,"No location fits the RTP radius and world border. Ask staff to check settings.");return; }
        int x=point.get().x(),z=point.get().z();
        world.getChunkAtAsync(x>>4,z>>4,true).whenComplete((chunk,error)->{
            if(!plugin.isEnabled())return;
            Bukkit.getScheduler().runTask(plugin,()-> {
                if(!valid(request))return;
                if(error!=null) { finish(request,"The destination chunk could not be loaded. Please try again later.");return; }
                int y=world.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if(y<world.getMinHeight()||y+2>=world.getMaxHeight()) { attempt(request,min,max,remaining-1);return; }
                var floor=world.getBlockAt(x,y,z);
                boolean safe=floor.getType().isOccluding()
                        &&floor.getType()!=Material.MAGMA_BLOCK&&floor.getRelative(0,1,0).getType().isAir()&&floor.getRelative(0,2,0).getType().isAir();
                Location destination=new Location(world,x+.5,y+1,z+.5,request.target().getLocation().getYaw(),request.target().getLocation().getPitch());
                if(!safe||!world.getWorldBorder().isInside(destination)) { attempt(request,min,max,remaining-1);return; }
                // The destination chunk has finished generation before the synchronous teleport.
                pending.remove(request.target().getUniqueId());
                if(!request.target().teleport(destination)) { Responses.error(request.sender(),"Random teleport was cancelled by the server.");return; }
                request.target().setFallDistance(0);
                Responses.success(request.sender(),"Random teleport",request.target().getName()+" · "+request.scope()+" · "+x+", "+(y+1)+", "+z);
                if(request.sender()!=request.target())Responses.info(request.target(),"Random teleport","Moved to safe ground in "+request.scope());
            });
        });
    }
    private void finish(Request request,String error) {
        if(pending.get(request.target().getUniqueId())!=request)return;
        pending.remove(request.target().getUniqueId());Responses.error(request.sender(),error);
    }
    List<String> complete(CommandSender sender,String[] args) {
        return args.length==1&&sender.hasPermission("hollowutils.rtp")?targets.targetNames(sender,"hollowutils.rtp.others"):List.of();
    }
}
