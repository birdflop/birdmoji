package com.birdflop.birdmoji;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.IntStream;

/**
 * /emojis [page] - lists the emojis, clicking one puts it in the chat box.
 */
public final class EmojisCommand implements TabExecutor {

    private static final int PER_PAGE = 50;

    private final Birdmoji plugin;

    EmojisCommand(Birdmoji plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        List<Emoji> emojis = List.copyOf(plugin.registry().all());
        if (emojis.isEmpty()) {
            sender.sendMessage(Component.text("There are no emojis yet.", NamedTextColor.RED));
            return true;
        }

        int pages = (emojis.size() + PER_PAGE - 1) / PER_PAGE;
        int page = 1;
        if (args.length > 0) {
            try {
                page = Math.clamp(Integer.parseInt(args[0]), 1, pages);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        boolean sprites = sender instanceof Player player && plugin.packServer().hasPack(player);
        TextComponent.Builder list = Component.text();
        List<Emoji> pageEmojis = emojis.subList((page - 1) * PER_PAGE, Math.min(page * PER_PAGE, emojis.size()));
        for (int i = 0; i < pageEmojis.size(); i++) {
            if (i > 0) list.append(Component.text(sprites ? " " : ", ", NamedTextColor.DARK_GRAY));
            list.append(entry(pageEmojis.get(i), sprites));
        }

        sender.sendMessage(Component.text()
                .append(Component.text("Emojis", NamedTextColor.GOLD))
                .append(Component.text(" (" + emojis.size() + ")", NamedTextColor.GRAY))
                .append(Component.text(" - click one to add it to chat", NamedTextColor.DARK_GRAY)));
        sender.sendMessage(list.build());
        if (pages > 1) sender.sendMessage(pagination(label, page, pages));
        return true;
    }

    private Component entry(Emoji emoji, boolean sprite) {
        String name = ":" + emoji.alias() + ":";
        Component entry = sprite ? plugin.sprite(emoji) : Component.text(name, NamedTextColor.WHITE);
        return entry
                .hoverEvent(HoverEvent.showText(Component.text()
                        .append(Component.text(name, NamedTextColor.WHITE))
                        .appendNewline()
                        .append(Component.text("Click to put in chat", NamedTextColor.GRAY))
                        .appendNewline()
                        .append(Component.text("Shift-click to insert", NamedTextColor.GRAY))))
                .clickEvent(ClickEvent.suggestCommand(name))
                // Shift-click inserts at the cursor instead of replacing what's typed
                .insertion(name);
    }

    private Component pagination(String label, int page, int pages) {
        return Component.text()
                .append(button("« Previous", label, page - 1, page > 1))
                .append(Component.text("  Page " + page + "/" + pages + "  ", NamedTextColor.GRAY))
                .append(button("Next »", label, page + 1, page < pages))
                .build();
    }

    private static Component button(String text, String label, int page, boolean enabled) {
        if (!enabled) return Component.text(text, NamedTextColor.DARK_GRAY);
        return Component.text(text, NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Go to page " + page)))
                .clickEvent(ClickEvent.runCommand("/" + label + " " + page));
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (args.length != 1) return List.of();
        int pages = (plugin.registry().all().size() + PER_PAGE - 1) / PER_PAGE;
        return IntStream.rangeClosed(1, pages).mapToObj(String::valueOf).filter(page -> page.startsWith(args[0])).toList();
    }
}
