package tw.eternalforge.config;

import org.bukkit.configuration.file.YamlConfiguration;
import tw.eternalforge.EternalForgePlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class ConfigManager {
    private final EternalForgePlugin plugin;
    private YamlConfiguration gui, messages, support;
    private final RecipeManager recipes;

    public ConfigManager(EternalForgePlugin plugin) {
        this.plugin = plugin;
        this.recipes = new RecipeManager(plugin);
    }

    public void loadAll() {
        saveIfMissing("gui.yml");
        saveIfMissing("messages.yml");
        saveIfMissing("support-items.yml");
        saveIfMissing("levels/default.yml");
        saveIfMissing("recipes/defaults/swords.yml");
        saveIfMissing("recipes/defaults/armor.yml");
        saveIfMissing("recipes/swords/example_sword.yml");
        gui = load("gui.yml");
        messages = load("messages.yml");
        support = load("support-items.yml");
        recipes.reload();
    }

    private void saveIfMissing(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (file.exists()) return;
        try {
            plugin.saveResource(path, false);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("缺少內建設定資源: " + path);
        }
    }

    private YamlConfiguration load(String path) {
        File file = new File(plugin.getDataFolder(), path);
        return YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration gui() { return gui; }
    public YamlConfiguration messages() { return messages; }
    public YamlConfiguration support() { return support; }
    public RecipeManager recipes() { return recipes; }
}
