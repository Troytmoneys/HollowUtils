package io.github.troytmoneys.hollowutils;

import io.papermc.paper.event.player.AsyncChatEvent;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerCommandEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class HollowUtils extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final Set<String> USER_COMMANDS = Set.of("server", "spawn");
    private static final Set<String> ADMIN_COMMANDS = Set.of("createserver", "backupserver", "whitelistserver", "deleteserver", "maintenanceserver", "setspawn");
    private final Map<String, ServerGroup> servers = new LinkedHashMap<>();
    // Chat runs asynchronously. It reads only immutable IDs from this concurrent snapshot.
    private final Map<UUID, String> memberships = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> chatPeers = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> allowedRoots = new ConcurrentHashMap<>();
    private final Map<String, Long> confirmations = new HashMap<>();
    private YamlConfiguration data;
    private File dataFile;
    private MultiverseBridge multiverse;
    private volatile boolean ready;
    private NamespacedKey menuKey;
    private UtilityCommands utilities;
    private ExtraCommands extras;
    private Warps warps;
    private Warps homes;
    private SocialMessages messages;
    private Moderation moderation;
    private CommandPriority commandPriority;

    @Override public void onEnable() {
        saveDefaultConfig();
        menuKey = new NamespacedKey(this, "server");
        getServer().getPluginManager().registerEvents(this, this);
        utilities = new UtilityCommands(Bukkit::getOnlinePlayers, (source, target) -> group(source) != null && group(source) == group(target));
        Map<String, Command> owned = new HashMap<>();
        for (String name : allCommands()) {
            PluginCommand command = Objects.requireNonNull(getCommand(name));
            command.setExecutor(this);
            command.setTabCompleter(this);
            owned.put(name, command);
        }
        commandPriority = new CommandPriority(owned);
        commandPriority.claim(getServer().getCommandMap().getKnownCommands());
        try {
            dataFile = new File(getDataFolder(), "servers.yml");
            data = dataFile.exists() ? YamlConfiguration.loadConfiguration(dataFile) : new YamlConfiguration();
            multiverse = new MultiverseBridge(Objects.requireNonNull(getServer().getPluginManager().getPlugin("Multiverse-Core")),
                    Objects.requireNonNull(getServer().getPluginManager().getPlugin("Multiverse-Inventories")));
            ConfigurationSection section = data.getConfigurationSection("servers");
            if (section != null) for (String id : section.getKeys(false)) {
                String base = "servers." + id + ".";
                ServerGroup group = new ServerGroup(id, data.getStringList(base + "worlds"));
                group.maintenance = data.getBoolean(base + "maintenance");
                group.whitelisted = data.getBoolean(base + "whitelisted");
                group.deleting = data.getBoolean(base + "deleting");
                for (String uuid : data.getStringList(base + "whitelist")) group.whitelist.add(UUID.fromString(uuid));
                servers.put(id, group);
            }
            if (servers.isEmpty()) {
                World primary = Bukkit.getWorlds().getFirst();
                List<String> lobby = new ArrayList<>(List.of(primary.getName()));
                for (String suffix : List.of("_nether", "_the_end")) if (Bukkit.getWorld(primary.getName() + suffix) != null) lobby.add(primary.getName() + suffix);
                servers.put("lobby", new ServerGroup("lobby", lobby));
            }
            Set<String> used = new HashSet<>();
            for (ServerGroup group : servers.values()) {
                for (String world : group.worlds) {
                    if (!used.add(world)) throw new IllegalStateException("World belongs to multiple servers: " + world);
                    if (!group.deleting && Bukkit.getWorld(world) == null) throw new IllegalStateException("Multiverse must load " + world + " before HollowUtils");
                }
                if (!group.deleting) multiverse.configure(group);
            }
            saveData();
            warps = new Warps(this);
            homes = new Warps(this,true);
            messages = new SocialMessages(this);
            extras = new ExtraCommands(this, utilities);
            moderation = new Moderation(this);
            ready = true;
            for (Player player : Bukkit.getOnlinePlayers()) remember(player);
            refreshVisibility();
            getLogger().info("Logical servers loaded: " + servers.keySet());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Isolation setup failed. Joins are locked until configuration is corrected and the server restarted.", e);
            for (Player player : Bukkit.getOnlinePlayers()) player.kick(Component.text("HollowUtils isolation setup failed. Contact an administrator."));
        }
    }
    private static Set<String> allCommands() { Set<String> names = new HashSet<>(USER_COMMANDS); names.addAll(ADMIN_COMMANDS); names.addAll(UtilityCommands.COMMANDS); names.addAll(ExtraCommands.NAMES); names.addAll(Moderation.COMMANDS); names.addAll(Warps.COMMANDS); names.addAll(Warps.HOME_COMMANDS); names.addAll(SocialMessages.COMMANDS); return names; }
    boolean isMuted(Player player) { return moderation != null && moderation.muted(player); }
    boolean hasServer(String id) { return servers.containsKey(id); }
    void removePunished(Player player, Component notice) {
        ServerGroup lobby = servers.get("lobby");
        if (lobby != null && group(player) != lobby && canEnter(player,lobby)) {
            join(player,lobby);
            if (group(player) == lobby) { player.sendMessage(notice); return; }
        }
        player.kick(notice);
    }
    String serverId(Player player) { return id(player); }
    boolean sameGroup(Player player, World world) { return group(player) != null && group(player) == group(world); }
    Path backupForCommand(String id) throws IOException {
        ServerGroup server = requireServer(id);
        if (server.id.equals("lobby")) throw new IllegalArgumentException("The primary lobby requires a stopped-process backup.");
        for (Player player : occupants(server)) join(player, requireServer("lobby"));
        return backup(server);
    }
    private ServerGroup group(World world) {
        return servers.values().stream().filter(s -> s.worlds.contains(world.getName())).findFirst().orElse(null);
    }
    private ServerGroup group(Player player) { return group(player.getWorld()); }
    private String id(Player player) { ServerGroup s = group(player); return s == null ? "unmanaged:" + player.getWorld().getUID() : s.id; }
    private boolean canEnter(Player player, ServerGroup server) { return server.accessible(player.getUniqueId(), player.hasPermission("hollowutils.bypass")) && (moderation == null || !moderation.banned(player,server.id)); }
    private void remember(Player player) { memberships.put(player.getUniqueId(), id(player)); }
    private void say(CommandSender sender, String text) { sender.sendMessage(Component.text("▎ HollowUtils  ", net.kyori.adventure.text.format.NamedTextColor.AQUA).append(Component.text(text,net.kyori.adventure.text.format.NamedTextColor.GRAY))); }
    private void saveData() throws IOException {
        for (ServerGroup group : servers.values()) {
            String base = "servers." + group.id + ".";
            data.set(base + "worlds", group.worlds);
            data.set(base + "maintenance", group.maintenance);
            data.set(base + "whitelisted", group.whitelisted);
            data.set(base + "deleting", group.deleting);
            data.set(base + "whitelist", group.whitelist.stream().map(UUID::toString).sorted().toList());
        }
        // Write then atomically replace; a crash must not truncate the registry.
        Path temp = Files.createTempFile(getDataFolder().toPath(), "servers-", ".tmp");
        try {
            Files.writeString(temp, data.saveToString());
            try { Files.move(temp, dataFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private Location spawn(ServerGroup server) {
        Location saved = data.getLocation("servers." + server.id + ".spawn");
        if (saved != null && saved.getWorld() != null && server.worlds.contains(saved.getWorld().getName())) return saved;
        return Objects.requireNonNull(Bukkit.getWorld(server.worlds.getFirst()), "Server overworld is not loaded").getSpawnLocation();
    }
    private List<Player> occupants(ServerGroup server) { return new ArrayList<>(Bukkit.getOnlinePlayers().stream().filter(p -> group(p) == server).toList()); }
    private void join(Player player, ServerGroup server) {
        if (!canEnter(player, server)) { say(player, "That server is unavailable or you are not whitelisted."); return; }
        player.closeInventory();
        if (!player.teleport(spawn(server))) say(player, "Teleport was cancelled.");
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName();
        if (!ready) { say(sender, "Isolation is locked. Check the server console."); return true; }
        if (ADMIN_COMMANDS.contains(name) && !sender.hasPermission("hollowutils.admin")) { say(sender, "You do not have permission."); return true; }
        try {
            if (SocialMessages.COMMANDS.contains(name)) return messages.execute(sender,name,args);
            if (Warps.HOME_COMMANDS.contains(name)) return homes.execute(sender,name,args);
            if (Warps.COMMANDS.contains(name)) return warps.execute(sender,name,args);
            if (Moderation.COMMANDS.contains(name)) return moderation.execute(sender,name,args);
            if (ExtraCommands.NAMES.contains(name)) return extras.execute(sender, name, args);
            if (UtilityCommands.COMMANDS.contains(name)) return utilities.execute(sender, name, args);
            if (name.equals("server")) {
                if (args.length == 0) menu(requirePlayer(sender), 0);
                else if (args.length <= 2) {
                    Player target = utilities.target(sender, args.length == 2 ? args[1] : null, "hollowutils.server.others");
                    join(target, requireServer(args[0]));
                } else return false;
            } else if (name.equals("spawn") || name.equals("setspawn")) {
                if (args.length > 1) return false;
                Player player = utilities.target(sender, args.length == 1 ? args[0] : null, "hollowutils." + name + ".others");
                ServerGroup server = Objects.requireNonNull(group(player), "You are outside a managed server.");
                if (name.equals("spawn")) join(player, server);
                else {
                    data.set("servers." + server.id + ".spawn", player.getLocation());
                    saveData(); say(sender, "Spawn set for " + server.id + ".");
                }
            } else if (name.equals("createserver")) {
                if (args.length < 1 || args.length > 2) return false;
                create(sender, args[0], args.length == 2 ? Long.parseLong(args[1]) : new Random().nextLong());
            } else {
                if (args.length == 0) return false;
                ServerGroup server = requireServer(args[0]);
                switch (name) {
                    case "backupserver" -> { if (args.length != 1) return false; say(sender, "World backup saved: " + backup(server).getFileName()); }
                    case "maintenanceserver" -> {
                        if (args.length != 2) return false;
                        if (server.id.equals("lobby")) throw new IllegalArgumentException("The fallback lobby cannot enter maintenance.");
                        server.maintenance = toggle(args[1]); saveData(); enforceAccess(server);
                        say(sender, server.id + " maintenance: " + server.maintenance);
                    }
                    case "whitelistserver" -> whitelist(sender, server, args);
                    case "deleteserver" -> {
                        if (args.length > 2) return false;
                        delete(sender, server, args.length == 2 && args[1].equals("confirm"));
                    }
                    default -> { return false; }
                }
            }
        } catch (Exception e) {
            say(sender, e.getMessage() == null ? "Operation failed; check the console." : e.getMessage());
            if (!(e instanceof IllegalArgumentException)) getLogger().log(Level.WARNING, "Command failed: " + name, e);
        }
        return true;
    }
    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        throw new IllegalArgumentException("This command requires a player.");
    }
    private ServerGroup requireServer(String id) {
        ServerGroup server = servers.get(id.toLowerCase(Locale.ROOT));
        if (server == null) throw new IllegalArgumentException("Unknown server.");
        return server;
    }
    private static boolean toggle(String text) {
        if (text.equalsIgnoreCase("on")) return true;
        if (text.equalsIgnoreCase("off")) return false;
        throw new IllegalArgumentException("Use on or off.");
    }
    private void create(CommandSender sender, String id, long seed) throws IOException {
        List<String> names = ServerGroup.worldNames(id);
        if (id.equals("lobby") || servers.containsKey(id)) throw new IllegalArgumentException("That server already exists or is reserved.");
        for (String world : names) if (Bukkit.getWorld(world) != null || Files.exists(Bukkit.getWorldContainer().toPath().resolve(world)))
            throw new IllegalArgumentException("World already exists: " + world);
        say(sender, "Creating three dimensions; world generation may briefly pause the server.");
        // Persist a locked recovery record BEFORE creating any worlds. Never delete pre-existing paths on failure.
        ServerGroup server = new ServerGroup(id, names);
        server.deleting = true;
        servers.put(id, server);
        saveData();
        try {
            multiverse.create(names.get(0), World.Environment.NORMAL, seed);
            multiverse.create(names.get(1), World.Environment.NETHER, seed);
            multiverse.create(names.get(2), World.Environment.THE_END, seed);
            multiverse.configure(server);
            server.deleting = false;
            saveData();
            say(sender, "Created " + id + ". Join with /server " + id);
        } catch (Exception e) {
            server.deleting = true; saveData();
            throw new IllegalStateException("Creation incomplete. Server remains locked; inspect console and use /deleteserver " + id + " to clean up.", e);
        }
    }
    private Path backup(ServerGroup server) throws IOException {
        if (!occupants(server).isEmpty()) throw new IllegalArgumentException("Move all players out of " + server.id + " before backing up or deleting it.");
        if (server.id.equals("lobby")) throw new IllegalArgumentException("Back up the primary lobby with the process stopped; Minecraft cannot unload its primary world.");
        Map<String, Path> roots = new LinkedHashMap<>();
        for (String name : server.worlds) {
            World world = Bukkit.getWorld(name);
            if (world != null) world.save();
            Path folder = Bukkit.getWorldContainer().toPath().resolve(name).toAbsolutePath().normalize();
            if (!folder.startsWith(Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize())) throw new IOException("Unsafe world path");
            if (Files.exists(folder)) roots.put(name, folder);
            else if (!server.deleting) throw new IOException("Missing world: " + name);
        }
        saveData();
        Path destination = getDataFolder().toPath().resolve("backups").resolve(server.id + "-" + UUID.randomUUID() + ".zip");
        List<String> unloaded = new ArrayList<>();
        try {
            for (String name : server.worlds) if (Bukkit.getWorld(name) != null) {
                multiverse.unload(name); unloaded.add(name);
            }
            WorldArchive.write(destination, roots);
            Files.copy(dataFile.toPath(), destination.resolveSibling(destination.getFileName() + ".servers.yml"));
            return destination;
        } finally {
            for (String name : unloaded) {
                try { multiverse.load(name); }
                catch (Exception e) {
                    server.deleting = true;
                    getLogger().log(Level.SEVERE, "Backup reload failed; server locked: " + name, e);
                    saveData();
                }
            }
        }
    }
    private void delete(CommandSender sender, ServerGroup server, boolean confirm) throws IOException {
        if (server.id.equals("lobby")) throw new IllegalArgumentException("The fallback lobby cannot be deleted.");
        String key = sender.getName() + ":" + server.id;
        if (!confirm || confirmations.getOrDefault(key, 0L) < System.currentTimeMillis()) {
            confirmations.put(key, System.currentTimeMillis() + 30_000);
            say(sender, "Permanently delete all three worlds? Run /deleteserver " + server.id + " confirm within 30 seconds. A world backup will be made first."); return;
        }
        if (!occupants(server).isEmpty()) throw new IllegalArgumentException("Move all players out before deleting.");
        Path archive = backup(server);
        server.deleting = true; saveData();
        for (String name : server.worlds) {
            // Partial deletes are retryable. A locked registry survives until every dimension is removed.
            if (Files.exists(Bukkit.getWorldContainer().toPath().resolve(name)) || Bukkit.getWorld(name) != null) multiverse.delete(name);
        }
        multiverse.removeGroup(server.id);
        servers.remove(server.id); data.set("servers." + server.id, null); saveData();
        confirmations.remove(key);
        say(sender, "Deleted " + server.id + ". World backup: " + archive.getFileName());
    }
    private void whitelist(CommandSender sender, ServerGroup server, String[] args) throws IOException {
        if (server.id.equals("lobby")) throw new IllegalArgumentException("The fallback lobby stays open.");
        if (args.length < 2) throw new IllegalArgumentException("Use on, off, add, remove or list.");
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "off" -> { if (args.length != 2) throw new IllegalArgumentException("Use on or off."); server.whitelisted = toggle(args[1]); }
            case "list" -> { say(sender, "Whitelist: " + server.whitelist); return; }
            case "add", "remove" -> {
                if (args.length != 3) throw new IllegalArgumentException("Specify an online player name or UUID.");
                Player online = Bukkit.getPlayerExact(args[2]);
                UUID uuid = online == null ? UUID.fromString(args[2]) : online.getUniqueId();
                if (args[1].equalsIgnoreCase("add")) server.whitelist.add(uuid); else server.whitelist.remove(uuid);
            }
            default -> throw new IllegalArgumentException("Use on, off, add, remove or list.");
        }
        saveData(); enforceAccess(server); say(sender, "Whitelist updated for " + server.id);
    }
    private void enforceAccess(ServerGroup server) {
        for (Player player : occupants(server)) if (!canEnter(player, server)) {
            join(player, requireServer("lobby"));
            if (group(player) == server) player.kick(Component.text("This server is now restricted."));
        }
    }
    private final class ServerMenu implements InventoryHolder {
        private Inventory inventory;
        private final int page;
        ServerMenu(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private void menu(Player player, int page) {
        List<ServerGroup> available = servers.values().stream().filter(s -> canEnter(player, s)).toList();
        int last = Math.max(0, (available.size() - 1) / 45);
        page = Math.clamp(page, 0, last);
        ServerMenu holder = new ServerMenu(page);
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text("Servers — page " + (page + 1)));
        int slot = 0;
        for (ServerGroup server : available.subList(page * 45, Math.min(available.size(), (page + 1) * 45))) {
            ItemStack item = new ItemStack(server.maintenance ? Material.BARRIER : Material.GRASS_BLOCK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(server.id));
            meta.lore(List.of(Component.text("Players: " + occupants(server).size()), Component.text("Click to join")));
            meta.getPersistentDataContainer().set(menuKey, PersistentDataType.STRING, server.id);
            item.setItemMeta(meta); holder.inventory.setItem(slot++, item);
        }
        if (page > 0) holder.inventory.setItem(45, new ItemStack(Material.ARROW));
        if (page < last) holder.inventory.setItem(53, new ItemStack(Material.ARROW));
        player.openInventory(holder.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ServerMenu holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;
        int slot = event.getRawSlot();
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        if (slot == 45 || slot == 53) {
            Bukkit.getScheduler().runTask(this, () -> menu(player, holder.page + (slot == 45 ? -1 : 1))); return;
        }
        String target = item.getItemMeta().getPersistentDataContainer().get(menuKey, PersistentDataType.STRING);
        if (target != null) Bukkit.getScheduler().runTask(this, () -> { ServerGroup server = servers.get(target); if (server != null) join(player, server); });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ServerMenu) event.setCancelled(true);
    }
    private boolean commandAllowed(Player player, String label) {
        String normalized = label.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("hollowutils:")) normalized = normalized.substring("hollowutils:".length());
        return (USER_COMMANDS.contains(normalized) && player.hasPermission("hollowutils.use"))
                || (ADMIN_COMMANDS.contains(normalized) && player.hasPermission("hollowutils.admin"))
                || (SocialMessages.COMMANDS.contains(normalized) && player.hasPermission(SocialMessages.permission(normalized)))
                || (Warps.HOME_COMMANDS.contains(normalized) && player.hasPermission("hollowutils."+normalized))
                || (Warps.COMMANDS.contains(normalized) && player.hasPermission("hollowutils." + normalized))
                || (Moderation.COMMANDS.contains(normalized) && player.hasPermission("hollowutils." + normalized))
                || (ExtraCommands.NAMES.contains(normalized) && player.hasPermission(ExtraCommands.permission(normalized)))
                || (UtilityCommands.COMMANDS.contains(normalized) && player.hasPermission(UtilityCommands.permission(normalized)))
                || getConfig().getStringList("allowed-commands").contains(normalized);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void command(PlayerCommandPreprocessEvent event) {
        String label = event.getMessage().substring(1).split(" ", 2)[0];
        if (!ready || !commandAllowed(event.getPlayer(), label)) { event.setCancelled(true); say(event.getPlayer(), "That command is not enabled on this server."); return; }
        event.setMessage("/" + prioritize(event.getMessage().substring(1)));
    }
    private String prioritize(String line) {
        int space = line.indexOf(' ');
        String root = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        return allCommands().contains(root) ? "hollowutils:" + root + (space < 0 ? "" : line.substring(space)) : line;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void consoleCommand(ServerCommandEvent event) {
        event.setCommand(prioritize(event.getCommand()));
    }
    @EventHandler public void serverLoaded(ServerLoadEvent event) { claimCommands(); }
    @EventHandler public void pluginEnabled(PluginEnableEvent event) {
        if (event.getPlugin() != this) Bukkit.getScheduler().runTask(this, this::claimCommands);
    }
    private void claimCommands() {
        commandPriority.claim(getServer().getCommandMap().getKnownCommands());
        // CraftServer exposes the command-tree refresh used after runtime command registration.
        try { getServer().getClass().getMethod("syncCommands").invoke(getServer()); }
        catch (ReflectiveOperationException e) { getLogger().log(Level.WARNING, "Command map updated, but command-tree refresh failed", e); }
        if (ready) refreshVisibility();
    }
    @EventHandler public void commands(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(label -> !commandAllowed(event.getPlayer(), label));
    }
    private static String completionRoot(String buffer) {
        return buffer.replaceFirst("^/", "").split(" ", 2)[0].toLowerCase(Locale.ROOT);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void asyncCompletion(AsyncTabCompleteEvent event) {
        if (event.getSender() instanceof Player player) {
            String root = completionRoot(event.getBuffer());
            if (!event.isCommand() || !allowedRoots.getOrDefault(player.getUniqueId(), Set.of()).contains(root)) {
                event.getCompletions().clear(); event.setHandled(true); event.setCancelled(true);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void completion(TabCompleteEvent event) {
        if (event.getSender() instanceof Player player && !commandAllowed(player, completionRoot(event.getBuffer()))) {
            event.setCompletions(List.of()); event.setCancelled(true);
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!ready || args.length == 0) return List.of();
        List<String> choices = new ArrayList<>();
        String name = command.getName();
        if (SocialMessages.COMMANDS.contains(name)) choices.addAll(messages.complete(sender,name,args));
        else if (Warps.HOME_COMMANDS.contains(name)) choices.addAll(homes.complete(sender,name,args));
        else if (Warps.COMMANDS.contains(name)) choices.addAll(warps.complete(sender,name,args));
        else if (Moderation.COMMANDS.contains(name)) choices.addAll(moderation.complete(sender,name,args));
        else if (ExtraCommands.NAMES.contains(name)) choices.addAll(extras.complete(sender, name, args));
        else if (UtilityCommands.COMMANDS.contains(name)) choices.addAll(utilities.complete(sender, name, args));
        else if (args.length == 1 && Set.of("spawn", "setspawn").contains(name)) choices.addAll(utilities.targetNames(sender, "hollowutils." + name + ".others"));
        else if (args.length == 2 && name.equals("server")) choices.addAll(utilities.targetNames(sender, "hollowutils.server.others"));
        else if (args.length == 1) {
            for (ServerGroup server : servers.values()) if (!(sender instanceof Player player) || canEnter(player, server)) choices.add(server.id);
        } else if (args.length == 2) switch (command.getName()) {
            case "whitelistserver" -> choices.addAll(List.of("on", "off", "add", "remove", "list"));
            case "maintenanceserver" -> choices.addAll(List.of("on", "off"));
            case "deleteserver" -> choices.add("confirm");
        }
        // No Bukkit default completion fallback: that would expose global player names.
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
    private void refreshVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Set<UUID> peers = new HashSet<>();
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (Objects.equals(memberships.get(viewer.getUniqueId()), memberships.get(target.getUniqueId()))) {
                    viewer.showPlayer(this, target); peers.add(target.getUniqueId());
                } else viewer.hidePlayer(this, target);
            }
            chatPeers.put(viewer.getUniqueId(), Set.copyOf(peers));
            Set<String> roots = new HashSet<>(getConfig().getStringList("allowed-commands"));
            for (String label : allCommands()) if (commandAllowed(viewer, label)) {
                roots.add(label); roots.add("hollowutils:" + label);
            }
            allowedRoots.put(viewer.getUniqueId(), Set.copyOf(roots));
            viewer.updateCommands();
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void chat(AsyncChatEvent event) {
        Set<UUID> allowed = chatPeers.getOrDefault(event.getPlayer().getUniqueId(), Set.of());
        event.viewers().removeIf(a -> a instanceof Player p && !allowed.contains(p.getUniqueId()));
        // Keep the console as an audit recipient. Do not re-add anyone hidden by another chat plugin.
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void login(PlayerLoginEvent event) {
        if (!ready) event.disallow(PlayerLoginEvent.Result.KICK_OTHER, "HollowUtils isolation setup failed. Contact an administrator.");
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void joined(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        ServerGroup server = group(player);
        if (server == null || !canEnter(player, server)) {
            join(player, requireServer("lobby"));
            if (group(player)==null || !canEnter(player,group(player))) {
                player.kick(Component.text("You cannot access this server. Contact staff to review your punishment.")); return;
            }
        }
        remember(player); refreshVisibility();
        local(player, player.getName() + " joined this server.");
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void quit(PlayerQuitEvent event) {
        event.quitMessage(null); local(event.getPlayer(), event.getPlayer().getName() + " left this server.");
        memberships.remove(event.getPlayer().getUniqueId()); chatPeers.remove(event.getPlayer().getUniqueId()); allowedRoots.remove(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(this, this::refreshVisibility);
    }
    private void local(Player source, String message) {
        String id = memberships.get(source.getUniqueId());
        for (Player target : Bukkit.getOnlinePlayers()) if (Objects.equals(id, memberships.get(target.getUniqueId()))) target.sendMessage(Component.text(message));
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void advancement(PlayerAdvancementDoneEvent event) {
        Component message = event.message(); event.message(null);
        if (message != null) for (Player player : Bukkit.getOnlinePlayers())
            if (group(player) == group(event.getPlayer())) player.sendMessage(message);
    }
    // Entity portal travel has no player access checks; block it to prevent item/mob leakage.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void entityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void died(PlayerDeathEvent event) {
        Component message = event.deathMessage(); event.deathMessage(null);
        if (message != null) for (Player player : Bukkit.getOnlinePlayers()) if (group(player) == group(event.getEntity())) player.sendMessage(message);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void changed(PlayerChangedWorldEvent event) {
        remember(event.getPlayer()); refreshVisibility();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void teleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        ServerGroup destination = group(event.getTo().getWorld());
        if (destination == null || !canEnter(event.getPlayer(), destination)) {
            event.setCancelled(true); say(event.getPlayer(), "Destination is outside your accessible servers.");
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void portal(PlayerPortalEvent event) {
        ServerGroup server = group(event.getPlayer());
        if (server == null || server.deleting) { event.setCancelled(true); return; }
        World.Environment environment = event.getFrom().getWorld().getEnvironment();
        boolean end = event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL;
        World.Environment targetEnvironment = end ? (environment == World.Environment.THE_END ? World.Environment.NORMAL : World.Environment.THE_END)
                : (environment == World.Environment.NETHER ? World.Environment.NORMAL : World.Environment.NETHER);
        World target = server.worlds.stream().map(Bukkit::getWorld).filter(Objects::nonNull).filter(w -> w.getEnvironment() == targetEnvironment).findFirst().orElse(null);
        if (target == null) { event.setCancelled(true); return; }
        if (end) event.setTo(targetEnvironment == World.Environment.NORMAL ? spawn(server) : target.getSpawnLocation());
        else {
            Location location = event.getFrom().clone();
            location.setWorld(target);
            double scale = targetEnvironment == World.Environment.NETHER ? 0.125 : 8.0;
            double bound = Math.max(0, Math.min(29_999_000, target.getWorldBorder().getSize() / 2 - 16));
            Location center = target.getWorldBorder().getCenter();
            location.setX(Math.clamp(location.getX() * scale, center.getX() - bound, center.getX() + bound));
            location.setZ(Math.clamp(location.getZ() * scale, center.getZ() - bound, center.getZ() + bound));
            location.setY(Math.clamp(location.getY(), target.getMinHeight() + 5, target.getMaxHeight() - 10));
            event.setTo(location); event.setCanCreatePortal(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent event) {
        ServerGroup server = group(event.getPlayer());
        if (server == null || !canEnter(event.getPlayer(), server)) server = requireServer("lobby");
        if (!canEnter(event.getPlayer(),server)) Bukkit.getScheduler().runTask(this, () ->
                event.getPlayer().kick(Component.text("You cannot access the fallback server. Contact staff.")));
        if (!server.worlds.contains(event.getRespawnLocation().getWorld().getName())) event.setRespawnLocation(spawn(server));
    }
    @Override public void onDisable() {
        if (commandPriority != null) commandPriority.restore(getServer().getCommandMap().getKnownCommands());
        if (ready) {
            try { saveData(); } catch (IOException e) { getLogger().log(Level.SEVERE, "Could not save registry", e); }
            // Hot unloading removes isolation; disconnect players instead of exposing other groups.
            for (Player player : Bukkit.getOnlinePlayers()) player.kick(Component.text("HollowUtils stopped; reconnect after restart."));
        }
    }
}
