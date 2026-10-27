package io.github.troytmoneys.hollowutils;

import com.destroystokyo.paper.profile.PlayerProfile;

final class DisguiseProfiles {
    private DisguiseProfiles() { }
    static PlayerProfile build(PlayerProfile real,PlayerProfile skin,String name) {
        PlayerProfile profile=real.clone();profile.setName(ChatFormat.name(name));profile.removeProperty("textures");
        profile.setProperties(skin.getProperties().stream().filter(property->property.getName().equals("textures")).toList());
        return profile;
    }
}
