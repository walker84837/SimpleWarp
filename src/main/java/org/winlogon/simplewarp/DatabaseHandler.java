package org.winlogon.simplewarp;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseHandler {
    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connectToDatabase() throws SQLException {
        String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/warps.db";
        connection = DriverManager.getConnection(url);
        createTableIfNotExists();
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS warps (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                     "name TEXT UNIQUE NOT NULL, " +
                     "x INTEGER NOT NULL, " +
                     "y INTEGER NOT NULL, " +
                     "z INTEGER NOT NULL, " +
                     "world TEXT NOT NULL)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connectToDatabase();
        }
        return connection;
    }

    public void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
