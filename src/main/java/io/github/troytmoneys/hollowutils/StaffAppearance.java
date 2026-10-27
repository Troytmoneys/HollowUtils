package io.github.troytmoneys.hollowutils;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class StaffAppearance implements Listener {
    static final Set<String> COMMANDS=Set.of("vanish","disguise");
    private record Original(PlayerProfile profile,Component display,Component tab) { }
    private record Mask(String name,PlayerProfile skin,String skinLabel,String rank,boolean changedName,boolean changedSkin) { }
    private enum View { MAIN, NAME, SKIN, RANK }
    private static final class Session {
        final UUID player;final String scope;String name,skinLabel,rank;PlayerProfile skin;
        boolean changedName,changedSkin,loading;int skinTicket;
        Session(Player player,String scope,Mask previous,PlayerProfile original) {
            this.player=player.getUniqueId();this.scope=scope;
            name=previous==null?Objects.requireNonNull(original.getName()):previous.name();
            skin=previous==null?original.clone():previous.skin().clone();skinLabel=previous==null?name:previous.skinLabel();rank=previous==null?null:previous.rank();
            changedName=previous!=null&&previous.changedName();changedSkin=previous!=null&&previous.changedSkin();
        }
    }
    private record Input(Session session,View kind) { }
    private static final class Menu implements InventoryHolder {
        final Session session;final View view;final int page;final List<String> choices;Inventory inventory;
        Menu(Session session,View view,int page,List<String> choices) { this.session=session;this.view=view;this.page=page;this.choices=List.copyOf(choices); }
        public Inventory getInventory() { return inventory; }
    }
    private final HollowUtils plugin;private final CosmeticRanks ranks;
    private final Set<UUID> vanished=new HashSet<>();
    private final Map<UUID,Original> originals=new HashMap<>();
    private final Map<UUID,Mask> masks=new HashMap<>();
    private final Map<UUID,String> realNames=new HashMap<>();
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,Input> inputs=new ConcurrentHashMap<>();
    private final Map<UUID,ChatFormat.Identity> chat=new ConcurrentHashMap<>();
    StaffAppearance(HollowUtils plugin) {
        this.plugin=plugin;ranks=plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")?new LuckPermsCosmetics():CosmeticRanks.none();
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        for(Player p:Bukkit.getOnlinePlayers())remember(p);
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            boolean changed=false;
            for(Player p:Bukkit.getOnlinePlayers()) {
                if(vanished.contains(p.getUniqueId())&&!p.hasPermission("hollowutils.vanish")) { vanished.remove(p.getUniqueId());changed=true; }
                Mask mask=masks.get(p.getUniqueId());
                if(mask!=null&&(!p.hasPermission("hollowutils.disguise")||(mask.changedName()&&!p.hasPermission("hollowutils.disguise.name"))
                        ||(mask.changedSkin()&&!p.hasPermission("hollowutils.disguise.skin"))||(mask.rank()!=null&&!p.hasPermission("hollowutils.disguise.rank")))) {
                    try { reset(p); } catch(Exception e) { plugin.getLogger().warning("Could not restore appearance for "+p.getUniqueId()+": "+e.getMessage()); }
                }
                refresh(p);
            }
            if(changed)plugin.refreshPlayerVisibility();
        },20L,20L);
    }
    String realName(Player p) { return realNames.getOrDefault(p.getUniqueId(),p.getName()); }
    boolean visible(Player viewer,Player target) { return !vanished.contains(target.getUniqueId())||viewer.getUniqueId().equals(target.getUniqueId())||viewer.hasPermission("hollowutils.vanish.see"); }
    boolean hidden(Player p) { return vanished.contains(p.getUniqueId()); }
    ChatFormat.Identity identity(Player p) { return chat.getOrDefault(p.getUniqueId(),ChatFormat.identity(null,p.getName())); }
    private void remember(Player p) { realNames.putIfAbsent(p.getUniqueId(),p.getName());refresh(p); }
    private void refresh(Player p) {
        try {
            Mask mask=masks.get(p.getUniqueId());String prefix=mask!=null&&mask.rank()!=null?ranks.groupPrefix(p,mask.rank()):ranks.actualPrefix(p);
            if(prefix==null)prefix=ranks.actualPrefix(p);
            ChatFormat.Identity identity=ChatFormat.identity(prefix,mask==null?realName(p):mask.name());ChatFormat.Identity previous=chat.put(p.getUniqueId(),identity);
            if(mask!=null&&!identity.equals(previous))p.playerListName(Component.empty().append(identity.prefix()).append(Component.space()).append(identity.name()));
        } catch(Exception e) { chat.put(p.getUniqueId(),ChatFormat.identity(null,realName(p))); }
    }
    boolean execute(CommandSender sender,String command,String[] args) {
        UtilityCommands.requirePermission(sender,"hollowutils."+command);
        if(!(sender instanceof Player p))throw new IllegalArgumentException("This command requires an in-game player.");
        if(command.equals("vanish")) {
            if(args.length>1)return false;
            boolean on;
            if(args.length==0)on=!hidden(p);
            else if(args[0].equalsIgnoreCase("on"))on=true;
            else if(args[0].equalsIgnoreCase("off"))on=false;
            else throw new IllegalArgumentException("Use /vanish [on|off].");
            if(on)vanished.add(p.getUniqueId());else vanished.remove(p.getUniqueId());
            plugin.refreshPlayerVisibility();Responses.success(p,"Vanish",on?"Enabled · hidden from players without staff visibility":"Disabled");return true;
        }
        if(args.length>1)return false;
        if(args.length==1) {
            if(!args[0].equalsIgnoreCase("off"))return false;
            reset(p);Responses.success(p,"Disguise","Removed");return true;
        }
        if(!plugin.hasServer(plugin.serverId(p)))throw new IllegalArgumentException("You must be in a managed server.");
        Original original=originals.get(p.getUniqueId());PlayerProfile profile=original==null?p.getPlayerProfile().clone():original.profile().clone();
        Session session=new Session(p,plugin.serverId(p),masks.get(p.getUniqueId()),profile);
        sessions.put(p.getUniqueId(),session);inputs.remove(p.getUniqueId());open(p,session,View.MAIN,0);return true;
    }
    private boolean valid(Player p,Session session) {
        return p.isOnline()&&sessions.get(p.getUniqueId())==session&&plugin.serverId(p).equals(session.scope)&&p.hasPermission("hollowutils.disguise");
    }
    private ItemStack icon(Material material,String title,String... lore) {
        ItemStack item=new ItemStack(material);var meta=item.getItemMeta();
        meta.displayName(Component.text(title,net.kyori.adventure.text.format.NamedTextColor.AQUA).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));
        meta.lore(Arrays.stream(lore).map(line->Component.text(line,net.kyori.adventure.text.format.NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false)).toList());item.setItemMeta(meta);return item;
    }
    private void open(Player p,Session session,View view,int requested) {
        if(!valid(p,session))return;
        List<String> choices=switch(view) {
            case MAIN->List.of();
            case NAME,SKIN->Bukkit.getOnlinePlayers().stream().filter(other->plugin.sameGroup(p,other.getWorld())&&p.canSee(other)).map(Player::getName).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            case RANK->ranks.choices(p).stream().map(CosmeticRanks.Choice::name).toList();
        };
        int page=Math.clamp(requested,0,Math.max(0,(choices.size()-1)/45));Menu menu=new Menu(session,view,page,choices);
        menu.inventory=Bukkit.createInventory(menu,54,Component.text(view==View.MAIN?"Disguise · appearance only":"Disguise · "+view.name().toLowerCase(Locale.ROOT)));
        if(view==View.MAIN) {
            menu.inventory.setItem(10,icon(Material.NAME_TAG,"Name · "+session.name,"Click to choose a name"));
            menu.inventory.setItem(12,icon(Material.PLAYER_HEAD,"Skin · "+session.skinLabel,"Choose an online skin or a Minecraft account"));
            menu.inventory.setItem(14,icon(Material.PAPER,"Rank · "+(session.rank==null?"Real rank":session.rank),"LuckPerms prefix appearance only","Permissions and punishment weight never change"));
            menu.inventory.setItem(16,icon(Material.LIME_CONCRETE,session.loading?"Skin loading…":"Apply disguise","Changes apply only when you click this"));
            menu.inventory.setItem(22,icon(Material.BARRIER,"Remove disguise","Restore your real name, skin and rank prefix"));
            menu.inventory.setItem(49,icon(Material.RED_CONCRETE,"Close","Discard this editing session"));
        } else {
            for(int i=page*45;i<Math.min(choices.size(),(page+1)*45);i++) {
                String value=choices.get(i);ItemStack item=icon(view==View.SKIN?Material.PLAYER_HEAD:Material.NAME_TAG,value,view==View.RANK?"Cosmetic rank · no permissions granted":"Click to select");
                if(view==View.SKIN) {
                    Player source=Bukkit.getOnlinePlayers().stream().filter(other->other.getName().equals(value)&&plugin.sameGroup(p,other.getWorld())&&p.canSee(other)).findFirst().orElse(null);
                    if(source!=null) { SkullMeta meta=(SkullMeta)item.getItemMeta();meta.setPlayerProfile(source.getPlayerProfile().clone());item.setItemMeta(meta); }
                }
                menu.inventory.setItem(i%45,item);
            }
            if(page>0)menu.inventory.setItem(45,icon(Material.ARROW,"Previous"));
            menu.inventory.setItem(49,icon(Material.BARRIER,"Back"));
            if(view==View.RANK)menu.inventory.setItem(47,icon(Material.PAPER,"Use real rank"));
            else menu.inventory.setItem(47,icon(Material.WRITABLE_BOOK,"Enter custom "+view.name().toLowerCase(Locale.ROOT),"Type a Minecraft-style name in chat","Your input will not be broadcast"));
            if((page+1)*45<choices.size())menu.inventory.setItem(53,icon(Material.ARROW,"Next"));
        }
        p.openInventory(menu.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(menu.session.player))return;
        int slot=event.getRawSlot();if(slot<0||slot>=54)return;
        Bukkit.getScheduler().runTask(plugin,()-> {
            if(!valid(p,menu.session)||p.getOpenInventory().getTopInventory().getHolder()!=menu)return;
            try { select(p,menu,slot); } catch(IllegalArgumentException e) { Responses.error(p,e.getMessage()); }
        });
    }
    private void select(Player p,Menu menu,int slot) {
        Session session=menu.session;
        if(menu.view==View.MAIN) {
            if(slot==10||slot==12||slot==14) {
                View view=slot==10?View.NAME:slot==12?View.SKIN:View.RANK;UtilityCommands.requirePermission(p,"hollowutils.disguise."+view.name().toLowerCase(Locale.ROOT));
                if(view==View.RANK&&ranks.choices(p).isEmpty())throw new IllegalArgumentException("No LuckPerms groups are available. Install LuckPerms to choose a cosmetic rank.");
                open(p,session,view,0);
            } else if(slot==16)apply(p,session);
            else if(slot==22) { reset(p);p.closeInventory();Responses.success(p,"Disguise","Removed"); }
            else if(slot==49) { sessions.remove(p.getUniqueId());inputs.remove(p.getUniqueId());p.closeInventory(); }
            return;
        }
        UtilityCommands.requirePermission(p,"hollowutils.disguise."+menu.view.name().toLowerCase(Locale.ROOT));
        if(slot==49)open(p,session,View.MAIN,0);
        else if(slot==45&&menu.page>0)open(p,session,menu.view,menu.page-1);
        else if(slot==53&&(menu.page+1)*45<menu.choices.size())open(p,session,menu.view,menu.page+1);
        else if(slot==47) {
            if(menu.view==View.RANK) { session.rank=null;open(p,session,View.MAIN,0); }
            else input(p,session,menu.view);
        } else if(slot<45) {
            int index=menu.page*45+slot;if(index>=menu.choices.size())return;String value=menu.choices.get(index);
            if(menu.view==View.NAME) { session.name=ChatFormat.name(value);session.changedName=!value.equals(realName(p));open(p,session,View.MAIN,0); }
            else if(menu.view==View.RANK) { session.rank=value;open(p,session,View.MAIN,0); }
            else {
                ItemStack card=menu.inventory.getItem(slot);
                if(card!=null&&card.getItemMeta() instanceof SkullMeta head&&head.getPlayerProfile()!=null) {
                    session.skin=head.getPlayerProfile().clone();session.skinLabel=value;session.changedSkin=true;open(p,session,View.MAIN,0);
                } else chooseSkin(p,session,value);
            }
        }
    }
    private void input(Player p,Session session,View view) {
        Input request=new Input(session,view);inputs.put(p.getUniqueId(),request);p.closeInventory();
        Responses.info(p,"Disguise", "Type the "+(view==View.NAME?"display name":"skin account name")+" in chat, or cancel. Input expires in 60 seconds.");
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(inputs.remove(p.getUniqueId(),request)) { sessions.remove(p.getUniqueId(),session);Responses.error(p,"Disguise input expired. Open /disguise to try again."); }
        },1200L);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void inputChat(AsyncChatEvent event) {
        UUID id=event.getPlayer().getUniqueId();Input request=inputs.get(id);if(request==null)return;
        event.setCancelled(true);
        if(!inputs.remove(id,request))return;
        String text=PlainTextComponentSerializer.plainText().serialize(event.message()).strip();
        Bukkit.getScheduler().runTask(plugin,()-> {
            Player p=event.getPlayer();Session session=request.session();if(!valid(p,session))return;
            try {
                UtilityCommands.requirePermission(p,"hollowutils.disguise."+request.kind().name().toLowerCase(Locale.ROOT));
                if(text.equalsIgnoreCase("cancel")) { open(p,session,View.MAIN,0);return; }
                ChatFormat.name(text);
                if(request.kind()==View.NAME) { session.name=text;session.changedName=!text.equals(realName(p));open(p,session,View.MAIN,0); }
                else chooseSkin(p,session,text);
            } catch(IllegalArgumentException e) { Responses.error(p,e.getMessage());open(p,session,View.MAIN,0); }
        });
    }
    private void chooseSkin(Player p,Session session,String account) {
        UtilityCommands.requirePermission(p,"hollowutils.disguise.skin");ChatFormat.name(account);
        Player local=Bukkit.getOnlinePlayers().stream().filter(other->(other.getName().equalsIgnoreCase(account)||realName(other).equalsIgnoreCase(account))&&plugin.sameGroup(p,other.getWorld())&&p.canSee(other)).findFirst().orElse(null);
        if(local!=null) { session.skin=local.getPlayerProfile().clone();session.skinLabel=account;session.changedSkin=true;open(p,session,View.MAIN,0);return; }
        session.loading=true;int ticket=++session.skinTicket;Responses.info(p,"Disguise","Looking up the signed skin for "+account+"…");
        Bukkit.createProfile(account).update().whenComplete((profile,error)-> {
            if(!plugin.isEnabled())return;
            Bukkit.getScheduler().runTask(plugin,()-> {
                if(!valid(p,session)||session.skinTicket!=ticket)return;session.loading=false;
                if(error!=null||profile==null||!profile.hasTextures()) { Responses.error(p,"No skin could be loaded. Check that the account exists and Mojang services are reachable.");open(p,session,View.MAIN,0);return; }
                session.skin=profile.clone();session.skinLabel=account;session.changedSkin=true;open(p,session,View.MAIN,0);
            });
        });
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(valid(p,session)&&session.skinTicket==ticket&&session.loading) { session.skinTicket++;session.loading=false;Responses.error(p,"Skin lookup timed out; existing appearance was kept.");open(p,session,View.MAIN,0); }
        },400L);
        open(p,session,View.MAIN,0);
    }
    private void apply(Player p,Session session) {
        if(session.loading)throw new IllegalArgumentException("Wait for the skin lookup to finish.");
        if(session.changedName)UtilityCommands.requirePermission(p,"hollowutils.disguise.name");
        if(session.changedSkin)UtilityCommands.requirePermission(p,"hollowutils.disguise.skin");
        if(session.rank!=null) {
            UtilityCommands.requirePermission(p,"hollowutils.disguise.rank");
            if(ranks.groupPrefix(p,session.rank)==null)throw new IllegalArgumentException("This LuckPerms group is no longer loaded. Choose another rank.");
        }
        Original original=originals.computeIfAbsent(p.getUniqueId(),ignored->new Original(p.getPlayerProfile().clone(),p.displayName(),p.playerListName()));
        PlayerProfile profile=DisguiseProfiles.build(original.profile(),session.skin,session.name);
        PlayerProfile previous=p.getPlayerProfile().clone();
        try { p.setPlayerProfile(profile);p.displayName(Component.text(session.name)); }
        catch(Exception e) { try { p.setPlayerProfile(previous); } catch(Exception ignored) { }throw new IllegalArgumentException("The server could not apply this appearance. Existing identity was kept."); }
        masks.put(p.getUniqueId(),new Mask(session.name,session.skin.clone(),session.skinLabel,session.rank,session.changedName,session.changedSkin));
        sessions.remove(p.getUniqueId());inputs.remove(p.getUniqueId());refresh(p);p.closeInventory();plugin.refreshPlayerVisibility();
        Responses.success(p,"Disguise",session.name+" · "+session.skinLabel+" · "+(session.rank==null?"real rank prefix":session.rank+" (cosmetic)"));
    }
    private void reset(Player p) {
        sessions.remove(p.getUniqueId());inputs.remove(p.getUniqueId());Original original=originals.get(p.getUniqueId());
        if(original!=null) { p.setPlayerProfile(original.profile().clone());p.displayName(original.display());p.playerListName(original.tab()); }
        originals.remove(p.getUniqueId());masks.remove(p.getUniqueId());refresh(p);
    }
    @EventHandler public void closed(InventoryCloseEvent event) {
        if(!(event.getInventory().getHolder() instanceof Menu menu)||!(event.getPlayer() instanceof Player p))return;
        Bukkit.getScheduler().runTask(plugin,()-> {
            if(sessions.get(p.getUniqueId())!=menu.session||inputs.containsKey(p.getUniqueId()))return;
            if(!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu current)||current.session!=menu.session) {
                sessions.remove(p.getUniqueId(),menu.session);menu.session.skinTicket++;
            }
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR) public void joined(PlayerJoinEvent event) { remember(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR) public void changed(PlayerChangedWorldEvent event) {
        if(!plugin.sameGroup(event.getPlayer(),event.getFrom()))reset(event.getPlayer());
        refresh(event.getPlayer());plugin.refreshPlayerVisibility();
    }
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        UUID id=event.getPlayer().getUniqueId();vanished.remove(id);originals.remove(id);masks.remove(id);sessions.remove(id);inputs.remove(id);chat.remove(id);realNames.remove(id);
    }
    List<String> complete(CommandSender sender,String command,String[] args) {
        return args.length==1&&sender.hasPermission("hollowutils."+command)?command.equals("vanish")?List.of("on","off"):List.of("off"):List.of();
    }
}
