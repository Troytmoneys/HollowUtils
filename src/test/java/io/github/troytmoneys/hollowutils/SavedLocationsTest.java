package io.github.troytmoneys.hollowutils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SavedLocationsTest {
    @Test void migratesLegacySpawnAndDeathRecordsIntoDataThatLoadsWithoutAWorldOrBukkitServer() throws Exception {
        UUID id=UUID.randomUUID();
        World world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(proxy,method,args)->switch(method.getName()) {
            case "getUID" -> id;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var yaml=new YamlConfiguration();
        yaml.set("servers.alpha.spawn",new Location(world,1.5,72,-8.25,135,20));
        yaml.set("player.alpha",new Location(world,4,10,9,-90,30));
        assertTrue(SavedLocations.migrate(yaml));
        String saved=yaml.saveToString();assertFalse(saved.contains("org.bukkit.Location"));
        // No Bukkit.getWorld call occurs while parsing these records.
        var restored=new YamlConfiguration();restored.loadFromString(saved);
        assertEquals(id.toString(),restored.getString("servers.alpha.spawn.world-id"));
        assertEquals(1.5,restored.getDouble("servers.alpha.spawn.x"));
        assertEquals(135,restored.getDouble("servers.alpha.spawn.yaw"));
        assertEquals(30,restored.getDouble("player.alpha.pitch"));
        assertFalse(SavedLocations.migrate(restored));assertEquals(saved,restored.saveToString());
    }
}
