package com.birdflop.birdmoji;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a resource pack with every emoji as an (animated) sprite.
 */
final class PackBuilder {

    /** Sprite text components were added in 1.21.9, resource pack format 69 */
    private static final int MIN_FORMAT = 69;

    record Pack(byte[] zip, String sha1, List<Emoji> emojis) {}

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Logger logger;

    PackBuilder(Logger logger) {
        this.logger = logger;
    }

    Pack build(List<Emoji> emojis, Settings settings) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<Emoji> included = new ArrayList<>();

        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            JsonObject pack = new JsonObject();
            pack.addProperty("description", "Birdmoji - Discord emojis by birdflop.com");
            pack.addProperty("pack_format", MIN_FORMAT);
            pack.addProperty("min_format", MIN_FORMAT);
            pack.addProperty("max_format", 1000);
            JsonObject mcmeta = new JsonObject();
            mcmeta.add("pack", pack);
            write(zip, "pack.mcmeta", mcmeta.toString());

            try (InputStream icon = PackBuilder.class.getResourceAsStream("/pack.png")) {
                if (icon != null) write(zip, "pack.png", icon.readAllBytes());
            }

            // A directory source picks up every texture in textures/<sprite path>
            String[] atlas = settings.atlas().split(":", 2);
            JsonObject source = new JsonObject();
            source.addProperty("type", "directory");
            source.addProperty("source", settings.spritePath().replaceAll("/$", ""));
            source.addProperty("prefix", settings.spritePath());
            JsonArray sources = new JsonArray();
            sources.add(source);
            JsonObject atlasJson = new JsonObject();
            atlasJson.add("sources", sources);
            write(zip, "assets/" + atlas[0] + "/atlases/" + atlas[1] + ".json", atlasJson.toString());

            String texturePath = "assets/" + settings.spriteNamespace() + "/textures/" + settings.spritePath();
            for (Emoji emoji : emojis) {
                try {
                    Texture texture = render(emoji, settings);
                    write(zip, texturePath + emoji.texture() + ".png", texture.png());
                    if (texture.mcmeta() != null) {
                        write(zip, texturePath + emoji.texture() + ".png.mcmeta", texture.mcmeta());
                    }
                    included.add(emoji);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to add emoji :" + emoji.name() + ": to the resource pack", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while downloading emojis", e);
                }
            }
        }

        byte[] zip = bytes.toByteArray();
        return new Pack(zip, sha1(zip), included);
    }

    private record Texture(byte[] png, String mcmeta) {}

    private Texture render(Emoji emoji, Settings settings) throws IOException, InterruptedException {
        // Discord only serves powers of two
        int requestSize = Integer.highestOneBit(settings.size() - 1) << 1;
        byte[] data = download(emoji.url(Math.max(16, requestSize)));
        int size = settings.size();

        if (emoji.animated()) {
            try {
                List<GifFrames.Frame> frames = reduce(GifFrames.read(data), settings.maxFrames());
                if (frames.size() > 1) return animated(frames, size);
                return new Texture(png(fit(frames.getFirst().image(), size)), null);
            } catch (IOException | RuntimeException e) {
                // ImageIO chokes on some GIFs, the static version of the emoji is better than nothing
                logger.fine("Falling back to a static image for :" + emoji.name() + ": (" + e.getMessage() + ")");
                data = download(emoji.url(Math.max(16, requestSize)).replace(".gif", ".png"));
            }
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) throw new IOException("Unsupported image format");
        return new Texture(png(fit(image, size)), null);
    }

    private Texture animated(List<GifFrames.Frame> frames, int size) throws IOException {
        BufferedImage strip = new BufferedImage(size, size * frames.size(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = strip.createGraphics();
        JsonArray frameTimes = new JsonArray();
        int firstTime = -1;
        boolean sameTime = true;

        for (int i = 0; i < frames.size(); i++) {
            graphics.drawImage(fit(frames.get(i).image(), size), 0, i * size, null);

            // Browsers treat delays under 2cs as 10cs, and a tick is 5cs
            int delay = frames.get(i).delayCentiseconds();
            int time = Math.max(1, Math.round((delay < 2 ? 10 : delay) / 5f));
            if (firstTime == -1) firstTime = time;
            sameTime &= time == firstTime;

            JsonObject frame = new JsonObject();
            frame.addProperty("index", i);
            frame.addProperty("time", time);
            frameTimes.add(frame);
        }
        graphics.dispose();

        JsonObject animation = new JsonObject();
        if (sameTime) animation.addProperty("frametime", firstTime);
        else animation.add("frames", frameTimes);
        JsonObject mcmeta = new JsonObject();
        mcmeta.add("animation", animation);
        return new Texture(png(strip), mcmeta.toString());
    }

    /**
     * Drops frames evenly to stay under the limit, keeping the total duration the same.
     */
    private static List<GifFrames.Frame> reduce(List<GifFrames.Frame> frames, int maxFrames) {
        if (frames.size() <= maxFrames) return frames;
        int step = (int) Math.ceil(frames.size() / (double) maxFrames);
        List<GifFrames.Frame> reduced = new ArrayList<>();
        for (int i = 0; i < frames.size(); i += step) {
            int delay = 0;
            for (int j = i; j < Math.min(i + step, frames.size()); j++) delay += frames.get(j).delayCentiseconds();
            reduced.add(new GifFrames.Frame(frames.get(i).image(), delay));
        }
        return reduced;
    }

    /**
     * Scales the image to fit a size x size square, centered.
     */
    private static BufferedImage fit(BufferedImage image, int size) {
        double scale = Math.min(size / (double) image.getWidth(), size / (double) image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));

        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image, (size - width) / 2, (size - height) / 2, width, height, null);
        graphics.dispose();
        return result;
    }

    private byte[] download(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + " from " + url);
        return response.body();
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static void write(ZipOutputStream zip, String path, String content) throws IOException {
        write(zip, path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(ZipOutputStream zip, String path, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
