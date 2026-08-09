package tw.eternalforge.gui;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tw.eternalforge.EternalForgePlugin;
import tw.eternalforge.history.ForgeHistory;
import tw.eternalforge.service.ForgeService;
import tw.eternalforge.service.ItemMatcher;
import tw.eternalforge.util.Text;

import java.util.*;

public final class ForgeMenu implements Listener {
    private static final String PLACEHOLDER_MARKER = "§0§0EF_PLACEHOLDER";

    private final EternalForgePlugin plugin;
    private final ForgeService service;
    private final ForgeHistory history;
    private final IconFactory icons = new IconFactory();
    private final Map<UUID, Inventory> menus = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> processing = new HashSet<>();

    /**
     * Player items are kept OUTSIDE Bukkit's top inventory. The top inventory always contains
     * fixed informational icons. This makes GUI placeholders impossible to steal and also means
     * a real item never visually replaces the guide icon.
     */
    private static final class Session {
        ItemStack equipment;
        final ItemStack[] materials = new ItemStack[4];
        ItemStack success;
        ItemStack protection;
        ItemStack amplifier;
    }

    private static final class ForgeHolder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;
        private ForgeHolder(UUID owner) { this.owner = owner; }
        private void bind(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public ForgeMenu(EternalForgePlugin p, ForgeService s, ForgeHistory h) {
        plugin = p;
        service = s;
        history = h;
    }

    private int eq() { return plugin.configs().gui().getInt("forge.equipment-slot", 13); }
    private int suc() { return plugin.configs().gui().getInt("forge.success-scroll-slot", 20); }
    private int prot() { return plugin.configs().gui().getInt("forge.protection-scroll-slot", 21); }
    private int amp() { return plugin.configs().gui().getInt("forge.amplifier-slot", 22); }
    private int confirm() { return plugin.configs().gui().getInt("forge.confirm-slot", 49); }
    private int preview() { return plugin.configs().gui().getInt("forge.preview-slot", 4); }

    private List<Integer> mats() {
        List<Integer> v = plugin.configs().gui().getIntegerList("forge.material-slots");
        return v.isEmpty() ? List.of(29, 30, 31, 32) : v.subList(0, Math.min(4, v.size()));
    }

    private int size() {
        int s = plugin.configs().gui().getInt("forge.size", 54);
        s = Math.max(9, Math.min(54, s));
        if (s % 9 != 0) s = ((s + 8) / 9) * 9;
        return Math.min(54, s);
    }

    private boolean input(int slot) {
        return slot == eq() || slot == suc() || slot == prot() || slot == amp() || mats().contains(slot);
    }

    private String guiTitle() {
        boolean rp = plugin.getConfig().getBoolean("resourcepack.enabled", true);
        if (rp) {
            boolean requireIA = plugin.getConfig().getBoolean("resourcepack.require-itemsadder", false);
            boolean hasIA = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
            if (!requireIA || hasIA) {
                String raw = plugin.getConfig().getString("resourcepack.symbol", "\\uE650");
                if (raw != null && raw.startsWith("\\u") && raw.length() == 6) {
                    try { raw = String.valueOf((char) Integer.parseInt(raw.substring(2), 16)); }
                    catch (NumberFormatException ignored) {}
                }
                if (raw != null && !raw.isBlank()) return Text.c("&f" + raw);
            }
            return Text.c(plugin.getConfig().getString("resourcepack.fallback-title",
                    plugin.configs().gui().getString("forge.title", "&8EternalForge")));
        }
        return Text.c(plugin.configs().gui().getString("forge.title", "&8EternalForge"));
    }

    public void open(Player p) {
        int sz = size();
        for (int x : allSlots()) {
            if (x < 0 || x >= sz) {
                p.sendMessage(Text.c("&cEternalForge GUI slot 設定超出範圍: " + x + " / size=" + sz));
                return;
            }
        }
        // If the player somehow opens a second menu, return the old stored inputs first.
        Session old = sessions.remove(p.getUniqueId());
        if (old != null) returnSessionItems(p, old);

        ForgeHolder holder = new ForgeHolder(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, sz, guiTitle());
        holder.bind(inv);
        menus.put(p.getUniqueId(), inv);
        sessions.put(p.getUniqueId(), new Session());
        paint(inv, p.getUniqueId());
        p.openInventory(inv);
    }

    private List<Integer> allSlots() {
        List<Integer> l = new ArrayList<>(mats());
        l.addAll(List.of(eq(), suc(), prot(), amp(), confirm(), preview()));
        return l;
    }

    private boolean ours(org.bukkit.inventory.InventoryView v) {
        if (!(v.getPlayer() instanceof Player p)) return false;
        InventoryHolder holder = v.getTopInventory().getHolder();
        return holder instanceof ForgeHolder fh && fh.owner.equals(p.getUniqueId());
    }

    private Session session(UUID id) { return sessions.computeIfAbsent(id, x -> new Session()); }

    private void paint(Inventory inv, UUID owner) {
        boolean clearFill = plugin.getConfig().getBoolean("resourcepack.enabled", true)
                && plugin.getConfig().getBoolean("resourcepack.clear-filler-items", true);
        if (!clearFill) {
            ItemStack fill = icons.make(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
            for (int i = 0; i < inv.getSize(); i++) {
                if (!input(i) && i != confirm() && i != preview()) inv.setItem(i, fill);
            }
        }
        refresh(inv, owner);
    }

    private boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        List<String> lore = meta.getLore();
        return lore != null && lore.contains(PLACEHOLDER_MARKER);
    }

    private ItemStack placeholder(Material mat, String name, List<String> lore) {
        List<String> copy = new ArrayList<>(lore);
        copy.add(PLACEHOLDER_MARKER);
        return icons.make(mat, name, copy);
    }

    private Material configuredMaterial(String path, Material fallback) {
        String s = plugin.configs().gui().getString(path + ".material", fallback.name());
        Material m = Material.matchMaterial(s);
        return m == null ? fallback : m;
    }

    private List<String> configuredLore(String path) { return new ArrayList<>(plugin.configs().gui().getStringList(path + ".lore")); }
    private String configuredName(String path, String fallback) { return plugin.configs().gui().getString(path + ".name", fallback); }

    private String supportMatcherName(String key) {
        ItemMatcher.Spec s = service.matcher().parse(plugin.configs().support().getString(key + ".matcher", ""));
        return s == null ? "未設定" : displaySpec(s);
    }

    private String displaySpec(ItemMatcher.Spec s) {
        String id = service.matcher().display(s);
        String mapped = plugin.configs().gui().getString("display-names." + id, "");
        return mapped == null || mapped.isBlank() ? id : mapped;
    }

    private String selected(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return null;
        return Text.itemDisplayName(item) + " &7x" + item.getAmount();
    }

    private void addSelectedStatus(List<String> lore, ItemStack item) {
        String s = selected(item);
        lore.add("");
        if (s == null) {
            lore.add("&8目前： &7尚未放入");
            lore.add("&e▶ 手持物品點擊此格放入");
        } else {
            lore.add("&a目前已放入： &f" + s);
            lore.add("&e▶ 空手點擊此格取回");
            lore.add("&7手持其他物品點擊可替換");
        }
    }

    private void refreshPlaceholders(Inventory inv, UUID owner, ForgeService.Preview p) {
        Session s = session(owner);

        String path = "gui-placeholders.equipment";
        List<String> lore = configuredLore(path);
        addSelectedStatus(lore, s.equipment);
        inv.setItem(eq(), placeholder(configuredMaterial(path, Material.ANVIL), configuredName(path, "&b&l✦ 放入強化裝備 ✦"), lore));

        List<Integer> slots = mats();
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            ItemStack selected = s.materials[i];
            if (p.valid() && i < p.materials().size()) {
                ItemMatcher.Spec spec = p.materials().get(i);
                String itemName = displaySpec(spec);
                List<String> ml = new ArrayList<>();
                ml.add("");
                ml.add("&7此格需要放入： &f" + itemName);
                ml.add("&7需求數量： &e" + spec.amount() + " 個");
                addSelectedStatus(ml, selected);
                inv.setItem(slot, placeholder(Material.GRAY_DYE, "&6&l材料" + (i + 1) + "： &f" + itemName, ml));
            } else if (p.valid()) {
                String up = "gui-placeholders.unused-material";
                List<String> ul = configuredLore(up);
                ul.add("");
                ul.add("&c此格目前已鎖定，不能放入物品。");
                inv.setItem(slot, placeholder(configuredMaterial(up, Material.LIGHT_GRAY_STAINED_GLASS_PANE), configuredName(up, "&8此欄位不需要材料"), ul));
            } else {
                String mp = "gui-placeholders.material";
                List<String> ml = configuredLore(mp);
                addSelectedStatus(ml, selected);
                inv.setItem(slot, placeholder(configuredMaterial(mp, Material.GRAY_DYE), configuredName(mp, "&6&l強化材料 %index%").replace("%index%", String.valueOf(i + 1)), ml));
            }
        }

        path = "gui-placeholders.success-scroll";
        lore = configuredLore(path);
        
        if (service.matchesSupport(s.success, "success")) {
            lore.add("");
            lore.add("&a目前強化石： " + service.successStoneName(s.success));
            lore.add("&7成功率加成： &a+" + fmt(service.successBonus(s.success)) + "%");
        }
        addSelectedStatus(lore, s.success);
        inv.setItem(suc(), placeholder(configuredMaterial(path, Material.PAPER), configuredName(path, "&a&l✦ 強化石槽位 ✦"), lore));

        path = "gui-placeholders.protection";
        lore = configuredLore(path);
        lore.add("&8需要： &7" + supportMatcherName("protection"));
        addSelectedStatus(lore, s.protection);
        inv.setItem(prot(), placeholder(configuredMaterial(path, Material.SHIELD), configuredName(path, "&b&l保護券"), lore));

        path = "gui-placeholders.amplifier";
        lore = configuredLore(path);
        for (int i = 0; i < lore.size(); i++) lore.set(i, lore.get(i).replace("%chance%", fmt(service.amplifierChance())).replace("%extra%", String.valueOf(service.amplifierExtra())));
        lore.add("&8需要： &7" + supportMatcherName("amplifier"));
        addSelectedStatus(lore, s.amplifier);
        inv.setItem(amp(), placeholder(configuredMaterial(path, Material.AMETHYST_SHARD), configuredName(path, "&d&l✦ 躍升石槽位 ✦"), lore));
    }

    private void refresh(Inventory inv, UUID owner) {
        Session s = session(owner);
        ForgeService.Preview p = service.preview(s.equipment, s.success);
        refreshPlaceholders(inv, owner, p);

        List<String> lore = new ArrayList<>();
        Material b = Material.BARRIER;
        String name = "&c&l無法強化";
        if (p.valid()) {
            name = "&b&l✦ 開始強化 ✦";
            b = Material.ANVIL;
            lore.add("&7裝備： &f" + Text.itemDisplayName(s.equipment));
            lore.add("&7等級： &e+" + p.current() + " &8→ &a+" + p.target());
            lore.add("&7成功率： &e" + fmt(p.chance()) + "%");
            if (service.matchesSupport(s.success, "success")) lore.add("&a  └ 強化石加成： +" + fmt(service.successBonus(s.success)) + "%");
            lore.add("&7金幣： &6" + money(p.rule().money()));
            lore.add("");
            int n = 1;
            for (ItemMatcher.Spec spec : p.materials()) {
                int have = service.count(s.materials, spec);
                lore.add((have >= spec.amount() ? "&a" : "&c") + "材料" + n + "： &f" + displaySpec(spec) + " &7" + have + "/" + spec.amount());
                n++;
            }
            if (service.matchesSupport(s.protection, "protection")) lore.add("&b保護券： &a已放入");
            if (service.matchesSupport(s.amplifier, "amplifier")) lore.add("&d躍升石： &a已放入 &7(" + fmt(service.amplifierChance()) + "% 額外 +" + service.amplifierExtra() + ")");
            if (p.rule().failDrop() > 0) lore.add("&7失敗掉級： &c-" + p.rule().failDrop());
            if (p.rule().destroy()) lore.add("&7失敗可能： &4裝備損壞");
            lore.add("");
            lore.add("&a▶ 點擊開始強化");
        } else {
            lore.add("&c" + plainMsg(p.error()));
            lore.add("");
            lore.add("&7請依照欄位提示放入強化裝備。");
        }
        inv.setItem(confirm(), icons.make(b, name, lore));

        List<String> pre = new ArrayList<>();
        if (p.valid()) {
            pre.add("&7目前強化： &e+" + p.current());
            pre.add("&7下一級： &a+" + p.target());
            pre.add("&7成功率： &e" + fmt(p.chance()) + "%");
            pre.add("&7費用： &6" + money(p.rule().money()));
            if (service.matchesSupport(s.amplifier, "amplifier")) pre.add("&d增幅成功可達： &f+" + Math.min(p.max(), p.target() + service.amplifierExtra()));
            pre.add("");
            pre.add("&8實際屬性由 MMOItems");
            pre.add("&8Upgrade Template 計算。");
        } else pre.add("&7放入裝備後顯示下一級資訊。");
        inv.setItem(preview(), icons.make(Material.PAPER, "&f&l下一級預覽", pre));
    }

    private boolean materialSlotDisabled(UUID owner, int raw) {
        int idx = mats().indexOf(raw);
        if (idx < 0) return false;
        Session s = session(owner);
        ForgeService.Preview p = service.preview(s.equipment, s.success);
        return p.valid() && idx >= p.materials().size();
    }

    private ItemStack getStored(Session s, int raw) {
        if (raw == eq()) return s.equipment;
        if (raw == suc()) return s.success;
        if (raw == prot()) return s.protection;
        if (raw == amp()) return s.amplifier;
        int idx = mats().indexOf(raw);
        return idx >= 0 && idx < s.materials.length ? s.materials[idx] : null;
    }

    private void setStored(Session s, int raw, ItemStack item) {
        item = normalize(item);
        if (raw == eq()) s.equipment = item;
        else if (raw == suc()) s.success = item;
        else if (raw == prot()) s.protection = item;
        else if (raw == amp()) s.amplifier = item;
        else {
            int idx = mats().indexOf(raw);
            if (idx >= 0 && idx < s.materials.length) s.materials[idx] = item;
        }
    }

    private ItemStack normalize(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0 ? null : item;
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!ours(e.getView())) return;
        Player p = (Player) e.getWhoClicked();
        UUID id = p.getUniqueId();
        Inventory top = e.getView().getTopInventory();
        Session s = session(id);

        if (processing.contains(id)) {
            e.setCancelled(true);
            p.sendMessage(Text.c(msg("processing")));
            return;
        }

        int raw = e.getRawSlot();
        String click = e.getClick() == null ? "" : e.getClick().name();
        if (click.equals("DOUBLE_CLICK") || click.equals("NUMBER_KEY") || click.equals("SWAP_OFFHAND") ||
                click.equals("DROP") || click.equals("CONTROL_DROP") || click.equals("MIDDLE") || click.contains("CREATIVE")) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> refresh(top, id));
            return;
        }

        // Entire forge top inventory is immutable from Bukkit's perspective.
        if (raw >= 0 && raw < top.getSize()) {
            e.setCancelled(true);

            if (raw == confirm()) {
                start(p, top);
                return;
            }
            if (!input(raw) || materialSlotDisabled(id, raw)) {
                Bukkit.getScheduler().runTask(plugin, () -> refresh(top, id));
                return;
            }

            ItemStack cursor = normalize(e.getCursor());
            ItemStack stored = normalize(getStored(s, raw));

            // Empty hand: retrieve only the REAL stored player item. Placeholder remains unchanged.
            if (cursor == null) {
                if (stored != null) {
                    e.setCursor(stored.clone());
                    setStored(s, raw, null);
                }
                Bukkit.getScheduler().runTask(plugin, () -> refresh(top, id));
                return;
            }

            // Hand has an item: store/swap it in the virtual slot; placeholder never changes.
            ItemStack incoming = cursor.clone();
            if (stored == null) {
                setStored(s, raw, incoming);
                e.setCursor(new ItemStack(Material.AIR));
            } else {
                setStored(s, raw, incoming);
                e.setCursor(stored.clone());
            }
            Bukkit.getScheduler().runTask(plugin, () -> refresh(top, id));
            return;
        }

        // Never allow shift-click to inject player items into arbitrary GUI slots.
        if (e.isShiftClick()) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> refresh(top, id));
        }
    }

    @EventHandler public void drag(InventoryDragEvent e) {
        if (!ours(e.getView())) return;
        if (processing.contains(e.getWhoClicked().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler public void close(InventoryCloseEvent e) {
        if (!ours(e.getView())) return;
        Player p = (Player) e.getPlayer();
        UUID id = p.getUniqueId();
        menus.remove(id);
        processing.remove(id);
        Session s = sessions.remove(id);
        if (s != null && plugin.configs().gui().getBoolean("forge.return-items-on-close", true)) returnSessionItems(p, s);
    }

    private void returnSessionItems(Player p, Session s) {
        List<ItemStack> items = new ArrayList<>();
        if (normalize(s.equipment) != null) items.add(s.equipment);
        for (ItemStack x : s.materials) if (normalize(x) != null) items.add(x);
        if (normalize(s.success) != null) items.add(s.success);
        if (normalize(s.protection) != null) items.add(s.protection);
        if (normalize(s.amplifier) != null) items.add(s.amplifier);
        for (ItemStack item : items) {
            var left = p.getInventory().addItem(item);
            for (ItemStack x : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), x);
        }
    }

    private void start(Player p, Inventory inv) {
        UUID id = p.getUniqueId();
        Session s = session(id);
        ForgeService.Preview pre = service.preview(s.equipment, s.success);
        if (!pre.valid()) {
            p.sendMessage(Text.c(msg(pre.error())));
            refresh(inv, id);
            return;
        }
        if (!processing.add(id)) return;
        boolean anim = plugin.configs().gui().getBoolean("forge.animation.enabled", true);
        if (!anim) { finish(p, inv); return; }
        int duration = Math.max(1, plugin.configs().gui().getInt("forge.animation.duration-ticks", 30));
        int step = Math.max(1, plugin.configs().gui().getInt("forge.animation.step-ticks", 5));
        final int[] t = {0};
        final org.bukkit.scheduler.BukkitTask[] task = {null};
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || !ours(p.getOpenInventory())) {
                processing.remove(id);
                task[0].cancel();
                return;
            }
            t[0] += step;
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, .25f, 1.7f);
            inv.setItem(confirm(), icons.make(Material.SMITHING_TABLE, "&e&l強化中...", List.of("&7進度： &f" + Math.min(100, t[0] * 100 / duration) + "%")));
            if (t[0] >= duration) {
                task[0].cancel();
                finish(p, inv);
            }
        }, 0L, step);
    }

    private void finish(Player p, Inventory inv) {
        UUID id = p.getUniqueId();
        Session s = session(id);
        ForgeService.Result r = service.forge(p, s.equipment, s.materials, s.success, s.protection, s.amplifier);
        processing.remove(id);

        normalizeSession(s);
        if (!r.attempted()) {
            String err = r.error();
            if (err != null && err.startsWith("no-material|")) {
                String[] x = err.split("\\|");
                p.sendMessage(Text.c(msg("no-material").replace("%material%", x[1]).replace("%need%", x[2]).replace("%have%", x[3])));
            } else p.sendMessage(Text.c(msg(err).replace("%money%", money(r.rule().money()))));
            refresh(inv, id);
            return;
        }

        s.equipment = normalize(r.item());
        if (r.success()) {
            p.sendMessage(Text.c(msg("success").replace("%old_level%", String.valueOf(r.before())).replace("%level%", String.valueOf(r.after()))));
            if (r.amplified()) p.sendMessage(Text.c(msg("amplified").replace("%level%", String.valueOf(r.after()))));
            sound(p, "success");
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 24, .5, .7, .5, .05);
            history.append(p, r.item(), r.before(), r.after(), r.amplified() ? "AMPLIFIED" : "SUCCESS", r.chance());
            if (plugin.getConfig().getBoolean("broadcast.enabled", true) && r.after() >= plugin.getConfig().getInt("broadcast.minimum-level", 10)) {
                Bukkit.broadcastMessage(Text.c(plugin.getConfig().getString("broadcast.message", "").replace("%player%", p.getName()).replace("%item%", Text.plainItemName(r.item())).replace("%level%", String.valueOf(r.after()))));
            }
        } else if (r.destroyed()) {
            s.equipment = null;
            p.sendMessage(Text.c(msg("destroyed")));
            sound(p, "destroy");
            history.append(p, null, r.before(), -1, "DESTROYED", r.chance());
        } else {
            p.sendMessage(Text.c(msg("fail").replace("%level%", String.valueOf(r.after()))));
            if (r.protectedFail()) p.sendMessage(Text.c(msg("protected")));
            sound(p, r.after() < r.before() ? "drop" : "fail");
            history.append(p, r.item(), r.before(), r.after(), r.after() < r.before() ? "DROP" : "FAIL", r.chance());
        }
        normalizeSession(s);
        refresh(inv, id);
    }

    private void normalizeSession(Session s) {
        s.equipment = normalize(s.equipment);
        for (int i = 0; i < s.materials.length; i++) s.materials[i] = normalize(s.materials[i]);
        s.success = normalize(s.success);
        s.protection = normalize(s.protection);
        s.amplifier = normalize(s.amplifier);
    }

    private void sound(Player p, String k) {
        String path = "feedback." + k + ".";
        try {
            Sound s = Sound.valueOf(plugin.configs().gui().getString(path + "sound", "ENTITY_EXPERIENCE_ORB_PICKUP").toUpperCase());
            p.playSound(p.getLocation(), s, (float) plugin.configs().gui().getDouble(path + "volume", .8), (float) plugin.configs().gui().getDouble(path + "pitch", 1));
        } catch (Exception ignored) {}
    }

    private String msg(String k) { return plugin.configs().messages().getString(k, "&c" + k); }
    private String plainMsg(String k) { String s = ChatColor.stripColor(Text.c(msg(k))); return s == null ? k : s; }
    private String fmt(double d) { return String.format(Locale.ROOT, "%.1f", d); }
    private String money(double d) { return String.format(Locale.ROOT, "%,.0f", d); }
}
