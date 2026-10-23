package com.birdflop.birdmoji;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Hosts the resource pack over HTTP and sends it to players next to the server's own pack.
 */
public final class PackServer implements Listener {

    /** Constant so clients replace the old emoji pack instead of stacking a new one */
    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("birdmoji".getBytes(StandardCharsets.UTF_8));

    private final Birdmoji plugin;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private HttpServer http;
    private volatile Settings settings;
    private volatile PackBuilder.Pack pack;
    private volatile String baseUrl;
    private volatile boolean assumeLoaded;

    PackServer(Birdmoji plugin) {
        this.plugin = plugin;
    }

    void start(Settings settings) {
        stop();
        this.settings = settings;
        assumeLoaded = !settings.sendPack();
        if (assumeLoaded) return;

        try {
            http = HttpServer.create(new InetSocketAddress(settings.port()), 0);
            http.createContext("/birdmoji/", this::handle);
            http.setExecutor(Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "Birdmoji Pack Server");
                thread.setDaemon(true);
                return thread;
            }));
            http.start();
        } catch (IOException e) {
            plugin.getLogger().severe("Could not host the resource pack on port " + settings.port() + ": " + e.getMessage()
                    + ". Set resource-pack.port to a free port, or disable resource-pack.enabled and add the pack yourself.");
            http = null;
        }
    }

    void stop() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
        baseUrl = null;
        loaded.clear();
    }

    /**
     * Called async after a pack was built. Resolves the public address and sends the pack to everyone online.
     */
    void setPack(PackBuilder.Pack pack) {
        this.pack = pack;
        if (http == null) return;
        if (baseUrl == null) baseUrl = resolveBaseUrl();
        loaded.clear();
        Bukkit.getOnlinePlayers().forEach(this::send);
    }

    /**
     * Whether this player can see the emoji sprites, otherwise they get :name: instead of missing textures.
     * When sending the pack is disabled, it's assumed the server owner merged it into their own pack.
     */
    public boolean hasPack(Player player) {
        return assumeLoaded || loaded.contains(player.getUniqueId());
    }

    private void send(Player player) {
        PackBuilder.Pack pack = this.pack;
        if (pack == null || baseUrl == null || pack.emojis().isEmpty()) return;

        String prompt = settings.prompt();
        ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(baseUrl + "/birdmoji/" + pack.sha1() + ".zip"), pack.sha1()))
                .replace(false)
                .required(settings.required());
        if (!prompt.isBlank()) request.prompt(MiniMessage.miniMessage().deserialize(prompt));
        player.sendResourcePacks(request.build());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        UUID player = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(player);
            case ACCEPTED, DOWNLOADED -> {}
            default -> loaded.remove(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    private void handle(HttpExchange exchange) throws IOException {
        PackBuilder.Pack pack = this.pack;
        try (exchange) {
            if (pack == null || !exchange.getRequestURI().getPath().equals("/birdmoji/" + pack.sha1() + ".zip")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, pack.zip().length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(pack.zip());
            }
        }
    }

    private String resolveBaseUrl() {
        if (!settings.publicUrl().isBlank()) return settings.publicUrl();

        String ip = Bukkit.getIp();
        if (ip.isBlank()) {
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                ip = client.send(HttpRequest.newBuilder(URI.create("https://checkip.amazonaws.com")).build(),
                        HttpResponse.BodyHandlers.ofString()).body().trim();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not detect this server's public IP, set resource-pack.public-url in config.yml");
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        String url = "http://" + ip + ":" + settings.port();
        plugin.getLogger().info("Hosting the emoji resource pack at " + url + " (set resource-pack.public-url to change this)");
        return url;
    }
}
