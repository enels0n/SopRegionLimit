package net.enelson.sopregionlimit;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class CleanerManager {
    private final SopRegionLimit plugin;
    private BukkitTask task;

    public CleanerManager(SopRegionLimit plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("cleaner.enabled", false)) {
            return;
        }

        long periodTicks = Math.max(20L, plugin.getConfig().getLong("cleaner.period", 3600L) * 20L);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                clean();
            }
        }, 20L * 5L, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void clean() {
        List<String> ignoredWorlds = plugin.getConfig().getStringList("cleaner.filter.ignore-worlds");
        List<String> ignoredRegions = plugin.getConfig().getStringList("cleaner.filter.ignore-regions");
        boolean ignoreWithoutOwners = plugin.getConfig().getBoolean("cleaner.filter.ignore-without-owners", true);

        for (World world : Bukkit.getWorlds()) {
            if (ignoredWorlds.contains(world.getName())) {
                continue;
            }

            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if (regionManager == null) {
                continue;
            }

            List<String> toRemove = new ArrayList<String>();
            for (ProtectedRegion region : regionManager.getRegions().values()) {
                if ("__global__".equalsIgnoreCase(region.getId()) || ignoredRegions.contains(region.getId())) {
                    continue;
                }
                if (ignoreWithoutOwners && region.getOwners().size() == 0) {
                    continue;
                }
                if (isExpired(region)) {
                    toRemove.add(region.getId());
                }
            }

            for (String regionId : toRemove) {
                ProtectedRegion region = regionManager.getRegion(regionId);
                if (region == null) {
                    continue;
                }
                RegionRemoveService.removeRegion(plugin, regionManager, region);
            }
        }
    }

    private boolean isExpired(ProtectedRegion region) {
        for (UUID uuid : region.getOwners().getUniqueIds()) {
            if (!wasLongAgo(uuid)) {
                return false;
            }
        }
        for (UUID uuid : region.getMembers().getUniqueIds()) {
            if (!wasLongAgo(uuid)) {
                return false;
            }
        }
        return true;
    }

    private boolean wasLongAgo(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        long lastPlayed = player.getLastPlayed();
        if (lastPlayed <= 0L) {
            return false;
        }

        long days = TimeUnit.DAYS.convert(new Date().getTime() - lastPlayed, TimeUnit.MILLISECONDS);
        return days > plugin.getConfig().getInt("cleaner.expire-time-days", 30);
    }
}
