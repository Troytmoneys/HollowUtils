package io.github.troytmoneys.hollowutils;

import org.bukkit.*;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.*;
import java.util.*;

/** Runtime bridge to Multiverse 5.x; no shaded or duplicate Multiverse classes. */
final class MultiverseBridge {
    private final ClassLoader coreLoader;
    private final ClassLoader inventoryLoader;
    private final Object worlds;
    private final Object inventories;
    private final Object groups;

    MultiverseBridge(Plugin core, Plugin inventory) {
        coreLoader = core.getClass().getClassLoader();
        inventoryLoader = inventory.getClass().getClassLoader();
        worlds = invoke(type(coreLoader, "org.mvplugins.multiverse.core.MultiverseCoreApi"), "get");
        inventories = invoke(type(inventoryLoader, "org.mvplugins.multiverse.inventories.MultiverseInventoriesApi"), "get");
        groups = invoke(inventories, "getWorldGroupManager");
        Object config = invoke(inventories, "getInventoriesConfig");
        var sharables = type(inventoryLoader, "org.mvplugins.multiverse.inventories.share.Sharables");
        // Enable all optional state EXCEPT economy and last-location, which may teleport on profile load.
        Object active = invoke(config, "getActiveOptionalShares");
        for (String field : List.of("ADVANCEMENTS", "GAME_STATISTICS", "RECIPES", "ENDER_CHEST")) {
            try { invoke(active, "add", sharables.getField(field).get(null)); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Multiverse-Inventories lacks " + field, e); }
        }
        checkTry(invoke(config, "setActiveOptionalShares", active));
        checkTry(invoke(config, "save"));
        invoke(groups, "recalculateApplicableShares");
    }
    private Object worldManager() { return invoke(worlds, "getWorldManager"); }
    void create(String name, World.Environment environment, long seed, WorldType terrain) {
        Object options = invoke(type(coreLoader, "org.mvplugins.multiverse.core.world.options.CreateWorldOptions"), "worldName", name);
        invoke(options, "environment", environment);
        invoke(options, "seed", seed);
        invoke(options, "worldType", terrain);
        checkAttempt(invoke(worldManager(), "createWorld", options));
        if (Bukkit.getWorld(name) == null) throw new IllegalStateException("Multiverse did not load " + name);
    }
    void unload(String name) {
        Object world = invoke(invoke(worldManager(), "getWorld", name), "get");
        Object options = invoke(type(coreLoader, "org.mvplugins.multiverse.core.world.options.UnloadWorldOptions"), "world", world);
        checkAttempt(invoke(worldManager(), "unloadWorld", options));
        if (Bukkit.getWorld(name) != null) throw new IllegalStateException("World failed to unload: " + name);
    }
    void load(String name) {
        Object world = invoke(invoke(worldManager(), "getWorld", name), "get");
        Object options = invoke(type(coreLoader, "org.mvplugins.multiverse.core.world.options.LoadWorldOptions"), "world", world);
        checkAttempt(invoke(worldManager(), "loadWorld", options));
    }
    void delete(String name) {
        Object option = invoke(worldManager(), "getWorld", name);
        Object world = invoke(option, "get");
        Object options = invoke(type(coreLoader, "org.mvplugins.multiverse.core.world.options.DeleteWorldOptions"), "world", world);
        checkAttempt(invoke(worldManager(), "deleteWorld", options));
    }
    void configure(ServerGroup server) {
        Object group = invoke(groups, "getGroup", "hollow_" + server.id);
        if (group == null && server.id.equals("lobby")) {
            for (Object candidate : (Collection<?>) invoke(groups, "getGroups")) {
                if (new HashSet<>((Collection<?>) invoke(candidate, "getConfigWorlds")).equals(new HashSet<>(server.worlds))) {
                    group = candidate; break;
                }
            }
        }
        final Object selectedGroup = group;
        // Never silently merge overlapping groups: that would break inventory isolation.
        for (String world : server.worlds) {
            Object memberships = invoke(groups, "getGroups");
            if (memberships instanceof Collection<?> collection) for (Object other : collection) {
                if (((Collection<?>) invoke(other, "getApplicableWorlds")).contains(world.toLowerCase(Locale.ROOT))
                        && other != selectedGroup
                        && !Objects.equals(invoke(other, "getName"), "hollow_" + server.id))
                    throw new IllegalStateException(world + " already belongs to MVI group " + invoke(other, "getName") + ". Remove that conflicting group membership first.");
            }
        }
        if (group == null) group = invoke(groups, "newEmptyGroup", "hollow_" + server.id);
        invoke(group, "addWorlds", server.worlds, false);
        Object shares = invoke(group, "getShares");
        Class<?> sharables = type(inventoryLoader, "org.mvplugins.multiverse.inventories.share.Sharables");
        Object selected = invoke(sharables, "allOf");
        for (String excluded : List.of("ECONOMY", "LAST_LOCATION")) {
            try { invoke(selected, "remove", sharables.getField(excluded).get(null)); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        invoke(shares, "clear");
        invoke(shares, "addAll", selected);
        invoke(groups, "updateGroup", group);
    }
    void removeGroup(String id) {
        Object group = invoke(groups, "getGroup", "hollow_" + id);
        if (group != null) invoke(groups, "removeGroup", group);
    }
    private static void checkAttempt(Object attempt) {
        if (!Boolean.TRUE.equals(invoke(attempt, "isSuccess")))
            throw new IllegalStateException("Multiverse: " + invoke(attempt, "getFailureReason"));
    }
    private static void checkTry(Object attempt) {
        if (Boolean.TRUE.equals(invoke(attempt, "isFailure")))
            throw new IllegalStateException("Multiverse configuration could not be saved");
    }
    private static Class<?> type(ClassLoader loader, String name) {
        try { return Class.forName(name, true, loader); }
        catch (ClassNotFoundException e) { throw new IllegalStateException("Requires compatible Multiverse 5.x: " + name, e); }
    }
    // Multiverse intentionally keeps YamlWorldGroupManager and its abstract superclass
    // package-private. Invoke the method declared by its public API interface instead.
    private static Method accessibleMethod(Class<?> type, String name, Class<?>[] parameters) {
        if (Modifier.isPublic(type.getModifiers())) {
            try {
                Method method = type.getMethod(name, parameters);
                if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) return method;
            } catch (NoSuchMethodException ignored) { }
        }
        for (Class<?> api : type.getInterfaces()) {
            Method method = accessibleMethod(api, name, parameters);
            if (method != null) return method;
        }
        return type.getSuperclass() == null ? null : accessibleMethod(type.getSuperclass(), name, parameters);
    }
    static Object invoke(Object target, String name, Object... args) {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < args.length; i++) {
                Class<?> p = parameters[i];
                if (p == boolean.class) p = Boolean.class;
                if (p == long.class) p = Long.class;
                if (p == int.class) p = Integer.class;
                if (args[i] != null && !p.isInstance(args[i])) { match = false; break; }
            }
            if (!match) continue;
            Method accessible = accessibleMethod(type, name, parameters);
            if (accessible == null) throw new IllegalStateException("No public Multiverse API declaration for " + type.getName() + "." + name);
            try { return accessible.invoke(target instanceof Class<?> ? null : target, args); }
            catch (InvocationTargetException e) { throw new IllegalStateException("Multiverse API failed: " + name + ": " + e.getCause().getMessage(), e.getCause()); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Multiverse API failed: " + name, e); }
        }
        throw new IllegalStateException("Incompatible Multiverse API: " + type.getName() + "." + name);
    }
}
