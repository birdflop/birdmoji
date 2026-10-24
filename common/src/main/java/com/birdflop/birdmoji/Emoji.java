package com.birdflop.birdmoji;

/**
 * A Discord custom emoji and the texture it was exported to.
 *
 * @param name    the emoji name on Discord, case-sensitive
 * @param texture the lowercase, unique texture name in the resource pack
 * @param alias   the name players type in Minecraft chat; same as {@code name} unless it collides with a
 *                Unicode emoji shortcode of the same name, in which case it gets a {@code ~1} suffix so the
 *                shortcode still resolves to the real Unicode emoji
 */
public record Emoji(String name, long id, boolean animated, String texture, String alias) {

    public String url(int size) {
        return "https://cdn.discordapp.com/emojis/" + id + (animated ? ".gif" : ".png") + "?size=" + size;
    }
}
