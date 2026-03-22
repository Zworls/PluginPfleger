package de.dermaschinist.pluginpfleger;

import net.luckperms.api.LuckPerms;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;

public class PermissionHelper {

    private final PluginPfleger plugin;

    public PermissionHelper(PluginPfleger plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            RegisteredServiceProvider<LuckPerms> provider =
                    plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                plugin.getLogger().info("LuckPerms erkannt – Permissions werden über LuckPerms verwaltet.");
            }
        } else {
            plugin.getLogger().info("LuckPerms nicht gefunden – nutze OP als Fallback.");
        }
    }

    public boolean hatBerechtigung(CommandSender sender, String permission) {
        if (!(sender instanceof org.bukkit.entity.Player)) return true;
        return sender.hasPermission(permission);
    }
}
