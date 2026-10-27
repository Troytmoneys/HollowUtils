package io.github.troytmoneys.hollowutils;

import com.destroystokyo.paper.profile.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DisguiseProfilesTest {
    private static PlayerProfile profile(UUID uuid,String initial,Collection<ProfileProperty> initialProperties) {
        String[] name={initial};Set<ProfileProperty> properties=new HashSet<>(initialProperties);
        return (PlayerProfile)Proxy.newProxyInstance(PlayerProfile.class.getClassLoader(),new Class<?>[]{PlayerProfile.class},(p,m,a)->switch(m.getName()) {
            case "clone"->profile(uuid,name[0],properties);
            case "getId","getUniqueId"->uuid;
            case "getName"->name[0];
            case "setName"->{String previous=name[0];name[0]=(String)a[0];yield previous;}
            case "getProperties"->properties;
            case "removeProperty"->properties.removeIf(property->property.getName().equals(a[0]));
            case "setProperties"->{for(Object item:(Collection<?>)a[0])properties.add((ProfileProperty)item);yield null;}
            default->throw new UnsupportedOperationException(m.getName());
        });
    }
    @Test void cosmeticSkinAndNameNeverReplaceTheRealUuidOrMutateOriginalProfiles() {
        UUID realId=UUID.randomUUID(),skinId=UUID.randomUUID();
        var oldTexture=new ProfileProperty("textures","old","signature");var newTexture=new ProfileProperty("textures","new","signature");
        var retained=new ProfileProperty("other","kept");var ignored=new ProfileProperty("other","untrusted-skin-property");
        var real=profile(realId,"RealName",List.of(oldTexture,retained));var skin=profile(skinId,"SkinAccount",List.of(newTexture,ignored));
        var changed=DisguiseProfiles.build(real,skin,"FakeName");
        assertEquals(realId,changed.getId());assertEquals("FakeName",changed.getName());
        assertTrue(changed.getProperties().contains(newTexture));assertTrue(changed.getProperties().contains(retained));
        assertFalse(changed.getProperties().contains(oldTexture));assertFalse(changed.getProperties().contains(ignored));
        assertEquals("RealName",real.getName());assertTrue(real.getProperties().contains(oldTexture));assertEquals(skinId,skin.getId());
    }
}
