package io.github.troytmoneys.hollowutils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Supplier;

final class InfoCommands {
    static final Set<String> COMMANDS=Set.of("ping","version");
    private final UtilityCommands targets;private final Supplier<String> version;
    InfoCommands(UtilityCommands targets,Supplier<String> version) { this.targets=targets;this.version=version; }
    boolean execute(CommandSender sender,String command,String[] args) {
        UtilityCommands.requirePermission(sender,"hollowutils."+command);
        if(command.equals("version")) {
            if(args.length!=0)return false;
            Responses.info(sender,"HollowUtils","v"+version.get());return true;
        }
        if(args.length>1)return false;
        Player target=targets.target(sender,args.length==1?args[0]:null,"hollowutils.ping.others");
        Responses.info(sender,"Ping",target.getName()+" · "+target.getPing()+" ms");return true;
    }
    List<String> complete(CommandSender sender,String command,String[] args) {
        return command.equals("ping")&&args.length==1&&sender.hasPermission("hollowutils.ping")?targets.targetNames(sender,"hollowutils.ping.others"):List.of();
    }
}
