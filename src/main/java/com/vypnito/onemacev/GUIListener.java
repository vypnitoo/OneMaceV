package com.vypnito.onemacev;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {

	private final Onemacev plugin;

	public GUIListener(Onemacev plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		String guiTitle = plugin.formatString(plugin.getConfig().getString("gui.title"));

		if (!event.getView().getTitle().equals(guiTitle)) {
			return;
		}

		event.setCancelled(true);

		ItemStack clickedItem = event.getCurrentItem();
		if (clickedItem == null || clickedItem.getType() == Material.AIR) {
			return;
		}

		Player player = (Player) event.getWhoClicked();

		if (clickedItem.getType() == Material.BARRIER) {
			player.performCommand("onemacev reset");
			player.closeInventory();
		} else if (clickedItem.getType() == Material.KNOWLEDGE_BOOK) {
			player.performCommand("onemacev reload");
			player.closeInventory();
		}
	}
}