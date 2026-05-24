package net.enelson.sopregionlimit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class ClaimLimitResolver {
    private final SopRegionLimit plugin;

    public ClaimLimitResolver(SopRegionLimit plugin) {
        this.plugin = plugin;
    }

    public ClaimLimits resolve(Player player, ClaimType claimType) {
        String worldName = player.getWorld().getName();
        ConfigurationSection defaultSection = getGroupSection(claimType, worldName, "default");
        if (defaultSection == null) {
            throw new IllegalStateException("Missing config for " + claimType.getPath() + ".default");
        }

        ClaimLimits limits = new ClaimLimits(
                getInt(defaultSection, "max-count", 1),
                getInt(defaultSection, "x-min", 0),
                getInt(defaultSection, "x-max", 0),
                getInt(defaultSection, "y-min", 0),
                getInt(defaultSection, "y-max", 0),
                getInt(defaultSection, "z-min", 0),
                getInt(defaultSection, "z-max", 0),
                isVerticalExpand(defaultSection)
        );

        ConfigurationSection globalGroups = plugin.getConfig().getConfigurationSection("limit." + claimType.getPath());
        if (globalGroups == null) {
            return limits;
        }

        for (String group : globalGroups.getKeys(false)) {
            if ("default".equalsIgnoreCase(group)) {
                continue;
            }

            if (!hasGroupPermission(player, claimType, group)) {
                continue;
            }

            ConfigurationSection section = getGroupSection(claimType, worldName, group);
            if (section == null) {
                continue;
            }

            limits.applyGroup(
                    getInt(section, "max-count", limits.getMaxCount()),
                    getInt(section, "x-min", limits.getMinX()),
                    getInt(section, "x-max", limits.getMaxX()),
                    getInt(section, "y-min", limits.getMinY()),
                    getInt(section, "y-max", limits.getMaxY()),
                    getInt(section, "z-min", limits.getMinZ()),
                    getInt(section, "z-max", limits.getMaxZ()),
                    isVerticalExpand(section)
            );
        }

        return limits;
    }

    private ConfigurationSection getGroupSection(ClaimType claimType, String worldName, String group) {
        ConfigurationSection perWorld = plugin.getConfig().getConfigurationSection("limit-per-world." + claimType.getPath() + "." + worldName + "." + group);
        if (perWorld != null) {
            return perWorld;
        }
        return plugin.getConfig().getConfigurationSection("limit." + claimType.getPath() + "." + group);
    }

    private boolean hasGroupPermission(Player player, ClaimType claimType, String group) {
        return player.hasPermission("sopregionlimit." + claimType.getPath() + "." + group);
    }

    private int getInt(ConfigurationSection section, String path, int def) {
        return section == null ? def : section.getInt(path, def);
    }

    private boolean isVerticalExpand(ConfigurationSection section) {
        return section != null && section.getBoolean("vertical-expand", section.getBoolean("vertexpand", false));
    }
}
