package org.winlogon.simplewarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WarpPlugin extends JavaPlugin {

    private Connection connection;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            connectToDatabase();
            createTableIfNotExists();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
        }

        PluginCommand warpCommand = getCommand("warp");
        if (warpCommand != null) {
            warpCommand.setExecutor(this::onCommand);
            warpCommand.setTabCompleter(this::onTabComplete);
        }
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                getLogger().severe("Failed to close database: " + e.getMessage());
            }
        }
    }

    private void connectToDatabase() throws SQLException {
        String url = "jdbc:sqlite:" + getDataFolder() + "/warps.db";
        connection = DriverManager.getConnection(url);
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS warps (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                     "name TEXT UNIQUE NOT NULL, " +
                     "x DOUBLE NOT NULL, " +
                     "y DOUBLE NOT NULL, " +
                     "z DOUBLE NOT NULL, " +
                     "world TEXT NOT NULL)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    private boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();
        String world = player.getWorld().getName();

        if (args.length >= 5) {
            try {
                x = Double.parseDouble(args[2]);
                y = Double.parseDouble(args[3]);
                z = Double.parseDouble(args[4]);
            } catch (NumberFormatException e) {
                player.sendMessage("Coordinates must be valid numbers.");
                return;
            }
        }

        try {
            String sql = "INSERT INTO warps (name, x, y, z, world) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                stmt.setDouble(2, x);
                stmt.setDouble(3, y);
                stmt.setDouble(4, z);
                stmt.setString(5, world);
                stmt.executeUpdate();
            }
            player.sendMessage("Warp '" + name + "' created at " + x + ", " + y + ", " + z + " in world '" + world + "'.");
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
        try {
            String sql = "DELETE FROM warps WHERE name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, name);
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    player.sendMessage("Warp '" + name + "' removed.");
                } else {
                    player.sendMessage("No warp found with name '" + name + "'.");
                }
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
        double x = player.getLocation().getX();
        double y = player.getLocation().getY();
        double z = player.getLocation().getZ();

        if (args.length >= 5) {
            try {
                x = Double.parseDouble(args[2]);
                y = Double.parseDouble(args[3]);
                z = Double.parseDouble(args[4]);
            } catch (NumberFormatException e) {
                player.sendMessage("Coordinates must be valid numbers.");
                return;
            }
        }

        try {
            String sql = "UPDATE warps SET x = ?, y = ?, z = ? WHERE name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setDouble(1, x);
                stmt.setDouble(2, y);
                stmt.setDouble(3, z);
                stmt.setString(4, name);
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    player.sendMessage("Warp '" + name + "' updated to " + x + ", " + y + ", " + z + ".");
                } else {
                    player.sendMessage("No warp found with name '" + name + "'.");
                }
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
        try {
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

    private List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("new");
            suggestions.add("remove");
            suggestions.add("edit");
            suggestions.add("teleport");
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("new")) {
            try {
                String sql = "SELECT name FROM warps";
                try (Statement stmt = connection.createStatement()) {
                    ResultSet rs = stmt.executeQuery(sql);
                    while (rs.next()) {
                        suggestions.add(rs.getString("name"));
                    }
                }
            } catch (SQLException e) {
                getLogger().severe("Failed to fetch warp names for tab completion: " + e.getMessage());
            }
        }
        return suggestions;
    }
}
