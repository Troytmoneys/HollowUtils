package io.github.troytmoneys.hollowutils;

import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.*;

final class MovementControls {
    static final Set<String> COMMANDS=Set.of("fly","flyspeed","walkspeed");
    record State(boolean flight,float flySpeed,float walkSpeed) {
        static final State DEFAULT=new State(false,.1f,.2f);
        State {
            if(!Float.isFinite(flySpeed)||!Float.isFinite(walkSpeed)||flySpeed<0||flySpeed>1||walkSpeed<0||walkSpeed>1)
                throw new IllegalArgumentException("Movement speeds must be within 0–10.");
        }
        void apply(Player player) {
            player.setFlySpeed(flySpeed); player.setWalkSpeed(walkSpeed);
            boolean allowed=flight||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR;
            if(!allowed) player.setFlying(false);
            player.setAllowFlight(allowed); player.setFallDistance(0);
        }
    }
    private final UtilityCommands targets;
    private final Function<Player,State> settings;
    private final BiConsumer<Player,State> save;
    MovementControls(UtilityCommands targets,Function<Player,State> settings,BiConsumer<Player,State> save) {
        this.targets=targets;this.settings=settings;this.save=save;
    }
    static float speed(String input,boolean flying) {
        if(input.equalsIgnoreCase("reset")) return flying?.1f:.2f;
        final double speed;
        try { speed=Double.parseDouble(input); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("Use a speed from 0 to 10, or reset."); }
        if(!Double.isFinite(speed)||speed<0||speed>10) throw new IllegalArgumentException("Use a speed from 0 to 10, or reset.");
        return (float)(speed/10);
    }
    boolean execute(CommandSender sender,String name,String[] args) {
        UtilityCommands.requirePermission(sender,"hollowutils."+name);
        Player target;State next;
        if(name.equals("fly")) {
            if(args.length>2) return false;
            Boolean enabled=null;String who=null;
            if(args.length>=1) {
                if(args[0].equalsIgnoreCase("on")) enabled=true;
                else if(args[0].equalsIgnoreCase("off")) enabled=false;
                else if(args.length==1) who=args[0];
                else throw new IllegalArgumentException("Use /fly [on|off] [player].");
            }
            if(args.length==2) who=args[1];
            target=targets.target(sender,who,"hollowutils.fly.others");
            if(target.getGameMode()==GameMode.CREATIVE||target.getGameMode()==GameMode.SPECTATOR)
                throw new IllegalArgumentException("Creative and spectator modes manage their own flight. Use survival or adventure to toggle flight.");
            State old=settings.apply(target);
            next=new State(enabled==null?!old.flight():enabled,old.flySpeed(),old.walkSpeed());
        } else {
            if(args.length<1||args.length>2) return false;
            float speed=speed(args[0],name.equals("flyspeed"));
            target=targets.target(sender,args.length==2?args[1]:null,"hollowutils."+name+".others");
            State old=settings.apply(target);
            next=name.equals("flyspeed")?new State(old.flight(),speed,old.walkSpeed()):new State(old.flight(),old.flySpeed(),speed);
        }
        if(target.isDead()) throw new IllegalArgumentException("Wait until the player has respawned.");
        save.accept(target,next);next.apply(target);
        String label=name.equals("fly")?"Flight":name.equals("flyspeed")?"Flight speed":"Walk speed";
        String value=name.equals("fly")?(next.flight()?"enabled":"disabled"):String.format(Locale.ROOT,"%.2f / 10",(name.equals("flyspeed")?next.flySpeed():next.walkSpeed())*10);
        Responses.success(sender,label,target.getName()+" · "+value);
        if(sender!=target) Responses.info(target,label,value);
        return true;
    }
    List<String> complete(CommandSender sender,String name,String[] args) {
        if(!sender.hasPermission("hollowutils."+name)) return List.of();
        if(args.length==2) return targets.targetNames(sender,"hollowutils."+name+".others");
        if(args.length==1) {
            if(name.equals("fly")) { List<String> options=new ArrayList<>(List.of("on","off"));options.addAll(targets.targetNames(sender,"hollowutils.fly.others"));return options; }
            return List.of("0","1","2","3","5","10","reset");
        }
        return List.of();
    }
}
