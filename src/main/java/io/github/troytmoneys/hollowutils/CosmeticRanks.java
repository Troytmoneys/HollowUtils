package io.github.troytmoneys.hollowutils;

import org.bukkit.entity.Player;
import java.util.*;

interface CosmeticRanks {
    record Choice(String name,int weight,String prefix) { }
    String actualPrefix(Player player);
    List<Choice> choices(Player player);
    String groupPrefix(Player player,String group);
    static CosmeticRanks none() {
        return new CosmeticRanks() {
            public String actualPrefix(Player player) { return "[Player]"; }
            public List<Choice> choices(Player player) { return List.of(); }
            public String groupPrefix(Player player,String group) { return null; }
        };
    }
}
