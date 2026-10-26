package io.github.troytmoneys.hollowutils;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerLifecycleTest {
    private static class Machine implements ServerLifecycle.Backend {
        final ServerGroup group=new ServerGroup("alpha",List.of("world","nether","end"));
        final Set<String> loaded=new HashSet<>(group.worlds),occupied=new HashSet<>();
        final Map<String,Boolean> autoload=new HashMap<>();
        final List<String> actions=new ArrayList<>();
        String failLoad,failUnload;boolean failIsolation;int saveCalls,failSave;
        boolean persistedOffline;
        public boolean loaded(String name) { return loaded.contains(name); }
        public boolean occupied(String name) { return occupied.contains(name); }
        public void load(String name) {
            assertTrue(group.unloaded);actions.add("load:"+name);
            if(name.equals(failLoad))throw new IllegalStateException("Load blocked");loaded.add(name);
        }
        public void unload(String name) {
            assertTrue(group.unloaded);actions.add("unload:"+name);
            if(name.equals(failUnload))throw new IllegalStateException("Unload blocked");loaded.remove(name);
        }
        public void autoLoad(String name,boolean enabled) { actions.add("auto:"+name+":"+enabled);autoload.put(name,enabled); }
        public void isolation(ServerGroup group) {
            assertTrue(group.unloaded);assertTrue(loaded.containsAll(group.worlds));actions.add("isolation");
            if(failIsolation)throw new IllegalStateException("Group conflict");
        }
        public void saveRegistry() throws IOException {
            saveCalls++;if(saveCalls==failSave)throw new IOException("Disk failure");
            persistedOffline=group.unloaded;actions.add("save:"+persistedOffline);
        }
        ServerLifecycle controller() { return new ServerLifecycle(this); }
    }
    @Test void unloadSavesAndLocksWholeGroupWithoutChangingProfileMetadata() throws Exception {
        var machine=new Machine();UUID user=UUID.randomUUID();machine.group.whitelist.add(user);machine.group.maintenance=true;
        machine.controller().unload(machine.group);
        assertTrue(machine.loaded.isEmpty());assertTrue(machine.group.unloaded);assertTrue(machine.persistedOffline);
        assertFalse(machine.group.transitioning);assertFalse(machine.group.accessible(user,true));
        assertEquals("save:true",machine.actions.getFirst());assertEquals(Set.of(user),machine.group.whitelist);assertTrue(machine.group.maintenance);
        assertEquals(List.of("world","nether","end"),machine.group.worlds);
        assertTrue(machine.autoload.values().stream().noneMatch(Boolean::booleanValue));
    }
    @Test void loadHandlesPartialStateAndVerifiesIsolationBeforeReopening() throws Exception {
        var machine=new Machine();machine.group.unloaded=true;machine.loaded.retainAll(Set.of("world"));
        machine.controller().load(machine.group);
        assertFalse(machine.group.unloaded);assertFalse(machine.persistedOffline);assertTrue(machine.loaded.containsAll(machine.group.worlds));
        assertFalse(machine.actions.contains("load:world"));
        assertTrue(machine.actions.indexOf("isolation")<machine.actions.indexOf("auto:world:true"));
        assertEquals("save:false",machine.actions.getLast());
    }
    @Test void occupiedLastDimensionStopsTheEntireOperationBeforeAnyChanges() {
        var machine=new Machine();machine.occupied.add("end");
        assertThrows(IllegalArgumentException.class,()->machine.controller().unload(machine.group));
        assertThrows(IllegalArgumentException.class,()->machine.controller().load(machine.group));
        assertTrue(machine.actions.isEmpty());assertFalse(machine.group.unloaded);assertEquals(3,machine.loaded.size());
    }
    @Test void partialUnloadFailureRemainsLockedAndRetryCompletes() throws Exception {
        var machine=new Machine();machine.failUnload="nether";
        assertThrows(IllegalStateException.class,()->machine.controller().unload(machine.group));
        assertTrue(machine.group.unloaded);assertTrue(machine.persistedOffline);assertFalse(machine.group.transitioning);
        assertFalse(machine.loaded.contains("world"));assertTrue(machine.loaded.contains("nether"));
        machine.failUnload=null;machine.controller().unload(machine.group);assertTrue(machine.loaded.isEmpty());
    }
    @Test void partialLoadAndGroupConflictNeverExposeAGroupButCanBeRetried() throws Exception {
        var machine=new Machine();machine.loaded.clear();machine.group.unloaded=true;machine.failLoad="end";
        assertThrows(IllegalStateException.class,()->machine.controller().load(machine.group));
        assertTrue(machine.group.unloaded);assertTrue(machine.persistedOffline);assertFalse(machine.group.accessible(UUID.randomUUID(),true));
        machine.failLoad=null;machine.failIsolation=true;
        assertThrows(IllegalStateException.class,()->machine.controller().load(machine.group));
        assertTrue(machine.group.unloaded);assertTrue(machine.autoload.values().stream().noneMatch(Boolean::booleanValue));
        machine.failIsolation=false;machine.controller().load(machine.group);assertFalse(machine.group.unloaded);
    }
    @Test void failedFinalSaveRestoresPersistentLockAndDisablesAutoload() {
        var machine=new Machine();machine.loaded.clear();machine.group.unloaded=true;machine.failSave=2;
        assertThrows(IllegalStateException.class,()->machine.controller().load(machine.group));
        assertTrue(machine.persistedOffline);assertTrue(machine.group.unloaded);assertFalse(machine.group.transitioning);
        assertTrue(machine.autoload.values().stream().noneMatch(Boolean::booleanValue));
    }
    @Test void failedInitialSaveDoesNotTouchWorldsOrChangePriorStatus() {
        var machine=new Machine();machine.failSave=1;
        assertThrows(IOException.class,()->machine.controller().unload(machine.group));
        assertFalse(machine.group.unloaded);assertTrue(machine.actions.isEmpty());assertEquals(3,machine.loaded.size());
    }
    @Test void lobbyDeletingAndBusyGroupsAreProtected() {
        var machine=new Machine();var controller=machine.controller();
        assertThrows(IllegalArgumentException.class,()->controller.unload(new ServerGroup("lobby",List.of("world"))));
        machine.group.deleting=true;assertThrows(IllegalArgumentException.class,()->controller.load(machine.group));
        machine.group.deleting=false;machine.group.transitioning=true;assertThrows(IllegalArgumentException.class,()->controller.unload(machine.group));
        assertTrue(machine.actions.isEmpty());
    }
}
