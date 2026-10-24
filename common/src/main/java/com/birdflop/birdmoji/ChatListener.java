package com.birdflop.birdmoji;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Lets players use :emoji: in Minecraft chat.
 */
public final class ChatListener implements Listener {

    private final Birdmoji plugin;

    ChatListener(Birdmoji plugin) {
        this.plugin = plugin;
    }

    /**
     * Fix the capitalization of emoji names in the message itself, so DiscordSRV (which reads the message,
     * not what players see) turns :pog: into the real :Pog: emoji on Discord. Also turns Unicode emoji
     * shortcodes like :smile: into the real Unicode character, so it reaches Discord as an actual emoji
     * and shows up in Minecraft chat as plain text (no sprite needed). Looked up by alias, so a Unicode
     * shortcode wins over a Discord emoji of the same name (which is then typed as :name~1:).
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChatName(AsyncChatEvent event) {
        if (plugin.registry().isEmpty() && !plugin.settings().unicodeShortcodes()) return;
        event.message(event.message().replaceText(builder -> builder
                .match(EmojiRegistry.EMOJI_PATTERN)
                .replacement((match, text) -> {
                    Emoji emoji = plugin.registry().findByAlias(match.group(1));
                    if (emoji != null) return Component.text(":" + emoji.name() + ":");
                    if (!plugin.settings().unicodeShortcodes()) return text;
                    String unicode = UnicodeEmojis.find(match.group(1));
                    if (unicode == null) return text;
                    Component replacement = Component.text(unicode);
                    return plugin.settings().preventTint() ? replacement.color(NamedTextColor.WHITE) : replacement;
                })));
    }

    /**
     * Swap emojis for sprites only in what each player sees, and only for players that have the pack.
     * This changes the finished chat line rather than the message, since formatting plugins like EssentialsChat
     * replace the renderer and ignore the message it's given. It runs at MONITOR so it wraps whichever renderer
     * ends up being used, and it doesn't change the event, only how it's displayed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatRender(AsyncChatEvent event) {
        if (plugin.registry().isEmpty()) return;
        ChatRenderer renderer = event.renderer();
        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = renderer.render(source, displayName, message, viewer);
            return viewer instanceof Player player && plugin.packServer().hasPack(player) ? sprites(rendered) : rendered;
        });
    }

    private Component sprites(Component message) {
        return message.replaceText(builder -> builder
                .match(EmojiRegistry.EMOJI_PATTERN)
                .replacement((match, text) -> {
                    Emoji emoji = plugin.registry().find(match.group(1));
                    return emoji == null ? text : plugin.spriteWithHover(emoji);
                }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().addCustomChatCompletions(plugin.completions());
    }
}
