package com.birdflop.birdmoji.ascension;

import com.discordsrv.api.discord.connection.details.DiscordCacheFlag;
import com.discordsrv.api.discord.connection.details.DiscordGatewayIntent;
import com.discordsrv.api.eventbus.Subscribe;
import com.discordsrv.api.module.Module;
import com.discordsrv.dependencies.net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import com.discordsrv.dependencies.net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import com.discordsrv.dependencies.net.dv8tion.jda.api.events.emoji.update.EmojiUpdateNameEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Asks Ascension to cache custom emojis, which it needs to show them from Discord, and rebuilds the pack when they change.
 * Ascension only enables this once it's connected with these intents and cache flags.
 */
public final class EmojiModule implements Module {

    private final AscensionHook hook;

    EmojiModule(AscensionHook hook) {
        this.hook = hook;
    }

    @Override
    public @NotNull Collection<DiscordGatewayIntent> requiredIntents() {
        return List.of(DiscordGatewayIntent.GUILD_EXPRESSIONS);
    }

    @Override
    public @NotNull Collection<DiscordCacheFlag> requiredCacheFlags() {
        return List.of(DiscordCacheFlag.EMOJI);
    }

    @Subscribe
    public void onEmojiAdded(EmojiAddedEvent event) {
        hook.emojisChanged(event.getGuild());
    }

    @Subscribe
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        hook.emojisChanged(event.getGuild());
    }

    @Subscribe
    public void onEmojiUpdateName(EmojiUpdateNameEvent event) {
        hook.emojisChanged(event.getGuild());
    }
}
