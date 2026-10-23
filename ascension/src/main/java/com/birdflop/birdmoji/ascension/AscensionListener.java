package com.birdflop.birdmoji.ascension;

import com.birdflop.birdmoji.Birdmoji;
import com.birdflop.birdmoji.Emoji;
import com.birdflop.birdmoji.FakeNitro;
import com.discordsrv.api.component.MinecraftComponent;
import com.discordsrv.api.discord.entity.message.SendableDiscordMessage;
import com.discordsrv.api.eventbus.Subscribe;
import com.discordsrv.api.events.lifecycle.DiscordSRVConnectedEvent;
import com.discordsrv.api.events.message.postprocess.discord.DiscordChatMessagePostProcessEvent;
import com.discordsrv.api.events.message.postprocess.game.GameChatMessagePostProcessEvent;
import com.discordsrv.api.events.message.render.game.CustomEmojiRenderEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles messages in both directions. This isn't part of {@link EmojiModule} so it works even when Ascension
 * can't see custom emojis.
 */
public final class AscensionListener {

    private static final String TRANSLATOR = "birdmoji";
    private static final String KEY_PREFIX = "discordsrv." + TRANSLATOR + ".";

    /**
     * :name: in a message going to Discord. Ascension escapes formatting characters, so it's actually \:name\: and
     * underscores are \_. Skips emojis that are already mentions like <:name:id>.
     */
    private static final Pattern DISCORD_EMOJI_PATTERN = Pattern.compile("(?<!<a?)\\\\?:((?:[A-Za-z0-9]|\\\\?_){2,32})\\\\?:");

    private final Birdmoji plugin;
    private final AscensionHook hook;

    AscensionListener(Birdmoji plugin, AscensionHook hook) {
        this.plugin = plugin;
        this.hook = hook;
    }

    @Subscribe
    public void onConnected(DiscordSRVConnectedEvent event) {
        hook.connected();
    }

    /**
     * Emojis from Discord become a placeholder that's translated for each player in {@link #onDiscordMessage}.
     */
    @Subscribe
    public void onEmojiRender(CustomEmojiRenderEvent event) {
        Emoji emoji = plugin.registry().findById(event.getEmoji().getId());
        if (emoji == null) return;
        event.process(toAscension(placeholder(emoji)));
    }

    private static Component placeholder(Emoji emoji) {
        return Component.translatable()
                .key(KEY_PREFIX + emoji.id())
                .fallback(":" + emoji.name() + ":")
                .build();
    }

    /**
     * Players with the pack see the sprite, everyone else (and the console) gets the :name: fallback.
     */
    @Subscribe
    public void onDiscordMessage(DiscordChatMessagePostProcessEvent event) {
        // FakeNitro emojis are links, emojis from other servers aren't in the pack so they just get their name
        Component message = GsonComponentSerializer.gson().deserialize(event.getMessage().asJson());
        Component replaced = FakeNitro.replace(message, (id, name) -> {
            Emoji emoji = plugin.registry().findById(id);
            return emoji != null ? placeholder(emoji) : Component.text(":" + name + ":");
        });
        if (!replaced.equals(message)) event.setMessage(toAscension(replaced));

        event.registerTranslator(TRANSLATOR, (component, player) -> {
            if (!(GsonComponentSerializer.gson().deserialize(component.asJson()) instanceof TranslatableComponent translatable)
                    || !translatable.key().startsWith(KEY_PREFIX)) {
                return null;
            }

            Emoji emoji;
            try {
                emoji = plugin.registry().findById(Long.parseLong(translatable.key().substring(KEY_PREFIX.length())));
            } catch (NumberFormatException e) {
                return null;
            }
            Player bukkitPlayer = player == null ? null : Bukkit.getPlayer(player.uniqueId());
            if (emoji == null || bukkitPlayer == null || !plugin.packServer().hasPack(bukkitPlayer)) return null;
            return toAscension(plugin.spriteWithHover(emoji));
        });
    }

    /**
     * Ascension doesn't turn :name: from Minecraft into Discord emojis, so do it in the final message.
     */
    @Subscribe
    public void onGameMessage(GameChatMessagePostProcessEvent event) {
        if (plugin.registry().isEmpty()) return;
        SendableDiscordMessage message = event.getMessage();
        String content = message.getContent();
        if (content == null || content.isEmpty()) return;

        Matcher matcher = DISCORD_EMOJI_PATTERN.matcher(content);
        StringBuilder replaced = new StringBuilder();
        while (matcher.find()) {
            Emoji emoji = plugin.registry().find(matcher.group(1).replace("\\_", "_"));
            String mention = emoji == null
                    ? matcher.group()
                    : "<" + (emoji.animated() ? "a" : "") + ":" + emoji.name() + ":" + emoji.id() + ">";
            matcher.appendReplacement(replaced, Matcher.quoteReplacement(mention));
        }
        matcher.appendTail(replaced);
        if (replaced.toString().equals(content)) return;

        // SendableDiscordMessage has no toBuilder, so copy everything over
        SendableDiscordMessage.Builder builder = SendableDiscordMessage.builder()
                .setContent(replaced.toString())
                .setAllowedMentions(message.getAllowedMentions())
                .setWebhookUsername(message.getWebhookUsername())
                .setWebhookAvatarUrl(message.getWebhookAvatarUrl())
                .setSuppressedNotifications(message.isSuppressedNotifications())
                .setSuppressedEmbeds(message.isSuppressedEmbeds())
                .setMessageIdToReplyTo(message.getMessageIdToReplyTo());
        message.getEmbeds().forEach(builder::addEmbed);
        message.getComponents().forEach(builder::addComponent);
        message.getAttachments().forEach(builder::addAttachment);
        event.setMessage(builder.build());
    }

    private static MinecraftComponent toAscension(Component component) {
        return MinecraftComponent.fromJson(GsonComponentSerializer.gson().serialize(component));
    }
}
