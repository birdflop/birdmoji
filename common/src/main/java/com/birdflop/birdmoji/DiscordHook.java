package com.birdflop.birdmoji;

import java.util.List;

/**
 * Connects Birdmoji to a DiscordSRV version.
 */
public interface DiscordHook {

    /**
     * A custom emoji as Discord reports it.
     */
    record DiscordEmoji(String name, long id, boolean animated, boolean available) {}

    record GuildEmojis(String guildName, List<DiscordEmoji> emojis) {}

    void enable();

    void disable();

    /**
     * Gets the emojis of the configured Discord server. Called off the main thread.
     *
     * @return the emojis, or {@code null} if Discord isn't connected or the server can't be found
     */
    GuildEmojis fetchEmojis() throws Exception;
}
