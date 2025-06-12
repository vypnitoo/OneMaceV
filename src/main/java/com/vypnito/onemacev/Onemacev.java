package com.vypnito.onemacev;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.List;
import java.util.Objects;

public final class Onemacev extends JavaPlugin implements CommandExecutor {

	private boolean maceAlreadyCrafted = false;

	private String firstMaceAnnouncement;
	private String disabledMaceRecipesMessage;
	private String forbiddenMaceCraftMessage;
	private String forbiddenIngredientMessage;
	private String maceRemovedMessage;
	private String defaultMaceSound;
	private float defaultMaceSoundVolume;
	private float defaultMaceSoundPitch;
	private List<String> bannedIngredients;

	@Override
	public void onEnable() {
		getLogger().info("Onemacev plugin is enabling!");

		saveDefaultConfig();
		loadPluginConfiguration(); // Load custom configuration

		getServer().getPluginManager().registerEvents(new MaceCraftListener(this), this);

		Objects.requireNonNull(getCommand("onemacev")).setExecutor(this);

		if (maceAlreadyCrafted) {
			getLogger().info("Mace has been crafted before. Further crafting is disabled.");
		} else {
			getLogger().info("Mace has NOT been crafted yet. First craft is allowed.");
		}
	}

	@Override
	public void onDisable() {
		getLogger().info("Onemacev plugin is disabling!");
		saveMaceCraftedState();
	}

	public void loadPluginConfiguration() {
		FileConfiguration config = getConfig();

		this.maceAlreadyCrafted = config.getBoolean("mace_already_crafted", false);
		this.firstMaceAnnouncement = config.getString("messages.first_mace_announcement", "<gold><bold>CONGRATULATIONS!</bold> <green><player></green><gold> has just crafted the FIRST MACE!</gold>");
		this.disabledMaceRecipesMessage = config.getString("messages.disabled_mace_recipes", "<red>All further Mace crafting recipes are now disabled!</red>");
		this.forbiddenMaceCraftMessage = config.getString("messages.forbidden_mace_craft", "<red>The Mace has already been crafted once. Further crafting is forbidden!</red>");
		this.forbiddenIngredientMessage = config.getString("messages.forbidden_ingredient", "<red>You cannot place <item> here. Mace crafting is forbidden!</red>");
		this.maceRemovedMessage = config.getString("messages.mace_removed", "<red>A Mace appeared and was removed. Crafting is forbidden!</red>");

		this.defaultMaceSound = config.getString("sound.type", "ENTITY_PLAYER_LEVELUP");
		this.defaultMaceSoundVolume = (float) config.getDouble("sound.volume", 1.0);
		this.defaultMaceSoundPitch = (float) config.getDouble("sound.pitch", 1.0);

		this.bannedIngredients = config.getStringList("banned_ingredients");
		if (this.bannedIngredients == null || this.bannedIngredients.isEmpty()) {
			this.bannedIngredients = List.of("HEAVY_CORE", "BREEZE_ROD");
		}

		getLogger().info("Plugin configuration reloaded.");
	}

	public boolean hasMaceAlreadyBeenCrafted() {
		return maceAlreadyCrafted;
	}

	public void setMaceAlreadyCrafted(boolean status) {
		this.maceAlreadyCrafted = status;
		saveMaceCraftedState();
	}

	private void saveMaceCraftedState() {
		FileConfiguration config = getConfig();
		config.set("mace_already_crafted", maceAlreadyCrafted);
		saveConfig();
		getLogger().info("'mace_already_crafted' state saved: " + maceAlreadyCrafted);
	}

	public void sendGlobalAnnouncement(Component message) {
		getServer().broadcast(message);
	}

	public String getFirstMaceAnnouncement() { return firstMaceAnnouncement; }
	public String getDisabledMaceRecipesMessage() { return disabledMaceRecipesMessage; }
	public String getForbiddenMaceCraftMessage() { return forbiddenMaceCraftMessage; }
	public String getForbiddenIngredientMessage() { return forbiddenIngredientMessage; }
	public String getMaceRemovedMessage() { return maceRemovedMessage; }
	public String getDefaultMaceSound() { return defaultMaceSound; }
	public float getDefaultMaceSoundVolume() { return defaultMaceSoundVolume; }
	public float getDefaultMaceSoundPitch() { return defaultMaceSoundPitch; }
	public List<String> getBannedIngredients() { return bannedIngredients; }

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("onemacev")) {
			return false;
		}

		if (args.length == 1) {
			if (args[0].equalsIgnoreCase("info")) {
				if (sender.hasPermission("onemacev.info")) {
					NamedTextColor statusColor = hasMaceAlreadyBeenCrafted() ? NamedTextColor.RED : NamedTextColor.GREEN;
					String statusText = hasMaceAlreadyBeenCrafted() ? "YES" : "NO";

					sender.sendMessage(Component.text("--- Onemacev Plugin Info ---", NamedTextColor.GOLD));
					sender.sendMessage(Component.text("Has the Mace been crafted yet? ", NamedTextColor.YELLOW)
							.append(Component.text(statusText, statusColor)));
					sender.sendMessage(Component.text("Version: " + getDescription().getVersion(), NamedTextColor.GRAY));
				} else {
					sender.sendMessage(Component.text("You don't have permission to view plugin info.", NamedTextColor.RED));
				}
				return true;
			} else if (args[0].equalsIgnoreCase("reload")) {
				if (sender.hasPermission("onemacev.reload")) {
					loadPluginConfiguration();
					sender.sendMessage(Component.text("Onemacev configuration reloaded.", NamedTextColor.GREEN));
				} else {
					sender.sendMessage(Component.text("You don't have permission to reload the plugin.", NamedTextColor.RED));
				}
				return true;
			} else if (args[0].equalsIgnoreCase("reset")) {
				if (sender.hasPermission("onemacev.reset")) {
					setMaceAlreadyCrafted(false);
					sender.sendMessage(Component.text("Mace crafted status has been reset to false.", NamedTextColor.GREEN));
					getLogger().info("Mace crafted status manually reset by " + sender.getName());
				} else {
					sender.sendMessage(Component.text("You don't have permission to reset the Mace status.", NamedTextColor.RED));
				}
				return true;
			}
		}

		sender.sendMessage(Component.text("Usage: /onemacev <info|reload|reset>", NamedTextColor.RED));
		return false;
	}
}