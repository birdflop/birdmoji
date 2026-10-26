package com.birdflop.birdmoji;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds emojis sent with FakeNitro (and similar client mods), which are links to the emoji's image instead of
 * real emojis, either as [name](url) or just the url.
 */
public final class FakeNitro {

    private static final String URL = "https?://(?:cdn|media)\\.discordapp\\.(?:com|net)/emojis/\\d{16,20}\\.(?:png|gif|webp|jpe?g|avif)(?:\\?[^\\s)<>\\\\]*)?";
    private static final Pattern URL_PATTERN = Pattern.compile(URL);
    private static final Pattern ID_PATTERN = Pattern.compile("/emojis/(\\d{16,20})\\.");
    private static final Pattern NAME_PARAM_PATTERN = Pattern.compile("[?&]name=([^&)>\\s]+)");
    /** Stands in for a link component so it can be matched together with the text around it, as \uE000id\uE001name\uE002 */
    private static final String MARKER = "\uE000\\d+\uE001[^\uE002]*\uE002";
    private static final Pattern MARKER_PATTERN = Pattern.compile("\uE000(\\d+)\uE001([^\uE002]*)\uE002");
    /** The url may be wrapped in <> to hide the embed, which DiscordSRV may have escaped */
    private static final Pattern TEXT_PATTERN = Pattern.compile(
            "\\[(?<name>[^\\]\\n]{1,64})]\\((?:\\\\?<)?(?<url>" + URL + "|" + MARKER + ")(?:\\\\?>)?\\)|(?<bare>" + URL + "|" + MARKER + ")");

    public interface Replacement {
        Component emoji(long id, String name);
    }

    private FakeNitro() {}

    public static Component replace(Component component, Replacement replacement) {
        Component marked = markLinks(component);
        // Compacting merges the markers back into the text around them, so [name]( and ) are matched with them
        return (marked.equals(component) ? component : marked.compact()).replaceText(builder -> builder
                .match(TEXT_PATTERN)
                .replacement((match, text) -> {
                    String url = match.group("url") != null ? match.group("url") : match.group("bare");
                    Matcher marker = MARKER_PATTERN.matcher(url);
                    if (marker.matches()) {
                        long id = Long.parseLong(marker.group(1));
                        return replacement.emoji(id, match.group("name") != null ? clean(match.group("name")) : marker.group(2));
                    }
                    Long id = id(url);
                    return id == null ? text : replacement.emoji(id, match.group("name") != null ? clean(match.group("name")) : name(url));
                }));
    }

    /**
     * Links that were already turned into clickable text, either masked links or urls that DiscordSRV made
     * clickable. DiscordSRV's url also swallows the ) closing a masked link, which is put back after the marker.
     */
    private static Component markLinks(Component component) {
        ClickEvent click = component.clickEvent();
        // Adventure 5 made Action a class and removed value(), so only use what's in both 4 and 5
        if (click != null && click.action().name().equalsIgnoreCase("open_url")
                && click.payload() instanceof ClickEvent.Payload.Text payload) {
            String url = payload.value();
            Matcher matcher = URL_PATTERN.matcher(url);
            Long id = matcher.lookingAt() ? id(url) : null;
            if (id != null) {
                String text = component instanceof TextComponent textComponent ? textComponent.content().trim() : "";
                String name = text.isEmpty() || text.startsWith("http") ? name(url) : clean(text);
                return Component.text("\uE000" + id + "\uE001" + name.replace("\uE002", "") + "\uE002" + url.substring(matcher.end()));
            }
        }

        List<Component> children = component.children();
        if (children.isEmpty()) return component;
        return component.children(children.stream().map(FakeNitro::markLinks).toList());
    }

    private static Long id(String url) {
        Matcher matcher = ID_PATTERN.matcher(url);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String name(String url) {
        Matcher matcher = NAME_PARAM_PATTERN.matcher(url);
        return matcher.find() ? clean(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8)) : "emoji";
    }

    private static String clean(String name) {
        String cleaned = name.replace(":", "").replace("\\", "").trim();
        return cleaned.isEmpty() ? "emoji" : cleaned;
    }
}
