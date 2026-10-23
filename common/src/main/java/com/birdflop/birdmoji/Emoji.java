package com.birdflop.birdmoji;

/**
 * A Discord custom emoji and the texture it was exported to.
 *
 * @param name    the emoji name on Discord, case-sensitive
 * @param texture the lowercase, unique texture name in the resource pack
 */
public record Emoji(String name, long id, boolean animated, String texture) {

    public String url(int size) {
        return "https://cdn.discordapp.com/emojis/" + id + (animated ? ".gif" : ".png") + "?size=" + size;
    }
}
