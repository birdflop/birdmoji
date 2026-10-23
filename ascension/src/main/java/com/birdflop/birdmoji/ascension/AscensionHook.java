package com.birdflop.birdmoji.ascension;

import com.birdflop.birdmoji.Birdmoji;
import com.birdflop.birdmoji.DiscordHook;
import com.discordsrv.api.DiscordSRV;
import com.discordsrv.api.reload.ReloadFlag;
import com.discordsrv.dependencies.net.dv8tion.jda.api.JDA;
import com.discordsrv.dependencies.net.dv8tion.jda.api.entities.Guild;
import com.discordsrv.dependencies.net.dv8tion.jda.api.requests.GatewayIntent;
import com.discordsrv.dependencies.net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * DiscordSRV Ascension. It doesn't cache custom emojis unless an addon asks before it connects to Discord,
 * which happens before Birdmoji enables, so Birdmoji reconnects it once at startup.
 */
public final class AscensionHook implements DiscordHook {

    private final Birdmoji plugin;
    private final AscensionListener listener;
    private final EmojiModule module;
    private final AtomicBoolean reconnected = new AtomicBoolean();
    private volatile long guildId;

    AscensionHook(Birdmoji plugin) {
        this.plugin = plugin;
        this.listener = new AscensionListener(plugin, this);
        this.module = new EmojiModule(this);
    }

    @Override
    public void enable() {
        DiscordSRV discordSRV = DiscordSRV.get();
        discordSRV.eventBus().subscribe(listener);
        discordSRV.registerModule(module);
        // Ascension usually connects before Birdmoji enables
        if (discordSRV.isReady()) connected();
    }

    @Override
    public void disable() {
        DiscordSRV.optional().ifPresent(discordSRV -> {
            discordSRV.eventBus().unsubscribe(listener);
            discordSRV.unregisterModule(module);
        });
    }

    /**
     * Called whenever Ascension (re)connects to Discord.
     */
    void connected() {
        JDA jda = DiscordSRV.get().jda();
        if (jda == null) return;

        boolean seesEmojis = jda.getGatewayIntents().contains(GatewayIntent.GUILD_EXPRESSIONS)
                && jda.getCacheFlags().contains(CacheFlag.EMOJI);
        if (!seesEmojis && reconnected.compareAndSet(false, true)) {
            plugin.getLogger().info("Reconnecting DiscordSRV to Discord so it can see custom emojis, this only happens once per startup");
            Thread thread = new Thread(this::reconnect, "Birdmoji Reconnect");
            thread.setDaemon(true);
            thread.start();
            return;
        }
        if (!seesEmojis) {
            plugin.getLogger().warning("DiscordSRV still can't see custom emojis, so emojis sent from Discord won't show in Minecraft. "
                    + "Run /discordsrv reload discord_connection -confirm to try again.");
        }
        plugin.refresh();
    }

    /**
     * Reconnects with the intents and cache flags from {@link EmojiModule}. runReload isn't in Ascension's API yet,
     * it's what /discordsrv reload discord_connection uses.
     */
    private void reconnect() {
        try {
            DiscordSRV discordSRV = DiscordSRV.get();
            discordSRV.getClass().getMethod("runReload", Set.class).invoke(discordSRV, EnumSet.of(ReloadFlag.DISCORD_CONNECTION));
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not reconnect DiscordSRV, run /discordsrv reload discord_connection -confirm "
                    + "so emojis sent from Discord show in Minecraft", e);
            plugin.refresh();
        }
    }

    @Override
    public GuildEmojis fetchEmojis() {
        DiscordSRV discordSRV = DiscordSRV.optional().orElse(null);
        JDA jda = discordSRV != null && discordSRV.isReady() ? discordSRV.jda() : null;
        if (jda == null) return null;

        Guild guild = guild(jda);
        if (guild == null) return null;
        guildId = guild.getIdLong();

        // Works without the emoji cache, unlike guild.getEmojis()
        return new GuildEmojis(guild.getName(), guild.retrieveEmojis().complete().stream()
                .map(emoji -> new DiscordEmoji(emoji.getName(), emoji.getIdLong(), emoji.isAnimated(), emoji.isAvailable()))
                .toList());
    }

    private Guild guild(JDA jda) {
        String configured = plugin.settings().guildId();
        if (!configured.isBlank()) {
            try {
                return jda.getGuildById(Long.parseLong(configured));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        List<Guild> guilds = jda.getGuilds();
        if (guilds.size() == 1) return guilds.getFirst();
        plugin.getLogger().warning("DiscordSRV's bot is in " + guilds.size() + " Discord servers, set guild-id in config.yml to choose one");
        return null;
    }

    void emojisChanged(Guild guild) {
        if (guild.getIdLong() == guildId) plugin.scheduleRefresh();
    }
}
