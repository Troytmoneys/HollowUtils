package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WarpStoreTest {
    @TempDir Path temp;
    private WarpStore.Destination destination() { return new WarpStore.Destination(UUID.randomUUID(),1.25,64,-10.5,180,35); }
    @Test void preservesIndependentSameNamedWarpsAndFacingAcrossRestarts() throws Exception {
        Path file=temp.resolve("warps.yml"); var store=new WarpStore(file);
        var alpha=destination(); var beta=destination();
        store.create("alpha","home",alpha); store.create("beta","home",beta);
        var restored=new WarpStore(file);
        assertEquals(alpha,restored.get("alpha","home")); assertEquals(beta,restored.get("beta","home"));
        assertNull(restored.get("gamma","home"));
        restored.delete("alpha","home");
        assertNull(new WarpStore(file).get("alpha","home"));
        assertEquals(beta,new WarpStore(file).get("beta","home"));
    }
    @Test void rejectsOverwriteAndConfigurationPathInjectionWithoutLosingOldWarp() throws Exception {
        var store=new WarpStore(temp.resolve("warps.yml")); var old=destination(); store.create("alpha","home",old);
        assertThrows(IllegalArgumentException.class,()->store.create("alpha","home",destination()));
        for(String name:List.of("../world","a.b","a:b","UPPER","")) assertThrows(IllegalArgumentException.class,()->store.create("alpha",name,destination()));
        assertEquals(old,store.get("alpha","home")); assertEquals(List.of("home"),store.names("alpha"));
    }
    @Test void failedWritesDoNotLeavePhantomWarpsOrDeleteExistingOnes() throws Exception {
        Path file=temp.resolve("warps.yml"); var store=new WarpStore(file); var old=destination(); store.create("alpha","home",old);
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("blocker"),"x");
        assertThrows(IOException.class,()->store.create("alpha","new",destination()));
        assertNull(store.get("alpha","new"));
        assertThrows(IOException.class,()->store.delete("alpha","home"));
        assertEquals(old,store.get("alpha","home"));
    }
    @Test void malformedDataIsNotSilentlyReplaced() throws Exception {
        Path file=Files.writeString(temp.resolve("warps.yml"),"alpha:\n  home:\n    world: not-a-uuid\n");
        String before=Files.readString(file);
        assertThrows(IOException.class,()->new WarpStore(file));
        assertEquals(before,Files.readString(file));
    }
    @Test void homesWithTheSameNameStayPrivateToOwnerAndServerAcrossRestarts() throws Exception {
        Path file=temp.resolve("homes.yml"); var store=new WarpStore(file);
        UUID alice=UUID.randomUUID(),bob=UUID.randomUUID();
        String first=WarpStore.homeScope("alpha",alice),second=WarpStore.homeScope("alpha",bob),third=WarpStore.homeScope("beta",alice);
        var a=destination(); var b=destination(); var c=destination();
        store.create(first,"bed",a); store.create(second,"bed",b); store.create(third,"bed",c);
        var restored=new WarpStore(file);
        assertEquals(a,restored.get(first,"bed")); assertEquals(b,restored.get(second,"bed")); assertEquals(c,restored.get(third,"bed"));
        restored.delete(first,"bed");
        assertEquals(List.of(),restored.names(first)); assertEquals(List.of("bed"),restored.names(second));
        assertEquals(c,new WarpStore(file).get(third,"bed"));
    }
    @Test void rejectsNonfiniteCoordinatesAndSortsSuggestions() throws Exception {
        assertThrows(IllegalArgumentException.class,()->new WarpStore.Destination(UUID.randomUUID(),Double.NaN,1,1,0,0));
        var store=new WarpStore(temp.resolve("warps.yml")); store.create("alpha","zoo",destination()); store.create("alpha","arena",destination());
        assertEquals(List.of("arena","zoo"),store.names("alpha"));
    }
}
