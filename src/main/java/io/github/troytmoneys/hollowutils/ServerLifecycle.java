package io.github.troytmoneys.hollowutils;

import java.util.Set;

/** Group-wide load state. Failed transitions remain persistently locked and may be retried. */
final class ServerLifecycle {
    static final Set<String> COMMANDS=Set.of("loadserver","unloadserver");
    interface Backend {
        boolean loaded(String world);
        boolean occupied(String world);
        void load(String world) throws Exception;
        void unload(String world) throws Exception;
        void autoLoad(String world,boolean enabled) throws Exception;
        void isolation(ServerGroup group) throws Exception;
        void saveRegistry() throws Exception;
    }
    private final Backend backend;
    ServerLifecycle(Backend backend) { this.backend=backend; }
    void unload(ServerGroup group) throws Exception { change(group,false); }
    void load(ServerGroup group) throws Exception { change(group,true); }
    private void change(ServerGroup group,boolean loading) throws Exception {
        if(group.id.equals("lobby")) throw new IllegalArgumentException("The fallback lobby cannot be loaded or unloaded with this command.");
        if(group.deleting) throw new IllegalArgumentException("This server is locked for creation/deletion; resolve that operation first.");
        if(group.transitioning) throw new IllegalArgumentException("A server load/unload operation is already in progress.");
        // Reject BEFORE changing flags or touching any world, including partially loaded groups.
        if(group.worlds.stream().anyMatch(backend::occupied)) throw new IllegalArgumentException("Move every player out of this server before loading or unloading its worlds.");
        boolean wasOffline=group.unloaded;
        group.unloaded=true;
        try { backend.saveRegistry(); }
        catch(Exception e) { group.unloaded=wasOffline;throw e; }
        group.transitioning=true;
        try {
            for(String world:group.worlds) backend.autoLoad(world,false);
            for(String world:group.worlds) {
                if(loading&&!backend.loaded(world)) backend.load(world);
                if(!loading&&backend.loaded(world)) backend.unload(world);
                if(backend.loaded(world)!=loading) throw new IllegalStateException("World did not "+(loading?"load: ":"unload: ")+world);
            }
            if(loading) {
                backend.isolation(group);
                for(String world:group.worlds) backend.autoLoad(world,true);
                group.unloaded=false;
                backend.saveRegistry();
            }
        } catch(Exception failure) {
            group.unloaded=true;
            for(String world:group.worlds) try { backend.autoLoad(world,false); } catch(Exception e) { failure.addSuppressed(e); }
            try { backend.saveRegistry(); } catch(Exception e) { failure.addSuppressed(e); }
            throw new IllegalStateException("Operation incomplete for "+group.id+"; entry remains locked. Fix the console error, then retry /loadserver or /unloadserver. Cause: "+failure.getMessage(),failure);
        } finally { group.transitioning=false; }
    }
}
