package com.vypnito.onemacev;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URL;
import java.net.URLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class Onemacev extends JavaPlugin implements CommandExecutor, Listener {

	private DatabaseManager databaseManager;
	private boolean itemAlreadyCrafted;
	private String firstCrafterName;
	private long firstCraftTimestamp;
	private Material limitedItemMaterial;
	private List<String> bannedIngredients;
	private String limitedItemName;
	private boolean updateAvailable = false;
	private String latestVersion = "";

	@Override
	public void onEnable() {
		getLogger().info("Onemacev is enabling!");
		saveDefaultConfig();
		loadPluginConfiguration();

		databaseManager = new DatabaseManager(this);
		try {
			databaseManager.connect();
			databaseManager.createTable();
			databaseManager.loadStatus();
		} catch (SQLException e) {
			getLogger().log(Level.SEVERE, "DATABASE ERROR! Disabling plugin.", e);
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		getServer().getPluginManager().registerEvents(new MaceCraftListener(this), this);
		getServer().getPluginManager().registerEvents(new GUIListener(this), this);
		getServer().getPluginManager().registerEvents(this, this);

		Objects.requireNonNull(getCommand("onemacev")).setExecutor(this);
		Objects.requireNonNull(getCommand("onemacev")).setTabCompleter(new OnemacevTabCompleter());

		if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			new OnemacevExpansion(this).register();
			getLogger().info("Successfully hooked into PlaceholderAPI!");
		}
		checkForUpdates();
	}

	@Override
	public void onDisable() {
		databaseManager.closeConnection();
		getLogger().info("Onemacev has been disabled.");
	}

	public void loadPluginConfiguration() {
		this.reloadConfig();
		FileConfiguration config = getConfig();
		try {
			limitedItemMaterial = Material.valueOf(config.getString("settings.limited_item", "MACE").toUpperCase());
			limitedItemName = limitedItemMaterial.name().replace("_", " ").toLowerCase();
		} catch (IllegalArgumentException e) {
			getLogger().severe("Invalid material in 'settings.limited_item'. Defaulting to MACE.");
			limitedItemMaterial = Material.MACE;
			limitedItemName = "mace";
		}
		bannedIngredients = config.getStringList("settings.banned_ingredients");
	}

	public void setItemCrafted(boolean status, Player player) {
		internalSetCraftedStatus(status);
		if (status && player != null) {
			internalSetCrafterName(player.getName());
			internalSetTimestamp(System.currentTimeMillis());
		} else {
			internalSetCrafterName("N/A");
			internalSetTimestamp(0);
		}
		databaseManager.saveStatus(this.itemAlreadyCrafted, this.firstCrafterName, this.firstCraftTimestamp);
	}

	public void openStatusGUI(Player player) {
		String title = formatString(getConfig().getString("gui.title"));
		Inventory gui = Bukkit.createInventory(null, 27, title);

		ItemStack statusItem;
		if (isItemAlreadyCrafted()) {
			statusItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
			ItemMeta meta = statusItem.getItemMeta();
			meta.setDisplayName(formatString(getConfig().getString("gui.item_status.crafted.name")));
			List<String> lore = new ArrayList<>();
			for (String line : getConfig().getStringList("gui.item_status.crafted.lore")) {
				line = line.replace("%crafter%", getFirstCrafterName());
				line = line.replace("%date%", new SimpleDateFormat("yyyy-MM-dd").format(new Date(getFirstCraftTimestamp())));
				lore.add(formatString(line));
			}
			meta.setLore(lore);
			statusItem.setItemMeta(meta);
		} else {
			statusItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
			ItemMeta meta = statusItem.getItemMeta();
			meta.setDisplayName(formatString(getConfig().getString("gui.item_status.not_crafted.name")));
			meta.setLore(formatStringList(getConfig().getStringList("gui.item_status.not_crafted.lore")));
			statusItem.setItemMeta(meta);
		}

		ItemStack resetButton = new ItemStack(Material.BARRIER);
		ItemMeta resetMeta = resetButton.getItemMeta();
		resetMeta.setDisplayName(formatString(getConfig().getString("gui.reset_button.name")));
		resetMeta.setLore(formatStringList(getConfig().getStringList("gui.reset_button.lore")));
		resetButton.setItemMeta(resetMeta);

		ItemStack reloadButton = new ItemStack(Material.KNOWLEDGE_BOOK);
		ItemMeta reloadMeta = reloadButton.getItemMeta();
		reloadMeta.setDisplayName(formatString(getConfig().getString("gui.reload_button.name")));
		reloadMeta.setLore(formatStringList(getConfig().getStringList("gui.reload_button.lore")));
		reloadButton.setItemMeta(reloadMeta);

		gui.setItem(11, statusItem);
		gui.setItem(14, resetButton);
		gui.setItem(15, reloadButton);

		player.openInventory(gui);
	}

	public String formatString(String text) {
		return ChatColor.translateAlternateColorCodes('&', text);
	}

	public List<String> formatStringList(List<String> list) {
		List<String> formatted = new ArrayList<>();
		for (String s : list) {
			formatted.add(formatString(s));
		}
		return formatted;
	}

	public Component formatMessage(String configPath, String... placeholders) {
		String message = getConfig().getString("messages." + configPath, "&cMissing message: " + configPath);
		message = message.replace("<item>", this.limitedItemName);
		if (placeholders != null) {
			for (int i = 0; i < placeholders.length; i += 2) {
				if (i + 1 < placeholders.length) {
					message = message.replace(placeholders[i], placeholders[i + 1]);
				}
			}
		}
		return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
	}

	public void sendGlobalAnnouncement(Component message) {
		getServer().broadcast(message);
	}

	public boolean isItemAlreadyCrafted() { return itemAlreadyCrafted; }
	public Material getLimitedItemMaterial() { return limitedItemMaterial; }
	public List<String> getBannedIngredients() { return bannedIngredients; }
	public String getFirstCrafterName() { return firstCrafterName; }
	public long getFirstCraftTimestamp() { return firstCraftTimestamp; }
	public String getLimitedItemName() { return limitedItemName; }
	public void internalSetCraftedStatus(boolean status) { this.itemAlreadyCrafted = status; }
	public void internalSetCrafterName(String name) { this.firstCrafterName = name; }
	public void internalSetTimestamp(long time) { this.firstCraftTimestamp = time; }

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			if (sender instanceof Player p) {
				if (p.hasPermission("onemacev.gui")) {
					openStatusGUI(p);
				} else {
					p.sendMessage(formatMessage("command_no_permission"));
				}
				return true;
			} else {
				sender.sendMessage("This command can only be run by a player to open the GUI. Use /onemacev <subcommand> for console.");
				return true;
			}
		}

		String subCommand = args[0].toLowerCase();
		switch (subCommand) {
			case "reload":
				if (!sender.hasPermission("onemacev.reload")) {
					sender.sendMessage(formatMessage("command_no_permission"));
					return true;
				}
				loadPluginConfiguration();
				sender.sendMessage(formatMessage("command_reload_success"));
				return true;
			case "reset":
				if (!sender.hasPermission("onemacev.reset")) {
					sender.sendMessage(formatMessage("command_no_permission"));
					return true;
				}
				setItemCrafted(false, null);
				sender.sendMessage(formatMessage("command_reset_success"));
				getLogger().info("Limited item status has been manually reset by " + sender.getName());
				return true;
			case "who":
				return true;
			case "info":
				return true;
			default:
				sender.sendMessage(Component.text("Usage: /onemacev or /onemacev <reload|reset|who|info>", net.kyori.adventure.text.format.NamedTextColor.RED));
				return false;
		}
	}

	private void checkForUpdates() {
		Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
			try {
				int resourceId = 125996;
				URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
				URLConnection connection = url.openConnection();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
					String newVersion = reader.readLine();
					String currentVersion = this.getDescription().getVersion();
					if (isNewerVersionAvailable(currentVersion, newVersion)) {
						this.latestVersion = newVersion;
						this.updateAvailable = true;
						getLogger().info("A new update is available: " + newVersion + " (You are running " + currentVersion + ")");
					}
				}
			} catch (Exception e) {
				getLogger().warning("Could not check for updates: " + e.getMessage());
			}
		});
	}

	private boolean isNewerVersionAvailable(String currentVersion, String onlineVersion) {
		String current = currentVersion.replaceAll("[^0-9.]", "");
		String online = onlineVersion.replaceAll("[^0-9.]", "");
		if (current.equals(online)) return false;
		try {
			String[] currentParts = current.split("\\.");
			String[] onlineParts = online.split("\\.");
			int maxLength = Math.max(currentParts.length, onlineParts.length);
			for (int i = 0; i < maxLength; i++) {
				int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
				int onlinePart = i < onlineParts.length ? Integer.parseInt(onlineParts[i]) : 0;
				if (onlinePart > currentPart) return true;
				if (onlinePart < currentPart) return false;
			}
		} catch (NumberFormatException e) {
			getLogger().warning("Could not compare versions: " + e.getMessage());
			return false;
		}
		return false;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (player.hasPermission("onemacev.notify.update") && this.updateAvailable) {
			Bukkit.getScheduler().runTaskLater(this, () -> {
				player.sendMessage(formatMessage("update_available",
						"<new_version>", this.latestVersion,
						"<current_version>", this.getDescription().getVersion()));
			}, 60L);
		}
	}
}