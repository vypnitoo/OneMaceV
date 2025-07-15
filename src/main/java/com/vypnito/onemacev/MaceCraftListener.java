package com.vypnito.onemacev;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

public class MaceCraftListener implements Listener {

	private final Onemacev plugin;

	public MaceCraftListener(Onemacev plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onCraftItem(CraftItemEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (player.hasPermission("onemacev.bypass")) return;

		if (event.getRecipe().getResult().getType() == plugin.getLimitedItemMaterial()) {
			event.setCancelled(true);

			if (plugin.isItemAlreadyCrafted()) {
				player.sendMessage(plugin.formatMessage("crafting_forbidden"));
			} else {
				CraftingInventory craftingInventory = event.getInventory();
				craftingInventory.clear();

				player.getInventory().addItem(new ItemStack(plugin.getLimitedItemMaterial(), 1));
				player.updateInventory();

				plugin.setItemCrafted(true, player);

				if (plugin.getConfig().getBoolean("effects.first_craft_firework", true)) {
					Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
					FireworkMeta fwm = fw.getFireworkMeta();
					fwm.addEffect(FireworkEffect.builder().withColor(Color.ORANGE).with(FireworkEffect.Type.BALL_LARGE).flicker(true).build());
					fwm.setPower(1);
					fw.setFireworkMeta(fwm);
				}

				if (plugin.getConfig().getBoolean("effects.first_craft_global_sound.enabled", true)) {
					try {
						String soundName = plugin.getConfig().getString("effects.first_craft_global_sound.sound");
						float volume = (float) plugin.getConfig().getDouble("effects.first_craft_global_sound.volume");
						float pitch = (float) plugin.getConfig().getDouble("effects.first_craft_global_sound.pitch");
						Sound sound = Sound.valueOf(soundName.toUpperCase());

						for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
							onlinePlayer.playSound(onlinePlayer.getLocation(), sound, volume, pitch);
						}
					} catch (Exception e) {
						plugin.getLogger().warning("Could not play global sound: " + e.getMessage());
					}
				}

				plugin.sendGlobalAnnouncement(plugin.formatMessage("first_craft_announcement", "<player>", player.getName()));
				plugin.sendGlobalAnnouncement(plugin.formatMessage("crafting_disabled_announcement"));
			}
		}
	}

	@EventHandler
	public void onPrepareItemCraft(PrepareItemCraftEvent event) { /* ... */ }

	@EventHandler
	public void onCrafterCraft(CrafterCraftEvent event) { /* ... */ }
}