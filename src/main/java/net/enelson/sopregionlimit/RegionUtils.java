package net.enelson.sopregionlimit;

import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.commands.region.RegionCommands;
import com.sk89q.worldguard.protection.flags.Flag;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RegionUtils {
    private static final Set<Character> FLAG_VALUE_FLAGS = resolveFlagCommandValueFlags();

    private RegionUtils() {
    }

    public static Actor wrapAsPrivileged(CommandSender sender, boolean showMessages) {
        final Actor actor = sender instanceof Player
                ? WorldGuardPlugin.inst().wrapPlayer((Player) sender)
                : WorldGuardPlugin.inst().wrapCommandSender(sender);
        return (Actor) Proxy.newProxyInstance(actor.getClass().getClassLoader(), actor.getClass().getInterfaces(), (proxy, method, args) -> {
            String name = method.getName();
            if ("print".equals(name) || "printRaw".equals(name) || "printDebug".equals(name) || "printError".equals(name) || "printInfo".equals(name)) {
                return showMessages ? method.invoke(actor, args) : null;
            }
            if ("hasPermission".equals(name)) {
                return true;
            }
            if ("checkPermission".equals(name)) {
                return null;
            }
            return method.invoke(actor, args);
        });
    }

    public static <T> void applyFlag(CommandSender sender, String worldName, String regionId, Flag<T> flag, String value) throws Exception {
        RegionCommands regionCommands = new RegionCommands(com.sk89q.worldguard.WorldGuard.getInstance());
        String command = String.format("flag %s -w %s %s %s", regionId, worldName, flag.getName(), value);
        CommandContext context = new CommandContext(command, FLAG_VALUE_FLAGS);
        regionCommands.flag(context, wrapAsPrivileged(sender, false));
    }

    private static Set<Character> resolveFlagCommandValueFlags() {
        try {
            Method method = RegionCommands.class.getMethod("flag", CommandContext.class, Actor.class);
            Command annotation = method.getAnnotation(Command.class);
            char[] flags = annotation.flags().toCharArray();
            Set<Character> result = new HashSet<Character>();
            for (int index = 0; index < flags.length; index++) {
                if (flags.length > index + 1 && flags[index + 1] == ':') {
                    result.add(Character.valueOf(flags[index]));
                    index++;
                }
            }
            return result;
        } catch (Throwable throwable) {
            return Collections.emptySet();
        }
    }
}
