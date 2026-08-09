package tw.eternalforge.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import tw.eternalforge.mmoitems.MMOItemsBridge;
import tw.eternalforge.util.Text;
import java.lang.reflect.Method;

/** Matches Vanilla, MMOItems and optional custom-item providers without compile-time dependency. */
public final class ItemMatcher {
    private final MMOItemsBridge mmo;
    public ItemMatcher(MMOItemsBridge mmo){this.mmo=mmo;}

    public record Spec(String source, String a, String b, int amount, String raw) {}

    public Spec parse(String raw) {
        if (raw == null) return null;
        String[] p = raw.trim().split(":");
        try {
            if (p.length >= 4 && p[0].equalsIgnoreCase("MMOITEMS")) return new Spec("MMOITEMS",p[1],p[2],Math.max(1,Integer.parseInt(p[3])),raw);
            if (p.length >= 3 && p[0].equalsIgnoreCase("VANILLA")) return new Spec("VANILLA",p[1],null,Math.max(1,Integer.parseInt(p[2])),raw);
            if (p.length >= 3 && (p[0].equalsIgnoreCase("ITEMSADDER")||p[0].equalsIgnoreCase("NEXO"))) return new Spec(p[0].toUpperCase(),p[1],null,Math.max(1,Integer.parseInt(p[2])),raw);
            if (p.length >= 3 && p[0].equalsIgnoreCase("MMOITEMS")) return new Spec("MMOITEMS",p[1],p[2],1,raw);
            if (p.length >= 2 && p[0].equalsIgnoreCase("VANILLA")) return new Spec("VANILLA",p[1],null,1,raw);
            if (p.length >= 2 && (p[0].equalsIgnoreCase("ITEMSADDER")||p[0].equalsIgnoreCase("NEXO"))) return new Spec(p[0].toUpperCase(),p[1],null,1,raw);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    public boolean matches(ItemStack item, Spec spec) {
        if (item == null || item.getType().isAir() || spec == null) return false;
        return switch (spec.source()) {
            case "MMOITEMS" -> mmo.matches(item,spec.a(),spec.b());
            case "VANILLA" -> {
                Material mat = Material.matchMaterial(spec.a());
                yield mat != null && item.getType() == mat;
            }
            case "ITEMSADDER" -> optionalId(item, "dev.lone.itemsadder.api.CustomStack", "byItemStack", "getNamespacedID", spec.a());
            case "NEXO" -> nexo(item,spec.a());
            default -> false;
        };
    }

    public String display(Spec s) {
        if (s==null) return "未知材料";
        if (s.source().equals("MMOITEMS")) return s.b();
        return s.a();
    }

    /** Uses the real display name when the player has placed a matching material in the GUI. */
    public String display(Spec s, ItemStack[] candidates) {
        if (s == null) return "未知材料";
        if (candidates != null) {
            for (ItemStack item : candidates) {
                if (matches(item, s)) return Text.itemDisplayName(item);
            }
        }
        return display(s);
    }

    private boolean optionalId(ItemStack item,String clazz,String byItem,String getter,String expected){
        try {
            Class<?> c=Class.forName(clazz); Object custom=c.getMethod(byItem,ItemStack.class).invoke(null,item); if(custom==null)return false;
            Object id=custom.getClass().getMethod(getter).invoke(custom); return id!=null&&expected.equalsIgnoreCase(id.toString());
        }catch(Throwable ignored){return false;}
    }
    private boolean nexo(ItemStack item,String expected){
        for(String cls:new String[]{"com.nexomc.nexo.api.NexoItems","com.nexomc.nexo.items.NexoItems"}) try{
            Class<?> c=Class.forName(cls);
            for(String mName:new String[]{"idFromItem","getIdFromItem"}) try{
                Method m=c.getMethod(mName,ItemStack.class); Object id=m.invoke(null,item); if(id!=null)return expected.equalsIgnoreCase(id.toString());
            }catch(NoSuchMethodException ignored){}
        }catch(Throwable ignored){}
        return false;
    }
}
