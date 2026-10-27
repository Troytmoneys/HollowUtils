package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InfoCommandsTest {
    private static final class Account {
        final UUID uuid=UUID.randomUUID();final Set<String> permissions=new HashSet<>();final List<String> messages=new ArrayList<>();final Player player;
        Account(String name) {
            player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
                case "getName"->name;
                case "getUniqueId"->uuid;
                case "hasPermission"->permissions.contains(a[0]);
                case "canSee"->true;
                case "getPing"->73;
                case "sendMessage"->{messages.add(PlainTextComponentSerializer.plainText().serialize((Component)a[0]));yield null;}
                default->throw new UnsupportedOperationException(m.getName());
            });
        }
    }
    @Test void pingTargetsNeedPermissionAndCannotExposeOtherServers() {
        var a=new Account("Alice");var b=new Account("Bob");var foreign=new Account("Foreign");a.permissions.add("hollowutils.ping");
        var utilities=new UtilityCommands(()->List.of(a.player,b.player,foreign.player),(source,target)->!target.getName().equals("Foreign"),p->20);
        var commands=new InfoCommands(utilities,()->"1.1.0");commands.execute(a.player,"ping",new String[0]);assertTrue(a.messages.getLast().contains("Alice · 73 ms"));
        assertThrows(IllegalArgumentException.class,()->commands.execute(a.player,"ping",new String[]{"Bob"}));
        a.permissions.add("hollowutils.ping.others");commands.execute(a.player,"ping",new String[]{"Bob"});
        assertThrows(IllegalArgumentException.class,()->commands.execute(a.player,"ping",new String[]{"Foreign"}));
    }
    @Test void versionUsesInstalledMetadataSupplierAndRejectsExtraArguments() {
        var a=new Account("Alice");a.permissions.add("hollowutils.version");
        var utilities=new UtilityCommands(()->List.of(a.player),(x,y)->true,p->20);var commands=new InfoCommands(utilities,()->"1.1.0");
        assertTrue(commands.execute(a.player,"version",new String[0]));assertTrue(a.messages.getLast().contains("v1.1.0"));
        assertFalse(commands.execute(a.player,"version",new String[]{"other"}));
    }
}
