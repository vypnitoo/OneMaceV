package com.vypnito.onemacev;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {

	private final Onemacev plugin;
	private Connection connection;

	public DatabaseManager(Onemacev plugin) {
		this.plugin = plugin;
	}

	public void connect() throws SQLException {
		File dataFolder = new File(plugin.getDataFolder(), "onemacev.db");
		if (!dataFolder.exists()) {
			try {
				if (!dataFolder.getParentFile().exists()) {
					dataFolder.getParentFile().mkdirs();
				}
				dataFolder.createNewFile();
			} catch (IOException e) {
				plugin.getLogger().log(Level.SEVERE, "Could not create database file!", e);
			}
		}

		try {
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
			plugin.getLogger().info("Successfully connected to the database.");
		} catch (SQLException | ClassNotFoundException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not connect to the database!", e);
		}
	}

	public void createTable() {
		String sql = "CREATE TABLE IF NOT EXISTS item_status (" +
				"item_key TEXT PRIMARY KEY," +
				"is_crafted BOOLEAN NOT NULL," +
				"crafter_name TEXT," +
				"craft_timestamp BIGINT" +
				");";
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not create database table!", e);
		}
	}

	public void loadStatus() {
		String itemKey = plugin.getLimitedItemName();
		String sql = "SELECT * FROM item_status WHERE item_key = ?";

		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setString(1, itemKey);
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				plugin.internalSetCraftedStatus(rs.getBoolean("is_crafted"));
				plugin.internalSetCrafterName(rs.getString("crafter_name"));
				plugin.internalSetTimestamp(rs.getLong("craft_timestamp"));
			} else {
				plugin.getLogger().info("No record found for '" + itemKey + "'. Creating a new one.");
				saveStatus(false, "N/A", 0);
				plugin.internalSetCraftedStatus(false);
				plugin.internalSetCrafterName("N/A");
				plugin.internalSetTimestamp(0);
			}
		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not load status from database!", e);
		}
	}

	public void saveStatus(boolean isCrafted, String crafterName, long timestamp) {
		String itemKey = plugin.getLimitedItemName();
		String sql = "INSERT OR REPLACE INTO item_status (item_key, is_crafted, crafter_name, craft_timestamp) VALUES (?, ?, ?, ?)";

		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setString(1, itemKey);
			ps.setBoolean(2, isCrafted);
			ps.setString(3, crafterName);
			ps.setLong(4, timestamp);
			ps.executeUpdate();
		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save status to database!", e);
		}
	}

	public void closeConnection() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (SQLException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not close database connection!", e);
		}
	}
}