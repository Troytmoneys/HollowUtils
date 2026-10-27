package io.github.troytmoneys.hollowutils;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import java.util.*;

final class ItemCommands {
    static final Set<String> COMMANDS=Set.of("togglebreakdelay","instabreak","i","give","enchant");
    private final UtilityCommands targets;
    private final InstantBreaking breaking;
    ItemCommands(HollowUtils plugin,UtilityCommands targets) {
        this.targets=targets;breaking=new InstantBreaking(plugin::serverId);
        plugin.getServer().getPluginManager().registerEvents(breaking,plugin);
    }
    boolean execute(CommandSender sender,String name,String[] args) {
        UtilityCommands.requirePermission(sender,CommandPermissions.node(name));
        if(name.equals("togglebreakdelay")||name.equals("instabreak"))return breaking.execute(sender,args);
        if(name.equals("enchant"))return enchant(sender,args);
        boolean give=name.equals("give");int index=give?1:0;
        if(args.length<index+1||args.length>index+2)return false;
        int amount=args.length==index+2?ItemAllocation.amount(args[index+1]):1;
        Material material=Material.matchMaterial(args[index]);
        if(material==null||material.isAir()||!material.isItem())throw new IllegalArgumentException("Unknown item. Use a material ID such as diamond or minecraft:diamond_sword.");
        Player target=targets.target(sender,give?args[0]:null,"hollowutils.give.others");
        if(target.isDead())throw new IllegalArgumentException("Wait for the target to respawn before giving items.");
        ItemStack template=new ItemStack(material);
        ItemStack[] contents=target.getInventory().getStorageContents();
        int[] counts=new int[contents.length];boolean[] same=new boolean[contents.length];
        for(int i=0;i<contents.length;i++)if(contents[i]!=null&&!contents[i].getType().isAir()) {
            counts[i]=contents[i].getAmount();same[i]=contents[i].isSimilar(template);
        }
        int[] add=ItemAllocation.plan(counts,same,template.getMaxStackSize(),amount);
        ItemStack[] updated=new ItemStack[contents.length];
        for(int i=0;i<contents.length;i++) {
            if(contents[i]!=null)updated[i]=contents[i].clone();
            if(add[i]>0) { if(counts[i]==0)updated[i]=template.clone();updated[i].setAmount(counts[i]+add[i]); }
        }
        target.getInventory().setStorageContents(updated);
        Responses.success(sender,"Items",target.getName()+" · "+amount+" × "+material.getKey().getKey());
        if(sender!=target)Responses.info(target,"Items","Received "+amount+" × "+material.getKey().getKey());
        return true;
    }
    private boolean enchant(CommandSender sender,String[] args) {
        if(!(sender instanceof Player p))throw new IllegalArgumentException("Hold an item in-game to enchant it.");
        if(args.length<1||args.length>2)return false;
        int level=EnchantmentLevels.parse(args.length==2?args[1]:"1");
        NamespacedKey key=NamespacedKey.fromString(args[0].toLowerCase(Locale.ROOT));
        Enchantment enchantment=key==null?null:Registry.ENCHANTMENT.get(key);
        if(enchantment==null)throw new IllegalArgumentException("Unknown enchantment. Use a registry ID such as sharpness or minecraft:efficiency.");
        ItemStack held=p.getInventory().getItemInMainHand();
        if(held.getType().isAir())throw new IllegalArgumentException("Hold the item you want to enchant in your main hand.");
        ItemStack edited=held.clone();var meta=edited.getItemMeta();
        boolean book=meta instanceof EnchantmentStorageMeta;
        if(level>0&&(level>enchantment.getMaxLevel()||(!book&&!enchantment.canEnchantItem(held))||(!book&&meta.hasConflictingEnchant(enchantment))))
            UtilityCommands.requirePermission(sender,"hollowutils.enchant.unsafe");
        if(meta instanceof EnchantmentStorageMeta storage) {
            if(level==0)storage.removeStoredEnchant(enchantment);else storage.addStoredEnchant(enchantment,level,true);
        } else { if(level==0)meta.removeEnchant(enchantment);else meta.addEnchant(enchantment,level,true); }
        if(!edited.setItemMeta(meta))throw new IllegalArgumentException("The server rejected this enchantment; the held item was not changed.");
        var actual=edited.getItemMeta();int stored=actual instanceof EnchantmentStorageMeta storage?storage.getStoredEnchantLevel(enchantment):actual.getEnchantLevel(enchantment);
        if(stored!=level)throw new IllegalArgumentException("The server did not preserve the requested level; the held item was not changed.");
        p.getInventory().setItemInMainHand(edited);
        Responses.success(sender,"Enchantments",level==0?"Removed "+enchantment.getKey().getKey():enchantment.getKey().getKey()+" · level "+level);
        return true;
    }
    List<String> complete(CommandSender sender,String name,String[] args) {
        if(!sender.hasPermission(CommandPermissions.node(name)))return List.of();
        if(name.equals("togglebreakdelay")||name.equals("instabreak"))return args.length==1?List.of("on","off"):List.of();
        if(name.equals("give")&&args.length==1)return targets.targetNames(sender,"hollowutils.give.others");
        if((name.equals("i")&&args.length==1)||(name.equals("give")&&args.length==2))return Arrays.stream(Material.values()).filter(m->m.isItem()&&!m.isAir()).map(m->m.getKey().getKey()).sorted().toList();
        if((name.equals("i")&&args.length==2)||(name.equals("give")&&args.length==3))return List.of("1","16","64");
        if(name.equals("enchant")&&args.length==1) { List<String> names=new ArrayList<>();Registry.ENCHANTMENT.forEach(e->names.add(e.getKey().getNamespace().equals("minecraft")?e.getKey().getKey():e.getKey().toString()));return names.stream().sorted().toList(); }
        if(name.equals("enchant")&&args.length==2)return List.of("0","1","5","255");
        return List.of();
    }
}
