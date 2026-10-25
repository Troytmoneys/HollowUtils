package io.github.troytmoneys.hollowutils;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Stores coordinates independently of loaded Bukkit worlds. */
final class WarpStore {
    record Destination(UUID world, double x, double y, double z, float yaw, float pitch) {
        Destination {
            Objects.requireNonNull(world);
            if (!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))
                throw new IllegalArgumentException("Warp coordinates must be finite.");
        }
    }
    private final Path file;
    private final Map<String, Map<String, Destination>> warps = new HashMap<>();
    WarpStore(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file)) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            for (String scope : yaml.getKeys(false)) {
                checkScope(scope); var section = Objects.requireNonNull(yaml.getConfigurationSection(scope));
                for (String name : section.getKeys(false)) {
                    checkName(name); String key = scope+"."+name+".";
                    Destination destination = new Destination(UUID.fromString(Objects.requireNonNull(yaml.getString(key+"world"))),
                            yaml.getDouble(key+"x"),yaml.getDouble(key+"y"),yaml.getDouble(key+"z"),(float)yaml.getDouble(key+"yaw"),(float)yaml.getDouble(key+"pitch"));
                    warps.computeIfAbsent(scope, ignored -> new HashMap<>()).put(name,destination);
                }
            }
        } catch (Exception e) { throw new IOException("Cannot read "+file.getFileName()+"; existing destinations were not overwritten.",e); }
    }
    static String homeScope(String server, UUID owner) {
        checkName(server); return server+"--u"+Objects.requireNonNull(owner);
    }
    private static void checkScope(String scope) {
        if (scope==null || !scope.matches("[a-z][a-z0-9_-]{0,95}")) throw new IllegalArgumentException("Invalid destination namespace.");
    }
    static String checkName(String name) {
        if (!ServerGroup.validId(name)) throw new IllegalArgumentException("Use 1–24 lowercase letters, digits, _ or -, starting with a letter.");
        return name;
    }
    List<String> names(String scope) { return warps.getOrDefault(scope,Map.of()).keySet().stream().sorted().toList(); }
    Destination get(String scope,String name) { return warps.getOrDefault(scope,Map.of()).get(name); }
    void create(String scope,String name,Destination destination) throws IOException {
        checkScope(scope); checkName(name); Objects.requireNonNull(destination);
        if (get(scope,name)!=null) throw new IllegalArgumentException("Warp already exists. Delete it first to replace it.");
        var group = warps.computeIfAbsent(scope,ignored -> new HashMap<>());
        group.put(name,destination);
        try { save(); } catch (IOException e) { group.remove(name); throw e; }
    }
    void delete(String scope,String name) throws IOException {
        var group=warps.get(scope);
        if (group==null || !group.containsKey(name)) throw new IllegalArgumentException("No warp with that name in this server.");
        Destination old=group.remove(name);
        try { save(); } catch (IOException e) { group.put(name,old); throw e; }
    }
    private void save() throws IOException {
        YamlConfiguration yaml=new YamlConfiguration();
        warps.forEach((scope,group) -> group.forEach((name,d) -> {
            String key=scope+"."+name+".";
            yaml.set(key+"world",d.world().toString()); yaml.set(key+"x",d.x()); yaml.set(key+"y",d.y()); yaml.set(key+"z",d.z()); yaml.set(key+"yaw",d.yaw()); yaml.set(key+"pitch",d.pitch());
        }));
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),"warps-",".tmp");
        try {
            Files.writeString(temp,yaml.saveToString());
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
