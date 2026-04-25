package net.enelson.sopregionlimit;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RegionRemoveService {
    private RegionRemoveService() {
    }

    public static void removeRegion(SopRegionLimit plugin, RegionManager regionManager, ProtectedRegion protectedRegion) {
        String parent = protectedRegion.getFlag(plugin.getParentFlag());
        if (parent != null) {
            ProtectedRegion parentRegion = regionManager.getRegion(parent);
            if (parentRegion != null) {
                parentRegion.setFlag(plugin.getChildsFlag(), joinChildren(removeChild(parseChildren(parentRegion.getFlag(plugin.getChildsFlag())), protectedRegion.getId())));
            }
            regionManager.removeRegion(protectedRegion.getId());
            logCleaner(plugin, protectedRegion.getId());
            save(regionManager);
            return;
        }

        List<String> children = parseChildren(protectedRegion.getFlag(plugin.getChildsFlag()));
        for (String childId : children) {
            regionManager.removeRegion(childId);
            logCleaner(plugin, childId);
        }
        regionManager.removeRegion(protectedRegion.getId());
        logCleaner(plugin, protectedRegion.getId());
        save(regionManager);
    }

    public static List<String> parseChildren(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equalsIgnoreCase(raw.trim())) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(raw.split(", ")));
    }

    public static String joinChildren(List<String> children) {
        return children.isEmpty() ? "null" : String.join(", ", children);
    }

    private static List<String> removeChild(List<String> children, String childId) {
        children.remove(childId);
        return children;
    }

    private static void logCleaner(SopRegionLimit plugin, String regionId) {
        if (plugin.getConfig().getBoolean("cleaner.log-in-console", true)) {
            plugin.getLogger().info(plugin.message(null, "cleaner-delete-region").replace("{region}", regionId));
        }
    }

    private static void save(RegionManager regionManager) {
        try {
            regionManager.save();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
