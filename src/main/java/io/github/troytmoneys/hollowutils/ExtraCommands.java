package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class ExtraCommands implements Listener {
    static final Set<String> NAMES = Set.of("back", "backup", "bottom", "bc", "broadcast", "ci", "clearinventory", "clear", "createkit", "kit", "deletekit");
    private final HollowUtils plugin;
    private final UtilityCommands targets;
    private final File file;
    private final YamlConfiguration kits;
    private final File deathsFile;
    private final YamlConfiguration deaths;
    ExtraCommands(HollowUtils plugin, UtilityCommands targets) {
        this.plugin = plugin; this.targets = targets;
        file = new File(plugin.getDataFolder(), "kits.yml"); kits = YamlConfiguration.loadConfiguration(file);
        deathsFile = new File(plugin.getDataFolder(), "deaths.yml"); deaths = YamlConfiguration.loadConfiguration(deathsFile);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    static String canonical(String name) {
        return switch (name) { case "bc" -> "broadcast"; case "ci", "clearinventory" -> "clear"; default -> name; };
    }
    static String permission(String name) { return "hollowutils." + canonical(name); }
    private void say(CommandSender sender, String message) { Responses.info(sender,"HollowUtils",message); }
    private Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        throw new IllegalArgumentException("A player is required.");
    }
    private String kitId(String id) {
        if (!ServerGroup.validId(id)) throw new IllegalArgumentException("Kit names must be 1–24 lowercase letters, digits, _ or -, starting with a letter.");
        return id;
    }
    boolean execute(CommandSender sender, String raw, String[] args) throws IOException {
        String name = canonical(raw);
        UtilityCommands.requirePermission(sender, permission(name));
        switch (name) {
            case "broadcast" -> {
                if (args.length == 0) return false;
                // Intentional exception to group-local messaging: all logical servers on this process.
                Component message = Responses.line("Broadcast",String.join(" ", args),net.kyori.adventure.text.format.NamedTextColor.GOLD);
                for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(message);
                Bukkit.getConsoleSender().sendMessage(message);
            }
            case "back" -> {
                if (args.length != 0) return false;
                Player p = player(sender);
                Location location = deaths.getLocation(p.getUniqueId() + "." + plugin.serverId(p));
                if (location == null || location.getWorld() == null || !plugin.sameGroup(p, location.getWorld())) throw new IllegalArgumentException("No death location in this server.");
                if (!p.teleport(location)) throw new IllegalArgumentException("Teleport was cancelled.");
                say(p, "Returned to your death location. Watch for hazards!");
            }
            case "bottom" -> {
                if (args.length > 1) return false;
                Player p = targets.target(sender, args.length == 1 ? args[0] : null, permission(name) + ".others");
                Location from = p.getLocation(), destination = null;
                World world = p.getWorld();
                for (int y = world.getMinHeight(); y <= Math.min(from.getBlockY() - 1, world.getMaxHeight() - 3); y++) {
                    var floor = world.getBlockAt(from.getBlockX(), y, from.getBlockZ());
                    var feet = floor.getRelative(0, 1, 0); var head = floor.getRelative(0, 2, 0);
                    if (floor.getType().isOccluding() && floor.getType() != Material.MAGMA_BLOCK
                            && feet.getType().isAir() && head.getType().isAir()) {
                        destination = new Location(world, from.getBlockX() + .5, y + 1, from.getBlockZ() + .5, from.getYaw(), from.getPitch()); break;
                    }
                }
                if (destination == null) throw new IllegalArgumentException("No safe lower standing space at these X/Z coordinates.");
                if (!p.teleport(destination)) throw new IllegalArgumentException("Teleport was cancelled.");
                p.setFallDistance(0); say(sender, "Moved " + p.getName() + " to the lowest safe block below them.");
            }
            case "clear" -> {
                if (args.length > 1) return false;
                Player p = targets.target(sender, args.length == 1 ? args[0] : null, permission(name) + ".others");
                p.closeInventory(); p.getInventory().clear(); p.setItemOnCursor(null);
                say(sender, "Cleared " + p.getName() + " (inventory, armor, offhand and cursor; ender chest preserved).");
            }
            case "backup" -> {
                if (args.length > 2) return false;
                String id = args.length == 0 ? plugin.serverId(player(sender)) : args[0];
                String mode = args.length < 2 ? "both" : args[1].toLowerCase(Locale.ROOT);
                if (!Set.of("local", "drive", "both").contains(mode)) throw new IllegalArgumentException("Use local, drive or both.");
                Path archive = plugin.backupForCommand(id);
                say(sender, "Local world backup: " + archive.getFileName());
                if (!mode.equals("local")) upload(sender, archive);
            }
            case "createkit" -> {
                if (args.length < 1 || args.length > 2) return false;
                Player p = player(sender); String id = kitId(args[0]);
                if (kits.contains(id)) throw new IllegalArgumentException("Kit exists; delete it first to replace it.");
                if (args.length == 1 || args[1].equalsIgnoreCase("INVENTORY")) {
                    saveKit(id, p.getInventory().getContents()); say(p, "Kit saved from inventory, armor and offhand.");
                } else if (args[1].equalsIgnoreCase("GUI")) openEditor(p, new Editor(id));
                else throw new IllegalArgumentException("Choose INVENTORY or GUI.");
            }
            case "deletekit" -> {
                if (args.length != 1) return false;
                String id = kitId(args[0]);
                if (!kits.contains(id)) throw new IllegalArgumentException("Unknown kit.");
                String old = kits.saveToString(); kits.set(id, null);
                try { persist(kits, file); } catch (IOException e) { try { kits.loadFromString(old); } catch (Exception ignored) {} throw e; }
                say(sender, "Deleted kit " + id);
            }
            case "kit" -> {
                if (args.length == 0) { say(sender, "Kits: " + String.join(", ", kits.getKeys(false))); return true; }
                if (args.length > 2) return false;
                String id = kitId(args[0]);
                Player p = targets.target(sender, args.length == 2 ? args[1] : null, permission(name) + ".others");
                if (!kits.contains(id)) throw new IllegalArgumentException("Unknown kit.");
                // Exact kit application never silently overwrites existing items.
                if (!p.getInventory().isEmpty() || !p.getItemOnCursor().getType().isAir()) throw new IllegalArgumentException("Target must empty inventory, armor, offhand and cursor first.");
                List<?> contents = kits.getList(id + ".contents", List.of());
                ItemStack[] items = new ItemStack[p.getInventory().getContents().length];
                for (int i = 0; i < Math.min(items.length, contents.size()); i++) if (contents.get(i) instanceof ItemStack item) items[i] = item.clone();
                p.getInventory().setContents(items); say(sender, "Applied kit " + id + " to " + p.getName());
            }
        }
        return true;
    }
    private void upload(CommandSender sender, Path archive) {
        String parent = plugin.getConfig().getString("gdrive.parent-id", "");
        String executable = plugin.getConfig().getString("gdrive.executable", "gdrive");
        if (!parent.matches("[A-Za-z0-9_-]+")) { say(sender, "Drive upload unavailable: set gdrive.parent-id and authenticate gdrive v3. Local backup retained."); return; }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            Path log = null;
            try {
                log = Files.createTempFile("hollow-gdrive-", ".log");
                Process process = new ProcessBuilder(executable, "files", "upload", "--parent", parent, archive.toAbsolutePath().toString())
                        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                process.getOutputStream().close(); // Never wait for interactive authentication from a server command.
                if (!process.waitFor(10, TimeUnit.MINUTES)) { process.destroyForcibly(); result = "Drive upload timed out. Local backup retained."; }
                else result = process.exitValue() == 0 ? "Drive upload completed: " + archive.getFileName() : "Drive upload failed (exit " + process.exitValue() + "). Check CLI authentication and folder permissions. Local backup retained.";
            } catch (Exception e) { result = "Could not run authenticated gdrive v3 CLI. Local backup retained."; }
            finally { if (log != null) try { Files.deleteIfExists(log); } catch (IOException ignored) {} }
            String message = result;
            if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> { say(sender, message); });
        });
    }
    @EventHandler(priority = EventPriority.MONITOR) public void death(PlayerDeathEvent event) {
        Player p = event.getEntity();
        deaths.set(p.getUniqueId() + "." + plugin.serverId(p), p.getLocation());
        try { persist(deaths, deathsFile); } catch (IOException e) { plugin.getLogger().warning("Could not persist death location."); }
    }
    private static void persist(YamlConfiguration yaml, File file) throws IOException {
        Path tmp = Files.createTempFile(file.getParentFile().toPath(), "hollow-", ".tmp");
        try { Files.writeString(tmp, yaml.saveToString()); Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(tmp); }
    }
    private void saveKit(String id, ItemStack[] items) throws IOException {
        if (kits.contains(id)) throw new IllegalArgumentException("Kit already exists.");
        kits.set(id + ".contents", Arrays.stream(items).map(i -> i == null ? null : i.clone()).toList());
        try { persist(kits, file); } catch (IOException e) { kits.set(id, null); throw e; }
    }
    private static final class Editor implements InventoryHolder {
        final String id;
        final ItemStack[] items = new ItemStack[41];
        ItemStack brush;
        Inventory inventory;
        Editor(String id) { this.id = id; }
        public Inventory getInventory() { return inventory; }
    }
    private static final class Enchants implements InventoryHolder {
        final Editor editor; final int slot; final int page;
        final List<Enchantment> entries;
        Inventory inventory;
        Enchants(Editor editor, int slot, int page) {
            this.editor = editor; this.slot = slot; this.page = page;
            entries = new ArrayList<>(); Registry.ENCHANTMENT.forEach(entries::add);
            entries.sort(Comparator.comparing(e -> e.getKey().toString()));
        }
        public Inventory getInventory() { return inventory; }
    }
    private ItemStack button(Material material, String text) {
        ItemStack item = new ItemStack(material); var meta = item.getItemMeta(); meta.displayName(Component.text(text)); item.setItemMeta(meta); return item;
    }
    private void openEditor(Player p, Editor editor) {
        editor.inventory = Bukkit.createInventory(editor, 54, Component.text("Kit: " + editor.id));
        for (int i = 0; i < 41; i++) editor.inventory.setItem(i, editor.items[i] == null ? null : editor.items[i].clone());
        editor.inventory.setItem(45, button(Material.LIME_CONCRETE, "Save kit"));
        editor.inventory.setItem(46, button(Material.RED_CONCRETE, "Discard"));
        editor.inventory.setItem(49, button(Material.BOOK, "Pick from your inventory; left: place, right: clear, shift-right: enchant"));
        p.openInventory(editor.inventory);
    }
    private void enchantEditor(Player p, Editor editor, int slot, int page) {
        Enchants view = new Enchants(editor, slot, page);
        view.inventory = Bukkit.createInventory(view, 54, Component.text("Enchantments: left + / right -"));
        for (int i = page * 45; i < Math.min(view.entries.size(), (page + 1) * 45); i++) {
            Enchantment enchant = view.entries.get(i);
            int level = editor.items[slot].getEnchantmentLevel(enchant);
            view.inventory.setItem(i % 45, button(Material.ENCHANTED_BOOK, enchant.getKey().getKey() + " " + level + "/" + enchant.getMaxLevel()));
        }
        if (page > 0) view.inventory.setItem(45, button(Material.ARROW, "Previous"));
        view.inventory.setItem(49, button(Material.BARRIER, "Back to kit"));
        if ((page + 1) * 45 < view.entries.size()) view.inventory.setItem(53, button(Material.ARROW, "Next"));
        p.openInventory(view.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Editor) && !(holder instanceof Enchants)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!p.hasPermission(permission("createkit"))) { p.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (holder instanceof Editor editor) {
            if (slot >= 54) { ItemStack item = event.getCurrentItem(); editor.brush = item == null ? null : item.clone(); return; }
            if (slot < 0) return;
            if (slot < 41) {
                if (event.isShiftClick() && event.isRightClick() && editor.items[slot] != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> enchantEditor(p, editor, slot, 0)); return;
                }
                editor.items[slot] = event.isRightClick() ? null : editor.brush == null ? null : editor.brush.clone();
                editor.inventory.setItem(slot, editor.items[slot] == null ? null : editor.items[slot].clone());
            } else if (slot == 45) {
                try { saveKit(editor.id, editor.items); say(p, "Kit saved: " + editor.id); p.closeInventory(); }
                catch (Exception e) { Responses.error(p, "Kit was not saved: " + e.getMessage()); }
            } else if (slot == 46) p.closeInventory();
        } else if (holder instanceof Enchants view) {
            if (slot < 0 || slot >= 54) return;
            int index = view.page * 45 + slot;
            if (slot < 45 && index < view.entries.size()) {
                Enchantment enchant = view.entries.get(index); ItemStack item = view.editor.items[view.slot];
                int level = Math.clamp(item.getEnchantmentLevel(enchant) + (event.isRightClick() ? -1 : 1), 0, enchant.getMaxLevel());
                if (level == 0) item.removeEnchantment(enchant);
                else if (enchant.canEnchantItem(item) && item.getEnchantments().keySet().stream().noneMatch(other -> !other.equals(enchant) && other.conflictsWith(enchant))) item.addEnchantment(enchant, level);
                else say(p, "That enchantment is incompatible with this item.");
                Bukkit.getScheduler().runTask(plugin, () -> enchantEditor(p, view.editor, view.slot, view.page));
            } else if (slot == 49) Bukkit.getScheduler().runTask(plugin, () -> openEditor(p, view.editor));
            else if (slot == 45 && view.page > 0) Bukkit.getScheduler().runTask(plugin, () -> enchantEditor(p, view.editor, view.slot, view.page - 1));
            else if (slot == 53 && (view.page + 1) * 45 < view.entries.size()) Bukkit.getScheduler().runTask(plugin, () -> enchantEditor(p, view.editor, view.slot, view.page + 1));
        }
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof Editor || holder instanceof Enchants) event.setCancelled(true);
    }
    List<String> complete(CommandSender sender, String raw, String[] args) {
        String name = canonical(raw);
        if (!sender.hasPermission(permission(name))) return List.of();
        if (args.length == 1 && Set.of("kit", "deletekit").contains(name)) return new ArrayList<>(kits.getKeys(false));
        if (args.length == 2 && name.equals("createkit")) return List.of("INVENTORY", "GUI");
        if (args.length == 2 && name.equals("backup")) return List.of("local", "drive", "both");
        if ((args.length == 1 && Set.of("clear", "bottom").contains(name)) || (args.length == 2 && name.equals("kit"))) return targets.targetNames(sender, permission(name) + ".others");
        return List.of();
    }
}
