package tw.eternalforge.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tw.eternalforge.EternalForgePlugin;
import tw.eternalforge.config.RecipeManager;
import tw.eternalforge.mmoitems.MMOItemsBridge;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class ForgeService {
    private final EternalForgePlugin plugin;
    private final Economy economy;
    private final MMOItemsBridge mmo;
    private final ItemMatcher matcher;

    public ForgeService(EternalForgePlugin p, Economy e, MMOItemsBridge m){plugin=p;economy=e;mmo=m;matcher=new ItemMatcher(m);}
    public ItemMatcher matcher(){return matcher;}
    public record Rule(double chance,double money,int failDrop,boolean destroy){}
    public record Preview(boolean valid,String error,int current,int target,int max,Rule rule,List<ItemMatcher.Spec> materials,double chance,String type,String id){}
    public record Result(boolean attempted,boolean success,boolean protectedFail,boolean amplified,boolean destroyed,String error,ItemStack item,int before,int after,Rule rule,double chance){}

    private YamlConfiguration gui(){ return plugin.configs().gui(); }
    private YamlConfiguration support(){ return plugin.configs().support(); }
    private RecipeManager rm(){ return plugin.configs().recipes(); }

    private Rule rule(RecipeManager.Recipe recipe, int target){
        RecipeManager.RuleData x=rm().rule(recipe,target);
        return new Rule(x.chance(),x.money(),x.failDrop(),x.destroy());
    }
    public int defaultMax(){return Math.max(1,gui().getInt("forge.default-max-level",15));}

    private RecipeManager.Recipe recipeFor(ItemStack equipment){
        String t=mmo.typeId(equipment), id=mmo.itemId(equipment);
        return t==null||id==null?null:rm().resolve(t,id);
    }

    public List<ItemMatcher.Spec> recipe(ItemStack equipment){
        RecipeManager.Recipe r=recipeFor(equipment);
        if(r==null)return List.of();
        int target=1;
        try{target=Math.max(1,mmo.inspect(equipment).level()+1);}catch(Throwable ignored){}
        List<ItemMatcher.Spec> out=new ArrayList<>();
        for(String raw:rm().materials(r,target)){ItemMatcher.Spec sp=matcher.parse(raw);if(sp!=null)out.add(sp);}
        return out;
    }

    public boolean matchesSupport(ItemStack item,String key){
        if ("success".equalsIgnoreCase(key)) return successBonus(item) > 0.0;
        return support().getBoolean(key+".enabled",true)&&matchesCfg(item,key+".matcher");
    }
    public double successBonus(ItemStack item){
        if(item==null||item.getType().isAir()||!support().getBoolean("success.enabled",true)) return 0.0;
        var sec=support().getConfigurationSection("success.stones");
        if(sec==null) return 0.0;
        for(String key:sec.getKeys(false)){
            String base="success.stones."+key+".";
            ItemMatcher.Spec spec=matcher.parse(support().getString(base+"matcher",""));
            if(spec!=null&&matcher.matches(item,spec)) return Math.max(0.0,support().getDouble(base+"bonus-chance",0.0));
        }
        return 0.0;
    }
    public String successStoneName(ItemStack item){
        if(item==null||item.getType().isAir()) return "";
        var sec=support().getConfigurationSection("success.stones");
        if(sec==null) return "";
        for(String key:sec.getKeys(false)){
            String base="success.stones."+key+".";
            ItemMatcher.Spec spec=matcher.parse(support().getString(base+"matcher",""));
            if(spec!=null&&matcher.matches(item,spec)) return support().getString(base+"name",key);
        }
        return "";
    }
    public double amplifierChance(){return support().getDouble("amplifier.trigger-chance",20.0);}
    public int amplifierExtra(){return Math.max(1,support().getInt("amplifier.extra-level",1));}
    public double chance(Rule r,ItemStack stone){return Math.max(0,Math.min(100,r.chance()+successBonus(stone)));}

    public Preview preview(ItemStack equipment,ItemStack successStone){
        Rule fb=new Rule(0,0,0,false);
        if(equipment==null||equipment.getType().isAir())return new Preview(false,"not-mmoitem",0,1,defaultMax(),fb,List.of(),0,null,null);
        String t=mmo.typeId(equipment),id=mmo.itemId(equipment);
        if(t==null||id==null)return new Preview(false,"not-mmoitem",0,1,defaultMax(),fb,List.of(),0,null,null);
        RecipeManager.Recipe rec=rm().resolve(t,id);
        try{
            MMOItemsBridge.Live live=mmo.inspect(equipment);
            if(!live.hasTemplate())return new Preview(false,"no-template",live.level(),live.level()+1,defaultMax(),fb,List.of(),0,t,id);
            if(rec==null && gui().getBoolean("forge.require-configured-recipe",true))return new Preview(false,"not-configured",live.level(),live.level()+1,defaultMax(),fb,List.of(),0,t,id);
            int configuredMax=rm().maxLevel(rec,defaultMax());
            int mx=Math.min(configuredMax,live.maxLevel()>0?live.maxLevel():configuredMax);
            if(live.level()>=mx)return new Preview(false,"max-level",live.level(),live.level(),mx,rule(rec,Math.max(1,live.level())),List.of(),0,t,id);
            int target=live.level()+1;
            Rule r=rule(rec,target);
            List<ItemMatcher.Spec> mats=new ArrayList<>();
            if(rec!=null)for(String raw:rm().materials(rec,target)){ItemMatcher.Spec sp=matcher.parse(raw);if(sp!=null)mats.add(sp);}
            return new Preview(true,null,live.level(),target,mx,r,mats,chance(r,successStone),t,id);
        }catch(Throwable ex){return new Preview(false,"not-mmoitem",0,1,defaultMax(),fb,List.of(),0,t,id);}
    }

    private boolean matchesCfg(ItemStack item,String path){ItemMatcher.Spec s=matcher.parse(support().getString(path,""));return s!=null&&matcher.matches(item,s);}
    public int count(ItemStack[] slots,ItemMatcher.Spec spec){int n=0;for(ItemStack i:slots)if(matcher.matches(i,spec))n+=i.getAmount();return n;}
    private void consume(ItemStack[] slots,ItemMatcher.Spec spec,int amount){int left=amount;for(ItemStack i:slots){if(left<=0)break;if(!matcher.matches(i,spec))continue;int take=Math.min(left,i.getAmount());i.setAmount(i.getAmount()-take);left-=take;}}
    private void consumeOne(ItemStack item){if(item!=null&&!item.getType().isAir())item.setAmount(Math.max(0,item.getAmount()-1));}

    public Result forge(Player player,ItemStack equipment,ItemStack[] materials,ItemStack successStone,ItemStack protect,ItemStack amplifier){
        Preview p=preview(equipment,successStone);if(!p.valid())return new Result(false,false,false,false,false,p.error(),equipment,p.current(),p.current(),p.rule(),p.chance());
        for(ItemMatcher.Spec s:p.materials()){int have=count(materials,s);if(have<s.amount())return new Result(false,false,false,false,false,"no-material|"+matcher.display(s)+"|"+s.amount()+"|"+have,equipment,p.current(),p.current(),p.rule(),p.chance());}
        OfflinePlayer account=player;
        if(!economy.has(account,p.rule().money()))return new Result(false,false,false,false,false,"no-money",equipment,p.current(),p.current(),p.rule(),p.chance());
        var tx=economy.withdrawPlayer(account,p.rule().money());if(!tx.transactionSuccess())return new Result(false,false,false,false,false,"economy-error",equipment,p.current(),p.current(),p.rule(),p.chance());
        for(ItemMatcher.Spec s:p.materials())consume(materials,s,s.amount());
        boolean successUsed=matchesSupport(successStone,"success");if(successUsed&&support().getBoolean("success.consume",true))consumeOne(successStone);
        boolean amplifierUsed=matchesSupport(amplifier,"amplifier");if(amplifierUsed&&support().getBoolean("amplifier.consume",true))consumeOne(amplifier);
        try{
            if(ThreadLocalRandom.current().nextDouble(100)<p.chance()){
                int target=p.target(); boolean amplified=false;
                if(amplifierUsed&&ThreadLocalRandom.current().nextDouble(100)<amplifierChance()){
                    target=Math.min(p.max(),target+amplifierExtra()); amplified=target>p.target();
                }
                ItemStack out=mmo.setUpgradeLevel(equipment,target);return new Result(true,true,false,amplified,false,null,out,p.current(),target,p.rule(),p.chance());
            }
            if(p.rule().destroy())return new Result(true,false,false,false,true,null,null,p.current(),-1,p.rule(),p.chance());
            boolean protection=p.rule().failDrop()>0&&matchesSupport(protect,"protection")&&support().getBoolean("protection.prevent-level-drop",true);
            if(protection&&support().getBoolean("protection.consume-on-trigger",true))consumeOne(protect);
            int after=protection?p.current():Math.max(0,p.current()-p.rule().failDrop());
            ItemStack out=after==p.current()?equipment.clone():mmo.setUpgradeLevel(equipment,after);
            return new Result(true,false,protection,false,false,null,out,p.current(),after,p.rule(),p.chance());
        }catch(Throwable ex){economy.depositPlayer(account,p.rule().money());plugin.getLogger().warning("MMOItems upgrade error: "+ex.getClass().getSimpleName()+": "+ex.getMessage());return new Result(false,false,false,false,false,"mmoitems-error",equipment,p.current(),p.current(),p.rule(),p.chance());}
    }
}
