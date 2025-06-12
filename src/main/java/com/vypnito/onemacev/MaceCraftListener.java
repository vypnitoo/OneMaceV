package com.vypnito.onemacev;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import org.bukkit.event.block.CrafterCraftEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Objects;

public class MaceCraftListener implements Listener {

	private final Onemacev plugin;
	private final MiniMessage miniMessage = MiniMessage.miniMessage();

	public MaceCraftListener(Onemacev plugin) {
		this.plugin = plugin;
	}

	/**
	 * Handles the CraftItemEvent, which fires when a player crafts an item.
	 * This is the primary point for allowing exactly one Mace craft.
	 *
	 * @param event The CraftItemEvent.
	 */
	@EventHandler
	public void onCraftItem(CraftItemEvent event) {
		// Only proceed if a player is involved in the craft.
		if (!(event.getWhoClicked() instanceof Player player)) {
			plugin.getLogger().warning("Non-player entity attempted to craft Mace. Blocking by default.");
			event.setCancelled(true); // Block non-player entities from crafting Mace
			return;
		}

		// --- PERMISSION BYPASS CHECK ---
		if (player.hasPermission("onemacev.bypass")) {
			// Allow players with bypass permission to craft as normal.
			plugin.getLogger().info("Player " + player.getName() + " with bypass permission crafted Mace.");
			event.setCancelled(false); // Do not cancel the event for bypass players
			return;
		}
		// --- END BYPASS CHECK ---

		// Check if the item being crafted is a Mace.
		if (event.getRecipe().getResult().getType() == Material.MACE) {
			// Always cancel the event first to take full control for non-bypass players.
			event.setCancelled(true);

			if (plugin.hasMaceAlreadyBeenCrafted()) {
				// If the Mace has already been crafted before, inform and block.
				player.sendMessage(miniMessage.deserialize(plugin.getForbiddenMaceCraftMessage()));
				plugin.getLogger().info("Attempt to craft Mace blocked as it has already been crafted by player (CraftItemEvent).");
			} else {
				// This is the first attempt to craft a Mace - grant one and block further.
				// Remove ingredients from the crafting grid.
				CraftingInventory craftingInventory = event.getInventory();
				for (ItemStack ingredient : craftingInventory.getMatrix()) {
					if (ingredient != null) {
						ingredient.setAmount(0);
					}
				}
				craftingInventory.setResult(null); // Clear the result slot in the UI.

				// Give the player EXACTLY ONE Mace.
				player.getInventory().addItem(new ItemStack(Material.MACE, 1));
				player.updateInventory(); // Immediately update the player's inventory on the client.

				// Set the plugin flag and announce the first successful craft.
				plugin.setMaceAlreadyCrafted(true);
				plugin.getLogger().info("First Mace delivered to " + player.getName() + ". Flag set.");

				// Send global announcements and play sound.
				plugin.sendGlobalAnnouncement(miniMessage.deserialize(plugin.getFirstMaceAnnouncement().replace("<player>", player.getName())));
				plugin.sendGlobalAnnouncement(miniMessage.deserialize(plugin.getDisabledMaceRecipesMessage()));

				try {
					player.playSound(player.getLocation(), Sound.valueOf(plugin.getDefaultMaceSound()), plugin.getDefaultMaceSoundVolume(), plugin.getDefaultMaceSoundPitch());
				} catch (IllegalArgumentException e) {
					plugin.getLogger().warning("Invalid sound specified in config: " + plugin.getDefaultMaceSound() + ". Using default.");
					player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
				}
			}
		}
	}

	/**
	 * Handles PrepareItemCraftEvent.
	 * Prevents the Mace recipe from being visually displayed/functional in UI
	 * after the first Mace has been crafted. Works for both Workbenches and Crafter UIs.
	 *
	 * @param event The PrepareItemCraftEvent.
	 */
	@EventHandler
	public void onPrepareItemCraft(PrepareItemCraftEvent event) {
		// If a player is viewing this inventory.
		if (event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player player) {
			// --- PERMISSION BYPASS CHECK ---
			if (player.hasPermission("onemacev.bypass")) {
				return; // Allow bypass players to see/use recipes as normal.
			}
			// --- END BYPASS CHECK ---
		}

		// Only block if Mace has already been crafted.
		if (plugin.hasMaceAlreadyBeenCrafted()) {
			// Check if the recipe result is a Mace.
			if (event.getRecipe() != null && event.getRecipe().getResult().getType() == Material.MACE) {
				if (event.getInventory().getType() == InventoryType.WORKBENCH || event.getInventory().getType() == InventoryType.CRAFTER) {
					event.getInventory().setResult(new ItemStack(Material.AIR)); // Clear the visual result.

					// Send message to player if using a workbench.
					if (event.getInventory().getType() == InventoryType.WORKBENCH && event.getViewers().size() > 0 && event.getViewers().get(0) instanceof Player player) {
						player.sendMessage(miniMessage.deserialize(plugin.getForbiddenMaceCraftMessage()));
					}
					plugin.getLogger().info("Mace crafting recipe visually prevented in " + event.getInventory().getType().name() + " (PrepareItemCraftEvent).");
				}
			}
		}
	}

	/**
	 * Handles CrafterCraftEvent (PaperMC specific).
	 * This is the primary and most reliable place to block crafting in Autocrafters.
	 *
	 * @param event The CrafterCraftEvent.
	 */
	@EventHandler
	public void onCrafterCraft(CrafterCraftEvent event) {
		// Check if a player is involved (indirectly, if triggered by a player circuit).
		// This is tricky, but often these events are tied to player actions/logins for permissions.
		// For this specific event, we assume non-player entities (hoppers, etc.) are always blocked if the flag is set.

		// Block if Mace has already been crafted AND the recipe result is a Mace.
		if (plugin.hasMaceAlreadyBeenCrafted() && event.getRecipe().getResult().getType() == Material.MACE) {
			// If a player is somehow associated with this craft event (e.g., through a redstone trigger by player),
			// check for bypass permission.
			// This check is a bit generalized as CrafterCraftEvent doesn't directly have a 'player'.
			// For now, it will block even if an OP player sets up an autocrafter.
			// If OP players need to bypass autocrafter, more complex logic is needed (e.g., check nearby players or block owner).
			// For now, we block all autocrafter crafts if the flag is set.

			event.setCancelled(true); // Stop the Autocrafter from crafting!
			plugin.getLogger().info("Mace crafting by Autocrafter prevented at " + event.getBlock().getLocation() + " (CrafterCraftEvent).");
		}
	}

	/**
	 * Handles InventoryClickEvent.
	 * Blocks:
	 * 1. Taking a Mace from result slots (crafting table, autocrafter).
	 * 2. Inserting banned ingredients (Heavy Core/Breeze Rod) into crafting input slots.
	 *
	 * @param event The InventoryClickEvent.
	 */
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return; // Only process player clicks.

		// --- PERMISSION BYPASS CHECK ---
		if (player.hasPermission("onemacev.bypass")) {
			return; // Allow bypass players to interact as normal.
		}
		// --- END BYPASS CHECK ---

		// Block only if Mace has already been crafted.
		if (plugin.hasMaceAlreadyBeenCrafted()) {
			// 1. Prevent taking Mace from the result slot (slot 0 for crafting inventories).
			if ((event.getInventory().getType() == InventoryType.CRAFTER || event.getInventory().getType() == InventoryType.WORKBENCH) && event.getSlot() == 0) {
				// Check if the item in the result slot is a Mace.
				if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.MACE) {
					event.setCancelled(true); // Cancel the action of taking the Mace.
					player.sendMessage(miniMessage.deserialize(plugin.getForbiddenMaceCraftMessage()));
					plugin.getLogger().info("Player blocked from taking Mace from result slot (InventoryClickEvent).");
				}
			}

			// 2. Prevent placing banned ingredients (Heavy Core/Breeze Rod) into crafting input slots.
			// Covers both direct clicks and shift-clicks into crafting grids.
			if (event.getClickedInventory() != null &&
					(event.getClickedInventory().getType() == InventoryType.WORKBENCH || event.getClickedInventory().getType() == InventoryType.CRAFTER)) {

				// Target slot is a crafting input slot (slots 1-9).
				if (event.getSlotType() == InventoryType.SlotType.CRAFTING || (event.getSlot() >= 1 && event.getSlot() <= 9)) {
					ItemStack cursorItem = event.getCursor(); // Item held by cursor.
					ItemStack clickedItem = event.getCurrentItem(); // Item in the clicked slot.

					boolean isBannedIngredientBeingMoved = false;

					// Scenario 1: Player attempts to place a banned ingredient (holding it on cursor).
					if (cursorItem != null && plugin.getBannedIngredients().contains(cursorItem.getType().name())) {
						isBannedIngredientBeingMoved = true;
					}
					// Scenario 2: Player shift-clicks a banned ingredient from THEIR INVENTORY to move it to the crafting grid.
					else if (event.isShiftClick() && clickedItem != null && plugin.getBannedIngredients().contains(clickedItem.getType().name())) {
						// Ensure the item is from the player's inventory (not from the crafting grid itself).
						if (event.getClickedInventory().equals(player.getInventory())) {
							isBannedIngredientBeingMoved = true;
						}
					}

					if (isBannedIngredientBeingMoved) {
						event.setCancelled(true); // Cancel the action.
						String itemName = Objects.requireNonNull(cursorItem != null ? cursorItem : clickedItem).getType().name().replace("_", " ");
						player.sendMessage(miniMessage.deserialize(plugin.getForbiddenIngredientMessage().replace("<item>", itemName)));
						plugin.getLogger().info("Blocked player from placing banned ingredient into crafting grid via click/shift-click.");
						return;
					}
				}
			}
		}
	}

	/**
	 * Handles InventoryMoveItemEvent.
	 * Prevents automated systems (hoppers, droppers) from moving banned ingredients
	 * into crafting inventories.
	 *
	 * @param event The InventoryMoveItemEvent.
	 */
	@EventHandler
	public void onInventoryMoveItem(InventoryMoveItemEvent event) {
		// --- PERMISSION BYPASS CHECK (Difficult for automated systems without player context) ---
		// This event doesn't directly involve a player for permission checks,
		// so automated systems will always be blocked if the flag is set.
		// If an OP player sets up an autocrafter, it will still be blocked by this.
		// For now, we block all automated ingredient movement if the flag is set.
		// --- END BYPASS CHECK ---

		// Block only if Mace has already been crafted.
		if (plugin.hasMaceAlreadyBeenCrafted()) {
			// Check if the destination is a crafting inventory (Workbench or Crafter).
			if (event.getDestination().getType() == InventoryType.WORKBENCH || event.getDestination().getType() == InventoryType.CRAFTER) {
				// Check if the item being moved is a banned ingredient.
				if (plugin.getBannedIngredients().contains(event.getItem().getType().name())) {
					event.setCancelled(true); // Cancel the item movement.
					plugin.getLogger().info("Blocked automated movement of " + event.getItem().getType().name() + " into a crafting inventory (InventoryMoveItemEvent).");
				}
			}
		}
	}

	/**
	 * Handles PlayerDropItemEvent.
	 * Prevents players from directly dropping banned ingredients into crafting inventories.
	 * (e.g., throwing a Heavy Core into a Crafter).
	 *
	 * @param event The PlayerDropItemEvent.
	 */
	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		Player player = event.getPlayer();

		// --- PERMISSION BYPASS CHECK ---
		if (player.hasPermission("onemacev.bypass")) {
			return; // Allow bypass players to drop items as normal.
		}
		// --- END BYPASS CHECK ---

		if (plugin.hasMaceAlreadyBeenCrafted()) {
			// Check if the dropped item is a banned ingredient.
			if (plugin.getBannedIngredients().contains(event.getItemDrop().getItemStack().getType().name())) {
				// This event doesn't easily tell us where the item will land.
				// For simplicity, we cancel the drop if the item is a banned ingredient and Mace is crafted.
				// This is a broad measure to prevent dropping into nearby crafters.
				event.setCancelled(true);
				player.sendMessage(miniMessage.deserialize(plugin.getForbiddenIngredientMessage().replace("<item>", event.getItemDrop().getItemStack().getType().name().replace("_", " "))));
				plugin.getLogger().info("Blocked player " + player.getName() + " from dropping banned ingredient: " + event.getItemDrop().getItemStack().getType().name());
			}
		}
	}


	/**
	 * Handles EntityPickupItemEvent.
	 * Prevents players from picking up Mace items if they somehow appear on the ground.
	 * This is a last resort cleanup.
	 *
	 * @param event The EntityPickupItemEvent.
	 */
	@EventHandler
	public void onEntityPickupItem(EntityPickupItemEvent event) {
		// Block pickup only if Mace has already been crafted.
		if (plugin.hasMaceAlreadyBeenCrafted()) {
			// If the entity picking up is a player and the item is a Mace.
			if (event.getEntity() instanceof Player player) {
				// --- PERMISSION BYPASS CHECK ---
				if (player.hasPermission("onemacev.bypass")) {
					return; // Allow bypass players to pick up items as normal.
				}
				// --- END BYPASS CHECK ---

				if (event.getItem().getItemStack().getType() == Material.MACE) {
					event.setCancelled(true); // Prevent player from picking up the Mace.
					event.getItem().remove(); // Remove the Mace from the ground.
					player.sendMessage(miniMessage.deserialize(plugin.getMaceRemovedMessage()));
					plugin.getLogger().info("Mace picked up by player " + player.getName() + " was removed.");
				}
			}
		}
	}
}