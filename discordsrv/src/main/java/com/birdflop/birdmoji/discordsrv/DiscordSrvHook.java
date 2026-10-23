package com.birdflop.birdmoji.discordsrv;

import com.birdflop.birdmoji.Birdmoji;
import com.birdflop.birdmoji.DiscordHook;
import com.birdflop.birdmoji.Emoji;
import com.birdflop.birdmoji.EmojiRegistry;
import com.birdflop.birdmoji.FakeNitro;
import com.birdflop.birdmoji.Settings;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreBroadcastEvent;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.events.emote.EmoteAddedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.events.emote.EmoteRemovedEvent;
import github.scarsz.discordsrv.dependencies.jda.api.events.emote.update.EmoteUpdateNameEvent;
import github.scarsz.discordsrv.dependencies.jda.api.hooks.ListenerAdapter;
import github.scarsz.discordsrv.dependencies.kyori.adventure.key.Key;
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.Component;
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.event.HoverEvent;
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.object.ObjectContents;
import github.scarsz.discordsrv.dependencies.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import github.scarsz.discordsrv.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * DiscordSRV 1.x. It relocates Adventure, so messages from Discord use its copy rather than Paper's.
 */
public final class DiscordSrvHook extends ListenerAdapter implements DiscordHook {

    private final Birdmoji plugin;
    private JDA jda;

    public DiscordSrvHook(Birdmoji plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        DiscordSRV.api.subscribe(this);
        // DiscordSRV may have connected before Birdmoji enabled (e.g. after a reload)
        if (DiscordSRV.isReady) connected();
    }

    @Override
    public void disable() {
        DiscordSRV.api.unsubscribe(this);
        if (jda != null) jda.removeEventListener(this);
    }

    @Override
    public GuildEmojis fetchEmojis() {
        Guild guild = guild();
        if (guild == null) return null;
        return new GuildEmojis(guild.getName(), guild.getEmotes().stream()
                .map(emote -> new DiscordEmoji(emote.getName(), emote.getIdLong(), emote.isAnimated(), emote.isAvailable()))
                .toList());
    }

    private Guild guild() {
        if (!DiscordSRV.isReady || DiscordSRV.getPlugin().getJda() == null) return null;
        String guildId = plugin.settings().guildId();
        return guildId.isBlank() ? DiscordSRV.getPlugin().getMainGuild() : DiscordSRV.getPlugin().getJda().getGuildById(guildId);
    }

    @Subscribe
    public void onDiscordReady(DiscordReadyEvent event) {
        connected();
    }

    private void connected() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            JDA jda = DiscordSRV.getPlugin().getJda();
            if (jda != null && this.jda != jda) {
                jda.addEventListener(this);
                this.jda = jda;
            }
            plugin.refresh();
        });
    }

    /**
     * DiscordSRV turns custom emojis into :name: before this, so swap those for sprites. Players without
     * the pack are taken out of the broadcast and get the plain :name: version instead.
     */
    @Subscribe
    public void onBroadcast(DiscordGuildMessagePreBroadcastEvent event) {
        if (plugin.registry().isEmpty()) return;

        Component message = event.getMessage();
        Component original = fakeNitroToNames(message);
        if (!original.equals(message)) event.setMessage(original);

        Component withSprites = original.replaceText(builder -> builder
                .match(EmojiRegistry.EMOJI_PATTERN)
                .replacement((match, text) -> {
                    Emoji emoji = plugin.registry().find(match.group(1));
                    return emoji == null ? text : sprite(emoji);
                }));
        if (withSprites.equals(original)) return;

        List<CommandSender> withoutPack = new ArrayList<>();
        event.getRecipients().removeIf(recipient -> {
            if (recipient instanceof Player player && !plugin.packServer().hasPack(player)) {
                withoutPack.add(player);
                return true;
            }
            return false;
        });
        event.setMessage(withSprites);
        if (!withoutPack.isEmpty()) MessageUtil.sendMessage(withoutPack, original);
    }

    /**
     * FakeNitro emojis are links, so turn them into :name: like real emojis. DiscordSRV relocates Adventure, so this
     * goes through JSON to use the shared code written against Paper's.
     */
    private Component fakeNitroToNames(Component message) {
        net.kyori.adventure.text.Component paper = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson()
                .deserialize(GsonComponentSerializer.gson().serialize(message));
        net.kyori.adventure.text.Component replaced = FakeNitro.replace(paper, (id, name) -> {
            Emoji emoji = plugin.registry().findById(id);
            return net.kyori.adventure.text.Component.text(":" + (emoji != null ? emoji.name() : name) + ":");
        });
        return replaced.equals(paper)
                ? message
                : GsonComponentSerializer.gson().deserialize(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(replaced));
    }

    private Component sprite(Emoji emoji) {
        Settings settings = plugin.settings();
        Component sprite = Component.object(ObjectContents.sprite(Key.key(settings.atlas()), Key.key(settings.sprite(emoji))));
        return settings.hoverName()
                ? sprite.hoverEvent(HoverEvent.showText(Component.text(":" + emoji.name() + ":")))
                : sprite;
    }

    @Override
    public void onEmoteAdded(@NotNull EmoteAddedEvent event) {
        emojisChanged(event.getGuild());
    }

    @Override
    public void onEmoteRemoved(@NotNull EmoteRemovedEvent event) {
        emojisChanged(event.getGuild());
    }

    @Override
    public void onEmoteUpdateName(@NotNull EmoteUpdateNameEvent event) {
        emojisChanged(event.getGuild());
    }

    private void emojisChanged(Guild guild) {
        Guild current = guild();
        if (current != null && current.getIdLong() == guild.getIdLong()) plugin.scheduleRefresh();
    }
}
