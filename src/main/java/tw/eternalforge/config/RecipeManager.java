package tw.eternalforge.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tw.eternalforge.EternalForgePlugin;

import java.io.File;
import java.util.*;

public final class RecipeManager {
    public record Recipe(String key, String type, String id, boolean enabled, Integer maxLevel,
                         String levelProfile, List<String> materials, YamlConfiguration yaml, File source) {}
    public record RuleData(double chance, double money, int failDrop, boolean destroy) {}

    private final EternalForgePlugin plugin;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> profiles = new LinkedHashMap<>();

    public RecipeManager(EternalForgePlugin plugin) { this.plugin = plugin; }

    public void reload() {
        recipes.clear(); profiles.clear();
        loadProfiles();
        File root = new File(plugin.getDataFolder(), "recipes");
        scanRecipes(root);
        plugin.getLogger().info("已載入 " + recipes.size() + " 個強化配方、" + profiles.size() + " 個 levels profile。");
    }

    private void loadProfiles() {
        File dir = new File(plugin.getDataFolder(), "levels");
        File[] files = dir.listFiles((d,n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            String name = f.getName().substring(0, f.getName().length()-4).toLowerCase(Locale.ROOT);
            profiles.put(name, YamlConfiguration.loadConfiguration(f));
        }
    }

    private void scanRecipes(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids == null) return;
            Arrays.sort(kids, Comparator.comparing(File::getName));
            for (File k : kids) scanRecipes(k);
            return;
        }
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        String type = y.getString("item.type", "*").trim().toUpperCase(Locale.ROOT);
        String id = y.getString("item.id", "*").trim().toUpperCase(Locale.ROOT);
        String key = type + ":" + id;
        Recipe r = new Recipe(key, type, id, y.getBoolean("enabled", true),
                y.contains("max-level") ? Math.max(1, y.getInt("max-level")) : null,
                y.getString("level-profile", "default").trim().toLowerCase(Locale.ROOT),
                List.copyOf(y.getStringList("materials")), y, file);
        Recipe old = recipes.put(key, r);
        if (old != null) plugin.getLogger().warning("重複強化配方 " + key + "，後載入檔案覆蓋前者: " + file.getPath());
    }

    public Recipe resolve(String type, String id) {
        if (type == null || id == null) return null;
        String t = type.toUpperCase(Locale.ROOT), i = id.toUpperCase(Locale.ROOT);
        for (String key : List.of(t+":"+i, t+":*", "*:*") ) {
            Recipe r = recipes.get(key);
            if (r != null && r.enabled()) return r;
        }
        return null;
    }

    private YamlConfiguration profile(String name) {
        YamlConfiguration y = profiles.get(name == null ? "default" : name.toLowerCase(Locale.ROOT));
        return y != null ? y : profiles.get("default");
    }

    public int maxLevel(Recipe r, int fallback) {
        if (r != null && r.maxLevel() != null) return r.maxLevel();
        YamlConfiguration p = profile(r == null ? "default" : r.levelProfile());
        if (p != null && p.contains("max-level")) return Math.max(1, p.getInt("max-level"));
        return fallback;
    }

    public RuleData rule(Recipe r, int target) {
        String path = "levels." + target;
        if (r != null && r.yaml().isConfigurationSection(path)) return readRule(r.yaml(), path);
        YamlConfiguration p = profile(r == null ? "default" : r.levelProfile());
        if (p != null && p.isConfigurationSection(path)) return readRule(p, path);
        YamlConfiguration def = profiles.get("default");
        if (def != null && def.isConfigurationSection(path)) return readRule(def, path);
        return new RuleData(0, 0, 0, false);
    }

    private RuleData readRule(YamlConfiguration y, String path) {
        return new RuleData(y.getDouble(path+".chance",0), y.getDouble(path+".money",0),
                Math.max(0,y.getInt(path+".fail-drop",0)), y.getBoolean(path+".destroy",false));
    }

    public List<String> materials(Recipe r, int target) {
        if (r == null) return List.of();
        String path = "levels." + target + ".materials";
        if (r.yaml().contains(path)) return List.copyOf(r.yaml().getStringList(path));
        return r.materials();
    }

    public Collection<Recipe> allRecipes() { return Collections.unmodifiableCollection(recipes.values()); }
}
