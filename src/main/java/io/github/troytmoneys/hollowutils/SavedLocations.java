package io.github.troytmoneys.hollowutils;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Coordinates deserialize without requiring the world to be loaded. */
final class SavedLocations {
    private SavedLocations() { }
    static void write(YamlConfiguration yaml,String key,Location location) {
        if(location.getWorld()==null) throw new IllegalArgumentException("Cannot save a location without a world.");
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("world-id",location.getWorld().getUID().toString());
        fields.put("x",location.getX());fields.put("y",location.getY());fields.put("z",location.getZ());
        fields.put("yaw",location.getYaw());fields.put("pitch",location.getPitch());
        yaml.set(key,null);yaml.createSection(key,fields);
    }
    static Location read(YamlConfiguration yaml,String key) {
        Object saved=yaml.get(key);
        if(saved instanceof Location location) return location.clone();
        String id=yaml.getString(key+".world-id");
        if(id==null) return null;
        World world=Bukkit.getWorld(UUID.fromString(id));
        return world==null?null:new Location(world,yaml.getDouble(key+".x"),yaml.getDouble(key+".y"),yaml.getDouble(key+".z"),(float)yaml.getDouble(key+".yaw"),(float)yaml.getDouble(key+".pitch"));
    }
    static boolean migrate(YamlConfiguration yaml) {
        boolean changed=false;
        for(var entry:new LinkedHashMap<>(yaml.getValues(true)).entrySet()) if(entry.getValue() instanceof Location location) {
            write(yaml,entry.getKey(),location);changed=true;
        }
        return changed;
    }
}
