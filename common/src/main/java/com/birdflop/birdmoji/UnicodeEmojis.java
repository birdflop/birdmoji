package com.birdflop.birdmoji;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Standard Unicode emoji shortcodes (e.g. :smile:), bundled from
 * <a href="https://github.com/github/gemoji">gemoji</a> so players can type them like Discord custom emojis.
 * Unlike custom emojis, these resolve to the real Unicode character instead of a sprite.
 */
final class UnicodeEmojis {

    private static final Map<String, String> BY_SHORTCODE = load();

    private UnicodeEmojis() {}

    /**
     * Finds the Unicode emoji for a shortcode, e.g. "smile" for :smile:.
     */
    static String find(String shortcode) {
        return BY_SHORTCODE.get(shortcode.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Every known shortcode, without the surrounding colons.
     */
    static Set<String> shortcodes() {
        return BY_SHORTCODE.keySet();
    }

    private static Map<String, String> load() {
        try (InputStream in = UnicodeEmojis.class.getResourceAsStream("/emoji-shortcodes.json")) {
            if (in == null) return Map.of();
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, String> shortcodes = new Gson().fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                return Map.copyOf(shortcodes);
            }
        } catch (IOException e) {
            Logger.getLogger(UnicodeEmojis.class.getName()).log(Level.WARNING, "Failed to load emoji-shortcodes.json", e);
            return Map.of();
        }
    }
}
