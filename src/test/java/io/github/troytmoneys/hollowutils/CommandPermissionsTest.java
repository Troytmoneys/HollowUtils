package io.github.troytmoneys.hollowutils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class CommandPermissionsTest {
    private YamlConfiguration descriptor() throws Exception {
        var yaml=new YamlConfiguration();
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/plugin.yml")),StandardCharsets.UTF_8)) { yaml.load(reader); }
        return yaml;
    }
    @Test void everyRegisteredCommandHasADeclaredPermissionMatchingTheRuntimeGate() throws Exception {
        var yaml=descriptor();var commands=Objects.requireNonNull(yaml.getConfigurationSection("commands"));
        for(String command:commands.getKeys(false)) {
            String node=yaml.getString("commands."+command+".permission");
            assertEquals(CommandPermissions.node(command),node,command);
            assertTrue(yaml.isConfigurationSection("permissions."+node),"Undeclared permission for "+command);
            assertNotNull(yaml.getString("permissions."+node+".default"),"Missing default for "+command);
        }
    }
    @Test void legacyParentPermissionsGrantIndividualCoreCommandsAndDangerousCommandsDefaultToOp() throws Exception {
        var yaml=descriptor();
        assertTrue(yaml.getBoolean("permissions.hollowutils.use.children.hollowutils.server"));
        assertTrue(yaml.getBoolean("permissions.hollowutils.use.children.hollowutils.spawn"));
        assertTrue(yaml.getBoolean("permissions.hollowutils.admin.children.hollowutils.createserver"));
        for(String name:new String[]{"god","give","i","enchant","togglebreakdelay","loadserver","unloadserver"})
            assertEquals("op",yaml.getString("permissions.hollowutils."+name+".default"));
        assertEquals("hollowutils.msg",CommandPermissions.node("whisper"));
        assertEquals("hollowutils.clear",CommandPermissions.node("ci"));
        assertEquals("hollowutils.gamemode",CommandPermissions.node("gmc"));
    }
}
