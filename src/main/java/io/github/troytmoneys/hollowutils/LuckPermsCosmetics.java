package io.github.troytmoneys.hollowutils;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import org.bukkit.entity.Player;
import java.util.*;

/** Reads metadata only: never changes nodes, groups, primary group, or weights. */
final class LuckPermsCosmetics implements CosmeticRanks {
    private final LuckPerms api=LuckPermsProvider.get();
    public String actualPrefix(Player player) { return api.getPlayerAdapter(Player.class).getMetaData(player).getPrefix(); }
    public List<Choice> choices(Player player) {
        return api.getGroupManager().getLoadedGroups().stream().map(group->new Choice(group.getName(),group.getWeight().orElse(0),prefix(player,group)))
                .sorted(Comparator.comparingInt(Choice::weight).reversed().thenComparing(Choice::name)).toList();
    }
    public String groupPrefix(Player player,String group) {
        Group value=api.getGroupManager().getGroup(group);return value==null?null:prefix(player,value);
    }
    private String prefix(Player player,Group group) {
        String prefix=group.getCachedData().getMetaData(api.getPlayerAdapter(Player.class).getQueryOptions(player)).getPrefix();
        return prefix==null||prefix.isBlank()?"["+group.getName()+"]":prefix;
    }
}
