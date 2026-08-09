package tw.eternalforge;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import tw.eternalforge.command.ForgeCommand;
import tw.eternalforge.config.ConfigManager;
import tw.eternalforge.gui.ForgeMenu;
import tw.eternalforge.history.ForgeHistory;
import tw.eternalforge.mmoitems.MMOItemsBridge;
import tw.eternalforge.service.ForgeService;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class EternalForgePlugin extends JavaPlugin {
    private Economy economy;
    private ConfigManager configs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configs = new ConfigManager(this);
        configs.loadAll();

        if (Bukkit.getPluginManager().getPlugin("MMOItems") == null) {
            getLogger().severe("找不到 MMOItems，插件已停用。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!setupEconomy()) {
            getLogger().severe("找不到 Vault Economy Provider，插件已停用。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (getConfig().getBoolean("resourcepack.auto-install", true)) installBundledItemsAdderGui();

        MMOItemsBridge mmo = new MMOItemsBridge(this);
        if (!mmo.initialize()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        ForgeHistory hist = new ForgeHistory(this);
        ForgeService service = new ForgeService(this, economy, mmo);
        ForgeMenu menu = new ForgeMenu(this, service, hist);
        ForgeCommand cmd = new ForgeCommand(this, menu, hist);

        if (getCommand("forge") != null) {
            getCommand("forge").setExecutor(cmd);
            getCommand("forge").setTabCompleter(cmd);
        }
        Bukkit.getPluginManager().registerEvents(menu, this);
        getLogger().info("EternalForge v3.4.0 Modular Config Edition 已啟用。Paper 1.21.10 / Java 21 / bridge=" + mmo.mode());
    }

    public ConfigManager configs() { return configs; }

    public void reloadAll() {
        reloadConfig();
        configs.loadAll();
    }

    private void installBundledItemsAdderGui() {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return;
        boolean overwrite = getConfig().getBoolean("resourcepack.overwrite-bundled-gui", true);
        try {
            File plugins = getDataFolder().getParentFile();
            if (plugins == null) return;
            Path base = plugins.toPath().resolve("ItemsAdder").resolve("contents").resolve("eternalforge");
            String[][] bundled = {
                    {"resourcepack/eternalforge/configs/font_images.yml", "configs/font_images.yml"},
                    {"resourcepack/eternalforge/textures/font/forge_gui.png", "textures/font/forge_gui.png"},
                    {"resourcepack/eternalforge/textures/font/forge_button.png", "textures/font/forge_button.png"},
                    {"resourcepack/eternalforge/VERSION.txt", "VERSION.txt"}
            };
            for (String[] entry : bundled) {
                Path target = base.resolve(entry[1]);
                if (!overwrite && Files.exists(target)) continue;
                try (InputStream in = getResource(entry[0])) {
                    if (in == null) continue;
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception ex) {
            getLogger().warning("同步 ItemsAdder Custom GUI 失敗：" + ex.getMessage());
        }
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> r = getServer().getServicesManager().getRegistration(Economy.class);
        if (r == null) return false;
        economy = r.getProvider();
        return economy != null;
    }
}
