package com.vypnito.onemacev;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class OnemacevTabCompleter implements TabCompleter {

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
		if (args.length == 1) {
			List<String> completions = new ArrayList<>();

			if (sender.hasPermission("onemacev.info")) completions.add("info");
			if (sender.hasPermission("onemacev.who")) completions.add("who");
			if (sender.hasPermission("onemacev.reload")) completions.add("reload");
			if (sender.hasPermission("onemacev.reset")) completions.add("reset");

			return completions.stream()
					.filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
					.collect(Collectors.toList());
		}
		return new ArrayList<>();
	}
}