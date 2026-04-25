package net.enelson.sopregionlimit;

import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ClaimCommandListener implements Listener {
    private static final Pattern REGION_NAME = Pattern.compile("[a-zA-Z0-9_]{3,20}");

    private final SopRegionLimit plugin;

    public ClaimCommandListener(SopRegionLimit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().split(" ");
        if (!isClaimCommand(args)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isOp() || hasPermission(player, "bypass")) {
            return;
        }

        event.setCancelled(true);

        String regionName = args[2];
        if (!REGION_NAME.matcher(regionName).matches() || "__global__".equalsIgnoreCase(regionName)) {
            player.sendMessage(plugin.message(player, "invalid-name"));
            return;
        }

        SelectionResult selectionResult = getSelection(player);
        if (selectionResult == null) {
            return;
        }
        if (!selectionResult.isAllowed()) {
            player.sendMessage(plugin.message(player, "unknown-region-type").replace("{type}", selectionResult.getType()));
            return;
        }

        CuboidRegion cuboid = selectionResult.getCuboid();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(cuboid.getWorld());
        if (regionManager == null) {
            player.sendMessage(plugin.message(player, "something-wrong"));
            return;
        }

        if (touchesForeignRegion(player, localPlayer, cuboid, regionManager)) {
            player.sendMessage(plugin.message(player, "another-region-affected"));
            return;
        }

        ClaimContext claimContext = resolveClaimContext(localPlayer, cuboid, regionManager);
        ClaimLimits limits = plugin.getClaimLimitResolver().resolve(player, claimContext.getClaimType());
        if (!checkDimensionLimits(player, cuboid, limits)) {
            return;
        }

        long ownedCount = countRegions(localPlayer, regionManager, claimContext.getClaimType());
        if (limits.getMaxCount() >= 0 && ownedCount >= limits.getMaxCount()) {
            player.sendMessage(plugin.message(player, claimContext.getClaimType() == ClaimType.IN_GLOBAL ? "max-count-in-global" : "max-count-in-own-region"));
            return;
        }

        String newRegionId = generateRegionId(claimContext, regionName);
        if (regionManager.getRegion(newRegionId) != null) {
            player.sendMessage(plugin.message(player, "region-exist"));
            return;
        }

        ProtectedCuboidRegion newRegion = new ProtectedCuboidRegion(newRegionId, cuboid.getPos1(), cuboid.getPos2());
        newRegion.getOwners().addPlayer(localPlayer);

        if (claimContext.getClaimType() == ClaimType.IN_OWN_REGION && claimContext.getParentRegion() != null) {
            ProtectedRegion parentRegion = claimContext.getParentRegion();
            List<String> children = RegionRemoveService.parseChildren(parentRegion.getFlag(plugin.getChildsFlag()));
            children.add(newRegion.getId());
            parentRegion.setFlag(plugin.getChildsFlag(), RegionRemoveService.joinChildren(children));
            newRegion.setFlag(plugin.getParentFlag(), parentRegion.getId());
            newRegion.setPriority(parentRegion.getPriority() + 1);
        } else {
            newRegion.setFlag(plugin.getChildsFlag(), "null");
        }

        regionManager.addRegion(newRegion);
        if (claimContext.getClaimType() == ClaimType.IN_OWN_REGION && claimContext.getParentRegion() != null) {
            applyFlags(player, player.getWorld().getName(), newRegion.getId(), plugin.getChildAutoFlags());
        } else {
            applyFlags(player, player.getWorld().getName(), newRegion.getId(), plugin.getParentAutoFlags());
        }
        try {
            regionManager.save();
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        player.sendMessage(plugin.message(player, claimContext.getClaimType() == ClaimType.IN_GLOBAL ? "success-in-global" : "success-in-own-region")
                .replace("{region}", newRegion.getId()));
    }

    private void applyFlags(Player player, String worldName, ProtectedRegion region, Map<Flag<?>, String> flags) {
        applyFlags(player, worldName, region.getId(), flags);
    }

    private void applyFlags(Player player, String worldName, String regionId, Map<Flag<?>, String> flags) {
        for (Map.Entry<Flag<?>, String> entry : flags.entrySet()) {
            try {
                RegionUtils.applyFlag(player, worldName, regionId, entry.getKey(), entry.getValue());
            } catch (CommandException exception) {
                exception.printStackTrace();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private boolean checkDimensionLimits(Player player, CuboidRegion cuboid, ClaimLimits limits) {
        if (checkMin(player, cuboid, cuboid.getWidth(), limits.getMinX(), "claim-min-x")) {
            return false;
        }
        if (checkMax(player, cuboid, cuboid.getWidth(), limits.getMaxX(), "claim-max-x")) {
            return false;
        }

        int height = cuboid.getHeight();
        if (limits.isVerticalExpand() && !hasPermission(player, "vertical-expand.bypass")) {
            expandVertically(cuboid, player);
            height = cuboid.getHeight();
        } else if (height < limits.getMinY() && plugin.getConfig().getBoolean("limit.autoexpand", false)) {
            autoExpandY(cuboid, limits.getMinY(), height);
            height = cuboid.getHeight();
        }

        if (checkMin(player, cuboid, height, limits.getMinY(), "claim-min-y")) {
            return false;
        }
        if (checkMax(player, cuboid, height, limits.getMaxY(), "claim-max-y")) {
            return false;
        }

        if (checkMin(player, cuboid, cuboid.getLength(), limits.getMinZ(), "claim-min-z")) {
            return false;
        }
        if (checkMax(player, cuboid, cuboid.getLength(), limits.getMaxZ(), "claim-max-z")) {
            return false;
        }
        return true;
    }

    private void autoExpandY(CuboidRegion cuboid, int target, int current) {
        int missing = target - current;
        if (missing <= 0) {
            return;
        }

        int downPercent = Math.max(0, Math.min(100, plugin.getConfig().getInt("limit.autoexpand-down", 15)));
        int down = (missing * downPercent) / 100;
        int up = missing - down;
        cuboid.setPos1(cuboid.getPos1().add(0, -down, 0));
        cuboid.setPos2(cuboid.getPos2().add(0, up, 0));
    }

    private void expandVertically(CuboidRegion cuboid, Player player) {
        int expand = player.getWorld().getMaxHeight() + 1;
        cuboid.expand(BlockVector3.at(0, expand, 0), BlockVector3.at(0, -expand, 0));
    }

    private boolean checkMin(Player player, CuboidRegion cuboid, int size, int min, String key) {
        if (min <= 0 || size >= min) {
            return false;
        }
        sendLimitMessage(player, cuboid, key, min);
        return true;
    }

    private boolean checkMax(Player player, CuboidRegion cuboid, int size, int max, String key) {
        if (max <= 0 || size <= max) {
            return false;
        }
        sendLimitMessage(player, cuboid, key, max);
        return true;
    }

    private void sendLimitMessage(Player player, CuboidRegion cuboid, String key, int limit) {
        player.sendMessage(plugin.message(player, key).replace("{size}", Integer.toString(limit)));
        player.sendMessage(plugin.message(player, "current-size")
                .replace("{x}", Integer.toString(cuboid.getWidth()))
                .replace("{y}", Integer.toString(cuboid.getHeight()))
                .replace("{z}", Integer.toString(cuboid.getLength())));
    }

    private SelectionResult getSelection(Player player) {
        LocalSession session = plugin.getWorldEdit().getSession(player);
        Region selection;
        try {
            selection = session.getSelection(session.getSelectionWorld());
        } catch (IncompleteRegionException exception) {
            return null;
        }

        if (!(selection instanceof CuboidRegion)) {
            return SelectionResult.denied(selection.getClass().getSimpleName());
        }
        return SelectionResult.allowed((CuboidRegion) selection);
    }

    private boolean touchesForeignRegion(Player player, LocalPlayer localPlayer, CuboidRegion cuboidRegion, RegionManager regionManager) {
        ProtectedCuboidRegion testRegion = new ProtectedCuboidRegion("__sopregionlimit_test__", cuboidRegion.getPos1(), cuboidRegion.getPos2());
        Iterator<ProtectedRegion> iterator = regionManager.getApplicableRegions(testRegion).iterator();
        while (iterator.hasNext()) {
            ProtectedRegion region = iterator.next();
            if ("__global__".equalsIgnoreCase(region.getId())) {
                continue;
            }
            plugin.debug("Checking overlap for " + player.getName() + " against region " + region.getId());
            if (!region.isOwner(localPlayer)) {
                return true;
            }
        }
        return false;
    }

    private ClaimContext resolveClaimContext(LocalPlayer localPlayer, CuboidRegion cuboidRegion, RegionManager regionManager) {
        ApplicableRegionSet applicableRegions1 = regionManager.getApplicableRegions(cuboidRegion.getPos1());
        ApplicableRegionSet applicableRegions2 = regionManager.getApplicableRegions(cuboidRegion.getPos2());

        for (ProtectedRegion region : applicableRegions1) {
            if (region.getFlag(plugin.getParentFlag()) != null) {
                continue;
            }
            if (region.getFlag(plugin.getChildsFlag()) == null) {
                continue;
            }
            if (!containsRegion(applicableRegions2, region)) {
                continue;
            }
            if (!region.isOwner(localPlayer)) {
                continue;
            }
            return new ClaimContext(ClaimType.IN_OWN_REGION, region);
        }
        return new ClaimContext(ClaimType.IN_GLOBAL, null);
    }

    private boolean containsRegion(ApplicableRegionSet set, ProtectedRegion region) {
        for (ProtectedRegion current : set) {
            if (current == region) {
                return true;
            }
        }
        return false;
    }

    private long countRegions(LocalPlayer localPlayer, RegionManager regionManager, ClaimType claimType) {
        long count = 0L;
        for (ProtectedRegion region : regionManager.getRegions().values()) {
            if (!region.getOwners().contains(localPlayer)) {
                continue;
            }
            if (claimType == ClaimType.IN_GLOBAL && region.getFlag(plugin.getChildsFlag()) != null) {
                count++;
            }
            if (claimType == ClaimType.IN_OWN_REGION && region.getFlag(plugin.getParentFlag()) != null) {
                count++;
            }
        }
        return count;
    }

    private String generateRegionId(ClaimContext context, String baseId) {
        return context.getClaimType() == ClaimType.IN_OWN_REGION && context.getParentRegion() != null
                ? context.getParentRegion().getId() + "_" + baseId.toLowerCase()
                : baseId.toLowerCase();
    }

    private boolean hasPermission(Player player, String suffix) {
        return player.hasPermission("sopregionlimit." + suffix)
                || player.hasPermission("aregionlimiter." + suffix)
                || player.hasPermission("magesregionlimit." + suffix);
    }

    private boolean isClaimCommand(String[] args) {
        return isRegionRoot(args) && args.length >= 3 && "claim".equalsIgnoreCase(args[1]);
    }

    private boolean isRegionRoot(String[] args) {
        if (args.length == 0) {
            return false;
        }
        return "/region".equalsIgnoreCase(args[0])
                || "/regions".equalsIgnoreCase(args[0])
                || "/rg".equalsIgnoreCase(args[0])
                || "/worldguard:region".equalsIgnoreCase(args[0])
                || "/worldguard:regions".equalsIgnoreCase(args[0])
                || "/worldguard:rg".equalsIgnoreCase(args[0]);
    }

    private static final class SelectionResult {
        private final CuboidRegion cuboid;
        private final boolean allowed;
        private final String type;

        private SelectionResult(CuboidRegion cuboid, boolean allowed, String type) {
            this.cuboid = cuboid;
            this.allowed = allowed;
            this.type = type;
        }

        private static SelectionResult allowed(CuboidRegion cuboid) {
            return new SelectionResult(cuboid, true, null);
        }

        private static SelectionResult denied(String type) {
            return new SelectionResult(null, false, type);
        }

        private CuboidRegion getCuboid() {
            return cuboid;
        }

        private boolean isAllowed() {
            return allowed;
        }

        private String getType() {
            return type;
        }
    }

    private static final class ClaimContext {
        private final ClaimType claimType;
        private final ProtectedRegion parentRegion;

        private ClaimContext(ClaimType claimType, ProtectedRegion parentRegion) {
            this.claimType = claimType;
            this.parentRegion = parentRegion;
        }

        private ClaimType getClaimType() {
            return claimType;
        }

        private ProtectedRegion getParentRegion() {
            return parentRegion;
        }
    }
}
