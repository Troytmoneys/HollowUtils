package io.github.troytmoneys.hollowutils;

import org.bukkit.command.Command;
import java.util.*;

/** Changes only unqualified command roots and remembers the previous owners for clean restoration. */
final class CommandPriority {
    private final Map<String, Command> owned;
    private final Map<String, Command> displaced = new HashMap<>();
    CommandPriority(Map<String, ? extends Command> owned) { this.owned = Map.copyOf(owned); }
    void claim(Map<String, Command> known) {
        owned.forEach((label, command) -> {
            if (known.get(label) != command) {
                displaced.put(label, known.get(label));
                known.put(label, command);
            }
        });
    }
    void restore(Map<String, Command> known) {
        owned.forEach((label, command) -> {
            if (known.get(label) == command) {
                Command previous = displaced.get(label);
                if (previous == null) known.remove(label); else known.put(label, previous);
            }
        });
        displaced.clear();
    }
}
