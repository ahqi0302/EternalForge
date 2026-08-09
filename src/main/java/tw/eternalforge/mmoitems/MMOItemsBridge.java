package tw.eternalforge.mmoitems;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Runtime bridge so small MMOItems API signature changes don't hard-crash the plugin. */
public final class MMOItemsBridge {
    private final JavaPlugin plugin;
    private Constructor<?> liveCtor;
    private Method getUpgradeLevel, hasUpgradeTemplate, getMaxUpgradeLevel, getUpgradeTemplate, newBuilder;
    private Method builderBuild;
    private String mode = "uninitialized";

    public MMOItemsBridge(JavaPlugin plugin) { this.plugin = plugin; }

    public boolean initialize() {
        try {
            Class<?> live = Class.forName("net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem");
            liveCtor = live.getConstructor(ItemStack.class);
            getUpgradeLevel = live.getMethod("getUpgradeLevel");
            hasUpgradeTemplate = live.getMethod("hasUpgradeTemplate");
            getMaxUpgradeLevel = live.getMethod("getMaxUpgradeLevel");
            getUpgradeTemplate = live.getMethod("getUpgradeTemplate");
            newBuilder = live.getMethod("newBuilder");
            mode = "LiveMMOItem-reflection";
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().severe("MMOItems bridge 初始化失敗: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }

    public String mode() { return mode; }

    public Live inspect(ItemStack item) throws ReflectiveOperationException {
        if (item == null || item.getType().isAir()) throw new IllegalArgumentException("empty item");
        Object live = liveCtor.newInstance(item.clone());
        int level = ((Number) getUpgradeLevel.invoke(live)).intValue();
        boolean hasTemplate = (Boolean) hasUpgradeTemplate.invoke(live);
        int max = ((Number) getMaxUpgradeLevel.invoke(live)).intValue();
        return new Live(live, level, max, hasTemplate);
    }

    public ItemStack setUpgradeLevel(ItemStack source, int level) throws ReflectiveOperationException {
        Live wrapped = inspect(source);
        Object live = wrapped.handle();
        Object template = getUpgradeTemplate.invoke(live);
        if (template == null) throw new NoSuchMethodException("UpgradeTemplate missing");
        Method upgradeTo = null;
        for (Method m : template.getClass().getMethods()) {
            if (!m.getName().equals("upgradeTo") || m.getParameterCount() != 2) continue;
            Class<?>[] p = m.getParameterTypes();
            if ((p[1] == int.class || p[1] == Integer.class) && p[0].isAssignableFrom(live.getClass())) { upgradeTo = m; break; }
        }
        if (upgradeTo == null) throw new NoSuchMethodException("UpgradeTemplate#upgradeTo(...,int)");
        upgradeTo.invoke(template, live, level);
        Object builder = newBuilder.invoke(live);
        if (builderBuild == null || !builderBuild.getDeclaringClass().isAssignableFrom(builder.getClass())) builderBuild = builder.getClass().getMethod("build");
        Object built = builderBuild.invoke(builder);
        if (!(built instanceof ItemStack stack)) throw new ReflectiveOperationException("builder.build() did not return ItemStack");
        return stack;
    }

    public String typeId(ItemStack item) { return nbt(item, "MMOITEMS_ITEM_TYPE"); }
    public String itemId(ItemStack item) { return nbt(item, "MMOITEMS_ITEM_ID"); }
    public boolean matches(ItemStack item, String type, String id) {
        String t=typeId(item), i=itemId(item);
        return t != null && i != null && t.equalsIgnoreCase(type) && i.equalsIgnoreCase(id);
    }

    private String nbt(ItemStack item, String key) {
        if (item == null || item.getType().isAir()) return null;
        for (String className : new String[]{"io.lumine.mythic.lib.api.item.NBTItem", "net.Indyuce.mmoitems.api.util.NBTItem"}) {
            try {
                Class<?> nbt = Class.forName(className);
                Object obj = nbt.getMethod("get", ItemStack.class).invoke(null, item);
                Object value = nbt.getMethod("getString", String.class).invoke(obj, key);
                if (value != null && !value.toString().isBlank()) return value.toString();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public record Live(Object handle, int level, int maxLevel, boolean hasTemplate) {}
}
