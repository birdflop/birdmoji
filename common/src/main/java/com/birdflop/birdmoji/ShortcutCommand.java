package com.birdflop.birdmoji;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Discord's /shrug, /tableflip and /unflip, which send the message with the face added to the end.
 * The \ in the shrug stops Discord from reading _(ツ)_ as italics, the same as Discord's own /shrug.
 */
public final class ShortcutCommand implements TabExecutor {

    public static final Map<String, String> SHORTCUTS = Map.of(
            "shrug", "¯\\_(ツ)_/¯",
            "tableflip", "(╯°□°)╯︵ ┻━┻",
            "unflip", "┬─┬ノ( º _ ºノ)"
    );

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
            return true;
        }

        String face = SHORTCUTS.get(command.getName());
        String message = String.join(" ", args).trim();
        // Sent as chat so chat plugins, DiscordSRV and :emoji: all see it like a normal message
        player.chat(message.isEmpty() ? face : message + " " + face);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        return List.of();
    }
}
