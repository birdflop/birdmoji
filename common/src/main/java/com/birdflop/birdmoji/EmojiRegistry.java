package com.birdflop.birdmoji;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The emojis currently in the resource pack, looked up by their Discord name.
 */
public final class EmojiRegistry {

    /**
     * Discord emoji names are 2-32 characters of letters, numbers and underscores. This also matches Unicode
     * emoji shortcodes like :+1: and :-1:, and Discord emoji aliases like :fire~1:, which additionally allow
     * +, - and ~ and run a little longer.
     */
    public static final Pattern EMOJI_PATTERN = Pattern.compile(":([A-Za-z0-9_+~-]{2,48}):");

    private volatile Map<String, Emoji> byName = Map.of();
    private volatile Map<String, Emoji> byLowerName = Map.of();
    private volatile Map<String, Emoji> byAlias = Map.of();
    private volatile Map<String, Emoji> byLowerAlias = Map.of();
    private volatile Map<Long, Emoji> byId = Map.of();
    private volatile List<Emoji> emojis = List.of();

    public void set(List<Emoji> emojis) {
        Map<String, Emoji> byName = new HashMap<>();
        Map<String, Emoji> byLowerName = new HashMap<>();
        Map<String, Emoji> byAlias = new HashMap<>();
        Map<String, Emoji> byLowerAlias = new HashMap<>();
        for (Emoji emoji : emojis) {
            byName.putIfAbsent(emoji.name(), emoji);
            byLowerName.putIfAbsent(emoji.name().toLowerCase(Locale.ROOT), emoji);
            byAlias.putIfAbsent(emoji.alias(), emoji);
            byLowerAlias.putIfAbsent(emoji.alias().toLowerCase(Locale.ROOT), emoji);
        }
        this.byName = Map.copyOf(byName);
        this.byLowerName = Map.copyOf(byLowerName);
        this.byAlias = Map.copyOf(byAlias);
        this.byLowerAlias = Map.copyOf(byLowerAlias);
        this.byId = emojis.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Emoji::id, emoji -> emoji));
        this.emojis = List.copyOf(emojis);
    }

    /**
     * Finds an emoji by its real Discord name, preferring an exact match so Pog and pog can both exist. Used for
     * emojis coming from Discord, which are always given by their real name.
     */
    public Emoji find(String name) {
        Emoji emoji = byName.get(name);
        return emoji != null ? emoji : byLowerName.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Finds an emoji by the name players type in Minecraft chat, which is {@link Emoji#alias} rather than
     * {@link Emoji#name} so a Unicode emoji shortcode wins over a same-named Discord emoji.
     */
    public Emoji findByAlias(String alias) {
        Emoji emoji = byAlias.get(alias);
        return emoji != null ? emoji : byLowerAlias.get(alias.toLowerCase(Locale.ROOT));
    }

    public Emoji findById(long id) {
        return byId.get(id);
    }

    public Collection<Emoji> all() {
        return emojis;
    }

    public boolean isEmpty() {
        return emojis.isEmpty();
    }
}
