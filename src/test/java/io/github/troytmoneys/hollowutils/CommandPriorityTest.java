package io.github.troytmoneys.hollowutils;

import org.bukkit.command.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandPriorityTest {
    private static Command command(String name) {
        return new Command(name) {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
        };
    }
    @Test void takesOverConflictWithoutChangingNamespacedAlternativeAndRestoresOnShutdown() {
        Command essentials = command("gmc"), hollow = command("gmc");
        Map<String, Command> known = new HashMap<>(Map.of("gmc", essentials, "essentials:gmc", essentials));
        CommandPriority priority = new CommandPriority(Map.of("gmc", hollow));
        priority.claim(known); priority.claim(known);
        assertSame(hollow, known.get("gmc"));
        assertSame(essentials, known.get("essentials:gmc"));
        priority.restore(known);
        assertSame(essentials, known.get("gmc"));
    }
    @Test void reclaimsAfterLateRegistrationButDoesNotClobberAnotherPluginOnShutdown() {
        Command first = command("heal"), hollow = command("heal"), late = command("heal");
        Map<String, Command> known = new HashMap<>(Map.of("heal", first));
        CommandPriority priority = new CommandPriority(Map.of("heal", hollow));
        priority.claim(known); known.put("heal", late); priority.claim(known);
        assertSame(hollow, known.get("heal"));
        priority.restore(known); assertSame(late, known.get("heal"));
        priority.claim(known); known.put("heal", first); priority.restore(known);
        assertSame(first, known.get("heal"));
    }
}
