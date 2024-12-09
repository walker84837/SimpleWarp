package org.winlogon.simplewarp;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Handles the connection to the SQLite 
 * database, which stores the warps.
 * 
 * @author walker84837
 */
public class DatabaseHandler {
    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Connects to the SQLite database.
     * 
     * @return void
     */
    public void connectToDatabase() throws SQLException {
        String url = "jdbc:sqlite:" + plugin.getDataFolder() + "/warps.db";
        connection = DriverManager.getConnection(url);
        createTableIfNotExists();
    }

    /**
     * Creates the warps table if it doesn't exist.
     * 
     * @return void
     */
    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS warps (" +
                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                     "name TEXT UNIQUE NOT NULL, " +
                     "x REAL NOT NULL, " +
                     "y REAL NOT NULL, " +
                     "z REAL NOT NULL, " +
                     "world TEXT NOT NULL)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Retrieves a connection to the SQLite database.
     * 
     * @return Connection The connection to the database.
     * @throws SQLException
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connectToDatabase();
        }
        return connection;
    }

    /**
     * Closes the connection to the SQLite database.
     * 
     * @return void
     * @throws SQLException
     */
    public void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
