package net.enelson.sopregionlimit;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;

public final class RemoveCommandListener implements Listener {
    private static final String[] DELETE_COMMANDS = new String[]{"remove", "rem", "delete", "del"};

    private final SopRegionLimit plugin;

    public RemoveCommandListener(SopRegionLimit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().split(" ");
        if (!isDeleteCommand(args)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        String worldName = player.getWorld().getName();
        String regionId = null;
        for (int index = 2; index < args.length; index++) {
            if ("-w".equalsIgnoreCase(args[index]) || "--world".equalsIgnoreCase(args[index])) {
                if (index + 1 < args.length) {
                    worldName = args[index + 1].replace("\"", "");
                    index++;
                }
                continue;
            }
            if (regionId == null) {
                regionId = args[index];
            }
        }

        if (regionId == null) {
            player.sendMessage(plugin.message(player, "something-wrong"));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(plugin.message(player, "invalid-world").replace("{world}", worldName));
            return;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            player.sendMessage(plugin.message(player, "invalid-world").replace("{world}", worldName));
            return;
        }

        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) {
            player.sendMessage(plugin.message(player, "invalid-region")
                    .replace("{region}", regionId)
                    .replace("{world}", worldName));
            return;
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        if (!region.isOwner(localPlayer) && !player.isOp() && !player.hasPermission("sopregionlimit.bypass")) {
            player.sendMessage(plugin.message(player, "no-perm-remove"));
            return;
        }

        String parent = region.getFlag(plugin.getParentFlag());
        if (parent != null) {
            ProtectedRegion parentRegion = regionManager.getRegion(parent);
            if (parentRegion != null) {
                List<String> children = RegionRemoveService.parseChildren(parentRegion.getFlag(plugin.getChildsFlag()));
                children.remove(region.getId());
                parentRegion.setFlag(plugin.getChildsFlag(), RegionRemoveService.joinChildren(children));
            }
            regionManager.removeRegion(region.getId());
            save(regionManager);
            player.sendMessage(plugin.message(player, "remove-region-child").replace("{region}", region.getId()));
            return;
        }

        List<String> children = RegionRemoveService.parseChildren(region.getFlag(plugin.getChildsFlag()));
        for (String childId : children) {
            regionManager.removeRegion(childId);
        }
        regionManager.removeRegion(region.getId());
        save(regionManager);
        if (children.isEmpty()) {
            player.sendMessage(plugin.message(player, "remove-region").replace("{region}", region.getId()));
        } else {
            player.sendMessage(plugin.message(player, "remove-region-parent")
                    .replace("{region}", region.getId())
                    .replace("{children}", String.join(", ", children)));
        }
    }

    private boolean isDeleteCommand(String[] args) {
        if (!isRegionRoot(args) || args.length < 3) {
            return false;
        }
        for (String command : DELETE_COMMANDS) {
            if (command.equalsIgnoreCase(args[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean isRegionRoot(String[] args) {
        return "/region".equalsIgnoreCase(args[0])
                || "/regions".equalsIgnoreCase(args[0])
                || "/rg".equalsIgnoreCase(args[0])
                || "/worldguard:region".equalsIgnoreCase(args[0])
                || "/worldguard:regions".equalsIgnoreCase(args[0])
                || "/worldguard:rg".equalsIgnoreCase(args[0]);
    }

    private void save(RegionManager regionManager) {
        try {
            regionManager.save();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
