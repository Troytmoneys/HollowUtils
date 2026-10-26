package io.github.troytmoneys.hollowutils;

import com.google.common.net.InetAddresses;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class Moderation implements Listener {
    static final Set<String> COMMANDS = Set.of("ban", "banip", "unban", "unbanip", "staffrollback", "kick", "mute", "unmute", "warn", "unwarn", "warns", "punishments", "history");
    private final HollowUtils plugin;
    private final File file;
    private final YamlConfiguration data;
    private volatile List<Punishment> records = List.of();
    private final Map<UUID, String> scopes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> notices = new ConcurrentHashMap<>();
    private final Map<String, Long> confirmations = new HashMap<>();
    private final String salt;

    Moderation(HollowUtils plugin) throws IOException {
        this.plugin = plugin; file = new File(plugin.getDataFolder(), "moderation.yml");
        data = new YamlConfiguration();
        if (file.exists()) {
            try { data.load(file); } catch (Exception e) { throw new IOException("Cannot read moderation ledger; refusing to discard punishments", e); }
        }
        salt = data.getString("ip-salt", UUID.randomUUID().toString()); data.set("ip-salt", salt);
        List<Punishment> loaded = new ArrayList<>();
        for (Map<?, ?> row : data.getMapList("records")) loaded.add(new Punishment(str(row,"id"),str(row,"server"),str(row,"type"),str(row,"subject"),str(row,"target"),str(row,"actor"),str(row,"actor-name"),str(row,"reason"),num(row,"created"),num(row,"expires"),num(row,"revoked"),str(row,"revoked-by")));
        records = List.copyOf(loaded); persist(records);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) remember(p);
    }
    private static String str(Map<?,?> map, String key) { return Objects.toString(map.get(key), ""); }
    private static long num(Map<?,?> map, String key) { return ((Number) map.get(key)).longValue(); }
    private void persist(List<Punishment> next) throws IOException {
        List<Map<String,Object>> rows = new ArrayList<>();
        for (Punishment p : next) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",p.id()); row.put("server",p.server()); row.put("type",p.type()); row.put("subject",p.subject()); row.put("target",p.targetName()); row.put("actor",p.actor()); row.put("actor-name",p.actorName()); row.put("reason",p.reason()); row.put("created",p.created()); row.put("expires",p.expires()); row.put("revoked",p.revoked()); row.put("revoked-by",p.revokedBy()); rows.add(row);
        }
        data.set("records",rows);
        Path temp = Files.createTempFile(plugin.getDataFolder().toPath(), "moderation-", ".tmp");
        try {
            Files.writeString(temp, data.saveToString());
            try { Files.move(temp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING); }
            records = List.copyOf(next);
        } finally { Files.deleteIfExists(temp); }
    }
    private String actor(CommandSender sender) { return sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE"; }
    private void message(CommandSender sender, String title, String body) {
        Responses.info(sender,title,body);
    }
    private String ip(InetAddress address) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((salt + ":" + InetAddresses.toAddrString(address)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    boolean muted(Player player) {
        String scope=plugin.serverId(player);
        return records.stream().anyMatch(p -> p.applies(scope,"MUTE",player.getUniqueId().toString(),System.currentTimeMillis()));
    }
    boolean banned(Player player, String scope) {
        String uuid = player.getUniqueId().toString();
        String address = player.getAddress() == null ? "" : ip(player.getAddress().getAddress());
        long now = System.currentTimeMillis();
        return records.stream().anyMatch(p -> p.applies(scope,"BAN",uuid,now) || p.applies(scope,"IP_BAN",address,now));
    }
    private void remember(Player p) {
        String scope = plugin.serverId(p); scopes.put(p.getUniqueId(),scope);
        data.set("players." + scope + "." + p.getUniqueId(),p.getName());
        if (p.getAddress()!=null) data.set("addresses."+scope+"."+p.getUniqueId(),ip(p.getAddress().getAddress()));
        try { persist(records); } catch (IOException e) { plugin.getLogger().severe("Could not persist moderation player index"); }
    }
    private String resolve(CommandSender sender, String scope, String value) {
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().equalsIgnoreCase(value) && plugin.serverId(p).equals(scope)
                && (!(sender instanceof Player source) || source.canSee(p))) return p.getUniqueId().toString();
        var section = data.getConfigurationSection("players." + scope);
        if (section != null) for (String uuid : section.getKeys(false)) if (value.equalsIgnoreCase(uuid) || value.equalsIgnoreCase(section.getString(uuid))) return uuid;
        // UUIDs are unambiguous for offline moderation; names must have been seen in this logical server.
        try { return UUID.fromString(value).toString(); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("No known player by that name in this server. Use an exact UUID for offline players."); }
    }
    private String label(String scope, String uuid) { return data.getString("players." + scope + "." + uuid,uuid); }
    private Player online(String uuid, String scope) {
        try { Player p = Bukkit.getPlayer(UUID.fromString(uuid)); return p != null && plugin.serverId(p).equals(scope) ? p : null; }
        catch (IllegalArgumentException e) { return null; }
    }
    private String ipSubject(CommandSender sender,String scope,String input) {
        if (InetAddresses.isInetAddress(input)) return ip(InetAddresses.forString(input));
        String uuid = resolve(sender,scope,input);
        Player p = online(uuid,scope);
        if (p != null && p.getAddress()!=null) return ip(p.getAddress().getAddress());
        String saved = data.getString("addresses."+scope+"."+uuid);
        if (saved==null) throw new IllegalArgumentException("No recorded IP for this player; use a literal IP address.");
        return saved;
    }
    boolean execute(CommandSender sender,String name,String[] input) throws IOException {
        UtilityCommands.requirePermission(sender,"hollowutils." + name);
        List<String> parts = new ArrayList<>(List.of(input));
        String scope;
        if (sender instanceof Player p) scope = plugin.serverId(p);
        else {
            if (parts.size() < 2 || !parts.getFirst().equalsIgnoreCase("--server")) throw new IllegalArgumentException("Console syntax: /" + name + " --server <server> <arguments>");
            scope = parts.get(1); parts.subList(0,2).clear();
            if (!plugin.hasServer(scope)) throw new IllegalArgumentException("Unknown logical server.");
        }
        String[] args = parts.toArray(String[]::new);
        if (args.length == 0) return false;
        if (Set.of("history","punishments","warns").contains(name)) {
            if (args.length > 2) return false;
            String uuid = InetAddresses.isInetAddress(args[0]) ? ipSubject(sender,scope,args[0]) : resolve(sender,scope,args[0]);
            int page = args.length == 2 ? Integer.parseInt(args[1]) : 1;
            String address = data.getString("addresses."+scope+"."+uuid, "");
            List<Punishment> found = records.stream().filter(p -> p.server().equals(scope) && (p.subject().equals(uuid) || (p.type().equals("IP_BAN") && p.subject().equals(address))))
                    .filter(p -> !name.equals("warns") || p.type().equals("WARN"))
                    .filter(p -> !name.equals("punishments") || p.active(System.currentTimeMillis())).sorted(Comparator.comparingLong(Punishment::created).reversed()).toList();
            int pages = Math.max(1,(found.size()+5)/6);
            if (page < 1 || page > pages) throw new IllegalArgumentException("Page must be between 1 and " + pages);
            message(sender,name.toUpperCase(Locale.ROOT),label(scope,uuid) + " · " + scope + " · " + page + "/" + pages);
            for (Punishment p : found.subList((page-1)*6, Math.min(found.size(),page*6))) {
                String state = p.revoked()!=0 ? "revoked by " + p.revokedBy() : p.active(System.currentTimeMillis()) ? "active" : p.type().equals("KICK") ? "executed" : "expired";
                Component line = Component.text("  #" + p.id().substring(0,8) + " ",NamedTextColor.DARK_GRAY)
                        .clickEvent(ClickEvent.copyToClipboard(p.id()))
                        .append(Component.text(p.type()+" · "+p.reason(),NamedTextColor.WHITE))
                        .hoverEvent(Component.text("Staff: "+p.actorName()+"\nCreated: "+Instant.ofEpochMilli(p.created())+"\nExpires: "+(p.expires()==0?"Never":Instant.ofEpochMilli(p.expires()))+"\nStatus: "+state+"\nClick to copy full ID"));
                sender.sendMessage(line);
            }
            if (found.isEmpty()) message(sender,"CLEAR","No records found.");
            if (page < pages && sender instanceof Player) sender.sendMessage(Component.text("  Next page →",NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/"+name+" "+args[0]+" "+(page+1))));
            return true;
        }
        if (name.equals("staffrollback")) {
            if (args.length > 2) return false;
            String staff = args[0];
            Set<String> actors = new HashSet<>();
            records.stream().filter(p -> p.server().equals(scope) && (p.actor().equalsIgnoreCase(staff)||p.actorName().equalsIgnoreCase(staff))).forEach(p -> actors.add(p.actor()));
            if (actors.size()!=1) throw new IllegalArgumentException("Specify an unambiguous staff UUID/name (or CONSOLE).");
            String identity = actors.iterator().next();
            String key = actor(sender)+":"+scope+":"+identity;
            long count = records.stream().filter(p -> p.server().equals(scope)&&p.actor().equals(identity)&&p.active(System.currentTimeMillis())).count();
            if (args.length!=2 || !args[1].equalsIgnoreCase("confirm") || confirmations.getOrDefault(key,0L)<System.currentTimeMillis()) {
                confirmations.put(key,System.currentTimeMillis()+30_000);
                message(sender,"CONFIRM", "Revoke " + count + " active punishments by " + staff + " in " + scope + ". Repeat with confirm within 30 seconds."); return true;
            }
            int changed = revoke(p -> p.server().equals(scope)&&p.actor().equals(identity),actor(sender)+" (staffrollback)"); confirmations.remove(key);
            message(sender,"ROLLBACK",changed+" punishments revoked. Kicks and past actions cannot be undone."); return true;
        }
        if (Set.of("unban","unbanip","unmute","unwarn").contains(name)) {
            if ((!name.equals("unwarn") && args.length!=1) || (name.equals("unwarn") && args.length!=2)) return false;
            String type = switch(name) { case "unban" -> "BAN"; case "unbanip" -> "IP_BAN"; case "unmute" -> "MUTE"; default -> "WARN"; };
            String subject = name.equals("unbanip") ? ipSubject(sender,scope,args[0]) : resolve(sender,scope,args[0]);
            if (name.equals("unwarn") && !args[1].equalsIgnoreCase("all")) {
                long matches = records.stream().filter(p -> p.applies(scope,type,subject,System.currentTimeMillis()) && p.id().startsWith(args[1])).count();
                if (matches!=1) throw new IllegalArgumentException("Warning ID must uniquely match one active warning, or use all.");
            }
            int changed = revoke(p -> p.server().equals(scope)&&p.type().equals(type)&&p.subject().equals(subject)
                    && (!name.equals("unwarn") || args[1].equalsIgnoreCase("all") || p.id().startsWith(args[1])),actor(sender));
            message(sender,"REVOKED",changed+" active record(s) revoked; history preserved."); return true;
        }
        boolean durationBased = name.equals("ban")||name.equals("mute");
        if (durationBased && args.length<3) return false;
        String type = switch(name) { case "ban" -> "BAN"; case "banip" -> "IP_BAN"; case "mute" -> "MUTE"; case "warn" -> "WARN"; case "kick" -> "KICK"; default -> throw new IllegalArgumentException("Unknown moderation action"); };
        String subject = type.equals("IP_BAN") ? ipSubject(sender,scope,args[0]) : resolve(sender,scope,args[0]);
        String targetName = type.equals("IP_BAN") ? "IP target" : label(scope,subject);
        String reason = args.length>(durationBased?2:1) ? String.join(" ",Arrays.copyOfRange(args,durationBased?2:1,args.length)) : "No reason provided";
        if (reason.isBlank()) throw new IllegalArgumentException("A reason is required.");
        if (reason.length()>300) throw new IllegalArgumentException("Reason must be 300 characters or fewer.");
        if (!type.equals("WARN")&&!type.equals("KICK")&&records.stream().anyMatch(p -> p.applies(scope,type,subject,System.currentTimeMillis()))) throw new IllegalArgumentException("An active punishment already exists; revoke it first.");
        Player target = online(subject,scope);
        if (type.equals("KICK")&&target==null) throw new IllegalArgumentException("Kick requires an online player in this server.");
        long now = System.currentTimeMillis(), expires = durationBased ? Punishment.expiry(args[1],now) : 0;
        Punishment record = new Punishment(UUID.randomUUID().toString(),scope,type,subject,targetName,actor(sender),sender.getName(),reason,now,expires,0,"");
        List<Punishment> next = new ArrayList<>(records); next.add(record); persist(next);
        message(sender,type,targetName+" · "+scope+" · "+reason+" · #"+record.id().substring(0,8));
        Component notice = Component.text("▎ "+type+" · "+scope+"\n",NamedTextColor.RED).append(Component.text(reason,NamedTextColor.WHITE))
                .append(Component.text("\n"+(expires==0?"Duration: infinite": "Until "+Instant.ofEpochMilli(expires))+"\nReference: "+record.id(),NamedTextColor.GRAY));
        if (type.equals("KICK")) { if (target!=null) target.kick(notice); }
        else if (type.equals("BAN")) { if (target!=null) plugin.removePunished(target,notice); }
        else if (type.equals("IP_BAN")) { for (Player p : List.copyOf(Bukkit.getOnlinePlayers())) if (plugin.serverId(p).equals(scope)&&banned(p,scope)) plugin.removePunished(p,notice); }
        else if (target!=null) target.sendMessage(notice);
        return true;
    }
    private int revoke(Predicate<Punishment> filter,String by) throws IOException {
        long now = System.currentTimeMillis(); int count=0; List<Punishment> next=new ArrayList<>();
        for (Punishment p:records) if (p.active(now)&&filter.test(p)) { next.add(p.revoke(now,by)); count++; } else next.add(p);
        persist(next); return count;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void chat(AsyncChatEvent event) {
        String scope = scopes.get(event.getPlayer().getUniqueId());
        if (scope==null) { event.setCancelled(true); return; }
        long now=System.currentTimeMillis();
        if (records.stream().anyMatch(p -> p.applies(scope,"MUTE",event.getPlayer().getUniqueId().toString(),now))) {
            event.setCancelled(true);
            if (now-notices.getOrDefault(event.getPlayer().getUniqueId(),0L)>2000) {
                notices.put(event.getPlayer().getUniqueId(),now);
                message(event.getPlayer(),"MUTED","You cannot chat in this server. Ask staff to review your punishment.");
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent event) { remember(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR) public void changed(PlayerChangedWorldEvent event) { remember(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { scopes.remove(event.getPlayer().getUniqueId()); notices.remove(event.getPlayer().getUniqueId()); }
    List<String> complete(CommandSender sender,String name,String[] args) {
        if (!sender.hasPermission("hollowutils."+name) || !(sender instanceof Player source)) return List.of();
        if (args.length==1 && !name.equals("staffrollback")) return Bukkit.getOnlinePlayers().stream().filter(p -> plugin.serverId(p).equals(plugin.serverId(source))&&source.canSee(p)).map(Player::getName).toList();
        if (args.length==2&&Set.of("ban","mute").contains(name)) return List.of("30m","1h","1d","7d","infinite");
        if (args.length==2&&name.equals("staffrollback")) return List.of("confirm");
        if (args.length==2&&name.equals("unwarn")) return List.of("all");
        return List.of();
    }
}
