package io.github.troytmoneys.hollowutils;

import org.bukkit.World;
import org.bukkit.WorldType;
import java.util.*;
import java.util.function.LongSupplier;

/** Validated creation plan; the same list drives generation and persisted group membership. */
record ServerCreation(String name, Mode mode, long seed) {
    enum Mode { FULL, NORMAL, FLAT }
    record Dimension(String name, World.Environment environment, WorldType terrain) { }
    ServerCreation {
        ServerGroup.worldNames(name); // Validate before any disk or world operations.
        Objects.requireNonNull(mode);
    }
    static List<String> options() { return List.of("full","normal","flat"); }
    static ServerCreation parse(String[] args, LongSupplier randomSeed) {
        if (args.length<1 || args.length>3) throw new IllegalArgumentException("Use /createserver <name> [full|normal|flat] [seed].");
        Mode mode=Mode.FULL;
        Long seed=null;
        if (args.length>=2) {
            String option=args[1].toLowerCase(Locale.ROOT);
            if (options().contains(option)) mode=Mode.valueOf(option.toUpperCase(Locale.ROOT));
            else if (args.length==2) seed=parseSeed(args[1]); // Preserve legacy /createserver <name> <seed>.
            else throw new IllegalArgumentException("Choose full, normal or flat before the seed.");
        }
        if (args.length==3) seed=parseSeed(args[2]);
        return new ServerCreation(args[0],mode,seed==null?randomSeed.getAsLong():seed);
    }
    private static long parseSeed(String input) {
        try { return Long.parseLong(input); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Choose full, normal or flat, and use a numeric 64-bit seed if supplied."); }
    }
    List<Dimension> dimensions() {
        List<String> names=ServerGroup.worldNames(name);
        Dimension overworld=new Dimension(names.getFirst(),World.Environment.NORMAL,mode==Mode.FLAT?WorldType.FLAT:WorldType.NORMAL);
        return mode==Mode.FULL ? List.of(overworld,new Dimension(names.get(1),World.Environment.NETHER,WorldType.NORMAL),
                new Dimension(names.get(2),World.Environment.THE_END,WorldType.NORMAL)) : List.of(overworld);
    }
    List<String> worldNames() { return dimensions().stream().map(Dimension::name).toList(); }
}
