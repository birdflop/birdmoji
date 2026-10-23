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

    /** Discord emoji names are 2-32 characters of letters, numbers and underscores */
    public static final Pattern EMOJI_PATTERN = Pattern.compile(":([A-Za-z0-9_]{2,32}):");

    private volatile Map<String, Emoji> byName = Map.of();
    private volatile Map<String, Emoji> byLowerName = Map.of();
    private volatile Map<Long, Emoji> byId = Map.of();
    private volatile List<Emoji> emojis = List.of();

    public void set(List<Emoji> emojis) {
        Map<String, Emoji> byName = new HashMap<>();
        Map<String, Emoji> byLowerName = new HashMap<>();
        for (Emoji emoji : emojis) {
            byName.putIfAbsent(emoji.name(), emoji);
            byLowerName.putIfAbsent(emoji.name().toLowerCase(Locale.ROOT), emoji);
        }
        this.byName = Map.copyOf(byName);
        this.byLowerName = Map.copyOf(byLowerName);
        this.byId = emojis.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Emoji::id, emoji -> emoji));
        this.emojis = List.copyOf(emojis);
    }

    /**
     * Finds an emoji by name, preferring an exact match so Pog and pog can both exist.
     */
    public Emoji find(String name) {
        Emoji emoji = byName.get(name);
        return emoji != null ? emoji : byLowerName.get(name.toLowerCase(Locale.ROOT));
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
