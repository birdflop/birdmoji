package com.birdflop.birdmoji;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;
import java.util.logging.Logger;

/**
 * Immutable snapshot of config.yml, safe to pass to async tasks.
 */
public record Settings(
        String guildId,
        String atlas,
        String spriteNamespace,
        String spritePath,
        int size,
        int maxFrames,
        boolean hoverName,
        boolean unicodeShortcodes,
        boolean preventTint,
        boolean sendPack,
        int port,
        String publicUrl,
        boolean required,
        String prompt
) {

    /**
     * The client only loads these atlases (AtlasManager.KNOWN_ATLASES), atlases added by resource packs are ignored
     * and their sprites render as missing glyphs. gui doesn't use mipmaps, which suits chat.
     */
    private static final Set<String> KNOWN_ATLASES = Set.of(
            "minecraft:banner_patterns", "minecraft:blocks", "minecraft:items", "minecraft:chests",
            "minecraft:decorated_pot", "minecraft:gui", "minecraft:map_decorations", "minecraft:paintings",
            "minecraft:particles", "minecraft:shield_patterns", "minecraft:shulker_boxes", "minecraft:celestials"
    );
    private static final String DEFAULT_ATLAS = "minecraft:gui";

    public static Settings load(FileConfiguration config, Logger logger) {
        String atlas = config.getString("atlas", DEFAULT_ATLAS);
        if (!atlas.contains(":")) atlas = "minecraft:" + atlas;
        if (!KNOWN_ATLASES.contains(atlas)) {
            logger.warning("Minecraft doesn't support the atlas " + atlas + " from config.yml, using " + DEFAULT_ATLAS
                    + " instead. Resource packs can only add sprites to " + String.join(", ", KNOWN_ATLASES.stream().sorted().toList()));
            atlas = DEFAULT_ATLAS;
        }

        String prefix = config.getString("sprite-prefix", "birdflop:gif/");
        int colon = prefix.indexOf(':');
        String namespace = colon == -1 ? "minecraft" : prefix.substring(0, colon);
        String path = colon == -1 ? prefix : prefix.substring(colon + 1);
        if (!path.isEmpty() && !path.endsWith("/")) path += "/";

        return new Settings(
                config.getString("guild-id", ""),
                atlas,
                namespace,
                path,
                Math.clamp(config.getInt("size", 32), 8, 256),
                Math.max(1, config.getInt("max-frames", 256)),
                config.getBoolean("hover-name", true),
                config.getBoolean("unicode-shortcodes", true),
                config.getBoolean("prevent-tint", true),
                config.getBoolean("resource-pack.enabled", true),
                config.getInt("resource-pack.port", 8164),
                config.getString("resource-pack.public-url", "").replaceAll("/+$", ""),
                config.getBoolean("resource-pack.required", false),
                config.getString("resource-pack.prompt", "")
        );
    }

    public String sprite(Emoji emoji) {
        return spriteNamespace + ":" + spritePath + emoji.texture();
    }
}
