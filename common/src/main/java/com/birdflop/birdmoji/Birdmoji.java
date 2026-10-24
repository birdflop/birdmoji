package com.birdflop.birdmoji;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * The shared plugin, each DiscordSRV version has its own plugin that provides a {@link DiscordHook}.
 */
public abstract class Birdmoji extends JavaPlugin {

    private final EmojiRegistry registry = new EmojiRegistry();
    private final PackServer packServer = new PackServer(this);
    private DiscordHook hook;
    private ExecutorService buildExecutor;
    private PackBuilder packBuilder;
    private BukkitTask pendingRefresh;
    private volatile Settings settings;
    private volatile List<String> completions = List.of();

    protected abstract DiscordHook createHook();

    @Override
    public void onEnable() {
        hook = createHook();
        saveDefaultConfig();
        settings = Settings.load(getConfig(), getLogger());
        packBuilder = new PackBuilder(getLogger());
        buildExecutor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "Birdmoji Pack Builder"));
        packServer.start(settings);
        updateCompletions();

        getServer().getPluginManager().registerEvents(packServer, this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        EmojisCommand emojisCommand = new EmojisCommand(this);
        getCommand("emojis").setExecutor(emojisCommand);
        getCommand("emojis").setTabCompleter(emojisCommand);
        hook.enable();
    }

    @Override
    public void onDisable() {
        if (hook != null) hook.disable();
        if (buildExecutor != null) buildExecutor.shutdownNow();
        packServer.stop();
    }

    /**
     * Rebuilds the pack right away, called by hooks once Discord is connected.
     */
    public void refresh() {
        Settings settings = this.settings;
        buildExecutor.execute(() -> {
            try {
                DiscordHook.GuildEmojis guild = hook.fetchEmojis();
                if (guild == null) {
                    getLogger().warning("Could not find the Discord server to get emojis from. Check guild-id in config.yml.");
                    return;
                }

                long start = System.currentTimeMillis();
                PackBuilder.Pack pack = packBuilder.build(toEmojis(guild.emojis()), settings);
                Files.write(new File(getDataFolder(), "Birdmoji.zip").toPath(), pack.zip());
                getLogger().info("Built the resource pack with " + pack.emojis().size() + " emojis from " + guild.guildName()
                        + " in " + (System.currentTimeMillis() - start) + "ms");

                registry.set(pack.emojis());
                packServer.setPack(pack);
                Bukkit.getScheduler().runTask(this, this::updateCompletions);
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to build the emoji resource pack", e);
            }
        });
    }

    /**
     * Rebuilds the pack shortly after the last emoji change, so adding a bunch of emojis only rebuilds once.
     */
    public void scheduleRefresh() {
        Bukkit.getScheduler().runTask(this, () -> {
            if (pendingRefresh != null) pendingRefresh.cancel();
            pendingRefresh = Bukkit.getScheduler().runTaskLater(this, this::refresh, 20 * 10);
        });
    }

    /**
     * Discord names are case-sensitive but texture names have to be lowercase, so duplicates get a suffix.
     * If a Discord emoji shares its name with a Unicode emoji shortcode, it gets a ~1 alias so the shortcode
     * still resolves to the real Unicode emoji when typed in Minecraft chat.
     */
    private static List<Emoji> toEmojis(List<DiscordHook.DiscordEmoji> discordEmojis) {
        List<Emoji> emojis = new ArrayList<>();
        Set<String> textures = new HashSet<>();
        discordEmojis.stream()
                .filter(DiscordHook.DiscordEmoji::available)
                .sorted(Comparator.comparingLong(DiscordHook.DiscordEmoji::id))
                .forEach(emoji -> {
                    String base = emoji.name().toLowerCase(Locale.ROOT);
                    String texture = base;
                    for (int i = 1; !textures.add(texture); i++) texture = base + "-" + i;
                    String alias = UnicodeEmojis.find(base) != null ? emoji.name() + "~1" : emoji.name();
                    emojis.add(new Emoji(emoji.name(), emoji.id(), emoji.animated(), texture, alias));
                });
        return emojis;
    }

    private void updateCompletions() {
        List<String> previous = completions;
        List<String> next = new ArrayList<>(registry.all().stream().map(emoji -> ":" + emoji.alias() + ":").toList());
        if (settings.unicodeShortcodes()) UnicodeEmojis.shortcodes().forEach(shortcode -> next.add(":" + shortcode + ":"));
        completions = List.copyOf(next);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removeCustomChatCompletions(previous);
            player.addCustomChatCompletions(completions);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) return false;

        reloadConfig();
        settings = Settings.load(getConfig(), getLogger());
        packServer.start(settings);
        refresh();
        sender.sendMessage("Reloaded Birdmoji, rebuilding the emoji resource pack...");
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        return args.length == 1 ? List.of("reload") : List.of();
    }

    /**
     * The emoji as a sprite, using Paper's Adventure. White by default so chat colors don't tint it.
     */
    public Component sprite(Emoji emoji) {
        Component sprite = Component.object(ObjectContents.sprite(Key.key(settings.atlas()), Key.key(settings.sprite(emoji))));
        return settings.preventTint() ? sprite.color(NamedTextColor.WHITE) : sprite;
    }

    /**
     * The emoji as a sprite with its name on hover if enabled, using Paper's Adventure
     */
    public Component spriteWithHover(Emoji emoji) {
        Component sprite = sprite(emoji);
        return settings.hoverName()
                ? sprite.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text(":" + emoji.name() + ":")))
                : sprite;
    }

    public EmojiRegistry registry() {
        return registry;
    }

    public PackServer packServer() {
        return packServer;
    }

    public Settings settings() {
        return settings;
    }

    List<String> completions() {
        return completions;
    }
}
