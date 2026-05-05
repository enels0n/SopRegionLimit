package net.enelson.sopregionlimit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class SopRegionLimitCommand implements CommandExecutor {
    private final SopRegionLimit plugin;

    public SopRegionLimitCommand(SopRegionLimit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("sopregionlimit.admin") && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }

            try {
                plugin.reloadPluginState();
                sender.sendMessage(plugin.message(sender, "reload"));
            } catch (Exception exception) {
                sender.sendMessage(ChatColor.RED + "Reload failed: " + exception.getMessage());
                exception.printStackTrace();
            }
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "/" + label + " reload");
        return true;
    }
}
