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
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("Usage: /warp <new|remove|edit|teleport> [arguments]");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "new" -> handleNewWarp(player, args);
            case "remove" -> handleRemoveWarp(player, args);
            case "edit" -> handleEditWarp(player, args);
            case "teleport" -> handleTeleport(player, args);
            default -> player.sendMessage("Invalid subcommand. Use: /warp <new|remove|edit|teleport>");
        }

        return true;
    }

    private void handleNewWarp(Player player, String[] args) {
        if (!player.hasPermission("warp.admin")) {
            player.sendMessage("You do not have permission to use this command.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("Usage: /warp new [name] {[x] [y] [z]}");
            return;
        }

        String name = args[1];
        Location location = player.getLocation();

        if (args.length >= 5) {
            try {
                location.setX(Double.parseDouble(args[2]));
                location.setY(Double.parseDouble(args[3]));
                location.setZ(Double.parseDouble(args[4]));
            } catch (NumberFormatException e) {
                player.sendMessage("Coordinates must be valid numbers.");
                return;
            }
        }

        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "INSERT INTO warps (name, x, y, z, world) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                stmt.setDouble(2, location.getX());
                stmt.setDouble(3, location.getY());
                stmt.setDouble(4, location.getZ());
                stmt.setString(5, location.getWorld().getName());
                stmt.executeUpdate();
            }
            player.sendMessage("Warp '" + name + "' created at " + location.toVector() + " in world '" + location.getWorld().getName() + "'.");
        } catch (SQLException e) {
            player.sendMessage("Failed to create warp: " + e.getMessage());
        }
    }

    private void handleRemoveWarp(Player player, String[] args) {
        if (!player.hasPermission("warp.admin")) {
            player.sendMessage("You do not have permission to use this command.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("Usage: /warp remove [name]");
            return;
        }

        String name = args[1];
        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "DELETE FROM warps WHERE name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                int rows = stmt.executeUpdate();
                player.sendMessage(rows > 0 ? "Warp '" + name + "' removed." : "No warp found with name '" + name + "'.");
            }
        } catch (SQLException e) {
            player.sendMessage("Failed to remove warp: " + e.getMessage());
        }
    }

    private void handleEditWarp(Player player, String[] args) {
        if (!player.hasPermission("warp.admin")) {
            player.sendMessage("You do not have permission to use this command.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("Usage: /warp edit [name] {[x] [y] [z]}");
            return;
        }

        String name = args[1];
        Location location = player.getLocation();

        if (args.length >= 5) {
            try {
                location.setX(Double.parseDouble(args[2]));
                location.setY(Double.parseDouble(args[3]));
                location.setZ(Double.parseDouble(args[4]));
            } catch (NumberFormatException e) {
                player.sendMessage("Coordinates must be valid numbers.");
                return;
            }
        }

        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "UPDATE warps SET x = ?, y = ?, z = ? WHERE name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setDouble(1, location.getX());
                stmt.setDouble(2, location.getY());
                stmt.setDouble(3, location.getZ());
                stmt.setString(4, name);
                int rows = stmt.executeUpdate();
                player.sendMessage(rows > 0 ? "Warp '" + name + "' updated to " + location.toVector() + "." : "No warp found with name '" + name + "'.");
            }
        } catch (SQLException e) {
            player.sendMessage("Failed to edit warp: " + e.getMessage());
        }
    }

    private void handleTeleport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /warp teleport [name]");
            return;
        }

        String name = args[1];
        try (Connection connection = databaseHandler.getConnection()) {
            String sql = "SELECT x, y, z, world FROM warps WHERE name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        player.teleport(new Location(world, x, y, z));
                        player.sendMessage("Teleported to warp '" + name + "'.");
                    } else {
                        player.sendMessage("Warp '" + name + "' references an unknown world '" + worldName + "'.");
                    }
                } else {
                    player.sendMessage("No warp found with name '" + name + "'.");
                }
            }
        } catch (SQLException e) {
            player.sendMessage("Failed to teleport: " + e.getMessage());
        }
    }
}