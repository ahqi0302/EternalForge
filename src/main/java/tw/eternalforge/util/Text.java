package tw.eternalforge.util;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;

public final class Text {
    private Text() {}
    public static String c(String s) { return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s); }
    public static List<String> c(List<String> lines) {
        List<String> out = new ArrayList<>();
        if (lines != null) for (String s : lines) out.add(c(s));
        return out;
    }

    /** Returns the actual visible item name, keeping legacy colors used by MMOItems. */
    public static String itemDisplayName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "未知物品";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name != null && !name.isBlank()) return name;
        }
        String raw = item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    public static String plainItemName(ItemStack item) {
        if (item == null) return "未知物品";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String s = ChatColor.stripColor(meta.getDisplayName());
            if (s != null && !s.isBlank()) return s;
        }
        return item.getType().name();
    }
}
