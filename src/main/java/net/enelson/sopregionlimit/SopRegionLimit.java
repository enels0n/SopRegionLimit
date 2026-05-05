package net.enelson.sopregionlimit;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import me.clip.placeholderapi.PlaceholderAPI;
import net.enelson.sopli.lib.text.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SopRegionLimit extends JavaPlugin {
    private final TextUtils textUtils = new TextUtils();

    private StringFlag childsFlag;
    private StringFlag parentFlag;
    private final Map<Flag<?>, String> parentAutoFlags = new LinkedHashMap<Flag<?>, String>();
    private final Map<Flag<?>, String> childAutoFlags = new LinkedHashMap<Flag<?>, String>();

    private CleanerManager cleanerManager;
    private ClaimLimitResolver claimLimitResolver;

    @Override
    public void onLoad() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        this.childsFlag = resolveStringFlag(registry, "rl-childs");
        this.parentFlag = resolveStringFlag(registry, "rl-parent");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getWorldEdit() == null) {
            getLogger().severe("WorldEdit is required for SopRegionLimit.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        reloadPluginState();

        getServer().getPluginManager().registerEvents(new ClaimCommandListener(this), this);
        getServer().getPluginManager().registerEvents(new RemoveCommandListener(this), this);

        if (getCommand("sopregionlimit") != null) {
            getCommand("sopregionlimit").setExecutor(new SopRegionLimitCommand(this));
        }
    }

    @Override
    public void onDisable() {
        if (cleanerManager != null) {
            cleanerManager.stop();
            cleanerManager = null;
        }
    }

    public void reloadPluginState() {
        reloadConfig();

        ConfigurationSection limitSection = getConfig().getConfigurationSection("limit");
        if (limitSection == null) {
            throw new IllegalStateException("Config must contain limit section.");
        }

        ConfigurationSection inGlobal = getConfig().getConfigurationSection("limit.in-global");
        if (inGlobal == null || !inGlobal.contains("default")) {
            throw new IllegalStateException("Config must contain limit.in-global.default section.");
        }

        ConfigurationSection inOwn = getConfig().getConfigurationSection("limit.in-own-region");
        if (inOwn == null || !inOwn.contains("default")) {
            throw new IllegalStateException("Config must contain limit.in-own-region.default section.");
        }

        loadAutoFlags();
        this.claimLimitResolver = new ClaimLimitResolver(this);

        if (cleanerManager != null) {
            cleanerManager.stop();
        }
        cleanerManager = new CleanerManager(this);
        cleanerManager.start();
    }

    public WorldEditPlugin getWorldEdit() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        return plugin instanceof WorldEditPlugin ? (WorldEditPlugin) plugin : null;
    }

    public StringFlag getChildsFlag() {
        return childsFlag;
    }

    public StringFlag getParentFlag() {
        return parentFlag;
    }

    public Map<Flag<?>, String> getParentAutoFlags() {
        return parentAutoFlags;
    }

    public Map<Flag<?>, String> getChildAutoFlags() {
        return childAutoFlags;
    }

    public ClaimLimitResolver getClaimLimitResolver() {
        return claimLimitResolver;
    }

    public String message(CommandSender sender, String path) {
        String raw = getConfig().getString("messages." + path);
        if (raw == null) {
            raw = getConfig().getString("messages.something-wrong", "&cSomething went wrong. Contact the administrator.");
        }
        if (raw == null) {
            raw = "&cSomething went wrong. Contact the administrator.";
        }
        if (sender instanceof org.bukkit.entity.Player && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            raw = PlaceholderAPI.setPlaceholders((org.bukkit.entity.Player) sender, raw);
        }
        return textUtils.color(raw);
    }

    public void debug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("[debug] " + message);
        }
    }

    private void loadAutoFlags() {
        parentAutoFlags.clear();
        childAutoFlags.clear();
        loadAutoFlags(parentAutoFlags, getConfig().getConfigurationSection("limit.in-global-default-flags"));
        loadAutoFlags(childAutoFlags, getConfig().getConfigurationSection("limit.in-own-region-default-flags"));
    }

    private void loadAutoFlags(Map<Flag<?>, String> target, ConfigurationSection section) {
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            Flag<?> flag = Flags.fuzzyMatchFlag(WorldGuard.getInstance().getFlagRegistry(), key);
            if (flag != null) {
                target.put(flag, section.getString(key, ""));
            }
        }
    }

    private StringFlag resolveStringFlag(FlagRegistry registry, String name) {
        Flag<?> existing = registry.get(name);
        if (existing instanceof StringFlag) {
            return (StringFlag) existing;
        }

        StringFlag flag = new StringFlag(name);
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException exception) {
            Flag<?> conflicted = registry.get(name);
            if (conflicted instanceof StringFlag) {
                return (StringFlag) conflicted;
            }
            throw new IllegalStateException("Unable to register WorldGuard flag " + name, exception);
        }
    }
}
