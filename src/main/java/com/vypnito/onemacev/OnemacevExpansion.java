package com.vypnito.onemacev;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import java.text.SimpleDateFormat;
import java.util.Date;

public class OnemacevExpansion extends PlaceholderExpansion {

	private final Onemacev plugin;

	public OnemacevExpansion(Onemacev plugin) {
		this.plugin = plugin;
	}

	@Override
	public @NotNull String getIdentifier() { return "onemacev"; }

	@Override
	public @NotNull String getAuthor() { return "vypnito"; }

	@Override
	public @NotNull String getVersion() { return "1.4"; }

	@Override
	public boolean persist() { return true; }

	@Override
	public String onRequest(OfflinePlayer player, @NotNull String params) {
		if (params.equalsIgnoreCase("is_crafted")) {
			return plugin.isItemAlreadyCrafted() ? "Yes" : "No";
		}

		if (params.equalsIgnoreCase("first_crafter")) {
			return plugin.getFirstCrafterName();
		}

		if (params.equalsIgnoreCase("craft_date")) {
			if (plugin.isItemAlreadyCrafted()) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				return sdf.format(new Date(plugin.getFirstCraftTimestamp()));
			} else {
				return "N/A";
			}
		}

		return null;
	}
}