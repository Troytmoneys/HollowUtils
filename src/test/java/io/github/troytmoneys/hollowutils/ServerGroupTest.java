package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerGroupTest {
    @Test void rejectsPathsAndConfigurationInjection() {
        for (String name : List.of("../world", "a.b", "A", "a/b", "a\\b", "", "-a", "a:b", "a".repeat(25))) {
            assertFalse(ServerGroup.validId(name), name);
            assertThrows(IllegalArgumentException.class, () -> ServerGroup.worldNames(name));
        }
    }
    @Test void dimensionsAreDistinctAndDeterministic() {
        assertEquals(List.of("hu_survival", "hu_survival_nether", "hu_survival_the_end"), ServerGroup.worldNames("survival"));
        assertTrue(ServerGroup.validId("a1_b-c"));
    }
    @Test void worldNamesCannotBeModified() {
        var input = new ArrayList<>(List.of("world"));
        var server = new ServerGroup("test", input);
        input.clear();
        assertEquals(List.of("world"), server.worlds);
        assertThrows(UnsupportedOperationException.class, () -> server.worlds.clear());
    }
    @Test void accessControlAndMaintenanceAreIndependent() {
        UUID member = UUID.randomUUID(), stranger = UUID.randomUUID();
        var server = new ServerGroup("test", List.of("world"));
        assertTrue(server.accessible(stranger, false));
        server.whitelisted = true; server.whitelist.add(member);
        assertTrue(server.accessible(member, false));
        assertFalse(server.accessible(stranger, false));
        assertTrue(server.accessible(stranger, true));
        server.maintenance = true;
        assertFalse(server.accessible(member, false));
        assertTrue(server.accessible(member, true));
        server.deleting = true;
        assertFalse(server.accessible(member, true), "Even administrators must not enter partially deleted worlds");
    }
}
