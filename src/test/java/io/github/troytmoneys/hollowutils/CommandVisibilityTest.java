package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandVisibilityTest {
    @Test void normalThirdPartyCommandsRemainVisibleWhileNamespacesAndUnauthorizedOwnedRootsAreHidden() {
        Set<String> owned=Set.of("god","spawn");
        assertTrue(CommandVisibility.visible("plugins",owned::contains,name->false));
        assertTrue(CommandVisibility.visible("warpotherplugin",owned::contains,name->false));
        assertFalse(CommandVisibility.visible("hollowutils:fk",owned::contains,name->true));
        assertFalse(CommandVisibility.visible("essentials:home",owned::contains,name->true));
        assertFalse(CommandVisibility.visible("minecraft:give",owned::contains,name->true));
        assertFalse(CommandVisibility.visible("god",owned::contains,name->false));
        assertTrue(CommandVisibility.visible("spawn",owned::contains,name->name.equals("spawn")));
    }
    @Test void partialRootsAndOtherPluginArgumentsCompleteButHiddenNamespacedRootsDoNot() {
        Set<String> roots=Set.of("spawn","plugins","plot");
        assertTrue(CommandVisibility.completionAllowed("/pl",roots));
        assertTrue(CommandVisibility.completionAllowed("/",roots));
        assertTrue(CommandVisibility.completionAllowed("/plot claim ",roots));
        assertFalse(CommandVisibility.completionAllowed("/hollowutils:spawn ",roots));
        assertFalse(CommandVisibility.completionAllowed("/essentials:msg ",roots));
        assertFalse(CommandVisibility.completionAllowed("/give ",roots));
        assertFalse(CommandVisibility.rootCompletion("/enchant minecraft:efficiency"));
        assertEquals("enchant",CommandVisibility.root("/ENCHANT minecraft:efficiency"));
    }
}
