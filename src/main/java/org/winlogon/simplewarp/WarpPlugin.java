package org.winlogon.simplewarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.HashMap;
import java.util.Arrays;

public class WarpPlugin extends JavaPlugin {
    private DatabaseHandler databaseHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseHandler = new DatabaseHandler(this);

        try {
            databaseHandler.connectToDatabase();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommands();
    }

    /** 
     * Registers the commands for this plugin.
     *
     * @return void
     */
    private void registerCommands() {
        PluginCommand warpCommand = getCommand("warp");
        if (warpCommand != null) {
            warpCommand.setExecutor(this::onCommand);
            warpCommand.setTabCompleter(new CommandCompletion(databaseHandler));
        }
    }

    @Override
    public void onDisable() {
        try {
            databaseHandler.closeConnection();
        } catch (SQLException e) {
            getLogger().severe("Failed to close database connection: " + e.getMessage());
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.AQUA + "/warp <new|remove|edit|teleport> [arguments]");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "new" -> newWarp(player, args);
            case "remove" -> removeWarp(player, args);
            case "edit" -> editWarp(player, args);
            case "teleport", "tp" -> teleport(player, args);
            default -> player.sendMessage(
                    ChatColor.RED + "Invalid subcommand." + ChatColor.GRAY + " Use: /warp <new|remove|edit|teleport>");
        }

        return true;
    }

    /**
     * Creates a new warp.
     *
     * @param player The player who executed the command.
     * @param args The command arguments.
     * @return void
     */
    private void newWarp(Player player, String[] args) {
        if (!player.hasPermission("warp.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /warp new [name] {[x] [y] [z]}");
            return;
        }

        String name = args[1];
        Location location = player.getLocation();

        if (args.length >= 5) {
            try {
                location.setX(Integer.parseInt(args[2]));
                location.setY(Integer.parseInt(args[3]));
                location.setZ(Integer.parseInt(args[4]));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Coordinates must be valid integers.");
                return;
            }
        }

        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "INSERT INTO warps (name, x, y, z, world) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                stmt.setInt(2, (int) Math.round(location.getX()));
                stmt.setInt(3, (int) Math.round(location.getY()));
                stmt.setInt(4, (int) Math.round(location.getZ()));
                stmt.setString(5, location.getWorld().getName());
                stmt.executeUpdate();
            }

            player.sendMessage(ChatColor.GRAY + "Warp " + ChatColor.DARK_AQUA + name + ChatColor.GRAY 
                + " created at " + ChatColor.DARK_GREEN + location.toVector() + ChatColor.GRAY + " in world "
                + ChatColor.DARK_AQUA + location.getWorld().getName() + ChatColor.GRAY + ".");
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Failed to create warp: " + e.getMessage());
        }
    }

    /**
     * Removes a warp.
     *
     * @param player The player who executed the command.
     * @param args The command arguments.
     * @return void
     */
    private void removeWarp(Player player, String[] args) {
        if (!player.hasPermission("warp.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.AQUA + "/warp remove [name]");
            return;
        }

        String name = args[1];
        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "DELETE FROM warps WHERE name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                int rows = stmt.executeUpdate();
                player.sendMessage(
                        rows > 0 
                        ? ChatColor.GRAY + "Warp " + ChatColor.DARK_AQUA + name + ChatColor.GRAY + " removed." 
                        : ChatColor.RED + "No warp found with name " + name + ".");
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Failed to remove warp: " + e.getMessage());
        }
    }

    /**
     * Edits a warp.
     * 
     * @param player The player who executed the command.
     * @param args The command arguments.
     * @return void
     */
    private void editWarp(Player player, String[] args) {
        if (!player.hasPermission("warp.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.AQUA + "/warp edit [name] {[x] [y] [z]}");
            return;
        }

        String name = args[1];
        Location location = player.getLocation();

        if (args.length >= 5) {
            try {
                location.setX(Integer.parseInt(args[2]));
                location.setY(Integer.parseInt(args[3]));
                location.setZ(Integer.parseInt(args[4]));
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Coordinates must be valid numbers.");
                return;
            }
        }

        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "UPDATE warps SET x = ?, y = ?, z = ? WHERE name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, (int) Math.round(location.getX()));
                stmt.setInt(2, (int) Math.round(location.getY()));
                stmt.setInt(3, (int) Math.round(location.getZ()));
                stmt.setString(4, name);

                int rows = stmt.executeUpdate();
                player.sendMessage(
                        rows > 0 
                        ? ChatColor.GRAY + "Warp " + ChatColor.DARK_AQUA + name + ChatColor.GRAY + " updated to " 
                            + ChatColor.DARK_GREEN + location.toVector() + ChatColor.GRAY + "." 
                        : ChatColor.RED + "No warp found with name " + name + ".");
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Failed to edit warp: " + e.getMessage());
        }
    }

    /**
     * Teleports a player to a warp.
     *
     * @param player The player who executed the command.
     * @param args The command arguments.
     * @return void
     */
    private void teleport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: " + ChatColor.AQUA + "/warp teleport [name]");
            return;
        }

        String name = args[1];
        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "SELECT x, y, z, world FROM warps WHERE name = ?";
            teleportToWarp(player, name, connection, sql);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Failed to teleport: " + e.getMessage());
        }
    }

    /**
     * Teleports a player to a warp.
     *
     * @param player The player who executed the command.
     * @param name The name of the warp to teleport to.
     * @param connection The database connection.
     * @param sql The SQL query to execute.
     * @return void
     * @throws SQLException
     */
    private void teleportToWarp(Player player, String name, Connection connection, String sql) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                player.sendMessage(ChatColor.RED + "No warp found with name " + ChatColor.DARK_AQUA + name + ChatColor.RED + ".");
                return;
            }

            List<String> axes = Arrays.asList("x", "y", "z");
            HashMap<String, Double> loc = new HashMap<>();

            for (String axis : axes) {
                loc.put(axis, rs.getDouble(axis));

            }

            String worldName = rs.getString("world");
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                player.sendMessage(ChatColor.RED + "Warp " + ChatColor.DARK_AQUA + name + ChatColor.RED 
                    + " references an unknown world " + ChatColor.DARK_PURPLE + worldName + ChatColor.RED + ".");
                return;
            }

            player.teleport(new Location(world, loc.get("x"), loc.get("y"), loc.get("z")));
            player.sendMessage(ChatColor.GRAY + "Teleported to warp " + ChatColor.DARK_AQUA + name + ChatColor.GRAY + ".");
        }
    }

}
